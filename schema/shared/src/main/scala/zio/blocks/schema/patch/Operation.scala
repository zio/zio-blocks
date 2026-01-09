package zio.blocks.schema.patch

import zio.blocks.schema.DynamicValue

//The top-level operation type for patches. Each operation describes a change to be applied to a DynamicValue.
sealed trait Operation

object Operation {

  // Set a value directly (clobber semantics). Replaces the target value entirely.
  final case class Set(value: DynamicValue) extends Operation

  // Apply a primitive delta operation. Used for numeric increments, string edits, temporal adjustments, etc.
  final case class PrimitiveDelta(op: PrimitiveOp) extends Operation

  // Apply sequence edit operations. Used for inserting, appending, deleting, or modifying sequence elements.
  final case class SequenceEdit(ops: Vector[SeqOp]) extends Operation

  // Apply map edit operations. Used for adding, removing, or modifying map entries.
  final case class MapEdit(ops: Vector[MapOp]) extends Operation
}
