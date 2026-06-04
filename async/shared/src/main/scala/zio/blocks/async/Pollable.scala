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
 * A suspended computation. `poll` returns the value (ready) or a [[Pollable]]
 * (still pending — it has stashed `waker` and will call `wake()` later).
 *
 * Top-level (not nested under a runtime) so that the encoding's discriminator —
 * "is this `Async[A]` a value or a `Pollable[A]`?" — is a single
 * `isInstanceOf[Pollable[?]]` check both at the JVM level and statically inside
 * the inline extension methods.
 *
 * `Pollable[A] <: Async[A]` via [[AsyncEncoding]]'s `Later[+A] <: Async[A]`
 * bound, so a pollable can flow directly into an `Async[A]` position with no
 * cast at the type level (compiler erases both to `Object`).
 */
abstract class Pollable[+A] {
  def poll(waker: Waker): Async[A]
}
