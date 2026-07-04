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

import scala.concurrent.ExecutionContext
import scala.scalajs.js

import zio.blocks.async.*

/**
 * Runtime support for the Scala 3.8+ JS native direct-style backend
 * ([[AsyncDirect]]). Every value crossing the `js.Promise` transport travels
 * inside a [[AsyncJsRuntime.Box]]: JS promise resolution '''adopts''' thenable
 * values (resolving a promise with another promise awaits the inner one), so a
 * `js.Promise`-as-value success would otherwise be silently replaced by its
 * settled value. Boxing also keeps the promises' element type non-`Unit`,
 * sidestepping the Scala 3.8.3 `js.await(js.Promise[Unit])` compiler
 * limitation.
 *
 * The members are referenced from macro-generated code spliced into user
 * compilation units, hence the public visibility despite the internal package.
 */
object AsyncJsRuntime {

  /** Opaque transport wrapper — see the class doc above. */
  final class Box[A](val value: A)

  /**
   * Sentinel for the single synchronous-outcome cell of a native `js.async`
   * block ([[AsyncDirect]] readiness wrapper): distinguishes "the body
   * suspended (still pending)" from a settled success [[Box]] or a settled
   * failure `Throwable`, so one captured ref replaces the former four. A shared
   * immutable object — never allocated per block.
   */
  val Unsettled: AnyRef = new AnyRef

  private implicit def queue: ExecutionContext = scala.scalajs.concurrent.JSExecutionContext.queue

  /**
   * `Async` → boxed promise, for the `js.await` leg of a rewritten `.await`.
   */
  def toBoxedPromise[A](fa: Async[A]): js.Promise[Box[A]] = {
    import scala.scalajs.js.JSConverters.*
    AsyncInterop.toFuture(fa).map(new Box(_)).toJSPromise
  }

  /** Boxed promise → `Async`, for a block result that genuinely suspended. */
  def fromBoxedPromise[A](p: js.Promise[Box[A]]): Async[A] =
    AsyncInterop.fromJsPromise(p).map(_.value)

  /** One delivery unwrap of a ready encoding (a public forwarder). */
  def deliver[A](encoded: Any): A = AsyncEncoding.deliverSuccess[A](encoded)

  /**
   * Re-throw a ready failure's cause at the await site, synchronously — a ready
   * `Failure` must not ride the promise transport (which costs a mandatory
   * microtask and turns a ready block into a pending one). The throw is caught
   * by the enclosing user `try`/`catch` or by the `js.async` wrapper's
   * completion capture, both synchronously, exactly like the DCA cells. A
   * `null` cause travels as `NullCauseMarker` and is decoded back by
   * [[readyFailure]].
   */
  def rethrowReadyFailure(failed: Any): Nothing =
    Failure.throwCause(failed.asInstanceOf[Failure].cause)

  /**
   * A body that threw before its first suspension settles to a ready failure,
   * exactly like the other backends (`NullCauseMarker` decoded back to a null
   * cause).
   */
  def readyFailure[A](t: Throwable): Async[A] =
    Async.fail(Failure.unwindCause(t)).asInstanceOf[Async[A]]

  /**
   * Attach a no-op handler to a promise whose rejection has already been
   * reported through another channel (the synchronous-throw fast path), so the
   * JS engine does not flag an unhandled rejection.
   */
  def discardRejection(p: js.Promise[?]): Unit = {
    p.toFuture.onComplete(_ => ())
    ()
  }
}
