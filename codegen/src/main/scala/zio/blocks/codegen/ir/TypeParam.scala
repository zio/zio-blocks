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

package zio.blocks.codegen.ir

/**
 * Represents type parameter variance.
 */
sealed trait Variance

object Variance {
  case object Invariant     extends Variance
  case object Covariant     extends Variance
  case object Contravariant extends Variance
}

/**
 * Represents a type parameter in the IR, with optional variance and bounds.
 *
 * @param name
 *   The name of the type parameter (e.g., "A", "B")
 * @param variance
 *   The variance of the type parameter (defaults to Invariant)
 * @param upperBound
 *   An optional upper bound type (e.g., Some(TypeRef("Serializable")) for `A <:
 *   Serializable`)
 * @param lowerBound
 *   An optional lower bound type (e.g., Some(TypeRef.Nothing) for `A >:
 *   Nothing`)
 *
 * @example
 *   {{{
 * // Simple invariant type param
 * val a = TypeParam("A")
 *
 * // Covariant type param
 * val covariant = TypeParam("A", Variance.Covariant)
 *
 * // With upper bound
 * val bounded = TypeParam("A", upperBound = Some(TypeRef("Serializable")))
 *   }}}
 */
final case class TypeParam(
  name: String,
  variance: Variance = Variance.Invariant,
  upperBound: Option[TypeRef] = None,
  lowerBound: Option[TypeRef] = None,
  typeParams: List[TypeParam] = Nil,
  contextBounds: List[TypeRef] = Nil
)
