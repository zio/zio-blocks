package effectintegration

import cats.effect.IO
import cats.effect.std.{Dispatcher, Queue}
import zio.blocks.streams.Stream

object CatsStreams {

  // ZIO Blocks Stream -> fs2: pulls up to `chunkSize` elements at a time on the blocking pool,
  // and closes the stream when the fs2 stream ends, fails or is cancelled.
  def toFs2[E <: Throwable, A](stream: => Stream[E, A], chunkSize: Int = 256): fs2.Stream[IO, A] =
    fs2.Stream
      .bracket(IO.blocking(BlockingPull(stream)))(pull => IO.blocking(pull.close()))
      .flatMap { pull =>
        fs2.Stream
          .repeatEval(IO.blocking(pull.next(chunkSize)).rethrow)
          .takeWhile(_.nonEmpty)
          .flatMap(chunk => fs2.Stream.iterable(chunk))
      }

  // fs2 -> ZIO Blocks Stream: each run starts the fs2 stream behind a bounded queue, so it
  // runs at most `buffer` elements ahead; closing the run cancels it.
  def fromFs2[A](stream: fs2.Stream[IO, A], buffer: Int = 16)(implicit
    dispatcher: Dispatcher[IO]
  ): Stream[Throwable, A] = {
    // The end of the stream (or its failure) is an element too, so every offer stays cancelable.
    val signals: fs2.Stream[IO, Option[Either[Throwable, A]]] =
      stream.map(a => Some(Right(a))).handleErrorWith(e => fs2.Stream.emit(Some(Left(e)))) ++ fs2.Stream.emit(None)
    val elements = for {
      queue <- cats.effect.Resource.eval(Queue.bounded[IO, Option[Either[Throwable, A]]](buffer))
      _     <- signals.evalMap(queue.offer).compile.drain.background
    } yield Iterator.continually(dispatcher.unsafeRunSync(queue.take)).takeWhile(_.isDefined).flatten
    Stream.fromResource(CatsResources.toResource(elements)) { iterator =>
      Stream.fromIterator(iterator).flatMap(_.fold(Stream.fail(_), Stream.succeed(_)))
    }
  }
}
