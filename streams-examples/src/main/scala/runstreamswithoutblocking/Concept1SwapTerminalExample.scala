package runstreamswithoutblocking

import zio.blocks.async._
import zio.blocks.chunk.Chunk
import zio.blocks.streams.Stream

/**
 * How to Run Streams Without Blocking — Swap a Blocking Terminal for Its Async
 * Twin
 *
 * Runs one stream of price lookups with `runCollect` and with
 * `runCollectAsync`, and shows when each call returns.
 *
 * Run with: cd streams-examples && sbt "runMain
 * runstreamswithoutblocking.Concept1SwapTerminalExample"
 */
object Concept1SwapTerminalExample {
  def main(args: Array[String]): Unit = {
    val prices: Stream[Nothing, Long] =
      Stream.fromIterator(BookPrices.shelf.iterator).mapAsync(book => BookPrices.priceInCents(book.title))

    val blockedAt = System.nanoTime()
    val blocking  = prices.runCollect
    println(s"runCollect returned after ${elapsedMillis(blockedAt)} ms with $blocking")

    val startedAt                                    = System.nanoTime()
    val pending: Async[Either[Nothing, Chunk[Long]]] = prices.runCollectAsync
    println(s"runCollectAsync returned after ${elapsedMillis(startedAt)} ms, nothing has run yet")
    println(s"the result is ${pending.block}")
  }

  private def elapsedMillis(since: Long): Long = (System.nanoTime() - since) / 1000000
}
