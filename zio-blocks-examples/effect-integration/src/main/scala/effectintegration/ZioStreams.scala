package effectintegration

import zio._
import zio.stream.ZStream
import zio.blocks.streams.Stream

object ZioStreams {

  // ZIO Blocks Stream -> ZStream: pulls up to `chunkSize` elements at a time on the blocking pool,
  // and closes the stream when the ZStream ends, fails or is interrupted.
  def toZStream[E, A](stream: => Stream[E, A], chunkSize: Int = 256): ZStream[Any, E, A] =
    ZStream
      .acquireReleaseWith(ZIO.attemptBlocking(BlockingPull(stream)).orDie)(pull =>
        ZIO.attemptBlocking(pull.close()).orDie
      )
      .flatMap { pull =>
        ZStream.repeatZIOChunkOption {
          ZIO.attemptBlocking(pull.next(chunkSize)).orDie.flatMap {
            case Left(error)                   => ZIO.fail(Some(error))
            case Right(chunk) if chunk.isEmpty => ZIO.fail(None)
            case Right(chunk)                  => ZIO.succeed(Chunk.fromIterable(chunk))
          }
        }
      }

  // ZStream -> ZIO Blocks Stream: each run starts the ZStream; closing the run interrupts it.
  def fromZStream[E, A](zstream: ZStream[Any, E, A])(implicit runtime: Runtime[Any]): Stream[E, A] =
    Stream.fromResource(ZioResources.toResource(zstream.toIterator)) { iterator =>
      Stream.fromIterator(iterator).flatMap(_.fold(Stream.fail(_), Stream.succeed(_)))
    }
}
