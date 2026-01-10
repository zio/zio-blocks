package zio.blocks.schema

/**
 * Represents an edit operation on a string.
 * Used for LCS-based string diffing.
 */
sealed trait StringOp

object StringOp {

  /**
   * Insert text at the specified index.
   *
   * @param index Position in string where text should be inserted
   * @param text The text to insert
   */
  final case class Insert(index: Int, text: String) extends StringOp

  /**
   * Delete characters starting at the specified index.
   *
   * @param index Position in string where deletion starts
   * @param length Number of characters to delete
   */
  final case class Delete(index: Int, length: Int) extends StringOp
}
