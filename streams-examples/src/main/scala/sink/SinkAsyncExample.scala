package sink

import zio.blocks.async._
import zio.blocks.streams._
import zio.blocks.streams.io.Reader

/**
 * A custom sink built with `Sink.createBoth`, plus one of the `*Async`
 * combinators, both driven from the cross-platform asynchronous terminal
 * `Stream#runAsync`.
 *
 * `createBoth` supplies two independent drains. The terminal picks exactly one:
 * `runAsync` takes the asynchronous callback, and the JVM-only blocking `run`
 * takes the synchronous one. Both must agree on what they consume and what they
 * return.
 *
 * JVM only, because it ends in `.block` to turn the `Async` into a value for
 * `main`.
 */
object SinkAsyncExample {

  /**
   * Mean of an `Int` lane. The synchronous callback is a plain `while` loop
   * over `readInt`, which allocates no `Async` per element; the asynchronous
   * callback threads the same accumulator through `flatMap`.
   */
  val meanCelsius: Sink[Nothing, Int, Double] =
    Sink.createBoth[Nothing, Int, Double](
      sync = { (reader: Reader.SyncReader[Int]) =>
        var sum   = 0L
        var count = 0L
        var value = reader.readInt(Long.MinValue)
        while (value != Long.MinValue) {
          sum += value
          count += 1L
          value = reader.readInt(Long.MinValue)
        }
        mean(sum, count)
      },
      async = { (reader: Reader.AsyncReader[Int]) =>
        def loop(sum: Long, count: Long): Async[Double] =
          reader.readInt(Long.MinValue).flatMap { value =>
            if (value == Long.MinValue) Async.succeed(mean(sum, count))
            else loop(sum + value, count + 1L)
          }

        loop(0L, 0L)
      }
    )

  private def mean(sum: Long, count: Long): Double =
    if (count == 0L) 0.0 else sum.toDouble / count.toDouble

  def main(args: Array[String]): Unit = {
    val celsius: Stream[Nothing, Int] = Stream(12, 7, 30, 21, 5)

    // Nothing has run yet: both terminals are descriptions.
    val average: Async[Either[Nothing, Double]] = celsius.runAsync(meanCelsius)

    // `existsAsync` runs at most one predicate effect at a time and stops at
    // the first `true`; 30 matches, so 21 and 5 are never pulled.
    val tooHot: Async[Either[Nothing, Boolean]] =
      celsius.runAsync(Sink.existsAsync[Int](c => Async.succeed(c > 25)))

    // `.block` parks the calling thread until the value arrives. It belongs
    // here, at the edge of a JVM `main`, and nowhere else.
    report("createBoth mean", average.block)
    report("existsAsync (> 25)", tooHot.block)
  }

  private def report[E, Z](label: String, result: Either[E, Z]): Unit =
    result match {
      case Right(value) => println(s"$label -> $value")
      case Left(error)  => println(s"$label -> typed error: $error")
    }
}
