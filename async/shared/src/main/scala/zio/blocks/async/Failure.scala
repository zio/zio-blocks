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
 * Failure outcome of an [[Async]]. Constructed via [[Async.fail]] (or by
 * [[Completer.fail]]); consumed via the `.catchAll` / `.attempt` extensions or
 * surfaced as a thrown [[Throwable]] by `.block`.
 *
 * `Failure` extends `Pollable[Nothing]` so it fits the existing single
 * discriminator — every [[Async]] is either a raw value or a `Pollable`. The
 * suspended-vs-failed distinction is then made by a second
 * `isInstanceOf[Failure]` check on the slow path, keeping the value fast path
 * to a single `isInstanceOf[Pollable[?]]`. `poll` returns `this` (terminal):
 * once a leaf has failed, polling can only re-observe the same failure.
 *
 * NOTE: errors thrown by user code inside `.map(f)` / `.flatMap(f)` are NOT
 * captured — `Async` is eager, so a `throw` in `f` escapes through the call
 * site before any later `.catchAll` runs. To convert thrown exceptions into a
 * Failure, wrap the work in [[Async.attempt]].
 */
final class Failure(val cause: Throwable) extends Pollable[Nothing] {
  def poll(waker: Waker): Async[Nothing] = this
}
