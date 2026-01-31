package zio.blocks.schema.json

import zio.blocks.chunk.Chunk
import zio.blocks.schema.patch.LCS

/**
 * Computes differences between [[Json]] values, producing [[JsonPatch]]
 * instances that can transform one JSON value into another.
 */
object JsonDiffer {

  /**
   * Compute the diff between two Json values.
   *
   * @param source
   *   The original Json value
   * @param target
   *   The desired Json value
   * @return
   *   A JsonPatch that transforms source into target
   */
  def diff(source: Json, target: Json): JsonPatch =
    if (source == target) {
      JsonPatch.empty
    } else {
      (source, target) match {
        case (Json.Object(oldFields), Json.Object(newFields)) =>
          diffObject(oldFields, newFields)

        case (Json.Array(oldElems), Json.Array(newElems)) =>
          diffArray(oldElems, newElems)

        case (oldStr: Json.String, newStr: Json.String) =>
          diffString(oldStr, newStr)

        case (oldNum: Json.Number, newNum: Json.Number) =>
          diffNumber(oldNum, newNum)

        case (Json.Boolean(oldVal), Json.Boolean(newVal)) =>
          if (oldVal == newVal) JsonPatch.empty else JsonPatch.root(JsonPatch.Op.Set(target))

        case (Json.Null, Json.Null) =>
          JsonPatch.empty

        case _ =>
          // Type mismatch - replace entirely
          JsonPatch.root(JsonPatch.Op.Set(target))
      }
    }

  // Diff Helpers

  /**
   * Diff two JSON numbers by computing their delta. Uses NumberDelta to
   * represent the change.
   */
  private def diffNumber(oldNum: Json.Number, newNum: Json.Number): JsonPatch = {
    val oldVal = BigDecimal(oldNum.value)
    val newVal = BigDecimal(newNum.value)
    val delta  = newVal - oldVal
    JsonPatch.root(JsonPatch.Op.PrimitiveDelta(JsonPatch.PrimitiveOp.NumberDelta(delta)))
  }

  /**
   * Diff two JSON strings using LCS algorithm. Uses StringEdit when more
   * compact than Set.
   */
  private def diffString(oldStr: Json.String, newStr: Json.String): JsonPatch =
    if (oldStr.value == newStr.value) {
      JsonPatch.empty
    } else {
      val edits = computeStringEdits(oldStr.value, newStr.value)

      // Calculate the "size" of the edit operations vs just setting the new string.
      // Deletes only store metadata, so they count as a single unit regardless of length.
      val editSize = edits.foldLeft(0) {
        case (acc, JsonPatch.StringOp.Insert(_, text))    => acc + text.length
        case (acc, JsonPatch.StringOp.Delete(_, _))       => acc + 1
        case (acc, JsonPatch.StringOp.Append(text))       => acc + text.length
        case (acc, JsonPatch.StringOp.Modify(_, _, text)) => acc + text.length
      }

      if (edits.nonEmpty && editSize < newStr.value.length) {
        JsonPatch.root(JsonPatch.Op.PrimitiveDelta(JsonPatch.PrimitiveOp.StringEdit(edits)))
      } else {
        JsonPatch.root(JsonPatch.Op.Set(newStr))
      }
    }

  /**
   * Compute string edit operations using LCS algorithm. Returns a sequence of
   * Insert/Delete operations with indices adjusted for previously applied
   * edits.
   */
  private def computeStringEdits(oldStr: String, newStr: String): Vector[JsonPatch.StringOp] = {
    if (oldStr == newStr) return Vector.empty
    if (oldStr.isEmpty) return Vector(JsonPatch.StringOp.Insert(0, newStr))
    if (newStr.isEmpty) return Vector(JsonPatch.StringOp.Delete(0, oldStr.length))

    val lcs   = LCS.stringLCS(oldStr, newStr)
    val edits = Vector.newBuilder[JsonPatch.StringOp]

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
      if (deleteLen > 0) edits.addOne(JsonPatch.StringOp.Delete(cursor, deleteLen))

      // Insert characters from the new string that appear before the next LCS character.
      val insertStart = newIdx
      while (newIdx < newStr.length && newStr.charAt(newIdx) != targetChar) {
        newIdx += 1
      }
      if (newIdx > insertStart) {
        val text = newStr.substring(insertStart, newIdx)
        edits.addOne(JsonPatch.StringOp.Insert(cursor, text))
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
      edits.addOne(JsonPatch.StringOp.Delete(cursor, deleteLen))
    }

    // Insert any trailing characters from the new string.
    if (newIdx < newStr.length) {
      val text = newStr.substring(newIdx)
      if (text.nonEmpty) edits.addOne(JsonPatch.StringOp.Insert(cursor, text))
    }

    edits.result()
  }

  /**
   * Diff two JSON arrays using LCS-based alignment. Produces
   * Insert/Delete/Append operations that describe how to transform the old
   * elements into the new ones.
   */
  private def diffArray(oldElems: Chunk[Json], newElems: Chunk[Json]): JsonPatch =
    if (oldElems == newElems) {
      JsonPatch.empty
    } else if (oldElems.isEmpty) {
      JsonPatch.root(JsonPatch.Op.ArrayEdit(Vector(JsonPatch.ArrayOp.Append(newElems))))
    } else if (newElems.isEmpty) {
      JsonPatch.root(JsonPatch.Op.ArrayEdit(Vector(JsonPatch.ArrayOp.Delete(0, oldElems.length))))
    } else {
      val arrayOps = computeArrayOps(oldElems, newElems)
      if (arrayOps.isEmpty) JsonPatch.empty
      else JsonPatch.root(JsonPatch.Op.ArrayEdit(arrayOps))
    }

  /**
   * Convert the difference between two arrays into ArrayOps using LCS
   * alignment.
   */
  private def computeArrayOps(oldElems: Chunk[Json], newElems: Chunk[Json]): Vector[JsonPatch.ArrayOp] = {
    val ops       = Vector.newBuilder[JsonPatch.ArrayOp]
    val matches   = LCS.indicesLCS(oldElems, newElems)(_ == _)
    var oldIdx    = 0
    var newIdx    = 0
    var cursor    = 0
    var curLength = oldElems.length

    def emitDelete(count: Int): Unit =
      if (count > 0) {
        ops.addOne(JsonPatch.ArrayOp.Delete(cursor, count))
        curLength -= count
      }

    def emitInsert(values: Chunk[Json]): Unit =
      if (values.nonEmpty) {
        val insertionIndex = cursor
        if (insertionIndex == curLength) ops.addOne(JsonPatch.ArrayOp.Append(values))
        else ops.addOne(JsonPatch.ArrayOp.Insert(insertionIndex, values))
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

  /**
   * Diff two JSON objects by comparing fields. Produces Add, Remove, and Modify
   * operations.
   */
  private def diffObject(
    oldFields: Chunk[(String, Json)],
    newFields: Chunk[(String, Json)]
  ): JsonPatch = {
    val oldMap = oldFields.toMap
    val newMap = newFields.toMap

    val ops = Vector.newBuilder[JsonPatch.ObjectOp]

    // Find modified and added fields
    for ((fieldName, newValue) <- newFields) {
      oldMap.get(fieldName) match {
        case Some(oldValue) if oldValue != newValue =>
          // Field exists in both but has different value - recursively diff
          val fieldPatch = diff(oldValue, newValue)
          if (!fieldPatch.isEmpty) {
            ops.addOne(JsonPatch.ObjectOp.Modify(fieldName, fieldPatch))
          }
        case None =>
          // Field only in new object - add it
          ops.addOne(JsonPatch.ObjectOp.Add(fieldName, newValue))
        case _ =>
        // Field unchanged - skip
      }
    }

    // Find removed fields
    for ((fieldName, _) <- oldFields) {
      if (!newMap.contains(fieldName)) {
        ops.addOne(JsonPatch.ObjectOp.Remove(fieldName))
      }
    }

    val result = ops.result()
    if (result.isEmpty) JsonPatch.empty
    else JsonPatch.root(JsonPatch.Op.ObjectEdit(result))
  }
}
