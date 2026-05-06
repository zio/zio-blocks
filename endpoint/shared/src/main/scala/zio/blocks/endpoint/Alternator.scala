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

import zio.blocks.combinators.Eithers

/**
 * Endpoint-local adapter for fallback output shapes.
 *
 * The endpoint AST stores one abstraction that can be backed by either
 * [[zio.blocks.combinators.Eithers]] on all Scala versions or
 * `zio.blocks.combinators.Unions` on Scala 3. This keeps `HttpCodec.Fallback`
 * and auth/error composition platform-agnostic while still delegating all
 * combine / separate logic to the `combinators` block library.
 */
trait Alternator[L, R] {
  type Out

  def combine(either: Either[L, R]): Out

  def separate(out: Out): Either[L, R]
}

object Alternator extends AlternatorPlatformSpecific {
  type WithOut[L, R, O] = Alternator[L, R] { type Out = O }

  /**
   * Adapts an [[zio.blocks.combinators.Eithers]] instance into endpoint
   * fallback shape.
   */
  def fromEithers[L, R, O](eithers: Eithers.Eithers.WithOut[L, R, O]): WithOut[L, R, O] =
    new Alternator[L, R] {
      type Out = O

      def combine(either: Either[L, R]): O = eithers.combine(either)

      def separate(out: O): Either[L, R] = eithers.separate(out)
    }

  /**
   * Reuses the canonical [[zio.blocks.combinators.Eithers]] derivation for
   * endpoint fallbacks.
   */
  implicit def fromEithersImplicit[L, R, O](implicit eithers: Eithers.Eithers.WithOut[L, R, O]): WithOut[L, R, O] =
    fromEithers(eithers)
}
