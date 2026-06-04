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

import scala.language.experimental.macros

/**
 * Scala 2 sliver of the [[Async]] companion: [[promise]] takes a
 * `Completer[A] => Unit` body, so callers write
 *
 * {{{
 *   Async.promise[Int] { implicit c => succeed(42) }
 * }}}
 *
 * with `succeed` / `fail` resolved through the explicit `implicit c` (Scala 2
 * has no context functions). Same observed behavior as the Scala 3 sibling —
 * synchronous completion collapses to a bare value, asynchronous completion
 * returns the [[Completer]] itself as the pending `Async[A]`.
 */
private[async] trait AsyncCompanionVersionSpecific {

  /**
   * Run a callback-style block. If the body completes the [[Completer]] before
   * returning, the result collapses to a bare value (no [[Pollable]]
   * allocated); otherwise the [[Completer]] itself is returned as the pending
   * `Async[A]` for the scheduler to drive.
   */
  def promise[A](body: Completer[A] => Unit): Async[A] =
    Async.promiseInternal(body)

  /**
   * Direct-style await block. Inside `body`, callers may write `.await` on any
   * `Async[X]` to extract its value; the body returns the final `A`, which
   * becomes the resulting `Async[A]`. Exceptions thrown by the body — including
   * the rethrow from a `.await` of a failed `Async` — surface as
   * [[Async.fail]].
   *
   * The block is rewritten at compile time by
   * [[zio.blocks.async.internal.AsyncMacros.asyncImpl]]: every `.await` becomes
   * a non-blocking `flatMap`/`map` chain over our single `Async` monad (a
   * single-monad CPS/ANF transform, in the scala-async / monadless tradition).
   * Awaits run in strict source order; a body with no `.await` collapses to
   * [[Async.attempt]] (the zero-suspension fast path). `.await` is lexically
   * restricted to this block — using it anywhere else is a compile error (see
   * `AsyncSyntaxVersionSpecific.await`).
   */
  def async[A](body: A): Async[A] = macro internal.AsyncMacros.asyncImpl[A]
}
