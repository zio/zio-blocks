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

package zio.blocks.endpoint

import zio.blocks.combinators.Unions

/**
 * Version-specific members of the [[Alternator]] companion: none on Scala 2,
 * the union-backed instances on Scala 3.
 */
private[endpoint] trait AlternatorPlatformSpecific {
  self: Alternator.type =>

  /**
   * Wraps `unions` as an [[Alternator]] whose `Out` is the union `L | R`;
   * `combine` and `separate` call it. A `Unions` instance does not exist, so
   * this does not compile, when `L` and `R` share a type or `R` is itself a
   * union.
   */
  def fromUnions[L, R, O](unions: Unions.Unions.WithOut[L, R, O]): WithOut[L, R, O] =
    new Alternator[L, R] {
      type Out = O

      def combine(either: Either[L, R]): O = unions.combine(either)

      def separate(out: O): Either[L, R] = unions.separate(out)
    }

  /**
   * Derives a union-backed [[Alternator]] when the expected `Out` is `L | R`;
   * when `Out` is left open, the [[zio.blocks.combinators.Eithers]] instance is
   * chosen instead.
   */
  given fromUnionsGiven[L, R, O](using unions: Unions.Unions.WithOut[L, R, O]): WithOut[L, R, O] =
    fromUnions(unions)
}
