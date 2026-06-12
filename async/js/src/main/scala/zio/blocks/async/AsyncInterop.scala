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

/**
 * Scala.js-only interop between [[Async]] and the standard async types: Scala's
 * [[scala.concurrent.Future]] and JavaScript's [[scala.scalajs.js.Promise]].
 *
 * Both directions preserve success and failure. All conversions are
 * non-blocking, as required on JavaScript. The JVM offers the analogous
 * `Future` conversions plus `CompletionStage` / `CompletableFuture` interop in
 * place of `js.Promise`.
 */
object AsyncInterop {

  /**
   * Construct an [[Async]] that completes with the same value or error as
   * `future`.
   */
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

  /**
   * Construct an [[Async]] that completes with the same value or error as
   * `promise`.
   */
  def fromJsPromise[A](promise: js.Promise[A]): Async[A] =
    fromFuture(promise.toFuture)

  /**
   * Convert `fa` into a [[scala.concurrent.Future]] that completes with the
   * same value or error. If `fa` is not yet complete it is driven without
   * blocking, so this call returns immediately and the future completes when
   * `fa` does. `ec` schedules the continuations.
   */
  def toFuture[A](fa: Async[A])(implicit ec: ExecutionContext): Future[A] = {
    val any = fa.asInstanceOf[Any]
    if (any.isInstanceOf[Failure]) failFuture(any.asInstanceOf[Failure].cause)
    // `requiresDriver` also routes a depth-1 pollable-as-value carrier through
    // the microtask driver: delivering it drives the user pollable for
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
   * Convert `fa` into a [[scala.scalajs.js.Promise]] — the canonical JS async
   * carrier — that resolves with `fa`'s value or rejects with its error.
   */
  def toJsPromise[A](fa: Async[A]): js.Promise[A] = {
    implicit val queue: ExecutionContext = scala.scalajs.concurrent.JSExecutionContext.queue
    toFuture(fa).toJSPromise
  }

  /**
   * Drive `fa` through to a value via microtask polls; complete `p` with the
   * result. The waker re-enters the loop on a `Promise.resolve().then(...)`
   * microtask so the surrounding event loop stays responsive.
   */
  private def drive[A](fa: Async[A], p: Promise[A])(implicit ec: ExecutionContext): Unit = {
    val any = fa.asInstanceOf[Any]
    if (any.isInstanceOf[Failure]) failPromise(p, any.asInstanceOf[Failure].cause)
    else if (AsyncEncoding.requiresDriver(any)) {
      // `current` holds the latest pollable; `step` advances it to the pollable
      // returned by `poll`, exactly like the JVM driver (`Async.block`) loops on
      // the *returned* pollable rather than re-polling the original. A single
      // shared waker re-enters `step` on the next microtask, so a stale waker
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
      var settled                   = false
      lazy val onComplete: Runnable = new Runnable {
        def run(): Unit =
          js.Promise
            .resolve[Unit](())
            .toFuture
            .onComplete(_ => step())(ec)
      }
      def step(): Unit =
        if (!settled) {
          // A throwing `poll` (on the initial or any resumption microtask) must
          // surface as a failed `Promise`, not be thrown to the caller / orphan
          // the future — matching the JVM driver and the JS `start` runner
          // runner, both of which funnel a thrown `poll` into the failure path.
          val next =
            try current.poll(onComplete)
            catch { case t: Throwable => settled = true; failPromise(p, Failure.unwindCause(t)); return }
          val nany = next.asInstanceOf[Any]
          if (nany.isInstanceOf[Failure]) { settled = true; failPromise(p, nany.asInstanceOf[Failure].cause) }
          else if (nany.isInstanceOf[Pollable[_]]) {
            current = nany.asInstanceOf[Pollable[A]]; ()
          } // advance; wait for waker
          else { settled = true; p.success(AsyncEncoding.deliverSuccess[A](nany)); () }
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

  /** Scala `Future` / `Promise.failure` reject a `null` exception. */
  private def failFuture[A](cause: Throwable): Future[A] =
    if (cause eq null) Future.failed(Failure.NullCauseMarker)
    else Future.failed(cause)

  private def failPromise[A](promise: Promise[A], cause: Throwable): Unit = {
    if (cause eq null) promise.failure(Failure.NullCauseMarker)
    else promise.failure(cause)
    ()
  }
}
