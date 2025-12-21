package zio.blocks.schema

/**
 * Operations for string editing based on LCS (Longest Common Subsequence) algorithm.
 * These operations represent the minimal set of edits needed to transform one string into another.
 */
sealed trait StringOp

object StringOp {
  
  /**
   * Insert text at the specified index.
   * 
   * @param index The position where text should be inserted
   * @param text The text to insert
   */
  final case class Insert(index: Int, text: String) extends StringOp
  
  /**
   * Delete characters starting at the specified index.
   * 
   * @param index The starting position for deletion
   * @param length The number of characters to delete
   */
  final case class Delete(index: Int, length: Int) extends StringOp
  
  /**
   * Apply a sequence of string operations to transform a string.
   * Operations are applied in order, with indices adjusted for previous operations.
   */
  def applyOps(source: String, ops: Vector[StringOp]): String = {
    val sb = new StringBuilder(source)
    var offset = 0
    
    ops.foreach {
      case Insert(index, text) =>
        sb.insert(index + offset, text)
        offset += text.length
        
      case Delete(index, length) =>
        sb.delete(index + offset, index + offset + length)
        offset -= length
    }
    
    sb.toString
  }
}
