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

import zio.blocks.combinators.Tuples.Tuples

/**
 * Scala 3 surface for [[Async]]: extension methods on `Async[A]` and a
 * context-function `promise`/`succeed`/`fail` builder. Mixed into the `async`
 * package object so `import zio.blocks.async.*` exposes the full DSL.
 *
 * Failure propagation: a failed `Async` short-circuits `map` / `flatMap` /
 * `zipWith` (the function is not invoked) and is intercepted by `catchAll`.
 *
 * Note: exceptions thrown by the functions passed to these operators are NOT
 * caught — wrap throwing work in [[Async.attempt]] to surface it as a failure.
 */
private[async] trait AsyncSyntaxVersionSpecific {

  extension [A](inline fa: Async[A]) {

    /**
     * Sequence `fa` with `f`: when `fa` succeeds, continue with `f` applied to
     * its value. A failure short-circuits and is propagated unchanged.
     */
    inline def flatMap[B](inline f: A => Async[B]): Async[B] = {
      val r: Any = fa
      if (r.isInstanceOf[Pollable[?]])
        AsyncSlowPath.flatMapAsync[A, B](r, (a: A) => f(a))
      else f(r.asInstanceOf[A])
    }

    /**
     * Transform the success value of `fa` with `f`. A failure short-circuits
     * and is propagated unchanged.
     */
    inline def map[B](inline f: A => B): Async[B] = {
      val r: Any = fa
      if (r.isInstanceOf[Pollable[?]])
        AsyncSlowPath.mapAsync[A, B](r, (a: A) => f(a))
      else f(r.asInstanceOf[A]).asInstanceOf[Async[B]]
    }

    /**
     * Recover from a failure by applying `f` to its cause. A successful `fa` is
     * returned unchanged (the handler is not invoked).
     */
    inline def catchAll[A1 >: A](inline f: Throwable => Async[A1]): Async[A1] = {
      val r: Any = fa
      if (r.isInstanceOf[Pollable[?]])
        if (r.isInstanceOf[Failure]) f(r.asInstanceOf[Failure].cause)
        else AsyncSlowPath.catchAllAsync[A, A1](r, (t: Throwable) => f(t))
      else r.asInstanceOf[Async[A1]]
    }

    /**
     * Drive `fa` to its value, blocking the calling thread until it is ready. A
     * failure is re-thrown as its cause.
     *
     * This is the unsafe escape hatch: ready values return immediately; a
     * pending value blocks the (Loom-friendly) calling thread on the JVM, and
     * on JS — which cannot block — throws. Inside an `Async.async { ... }`
     * block use the direct-style [[await]] instead, which runs without
     * blocking.
     */
    inline def block: A = {
      val r: Any = fa
      if (r.isInstanceOf[Pollable[?]])
        AsyncSlowPath.awaitSuspended[A](r.asInstanceOf[Pollable[A]])
      else r.asInstanceOf[A]
    }

    /**
     * Direct-style await: extract the value of `fa` without blocking, usable
     * '''only''' directly inside an `Async.async { ... }` block. Using `.await`
     * anywhere else is a compile error.
     */
    inline def await: A =
      ${ zio.blocks.async.internal.AsyncDirect.awaitImpl[A]('fa) }

    /**
     * Combine `fa` with `that` using `f`. The two are sequenced strictly
     * left-to-right: `fa` is driven to a value first, and only then is `that`
     * driven. A failure in `fa` short-circuits without driving `that`; a
     * failure in `that` is surfaced only after `fa` has succeeded.
     */
    inline def zipWith[B, C](inline that: Async[B])(inline f: (A, B) => C): Async[C] = {
      val ra: Any = fa
      val rb: Any = that
      if (ra.isInstanceOf[Pollable[?]] || rb.isInstanceOf[Pollable[?]])
        AsyncSlowPath.zipWithAsync[A, B, C](ra, rb, (a, b) => f(a, b))
      else f(ra.asInstanceOf[A], rb.asInstanceOf[B]).asInstanceOf[Async[C]]
    }

    /**
     * Sequentially combine `fa` with `that`, fusing the values via the
     * `combinators` [[Tuples]] combiner: `a zip b zip c` flattens to
     * `Async[(A, B, C)]` rather than `Async[((A, B), C)]`. `Unit` on either
     * side is erased by the `Tuples` instances; tuple-on-tuple inputs are
     * heterogeneously-concatenated.
     */
    inline def zip[B](inline that: Async[B])(using t: Tuples[A, B]): Async[t.Out] =
      zipWith(that)((a, b) => t.combine(a, b))

    /**
     * Run `f` for its effect when `fa` succeeds, then yield `fa`'s original
     * value. A failure in `fa` (or in `f`) is propagated.
     */
    inline def tap(inline f: A => Async[Any]): Async[A] = {
      val r: Any = fa
      if (r.isInstanceOf[Pollable[?]])
        AsyncSlowPath.tapAsync[A](r, (a: A) => f(a))
      else {
        val a = r.asInstanceOf[A]
        AsyncSlowPath.runThenValue[A](f(a), a, suppressFailure = false)
      }
    }

    /**
     * Run `finalizer` after `fa` completes — success, failure, or suspension —
     * and propagate the original outcome. A failure in `finalizer` is
     * suppressed (the original outcome wins).
     */
    inline def ensuring(inline finalizer: Async[Any]): Async[A] = {
      val r: Any = fa
      if (r.isInstanceOf[Pollable[?]])
        AsyncSlowPath.ensuringAsync[A](r, finalizer)
      else {
        val a = r.asInstanceOf[A]
        AsyncSlowPath.runThenValue[A](finalizer, a, suppressFailure = true)
      }
    }

    /** Transform the cause of any [[Failure]]; values pass through. */
    inline def mapError(inline f: Throwable => Throwable): Async[A] =
      catchAll((t: Throwable) => Async.fail(f(t)))

    /** Fall back to `that` if `fa` fails. */
    inline def orElse[A1 >: A](inline that: Async[A1]): Async[A1] =
      catchAll((_: Throwable) => that)

    /**
     * Fold a possibly-failed [[Async]] into a guaranteed-success value by
     * handling both branches. Equivalent to
     * `fa.map(onSuccess).catchAll(t => Async.succeed(onFailure(t)))`.
     */
    inline def foldCause[B](inline onFailure: Throwable => B)(inline onSuccess: A => B): Async[B] =
      map(onSuccess).catchAll((t: Throwable) => Async.succeed(onFailure(t)))

    /** Convert any [[Failure]] into a `Left`; any value into a `Right`. */
    inline def either: Async[Either[Throwable, A]] =
      foldCause((t: Throwable) => Left(t))((a: A) => Right(a))

    /** Replace the value with `b`. Equivalent to `fa.map(_ => b)`. */
    inline def as[B](inline b: B): Async[B] = map((_: A) => b)

    /** Discard the value. Equivalent to `fa.as(())`. */
    inline def unit: Async[Unit] = as(())

    /** Sequence then return `that`'s value (`zipRight`, ZIO's `*>`). */
    inline def *>[B](inline that: Async[B]): Async[B] = zipWith(that)((_, b) => b)

    /** Sequence then return `fa`'s value (`zipLeft`, ZIO's `<*`). */
    inline def <*[B](inline that: Async[B]): Async[A] = zipWith(that)((a, _) => a)
  }

  /** Flatten an `Async[Async[A]]` one level. */
  extension [A](inline ffa: Async[Async[A]]) {
    inline def flatten: Async[A] = ffa.flatMap((fa: Async[A]) => fa)
  }

  /**
   * Conditionally evaluate `fa`. Returns `fa.unit` when `cond` is true,
   * `Async.succeed(())` otherwise. The unevaluated `fa` is by-name so it is not
   * constructed when the condition is false.
   */
  inline def when(inline cond: Boolean)(inline fa: Async[Any]): Async[Unit] =
    if (cond) fa.unit else Async.succeed(())

  /** Inverse of [[when]]: evaluate `fa` only when `cond` is false. */
  inline def unless(inline cond: Boolean)(inline fa: Async[Any]): Async[Unit] =
    if (!cond) fa.unit else Async.succeed(())

  // The callback-to-Async bridge is `Async.promise` (defined on the companion
  // via `AsyncCompanionVersionSpecific` so it can take a `?=>` context
  // function on Scala 3). The helpers below pick up the `Completer` from the
  // context function and let users write `succeed(x)` / `fail(t)` at top
  // level inside an `Async.promise { ... }` body.

  /** Complete the surrounding [[Async.promise]] block with `value`. */
  def succeed[A](value: A)(using c: Completer[A]): Unit = c.succeed(value)

  /** Complete the surrounding [[Async.promise]] block with a failure. */
  def fail[A](cause: Throwable)(using c: Completer[A]): Unit = c.fail(cause)
}
