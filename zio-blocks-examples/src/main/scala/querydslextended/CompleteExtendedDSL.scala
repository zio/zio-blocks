package querydslextended

import zio.blocks.schema._

/**
 * Query DSL Part 3 — Complete Example
 *
 * A complete, self-contained example demonstrating the independent Expr expression
 * language. This design translates SchemaExpr into Expr via fromSchemaExpr.
 *
 * Run with: sbt "examples/runMain querydslextended.CompleteExtendedDSL"
 */
object CompleteExtendedDSL extends App {

  // --- Domain ---

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

  // --- Independent Expr ADT ---

  sealed trait Expr[S, A]

  object Expr {
    // Core nodes
    final case class Column[S, A](optic: Optic[S, A]) extends Expr[S, A]
    final case class Lit[S, A](value: A, schema: Schema[A]) extends Expr[S, A]

    // Relational, logical, arithmetic, string
    final case class Relational[S, A](left: Expr[S, A], right: Expr[S, A], op: RelOp) extends Expr[S, Boolean]
    final case class And[S](left: Expr[S, Boolean], right: Expr[S, Boolean]) extends Expr[S, Boolean]
    final case class Or[S](left: Expr[S, Boolean], right: Expr[S, Boolean]) extends Expr[S, Boolean]
    final case class Not[S](expr: Expr[S, Boolean]) extends Expr[S, Boolean]
    final case class Arithmetic[S, A](left: Expr[S, A], right: Expr[S, A], op: ArithOp) extends Expr[S, A]
    final case class StringConcat[S](left: Expr[S, String], right: Expr[S, String]) extends Expr[S, String]
    final case class StringRegexMatch[S](regex: Expr[S, String], string: Expr[S, String]) extends Expr[S, Boolean]
    final case class StringLength[S](string: Expr[S, String]) extends Expr[S, Int]

    // SQL-specific extensions
    final case class In[S, A](expr: Expr[S, A], values: List[A]) extends Expr[S, Boolean]
    final case class Between[S, A](expr: Expr[S, A], low: A, high: A) extends Expr[S, Boolean]
    final case class IsNull[S, A](expr: Expr[S, A]) extends Expr[S, Boolean]
    final case class Like[S](expr: Expr[S, String], pattern: String) extends Expr[S, Boolean]

    // Type-safe aggregates
    final case class Agg[S, A, B](function: AggFunc[A, B], expr: Expr[S, A]) extends Expr[S, B]

    // Conditional
    final case class CaseWhen[S, A](
      branches: List[(Expr[S, Boolean], Expr[S, A])],
      otherwise: Option[Expr[S, A]]
    ) extends Expr[S, A]

    // Factory methods
    def col[S, A](optic: Optic[S, A]): Expr[S, A] = Column(optic)
    def lit[S, A](value: A)(implicit schema: Schema[A]): Expr[S, A] = Lit(value, schema)
    def count[S, A](expr: Expr[S, A]): Expr[S, Long]  = Agg(AggFunc.Count(), expr)
    def sum[S](expr: Expr[S, Double]): Expr[S, Double] = Agg(AggFunc.Sum, expr)
    def avg[S](expr: Expr[S, Double]): Expr[S, Double] = Agg(AggFunc.Avg, expr)
    def min[S, A](expr: Expr[S, A]): Expr[S, A]        = Agg(AggFunc.Min(), expr)
    def max[S, A](expr: Expr[S, A]): Expr[S, A]        = Agg(AggFunc.Max(), expr)

    def caseWhen[S, A](branches: (Expr[S, Boolean], Expr[S, A])*): CaseWhenBuilder[S, A] =
      CaseWhenBuilder(branches.toList)

    case class CaseWhenBuilder[S, A](branches: List[(Expr[S, Boolean], Expr[S, A])]) {
      def otherwise(value: Expr[S, A]): Expr[S, A] = CaseWhen(branches, Some(value))
      def end: Expr[S, A] = CaseWhen(branches, None)
    }

    // One-way translation from SchemaExpr
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
        Relational(fromSchemaExpr(l), fromSchemaExpr(r), relOp).asInstanceOf[Expr[S, A]]
      case SchemaExpr.Logical(l, r, op) =>
        val expr = op match {
          case SchemaExpr.LogicalOperator.And => And(fromSchemaExpr(l).asInstanceOf[Expr[S, Boolean]], fromSchemaExpr(r).asInstanceOf[Expr[S, Boolean]])
          case SchemaExpr.LogicalOperator.Or  => Or(fromSchemaExpr(l).asInstanceOf[Expr[S, Boolean]], fromSchemaExpr(r).asInstanceOf[Expr[S, Boolean]])
        }
        expr.asInstanceOf[Expr[S, A]]
      case SchemaExpr.Not(inner) =>
        Not(fromSchemaExpr(inner).asInstanceOf[Expr[S, Boolean]]).asInstanceOf[Expr[S, A]]
      case SchemaExpr.Arithmetic(l, r, op, _) =>
        val arithOp = op match {
          case SchemaExpr.ArithmeticOperator.Add      => ArithOp.Add
          case SchemaExpr.ArithmeticOperator.Subtract => ArithOp.Subtract
          case SchemaExpr.ArithmeticOperator.Multiply => ArithOp.Multiply
        }
        Arithmetic(fromSchemaExpr(l), fromSchemaExpr(r), arithOp)
      case SchemaExpr.StringConcat(l, r) =>
        StringConcat(fromSchemaExpr(l).asInstanceOf[Expr[S, String]], fromSchemaExpr(r).asInstanceOf[Expr[S, String]]).asInstanceOf[Expr[S, A]]
      case SchemaExpr.StringRegexMatch(regex, string) =>
        StringRegexMatch(fromSchemaExpr(regex).asInstanceOf[Expr[S, String]], fromSchemaExpr(string).asInstanceOf[Expr[S, String]]).asInstanceOf[Expr[S, A]]
      case SchemaExpr.StringLength(string) =>
        StringLength(fromSchemaExpr(string).asInstanceOf[Expr[S, String]]).asInstanceOf[Expr[S, A]]
    }
  }

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

  // Named AggFunc to avoid clash with package-level AggFunction
  sealed trait AggFunc[A, B] { def name: String }
  object AggFunc {
    case class Count[A]() extends AggFunc[A, Long]          { val name = "COUNT" }
    case object Sum        extends AggFunc[Double, Double]    { val name = "SUM" }
    case object Avg        extends AggFunc[Double, Double]    { val name = "AVG" }
    case class Min[A]()   extends AggFunc[A, A]              { val name = "MIN" }
    case class Max[A]()   extends AggFunc[A, A]              { val name = "MAX" }
  }

  // --- Extension methods with bridge ---

  implicit final class OpticOps[S, A](private val optic: Optic[S, A]) extends AnyVal {
    def in(values: A*): Expr[S, Boolean]           = Expr.In(Expr.col(optic), values.toList)
    def between(low: A, high: A): Expr[S, Boolean] = Expr.Between(Expr.col(optic), low, high)
    def isNull: Expr[S, Boolean]                   = Expr.IsNull(Expr.col(optic))
    def isNotNull: Expr[S, Boolean]                = Expr.Not(Expr.IsNull(Expr.col(optic)))
  }

  implicit final class StringOpticOps[S](private val optic: Optic[S, String]) extends AnyVal {
    def like(pattern: String): Expr[S, Boolean] = Expr.Like(Expr.col(optic), pattern)
  }

  implicit final class ExprBoolOps[S](private val self: Expr[S, Boolean]) extends AnyVal {
    def &&(other: Expr[S, Boolean]): Expr[S, Boolean]      = Expr.And(self, other)
    def &&(other: SchemaExpr[S, Boolean]): Expr[S, Boolean] = Expr.And(self, Expr.fromSchemaExpr(other))
    def ||(other: Expr[S, Boolean]): Expr[S, Boolean]      = Expr.Or(self, other)
    def ||(other: SchemaExpr[S, Boolean]): Expr[S, Boolean] = Expr.Or(self, Expr.fromSchemaExpr(other))
    def unary_! : Expr[S, Boolean]                          = Expr.Not(self)
  }

  implicit final class SchemaExprBridge[S](private val self: SchemaExpr[S, Boolean]) extends AnyVal {
    def &&(other: Expr[S, Boolean]): Expr[S, Boolean] = Expr.And(Expr.fromSchemaExpr(self), other)
    def ||(other: Expr[S, Boolean]): Expr[S, Boolean] = Expr.Or(Expr.fromSchemaExpr(self), other)
    def toExpr: Expr[S, Boolean] = Expr.fromSchemaExpr(self)
  }

  // --- SQL rendering ---

  def columnName(optic: zio.blocks.schema.Optic[_, _]): String =
    optic.toDynamic.nodes.collect { case f: DynamicOptic.Node.Field => f.name }.mkString("_")

  def sqlLiteralUntyped(value: Any): String = value match {
    case s: String  => s"'${s.replace("'", "''")}'"
    case b: Boolean => if (b) "TRUE" else "FALSE"
    case n: Number  => n.toString
    case other      => other.toString
  }

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

  // Single unified interpreter
  def exprToSql[S, A](expr: Expr[S, A]): String = expr match {
    case Expr.Column(optic)      => columnName(optic)
    case Expr.Lit(value, schema) => sqlLiteral(value, schema)
    case Expr.Relational(left, right, op) =>
      val sqlOp = op match {
        case RelOp.Equal => "="; case RelOp.NotEqual => "<>"
        case RelOp.LessThan => "<"; case RelOp.LessThanOrEqual => "<="
        case RelOp.GreaterThan => ">"; case RelOp.GreaterThanOrEqual => ">="
      }
      s"(${exprToSql(left)} $sqlOp ${exprToSql(right)})"
    case Expr.And(l, r)                   => s"(${exprToSql(l)} AND ${exprToSql(r)})"
    case Expr.Or(l, r)                    => s"(${exprToSql(l)} OR ${exprToSql(r)})"
    case Expr.Not(e)                      => s"NOT (${exprToSql(e)})"
    case Expr.Arithmetic(left, right, op) =>
      val sqlOp = op match {
        case ArithOp.Add => "+"; case ArithOp.Subtract => "-"; case ArithOp.Multiply => "*"
      }
      s"(${exprToSql(left)} $sqlOp ${exprToSql(right)})"
    case Expr.StringConcat(l, r)         => s"CONCAT(${exprToSql(l)}, ${exprToSql(r)})"
    case Expr.StringRegexMatch(regex, s) => s"(${exprToSql(s)} LIKE ${exprToSql(regex)})"
    case Expr.StringLength(s)            => s"LENGTH(${exprToSql(s)})"
    case Expr.In(e, values)              =>
      s"${exprToSql(e)} IN (${values.map(v => sqlLiteralUntyped(v)).mkString(", ")})"
    case Expr.Between(e, low, high) =>
      s"(${exprToSql(e)} BETWEEN ${sqlLiteralUntyped(low)} AND ${sqlLiteralUntyped(high)})"
    case Expr.IsNull(e)        => s"${exprToSql(e)} IS NULL"
    case Expr.Like(e, pattern) => s"${exprToSql(e)} LIKE '${pattern.replace("'", "''")}'"
    case Expr.Agg(func, e)     => s"${func.name}(${exprToSql(e)})"
    case Expr.CaseWhen(branches, otherwise) =>
      val cases = branches.map { case (cond, value) =>
        s"WHEN ${exprToSql(cond)} THEN ${exprToSql(value)}"
      }.mkString(" ")
      val elseClause = otherwise.map(e => s" ELSE ${exprToSql(e)}").getOrElse("")
      s"CASE $cases$elseClause END"
  }

  // --- Usage ---

  println("=== Complete Extended DSL Example ===")
  println()

  // 1. SQL-specific predicates — seamless composition
  val q1 = Product.category.in("Electronics", "Books") &&
    Product.price.between(10.0, 500.0) &&
    (Product.rating >= 4) &&
    Product.name.like("M%")

  println("1. Advanced WHERE clause:")
  println(s"   SELECT * FROM products WHERE ${exprToSql(q1)}")
  println()

  // 2. Type-safe aggregation
  val countExpr: Expr[Product, Long]   = Expr.count(Expr.col(Product.name))
  val avgExpr: Expr[Product, Double]   = Expr.avg(Expr.col(Product.price))
  val countSql = exprToSql(countExpr)
  val avgSql   = exprToSql(avgExpr)

  println("2. GROUP BY with type-safe aggregates:")
  println(s"   SELECT category, $countSql AS cnt, $avgSql AS avg_price")
  println(s"   FROM products GROUP BY category HAVING $countSql > 2")
  println()

  // 3. CASE WHEN
  val tier = Expr
    .caseWhen[Product, String](
      (Product.price > 100.0).toExpr -> Expr.lit[Product, String]("expensive"),
      (Product.price > 10.0).toExpr  -> Expr.lit[Product, String]("moderate")
    )
    .otherwise(Expr.lit[Product, String]("cheap"))

  println("3. CASE WHEN computed column:")
  println(s"   SELECT name, price, ${exprToSql(tier)} AS tier FROM products")
  println()

  // 4. Multiple computed columns
  val stockStatus = Expr
    .caseWhen[Product, String](
      (Product.inStock === true).toExpr -> Expr.lit[Product, String]("available")
    )
    .otherwise(Expr.lit[Product, String]("out of stock"))

  println("4. Multiple computed columns:")
  println(s"   SELECT name, price, ${exprToSql(tier)} AS tier, ${exprToSql(stockStatus)} AS status FROM products")

}
