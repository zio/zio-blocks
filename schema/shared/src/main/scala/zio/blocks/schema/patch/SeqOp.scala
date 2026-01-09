package zio.blocks.schema.patch

import zio.blocks.schema.DynamicValue

sealed trait SeqOp

object SeqOp {

  // Insert elements at the given index.
  final case class Insert(index: Int, values: Vector[DynamicValue]) extends SeqOp

  // Append elements to the end of the sequence.
  final case class Append(values: Vector[DynamicValue]) extends SeqOp

  // Delete elements starting at the given index.
  final case class Delete(index: Int, count: Int) extends SeqOp

  // Modify the element at the given index with a nested operation.
  final case class Modify(index: Int, op: Operation) extends SeqOp
}
