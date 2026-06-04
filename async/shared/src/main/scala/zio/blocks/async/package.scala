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
 * ZIO Blocks Async — a value-carrying (thunk-model) async runtime.
 *
 * `Async[+A]` is encoded via an abstract type member ([[AsyncEncoding]]) whose
 * representation is `Any`. Callers cannot observe the rep (the encoding holder
 * is ascribed to its bare type below), so `A` is NOT a subtype of `Async[A]` —
 * values must enter the encoding through [[async.Async.succeed]], a
 * [[async.Pollable]], or [[async.Async.promise]]. Suspension is a `Pollable`
 * value, never thrown.
 *
 * All operations (`map` / `flatMap` / `await` / `promise` / `succeed` / `fail`)
 * live in the version-specific syntax (`AsyncSyntaxVersionSpecific`) mixed into
 * this package object. There is no separate runtime object: the extensions fold
 * the encoding inline via `isInstanceOf[Pollable[?]]` and delegate only the
 * slow path to `async.internal.AsyncSlowPath`.
 *
 * ==Quick start==
 * {{{
 *   import zio.blocks.async._
 *
 *   // sync completion collapses to a bare Int
 *   val a: Async[Int] = Async.promise[Int](c => c.succeed(42))
 *
 *   // ops + await
 *   val n: Int = a.map(_ + 1).block
 * }}}
 */
package object async extends AsyncSyntaxVersionSpecific {

  /**
   * The encapsulated encoding instance. Ascribed to the bare [[AsyncEncoding]]
   * type so the `= Any` representation stays hidden — this is the Scala-2
   * module-pattern trick, and it works identically in Scala 3.
   */
  private[async] val encoding: AsyncEncoding = AsyncEncoding.Instance

  /**
   * A possibly-suspended computation that will eventually yield an `A`.
   * `Pollable[A] <: Async[A]` is exposed via [[AsyncEncoding]]'s lower bound,
   * so a pollable flows into an `Async[A]` position with no cast.
   */
  type Async[+A] = encoding.Async[A]
}
