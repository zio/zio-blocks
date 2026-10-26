package runstreamswithoutblocking

import zio.blocks.async._

import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit.MILLISECONDS

final case class Book(title: String, authors: String)

/**
 * A fictional price service. `priceInCents` answers after a 100 ms delay on
 * another thread, the way a remote call would, and fails for a title the shop
 * does not stock. Prices are sample data.
 */
object BookPrices {
  val shelf: List[Book] = List(
    Book("Zionomicon", "John A. De Goes, Adam Fraser and Milad Khajavi"),
    Book("Programming in Scala", "Martin Odersky, Lex Spoon and Bill Venners"),
    Book("Functional Programming in Scala", "Paul Chiusano and Runar Bjarnason")
  )

  private val cents: Map[String, Long] = Map(
    "Zionomicon"                      -> 4999L,
    "Programming in Scala"            -> 5995L,
    "Functional Programming in Scala" -> 4499L
  )

  def priceInCents(title: String): Async[Long] =
    Async.fromCompletionStage(
      CompletableFuture.supplyAsync(
        () => cents.getOrElse(title, throw new NoSuchElementException(s"No price for $title")),
        CompletableFuture.delayedExecutor(100, MILLISECONDS)
      )
    )
}
