//> using scala 3.9.0
//> using dep dev.zio::zio-blocks-maybe:0.0.56
//> using options -experimental
//> using file Allocation.scala
// Run from this folder:      scala-cli run Step6MaybeExample.scala
// Or from schema-examples:   sbt "runMain avoidallocation.Step6MaybeExample"

package avoidallocation

import zio.blocks.maybe._

/**
 * How to Avoid Allocation on Hot Paths — Replace Option With Maybe for Per-Tick
 * State
 *
 * Writes the last trade of every tick into an `Option` field and a `Maybe`
 * field, and shows that a `Maybe[Long]` still boxes its value.
 */
object Step6MaybeExample {

  final case class Tick(symbol: String, priceMicros: Long, size: Int)

  val tickCount           = 10000
  val prices: Array[Long] = Array.tabulate(tickCount)(i => 187000000L + (i % 997) * 1300L)
  val sizes: Array[Int]   = Array.tabulate(tickCount)(i => 100 + i % 50)
  val ticks: Array[Tick]  = Array.tabulate(tickCount)(i => Tick("ACME", prices(i), sizes(i)))

  final class LastTrade {
    var tick: Maybe[Tick] = Maybe.absent

    def onTick(t: Tick): Unit = tick = Maybe(t)

    def lastPriceMicros: Long = tick.fold(0L)(_.priceMicros)
  }

  final class OptionLastTrade { var tick: Option[Tick] = None }

  // The primitive case: the price itself in each kind of field
  final class OptionLastPrice { var price: Option[Long] = None        }
  final class MaybeLastPrice  { var price: Maybe[Long] = Maybe.absent }

  def main(args: Array[String]): Unit = {
    val lastTrade = new LastTrade
    ticks.foreach(lastTrade.onTick)
    println(s"last price: ${lastTrade.lastPriceMicros}")

    val optionTrade = new OptionLastTrade
    val maybeTrade  = new LastTrade

    val optionBytes = Allocation.bytesPerRun {
      var i = 0
      while (i < tickCount) { optionTrade.tick = Some(ticks(i)); i += 1 }
      optionTrade
    }

    val maybeBytes = Allocation.bytesPerRun {
      var i = 0
      while (i < tickCount) { maybeTrade.onTick(ticks(i)); i += 1 }
      maybeTrade
    }

    println(s"Option field: ${optionBytes.toDouble / tickCount} bytes per tick")
    println(s"Maybe field:  ${maybeBytes.toDouble / tickCount} bytes per tick")

    val optionPrice = new OptionLastPrice
    val maybePrice  = new MaybeLastPrice

    val optionPriceBytes = Allocation.bytesPerRun {
      var i = 0
      while (i < tickCount) { optionPrice.price = Some(prices(i)); i += 1 }
      optionPrice
    }

    val maybePriceBytes = Allocation.bytesPerRun {
      var i = 0
      while (i < tickCount) { maybePrice.price = Maybe(prices(i)); i += 1 }
      maybePrice
    }

    println(s"Option[Long] field: ${optionPriceBytes.toDouble / tickCount} bytes per tick")
    println(s"Maybe[Long] field:  ${maybePriceBytes.toDouble / tickCount} bytes per tick")
  }
}
