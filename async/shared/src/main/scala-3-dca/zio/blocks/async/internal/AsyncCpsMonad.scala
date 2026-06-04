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

import cps.CpsTryMonadInstanceContext

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
      applyF(TrySuccess(r.asInstanceOf[A]))
  }
}

/**
 * Implicit handle to the singleton [[AsyncCpsMonad]] for DCA's implicit search.
 */
given asyncCpsMonad: CpsTryMonadInstanceContext[Async] = AsyncCpsMonad
