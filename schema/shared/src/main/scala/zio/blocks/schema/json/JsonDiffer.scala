package zio.blocks.schema.json

import zio.blocks.chunk.Chunk

/**
 * Computes minimal patches between two [[Json]] values.
 *
 * This object mirrors [[zio.blocks.schema.patch.Differ]] but is specialized for
 * JSON's simpler data model.
 */
private[json] object JsonDiffer {

  /**
   * Compute the diff between two JSON values.
   *
   * @return
   *   A [[JsonPatch]] that transforms `oldJson` into `newJson`.
   */
  def diff(oldJson: Json, newJson: Json): JsonPatch =
    if (oldJson == newJson) {
      JsonPatch.empty
    } else {
      (oldJson, newJson) match {
        case (oldNum: Json.Number, newNum: Json.Number) =>
          diffNumber(oldNum, newNum)

        case (oldStr: Json.String, newStr: Json.String) =>
          diffString(oldStr.value, newStr.value)

        case (oldArr: Json.Array, newArr: Json.Array) =>
          diffArray(oldArr.value, newArr.value)

        case (oldObj: Json.Object, newObj: Json.Object) =>
          diffObject(oldObj.value, newObj.value)

        case _ =>
          // Type mismatch or boolean/null change - use Set to replace entirely
          JsonPatch.root(JsonPatch.Op.Set(newJson))
      }
    }

  // ─────────────────────────────────────────────────────────────────────────
  // Number Diff
  // ─────────────────────────────────────────────────────────────────────────

  private def diffNumber(oldNum: Json.Number, newNum: Json.Number): JsonPatch = {
    // Fast path: if strings are identical, numbers are equal - no change needed
    if (oldNum.value == newNum.value) {
      return JsonPatch.empty
    }

    val oldValue = oldNum.toBigDecimalOption
    val newValue = newNum.toBigDecimalOption

    (oldValue, newValue) match {
      case (Some(oldVal), Some(newVal)) =>
        val delta = newVal - oldVal
        if (delta == BigDecimal(0)) {
          // Numeric values are equal but string representations differ
          // Use Set to preserve exact string representation
          JsonPatch.root(JsonPatch.Op.Set(newNum))
        } else {
          JsonPatch.root(JsonPatch.Op.PrimitiveDelta(JsonPatch.PrimitiveOp.NumberDelta(delta)))
        }
      case _ =>
        // Cannot parse as BigDecimal, use Set
        JsonPatch.root(JsonPatch.Op.Set(newNum))
    }
  }

  // ─────────────────────────────────────────────────────────────────────────
  // String Diff
  // ─────────────────────────────────────────────────────────────────────────

  private def diffString(oldStr: java.lang.String, newStr: java.lang.String): JsonPatch =
    if (oldStr == newStr) {
      JsonPatch.empty
    } else {
      val edits = computeStringEdits(oldStr, newStr)

      // Calculate the "size" of the edit operations vs just setting the new string.
      // Deletes only store metadata, so they count as a single unit regardless of length.
      val editSize = edits.foldLeft(0) {
        case (acc, JsonPatch.StringOp.Insert(_, text))    => acc + text.length
        case (acc, JsonPatch.StringOp.Delete(_, _))       => acc + 1
        case (acc, JsonPatch.StringOp.Append(text))       => acc + text.length
        case (acc, JsonPatch.StringOp.Modify(_, _, text)) => acc + text.length
      }

      if (edits.nonEmpty && editSize < newStr.length) {
        JsonPatch.root(JsonPatch.Op.PrimitiveDelta(JsonPatch.PrimitiveOp.StringEdit(edits)))
      } else {
        JsonPatch.root(JsonPatch.Op.Set(new Json.String(newStr)))
      }
    }

  private def computeStringEdits(
    oldStr: java.lang.String,
    newStr: java.lang.String
  ): Vector[JsonPatch.StringOp] = {
    if (oldStr == newStr) return Vector.empty
    if (oldStr.isEmpty) return Vector(JsonPatch.StringOp.Insert(0, newStr))
    if (newStr.isEmpty) return Vector(JsonPatch.StringOp.Delete(0, oldStr.length))

    val lcs   = longestCommonSubsequence(oldStr, newStr)
    val edits = Vector.newBuilder[JsonPatch.StringOp]

    var oldIdx = 0
    var newIdx = 0
    var lcsIdx = 0
    var cursor = 0

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

  private def longestCommonSubsequence(s1: java.lang.String, s2: java.lang.String): java.lang.String = {
    val m = s1.length
    val n = s2.length

    // DP table where dp(i)(j) = length of LCS of s1[0..i) and s2[0..j)
    val dp = Array.ofDim[Int](m + 1, n + 1)

    // Fill the DP table
    var i = 1
    while (i <= m) {
      var j = 1
      while (j <= n) {
        if (s1.charAt(i - 1) == s2.charAt(j - 1)) {
          dp(i)(j) = dp(i - 1)(j - 1) + 1
        } else {
          dp(i)(j) = Math.max(dp(i - 1)(j), dp(i)(j - 1))
        }
        j += 1
      }
      i += 1
    }

    // Reconstruct the LCS by appending chars in reverse, then reversing the result
    // This is O(n) instead of O(n^2) from using insert(0, ...)
    val result = new StringBuilder
    i = m
    var j = n
    while (i > 0 && j > 0) {
      if (s1.charAt(i - 1) == s2.charAt(j - 1)) {
        result.append(s1.charAt(i - 1))
        i -= 1
        j -= 1
      } else if (dp(i - 1)(j) > dp(i)(j - 1)) {
        i -= 1
      } else {
        j -= 1
      }
    }

    result.reverse.toString()
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Array Diff
  // ─────────────────────────────────────────────────────────────────────────

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

  private def computeArrayOps(oldElems: Chunk[Json], newElems: Chunk[Json]): Vector[JsonPatch.ArrayOp] = {
    val ops       = Vector.newBuilder[JsonPatch.ArrayOp]
    val matches   = longestCommonSubsequenceIndices(oldElems, newElems)
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

    // Emit Modify for 1:1 element replacements instead of Delete+Insert
    // This produces smaller patches for modified elements (recursive diff)
    def emitChanges(oldStart: Int, oldEnd: Int, newStart: Int, newEnd: Int): Unit = {
      val deleteCount = oldEnd - oldStart
      val insertCount = newEnd - newStart

      if (deleteCount == insertCount && deleteCount > 0) {
        // 1:1 replacements: use Modify with recursive diff
        var i = 0
        while (i < deleteCount) {
          val oldElem = oldElems(oldStart + i)
          val newElem = newElems(newStart + i)
          if (oldElem != newElem) {
            val patch = diff(oldElem, newElem)
            if (!patch.isEmpty) {
              // JsonDiffer.diff always produces single-op patches at root.
              // This invariant is enforced here; if violated, it indicates a bug.
              require(
                patch.ops.length == 1,
                s"JsonDiffer.diff produced ${patch.ops.length} ops, expected 1"
              )
              ops.addOne(JsonPatch.ArrayOp.Modify(cursor, patch.ops.head.op))
            }
          }
          cursor += 1
          i += 1
        }
      } else {
        // Different counts: fall back to Delete + Insert
        emitDelete(deleteCount)
        emitInsert(newElems.slice(newStart, newEnd))
      }
    }

    var matchIdx = 0
    while (matchIdx < matches.length) {
      val (matchOld, matchNew) = matches(matchIdx)
      emitChanges(oldIdx, matchOld, newIdx, matchNew)

      oldIdx = matchOld + 1
      newIdx = matchNew + 1
      cursor += 1
      matchIdx += 1
    }

    emitChanges(oldIdx, oldElems.length, newIdx, newElems.length)

    ops.result()
  }

  private def longestCommonSubsequenceIndices(
    oldElems: Chunk[Json],
    newElems: Chunk[Json]
  ): Chunk[(Int, Int)] = {
    val m  = oldElems.length
    val n  = newElems.length
    val dp = Array.ofDim[Int](m + 1, n + 1)

    var i = 1
    while (i <= m) {
      var j = 1
      while (j <= n) {
        if (oldElems(i - 1) == newElems(j - 1)) {
          dp(i)(j) = dp(i - 1)(j - 1) + 1
        } else {
          dp(i)(j) = Math.max(dp(i - 1)(j), dp(i)(j - 1))
        }
        j += 1
      }
      i += 1
    }

    val builder = Chunk.newBuilder[(Int, Int)]
    i = m
    var j = n
    while (i > 0 && j > 0) {
      if (oldElems(i - 1) == newElems(j - 1)) {
        builder.addOne((i - 1, j - 1))
        i -= 1
        j -= 1
      } else if (dp(i - 1)(j) >= dp(i)(j - 1)) {
        i -= 1
      } else {
        j -= 1
      }
    }

    builder.result().reverse
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Object Diff
  // ─────────────────────────────────────────────────────────────────────────

  private def diffObject(
    oldFields: Chunk[(java.lang.String, Json)],
    newFields: Chunk[(java.lang.String, Json)]
  ): JsonPatch = {
    val oldMap = oldFields.toMap
    val newMap = newFields.toMap

    val ops = Vector.newBuilder[JsonPatch.ObjectOp]

    // Find added and modified keys
    var i = 0
    while (i < newFields.length) {
      val (key, newValue) = newFields(i)
      oldMap.get(key) match {
        case Some(oldValue) if oldValue != newValue =>
          // Key exists in both but value changed - recursively diff
          val valuePatch = diff(oldValue, newValue)
          if (!valuePatch.isEmpty) {
            ops.addOne(JsonPatch.ObjectOp.Modify(key, valuePatch))
          }
        case None =>
          // Key only in new map - add it
          ops.addOne(JsonPatch.ObjectOp.Add(key, newValue))
        case _ =>
        // Key unchanged - skip
      }
      i += 1
    }

    // Find removed keys
    i = 0
    while (i < oldFields.length) {
      val (key, _) = oldFields(i)
      if (!newMap.contains(key)) {
        ops.addOne(JsonPatch.ObjectOp.Remove(key))
      }
      i += 1
    }

    val result = ops.result()
    if (result.isEmpty) JsonPatch.empty
    else JsonPatch.root(JsonPatch.Op.ObjectEdit(result))
  }
}
