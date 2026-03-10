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
 * Represents the bounds of a type parameter or abstract type member.
 *
 * In Scala, type bounds are expressed as:
 *   - `A <: Upper` (upper bound only)
 *   - `A >: Lower` (lower bound only)
 *   - `A >: Lower <: Upper` (both bounds)
 *
 * When no bound is specified:
 *   - Lower bound defaults to `Nothing`
 *   - Upper bound defaults to `Any`
 *
 * @param lower
 *   The lower bound (>:), None means Nothing
 * @param upper
 *   The upper bound (<:), None means Any
 */
final case class TypeBounds(
  lower: Option[TypeRepr] = None,
  upper: Option[TypeRepr] = None
) {

  /**
   * Returns true if no bounds are specified (unbounded).
   */
  def isUnbounded: Boolean = lower.isEmpty && upper.isEmpty

  /**
   * Returns true if only an upper bound is specified.
   */
  def hasOnlyUpper: Boolean = lower.isEmpty && upper.isDefined

  /**
   * Returns true if only a lower bound is specified.
   */
  def hasOnlyLower: Boolean = lower.isDefined && upper.isEmpty

  /**
   * Returns true if both bounds are specified.
   */
  def hasBothBounds: Boolean = lower.isDefined && upper.isDefined

  /**
   * Returns true if this represents a type alias (lower == upper).
   */
  def isAlias: Boolean = lower.isDefined && lower == upper

  /**
   * Returns the alias type if this represents a type alias.
   */
  def aliasType: Option[TypeRepr] = if (isAlias) lower else None
}

object TypeBounds {

  /**
   * Unbounded type bounds (>: Nothing <: Any).
   */
  val Unbounded: TypeBounds = TypeBounds()

  /**
   * Creates bounds with only an upper bound.
   */
  def upper(bound: TypeRepr): TypeBounds = TypeBounds(upper = Some(bound))

  /**
   * Creates bounds with only a lower bound.
   */
  def lower(bound: TypeRepr): TypeBounds = TypeBounds(lower = Some(bound))

  /**
   * Creates bounds with both lower and upper bounds.
   */
  def apply(lower: TypeRepr, upper: TypeRepr): TypeBounds =
    TypeBounds(Some(lower), Some(upper))

  /**
   * Creates type alias bounds (lower == upper).
   */
  def alias(tpe: TypeRepr): TypeBounds = TypeBounds(Some(tpe), Some(tpe))
}
