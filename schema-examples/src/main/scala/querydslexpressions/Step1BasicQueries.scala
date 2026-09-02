package querydslexpressions

import zio.blocks.schema._

/**
 * Query DSL with Reified Optics — Part 1, Step 1: Basic Queries
 *
 * Demonstrates defining domain types with schemas and optics, then building
 * equality and comparison queries and evaluating them against data.
 *
 * Run with: sbt "examples/runMain querydslexpressions.Step1BasicQueries"
 */
object Step1BasicQueries extends App {

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

  // --- Equality and Comparison Queries ---

  val isElectronics: SchemaExpr[Product, Boolean] =
    Product.category === "Electronics"

  val expensiveItems: SchemaExpr[Product, Boolean] =
    Product.price > 100.0

  val budgetFriendly: SchemaExpr[Product, Boolean] =
    Product.price <= 50.0

  val highRated: SchemaExpr[Product, Boolean] =
    Product.rating >= 4

  // --- Evaluating Queries ---

  val laptop = Product("Laptop", 999.99, "Electronics", true, 5)
  val pen    = Product("Pen", 2.50, "Office", true, 3)

  println("=== Equality and Comparison Queries ===")
  println()

  println(s"isElectronics(laptop) = ${isElectronics.eval(laptop)}")
  println(s"isElectronics(pen)    = ${isElectronics.eval(pen)}")
  println()

  println(s"expensiveItems(laptop) = ${expensiveItems.eval(laptop)}")
  println(s"expensiveItems(pen)    = ${expensiveItems.eval(pen)}")
  println()

  println(s"budgetFriendly(laptop) = ${budgetFriendly.eval(laptop)}")
  println(s"budgetFriendly(pen)    = ${budgetFriendly.eval(pen)}")
  println()

  println(s"highRated(laptop) = ${highRated.eval(laptop)}")
  println(s"highRated(pen)    = ${highRated.eval(pen)}")
}
