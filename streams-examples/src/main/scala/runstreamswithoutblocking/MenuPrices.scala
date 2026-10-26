package runstreamswithoutblocking

import zio.blocks.async._

import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit.MILLISECONDS

final case class OrderLine(item: String, table: Int)

/**
 * A fictional price service for a coffee shop menu. `priceInCents` answers
 * after a 100 ms delay on another thread, the way a remote call would, and
 * fails for an item the menu does not list. Prices are sample data.
 */
object MenuPrices {
  val order: List[OrderLine] = List(
    OrderLine("Espresso", 4),
    OrderLine("Flat White", 7),
    OrderLine("Croissant", 4)
  )

  private val cents: Map[String, Long] = Map(
    "Espresso"   -> 350L,
    "Flat White" -> 480L,
    "Croissant"  -> 420L
  )

  def priceInCents(item: String): Async[Long] =
    Async.fromCompletionStage(
      CompletableFuture.supplyAsync(
        () => cents.getOrElse(item, throw new NoSuchElementException(s"No price for $item")),
        CompletableFuture.delayedExecutor(100, MILLISECONDS)
      )
    )
}
