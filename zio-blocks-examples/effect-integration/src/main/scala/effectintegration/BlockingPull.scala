package effectintegration

import scala.annotation.nowarn
import zio.blocks.chunk.Chunk
import zio.blocks.scope.Scope
import zio.blocks.streams.Stream
import zio.blocks.streams.internal.StreamError
import zio.blocks.streams.io.Reader

// A ZIO Blocks stream opened for pulling from outside, for an effect system's stream.
// Every call blocks, so call it on a blocking pool.
final class BlockingPull[E, A] private (open: Scope.OpenScope, reader: Reader.SyncReader[A]) {
  @volatile private var failed = false

  // Up to `max` elements, or an empty chunk at the end. A pull throws the stream's typed error
  // wrapped in `StreamError`, an internal class; this unwraps it into a `Left`.
  def next(max: Int): Either[E, Chunk[A]] =
    try Right(reader.readUpToN[A](max))
    catch {
      case error: StreamError => failed = true; Left(error.value.asInstanceOf[E])
      case defect: Throwable  => failed = true; throw defect
    }

  // After a failed pull the stream's finalizers have already run, and closing the reader
  // throws that failure again (ZIO Blocks 0.0.56), so close errors are ignored then.
  def close(): Unit = {
    val finalization = open.close()
    if (!failed) finalization.orThrow()
  }
}

object BlockingPull {
  @nowarn // `leak` warns on purpose: the reader leaves the scope, and `close` closes it
  def apply[E, A](stream: Stream[E, A]): BlockingPull[E, A] = {
    val open = Scope.global.open()
    try new BlockingPull[E, A](open, open.scope.leak(stream.start(open.scope)))
    catch { case t: Throwable => open.close(); throw t }
  }
}
