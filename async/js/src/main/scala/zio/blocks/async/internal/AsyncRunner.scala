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

import zio.blocks.async.{Async, AsyncEncoding, Completer, Failure, Pollable}

/**
 * Scala.js implementation of [[Async.start]].
 *
 * Suspended values are driven by a microtask polling loop (the same pattern as
 * `AsyncInterop.drive`). Cancellation suppresses publishing a terminal value.
 */
private[async] object AsyncRunner {

  def start[A](fa: Async[A]): Async.Running[A] = {
    val any = fa.asInstanceOf[Any]
    if (any.isInstanceOf[Failure])
      new CompletedRunning[A](any)
    else if (AsyncEncoding.isSuspended(any)) {
      val run = new SuspendedRunning[A]
      run.drive(any.asInstanceOf[Pollable[A]])
      run
    } else if (any.isInstanceOf[AsyncEncoding.WrappedPollable]) {
      // A depth-1 ready success whose value is the user pollable itself: drive
      // it for its effects via microtasks — `start` cannot block on JS — and
      // settle to the pollable-as-value carrier. A deeper carrier (nested
      // `succeed`) is already settled as-is; publishing it unchanged preserves
      // nesting depth so post-`start` unwrapping agrees with the unstarted
      // value.
      val w = any.asInstanceOf[AsyncEncoding.WrappedPollable]
      if (w.depth > 1 || w.value.isInstanceOf[Failure])
        new CompletedRunning[A](any)
      else {
        val run = new SuspendedRunning[A]
        run.drive(Async.slowPath.observe(w.value.asInstanceOf[Pollable[A]], w.value.asInstanceOf[A]))
        run
      }
    } else
      new CompletedRunning[A](any) // plain ready value: already settled
  }

  def startEval[A](body: => A): Async.Running[A] = {
    val completer = new Completer[A]
    val running   = start(completer.peek)
    js.Promise
      .resolve[Unit](())
      .toFuture
      .onComplete { _ =>
        try completer.succeed(body)
        catch { case t: Throwable => completer.fail(t) }
      }(scala.scalajs.concurrent.JSExecutionContext.queue)
    running
  }

  private final class SuspendedRunning[A] extends Async.Running[A] {

    private implicit val ec: ExecutionContext = scala.scalajs.concurrent.JSExecutionContext.queue

    private var terminal: Any = null
    private var hasTerminal   = false
    private var cancelled     = false
    private var settled       = false
    private var waiters       = List.empty[Runnable]

    private var current: Pollable[A] = null

    private val onComplete = new Runnable {
      def run(): Unit =
        if (!cancelled)
          js.Promise
            .resolve[Unit](())
            .toFuture
            .onComplete(_ => if (!cancelled) step())
    }

    def drive(pa: Pollable[A]): Unit = {
      if (cancelled) return
      current = pa
      step()
    }

    private def step(): Unit = {
      if (settled) return
      val next =
        try current.poll(onComplete)
        catch { case t: Throwable => complete(new Failure(Failure.unwindCause(t))); return }
      val nany = next.asInstanceOf[Any]
      if (nany.isInstanceOf[Failure]) complete(nany)
      else if (nany.isInstanceOf[Pollable[_]])
        current = nany.asInstanceOf[Pollable[A]]
      else complete(nany) // store the raw terminal encoding (preserves pollable-as-value)
    }

    private def complete(value: Any): Unit =
      if (!cancelled && !settled) {
        settled = true
        terminal = value
        hasTerminal = true
        val ws = waiters
        waiters = Nil
        // A throwing waker must not starve the remaining waiters.
        ws.foreach { w =>
          try w.run()
          catch { case _: Throwable => () }
        }
      }

    def poll(onComplete: Runnable): Async[A] =
      if (hasTerminal) terminal.asInstanceOf[Async[A]]
      else {
        // Coalesce by identity only: two distinct drivers may register
        // `==`-equal runnables, and each must still be woken.
        if (!waiters.exists(_ eq onComplete)) waiters = onComplete :: waiters
        this
      }

    def cancel(): Unit =
      if (!settled) {
        settled = true
        cancelled = true
      }
  }
}
