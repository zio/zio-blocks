package zio.blocks.schema

import DynamicPatch._

/**
 * Differ provides smart diffing algorithms for DynamicValue. Uses heuristics to
 * choose between delta/edit vs set operations.
 */
object Differ {

  /**
   * Compute a DynamicPatch from oldValue to newValue. Uses smart heuristics to
   * produce minimal patches.
   */
  def diff(oldValue: DynamicValue, newValue: DynamicValue): DynamicPatch =
    if (oldValue == newValue) {
      DynamicPatch.empty
    } else {
      (oldValue, newValue) match {
        case (DynamicValue.Primitive(oldPrim), DynamicValue.Primitive(newPrim)) =>
          diffPrimitive(oldPrim, newPrim)

        case (DynamicValue.Record(oldFields), DynamicValue.Record(newFields)) =>
          diffRecord(oldFields, newFields)

        case (DynamicValue.Sequence(oldElems), DynamicValue.Sequence(newElems)) =>
          diffSequence(oldElems, newElems)

        case (DynamicValue.Map(oldEntries), DynamicValue.Map(newEntries)) =>
          diffMap(oldEntries, newEntries)

        case (DynamicValue.Variant(oldCase, oldVal), DynamicValue.Variant(newCase, newVal)) =>
          if (oldCase == newCase) {
            // Same case, diff the inner value
            val innerPatch = diff(oldVal, newVal)
            if (innerPatch.ops.isEmpty) DynamicPatch.empty
            else DynamicPatch.set(newValue) // Simplified: just set the variant
          } else {
            // Different cases, must set
            DynamicPatch.set(newValue)
          }

        case _ =>
          // Types don't match, must set
          DynamicPatch.set(newValue)
      }
    }

  private def diffPrimitive(oldPrim: PrimitiveValue, newPrim: PrimitiveValue): DynamicPatch =
    (oldPrim, newPrim) match {
      case (PrimitiveValue.Int(oldV), PrimitiveValue.Int(newV)) =>
        val delta = newV - oldV
        if (delta == 0) DynamicPatch.empty
        else DynamicPatch(Operation.PrimitiveDelta(PrimitiveOp.IntDelta(delta)))

      case (PrimitiveValue.Long(oldV), PrimitiveValue.Long(newV)) =>
        val delta = newV - oldV
        if (delta == 0) DynamicPatch.empty
        else DynamicPatch(Operation.PrimitiveDelta(PrimitiveOp.LongDelta(delta)))

      case (PrimitiveValue.Double(oldV), PrimitiveValue.Double(newV)) =>
        val delta = newV - oldV
        if (delta == 0.0) DynamicPatch.empty
        else DynamicPatch(Operation.PrimitiveDelta(PrimitiveOp.DoubleDelta(delta)))

      case (PrimitiveValue.String(oldV), PrimitiveValue.String(newV)) =>
        val ops = LCS.stringDiff(oldV, newV)
        if (ops.isEmpty) DynamicPatch.empty
        else if (
          ops.map {
            case StringOp.Insert(_, t) => t.length
            case StringOp.Delete(_, l) => l
          }.sum >= newV.length
        ) {
          // Edit is longer than just setting, use set
          DynamicPatch.set(DynamicValue.Primitive(newPrim))
        } else {
          DynamicPatch(Operation.PrimitiveDelta(PrimitiveOp.StringEditOp(ops)))
        }

      case _ =>
        // Other primitives, just set
        DynamicPatch.set(DynamicValue.Primitive(newPrim))
    }

  private def diffRecord(
    oldFields: Vector[(String, DynamicValue)],
    newFields: Vector[(String, DynamicValue)]
  ): DynamicPatch = {
    val oldMap = oldFields.toMap
    val newMap = newFields.toMap

    val fieldOps = newFields.flatMap { case (name, newVal) =>
      oldMap.get(name) match {
        case Some(oldVal) if oldVal != newVal =>
          val innerPatch = diff(oldVal, newVal)
          if (innerPatch.ops.isEmpty) None
          else Some((name, Operation.Set(newVal))) // Simplified: set the field value
        case None =>
          // New field
          Some((name, Operation.Set(newVal)))
        case _ =>
          None
      }
    }

    if (fieldOps.isEmpty) DynamicPatch.empty
    else DynamicPatch(Operation.RecordPatch(fieldOps))
  }

  private def diffSequence(
    oldElems: Vector[DynamicValue],
    newElems: Vector[DynamicValue]
  ): DynamicPatch = {
    val ops = LCS.sequenceDiff(oldElems, newElems)
    if (ops.isEmpty) DynamicPatch.empty
    else DynamicPatch(Operation.SequenceEdit(ops))
  }

  private def diffMap(
    oldEntries: Vector[(DynamicValue, DynamicValue)],
    newEntries: Vector[(DynamicValue, DynamicValue)]
  ): DynamicPatch = {
    val oldMap = oldEntries.toMap
    val newMap = newEntries.toMap

    val ops = Vector.newBuilder[MapOp]

    // Find additions and modifications
    newEntries.foreach { case (key, newVal) =>
      oldMap.get(key) match {
        case Some(oldVal) if oldVal != newVal =>
          ops += MapOp.Modify(key, Operation.Set(newVal))
        case None =>
          ops += MapOp.Add(key, newVal)
        case _ =>
          ()
      }
    }

    // Find removals
    oldEntries.foreach { case (key, _) =>
      if (!newMap.contains(key)) {
        ops += MapOp.Remove(key)
      }
    }

    val result = ops.result()
    if (result.isEmpty) DynamicPatch.empty
    else DynamicPatch(Operation.MapEdit(result))
  }
}
