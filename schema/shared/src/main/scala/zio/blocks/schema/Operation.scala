package zio.blocks.schema

/**
 * Operation represents the core operations that can be applied to a DynamicValue.
 */
sealed trait Operation

object Operation {
  /**
   * Set the value to a specific DynamicValue (clobber semantics).
   */
  final case class Set(value: DynamicValue) extends Operation
  
  /**
   * Apply a delta operation to a primitive value.
   */
  final case class PrimitiveDelta(op: PrimitiveOp) extends Operation
  
  /**
   * Apply a sequence of edits to a sequence value.
   */
  final case class SequenceEdit(ops: Vector[SeqOp]) extends Operation
  
  /**
   * Apply a sequence of edits to a map value.
   */
  final case class MapEdit(ops: Vector[MapOp]) extends Operation
  
  /**
   * Apply a record field patch.
   */
  final case class RecordPatch(fieldOps: Vector[(String, Operation)]) extends Operation

  /**
   * Apply an operation to a DynamicValue.
   */
  def apply(value: DynamicValue, op: Operation, mode: PatchMode): Either[SchemaError, DynamicValue] = op match {
    case Set(newValue) =>
      Right(newValue)
    
    case PrimitiveDelta(primOp) =>
      value match {
        case DynamicValue.Primitive(pv) =>
          PrimitiveOp.apply(pv, primOp).map(DynamicValue.Primitive(_))
        case _ =>
          Left(SchemaError(SchemaError.TypeMismatch("Primitive", value.getClass.getSimpleName)))
      }
    
    case SequenceEdit(ops) =>
      value match {
        case DynamicValue.Sequence(elements) =>
          SeqOp.applyAll(elements, ops, mode).map(DynamicValue.Sequence(_))
        case _ =>
          Left(SchemaError(SchemaError.TypeMismatch("Sequence", value.getClass.getSimpleName)))
      }
    
    case MapEdit(ops) =>
      value match {
        case DynamicValue.Map(entries) =>
          MapOp.applyAll(entries, ops, mode).map(DynamicValue.Map(_))
        case _ =>
          Left(SchemaError(SchemaError.TypeMismatch("Map", value.getClass.getSimpleName)))
      }
    
    case RecordPatch(fieldOps) =>
      value match {
        case DynamicValue.Record(fields) =>
          var result = fields
          var i = 0
          while (i < fieldOps.length) {
            val (fieldName, fieldOp) = fieldOps(i)
            val fieldIdx = result.indexWhere(_._1 == fieldName)
            if (fieldIdx < 0) {
              if (mode != PatchMode.Lenient) 
                return Left(SchemaError(SchemaError.FieldNotFound(fieldName)))
            } else {
              apply(result(fieldIdx)._2, fieldOp, mode) match {
                case Right(modified) => result = result.updated(fieldIdx, (fieldName, modified))
                case left => 
                  if (mode != PatchMode.Lenient) return left
              }
            }
            i += 1
          }
          Right(DynamicValue.Record(result))
        case _ =>
          Left(SchemaError(SchemaError.TypeMismatch("Record", value.getClass.getSimpleName)))
      }
  }
}
