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
 * Scala 3 sliver of the [[Async]] companion. The public [[promise]] takes a
 * `Completer[A] ?=> Unit` context-function body so callers write
 *
 * {{{
 *   Async.promise[Int] { succeed(42) }
 * }}}
 *
 * with `succeed` / `fail` resolved through the implicit [[Completer]] supplied
 * by the context function — no explicit `c =>` binder needed.
 *
 * Marked `inline` (with an `inline` body parameter) so the call site fuses with
 * the [[Completer]] allocation. HotSpot's escape analysis can scalar- replace
 * the completer when the body completes synchronously — verified via
 * `-XX:+PrintInlining`. The implementation delegates to `Async.promiseInternal`
 * so cross-version interop code can stay on a single `=>` shape regardless of
 * Scala version.
 */
private[async] trait AsyncCompanionVersionSpecific {

  /**
   * Run a callback-style block. If the body completes the [[Completer]] before
   * returning, the result collapses to a bare value (no [[Pollable]]
   * allocated); otherwise the [[Completer]] itself is returned as the pending
   * `Async[A]` for the scheduler to drive.
   */
  inline def promise[A](inline body: Completer[A] ?=> Unit): Async[A] =
    Async.promiseInternal[A](c => body(using c))

  /**
   * Direct-style await block. Inside `body`, callers may write `.await` on any
   * `Async[X]` to extract its value; the body returns the final `A`, which
   * becomes the resulting `Async[A]`. Exceptions thrown by the body — including
   * the rethrow from a `.await` of a failed `Async` — surface as
   * [[Async.fail]].
   *
   * The block is rewritten at compile time by
   * [[zio.blocks.async.internal.AsyncDirect.asyncImpl]]: each `.await` becomes
   * a dotty-cps-async `cps.await`, and the whole body is wrapped in a
   * `cps.async[Async]`, producing a non-blocking `flatMap`/`map` chain. Where a
   * `.await` sits in a position DCA cannot rewrite (e.g. an unshifted
   * higher-order lambda), the JVM falls back to blocking; on JS it is a compile
   * error.
   *
   * `.await` is lexically restricted to this block — using it anywhere else is
   * a compile error.
   */
  transparent inline def async[A](inline body: A): Async[A] =
    ${ zio.blocks.async.internal.AsyncDirect.asyncImpl[A]('body) }
}
