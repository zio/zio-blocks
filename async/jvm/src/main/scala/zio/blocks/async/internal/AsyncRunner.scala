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

import java.util.concurrent.atomic.{AtomicInteger, AtomicLong, AtomicReference}

import zio.blocks.async.{Async, AsyncEncoding, Completer, Failure, Pollable}

/**
 * JVM implementation of [[Async.start]].
 *
 * Ready values settle synchronously. Suspended values are driven by serialized
 * tasks on the shared scheduler; a task exits whenever its current pollable is
 * still pending and its registered waker submits the next task. Cancellation
 * suppresses terminal publication and signals the active pending pollable.
 */
private[async] object AsyncRunner {
  def start[A](fa: Async[A]): Async.Running[A] =
    startRegisteredOwned(fa, PlatformAsync.currentExecutionOwner)(_ => ())

  def startRegistered[A](fa: Async[A])(register: Async.Running[A] => Unit): Async.Running[A] =
    startRegisteredOwned(fa, PlatformAsync.currentExecutionOwner)(register)

  def startRegisteredInline[A](fa: Async[A])(register: Async.Running[A] => Unit): Async.Running[A] =
    startRegisteredOwned0(fa, PlatformAsync.currentExecutionOwner, driveInline = true)(register)

  def startRegisteredOwned[A](
    fa: Async[A],
    executionOwner: AnyRef
  )(register: Async.Running[A] => Unit): Async.Running[A] =
    startRegisteredOwned0(fa, executionOwner, driveInline = false)(register)

  private def startRegisteredOwned0[A](
    fa: Async[A],
    executionOwner: AnyRef,
    driveInline: Boolean
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
      if (driveInline) run.kickInline() else run.kick()
      run
    } else if (any.isInstanceOf[AsyncEncoding.WrappedPollable]) {
      // A depth-1 ready success whose value is the user pollable itself: drive
      // it for its effects on the background worker — `start` must not block
      // the caller — and settle to the pollable-as-value carrier. A deeper
      // carrier (nested `succeed`) is already settled as-is; publishing it
      // unchanged preserves nesting depth so post-`start` unwrapping agrees
      // with the unstarted value.
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
        if (driveInline) run.kickInline() else run.kick()
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
    val worker            = new Thread(new Runnable {
      def run(): Unit =
        PlatformAsync.withExecutionOwner(owner) {
          PlatformAsync.withCancellationBatch(cancellationBatch) {
            try completer.succeed(body)
            catch { case t: Throwable => completer.fail(t) }
          }
        }
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
   * publishing": the driver's publish CAS `compareAndSet(null, value)` then
   * naturally loses against it, and `poll` treats it as still-pending (a
   * cancelled run never delivers).
   */
  private val Cancelled: AnyRef = new AnyRef

  /** Sentinel in `active` after cancellation has claimed its pending leaf. */
  private val ActiveCancelled: AnyRef = new AnyRef

  private final class SuspendedRunning[A](initial: Pollable[A], executionOwner: AnyRef, cancellationBatch: AnyRef)
      extends Async.Running[A]
      with Runnable {

    private[blocks] def isDriverThread: Boolean = Thread.currentThread() eq driverThread
    private[async] def isDriverAlive: Boolean   = started && (terminal.get() == null)

    // null = pending; `Cancelled` = cancelled (suppress); anything else = the
    // settled outcome (a value, `NullTerminal`, or a `Failure`).
    private val terminal = new AtomicReference[Any](null)
    // The pollable currently owned by the driver. Unlike the former local
    // await-loop cursor, this slot lets cancel signal replacement leaves and
    // closes the cancel-during-poll handoff race.
    private val active                                             = new AtomicReference[AnyRef](initial)
    private val polling                                            = new AtomicLong(0L)
    private val pollingSequence                                    = new AtomicLong(0L)
    private val work                                               = new AtomicInteger(0)
    private val waker                                              = new Runnable { def run(): Unit = requestDrive() }
    @volatile private var cancellationCleanup: CancellationCleanup = null
    @volatile private var cancellationEffect: Async[Unit]          = null
    @volatile private var driverThread: Thread                     = null
    @volatile private var started                                  = false
    private val cancellationClaimLock                              = new AnyRef
    private var primaryWaiter: Runnable                            = null
    private var waiters                                            = List.empty[Runnable]
    // The waiter-list critical sections (`registerOnComplete` / `wakeAll`)
    // synchronize on `this` rather than a dedicated lock object — one fewer
    // allocation per `start`. They never block while held (only list mutation +
    // non-blocking wakers run inside), so this does not risk Loom carrier
    // pinning; a `ReentrantLock` would only add its `Sync` object here.

    // True once `terminal` holds a real settled outcome (not pending, not
    // cancelled).
    private def settled(t: Any): Boolean = (t != null) && (t.asInstanceOf[AnyRef] ne Cancelled)

    def kick(): Unit = {
      started = true
      requestDrive()
    }

    def kickInline(): Unit = {
      started = true
      work.set(1)
      run()
    }

    private def withCancellationContext[B](body: => B): B =
      PlatformAsync.withExecutionOwner(executionOwner) {
        PlatformAsync.withCancellationBatch(cancellationBatch)(body)
      }

    private def driveUntilWait(): Unit = {
      if (terminal.get().asInstanceOf[AnyRef] eq Cancelled) return
      try {
        var continue = true
        while (continue) {
          val currentAny = active.get()
          if ((currentAny eq null) || (currentAny eq ActiveCancelled)) {
            // Cancellation can claim the active slot after a poll publishes no
            // replacement but before this loop observes that publication. In
            // that race initializeCancellation saw polling=true and therefore
            // deliberately left the handoff open for the driver. Closing it
            // here is essential: returning directly strands cancelWithCleanup
            // forever even though there is no replacement left to cancel.
            val cleanup = cancellationCleanup
            if (cleanup ne null) withCancellationContext(cleanup.noReplacement())
            return
          }
          val current   = currentAny.asInstanceOf[Pollable[A]]
          val ownership = pollingSequence.incrementAndGet()
          polling.set(ownership)
          if (terminal.get().asInstanceOf[AnyRef] eq Cancelled) {
            finishPoll(ownership, null)
            return
          }
          val next: Async[A] = PlatformAsync.withExecutionOwner(executionOwner) {
            PlatformAsync.withCancellationBatch(cancellationBatch) {
              try current.poll(waker)
              catch { case t: Throwable => new Failure(Failure.unwindCause(t)) }
            }
          }
          val nextAny = next.asInstanceOf[Any]
          if (nextAny.isInstanceOf[Failure]) {
            publish(nextAny)
            finishPoll(ownership, null)
            continue = false
          } else if (nextAny.isInstanceOf[Pollable[?]]) {
            val replacement = nextAny.asInstanceOf[Pollable[A]]
            if (active.compareAndSet(current, replacement)) {
              val cancelled = finishPoll(ownership, null)
              if (cancelled || (replacement eq current)) continue = false
            } else {
              // Cancellation claimed `current` while poll was in flight. It
              // could not see the replacement, so hand the signal forward now.
              finishPoll(ownership, if (replacement eq current) null else replacement)
              continue = false
            }
          } else {
            publish(nextAny)
            finishPoll(ownership, null)
            continue = false
          }
        }
      } catch {
        case t: Throwable =>
          publish(new Failure(Failure.unwindCause(t)))
          val ownership = polling.get()
          if (ownership != 0L) finishPoll(ownership, null)
      }
    }

    private def finishPoll(ownership: Long, replacement: Pollable[A]): Boolean = {
      val cleanup = cancellationClaimLock.synchronized {
        val ownsPoll = polling.compareAndSet(ownership, 0L)
        if (ownsPoll) cancellationCleanup else null
      }
      if (cleanup ne null)
        withCancellationContext {
          if (replacement eq null) cleanup.noReplacement()
          else cleanup.replacement(replacement)
        }
      cleanup ne null
    }

    private def publish(value0: Any): Unit = {
      val value = if (value0 == null) NullTerminal else value0
      // CAS fails if cancel already won. Clear active only after publication
      // wins, so a concurrent winning cancel can always see and signal a leaf.
      if (terminal.compareAndSet(null, value)) {
        active.set(null)
        wakeAll()
      }
    }

    def poll(onComplete: Runnable): Async[A] = {
      val t = terminal.get()
      if (settled(t)) (if (t.asInstanceOf[AnyRef] eq NullTerminal) null else t).asInstanceOf[Async[A]]
      else {
        registerOnComplete(onComplete)
        this
      }
    }

    // Single-winner CAS against the driver's publish: if cancel wins, the
    // publish loses and is suppressed; if the run already settled, cancel is a
    // clean no-op (no spurious interrupt).
    override def cancel(): Unit = {
      val caller  = Thread.currentThread()
      val handler = caller.getUncaughtExceptionHandler
      cancel(cause => reportCleanupFailure(handler, caller, cause))
    }

    private def claimCancellation(): (Async[Unit], Pollable[A], CancellationCleanup, Boolean) =
      cancellationClaimLock.synchronized {
        if (terminal.compareAndSet(null, Cancelled)) {
          val joined = new CancellationCleanup
          cancellationCleanup = joined
          cancellationEffect = joined.effect
          val current = active.getAndSet(ActiveCancelled)
          (joined.effect, current.asInstanceOf[Pollable[A]], joined, polling.get() != 0L)
        } else {
          val effect = cancellationEffect
          (if (effect == null) Async.succeed(()) else effect, null, null, false)
        }
      }

    private def initializeCancellation(
      current: Pollable[A],
      joined: CancellationCleanup,
      pollInFlight: Boolean
    ): Unit =
      withCancellationContext {
        val cleanup =
          try if (current eq null) Async.succeed(()) else current.cancelWithCleanup()
          catch { case t: Throwable => Async.fail(t) }
        joined.primary(cleanup)
        if (!pollInFlight) joined.noReplacement()
      }

    override private[async] def cancelWithCleanup(): Async[Unit] = {
      val (effect, current, joined, pollInFlight) = claimCancellation()
      if (joined ne null) initializeCancellation(current, joined, pollInFlight)
      effect
    }

    def cancel(onCleanupFailure: Throwable => Unit): Unit = {
      val (effect, current, joined, pollInFlight) = claimCancellation()
      if (joined ne null) {
        initializeCancellation(current, joined, pollInFlight)
        driveCleanup(effect, onCleanupFailure, executionOwner, cancellationBatch)
      }
    }

    def run(): Unit = {
      val current = Thread.currentThread()
      driverThread = current
      var missed = 1
      try {
        var continue = true
        while (continue) {
          driveUntilWait()
          missed = work.addAndGet(-missed)
          continue = missed != 0 && terminal.get() == null
        }
      } finally {
        if (driverThread eq current) driverThread = null
        if (terminal.get() != null) work.set(0)
      }
    }

    private def registerOnComplete(w: Runnable): Unit = {
      val runNow = synchronized {
        if (settled(terminal.get())) true
        else {
          if (primaryWaiter eq null) primaryWaiter = w
          else waiters = w :: waiters
          false
        }
      }
      if (runNow) runWaker(w)
    }

    private def requestDrive(): Unit =
      if (work.getAndIncrement() == 0) {
        if (PlatformAsync.isSchedulerThread) run()
        else PlatformAsync.scheduleRaw(this)
      }

    private def wakeAll(): Unit = {
      var primary: Runnable = null
      val ws                = synchronized {
        primary = primaryWaiter
        primaryWaiter = null
        val result = waiters
        waiters = Nil
        result
      }
      ws.foreach(runWaker)
      if (primary ne null) runWaker(primary)
    }

    /**
     * A throwing waker must not starve the remaining waiters.
     */
    private def runWaker(w: Runnable): Unit =
      try w.run()
      catch { case _: Throwable => () }
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

  private def reportCleanupFailure(
    handler: Thread.UncaughtExceptionHandler,
    thread: Thread,
    cause: Throwable
  ): Unit = {
    val reported = if (cause eq null) Failure.NullCauseMarker else cause
    handler.uncaughtException(thread, reported)
  }

}
