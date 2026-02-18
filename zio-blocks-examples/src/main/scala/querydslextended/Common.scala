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

sealed trait RelOp
object RelOp {
  case object Equal              extends RelOp
  case object NotEqual           extends RelOp
  case object LessThan           extends RelOp
  case object LessThanOrEqual    extends RelOp
  case object GreaterThan        extends RelOp
  case object GreaterThanOrEqual extends RelOp
}

sealed trait ArithOp
object ArithOp {
  case object Add      extends ArithOp
  case object Subtract extends ArithOp
  case object Multiply extends ArithOp
}

// Typed aggregate functions — the phantom types encode input->output
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
