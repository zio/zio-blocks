package zio.blocks.streams.negative

import zio.blocks.async.Async
import zio.blocks.streams._
import zio.blocks.streams.io._

/**
 * Scala 2 negative-compilation fixture. It is intentionally outside the normal
 * test source directories. The async-readers verification command adds this
 * directory to `streamsJS / Test / unmanagedSourceDirectories` and requires
 * compilation to reject every blocking API reference below.
 */
object BlockingApiPlacement {
  val reader: Reader.AsyncReader[Int] = Reader.singleInt(1).toAsync

  val syncReader = reader.toSync
  val run        = Stream(1).run(Sink.count)
  val runCollect = Stream(1).runCollect
  val start      = Stream(1).start
  val create     = Sink.create[Int, Unit](_ => ())

  val concatWriter = Writer.single[Int].concatAsync(() => Async.succeed(Writer.single[Int]))
  val mappedWriter = Writer.single[Int].contramapAsync[String](value => Async.succeed(value.length))
}
