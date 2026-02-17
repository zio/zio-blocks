package querydslextended

import zio.blocks.schema._

// ---------------------------------------------------------------------------
// Independent Expr language — translated from SchemaExpr, not wrapping it
// ---------------------------------------------------------------------------

sealed trait Expr[S, A]

object Expr {

  // --- Core nodes (superset of SchemaExpr's nodes) ---
  final case class Column[S, A](optic: Optic[S, A]) extends Expr[S, A]
  final case class Lit[S, A](value: A, schema: Schema[A]) extends Expr[S, A]

  // Relational
  final case class Relational[S, A](left: Expr[S, A], right: Expr[S, A], op: RelOp) extends Expr[S, Boolean]

  // Logical
  final case class And[S](left: Expr[S, Boolean], right: Expr[S, Boolean]) extends Expr[S, Boolean]
  final case class Or[S](left: Expr[S, Boolean], right: Expr[S, Boolean]) extends Expr[S, Boolean]
  final case class Not[S](expr: Expr[S, Boolean]) extends Expr[S, Boolean]

  // Arithmetic
  final case class Arithmetic[S, A](left: Expr[S, A], right: Expr[S, A], op: ArithOp) extends Expr[S, A]

  // String
  final case class StringConcat[S](left: Expr[S, String], right: Expr[S, String]) extends Expr[S, String]
  final case class StringRegexMatch[S](regex: Expr[S, String], string: Expr[S, String]) extends Expr[S, Boolean]
  final case class StringLength[S](string: Expr[S, String]) extends Expr[S, Int]

  // --- SQL-specific extensions (no SchemaExpr equivalents) ---
  final case class In[S, A](expr: Expr[S, A], values: List[A]) extends Expr[S, Boolean]
  final case class Between[S, A](expr: Expr[S, A], low: A, high: A) extends Expr[S, Boolean]
  final case class IsNull[S, A](expr: Expr[S, A]) extends Expr[S, Boolean]
  final case class Like[S](expr: Expr[S, String], pattern: String) extends Expr[S, Boolean]

  // --- Aggregates (return type reflects SQL semantics) ---
  final case class Agg[S, A, B](function: AggFunction[A, B], expr: Expr[S, A]) extends Expr[S, B]

  // --- Conditional ---
  final case class CaseWhen[S, A](
    branches: List[(Expr[S, Boolean], Expr[S, A])],
    otherwise: Option[Expr[S, A]]
  ) extends Expr[S, A]

  // --- Factory methods ---
  def col[S, A](optic: Optic[S, A]): Expr[S, A] = Column(optic)
  def lit[S, A](value: A)(implicit schema: Schema[A]): Expr[S, A] = Lit(value, schema)

  def count[S, A](expr: Expr[S, A]): Expr[S, Long]   = Agg(AggFunction.Count(), expr)
  def sum[S](expr: Expr[S, Double]): Expr[S, Double]  = Agg(AggFunction.Sum, expr)
  def avg[S](expr: Expr[S, Double]): Expr[S, Double]  = Agg(AggFunction.Avg, expr)
  def min[S, A](expr: Expr[S, A]): Expr[S, A]         = Agg(AggFunction.Min(), expr)
  def max[S, A](expr: Expr[S, A]): Expr[S, A]         = Agg(AggFunction.Max(), expr)

  def caseWhen[S, A](branches: (Expr[S, Boolean], Expr[S, A])*): CaseWhenBuilder[S, A] =
    CaseWhenBuilder(branches.toList)

  case class CaseWhenBuilder[S, A](branches: List[(Expr[S, Boolean], Expr[S, A])]) {
    def otherwise(value: Expr[S, A]): Expr[S, A] = CaseWhen(branches, Some(value))
    def end: Expr[S, A] = CaseWhen(branches, None)
  }

  // --- Translation from SchemaExpr (one-way, not embedding) ---
  def fromSchemaExpr[S, A](se: SchemaExpr[S, A]): Expr[S, A] = se match {
    case SchemaExpr.Optic(optic)      => Column(optic.asInstanceOf[Optic[S, A]])
    case SchemaExpr.Literal(value, s) => Lit(value.asInstanceOf[A], s.asInstanceOf[Schema[A]])

    case SchemaExpr.Relational(l, r, op) =>
      val relOp = op match {
        case SchemaExpr.RelationalOperator.Equal              => RelOp.Equal
        case SchemaExpr.RelationalOperator.NotEqual           => RelOp.NotEqual
        case SchemaExpr.RelationalOperator.LessThan           => RelOp.LessThan
        case SchemaExpr.RelationalOperator.LessThanOrEqual    => RelOp.LessThanOrEqual
        case SchemaExpr.RelationalOperator.GreaterThan        => RelOp.GreaterThan
        case SchemaExpr.RelationalOperator.GreaterThanOrEqual => RelOp.GreaterThanOrEqual
      }
      Relational(fromSchemaExpr(l), fromSchemaExpr(r), relOp)
        .asInstanceOf[Expr[S, A]] // Boolean

    case SchemaExpr.Logical(l, r, op) =>
      val expr = op match {
        case SchemaExpr.LogicalOperator.And => And(fromSchemaExpr(l).asInstanceOf[Expr[S, Boolean]], fromSchemaExpr(r).asInstanceOf[Expr[S, Boolean]])
        case SchemaExpr.LogicalOperator.Or  => Or(fromSchemaExpr(l).asInstanceOf[Expr[S, Boolean]], fromSchemaExpr(r).asInstanceOf[Expr[S, Boolean]])
      }
      expr.asInstanceOf[Expr[S, A]]

    case SchemaExpr.Not(inner) =>
      Not(fromSchemaExpr(inner).asInstanceOf[Expr[S, Boolean]])
        .asInstanceOf[Expr[S, A]]

    case SchemaExpr.Arithmetic(l, r, op, _) =>
      val arithOp = op match {
        case SchemaExpr.ArithmeticOperator.Add      => ArithOp.Add
        case SchemaExpr.ArithmeticOperator.Subtract => ArithOp.Subtract
        case SchemaExpr.ArithmeticOperator.Multiply => ArithOp.Multiply
      }
      Arithmetic(fromSchemaExpr(l), fromSchemaExpr(r), arithOp)

    case SchemaExpr.StringConcat(l, r) =>
      StringConcat(fromSchemaExpr(l).asInstanceOf[Expr[S, String]], fromSchemaExpr(r).asInstanceOf[Expr[S, String]])
        .asInstanceOf[Expr[S, A]]

    case SchemaExpr.StringRegexMatch(regex, string) =>
      StringRegexMatch(fromSchemaExpr(regex).asInstanceOf[Expr[S, String]], fromSchemaExpr(string).asInstanceOf[Expr[S, String]])
        .asInstanceOf[Expr[S, A]]

    case SchemaExpr.StringLength(string) =>
      StringLength(fromSchemaExpr(string).asInstanceOf[Expr[S, String]])
        .asInstanceOf[Expr[S, A]]
  }
}

// --- Operators ---

enum RelOp {
  case Equal, NotEqual, LessThan, LessThanOrEqual, GreaterThan, GreaterThanOrEqual
}

enum ArithOp {
  case Add, Subtract, Multiply
}

// Typed aggregate functions — the phantom types encode input→output
sealed trait AggFunction[A, B] {
  def name: String
}
object AggFunction {
  case class Count[A]()                   extends AggFunction[A, Long]   { val name = "COUNT" }
  case object Sum                         extends AggFunction[Double, Double] { val name = "SUM" }
  case object Avg                         extends AggFunction[Double, Double] { val name = "AVG" }
  case class Min[A]()                     extends AggFunction[A, A]      { val name = "MIN" }
  case class Max[A]()                     extends AggFunction[A, A]      { val name = "MAX" }
}

// ---------------------------------------------------------------------------
// Extension methods — single definition, no duplication
// ---------------------------------------------------------------------------

extension [S, A](optic: Optic[S, A]) {
  def in(values: A*): Expr[S, Boolean]           = Expr.In(Expr.col(optic), values.toList)
  def between(low: A, high: A): Expr[S, Boolean] = Expr.Between(Expr.col(optic), low, high)
  def isNull: Expr[S, Boolean]                   = Expr.IsNull(Expr.col(optic))
  def isNotNull: Expr[S, Boolean]                = Expr.Not(Expr.IsNull(Expr.col(optic)))
}

extension [S](optic: Optic[S, String]) {
  def like(pattern: String): Expr[S, Boolean] = Expr.Like(Expr.col(optic), pattern)
}

// Boolean combinators — work with both Expr and SchemaExpr (via implicit conversion)
extension [S](self: Expr[S, Boolean]) {
  def &&(other: Expr[S, Boolean]): Expr[S, Boolean]      = Expr.And(self, other)
  def &&(other: SchemaExpr[S, Boolean]): Expr[S, Boolean] = Expr.And(self, Expr.fromSchemaExpr(other))
  def ||(other: Expr[S, Boolean]): Expr[S, Boolean]      = Expr.Or(self, other)
  def ||(other: SchemaExpr[S, Boolean]): Expr[S, Boolean] = Expr.Or(self, Expr.fromSchemaExpr(other))
  def unary_! : Expr[S, Boolean]                          = Expr.Not(self)
}

// Bridge: SchemaExpr && Expr (when SchemaExpr's own && doesn't match)
extension [S](self: SchemaExpr[S, Boolean]) {
  def &&(other: Expr[S, Boolean]): Expr[S, Boolean] = Expr.And(Expr.fromSchemaExpr(self), other)
  def ||(other: Expr[S, Boolean]): Expr[S, Boolean] = Expr.Or(Expr.fromSchemaExpr(self), other)
  def toExpr: Expr[S, Boolean] = Expr.fromSchemaExpr(self)
}

// ---------------------------------------------------------------------------
// SQL rendering helpers — typed, no `Any`
// ---------------------------------------------------------------------------

def columnName(optic: zio.blocks.schema.Optic[?, ?]): String =
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
    val cases = branches.map { (cond, value) =>
      s"WHEN ${exprToSql(cond)} THEN ${exprToSql(value)}"
    }.mkString(" ")
    val elseClause = otherwise.map(e => s" ELSE ${exprToSql(e)}").getOrElse("")
    s"CASE $cases$elseClause END"
}
