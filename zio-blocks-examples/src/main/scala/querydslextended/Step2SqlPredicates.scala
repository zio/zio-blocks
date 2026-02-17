package querydslextended

import zio.blocks.schema._

/**
 * Query DSL Part 3 — Step 2: SQL-Specific Predicates
 *
 * Demonstrates generating SQL from the extended expression ADT, including
 * IN, BETWEEN, IS NULL, LIKE, and composing with built-in SchemaExpr queries.
 *
 * Run with: sbt "examples/runMain querydslextended.Step2SqlPredicates"
 */
object Step2SqlPredicates extends App {

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

  // --- Extended Expression ADT ---

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

    def wrap[S, A](expr: SchemaExpr[S, A]): Expr[S, A] = Wrapped(expr)
    def col[S, A](optic: Optic[S, A]): Expr[S, A] = Column(optic)
  }

  extension [S, A](optic: Optic[S, A]) {
    def in(values: A*): Expr[S, Boolean] = Expr.In(Expr.col(optic), values.toList)
    def between(low: A, high: A): Expr[S, Boolean] = Expr.Between(Expr.col(optic), low, high)
    def isNull: Expr[S, Boolean] = Expr.IsNull(Expr.col(optic))
    def isNotNull: Expr[S, Boolean] = Expr.Not(Expr.IsNull(Expr.col(optic)))
  }

  extension [S](optic: Optic[S, String]) {
    def like(pattern: String): Expr[S, Boolean] = Expr.Like(Expr.col(optic), pattern)
  }

  extension [S](expr: Expr[S, Boolean]) {
    def &&(other: Expr[S, Boolean]): Expr[S, Boolean] = Expr.And(expr, other)
    def ||(other: Expr[S, Boolean]): Expr[S, Boolean] = Expr.Or(expr, other)
  }

  extension [S, A](expr: SchemaExpr[S, A]) {
    def toExpr: Expr[S, A] = Expr.Wrapped(expr)
  }

  // --- Extended SQL interpreter ---

  def exprToSql[S, A](expr: Expr[S, A]): String = expr match {
    case Expr.Wrapped(schemaExpr)     => toSql(schemaExpr)
    case Expr.Column(optic)          => columnName(optic)
    case Expr.Lit(value)             => sqlLiteral(value)
    case Expr.In(e, values)          =>
      s"${exprToSql(e)} IN (${values.map(v => sqlLiteral(v)).mkString(", ")})"
    case Expr.Between(e, low, high) =>
      s"(${exprToSql(e)} BETWEEN ${sqlLiteral(low)} AND ${sqlLiteral(high)})"
    case Expr.IsNull(e)              => s"${exprToSql(e)} IS NULL"
    case Expr.Like(e, pattern)       => s"${exprToSql(e)} LIKE '${pattern.replace("'", "''")}'"
    case Expr.And(l, r)              => s"(${exprToSql(l)} AND ${exprToSql(r)})"
    case Expr.Or(l, r)               => s"(${exprToSql(l)} OR ${exprToSql(r)})"
    case Expr.Not(e)                 => s"NOT (${exprToSql(e)})"
  }

  // --- Output ---

  println("=== SQL-Specific Predicates ===")
  println()

  println(s"IN:      ${exprToSql(Product.category.in("Electronics", "Books", "Toys"))}")
  println(s"BETWEEN: ${exprToSql(Product.price.between(10.0, 100.0))}")
  println(s"IS NULL: ${exprToSql(Product.name.isNull)}")
  println(s"NOT NULL:${exprToSql(Product.name.isNotNull)}")
  println(s"LIKE:    ${exprToSql(Product.name.like("Lap%"))}")
  println()

  println("=== Composing with SchemaExpr ===")
  println()

  val combined =
    Product.category.in("Electronics", "Books") &&
    Product.price.between(10.0, 500.0) &&
    (Product.rating >= 4).toExpr &&
    Product.name.like("M%")

  println(s"SELECT * FROM products WHERE ${exprToSql(combined)}")
}
