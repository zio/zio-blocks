/*
 * A small helper shared by the projection examples.
 *
 * The projection engine processes appended events asynchronously, so a query
 * issued right after an append may not see the result yet. `pollUntil` repeats a
 * query every 20 ms until it returns a value or the timeout passes, which keeps
 * the examples short and deterministic.
 */

package projection.examples

import zio.*

object Polling {

  def pollUntil[A](zio: Task[Option[A]], timeout: Duration = 3.seconds): ZIO[Any, Throwable, Option[A]] =
    for {
      start  <- Clock.currentTime(java.util.concurrent.TimeUnit.MILLISECONDS)
      result <- loopUntil(zio, start + timeout.toMillis)
    } yield result

  private def loopUntil[A](zio: Task[Option[A]], deadline: Long): ZIO[Any, Throwable, Option[A]] =
    for {
      opt <- zio
      now <- Clock.currentTime(java.util.concurrent.TimeUnit.MILLISECONDS)
      res <- if (opt.isDefined) ZIO.succeed(opt)
             else if (now >= deadline) ZIO.succeed(None)
             else ZIO.sleep(20.millis) *> loopUntil(zio, deadline)
    } yield res
}
