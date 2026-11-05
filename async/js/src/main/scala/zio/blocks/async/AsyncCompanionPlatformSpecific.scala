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

import scala.concurrent.Future
import scala.scalajs.js

/**
 * The Scala.js constructors of the [[Async]] companion that bring in a
 * [[scala.concurrent.Future]] or a [[scala.scalajs.js.Promise]]. `object Async`
 * mixes this trait in, so callers write `Async.fromFuture(...)` and
 * `Async.fromJsPromise(...)`. The `toFuture` and `toJsPromise` extension
 * methods convert the other way.
 */
private[async] trait AsyncCompanionPlatformSpecific {

  /**
   * Creates an [[Async]] that completes with the value or the failure of
   * `future`. It needs no `ExecutionContext`. A future that has already
   * completed gives a ready `Async`; otherwise the `Async` completes when
   * `future` does. On Scala.js, a `JavaScriptException` that wraps `null` (from
   * a JavaScript promise rejected with `null`) becomes a `null` cause.
   *
   * Cancelling a run of this `Async` does not reach `future`, a known bug that
   * the built-in bridges share.
   *
   * {{{
   * import scala.concurrent.Future
   *
   * val quote: Async[Int] = Async.fromFuture(Future.successful(125))
   * quote.map(_ * 2).block // 250
   * }}}
   *
   * @see
   *   [[https://zioblocks.com/reference/async/#integration-points Integration Points]]
   */
  def fromFuture[A](future: Future[A]): Async[A] = AsyncInterop.fromFuture(future)

  /**
   * Creates an [[Async]] that completes with the value or the failure of
   * `promise`. The `Async` is pending even when `promise` has already settled,
   * because a JavaScript promise reports its outcome on a later microtask, so
   * `block` on it throws: drive it with `toFuture`, `toJsPromise` or
   * `Async.async`.
   *
   * A rejection with a `Throwable` fails with that `Throwable`, a rejection
   * with any other value fails with a [[scala.scalajs.js.JavaScriptException]]
   * that wraps it, and a rejection with `null` fails with a `null` cause.
   * Cancelling a run of this `Async` does not reach `promise`, a known bug that
   * the built-in bridges share.
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
  def fromJsPromise[A](promise: js.Promise[A]): Async[A] = AsyncInterop.fromJsPromise(promise)
}
