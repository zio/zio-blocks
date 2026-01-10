package zio.blocks.schema

/**
 * Represents an operation that can be applied to a DynamicValue.
 */
sealed trait Operation

object Operation {

  /**
   * Set the value directly (clobber semantics). Replaces the entire value at
   * the target location.
   *
   * @param value
   *   The new value to set
   */
  final case class Set(value: DynamicValue) extends Operation

  /**
   * Apply a delta operation to a primitive value.
   *
   * @param op
   *   The primitive delta operation
   */
  final case class PrimitiveDelta(op: PrimitiveOp) extends Operation

  /**
   * Apply a sequence of edit operations to a sequence value.
   *
   * @param ops
   *   The sequence operations to apply (in order)
   */
  final case class SequenceEdit(ops: Vector[SeqOp]) extends Operation

  /**
   * Apply a sequence of edit operations to a map value.
   *
   * @param ops
   *   The map operations to apply (in order)
   */
  final case class MapEdit(ops: Vector[MapOp]) extends Operation
}
