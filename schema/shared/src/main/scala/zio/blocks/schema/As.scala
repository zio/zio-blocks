package zio.blocks.schema

import zio.blocks.schema.SchemaError

/**
 * A bidirectional conversion between types A and B.
 *
 * As[A, B] extends Into[A, B], so it can be used anywhere an Into is expected.
 * Use `swap` to get an As[B, A] that reverses the direction.
 *
 * For As[A, B] to be derivable, the bidirectional conversion must be
 * compatible:
 *
 * Compatibility Rules:
 *   - Field mappings must be consistent: The same field correspondence in both
 *     directions
 *   - Coercions must be invertible with runtime validation (Int ↔ Long, Float ↔
 *     Double, etc.)
 *   - Optional fields: Can add optional fields in one direction (becomes None
 *     in reverse)
 *   - Default values: Cannot use default arguments (breaks round-trip
 *     guarantee)
 *   - Collection types: Can convert between different collection types (may be
 *     lossy for Set/List conversions)
 */
trait As[A, B] extends Into[A, B] {
  def from(input: B): Either[SchemaError, A]

  /**
   * Returns an As[B, A] with swapped directions. The `into` method becomes
   * `from` and vice versa.
   */
  def reverse: As[B, A] = {
    val self = this
    new As[B, A] {
      def into(input: B): Either[SchemaError, A] = self.from(input)
      def from(input: A): Either[SchemaError, B] = self.into(input)
    }
  }
}

object As extends AsVersionSpecific with AsLowPriorityImplicits {

  /**
   * Creates an As[A, B] from two Into instances.
   */
  def apply[A, B](intoAB: Into[A, B], intoBA: Into[B, A]): As[A, B] = new As[A, B] {
    def into(input: A): Either[SchemaError, B] = intoAB.into(input)
    def from(input: B): Either[SchemaError, A] = intoBA.into(input)
  }

  def apply[A, B](implicit ev: As[A, B]): As[A, B] = ev
}

/**
 * Low priority implicit for extracting Into[B, A] from As[A, B]. This is placed
 * in a separate trait to have lower priority than direct Into instances.
 */
trait AsLowPriorityImplicits {

  /**
   * Extracts Into[B, A] from As[A, B]. This allows container instances like
   * `optionInto` to work when only As is in scope. For example, if `As[A, B]`
   * is implicit, this provides `Into[B, A]` for the reverse direction.
   */
  implicit def asReverse[A, B](implicit as: As[A, B]): Into[B, A] = new Into[B, A] {
    def into(input: B): Either[SchemaError, A] = as.from(input)
  }
}
