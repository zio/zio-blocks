/*
 * Copyright 2024-2026 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
