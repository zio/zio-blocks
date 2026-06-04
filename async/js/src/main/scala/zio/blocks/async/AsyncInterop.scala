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
 * [[toFuture]] is non-blocking — JavaScript is single-threaded, so the
 * conversion is driven by a microtask continuation rather than a worker thread.
 * The polling loop is the same as `AsyncSlowPath.awaitSuspended` but threaded
 * through `Promise.resolve(...).then(...)` instead of a parker.
 *
 * [[fromJsPromise]] / [[toJsPromise]] route through Scala.js's
 * [[scala.scalajs.js.JSConverters]] bridge so the type juggling of the native
 * `Thenable` union types stays inside the standard library.
 */
object AsyncInterop {

  /**
   * Construct an [[Async]] that mirrors `future`. Already-completed futures
   * collapse to a raw value or an [[Async.fail]]; otherwise the completion is
   * routed through a [[Completer]] using `ExecutionContext.parasitic` so the
   * callback fires inline on the microtask that completed the future.
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

  /** Construct an [[Async]] that mirrors `promise`. */
  def fromJsPromise[A](promise: js.Promise[A]): Async[A] =
    fromFuture(promise.toFuture)

  /**
   * Convert `fa` into a [[scala.concurrent.Future]]. Already-resolved values
   * and failures collapse synchronously; suspended pollables are driven by a
   * microtask polling loop because JavaScript cannot block. `ec` is the
   * execution context used to schedule the per-poll microtask continuations.
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
   * carrier. Routed through [[toFuture]] and Scala.js's `toJSPromise` bridge,
   * so the JS-side `Thenable` semantics match what Scala.js produces for any
   * other `Future[A]`.
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
      val waker = new Waker {
        def wake(): Unit =
          // Re-enter the loop on the next microtask; the captured `fa` will
          // re-poll the same pollable, which (under the encoding) returns
          // either a value, the same pollable, or a Failure.
          js.Promise
            .resolve[Unit](())
            .toFuture
            .onComplete { _ =>
              drive(fa, p)
            }(ec)
      }
      val next = any.asInstanceOf[Pollable[A]].poll(waker)
      val nany = next.asInstanceOf[Any]
      if (nany.isInstanceOf[Failure]) { p.failure(nany.asInstanceOf[Failure].cause); () }
      else if (nany.isInstanceOf[Pollable[_]]) () // wait for waker
      else { p.success(nany.asInstanceOf[A]); () }
    } else { p.success(any.asInstanceOf[A]); () }
  }
}
