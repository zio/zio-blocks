package querydslextended

/**
 * Query DSL Part 3 — Complete Example
 *
 * A complete example demonstrating the independent Expr expression language.
 * This design translates SchemaExpr into Expr via fromSchemaExpr.
 *
 * Run with: sbt "examples/runMain querydslextended.CompleteExtendedDSL"
 */
object CompleteExtendedDSL extends App {

  // --- Usage ---

  println("=== Complete Extended DSL Example ===")
  println()

  // 1. SQL-specific predicates — seamless composition
  val q1 = Product.category.in("Electronics", "Books") &&
    Product.price.between(10.0, 500.0) &&
    (Product.rating >= 4) &&
    Product.name.like("M%")

  println("1. Advanced WHERE clause:")
  println(s"   SELECT * FROM products WHERE ${exprToSql(q1)}")
  println()

  // 2. Type-safe aggregation
  val countExpr: Expr[Product, Long] = Expr.count(Expr.col(Product.name))
  val avgExpr: Expr[Product, Double] = Expr.avg(Expr.col(Product.price))
  val countSql                       = exprToSql(countExpr)
  val avgSql                         = exprToSql(avgExpr)

  println("2. GROUP BY with type-safe aggregates:")
  println(s"   SELECT category, $countSql AS cnt, $avgSql AS avg_price")
  println(s"   FROM products GROUP BY category HAVING $countSql > 2")
  println()

  // 3. CASE WHEN
  val tier = Expr
    .caseWhen[Product, String](
      (Product.price > 100.0).toExpr -> Expr.lit[Product, String]("expensive"),
      (Product.price > 10.0).toExpr  -> Expr.lit[Product, String]("moderate")
    )
    .otherwise(Expr.lit[Product, String]("cheap"))

  println("3. CASE WHEN computed column:")
  println(s"   SELECT name, price, ${exprToSql(tier)} AS tier FROM products")
  println()

  // 4. Multiple computed columns
  val stockStatus = Expr
    .caseWhen[Product, String](
      (Product.inStock === true).toExpr -> Expr.lit[Product, String]("available")
    )
    .otherwise(Expr.lit[Product, String]("out of stock"))

  println("4. Multiple computed columns:")
  println(s"   SELECT name, price, ${exprToSql(tier)} AS tier, ${exprToSql(stockStatus)} AS status FROM products")

}
