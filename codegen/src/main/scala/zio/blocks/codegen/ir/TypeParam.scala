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
  lowerBound: Option[TypeRef] = None
)
