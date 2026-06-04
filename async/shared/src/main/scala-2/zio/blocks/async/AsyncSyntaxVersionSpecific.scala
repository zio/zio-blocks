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
 * Scala 2 surface for [[Async]]: an `implicit class` of ops on `Async[A]` and
 * an `implicit`-param `promise` / `succeed` / `fail` builder. Mixed into the
 * `async` package object so `import zio.blocks.async._` exposes the full DSL.
 *
 * Scala 2 has no `inline`, so the extensions are real methods on a regular
 * `AsyncOps` class (not `AnyVal` — `Async[A]` is an abstract type member, which
 * is not eligible for value-class erasure). Each body folds the encoding inline
 * via `isInstanceOf[Pollable[_]]` and delegates only the suspended branch to
 * [[AsyncSlowPath]], mirroring the Scala 3 shape so the two surfaces stay
 * observationally identical.
 *
 * Failure propagation is handled identically to Scala 3: [[Failure]] is a
 * [[Pollable]], so `map` / `flatMap` route it to the slow path, which
 * short-circuits past `f`; `catchAll` intercepts in the slow branch.
 *
 * Call sugar form: `Async.promise[Int] { implicit c => succeed(42) }`.
 */
private[async] trait AsyncSyntaxVersionSpecific {

  implicit class AsyncOps[A](private val fa: Async[A]) {

    def flatMap[B](f: A => Async[B]): Async[B] = {
      val r: Any = fa
      if (r.isInstanceOf[Pollable[_]])
        AsyncSlowPath.flatMapAsync[A, B](r, f)
      else f(r.asInstanceOf[A])
    }

    def map[B](f: A => B): Async[B] = {
      val r: Any = fa
      if (r.isInstanceOf[Pollable[_]])
        AsyncSlowPath.mapAsync[A, B](r, f)
      else f(r.asInstanceOf[A]).asInstanceOf[Async[B]]
    }

    def catchAll[A1 >: A](f: Throwable => Async[A1]): Async[A1] = {
      val r: Any = fa
      if (r.isInstanceOf[Pollable[_]])
        if (r.isInstanceOf[Failure]) f(r.asInstanceOf[Failure].cause)
        else AsyncSlowPath.catchAllAsync[A, A1](r, f)
      else r.asInstanceOf[Async[A1]]
    }

    /**
     * Drive `fa` to its value, blocking the calling thread if necessary. The
     * unsafe escape hatch: ready values return immediately, pending values
     * block the (Loom-friendly) calling thread on the JVM, and on JS a
     * genuinely pending value throws. The direct-style `.await` operator is
     * Scala 3 only for now (the Scala 2 macro arrives in a later phase).
     */
    def block: A = {
      val r: Any = fa
      if (r.isInstanceOf[Pollable[_]])
        AsyncSlowPath.awaitSuspended[A](r.asInstanceOf[Pollable[A]])
      else r.asInstanceOf[A]
    }

    def zipWith[B, C](that: Async[B])(f: (A, B) => C): Async[C] = {
      val ra: Any = fa
      val rb: Any = that
      if (ra.isInstanceOf[Pollable[_]] || rb.isInstanceOf[Pollable[_]])
        AsyncSlowPath.zipWithAsync[A, B, C](ra, rb, f)
      else f(ra.asInstanceOf[A], rb.asInstanceOf[B]).asInstanceOf[Async[C]]
    }

    def zip[B](that: Async[B])(implicit t: Tuples[A, B]): Async[t.Out] =
      zipWith(that)((a, b) => t.combine(a, b))

    def tap(f: A => Async[Any]): Async[A] = {
      val r: Any = fa
      if (r.isInstanceOf[Pollable[_]])
        AsyncSlowPath.tapAsync[A](r, f)
      else {
        val a = r.asInstanceOf[A]
        AsyncSlowPath.runThenValue[A](f(a), a, suppressFailure = false)
      }
    }

    def ensuring(finalizer: Async[Any]): Async[A] = {
      val r: Any = fa
      if (r.isInstanceOf[Pollable[_]])
        AsyncSlowPath.ensuringAsync[A](r, finalizer)
      else {
        val a = r.asInstanceOf[A]
        AsyncSlowPath.runThenValue[A](finalizer, a, suppressFailure = true)
      }
    }

    def mapError(f: Throwable => Throwable): Async[A] =
      catchAll((t: Throwable) => Async.fail(f(t)))

    def orElse[A1 >: A](that: => Async[A1]): Async[A1] =
      catchAll((_: Throwable) => that)

    def foldCause[B](onFailure: Throwable => B)(onSuccess: A => B): Async[B] =
      map(onSuccess).catchAll((t: Throwable) => Async.succeed(onFailure(t)))

    def either: Async[Either[Throwable, A]] =
      foldCause((t: Throwable) => Left(t): Either[Throwable, A])((a: A) => Right(a): Either[Throwable, A])

    /** Replace the value with `b`. */
    def as[B](b: B): Async[B] = map((_: A) => b)

    /** Discard the value. */
    def unit: Async[Unit] = as(())

    /** Sequence then return `that`'s value (`zipRight`, ZIO's `*>`). */
    def *>[B](that: Async[B]): Async[B] = zipWith(that)((_, b) => b)

    /** Sequence then return `fa`'s value (`zipLeft`, ZIO's `<*`). */
    def <*[B](that: Async[B]): Async[A] = zipWith(that)((a, _) => a)
  }

  implicit class AsyncFlattenOps[A](private val ffa: Async[Async[A]]) {
    def flatten: Async[A] = ffa.flatMap((fa: Async[A]) => fa)
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
