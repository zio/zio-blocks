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
 */
private[async] trait AsyncCompanionVersionSpecific {

  /**
   * Create an [[Async]] completed by `body`, which is given a [[Completer]] to
   * `succeed` or `fail`. If the body completes the completer before returning,
   * the result is already completed; otherwise it remains pending until the
   * completer is completed (e.g. from a callback).
   */
  inline def promise[A](inline body: Completer[A] ?=> Unit): Async[A] =
    Async.promiseInternal[A](c => body(using c))

  /**
   * Evaluate `body` eagerly, capturing any thrown `Throwable` as a failed
   * [[Async]] (see [[Async.fail]]). The standard way to bridge throw-based code
   * into `Async` so that `.catchAll` can recover the error.
   *
   * `inline` so `body` is spliced directly into the `try` at the call site:
   * unlike a by-name `body: => A` parameter, no `Function0` thunk is allocated
   * (the JVM's escape analysis usually scalar-replaces such a thunk on a hot
   * monomorphic path anyway, but inlining removes it unconditionally —
   * including on cold, megamorphic, and code-generated call sites such as a
   * no-`await` `Async.async { ... }`).
   *
   * Note: `attempt` catches every `Throwable`, with no non-fatal/fatal
   * distinction. Callers who want fatal errors to propagate should rethrow them
   * from their recovery handler.
   */
  inline def attempt[A](inline body: A): Async[A] =
    try Async.succeed(body)
    catch { case t: Throwable => Async.fail(t) }

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
   */
  transparent inline def async[A](inline body: A): Async[A] =
    ${ zio.blocks.async.internal.AsyncDirect.asyncImpl[A]('body) }
}
