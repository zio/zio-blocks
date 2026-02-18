package object querydslextended {

  import zio.blocks.schema._

  // ---------------------------------------------------------------------------
  // Extension methods — single definition, no duplication
  // ---------------------------------------------------------------------------

  implicit final class OpticExprOps[S, A](private val optic: Optic[S, A]) extends AnyVal {
    def in(values: A*): Expr[S, Boolean]           = Expr.In(Expr.col(optic), values.toList)
    def between(low: A, high: A): Expr[S, Boolean] = Expr.Between(Expr.col(optic), low, high)
    def isNull: Expr[S, Boolean]                   = Expr.IsNull(Expr.col(optic))
    def isNotNull: Expr[S, Boolean]                = Expr.Not(Expr.IsNull(Expr.col(optic)))
  }

  implicit final class StringOpticExprOps[S](private val optic: Optic[S, String]) extends AnyVal {
    def like(pattern: String): Expr[S, Boolean] = Expr.Like(Expr.col(optic), pattern)
  }

  // Boolean combinators — work with both Expr and SchemaExpr (via bridge)
  implicit final class ExprBooleanOps[S](private val self: Expr[S, Boolean]) extends AnyVal {
    def &&(other: Expr[S, Boolean]): Expr[S, Boolean]      = Expr.And(self, other)
    def &&(other: SchemaExpr[S, Boolean]): Expr[S, Boolean] = Expr.And(self, Expr.fromSchemaExpr(other))
    def ||(other: Expr[S, Boolean]): Expr[S, Boolean]      = Expr.Or(self, other)
    def ||(other: SchemaExpr[S, Boolean]): Expr[S, Boolean] = Expr.Or(self, Expr.fromSchemaExpr(other))
    def unary_! : Expr[S, Boolean]                          = Expr.Not(self)
  }

  // Bridge: SchemaExpr && Expr (when SchemaExpr's own && doesn't match)
  implicit final class SchemaExprBooleanBridge[S](private val self: SchemaExpr[S, Boolean]) extends AnyVal {
    def &&(other: Expr[S, Boolean]): Expr[S, Boolean] = Expr.And(Expr.fromSchemaExpr(self), other)
    def ||(other: Expr[S, Boolean]): Expr[S, Boolean] = Expr.Or(Expr.fromSchemaExpr(self), other)
    def toExpr: Expr[S, Boolean] = Expr.fromSchemaExpr(self)
  }

  // ---------------------------------------------------------------------------
  // SQL rendering helpers — typed, no `Any`
  // ---------------------------------------------------------------------------

  def columnName(optic: zio.blocks.schema.Optic[_, _]): String =
    optic.toDynamic.nodes.collect { case f: DynamicOptic.Node.Field => f.name }.mkString("_")

  def sqlLiteral[A](value: A, schema: Schema[A]): String = {
    val dv = schema.toDynamicValue(value)
    dv match {
      case p: DynamicValue.Primitive => p.value match {
        case _: PrimitiveValue.String  => s"'${value.toString.replace("'", "''")}'"
        case _: PrimitiveValue.Boolean => if (value.asInstanceOf[Boolean]) "TRUE" else "FALSE"
        case _                         => value.toString
      }
      case _ => value.toString
    }
  }

  // Fallback for raw values (aggregates, Between, In — where we don't carry Schema)
  def sqlLiteralUntyped(value: Any): String = value match {
    case s: String  => s"'${s.replace("'", "''")}'"
    case b: Boolean => if (b) "TRUE" else "FALSE"
    case n: Number  => n.toString
    case other      => other.toString
  }

  // ---------------------------------------------------------------------------
  // Single unified SQL interpreter — no delegation to a second interpreter
  // ---------------------------------------------------------------------------

  def exprToSql[S, A](expr: Expr[S, A]): String = expr match {
    case Expr.Column(optic)      => columnName(optic)
    case Expr.Lit(value, schema) => sqlLiteral(value, schema)

    case Expr.Relational(left, right, op) =>
      val sqlOp = op match {
        case RelOp.Equal              => "="
        case RelOp.NotEqual           => "<>"
        case RelOp.LessThan           => "<"
        case RelOp.LessThanOrEqual    => "<="
        case RelOp.GreaterThan        => ">"
        case RelOp.GreaterThanOrEqual => ">="
      }
      s"(${exprToSql(left)} $sqlOp ${exprToSql(right)})"

    case Expr.And(l, r) => s"(${exprToSql(l)} AND ${exprToSql(r)})"
    case Expr.Or(l, r)  => s"(${exprToSql(l)} OR ${exprToSql(r)})"
    case Expr.Not(e)    => s"NOT (${exprToSql(e)})"

    case Expr.Arithmetic(left, right, op) =>
      val sqlOp = op match {
        case ArithOp.Add      => "+"
        case ArithOp.Subtract => "-"
        case ArithOp.Multiply => "*"
      }
      s"(${exprToSql(left)} $sqlOp ${exprToSql(right)})"

    case Expr.StringConcat(l, r)          => s"CONCAT(${exprToSql(l)}, ${exprToSql(r)})"
    case Expr.StringRegexMatch(regex, s)  => s"(${exprToSql(s)} LIKE ${exprToSql(regex)})"
    case Expr.StringLength(s)             => s"LENGTH(${exprToSql(s)})"

    // SQL-specific
    case Expr.In(e, values) =>
      s"${exprToSql(e)} IN (${values.map(v => sqlLiteralUntyped(v)).mkString(", ")})"
    case Expr.Between(e, low, high) =>
      s"(${exprToSql(e)} BETWEEN ${sqlLiteralUntyped(low)} AND ${sqlLiteralUntyped(high)})"
    case Expr.IsNull(e)          => s"${exprToSql(e)} IS NULL"
    case Expr.Like(e, pattern)   => s"${exprToSql(e)} LIKE '${pattern.replace("'", "''")}'"

    // Aggregates
    case Expr.Agg(func, e) => s"${func.name}(${exprToSql(e)})"

    // CASE WHEN
    case Expr.CaseWhen(branches, otherwise) =>
      val cases = branches.map { case (cond, value) =>
        s"WHEN ${exprToSql(cond)} THEN ${exprToSql(value)}"
      }.mkString(" ")
      val elseClause = otherwise.map(e => s" ELSE ${exprToSql(e)}").getOrElse("")
      s"CASE $cases$elseClause END"
  }
}
