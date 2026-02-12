package zio.blocks.schema.patch

import zio.blocks.chunk.{Chunk, ChunkBuilder}
import zio.blocks.schema._

//Differ computes minimal patches between two DynamicValues.
private[schema] object Differ {

  // Compute the diff between two DynamicValues. Returns a DynamicPatch that
  // transforms oldValue into newValue.
  def diff(oldValue: DynamicValue, newValue: DynamicValue): DynamicPatch =
    if (oldValue == newValue) DynamicPatch.empty
    else if (oldValue.typeIndex == newValue.typeIndex) {
      oldValue match {
        case o: DynamicValue.Primitive => diffPrimitive(o.value, newValue.asInstanceOf[DynamicValue.Primitive].value)
        case o: DynamicValue.Record    => diffRecord(o.fields, newValue.asInstanceOf[DynamicValue.Record].fields)
        case o: DynamicValue.Variant   =>
          val n = newValue.asInstanceOf[DynamicValue.Variant]
          diffVariant(o.caseNameValue, o.value, n.caseNameValue, n.value)
        case o: DynamicValue.Sequence => diffSequence(o.elements, newValue.asInstanceOf[DynamicValue.Sequence].elements)
        case o: DynamicValue.Map      => diffMap(o.entries, newValue.asInstanceOf[DynamicValue.Map].entries)
        case _                        => DynamicPatch.empty
      }
    } else DynamicPatch.root(new Patch.Operation.Set(newValue)) // Type mismatch - use Set to replace entirely

  // Diff two primitive values. Uses delta operations for numerics, temporal
  // types, and strings. Falls back to Set for other types.
  private def diffPrimitive(oldPrim: PrimitiveValue, newPrim: PrimitiveValue): DynamicPatch =
    if (oldPrim.typeIndex == newPrim.typeIndex) {
      oldPrim match {
        // Numeric types - use delta operations
        case o: PrimitiveValue.Int =>
          val delta = newPrim.asInstanceOf[PrimitiveValue.Int].value - o.value
          DynamicPatch.root(new Patch.Operation.PrimitiveDelta(new Patch.PrimitiveOp.IntDelta(delta)))
        case o: PrimitiveValue.Long =>
          val delta = newPrim.asInstanceOf[PrimitiveValue.Long].value - o.value
          DynamicPatch.root(new Patch.Operation.PrimitiveDelta(new Patch.PrimitiveOp.LongDelta(delta)))
        case o: PrimitiveValue.Double =>
          val oldVal   = o.value
          val newVal   = newPrim.asInstanceOf[PrimitiveValue.Double].value
          val oldIsNaN = java.lang.Double.isNaN(oldVal)
          val newIsNaN = java.lang.Double.isNaN(newVal)
          if (oldIsNaN && newIsNaN) DynamicPatch.empty
          else if (oldIsNaN || newIsNaN) {
            DynamicPatch.root(new Patch.Operation.Set(new DynamicValue.Primitive(new PrimitiveValue.Double(newVal))))
          } else {
            val delta = newVal - oldVal
            DynamicPatch.root(new Patch.Operation.PrimitiveDelta(new Patch.PrimitiveOp.DoubleDelta(delta)))
          }
        case o: PrimitiveValue.Float =>
          val oldVal   = o.value
          val newVal   = newPrim.asInstanceOf[PrimitiveValue.Float].value
          val oldIsNaN = java.lang.Float.isNaN(oldVal)
          val newIsNaN = java.lang.Float.isNaN(newVal)
          if (oldIsNaN && newIsNaN) DynamicPatch.empty
          else if (oldIsNaN || newIsNaN) {
            DynamicPatch.root(new Patch.Operation.Set(new DynamicValue.Primitive(new PrimitiveValue.Float(newVal))))
          } else {
            val delta = newVal - oldVal
            DynamicPatch.root(new Patch.Operation.PrimitiveDelta(new Patch.PrimitiveOp.FloatDelta(delta)))
          }
        case o: PrimitiveValue.Short =>
          val delta = (newPrim.asInstanceOf[PrimitiveValue.Short].value - o.value).toShort
          DynamicPatch.root(new Patch.Operation.PrimitiveDelta(new Patch.PrimitiveOp.ShortDelta(delta)))
        case o: PrimitiveValue.Byte =>
          val delta = (newPrim.asInstanceOf[PrimitiveValue.Byte].value - o.value).toByte
          DynamicPatch.root(new Patch.Operation.PrimitiveDelta(new Patch.PrimitiveOp.ByteDelta(delta)))
        case o: PrimitiveValue.BigInt =>
          val delta = newPrim.asInstanceOf[PrimitiveValue.BigInt].value - o.value
          DynamicPatch.root(new Patch.Operation.PrimitiveDelta(new Patch.PrimitiveOp.BigIntDelta(delta)))
        case o: PrimitiveValue.BigDecimal =>
          val delta = newPrim.asInstanceOf[PrimitiveValue.BigDecimal].value - o.value
          DynamicPatch.root(new Patch.Operation.PrimitiveDelta(new Patch.PrimitiveOp.BigDecimalDelta(delta)))
        // String - use LCS to determine if edit is more efficient
        case o: PrimitiveValue.String =>
          diffString(o.value, newPrim.asInstanceOf[PrimitiveValue.String].value)
        // Temporal types - use period/duration deltas
        case o: PrimitiveValue.Instant =>
          val duration = java.time.Duration.between(o.value, newPrim.asInstanceOf[PrimitiveValue.Instant].value)
          DynamicPatch.root(new Patch.Operation.PrimitiveDelta(new Patch.PrimitiveOp.InstantDelta(duration)))
        case o: PrimitiveValue.Duration =>
          val delta = newPrim.asInstanceOf[PrimitiveValue.Duration].value.minus(o.value)
          DynamicPatch.root(new Patch.Operation.PrimitiveDelta(new Patch.PrimitiveOp.DurationDelta(delta)))
        case o: PrimitiveValue.LocalDate =>
          val period = java.time.Period.between(o.value, newPrim.asInstanceOf[PrimitiveValue.LocalDate].value)
          DynamicPatch.root(new Patch.Operation.PrimitiveDelta(new Patch.PrimitiveOp.LocalDateDelta(period)))
        case o: PrimitiveValue.LocalDateTime =>
          val oldVal      = o.value
          val newVal      = newPrim.asInstanceOf[PrimitiveValue.LocalDateTime].value
          val period      = java.time.Period.between(oldVal.toLocalDate, newVal.toLocalDate)
          val afterPeriod = oldVal.plus(period)
          val duration    = java.time.Duration.between(afterPeriod, newVal)
          DynamicPatch.root(
            new Patch.Operation.PrimitiveDelta(new Patch.PrimitiveOp.LocalDateTimeDelta(period, duration))
          )
        case o: PrimitiveValue.Period =>
          val oldVal = o.value
          val newVal = newPrim.asInstanceOf[PrimitiveValue.Period].value
          // Period doesn't have minus, so we compute the delta manually
          val delta = java.time.Period.of(
            newVal.getYears - oldVal.getYears,
            newVal.getMonths - oldVal.getMonths,
            newVal.getDays - oldVal.getDays
          )
          DynamicPatch.root(new Patch.Operation.PrimitiveDelta(new Patch.PrimitiveOp.PeriodDelta(delta)))
        // All other types - use Set
        case _ => DynamicPatch.root(new Patch.Operation.Set(new DynamicValue.Primitive(newPrim)))
      }
    } else DynamicPatch.root(new Patch.Operation.Set(new DynamicValue.Primitive(newPrim)))

  // Diff two strings using LCS algorithm. Uses StringEdit if the edit
  // operations are more compact than replacing the entire string.
  private[this] def diffString(oldStr: String, newStr: String): DynamicPatch =
    if (oldStr == newStr) DynamicPatch.empty
    else {
      val edits = computeStringEdits(oldStr, newStr)
      // Calculate the "size" of the edit operations vs just setting the new string.
      // Deletes only store metadata, so they count as a single unit regardless of length.
      val editSize = edits.foldLeft(0) { (acc, e) =>
        acc + (e match {
          case i: Patch.StringOp.Insert => i.text.length
          case a: Patch.StringOp.Append => a.text.length
          case m: Patch.StringOp.Modify => m.text.length
          case _                        => 1
        })
      }
      DynamicPatch.root(if (edits.nonEmpty && editSize < newStr.length) {
        new Patch.Operation.PrimitiveDelta(new Patch.PrimitiveOp.StringEdit(edits))
      } else {
        new Patch.Operation.Set(new DynamicValue.Primitive(new PrimitiveValue.String(newStr)))
      })
    }

  // String edit operations using LCS algorithm. Returns a sequence of
  // Insert/Delete operations with indices adjusted for previously applied
  // edits.
  private[this] def computeStringEdits(oldStr: String, newStr: String): Chunk[Patch.StringOp] = {
    if (oldStr == newStr) return Chunk.empty
    if (oldStr.isEmpty) return Chunk.single(new Patch.StringOp.Insert(0, newStr))
    if (newStr.isEmpty) return Chunk.single(new Patch.StringOp.Delete(0, oldStr.length))
    val lcs    = LCS.stringLCS(oldStr, newStr)
    val edits  = ChunkBuilder.make[Patch.StringOp]()
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
      if (deleteLen > 0) edits.addOne(new Patch.StringOp.Delete(cursor, deleteLen))
      // Insert characters from the new string that appear before the next LCS character.
      val insertStart = newIdx
      while (newIdx < newStr.length && newStr.charAt(newIdx) != targetChar) {
        newIdx += 1
      }
      if (newIdx > insertStart) {
        val text = newStr.substring(insertStart, newIdx)
        edits.addOne(new Patch.StringOp.Insert(cursor, text))
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
      edits.addOne(new Patch.StringOp.Delete(cursor, deleteLen))
    }
    // Insert any trailing characters from the new string.
    if (newIdx < newStr.length) {
      val text = newStr.substring(newIdx)
      if (text.nonEmpty) edits.addOne(new Patch.StringOp.Insert(cursor, text))
    }
    edits.result()
  }

  // Diff two records by comparing fields. Only includes patches for fields that
  // have changed.
  private[this] def diffRecord(
    oldFields: Chunk[(String, DynamicValue)],
    newFields: Chunk[(String, DynamicValue)]
  ): DynamicPatch = {
    val oldMap = oldFields.toMap
    val ops    = ChunkBuilder.make[Patch.DynamicPatchOp]()
    // Check each field in the new record
    for ((fieldName, newValue) <- newFields) {
      oldMap.get(fieldName) match {
        case Some(oldValue) =>
          if (oldValue != newValue) {
            // Field exists in both but has different value - recursively diff
            val fieldPatch = diff(oldValue, newValue)
            if (!fieldPatch.isEmpty) {
              // Prepend the field path to each operation
              for (op <- fieldPatch.ops) {
                ops.addOne(
                  new Patch.DynamicPatchOp(
                    new DynamicOptic(new DynamicOptic.Node.Field(fieldName) +: op.path.nodes),
                    op.operation
                  )
                )
              }
            }
          }
        case _ =>
          // Field only exists in new record - set it
          ops.addOne(
            new Patch.DynamicPatchOp(
              new DynamicOptic(Chunk.single(new DynamicOptic.Node.Field(fieldName))),
              new Patch.Operation.Set(newValue)
            )
          )
      }
    }
    // Fields that exist in old but not in new are ignored
    // (Records are immutable, we can't delete fields)
    new DynamicPatch(ops.result())
  }

  // Diff two variants. Case changes always replace the whole variant, but
  // identical cases reuse inner diffs.
  private[this] def diffVariant(
    oldCase: String,
    oldValue: DynamicValue,
    newCase: String,
    newValue: DynamicValue
  ): DynamicPatch =
    if (oldCase != newCase) DynamicPatch.root(new Patch.Operation.Set(new DynamicValue.Variant(newCase, newValue)))
    else if (oldValue == newValue) DynamicPatch.empty
    else {
      val innerPatch = diff(oldValue, newValue)
      if (innerPatch.isEmpty) DynamicPatch.empty
      else {
        new DynamicPatch(innerPatch.ops.map { op =>
          new Patch.DynamicPatchOp(new DynamicOptic(new DynamicOptic.Node.Case(oldCase) +: op.path.nodes), op.operation)
        })
      }
    }

  // Diff two sequences using an LCS-based alignment. Produces
  // Patch.SeqOp.Insert/Delete/Append operations that describe how to transform the
  // old elements into the new ones without replacing the entire collection.
  private[this] def diffSequence(oldElems: Chunk[DynamicValue], newElems: Chunk[DynamicValue]): DynamicPatch =
    if (oldElems == newElems) DynamicPatch.empty
    else if (oldElems.isEmpty) {
      DynamicPatch.root(new Patch.Operation.SequenceEdit(Chunk.single(new Patch.SeqOp.Append(newElems))))
    } else if (newElems.isEmpty) {
      DynamicPatch.root(new Patch.Operation.SequenceEdit(Chunk.single(new Patch.SeqOp.Delete(0, oldElems.length))))
    } else {
      val seqOps = computeSequenceOps(oldElems, newElems)
      if (seqOps.isEmpty) DynamicPatch.empty
      else DynamicPatch.root(new Patch.Operation.SequenceEdit(seqOps))
    }

  // Convert the difference between two sequences into SeqOps using LCS
  // alignment.
  private[this] def computeSequenceOps(
    oldElems: Chunk[DynamicValue],
    newElems: Chunk[DynamicValue]
  ): Chunk[Patch.SeqOp] = {
    val ops       = ChunkBuilder.make[Patch.SeqOp]()
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
  private[this] def diffMap(
    oldEntries: Chunk[(DynamicValue, DynamicValue)],
    newEntries: Chunk[(DynamicValue, DynamicValue)]
  ): DynamicPatch = {
    val oldMap = oldEntries.toMap
    val newMap = newEntries.toMap
    val ops    = ChunkBuilder.make[Patch.MapOp]()
    // Find added and modified keys
    for ((key, newValue) <- newEntries) {
      oldMap.get(key) match {
        case Some(oldValue) =>
          if (oldValue != newValue) {
            // Key exists in both but value changed - recursively diff
            val valuePatch = diff(oldValue, newValue)
            if (!valuePatch.isEmpty) {
              ops.addOne(new Patch.MapOp.Modify(key, valuePatch))
            }
          }
        case _ => ops.addOne(new Patch.MapOp.Add(key, newValue))
      }
    }
    // Find removed keys
    for ((key, _) <- oldEntries) {
      if (!newMap.contains(key)) ops.addOne(new Patch.MapOp.Remove(key))
    }
    DynamicPatch.root(new Patch.Operation.MapEdit(ops.result()))
  }
}
