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
 * The implementation point for a custom suspended [[Async]] leaf (a timer, a
 * socket read, a callback bridge, ...). A `Pollable[A]` is itself an
 * `Async[A]`, so it can be used wherever an `Async[A]` is expected.
 *
 * Implementors define [[poll]]: when asked for its value, a pollable either
 * returns the completed result (a value, or a failed [[Async]]) or, if it is
 * still pending, stashes the supplied [[java.lang.Runnable]] and returns a
 * pending `Async` (typically itself). When the value later becomes available it
 * must invoke `onComplete.run()` to ask the scheduler to poll again.
 */
abstract class Pollable[+A] {

  /**
   * Attempt to produce this computation's value. Returns the completed result
   * if it is ready; otherwise registers `onComplete` (to be invoked when the
   * value becomes available) and returns a still-pending `Async`.
   *
   * Polling is a '''one-shot driver protocol''': a driver should keep polling
   * only while `poll` returns a still-pending `Pollable`, and must stop as soon
   * as it returns a terminal result — a raw value or a failed [[Async]]. The
   * built-in drivers (`block`, `start`, and the interop runners) all follow
   * this, so they never poll a settled computation again.
   *
   * Re-polling a `Pollable` after it has returned a terminal result is outside
   * the contract and is '''not''' guaranteed to be a pure re-observation: a
   * continuation may run again and diagnostic state may be repeated. Drive
   * computations through the built-in runners unless you are implementing this
   * protocol yourself, in which case honor the stop-at-terminal rule.
   */
  def poll(onComplete: Runnable): Async[A]
}
