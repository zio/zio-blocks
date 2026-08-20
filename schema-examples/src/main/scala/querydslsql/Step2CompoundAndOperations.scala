package querydslsql

/**
 * Query DSL SQL Generation — Part 2, Step 2: Compound Queries and Operations
 *
 * Demonstrates translating compound boolean queries (AND, OR, NOT), arithmetic
 * expressions, and string operations to SQL.
 *
 * SQL helpers (columnName, sqlLiteral, toSql) are defined in the package object
 * (package.scala). The Product domain type is defined in Common.scala.
 *
 * Run with: sbt "examples/runMain querydslsql.Step2CompoundAndOperations"
 */
object Step2CompoundAndOperations extends App {

  // --- Compound Queries ---

  val affordableElectronics =
    (Product.category === "Electronics") && (Product.price < 500.0)

  val goodDeal =
    (Product.price < 10.0) || (Product.rating >= 5)

  val outOfStock = !Product.inStock

  val complexQuery =
    ((Product.category === "Electronics") && (Product.price < 500.0)) ||
      ((Product.category === "Office") && (Product.rating >= 4))

  println("=== Compound Queries ===")
  println()
  println(s"affordableElectronics -> ${toSql(affordableElectronics)}")
  println(s"goodDeal              -> ${toSql(goodDeal)}")
  println(s"outOfStock            -> ${toSql(outOfStock)}")
  println(s"complexQuery          -> ${toSql(complexQuery)}")
  println()

  // --- Arithmetic in SQL ---

  val discountedPrice = Product.price * 0.9
  val priceWithTax    = Product.price * 1.08

  println("=== Arithmetic in SQL ===")
  println()
  println(s"discountedPrice -> ${toSql(discountedPrice)}")
  println(s"priceWithTax    -> ${toSql(priceWithTax)}")
  println()

  // --- String Operations in SQL ---

  val startsWithL = Product.name.matches("L%")
  val labeledName = Product.name.concat(" [SALE]")
  val nameLength  = Product.name.length

  println("=== String Operations in SQL ===")
  println()
  println(s"startsWithL -> ${toSql(startsWithL)}")
  println(s"labeledName -> ${toSql(labeledName)}")
  println(s"nameLength  -> ${toSql(nameLength)}")
}
