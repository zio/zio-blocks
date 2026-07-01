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

/**
 * Failure outcome of an [[Async]]. Constructed via [[Async.fail]] (or by
 * [[Completer.fail]], or captured from thrown code by [[Async.attempt]]);
 * recovered via the `.catchAll` / `.mapError` / `.orElse` extensions or
 * surfaced as a thrown `Throwable` by `.block`.
 *
 * A failure is terminal: once an [[Async]] has failed, it stays failed with the
 * same `cause` unless a handler such as `.catchAll` recovers it.
 *
 * NOTE: errors thrown by user code inside `.map(f)` / `.flatMap(f)` are NOT
 * captured — `Async` is eager, so a `throw` in `f` escapes through the call
 * site before any later `.catchAll` runs. To convert thrown exceptions into a
 * Failure, wrap the work in [[Async.attempt]].
 */
final class Failure(val cause: Throwable) extends Pollable[Nothing] {
  def poll(onComplete: Runnable): Async[Nothing] = this
}

private[async] object Failure {

  /**
   * Thrown by [[throwCause]] when the logical failure cause is `null`. The JVM
   * and Scala.js backends cannot `throw null`, so this marker reifies the same
   * channel `.either` exposes as `Left(null)`.
   */
  object NullCauseMarker extends Throwable(null, null, false, false) with scala.util.control.NoStackTrace {
    override def fillInStackTrace(): Throwable = this
  }

  /**
   * Re-throw a failure cause for `.block` / `awaitSuspended`, including `null`.
   */
  def throwCause(cause: Throwable): Nothing =
    if (cause eq null) throw NullCauseMarker
    else throw cause

  /** Decode a thrown transport marker back to the logical failure cause. */
  def unwindCause(t: Throwable): Throwable =
    if (t eq NullCauseMarker) null else t
}
