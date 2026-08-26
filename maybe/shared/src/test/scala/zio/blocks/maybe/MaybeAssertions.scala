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

package zio.blocks.maybe

import zio.test._

/**
 * ZIO Test assertions for `Maybe`, mirroring `isSome`/`isSome(assertion)` for
 * `Option`.
 *
 * Implementations use only the version-agnostic public API (`isAbsent`,
 * `isPresent`, `get`) and allocate nothing: no `Option`, `Some`/`None`, or
 * `Present` wrapper is constructed by the matchers themselves. The only cost is
 * the inner `Assertion` invocation in `isPresent(assertion)`.
 */
object MaybeAssertions {

  /** Asserts that the `Maybe` is absent. */
  def isAbsent: Assertion[Maybe[Any]] =
    Assertion.assertion("isAbsent")(_.isAbsent)

  /** Asserts that the `Maybe` is present, without inspecting the value. */
  def isPresent: Assertion[Maybe[Any]] =
    Assertion.assertion("isPresent")(_.isPresent)

  /**
   * Asserts that the `Maybe` is present and its value satisfies the specified
   * assertion.
   */
  def isPresent[A](assertion: Assertion[A]): Assertion[Maybe[A]] =
    Assertion.assertion("isPresent(" + assertion + ")")(m => m.isPresent && assertion.test(m.get))
}
