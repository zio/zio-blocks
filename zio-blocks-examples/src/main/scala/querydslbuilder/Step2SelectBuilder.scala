package querydslbuilder

import zio.blocks.schema._

/**
 * Query DSL Part 4 — Step 2: The SELECT Builder
 *
 * Demonstrates the fluent SELECT builder with .columns(), .where(), .orderBy(),
 * and .limit() methods. The single unified exprToSql interpreter handles all
 * expression types — no dual-interpreter delegation needed.
 *
 * Run with: sbt "examples/runMain querydslbuilder.Step2SelectBuilder"
 */
object Step2SelectBuilder extends App {

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
      Product.price.between(10.0, 500.0) &&
        (Product.category === "Electronics") &&
        (Product.rating >= 4)
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
