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

import zio.blocks.async.internal.AsyncRunner

/**
 * Constructors for [[Async]] values.
 *
 * An `Async[A]` is created with one of [[succeed]], [[fail]], [[attempt]], or
 * [[promise]] (a bare `A` is not itself an `Async[A]`). The transformation and
 * combination operators (`map`, `flatMap`, `catchAll`, `await`, ...) become
 * available as extension methods on `Async[A]` after importing
 * `zio.blocks.async._`.
 */
object Async extends AsyncCompanionVersionSpecific {

  /**
   * Lift an already-available value into a successful [[Async]].
   *
   * The value must not itself be an `Async` (i.e. `Async[Async[A]]` and other
   * nested forms are unsupported): `Async` is a restricted monad whose success
   * values may not be effects. Sequence effects with `flatMap` instead of
   * nesting them. (Nesting plain, non-effect values is fine.)
   */
  def succeed[A](a: A): Async[A] = a.asInstanceOf[Async[A]]

  /**
   * Create a failed [[Async]] carrying `cause`. A failed value short-circuits
   * `map` / `flatMap` without invoking the continuation, is recoverable with
   * `.catchAll`, and is re-thrown by `.block`.
   */
  def fail(cause: Throwable): Async[Nothing] = new Failure(cause)

  /**
   * Evaluate `body` eagerly, capturing any thrown [[Throwable]] as a failed
   * [[Async]] (see [[fail]]). The standard way to bridge throw-based code into
   * `Async` so that `.catchAll` can recover the error.
   *
   * Note: `attempt` catches every `Throwable`, with no non-fatal/fatal
   * distinction. Callers who want fatal errors to propagate should rethrow them
   * from their recovery handler.
   */
  def attempt[A](body: => A): Async[A] =
    try succeed(body)
    catch { case t: Throwable => fail(t) }

  // The public `promise` lives in `AsyncCompanionVersionSpecific` (mixed in
  // above) so Scala 3 can offer a `Completer[A] ?=> Unit` context-function
  // body while Scala 2 keeps the `Completer[A] => Unit` shape — single API
  // name across versions, version-appropriate parameter shape. Both
  // implementations delegate to the package-private helper below.

  /**
   * Cross-version implementation of [[promise]]. Takes a plain
   * `Completer[A] => Unit` body so it is callable from `private[async]` code
   * (e.g. `AsyncInterop`) regardless of Scala version, even though the public
   * `promise` shape differs between Scala 2 and Scala 3.
   */
  private[async] def promiseInternal[A](body: Completer[A] => Unit): Async[A] = {
    val c = new Completer[A]
    body(c)
    c.peek
  }

  /**
   * Run `fa`, invoking `cb` with its terminal outcome, and return a
   * [[Cancelable]] that can stop the run. This is the supported way to drive an
   * arbitrary [[Async]] to its result without blocking.
   *
   * Semantics:
   *
   *   - The callback is invoked '''at most once''': exactly once if the run
   *     reaches success or failure before [[Cancelable.cancel]] wins, and never
   *     if cancellation happens first.
   *   - Completion and cancellation are linearized, so the outcome is
   *     deterministic per run and `cancel()` is idempotent.
   *   - For an already-completed (success or failure) `fa`, `cb` runs
   *     '''synchronously on the calling thread''', before this method returns —
   *     callers must tolerate that. For a still-pending `fa`, `cb` is delivered
   *     asynchronously once the value becomes available.
   *   - `cb` is never invoked reentrantly while `fa` is being driven.
   *   - A failure reached during the run (whether from [[Async.fail]] or a
   *     [[Throwable]] thrown while evaluating `fa`) surfaces as
   *     `cb(Left(cause))`.
   *
   * No thread is blocked: on the JVM a pending run proceeds on a background
   * worker, and on Scala.js it proceeds without blocking the event loop.
   */
  def unsafeRunAsync[A](fa: Async[A])(cb: Either[Throwable, A] => Unit): Cancelable =
    AsyncRunner.unsafeRunAsync(fa)(cb)

  /**
   * An [[Async]] that never completes. Useful as a sentinel in tests and as the
   * right-zero of `orElse`-style operations.
   */
  val never: Async[Nothing] = new Pollable[Nothing] {
    def poll(waker: Waker): Async[Nothing] = this
  }

  /**
   * Sequentially run `as` and collect their values into a [[List]] in input
   * order. A failure short-circuits — subsequent inputs are not driven and the
   * failure is propagated.
   */
  def collectAll[A](as: Iterable[Async[A]]): Async[List[A]] =
    drainCollectAll[A](as.iterator, new scala.collection.mutable.ListBuffer[A])

  /**
   * Drain `it`, appending into `buf`. While inputs are raw values we stay in a
   * tight loop; on the first [[Pollable]] we hand the rest to
   * [[continueCollectAll]] (a single `flatMap` continuation that reuses the
   * same iterator and buffer to avoid copying).
   */
  private def drainCollectAll[A](
    it: Iterator[Async[A]],
    buf: scala.collection.mutable.ListBuffer[A]
  ): Async[List[A]] = {
    while (it.hasNext) {
      val any = it.next().asInstanceOf[Any]
      if (any.isInstanceOf[Pollable[_]]) return continueCollectAll[A](any, it, buf)
      buf += any.asInstanceOf[A]
    }
    succeed(buf.toList)
  }

  /**
   * Continuation: drive `pending` to a value (failures propagate via
   * `flatMap`'s short-circuit), append, then resume draining.
   */
  private def continueCollectAll[A](
    pending: Any,
    it: Iterator[Async[A]],
    buf: scala.collection.mutable.ListBuffer[A]
  ): Async[List[A]] =
    pending.asInstanceOf[Async[A]].flatMap { a =>
      buf += a
      drainCollectAll[A](it, buf)
    }
}
