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
 * Type-encoding holder for [[Async]]. The whole reason this trait exists is to
 * give us an abstract type member that can be ascribed to its bare type in the
 * `async` package object, hiding the `= Any` representation behind an
 * opaque-style interface that compiles identically under Scala 2.13 and Scala 3
 * — no opaque types, no version-specific type definitions.
 *
 * The single declared bound — `Async[+A] >: Pollable[A]` — is what lets a
 * [[Pollable]] flow into an `Async[A]` position without a cast at the type
 * level (e.g. `Completer.poll` returns `this`, callers write
 * `val a: Async[Int] = somePollable`). Raw `A` values are NOT a subtype, so
 * callers must enter the encoding through [[Async.succeed]].
 *
 * No methods live here: every operation is either an inline extension on
 * `Async[A]` (see `AsyncSyntaxVersionSpecific`) which folds the encoding via
 * `isInstanceOf[Pollable[?]]`, or a slow-path helper in
 * `zio.blocks.async.internal.AsyncSlowPath`.
 */
private[async] abstract class AsyncEncoding {
  type Async[+A] >: Pollable[A]
}

private[async] object AsyncEncoding {

  /**
   * The one and only encoding instance. Ascribed to the bare [[AsyncEncoding]]
   * type at the `val` site (in the package object) so the `= Any` rep stays
   * hidden from outside callers.
   */
  val Instance: AsyncEncoding = new AsyncEncoding {
    type Async[+A] = Any
  }
}
