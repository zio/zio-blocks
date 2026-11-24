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

import zio.blocks.combinators.Concat
import zio.blocks.combinators.Tuples.Tuples
import zio.blocks.async.internal.AsyncRunner
import zio.blocks.async.AsyncEncoding.WrappedPollable

/**
 * The Scala 3 operators on [[Async]]: extension methods on `Async[A]`,
 * `flatten` on a nested `Async`, the `succeed` and `fail` helpers for an
 * `Async.promise` body, and `when` and `unless`. Mixed into the `async` package
 * object, so `import zio.blocks.async.*` brings them into scope. Most operators
 * are `inline`.
 */
private[async] trait AsyncSyntaxVersionSpecific {

  extension [A](inline fa: Async[A]) {

    /**
     * Continues with `f` applied to the value: `f` returns the next `Async`,
     * and the result follows it. On a ready value `f` runs at once; on a
     * pending value it runs when a driver settles the value. A failure passes
     * through without calling `f`. A throw from `f` is not caught by a later
     * `catchAll` (see [[Failure]]).
     *
     * {{{
     * def findUser(id: Int): Async[String] = Async.succeed("user-" + id)
     * def orderCount(user: String): Async[Int] = Async.succeed(user.length)
     *
     * Async.succeed(7).flatMap(findUser).flatMap(orderCount).block // 6
     * }}}
     */
    inline def flatMap[B](inline f: A => Async[B]): Async[B] = {
      val r: Any = fa
      if (r.isInstanceOf[Failure]) r.asInstanceOf[Async[B]]
      else if (r.isInstanceOf[Pollable[?]])
        Async.slowPath.flatMapAsync[A, B](r, (a: A) => f(a))
      else f(AsyncEncoding.deliverSuccess[A](r))
    }

    /**
     * Applies `f` to the value. On a ready value `f` runs at once; on a pending
     * value it runs when a driver settles the value. A failure passes through
     * without calling `f`. A throw from `f` is not caught by a later `catchAll`
     * (see [[Failure]]): for code that throws, use `flatMap` with
     * `Async.attempt`.
     *
     * {{{
     * Async.succeed(1200).map(_ * 2).block // 2400
     * }}}
     */
    inline def map[B](inline f: A => B): Async[B] = {
      val r: Any = fa
      if (r.isInstanceOf[Failure]) r.asInstanceOf[Async[B]]
      else if (r.isInstanceOf[Pollable[?]])
        Async.slowPath.mapAsync[A, B](r, (a: A) => f(a))
      else Async.succeed(f(AsyncEncoding.deliverSuccess[A](r))).asInstanceOf[Async[B]]
    }

    /**
     * Recovers from a failure by continuing with `f(cause)`. A successful value
     * passes through without calling `f`. A throw from `f` becomes the failure
     * of the result.
     *
     * {{{
     * val price: Async[Int] = Async.fail(new java.util.concurrent.TimeoutException("price service"))
     * price.catchAll(_ => Async.succeed(0)).block // 0
     * }}}
     */
    inline def catchAll[A1 >: A](inline f: Throwable => Async[A1]): Async[A1] = {
      val r: Any = fa
      if (r.isInstanceOf[Failure])
        try f(r.asInstanceOf[Failure].cause).asInstanceOf[Async[A1]]
        catch { case t: Throwable => Async.fail(t).asInstanceOf[Async[A1]] }
      else if (r.isInstanceOf[Pollable[?]])
        Async.slowPath.catchAllAsync[A, A1](r, (t: Throwable) => f(t))
      else r.asInstanceOf[Async[A1]]
    }

    /**
     * Waits for this `Async` to settle and returns its value, or throws its
     * failure's cause. Use it at the edge of a program: `main`, a test, or a
     * boundary with synchronous code. A ready value returns at once. On the JVM
     * a pending value parks the calling thread (a virtual thread does not pin
     * its carrier); calling `block` from inside a `poll` can deadlock the
     * driver. On Scala.js nothing can park: `block` throws
     * `IllegalStateException` as soon as the value has to wait for something
     * that happens later, such as a timer, I/O or another microtask. Inside
     * `Async.async` use `await` instead.
     *
     * Drive a pending value from one consumer only: `block` racing another
     * driver of the same value, such as `toFuture`, can hang forever (a known
     * bug). Call `start` once and `block` on the `Async.Running` it returns.
     *
     * {{{
     * Async.succeed(8080).map(_ + 1).block // 8081
     * Async.succeed("80a").flatMap(s => Async.attempt(s.toInt)).either.block
     * // Left(java.lang.NumberFormatException: For input string: "80a")
     * }}}
     */
    inline def block: A = Async.slowPath.block[A](fa)

    /**
     * Inside an `Async.async` block, waits for this `Async` and returns its
     * value without blocking a thread: `Async.async` rewrites the code after
     * the `await` into a continuation. A failure becomes the failure of the
     * enclosing block. Anywhere else, `await` does not compile.
     *
     * {{{
     * val total: Async[Int] = Async.async {
     *   val subtotal = Async.succeed(1200).await
     *   val shipping = Async.succeed(50).await
     *   subtotal + shipping
     * }
     * total.block // 1250
     * }}}
     */
    inline def await: A =
      ${ zio.blocks.async.internal.AsyncDirect.awaitImpl[A]('fa) }

    /**
     * Combines this value with `that` using `f`, waiting for this value first
     * and then for `that`. Both are already built, so work they started runs at
     * the same time; only the waiting happens in order. A failure of this value
     * fails the result without waiting for `that`; a failure of `that` fails it
     * once this value has succeeded.
     *
     * {{{
     * val subtotal: Async[Int] = Async.succeed(1200)
     * val shipping: Async[Int] = Async.succeed(50)
     * subtotal.zipWith(shipping)(_ + _).block // 1250
     * }}}
     */
    inline def zipWith[B, C](inline that: Async[B])(inline f: (A, B) => C): Async[C] = {
      val ra: Any = fa
      val rb: Any = that
      if (ra.isInstanceOf[Pollable[?]] || rb.isInstanceOf[Pollable[?]])
        Async.slowPath.zipWithAsync[A, B, C](ra, rb, (a, b) => f(a, b))
      else
        Async
          .succeed(
            f(
              AsyncEncoding.deliverSuccess[A](ra),
              AsyncEncoding.deliverSuccess[B](rb)
            )
          )
          .asInstanceOf[Async[C]]
    }

    /**
     * Pairs this value with `that`, waiting for this value first, like
     * `zipWith`. The `Tuples` combiner of the combinators module builds the
     * result: a tuple on the left is extended, so `a.zip(b).zip(c)` gives
     * `(A, B, C)`, and a `Unit` on either side is dropped. A tuple on the right
     * is concatenated on Scala 3 (`(1, 2)` with `(3, 4)` gives `(1, 2, 3, 4)`)
     * but nested on Scala 2 (`(1, 2, (3, 4))`).
     *
     * {{{
     * Async.succeed(8080).zip(Async.succeed("localhost")).zip(Async.succeed(true)).block
     * // (8080,localhost,true)
     * }}}
     */
    inline def zip[B](inline that: Async[B])(using t: Tuples[A, B]): Async[t.Out] =
      zipWith(that)((a, b) => t.combine(a, b))

    /**
     * Runs `f` on the value for its effect and keeps the original value. If the
     * `Async` that `f` returns fails, the result fails; a failure of this value
     * skips `f`.
     *
     * {{{
     * val lastSeen = new java.util.concurrent.atomic.AtomicInteger
     * Async.succeed(42).tap(id => Async.attempt(lastSeen.set(id))).block // 42
     * lastSeen.get() // 42
     * }}}
     */
    inline def tap(inline f: A => Async[Any]): Async[A] = {
      val r: Any = fa
      if (r.isInstanceOf[Pollable[?]])
        Async.slowPath.tapAsync[A](r, (a: A) => f(a))
      else if (r.isInstanceOf[WrappedPollable])
        Async.slowPath.tapReady[A](AsyncEncoding.deliverSuccess[A](r), f)
      else {
        // Plain ready value: apply `f` inline. A ready successful effect means
        // the original encoding passes through unchanged — no re-lift needed.
        val a        = r.asInstanceOf[A]
        val fin: Any = f(a)
        if (fin.isInstanceOf[Failure]) fin.asInstanceOf[Async[A]]
        else if (fin.isInstanceOf[Pollable[?]])
          Async.slowPath.runThenValue[A](fin, a, suppressFailure = false)
        else r.asInstanceOf[Async[A]]
      }
    }

    /**
     * Returns an `Async` with this value's outcome that, once this value
     * settles, also waits for `finalizer` if the finalizer is pending. The
     * finalizer never changes the outcome: its failure is dropped after a
     * success, and added to the cause's suppressed exceptions after a failure.
     *
     * `finalizer` is a value, not a function: building it runs its synchronous
     * part at once, before this value settles. To run cleanup only after a
     * pending value completes, put it in a `flatMap`, `tap` or `foldCause`
     * function.
     *
     * {{{
     * val released = new java.util.concurrent.atomic.AtomicBoolean(false)
     * val read: Async[String] =
     *   Async.attempt[String](throw new java.io.IOException("read failed"))
     *     .ensuring(Async.attempt(released.set(true)))
     * read.either.block // Left(java.io.IOException: read failed)
     * released.get() // true
     * }}}
     */
    inline def ensuring(inline finalizer: Async[Any]): Async[A] = {
      val r: Any = fa
      if (r.isInstanceOf[Pollable[?]])
        Async.slowPath.ensuringAsync[A](r, finalizer)
      else {
        val a        = AsyncEncoding.deliverSuccess[A](r)
        val fin: Any = finalizer
        if (fin.isInstanceOf[Failure]) Async.succeed(a)
        else if (fin.isInstanceOf[Pollable[?]])
          Async.slowPath.ensuringReady[A](a, fin.asInstanceOf[Pollable[Any]])
        else Async.succeed(a)
      }
    }

    /**
     * Replaces a failure's cause with `f(cause)`; a value passes through. A
     * throw from `f` becomes the failure of the result.
     */
    inline def mapError(inline f: Throwable => Throwable): Async[A] = {
      val r: Any = fa
      if (r.isInstanceOf[Failure])
        // Guarded like the pending path (CatchAllPollable): a throwing mapper
        // is reified as the failure, never thrown at the call site.
        try Async.fail(f(r.asInstanceOf[Failure].cause))
        catch { case t: Throwable => Async.fail(t) }
      else if (r.isInstanceOf[Pollable[?]])
        Async.slowPath.catchAllAsync[A, A](r, (t: Throwable) => Async.fail(f(t)))
      else r.asInstanceOf[Async[A]]
    }

    /**
     * Turns both outcomes into a value: `onFailure(cause)` for a failure,
     * `onSuccess(value)` for a success, so the result succeeds unless a
     * function throws. For functions that do not throw, it equals
     * `fa.map(onSuccess).catchAll(t => Async.succeed(onFailure(t)))`.
     *
     * {{{
     * Async.attempt("8o80".toInt).foldCause(_ => 8080)(port => port).block // 8080
     * }}}
     */
    inline def foldCause[B](inline onFailure: Throwable => B)(inline onSuccess: A => B): Async[B] = {
      val r: Any = fa
      if (r.isInstanceOf[Failure])
        // Guarded like the pending path (CatchAllPollable): a throwing
        // onFailure is reified as the failure, never thrown at the call site.
        try Async.succeed(onFailure(r.asInstanceOf[Failure].cause))
        catch { case t: Throwable => Async.fail(t) }
      else if (r.isInstanceOf[Pollable[?]]) {
        Async.slowPath.foldCauseAsync[A, B](r, (t: Throwable) => onFailure(t), (a: A) => onSuccess(a))
      } else {
        val a = AsyncEncoding.deliverSuccess[A](r)
        Async.succeed(onSuccess(a))
      }
    }

    /**
     * Turns a failure into `Left(cause)` and a value into `Right(value)`, so
     * the failure becomes data you can match on.
     *
     * {{{
     * Async.fail(new RuntimeException("no quote")).either.block
     * // Left(java.lang.RuntimeException: no quote)
     * }}}
     */
    inline def either: Async[Either[Throwable, A]] = {
      val r: Any = fa
      if (r.isInstanceOf[Failure]) Async.succeed(Left(r.asInstanceOf[Failure].cause))
      else if (r.isInstanceOf[Pollable[?]]) {
        val mapped = Async.slowPath.mapAsync[A, Either[Throwable, A]](r, (a: A) => Right(a))
        Async.slowPath.catchAllAsync[Either[Throwable, A], Either[Throwable, A]](
          mapped,
          (t: Throwable) => Async.succeed(Left(t))
        )
      } else {
        val a = AsyncEncoding.deliverSuccess[A](r)
        Async.succeed(Right(a))
      }
    }

    /**
     * Replaces the value with `b` once this value succeeds; a failure passes
     * through. `b` is evaluated then, not before.
     *
     * {{{
     * Async.succeed(8080).as("ready").block // ready
     * }}}
     */
    inline def as[B](inline b: B): Async[B] = map((_: A) => b)

    /**
     * Replaces the value with `()`. Equivalent to `as(())`.
     */
    inline def unit: Async[Unit] = as(())

    /**
     * Waits for this value and then for `that`, and returns `that`'s value; a
     * failure of either fails the result. Equivalent to `zipWith(that)((_, b) =>
     * b)`.
     */
    inline def *>[B](inline that: Async[B]): Async[B] = zipWith(that)((_, b) => b)

    /**
     * Waits for this value and then for `that`, and returns this value; a
     * failure of either fails the result. Equivalent to `zipWith(that)((a, _) =>
     * a)`.
     */
    inline def <*[B](inline that: Async[B]): Async[A] = zipWith(that)((a, _) => a)
  }

  // A separate, non-`inline` extension so `start` can reach the in-package
  // `internal.AsyncRunner` driver directly.
  extension [A](fa: Async[A]) {

    /**
     * Hands this `Async` to a driver and returns an `Async.Running` without
     * waiting. A ready or failed value gives a handle that is already complete.
     * A pending value is driven in the background: on the JVM by tasks on
     * `ForkJoinPool.commonPool()` that hold no thread while the value waits; on
     * Scala.js the first step runs on the calling thread and later ones on
     * `JSExecutionContext.queue` (after a long run of ready steps, on a
     * `setTimeout` macrotask). Operators applied before `start` are part of
     * what the driver runs; operators applied to the handle afterwards build a
     * new value that runs only when you drive it. `fa.start` cannot move work
     * off the calling thread that `fa` already did while it was built: use
     * `Async.start(body)` for that.
     *
     * {{{
     * val gate = new Completer[Int]
     * val running: Async.Running[Int] = gate.peek.map(_ * 2).start
     * gate.succeed(21)
     * running.block // 42
     * }}}
     */
    def start: Async.Running[A] = AsyncRunner.start(fa)

    /**
     * Falls back to `that` when this value fails. `that` is by-name and is
     * built only on failure. The `Concat` combiner of the combinators module
     * merges the two success types: the same type, or a type and its supertype,
     * give that type. Other pairs give the union `A | B` on Scala 3; on Scala 2
     * they give their shared supertype when there is exactly one besides
     * general types such as `Any`, `AnyRef`, `Product` and `Serializable`, and
     * `Either[A, B]` otherwise (`Left` for this value, `Right` for the
     * fallback).
     *
     * {{{
     * val primary: Async[Int] = Async.fail(new RuntimeException("primary down"))
     * primary.orElse(Async.succeed(8443)).block // 8443
     * }}}
     *
     * {{{
     * // scala-2-only
     * val port: Async[Either[Int, String]] = Async.succeed(8080).orElse(Async.succeed("unknown"))
     * port.block // Left(8080)
     * }}}
     *
     * {{{
     * // scala-3-only
     * val port: Async[Int | String] = Async.succeed(8080).orElse(Async.succeed("unknown"))
     * port.block // 8080
     * }}}
     */
    def orElse[B](that: => Async[B])(using c: Concat[A, B]): Async[c.Out] =
      if (c.isIdentityLike)
        fa.asInstanceOf[Async[c.Out]].catchAll((_: Throwable) => that.asInstanceOf[Async[c.Out]])
      else
        fa.map(c.left).catchAll((_: Throwable) => that.map(c.right))
  }

  extension [A](inline ffa: Async[Async[A]]) {

    /**
     * Removes one level of nesting from an `Async[Async[A]]`: the result
     * follows the inner `Async`.
     */
    inline def flatten: Async[A] = {
      val any = ffa.asInstanceOf[Any]
      if (any.isInstanceOf[Pollable[?]]) Async.slowPath.flattenAsync[A](any)
      else AsyncEncoding.deliverSuccess[Async[A]](any)
    }
  }

  /**
   * Returns `fa` with its value discarded when `cond` is true, and a ready `()`
   * otherwise. `fa` is not built when `cond` is false.
   */
  inline def when(inline cond: Boolean)(inline fa: Async[Any]): Async[Unit] =
    if (cond) fa.unit else Async.succeed(())

  /**
   * Returns `fa` with its value discarded when `cond` is false, and a ready
   * `()` otherwise: the opposite of `when`. `fa` is not built when `cond` is
   * true.
   */
  inline def unless(inline cond: Boolean)(inline fa: Async[Any]): Async[Unit] =
    if (!cond) fa.unit else Async.succeed(())

  // The callback-to-Async bridge is `Async.promise` (defined on the companion
  // via `AsyncCompanionVersionSpecific` so it can take a `?=>` context
  // function on Scala 3). The helpers below pick up the `Completer` from the
  // context function and let users write `succeed(x)` / `fail(t)` at top
  // level inside an `Async.promise { ... }` body.

  /**
   * Completes the `Completer` of the enclosing `Async.promise` body with
   * `value`. On Scala 3 the body supplies the `Completer` as a context
   * parameter; on Scala 2 the body must bind it as `implicit c => ...`.
   */
  def succeed[A](value: A)(using c: Completer[A]): Unit = c.succeed(value)

  /**
   * Completes the `Completer` of the enclosing `Async.promise` body with the
   * failure `cause`. On Scala 3 the body supplies the `Completer` as a context
   * parameter; on Scala 2 the body must bind it as `implicit c => ...`.
   */
  def fail[A](cause: Throwable)(using c: Completer[A]): Unit = c.fail(cause)
}
