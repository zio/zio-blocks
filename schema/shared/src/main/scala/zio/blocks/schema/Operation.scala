package zio.blocks.schema

/**
 * Represents an operation that can be applied to a value at a specific location.
 * Operations are the building blocks of patches and can be composed.
 */
sealed trait Operation

object Operation {
  
  /**
   * Set a value directly (clobber semantics).
   * This replaces the target value entirely.
   * 
   * @param value The new value to set
   */
  final case class Set(value: DynamicValue) extends Operation
  
  /**
   * Apply a primitive delta operation (increment, string edit, etc.).
   * 
   * @param op The primitive operation to apply
   */
  final case class PrimitiveDelta(op: PrimitiveOp) extends Operation
  
  /**
   * Apply a sequence of edits to a sequence value.
   * 
   * @param ops The sequence operations to apply
   */
  final case class SequenceEdit(ops: Vector[SeqOp]) extends Operation
  
  /**
   * Apply a sequence of edits to a map value.
   * 
   * @param ops The map operations to apply
   */
  final case class MapEdit(ops: Vector[MapOp]) extends Operation
}
