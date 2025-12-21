package zio.blocks.schema

import scala.collection.immutable.ArraySeq

/**
 * An untyped patch that operates on DynamicValue.
 * This is the core of the patch system, with typed Patch[A] wrapping this.
 * 
 * DynamicPatch is serializable and can be transported across systems.
 */
final case class DynamicPatch(ops: Vector[DynamicPatchOp]) {
  
  /**
   * Compose two patches sequentially.
   * The result applies `this` patch first, then `that` patch.
   */
  def ++(that: DynamicPatch): DynamicPatch = 
    DynamicPatch(this.ops ++ that.ops)
  
  /**
   * Apply this patch to a dynamic value with the specified mode.
   */
  def apply(value: DynamicValue, mode: PatchMode): Either[SchemaError, DynamicValue] = {
    var current = value
    val len = ops.length
    var idx = 0
    
    while (idx < len) {
      val op = ops(idx)
      applyOp(current, op, mode) match {
        case Right(v) => current = v
        case Left(err) => 
          mode match {
            case PatchMode.Strict => return Left(err)
            case PatchMode.Lenient => // Skip this operation
            case PatchMode.Clobber => // Should not happen as clobber always succeeds
          }
      }
      idx += 1
    }
    
    Right(current)
  }
  
  private def applyOp(
    value: DynamicValue, 
    patchOp: DynamicPatchOp, 
    mode: PatchMode
  ): Either[SchemaError, DynamicValue] = {
    val nodes = patchOp.optic.nodes
    if (nodes.isEmpty) {
      applyOperation(value, patchOp.operation, mode, Nil)
    } else {
      applyAtPath(value, nodes.toList, patchOp.operation, mode, Nil)
    }
  }
  
  private def applyAtPath(
    value: DynamicValue,
    path: List[DynamicOptic.Node],
    operation: Operation,
    mode: PatchMode,
    trace: List[DynamicOptic.Node]
  ): Either[SchemaError, DynamicValue] = {
    path match {
      case Nil => 
        applyOperation(value, operation, mode, trace)
        
      case DynamicOptic.Node.Field(name) :: rest =>
        value match {
          case DynamicValue.Record(fields) =>
            val fieldIndex = fields.indexWhere(_._1 == name)
            if (fieldIndex < 0) {
              mode match {
                case PatchMode.Strict =>
                  Left(SchemaError.missingField(trace.reverse, name))
                case PatchMode.Lenient =>
                  Right(value)
                case PatchMode.Clobber =>
                  // In clobber mode, we can't create a field that doesn't exist
                  // without knowing its schema
                  Right(value)
              }
            } else {
              val (fieldName, fieldValue) = fields(fieldIndex)
              applyAtPath(fieldValue, rest, operation, mode, DynamicOptic.Node.Field(name) :: trace) match {
                case Right(newFieldValue) =>
                  Right(DynamicValue.Record(fields.updated(fieldIndex, (fieldName, newFieldValue))))
                case left => left
              }
            }
          case _ =>
            Left(SchemaError.expectationMismatch(trace.reverse, s"Expected Record, got ${value.getClass.getSimpleName}"))
        }
        
      case DynamicOptic.Node.Case(name) :: rest =>
        value match {
          case DynamicValue.Variant(caseName, caseValue) =>
            if (caseName == name) {
              applyAtPath(caseValue, rest, operation, mode, DynamicOptic.Node.Case(name) :: trace) match {
                case Right(newValue) => Right(DynamicValue.Variant(caseName, newValue))
                case left => left
              }
            } else {
              mode match {
                case PatchMode.Strict =>
                  Left(SchemaError.expectationMismatch(
                    trace.reverse, 
                    s"Expected case $name, got $caseName"
                  ))
                case PatchMode.Lenient => Right(value)
                case PatchMode.Clobber => Right(value)
              }
            }
          case _ =>
            Left(SchemaError.expectationMismatch(trace.reverse, s"Expected Variant, got ${value.getClass.getSimpleName}"))
        }
        
      case DynamicOptic.Node.AtIndex(index) :: rest =>
        value match {
          case DynamicValue.Sequence(elements) =>
            if (index < 0 || index >= elements.length) {
              mode match {
                case PatchMode.Strict =>
                  Left(SchemaError.expectationMismatch(trace.reverse, s"Index $index out of bounds (length: ${elements.length})"))
                case _ => Right(value)
              }
            } else {
              applyAtPath(elements(index), rest, operation, mode, DynamicOptic.Node.AtIndex(index) :: trace) match {
                case Right(newElement) =>
                  Right(DynamicValue.Sequence(elements.updated(index, newElement)))
                case left => left
              }
            }
          case _ =>
            Left(SchemaError.expectationMismatch(trace.reverse, s"Expected Sequence, got ${value.getClass.getSimpleName}"))
        }
        
      case DynamicOptic.Node.AtMapKey(key) :: rest =>
        value match {
          case DynamicValue.Map(entries) =>
            val keyDyn = key.asInstanceOf[DynamicValue]
            val entryIndex = entries.indexWhere(_._1 == keyDyn)
            if (entryIndex < 0) {
              mode match {
                case PatchMode.Strict =>
                  Left(SchemaError.expectationMismatch(trace.reverse, s"Key not found in map"))
                case _ => Right(value)
              }
            } else {
              val (k, v) = entries(entryIndex)
              applyAtPath(v, rest, operation, mode, DynamicOptic.Node.AtMapKey(key) :: trace) match {
                case Right(newValue) =>
                  Right(DynamicValue.Map(entries.updated(entryIndex, (k, newValue))))
                case left => left
              }
            }
          case _ =>
            Left(SchemaError.expectationMismatch(trace.reverse, s"Expected Map, got ${value.getClass.getSimpleName}"))
        }
        
      case DynamicOptic.Node.Elements :: rest =>
        value match {
          case DynamicValue.Sequence(elements) =>
            var idx = 0
            val len = elements.length
            val newElements = new Array[DynamicValue](len)
            while (idx < len) {
              applyAtPath(elements(idx), rest, operation, mode, DynamicOptic.Node.Elements :: trace) match {
                case Right(v) => newElements(idx) = v
                case Left(err) =>
                  mode match {
                    case PatchMode.Strict => return Left(err)
                    case _ => newElements(idx) = elements(idx)
                  }
              }
              idx += 1
            }
            Right(DynamicValue.Sequence(newElements.toVector))
          case _ =>
            Left(SchemaError.expectationMismatch(trace.reverse, s"Expected Sequence, got ${value.getClass.getSimpleName}"))
        }
        
      case DynamicOptic.Node.MapValues :: rest =>
        value match {
          case DynamicValue.Map(entries) =>
            var idx = 0
            val len = entries.length
            val newEntries = new Array[(DynamicValue, DynamicValue)](len)
            while (idx < len) {
              val (k, v) = entries(idx)
              applyAtPath(v, rest, operation, mode, DynamicOptic.Node.MapValues :: trace) match {
                case Right(newV) => newEntries(idx) = (k, newV)
                case Left(err) =>
                  mode match {
                    case PatchMode.Strict => return Left(err)
                    case _ => newEntries(idx) = (k, v)
                  }
              }
              idx += 1
            }
            Right(DynamicValue.Map(newEntries.toVector))
          case _ =>
            Left(SchemaError.expectationMismatch(trace.reverse, s"Expected Map, got ${value.getClass.getSimpleName}"))
        }
        
      case other :: _ =>
        Left(SchemaError.expectationMismatch(trace.reverse, s"Unsupported optic node: $other"))
    }
  }
  
  private def applyOperation(
    value: DynamicValue,
    operation: Operation,
    mode: PatchMode,
    trace: List[DynamicOptic.Node]
  ): Either[SchemaError, DynamicValue] = {
    operation match {
      case Operation.Set(newValue) =>
        Right(newValue)
        
      case Operation.PrimitiveDelta(op) =>
        value match {
          case DynamicValue.Primitive(pv) =>
            PrimitiveOp.applyOp(pv, op) match {
              case Right(newPv) => Right(DynamicValue.Primitive(newPv))
              case Left(err) =>
                Left(SchemaError.expectationMismatch(trace.reverse, err))
            }
          case _ =>
            Left(SchemaError.expectationMismatch(trace.reverse, s"Expected Primitive, got ${value.getClass.getSimpleName}"))
        }
        
      case Operation.SequenceEdit(seqOps) =>
        value match {
          case DynamicValue.Sequence(elements) =>
            applySeqOps(elements, seqOps, mode, trace)
          case _ =>
            Left(SchemaError.expectationMismatch(trace.reverse, s"Expected Sequence, got ${value.getClass.getSimpleName}"))
        }
        
      case Operation.MapEdit(mapOps) =>
        value match {
          case DynamicValue.Map(entries) =>
            applyMapOps(entries, mapOps, mode, trace)
          case _ =>
            Left(SchemaError.expectationMismatch(trace.reverse, s"Expected Map, got ${value.getClass.getSimpleName}"))
        }
    }
  }
  
  private def applySeqOps(
    elements: Vector[DynamicValue],
    ops: Vector[SeqOp],
    mode: PatchMode,
    trace: List[DynamicOptic.Node]
  ): Either[SchemaError, DynamicValue] = {
    var current = elements
    
    ops.foreach {
      case SeqOp.Insert(index, values) =>
        if (index < 0 || index > current.length) {
          mode match {
            case PatchMode.Strict =>
              return Left(SchemaError.expectationMismatch(trace.reverse, s"Insert index $index out of bounds"))
            case PatchMode.Lenient => // Skip
            case PatchMode.Clobber =>
              // Append at nearest valid position
              val validIndex = Math.max(0, Math.min(index, current.length))
              val (before, after) = current.splitAt(validIndex)
              current = before ++ values ++ after
          }
        } else {
          val (before, after) = current.splitAt(index)
          current = before ++ values ++ after
        }
        
      case SeqOp.Append(values) =>
        current = current ++ values
        
      case SeqOp.Delete(index, count) =>
        if (index < 0 || index >= current.length) {
          mode match {
            case PatchMode.Strict =>
              return Left(SchemaError.expectationMismatch(trace.reverse, s"Delete index $index out of bounds"))
            case _ => // Skip
          }
        } else {
          val actualCount = Math.min(count, current.length - index)
          current = current.take(index) ++ current.drop(index + actualCount)
        }
        
      case SeqOp.Modify(index, op) =>
        if (index < 0 || index >= current.length) {
          mode match {
            case PatchMode.Strict =>
              return Left(SchemaError.expectationMismatch(trace.reverse, s"Modify index $index out of bounds"))
            case _ => // Skip
          }
        } else {
          applyOperation(current(index), op, mode, trace) match {
            case Right(newValue) => current = current.updated(index, newValue)
            case Left(err) if mode == PatchMode.Strict => return Left(err)
            case _ => // Skip on error in lenient/clobber
          }
        }
    }
    
    Right(DynamicValue.Sequence(current))
  }
  
  private def applyMapOps(
    entries: Vector[(DynamicValue, DynamicValue)],
    ops: Vector[MapOp],
    mode: PatchMode,
    trace: List[DynamicOptic.Node]
  ): Either[SchemaError, DynamicValue] = {
    var current = entries
    
    ops.foreach {
      case MapOp.Add(key, value) =>
        val existingIndex = current.indexWhere(_._1 == key)
        if (existingIndex >= 0) {
          mode match {
            case PatchMode.Strict =>
              return Left(SchemaError.expectationMismatch(trace.reverse, s"Key already exists in map"))
            case PatchMode.Lenient => // Skip
            case PatchMode.Clobber =>
              current = current.updated(existingIndex, (key, value))
          }
        } else {
          current = current :+ (key, value)
        }
        
      case MapOp.Remove(key) =>
        val existingIndex = current.indexWhere(_._1 == key)
        if (existingIndex < 0) {
          mode match {
            case PatchMode.Strict =>
              return Left(SchemaError.expectationMismatch(trace.reverse, s"Key not found in map"))
            case _ => // Skip
          }
        } else {
          current = current.take(existingIndex) ++ current.drop(existingIndex + 1)
        }
        
      case MapOp.Modify(key, op) =>
        val existingIndex = current.indexWhere(_._1 == key)
        if (existingIndex < 0) {
          mode match {
            case PatchMode.Strict =>
              return Left(SchemaError.expectationMismatch(trace.reverse, s"Key not found in map"))
            case _ => // Skip
          }
        } else {
          val (k, v) = current(existingIndex)
          applyOperation(v, op, mode, trace) match {
            case Right(newValue) => current = current.updated(existingIndex, (k, newValue))
            case Left(err) if mode == PatchMode.Strict => return Left(err)
            case _ => // Skip on error
          }
        }
    }
    
    Right(DynamicValue.Map(current))
  }
}

object DynamicPatch {
  
  /**
   * Empty patch (monoid identity).
   */
  val empty: DynamicPatch = DynamicPatch(Vector.empty)
  
  /**
   * Create a patch with a single operation at the root.
   */
  def single(operation: Operation): DynamicPatch =
    DynamicPatch(Vector(DynamicPatchOp(DynamicOptic.root, operation)))
  
  /**
   * Create a patch with a single operation at the specified path.
   */
  def at(optic: DynamicOptic, operation: Operation): DynamicPatch =
    DynamicPatch(Vector(DynamicPatchOp(optic, operation)))
}

/**
 * A single operation to apply at a specific location.
 */
final case class DynamicPatchOp(optic: DynamicOptic, operation: Operation)
