package zio.blocks.schema

import zio.blocks.schema.SchemaError

/**
 * Bidirectional conversion between types A and B.
 *
 * As[A, B] extends Into[A, B], so it can be used anywhere an Into is expected.
 * Use `reverse` to get an As[B, A] that reverses the direction.
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

  /** Reverses the direction of this As, producing an As[B, A]. */
  def reverse: As[B, A] = {
    val self = this
    new As[B, A] {
      def into(input: B): Either[SchemaError, A] = self.from(input)
      def from(input: A): Either[SchemaError, B] = self.into(input)
    }
  }
}

object As extends AsVersionSpecific with AsLowPriorityImplicits {

  /** Creates an As[A, B] from two Into instances. */
  def apply[A, B](intoAB: Into[A, B], intoBA: Into[B, A]): As[A, B] = new As[A, B] {
    def into(input: A): Either[SchemaError, B] = intoAB.into(input)
    def from(input: B): Either[SchemaError, A] = intoBA.into(input)
  }

  def apply[A, B](implicit ev: As[A, B]): As[A, B] = ev
}

trait AsLowPriorityImplicits {

  implicit def reverseInto[A, B](implicit as: As[A, B]): Into[B, A] =
    (input: B) => as.from(input)
}
