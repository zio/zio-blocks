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

package zio.blocks.async.internal

import java.util.concurrent.atomic.AtomicBoolean

import zio.blocks.async.{Async, AsyncSlowPath, Cancelable, Failure, Pollable}

/**
 * JVM implementation of [[Async.unsafeRunAsync]].
 *
 * A ready (or already-failed) [[Async]] is settled synchronously on the caller
 * thread. A suspended [[Async]] is driven on a daemon worker thread that parks
 * between polls (via [[AsyncSlowPath.awaitSuspended]], which uses a
 * Loom-friendly `ReentrantLock` parker). Cancellation flips a single atomic
 * terminal flag and `interrupt()`s the worker so a parked `poll` unparks;
 * whoever flips the flag first wins, giving at-most-once callback delivery.
 */
private[async] object AsyncRunner {

  def unsafeRunAsync[A](fa: Async[A])(cb: Either[Throwable, A] => Unit): Cancelable = {
    val any = fa.asInstanceOf[Any]
    if (any.isInstanceOf[Failure]) {
      cb(Left(any.asInstanceOf[Failure].cause))
      Cancelable.noop
    } else if (any.isInstanceOf[Pollable[_]]) {
      val run = new Run[A](any.asInstanceOf[Pollable[A]], cb)
      run.start()
      run
    } else {
      cb(Right(any.asInstanceOf[A]))
      Cancelable.noop
    }
  }

  private final class Run[A](pa: Pollable[A], cb: Either[Throwable, A] => Unit) extends Cancelable {

    // The single linearization point: whoever CAS-es false -> true decides the
    // outcome. The worker delivers `cb` only if it wins; `cancel()` suppresses
    // `cb` only if it wins.
    private val terminated = new AtomicBoolean(false)

    // Constructed eagerly (not in `start`) so the field is a non-null `val`: a
    // `Cancelable` only escapes to a caller after `unsafeRunAsync` has returned
    // this instance, by which point `worker` is set, so `cancel()` never needs a
    // null guard.
    private val worker: Thread = {
      val t = new Thread(new Runnable {
        def run(): Unit = drive()
      })
      t.setName("zio-blocks-async-runner")
      t.setDaemon(true)
      t
    }

    def start(): Unit = worker.start()

    private def drive(): Unit = {
      // `awaitSuspended` parks between polls and throws on failure (including a
      // thrown `poll`), so success and every error funnel through here. If
      // `cancel()` interrupted us, the resulting throwable's CAS simply loses.
      val result: Either[Throwable, A] =
        try Right(AsyncSlowPath.awaitSuspended(pa))
        catch { case t: Throwable => Left(t) }
      if (terminated.compareAndSet(false, true)) cb(result)
    }

    def cancel(): Unit =
      if (terminated.compareAndSet(false, true)) worker.interrupt()
  }
}
