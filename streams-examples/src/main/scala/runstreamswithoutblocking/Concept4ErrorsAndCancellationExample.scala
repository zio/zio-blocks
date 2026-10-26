package runstreamswithoutblocking

import zio.blocks.async._
import zio.blocks.streams.Stream

import java.util.concurrent.atomic.AtomicInteger

/**
 * How to Run Streams Without Blocking — Errors and Cancellation
 *
 * Shows where a typed error, a defect and a cancel end up, and that cancelling
 * a running terminal stops the pulls and runs the stream's finalizers.
 *
 * Run with: cd streams-examples && sbt "runMain
 * runstreamswithoutblocking.Concept4ErrorsAndCancellationExample"
 */
object Concept4ErrorsAndCancellationExample {
  def main(args: Array[String]): Unit = {
    // A typed error lands in the Either.
    val typed: Stream[String, Long] = Stream.fail("the price service is down")
    println(s"typed error: ${typed.runFoldAsync(0L)((sum, cents) => Async.succeed(sum + cents)).block}")

    // A defect lands in the Async's own failure channel.
    val defect: Stream[Nothing, Book] = Stream.fromIterator(BookPrices.shelf.iterator)
    val unstocked                     = defect.runFoldAsync(0L) { (sum, book) =>
      BookPrices.priceInCents(if (book.title == "Zionomicon") "Zionomicon 2" else book.title).map(sum + _)
    }
    println(s"defect, as an Either: ${unstocked.either.block}")
    println(s"defect, through block: ${scala.util.Try(unstocked.block)}")

    // Cancel a long run: the pulls stop and the finalizer runs.
    val pulls                          = new AtomicInteger
    val finalized                      = new AtomicInteger
    val endless: Stream[Nothing, Long] =
      Stream
        .fromIterator(Iterator.continually(BookPrices.shelf.head))
        .mapAsync { book =>
          pulls.incrementAndGet()
          BookPrices.priceInCents(book.title)
        }
        .ensuring { finalized.incrementAndGet(); () }

    val running = endless.runDrainAsync.start
    Thread.sleep(350)
    running.cancel()
    val pullsAtCancel = pulls.get()
    Thread.sleep(300)
    println(s"pulls at cancel: $pullsAtCancel, pulls 300 ms later: ${pulls.get()}, finalizer ran: ${finalized.get()}")
  }
}
