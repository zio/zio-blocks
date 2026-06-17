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
 * has no context functions). Same behavior as the Scala 3 sibling.
 */
private[async] trait AsyncCompanionVersionSpecific {

  /**
   * Create an [[Async]] completed by `body`, which is given a [[Completer]] to
   * `succeed` or `fail`. If the body completes the completer before returning,
   * the result is already completed; otherwise it remains pending until the
   * completer is completed (e.g. from a callback).
   */
  def promise[A](body: Completer[A] => Unit): Async[A] =
    Async.promiseInternal(body)

  /**
   * Direct-style await block. Inside `body`, callers may write `.await` on any
   * `Async[X]` to extract its value without blocking; the body returns the
   * final `A`, which becomes the resulting `Async[A]`. Exceptions thrown by the
   * body — including the rethrow from a `.await` of a failed `Async` — surface
   * as [[Async.fail]]. Awaits run in source order.
   *
   * `.await` is lexically restricted to this block — using it anywhere else is
   * a compile error.
   *
   * '''Evaluation is eager up to the first pending suspension''' (see the
   * "Evaluation model" docs): the synchronous prefix and any ready `.await`s
   * run at construction. At a genuinely pending `.await` the platforms diverge
   * by design — on the JVM (and Scala.js < 3.8) the continuation runs only when
   * the result is driven (`.block` / `fa.start` / interop), while the Scala.js
   * 3.8+ native `js.async`/`js.await` arm self-resumes it off the event loop.
   * The delivered value is identical everywhere.
   *   */
  def async[A](body: A): Async[A] = macro internal.AsyncMacros.asyncImpl[A]
}
