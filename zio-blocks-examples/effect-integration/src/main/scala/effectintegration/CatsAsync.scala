package effectintegration

import scala.concurrent.ExecutionContext
import cats.effect.IO
import cats.effect.std.Dispatcher
import zio.blocks.async._
import Cancellable.cancellable

object CatsAsync {

  // ZIO Blocks Async -> IO: each run builds the Async; cancelling the fiber cancels it.
  def fromAsync[A](async: => Async[A]): IO[A] =
    IO.async[A] { callback =>
      IO {
        val running = async.foldCause(e => callback(Left(e)))(a => callback(Right(a))).start
        Some(IO(running.cancel()))
      }
    }

  // IO -> ZIO Blocks Async: starts the effect now; cancelling the Async cancels its fiber.
  def toAsync[A](io: IO[A])(implicit dispatcher: Dispatcher[IO]): Async[A] =
    cancellable[A] { completer =>
      val (future, cancel)              = dispatcher.unsafeToFutureCancelable(io)
      implicit val ec: ExecutionContext = ExecutionContext.parasitic
      future.onComplete(_.fold(completer.fail, completer.succeed))
      () => { cancel(); () }
    }
}
