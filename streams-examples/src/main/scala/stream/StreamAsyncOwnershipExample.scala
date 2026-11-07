package stream

import java.util.concurrent.atomic.AtomicInteger

import zio.blocks.async._
import zio.blocks.chunk.Chunk
import zio.blocks.streams.Stream
import zio.blocks.streams.io.Reader

/**
 * Manual pull and close ownership.
 *
 * `startAsync` hands the reader to the caller, who must drive it and await
 * `close()`. `useReaderAsync` keeps ownership and awaits `close()` on every
 * outcome. An observable finalizer makes the difference visible.
 */
object StreamAsyncOwnershipExample {
  def main(args: Array[String]): Unit = {
    // startAsync: ownership transfers. Forget the `close()` and the finalizer
    // never runs.
    val startFinalized                  = new AtomicInteger
    val reader: Reader.AsyncReader[Int] = source(startFinalized).startAsync.block
    val started: Chunk[Int]             =
      try reader.readAll[Int]().block
      finally reader.close().block
    println(s"startAsync              -> $started, finalizer ran ${startFinalized.get()} time(s)")

    // useReaderAsync on success: ownership is retained, close is awaited.
    val useFinalized     = new AtomicInteger
    val used: Chunk[Int] = source(useFinalized).useReaderAsync(r => r.readAll[Int]()).block
    println(s"useReaderAsync          -> $used, finalizer ran ${useFinalized.get()} time(s)")

    // useReaderAsync on failure: close is awaited just the same.
    val failFinalized                         = new AtomicInteger
    val failed: Either[Throwable, Chunk[Int]] =
      source(failFinalized)
        .useReaderAsync[Chunk[Int]](_ => Async.fail(new RuntimeException("consumer gave up")))
        .either
        .block
    val message = failed.left.map(_.getMessage)
    println(s"useReaderAsync (failing) -> $message, finalizer ran ${failFinalized.get()} time(s)")
  }

  /** A stream carrying an asynchronous finalizer that counts its own runs. */
  private def source(finalized: AtomicInteger): Stream[Nothing, Int] =
    Stream(1, 2, 3).ensuringAsync(Async.succeed {
      finalized.incrementAndGet()
      ()
    })
}
