package zio.blocks.typeid

/**
 * Represents a type parameter in a type definition.
 *
 * @param name
 *   The name of the type parameter (e.g., "A", "K", "V")
 * @param index
 *   The position of the type parameter (0-indexed)
 */
final case class TypeParam(
  name: String,
  index: Int
) {
  override def toString: String = name
}

object TypeParam {

  /** Create a type parameter at position 0 */
  def apply(name: String): TypeParam = TypeParam(name, 0)

  /** Common type parameter names */
  val A: TypeParam = TypeParam("A", 0)
  val B: TypeParam = TypeParam("B", 1)
  val C: TypeParam = TypeParam("C", 2)
  val K: TypeParam = TypeParam("K", 0)
  val V: TypeParam = TypeParam("V", 1)
}
