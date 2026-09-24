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

package zio.blocks.streams

import zio.blocks.async.{Async, Pollable}

private[streams] trait LifecycleWaitPlatform {
  protected final def awaitClose(result: Async[Unit]): Unit =
    foldClose(result, allowPoll = true)

  private def foldClose(result: Async[Unit], allowPoll: Boolean): Unit =
    Async.foldStep(result)(new Async.StepFold[Unit, Unit] {
      def success(value: Unit): Unit                      = value
      def failure(cause: Throwable): Unit                 = throw cause
      override def trustedFailure(cause: Throwable): Unit = throw cause
      def pending(pollable: Pollable[Unit]): Unit         =
        if (allowPoll)
          foldClose(pollable.poll(new Runnable { def run(): Unit = () }), allowPoll = false)
        else throw new IllegalStateException("single-threaded close unexpectedly remained pending")
    })
}

trait StreamPlatformSpecific[+E, +A] { self: Stream[E, A] => }
