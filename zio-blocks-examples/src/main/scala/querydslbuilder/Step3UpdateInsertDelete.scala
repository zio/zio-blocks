package querydslbuilder

import zio.blocks.schema._

/**
 * Query DSL Part 4 — Step 3: UPDATE, INSERT, and DELETE Builders
 *
 * Demonstrates the fluent UPDATE builder with .set() and .where(), the INSERT
 * builder with .set(), and the DELETE builder with .where(). All builders use
 * the Expr ADT with fromSchemaExpr translation.
 *
 * Run with: sbt "examples/runMain querydslbuilder.Step3UpdateInsertDelete"
 */
object Step3UpdateInsertDelete extends App {

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

    val table: Table[Product] = Table("products")

    val name: Lens[Product, String]     = optic(_.name)
    val price: Lens[Product, Double]    = optic(_.price)
    val category: Lens[Product, String] = optic(_.category)
    val inStock: Lens[Product, Boolean] = optic(_.inStock)
    val rating: Lens[Product, Int]      = optic(_.rating)
  }

  // Expr, extensions, builders, and renderers are defined in Common.scala and package.scala

  // --- Output ---

  println("=== Step 3: UPDATE, INSERT, DELETE ===")
  println()

  // UPDATE matching user's desired syntax
  val basicUpdate =
    update(Product.table)
      .set(Product.price, 9.99)
      .where(
        Product.price.between(10.0, 30.0) &&
          Product.name.like("M%") &&
          (Product.category === "Books") &&
          (Product.rating >= 4) &&
          (Product.inStock === true)
      )

  println("1. UPDATE with mixed conditions:")
  println(s"   ${renderUpdate(basicUpdate)}")
  println()

  // Multi-set UPDATE
  val multiUpdate =
    update(Product.table)
      .set(Product.price, 19.99)
      .set(Product.inStock, false)
      .where(Product.category === "Clearance")

  println("2. UPDATE with multiple SET clauses:")
  println(s"   ${renderUpdate(multiUpdate)}")
  println()

  // INSERT
  val ins = insertInto(Product.table)
    .set(Product.name, "Wireless Mouse")
    .set(Product.price, 29.99)
    .set(Product.category, "Electronics")
    .set(Product.inStock, true)
    .set(Product.rating, 4)

  println("3. INSERT:")
  println(s"   ${renderInsert(ins)}")
  println()

  // DELETE
  val del = deleteFrom(Product.table)
    .where(
      Product.price.between(0.0, 1.0) &&
        (Product.inStock === false)
    )

  println("4. DELETE with mixed conditions:")
  println(s"   ${renderDelete(del)}")
}
