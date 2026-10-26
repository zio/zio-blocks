package runstreamswithoutblocking

import zio.blocks.async._
import zio.blocks.streams.Stream

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

/**
 * How to Run Streams Without Blocking — Use the Result Without Waiting
 *
 * Composes the result of a terminal with `map`, hands it to code that expects a
 * `Future`, and blocks only at the end of `main`.
 *
 * Run with: cd streams-examples && sbt "runMain
 * runstreamswithoutblocking.Concept3UsingResultExample"
 */
object Concept3UsingResultExample {
  def totalInCents(items: Stream[Nothing, String]): Async[Either[Nothing, Long]] =
    items.runFoldAsync(0L)((sum, item) => MenuPrices.priceInCents(item).map(sum + _))

  def main(args: Array[String]): Unit = {
    val items: Stream[Nothing, String] = Stream.fromIterator(MenuPrices.order.iterator.map(_.item))

    // `map` on the Async: no thread waits, the function runs when the total is ready.
    val label: Async[Either[Nothing, String]] =
      totalInCents(items).map(_.map(cents => f"The order costs ${cents / 100.0}%.2f"))

    // Hand the same Async to code that wants a Future.
    val future = label.toFuture
    println(s"Future completed yet: ${future.isCompleted}")

    // The one blocking call: the end of main, which has nothing left to do.
    println(Await.result(future, 5.seconds))
    println(label.block)
  }
}
