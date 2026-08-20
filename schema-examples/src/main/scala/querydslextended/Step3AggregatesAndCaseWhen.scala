package querydslextended

/**
 * Query DSL Part 3 — Step 3: Aggregates and CASE WHEN
 *
 * Demonstrates type-safe aggregate functions and CASE WHEN conditional
 * expressions. Aggregate return types reflect SQL semantics: COUNT → Long,
 * SUM/AVG → Double, MIN/MAX → same type as input.
 *
 * Run with: sbt "examples/runMain querydslextended.Step3AggregatesAndCaseWhen"
 */
object Step3AggregatesAndCaseWhen extends App {

  // --- Aggregate expressions ---

  println("=== Aggregate Expressions ===")
  println()

  // COUNT returns Expr[S, Long], SUM/AVG return Expr[S, Double], MIN/MAX preserve input type
  val countExpr: Expr[Product, Long] = Expr.count(Expr.col(Product.name))
  val avgExpr: Expr[Product, Double] = Expr.avg(Expr.col(Product.price))
  val maxExpr: Expr[Product, Int]    = Expr.max(Expr.col(Product.rating))

  val countSql = exprToSql(countExpr)
  val avgSql   = exprToSql(avgExpr)
  val maxSql   = exprToSql(maxExpr)

  println(s"COUNT: $countSql")
  println(s"AVG:   $avgSql")
  println(s"MAX:   $maxSql")
  println()

  // GROUP BY query
  val groupBySql = s"SELECT category, $countSql AS product_count, $avgSql AS avg_price " +
    s"FROM products GROUP BY category HAVING $countSql > 2"
  println(s"GROUP BY: $groupBySql")
  println()

  // --- CASE WHEN ---

  println("=== CASE WHEN Expressions ===")
  println()

  // Bridge extensions handle SchemaExpr→Expr translation automatically
  val priceLabel = Expr
    .caseWhen[Product, String](
      (Product.price > 100.0).toExpr -> Expr.lit[Product, String]("expensive"),
      (Product.price > 10.0).toExpr  -> Expr.lit[Product, String]("moderate")
    )
    .otherwise(Expr.lit[Product, String]("cheap"))

  println(s"CASE WHEN: ${exprToSql(priceLabel)}")
  println()

  val selectSql = s"SELECT name, price, ${exprToSql(priceLabel)} AS tier FROM products"
  println(s"Full SELECT: $selectSql")
}
