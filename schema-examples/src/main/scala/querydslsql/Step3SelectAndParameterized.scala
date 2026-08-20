package querydslsql

/**
 * Query DSL SQL Generation — Part 2, Step 3: SELECT Statements and
 * Parameterized Queries
 *
 * Demonstrates building complete SELECT statements from SchemaExpr predicates,
 * and generating parameterized queries with ? placeholders for SQL injection
 * safety.
 *
 * SQL helpers, SELECT builders, and parameterized query support are defined in
 * the package object (package.scala). The Product domain type is defined in
 * Common.scala.
 *
 * Run with: sbt "examples/runMain querydslsql.Step3SelectAndParameterized"
 */
object Step3SelectAndParameterized extends App {

  val query = (Product.category === "Electronics") && (Product.inStock === true) && (Product.price < 500.0)

  println("=== SELECT Statements ===")
  println()
  println(select("products", query))
  println()
  println(selectColumns("products", List("name", "price"), query))
  println()
  println(selectWithLimit("products", query, orderBy = Some("price ASC"), limit = Some(10)))
  println()

  // --- Parameterized Queries ---

  val q          = (Product.category === "Electronics") && (Product.price < 500.0) && (Product.rating >= 4)
  val paramQuery = toParameterized(q)

  println("=== Parameterized Queries ===")
  println()
  println(s"SQL:    ${paramQuery.sql}")
  println(s"Params: ${paramQuery.params}")
}
