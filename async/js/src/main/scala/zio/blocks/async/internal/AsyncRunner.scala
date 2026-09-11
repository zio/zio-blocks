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

import zio.blocks.async.{Async, AsyncEncoding, Completer, Failure, Pollable}

/**
 * Scala.js implementation of [[Async.start]].
 *
 * Suspended values are driven by a microtask polling loop (the same pattern as
 * `AsyncInterop.drive`). Cancellation suppresses publishing a terminal value
 * and signals the active pending pollable.
 */
private[async] object AsyncRunner {

  def start[A](fa: Async[A]): Async.Running[A] =
    startRegisteredOwned(fa, PlatformAsync.currentExecutionOwner)(_ => ())

  def startRegistered[A](fa: Async[A])(register: Async.Running[A] => Unit): Async.Running[A] =
    startRegisteredOwned(fa, PlatformAsync.currentExecutionOwner)(register)

  def startRegisteredInline[A](fa: Async[A])(register: Async.Running[A] => Unit): Async.Running[A] =
    startRegistered(fa)(register)

  def startRegisteredOwned[A](
    fa: Async[A],
    executionOwner: AnyRef
  )(register: Async.Running[A] => Unit): Async.Running[A] = {
    val cancellationBatch = PlatformAsync.currentCancellationBatch
    val any               = fa.asInstanceOf[Any]
    if (any.isInstanceOf[Failure]) {
      val run = new CompletedRunning[A](any)
      register(run)
      run
    } else if (AsyncEncoding.isSuspended(any)) {
      val run = new SuspendedRunning[A](any.asInstanceOf[Pollable[A]], executionOwner, cancellationBatch)
      register(run)
      run.drive()
      run
    } else if (any.isInstanceOf[AsyncEncoding.WrappedPollable]) {
      // A depth-1 ready success whose value is the user pollable itself: drive
      // it for its effects via microtasks — `start` cannot block on JS — and
      // settle to the pollable-as-value carrier. A deeper carrier (nested
      // `succeed`) is already settled as-is; publishing it unchanged preserves
      // nesting depth so post-`start` unwrapping agrees with the unstarted
      // value.
      val w = any.asInstanceOf[AsyncEncoding.WrappedPollable]
      if (w.depth > 1 || w.value.isInstanceOf[Failure]) {
        val run = new CompletedRunning[A](any)
        register(run)
        run
      } else {
        val run = new SuspendedRunning[A](
          Async.slowPath.observe(w.value.asInstanceOf[Pollable[A]], w.value.asInstanceOf[A]),
          executionOwner,
          cancellationBatch
        )
        register(run)
        run.drive()
        run
      }
    } else {
      val run = new CompletedRunning[A](any)
      register(run)
      run // plain ready value: already settled
    }
  }

  def startEval[A](body: => A): Async.Running[A] = {
    val owner             = PlatformAsync.currentExecutionOwner
    val cancellationBatch = PlatformAsync.currentCancellationBatch
    val completer         = new Completer[A]
    val running           = startRegisteredOwned(completer.peek, owner)(_ => ())
    // Evaluate `body` on the next microtask, scheduled directly onto the JS
    // microtask queue (`execute`, which Scala.js implements as
    // `Promise.resolve().then(...)`), instead of allocating a
    // Promise + Future + `Try` bridge per `start`. Same "evaluate on the next
    // microtask" timing and throw -> `fail` capture.
    scala.scalajs.concurrent.JSExecutionContext.queue.execute(new Runnable {
      def run(): Unit =
        PlatformAsync.withExecutionOwner(owner) {
          PlatformAsync.withCancellationBatch(cancellationBatch) {
            try completer.succeed(body)
            catch { case t: Throwable => completer.fail(t) }
          }
        }
    })
    running
  }

  private final class SuspendedRunning[A](initial: Pollable[A], executionOwner: AnyRef, cancellationBatch: AnyRef)
      extends Async.Running[A] {

    private[blocks] def isDriverThread: Boolean = polling
    private[async] def isDriverAlive: Boolean   = false

    private var terminal: Any    = null
    private var hasTerminal      = false
    private var cancelled        = false
    private var settled          = false
    private var polling          = false
    private var signalled        = false
    private var readyResumptions = 0
    private var yieldScheduled   = false
    private var waiters          = List.empty[Runnable]

    private var current: Pollable[A]                     = initial
    private var cancellationCleanup: CancellationCleanup = null
    private var cancellationEffect: Async[Unit]          = null

    // Re-arm on the next microtask via a cached `Runnable` scheduled directly
    // onto the JS microtask queue (`ec.execute`, which Scala.js implements as
    // `Promise.resolve().then(...)`), instead of allocating a
    // Promise + Future + `Try` bridge (`Promise.resolve().toFuture.onComplete`)
    // on every wakeup. Same "one synchronous chance, then resume on the next
    // microtask" semantics; `cancelled` is still re-checked at both the wakeup
    // and the resumption.
    private def runSignalled(): Unit =
      if (!cancelled && !settled && !polling && signalled) {
        signalled = false
        step()
      }

    private def requestStep(freshWake: Boolean = false): Unit =
      if (!cancelled && !settled && !signalled) {
        if (freshWake) readyResumptions = 0
        signalled = true
        readyResumptions += 1
        yieldScheduled = readyResumptions >= PlatformAsync.ReadyResumptionLimit
        PlatformAsync.schedule(resume, forceMacrotask = yieldScheduled)
      }

    private val resume: Runnable = new Runnable {
      def run(): Unit = {
        if (yieldScheduled) readyResumptions = 0
        yieldScheduled = false
        runSignalled()
      }
    }

    private val onComplete: Runnable = new Runnable {
      def run(): Unit = requestStep(freshWake = !polling)
    }

    def drive(): Unit = {
      if (cancelled) return
      step()
    }

    private def withCancellationContext[B](body: => B): B =
      PlatformAsync.withExecutionOwner(executionOwner) {
        PlatformAsync.withCancellationBatch(cancellationBatch)(body)
      }

    private def step(): Unit = {
      if (settled) return
      polling = true
      var pollFailure: Throwable = null
      val next                   = PlatformAsync.withExecutionOwner(executionOwner) {
        PlatformAsync.withCancellationBatch(cancellationBatch) {
          try current.poll(onComplete)
          catch {
            case t: Throwable =>
              polling = false
              pollFailure = t
              null
          }
        }
      }
      if (pollFailure ne null) {
        complete(new Failure(Failure.unwindCause(pollFailure)))
        val cleanup = cancellationCleanup
        if (cleanup ne null) withCancellationContext(cleanup.noReplacement())
        return
      }
      val nany = next.asInstanceOf[Any]
      if (cancelled) {
        // cancel may run reentrantly from inside poll, before a replacement is
        // returned and installed. Forward the signal to that replacement.
        withCancellationContext {
          if (!nany.isInstanceOf[Failure] && nany.isInstanceOf[Pollable[_]]) {
            val replacement = nany.asInstanceOf[Pollable[A]]
            if (replacement eq current) cancellationCleanup.noReplacement()
            else cancellationCleanup.replacement(replacement)
          } else cancellationCleanup.noReplacement()
        }
      } else if (nany.isInstanceOf[Failure]) complete(nany)
      else if (nany.isInstanceOf[Pollable[_]]) {
        val replacement = nany.asInstanceOf[Pollable[A]]
        if (replacement ne current) {
          current = replacement
          // The replacement has not registered `onComplete` yet and may
          // already be ready. Give it a poll on the next microtask rather than
          // parking forever waiting for a wake it never promised to send.
          requestStep()
        }
      } else complete(nany) // store the raw terminal encoding (preserves pollable-as-value)
      polling = false
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

    def poll(onComplete: Runnable): Async[A] = {
      // A caller polling a JS run may arrive before a queued resume microtask.
      // Help the single-threaded driver make that already-signalled progress;
      // a later queued resume observes `settled` and becomes a no-op.
      if (!hasTerminal && !yieldScheduled) runSignalled()
      if (hasTerminal) terminal.asInstanceOf[Async[A]]
      else {
        // Coalesce by identity only: two distinct drivers may register
        // `==`-equal runnables, and each must still be woken.
        if (!waiters.exists(_ eq onComplete)) waiters = onComplete :: waiters
        this
      }
    }

    override def cancel(): Unit =
      cancel(reportCleanupFailure)

    override private[async] def cancelWithCleanup(): Async[Unit] = {
      if (!settled) {
        settled = true
        cancelled = true
        val joined = new CancellationCleanup
        cancellationCleanup = joined
        cancellationEffect = joined.effect
        withCancellationContext {
          val cleanup =
            try {
              if (current ne null) current.cancelWithCleanup()
              else Async.succeed(())
            } catch { case t: Throwable => Async.fail(t) }
          joined.primary(cleanup)
          if (!polling) joined.noReplacement()
        }
      }
      if (cancellationEffect == null) Async.succeed(()) else cancellationEffect
    }

    def cancel(onCleanupFailure: Throwable => Unit): Unit =
      if (!settled) {
        settled = true
        cancelled = true
        val joined = new CancellationCleanup
        cancellationCleanup = joined
        cancellationEffect = joined.effect
        withCancellationContext {
          val cleanup =
            try {
              if (current ne null) current.cancelWithCleanup()
              else Async.succeed(())
            } catch { case t: Throwable => Async.fail(t) }
          joined.primary(cleanup)
          if (!polling) joined.noReplacement()
        }
        driveCleanup(joined.effect, onCleanupFailure, executionOwner, cancellationBatch)
      }
  }

  private def driveCleanup(
    cleanup: Async[Unit],
    reportFailure: Throwable => Unit,
    owner: AnyRef,
    cancellationBatch: AnyRef
  ): Unit = {
    val running = PlatformAsync.withCancellationBatch(cancellationBatch) {
      startRegisteredOwned(cleanup, owner)(_ => ())
    }
    lazy val observe: Runnable = new Runnable {
      def run(): Unit = {
        val next = running.poll(observe)
        if (next.isInstanceOf[Failure]) reportFailure(next.asInstanceOf[Failure].cause)
      }
    }
    observe.run()
  }

  private def reportCleanupFailure(cause: Throwable): Unit =
    scala.scalajs.concurrent.JSExecutionContext.queue.reportFailure(
      if (cause eq null) Failure.NullCauseMarker else cause
    )
}
