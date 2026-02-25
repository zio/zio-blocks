package querydslexpressions

import zio.blocks.schema._

/**
 * Query DSL with Reified Optics — Part 1, Step 2: Boolean Logic
 *
 * Demonstrates combining queries with && (and), || (or), and ! (not) to build
 * complex compound predicates.
 *
 * Run with: sbt "examples/runMain querydslexpressions.Step2BooleanLogic"
 */
object Step2BooleanLogic extends App {

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

    val name: Lens[Product, String]     = optic(_.name)
    val price: Lens[Product, Double]    = optic(_.price)
    val category: Lens[Product, String] = optic(_.category)
    val inStock: Lens[Product, Boolean] = optic(_.inStock)
    val rating: Lens[Product, Int]      = optic(_.rating)
  }

  // --- Boolean Combinators ---

  // AND: electronics under $500
  val affordableElectronics: SchemaExpr[Product, Boolean] =
    (Product.category === "Electronics") && (Product.price < 500.0)

  // OR: either cheap or highly rated
  val goodDeal: SchemaExpr[Product, Boolean] =
    (Product.price < 10.0) || (Product.rating >= 5)

  // NOT: items that are out of stock
  val outOfStock: SchemaExpr[Product, Boolean] =
    !Product.inStock

  // Complex compound query
  val complexQuery: SchemaExpr[Product, Boolean] =
    ((Product.category === "Electronics") && (Product.price < 500.0)) ||
      ((Product.category === "Office") && (Product.rating >= 4))

  // --- Evaluate ---

  val laptop   = Product("Laptop", 999.99, "Electronics", true, 5)
  val mouse    = Product("Mouse", 29.99, "Electronics", true, 4)
  val pen      = Product("Pen", 2.50, "Office", true, 3)
  val notebook = Product("Notebook", 5.99, "Office", true, 4)

  println("=== Boolean Logic ===")
  println()

  println("affordableElectronics:")
  println(s"  laptop   = ${affordableElectronics.eval(laptop)}")
  println(s"  mouse    = ${affordableElectronics.eval(mouse)}")
  println(s"  pen      = ${affordableElectronics.eval(pen)}")
  println()

  println("goodDeal:")
  println(s"  laptop   = ${goodDeal.eval(laptop)}")
  println(s"  pen      = ${goodDeal.eval(pen)}")
  println()

  println("outOfStock:")
  println(s"  laptop   = ${outOfStock.eval(laptop)}")
  println()

  println("complexQuery (electronics < $500 OR office rated >= 4):")
  println(s"  laptop   = ${complexQuery.eval(laptop)}")
  println(s"  mouse    = ${complexQuery.eval(mouse)}")
  println(s"  pen      = ${complexQuery.eval(pen)}")
  println(s"  notebook = ${complexQuery.eval(notebook)}")
}
