package zio.blocks.typeid

/**
 * Represents a type parameter in a type definition.
 *
 * @param name  The name of the type parameter (e.g., "A", "F", "B")
 * @param index The zero-based position of this parameter in the parameter list
 */
final case class TypeParam(name: String, index: Int) {
  override def equals(that: Any): Boolean = that match {
    case TypeParam(thatName, thatIndex) => name == thatName && index == thatIndex
    case _                              => false
  }

  override def hashCode(): Int = name.hashCode ^ index.hashCode
}
