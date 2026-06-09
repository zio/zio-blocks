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

import scala.concurrent.ExecutionContext
import scala.scalajs.js

import zio.blocks.async.{Async, Cancelable, Failure, Pollable, Waker}

/**
 * Scala.js implementation of [[Async.unsafeRunAsync]].
 *
 * JavaScript is single-threaded and cannot block, so a suspended [[Async]] is
 * driven by a microtask polling loop (the same pattern as
 * `AsyncInterop.drive`). The waker re-enters the loop on a
 * `Promise.resolve().then(...)` microtask. Cancellation flips a flag that is
 * checked before scheduling the next microtask, at the top of the loop, and
 * before invoking the callback; because everything runs on the single JS
 * thread, a plain flag is sufficient (no atomics).
 */
private[async] object AsyncRunner {

  def unsafeRunAsync[A](fa: Async[A])(cb: Either[Throwable, A] => Unit): Cancelable = {
    val any = fa.asInstanceOf[Any]
    if (any.isInstanceOf[Failure]) {
      cb(Left(any.asInstanceOf[Failure].cause))
      Cancelable.noop
    } else if (any.isInstanceOf[Pollable[_]]) {
      val run = new Run[A](cb)
      run.drive(fa)
      run
    } else {
      cb(Right(any.asInstanceOf[A]))
      Cancelable.noop
    }
  }

  private final class Run[A](cb: Either[Throwable, A] => Unit) extends Cancelable {

    private implicit val ec: ExecutionContext = scala.scalajs.concurrent.JSExecutionContext.queue

    // Single JS thread: plain flags, no atomics. `terminated` guards
    // at-most-once delivery; `cancelled` short-circuits the loop.
    private var terminated = false
    private var cancelled  = false

    // The latest pollable to drive. `step` advances this to the pollable
    // returned by `poll`, exactly like the JVM driver (`awaitSuspended`) loops
    // on the *returned* pollable rather than re-polling the original. A single
    // shared waker re-enters `step` on the next microtask, so a stale waker
    // captured by an earlier suspension still resumes the current state.
    private var current: Pollable[A] = null

    private val waker = new Waker {
      def wake(): Unit =
        if (!cancelled)
          js.Promise
            .resolve[Unit](())
            .toFuture
            .onComplete(_ => if (!cancelled) step())
    }

    def cancel(): Unit =
      if (!terminated) {
        terminated = true
        cancelled = true
      }

    def drive(fa: Async[A]): Unit = {
      if (cancelled) return
      val any = fa.asInstanceOf[Any]
      if (any.isInstanceOf[Failure]) terminate(Left(any.asInstanceOf[Failure].cause))
      else if (any.isInstanceOf[Pollable[_]]) {
        current = any.asInstanceOf[Pollable[A]]
        step()
      } else terminate(Right(any.asInstanceOf[A]))
    }

    private def step(): Unit = {
      // Guard on `terminated` (set by both completion and `cancel`), not just
      // `cancelled`: a pollable may fire its waker more than once (a legitimate
      // spurious / multi-source wakeup), scheduling several resumption
      // microtasks. Once the run has settled, every later `step` must be a
      // no-op so a completed pollable is never re-polled — matching the JVM
      // `Parker`, which collapses multiple wakeups into one and stops polling
      // after a terminal value.
      if (terminated) return
      val next =
        try current.poll(waker)
        catch { case t: Throwable => terminate(Left(t)); return }
      val nany = next.asInstanceOf[Any]
      if (nany.isInstanceOf[Failure]) terminate(Left(nany.asInstanceOf[Failure].cause))
      else if (nany.isInstanceOf[Pollable[_]])
        current = nany.asInstanceOf[Pollable[A]] // advance; wait for the waker to re-enter
      else terminate(Right(nany.asInstanceOf[A]))
    }

    private def terminate(result: Either[Throwable, A]): Unit =
      if (!terminated) {
        terminated = true
        cb(result)
      }
  }
}
