package zio.blocks.schema

/**
 * Represents an edit operation on a sequence (Vector, List, etc.).
 */
sealed trait SeqOp

object SeqOp {

  /**
   * Insert values at the specified index.
   * In Strict mode, fails if index is out of bounds.
   *
   * @param index Position where values should be inserted
   * @param values The values to insert
   */
  final case class Insert(index: Int, values: Vector[DynamicValue]) extends SeqOp

  /**
   * Append values to the end of the sequence.
   * Always succeeds.
   *
   * @param values The values to append
   */
  final case class Append(values: Vector[DynamicValue]) extends SeqOp

  /**
   * Delete count elements starting at the specified index.
   * In Strict mode, fails if range is out of bounds.
   *
   * @param index Position where deletion starts
   * @param count Number of elements to delete
   */
  final case class Delete(index: Int, count: Int) extends SeqOp

  /**
   * Modify the element at the specified index with a nested operation.
   * In Strict mode, fails if index is out of bounds.
   *
   * @param index Position of element to modify
   * @param op The operation to apply to the element
   */
  final case class Modify(index: Int, op: Operation) extends SeqOp
}
