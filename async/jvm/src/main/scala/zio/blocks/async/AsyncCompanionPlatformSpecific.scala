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

import java.util.concurrent.CompletionStage

import scala.concurrent.Future

/**
 * The JVM constructors of the [[Async]] companion that bring in a
 * [[scala.concurrent.Future]] or a [[java.util.concurrent.CompletionStage]].
 * `object Async` mixes this trait in, so callers write `Async.fromFuture(...)`
 * and `Async.fromCompletionStage(...)`. The `toFuture` and
 * `toCompletableFuture` extension methods convert the other way.
 */
private[async] trait AsyncCompanionPlatformSpecific {

  /**
   * Creates an [[Async]] that completes with the value or the failure of
   * `future`. It needs no `ExecutionContext`. A future that has already
   * completed gives a ready `Async`; otherwise the `Async` completes when
   * `future` does. On Scala.js, a `JavaScriptException` that wraps `null` (from
   * a JavaScript promise rejected with `null`) becomes a `null` cause.
   *
   * Cancelling a run of this `Async` stops waiting for `future` but cannot stop
   * it: a `Future` has no cancel operation.
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
   * `stage`. A stage that has already completed gives a ready `Async`;
   * otherwise the `Async` completes when `stage` does.
   *
   * A failure wrapped in a [[java.util.concurrent.CompletionException]] is
   * unwrapped to its cause, so the `Async` fails with what the producer threw,
   * and a cancelled stage fails with its
   * [[java.util.concurrent.CancellationException]]. A stage that fails with an
   * [[java.util.concurrent.ExecutionException]] is unwrapped as well when it
   * fails after this call, but not when it has already failed, a known bug.
   * Cancelling a run of this `Async` does not cancel `stage`, a known bug.
   *
   * {{{
   * import java.util.concurrent.CompletableFuture
   *
   * val token                = new CompletableFuture[String]()
   * val upper: Async[String] = Async.fromCompletionStage(token).map(_.toUpperCase)
   * token.complete("t-123")
   * upper.block // T-123
   * }}}
   *
   * @see
   *   [[https://zioblocks.com/reference/async/#integration-points Integration Points]]
   */
  def fromCompletionStage[A](stage: CompletionStage[A]): Async[A] = AsyncInterop.fromCompletionStage(stage)
}
