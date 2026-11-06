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
 * Converts between the `Either[L, R]` of a fallback and the single `Out` type
 * the fallback exposes.
 *
 * [[HttpCodec.Fallback]] stores one of these instead of a concrete combinator,
 * so the endpoint description is the same on every Scala version. An instance
 * is backed by [[zio.blocks.combinators.Eithers]] on all Scala versions, or by
 * `zio.blocks.combinators.Unions` on Scala 3; both do the `combine` and
 * `separate` work.
 *
 * With an `Eithers` instance, `Out` is an `Either`, and an `Either` on either
 * side is flattened into one left-nested `Either`. With a `Unions` instance,
 * `Out` is the union `L | R`.
 */
trait Alternator[L, R] {

  /** The type the fallback exposes, built from `L` and `R`. */
  type Out

  /**
   * Turns `either` into `Out`. With an `Eithers` instance `Left(1)` stays
   * `Left(1)`; with a `Unions` instance it becomes the bare value `1`.
   */
  def combine(either: Either[L, R]): Out

  /**
   * Splits `out` back into `Left` or `Right`.
   *
   * A `Unions` instance tests the value against `R` at run time: an instance of
   * `R` becomes `Right`, anything else `Left`. So a value of `L` that is also
   * an instance of `R` comes back as `Right`; for example, with `L` a sealed
   * trait `AppError` and `R` its subclass `NotFound`, `Left(NotFound(1))`
   * separates as `Right(NotFound(1))`, and when `L` is a subclass of `R` every
   * value separates as `Right`. The test sees only the erased class, so with
   * `L = List[Int]` and `R = List[String]` (an unchecked-type warning) every
   * list separates as `Right`.
   */
  def separate(out: Out): Either[L, R]
}

/** Constructors and implicit instances of [[Alternator]]. */
object Alternator extends AlternatorPlatformSpecific {

  /** An [[Alternator]] whose `Out` type is fixed to `O`. */
  type WithOut[L, R, O] = Alternator[L, R] { type Out = O }

  /**
   * Wraps `eithers` as an [[Alternator]] with the same `Out`; `combine` and
   * `separate` call it.
   */
  def fromEithers[L, R, O](eithers: Eithers.Eithers.WithOut[L, R, O]): WithOut[L, R, O] =
    new Alternator[L, R] {
      type Out = O

      def combine(either: Either[L, R]): O = eithers.combine(either)

      def separate(out: O): Either[L, R] = eithers.separate(out)
    }

  /**
   * Derives an [[Alternator]] from the implicit
   * [[zio.blocks.combinators.Eithers]] instance for `L` and `R`.
   */
  implicit def fromEithersImplicit[L, R, O](implicit eithers: Eithers.Eithers.WithOut[L, R, O]): WithOut[L, R, O] =
    fromEithers(eithers)
}
