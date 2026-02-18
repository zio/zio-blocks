package querydslsql

/**
 * Query DSL SQL Generation — Part 2: Complete Example
 *
 * A complete, self-contained SQL generator that translates SchemaExpr query
 * expressions into both inline SQL and parameterized queries. Combines all
 * techniques: column name extraction, literal formatting, the full interpreter,
 * SELECT statement builders, and parameterized query generation.
 *
 * All SQL helpers, interpreters, and SELECT builders are defined in the package
 * object (package.scala). The Product domain type is defined in Common.scala.
 *
 * Run with: sbt "examples/runMain querydslsql.CompleteSqlGenerator"
 */
object CompleteSqlGenerator extends App {

  // --- Usage ---

  val query =
    (Product.category === "Electronics") &&
      (Product.inStock === true) &&
      (Product.price < 500.0) &&
      (Product.rating >= 4)

  println("=== Complete SQL Generator Example ===")
  println()

  // Inline SQL for debugging
  println("Inline SQL:")
  println(s"  ${select("products", query)}")
  println()

  // Parameterized SQL for execution
  val pq = toParameterized(query)
  println("Parameterized SQL:")
  println(s"  SQL:    ${pq.sql}")
  println(s"  Params: ${pq.params}")
  println()

  // String operations in SQL
  println("String operations:")
  println(s"  LIKE:   ${toSql(Product.name.matches("L%"))}")
  println(s"  CONCAT: ${toSql(Product.name.concat(" [SALE]"))}")
  println(s"  LENGTH: ${toSql(Product.name.length)}")
  println()

  // Arithmetic in SQL
  println("Arithmetic:")
  println(s"  Discount: ${toSql(Product.price * 0.9)}")
  println(s"  Tax:      ${toSql(Product.price * 1.08)}")
}
