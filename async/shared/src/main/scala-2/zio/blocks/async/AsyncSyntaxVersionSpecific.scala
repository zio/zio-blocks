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
import scala.annotation.compileTimeOnly

/**
 * Scala 2 surface for [[Async]]: an `implicit class` of ops on `Async[A]` and
 * an `implicit`-param `promise` / `succeed` / `fail` builder. Mixed into the
 * `async` package object so `import zio.blocks.async._` exposes the full DSL.
 * The surface mirrors the Scala 3 one so the two stay observationally
 * identical.
 *
 * Failure propagation: a failed `Async` short-circuits `map` / `flatMap` /
 * `zipWith` (the function is not invoked) and is intercepted by `catchAll`.
 *
 * Note: exceptions thrown by the functions passed to these operators are NOT
 * caught — wrap throwing work in [[Async.attempt]] to surface it as a failure.
 *
 * Call sugar form: `Async.promise[Int] { implicit c => succeed(42) }`.
 */
private[async] trait AsyncSyntaxVersionSpecific {

  implicit class AsyncOps[A](private val fa: Async[A]) {

    /**
     * Sequence `fa` with `f`: when `fa` succeeds, continue with `f` applied to
     * its value. A failure short-circuits and is propagated unchanged.
     */
    def flatMap[B](f: A => Async[B]): Async[B] = {
      val r: Any = fa
      if (r.isInstanceOf[Failure]) r.asInstanceOf[Async[B]]
      else if (r.isInstanceOf[Pollable[_]])
        Async.slowPath.flatMapAsync[A, B](r, f)
      else f(AsyncEncoding.deliverSuccess[A](r))
    }

    /**
     * Transform the success value of `fa` with `f`. A failure short-circuits
     * and is propagated unchanged.
     */
    def map[B](f: A => B): Async[B] = {
      val r: Any = fa
      if (r.isInstanceOf[Failure]) r.asInstanceOf[Async[B]]
      else if (r.isInstanceOf[Pollable[_]])
        Async.slowPath.mapAsync[A, B](r, f)
      else Async.succeed(f(AsyncEncoding.deliverSuccess[A](r))).asInstanceOf[Async[B]]
    }

    /**
     * Recover from a failure by applying `f` to its cause. A successful `fa` is
     * returned unchanged (the handler is not invoked).
     */
    def catchAll[A1 >: A](f: Throwable => Async[A1]): Async[A1] = {
      val r: Any = fa
      if (r.isInstanceOf[Failure])
        try f(r.asInstanceOf[Failure].cause).asInstanceOf[Async[A1]]
        catch { case t: Throwable => Async.fail(t).asInstanceOf[Async[A1]] }
      else if (r.isInstanceOf[Pollable[_]])
        Async.slowPath.catchAllAsync[A, A1](r, f)
      else r.asInstanceOf[Async[A1]]
    }

    /**
     * Drive `fa` to its value, blocking the calling thread until it is ready. A
     * failure is re-thrown as its cause.
     *
     * This is the unsafe escape hatch: ready values return immediately; a
     * pending value blocks the (Loom-friendly) calling thread on the JVM, and
     * on JS — which cannot block — throws. Inside an `Async.async { ... }` block
     * use the direct-style [[await]] instead, which runs without blocking.
     */
    def block: A = Async.slowPath.block[A](fa)

    /**
     * Direct-style await: extract the value of `fa` without blocking, usable
     * '''only''' directly inside an `Async.async { ... }` block. Using `.await`
     * anywhere else is a compile error.
     */
    @compileTimeOnly("`.await` may only be used directly inside an `Async.async { ... }` block.")
    def await: A = throw new IllegalStateException("`.await` was not rewritten by `Async.async`.")

    /**
     * Combine `fa` with `that` using `f`. The two are sequenced strictly
     * left-to-right: `fa` is driven to a value first, and only then is `that`
     * driven. A failure in `fa` short-circuits without driving `that`; a failure
     * in `that` is surfaced only after `fa` has succeeded.
     */
    def zipWith[B, C](that: Async[B])(f: (A, B) => C): Async[C] = {
      val ra: Any = fa
      val rb: Any = that
      if (ra.isInstanceOf[Pollable[_]] || rb.isInstanceOf[Pollable[_]])
        Async.slowPath.zipWithAsync[A, B, C](ra, rb, f)
      else
        Async.succeed(
          f(
            AsyncEncoding.deliverSuccess[A](ra),
            AsyncEncoding.deliverSuccess[B](rb)
          )
        ).asInstanceOf[Async[C]]
    }

    /**
     * Sequentially combine `fa` with `that`, fusing the values via the
     * `combinators` [[Tuples]] combiner: `a zip b zip c` flattens to
     * `Async[(A, B, C)]` rather than `Async[((A, B), C)]`. `Unit` on either
     * side is erased by the `Tuples` instances; tuple-on-tuple inputs are
     * heterogeneously-concatenated.
     */
    def zip[B](that: Async[B])(implicit t: Tuples[A, B]): Async[t.Out] =
      zipWith(that)((a, b) => t.combine(a, b))

    /**
     * Run `f` for its effect when `fa` succeeds, then yield `fa`'s original
     * value. A failure in `fa` (or in `f`) is propagated.
     */
    def tap(f: A => Async[Any]): Async[A] = {
      val r: Any = fa
      if (r.isInstanceOf[Pollable[_]])
        Async.slowPath.tapAsync[A](r, f)
      else Async.slowPath.tapReady[A](AsyncEncoding.deliverSuccess[A](r), f)
    }

    /**
     * Run `finalizer` after `fa` completes — success, failure, or suspension —
     * and propagate the original outcome. A failure in `finalizer` is
     * suppressed (the original outcome wins).
     */
    def ensuring(finalizer: Async[Any]): Async[A] = {
      val r: Any = fa
      if (r.isInstanceOf[Pollable[_]])
        Async.slowPath.ensuringAsync[A](r, finalizer)
      else {
        val a = AsyncEncoding.deliverSuccess[A](r)
        Async.slowPath.runThenValue[A](finalizer, a, suppressFailure = true)
      }
    }

    /** Transform the cause of any [[Failure]]; values pass through. */
    def mapError(f: Throwable => Throwable): Async[A] = {
      val r: Any = fa
      if (r.isInstanceOf[Failure]) Async.fail(f(r.asInstanceOf[Failure].cause))
      else if (r.isInstanceOf[Pollable[_]])
        Async.slowPath.catchAllAsync[A, A](r, (t: Throwable) => Async.fail(f(t)))
      else r.asInstanceOf[Async[A]]
    }

    /** Fall back to `that` if `fa` fails. */
    def orElse[A1 >: A](that: => Async[A1]): Async[A1] =
      catchAll((_: Throwable) => that)

    /**
     * Fold a possibly-failed [[Async]] into a guaranteed-success value by
     * handling both branches. Equivalent to
     * `fa.map(onSuccess).catchAll(t => Async.succeed(onFailure(t)))`.
     */
    def foldCause[B](onFailure: Throwable => B)(onSuccess: A => B): Async[B] = {
      val r: Any = fa
      if (r.isInstanceOf[Failure]) Async.succeed(onFailure(r.asInstanceOf[Failure].cause))
      else if (r.isInstanceOf[Pollable[_]])
        map(onSuccess).catchAll((t: Throwable) => Async.succeed(onFailure(t)))
      else {
        val a = AsyncEncoding.deliverSuccess[A](r)
        Async.succeed(onSuccess(a))
      }
    }

    /** Convert any [[Failure]] into a `Left`; any value into a `Right`. */
    def either: Async[Either[Throwable, A]] = {
      val r: Any = fa
      if (r.isInstanceOf[Failure]) Async.succeed(Left(r.asInstanceOf[Failure].cause))
      else if (r.isInstanceOf[Pollable[_]])
        foldCause((t: Throwable) => Left(t): Either[Throwable, A])((a: A) => Right(a): Either[Throwable, A])
      else {
        val a = AsyncEncoding.deliverSuccess[A](r)
        Async.succeed(Right(a): Either[Throwable, A])
      }
    }

    /** Replace the value with `b`. Equivalent to `fa.map(_ => b)`. */
    def as[B](b: => B): Async[B] = map((_: A) => b)

    /** Discard the value. */
    def unit: Async[Unit] = as(())

    /** Sequence then return `that`'s value (`zipRight`, ZIO's `*>`). */
    def *>[B](that: Async[B]): Async[B] = zipWith(that)((_, b) => b)

    /** Sequence then return `fa`'s value (`zipLeft`, ZIO's `<*`). */
    def <*[B](that: Async[B]): Async[A] = zipWith(that)((a, _) => a)

    /** Eagerly drive `fa` and return a [[Async.Running]] handle. */
    def start: Async.Running[A] = Async.start(fa)
  }

  implicit class AsyncFlattenOps[A](private val ffa: Async[Async[A]]) {

    /** Flatten an `Async[Async[A]]` one level. */
    def flatten: Async[A] = ffa.flatMap((inner: Async[A]) => inner)
  }

  // The callback-to-Async bridge is `Async.promise` (defined on the companion
  // via `AsyncCompanionVersionSpecific` so the Scala 3 sibling can take a
  // `?=>` body). The helpers below pick up the `Completer` from the explicit
  // `implicit c =>` binder and let users write `succeed(x)` / `fail(t)` at
  // top level inside an `Async.promise { ... }` body.

  /** Complete the surrounding [[Async.promise]] block with `value`. */
  def succeed[A](value: A)(implicit c: Completer[A]): Unit = c.succeed(value)

  /** Complete the surrounding [[Async.promise]] block with a failure. */
  def fail[A](cause: Throwable)(implicit c: Completer[A]): Unit = c.fail(cause)

  /**
   * Conditionally evaluate `fa`. Returns `fa.unit` when `cond` is true,
   * `Async.succeed(())` otherwise. The unevaluated `fa` is by-name so it is not
   * constructed when the condition is false.
   */
  def when(cond: Boolean)(fa: => Async[Any]): Async[Unit] =
    if (cond) fa.unit else Async.succeed(())

  /** Inverse of [[when]]: evaluate `fa` only when `cond` is false. */
  def unless(cond: Boolean)(fa: => Async[Any]): Async[Unit] =
    if (!cond) fa.unit else Async.succeed(())
}
