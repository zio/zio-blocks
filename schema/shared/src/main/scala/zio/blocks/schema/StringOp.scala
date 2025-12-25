package zio.blocks.schema

/**
 * StringOp represents edit operations on strings.
 * Used with LCS algorithm to compute minimal edit sequences.
 */
sealed trait StringOp

object StringOp {
  /**
   * Insert text at the given index.
   */
  final case class Insert(index: Int, text: String) extends StringOp
  
  /**
   * Delete characters starting at index for the given length.
   */
  final case class Delete(index: Int, length: Int) extends StringOp

  /**
   * Apply a single string operation to a string.
   */
  def apply(s: String, op: StringOp): Either[SchemaError, String] = op match {
    case Insert(index, text) =>
      if (index < 0 || index > s.length) 
        Left(SchemaError(SchemaError.IndexOutOfBounds(index, s.length)))
      else
        Right(s.substring(0, index) + text + s.substring(index))
    
    case Delete(index, length) =>
      if (index < 0 || index > s.length)
        Left(SchemaError(SchemaError.IndexOutOfBounds(index, s.length)))
      else if (index + length > s.length)
        Left(SchemaError(SchemaError.IndexOutOfBounds(index + length, s.length)))
      else
        Right(s.substring(0, index) + s.substring(index + length))
  }

  /**
   * Apply a sequence of string operations to a string.
   * Operations are applied in reverse order to handle index shifts correctly.
   */
  def applyAll(s: String, ops: Vector[StringOp]): Either[SchemaError, String] = {
    var result = s
    var i = ops.length - 1
    while (i >= 0) {
      apply(result, ops(i)) match {
        case Right(r) => result = r
        case left => return left
      }
      i -= 1
    }
    Right(result)
  }
}
