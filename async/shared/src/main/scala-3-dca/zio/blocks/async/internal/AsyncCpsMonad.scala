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

package zio.blocks.async.internal

import cps.{CpsTryMonadInstanceContext, CpsTryMonadInstanceContextBody}

import zio.blocks.async.*

import scala.util.{Failure as TryFailure, Success as TrySuccess, Try}

/**
 * The dotty-cps-async monad instance for [[Async]]. This is the single bridge
 * between the DCA macro and our encoding: the macro emits `cps.block` /
 * `cps.async` nodes that drive these six methods, all of which delegate to the
 * zero-cost inline `Async` surface (`map` / `flatMap` / `catchAll`).
 *
 * It is an `object` (not an anonymous `given`) so the [[zio.blocks.async]]
 * macro can reference it by a stable fully-qualified name when it splices the
 * generated `cps.async[Async] { ... }` block. The companion `given` below makes
 * the same instance available for ordinary implicit search (e.g. DCA's
 * `summon[CpsMonad[Async]]`).
 *
 * `CpsTryMonadInstanceContext` provides the `Context` type and the `apply`
 * method for free; we only implement the five primitives DCA needs: `pure`,
 * `map`, `flatMap`, `error`, and `flatMapTry`.
 */
object AsyncCpsMonad extends CpsTryMonadInstanceContext[Async] {

  /**
   * DCA's default `apply` allocates a fresh `CpsTryMonadInstanceContextBody`
   * for every `cps.async { ... }` block (see `CpsTryMonadInstanceContext`). The
   * body is a read-only witness whose only field is a back-pointer to this
   * singleton monad; our [[Async]] carries no per-run state (no cancellation
   * token, no environment), so a single shared context is valid for every
   * invocation. Caching it removes a fixed ~144 B/op heap allocation per
   * `Async.async` block — the difference between the DCA path being elidable by
   * escape analysis and not. The CPS-transformed body only ever reads
   * `ctx.monad`; it never mutates, stores, or identity-compares the context.
   */
  private val sharedContext: Context = new CpsTryMonadInstanceContextBody[Async](this)

  override def apply[T](op: Context => Async[T]): Async[T] = op(sharedContext)

  def pure[A](a: A): Async[A] = Async.succeed(a)

  def map[A, B](fa: Async[A])(f: A => B): Async[B] = fa.map(f)

  def flatMap[A, B](fa: Async[A])(f: A => Async[B]): Async[B] = fa.flatMap(f)

  def error[A](e: Throwable): Async[A] = Async.fail(e)

  /**
   * Run `f` against the `Try`-shaped outcome of `fa`. A ready value or a
   * [[Failure]] is dispatched directly (no `Pollable` allocation); a suspended
   * input is reified to `Async[Try[A]]` via `map`/`catchAll` and then handed to
   * `f`. Exceptions thrown by `f` itself are captured as [[Async.fail]] so the
   * DCA `try`/`catch` rewrite observes them as monadic failures.
   */
  def flatMapTry[A, B](fa: Async[A])(f: Try[A] => Async[B]): Async[B] = {
    def applyF(ta: Try[A]): Async[B] =
      try f(ta)
      catch { case t: Throwable => Async.fail(t) }

    val r: Any = fa
    if (r.isInstanceOf[Failure])
      applyF(TryFailure(r.asInstanceOf[Failure].cause))
    else if (r.isInstanceOf[Pollable[?]])
      fa.map((a: A) => TrySuccess(a): Try[A])
        .catchAll((t: Throwable) => Async.succeed(TryFailure(t): Try[A]))
        .flatMap(applyF)
    else
      applyF(TrySuccess(AsyncEncoding.deliverSuccess[A](r)))
  }

  // The CpsTryMonad defaults for the try/finally combinators attach the
  // in-flight failure to a throwing finalizer's exception via an UNGUARDED
  // `addSuppressed(primary)`. `Async.fail(null)` is first-class here, and
  // `Throwable.addSuppressed(null)` throws NullPointerException — destroying
  // both the finalizer's failure and the original. Same logic, null-guarded.

  override def withAction[A](fa: Async[A])(action: => Unit): Async[A] =
    flatMapTry(fa) { ra =>
      try {
        action
        fromTry(ra)
      } catch {
        case ex: Throwable =>
          suppressPrimary(ex, ra)
          error(ex)
      }
    }

  override def withAsyncAction[A](fa: Async[A])(action: => Async[Unit]): Async[A] =
    flatMapTry(fa) { ra =>
      flatMapTry(action) { rb =>
        rb match {
          case TrySuccess(_)  => fromTry(ra)
          case TryFailure(ex) =>
            suppressPrimary(ex, ra)
            error(ex)
        }
      }
    }

  /**
   * Plain-Scala try/finally semantics: a throwing finalizer replaces the
   * in-flight failure, keeping the original reachable as a suppressed exception
   * when that is legal (non-null, distinct — `addSuppressed` throws on both a
   * null argument and self-suppression).
   */
  private def suppressPrimary(ex: Throwable, primary: Try[?]): Unit =
    primary match {
      case TryFailure(ex1) if (ex1 ne null) && (ex1 ne ex) =>
        try ex.addSuppressed(ex1)
        catch { case _: Throwable => () }
      case _ => ()
    }
}

/**
 * Implicit handle to the singleton [[AsyncCpsMonad]] for DCA's implicit search.
 */
given asyncCpsMonad: CpsTryMonadInstanceContext[Async] = AsyncCpsMonad
