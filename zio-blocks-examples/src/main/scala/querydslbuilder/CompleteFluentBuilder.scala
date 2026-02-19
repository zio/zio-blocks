package querydslbuilder

/**
 * Query DSL Part 4 — Complete Example
 *
 * A complete example demonstrating the fluent SQL builder DSL: SELECT, UPDATE,
 * INSERT, and DELETE with seamless SchemaExpr/Expr composition, schema-driven
 * table names, and SQL rendering.
 *
 * Run with: sbt "examples/runMain querydslbuilder.CompleteFluentBuilder"
 */
object CompleteFluentBuilder extends App {

  // --- Usage ---

  println("=== Complete Fluent SQL Builder ===")
  println()

  // Table names are derived from Schema metadata:
  //   Product   → auto-pluralized to "products"
  //   OrderItem → Modifier.config("sql.table_name", "order_items")
  println(s"Product table:   ${Product.table.name}")
  println(s"OrderItem table: ${OrderItem.table.name}")
  println()

  // 1. SELECT with mixed conditions, ordering, and limit
  val q = select(Product.table)
    .columns(Product.name, Product.price, Product.category)
    .where(
      Product.category.in("Electronics", "Books") &&
        Product.price.between(10.0, 500.0) &&
        (Product.rating >= 4).toExpr
    )
    .orderBy(Product.price, SortOrder.Desc)
    .limit(20)

  println("1. SELECT:")
  println(s"   ${renderSelect(q)}")
  println()

  // 2. UPDATE with seamless condition mixing
  val u = update(Product.table)
    .set(Product.price, 9.99)
    .where(
      Product.price.between(10.0, 30.0) &&
        Product.name.like("M%") &&
        (Product.category === "Books") &&
        (Product.rating >= 4) &&
        (Product.inStock === true)
    )

  println("2. UPDATE:")
  println(s"   ${renderUpdate(u)}")
  println()

  // 3. INSERT
  val i = insertInto(Product.table)
    .set(Product.name, "Wireless Mouse")
    .set(Product.price, 29.99)
    .set(Product.category, "Electronics")
    .set(Product.inStock, true)
    .set(Product.rating, 4)

  println("3. INSERT:")
  println(s"   ${renderInsert(i)}")
  println()

  // 4. DELETE
  val d = deleteFrom(Product.table)
    .where(Product.price.between(0.0, 1.0) && (Product.inStock === false))

  println("4. DELETE:")
  println(s"   ${renderDelete(d)}")
  println()

  // 5. Cross-table query using annotated OrderItem table
  val orderQuery = select(OrderItem.table)
    .columns(OrderItem.orderId, OrderItem.productId, OrderItem.quantity)
    .where(OrderItem.quantity >= 3)
    .orderBy(OrderItem.unitPrice, SortOrder.Desc)

  println("5. OrderItem SELECT (annotated table name):")
  println(s"   ${renderSelect(orderQuery)}")
}
