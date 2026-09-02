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
 * Scala.js platform sliver of the [[Async]] companion: constructors that
 * ingress the standard JS async carriers ([[scala.concurrent.Future]] and
 * [[scala.scalajs.js.Promise]]). Mixed into `object Async`, so callers write
 * `Async.fromFuture(...)` / `Async.fromJsPromise(...)`. The egress direction
 * lives on the `fa.toFuture` / `fa.toJsPromise` extension methods.
 */
private[async] trait AsyncCompanionPlatformSpecific {

  /**
   * Construct an [[Async]] that completes with the same value or error as
   * `future`.
   */
  def fromFuture[A](future: Future[A]): Async[A] = AsyncInterop.fromFuture(future)

  /**
   * Construct an [[Async]] that completes with the same value or error as
   * `promise`.
   */
  def fromJsPromise[A](promise: js.Promise[A]): Async[A] = AsyncInterop.fromJsPromise(promise)
}
