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
import zio.blocks.async.internal.AsyncRunner

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
      if (r.isInstanceOf[Failure]) r.asInstanceOf[Async[B]]
      else if (r.isInstanceOf[Pollable[?]])
        Async.slowPath.flatMapAsync[A, B](r, (a: A) => f(a))
      else f(AsyncEncoding.deliverSuccess[A](r))
    }

    /**
     * Transform the success value of `fa` with `f`. A failure short-circuits
     * and is propagated unchanged.
     */
    inline def map[B](inline f: A => B): Async[B] = {
      val r: Any = fa
      if (r.isInstanceOf[Failure]) r.asInstanceOf[Async[B]]
      else if (r.isInstanceOf[Pollable[?]])
        Async.slowPath.mapAsync[A, B](r, (a: A) => f(a))
      else Async.succeed(f(AsyncEncoding.deliverSuccess[A](r))).asInstanceOf[Async[B]]
    }

    /**
     * Recover from a failure by applying `f` to its cause. A successful `fa` is
     * returned unchanged (the handler is not invoked).
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
     * Drive `fa` to its value, blocking the calling thread until it is ready. A
     * failure is re-thrown as its cause.
     *
     * This is the unsafe escape hatch: ready values return immediately; a
     * pending value blocks the (Loom-friendly) calling thread on the JVM, and
     * on JS — which cannot block — throws. Inside an `Async.async { ... }`
     * block use the direct-style [[await]] instead, which runs without
     * blocking.
     */
    inline def block: A = Async.slowPath.block[A](fa)

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
        Async.slowPath.tapAsync[A](r, (a: A) => f(a))
      else if (r.isInstanceOf[AsyncEncoding.WrappedPollable])
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
     * Run `finalizer` after `fa` completes — success, failure, or suspension —
     * and propagate the original outcome. A failure in `finalizer` is
     * suppressed (the original outcome wins).
     */
    inline def ensuring(inline finalizer: Async[Any]): Async[A] = {
      val r: Any = fa
      if (r.isInstanceOf[Pollable[?]])
        Async.slowPath.ensuringAsync[A](r, finalizer)
      else {
        val a = AsyncEncoding.deliverSuccess[A](r)
        Async.slowPath.runThenValue[A](finalizer, a, suppressFailure = true)
      }
    }

    /** Transform the cause of any [[Failure]]; values pass through. */
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

    /** Fall back to `that` if `fa` fails. */
    inline def orElse[A1 >: A](inline that: Async[A1]): Async[A1] =
      catchAll((_: Throwable) => that)

    /**
     * Fold a possibly-failed [[Async]] into a guaranteed-success value by
     * handling both branches. Equivalent to
     * `fa.map(onSuccess).catchAll(t => Async.succeed(onFailure(t)))`.
     */
    inline def foldCause[B](inline onFailure: Throwable => B)(inline onSuccess: A => B): Async[B] = {
      val r: Any = fa
      if (r.isInstanceOf[Failure])
        // Guarded like the pending path (CatchAllPollable): a throwing
        // onFailure is reified as the failure, never thrown at the call site.
        try Async.succeed(onFailure(r.asInstanceOf[Failure].cause))
        catch { case t: Throwable => Async.fail(t) }
      else if (r.isInstanceOf[Pollable[?]])
        map(onSuccess).catchAll((t: Throwable) => Async.succeed(onFailure(t)))
      else {
        val a = AsyncEncoding.deliverSuccess[A](r)
        Async.succeed(onSuccess(a))
      }
    }

    /** Convert any [[Failure]] into a `Left`; any value into a `Right`. */
    inline def either: Async[Either[Throwable, A]] = {
      val r: Any = fa
      if (r.isInstanceOf[Failure]) Async.succeed(Left(r.asInstanceOf[Failure].cause))
      else if (r.isInstanceOf[Pollable[?]])
        foldCause((t: Throwable) => Left(t))((a: A) => Right(a))
      else {
        val a = AsyncEncoding.deliverSuccess[A](r)
        Async.succeed(Right(a))
      }
    }

    /** Replace the value with `b`. Equivalent to `fa.map(_ => b)`. */
    inline def as[B](inline b: B): Async[B] = map((_: A) => b)

    /** Discard the value. Equivalent to `fa.as(())`. */
    inline def unit: Async[Unit] = as(())

    /** Sequence then return `that`'s value (`zipRight`, ZIO's `*>`). */
    inline def *>[B](inline that: Async[B]): Async[B] = zipWith(that)((_, b) => b)

    /** Sequence then return `fa`'s value (`zipLeft`, ZIO's `<*`). */
    inline def <*[B](inline that: Async[B]): Async[A] = zipWith(that)((a, _) => a)
  }

  /**
   * Eagerly drive an already-built `fa` and return a [[Async.Running]] handle.
   * Separate (non-`inline`) extension so it can reach the in-package
   * `internal.AsyncRunner` driver directly — driving an existing `Async` is
   * distinct from the companion `Async.start(body)`, which evaluates a by-name
   * body (`Future.apply`-style).
   */
  extension [A](fa: Async[A]) {
    def start: Async.Running[A] = AsyncRunner.start(fa)
  }

  /** Flatten an `Async[Async[A]]` one level. */
  extension [A](inline ffa: Async[Async[A]]) {
    inline def flatten: Async[A] = ffa.flatMap((inner: Async[A]) => inner)
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
