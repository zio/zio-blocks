package runstreamswithoutblocking

import zio.blocks.async._
import zio.blocks.streams.Stream

/**
 * How to Run Streams Without Blocking — Putting It Together
 *
 * One pipeline written against a price lookup that returns an `Async`: it
 * prints a line per book and totals the shelf without ever parking a thread.
 * `main` blocks once, at the very end.
 *
 * Run with: cd streams-examples && sbt "runMain
 * runstreamswithoutblocking.CompleteExample"
 */
object CompleteExample {
  type PriceLookup = String => Async[Long]

  def receipt(shelf: List[Book], price: PriceLookup): Async[Either[Nothing, Long]] = {
    val books: Stream[Nothing, Book] = Stream.fromIterator(shelf.iterator)

    val printed: Async[Either[Nothing, Unit]] =
      books.runForeachAsync { book =>
        price(book.title).map(cents => println(s"${book.title} by ${book.authors}: $cents cents"))
      }

    val total: Async[Either[Nothing, Long]] =
      books.runFoldAsync(0L)((sum, book) => price(book.title).map(sum + _))

    printed.flatMap(_ => total)
  }

  def main(args: Array[String]): Unit = {
    val ok: Async[Either[Nothing, Long]] = receipt(BookPrices.shelf, BookPrices.priceInCents)
    println(s"Total for the shelf: ${ok.block} cents")

    // A title the shop does not stock fails the Async itself, so `either` is the way to look at it.
    val unstocked = Book("Zionomicon 2", "Nobody yet")
    val failing   = receipt(BookPrices.shelf :+ unstocked, BookPrices.priceInCents)
    println(s"With an unstocked book: ${failing.either.block}")
  }
}
