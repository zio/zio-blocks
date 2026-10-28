package effectintegration

import scala.concurrent.ExecutionContext
import zio._
import zio.blocks.async._
import Cancellable.cancellable

object ZioAsync {

  // ZIO Blocks Async -> ZIO: each run builds the Async; interrupting the fiber cancels it.
  def fromAsync[A](async: => Async[A]): Task[A] =
    ZIO.asyncInterrupt[Any, Throwable, A] { callback =>
      val running = async.foldCause(e => callback(ZIO.fail(e)))(a => callback(ZIO.succeed(a))).start
      Left(ZIO.succeed(running.cancel()))
    }

  // ZIO -> ZIO Blocks Async: starts the effect now; cancelling the Async interrupts its fiber.
  def toAsync[A](task: Task[A])(implicit runtime: Runtime[Any]): Async[A] =
    cancellable[A] { completer =>
      val future                        = Unsafe.unsafe(implicit unsafe => runtime.unsafe.runToFuture(task))
      implicit val ec: ExecutionContext = ExecutionContext.parasitic
      future.onComplete(_.fold(completer.fail, completer.succeed))
      () => { future.cancel(); () }
    }
}
