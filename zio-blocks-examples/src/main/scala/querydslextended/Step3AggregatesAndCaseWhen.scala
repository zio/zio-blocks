package querydslextended

import zio.blocks.schema._

/**
 * Query DSL Part 3 — Step 3: Aggregates and CASE WHEN
 *
 * Demonstrates aggregate functions (COUNT, SUM, AVG, MIN, MAX) and CASE WHEN
 * conditional expressions in the extended expression ADT, generating advanced
 * SQL with GROUP BY and computed columns.
 *
 * Run with: sbt "examples/runMain querydslextended.Step3AggregatesAndCaseWhen"
 */
object Step3AggregatesAndCaseWhen extends App {

  // --- Domain Types ---

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

  // --- Part 2 SQL helpers ---

  def columnName(optic: zio.blocks.schema.Optic[?, ?]): String =
    optic.toDynamic.nodes.collect { case f: DynamicOptic.Node.Field => f.name }.mkString("_")

  def sqlLiteral(value: Any): String = value match {
    case s: String  => s"'${s.replace("'", "''")}'"
    case b: Boolean => if (b) "TRUE" else "FALSE"
    case n: Number  => n.toString
    case other      => other.toString
  }

  def toSql[A, B](expr: SchemaExpr[A, B]): String = expr match {
    case SchemaExpr.Optic(optic)      => columnName(optic)
    case SchemaExpr.Literal(value, _) => sqlLiteral(value)
    case SchemaExpr.Relational(left, right, op) =>
      val sqlOp = op match {
        case SchemaExpr.RelationalOperator.Equal              => "="
        case SchemaExpr.RelationalOperator.NotEqual           => "<>"
        case SchemaExpr.RelationalOperator.LessThan           => "<"
        case SchemaExpr.RelationalOperator.LessThanOrEqual    => "<="
        case SchemaExpr.RelationalOperator.GreaterThan        => ">"
        case SchemaExpr.RelationalOperator.GreaterThanOrEqual => ">="
      }
      s"(${toSql(left)} $sqlOp ${toSql(right)})"
    case SchemaExpr.Logical(left, right, op) =>
      val sqlOp = op match {
        case SchemaExpr.LogicalOperator.And => "AND"
        case SchemaExpr.LogicalOperator.Or  => "OR"
      }
      s"(${toSql(left)} $sqlOp ${toSql(right)})"
    case SchemaExpr.Not(inner)                      => s"NOT (${toSql(inner)})"
    case SchemaExpr.Arithmetic(left, right, op, _) =>
      val sqlOp = op match {
        case SchemaExpr.ArithmeticOperator.Add      => "+"
        case SchemaExpr.ArithmeticOperator.Subtract => "-"
        case SchemaExpr.ArithmeticOperator.Multiply => "*"
      }
      s"(${toSql(left)} $sqlOp ${toSql(right)})"
    case SchemaExpr.StringConcat(left, right)       => s"CONCAT(${toSql(left)}, ${toSql(right)})"
    case SchemaExpr.StringRegexMatch(regex, string) => s"(${toSql(string)} LIKE ${toSql(regex)})"
    case SchemaExpr.StringLength(string)            => s"LENGTH(${toSql(string)})"
  }

  // --- Full Extended Expression ADT ---

  sealed trait Expr[S, A]
  object Expr {
    final case class Wrapped[S, A](expr: SchemaExpr[S, A]) extends Expr[S, A]
    final case class Column[S, A](optic: Optic[S, A]) extends Expr[S, A]
    final case class Lit[S, A](value: A) extends Expr[S, A]
    final case class In[S, A](expr: Expr[S, A], values: List[A]) extends Expr[S, Boolean]
    final case class Between[S, A](expr: Expr[S, A], low: A, high: A) extends Expr[S, Boolean]
    final case class IsNull[S, A](expr: Expr[S, A]) extends Expr[S, Boolean]
    final case class Like[S](expr: Expr[S, String], pattern: String) extends Expr[S, Boolean]
    final case class And[S](left: Expr[S, Boolean], right: Expr[S, Boolean]) extends Expr[S, Boolean]
    final case class Or[S](left: Expr[S, Boolean], right: Expr[S, Boolean]) extends Expr[S, Boolean]
    final case class Not[S](expr: Expr[S, Boolean]) extends Expr[S, Boolean]
    final case class Agg[S, A](function: AggFunction, expr: Expr[S, A]) extends Expr[S, A]
    final case class CaseWhen[S, A](
      branches: List[(Expr[S, Boolean], Expr[S, A])],
      otherwise: Option[Expr[S, A]]
    ) extends Expr[S, A]

    def wrap[S, A](expr: SchemaExpr[S, A]): Expr[S, A] = Wrapped(expr)
    def col[S, A](optic: Optic[S, A]): Expr[S, A] = Column(optic)
    def lit[S, A](value: A): Expr[S, A] = Lit(value)
    def count[S, A](expr: Expr[S, A]): Expr[S, A] = Agg(AggFunction.Count, expr)
    def sum[S, A](expr: Expr[S, A]): Expr[S, A] = Agg(AggFunction.Sum, expr)
    def avg[S, A](expr: Expr[S, A]): Expr[S, A] = Agg(AggFunction.Avg, expr)
    def min[S, A](expr: Expr[S, A]): Expr[S, A] = Agg(AggFunction.Min, expr)
    def max[S, A](expr: Expr[S, A]): Expr[S, A] = Agg(AggFunction.Max, expr)

    def caseWhen[S, A](branches: (Expr[S, Boolean], Expr[S, A])*): CaseWhenBuilder[S, A] =
      CaseWhenBuilder(branches.toList)

    case class CaseWhenBuilder[S, A](branches: List[(Expr[S, Boolean], Expr[S, A])]) {
      def otherwise(value: Expr[S, A]): Expr[S, A] = CaseWhen(branches, Some(value))
      def end: Expr[S, A] = CaseWhen(branches, None)
    }
  }

  sealed trait AggFunction
  object AggFunction {
    case object Count extends AggFunction
    case object Sum   extends AggFunction
    case object Avg   extends AggFunction
    case object Min   extends AggFunction
    case object Max   extends AggFunction
  }

  extension [S, A](expr: SchemaExpr[S, A]) {
    def toExpr: Expr[S, A] = Expr.Wrapped(expr)
  }

  // --- Full interpreter ---

  def exprToSql[S, A](expr: Expr[S, A]): String = expr match {
    case Expr.Wrapped(schemaExpr) => toSql(schemaExpr)
    case Expr.Column(optic)      => columnName(optic)
    case Expr.Lit(value)         => sqlLiteral(value)
    case Expr.In(e, values)      =>
      s"${exprToSql(e)} IN (${values.map(v => sqlLiteral(v)).mkString(", ")})"
    case Expr.Between(e, low, high) =>
      s"(${exprToSql(e)} BETWEEN ${sqlLiteral(low)} AND ${sqlLiteral(high)})"
    case Expr.IsNull(e)          => s"${exprToSql(e)} IS NULL"
    case Expr.Like(e, pattern)   => s"${exprToSql(e)} LIKE '${pattern.replace("'", "''")}'"
    case Expr.And(l, r)          => s"(${exprToSql(l)} AND ${exprToSql(r)})"
    case Expr.Or(l, r)           => s"(${exprToSql(l)} OR ${exprToSql(r)})"
    case Expr.Not(e)             => s"NOT (${exprToSql(e)})"
    case Expr.Agg(func, e)      =>
      val name = func match {
        case AggFunction.Count => "COUNT"
        case AggFunction.Sum   => "SUM"
        case AggFunction.Avg   => "AVG"
        case AggFunction.Min   => "MIN"
        case AggFunction.Max   => "MAX"
      }
      s"$name(${exprToSql(e)})"
    case Expr.CaseWhen(branches, otherwise) =>
      val cases = branches.map { (cond, value) =>
        s"WHEN ${exprToSql(cond)} THEN ${exprToSql(value)}"
      }.mkString(" ")
      val elseClause = otherwise.map(e => s" ELSE ${exprToSql(e)}").getOrElse("")
      s"CASE $cases$elseClause END"
  }

  // --- Aggregate expressions ---

  println("=== Aggregate Expressions ===")
  println()

  val countSql = exprToSql(Expr.count(Expr.col(Product.name)))
  val avgSql   = exprToSql(Expr.avg(Expr.col(Product.price)))
  val maxSql   = exprToSql(Expr.max(Expr.col(Product.rating)))

  println(s"COUNT: $countSql")
  println(s"AVG:   $avgSql")
  println(s"MAX:   $maxSql")
  println()

  // GROUP BY query
  val groupBySql = s"SELECT category, $countSql AS product_count, $avgSql AS avg_price " +
    s"FROM products GROUP BY category HAVING $countSql > 2"
  println(s"GROUP BY: $groupBySql")
  println()

  // --- CASE WHEN ---

  println("=== CASE WHEN Expressions ===")
  println()

  val priceLabel = Expr.caseWhen[Product, String](
    (Product.price > 100.0).toExpr -> Expr.lit[Product, String]("expensive"),
    (Product.price > 10.0).toExpr  -> Expr.lit[Product, String]("moderate")
  ).otherwise(Expr.lit[Product, String]("cheap"))

  println(s"CASE WHEN: ${exprToSql(priceLabel)}")
  println()

  val selectSql = s"SELECT name, price, ${exprToSql(priceLabel)} AS tier FROM products"
  println(s"Full SELECT: $selectSql")
}
