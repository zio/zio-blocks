package querydslbuilder

import zio.blocks.schema._

/**
 * Query DSL Part 4 — Step 1: Seamless Condition Composition
 *
 * Demonstrates bridge extension methods that let SchemaExpr and Expr
 * compose with && and || without explicit .toExpr lifting.
 *
 * Run with: sbt "examples/runMain querydslbuilder.Step1SeamlessConditions"
 */
object Step1SeamlessConditions extends App {

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

  // --- SQL helpers ---

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

  // --- Expr ADT ---

  sealed trait Expr[S, A]

  object Expr {
    final case class Wrapped[S, A](expr: SchemaExpr[S, A]) extends Expr[S, A]
    final case class Column[S, A](optic: Optic[S, A])      extends Expr[S, A]
    final case class Lit[S, A](value: A)                   extends Expr[S, A]

    final case class In[S, A](expr: Expr[S, A], values: List[A])      extends Expr[S, Boolean]
    final case class Between[S, A](expr: Expr[S, A], low: A, high: A) extends Expr[S, Boolean]
    final case class IsNull[S, A](expr: Expr[S, A])                   extends Expr[S, Boolean]
    final case class Like[S](expr: Expr[S, String], pattern: String)  extends Expr[S, Boolean]

    final case class And[S](left: Expr[S, Boolean], right: Expr[S, Boolean]) extends Expr[S, Boolean]
    final case class Or[S](left: Expr[S, Boolean], right: Expr[S, Boolean])  extends Expr[S, Boolean]
    final case class Not[S](expr: Expr[S, Boolean])                          extends Expr[S, Boolean]

    def col[S, A](optic: Optic[S, A]): Expr[S, A] = Column(optic)
  }

  // --- Extension methods with bridge ---

  extension [S, A](optic: Optic[S, A]) {
    def in(values: A*): Expr[S, Boolean]           = Expr.In(Expr.col(optic), values.toList)
    def between(low: A, high: A): Expr[S, Boolean] = Expr.Between(Expr.col(optic), low, high)
  }

  extension [S](optic: Optic[S, String]) {
    def like(pattern: String): Expr[S, Boolean] = Expr.Like(Expr.col(optic), pattern)
  }

  extension [S](self: Expr[S, Boolean]) {
    def &&(other: Expr[S, Boolean]): Expr[S, Boolean]      = Expr.And(self, other)
    def &&(other: SchemaExpr[S, Boolean]): Expr[S, Boolean] = Expr.And(self, Expr.Wrapped(other))
    def ||(other: Expr[S, Boolean]): Expr[S, Boolean]      = Expr.Or(self, other)
    def ||(other: SchemaExpr[S, Boolean]): Expr[S, Boolean] = Expr.Or(self, Expr.Wrapped(other))
    def unary_! : Expr[S, Boolean]                         = Expr.Not(self)
  }

  extension [S](self: SchemaExpr[S, Boolean]) {
    def &&(other: Expr[S, Boolean]): Expr[S, Boolean] = Expr.And(Expr.Wrapped(self), other)
    def ||(other: Expr[S, Boolean]): Expr[S, Boolean] = Expr.Or(Expr.Wrapped(self), other)
  }

  extension [S, A](expr: SchemaExpr[S, A]) {
    def toExpr: Expr[S, A] = Expr.Wrapped(expr)
  }

  // --- SQL interpreter ---

  def exprToSql[S, A](expr: Expr[S, A]): String = expr match {
    case Expr.Wrapped(schemaExpr)     => toSql(schemaExpr)
    case Expr.Column(optic)           => columnName(optic)
    case Expr.Lit(value)              => sqlLiteral(value)
    case Expr.In(e, values)           =>
      s"${exprToSql(e)} IN (${values.map(v => sqlLiteral(v)).mkString(", ")})"
    case Expr.Between(e, low, high)   =>
      s"(${exprToSql(e)} BETWEEN ${sqlLiteral(low)} AND ${sqlLiteral(high)})"
    case Expr.IsNull(e)               => s"${exprToSql(e)} IS NULL"
    case Expr.Like(e, pattern)        => s"${exprToSql(e)} LIKE '${pattern.replace("'", "''")}'"
    case Expr.And(l, r)               => s"(${exprToSql(l)} AND ${exprToSql(r)})"
    case Expr.Or(l, r)                => s"(${exprToSql(l)} OR ${exprToSql(r)})"
    case Expr.Not(e)                  => s"NOT (${exprToSql(e)})"
  }

  // --- Demonstrate seamless composition ---

  println("=== Step 1: Seamless Condition Composition ===")
  println()

  // No .toExpr needed! SchemaExpr and Expr mix naturally
  val condition =
    Product.category === "Electronics" &&
    Product.rating >= 4 &&
    Product.price.between(10.0, 500.0) &&
    Product.name.like("L%")

  println("Mixed SchemaExpr + Expr condition:")
  println(s"  ${exprToSql(condition)}")
  println()

  // OR combination works too
  val orCondition =
    Product.price.between(0.0, 10.0) ||
    (Product.rating >= 5).toExpr ||
    Product.category.in("Sale", "Clearance")

  println("OR combination:")
  println(s"  ${exprToSql(orCondition)}")
}
