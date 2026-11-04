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

package zio.blocks.typeid

/**
 * Represents the variance of a type parameter.
 *
 * Variance determines how subtyping of type arguments affects subtyping of
 * parameterized types:
 *
 *   - Covariant (+A): If `Dog <: Animal`, then `List[Dog] <: List[Animal]`
 *   - Contravariant (-A): If `Dog <: Animal`, then
 *     `Handler[Animal] <: Handler[Dog]`
 *   - Invariant (A): No subtyping relationship between `Box[Dog]` and
 *     `Box[Animal]`
 */
sealed trait Variance {

  /**
   * Returns the Scala syntax for this variance annotation.
   */
  def symbol: String = this match {
    case Variance.Covariant     => "+"
    case Variance.Contravariant => "-"
    case Variance.Invariant     => ""
  }

  /**
   * Returns true if this is covariant.
   */
  def isCovariant: Boolean = this == Variance.Covariant

  /**
   * Returns true if this is contravariant.
   */
  def isContravariant: Boolean = this == Variance.Contravariant

  /**
   * Returns true if this is invariant.
   */
  def isInvariant: Boolean = this == Variance.Invariant

  /**
   * Flips the variance (covariant <-> contravariant, invariant stays).
   */
  def flip: Variance = this match {
    case Variance.Covariant     => Variance.Contravariant
    case Variance.Contravariant => Variance.Covariant
    case Variance.Invariant     => Variance.Invariant
  }

  /**
   * Composes two variances, as for a type parameter in a nested position: two
   * contravariant positions give covariant, a covariant and a contravariant
   * position give contravariant, and an invariant position gives invariant.
   */
  def *(other: Variance): Variance = (this, other) match {
    case (Variance.Invariant, _)                          => Variance.Invariant
    case (_, Variance.Invariant)                          => Variance.Invariant
    case (Variance.Covariant, Variance.Covariant)         => Variance.Covariant
    case (Variance.Contravariant, Variance.Contravariant) => Variance.Covariant
    case (Variance.Covariant, Variance.Contravariant)     => Variance.Contravariant
    case (Variance.Contravariant, Variance.Covariant)     => Variance.Contravariant
  }
}

/**
 * The three cases of [[Variance]]: `Covariant`, `Contravariant` and
 * `Invariant`.
 */
object Variance {

  /**
   * Covariant (`+A`): if `Dog <: Animal`, then `F[Dog] <: F[Animal]`.
   */
  case object Covariant extends Variance

  /**
   * Contravariant (`-A`): if `Dog <: Animal`, then `F[Animal] <: F[Dog]`.
   */
  case object Contravariant extends Variance

  /**
   * Invariant (`A`): `F[Dog]` and `F[Animal]` are not subtypes of each other.
   */
  case object Invariant extends Variance
}
