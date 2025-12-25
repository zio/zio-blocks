package zio.blocks.schema

/**
 * SeqOp represents operations on sequences.
 */
sealed trait SeqOp

object SeqOp {

  /**
   * Insert values at the given index. In Strict mode, fails if index is
   * occupied.
   */
  final case class Insert(index: Int, values: Vector[DynamicValue]) extends SeqOp

  /**
   * Append values to the end of the sequence.
   */
  final case class Append(values: Vector[DynamicValue]) extends SeqOp

  /**
   * Delete count elements starting at index.
   */
  final case class Delete(index: Int, count: Int) extends SeqOp

  /**
   * Modify element at index with a nested operation.
   */
  final case class Modify(index: Int, op: Operation) extends SeqOp

  /**
   * Apply a single sequence operation to a sequence.
   */
  def apply(elements: Vector[DynamicValue], op: SeqOp, mode: PatchMode): Either[SchemaError, Vector[DynamicValue]] =
    op match {
      case Insert(index, values) =>
        if (index < 0 || index > elements.length)
          Left(SchemaError(SchemaError.IndexOutOfBounds(index, elements.length)))
        else {
          val (prefix, suffix) = elements.splitAt(index)
          Right(prefix ++ values ++ suffix)
        }

      case Append(values) =>
        Right(elements ++ values)

      case Delete(index, count) =>
        if (index < 0 || index >= elements.length)
          if (mode == PatchMode.Lenient) Right(elements)
          else Left(SchemaError(SchemaError.IndexOutOfBounds(index, elements.length)))
        else if (index + count > elements.length)
          if (mode == PatchMode.Lenient) Right(elements.take(index))
          else Left(SchemaError(SchemaError.IndexOutOfBounds(index + count, elements.length)))
        else
          Right(elements.take(index) ++ elements.drop(index + count))

      case Modify(index, nestedOp) =>
        if (index < 0 || index >= elements.length)
          if (mode == PatchMode.Lenient) Right(elements)
          else Left(SchemaError(SchemaError.IndexOutOfBounds(index, elements.length)))
        else
          Operation.apply(elements(index), nestedOp, mode).map { modified =>
            elements.updated(index, modified)
          }
    }

  /**
   * Apply a sequence of operations to a sequence.
   */
  def applyAll(
    elements: Vector[DynamicValue],
    ops: Vector[SeqOp],
    mode: PatchMode
  ): Either[SchemaError, Vector[DynamicValue]] = {
    var result = elements
    var i      = 0
    while (i < ops.length) {
      apply(result, ops(i), mode) match {
        case Right(r) => result = r
        case left     =>
          if (mode == PatchMode.Lenient) () // skip failed operation
          else return left
      }
      i += 1
    }
    Right(result)
  }
}
