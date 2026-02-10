package zio.blocks.schema

/**
 * A typed wrapper around `DynamicSchemaExpr` that provides compile-time type
 * safety. Mirrors the `Migration[A, B]` / `DynamicMigration` pattern.
 *
 * `SchemaExpr` are used for persistence DSLs, implemented in third-party
 * libraries, as well as for validation, implemented in this library. In
 * addition, `SchemaExpr` could be used for data migration.
 *
 * @tparam A
 *   The input type
 * @tparam B
 *   The output type
 * @param dynamic
 *   The underlying serializable expression
 * @param inputSchema
 *   Schema for converting A to DynamicValue
 * @param outputSchema
 *   Schema for converting DynamicValue back to B
 */
final case class SchemaExpr[A, B](
  dynamic: DynamicSchemaExpr,
  inputSchema: Schema[A],
  outputSchema: Schema[B]
) {

  /**
   * Evaluate the expression on the input value.
   */
  def eval(input: A): Either[OpticCheck, Seq[B]] = {
    val dv = inputSchema.toDynamicValue(input)
    dynamic.eval(dv) match {
      case Left(err)  => Left(SchemaExpr.toOpticCheck(err, dynamic, input))
      case Right(dvs) =>
        val results = dvs.map(outputSchema.fromDynamicValue)
        results.collectFirst { case Left(err) => err } match {
          case Some(err) => Left(SchemaExpr.schemaErrorToOpticCheck(err))
          case None      => Right(results.collect { case Right(v) => v })
        }
    }
  }

  /**
   * Evaluate the expression on the input value, returning DynamicValue results.
   */
  def evalDynamic(input: A): Either[OpticCheck, Seq[DynamicValue]] = {
    val dv = inputSchema.toDynamicValue(input)
    dynamic.eval(dv).left.map(SchemaExpr.toOpticCheck(_, dynamic, input))
  }

  final def &&(that: SchemaExpr[A, Boolean])(implicit ev: B =:= Boolean): SchemaExpr[A, Boolean] = {
    val _ = ev
    SchemaExpr(
      DynamicSchemaExpr.Logical(this.dynamic, that.dynamic, DynamicSchemaExpr.LogicalOperator.And),
      inputSchema,
      that.outputSchema
    )
  }

  final def ||(that: SchemaExpr[A, Boolean])(implicit ev: B =:= Boolean): SchemaExpr[A, Boolean] = {
    val _ = ev
    SchemaExpr(
      DynamicSchemaExpr.Logical(this.dynamic, that.dynamic, DynamicSchemaExpr.LogicalOperator.Or),
      inputSchema,
      that.outputSchema
    )
  }
}

object SchemaExpr {

  // --- Operator type aliases (delegate to DynamicSchemaExpr) ---

  type RelationalOperator = DynamicSchemaExpr.RelationalOperator
  val RelationalOperator: DynamicSchemaExpr.RelationalOperator.type = DynamicSchemaExpr.RelationalOperator

  type LogicalOperator = DynamicSchemaExpr.LogicalOperator
  val LogicalOperator: DynamicSchemaExpr.LogicalOperator.type = DynamicSchemaExpr.LogicalOperator

  type ArithmeticOperator = DynamicSchemaExpr.ArithmeticOperator
  val ArithmeticOperator: DynamicSchemaExpr.ArithmeticOperator.type = DynamicSchemaExpr.ArithmeticOperator

  // --- Factory methods used by Optic DSL ---

  private[schema] def literal[S, A](value: A, schema: Schema[A], sourceSchema: Schema[S]): SchemaExpr[S, A] =
    SchemaExpr(DynamicSchemaExpr.Literal(schema.toDynamicValue(value)), sourceSchema, schema)

  private[schema] def optic[S, A](optic: zio.blocks.schema.Optic[S, A]): SchemaExpr[S, A] =
    SchemaExpr(DynamicSchemaExpr.Dynamic(optic.toDynamic), new Schema(optic.source), new Schema(optic.focus))

  private[schema] def relational[S, A](
    left: SchemaExpr[S, A],
    right: SchemaExpr[S, A],
    op: DynamicSchemaExpr.RelationalOperator
  ): SchemaExpr[S, Boolean] =
    SchemaExpr(DynamicSchemaExpr.Relational(left.dynamic, right.dynamic, op), left.inputSchema, Schema.boolean)

  private[schema] def logical[S](
    left: SchemaExpr[S, Boolean],
    right: SchemaExpr[S, Boolean],
    op: DynamicSchemaExpr.LogicalOperator
  ): SchemaExpr[S, Boolean] =
    SchemaExpr(DynamicSchemaExpr.Logical(left.dynamic, right.dynamic, op), left.inputSchema, Schema.boolean)

  private[schema] def not[S](expr: SchemaExpr[S, Boolean]): SchemaExpr[S, Boolean] =
    SchemaExpr(DynamicSchemaExpr.Not(expr.dynamic), expr.inputSchema, Schema.boolean)

  private[schema] def arithmetic[S, A](
    left: SchemaExpr[S, A],
    right: SchemaExpr[S, A],
    op: DynamicSchemaExpr.ArithmeticOperator,
    isNumeric: IsNumeric[A]
  ): SchemaExpr[S, A] =
    SchemaExpr(
      DynamicSchemaExpr.Arithmetic(
        left.dynamic,
        right.dynamic,
        op,
        DynamicSchemaExpr.NumericType.fromIsNumeric(isNumeric)
      ),
      left.inputSchema,
      left.outputSchema
    )

  private[schema] def stringConcat[S](
    left: SchemaExpr[S, String],
    right: SchemaExpr[S, String]
  ): SchemaExpr[S, String] =
    SchemaExpr(DynamicSchemaExpr.StringConcat(left.dynamic, right.dynamic), left.inputSchema, Schema.string)

  private[schema] def stringRegexMatch[S](
    regex: SchemaExpr[S, String],
    string: SchemaExpr[S, String]
  ): SchemaExpr[S, Boolean] =
    SchemaExpr(DynamicSchemaExpr.StringRegexMatch(regex.dynamic, string.dynamic), regex.inputSchema, Schema.boolean)

  private[schema] def stringLength[S](string: SchemaExpr[S, String]): SchemaExpr[S, Int] =
    SchemaExpr(DynamicSchemaExpr.StringLength(string.dynamic), string.inputSchema, Schema.int)

  // --- Error conversion helpers ---

  private val UnexpectedCasePattern = "UNEXPECTED_CASE:(.+):(.+):(\\d+)".r
  private val EmptySequencePattern  = "EMPTY_SEQUENCE:(\\d+)".r

  private def toOpticCheck(error: String, expr: DynamicSchemaExpr, input: Any): OpticCheck = {
    val optic = findOptic(expr).getOrElse(DynamicOptic.root)
    error match {
      case UnexpectedCasePattern(expected, actual, nodeIdxStr) =>
        val nodeIdx = nodeIdxStr.toInt
        val prefix  = new DynamicOptic(optic.nodes.take(nodeIdx + 1))
        new OpticCheck(new ::(OpticCheck.UnexpectedCase(expected, actual, optic, prefix, input), Nil))
      case EmptySequencePattern(nodeIdxStr) =>
        val nodeIdx = nodeIdxStr.toInt
        val prefix  = new DynamicOptic(optic.nodes.take(nodeIdx + 1))
        new OpticCheck(new ::(OpticCheck.EmptySequence(optic, prefix), Nil))
      case _ =>
        new OpticCheck(new ::(OpticCheck.WrappingError(optic, optic, SchemaError(error)), Nil))
    }
  }

  private def findOptic(expr: DynamicSchemaExpr): Option[DynamicOptic] = expr match {
    case DynamicSchemaExpr.Dynamic(optic)              => Some(optic)
    case DynamicSchemaExpr.Relational(left, _, _)      => findOptic(left)
    case DynamicSchemaExpr.Logical(left, _, _)         => findOptic(left)
    case DynamicSchemaExpr.Arithmetic(left, _, _, _)   => findOptic(left)
    case DynamicSchemaExpr.StringConcat(left, _)       => findOptic(left)
    case DynamicSchemaExpr.StringLength(string)        => findOptic(string)
    case DynamicSchemaExpr.StringRegexMatch(_, string) => findOptic(string)
    case DynamicSchemaExpr.Not(inner)                  => findOptic(inner)
    case DynamicSchemaExpr.Convert(inner, _)           => findOptic(inner)
    case DynamicSchemaExpr.StringUppercase(inner)      => findOptic(inner)
    case DynamicSchemaExpr.StringLowercase(inner)      => findOptic(inner)
    case DynamicSchemaExpr.StringSplit(inner, _)       => findOptic(inner)
    case _                                             => None
  }

  private def schemaErrorToOpticCheck(error: SchemaError): OpticCheck =
    new OpticCheck(new ::(OpticCheck.WrappingError(DynamicOptic.root, DynamicOptic.root, error), Nil))
}
