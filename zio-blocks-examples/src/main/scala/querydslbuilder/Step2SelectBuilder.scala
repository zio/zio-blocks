package querydslbuilder

import zio.blocks.schema._

/**
 * Query DSL Part 4 — Step 2: The SELECT Builder
 *
 * Demonstrates the fluent SELECT builder with .columns(), .where(),
 * .orderBy(), and .limit() methods. The single unified exprToSql interpreter
 * handles all expression types — no dual-interpreter delegation needed.
 *
 * Run with: sbt "examples/runMain querydslbuilder.Step2SelectBuilder"
 */
object Step2SelectBuilder extends App {

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

  println("=== Step 2: SELECT Builder ===")
  println()

  val basicSelect = select(Product.table)
    .columns(Product.name, Product.price)
    .where(Product.inStock === true)

  println("1. Basic SELECT:")
  println(s"   ${renderSelect(basicSelect)}")
  println()

  val advancedSelect = select(Product.table)
    .columns(Product.name, Product.price, Product.category)
    .where(
      Product.category === "Electronics" &&
      Product.rating >= 4 &&
      Product.price.between(10.0, 500.0)
    )
    .orderBy(Product.price, SortOrder.Desc)
    .limit(10)

  println("2. Advanced SELECT with ordering and limit:")
  println(s"   ${renderSelect(advancedSelect)}")
  println()

  val selectAll = select(Product.table)
    .where(Product.name.like("Wire%"))

  println("3. SELECT * with LIKE condition:")
  println(s"   ${renderSelect(selectAll)}")
}
