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

package zio.blocks

/**
 * ZIO Blocks Async — a lightweight asynchronous effect type.
 *
 * An `Async[A]` is a (possibly suspended) computation that eventually yields an
 * `A` or fails with a [[Throwable]]. Create one with [[async.Async.succeed]],
 * [[async.Async.fail]], [[async.Async.attempt]], or [[async.Async.promise]]; a
 * bare `A` is not itself an `Async[A]`. Transform and combine values with the
 * extension methods (`map`, `flatMap`, `catchAll`, `await`, ...) brought into
 * scope by importing this package.
 *
 * ==Quick start==
 * {{{
 *   import zio.blocks.async._
 *
 *   // completes immediately with 42
 *   val a: Async[Int] = Async.promise[Int](c => c.succeed(42))
 *
 *   // ops + block for the result
 *   val n: Int = a.map(_ + 1).block
 * }}}
 */
package object async extends AsyncSyntaxVersionSpecific {

  private[async] val encoding: AsyncEncoding = AsyncEncoding.Instance

  /**
   * A possibly-suspended computation that will eventually yield an `A` (or fail
   * with a [[Throwable]]). A [[async.Pollable]] may be used wherever an
   * `Async[A]` is expected.
   */
  type Async[+A] = encoding.Async[A]
}
