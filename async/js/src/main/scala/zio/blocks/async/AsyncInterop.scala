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

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.scalajs.js
import scala.scalajs.js.JSConverters._
import scala.util.{Failure => SFailure, Success => SSuccess}
import zio.blocks.async.internal.PlatformAsync

/**
 * Converts between [[Async]] and the standard asynchronous types of Scala.js:
 * Scala's [[scala.concurrent.Future]] and JavaScript's
 * [[scala.scalajs.js.Promise]]. Use these conversions to hand an `Async` to a
 * JavaScript API, or to bring a promise from one into `Async` code.
 *
 * Callers reach them through `Async.fromFuture` and `Async.fromJsPromise` on
 * the companion and the `toFuture` and `toJsPromise` extension methods, which
 * forward here. Each direction keeps the value or the failure, and none of them
 * blocks. The JVM has the same `Future` conversions, with `CompletionStage` and
 * `CompletableFuture` in place of `js.Promise`.
 *
 * {{{
 * // no-check: Scala.js only
 * import scala.scalajs.js
 *
 * val port: Async[Int]      = Async.fromJsPromise(js.Promise.resolve[Int](8080))
 * val next: js.Promise[Int] = port.map(_ + 1).toJsPromise // resolves to 8081
 * }}}
 *
 * @see
 *   [[https://zioblocks.com/reference/async/#integration-points Integration Points]]
 */
private[async] object AsyncInterop {

  /** Implements `Async.fromFuture`. */
  def fromFuture[A](future: Future[A]): Async[A] = {
    val cur = future.value
    if (cur.isDefined) cur.get match {
      case SSuccess(v) => Async.succeed(v)
      case SFailure(t) => Async.fail(ingressCause(t))
    }
    else
      Async.promiseInternal[A] { c =>
        future.onComplete {
          case SSuccess(v) => c.succeed(v)
          case SFailure(t) => c.fail(ingressCause(t))
        }(ExecutionContext.parasitic)
      }
  }

  /** Implements `Async.fromJsPromise`. */
  def fromJsPromise[A](promise: js.Promise[A]): Async[A] =
    fromFuture(promise.toFuture)

  /** Implements the `toFuture` extension method. */
  def toFuture[A](fa: Async[A])(implicit ec: ExecutionContext): Future[A] = {
    val any = fa.asInstanceOf[Any]
    if (any.isInstanceOf[Failure]) failFuture(any.asInstanceOf[Failure].cause)
    // `requiresDriver` also routes a depth-1 pollable-as-value carrier through
    // the non-blocking driver: delivering it drives the user pollable for
    // effects, which may suspend — and JS cannot block for that.
    else if (AsyncEncoding.requiresDriver(any)) {
      val p = Promise[A]()
      drive(fa, p)
      p.future
    } else
      try Future.successful(Async.slowPath.block[A](fa))
      catch { case t: Throwable => failFuture(Failure.unwindCause(t)) }
  }

  /**
   * Implements the `toJsPromise` extension method, driving `fa` on
   * `JSExecutionContext.queue`.
   */
  def toJsPromise[A](fa: Async[A]): js.Promise[A] = {
    implicit val queue: ExecutionContext = scala.scalajs.concurrent.JSExecutionContext.queue
    toFuture(fa).toJSPromise
  }

  /**
   * Polls `fa` until it settles and completes `p` with the outcome. The first
   * poll runs in the caller; each later one runs on `ec`, except that after
   * `PlatformAsync.ReadyResumptionLimit` resumptions in a row without a fresh
   * wake it yields through a `setTimeout` macrotask.
   */
  private def drive[A](fa: Async[A], p: Promise[A])(implicit ec: ExecutionContext): Unit = {
    val any = fa.asInstanceOf[Any]
    if (any.isInstanceOf[Failure]) failPromise(p, any.asInstanceOf[Failure].cause)
    else if (AsyncEncoding.requiresDriver(any)) {
      // `current` holds the latest pollable; `step` advances it to the pollable
      // returned by `poll`, exactly like the JVM driver (`Async.block`) loops on
      // the *returned* pollable rather than re-polling the original. A single
      // shared waker re-enters `step` through `ec` (or `setTimeout`), so a stale waker
      // captured by an earlier suspension still resumes the current state.
      // A depth-1 pollable-as-value carrier is driven for effects through an
      // observing pollable that settles to the user pollable itself.
      var current: Pollable[A] =
        if (any.isInstanceOf[AsyncEncoding.WrappedPollable]) {
          val w = any.asInstanceOf[AsyncEncoding.WrappedPollable]
          Async.slowPath.observe(w.value.asInstanceOf[Pollable[A]], w.value.asInstanceOf[A])
        } else any.asInstanceOf[Pollable[A]]
      // `settled` makes resumption idempotent: a pollable may fire its waker
      // more than once (a legitimate spurious / multi-source wakeup), scheduling
      // several resumption microtasks. Without this guard a redundant `step`
      // would re-poll the completed pollable and re-complete `p`, throwing
      // `IllegalStateException: Promise already completed`. Mirrors the JVM
      // driver, which collapses multiple wakeups and stops polling after a value.
      var settled = false
      // Re-arm through a cached `Runnable` handed to `ec.execute` (a microtask
      // when `ec` is `JSExecutionContext.queue`), instead of allocating a
      // Promise + Future + `Try` bridge per wakeup. `step` already guards on
      // `settled`, so a redundant resumption stays idempotent.
      var signalled                                     = false
      var polling                                       = false
      var wakePending                                   = false
      var readyResumptions                              = 0
      var yieldScheduled                                = false
      def requestStep(freshWake: Boolean = false): Unit =
        if (!settled && !signalled) {
          if (freshWake) readyResumptions = 0
          signalled = true
          readyResumptions += 1
          yieldScheduled = readyResumptions >= PlatformAsync.ReadyResumptionLimit
          if (yieldScheduled) PlatformAsync.schedule(resume, forceMacrotask = true)
          else ec.execute(resume)
        }
      lazy val resume: Runnable = new Runnable {
        def run(): Unit = {
          if (yieldScheduled) readyResumptions = 0
          yieldScheduled = false
          signalled = false
          step()
        }
      }
      lazy val onComplete: Runnable = new Runnable {
        def run(): Unit =
          if (polling) wakePending = true
          else requestStep(freshWake = true)
      }
      def step(): Unit =
        if (!settled) {
          // A throwing `poll` (on the initial or any resumption microtask) must
          // surface as a failed `Promise`, not be thrown to the caller / orphan
          // the future — matching the JVM driver and the JS `start` runner,
          // both of which funnel a thrown `poll` into the failure path.
          polling = true
          wakePending = false
          val next =
            try current.poll(onComplete)
            catch {
              case t: Throwable =>
                polling = false
                settled = true
                failPromise(p, Failure.unwindCause(t))
                return
            }
          polling = false
          val nany = next.asInstanceOf[Any]
          if (nany.isInstanceOf[Failure]) { settled = true; failPromise(p, nany.asInstanceOf[Failure].cause) }
          else if (nany.isInstanceOf[Pollable[_]]) {
            val replacement = nany.asInstanceOf[Pollable[A]]
            if (replacement ne current) {
              current = replacement
              // A distinct replacement is synchronous driver progress. It has
              // not necessarily registered this callback, so schedule its
              // first poll rather than waiting for a wake it never promised.
              requestStep()
            } else if (wakePending) requestStep()
          } else { settled = true; p.success(AsyncEncoding.deliverSuccess[A](nany)); () }
        }
      step()
    } else
      try { p.success(Async.slowPath.block[A](any)); () }
      catch { case t: Throwable => failPromise(p, Failure.unwindCause(t)) }
  }

  /**
   * Unwrap Scala.js `JavaScriptException` from raw `js.Promise.reject(null)`.
   */
  private def ingressCause(t: Throwable): Throwable =
    Failure.unwindCause(t match {
      case e: js.JavaScriptException if e.exception == null => null
      case other                                            => other
    })

  /**
   * A failed `Future`, with `Failure.NullCauseMarker` in place of a `null`
   * cause, which `fromFuture` turns back into `null`.
   */
  private def failFuture[A](cause: Throwable): Future[A] =
    if (cause eq null) Future.failed(Failure.NullCauseMarker)
    else Future.failed(cause)

  /**
   * Fails `promise`, with `Failure.NullCauseMarker` in place of a `null` cause.
   */
  private def failPromise[A](promise: Promise[A], cause: Throwable): Unit = {
    if (cause eq null) promise.failure(Failure.NullCauseMarker)
    else promise.failure(cause)
    ()
  }
}
