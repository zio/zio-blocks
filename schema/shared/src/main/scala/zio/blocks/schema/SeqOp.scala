package zio.blocks.schema

/**
 * Operations for sequence (list, vector, etc.) editing.
 * These operations represent structural changes to ordered collections.
 */
sealed trait SeqOp

object SeqOp {
  
  /**
   * Insert values at the specified index.
   * In Strict mode, fails if index is out of bounds.
   * 
   * @param index The position where values should be inserted
   * @param values The values to insert
   */
  final case class Insert(index: Int, values: Vector[DynamicValue]) extends SeqOp
  
  /**
   * Append values to the end of the sequence.
   * Always succeeds regardless of PatchMode.
   * 
   * @param values The values to append
   */
  final case class Append(values: Vector[DynamicValue]) extends SeqOp
  
  /**
   * Delete elements starting at the specified index.
   * In Strict mode, fails if index is out of bounds or count exceeds remaining elements.
   * 
   * @param index The starting position for deletion
   * @param count The number of elements to delete
   */
  final case class Delete(index: Int, count: Int) extends SeqOp
  
  /**
   * Modify the element at the specified index with a nested operation.
   * In Strict mode, fails if index is out of bounds.
   * 
   * @param index The position of the element to modify
   * @param op The operation to apply to the element
   */
  final case class Modify(index: Int, op: Operation) extends SeqOp
}
