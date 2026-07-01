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

import java.util.concurrent.atomic.AtomicReference

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
      // A depth-1 ready success whose value is the user pollable itself: drive
      // it for its effects on the background worker — `start` must not block
      // the caller — and settle to the pollable-as-value carrier. A deeper
      // carrier (nested `succeed`) is already settled as-is; publishing it
      // unchanged preserves nesting depth so post-`start` unwrapping agrees
      // with the unstarted value.
      val w = any.asInstanceOf[AsyncEncoding.WrappedPollable]
      if (w.depth > 1 || w.value.isInstanceOf[Failure])
        new CompletedRunning[A](any)
      else {
        val run =
          new SuspendedRunning[A](Async.slowPath.observe(w.value.asInstanceOf[Pollable[A]], w.value.asInstanceOf[A]))
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

  /**
   * Sentinel stored in `terminal` for a run that settles with a raw `null`
   * success value: storing plain `null` would be indistinguishable from "not
   * yet settled", so the publish CAS would silently succeed without ever making
   * `poll` observe completion (mirrors `Completer.NullValue`).
   */
  private val NullTerminal: AnyRef = new AnyRef

  /**
   * Sentinel stored in `terminal` by [[SuspendedRunning.cancel]] to record
   * cancellation in the SAME slot as the settled outcome — folding what used to
   * be a separate `cancelled` AtomicBoolean into the `terminal` reference (one
   * fewer object per `start`). A `terminal` of `Cancelled` means "suppress
   * publishing": the worker's publish CAS `compareAndSet(null, value)` then
   * naturally loses against it, and `poll` treats it as still-pending (a
   * cancelled run never delivers).
   */
  private val Cancelled: AnyRef = new AnyRef

  private final class SuspendedRunning[A](pa: Pollable[A]) extends Async.Running[A] {

    // null = pending; `Cancelled` = cancelled (suppress); anything else = the
    // settled outcome (a value, `NullTerminal`, or a `Failure`).
    private val terminal = new AtomicReference[Any](null)
    private var waiters  = List.empty[Runnable]
    // The waiter-list critical sections (`registerOnComplete` / `wakeAll`)
    // synchronize on `this` rather than a dedicated lock object — one fewer
    // allocation per `start`. They never block while held (only list mutation +
    // non-blocking wakers run inside), so this does not risk Loom carrier
    // pinning; a `ReentrantLock` would only add its `Sync` object here.

    // True once `terminal` holds a real settled outcome (not pending, not
    // cancelled).
    private def settled(t: Any): Boolean = (t != null) && (t.asInstanceOf[AnyRef] ne Cancelled)

    private val worker: Thread = {
      val t = new Thread(new Runnable { def run(): Unit = drive() })
      t.setName("zio-blocks-async-runner")
      t.setDaemon(true)
      t
    }

    def kick(): Unit = worker.start()

    private def drive(): Unit = {
      if (terminal.get().asInstanceOf[AnyRef] eq Cancelled) return
      val value: Any =
        try {
          val v = AsyncEncoding.liftSuccess(Async.slowPath.awaitSuspended(pa))
          if (v == null) NullTerminal else v // a raw null success must still publish
        } catch { case t: Throwable => new Failure(Failure.unwindCause(t)) }
      // CAS fails if `cancel` already won the `terminal` slot — so a cancelled
      // run never publishes or wakes, exactly as before.
      if (terminal.compareAndSet(null, value)) wakeAll()
    }

    def poll(onComplete: Runnable): Async[A] = {
      val t = terminal.get()
      if (settled(t)) (if (t.asInstanceOf[AnyRef] eq NullTerminal) null else t).asInstanceOf[Async[A]]
      else {
        registerOnComplete(onComplete)
        this
      }
    }

    // Single-winner CAS against `drive`'s publish: if cancel wins, the worker's
    // publish loses and is suppressed; if the run already settled, cancel is a
    // clean no-op (no spurious interrupt).
    def cancel(): Unit =
      if (terminal.compareAndSet(null, Cancelled)) worker.interrupt()

    private def registerOnComplete(w: Runnable): Unit = synchronized {
      if (settled(terminal.get())) runWaker(w)
      else waiters = w :: waiters
    }

    private def wakeAll(): Unit = synchronized {
      val ws = waiters
      waiters = Nil
      ws.foreach(runWaker)
    }

    /**
     * A throwing waker must not starve the remaining waiters (or the worker).
     */
    private def runWaker(w: Runnable): Unit =
      try w.run()
      catch { case _: Throwable => () }
  }
}
