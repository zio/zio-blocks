package zio.blocks.schema

/**
 * A fluent builder for constructing typed migrations from `A` to `B`.
 *
 * Provides methods that accept `DynamicOptic` paths directly. Version-specific
 * traits (`MigrationBuilderVersionSpecific`) add macro-based selector methods
 * for ergonomic use with lambda expressions (e.g. `_.fieldName`).
 *
 * @param sourceSchema
 *   Schema for the source type
 * @param targetSchema
 *   Schema for the target type
 * @param actions
 *   Accumulated migration actions
 */
class MigrationBuilder[A, B](
  val sourceSchema: Schema[A],
  val targetSchema: Schema[B],
  val actions: Vector[MigrationAction]
) extends MigrationBuilderVersionSpecific[A, B] {

  /**
   * Build the migration with validation.
   *
   * Validates that root-level field operations reference fields that exist in
   * the source and target schemas. Throws `IllegalArgumentException` on
   * validation failure. Use `buildPartial` to skip validation.
   */
  def build: Migration[A, B] = {
    val errors = validate()
    if (errors.nonEmpty)
      throw new IllegalArgumentException(
        s"Migration validation failed: ${errors.mkString("; ")}"
      )
    Migration(DynamicMigration(actions), sourceSchema, targetSchema)
  }

  /**
   * Build the migration without validation.
   */
  def buildPartial: Migration[A, B] =
    Migration(DynamicMigration(actions), sourceSchema, targetSchema)

  // ----- Record operations (DynamicOptic-based) -----

  def addField(at: DynamicOptic, fieldName: String, default: DynamicValue): MigrationBuilder[A, B] =
    withAction(MigrationAction.AddField(at, fieldName, default))

  def dropField(at: DynamicOptic, fieldName: String, defaultForReverse: DynamicValue): MigrationBuilder[A, B] =
    withAction(MigrationAction.DropField(at, fieldName, defaultForReverse))

  def renameField(at: DynamicOptic, fromName: String, toName: String): MigrationBuilder[A, B] =
    withAction(MigrationAction.Rename(at, fromName, toName))

  def transformValue(
    at: DynamicOptic,
    transform: DynamicValue,
    reverseTransform: Option[DynamicValue] = None
  ): MigrationBuilder[A, B] =
    withAction(MigrationAction.TransformValue(at, transform, reverseTransform))

  def mandate(at: DynamicOptic, default: DynamicValue): MigrationBuilder[A, B] =
    withAction(MigrationAction.Mandate(at, default))

  def optionalize(at: DynamicOptic): MigrationBuilder[A, B] =
    withAction(MigrationAction.Optionalize(at))

  def changeType(
    at: DynamicOptic,
    converter: DynamicValue,
    reverseConverter: Option[DynamicValue] = None
  ): MigrationBuilder[A, B] =
    withAction(MigrationAction.ChangeType(at, converter, reverseConverter))

  // ----- SchemaExpr overloads -----

  /**
   * Add a field with a default provided by a `SchemaExpr`.
   *
   * The expression is evaluated at build time to produce a `DynamicValue`.
   * Supports `SchemaExpr.Literal` and `SchemaExpr.DefaultValue`.
   */
  def addField[S](at: DynamicOptic, fieldName: String, default: SchemaExpr[S, _]): MigrationBuilder[A, B] =
    addField(at, fieldName, MigrationBuilder.evalSchemaExpr(default))

  /**
   * Drop a field with a reverse default provided by a `SchemaExpr`.
   */
  def dropField[S](at: DynamicOptic, fieldName: String, defaultForReverse: SchemaExpr[S, _]): MigrationBuilder[A, B] =
    dropField(at, fieldName, MigrationBuilder.evalSchemaExpr(defaultForReverse))

  /**
   * Make a field mandatory with a default provided by a `SchemaExpr`.
   */
  def mandate[S](at: DynamicOptic, default: SchemaExpr[S, _]): MigrationBuilder[A, B] =
    mandate(at, MigrationBuilder.evalSchemaExpr(default))

  /**
   * Transform a field value with a transform provided by a `SchemaExpr`.
   */
  def transformValueExpr[S](
    at: DynamicOptic,
    transform: SchemaExpr[S, _],
    reverseTransform: Option[SchemaExpr[S, _]]
  ): MigrationBuilder[A, B] =
    transformValue(
      at,
      MigrationBuilder.evalSchemaExpr(transform),
      reverseTransform.map(MigrationBuilder.evalSchemaExpr(_))
    )

  /**
   * Change field type with a converter provided by a `SchemaExpr`.
   */
  def changeTypeExpr[S](
    at: DynamicOptic,
    converter: SchemaExpr[S, _],
    reverseConverter: Option[SchemaExpr[S, _]]
  ): MigrationBuilder[A, B] =
    changeType(
      at,
      MigrationBuilder.evalSchemaExpr(converter),
      reverseConverter.map(MigrationBuilder.evalSchemaExpr(_))
    )

  /**
   * Transform collection elements with a transform provided by a `SchemaExpr`.
   */
  def transformElementsExpr[S](
    at: DynamicOptic,
    transform: SchemaExpr[S, _],
    reverseTransform: Option[SchemaExpr[S, _]]
  ): MigrationBuilder[A, B] =
    transformElements(
      at,
      MigrationBuilder.evalSchemaExpr(transform),
      reverseTransform.map(MigrationBuilder.evalSchemaExpr(_))
    )

  /**
   * Transform map keys with a transform provided by a `SchemaExpr`.
   */
  def transformKeysExpr[S](
    at: DynamicOptic,
    transform: SchemaExpr[S, _],
    reverseTransform: Option[SchemaExpr[S, _]]
  ): MigrationBuilder[A, B] =
    transformKeys(
      at,
      MigrationBuilder.evalSchemaExpr(transform),
      reverseTransform.map(MigrationBuilder.evalSchemaExpr(_))
    )

  /**
   * Transform map values with a transform provided by a `SchemaExpr`.
   */
  def transformValuesExpr[S](
    at: DynamicOptic,
    transform: SchemaExpr[S, _],
    reverseTransform: Option[SchemaExpr[S, _]]
  ): MigrationBuilder[A, B] =
    transformValues(
      at,
      MigrationBuilder.evalSchemaExpr(transform),
      reverseTransform.map(MigrationBuilder.evalSchemaExpr(_))
    )

  /**
   * Join multiple fields with a combiner provided by a `SchemaExpr`.
   */
  def joinExpr[S](
    at: DynamicOptic,
    sourcePaths: Vector[DynamicOptic],
    combiner: SchemaExpr[S, _],
    splitter: Option[SchemaExpr[S, _]]
  ): MigrationBuilder[A, B] =
    join(
      at,
      sourcePaths,
      MigrationBuilder.evalSchemaExpr(combiner),
      splitter.map(MigrationBuilder.evalSchemaExpr(_))
    )

  /**
   * Split a field with a splitter provided by a `SchemaExpr`.
   */
  def splitExpr[S](
    at: DynamicOptic,
    targetPaths: Vector[DynamicOptic],
    splitter: SchemaExpr[S, _],
    combiner: Option[SchemaExpr[S, _]]
  ): MigrationBuilder[A, B] =
    split(
      at,
      targetPaths,
      MigrationBuilder.evalSchemaExpr(splitter),
      combiner.map(MigrationBuilder.evalSchemaExpr(_))
    )

  // ----- Enum operations -----

  def renameCase(at: DynamicOptic, fromName: String, toName: String): MigrationBuilder[A, B] =
    withAction(MigrationAction.RenameCase(at, fromName, toName))

  def transformCase(
    at: DynamicOptic,
    caseName: String,
    nestedActions: Vector[MigrationAction]
  ): MigrationBuilder[A, B] =
    withAction(MigrationAction.TransformCase(at, caseName, nestedActions))

  // ----- Join / Split operations -----

  /**
   * Join multiple fields into a single field.
   *
   * @param at
   *   The path where the combined value will be placed
   * @param sourcePaths
   *   The paths to the fields being joined
   * @param combiner
   *   The combined value
   * @param splitter
   *   Optional reverse splitter for reversing the migration
   */
  def join(
    at: DynamicOptic,
    sourcePaths: Vector[DynamicOptic],
    combiner: DynamicValue,
    splitter: Option[DynamicValue] = None
  ): MigrationBuilder[A, B] =
    withAction(MigrationAction.Join(at, sourcePaths, combiner, splitter))

  /**
   * Split a single field into multiple fields.
   *
   * @param at
   *   The path to the field being split
   * @param targetPaths
   *   The paths where split values will be placed
   * @param splitter
   *   The split value structure
   * @param combiner
   *   Optional reverse combiner for reversing the migration
   */
  def split(
    at: DynamicOptic,
    targetPaths: Vector[DynamicOptic],
    splitter: DynamicValue,
    combiner: Option[DynamicValue] = None
  ): MigrationBuilder[A, B] =
    withAction(MigrationAction.Split(at, targetPaths, splitter, combiner))

  // ----- Collection / Map operations -----

  def transformElements(
    at: DynamicOptic,
    transform: DynamicValue,
    reverseTransform: Option[DynamicValue] = None
  ): MigrationBuilder[A, B] =
    withAction(MigrationAction.TransformElements(at, transform, reverseTransform))

  def transformKeys(
    at: DynamicOptic,
    transform: DynamicValue,
    reverseTransform: Option[DynamicValue] = None
  ): MigrationBuilder[A, B] =
    withAction(MigrationAction.TransformKeys(at, transform, reverseTransform))

  def transformValues(
    at: DynamicOptic,
    transform: DynamicValue,
    reverseTransform: Option[DynamicValue] = None
  ): MigrationBuilder[A, B] =
    withAction(MigrationAction.TransformValues(at, transform, reverseTransform))

  // ----- Raw action -----

  /**
   * Add a raw `MigrationAction` to this builder.
   */
  def addAction(action: MigrationAction): MigrationBuilder[A, B] =
    withAction(action)

  // ----- Validation -----

  private def validate(): List[String] = {
    val errors       = List.newBuilder[String]
    val sourceFields = MigrationBuilder.extractFieldNames(sourceSchema)
    val targetFields = MigrationBuilder.extractFieldNames(targetSchema)

    actions.foreach {
      case MigrationAction.AddField(at, fieldName, _) if at.nodes.isEmpty =>
        if (targetFields.nonEmpty && !targetFields.contains(fieldName))
          errors += s"AddField '$fieldName' not found in target schema"
      case MigrationAction.DropField(at, fieldName, _) if at.nodes.isEmpty =>
        if (sourceFields.nonEmpty && !sourceFields.contains(fieldName))
          errors += s"DropField '$fieldName' not found in source schema"
      case MigrationAction.Rename(at, fromName, toName) if at.nodes.isEmpty =>
        if (sourceFields.nonEmpty && !sourceFields.contains(fromName))
          errors += s"Rename source '$fromName' not found in source schema"
        if (targetFields.nonEmpty && !targetFields.contains(toName))
          errors += s"Rename target '$toName' not found in target schema"
      case _ => () // Nested paths and other actions: skip validation
    }

    errors.result()
  }

  private def withAction(action: MigrationAction): MigrationBuilder[A, B] =
    new MigrationBuilder(sourceSchema, targetSchema, actions :+ action)
}

object MigrationBuilder {

  /**
   * Evaluate a `SchemaExpr` to a `DynamicValue` at build time.
   *
   * Supports input-independent expressions such as `SchemaExpr.Literal` and
   * `SchemaExpr.DefaultValue`. Throws `IllegalArgumentException` if the
   * expression fails to evaluate.
   */
  private[schema] def evalSchemaExpr[S, A](expr: SchemaExpr[S, A]): DynamicValue =
    expr.evalDynamic(null.asInstanceOf[S]) match {
      case Right(values) if values.nonEmpty => values.head
      case Right(_)                         =>
        throw new IllegalArgumentException("SchemaExpr produced no values")
      case Left(check) =>
        throw new IllegalArgumentException(s"SchemaExpr evaluation failed: $check")
    }

  private[schema] def extractFieldNames[X](schema: Schema[X]): Set[String] =
    schema.reflect match {
      case r: Reflect.Record[binding.Binding @unchecked, X @unchecked] =>
        r.fields.map(_.name).toSet
      case _ => Set.empty
    }
}
