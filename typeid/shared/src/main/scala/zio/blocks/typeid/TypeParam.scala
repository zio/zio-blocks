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
 * A type parameter of a type: its name, position, variance, bounds and kind, as
 * [[TypeId#typeParams]] lists them.
 *
 * For `Either[+A, +B]`, `A` is `TypeParam("A", 0, Variance.Covariant)` and `B`
 * is `TypeParam("B", 1, Variance.Covariant)`. The macros record only the name,
 * index and variance: a derived parameter has [[TypeBounds.Unbounded]] bounds
 * and kind [[Kind.Type]], so `F` in `Functor[F[_]]` comes out as
 * `TypeParam("F", 0)` instead of a parameter of kind [[Kind.Star1]] (a known
 * bug). Build a parameter by hand, with [[TypeParam.higherKinded]] for example,
 * to record a kind.
 *
 * @param name
 *   the name of the type parameter
 * @param index
 *   the position in the type parameter list, from 0
 * @param variance
 *   the variance annotation (`+`, `-` or none)
 * @param bounds
 *   the bounds (`>: Lower <: Upper`)
 * @param kind
 *   the kind of the parameter (`*`, `* -> *` and so on)
 */
final case class TypeParam(
  name: String,
  index: Int,
  variance: Variance = Variance.Invariant,
  bounds: TypeBounds = TypeBounds.Unbounded,
  kind: Kind = Kind.Type
) {

  /**
   * Returns true if this type parameter is covariant (+).
   */
  def isCovariant: Boolean = variance.isCovariant

  /**
   * Returns true if this type parameter is contravariant (-).
   */
  def isContravariant: Boolean = variance.isContravariant

  /**
   * Returns true if this type parameter is invariant.
   */
  def isInvariant: Boolean = variance.isInvariant

  /**
   * Returns true if this type parameter has an upper bound.
   */
  def hasUpperBound: Boolean = bounds.upper.isDefined

  /**
   * Returns true if this type parameter has a lower bound.
   */
  def hasLowerBound: Boolean = bounds.lower.isDefined

  /**
   * Returns true if this type parameter is a proper type (kind *).
   */
  def isProperType: Boolean = kind.isProperType

  /**
   * Returns true if this type parameter is a type constructor (kind * -> *
   * etc.).
   */
  def isTypeConstructor: Boolean = !kind.isProperType

  /**
   * Returns a string representation of this type parameter.
   */
  override def toString: String = TypeIdPrinter.render(this)
}

object TypeParam {

  private[typeid] val A: TypeParam        = TypeParam("A", 0, Variance.Covariant)
  private[typeid] val B: TypeParam        = TypeParam("B", 1, Variance.Covariant)
  private[typeid] val C: TypeParam        = TypeParam("C", 2, Variance.Covariant)
  private[typeid] val K: TypeParam        = TypeParam("K", 0, Variance.Invariant)
  private[typeid] val V: TypeParam        = TypeParam("V", 1, Variance.Covariant)
  private[typeid] val F: TypeParam        = TypeParam("F", 0, Variance.Invariant, kind = Kind.Star1)
  private[typeid] val Contra: TypeParam   = TypeParam("T", 0, Variance.Contravariant)
  private[typeid] val CoResult: TypeParam = TypeParam("R", 1, Variance.Covariant)

  /**
   * Creates a covariant type parameter.
   */
  def covariant(name: String, index: Int): TypeParam =
    TypeParam(name, index, Variance.Covariant)

  /**
   * Creates a contravariant type parameter.
   */
  def contravariant(name: String, index: Int): TypeParam =
    TypeParam(name, index, Variance.Contravariant)

  /**
   * Creates a type parameter with an upper bound.
   */
  def bounded(name: String, index: Int, upper: TypeRepr): TypeParam =
    TypeParam(name, index, bounds = TypeBounds.upper(upper))

  /**
   * Creates a higher-kinded type parameter (F[_]).
   */
  def higherKinded(name: String, index: Int, arity: Int = 1): TypeParam =
    TypeParam(name, index, kind = Kind.constructor(arity))
}
