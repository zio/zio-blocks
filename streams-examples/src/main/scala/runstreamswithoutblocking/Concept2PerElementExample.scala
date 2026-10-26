package runstreamswithoutblocking

import zio.blocks.async._
import zio.blocks.streams.Stream

/**
 * How to Run Streams Without Blocking — Do Async Work for Each Element
 *
 * Totals the price of every line of an order with `runFoldAsync`, and prints
 * each price as it arrives with `runForeachAsync`. The step function returns an
 * `Async`, so no thread waits for a price.
 *
 * Run with: cd streams-examples && sbt "runMain
 * runstreamswithoutblocking.Concept2PerElementExample"
 */
object Concept2PerElementExample {
  def main(args: Array[String]): Unit = {
    val order: Stream[Nothing, OrderLine] = Stream.fromIterator(MenuPrices.order.iterator)

    val printed: Async[Either[Nothing, Unit]] =
      order.runForeachAsync { line =>
        MenuPrices.priceInCents(line.item).map(cents => println(s"${line.item} for table ${line.table}: $cents cents"))
      }

    val total: Async[Either[Nothing, Long]] =
      order.runFoldAsync(0L)((sum, line) => MenuPrices.priceInCents(line.item).map(sum + _))

    println(s"printed: ${printed.block}")
    println(s"total: ${total.block} cents")
  }
}
