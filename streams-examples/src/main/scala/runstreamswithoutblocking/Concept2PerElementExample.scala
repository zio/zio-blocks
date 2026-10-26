package runstreamswithoutblocking

import zio.blocks.async._
import zio.blocks.streams.Stream

/**
 * How to Run Streams Without Blocking — Do Async Work for Each Element
 *
 * Totals the price of every book on a shelf with `runFoldAsync`, and prints
 * each price as it arrives with `runForeachAsync`. The step function returns an
 * `Async`, so no thread waits for a price.
 *
 * Run with: cd streams-examples && sbt "runMain
 * runstreamswithoutblocking.Concept2PerElementExample"
 */
object Concept2PerElementExample {
  def main(args: Array[String]): Unit = {
    val shelf: Stream[Nothing, Book] = Stream.fromIterator(BookPrices.shelf.iterator)

    val printed: Async[Either[Nothing, Unit]] =
      shelf.runForeachAsync { book =>
        BookPrices.priceInCents(book.title).map(cents => println(s"${book.title} by ${book.authors}: $cents cents"))
      }

    val total: Async[Either[Nothing, Long]] =
      shelf.runFoldAsync(0L)((sum, book) => BookPrices.priceInCents(book.title).map(sum + _))

    println(s"printed: ${printed.block}")
    println(s"total: ${total.block} cents")
  }
}
