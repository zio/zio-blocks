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

package golem

import scala.concurrent.Future
import scala.scalajs.js

/**
 * These helpers convert between Scala `Future` and JS `Promise`, and provide a
 * small `Either -> Future` adapter.
 */
object FutureInterop {
  def fromPromise[A](promise: js.Promise[A]): Future[A] =
    golem.runtime.util.FutureInterop.fromPromise(promise)

  def toPromise[A](future: Future[A]): js.Promise[A] =
    golem.runtime.util.FutureInterop.toPromise(future)

  def fromEither[A](either: Either[String, A]): Future[A] =
    golem.runtime.util.FutureInterop.fromEither(either)

  def failed[A](message: String): Future[A] =
    golem.runtime.util.FutureInterop.failed(message)
}
