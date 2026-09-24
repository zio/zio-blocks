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

package zio.blocks.async

import scala.annotation.tailrec

import zio.blocks.async.internal.{AsyncRunner, CancellationCleanup, PlatformAsync}

/**
 * Constructors for [[Async]] values.
 *
 * An `Async[A]` is created with one of [[succeed]], [[fail]], [[attempt]], or
 * [[promise]] (a bare `A` is not itself an `Async[A]`). The transformation and
 * combination operators (`map`, `flatMap`, `catchAll`, `await`, ...) become
 * available as extension methods on `Async[A]` after importing
 * `zio.blocks.async._`.
 */
object Async extends AsyncCompanionVersionSpecific with AsyncCompanionPlatformSpecific {

  private val DeferredFresh: AnyRef     = new AnyRef
  private val DeferredRunning: AnyRef   = new AnyRef
  private val DeferredCancelled: AnyRef = new AnyRef
  private val MaxInlineBracketPollDepth = 128

  private final class DeferredDone(val value: Any)
  private final class DeferredPublished(val value: Any)

  /**
   * Allocation-free observer used by internal interpreters that need to
   * distinguish one [[Async]] step without blocking or starting a [[Running]].
   * Representation details remain confined to this module.
   */
  private[blocks] trait StepFold[-A, +R] {
    def success(value: A): R
    def failure(cause: Throwable): R

    /**
     * Observe a failure created by a trusted ZIO Blocks internal boundary. The
     * default preserves source and binary compatibility for existing folds,
     * which continue to observe both kinds through [[failure]].
     */
    def trustedFailure(cause: Throwable): R = failure(cause)

    def pending(pollable: Pollable[A]): R
  }

  /**
   * Primitive Double variant of [[StepFold]] for allocation-sensitive internal
   * interpreters.
   */
  private[blocks] trait DoubleStepFold[+R] {
    def successDouble(value: Double): R
    def failureDouble(cause: Throwable): R
    def trustedFailureDouble(cause: Throwable): R = failureDouble(cause)
    def pendingDouble(pollable: Pollable[Double]): R
  }

  /**
   * Primitive Float variant of [[StepFold]] for allocation-sensitive internal
   * interpreters.
   */
  private[blocks] trait FloatStepFold[+R] {
    def successFloat(value: Float): R
    def failureFloat(cause: Throwable): R
    def trustedFailureFloat(cause: Throwable): R = failureFloat(cause)
    def pendingFloat(pollable: Pollable[Float]): R
  }

  /**
   * Primitive Int variant of [[StepFold]] for allocation-sensitive internal
   * interpreters.
   */
  private[blocks] trait IntStepFold[+R] {
    def successInt(value: Int): R
    def failureInt(cause: Throwable): R
    def trustedFailureInt(cause: Throwable): R = failureInt(cause)
    def pendingInt(pollable: Pollable[Int]): R
  }

  /**
   * Primitive Long variant of [[StepFold]] for allocation-sensitive internal
   * interpreters.
   */
  private[blocks] trait LongStepFold[+R] {
    def successLong(value: Long): R
    def failureLong(cause: Throwable): R
    def trustedFailureLong(cause: Throwable): R = failureLong(cause)
    def pendingLong(pollable: Pollable[Long]): R
  }

  /**
   * A "fiber" / green-thread handle: an in-flight [[Async]] that is being
   * driven eagerly, can be [[Cancelable.cancel]]led, and can be
   * [[Pollable.poll]] ed to observe (or join) its outcome. A [[Running]] is
   * itself an `Async[A]`.
   */
  abstract class Running[+A] extends Pollable[A] with Cancelable {

    /** Whether the current thread is this run's active driver thread. */
    private[blocks] def isDriverThread: Boolean

    /** Whether this run's platform driver is still alive. */
    private[async] def isDriverAlive: Boolean

    /**
     * Cancel this run and report a failure from its asynchronous cleanup. The
     * reporter is retained only when this cancellation wins completion and is
     * invoked at most once.
     */
    def cancel(onCleanupFailure: Throwable => Unit): Unit
  }

  /**
   * Lift an already-available value into a successful [[Async]].
   *
   * The value may itself be an `Async` (or a [[Pollable]]): such a value is
   * carried as '''data''', one nesting level at a time — `map`/`flatMap`
   * continuations, `.block`, and the drivers deliver it unwrapped exactly one
   * layer per delivery (`flatten` peels one level), never silently running it
   * as a computation. Sequencing effects is `flatMap`'s job; reach for nesting
   * only when an effect value is genuinely the payload.
   */
  def succeed[A](a: A): Async[A] = AsyncEncoding.liftSuccess(a)

  /**
   * Observe one [[Async]] step without polling, blocking, scheduling, or
   * allocating a [[Running]]. A successful [[Pollable]] payload is delivered as
   * data; only a bare suspended pollable selects [[StepFold.pending]].
   */
  private[blocks] def foldStep[A, R](async: Async[A])(fold: StepFold[A, R]): R = {
    val any: Any = async
    if (any.isInstanceOf[Failure]) {
      val failed = any.asInstanceOf[Failure]
      if (failed.trusted) fold.trustedFailure(failed.cause)
      else fold.failure(failed.cause)
    } else if (any.isInstanceOf[Pollable[?]]) fold.pending(any.asInstanceOf[Pollable[A]])
    else fold.success(AsyncEncoding.deliverSuccess[A](any))
  }

  /**
   * Observes one Double-valued step without boxing the delivered ready value
   * again.
   */
  private[blocks] def foldDoubleStep[R](async: Async[Double])(fold: DoubleStepFold[R]): R = {
    val any: Any = async
    if (any.isInstanceOf[Failure]) {
      val failed = any.asInstanceOf[Failure]
      if (failed.trusted) fold.trustedFailureDouble(failed.cause)
      else fold.failureDouble(failed.cause)
    } else if (any.isInstanceOf[Pollable[?]]) fold.pendingDouble(any.asInstanceOf[Pollable[Double]])
    else fold.successDouble(AsyncEncoding.deliverSuccess[Double](any))
  }

  /**
   * Observes one Float-valued step without boxing the delivered ready value
   * again.
   */
  private[blocks] def foldFloatStep[R](async: Async[Float])(fold: FloatStepFold[R]): R = {
    val any: Any = async
    if (any.isInstanceOf[Failure]) {
      val failed = any.asInstanceOf[Failure]
      if (failed.trusted) fold.trustedFailureFloat(failed.cause)
      else fold.failureFloat(failed.cause)
    } else if (any.isInstanceOf[Pollable[?]]) fold.pendingFloat(any.asInstanceOf[Pollable[Float]])
    else fold.successFloat(AsyncEncoding.deliverSuccess[Float](any))
  }

  /**
   * Observes one Int-valued step without boxing the delivered ready value
   * again.
   */
  private[blocks] def foldIntStep[R](async: Async[Int])(fold: IntStepFold[R]): R = {
    val any: Any = async
    if (any.isInstanceOf[Failure]) {
      val failed = any.asInstanceOf[Failure]
      if (failed.trusted) fold.trustedFailureInt(failed.cause)
      else fold.failureInt(failed.cause)
    } else if (any.isInstanceOf[Pollable[?]]) fold.pendingInt(any.asInstanceOf[Pollable[Int]])
    else fold.successInt(AsyncEncoding.deliverSuccess[Int](any))
  }

  /**
   * Observes one Long-valued step without boxing the delivered ready value
   * again.
   */
  private[blocks] def foldLongStep[R](async: Async[Long])(fold: LongStepFold[R]): R = {
    val any: Any = async
    if (any.isInstanceOf[Failure]) {
      val failed = any.asInstanceOf[Failure]
      if (failed.trusted) fold.trustedFailureLong(failed.cause)
      else fold.failureLong(failed.cause)
    } else if (any.isInstanceOf[Pollable[?]]) fold.pendingLong(any.asInstanceOf[Pollable[Long]])
    else fold.successLong(AsyncEncoding.deliverSuccess[Long](any))
  }

  private[blocks] def stepBoolean(async: Async[Boolean]): Boolean =
    AsyncEncoding.deliverSuccess[Boolean](async)

  private[blocks] def stepCause[A](async: Async[A]): Throwable =
    async.asInstanceOf[Failure].cause

  private[blocks] def stepInt(async: Async[Int]): Int =
    AsyncEncoding.deliverSuccess[Int](async)

  private[blocks] def stepKind[A](async: Async[A]): Int = {
    val any: Any = async
    if (any.isInstanceOf[Failure]) 1
    else if (any.isInstanceOf[Pollable[?]]) 2
    else 0
  }

  private[blocks] def stepLong(async: Async[Long]): Long =
    AsyncEncoding.deliverSuccess[Long](async)

  private[blocks] def stepValue[A](async: Async[A]): A =
    AsyncEncoding.deliverSuccess[A](async)

  private[blocks] def stepTrusted[A](async: Async[A]): Boolean =
    async.asInstanceOf[Failure].trusted

  /**
   * Create a failure in the trusted typed-error channel used by ZIO Blocks
   * interpreters. Public [[fail]] and public [[Failure]] construction remain
   * untrusted; the distinction is observable only through [[foldStep]].
   */
  private[blocks] def failTrusted(cause: Throwable): Async[Nothing] = Failure.trusted(cause)

  /**
   * Schedules one interpreter continuation without invoking it recursively.
   * Scala.js callers set `forceMacrotask` after exhausting their bounded
   * microtask budget so timers, rendering, and I/O receive a turn.
   */
  private[blocks] def schedule(runnable: Runnable, forceMacrotask: Boolean): Unit =
    PlatformAsync.schedule(runnable, forceMacrotask)

  /**
   * Base for library-owned operation handles which need to expose asynchronous
   * cancellation cleanup to an Async driver without making that hook public.
   */
  private[blocks] abstract class Operation[+A] extends Pollable[A] {
    protected def cancelOperation(): Async[Unit]

    final override def cancel(): Unit = {
      val cleanup =
        try cancelOperation()
        catch { case t: Throwable => Async.fail(t) }
      cleanup.start
      ()
    }

    final override private[async] def cancelWithCleanup(): Async[Unit] =
      try cancelOperation()
      catch { case t: Throwable => Async.fail(t) }
  }

  /** Joins cleanup discovered after cancellation races an in-flight poll. */
  private[blocks] final class Cancellation private[Async] () {
    private val underlying = new CancellationCleanup

    def effect: Async[Unit]                          = underlying.effect
    def primary(value: Async[Unit]): Unit            = underlying.primary(value)
    def replacement(value: Pollable[?]): Unit        = underlying.replacement(value)
    def claimedReplacement(value: Async[Unit]): Unit = underlying.claimedReplacement(value)
    def noReplacement(): Unit                        = underlying.noReplacement()
  }

  private[blocks] def cancellation(): Cancellation = new Cancellation

  /**
   * Lazily lift one synchronous operation into an [[Async]]. `thunk` is first
   * evaluated by [[Pollable.poll]], never by construction. If cancellation wins
   * before completion, `onCancel` runs once and the stale result is not
   * published. The hook may wake/close blocking work but does not imply that
   * arbitrary synchronous work is preemptible.
   */
  private[blocks] def deferCancelable[A](thunk: () => A, onCancel: () => Unit): Async[A] =
    new DeferredCancelablePollable[A](
      thunk,
      () => {
        onCancel()
        Async.succeed(())
      }
    )

  /**
   * Lazily lift synchronous work whose cancellation has asynchronous cleanup.
   */
  private[blocks] def deferCancelableWithCleanup[A](
    thunk: () => A,
    onCancel: () => Async[Unit]
  ): Async[A] = new DeferredCancelablePollable[A](thunk, onCancel)

  /**
   * Lazily acquires a caller-owned value. Cancellation that wins while
   * acquisition is in flight joins release of the unpublished value.
   */
  private[blocks] def acquireCancelable[A](acquire: () => A, release: A => Async[Unit]): Async[A] =
    new AcquireCancelablePollable[A](acquire, release)

  /**
   * Defers construction to a fresh scheduler turn and propagates cancellation
   * to the constructed child.
   */
  private[blocks] def reschedule[A](
    thunk: () => Async[A],
    onStarted: () => Unit = () => ()
  ): Async[A] =
    new RescheduledPollable[A](thunk, onStarted)

  /**
   * Defers driving an already-constructed computation to a fresh scheduler
   * turn. Unlike [[reschedule]], cancellation before that turn still belongs to
   * `effect` and therefore signals and joins its cleanup.
   */
  private[blocks] def rescheduleKnown[A](effect: Async[A], onStarted: () => Unit): Async[A] =
    new RescheduledRunningPollable[A](effect, onStarted)

  /** A lazy, cancellation-safe bracket for synchronously acquired resources. */
  private[blocks] def bracketSync[R, A](
    acquire: () => R,
    use: R => Async[A],
    release: R => Async[Unit]
  ): Async[A] = bracketAsync(
    () => {
      try Async.succeed(acquire())
      catch { case t: Throwable => Async.fail(t) }
    },
    use,
    release
  )

  /**
   * A lazy, cancellation-safe bracket for asynchronously acquired resources.
   */
  private[blocks] def bracketAsync[R, A](
    acquire: () => Async[R],
    use: R => Async[A],
    release: R => Async[Unit],
    mapAcquisitionFailure: Throwable => Throwable = identity
  ): Async[A] = new BracketAsyncPollable[R, A](releaseAfterUse = true) {
    protected def acquireResource(): Async[R]                              = acquire()
    override protected def acquisitionFailure(cause: Throwable): Throwable = mapAcquisitionFailure(cause)
    protected def releaseResource(resource: R): Async[Unit]                = release(resource)
    protected def useResource(resource: R): Async[A]                       = use(resource)
  }

  /**
   * Acquires asynchronously, installs the acquired value into an owning result,
   * and transfers cleanup ownership atomically with publication of that result.
   * Cancellation or installation failure before publication releases the
   * acquired value; normal publication transfers release responsibility to the
   * installed result.
   */
  private[blocks] def acquireInstallCancelable[R, A](
    acquire: () => Async[R],
    install: R => A,
    release: R => Async[Unit],
    mapAcquisitionFailure: Throwable => Throwable = identity
  ): Async[A] =
    new BracketAsyncPollable[R, A](releaseAfterUse = false) {
      protected def acquireResource(): Async[R]                              = acquire()
      override protected def acquisitionFailure(cause: Throwable): Throwable = mapAcquisitionFailure(cause)
      protected def releaseResource(resource: R): Async[Unit]                = release(resource)
      protected def useResource(resource: R): Async[A]                       = Async.succeed(install(resource))
    }

  /** Join the cleanup performed when cancellation wins a pending operation. */
  private[blocks] def cancelWithCleanup(pollable: Pollable[_]): Async[Unit] =
    pollable.cancelWithCleanup()

  private[blocks] def currentExecutionOwner: AnyRef   = PlatformAsync.currentExecutionOwner
  private[async] def currentCancellationBatch: AnyRef = PlatformAsync.currentCancellationBatch

  private[blocks] def isExecutionOwner(owner: AnyRef): Boolean =
    (owner ne null) && (PlatformAsync.currentExecutionOwner eq owner)

  private[blocks] def withExecutionOwner[A](owner: AnyRef)(body: => A): A =
    PlatformAsync.withExecutionOwner(owner)(body)

  private[async] def withCancellationBatch[A](batch: AnyRef)(body: => A): A =
    PlatformAsync.withCancellationBatch(batch)(body)

  /**
   * Create a reusable fair selector over `inputs`.
   *
   * A selector owns one running observation of each input. Each successful
   * [[AsyncSelector.select]] removes its winning slot; call
   * [[AsyncSelector.replace]] to arm that slot again. The scan starts after the
   * previous winner, so a continuously-ready slot is selected within at most
   * the number of currently armed slots selections. Shutting down the selector
   * cancels every armed loser and joins all of their cleanup.
   */
  def selector[A](inputs: IndexedSeq[Async[A]]): AsyncSelector[A] =
    AsyncSelector(inputs)

  /**
   * Internal sparse selector used by bounded schedulers with temporarily idle
   * slots.
   */
  private[blocks] def selectorWithCapacity[A](
    size: Int,
    inputs: IndexedSeq[(Int, Async[A])]
  ): AsyncSelector[A] = AsyncSelector.sparse(size, inputs)

  /**
   * Ensure concurrent cancellation signals drive a pending operation's cleanup
   * once.
   */
  private[blocks] def shareCancellation[A](pollable: Pollable[A]): Pollable[A] =
    new SharedCancellationPollable[A](pollable, null)

  private[blocks] def superviseResetUnit(
    reset: () => Async[Unit],
    rejectClose: () => Async[Unit]
  ): Pollable[Unit] = new ResetUnitSupervisor(reset, rejectClose, rejectAfterReset = true)

  /**
   * Supervise a reset nested inside an operation whose enclosing owner performs
   * the final close after cancellation joins that operation. A close may still
   * be needed to unblock synchronous reset construction, but the nested reset
   * must never await the enclosing close owner.
   */
  private[blocks] def superviseResetWithinClose(
    reset: () => Async[Unit],
    unblockClose: () => Async[Unit]
  ): Pollable[Unit] = new ResetUnitSupervisor(reset, unblockClose, rejectAfterReset = false)

  /** Cancel `pollable`, join cleanup, then settle observers to `fallback`. */
  private[blocks] def cancelTo[A](pollable: Pollable[A], fallback: () => Async[A]): Pollable[A] =
    new SharedCancellationPollable[A](pollable, fallback)

  /**
   * Whether a replacement returned by a cancelled in-flight poll still needs an
   * ownership signal from the caller coordinating that poll.
   */
  private[async] def replacementNeedsCancellation(current: Pollable[?], replacement: Pollable[?]): Boolean =
    (replacement ne current) && (current match {
      case provenance: CancelledReplacementProvenance => !provenance.wasCancelledReplacement(replacement)
      case _                                          => true
    })

  private final class SharedCancellationPollable[A](initial: Pollable[A], fallback: () => Async[A])
      extends Pollable[A] {
    private val executionOwner                    = Async.currentExecutionOwner
    private var current: Pollable[A]              = initial
    private var polling: Pollable[A]              = null
    private var cancelled                         = false
    private var cleanup: Completer[Unit]          = null
    private var cancellation: CancellationCleanup = null
    private var cancellationBatch: AnyRef         = null
    private var cancellationResult: Async[A]      = null
    private var cancellationFinished              = false
    private var cancellationWaiters               = List.empty[Runnable]

    private def rememberCancellationWaiter(waiter: Runnable): Unit =
      if ((fallback ne null) && !cancellationWaiters.exists(_ eq waiter))
        cancellationWaiters = waiter :: cancellationWaiters

    private def withCancellationContext[B](body: => B): B =
      Async.withExecutionOwner(executionOwner) {
        Async.withCancellationBatch(synchronized(cancellationBatch))(body)
      }

    def poll(onComplete: Runnable): Async[A] =
      Async.withExecutionOwner(executionOwner)(pollOwned(onComplete))

    private def pollOwned(onComplete: Runnable): Async[A] = {
      while (true) {
        val (hasCancelledState, cancelledState, child) = synchronized {
          if (cancelled && cancellationFinished) (true, cancellationResult, null)
          else if (cancelled) {
            rememberCancellationWaiter(onComplete)
            (true, this, null)
          } else if (current eq null) (false, null.asInstanceOf[Async[A]], null)
          else {
            rememberCancellationWaiter(onComplete)
            polling = current
            (false, null.asInstanceOf[Async[A]], current)
          }
        }
        if (hasCancelledState) return cancelledState
        if (child eq null) return this
        var pollFailure: Throwable = null
        val result                 =
          try child.poll(onComplete)
          catch {
            case cause: Throwable =>
              pollFailure = cause
              Async.fail(cause)
          }
        if (finishPoll(child, result, onComplete)) ()
        else if (pollFailure ne null) throw pollFailure
        else if (result.asInstanceOf[AnyRef] eq null) return null.asInstanceOf[Async[A]]
        else {
          val terminal = foldStep(result)(new StepFold[A, Async[A]] {
            def success(value: A): Async[A]          = Async.succeed(value)
            def failure(cause: Throwable): Async[A]  = Async.fail(cause)
            def pending(next: Pollable[A]): Async[A] =
              if (next eq child) SharedCancellationPollable.this
              else null.asInstanceOf[Async[A]]
          })
          if (terminal.asInstanceOf[AnyRef] ne null) return terminal
          // A distinct replacement is synchronous progress. Continue only
          // after finishPoll has published it as the current child.
        }
      }
      this
    }

    private def finishPoll(child: Pollable[A], result: Async[A], onComplete: Runnable): Boolean = {
      val (joined, replacement, cancelledWon) = synchronized {
        if (polling eq child) polling = null
        val next =
          if (result.isInstanceOf[Pollable[?]] && !result.isInstanceOf[Failure])
            result.asInstanceOf[Pollable[A]]
          else null
        if (cancelled) (cancellation, if ((next ne null) && (next ne child)) next else null, true)
        else {
          current = next
          if (next eq null) cancellationWaiters = cancellationWaiters.filterNot(_ eq onComplete)
          (null, null, false)
        }
      }
      if (joined ne null)
        withCancellationContext {
          if (replacement ne null) joined.replacement(replacement)
          else joined.noReplacement()
        }
      cancelledWon
    }

    override def cancel(): Unit = { cancelWithCleanup().start; () }

    override private[async] def cancelWithCleanup(): Async[Unit] =
      Async.withExecutionOwner(executionOwner)(cancelWithCleanupOwned())

    private def cancelWithCleanupOwned(): Async[Unit] = {
      val claimed = synchronized {
        if (cleanup eq null) {
          cleanup = new Completer[Unit]
          cancelled = true
          cancellationBatch = Async.currentCancellationBatch
          val joined = new CancellationCleanup
          cancellation = joined
          val child = current
          current = null
          (cleanup, child, joined, polling ne null)
        } else null
      }
      if (claimed ne null) {
        val (completion, child, joined, pollInFlight) = claimed
        withCancellationContext {
          val childCleanup =
            try if (child eq null) Async.succeed(()) else child.cancelWithCleanup()
            catch { case cause: Throwable => Async.fail(cause) }
          val primary =
            if (childCleanup.asInstanceOf[AnyRef] eq completion.asInstanceOf[AnyRef]) Async.succeed(())
            else childCleanup
          joined.primary(primary)
          if (!pollInFlight) joined.noReplacement()
          val effect = joined.effect.foldCause { cause =>
            completion.fail(cause)
            finishCancellation(Async.fail(cause))
          } { _ =>
            completion.succeed(())
            if (fallback ne null) {
              val result =
                try fallback()
                catch { case cause: Throwable => Async.fail(cause) }
              finishCancellation(result)
            }
          }
          Async.startRegisteredOwned(effect, executionOwner)(_ => ())
        }
      }
      synchronized(cleanup)
    }

    private def finishCancellation(result: Async[A]): Unit = {
      val waiters = synchronized {
        cancellationResult = result
        cancellationFinished = true
        val pending = cancellationWaiters
        cancellationWaiters = Nil
        pending
      }
      waiters.foreach { waiter =>
        try waiter.run()
        catch { case _: Throwable => () }
      }
    }
  }

  /** A reset-only supervisor: cancellation joins, but never cancels, reset. */
  private final class ResetUnitSupervisor(
    reset0: () => Async[Unit],
    rejectClose0: () => Async[Unit],
    rejectAfterReset: Boolean
  ) extends Pollable[Unit] {
    private val Unstarted                  = new AnyRef
    private val Starting                   = new AnyRef
    private val Terminal                   = new AnyRef
    private var resetState: Any            = Unstarted
    private var closeState: Any            = Unstarted
    private var resetFailure: Failure      = null
    private var closeFailure: Failure      = null
    private var earlyCloseStarted          = false
    private var compensationCloseStarted   = false
    private var cancelled                  = false
    private var cleanup: Pollable[Unit]    = null
    private var cleanupDone                = false
    private var cleanupResult: Async[Unit] = null
    private var driving                    = false
    private var childWaiters               = List.empty[Runnable]
    private var permitWaiters              = List.empty[Runnable]
    private var cancelledWaiters           = List.empty[Runnable]

    private def addWaiter(waiters: List[Runnable], waiter: Runnable): List[Runnable] =
      if (waiters.exists(_ eq waiter)) waiters else waiter :: waiters

    private def wakeAll(waiters: List[Runnable]): Unit =
      waiters.foreach { waiter =>
        try waiter.run()
        catch { case _: Throwable => () }
      }

    private val relay = new Runnable {
      def run(): Unit = {
        val wakes = ResetUnitSupervisor.this.synchronized {
          val current = childWaiters
          childWaiters = Nil
          current
        }
        wakeAll(wakes)
      }
    }

    private def acquire(onComplete: Runnable): Boolean = ResetUnitSupervisor.this.synchronized {
      if (driving) { permitWaiters = addWaiter(permitWaiters, onComplete); false }
      else {
        driving = true
        childWaiters = addWaiter(childWaiters, onComplete)
        true
      }
    }

    private def release(): Unit = {
      val wakes = ResetUnitSupervisor.this.synchronized {
        driving = false
        val contenders = permitWaiters
        permitWaiters = Nil
        contenders
      }
      wakeAll(wakes)
    }

    private def startReset(): Unit = {
      val claimed = ResetUnitSupervisor.this.synchronized {
        if (resetState.asInstanceOf[AnyRef] eq Unstarted) {
          // Publish construction before invoking user code. Cancellation can
          // now observe the in-flight constructor and start the rejection
          // close that may be required to unblock it.
          resetState = Starting
          true
        } else false
      }
      if (claimed) {
        val effect =
          try reset0()
          catch { case cause: Throwable => new Failure(Failure.unwindCause(cause)) }
        ResetUnitSupervisor.this.synchronized {
          if (resetState.asInstanceOf[AnyRef] eq Starting) resetState = effect
        }
        relay.run()
      }
    }

    private def startClose(): Unit = {
      val effect =
        try rejectClose0()
        catch { case cause: Throwable => new Failure(Failure.unwindCause(cause)) }
      effect match {
        case failure: Failure =>
          ResetUnitSupervisor.this.synchronized { closeState = failure }
          relay.run()
        case _ =>
          Async.startRegistered(effect) { running =>
            ResetUnitSupervisor.this.synchronized { closeState = running }
            relay.run()
          }
      }
    }

    /**
     * Drives through distinct replacements and stops only at suspension or a
     * terminal.
     */
    private def drive(state: Any): (Any, Boolean) =
      if (!state.isInstanceOf[Failure] && state.isInstanceOf[Pollable[?]]) {
        var current = state
        var driving = true
        while (driving && !current.isInstanceOf[Failure] && current.isInstanceOf[Pollable[?]]) {
          val child = current.asInstanceOf[Pollable[Unit]]
          val next  =
            try child.poll(relay)
            catch { case cause: Throwable => new Failure(Failure.unwindCause(cause)) }
          current = next
          driving = next.isInstanceOf[Pollable[?]] && !next.isInstanceOf[Failure] &&
            (next.asInstanceOf[AnyRef] ne child.asInstanceOf[AnyRef])
        }
        (current, false)
      } else (state, false)

    def poll(onComplete: Runnable): Async[Unit] = {
      ResetUnitSupervisor.this.synchronized {
        if (cancelled) {
          cancelledWaiters = addWaiter(cancelledWaiters, onComplete)
          if (cleanupDone) return Async.succeed(())
          else return this
        }
      }
      if (!acquire(onComplete)) return this
      var wakeReplacement = false
      try {
        startReset()
        val driven = drive(ResetUnitSupervisor.this.synchronized(resetState))
        val next   = driven._1
        wakeReplacement = driven._2
        ResetUnitSupervisor.this.synchronized {
          resetState = next
          if (next.isInstanceOf[Failure]) { resetFailure = next.asInstanceOf[Failure]; resetState = Terminal }
          else if (!next.isInstanceOf[Pollable[?]]) resetState = Terminal
          if (cancelled) this
          else if (resetState.asInstanceOf[AnyRef] eq Terminal) {
            if (resetFailure ne null) resetFailure.asInstanceOf[Async[Unit]] else Async.succeed(())
          } else this
        }
      } finally {
        release()
        if (wakeReplacement) relay.run()
      }
    }

    override def cancel(): Unit = { cancelWithCleanup().start; () }

    override private[async] def cancelWithCleanup(): Async[Unit] = {
      val claimed = ResetUnitSupervisor.this.synchronized {
        if (cleanup eq null) {
          cancelled = true
          cleanup = new Pollable[Unit] {
            def poll(onComplete: Runnable): Async[Unit] = {
              val completed = ResetUnitSupervisor.this.synchronized {
                if (cleanupDone) cleanupResult else null
              }
              if (completed.asInstanceOf[AnyRef] ne null) return completed
              if (!acquire(onComplete)) return this
              var wakeReplacement = false
              try {
                startReset()
                val resetDriven = drive(ResetUnitSupervisor.this.synchronized(resetState))
                val resetNext   = resetDriven._1
                wakeReplacement = resetDriven._2
                val startRejectionClose = ResetUnitSupervisor.this.synchronized {
                  resetState = resetNext
                  if (resetNext.isInstanceOf[Failure]) {
                    resetFailure = resetNext.asInstanceOf[Failure]; resetState = Terminal
                  } else if (!resetNext.isInstanceOf[Pollable[?]]) resetState = Terminal
                  val resetFinished  = resetState.asInstanceOf[AnyRef] eq Terminal
                  val closeUnstarted = closeState.asInstanceOf[AnyRef] eq Unstarted
                  val initialClose   = rejectAfterReset && resetFinished && closeUnstarted
                  if (!rejectAfterReset && resetFinished && closeUnstarted) closeState = Terminal
                  if (initialClose) {
                    closeState = Starting
                    true
                  } else false
                }
                // A rejected reset must finish before close begins. Otherwise
                // a fast close followed by a late reset completion can reopen
                // a resource after cancellation has already joined cleanup.
                if (startRejectionClose) startClose()
                val closeBefore = ResetUnitSupervisor.this.synchronized(closeState)
                val closeDriven =
                  if (
                    (closeBefore.asInstanceOf[AnyRef] eq Starting) ||
                    (closeBefore.asInstanceOf[AnyRef] eq Unstarted)
                  ) (closeBefore, false)
                  else drive(closeBefore)
                val closeNext = closeDriven._1
                wakeReplacement ||= closeDriven._2
                ResetUnitSupervisor.this.synchronized {
                  if (
                    (closeNext.asInstanceOf[AnyRef] ne Starting) &&
                    (closeNext.asInstanceOf[AnyRef] ne Unstarted)
                  ) {
                    closeState = closeNext
                    if (closeNext.isInstanceOf[Failure]) {
                      val failure = closeNext.asInstanceOf[Failure]
                      if (closeFailure eq null) closeFailure = failure
                      else if (
                        (closeFailure.cause ne null) && (failure.cause ne null) &&
                        (closeFailure.cause ne failure.cause)
                      ) closeFailure.cause.addSuppressed(failure.cause)
                      closeState = Terminal
                    } else if (!closeNext.isInstanceOf[Pollable[?]]) closeState = Terminal
                  }
                }
                val startCompensationClose = ResetUnitSupervisor.this.synchronized {
                  val resetFinished = resetState.asInstanceOf[AnyRef] eq Terminal
                  val earlyFinished = closeState.asInstanceOf[AnyRef] eq Terminal
                  if (
                    rejectAfterReset && resetFinished && earlyCloseStarted && earlyFinished &&
                    !compensationCloseStarted
                  ) {
                    compensationCloseStarted = true
                    closeState = Starting
                    true
                  } else false
                }
                // An early close may unblock reset construction, after which
                // reset can reopen the resource. Close once more only after
                // both reset and the early close have terminated.
                if (startCompensationClose) startClose()
                val (result, wakes) = ResetUnitSupervisor.this.synchronized {
                  if ((closeState.asInstanceOf[AnyRef] ne Terminal) || (resetState.asInstanceOf[AnyRef] ne Terminal))
                    (this.asInstanceOf[Async[Unit]], Nil)
                  else {
                    val result =
                      if (resetFailure ne null) {
                        if (
                          (resetFailure.cause ne null) && (closeFailure ne null) && (closeFailure.cause ne null) &&
                          (closeFailure.cause ne resetFailure.cause)
                        )
                          resetFailure.cause.addSuppressed(closeFailure.cause)
                        resetFailure.asInstanceOf[Async[Unit]]
                      } else if (closeFailure ne null) closeFailure.asInstanceOf[Async[Unit]]
                      else Async.succeed(())
                    cleanupResult = result
                    cleanupDone = true
                    val wakes = cancelledWaiters
                    cancelledWaiters = Nil
                    (result, wakes)
                  }
                }
                wakeAll(wakes)
                result
              } finally {
                release()
                if (wakeReplacement) relay.run()
              }
            }
          }
        }
        val startEarly =
          (resetState.asInstanceOf[AnyRef] eq Starting) &&
            (closeState.asInstanceOf[AnyRef] eq Unstarted)
        if (startEarly) {
          earlyCloseStarted = true
          closeState = Starting
        }
        (cleanup, startEarly)
      }
      // Blocking reset construction may require an early close to unblock it.
      // That close is compensated after reset terminates; a normally returned
      // pending reset is joined before its one rejection close starts.
      if (claimed._2) startClose()
      claimed._1
    }
  }

  private[blocks] trait CancelledReplacementProvenance {
    def wasCancelledReplacement(pollable: Pollable[?]): Boolean
  }

  /**
   * Reports that cancellation linearized before this wrapper could create its
   * output.
   */
  private[blocks] trait CancelledOutputOwnership {
    def cancellationOwnsOutput: Boolean
  }

  private final class CancelledPollableObserver[A](initial: Pollable[A], owner: AnyRef)
      extends Pollable[A]
      with CancelledReplacementProvenance
      with CancelledOutputOwnership {
    private var current = initial

    def advance(previous: Pollable[?], next: Pollable[?]): Unit = synchronized {
      if (current.asInstanceOf[AnyRef] eq previous.asInstanceOf[AnyRef]) current = next.asInstanceOf[Pollable[A]]
    }

    def wasCancelledReplacement(pollable: Pollable[?]): Boolean =
      owner match {
        case provenance: CancelledReplacementProvenance => provenance.wasCancelledReplacement(pollable)
        case _                                          => false
      }

    def cancellationOwnsOutput: Boolean = synchronized(current) match {
      case ownership: CancelledOutputOwnership => ownership.cancellationOwnsOutput
      case _                                   => false
    }

    def poll(onComplete: Runnable): Async[A] = {
      val child = synchronized(current)
      val next  =
        try child.poll(onComplete)
        catch { case t: Throwable => new Failure(Failure.unwindCause(t)) }
      if (!next.isInstanceOf[Failure] && next.isInstanceOf[Pollable[?]]) {
        val pending = next.asInstanceOf[Pollable[A]]
        synchronized {
          if (current.asInstanceOf[AnyRef] eq child.asInstanceOf[AnyRef]) current = pending
        }
        if (pending.asInstanceOf[AnyRef] eq child.asInstanceOf[AnyRef]) this else pending
      } else next
    }

    override private[async] def cancelWithCleanup(): Async[Unit] = Async.succeed(())
  }

  private[blocks] abstract class BracketAsyncPollable[R, A](
    releaseAfterUse: Boolean,
    acquisitionContext: Any = null,
    useContext: Any = null
  ) extends Pollable[A]
      with CancelledOutputOwnership {
    protected def acquireResource(): Async[R]
    protected def acquisitionFailure(cause: Throwable): Throwable = cause
    protected def completeResult(result: Async[A]): Async[A]      = result
    protected def releaseResource(resource: R): Async[Unit]
    protected def releaseResource(resource: R, useResult: Async[A]): Async[Unit] = {
      val _ = useResult
      releaseResource(resource)
    }
    protected def useFailed(cause: Throwable): Unit = ()
    protected def useResource(resource: R): Async[A]

    private val Fresh          = 0
    private val Acquiring      = 1
    private val Using          = 2
    private val Releasing      = 3
    private val Done           = 4
    private val CancelledFresh = 5

    @volatile private var phase                             = Fresh
    @volatile private var cancelled                         = false
    private var polling                                     = false
    private var waiter: Runnable                            = null
    private var acquisition: Any                            = acquisitionContext
    private var active: Any                                 = useContext
    private var acquisitionPolling                          = false
    private var cancellationState: BracketCancellationState = null
    private var ownsCancelledOutput                         = false

    protected final def currentResource: R             = acquisition.asInstanceOf[R]
    protected final def initialAcquisitionContext: Any = acquisition
    protected final def initialUseContext: Any         = active

    private final class BracketCancellationState {
      var cleanup: BracketCleanup                      = null
      var useCleanups                                  = List.empty[UseCleanupEntry]
      var useCleanupIndex                              = 0
      var currentUseCleanup: Any                       = null
      var drivingUseCleanups                           = false
      var cleanupFailure: Failure                      = null
      var cancelledAcquisitions                        = List.empty[AnyRef]
      var acquisitionCancellation: CancellationCleanup = null
    }

    private def acquirePermit(onComplete: Runnable): Boolean = synchronized {
      if (polling) { waiter = onComplete; false }
      else { polling = true; true }
    }

    private def releasePermit(): Unit = {
      val wake = synchronized {
        polling = false
        val w = waiter
        waiter = null
        w
      }
      if (wake ne null)
        try wake.run()
        catch { case _: Throwable => () }
    }

    private def failure(t: Throwable): Failure = new Failure(Failure.unwindCause(t))

    private def suppress(primary: Failure, secondary: Failure): Failure = {
      val p = primary.cause
      val s = secondary.cause
      if ((p ne null) && (s ne null) && (p ne s))
        try p.addSuppressed(s)
        catch { case _: Throwable => () }
      primary
    }

    private def recordCleanupFailure(next: Failure): Unit = synchronized {
      val state = cancellationState
      if (state.cleanupFailure eq null) state.cleanupFailure = next
      else suppress(state.cleanupFailure, next)
    }

    private final class UseCleanupEntry(val owner: AnyRef) {
      private var effect: Any      = null
      private var waiter: Runnable = null

      def complete(value: Any): Unit = {
        val wake = synchronized {
          effect = value
          val w = waiter
          waiter = null
          w
        }
        if (wake ne null)
          try wake.run()
          catch { case _: Throwable => () }
      }

      def getOrAwait(onComplete: Runnable): Any = synchronized {
        if (effect == null) { waiter = onComplete; null }
        else effect
      }
    }

    private def prepareActiveCancellation(): Unit = {
      val claimed = synchronized {
        val state = cancellationState
        // The poll driver does not hold this monitor while replacing `active`.
        // Test and cast one captured value so a concurrent terminal publication
        // cannot turn the checked Pollable into a raw successful value.
        val current   = active
        val candidate =
          if (
            phase == Using && !state.drivingUseCleanups && current.isInstanceOf[Pollable[?]] &&
            !current.isInstanceOf[Failure]
          )
            current.asInstanceOf[Pollable[Any]]
          else null
        if ((candidate eq null) || state.useCleanups.exists(_.owner eq candidate.asInstanceOf[AnyRef])) null
        else {
          val entry = new UseCleanupEntry(candidate.asInstanceOf[AnyRef])
          state.useCleanups = state.useCleanups :+ entry
          (candidate, entry)
        }
      }
      if (claimed ne null) {
        val (current, entry) = claimed
        val effect           =
          try current.cancelWithCleanup()
          catch { case t: Throwable => failure(t) }
        entry.complete(effect)
      }
    }

    private def prepareAcquisitionCancellation(): Unit = {
      val claimed = synchronized {
        val state     = cancellationState
        val current   = acquisition
        val candidate =
          if (phase == Acquiring && current.isInstanceOf[Pollable[?]] && !current.isInstanceOf[Failure])
            current.asInstanceOf[Pollable[Any]]
          else null
        if ((candidate eq null) || state.cancelledAcquisitions.exists(_ eq candidate.asInstanceOf[AnyRef])) null
        else {
          val entry  = new UseCleanupEntry(candidate.asInstanceOf[AnyRef])
          val joined = new CancellationCleanup
          state.cancelledAcquisitions = candidate.asInstanceOf[AnyRef] :: state.cancelledAcquisitions
          state.useCleanups = state.useCleanups :+ entry
          val inFlight = acquisitionPolling
          if (inFlight) state.acquisitionCancellation = joined
          (candidate, entry, joined, inFlight)
        }
      }
      if (claimed ne null) {
        val (current, entry, joined, inFlight) = claimed
        val primary                            =
          try current.cancelWithCleanup()
          catch { case t: Throwable => Async.fail(t) }
        joined.primary(primary)
        if (!inFlight) joined.noReplacement()
        entry.complete(joined.effect)
      }
    }

    private def beginAcquisitionPoll(current: Pollable[Any]): Unit = {
      val armed = synchronized {
        acquisitionPolling = true
        val state = cancellationState
        if (
          cancelled && (state.acquisitionCancellation eq null) &&
          !state.cancelledAcquisitions.exists(_ eq current.asInstanceOf[AnyRef])
        ) {
          val entry  = new UseCleanupEntry(current.asInstanceOf[AnyRef])
          val joined = new CancellationCleanup
          state.cancelledAcquisitions = current.asInstanceOf[AnyRef] :: state.cancelledAcquisitions
          state.useCleanups = state.useCleanups :+ entry
          state.acquisitionCancellation = joined
          (entry, joined)
        } else null
      }
      if (armed ne null) {
        val (entry, joined) = armed
        val primary         =
          try current.cancelWithCleanup()
          catch { case t: Throwable => failure(t) }
        joined.primary(primary)
        entry.complete(joined.effect)
      }
    }

    private def finishAcquisitionPoll(current: Pollable[Any], result: Any): Unit = {
      val (joined, replacement) = synchronized {
        acquisitionPolling = false
        val state = cancellationState
        val value = if (state eq null) null else state.acquisitionCancellation
        if (state ne null) state.acquisitionCancellation = null
        val candidate =
          if (
            result.isInstanceOf[Pollable[?]] && !result.isInstanceOf[Failure] &&
            (result.asInstanceOf[Pollable[Any]] ne current)
          ) {
            result.asInstanceOf[Pollable[Any]]
          } else null
        val alreadyCancelled =
          (candidate ne null) && current.isInstanceOf[CancelledReplacementProvenance] &&
            current.asInstanceOf[CancelledReplacementProvenance].wasCancelledReplacement(candidate)
        if (alreadyCancelled && (state ne null))
          state.cancelledAcquisitions = candidate.asInstanceOf[AnyRef] :: state.cancelledAcquisitions
        val next =
          if ((value ne null) && (candidate ne null) && !alreadyCancelled) {
            state.cancelledAcquisitions = candidate.asInstanceOf[AnyRef] :: state.cancelledAcquisitions
            candidate
          } else null
        acquisition = result
        (value, next)
      }
      if (joined ne null) {
        if (replacement ne null) joined.replacement(replacement)
        else joined.noReplacement()
      }
    }

    private def acquisitionCleanupsDrained: Boolean = synchronized {
      val state = cancellationState
      state.useCleanupIndex >= state.useCleanups.length
    }

    private def startRelease(): Unit = {
      val release =
        try
          if (phase == Acquiring) releaseResource(acquisition.asInstanceOf[R])
          else releaseResource(acquisition.asInstanceOf[R], active.asInstanceOf[Async[A]])
        catch { case t: Throwable => failure(t) }
      // Once release has been constructed, the resource is owned by that
      // effect. Reuse the acquisition slot for the use outcome while `active`
      // drives release; these values are never needed in the same phase.
      acquisition = active
      phase = Releasing
      active = release
    }

    private def driveCurrent(onComplete: Runnable): Boolean = {
      while (active.isInstanceOf[Pollable[?]] && !active.isInstanceOf[Failure]) {
        val current = active.asInstanceOf[Pollable[Any]]
        active =
          try current.poll(onComplete)
          catch { case t: Throwable => failure(t) }
        if (
          active.isInstanceOf[Pollable[?]] && !active.isInstanceOf[Failure] &&
          (active.asInstanceOf[AnyRef] eq current.asInstanceOf[AnyRef])
        ) return false
      }
      true
    }

    def poll(onComplete: Runnable): Async[A] = {
      val depth = PlatformAsync.enterBracketPoll()
      try
        if (depth > MaxInlineBracketPollDepth) Async.rescheduleKnown(this, () => ())
        else {
          if (!acquirePermit(onComplete)) return this
          try drive(onComplete, cleaning = false).asInstanceOf[Async[A]]
          finally releasePermit()
        }
      finally PlatformAsync.exitBracketPoll()
    }

    private def drive(onComplete: Runnable, cleaning: Boolean): Any = {
      while (true) {
        phase match {
          case Fresh =>
            phase = Acquiring
            acquisition =
              try acquireResource()
              catch { case t: Throwable => failure(t) }
          case Acquiring =>
            if (cancelled) {
              val state = cancellationState
              prepareAcquisitionCancellation()
              if (!cleaning) return this
              state.drivingUseCleanups = true
              if (state.currentUseCleanup == null && state.useCleanupIndex < state.useCleanups.length) {
                state.currentUseCleanup = state.useCleanups(state.useCleanupIndex).getOrAwait(onComplete)
                if (state.currentUseCleanup == null) return state.cleanup
              }
              if (state.currentUseCleanup != null) {
                active = state.currentUseCleanup
                if (!driveCurrent(onComplete)) { state.currentUseCleanup = active; return state.cleanup }
                if (active.isInstanceOf[Failure]) recordCleanupFailure(active.asInstanceOf[Failure])
                state.useCleanupIndex += 1
                state.currentUseCleanup = null
              }
            }
            if (!cancelled || acquisitionCleanupsDrained) {
              if (
                cancelled && acquisition.isInstanceOf[CancelledOutputOwnership] &&
                acquisition.asInstanceOf[CancelledOutputOwnership].cancellationOwnsOutput
              ) {
                phase = Done
              } else {
                if (acquisition.isInstanceOf[Pollable[?]] && !acquisition.isInstanceOf[Failure]) {
                  val current = acquisition.asInstanceOf[Pollable[Any]]
                  beginAcquisitionPoll(current)
                  val result =
                    try current.poll(onComplete)
                    catch { case t: Throwable => failure(t) }
                  finishAcquisitionPoll(current, result)
                  if (acquisition.isInstanceOf[Pollable[?]] && !acquisition.isInstanceOf[Failure])
                    if (acquisition.asInstanceOf[AnyRef] eq current.asInstanceOf[AnyRef])
                      return if (cleaning) cancellationState.cleanup else this
                }
                if (
                  (!cancelled || acquisitionCleanupsDrained) &&
                  (!acquisition.isInstanceOf[Pollable[?]] || acquisition.isInstanceOf[Failure])
                ) {
                  if (acquisition.isInstanceOf[Failure]) {
                    val failure = acquisition.asInstanceOf[Failure]
                    val cause   =
                      try acquisitionFailure(failure.cause)
                      catch { case transformed: Throwable => transformed }
                    if (cause.asInstanceOf[AnyRef] ne failure.cause.asInstanceOf[AnyRef])
                      acquisition = new Failure(cause)
                    phase = Done
                  } else {
                    val r = AsyncEncoding.deliverSuccess[R](acquisition)
                    acquisition = r
                    if (cancelled) startRelease()
                    else {
                      phase = Using
                      active =
                        try useResource(r)
                        catch { case t: Throwable => failure(t) }
                    }
                  }
                }
              }
            }
          case Using =>
            if (cancelled) {
              val state = cancellationState
              prepareActiveCancellation()
              if (!cleaning) return this
              state.drivingUseCleanups = true
              if (state.currentUseCleanup == null) {
                if (state.useCleanupIndex >= state.useCleanups.length) startRelease()
                else {
                  state.currentUseCleanup = state.useCleanups(state.useCleanupIndex).getOrAwait(onComplete)
                  if (state.currentUseCleanup == null) return state.cleanup
                }
              }
              if (phase == Using) {
                active = state.currentUseCleanup
                if (!driveCurrent(onComplete)) { state.currentUseCleanup = active; return state.cleanup }
                state.currentUseCleanup = active
                if (active.isInstanceOf[Failure]) recordCleanupFailure(active.asInstanceOf[Failure])
                state.useCleanupIndex += 1
                state.currentUseCleanup = null
              }
            } else {
              val completed = driveCurrent(onComplete)
              if (cancelled) {
                prepareActiveCancellation()
                if (!cleaning) return this
              } else if (!completed) return this
              else {
                if (active.isInstanceOf[Failure]) useFailed(active.asInstanceOf[Failure].cause)
                if (releaseAfterUse || active.isInstanceOf[Failure]) startRelease()
                else { acquisition = active; phase = Done }
              }
            }
          case Releasing =>
            if (!driveCurrent(onComplete)) return if (cleaning) cancellationState.cleanup else this
            val releaseFailure = if (active.isInstanceOf[Failure]) active.asInstanceOf[Failure] else null
            if (cancelled) {
              val state = cancellationState
              if (state.cleanupFailure ne null) {
                if (releaseFailure ne null) suppress(state.cleanupFailure, releaseFailure)
              } else state.cleanupFailure = releaseFailure
            } else if (releaseFailure ne null) {
              if (acquisition.isInstanceOf[Failure]) suppress(acquisition.asInstanceOf[Failure], releaseFailure)
              else acquisition = releaseFailure.asInstanceOf[Async[A]]
            }
            if (!cancelled) acquisition = completeResult(acquisition.asInstanceOf[Async[A]])
            phase = Done
          case Done =>
            if (cleaning) {
              val failure = cancellationState.cleanupFailure
              return if (failure ne null) failure.asInstanceOf[Async[Unit]] else Async.succeed(())
            } else if (cancelled) return this
            else return acquisition
          case CancelledFresh => return if (cleaning) Async.succeed(()) else this
        }
      }
      Async.succeed(())
    }

    override def cancel(): Unit = {
      val effect = cancelWithCleanup()
      val any    = effect.asInstanceOf[Any]
      if (any.isInstanceOf[Pollable[?]] && !any.isInstanceOf[Failure]) {
        val pending = any.asInstanceOf[Pollable[Unit]]
        val next    =
          try pending.poll(new Runnable { def run(): Unit = () })
          catch { case cause: Throwable => Async.fail(cause) }
        if (next.isInstanceOf[Pollable[?]] && !next.isInstanceOf[Failure]) next.start
      }
      ()
    }

    override private[async] def cancelWithCleanup(): Async[Unit] = {
      val depth = PlatformAsync.enterBracketCancellation()
      try {
        val result = synchronized {
          if (cancellationState eq null) cancellationState = new BracketCancellationState
          val state = cancellationState
          if (!cancelled) ownsCancelledOutput = phase != Done
          cancelled = true
          if (phase == Fresh) phase = CancelledFresh
          if (state.cleanup eq null) state.cleanup = new BracketCleanup
          state.cleanup
        }
        // A cleanup poll will continue propagation from this bracket. Bounding
        // the eager signal here keeps deeply nested resource scopes stack safe
        // without changing their inner-first cleanup ownership.
        if (depth <= MaxInlineBracketPollDepth) {
          prepareAcquisitionCancellation()
          prepareActiveCancellation()
        }
        result
      } finally PlatformAsync.exitBracketCancellation()
    }

    def cancellationOwnsOutput: Boolean = synchronized(ownsCancelledOutput)

    private final class BracketCleanup extends Pollable[Unit] {
      def poll(onComplete: Runnable): Async[Unit] = {
        val depth = PlatformAsync.enterBracketPoll()
        try
          if (depth > MaxInlineBracketPollDepth) Async.rescheduleKnown(this, () => ())
          else {
            if (!acquirePermit(onComplete)) return this
            try drive(onComplete, cleaning = true).asInstanceOf[Async[Unit]]
            finally releasePermit()
          }
        finally PlatformAsync.exitBracketPoll()
      }

      override private[async] def cancelWithCleanup(): Async[Unit] = this
    }
  }

  private final class DeferredCancelablePollable[A](thunk: () => A, onCancel: () => Async[Unit]) extends Pollable[A] {
    private val state   = new java.util.concurrent.atomic.AtomicReference[AnyRef](DeferredFresh)
    private val cleanup = new Completer[Async[Unit]]

    def poll(onComplete: Runnable): Async[A] = {
      val current = state.get()
      if (current eq DeferredFresh) {
        if (!state.compareAndSet(DeferredFresh, DeferredRunning)) poll(onComplete)
        else {
          val result =
            try Async.succeed(thunk())
            catch { case t: Throwable => Async.fail(t) }
          val done = new DeferredDone(result)
          if (state.compareAndSet(DeferredRunning, done)) result
          else this
        }
      } else if (current.isInstanceOf[DeferredDone])
        current.asInstanceOf[DeferredDone].value.asInstanceOf[Async[A]]
      else this
    }

    override def cancel(): Unit = { cancelWithCleanup().start; () }

    override private[async] def cancelWithCleanup(): Async[Unit] = {
      var loop = true
      while (loop) {
        val current = state.get()
        if ((current eq DeferredCancelled) || current.isInstanceOf[DeferredDone]) loop = false
        else if (state.compareAndSet(current, DeferredCancelled)) {
          loop = false
          val result =
            try onCancel()
            catch { case cause: Throwable => Async.fail(cause) }
          cleanup.succeed(result)
        }
      }
      val current = state.get()
      if (current.isInstanceOf[DeferredDone]) Async.succeed(())
      else cleanup.peek.flatMap(identity)
    }
  }

  private final class AcquireCancelablePollable[A](acquire: () => A, release: A => Async[Unit]) extends Pollable[A] {
    private val state     = new java.util.concurrent.atomic.AtomicReference[AnyRef](DeferredFresh)
    private val cleanup   = new Completer[Async[Unit]]
    private val published = new Completer[Unit]

    def poll(onComplete: Runnable): Async[A] = {
      val current = state.get()
      if (current eq DeferredFresh) {
        if (!state.compareAndSet(DeferredFresh, DeferredRunning)) poll(onComplete)
        else {
          val result =
            try Async.succeed(acquire())
            catch { case cause: Throwable => Async.fail(cause) }
          if (state.compareAndSet(DeferredRunning, new DeferredPublished(result))) {
            published.succeed(())
            result
          } else {
            result match {
              case _: Failure => cleanup.succeed(Async.succeed(()))
              case success    =>
                val released =
                  try {
                    val effect = release(AsyncEncoding.deliverSuccess[A](success))
                    if (effect.asInstanceOf[AnyRef] eq null)
                      Async.fail(new NullPointerException("acquireCancelable release returned null Async"))
                    else effect
                  } catch { case cause: Throwable => Async.fail(cause) }
                cleanup.succeed(released)
            }
            this
          }
        }
      } else if (current.isInstanceOf[DeferredPublished])
        current.asInstanceOf[DeferredPublished].value.asInstanceOf[Async[A]]
      else if (current eq DeferredRunning) {
        val readiness = published.poll(onComplete)
        if (readiness.isInstanceOf[Pollable[?]]) this else poll(onComplete)
      } else this
    }

    override def cancel(): Unit = { cancelWithCleanup().start; () }

    override private[async] def cancelWithCleanup(): Async[Unit] = {
      var loop = true
      while (loop) {
        val current = state.get()
        if ((current eq DeferredCancelled) || current.isInstanceOf[DeferredPublished]) loop = false
        else if (state.compareAndSet(current, DeferredCancelled)) {
          loop = false
          if (current eq DeferredFresh) cleanup.succeed(Async.succeed(()))
        }
      }
      if (state.get().isInstanceOf[DeferredPublished]) Async.succeed(())
      else cleanup.peek.flatMap(identity)
    }
  }

  private object RescheduledUnset

  /**
   * A lazy scheduler boundary. The scheduled turn publishes the constructed
   * computation back to the existing driver instead of starting a nested
   * [[Running]]; repeated cooperative yields therefore replace one active
   * pollable rather than retaining an ever-growing chain of runners.
   */
  private final class RescheduledPollable[A](
    thunk: () => Async[A],
    onStarted: () => Unit
  ) extends Operation[A]
      with Runnable {
    private val ready                         = new Completer[Unit]
    private var scheduled                     = false
    private var constructing                  = false
    private var cancelled                     = false
    private var handedOff                     = false
    private var result: Any                   = RescheduledUnset
    private var cancellation: Cancellation    = null
    private var cancellationJoin: Async[Unit] = null

    def poll(onComplete: Runnable): Async[A] = {
      val enqueue = synchronized {
        if (!scheduled && !cancelled) { scheduled = true; true }
        else false
      }
      if (enqueue) {
        ready.poll(onComplete)
        Async.schedule(this, forceMacrotask = true)
        return this
      }
      val child = claimResult()
      if (child.asInstanceOf[AnyRef] ne RescheduledUnset) child.asInstanceOf[Async[A]]
      else {
        val readiness = ready.poll(onComplete)
        if (readiness.isInstanceOf[Pollable[_]]) this
        else {
          val published = claimResult()
          if (published.asInstanceOf[AnyRef] eq RescheduledUnset) this
          else published.asInstanceOf[Async[A]]
        }
      }
    }

    def run(): Unit = {
      val evaluate = synchronized {
        if (cancelled) false
        else { constructing = true; true }
      }
      if (!evaluate) ()
      else {
        val effect =
          try thunk()
          catch { case cause: Throwable => Async.fail(cause) }
        val owner = synchronized {
          constructing = false
          result = effect
          if (cancelled && !handedOff) cancellation else null
        }
        if (owner ne null) {
          owner.primary(cancelResult(effect))
          owner.noReplacement()
        }
        onStarted()
        ready.succeed(())
      }
    }

    protected def cancelOperation(): Async[Unit] = {
      val (joined, child, ownsChild, completeNow) = synchronized {
        if (cancellation ne null) (cancellationJoin, null, false, false)
        else {
          cancelled = true
          cancellation = Async.cancellation()
          cancellationJoin = cancellation.effect
          val current = result
          val owns    = (current.asInstanceOf[AnyRef] ne RescheduledUnset) && !handedOff
          (cancellationJoin, current, owns, !constructing && !owns)
        }
      }
      if (ownsChild) {
        cancellation.primary(cancelResult(child.asInstanceOf[Async[A]]))
        cancellation.noReplacement()
      } else if (completeNow) {
        cancellation.primary(Async.succeed(()))
        cancellation.noReplacement()
      }
      joined
    }

    private def cancelResult(child: Async[A]): Async[Unit] =
      try {
        val any = child.asInstanceOf[Any]
        if (any.isInstanceOf[Pollable[?]]) Async.cancelWithCleanup(any.asInstanceOf[Pollable[A]])
        else Async.succeed(())
      } catch { case cause: Throwable => Async.fail(cause) }

    private def claimResult(): Any = synchronized {
      if (cancelled || handedOff || (result.asInstanceOf[AnyRef] eq RescheduledUnset)) RescheduledUnset
      else {
        handedOff = true
        result
      }
    }
  }

  /**
   * Scheduler boundary for a known, already-owned computation. A registered
   * child runner preserves cancellation handoff across deep bracket boundaries,
   * where cleanup must remain strictly inner-first.
   */
  private final class RescheduledRunningPollable[A](effect: Async[A], onStarted: () => Unit)
      extends Operation[A]
      with Runnable {
    private val ready                         = new Completer[Unit]
    private var scheduled                     = false
    private var constructing                  = false
    private var cancelled                     = false
    private var running: Running[_]           = null
    private var cancellation: Cancellation    = null
    private var cancellationJoin: Async[Unit] = null

    def poll(onComplete: Runnable): Async[A] = {
      val (enqueue, child) = synchronized {
        if (!scheduled && !cancelled) { scheduled = true; (true, null) }
        else (false, running)
      }
      if (enqueue) {
        ready.poll(onComplete)
        Async.schedule(this, forceMacrotask = true)
        return this
      }
      if (child ne null) child.asInstanceOf[Running[A]].poll(onComplete)
      else {
        val result = ready.poll(onComplete)
        if (result.isInstanceOf[Pollable[_]]) this
        else {
          val registered = synchronized(running)
          if (registered eq null) this else registered.asInstanceOf[Running[A]].poll(onComplete)
        }
      }
    }

    def run(): Unit = {
      val evaluate = synchronized {
        if (cancelled) false
        else { constructing = true; true }
      }
      if (!evaluate) ()
      else {
        val register: Running[A] => Unit = { started =>
          val owner = synchronized {
            constructing = false
            running = started
            if (cancelled) cancellation else null
          }
          if (owner ne null) {
            owner.primary(cancelResult(started))
            owner.noReplacement()
          }
          onStarted()
          ready.succeed(())
        }
        Async.startRegisteredInline(effect)(register)
      }
    }

    protected def cancelOperation(): Async[Unit] = {
      val (joined, child, completeNow) = synchronized {
        if (cancellation ne null) (cancellationJoin, null, false)
        else {
          cancelled = true
          cancellation = Async.cancellation()
          cancellationJoin = cancellation.effect
          (cancellationJoin, running, !constructing && (running eq null))
        }
      }
      if (child ne null) {
        cancellation.primary(cancelResult(child.asInstanceOf[Async[A]]))
        cancellation.noReplacement()
      } else if (completeNow) {
        cancellation.primary(cancelResult(effect))
        cancellation.noReplacement()
      }
      joined
    }

    private def cancelResult(child: Async[A]): Async[Unit] =
      try {
        val any = child.asInstanceOf[Any]
        if (any.isInstanceOf[Pollable[?]]) Async.cancelWithCleanup(any.asInstanceOf[Pollable[A]])
        else Async.succeed(())
      } catch { case cause: Throwable => Async.fail(cause) }
  }

  /**
   * Create a failed [[Async]] carrying `cause`. A failed value short-circuits
   * `map` / `flatMap` without invoking the continuation, is recoverable with
   * `.catchAll`, and is re-thrown by `.block`.
   */
  def fail(cause: Throwable): Async[Nothing] = new Failure(cause)

  // `attempt` lives in `AsyncCompanionVersionSpecific` (mixed in above) so Scala
  // 3 can make it an `inline def` (the body is spliced at the call site — no
  // `Function0` thunk for the by-name argument), while Scala 2 keeps the plain
  // by-name `def`. Single public API name across versions.

  // The public `promise` lives in `AsyncCompanionVersionSpecific` (mixed in
  // above) so Scala 3 can offer a `Completer[A] ?=> Unit` context-function
  // body while Scala 2 keeps the `Completer[A] => Unit` shape — single API
  // name across versions, version-appropriate parameter shape. Both
  // implementations delegate to the package-private helper below.

  /**
   * Cross-version implementation of [[promise]]. Takes a plain
   * `Completer[A] => Unit` body so it is callable from `private[async]` code
   * (e.g. `AsyncInterop`) regardless of Scala version, even though the public
   * `promise` shape differs between Scala 2 and Scala 3.
   */
  private[async] def promiseInternal[A](body: Completer[A] => Unit): Async[A] = {
    val c = new Completer[A]
    body(c)
    c.peek
  }

  /**
   * Evaluate `body` on a background worker (JVM) or microtask (JS) and return a
   * [[Running]] for the result — the `Async` analogue of `Future.apply`.
   *
   * A single by-name entry point (no by-value overload): the worker evaluates
   * `body`, capturing a throw — including a statically `Nothing`-typed body
   * (`Async.start { ...; throw e }`, `Async.start(???)`) — as a failed run
   * rather than letting it escape at the call site, and lifts the result with
   * the same runtime encoding as every other value received from the user (a
   * `Pollable` success value is wrapped, a `AsyncEncoding.WrappedPollable`
   * carrier has its depth incremented).
   *
   * To eagerly drive an '''already-built''' `Async` instead — composing with
   * `either`, `tap`, `foldCause`, ... before driving (e.g.
   * `fa.either.tap(record).start`) — use the `fa.start` extension method. A
   * [[Running]] is itself an `Async[A]` and may be polled,
   * [[Cancelable.cancel]] led (a no-op once completed), or further composed; on
   * the JVM a suspended run proceeds on a background worker, on Scala.js via
   * microtasks.
   */
  def start[A](body: => A): Running[A] =
    AsyncRunner.startEval(body)

  /** Start `fa` after publishing its running handle to `register`. */
  private[blocks] def startRegistered[A](fa: Async[A])(register: Running[A] => Unit): Running[A] =
    AsyncRunner.startRegistered(fa)(register)

  /**
   * Starts a child from a scheduler turn without enqueueing a redundant initial
   * driver task.
   */
  private[blocks] def startRegisteredInline[A](fa: Async[A])(register: Running[A] => Unit): Running[A] =
    AsyncRunner.startRegisteredInline(fa)(register)

  private[blocks] def startRegisteredOwned[A](
    fa: Async[A],
    owner: AnyRef
  )(register: Running[A] => Unit): Running[A] =
    AsyncRunner.startRegisteredOwned(fa, owner)(register)

  /**
   * An [[Async]] that never completes. Useful as a sentinel in tests and as the
   * right-zero of `orElse`-style operations.
   */
  val never: Async[Nothing] = new Pollable[Nothing] {
    def poll(onComplete: Runnable): Async[Nothing] = this
  }

  /**
   * Sequentially run `as` and collect their values into a `List` in input
   * order. A failure short-circuits — subsequent inputs are not driven and the
   * failure is propagated.
   */
  def collectAll[A](as: IterableOnce[Async[A]]): Async[List[A]] =
    // Reify a fault in the source itself (acquiring the iterator, `hasNext`,
    // `next`) through the failure channel rather than letting it escape the
    // construction call — matching the deferred drain, where a throw from
    // inside `CollectAllPollable.poll` is reified by the driver. A `try` with
    // no throw is free on the JVM happy path (exception-table metadata only).
    try
      as match {
        case list: List[?] => collectAllList[A](list.asInstanceOf[List[Async[A]]])
        case _             => drainCollectAll[A](as.iterator, new scala.collection.mutable.ListBuffer[A])
      }
    catch { case t: Throwable => fail(t) }

  /**
   * Fast path for immutable lists of already-ready values. In that case the
   * input spine is already the result spine, so copying into a new List is pure
   * allocation. Wrapped pollable success values still need unwrapping, and any
   * suspended/failed element needs normal sequencing, so those shapes fall back
   * to the iterator implementation.
   */
  private def collectAllList[A](list: List[Async[A]]): Async[List[A]] = {
    var rem = list
    while (rem.nonEmpty) {
      val any = rem.head.asInstanceOf[Any]
      if (any.isInstanceOf[Pollable[?]] || any.isInstanceOf[AsyncEncoding.WrappedPollable])
        return drainCollectAll[A](list.iterator, new scala.collection.mutable.ListBuffer[A])
      rem = rem.tail
    }
    list.asInstanceOf[Async[List[A]]]
  }

  /**
   * Drain `it`, appending into `buf`. While inputs are raw values we stay in a
   * tight loop; on the first [[Pollable]] the rest is handed to a single
   * [[CollectAllPollable]] that reuses the same iterator and buffer — one
   * allocation for the whole batch, and (unlike a per-element `flatMap` chain)
   * iterative draining: a batch of already-settled pollables consumes constant
   * stack regardless of size.
   */
  private def drainCollectAll[A](
    it: Iterator[Async[A]],
    buf: scala.collection.mutable.ListBuffer[A]
  ): Async[List[A]] = {
    while (it.hasNext) {
      val any = it.next().asInstanceOf[Any]
      if (any.isInstanceOf[Failure]) return any.asInstanceOf[Async[List[A]]]
      else if (any.isInstanceOf[Pollable[_]])
        return new CollectAllPollable[A](any.asInstanceOf[Pollable[A]], it, buf)
      else buf += AsyncEncoding.deliverSuccess[A](any)
    }
    succeed(buf.toList)
  }

  /**
   * Shared cancellation handoff for built-in pollable wrappers. The active
   * child is published before it can suspend; if cancellation races a pending
   * replacement, the replacement receives the signal before installation
   * returns. Hooks always run outside the ownership lock.
   */
  private[blocks] abstract class CancellationPropagatingPollable[+A](initial: Pollable[?])
      extends Pollable[A]
      with CancelledReplacementProvenance
      with CancelledOutputOwnership {
    private var active: Pollable[?]                             = initial
    private var cancelled                                       = false
    private var polling                                         = 0L
    private var pollingOwner: AnyRef                            = null
    private var pollingSequence                                 = 0L
    private var cleanup: CancellationCleanup                    = null
    private var cancellationClaim: PropagatingCancellationClaim = null
    private var cancelledReplacements                           = List.empty[AnyRef]
    private var cancelledActiveObserver                         = null.asInstanceOf[CancelledPollableObserver[Any]]
    private var ownsCancelledOutput                             = false

    protected def ownOutputOnIdleCancellation: Boolean = false

    final def cancellationOwnsOutput: Boolean = synchronized(ownsCancelledOutput)

    protected final def ownCancelledOutput(): Unit = synchronized {
      ownsCancelledOutput = true
    }

    final def wasCancelledReplacement(pollable: Pollable[?]): Boolean = synchronized {
      cancelledReplacements.exists(_ eq pollable.asInstanceOf[AnyRef])
    }

    private def rememberCancelledReplacement(pollable: Pollable[?]): Unit = synchronized {
      if (!cancelledReplacements.exists(_ eq pollable.asInstanceOf[AnyRef]))
        cancelledReplacements = pollable.asInstanceOf[AnyRef] :: cancelledReplacements
    }

    protected final def cancellationRequested: Boolean = synchronized(cancelled)

    protected final def observeCancelledActive[B](fallback: Pollable[B]): Async[B] = synchronized {
      if (cancelledActiveObserver eq null) fallback
      else cancelledActiveObserver.asInstanceOf[Pollable[B]]
    }

    protected final def replaceActive(next: Pollable[?]): Boolean =
      synchronized {
        if (cancelled) false
        else {
          active = next
          true
        }
      }

    /** Poll a child while publishing the in-flight handoff to cancellation. */
    protected final def pollChild[B](child: Pollable[B], onComplete: Runnable): Async[B] = {
      val ownership = beginChildPoll(child)
      if (ownership == 0L) return child

      val result =
        try child.poll(onComplete)
        catch {
          case t: Throwable =>
            finishChildPoll(ownership, null)
            throw t
        }
      finishChildPoll(ownership, result)
      result
    }

    protected final def beginChildPoll(child: Pollable[?]): Long =
      synchronized {
        if (cancelled) 0L
        else {
          pollingSequence += 1L
          val ownership = pollingSequence
          polling = ownership
          pollingOwner = child
          ownership
        }
      }

    protected final def beginContinuation(owner: AnyRef): Long =
      synchronized {
        if (cancelled) 0L
        else {
          pollingSequence += 1L
          val ownership = pollingSequence
          polling = ownership
          pollingOwner = owner
          ownership
        }
      }

    protected final def handoffToContinuation(ownership: Long, owner: AnyRef): Long =
      synchronized {
        if (!cancelled && polling == ownership) {
          pollingSequence += 1L
          val next = pollingSequence
          polling = next
          pollingOwner = owner
          active = null
          next
        } else 0L
      }

    protected final def handoffContinuationToChild(ownership: Long, child: Pollable[?]): Boolean =
      synchronized {
        if (!cancelled && polling == ownership) {
          // The continuation returns `child` to its caller; it does not poll
          // the child itself. Publish the child as idle active ownership rather
          // than leaving a phantom in-flight poll that no finishChildPoll call
          // could ever complete.
          polling = 0L
          pollingOwner = null
          active = child
          true
        } else false
      }

    protected final def finishContinuation(ownership: Long, result: Any): Boolean = {
      val (joined, claim) = synchronized {
        val ownsPoll = polling == ownership
        if (ownsPoll) { polling = 0L; pollingOwner = null }
        if (cancelled && ownsPoll) (cleanup, cancellationClaim)
        else {
          if (!cancelled && ownsPoll)
            active =
              if ((result != null) && !result.isInstanceOf[Failure] && result.isInstanceOf[Pollable[?]])
                result.asInstanceOf[Pollable[?]]
              else null
          (null, null)
        }
      }
      if (joined ne null) {
        if ((result != null) && !result.isInstanceOf[Failure] && result.isInstanceOf[Pollable[?]]) {
          val replacement = result.asInstanceOf[Pollable[?]]
          rememberCancelledReplacement(replacement)
          slowPath.ContinuationInterpreter.collectReplacement(claim.batch, replacement)
          joined.claimedReplacement(Async.succeed(()))
        } else joined.noReplacement()
        true
      } else false
    }

    protected final def finishChildPoll(ownership: Long, result: Any, unwrapAsyncOutput: Boolean = false): Unit = {
      val (joined, child, claim) = synchronized {
        val ownsPoll = polling == ownership
        val owner    = if (ownsPoll) pollingOwner.asInstanceOf[Pollable[?]] else null
        if (ownsPoll) { polling = 0L; pollingOwner = null }
        val joined =
          if (cancelled && ownsPoll) cleanup
          else {
            if (!cancelled && ownsPoll)
              active =
                if ((result != null) && !result.isInstanceOf[Failure] && result.isInstanceOf[Pollable[?]])
                  result.asInstanceOf[Pollable[?]]
                else null
            null
          }
        (joined, owner, if (joined ne null) cancellationClaim else null)
      }
      if (joined ne null) {
        if ((result != null) && !result.isInstanceOf[Failure] && result.isInstanceOf[Pollable[?]]) {
          val replacement = result.asInstanceOf[Pollable[?]]
          val observer    = synchronized(cancelledActiveObserver)
          if (observer ne null) observer.advance(child, replacement)
          if (replacement eq child) {
            rememberCancelledReplacement(replacement)
            joined.noReplacement()
          } else {
            rememberCancelledReplacement(replacement)
            slowPath.ContinuationInterpreter.collectReplacement(claim.batch, replacement)
            joined.claimedReplacement(Async.succeed(()))
          }
        } else if (unwrapAsyncOutput && (result != null) && !result.isInstanceOf[Failure]) {
          val output = AsyncEncoding.deliverSuccess[Any](result)
          if (AsyncEncoding.isSuspended(output)) {
            val replacement = output.asInstanceOf[Pollable[?]]
            rememberCancelledReplacement(replacement)
            slowPath.ContinuationInterpreter.collectReplacement(claim.batch, replacement)
            joined.claimedReplacement(Async.succeed(()))
          } else joined.noReplacement()
        } else joined.noReplacement()
      }
    }

    protected final def clearActive(): Unit = synchronized {
      active = null
    }

    override def cancel(): Unit = {
      cancelWithCleanup().start
      ()
    }

    private[async] def claimCancellation(
      batch: slowPath.ContinuationInterpreter.CancellationBatch
    ): Either[PropagatingCancellationClaim, PropagatingCancellationClaim] = synchronized {
      if (cancelled) Left(cancellationClaim)
      else {
        cancelled = true
        val joined     = new CancellationCleanup
        val completion = new Completer[Unit]
        cleanup = joined
        val current = active
        if ((polling == 0L) && ownOutputOnIdleCancellation) ownsCancelledOutput = true
        if (current ne null)
          cancelledActiveObserver = new CancelledPollableObserver[Any](current.asInstanceOf[Pollable[Any]], this)
        active = null
        val claim = new PropagatingCancellationClaim(current, joined, polling, completion, batch)
        cancellationClaim = claim
        Right(claim)
      }
    }

    override private[async] def cancelWithCleanup(): Async[Unit] =
      slowPath.cancelContinuation(this)
  }

  private[async] final class PropagatingCancellationClaim(
    val current: Pollable[?],
    val joined: CancellationCleanup,
    val pollInFlight: Long,
    val completion: Completer[Unit],
    private[async] var batch: slowPath.ContinuationInterpreter.CancellationBatch
  )

  /**
   * Sequencing continuation for [[collectAll]]: drive `cur` to a value, append,
   * then keep draining ready elements in place. A failure — whether an element
   * is already failed or `cur` resolves to one — short-circuits without driving
   * the remaining inputs. The drain is a flat loop: completed elements never
   * re-enter `poll`, so stack depth and re-poll cost stay constant no matter
   * how many elements settle between wakeups.
   */
  private final class CollectAllPollable[A](
    private var cur: Pollable[A],
    it: Iterator[Async[A]],
    buf: scala.collection.mutable.ListBuffer[A]
  ) extends CancellationPropagatingPollable[List[A]](cur) {

    // Memoized terminal (the completed `List` or a short-circuit `Failure`). The
    // iterator and buffer are single-use, so once settled `cur` is nulled and
    // re-entering the drain would NPE / re-consume; a second poll — a re-poll or
    // a multi-consumer fan-out — returns this stable result instead.
    private var done: Async[List[A]] = null

    override protected def ownOutputOnIdleCancellation: Boolean = done == null

    def poll(onComplete: Runnable): Async[List[A]] = {
      if (done != null) return done
      if (cancellationRequested) return this
      while (true) {
        // `cur` can be null here under CONCURRENT fan-out (undefined — see the
        // slowPath memo note): another thread nulled it mid-drain. Don't
        // dereference it. If the terminal is already published, yield it;
        // otherwise the other thread is settling `done` right now, so re-arm
        // this caller's waker and stay pending — it re-polls promptly (instead
        // of parking on a wake that will never come, since the leaf is already
        // consumed) and observes `done` on the next poll. This keeps both the
        // crash (an NPE) and a lost-wakeup hang out of an undefined-but-
        // reachable race; a concurrently mutated buffer may still yield an
        // undefined list, the documented platform-specific (JVM-only) cost.
        val c = cur
        if (c == null) {
          val d = done
          if (d != null) return d
          onComplete.run()
          return this
        }
        val res = pollChild(c, onComplete)
        if (res.isInstanceOf[Failure]) {
          clearActive()
          done = res.asInstanceOf[Async[List[A]]]
          return done
        }
        if (res.isInstanceOf[Pollable[?]]) {
          val previous = c
          cur = res.asInstanceOf[Pollable[A]]
          if (!replaceActive(cur)) { ownCancelledOutput(); return this }
          if (cur.asInstanceOf[AnyRef] eq previous.asInstanceOf[AnyRef]) return this
        } else {
          clearActive()
          val continuation = beginContinuation(this)
          if (continuation == 0L) { ownCancelledOutput(); return this }
          try {
            buf += AsyncEncoding.deliverSuccess[A](res)
            cur = null
            while ((cur eq null) && it.hasNext) {
              val any = it.next().asInstanceOf[Any]
              if (any.isInstanceOf[Failure]) {
                done = any.asInstanceOf[Async[List[A]]]
                if (finishContinuation(continuation, done)) return this
                return done
              } else if (any.isInstanceOf[Pollable[_]]) cur = any.asInstanceOf[Pollable[A]]
              else buf += AsyncEncoding.deliverSuccess[A](any)
            }
            if (cur eq null) {
              done = Async.succeed(buf.toList)
              if (finishContinuation(continuation, done)) return this
              return done
            } else if (finishContinuation(continuation, cur)) {
              ownCancelledOutput()
              return this
            }
          } catch {
            case cause: Throwable =>
              finishContinuation(continuation, null)
              throw cause
          }
        }
      }
      throw new IllegalStateException("unreachable")
    }
  }

  /**
   * Slow-path implementation for suspended [[Async]] combinators. Called from
   * the inline extension methods after they establish
   * `fa.isInstanceOf[Pollable[?]]`.
   */
  private[async] object slowPath {

    /**
     * One [[AsyncEncoding.unwrapLayer]] before invoking a user continuation.
     */
    private def terminalValue(any: Any): Any = AsyncEncoding.unwrapLayer(any)

    // Poll-protocol note shared by every continuation pollable below: a `poll`
    // that returns a non-[[Failure]] [[Pollable]] is STILL PENDING — the result
    // is either the same instance (re-armed) or a replacement pollable that the
    // caller must advance to, exactly as the top-level drivers (`block`,
    // `start`, the interop runners) do. A terminal success is never encoded as
    // a bare `Pollable`: pollable-as-value successes travel as
    // `AsyncEncoding.WrappedPollable` carriers (which do not extend
    // [[Pollable]]). Combinators that have applied their user function and owe
    // the caller nothing further return their child's pending pollable
    // directly (a replacement), which keeps resume O(1) and collapses
    // continuation chains instead of growing one nesting level per suspension.

    /**
     * Sentinel for [[EnsuringPollable]]'s `outcome` slot meaning "`pa` has not
     * yet resolved". A raw `null` cannot be used: `pa` may legitimately resolve
     * to a `null` value, which would otherwise be re-read as "still pending"
     * forever.
     */
    private val NotResolved: AnyRef = new AnyRef

    /**
     * Slow-path implementation of `Async#map` when the input is suspended (or
     * failed). A [[Failure]] is propagated unchanged; any other [[Pollable]] is
     * wrapped in a continuation pollable.
     */
    def mapAsync[A, B](fa: Any, f: A => B): Async[B] =
      if (fa.isInstanceOf[Failure]) fa.asInstanceOf[Async[B]]
      else new MapPollable[A, B](fa.asInstanceOf[Pollable[A]], f)

    /**
     * Slow-path implementation of `Async#flatMap` when the input is suspended
     * (or failed). A [[Failure]] is propagated unchanged; any other
     * [[Pollable]] is wrapped in a continuation pollable.
     */
    def flatMapAsync[A, B](fa: Any, f: A => Async[B]): Async[B] =
      if (fa.isInstanceOf[Failure]) fa.asInstanceOf[Async[B]]
      else new FlatMapPollable[A, B](fa.asInstanceOf[Pollable[A]], f, flattenOutput = false)

    /**
     * Slow-path implementation of `Async#flatten`. Unlike an unrestricted
     * `flatMap(identity)`, cancellation must claim an inner pollable that the
     * outer computation published before the continuation accepted it.
     */
    def flattenAsync[A](ffa: Any): Async[A] =
      if (ffa.isInstanceOf[Failure]) ffa.asInstanceOf[Async[A]]
      else
        new FlatMapPollable[Async[A], A](
          ffa.asInstanceOf[Pollable[Async[A]]],
          (inner: Async[A]) => inner,
          flattenOutput = true
        )

    /**
     * Slow-path implementation of `Async#catchAll` when the input is suspended
     * (or failed). A [[Failure]] applies the handler; any other [[Pollable]] is
     * wrapped so the handler runs only if the pollable eventually fails.
     */
    def catchAllAsync[A, B >: A](fa: Any, f: Throwable => Async[B]): Async[B] =
      if (fa.isInstanceOf[Failure]) f(fa.asInstanceOf[Failure].cause)
      else new CatchAllPollable[A, B](fa.asInstanceOf[Pollable[A]], f)

    /** Fold a suspended success or failure with one continuation node. */
    def foldCauseAsync[A, B](fa: Any, onFailure: Throwable => B, onSuccess: A => B): Async[B] =
      new FoldCausePollable[A, B](fa.asInstanceOf[Pollable[A]], onFailure, onSuccess)

    /**
     * Slow-path `zipWith`: at least one input is suspended (or failed).
     * Allocate a [[Pollable]] that drives `fa` then `fb`. Failures from either
     * side are propagated. Both inputs are typed `Any` to absorb whichever
     * encoding case they happen to be (value, Pollable, or Failure).
     *
     * `zipWith` is strictly sequential left-to-right: `fa` is driven first and
     * `fb`'s failure is surfaced only once `fa` has succeeded. So a right that
     * is already a [[Failure]] short-circuits eagerly ONLY when the left is not
     * still pending (a `Failure` is itself a `Pollable`, so
     * `!fa.isInstanceOf[Pollable]` means `fa` is a ready value); a pending left
     * is driven first via [[ZipWithPollable]]. This keeps the (left-failed) and
     * (left-ready, right-failed) cases immediate while giving a pending left
     * the same left-to-right ordering as a pending-then-failing right.
     */
    def zipWithAsync[A, B, C](fa: Any, fb: Any, f: (A, B) => C): Async[C] =
      if (fa.isInstanceOf[Failure]) fa.asInstanceOf[Async[C]]
      else if (fb.isInstanceOf[Failure] && !fa.isInstanceOf[Pollable[?]]) fb.asInstanceOf[Async[C]]
      else new ZipWithPollable[A, B, C](fa, fb, f)

    /**
     * Ready-path `tap` after [[AsyncEncoding.deliverSuccess]] has unwrapped
     * carriers. `a` is the delivered user value: run `f(a)` for its effect,
     * then yield `a` unchanged. A pollable-as-value `a` is '''data''', not a
     * computation — it is carried through `runThenValue`/[[Async.succeed]]
     * exactly as `map`/`flatMap`/`zipWith`/`ensuring` and the pending
     * `tapAsync` path do, never driven (driving it would surface its poll
     * outcome instead of `a`, or hang on a non-settling value).
     */
    def tapReady[A](a: A, f: A => Async[Any]): Async[A] =
      runThenValue(f(a), a, suppressFailure = false)

    /** Slow-path `tap`: input is suspended (or failed). */
    def tapAsync[A](fa: Any, f: A => Async[Any]): Async[A] =
      if (fa.isInstanceOf[Failure]) fa.asInstanceOf[Async[A]]
      else
        new FlatMapPollable[A, A](
          fa.asInstanceOf[Pollable[A]],
          (a: A) => runThenValue[A](f(a), a, suppressFailure = false),
          flattenOutput = false
        )

    /**
     * Drive `target` for effects but settle to `observed` (pollable-as-value
     * tap must not replace the user [[Pollable]] with its polled scalar).
     */
    private final class ObservedPollable[A](target0: Pollable[A], observed: A)
        extends CancellationPropagatingPollable[A](target0) {
      private var target: Pollable[A]          = target0
      def poll(onComplete: Runnable): Async[A] = {
        if (cancellationRequested) return this
        while (true) {
          val previous = target
          val res      = pollChild(previous, onComplete)
          if (res.isInstanceOf[Failure]) { clearActive(); return res.asInstanceOf[Async[A]] }
          else if (res.isInstanceOf[Pollable[?]]) {
            target = res.asInstanceOf[Pollable[A]]
            if (!replaceActive(target)) return this
            if (target.asInstanceOf[AnyRef] eq previous.asInstanceOf[AnyRef]) return this
          } else {
            clearActive()
            return (
              if (cancellationRequested) this
              else Async.succeed(observed).asInstanceOf[Async[A]]
            )
          }
        }
        this
      }
    }

    /**
     * Drive the user [[Pollable]] stored as a ready success value for its
     * effects, settling to the pollable itself. Used by the platform `start`
     * runners so a pollable-as-value input is driven on the background
     * worker/microtask instead of blocking (or failing) the caller.
     */
    private[async] def observe[A](target: Pollable[A], observed: A): Pollable[A] =
      new ObservedPollable(target, observed)

    /**
     * Drive `fin` for its effect, then yield `a`. `fin` may be a value, a
     * [[Pollable]], or a [[Failure]]. If `suppressFailure` is `true`, a
     * `Failure` in `fin` is dropped (ensuring semantics). Otherwise it is
     * propagated as the overall result (tap semantics).
     */
    def runThenValue[A](fin: Any, a: A, suppressFailure: Boolean): Async[A] =
      if (fin.isInstanceOf[Failure])
        if (suppressFailure) Async.succeed(a).asInstanceOf[Async[A]]
        else fin.asInstanceOf[Async[A]]
      else if (fin.isInstanceOf[Pollable[?]])
        new RunThenValuePollable[A](fin.asInstanceOf[Pollable[Any]], a, suppressFailure)
      else Async.succeed(a).asInstanceOf[Async[A]]

    /**
     * Slow-path `ensuring`: drive `fa` to completion (value or failure), then
     * run `finalizer` (ignoring its failures), then propagate the original
     * outcome.
     */
    def ensuringAsync[A](fa: Any, finalizer: Async[Any]): Async[A] =
      new EnsuringPollable[A](fa.asInstanceOf[Pollable[A]], NotResolved, null, finalizer)

    /**
     * Ready-primary ensuring lifecycle used only when the finalizer suspends.
     */
    def ensuringReady[A](a: A, finalizer: Pollable[Any]): Async[A] =
      new EnsuringPollable[A](null, Async.succeed(a), finalizer, finalizer)

    /**
     * Drive a suspended pollable to its value, parking the calling thread
     * between polls. Allocated once per `await` call (not per poll iteration).
     * If the pollable ever resolves to a [[Failure]], its `cause` is thrown.
     *
     *   - '''JVM:''' parks on a [[java.util.concurrent.locks.ReentrantLock]]
     *     `Condition`, which (unlike `synchronized`) does not pin a virtual
     *     thread's carrier under Project Loom.
     *   - '''Scala.js:''' there is no thread to park. Each suspension is given
     *     one chance to complete synchronously inside `poll`; if the waker has
     *     not fired by the time `poll` returns, throws
     *     [[IllegalStateException]].
     *
     * Must run on a non-reactor thread on the JVM — calling it from inside a
     * `poll` deadlocks the loop.
     */
    /**
     * Drive `target` for effects; return `observed` (pollable-as-value
     * `.block`).
     */
    private def awaitObservedPollable[A](target: Pollable[A], observed: A): A =
      awaitSuspended(new ObservedPollable(target, observed))

    /**
     * Drive `fa` to its value. Bare [[Pollable]] encodings are suspended
     * computations; `AsyncEncoding.WrappedPollable` drives the stored user
     * [[Pollable]] for effects but exits with pollable identity.
     */
    def block[A](fa: Any): A =
      if (fa.isInstanceOf[AsyncEncoding.WrappedPollable]) {
        val w        = fa.asInstanceOf[AsyncEncoding.WrappedPollable]
        val out: Any = AsyncEncoding.unwrapLayer(fa) // one delivery layer, depth-aware
        // Only a depth-1 carrier exposes the user pollable itself as the
        // delivered value, so only then is it driven for effects. A deeper
        // carrier (nested `succeed`) delivers a shallower carrier untouched —
        // peeling more than one layer here would skip nesting levels.
        if (w.depth > 1 || w.value.isInstanceOf[Failure]) out.asInstanceOf[A]
        else awaitObservedPollable(w.value.asInstanceOf[Pollable[A]], out.asInstanceOf[A])
      } else if (fa.isInstanceOf[Failure])
        Failure.throwCause(fa.asInstanceOf[Failure].cause)
      else if (fa.isInstanceOf[Pollable[?]])
        awaitSuspended(fa.asInstanceOf[Pollable[A]])
      else fa.asInstanceOf[A]

    /** Alias for CPS / interop call sites that take `Any`. */
    def blockGeneric[A](fa: Any): A = block[A](fa)

    def awaitSuspended[A](pa0: Pollable[A]): A = {
      if (pa0.isInstanceOf[Failure]) Failure.throwCause(pa0.asInstanceOf[Failure].cause)

      val parker = PlatformAsync.newParker()

      @tailrec def loop(cur: Pollable[A]): A = {
        parker.reset() // reset under the parker's lock — no lost wakeup
        val next: Async[A] = cur.poll(parker.onComplete)
        if (next.isInstanceOf[Failure])
          Failure.throwCause(next.asInstanceOf[Failure].cause)
        else if (next.isInstanceOf[Pollable[?]]) {
          val replacement = next.asInstanceOf[Pollable[A]]
          // A distinct replacement has not necessarily registered this
          // parker's callback. Poll it immediately; only the same identity may
          // rely on the callback permit before being polled again.
          if (replacement.asInstanceOf[AnyRef] eq cur.asInstanceOf[AnyRef])
            parker.park() // JVM: blocks until wake(); JS: throws if not already woken
          loop(replacement)
        } else AsyncEncoding.deliverSuccess[A](next)
      }

      try loop(pa0)
      finally parker.release() // return a pooled parker to its per-thread slot
    }

    /**
     * `map` continuation: `f` produces a plain '''value''' (never a nested
     * `Async` to flatten), so the result is always lifted via [[Async.succeed]]
     * — exactly like the ready path. A [[Pollable]] returned by `f` (including
     * a combinator-built `Async` held as data) is a pollable-as-value, never a
     * computation to drive; flattening belongs to `flatMap`.
     */
    // Each continuation pollable memoizes its settled terminal in a `done` slot
    // and short-circuits to it on a subsequent poll. This is DEFENSIVE, not a
    // contract change: [[Pollable]] still documents re-polling after a terminal
    // as undefined and potentially platform-specific. It makes a SEQUENTIAL
    // re-drive of the same `Async` idempotent — a consumer that polls after an
    // earlier drive has already settled (a re-poll, or a single-threaded event
    // loop fanning the same value out to several continuations) observes the
    // memoized result instead of re-running the user function or re-consuming
    // `collectAll`'s spent iterator.
    //
    // The memo is a plain `var`, NOT a synchronization primitive: it does NOT
    // make CONCURRENT fan-out safe. Driving the same raw `Async` from two
    // threads at once (e.g. two `fa.start`s, or `fa.start` racing `fa.block`,
    // on the JVM) is undefined — the user function may fire more than once and
    // (for `collectAll`) the shared buffer/iterator may be observed mid-drain.
    // This cannot occur on Scala.js (single-threaded), so it is exactly the
    // "platform-specific" case [[Pollable]] already disclaims. To fan a value
    // out to several CONCURRENT consumers, share the [[Async.Running]] handle
    // from `fa.start` — its result is published once through an atomic, so the
    // underlying `Async` is driven exactly once — rather than re-driving the
    // raw `Async`. Making the memo itself thread-safe was measured to cost a
    // per-pollable allocation (an `AtomicReference`, ~+63% bytes/op on the
    // suspended path) plus a settle-time claim, and was rejected in favor of
    // this documented contract.
    //
    // The null check is a perfectly-predicted branch dwarfed by the `pa.poll`
    // it guards, the `done` field fits the object's alignment padding (0 extra
    // bytes/op under compressed oops), and the ready/fast path never allocates
    // these pollables at all — measured free single-threaded and at -t 16.

    private[async] trait ContinuationNode {
      def prepareContinuationPoll(): Long
      def continuationPollSource: Pollable[?]
      def continuationImmediate: Any
      def finishContinuationPoll(ownership: Long, result: Any, onComplete: Runnable): Any
      def abortContinuationPoll(ownership: Long): Unit
    }

    /**
     * Polls an accumulated map/flatMap spine without recursively invoking each
     * wrapper's `poll`. Every node still owns and memoizes its own transition;
     * this loop only replaces the native call stack used to reach the leaf and
     * unwind its result.
     */
    private[async] object ContinuationInterpreter {
      private final val Open = 0
      private final val Done = 1

      private final class PollScratch {
        var frames     = new Array[ContinuationNode](8)
        var ownerships = new Array[Long](8)
        var length     = 0

        def append(node: ContinuationNode, ownership: Long): Unit = {
          if (length == frames.length) {
            val nextFrames     = new Array[ContinuationNode](length * 2)
            val nextOwnerships = new Array[Long](length * 2)
            Array.copy(frames, 0, nextFrames, 0, length)
            Array.copy(ownerships, 0, nextOwnerships, 0, length)
            frames = nextFrames
            ownerships = nextOwnerships
          }
          frames(length) = node
          ownerships(length) = ownership
          length += 1
        }

        def clear(): Unit = {
          var i = 0
          while (i < length) {
            frames(i) = null
            ownerships(i) = 0L
            i += 1
          }
          length = 0
        }
      }

      private val runtimeLock   = new AnyRef
      private val activeByOwner = new java.util.IdentityHashMap[AnyRef, CancellationBatch]
      private var sequence      = 0L

      private[async] final case class Task(batch: CancellationBatch, sequence: Long, effect: Async[Unit])
      private final case class Finalization(
        claims: List[PropagatingCancellationClaim],
        results: List[Completer[Unit]],
        failures: List[(Long, Throwable)]
      )

      private[async] final class CancellationBatch(val initialOwner: AnyRef) {
        var parent: CancellationBatch = this
        var rank                      = 0
        var state                     = Open
        var builders                  = 0
        var pending                   = 0
        var observed                  = false
        var nextTask                  = 0
        val claims                    = new scala.collection.mutable.ArrayBuffer[PropagatingCancellationClaim](8)
        val results                   = new scala.collection.mutable.ArrayBuffer[Completer[Unit]](2)
        val tasks                     = new scala.collection.mutable.ArrayBuffer[Task](8)
        val failures                  = new scala.collection.mutable.ArrayBuffer[(Long, Throwable)](2)
      }

      private final class BatchJoin(val batch: CancellationBatch, val result: Completer[Unit]) extends Pollable[Unit] {
        def poll(onComplete: Runnable): Async[Unit] = {
          val current = Async.currentCancellationBatch match {
            case value: CancellationBatch => value
            case _                        => null
          }
          if ((current ne null) && link(current, batch)) Async.succeed(())
          else {
            observe(batch)
            result.poll(onComplete)
          }
        }

        override private[async] def cancelWithCleanup(): Async[Unit] = Async.succeed(())
      }

      private def find(batch: CancellationBatch): CancellationBatch = {
        var root = batch
        while (root.parent ne root) root = root.parent
        var current = batch
        while (current.parent ne current) {
          val next = current.parent
          current.parent = root
          current = next
        }
        root
      }

      private def union(left: CancellationBatch, right: CancellationBatch): CancellationBatch = {
        var a = find(left)
        var b = find(right)
        if (a eq b) a
        else {
          require(a.state == Open && b.state == Open, "cannot union a finalized cancellation batch")
          if (a.rank < b.rank) { val swap = a; a = b; b = swap }
          b.parent = a
          if (a.rank == b.rank) a.rank += 1
          a.builders += b.builders
          a.pending += b.pending
          a.observed = a.observed || b.observed
          a.claims ++= b.claims
          a.results ++= b.results
          a.tasks ++= b.tasks.drop(b.nextTask)
          a.failures ++= b.failures
          b.claims.foreach(_.batch = a)
          a
        }
      }

      private def link(current: CancellationBatch, target: CancellationBatch): Boolean = {
        val linked = runtimeLock.synchronized {
          val a = find(current)
          val b = find(target)
          if ((a.state == Done) || (b.state == Done)) null
          else if (a eq b) a
          else union(a, b)
        }
        if (linked ne null) { activate(linked); true }
        else false
      }

      private def selectBatch(owner: AnyRef): (CancellationBatch, Completer[Unit]) = runtimeLock.synchronized {
        val contextual = Async.currentCancellationBatch match {
          case value: CancellationBatch if find(value).state == Open => find(value)
          case _                                                     => null
        }
        val owned = if ((contextual eq null) && (owner ne null)) activeByOwner.get(owner) else null
        val batch =
          if (contextual ne null) contextual
          else if ((owned ne null) && find(owned).state == Open) find(owned)
          else {
            val created = new CancellationBatch(owner)
            if (owner ne null) activeByOwner.put(owner, created)
            created
          }
        val result = new Completer[Unit]
        batch.results += result
        batch.builders += 1
        (batch, result)
      }

      private def submit(batch: CancellationBatch, effect: Async[Unit]): Unit = {
        val starts = runtimeLock.synchronized {
          val root = find(batch)
          sequence += 1L
          root.tasks += Task(root, sequence, effect)
          if (root.observed) {
            val queued = root.tasks.drop(root.nextTask).toList
            root.nextTask = root.tasks.length
            root.pending += queued.length
            queued
          } else Nil
        }
        starts.foreach(startTask)
      }

      private def registerClaim(batch: CancellationBatch, claim: PropagatingCancellationClaim): Unit = {
        val root = runtimeLock.synchronized {
          val value = find(batch)
          claim.batch = value
          value.claims += claim
          value
        }
        claim.joined.primary(Async.succeed(()))
        if (claim.pollInFlight == 0L) claim.joined.noReplacement()
        submit(root, claim.joined.effect)
      }

      private def attach(
        batch: CancellationBatch,
        dependency: CancellationBatch,
        replay: Async[Unit]
      ): CancellationBatch = {
        val (root, effect) = runtimeLock.synchronized {
          val current = find(batch)
          val target  = find(dependency)
          if (current eq target) (current, null)
          else if (target.state == Done) (current, replay)
          else (union(current, target), null)
        }
        if (effect != null) submit(root, effect)
        activate(root)
        root
      }

      private def collect(batch0: CancellationBatch, initial: Pollable[?]): CancellationBatch = {
        var batch     = batch0
        var current   = initial
        var searching = true
        while (searching && current.isInstanceOf[CancellationPropagatingPollable[?]]) {
          val node = current.asInstanceOf[CancellationPropagatingPollable[?]]
          node.claimCancellation(batch) match {
            case Left(existing) =>
              batch = attach(batch, existing.batch, existing.completion)
              searching = false
            case Right(claimed) =>
              registerClaim(batch, claimed)
              current = claimed.current
              if (current eq null) searching = false
          }
        }
        if (searching && (current ne null)) {
          val cleanup = Async.withCancellationBatch(runtimeLock.synchronized(find(batch))) {
            try current.cancelWithCleanup()
            catch { case cause: Throwable => Async.fail(cause) }
          }
          cleanup match {
            case join: BatchJoin =>
              batch = attach(batch, join.batch, join.result)
            case _ => submit(batch, cleanup)
          }
        }
        val root = runtimeLock.synchronized(find(batch))
        activate(root)
        root
      }

      private def finishBuilder(batch: CancellationBatch): Unit = {
        val finalization = runtimeLock.synchronized {
          val root = find(batch)
          root.builders -= 1
          tryFinalize(root)
        }
        complete(finalization)
      }

      private def activate(batch: CancellationBatch): Unit = {
        val starts = runtimeLock.synchronized {
          val root = find(batch)
          if (root.observed) {
            val queued = root.tasks.drop(root.nextTask).toList
            root.nextTask = root.tasks.length
            root.pending += queued.length
            queued
          } else Nil
        }
        starts.foreach(startTask)
      }

      private def observe(batch: CancellationBatch): Unit = {
        val (starts, finalization) = runtimeLock.synchronized {
          val root = find(batch)
          root.observed = true
          val queued = root.tasks.drop(root.nextTask).toList
          root.nextTask = root.tasks.length
          root.pending += queued.length
          (queued, tryFinalize(root))
        }
        starts.foreach(startTask)
        complete(finalization)
      }

      private def startTask(task: Task): Unit = {
        val owner = Async.currentExecutionOwner
        val batch = runtimeLock.synchronized(find(task.batch))
        driveTask(task, task.effect, owner, batch)
      }

      private def driveTask(
        task: Task,
        start: Async[Unit],
        owner: AnyRef,
        batch: CancellationBatch
      ): Unit =
        Async.withExecutionOwner(owner) {
          Async.withCancellationBatch(runtimeLock.synchronized(find(batch))) {
            var current  = start
            var continue = true
            while (continue) {
              Async.foldStep(current)(new Async.StepFold[Unit, Unit] {
                def success(value: Unit): Unit = {
                  continue = false
                  taskCompleted(task, Right(value))
                }
                def failure(cause: Throwable): Unit = {
                  continue = false
                  taskCompleted(task, Left(cause))
                }
                def pending(value: Pollable[Unit]): Unit = {
                  val state = new java.util.concurrent.atomic.AtomicInteger(0)
                  val wake  = new Runnable {
                    def run(): Unit = {
                      val previous = state.getAndSet(2)
                      if (previous == 1) driveTask(task, value, owner, batch)
                    }
                  }
                  val next =
                    try value.poll(wake)
                    catch { case cause: Throwable => Async.fail(cause) }
                  if (next.asInstanceOf[AnyRef] eq value.asInstanceOf[AnyRef]) {
                    if (state.compareAndSet(0, 1)) continue = false
                    else current = value
                  } else current = next
                }
              })
            }
          }
        }

      private def taskCompleted(task: Task, result: Either[Throwable, Unit]): Unit = {
        val finalization = runtimeLock.synchronized {
          val root = find(task.batch)
          result match {
            case Left(cause) => root.failures += ((task.sequence, cause))
            case Right(_)    => ()
          }
          root.pending -= 1
          tryFinalize(root)
        }
        complete(finalization)
      }

      private def tryFinalize(batch: CancellationBatch): Finalization = {
        val root = find(batch)
        if (
          root.state == Open && root.observed && root.builders == 0 && root.pending == 0 &&
          root.nextTask == root.tasks.length
        ) {
          root.state = Done
          val entries = activeByOwner.entrySet().iterator()
          while (entries.hasNext) {
            val entry = entries.next()
            if (find(entry.getValue) eq root) entries.remove()
          }
          Finalization(root.claims.toList, root.results.toList, root.failures.toList.sortBy(_._1))
        } else null
      }

      private def complete(finalization: Finalization): Unit =
        if (finalization ne null) {
          val failure = finalization.failures match {
            case Nil                        => null
            case (_, primary) :: suppressed =>
              suppressed.foreach { case (_, cause) =>
                if ((primary ne null) && (cause ne null) && (primary ne cause)) primary.addSuppressed(cause)
              }
              primary
          }
          finalization.claims.foreach { claim =>
            if (failure eq null) claim.completion.succeed(()) else claim.completion.fail(failure)
          }
          finalization.results.foreach { result =>
            if (failure eq null) result.succeed(()) else result.fail(failure)
          }
        }

      private[async] def collectReplacement(batch: CancellationBatch, replacement: Pollable[?]): Unit = {
        val root = runtimeLock.synchronized {
          val value = find(batch)
          if (value.state == Open) { value.builders += 1; value }
          else null
        }
        if (root ne null)
          try collect(root, replacement)
          finally finishBuilder(root)
      }

      private[Async] def cancel(root: CancellationPropagatingPollable[?]): Async[Unit] = {
        val owner           = Async.currentExecutionOwner
        val (batch, result) = selectBatch(owner)
        val collected       =
          try collect(batch, root)
          finally finishBuilder(batch)
        new BatchJoin(collected, result)
      }

      def poll(root: ContinuationNode, onComplete: Runnable): Any = {
        val cached  = PlatformAsync.takeContinuationScratch()
        val scratch = if (cached eq null) new PollScratch else cached.asInstanceOf[PollScratch]
        try {
          var result: Any = root
          var driveAgain  = true
          while (driveAgain) {
            scratch.clear()
            var current: Any = root
            var descending   = true
            while (descending && current.isInstanceOf[ContinuationNode]) {
              val node      = current.asInstanceOf[ContinuationNode]
              val ownership = node.prepareContinuationPoll()
              if (ownership != 0L) {
                scratch.append(node, ownership)
                current = node.continuationPollSource
              } else {
                current = node.continuationImmediate
                descending = false
              }
            }
            if (descending && current.isInstanceOf[Pollable[?]] && !current.isInstanceOf[Failure]) {
              try current = current.asInstanceOf[Pollable[Any]].poll(onComplete)
              catch {
                case cause: Throwable =>
                  var i = scratch.length - 1
                  while (i >= 0) {
                    scratch.frames(i).abortContinuationPoll(scratch.ownerships(i)); i -= 1
                  }
                  throw cause
              }
            }
            var maskedDistinct = false
            var i              = scratch.length - 1
            try
              while (i >= 0) {
                val frame               = scratch.frames(i)
                val source              = frame.continuationPollSource
                val childResult         = current
                val distinctReplacement =
                  childResult.isInstanceOf[Pollable[?]] &&
                    !childResult.isInstanceOf[Failure] &&
                    (childResult.asInstanceOf[AnyRef] ne source.asInstanceOf[AnyRef])
                current = frame.finishContinuationPoll(scratch.ownerships(i), childResult, onComplete)
                if (distinctReplacement && (current.asInstanceOf[AnyRef] eq frame.asInstanceOf[AnyRef]))
                  maskedDistinct = true
                i -= 1
              }
            catch {
              case cause: Throwable =>
                while (i >= 0) {
                  scratch.frames(i).abortContinuationPoll(scratch.ownerships(i)); i -= 1
                }
                throw cause
            } finally {
              var pending = scratch.length - 1
              while (pending >= 0) {
                scratch.frames(pending).abortContinuationPoll(scratch.ownerships(pending))
                pending -= 1
              }
            }
            result = current
            driveAgain = maskedDistinct
          }
          result
        } finally {
          scratch.clear()
          PlatformAsync.releaseContinuationScratch(scratch)
        }
      }
    }

    private[async] def cancelContinuation(root: Pollable[?]): Async[Unit] =
      ContinuationInterpreter.cancel(root.asInstanceOf[CancellationPropagatingPollable[?]])

    private final class MapPollable[A, B](pa0: Pollable[A], f: A => B)
        extends CancellationPropagatingPollable[B](pa0)
        with ContinuationNode {

      private var pa: Pollable[A]          = pa0
      @volatile private var done: Async[B] = null
      @volatile private var doneSet        = false

      override protected def ownOutputOnIdleCancellation: Boolean = !doneSet

      def poll(onComplete: Runnable): Async[B] =
        ContinuationInterpreter.poll(this, onComplete).asInstanceOf[Async[B]]

      def prepareContinuationPoll(): Long =
        if (!doneSet && !cancellationRequested) beginChildPoll(pa) else 0L

      def continuationPollSource: Pollable[?] = pa

      def continuationImmediate: Any = if (doneSet) done else this

      def abortContinuationPoll(ownership: Long): Unit = finishChildPoll(ownership, null)

      def finishContinuationPoll(ownership: Long, result: Any, onComplete: Runnable): Any = {
        val res = result
        if (res.isInstanceOf[Failure]) {
          done = res.asInstanceOf[Async[B]]; doneSet = true; clearActive(); finishChildPoll(ownership, res); done
        } else if (res.isInstanceOf[Pollable[?]]) {
          finishChildPoll(ownership, res)
          pa = res.asInstanceOf[Pollable[A]]
          if (cancellationRequested) { ownCancelledOutput(); return this }
          this
        } else {
          val continuation = handoffToContinuation(ownership, this)
          if (continuation == 0L) {
            ownCancelledOutput(); finishChildPoll(ownership, res); return this
          }
          done =
            try Async.succeed(f(terminalValue(res).asInstanceOf[A])).asInstanceOf[Async[B]]
            catch {
              case t: Throwable =>
                finishContinuation(continuation, null)
                throw t
            }
          doneSet = true
          if (finishContinuation(continuation, done)) return this
          done
        }
      }
    }

    private final class FoldCausePollable[A, B](
      pa0: Pollable[A],
      onFailure: Throwable => B,
      onSuccess: A => B
    ) extends CancellationPropagatingPollable[B](pa0)
        with ContinuationNode {

      private var pa: Pollable[A]          = pa0
      @volatile private var done: Async[B] = null
      @volatile private var doneSet        = false

      override protected def ownOutputOnIdleCancellation: Boolean = !doneSet

      def poll(onComplete: Runnable): Async[B] =
        ContinuationInterpreter.poll(this, onComplete).asInstanceOf[Async[B]]

      def prepareContinuationPoll(): Long =
        if (!doneSet && !cancellationRequested) beginChildPoll(pa) else 0L

      def continuationPollSource: Pollable[?] = pa

      def continuationImmediate: Any = if (doneSet) done else this

      def abortContinuationPoll(ownership: Long): Unit = finishChildPoll(ownership, null)

      def finishContinuationPoll(ownership: Long, result: Any, onComplete: Runnable): Any = {
        val res = result
        if (res.isInstanceOf[Pollable[?]] && !res.isInstanceOf[Failure]) {
          finishChildPoll(ownership, res)
          pa = res.asInstanceOf[Pollable[A]]
          if (cancellationRequested) { ownCancelledOutput(); return this }
          this
        } else {
          val continuation = handoffToContinuation(ownership, this)
          if (continuation == 0L) {
            ownCancelledOutput(); finishChildPoll(ownership, res); return this
          }
          done = try {
            val value =
              if (res.isInstanceOf[Failure]) onFailure(res.asInstanceOf[Failure].cause)
              else onSuccess(terminalValue(res).asInstanceOf[A])
            Async.succeed(value)
          } catch { case cause: Throwable => new Failure(cause) }
          doneSet = true
          if (finishContinuation(continuation, done)) this
          else { clearActive(); done }
        }
      }
    }

    private final class FlatMapPollable[A, B](pa0: Pollable[A], f: A => Async[B], flattenOutput: Boolean)
        extends CancellationPropagatingPollable[B](pa0)
        with ContinuationNode {

      private var pa: Pollable[A]          = pa0
      @volatile private var done: Async[B] = null
      @volatile private var doneSet        = false

      override protected def ownOutputOnIdleCancellation: Boolean = !doneSet

      def poll(onComplete: Runnable): Async[B] =
        ContinuationInterpreter.poll(this, onComplete).asInstanceOf[Async[B]]

      def prepareContinuationPoll(): Long =
        if (!doneSet && !cancellationRequested) beginChildPoll(pa) else 0L

      def continuationPollSource: Pollable[?] = pa

      def continuationImmediate: Any = if (doneSet) done else this

      def abortContinuationPoll(ownership: Long): Unit = finishChildPoll(ownership, null)

      def finishContinuationPoll(ownership: Long, result: Any, onComplete: Runnable): Any = {
        val res = result
        if (res.isInstanceOf[Failure]) {
          done = res.asInstanceOf[Async[B]]; doneSet = true; clearActive(); finishChildPoll(ownership, res); done
        }                                         // pa failed: propagate
        else if (res.isInstanceOf[Pollable[?]]) { // pa still pending; it re-armed onComplete itself
          finishChildPoll(ownership, res)
          pa = res.asInstanceOf[Pollable[A]]
          if (cancellationRequested) { ownCancelledOutput(); return this }
          this
        } else {
          val continuation = handoffToContinuation(ownership, this)
          if (continuation == 0L) {
            ownCancelledOutput(); finishChildPoll(ownership, res, flattenOutput); return this
          }
          val a    = terminalValue(res).asInstanceOf[A]
          val fRes =
            try f(a) // f(a): Async[B] — may be ready, suspend, fail, or throw
            catch {
              case t: Throwable =>
                finishContinuation(continuation, null)
                throw t
            }
          done = fRes
          doneSet = true
          if (fRes.isInstanceOf[Failure]) {
            if (finishContinuation(continuation, fRes)) this else done
          } else if (fRes.isInstanceOf[Pollable[?]]) {
            val inner = fRes.asInstanceOf[Pollable[B]]
            // f suspended: drive it once and memoize the result — terminal OR a
            // pending replacement. Caching the replacement is what makes a
            // re-poll/fan-out idempotent: a second consumer gets the SAME inner
            // pollable to follow (itself idempotent) instead of re-applying `f`.
            // A single driver still follows the replacement directly, so the
            // chain still collapses (this pollable drops out of its path).
            if (!handoffContinuationToChild(continuation, inner)) {
              finishContinuation(continuation, inner); return this
            }
            done = inner
            doneSet = true
            inner
          } else {
            if (finishContinuation(continuation, fRes)) this else { clearActive(); fRes }
          } // f finished synchronously (may carry WrappedPollable for pollable-as-value)
        }
      }
    }

    /**
     * Recovery continuation: drive `pa` to a value or failure; on success
     * propagate the value; on failure invoke `f` — guarded like the ready path,
     * so a synchronously-throwing handler surfaces as a [[Failure]] — and hand
     * any pending recovery pollable to the caller as a replacement.
     */
    private final class CatchAllPollable[A, B >: A](pa0: Pollable[A], f: Throwable => Async[B])
        extends CancellationPropagatingPollable[B](pa0)
        with ContinuationNode {

      private var pa: Pollable[A]          = pa0
      @volatile private var done: Async[B] = null // memoized terminal (see MapPollable note)
      @volatile private var doneSet        = false

      def poll(onComplete: Runnable): Async[B] =
        ContinuationInterpreter.poll(this, onComplete).asInstanceOf[Async[B]]

      def prepareContinuationPoll(): Long =
        if (!doneSet && !cancellationRequested) beginChildPoll(pa) else 0L

      def continuationPollSource: Pollable[?] = pa

      def continuationImmediate: Any =
        if (doneSet) done
        else if (cancellationRequested) observeCancelledActive(this)
        else this

      def abortContinuationPoll(ownership: Long): Unit = finishChildPoll(ownership, null)

      def finishContinuationPoll(ownership: Long, result: Any, onComplete: Runnable): Any = {
        val res = result
        if (res.isInstanceOf[Failure]) {
          val continuation = handoffToContinuation(ownership, this)
          if (continuation == 0L) {
            ownCancelledOutput(); finishChildPoll(ownership, res); return this
          }
          val recovered =
            try f(res.asInstanceOf[Failure].cause)
            catch { case cause: Throwable => new Failure(cause) }
          done = recovered.asInstanceOf[Async[B]]
          doneSet = true
          if (recovered.isInstanceOf[Failure]) {
            if (finishContinuation(continuation, recovered)) this else done
          } else if (recovered.isInstanceOf[Pollable[?]]) {
            val inner = recovered.asInstanceOf[Pollable[B]]
            if (!handoffContinuationToChild(continuation, inner)) {
              finishContinuation(continuation, inner); this
            } else inner
          } else if (finishContinuation(continuation, recovered)) this
          else done
        } else if (res.isInstanceOf[Pollable[?]]) {
          finishChildPoll(ownership, res)
          pa = res.asInstanceOf[Pollable[A]]
          this
        } else {
          done = res.asInstanceOf[Async[B]]
          doneSet = true
          finishChildPoll(ownership, res)
          done
        }
      }
    }

    /**
     * Drive `fa` to a value, then `fb` to a value, then `f(a, b)`. Either side
     * being a [[Failure]] is propagated immediately. `faSt` keeps the resolved
     * left side's '''raw terminal encoding''' (value or `WrappedPollable`
     * carrier) — `faResolved` marks it settled so a pollable-as-value left is
     * never re-dispatched as a suspended computation on a later poll, and the
     * leaf for `fa` is never re-polled after it yields its value, even while
     * `fb` is still pending. Unwrapping to user values happens once, at the
     * combine.
     */
    private final class ZipWithPollable[A, B, C](fa0: Any, fb0: Any, f: (A, B) => C)
        extends CancellationPropagatingPollable[C](
          if (fa0.isInstanceOf[Pollable[?]] && !fa0.isInstanceOf[Failure]) fa0.asInstanceOf[Pollable[?]]
          else if (fb0.isInstanceOf[Pollable[?]] && !fb0.isInstanceOf[Failure]) fb0.asInstanceOf[Pollable[?]]
          else null
        ) {

      // `Any` because either field can hold value | Pollable | Failure at start.
      private var faSt: Any                = fa0
      private var fbSt: Any                = fb0
      private var faResolved               = false
      @volatile private var done: Async[C] = null // memoized terminal (see MapPollable note)
      @volatile private var doneSet        = false

      override protected def ownOutputOnIdleCancellation: Boolean = !doneSet

      def poll(onComplete: Runnable): Async[C] = {
        if (doneSet) return done
        if (cancellationRequested) return this
        // Resolve fa first to a terminal encoding or surface a failure/suspension.
        if (!faResolved) {
          if (faSt.isInstanceOf[Failure]) { done = faSt.asInstanceOf[Async[C]]; doneSet = true; return done }
          var driveLeft = true
          while (driveLeft && faSt.isInstanceOf[Pollable[?]]) {
            val previous = faSt.asInstanceOf[Pollable[A]]
            val next     = pollChild(previous, onComplete)
            if (next.isInstanceOf[Failure]) {
              clearActive(); done = next.asInstanceOf[Async[C]]; doneSet = true; return done
            }
            if (next.isInstanceOf[Pollable[?]]) {
              faSt = next
              if (!replaceActive(next.asInstanceOf[Pollable[A]])) { ownCancelledOutput(); return this }
              if (next.asInstanceOf[AnyRef] eq previous.asInstanceOf[AnyRef]) return this
            } else { faSt = next; driveLeft = false }
          }
          faResolved = true
          clearActive()
          if (cancellationRequested) { ownCancelledOutput(); return this }
        }
        // fa is terminal now; resolve fb the same way (poll is one-shot, so this
        // section runs at most until fb settles — no flag needed).
        if (fbSt.isInstanceOf[Failure]) { done = fbSt.asInstanceOf[Async[C]]; doneSet = true; return done }
        var driveRight = true
        while (driveRight && fbSt.isInstanceOf[Pollable[?]]) {
          val right = fbSt.asInstanceOf[Pollable[B]]
          if (!replaceActive(right)) { ownCancelledOutput(); return this }
          val next = pollChild(right, onComplete)
          if (next.isInstanceOf[Failure]) { done = next.asInstanceOf[Async[C]]; doneSet = true; return done }
          if (next.isInstanceOf[Pollable[?]]) {
            fbSt = next
            if (!replaceActive(next.asInstanceOf[Pollable[B]])) { ownCancelledOutput(); return this }
            if (next.asInstanceOf[AnyRef] eq right.asInstanceOf[AnyRef]) return this
          } else { fbSt = next; driveRight = false }
        }
        clearActive()
        val continuation = beginContinuation(this)
        if (continuation == 0L) { ownCancelledOutput(); return this }
        done =
          try
            Async
              .succeed(f(terminalValue(faSt).asInstanceOf[A], terminalValue(fbSt).asInstanceOf[B]))
              .asInstanceOf[Async[C]]
          catch {
            case cause: Throwable =>
              finishContinuation(continuation, null)
              throw cause
          }
        doneSet = true
        if (finishContinuation(continuation, done)) this else done
      }
    }

    /**
     * Drive a Pollable `fin` for its effect, then yield `a`. If
     * `suppressFailure` is true and `fin` resolves to a [[Failure]], the
     * failure is suppressed; otherwise it is propagated as the result.
     */
    private final class RunThenValuePollable[A](fin: Pollable[Any], a: A, suppressFailure: Boolean)
        extends CancellationPropagatingPollable[A](fin) {

      private var st: Any          = fin
      private var done: Async[A]   = null
      private var doneSet: Boolean = false

      def poll(onComplete: Runnable): Async[A] = {
        if (doneSet) return done
        if (cancellationRequested) {
          done = Async.succeed(a).asInstanceOf[Async[A]]
          doneSet = true
          return done
        }
        var driveFinalizer = true
        while (driveFinalizer && st.isInstanceOf[Pollable[?]] && !st.isInstanceOf[Failure]) {
          try {
            val previous = st.asInstanceOf[Pollable[Any]]
            val next     = pollChild(previous, onComplete)
            if (!next.isInstanceOf[Failure] && next.isInstanceOf[Pollable[?]]) {
              st = next // still pending (possibly a replacement pollable)
              if (cancellationRequested) {
                done = Async.succeed(a).asInstanceOf[Async[A]]
                doneSet = true
                return this
              }
              if (!replaceActive(next.asInstanceOf[Pollable[Any]])) return this
              if (next.asInstanceOf[AnyRef] eq previous.asInstanceOf[AnyRef]) return this
            } else { st = next; driveFinalizer = false }
          } catch {
            case t: Throwable =>
              // A throwing finalizer poll is the finalizer's failure channel:
              // suppressed (encoded, so a pollable-as-value `a` stays a settled
              // carrier) or propagated, mirroring the resolved-Failure arm below.
              val continuation = beginContinuation(this)
              if (continuation == 0L) {
                done = Async.succeed(a).asInstanceOf[Async[A]]
                doneSet = true
                return this
              } else if (suppressFailure) {
                done = Async.succeed(a).asInstanceOf[Async[A]]
                doneSet = true
                if (finishContinuation(continuation, done)) return this
                return done
              } else if (finishContinuation(continuation, new Failure(Failure.unwindCause(t)))) {
                done = Async.succeed(a).asInstanceOf[Async[A]]
                doneSet = true
                return this
              } else throw t
          }
        }
        clearActive()
        val continuation = beginContinuation(this)
        if (continuation == 0L) {
          done = Async.succeed(a).asInstanceOf[Async[A]]
          doneSet = true
          return this
        }
        done =
          if (st.isInstanceOf[Failure])
            if (suppressFailure) Async.succeed(a).asInstanceOf[Async[A]]
            else st.asInstanceOf[Async[A]]
          else Async.succeed(a).asInstanceOf[Async[A]]
        doneSet = true
        if (finishContinuation(continuation, done)) {
          done = Async.succeed(a).asInstanceOf[Async[A]]
          this
        } else done
      }
    }

    /**
     * Drive `pa` to a value or failure, run `finalizer`, then propagate the
     * original outcome. Failures from the finalizer are suppressed. The
     * `outcome` memo holds the resolved value-or-failure so that, once the
     * finalizer is being driven, we don't re-poll `pa`.
     */
    private final class EnsuringPollable[A](
      pa0: Pollable[A],
      outcome0: Any,
      finSt0: Any,
      finalizer: Async[Any]
    ) extends Pollable[A]
        with CancelledReplacementProvenance {

      private var pa: Pollable[A] = pa0
      // NotResolved while pa is still pending; otherwise holds the resolved
      // Async[A] (which is either a raw value — possibly null — or a Failure).
      private var outcome: Any = outcome0
      // null until we start driving the finalizer; then holds its current state.
      private var finSt: Any                                             = finSt0
      private var cancelled                                              = false
      private var polling                                                = false
      private var waiter: Runnable                                       = null
      private var cleanup: Any                                           = null
      private var primaryJoin: CancellationCleanup                       = null
      private var primaryPolling                                         = false
      private var retainedOutcomeOnCancel                                = false
      private var cancellationProvenance: CancelledReplacementProvenance = null

      def wasCancelledReplacement(pollable: Pollable[?]): Boolean =
        if ((cancellationProvenance ne null) && cancellationProvenance.wasCancelledReplacement(pollable)) true
        else
          pa match {
            case provenance: CancelledReplacementProvenance => provenance.wasCancelledReplacement(pollable)
            case _                                          => false
          }

      private def acquire(onComplete: Runnable): Boolean = synchronized {
        if (polling) {
          waiter = onComplete
          false
        } else {
          polling = true
          true
        }
      }

      private def release(): Unit = {
        val wake = synchronized {
          polling = false
          val w = waiter
          waiter = null
          w
        }
        if (wake ne null)
          try wake.run()
          catch { case _: Throwable => () }
      }

      private def isCancelled: Boolean = synchronized(cancelled)

      def poll(onComplete: Runnable): Async[A] = {
        val (hasCancelledResult, cancelledResult) = synchronized {
          if (cancelled && retainedOutcomeOnCancel && finSt == null) (true, outcome.asInstanceOf[Async[A]])
          else (false, null.asInstanceOf[Async[A]])
        }
        if (hasCancelledResult) return cancelledResult
        if (isCancelled || !acquire(onComplete)) return this
        try pollOwned(onComplete)
        finally release()
      }

      private def pollOwned(onComplete: Runnable): Async[A] = {
        if (isCancelled) return this
        while ((outcome.asInstanceOf[AnyRef] eq NotResolved) && !isCancelled) {
          val drivePrimary = synchronized {
            if (cancelled) false
            else {
              primaryPolling = true
              true
            }
          }
          if (!drivePrimary) return this
          val primary = pa
          // A throw escaping `pa.poll` is the primary's failure channel (the
          // top-level drivers reify it the same way); the finalizer must still
          // run before that failure propagates.
          val next =
            try primary.poll(onComplete)
            catch { case t: Throwable => new Failure(Failure.unwindCause(t)) }
          val joined = synchronized {
            primaryPolling = false
            if (cancelled) {
              primary match {
                case provenance: CancelledReplacementProvenance => cancellationProvenance = provenance
                case _                                          =>
              }
              if (next.isInstanceOf[Failure] || !next.isInstanceOf[Pollable[?]]) {
                outcome = next
                finSt = finalizer
                retainedOutcomeOnCancel = true
              } else pa = next.asInstanceOf[Pollable[A]]
              primaryJoin
            } else {
              if (!next.isInstanceOf[Failure] && next.isInstanceOf[Pollable[?]])
                pa = next.asInstanceOf[Pollable[A]]
              else {
                outcome = next
                finSt = finalizer
              }
              null
            }
          }
          if (joined ne null) {
            if (
              !next.isInstanceOf[Failure] && next.isInstanceOf[Pollable[?]] &&
              (next.asInstanceOf[AnyRef] ne primary.asInstanceOf[AnyRef])
            ) {
              val replacement      = next.asInstanceOf[Pollable[?]]
              val alreadyCancelled = primary match {
                case provenance: CancelledReplacementProvenance => provenance.wasCancelledReplacement(replacement)
                case _                                          => false
              }
              if (alreadyCancelled) joined.noReplacement()
              else joined.replacement(replacement)
            } else joined.noReplacement()
          }
          if (!next.isInstanceOf[Failure] && next.isInstanceOf[Pollable[?]]) {
            if (next.asInstanceOf[AnyRef] eq primary.asInstanceOf[AnyRef]) return this
          }
        }
        if (isCancelled) return this
        // pa is done; drive finalizer (ignoring its failure) and propagate outcome.
        while (finSt.isInstanceOf[Pollable[?]]) {
          // including a Failure pollable, which we want to *suppress*
          if (!finSt.isInstanceOf[Failure]) {
            val fin = finSt.asInstanceOf[Pollable[Any]]
            try {
              val next = fin.poll(onComplete)
              if (!next.isInstanceOf[Failure] && next.isInstanceOf[Pollable[?]]) {
                finSt = next // still pending (possibly a replacement pollable)
                if (next.asInstanceOf[AnyRef] eq fin.asInstanceOf[AnyRef]) return this
              } else finSt = next
            } catch {
              case t: Throwable =>
                val cancellationOwnsFailure = synchronized {
                  if (cancelled) { finSt = new Failure(t); true }
                  else { finSt = null; false }
                }
                if (!cancellationOwnsFailure && outcome.isInstanceOf[Failure]) {
                  val primaryCause = outcome.asInstanceOf[Failure].cause
                  if ((primaryCause ne null) && (primaryCause ne t) && (t ne null))
                    try primaryCause.addSuppressed(t)
                    catch { case _: Throwable => () }
                }
            }
          } else return finishEnsuringOutcome()
        }
        if (isCancelled) return this
        finishEnsuringOutcome()
      }

      private def finishEnsuringOutcome(): Async[A] = {
        if (isCancelled) return this
        // If the original outcome was itself a failure and the finalizer also
        // failed, keep the finalizer's cause reachable as a suppressed exception
        // on the primary rather than dropping it silently. `Throwable
        // .addSuppressed` itself throws — `IllegalArgumentException` on self-
        // suppression (same instance) and `NullPointerException` on a null
        // argument — and `fail(null)` accepts a null cause, so guard all three:
        // distinct, non-null primary, non-null finalizer. The primary outcome
        // must always win; a finalizer-cause we cannot attach is simply dropped.
        if (finSt.isInstanceOf[Failure]) {
          val finalizerCause          = finSt.asInstanceOf[Failure].cause
          val cancellationOwnsFailure = synchronized {
            if (cancelled) true
            else { finSt = null; false }
          }
          if (!cancellationOwnsFailure && outcome.isInstanceOf[Failure]) {
            val primaryCause = outcome.asInstanceOf[Failure].cause
            if ((primaryCause ne finalizerCause) && (primaryCause ne null) && (finalizerCause ne null))
              primaryCause.addSuppressed(finalizerCause)
          }
        }
        // `outcome` is a terminal encoding — a Failure, a raw value, or a
        // settled WrappedPollable carrier — and propagates as-is.
        outcome.asInstanceOf[Async[A]]
      }

      override def cancel(): Unit = { cancelWithCleanup().start; () }

      override private[async] def cancelWithCleanup(): Async[Unit] = {
        val claimed = synchronized {
          if (cleanup != null) Left(cleanup.asInstanceOf[Async[Unit]])
          else if (!(outcome.asInstanceOf[AnyRef] eq NotResolved) && finSt == null)
            Left(Async.succeed(()))
          else {
            cancelled = true
            retainedOutcomeOnCancel = !(outcome.asInstanceOf[AnyRef] eq NotResolved)
            val join = if (outcome.asInstanceOf[AnyRef] eq NotResolved) new CancellationCleanup else null
            primaryJoin = join
            val c = new EnsuringCleanup(if (join eq null) Async.succeed(()) else join.effect)
            cleanup = c
            Right((c, join, pa, primaryPolling))
          }
        }
        claimed match {
          case Left(existing)                          => existing
          case Right((c, join, primary, pollInFlight)) =>
            if (join ne null) {
              val primaryCleanup =
                try primary.cancelWithCleanup()
                catch { case t: Throwable => Async.fail(t) }
              join.primary(primaryCleanup)
              if (!pollInFlight) join.noReplacement()
            }
            c
        }
      }

      /**
       * Cancellation owns the same poll permit as normal evaluation. Thus an
       * already-running finalizer poll is allowed to return before cleanup
       * continues, and no finalizer poll can be duplicated concurrently.
       */
      private final class EnsuringCleanup(initialPrimaryCleanup: Async[Unit]) extends Pollable[Unit] {
        private var primaryCleanup: Any     = initialPrimaryCleanup
        private var primaryFailure: Failure = null
        private var primaryDone             = false
        private var result: Async[Unit]     = null
        private var resultSet               = false

        def poll(onComplete: Runnable): Async[Unit] = {
          if (resultSet) return result
          if (!acquire(onComplete)) return this
          try {
            if (!primaryDone) {
              var drivePrimaryCleanup = true
              while (
                drivePrimaryCleanup && !primaryCleanup.isInstanceOf[Failure] &&
                primaryCleanup.isInstanceOf[Pollable[?]]
              ) {
                val current = primaryCleanup.asInstanceOf[Pollable[Unit]]
                val next    =
                  try current.poll(onComplete)
                  catch { case t: Throwable => new Failure(t) }
                primaryCleanup = next
                if (!next.isInstanceOf[Failure] && next.isInstanceOf[Pollable[?]]) {
                  if (next.asInstanceOf[AnyRef] eq current.asInstanceOf[AnyRef]) return this
                } else drivePrimaryCleanup = false
              }
              if (primaryCleanup.isInstanceOf[Failure]) primaryFailure = primaryCleanup.asInstanceOf[Failure]
              primaryDone = true
            }
            if (outcome.asInstanceOf[AnyRef] eq NotResolved) {
              outcome = new CancelledPollableObserver[A](pa, EnsuringPollable.this)
              finSt = finalizer
              retainedOutcomeOnCancel = true
            }
            var driveFinalizer = true
            while (driveFinalizer && finSt.isInstanceOf[Pollable[?]] && !finSt.isInstanceOf[Failure]) {
              val current = finSt.asInstanceOf[Pollable[Any]]
              val next    =
                try current.poll(onComplete)
                catch { case t: Throwable => new Failure(t) }
              finSt = next
              if (!next.isInstanceOf[Failure] && next.isInstanceOf[Pollable[?]]) {
                if (next.asInstanceOf[AnyRef] eq current.asInstanceOf[AnyRef]) return this
              } else driveFinalizer = false
            }
            val finalizerFailure =
              if (finSt.isInstanceOf[Failure]) finSt.asInstanceOf[Failure]
              else null
            result = if (primaryFailure ne null) {
              if (finalizerFailure ne null) {
                val primaryCause   = primaryFailure.cause
                val finalizerCause = finalizerFailure.cause
                if ((primaryCause ne finalizerCause) && (primaryCause ne null) && (finalizerCause ne null))
                  try primaryCause.addSuppressed(finalizerCause)
                  catch { case _: Throwable => () }
              }
              primaryFailure.asInstanceOf[Async[Unit]]
            } else if (finalizerFailure ne null) finalizerFailure.asInstanceOf[Async[Unit]]
            else Async.succeed(())
            finSt = null
            resultSet = true
            result
          } finally release()
        }
      }
    }
  }
}
