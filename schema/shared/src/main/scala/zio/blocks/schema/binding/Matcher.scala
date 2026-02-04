package zio.blocks.schema.binding

/**
 * A `Matcher` is a typeclass that can match a value of type `A` with a specific
 * term of its sum type.
 */
trait Matcher[+A] {

  /**
   * Downcasts a value of type `Any` to a value of type `A` or return `null`.
   */
  def downcastOrNull(any: Any): A
}

/**
 * A matcher for structural types that exist only at compile-time.
 * Structural types cannot be matched at runtime; they work only with DynamicValue.
 */
class StructuralMatcher[+A] extends Matcher[A] {
  def downcastOrNull(any: Any): A = null.asInstanceOf[A]
}
