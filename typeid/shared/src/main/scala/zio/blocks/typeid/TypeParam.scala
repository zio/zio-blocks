package zio.blocks.typeid

/**
 * Represents a type parameter specification.
 *
 * A TypeParam captures the essential information about a type parameter,
 * including its name and position (index) in the type parameter list.
 *
 * For example, for `Either[A, B]`:
 *   - `A` would be `TypeParam("A", 0)`
 *   - `B` would be `TypeParam("B", 1)`
 */
final case class TypeParam(
  name: String,
  index: Int
  // TODO: Uncomment and implement these features in the future
//  variance: TypeParamVariance,
//  bounds: TypeParamBounds,
//  kind: TypeParamKind,
) {

  /**
   * Returns a string representation of this type parameter.
   */
  override def toString: String = s"$name@$index"
}

object TypeParam {

  // Internal predefined type parameters for common usage like Options, Either, Maps, etc.
  private[typeid] val A: TypeParam = TypeParam("A", 0)
  private[typeid] val B: TypeParam = TypeParam("B", 1)
  private[typeid] val C: TypeParam = TypeParam("C", 2)
  private[typeid] val K: TypeParam = TypeParam("K", 0)
  private[typeid] val V: TypeParam = TypeParam("V", 1)
  private[typeid] val F: TypeParam = TypeParam("F", 0)
}
