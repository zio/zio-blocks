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
 * JVM-only runtime-await fallback for DCA.
 *
 * The DCA macro rewrites `.block` into `flatMap`/`map` chains for all
 * positions it understands (sequential, `if`, `while`, `try`/`catch`, `match`,
 * and higher-order calls that have an `AsyncShift`). For the residual cases it
 * cannot rewrite — e.g. an `.block` inside a plain user lambda with no
 * shift — it falls back to this instance, which drives the `Async` to its value
 * by blocking the calling thread (Loom-friendly, via the same parker as
 * [[Async.block]]).
 *
 * There is deliberately no equivalent on JS: a non-rewritable `.block`
 * there is a compile error, since JavaScript cannot block.
 *
 * This is **not** a [[cps.CpsRuntimeAsyncAwait]] — providing only the plain
 * `CpsRuntimeAwait` keeps the macro rewrite as the primary path and uses
 * blocking strictly as a last resort.
 */
given asyncRuntimeAwait: CpsRuntimeAwait[Async] with {
  def await[A](fa: Async[A])(ctx: CpsTryMonadContext[Async]): A =
    fa.block
}
