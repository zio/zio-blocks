package stream

import zio.blocks.async._
import zio.blocks.streams.Stream
import zio.blocks.streams.io.Reader

/**
 * Mixing a synchronous source with an asynchronous operator.
 *
 * Adding an asynchronous stage changes no static type and requires no
 * annotation. The description is still `Stream[Nothing, Int]`; what changes is
 * the reader it compiles to, and that happens once, at materialization.
 */
object StreamMixedKindExample {
  def main(args: Array[String]): Unit = {
    // Entirely synchronous: a synchronous reader plus a synchronous callback.
    val syncOnly: Stream[Nothing, Int] =
      Stream.fromReader[Nothing, Int](Reader.fromIterable(List(1, 2, 3, 4, 5))).map(_ * 10)

    // The same source with one asynchronous stage appended. Note that the
    // annotation on the left is identical.
    val mixed: Stream[Nothing, Int] =
      syncOnly.filterAsync(i => Async.succeed(i > 20)).mapAsync(i => Async.succeed(i + 1))

    println(s"syncOnly -> ${syncOnly.runCollectAsync.block}")
    println(s"mixed    -> ${mixed.runCollectAsync.block}")

    // The JVM blocking terminal accepts the mixed graph too: the asynchronous
    // reader is converted back at the final boundary.
    println(s"mixed (blocking terminal) -> ${mixed.runCollect}")
  }
}
