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
   * Eagerly evaluate `body`, capturing a thrown [[Throwable]] as
   * [[Async.fail]].
   *
   * On Scala 2 there is no direct-style `.block` operator yet (the
   * Scala 2 macro arrives in a later phase), so this block currently behaves
   * like [[Async.attempt]]: it runs straight-line, synchronous code and lifts
   * the result (or a thrown exception) into `Async`. The Scala 3 sibling
   * rewrites `.block` calls inside the block into a non-blocking
   * `flatMap` chain via dotty-cps-async; the Scala 2 macro will reach the same
   * semantics.
   */
  def async[A](body: => A): Async[A] =
    try Async.succeed(body)
    catch { case t: Throwable => Async.fail(t) }
}
