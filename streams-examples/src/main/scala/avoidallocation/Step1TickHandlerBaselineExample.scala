//> using scala 3.9.0
//> using dep dev.zio::zio-blocks-streams:0.0.56
//> using options -experimental
//> using file Allocation.scala
//> using file TickData.scala
// Run from this folder:      scala-cli run Step1TickHandlerBaselineExample.scala
// Or from streams-examples:  sbt "runMain avoidallocation.Step1TickHandlerBaselineExample"

package avoidallocation

import avoidallocation.TickData._

/**
 * How to Avoid Allocation on Hot Paths — The Problem
 *
 * Measures what the plain-Scala tick handler allocates per tick and per price
 * before any ZIO Blocks tool is applied.
 */
object Step1TickHandlerBaselineExample {

  final class TickHandler {
    var lastTick: Option[Tick] = None
    val counts                 = scala.collection.mutable.HashMap.empty[(String, String), Long]

    // Called for every trade
    def onTick(tick: Tick): Unit = {
      lastTick = Some(tick)
      val key = (tick.symbol, "XNAS")
      counts.update(key, counts.getOrElse(key, 0L) + 1L)
    }

    // Called for every batch of prices, in micro-dollars
    def turnoverCents(prices: Array[Long]): Long =
      prices.iterator.map(_ / 10000L).filter(_ > 18750L).foldLeft(0L)(_ + _)
  }

  def main(args: Array[String]): Unit = {
    val handler = new TickHandler

    val onTickBytes = Allocation.bytesPerRun {
      var i = 0
      while (i < tickCount) { handler.onTick(ticks(i)); i += 1 }
      handler
    }
    val turnoverBytes = Allocation.bytesPerRun(handler.turnoverCents(prices))

    println(s"onTick:        ${onTickBytes.toDouble / tickCount} bytes per tick")
    println(s"turnoverCents: ${turnoverBytes.toDouble / tickCount} bytes per price")
    println(s"turnover:      ${handler.turnoverCents(prices)} cents")
  }
}
