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
      case SFailure(t) => Async.fail(t)
    }
    else
      Async.promiseInternal[A] { c =>
        future.onComplete {
          case SSuccess(v) => c.succeed(v)
          case SFailure(t) => c.fail(t)
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
    if (any.isInstanceOf[Failure]) Future.failed(any.asInstanceOf[Failure].cause)
    else if (any.isInstanceOf[Pollable[_]]) {
      val p = Promise[A]()
      drive(fa, p)
      p.future
    } else Future.successful(any.asInstanceOf[A])
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
    if (any.isInstanceOf[Failure]) { p.failure(any.asInstanceOf[Failure].cause); () }
    else if (any.isInstanceOf[Pollable[_]]) {
      // `current` holds the latest pollable; `step` advances it to the pollable
      // returned by `poll`, exactly like the JVM driver (`Async.block`) loops on
      // the *returned* pollable rather than re-polling the original. A single
      // shared waker re-enters `step` on the next microtask, so a stale waker
      // captured by an earlier suspension still resumes the current state.
      var current: Pollable[A] = any.asInstanceOf[Pollable[A]]
      lazy val waker: Waker    = new Waker {
        def wake(): Unit =
          js.Promise
            .resolve[Unit](())
            .toFuture
            .onComplete(_ => step())(ec)
      }
      def step(): Unit = {
        val next = current.poll(waker)
        val nany = next.asInstanceOf[Any]
        if (nany.isInstanceOf[Failure]) { p.failure(nany.asInstanceOf[Failure].cause); () }
        else if (nany.isInstanceOf[Pollable[_]]) {
          current = nany.asInstanceOf[Pollable[A]]; ()
        } // advance; wait for waker
        else { p.success(nany.asInstanceOf[A]); () }
      }
      step()
    } else { p.success(any.asInstanceOf[A]); () }
  }
}
