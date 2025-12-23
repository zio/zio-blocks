package zio.blocks.schema

import zio.blocks.schema.SchemaError

/**
 * A bidirectional conversion between types A and B.
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
trait As[A, B] {
  def into(input: A): Either[SchemaError, B]
  def from(input: B): Either[SchemaError, A]
}

object As extends AsVersionSpecific {

  /**
   * Creates an As[A, B] from two Into instances.
   */
  def apply[A, B](intoAB: Into[A, B], intoBA: Into[B, A]): As[A, B] = new As[A, B] {
    def into(input: A): Either[SchemaError, B] = intoAB.into(input)
    def from(input: B): Either[SchemaError, A] = intoBA.into(input)
  }
}
