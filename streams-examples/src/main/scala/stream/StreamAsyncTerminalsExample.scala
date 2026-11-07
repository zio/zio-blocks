package stream

import zio.blocks.async._
import zio.blocks.chunk.Chunk
import zio.blocks.streams.Stream

/**
 * The cross-platform asynchronous terminal family.
 *
 * Every `*Async` terminal returns `Async[Either[E, Z]]`: the typed error
 * channel stays in the `Either`, and `Async`'s own `Throwable` channel is
 * reserved for defects. `.block` drives the `Async` to its value and belongs
 * only here, at the edge of a JVM `main`.
 */
object StreamAsyncTerminalsExample {
  final case class Reading(sensor: String, celsius: Int)

  def main(args: Array[String]): Unit = {
    val readings: Stream[String, Reading] =
      Stream(Reading("north", 12), Reading("south", 7), Reading("east", 30))

    // One description, three terminals. Nothing has run yet.
    val collected: Async[Either[String, Chunk[Reading]]] = readings.runCollectAsync
    val total: Async[Either[String, Long]]               =
      readings.runFoldAsync(0L)((sum, reading) => Async.succeed(sum + reading.celsius))
    val first: Async[Either[String, Option[Reading]]] = readings.headAsync

    // A stream that fails with a typed error surfaces it as `Left`, not as a
    // failure of the outer `Async`.
    val offline: Stream[String, Reading]              = Stream.fail("west sensor is offline")
    val failed: Async[Either[String, Chunk[Reading]]] = offline.runCollectAsync

    // `.block` parks the calling thread until the value is ready. It is JVM
    // only: on Scala.js it throws, so keep the `Async` and let the host drive it.
    report("runCollectAsync", collected.block.map(_.length))
    report("runFoldAsync", total.block)
    report("headAsync", first.block.map(_.map(_.sensor)))
    report("runCollectAsync (failing)", failed.block.map(_.length))
  }

  private def report[Z](label: String, result: Either[String, Z]): Unit =
    result match {
      case Right(value) => println(s"$label -> $value")
      case Left(error)  => println(s"$label -> typed error: $error")
    }
}
