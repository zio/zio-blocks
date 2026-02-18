package querydslbuilder

import zio.blocks.schema._

/**
 * Query DSL Part 4 — Step 1: Seamless Condition Composition
 *
 * Demonstrates bridge extension methods that let SchemaExpr and Expr
 * compose with && and || without explicit .toExpr lifting. SchemaExpr
 * values are translated into Expr at the boundary via fromSchemaExpr.
 *
 * Run with: sbt "examples/runMain querydslbuilder.Step1SeamlessConditions"
 */
object Step1SeamlessConditions extends App {

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

  // Expr, extensions, and exprToSql are defined in Common.scala and package.scala

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
