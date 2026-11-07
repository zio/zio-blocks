package scope.examples.compileerrors

import zio.blocks.scope._

/**
 * A bookstore service graph that compiles cleanly, built from the fixes in the
 * "How to Fix Common Scope and Wire Compile Errors" guide:
 *
 *   - `Db` needs a `String`, which cannot be auto-created, so the graph
 *     provides it with `Wire(value)`.
 *   - `PriceService` is a trait, so the graph names a concrete implementation
 *     with `Wire.shared[FixedPrices]`.
 *   - `InventoryRepo` and `PriceService` both depend on `Db` instead of on each
 *     other, so there is no dependency cycle.
 *   - The `scoped` block returns a `Quote` case class, which has an `Unscoped`
 *     instance, instead of the scoped `Catalog` itself.
 */

final class Db(url: String) extends AutoCloseable {
  println(s"[Db] connected to $url")

  private val stock = Map("Zionomicon" -> 3, "Programming in Scala" -> 0)
  private val price = Map("Zionomicon" -> BigDecimal("49.00"), "Programming in Scala" -> BigDecimal("59.95"))

  def stockOf(title: String): Int        = stock.getOrElse(title, 0)
  def priceOf(title: String): BigDecimal = price.getOrElse(title, BigDecimal(0))

  def close(): Unit = println("[Db] closed")
}

trait PriceService {
  def priceOf(title: String): BigDecimal
}

final class FixedPrices(db: Db) extends PriceService {
  def priceOf(title: String): BigDecimal = db.priceOf(title)
}

final class InventoryRepo(db: Db) {
  def stockOf(title: String): Int = db.stockOf(title)
}

final class Catalog(inventory: InventoryRepo, prices: PriceService) {
  def quote(title: String): Quote = Quote(title, prices.priceOf(title), inventory.stockOf(title))
}

final case class Quote(title: String, price: BigDecimal, inStock: Int)

@main def bookstoreWiringExample(): Unit = {
  val quote: Quote = Scope.global.scoped { scope =>
    import scope._

    val catalog = allocate(
      Resource.from[Catalog](
        Wire("jdbc:h2:mem:bookstore"), // String cannot be auto-created: provide the value
        Wire.shared[FixedPrices]       // PriceService is a trait: pick an implementation
      )
    )

    $(catalog)(_.quote("Zionomicon"))
  }

  println(quote)
}
