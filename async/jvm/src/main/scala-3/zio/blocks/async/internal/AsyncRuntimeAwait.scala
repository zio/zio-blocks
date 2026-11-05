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

import cps.{CpsRuntimeAwait, CpsTryMonadContext}

import zio.blocks.async.*

/**
 * JVM-only runtime-await instance for dotty-cps-async (DCA).
 *
 * The DCA transform rewrites `.await` into `flatMap`/`map` chains for the
 * positions it understands (sequential code, `if`, `while`, `try`/`catch`,
 * `match`, and higher-order calls that have an `AsyncShift`). For an `.await`
 * it cannot rewrite, such as one inside a plain user lambda with no shift, DCA
 * would use this instance, which drives the `Async` to its value by blocking
 * the calling thread with the same parker as `block`.
 *
 * DCA does not find this `given` on its own, so such an `.await` fails to
 * compile ("Can't find AsyncShift") unless the user imports
 * `zio.blocks.async.internal.asyncRuntimeAwait`, a known bug. There is no
 * equivalent on JS, where such an `.await` is a compile error, since JavaScript
 * cannot block.
 *
 * This is **not** a [[cps.CpsRuntimeAsyncAwait]] — providing only the plain
 * `CpsRuntimeAwait` keeps the macro rewrite as the primary path and uses
 * blocking strictly as a last resort.
 */
given asyncRuntimeAwait: CpsRuntimeAwait[Async] with {
  def await[A](fa: Async[A])(ctx: CpsTryMonadContext[Async]): A =
    Async.slowPath.blockGeneric[A](fa)
}
