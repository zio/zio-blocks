package runstreamswithoutblocking

import zio.blocks.async._
import zio.blocks.streams.Stream

/**
 * How to Run Streams Without Blocking — Putting It Together
 *
 * One pipeline written against a price lookup that returns an `Async`: it
 * prints a line per order line and totals the order without ever parking a
 * thread. `main` blocks once, at the very end.
 *
 * Run with: cd streams-examples && sbt "runMain
 * runstreamswithoutblocking.CompleteExample"
 */
object CompleteExample {
  type PriceLookup = String => Async[Long]

  def receipt(order: List[OrderLine], price: PriceLookup): Async[Either[Nothing, Long]] = {
    val lines: Stream[Nothing, OrderLine] = Stream.fromIterator(order.iterator)

    val printed: Async[Either[Nothing, Unit]] =
      lines.runForeachAsync { line =>
        price(line.item).map(cents => println(s"${line.item} for table ${line.table}: $cents cents"))
      }

    val total: Async[Either[Nothing, Long]] =
      lines.runFoldAsync(0L)((sum, line) => price(line.item).map(sum + _))

    printed.flatMap(_ => total)
  }

  def main(args: Array[String]): Unit = {
    val ok: Async[Either[Nothing, Long]] = receipt(MenuPrices.order, MenuPrices.priceInCents)
    println(s"Total for the order: ${ok.block} cents")

    // An item the menu does not list fails the Async itself, so `either` is the way to look at it.
    val unlisted = OrderLine("Cold Brew", 9)
    val failing  = receipt(MenuPrices.order :+ unlisted, MenuPrices.priceInCents)
    println(s"With an unlisted item: ${failing.either.block}")
  }
}
