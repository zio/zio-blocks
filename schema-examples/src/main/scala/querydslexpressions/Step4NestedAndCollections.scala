package querydslexpressions

import zio.blocks.schema._

/**
 * Query DSL with Reified Optics — Part 1, Step 4: Nested Structures and
 * Collections
 *
 * Demonstrates querying through nested case classes using composed lenses, and
 * querying across collection elements using traversals.
 *
 * Run with: sbt "examples/runMain
 * querydslexpressions.Step4NestedAndCollections"
 */
object Step4NestedAndCollections extends App {

  // --- Nested Domain Types ---

  case class Address(city: String, country: String)
  object Address {
    implicit val schema: Schema[Address] = Schema.derived
  }

  case class Seller(name: String, address: Address, rating: Double)
  object Seller extends CompanionOptics[Seller] {
    implicit val schema: Schema[Seller] = Schema.derived

    val name: Lens[Seller, String]    = optic(_.name)
    val rating: Lens[Seller, Double]  = optic(_.rating)
    val city: Lens[Seller, String]    = optic(_.address.city)
    val country: Lens[Seller, String] = optic(_.address.country)
  }

  // --- Querying Nested Structures ---

  val localSeller: SchemaExpr[Seller, Boolean] =
    (Seller.city === "Berlin") && (Seller.rating >= 4.0)

  val seller = Seller("TechShop", Address("Berlin", "Germany"), 4.5)

  println("=== Nested Structures ===")
  println()
  println(s"localSeller(seller) = ${localSeller.eval(seller)}")
  println()

  // --- Collection Domain Types ---

  case class LineItem(sku: String, price: Double, quantity: Int)
  object LineItem {
    implicit val schema: Schema[LineItem] = Schema.derived
  }

  case class Order(id: String, items: List[LineItem])
  object Order extends CompanionOptics[Order] {
    implicit val schema: Schema[Order] = Schema.derived

    val id: Lens[Order, String]             = optic(_.id)
    val allPrices: Traversal[Order, Double] = optic(_.items.each.price)
    val allSkus: Traversal[Order, String]   = optic(_.items.each.sku)
  }

  // --- Querying Through Collections ---

  val order = Order(
    "ORD-1",
    List(
      LineItem("SKU-A", 29.99, 2),
      LineItem("SKU-B", 149.99, 1),
      LineItem("SKU-C", 9.99, 5)
    )
  )

  val hasExpensiveItem: SchemaExpr[Order, Boolean] =
    Order.allPrices > 100.0

  println("=== Collections (Traversals) ===")
  println()
  println(s"hasExpensiveItem(order) = ${hasExpensiveItem.eval(order)}")
  println("  ^ One result per element: false, true, false")
}
