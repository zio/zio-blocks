package zio.blocks.schema

import zio.blocks.schema.Schema

case class DynamicPatch(ops: Vector[DynamicPatchOp]) {
  def ++(that: DynamicPatch): DynamicPatch = DynamicPatch(ops ++ that.ops)

  def apply(value: DynamicValue, mode: PatchMode = PatchMode.Strict): Either[SchemaError, DynamicValue] =
    ops.foldLeft[Either[SchemaError, DynamicValue]](Right(value)) {
      case (Right(v), op) => op.apply(v, mode)
      case (Left(e), _)   => Left(e)
    }
}

case class DynamicPatchOp(optic: DynamicOptic, operation: Operation) {
  def apply(root: DynamicValue, mode: PatchMode): Either[SchemaError, DynamicValue] =
    DynamicPatchOp.applyAt(root, optic.nodes.toList, operation, mode, List.empty)
}

object DynamicPatchOp {
  implicit val schema: Schema[DynamicPatchOp] = Schema.derived

  def applyAt(
    current: DynamicValue, 
    path: List[DynamicOptic.Node], 
    op: Operation, 
    mode: PatchMode,
    trace: List[DynamicOptic.Node]
  ): Either[SchemaError, DynamicValue] = path match {
    case Nil => op.apply(current, mode)
    case head :: tail =>
      val currentTrace = trace :+ head
      
      (head, current) match {
        // Record Field
        case (DynamicOptic.Node.Field(name), DynamicValue.Record(fields)) =>
          val idx = fields.indexWhere(_._1 == name)
          if (idx >= 0) {
            val (fieldName, oldValue) = fields(idx)
            applyAt(oldValue, tail, op, mode, currentTrace).map { newValue =>
              DynamicValue.Record(fields.updated(idx, fieldName -> newValue))
            }
          } else {
            if (mode == PatchMode.Strict) Left(SchemaError.missingField(currentTrace, name))
            else Right(current)
          }
        
        // Variant Case
        case (DynamicOptic.Node.Case(name), DynamicValue.Variant(c, v)) =>
          if (c == name) {
            applyAt(v, tail, op, mode, currentTrace).map { newValue =>
              DynamicValue.Variant(c, newValue)
            }
          } else {
            if (mode == PatchMode.Strict) Left(SchemaError.unknownCase(currentTrace, name))
            else Right(current)
          }

        // Sequence Index
        case (DynamicOptic.Node.AtIndex(idx), DynamicValue.Sequence(elems)) =>
          if (idx >= 0 && idx < elems.length) {
            applyAt(elems(idx), tail, op, mode, currentTrace).map { newValue =>
               DynamicValue.Sequence(elems.updated(idx, newValue))
            }
          } else {
             if (mode == PatchMode.Strict) Left(SchemaError.expectationMismatch(currentTrace, s"Index $idx out of bounds"))
             else Right(current)
          }

        // Elements (Traversal - applies to ALL elements of a sequence)
        // For each element, try to apply the operation. If path doesn't match (e.g., Case mismatch),
        // keep the original element unchanged. Only fail if there are NO successful matches in Strict mode.
        case (DynamicOptic.Node.Elements, DynamicValue.Sequence(elems)) =>
          if (elems.isEmpty) {
            if (mode == PatchMode.Strict) Left(SchemaError.expectationMismatch(currentTrace, "Empty sequence"))
            else Right(current)
          } else {
            // Try to apply to all elements. On failure, keep original element.
            val updatedElems = elems.map { elem =>
              applyAt(elem, tail, op, mode, currentTrace) match {
                case Right(updated) => updated
                case Left(_) => elem  // Keep original on path mismatch
              }
            }
            Right(DynamicValue.Sequence(updatedElems))
          }

        // Map Key
        case (DynamicOptic.Node.AtMapKey(key), DynamicValue.Map(entries)) =>
           val idx = entries.indexWhere(_._1 == key)
           if (idx >= 0) {
             val (k, oldValue) = entries(idx)
             applyAt(oldValue, tail, op, mode, currentTrace).map { newValue =>
               DynamicValue.Map(entries.updated(idx, k -> newValue))
             }
           } else {
             if (mode == PatchMode.Strict) Left(SchemaError.missingField(currentTrace, key.toString))
             else Right(current)
           }

        case _ => 
          if (mode == PatchMode.Strict) Left(SchemaError.expectationMismatch(currentTrace, s"Path not found or type mismatch at $head"))
          else Right(current)
      }
  }
}

sealed trait Operation {
  def apply(value: DynamicValue, mode: PatchMode): Either[SchemaError, DynamicValue]
}

object Operation {
  implicit val schema: Schema[Operation] = Schema.derived

  case class Set(value: DynamicValue) extends Operation {
    def apply(target: DynamicValue, mode: PatchMode): Either[SchemaError, DynamicValue] = Right(value)
  }

  case class PrimitiveDelta(op: PrimitiveOp) extends Operation {
    def apply(target: DynamicValue, mode: PatchMode): Either[SchemaError, DynamicValue] = 
      op.apply(target)
  }

  case class SequenceEdit(ops: Vector[SeqOp]) extends Operation {
     def apply(target: DynamicValue, mode: PatchMode): Either[SchemaError, DynamicValue] = target match {
       case DynamicValue.Sequence(elements) => 
         ops.foldLeft[Either[SchemaError, Vector[DynamicValue]]](Right(elements)) {
           case (Right(seq), op) => op.apply(seq, mode)
           case (Left(e), _) => Left(e)
         }.map(DynamicValue.Sequence(_))
       case _ => Left(SchemaError.expectationMismatch(Nil, "Expected Sequence for SequenceEdit"))
     }
  }

  case class MapEdit(ops: Vector[MapOp]) extends Operation {
    def apply(target: DynamicValue, mode: PatchMode): Either[SchemaError, DynamicValue] = target match {
       case DynamicValue.Map(entries) =>
         ops.foldLeft[Either[SchemaError, Vector[(DynamicValue, DynamicValue)]]](Right(entries)) {
           case (Right(seq), op) => op.apply(seq, mode)
           case (Left(e), _) => Left(e)
         }.map(DynamicValue.Map(_))
       case _ => Left(SchemaError.expectationMismatch(Nil, "Expected Map for MapEdit"))
    }
  }
}

sealed trait PrimitiveOp {
  def apply(value: DynamicValue): Either[SchemaError, DynamicValue]
}
object PrimitiveOp {
  implicit val schema: Schema[PrimitiveOp] = Schema.derived

  case class IntDelta(delta: Int) extends PrimitiveOp {
    def apply(value: DynamicValue): Either[SchemaError, DynamicValue] = value match {
      case DynamicValue.Primitive(PrimitiveValue.Int(v)) => Right(DynamicValue.Primitive(PrimitiveValue.Int(v + delta)))
      case _ => Left(SchemaError.expectationMismatch(Nil, "Expected Int"))
    }
  }
  case class LongDelta(delta: Long) extends PrimitiveOp {
    def apply(value: DynamicValue): Either[SchemaError, DynamicValue] = value match {
      case DynamicValue.Primitive(PrimitiveValue.Long(v)) => Right(DynamicValue.Primitive(PrimitiveValue.Long(v + delta)))
      case _ => Left(SchemaError.expectationMismatch(Nil, "Expected Long"))
    }
  }
   case class FloatDelta(delta: Float) extends PrimitiveOp {
    def apply(value: DynamicValue): Either[SchemaError, DynamicValue] = value match {
      case DynamicValue.Primitive(PrimitiveValue.Float(v)) => Right(DynamicValue.Primitive(PrimitiveValue.Float(v + delta)))
      case _ => Left(SchemaError.expectationMismatch(Nil, "Expected Float"))
    }
  }
  case class DoubleDelta(delta: Double) extends PrimitiveOp {
    def apply(value: DynamicValue): Either[SchemaError, DynamicValue] = value match {
      case DynamicValue.Primitive(PrimitiveValue.Double(v)) => Right(DynamicValue.Primitive(PrimitiveValue.Double(v + delta)))
      case _ => Left(SchemaError.expectationMismatch(Nil, "Expected Double"))
    }
  }

  case class BigIntDelta(delta: BigInt) extends PrimitiveOp {
    def apply(value: DynamicValue): Either[SchemaError, DynamicValue] = value match {
      case DynamicValue.Primitive(PrimitiveValue.BigInt(v)) => Right(DynamicValue.Primitive(PrimitiveValue.BigInt(v + delta)))
      case _ => Left(SchemaError.expectationMismatch(Nil, "Expected BigInt"))
    }
  }

  case class BigDecimalDelta(delta: BigDecimal) extends PrimitiveOp {
    def apply(value: DynamicValue): Either[SchemaError, DynamicValue] = value match {
      case DynamicValue.Primitive(PrimitiveValue.BigDecimal(v)) => Right(DynamicValue.Primitive(PrimitiveValue.BigDecimal(v + delta)))
      case _ => Left(SchemaError.expectationMismatch(Nil, "Expected BigDecimal"))
    }
  }

  case class DurationDelta(delta: java.time.Duration) extends PrimitiveOp {
    def apply(value: DynamicValue): Either[SchemaError, DynamicValue] = value match {
      case DynamicValue.Primitive(PrimitiveValue.Duration(v)) => Right(DynamicValue.Primitive(PrimitiveValue.Duration(v.plus(delta))))
      case _ => Left(SchemaError.expectationMismatch(Nil, "Expected Duration"))
    }
  }

  case class InstantDelta(delta: java.time.Duration) extends PrimitiveOp {
    def apply(value: DynamicValue): Either[SchemaError, DynamicValue] = value match {
      case DynamicValue.Primitive(PrimitiveValue.Instant(v)) => Right(DynamicValue.Primitive(PrimitiveValue.Instant(v.plus(delta))))
      case _ => Left(SchemaError.expectationMismatch(Nil, "Expected Instant"))
    }
  }

  case class StringEdit(ops: Vector[StringOp]) extends PrimitiveOp {
     def apply(value: DynamicValue): Either[SchemaError, DynamicValue] = value match {
       case DynamicValue.Primitive(PrimitiveValue.String(s)) =>
         ops.foldLeft[Either[SchemaError, String]](Right(s)) {
           case (Right(curr), op) => op.apply(curr)
           case (Left(e), _) => Left(e)
         }.map(res => DynamicValue.Primitive(PrimitiveValue.String(res)))
       case _ => Left(SchemaError.expectationMismatch(Nil, "Expected String"))
     }
  }
}

sealed trait StringOp {
  def apply(s: String): Either[SchemaError, String]
}
object StringOp {
  implicit val schema: Schema[StringOp] = Schema.derived

  case class Insert(index: Int, text: String) extends StringOp {
     def apply(s: String): Either[SchemaError, String] = {
       if (index < 0 || index > s.length) Left(SchemaError.expectationMismatch(Nil, s"String index $index out of bounds"))
       else Right(s.substring(0, index) + text + s.substring(index))
     }
  }
  case class Delete(index: Int, length: Int) extends StringOp {
    def apply(s: String): Either[SchemaError, String] = {
      if (index < 0 || index + length > s.length) Left(SchemaError.expectationMismatch(Nil, s"String deletion out of bounds"))
      else Right(s.substring(0, index) + s.substring(index + length))
    }
  }
}

sealed trait SeqOp {
  def apply(seq: Vector[DynamicValue], mode: PatchMode): Either[SchemaError, Vector[DynamicValue]]
}
object SeqOp {
  implicit val schema: Schema[SeqOp] = Schema.derived

  case class Insert(index: Int, values: Vector[DynamicValue]) extends SeqOp {
    def apply(seq: Vector[DynamicValue], mode: PatchMode): Either[SchemaError, Vector[DynamicValue]] = {
      if (index < 0 || index > seq.length) {
         if (mode == PatchMode.Strict) Left(SchemaError.expectationMismatch(Nil, s"Sequence index $index out of bounds"))
         else Right(seq)
      } else Right(seq.take(index) ++ values ++ seq.drop(index))
    }
  }
  case class Append(values: Vector[DynamicValue]) extends SeqOp {
    def apply(seq: Vector[DynamicValue], mode: PatchMode): Either[SchemaError, Vector[DynamicValue]] = 
      Right(seq ++ values)
  }
  case class Delete(index: Int, count: Int) extends SeqOp {
    def apply(seq: Vector[DynamicValue], mode: PatchMode): Either[SchemaError, Vector[DynamicValue]] = {
      if (index < 0 || index + count > seq.length) {
         if (mode == PatchMode.Strict) Left(SchemaError.expectationMismatch(Nil, s"Sequence index $index out of bounds for delete"))
         else Right(seq)
      } else Right(seq.take(index) ++ seq.drop(index + count))
    }
  }
  case class Modify(index: Int, op: Operation) extends SeqOp {
    def apply(seq: Vector[DynamicValue], mode: PatchMode): Either[SchemaError, Vector[DynamicValue]] = {
      if (index < 0 || index >= seq.length) {
         if (mode == PatchMode.Strict) Left(SchemaError.expectationMismatch(Nil, s"Sequence index $index out of bounds for modify"))
         else Right(seq)
      } else {
        op.apply(seq(index), mode).map(nv => seq.updated(index, nv))
      }
    }
  }
}

sealed trait MapOp {
  def apply(entries: Vector[(DynamicValue, DynamicValue)], mode: PatchMode): Either[SchemaError, Vector[(DynamicValue, DynamicValue)]]
}
object MapOp {
  implicit val schema: Schema[MapOp] = Schema.derived

  case class Add(key: DynamicValue, value: DynamicValue) extends MapOp {
    def apply(entries: Vector[(DynamicValue, DynamicValue)], mode: PatchMode): Either[SchemaError, Vector[(DynamicValue, DynamicValue)]] = {
      if (entries.exists(_._1 == key)) {
         if (mode == PatchMode.Strict) Left(SchemaError.duplicatedField(Nil, key.toString))
         else Right(entries)
      } else Right(entries :+ (key -> value))
    }
  }
  case class Remove(key: DynamicValue) extends MapOp {
    def apply(entries: Vector[(DynamicValue, DynamicValue)], mode: PatchMode): Either[SchemaError, Vector[(DynamicValue, DynamicValue)]] = {
      val idx = entries.indexWhere(_._1 == key)
      if (idx == -1) {
         if (mode == PatchMode.Strict) Left(SchemaError.missingField(Nil, key.toString))
         else Right(entries)
      } else Right(entries.filterNot(_._1 == key))
    }
  }
  case class Modify(key: DynamicValue, op: Operation) extends MapOp {
    def apply(entries: Vector[(DynamicValue, DynamicValue)], mode: PatchMode): Either[SchemaError, Vector[(DynamicValue, DynamicValue)]] = {
      val idx = entries.indexWhere(_._1 == key)
       if (idx == -1) {
         if (mode == PatchMode.Strict) Left(SchemaError.missingField(Nil, key.toString))
         else Right(entries)
      } else {
         val (k, v) = entries(idx)
         op.apply(v, mode).map(nv => entries.updated(idx, k -> nv))
      }
    }
  }
}

object DynamicPatch {
  implicit val schema: Schema[DynamicPatch] = Schema.derived

  def empty: DynamicPatch = DynamicPatch(Vector.empty)

  private def toIdentityOptic: DynamicOptic = DynamicOptic(Vector.empty)

  def diff(oldValue: DynamicValue, newValue: DynamicValue): DynamicPatch = {
    (oldValue, newValue) match {
       case (DynamicValue.Primitive(oldV), DynamicValue.Primitive(newV)) =>
        if (oldV == newV) empty
        else (oldV, newV) match {
          case (PrimitiveValue.Int(o), PrimitiveValue.Int(n)) =>
             DynamicPatch(Vector(DynamicPatchOp(toIdentityOptic, Operation.PrimitiveDelta(PrimitiveOp.IntDelta(n - o)))))
          case (PrimitiveValue.Long(o), PrimitiveValue.Long(n)) =>
             DynamicPatch(Vector(DynamicPatchOp(toIdentityOptic, Operation.PrimitiveDelta(PrimitiveOp.LongDelta(n - o)))))
          case (PrimitiveValue.Float(o), PrimitiveValue.Float(n)) =>
             DynamicPatch(Vector(DynamicPatchOp(toIdentityOptic, Operation.PrimitiveDelta(PrimitiveOp.FloatDelta(n - o)))))
          case (PrimitiveValue.Double(o), PrimitiveValue.Double(n)) =>
             DynamicPatch(Vector(DynamicPatchOp(toIdentityOptic, Operation.PrimitiveDelta(PrimitiveOp.DoubleDelta(n - o)))))
          case (PrimitiveValue.BigInt(o), PrimitiveValue.BigInt(n)) =>
             DynamicPatch(Vector(DynamicPatchOp(toIdentityOptic, Operation.PrimitiveDelta(PrimitiveOp.BigIntDelta(n - o)))))
          case (PrimitiveValue.BigDecimal(o), PrimitiveValue.BigDecimal(n)) =>
             DynamicPatch(Vector(DynamicPatchOp(toIdentityOptic, Operation.PrimitiveDelta(PrimitiveOp.BigDecimalDelta(n - o)))))
          case (PrimitiveValue.Duration(o), PrimitiveValue.Duration(n)) =>
             DynamicPatch(Vector(DynamicPatchOp(toIdentityOptic, Operation.PrimitiveDelta(PrimitiveOp.DurationDelta(n.minus(o))))))
          case (PrimitiveValue.Instant(o), PrimitiveValue.Instant(n)) =>
             DynamicPatch(Vector(DynamicPatchOp(toIdentityOptic, Operation.PrimitiveDelta(PrimitiveOp.InstantDelta(java.time.Duration.between(o, n))))))
          case (PrimitiveValue.String(o), PrimitiveValue.String(n)) =>
             val edits = Diff.diff(o.toIndexedSeq, n.toIndexedSeq)
             val ops = editsToStringOps(edits)
             DynamicPatch(Vector(DynamicPatchOp(toIdentityOptic, Operation.PrimitiveDelta(PrimitiveOp.StringEdit(ops)))))
          case _ => 
             DynamicPatch(Vector(DynamicPatchOp(toIdentityOptic, Operation.Set(newValue))))
        }

      case (DynamicValue.Sequence(oldSeq), DynamicValue.Sequence(newSeq)) =>
        val editScript = Diff.diff(oldSeq, newSeq)
        val seqOps = editsToSeqOps(editScript)
        DynamicPatch(Vector(DynamicPatchOp(toIdentityOptic, Operation.SequenceEdit(seqOps))))

      case (DynamicValue.Map(oldEntries), DynamicValue.Map(newEntries)) =>
        val oldMap = oldEntries.toMap
        val newMap = newEntries.toMap
        val keys = (oldMap.keySet ++ newMap.keySet).toVector
        val ops = keys.flatMap { key =>
           (oldMap.get(key), newMap.get(key)) match {
             case (None, Some(v)) => Some(MapOp.Add(key, v))
             case (Some(_), None) => Some(MapOp.Remove(key))
             case (Some(ov), Some(nv)) if ov != nv =>
               Some(MapOp.Modify(key, Operation.Set(nv)))
             case _ => None
           }
        }
        DynamicPatch(Vector(DynamicPatchOp(toIdentityOptic, Operation.MapEdit(ops))))
        
      case (DynamicValue.Record(oldFields), DynamicValue.Record(newFields)) =>
         val oldMap = oldFields.toMap
         val newMap = newFields.toMap
         val keys = (oldMap.keySet ++ newMap.keySet).toVector
         val ops = keys.flatMap { key =>
            (oldMap.get(key), newMap.get(key)) match {
              case (Some(ov), Some(nv)) if ov != nv =>
                 diff(ov, nv).ops.map { op =>
                   val newOptic = DynamicOptic(DynamicOptic.Node.Field(key) +: op.optic.nodes)
                   op.copy(optic = newOptic)
                 }
              case (None, Some(_)) =>
                 Vector.empty
              case _ => Vector.empty
            }
         }
         DynamicPatch(ops)

      case (DynamicValue.Variant(oldCase, oldVal), DynamicValue.Variant(newCase, newVal)) if oldCase == newCase =>
         val ops = diff(oldVal, newVal).ops.map { op =>
           val newOptic = DynamicOptic(DynamicOptic.Node.Case(oldCase) +: op.optic.nodes)
           op.copy(optic = newOptic)
         }
         DynamicPatch(ops)
         
      case _ =>
        DynamicPatch(Vector(DynamicPatchOp(toIdentityOptic, Operation.Set(newValue))))
    }
  }
  
  private def editsToStringOps(edits: Vector[Diff.Edit[Char]]): Vector[StringOp] = {
    var ops = Vector.empty[StringOp]
    var idx = 0
    edits.foreach {
      case Diff.Keep(_) => idx += 1
      case Diff.Insert(c) =>
        ops = ops :+ StringOp.Insert(idx, c.toString)
        idx += 1
      case Diff.Delete(_) =>
        ops = ops :+ StringOp.Delete(idx, 1)
    }
    ops
  }

  private def editsToSeqOps(edits: Vector[Diff.Edit[DynamicValue]]): Vector[SeqOp] = {
    var ops = Vector.empty[SeqOp]
    var idx = 0
    edits.foreach {
      case Diff.Keep(_) => idx += 1
      case Diff.Insert(v) =>
        ops = ops :+ SeqOp.Insert(idx, Vector(v))
        idx += 1
      case Diff.Delete(_) =>
        ops = ops :+ SeqOp.Delete(idx, 1)
    }
    ops
  }
}
