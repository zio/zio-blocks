package querydslexpressions

import zio.blocks.schema._

/**
 * Query DSL with Reified Optics — Part 1: Complete Example
 *
 * A complete, self-contained example combining all techniques from the guide:
 * domain definition, equality/comparison queries, boolean logic, arithmetic,
 * string operations, nested structures, and generic filtering.
 *
 * Run with: sbt "examples/runMain querydslexpressions.CompleteQueryDSL"
 */
object CompleteQueryDSL extends App {

  // --- Domain ---

  case class Address(city: String, country: String)
  object Address {
    implicit val schema: Schema[Address] = Schema.derived
  }

  case class Product(
    name: String,
    price: Double,
    category: String,
    inStock: Boolean,
    rating: Int,
    warehouse: Address
  )

  object Product extends CompanionOptics[Product] {
    implicit val schema: Schema[Product] = Schema.derived

    val name: Lens[Product, String]     = optic(_.name)
    val price: Lens[Product, Double]    = optic(_.price)
    val category: Lens[Product, String] = optic(_.category)
    val inStock: Lens[Product, Boolean] = optic(_.inStock)
    val rating: Lens[Product, Int]      = optic(_.rating)
    val city: Lens[Product, String]     = optic(_.warehouse.city)
  }

  // --- Generic query filter ---

  def filter[A](items: List[A], predicate: SchemaExpr[A, Boolean]): List[A] =
    items.filter(item =>
      predicate.eval(item) match {
        case Right(results) => results.forall(_ == true)
        case Left(_)        => false
      }
    )

  // --- Data ---

  val catalog = List(
    Product("Laptop", 999.99, "Electronics", true, 5, Address("Berlin", "Germany")),
    Product("Mouse", 29.99, "Electronics", true, 4, Address("Berlin", "Germany")),
    Product("Pen", 2.50, "Office", true, 3, Address("London", "UK")),
    Product("Monitor", 349.99, "Electronics", false, 5, Address("Berlin", "Germany")),
    Product("Notebook", 5.99, "Office", true, 4, Address("London", "UK"))
  )

  // --- Compose a query ---

  val query =
    (Product.category === "Electronics") &&
      (Product.inStock === true) &&
      (Product.price < 500.0) &&
      (Product.city === "Berlin") &&
      (Product.rating >= 4)

  println("=== Complete Query DSL Example ===")
  println()

  println("Query: in-stock electronics under $500, from Berlin, rated >= 4")
  val results = filter(catalog, query)
  println(s"Results: ${results.map(_.name)}")
  println()

  // --- String operations ---

  val searchQuery = Product.name.matches(".*top$")
  val matches     = filter(catalog, searchQuery)
  println(s"Name matches '.*top$$': ${matches.map(_.name)}")
  println()

  // --- Arithmetic: compute discounted prices ---

  val discounted = Product.price * 0.8
  println("Discounted prices (20% off):")
  catalog.foreach { p =>
    println(s"  ${Product.name.get(p)}: ${discounted.eval(p)}")
  }
}
