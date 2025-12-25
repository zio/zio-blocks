package zio.blocks.schema

/**
 * MapOp represents operations on maps.
 */
sealed trait MapOp

object MapOp {
  /**
   * Add a key-value pair.
   * In Strict mode, fails if the key already exists.
   */
  final case class Add(key: DynamicValue, value: DynamicValue) extends MapOp
  
  /**
   * Remove a key.
   * In Strict mode, fails if the key doesn't exist.
   */
  final case class Remove(key: DynamicValue) extends MapOp
  
  /**
   * Modify the value at a key with a nested operation.
   */
  final case class Modify(key: DynamicValue, op: Operation) extends MapOp

  /**
   * Apply a single map operation to map entries.
   */
  def apply(entries: Vector[(DynamicValue, DynamicValue)], op: MapOp, mode: PatchMode): Either[SchemaError, Vector[(DynamicValue, DynamicValue)]] = 
    op match {
      case Add(key, value) =>
        val existingIdx = entries.indexWhere(_._1 == key)
        if (existingIdx >= 0) {
          mode match {
            case PatchMode.Strict => Left(SchemaError(SchemaError.KeyAlreadyExists(key.toString)))
            case PatchMode.Lenient => Right(entries) // skip
            case PatchMode.Clobber => Right(entries.updated(existingIdx, (key, value)))
          }
        } else {
          Right(entries :+ (key, value))
        }
      
      case Remove(key) =>
        val existingIdx = entries.indexWhere(_._1 == key)
        if (existingIdx < 0) {
          mode match {
            case PatchMode.Strict => Left(SchemaError(SchemaError.KeyNotFound(key.toString)))
            case _ => Right(entries)
          }
        } else {
          Right(entries.take(existingIdx) ++ entries.drop(existingIdx + 1))
        }
      
      case Modify(key, nestedOp) =>
        val existingIdx = entries.indexWhere(_._1 == key)
        if (existingIdx < 0) {
          mode match {
            case PatchMode.Strict => Left(SchemaError(SchemaError.KeyNotFound(key.toString)))
            case _ => Right(entries)
          }
        } else {
          Operation.apply(entries(existingIdx)._2, nestedOp, mode).map { modified =>
            entries.updated(existingIdx, (key, modified))
          }
        }
    }

  /**
   * Apply a sequence of operations to map entries.
   */
  def applyAll(entries: Vector[(DynamicValue, DynamicValue)], ops: Vector[MapOp], mode: PatchMode): Either[SchemaError, Vector[(DynamicValue, DynamicValue)]] = {
    var result = entries
    var i = 0
    while (i < ops.length) {
      apply(result, ops(i), mode) match {
        case Right(r) => result = r
        case left => 
          if (mode == PatchMode.Lenient) () // skip failed operation
          else return left
      }
      i += 1
    }
    Right(result)
  }
}
