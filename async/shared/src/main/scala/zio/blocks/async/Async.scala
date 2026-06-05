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
 * The type `Async[+A]` is declared in the `async` package object via the
 * [[AsyncEncoding]] holder; the underlying representation (`Any`) is hidden, so
 * callers cannot rely on `A <: Async[A]` and must enter the union through
 * either [[succeed]], [[fail]], [[attempt]], a [[Pollable]], or [[promise]].
 *
 * No `map` / `flatMap` / `await` / `catchAll` here — those live as inline
 * extension methods on `Async[A]` (see `AsyncSyntaxVersionSpecific`), which
 * fold the encoding themselves so the hot path never allocates a `Function1`
 * wrapper.
 */
object Async extends AsyncCompanionVersionSpecific {

  /**
   * Lift a pure value into [[Async]]. Identity at runtime — the underlying
   * representation is `Any`, so this just casts to the abstract type. The value
   * is boxed if `A` is a primitive (unavoidable for any generic `Object`-erased
   * API; HotSpot eliminates the box on the hot path).
   */
  def succeed[A](a: A): Async[A] = a.asInstanceOf[Async[A]]

  /**
   * Lift a thrown [[Throwable]] into [[Async]]. The resulting value, when
   * mapped / flatMapped, short-circuits without invoking the continuation;
   * `.catchAll` recovers it; `.block` throws it.
   */
  def fail(cause: Throwable): Async[Nothing] = new Failure(cause)

  /**
   * Evaluate `body` eagerly; convert any thrown [[Throwable]] into an
   * [[Async.fail]]. The standard way to bridge throw-based code into the
   * encoding so that `.catchAll` can see the error.
   *
   * Note: non-fatal vs. fatal distinction is intentionally omitted to keep the
   * hot path lean. Callers that need fatal-error handling should rethrow from
   * their handler.
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
   * [[Cancelable]] that can stop the run. This is the sanctioned way to drive
   * an arbitrary [[Async]] without reaching through the hidden encoding.
   *
   * Semantics:
   *
   *   - The callback is invoked '''at most once''': exactly once if the run
   *     reaches success or failure before [[Cancelable.cancel]] wins, and never
   *     if cancellation linearizes first.
   *   - Completion and cancellation race through a single atomic terminal
   *     state, so the outcome is deterministic per run and `cancel()` is
   *     idempotent.
   *   - For an already-ready (or already-failed) `fa`, `cb` runs
   *     '''synchronously on the calling thread''', before this method returns —
   *     callers must tolerate that. For a suspended `fa`, `cb` runs on the
   *     driver worker (JVM) or a microtask (JS).
   *   - `cb` is never invoked while an internal driver lock is held, and never
   *     re-entrantly from inside a `poll`.
   *   - A [[Throwable]] escaping `poll`, an [[Async.fail]], or any [[Failure]]
   *     reached during the run surfaces as `cb(Left(cause))`.
   *
   * On Scala.js a truly asynchronous run is driven by microtasks (no thread is
   * blocked); on the JVM it is driven on a daemon worker thread that parks
   * between polls.
   */
  def unsafeRunAsync[A](fa: Async[A])(cb: Either[Throwable, A] => Unit): Cancelable =
    AsyncRunner.unsafeRunAsync(fa)(cb)

  /**
   * An [[Async]] that never completes. Polling it always returns itself; it
   * never wakes a waker. Useful as a sentinel in tests and as the right-zero of
   * `orElse`-style operations.
   */
  val never: Async[Nothing] = new Pollable[Nothing] {
    def poll(waker: Waker): Async[Nothing] = this
  }

  /**
   * Sequentially evaluate `as` and collect their values into a [[List]] in
   * input order. A [[Failure]] short-circuits — subsequent inputs are NOT
   * polled and the failure is propagated.
   *
   * Fast path: if every input is already a raw value (no [[Pollable]]), the
   * implementation just builds the list in a tight `while` with no `Pollable`
   * allocation. As soon as a `Pollable` (or `Failure`) is encountered, we
   * switch to a `flatMap`-based continuation for the rest.
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
