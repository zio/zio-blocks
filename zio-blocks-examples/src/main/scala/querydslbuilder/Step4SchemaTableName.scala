package querydslbuilder

import zio.blocks.schema._

/**
 * Query DSL Part 4 — Step 4: Schema-Driven Table Names
 *
 * Demonstrates deriving SQL table names from Schema metadata:
 *   - Auto-pluralization from the case class name ("Product" → "products")
 *   - Explicit override via Modifier.config("sql.table_name", "order_items")
 *
 * Run with: sbt "examples/runMain querydslbuilder.Step4SchemaTableName"
 */
object Step4SchemaTableName extends App {

  // --- Output ---

  println("=== Step 4: Schema-Driven Table Names ===")
  println()

  // Product has no sql.table_name annotation → auto-pluralized from "Product"
  println(s"Product table name: ${Product.table.name}")
  println(s"  (derived from class name '${Product.schema.reflect.typeId.name}' → pluralized)")
  println()

  // OrderItem has Modifier.config("sql.table_name", "order_items") → uses annotation
  println(s"OrderItem table name: ${OrderItem.table.name}")
  println(s"  (from Modifier.config annotation)")
  println()

  // Show it working in generated SQL
  val productQuery = select(Product.table)
    .columns(Product.name, Product.price)
    .where(Product.inStock === true)

  println("Product SELECT:")
  println(s"  ${renderSelect(productQuery)}")
  println()

  val orderQuery = select(OrderItem.table)
    .columns(OrderItem.orderId, OrderItem.quantity, OrderItem.unitPrice)
    .where(OrderItem.quantity >= 5)

  println("OrderItem SELECT:")
  println(s"  ${renderSelect(orderQuery)}")
  println()

  // Demonstrate pluralize edge cases
  println("Pluralization examples:")
  println(s"  product  → ${pluralize("product")}")
  println(s"  category → ${pluralize("category")}")
  println(s"  address  → ${pluralize("address")}")
  println(s"  tax      → ${pluralize("tax")}")
  println(s"  key      → ${pluralize("key")}")
}
