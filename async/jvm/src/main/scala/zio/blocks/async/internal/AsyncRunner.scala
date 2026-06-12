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

import java.util.concurrent.atomic.{AtomicBoolean, AtomicReference}

import zio.blocks.async.{Async, AsyncEncoding, Completer, Failure, Pollable}

/**
 * JVM implementation of [[Async.start]].
 *
 * Ready values settle synchronously. Suspended values are driven on a daemon
 * worker that parks between polls (via [[Async.slowPath.awaitSuspended]]).
 * Cancellation suppresses publishing a terminal value and interrupts the
 * worker.
 */
private[async] object AsyncRunner {

  def start[A](fa: Async[A]): Async.Running[A] = {
    val any = fa.asInstanceOf[Any]
    if (any.isInstanceOf[Failure])
      new CompletedRunning[A](any)
    else if (AsyncEncoding.isSuspended(any)) {
      val run = new SuspendedRunning[A](any.asInstanceOf[Pollable[A]])
      run.kick()
      run
    } else if (any.isInstanceOf[AsyncEncoding.WrappedPollable]) {
      // A ready success whose value is itself a pollable: drive the user
      // pollable for its effects on the background worker — `start` must not
      // block the caller — and settle to the pollable-as-value carrier.
      val inner = any.asInstanceOf[AsyncEncoding.WrappedPollable].value
      if (inner.isInstanceOf[Failure])
        new CompletedRunning[A](any) // a Failure-as-value carrier is already settled
      else {
        val run =
          new SuspendedRunning[A](Async.slowPath.observe(inner.asInstanceOf[Pollable[A]], inner.asInstanceOf[A]))
        run.kick()
        run
      }
    } else
      new CompletedRunning[A](any) // plain ready value: already settled
  }

  def startEval[A](body: => A): Async.Running[A] = {
    val completer = new Completer[A]
    val running   = start(completer.peek)
    val worker    = new Thread(new Runnable {
      def run(): Unit =
        try completer.succeed(body)
        catch { case t: Throwable => completer.fail(t) }
    })
    worker.setName("zio-blocks-async-eval")
    worker.setDaemon(true)
    worker.start()
    running
  }

  private final class SuspendedRunning[A](pa: Pollable[A]) extends Async.Running[A] {

    private val terminal  = new AtomicReference[Any](null)
    private val cancelled = new AtomicBoolean(false)
    private val lock      = new AnyRef
    private var waiters   = List.empty[Runnable]

    private val worker: Thread = {
      val t = new Thread(new Runnable { def run(): Unit = drive() })
      t.setName("zio-blocks-async-runner")
      t.setDaemon(true)
      t
    }

    def kick(): Unit = worker.start()

    private def drive(): Unit = {
      if (cancelled.get()) return
      val value: Any =
        try AsyncEncoding.liftSuccess(Async.slowPath.awaitSuspended(pa))
        catch { case t: Throwable => new Failure(Failure.unwindCause(t)) }
      if (!cancelled.get() && terminal.compareAndSet(null, value)) wakeAll()
    }

    def poll(onComplete: Runnable): Async[A] = {
      val t = terminal.get()
      if (t != null) t.asInstanceOf[Async[A]]
      else {
        registerOnComplete(onComplete)
        this
      }
    }

    def cancel(): Unit =
      if (cancelled.compareAndSet(false, true)) worker.interrupt()

    private def registerOnComplete(w: Runnable): Unit = lock.synchronized {
      if (terminal.get() != null) w.run()
      else waiters = w :: waiters
    }

    private def wakeAll(): Unit = lock.synchronized {
      val ws = waiters
      waiters = Nil
      ws.foreach(_.run())
    }
  }
}
