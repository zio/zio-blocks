package querydslextended

import zio.blocks.schema._

// ---------------------------------------------------------------------------
// Shared domain type used across Steps 1–3 and CompleteExtendedDSL
// ---------------------------------------------------------------------------

case class Product(
  name: String,
  price: Double,
  category: String,
  inStock: Boolean,
  rating: Int
)

object Product extends CompanionOptics[Product] {
  implicit val schema: Schema[Product] = Schema.derived

  val name: Lens[Product, String]     = optic(_.name)
  val price: Lens[Product, Double]    = optic(_.price)
  val category: Lens[Product, String] = optic(_.category)
  val inStock: Lens[Product, Boolean] = optic(_.inStock)
  val rating: Lens[Product, Int]      = optic(_.rating)
}

// ---------------------------------------------------------------------------
// Expr language
// ---------------------------------------------------------------------------

sealed trait Expr[S, A]

object Expr {

  // --- Core nodes (superset of SchemaExpr's nodes) ---
  final case class Column[S, A](path: DynamicOptic) extends Expr[S, A]
  final case class Lit[S, A](value: DynamicValue)   extends Expr[S, A]

  // Relational
  final case class Relational[S, A](left: Expr[S, A], right: Expr[S, A], op: RelOp) extends Expr[S, Boolean]

  // Logical
  final case class And[S](left: Expr[S, Boolean], right: Expr[S, Boolean]) extends Expr[S, Boolean]
  final case class Or[S](left: Expr[S, Boolean], right: Expr[S, Boolean])  extends Expr[S, Boolean]
  final case class Not[S](expr: Expr[S, Boolean])                          extends Expr[S, Boolean]

  // Arithmetic
  final case class Arithmetic[S, A](left: Expr[S, A], right: Expr[S, A], op: ArithOp) extends Expr[S, A]

  // String
  final case class StringConcat[S](left: Expr[S, String], right: Expr[S, String])       extends Expr[S, String]
  final case class StringRegexMatch[S](regex: Expr[S, String], string: Expr[S, String]) extends Expr[S, Boolean]
  final case class StringLength[S](string: Expr[S, String])                             extends Expr[S, Int]

  // --- SQL-specific extensions (no SchemaExpr equivalents) ---
  final case class In[S, A](expr: Expr[S, A], values: List[A], schema: Schema[A])      extends Expr[S, Boolean]
  final case class Between[S, A](expr: Expr[S, A], low: A, high: A, schema: Schema[A]) extends Expr[S, Boolean]
  final case class IsNull[S, A](expr: Expr[S, A])                                      extends Expr[S, Boolean]
  final case class Like[S](expr: Expr[S, String], pattern: String)                     extends Expr[S, Boolean]

  // --- Aggregates (return type reflects SQL semantics) ---
  final case class Agg[S, A, B](function: AggFunction[A, B], expr: Expr[S, A]) extends Expr[S, B]

  // --- Conditional ---
  final case class CaseWhen[S, A](
    branches: List[(Expr[S, Boolean], Expr[S, A])],
    otherwise: Option[Expr[S, A]]
  ) extends Expr[S, A]

  // --- Factory methods ---
  def col[S, A](optic: Optic[S, A]): Expr[S, A]                   = Column(optic.toDynamic)
  def lit[S, A](value: A)(implicit schema: Schema[A]): Expr[S, A] = Lit(schema.toDynamicValue(value))

  def count[S, A](expr: Expr[S, A]): Expr[S, Long]   = Agg(AggFunction.Count(), expr)
  def sum[S](expr: Expr[S, Double]): Expr[S, Double] = Agg(AggFunction.Sum, expr)
  def avg[S](expr: Expr[S, Double]): Expr[S, Double] = Agg(AggFunction.Avg, expr)
  def min[S, A](expr: Expr[S, A]): Expr[S, A]        = Agg(AggFunction.Min(), expr)
  def max[S, A](expr: Expr[S, A]): Expr[S, A]        = Agg(AggFunction.Max(), expr)

  def caseWhen[S, A](branches: (Expr[S, Boolean], Expr[S, A])*): CaseWhenBuilder[S, A] =
    CaseWhenBuilder(branches.toList)

  case class CaseWhenBuilder[S, A](branches: List[(Expr[S, Boolean], Expr[S, A])]) {
    def otherwise(value: Expr[S, A]): Expr[S, A] = CaseWhen(branches, Some(value))
    def end: Expr[S, A]                          = CaseWhen(branches, None)
  }

  // --- Translation from SchemaExpr ---
  def fromSchemaExpr[S, A](se: SchemaExpr[S, A]): Expr[S, A] = {
    val result = fromDynamic[S](se.dynamic)
    result.asInstanceOf[Expr[S, A]]
  }

  private def fromDynamic[S](dse: DynamicSchemaExpr): Expr[S, ?] = dse match {
    case DynamicSchemaExpr.Select(path)   => Column[S, Any](path)
    case DynamicSchemaExpr.Literal(value) => Lit[S, Any](value)

    case DynamicSchemaExpr.Relational(l, r, op) =>
      val relOp = op match {
        case DynamicSchemaExpr.RelationalOperator.Equal              => RelOp.Equal
        case DynamicSchemaExpr.RelationalOperator.NotEqual           => RelOp.NotEqual
        case DynamicSchemaExpr.RelationalOperator.LessThan           => RelOp.LessThan
        case DynamicSchemaExpr.RelationalOperator.LessThanOrEqual    => RelOp.LessThanOrEqual
        case DynamicSchemaExpr.RelationalOperator.GreaterThan        => RelOp.GreaterThan
        case DynamicSchemaExpr.RelationalOperator.GreaterThanOrEqual => RelOp.GreaterThanOrEqual
      }
      Relational(fromDynamic[S](l).asInstanceOf[Expr[S, Any]], fromDynamic[S](r).asInstanceOf[Expr[S, Any]], relOp)

    case DynamicSchemaExpr.Logical(l, r, op) =>
      op match {
        case DynamicSchemaExpr.LogicalOperator.And =>
          And(fromDynamic[S](l).asInstanceOf[Expr[S, Boolean]], fromDynamic[S](r).asInstanceOf[Expr[S, Boolean]])
        case DynamicSchemaExpr.LogicalOperator.Or =>
          Or(fromDynamic[S](l).asInstanceOf[Expr[S, Boolean]], fromDynamic[S](r).asInstanceOf[Expr[S, Boolean]])
      }

    case DynamicSchemaExpr.Not(inner) => Not(fromDynamic[S](inner).asInstanceOf[Expr[S, Boolean]])

    case DynamicSchemaExpr.Arithmetic(l, r, op, _) =>
      val arithOp = op match {
        case DynamicSchemaExpr.ArithmeticOperator.Add      => ArithOp.Add
        case DynamicSchemaExpr.ArithmeticOperator.Subtract => ArithOp.Subtract
        case DynamicSchemaExpr.ArithmeticOperator.Multiply => ArithOp.Multiply
        case _                                             => ArithOp.Add
      }
      Arithmetic(fromDynamic[S](l).asInstanceOf[Expr[S, Any]], fromDynamic[S](r).asInstanceOf[Expr[S, Any]], arithOp)

    case DynamicSchemaExpr.StringConcat(l, r) =>
      StringConcat(fromDynamic[S](l).asInstanceOf[Expr[S, String]], fromDynamic[S](r).asInstanceOf[Expr[S, String]])
    case DynamicSchemaExpr.StringRegexMatch(regex, string) =>
      StringRegexMatch(
        fromDynamic[S](regex).asInstanceOf[Expr[S, String]],
        fromDynamic[S](string).asInstanceOf[Expr[S, String]]
      )
    case DynamicSchemaExpr.StringLength(string) => StringLength(fromDynamic[S](string).asInstanceOf[Expr[S, String]])
    case _                                      => Lit[S, Any](DynamicValue.Null)
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
  case class Count[A]() extends AggFunction[A, Long]        { val name = "COUNT" }
  case object Sum       extends AggFunction[Double, Double] { val name = "SUM"   }
  case object Avg       extends AggFunction[Double, Double] { val name = "AVG"   }
  case class Min[A]()   extends AggFunction[A, A]           { val name = "MIN"   }
  case class Max[A]()   extends AggFunction[A, A]           { val name = "MAX"   }
}
