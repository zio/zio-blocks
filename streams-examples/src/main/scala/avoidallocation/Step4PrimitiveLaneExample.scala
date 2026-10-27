//> using scala 3.9.0
//> using dep dev.zio::zio-blocks-streams:0.0.56
//> using options -experimental
//> using file Allocation.scala
//> using file TickData.scala
// Run from this folder:      scala-cli run Step4PrimitiveLaneExample.scala
// Or from streams-examples:  sbt "runMain avoidallocation.Step4PrimitiveLaneExample"

package avoidallocation

import avoidallocation.TickData._
import zio.blocks.streams._

/**
 * How to Avoid Allocation on Hot Paths — Keep a Stream on a Primitive Lane
 *
 * Compares an `Iterator` pipeline with the same pipeline as a `Stream`, reports
 * which lane each operator leaves the stream on, replaces a zip with an index,
 * and reads a `Long` lane in a custom sink without mistaking a price for the
 * end.
 */
object Step4PrimitiveLaneExample {

  def lane[A]: Sink[Nothing, A, JvmType] = Sink.create[Nothing, A, JvmType](reader => reader.jvmType)

  val highestPrice: Sink[Nothing, Long, Long] = Sink.create[Nothing, Long, Long] { reader =>
    val one     = new Array[Long](1)
    var highest = Long.MinValue
    var n       = reader.readLongs(one, 0, 1)
    while (n >= 0) {
      if (one(0) > highest) highest = one(0)
      n = reader.readLongs(one, 0, 1)
    }
    highest
  }

  val priceChanges = Array(5L, -3L, 0L, 7L)

  val sumUntilMarker: Sink[Nothing, Long, Long] = Sink.create[Nothing, Long, Long] { reader =>
    var sum   = 0L
    var value = reader.readLong(0L)
    while (value != 0L) {
      sum += value
      value = reader.readLong(0L)
    }
    sum
  }

  def main(args: Array[String]): Unit = {
    val iteratorBytes = Allocation.bytesPerRun {
      prices.iterator.map(_ / 10000L).filter(_ > 18750L).foldLeft(0L)(_ + _)
    }

    val streamBytes = Allocation.bytesPerRun {
      Stream.fromArray(prices).map(_ / 10000L).filter(_ > 18750L).runFold(0L)(_ + _)
    }

    println(s"Iterator: ${iteratorBytes.toDouble / tickCount} bytes per tick")
    println(s"Stream:   ${streamBytes.toDouble / tickCount} bytes per tick")

    val viaIterator = prices.iterator.map(_ / 10000L).filter(_ > 18750L).foldLeft(0L)(_ + _)
    val viaStream   = Stream.fromArray(prices).map(_ / 10000L).filter(_ > 18750L).runFold(0L)(_ + _)
    println(s"viaIterator: $viaIterator")
    println(s"viaStream:   $viaStream")

    println(s"map + filter:      ${Stream.fromArray(prices).map(_ / 10000L).filter(_ > 18750L).run(lane)}")
    println(s"map to Double:     ${Stream.fromArray(prices).map(_.toDouble / 1e6).run(lane)}")
    println(s"scan:              ${Stream.fromArray(prices).scan(0L)(_ + _).run(lane)}")
    println(s"&& (zip):          ${(Stream.fromArray(prices) && Stream.fromArray(sizes)).run(lane)}")
    println(s"grouped(100):      ${Stream.fromArray(prices).grouped(100).run(lane)}")

    val zippedBytes = Allocation.bytesPerRun {
      (Stream.fromArray(prices) && Stream.fromArray(sizes)).map { case (p, s) => p * s }.runFold(0L)(_ + _)
    }

    val indexedBytes = Allocation.bytesPerRun {
      Stream.range(0, tickCount).map(i => prices(i) * sizes(i)).runFold(0L)(_ + _)
    }

    println(s"zipped:  ${zippedBytes.toDouble / tickCount} bytes per tick")
    println(s"indexed: ${indexedBytes.toDouble / tickCount} bytes per tick")
    println(
      s"zipped notional:  ${(Stream.fromArray(prices) && Stream.fromArray(sizes)).map { case (p, s) => p * s }.runFold(0L)(_ + _)}"
    )
    println(s"indexed notional: ${Stream.range(0, tickCount).map(i => prices(i) * sizes(i)).runFold(0L)(_ + _)}")

    val sumSinkBytes   = Allocation.bytesPerRun(Stream.fromArray(prices).run(Sink.sumLong))
    val countSinkBytes = Allocation.bytesPerRun(Stream.fromArray(prices).run(Sink.count))
    val runFoldBytes   = Allocation.bytesPerRun(Stream.fromArray(prices).runFold(0L)(_ + _))

    println(s"Sink.sumLong: ${sumSinkBytes.toDouble / tickCount} bytes per tick")
    println(s"Sink.count:   ${countSinkBytes.toDouble / tickCount} bytes per tick")
    println(s"runFold(0L):  ${runFoldBytes.toDouble / tickCount} bytes per tick")
    println(s"count with runFold: ${Stream.fromArray(prices).runFold(0L)((n, _) => n + 1)}")

    println(s"highest price: ${Stream.fromArray(prices).run(highestPrice)}")
    val sinkBytes = Allocation.bytesPerRun(Stream.fromArray(prices).run(highestPrice))
    println(s"highestPrice sink: $sinkBytes bytes per run of $tickCount ticks")

    println(s"sumUntilMarker: ${Stream.fromArray(priceChanges).run(sumUntilMarker)}")
    println(s"Sink.sumLong:   ${Stream.fromArray(priceChanges).run(Sink.sumLong)}")
  }
}
