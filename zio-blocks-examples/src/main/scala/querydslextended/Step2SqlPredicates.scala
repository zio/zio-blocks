package querydslextended

import zio.blocks.schema._

/**
 * Query DSL Part 3 — Step 2: SQL-Specific Predicates
 *
 * Demonstrates generating SQL from the independent Expr ADT using the single
 * unified interpreter (exprToSql). SchemaExpr values are translated into Expr
 * at the boundary via fromSchemaExpr — no dual-interpreter delegation needed.
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

  // Expr, exprToSql, and extensions are defined in Common.scala

  // --- Output ---

  println("=== SQL-Specific Predicates ===")
  println()

  println(s"IN:       ${exprToSql(Product.category.in("Electronics", "Books", "Toys"))}")
  println(s"BETWEEN:  ${exprToSql(Product.price.between(10.0, 100.0))}")
  println(s"IS NULL:  ${exprToSql(Product.name.isNull)}")
  println(s"NOT NULL: ${exprToSql(Product.name.isNotNull)}")
  println(s"LIKE:     ${exprToSql(Product.name.like("Lap%"))}")
  println()

  // Translate a SchemaExpr into Expr, then render with the same interpreter
  val schemaExpr: SchemaExpr[Product, Boolean] = Product.rating >= 4
  val asExpr = Expr.fromSchemaExpr(schemaExpr)
  println(s"SchemaExpr translated to SQL: ${exprToSql(asExpr)}")
  println()

  println("=== Composing with SchemaExpr ===")
  println()

  // Bridge extensions auto-translate SchemaExpr at the boundary — no .toExpr needed
  val combined =
    Product.category.in("Electronics", "Books") &&
    Product.price.between(10.0, 500.0) &&
    (Product.rating >= 4) &&
    Product.name.like("M%")

  println(s"SELECT * FROM products WHERE ${exprToSql(combined)}")
}
