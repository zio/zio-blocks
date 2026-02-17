package querydslextended

import zio.blocks.schema._

/**
 * Query DSL Part 3 — Step 1: The Extended AST
 *
 * Demonstrates defining a custom Expr[S, A] sealed trait that wraps SchemaExpr
 * and adds SQL-specific predicates (IN, BETWEEN, IS NULL, LIKE), with extension
 * methods for ergonomic syntax.
 *
 * Run with: sbt "examples/runMain querydslextended.Step1ExtendedAST"
 */
object Step1ExtendedAST extends App {

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
    def lit[S, A](value: A): Expr[S, A] = Lit(value)
  }

  // --- Extension Methods ---

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
    def unary_! : Expr[S, Boolean] = Expr.Not(expr)
  }

  extension [S, A](expr: SchemaExpr[S, A]) {
    def toExpr: Expr[S, A] = Expr.Wrapped(expr)
  }

  // --- Show the AST structure ---

  println("=== Extended AST Examples ===")
  println()

  val inExpr = Product.category.in("Electronics", "Books", "Toys")
  println(s"IN expression:      $inExpr")

  val betweenExpr = Product.price.between(10.0, 100.0)
  println(s"BETWEEN expression: $betweenExpr")

  val likeExpr = Product.name.like("Lap%")
  println(s"LIKE expression:    $likeExpr")

  val nullExpr = Product.name.isNull
  println(s"IS NULL expression: $nullExpr")
  println()

  // Composing with SchemaExpr
  val combined = Product.category.in("Electronics") &&
    Product.price.between(10.0, 500.0) &&
    (Product.rating >= 4).toExpr
  println(s"Combined expression: $combined")
}
