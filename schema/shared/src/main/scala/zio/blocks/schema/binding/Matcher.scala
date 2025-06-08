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
