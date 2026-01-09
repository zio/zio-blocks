package zio.blocks.schema.patch

sealed trait StringOp

object StringOp {

  // Insert text at the given index.
  final case class Insert(index: Int, text: String) extends StringOp

  // Delete characters starting at the given index.
  final case class Delete(index: Int, length: Int) extends StringOp
}
