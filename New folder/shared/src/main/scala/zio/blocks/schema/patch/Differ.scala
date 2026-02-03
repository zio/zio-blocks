package zio.blocks.schema.patch

import zio.blocks.chunk.Chunk
import zio.blocks.schema._

//Differ computes minimal patches between two DynamicValues.
private[schema] object Differ {

  // Compute the diff between two DynamicValues. Returns a DynamicPatch that
  // transforms oldValue into newValue.
  def diff(oldValue: DynamicValue, newValue: DynamicValue): DynamicPatch =
    if (oldValue == newValue) {
      DynamicPatch.empty
    } else {
      (oldValue, newValue) match {
        case (DynamicValue.Primitive(oldPrim), DynamicValue.Primitive(newPrim)) =>
          diffPrimitive(oldPrim, newPrim)

        case (DynamicValue.Record(oldFields), DynamicValue.Record(newFields)) =>
          diffRecord(oldFields, newFields)

        case (DynamicValue.Variant(oldCase, oldVal), DynamicValue.Variant(newCase, newVal)) =>
          diffVariant(oldCase, oldVal, newCase, newVal)

        case (DynamicValue.Sequence(oldElems), DynamicValue.Sequence(newElems)) =>
          diffSequence(oldElems, newElems)

        case (DynamicValue.Map(oldEntries), DynamicValue.Map(newEntries)) =>
          diffMap(oldEntries, newEntries)

        case _ =>
          // Type mismatch - use Set to replace entirely
          DynamicPatch.root(Patch.Operation.Set(newValue))
      }
    }

  // Diff two primitive values. Uses delta operations for numerics, temporal
  // types, and strings. Falls back to Set for other types.
  private def diffPrimitive(oldPrim: PrimitiveValue, newPrim: PrimitiveValue): DynamicPatch =
    (oldPrim, newPrim) match {
      // Numeric types - use delta operations
      case (PrimitiveValue.Int(oldVal), PrimitiveValue.Int(newVal)) =>
        val delta = newVal - oldVal
        DynamicPatch.root(Patch.Operation.PrimitiveDelta(Patch.PrimitiveOp.IntDelta(delta)))

      case (PrimitiveValue.Long(oldVal), PrimitiveValue.Long(newVal)) =>
        val delta = newVal - oldVal
        DynamicPatch.root(Patch.Operation.PrimitiveDelta(Patch.PrimitiveOp.LongDelta(delta)))

      case (PrimitiveValue.Double(oldVal), PrimitiveValue.Double(newVal)) =>
        val oldIsNaN = java.lang.Double.isNaN(oldVal)
        val newIsNaN = java.lang.Double.isNaN(newVal)
        if (oldIsNaN && newIsNaN) {
          DynamicPatch.empty
        } else if (oldIsNaN || newIsNaN) {
          DynamicPatch.root(Patch.Operation.Set(DynamicValue.Primitive(PrimitiveValue.Double(newVal))))
        } else {
          val delta = newVal - oldVal
          DynamicPatch.root(Patch.Operation.PrimitiveDelta(Patch.PrimitiveOp.DoubleDelta(delta)))
        }

      case (PrimitiveValue.Float(oldVal), PrimitiveValue.Float(newVal)) =>
        val oldIsNaN = java.lang.Float.isNaN(oldVal)
        val newIsNaN = java.lang.Float.isNaN(newVal)
        if (oldIsNaN && newIsNaN) {
          DynamicPatch.empty
        } else if (oldIsNaN || newIsNaN) {
          DynamicPatch.root(Patch.Operation.Set(DynamicValue.Primitive(PrimitiveValue.Float(newVal))))
        } else {
          val delta = newVal - oldVal
          DynamicPatch.root(Patch.Operation.PrimitiveDelta(Patch.PrimitiveOp.FloatDelta(delta)))
        }

      case (PrimitiveValue.Short(oldVal), PrimitiveValue.Short(newVal)) =>
        val delta = (newVal - oldVal).toShort
        DynamicPatch.root(Patch.Operation.PrimitiveDelta(Patch.PrimitiveOp.ShortDelta(delta)))

      case (PrimitiveValue.Byte(oldVal), PrimitiveValue.Byte(newVal)) =>
        val delta = (newVal - oldVal).toByte
        DynamicPatch.root(Patch.Operation.PrimitiveDelta(Patch.PrimitiveOp.ByteDelta(delta)))

      case (PrimitiveValue.BigInt(oldVal), PrimitiveValue.BigInt(newVal)) =>
        val delta = newVal - oldVal
        DynamicPatch.root(Patch.Operation.PrimitiveDelta(Patch.PrimitiveOp.BigIntDelta(delta)))

      case (PrimitiveValue.BigDecimal(oldVal), PrimitiveValue.BigDecimal(newVal)) =>
        val delta = newVal - oldVal
        DynamicPatch.root(Patch.Operation.PrimitiveDelta(Patch.PrimitiveOp.BigDecimalDelta(delta)))

      // String - use LCS to determine if edit is more efficient
      case (PrimitiveValue.String(oldStr), PrimitiveValue.String(newStr)) =>
        diffString(oldStr, newStr)

      // Temporal types - use period/duration deltas
      case (PrimitiveValue.Instant(oldVal), PrimitiveValue.Instant(newVal)) =>
        val duration = java.time.Duration.between(oldVal, newVal)
        DynamicPatch.root(Patch.Operation.PrimitiveDelta(Patch.PrimitiveOp.InstantDelta(duration)))

      case (PrimitiveValue.Duration(oldVal), PrimitiveValue.Duration(newVal)) =>
        val delta = newVal.minus(oldVal)
        DynamicPatch.root(Patch.Operation.PrimitiveDelta(Patch.PrimitiveOp.DurationDelta(delta)))

      case (PrimitiveValue.LocalDate(oldVal), PrimitiveValue.LocalDate(newVal)) =>
        val period = java.time.Period.between(oldVal, newVal)
        DynamicPatch.root(Patch.Operation.PrimitiveDelta(Patch.PrimitiveOp.LocalDateDelta(period)))

      case (PrimitiveValue.LocalDateTime(oldVal), PrimitiveValue.LocalDateTime(newVal)) =>
        val period      = java.time.Period.between(oldVal.toLocalDate, newVal.toLocalDate)
        val afterPeriod = oldVal.plus(period)
        val duration    = java.time.Duration.between(afterPeriod, newVal)
        DynamicPatch.root(Patch.Operation.PrimitiveDelta(Patch.PrimitiveOp.LocalDateTimeDelta(period, duration)))

      case (PrimitiveValue.Period(oldVal), PrimitiveValue.Period(newVal)) =>
        // Period doesn't have minus, so we compute the delta manually
        val delta = java.time.Period.of(
          newVal.getYears - oldVal.getYears,
          newVal.getMonths - oldVal.getMonths,
          newVal.getDays - oldVal.getDays
        )
        DynamicPatch.root(Patch.Operation.PrimitiveDelta(Patch.PrimitiveOp.PeriodDelta(delta)))

      // All other types - use Set
      case _ =>
        DynamicPatch.root(Patch.Operation.Set(DynamicValue.Primitive(newPrim)))
    }

  // Diff two strings using LCS algorithm. Uses StringEdit if the edit
  // operations are more compact than replacing the entire string.
  private def diffString(oldStr: String, newStr: String): DynamicPatch =
    if (oldStr == newStr) {
      DynamicPatch.empty
    } else {
      val edits = computeStringEdits(oldStr, newStr)

      // Calculate the "size" of the edit operations vs just setting the new string.
      // Deletes only store metadata, so they count as a single unit regardless of length.
      val editSize = edits.foldLeft(0) {
        case (acc, Patch.StringOp.Insert(_, text))    => acc + text.length
        case (acc, Patch.StringOp.Delete(_, _))       => acc + 1
        case (acc, Patch.StringOp.Append(text))       => acc + text.length
        case (acc, Patch.StringOp.Modify(_, _, text)) => acc + text.length
      }

      if (edits.nonEmpty && editSize < newStr.length) {
        DynamicPatch.root(Patch.Operation.PrimitiveDelta(Patch.PrimitiveOp.StringEdit(edits)))
      } else {
        DynamicPatch.root(Patch.Operation.Set(DynamicValue.Primitive(PrimitiveValue.String(newStr))))
      }
    }

  // String edit operations using LCS algorithm. Returns a sequence of
  // Insert/Delete operations with indices adjusted for previously applied
  // edits.
  private def computeStringEdits(oldStr: String, newStr: String): Vector[Patch.StringOp] = {
    if (oldStr == newStr) return Vector.empty
    if (oldStr.isEmpty) return Vector(Patch.StringOp.Insert(0, newStr))
    if (newStr.isEmpty) return Vector(Patch.StringOp.Delete(0, oldStr.length))

    val lcs   = LCS.stringLCS(oldStr, newStr)
    val edits = Vector.newBuilder[Patch.StringOp]

    var oldIdx = 0
    var newIdx = 0
    var lcsIdx = 0
    var cursor = 0 // current index in the string after applying previous edits

    while (lcsIdx < lcs.length) {
      val targetChar = lcs.charAt(lcsIdx)

      // Delete any characters in the old string that do not appear before the next LCS character.
      val deleteStart = oldIdx
      while (oldIdx < oldStr.length && oldStr.charAt(oldIdx) != targetChar) {
        oldIdx += 1
      }
      val deleteLen = oldIdx - deleteStart
      if (deleteLen > 0) edits.addOne(Patch.StringOp.Delete(cursor, deleteLen))

      // Insert characters from the new string that appear before the next LCS character.
      val insertStart = newIdx
      while (newIdx < newStr.length && newStr.charAt(newIdx) != targetChar) {
        newIdx += 1
      }
      if (newIdx > insertStart) {
        val text = newStr.substring(insertStart, newIdx)
        edits.addOne(Patch.StringOp.Insert(cursor, text))
        cursor += text.length
      }

      // Consume the matching LCS character.
      oldIdx += 1
      newIdx += 1
      cursor += 1
      lcsIdx += 1
    }

    // Delete any trailing characters left in the old string.
    if (oldIdx < oldStr.length) {
      val deleteLen = oldStr.length - oldIdx
      edits.addOne(Patch.StringOp.Delete(cursor, deleteLen))
    }

    // Insert any trailing characters from the new string.
    if (newIdx < newStr.length) {
      val text = newStr.substring(newIdx)
      if (text.nonEmpty) edits.addOne(Patch.StringOp.Insert(cursor, text))
    }

    edits.result()
  }

  // Diff two records by comparing fields. Only includes patches for fields that
  // have changed.
  private def diffRecord(
    oldFields: Chunk[(String, DynamicValue)],
    newFields: Chunk[(String, DynamicValue)]
  ): DynamicPatch = {
    val oldMap = oldFields.toMap

    val ops = Vector.newBuilder[Patch.DynamicPatchOp]

    // Check each field in the new record
    for ((fieldName, newValue) <- newFields) {
      oldMap.get(fieldName) match {
        case Some(oldValue) if oldValue != newValue =>
          // Field exists in both but has different value - recursively diff
          val fieldPatch = diff(oldValue, newValue)
          if (!fieldPatch.isEmpty) {
            // Prepend the field path to each operation
            for (op <- fieldPatch.ops) {
              ops.addOne(
                Patch.DynamicPatchOp(
                  new DynamicOptic(DynamicOptic.Node.Field(fieldName) +: op.path.nodes),
                  op.operation
                )
              )
            }
          }
        case None =>
          // Field only exists in new record - set it
          ops.addOne(
            Patch.DynamicPatchOp(
              new DynamicOptic(Vector(DynamicOptic.Node.Field(fieldName))),
              Patch.Operation.Set(newValue)
            )
          )
        case _ =>
        // Field unchanged - skip
      }
    }

    // Fields that exist in old but not in new are ignored
    // (Records are immutable, we can't delete fields)

    DynamicPatch(ops.result())
  }

  // Diff two variants. Case changes always replace the whole variant, but
  // identical cases reuse inner diffs.
  private def diffVariant(
    oldCase: String,
    oldValue: DynamicValue,
    newCase: String,
    newValue: DynamicValue
  ): DynamicPatch =
    if (oldCase != newCase) {
      DynamicPatch.root(Patch.Operation.Set(DynamicValue.Variant(newCase, newValue)))
    } else if (oldValue == newValue) {
      DynamicPatch.empty
    } else {
      val innerPatch = diff(oldValue, newValue)
      if (innerPatch.isEmpty) {
        DynamicPatch.empty
      } else {
        val ops = innerPatch.ops.map { op =>
          Patch.DynamicPatchOp(new DynamicOptic(DynamicOptic.Node.Case(oldCase) +: op.path.nodes), op.operation)
        }
        DynamicPatch(ops)
      }
    }

  // Diff two sequences using an LCS-based alignment. Produces
  // Patch.SeqOp.Insert/Delete/Append operations that describe how to transform the
  // old elements into the new ones without replacing the entire collection.
  private def diffSequence(
    oldElems: Chunk[DynamicValue],
    newElems: Chunk[DynamicValue]
  ): DynamicPatch =
    if (oldElems == newElems) {
      DynamicPatch.empty
    } else if (oldElems.isEmpty) {
      DynamicPatch.root(Patch.Operation.SequenceEdit(Vector(Patch.SeqOp.Append(newElems))))
    } else if (newElems.isEmpty) {
      DynamicPatch.root(Patch.Operation.SequenceEdit(Vector(Patch.SeqOp.Delete(0, oldElems.length))))
    } else {
      val seqOps = computeSequenceOps(oldElems, newElems)
      if (seqOps.isEmpty) DynamicPatch.empty
      else DynamicPatch.root(Patch.Operation.SequenceEdit(seqOps))
    }

  // Convert the difference between two sequences into SeqOps using LCS
  // alignment.
  private def computeSequenceOps(
    oldElems: Chunk[DynamicValue],
    newElems: Chunk[DynamicValue]
  ): Vector[Patch.SeqOp] = {
    val ops       = Vector.newBuilder[Patch.SeqOp]
    val matches   = LCS.indicesLCS(oldElems, newElems)(_ == _)
    var oldIdx    = 0
    var newIdx    = 0
    var cursor    = 0
    var curLength = oldElems.length

    def emitDelete(count: Int): Unit =
      if (count > 0) {
        ops.addOne(Patch.SeqOp.Delete(cursor, count))
        curLength -= count
      }

    def emitInsert(values: Chunk[DynamicValue]): Unit =
      if (values.nonEmpty) {
        val insertionIndex = cursor
        if (insertionIndex == curLength) ops.addOne(Patch.SeqOp.Append(values))
        else ops.addOne(Patch.SeqOp.Insert(insertionIndex, values))
        cursor += values.length
        curLength += values.length
      }

    matches.foreach { case (matchOld, matchNew) =>
      emitDelete(matchOld - oldIdx)
      emitInsert(newElems.slice(newIdx, matchNew))

      oldIdx = matchOld + 1
      newIdx = matchNew + 1
      cursor += 1
    }

    emitDelete(oldElems.length - oldIdx)
    emitInsert(newElems.slice(newIdx, newElems.length))

    ops.result()
  }

  // Diff two maps by comparing keys and values. Produces Add, Remove, and
  // Modify operations.
  private def diffMap(
    oldEntries: Chunk[(DynamicValue, DynamicValue)],
    newEntries: Chunk[(DynamicValue, DynamicValue)]
  ): DynamicPatch = {
    val oldMap = oldEntries.toMap
    val newMap = newEntries.toMap

    val ops = Vector.newBuilder[Patch.MapOp]

    // Find added and modified keys
    for ((key, newValue) <- newEntries) {
      oldMap.get(key) match {
        case Some(oldValue) if oldValue != newValue =>
          // Key exists in both but value changed - recursively diff
          val valuePatch = diff(oldValue, newValue)
          if (!valuePatch.isEmpty) {
            ops.addOne(Patch.MapOp.Modify(key, valuePatch))
          }
        case None =>
          // Key only in new map - add it
          ops.addOne(Patch.MapOp.Add(key, newValue))
        case _ =>
        // Key unchanged - skip
      }
    }

    // Find removed keys
    for ((key, _) <- oldEntries) {
      if (!newMap.contains(key)) {
        ops.addOne(Patch.MapOp.Remove(key))
      }
    }

    DynamicPatch.root(Patch.Operation.MapEdit(ops.result()))
  }
}
