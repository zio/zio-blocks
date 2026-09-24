package pipeline

import zio.blocks.async.*
import zio.blocks.chunk.Chunk
import zio.blocks.streams.*

/**
 * The three asynchronous `Pipeline` factories — `mapAsync`, `filterAsync` and
 * `collectAsync` — built once as values and then applied through both routes:
 * `stream.via(pipe)` and `pipe.andThenSink(sink)` (with `pipe.applyToSink` as
 * the third spelling of the second route).
 *
 * The point of running both is that they agree. A pipeline is a description,
 * not a stage bound to one side of the graph, so the same value produces the
 * same elements whichever end it is attached to.
 *
 * JVM only, because it ends in `.block` to turn an `Async` into a value for
 * `main`.
 */
object PipelineAsyncExample {

  /** Each field of a raw record, as a trimmed reading. */
  private val readings: Stream[Nothing, String] =
    Stream(" 17 ", "  4", "31  ", " 8 ", "  ", "150")

  /**
   * `mapAsync` — one asynchronous callback per element, awaited before the next
   * element is pulled. It asks for `JvmType.Infer` evidence for `Int`, its
   * result type, and nothing for its input.
   */
  private val parse: Pipeline[String, Int] =
    Pipeline
      .mapAsync[String, String](raw => Async.succeed(raw.trim))
      .andThen(Pipeline.mapAsync[String, Int](t => Async.succeed(if (t.isEmpty) 0 else t.toInt)))

  /** `filterAsync` — type-preserving, so it needs no result evidence. */
  private val inRange: Pipeline[Int, Int] =
    Pipeline.filterAsync[Int](n => Async.succeed(n > 0 && n <= 100))

  /**
   * `collectAsync` — filter and transform in one callback. It returns
   * `Async[Option[B]]` rather than a `PartialFunction`: a `None` drops the
   * element, a `Some` emits its value.
   */
  private val tensDigit: Pipeline[Int, Int] =
    Pipeline.collectAsync[Int, Int](n => Async.succeed(if (n >= 10) Some(n / 10) else None))

  /** One composed value, applied unchanged through both routes below. */
  private val pipe: Pipeline[String, Int] =
    parse.andThen(inRange).andThen(tensDigit)

  def main(args: Array[String]): Unit = {
    val sink: Sink[Nothing, Int, Chunk[Int]] = Sink.collectAll[Int]

    // Route 1 — attach the pipeline to the stream, then run a plain sink.
    val viaStream = readings.via(pipe).runAsync(sink).block

    // Route 2 — attach the pipeline to the sink, then run the plain stream.
    val viaSink = readings.runAsync(pipe.andThenSink(sink)).block

    // The third spelling: `andThenSink` is an alias for `applyToSink`.
    val viaApplyToSink = readings.runAsync(pipe.applyToSink(sink)).block

    report("stream.via(pipe)", viaStream)
    report("pipe.andThenSink(sink)", viaSink)
    report("pipe.applyToSink(sink)", viaApplyToSink)
    println(s"routes agree: ${viaStream == viaSink && viaSink == viaApplyToSink}")
  }

  private def report[E, Z](label: String, result: Either[E, Z]): Unit =
    result match {
      case Right(value) => println(s"$label -> $value")
      case Left(error)  => println(s"$label -> typed error: $error")
    }
}
