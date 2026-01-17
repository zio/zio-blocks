package zio.blocks.schema.patch

import zio.blocks.schema.json.Json

/**
 * Computes efficient patches between JSON values using the Longest Common
 * Subsequence (LCS) algorithm.
 *
 * This differ produces minimal "Git-style" patches for arrays and strings by
 * identifying insert/delete operations rather than replacing entire values. For
 * objects, it recursively diffs nested fields.
 *
 * @example
 *   {{{ val oldJson = Json.Arr(Vector(Json.Num(1), Json.Num(3))) val newJson =
 *   Json.Arr(Vector(Json.Num(1), Json.Num(2), Json.Num(3))) val patch =
 *   JsonDiffer.diff(oldJson, newJson) // Produces: ArrayEdit(Vector(Insert(1,
 *   Num(2)))) }}}
 */
object JsonDiffer {

  /**
   * Computes a patch that transforms `source` into `target`.
   *
   * The diff algorithm uses:
   *   - LCS for arrays and strings (minimal insert/delete operations)
   *   - Delta for numbers (instead of full replacement)
   *   - Recursive comparison for objects
   *   - Set operation for type mismatches
   *
   * @param source
   *   the original JSON value
   * @param target
   *   the desired JSON value
   * @return
   *   a JsonPatch that when applied to `source` produces `target`
   */
  def diff(source: Json, target: Json): JsonPatch =
    if (source == target) JsonPatch.empty
    else
      (source, target) match {
        case (Json.Bool(_), Json.Bool(_)) =>
          JsonPatch(Vector(JsonPatchOp.Update(JsonOp.Set(target))))

        case (Json.Num(oldVal), Json.Num(newVal)) =>
          val delta = newVal - oldVal
          JsonPatch(Vector(JsonPatchOp.Update(JsonOp.NumberDelta(delta))))

        case (Json.Str(oldVal), Json.Str(newVal)) =>
          val edits = computeStringEdits(oldVal, newVal)
          if (edits.isEmpty) JsonPatch.empty
          else JsonPatch(Vector(JsonPatchOp.Update(JsonOp.StringEdit(edits))))

        case (Json.Arr(oldElem), Json.Arr(newElem)) =>
          val edits = computeArrayEdits(oldElem, newElem)
          if (edits.isEmpty) JsonPatch.empty
          else JsonPatch(Vector(JsonPatchOp.Update(JsonOp.ArrayEdit(edits))))

        case (Json.Obj(oldFields), Json.Obj(newFields)) =>
          diffObject(oldFields, newFields)

        case _ =>
          JsonPatch(Vector(JsonPatchOp.Update(JsonOp.Set(target))))
      }

  private def diffObject(oldFields: Vector[(String, Json)], newFields: Vector[(String, Json)]): JsonPatch = {
    val oldMap = oldFields.toMap
    val newMap = newFields.toMap
    val ops    = Vector.newBuilder[JsonPatchOp]

    // 1. Recursive updates for modified fields
    // 2. Additions for new fields
    // Combined loop for structure
    val addedOrRemovedOps = Vector.newBuilder[ObjectOp]

    newFields.foreach { case (key, newValue) =>
      oldMap.get(key) match {
        case Some(oldValue) if oldValue != newValue =>
          val subPatch = diff(oldValue, newValue)
          if (subPatch.ops.nonEmpty) {
            // For each op in subPatch, wrap it in AtKey(key, ...)
            subPatch.ops.foreach { op =>
              ops += JsonPatchOp.AtKey(key, op)
            }
          }
        case None =>
          addedOrRemovedOps += ObjectOp.Add(key, newValue)
        case _ => // Unchanged
      }
    }

    // 3. Removals for missing fields
    oldFields.foreach { case (key, _) =>
      if (!newMap.contains(key)) {
        addedOrRemovedOps += ObjectOp.Remove(key)
      }
    }

    val objOps = addedOrRemovedOps.result()
    if (objOps.nonEmpty) {
      ops += JsonPatchOp.Update(JsonOp.ObjectEdit(objOps))
    }

    JsonPatch(ops.result())
  }

  // --- LCS for Arrays ---

  private def computeArrayEdits(oldElems: Vector[Json], newElems: Vector[Json]): Vector[ArrayOp] = {
    val ops     = Vector.newBuilder[ArrayOp]
    val matches = longestCommonSubsequenceIndices(oldElems, newElems)

    var oldIdx = 0
    var newIdx = 0
    var cursor = 0 // current index in the array after applied edits (DynamicPatch logic)
    // Wait, DynamicPatch SequenceOp.Delete uses index relative to "current state" ???
    // Let's re-read Differ.scala carefully.
    // loops over matches.
    // emitDelete(matchOld - oldIdx) -> Delete(cursor, count)
    // cursror NOT incremented on delete.
    // emitInsert -> Insert(cursor, values) -> cursor += values.length
    // on Match -> cursor += 1

    // JsonPatch ArrayOp.Delete(idx). NO COUNT. one by one.
    // JsonPatch ArrayOp.Insert(idx, val). One by one.

    matches.foreach { case (matchOld, matchNew) =>
      // Delete skipped elements from old
      val deleteCount = matchOld - oldIdx
      // If we delete at `cursor`, we delete the element that is currently there.
      // We need to do this `deleteCount` times.
      // Since `cursor` points to the start of the block to be deleted, repeatedly deleting at `cursor` works?
      // No, if we delete at `cursor`, the next element shifts to `cursor`.
      // So yes, repeatedly Delete(cursor) `deleteCount` times removes the block.
      // BUT, does DynamicPatch `Delete(idx, count)` support range? Yes.
      // Our `ArrayOp.Delete` is `case class Delete(index: Int)`. Single index.
      // So we must emit `Delete(cursor)` `count` times.

      for (_ <- 0 until deleteCount) {
        ops += ArrayOp.Delete(cursor)
      }
      // Note: deleting doesn't advance cursor relative to the resulting array structure for *Insertions*,
      // but it consumes old elements.
      // The `cursor` tracks position in the *building* new array.
      // Elements before cursor are "stable" or "processed".

      // Insert new elements
      val insertCount = matchNew - newIdx
      for (k <- 0 until insertCount) {
        val newVal = newElems(newIdx + k)
        ops += ArrayOp.Insert(cursor, newVal)
        cursor += 1
      }

      oldIdx = matchOld + 1
      newIdx = matchNew + 1
      cursor += 1 // Advance for the matching element
    }

    // Trailing deletes
    val tailDelete = oldElems.length - oldIdx
    for (_ <- 0 until tailDelete) {
      ops += ArrayOp.Delete(cursor)
    }

    // Trailing inserts
    val tailInsert = newElems.length - newIdx
    for (k <- 0 until tailInsert) {
      // Should we use Append? Or Insert?
      // Append is safer if index updates match.
      // If cursor == current length, Insert(cursor) == Append.
      // Let's use Append if we are at the end?
      // Or just Insert(cursor) for simplicity as implemented in applyOp.
      // Although ArrayOp has `Append`.
      // Let's use Append if possible?
      // DynamicPatch uses Append for optimization.
      // We can just use Insert for now.
      val newVal = newElems(newIdx + k)
      ops += ArrayOp.Insert(cursor, newVal)
      cursor += 1
    }

    ops.result()
  }

  private def longestCommonSubsequenceIndices(oldElems: Vector[Json], newElems: Vector[Json]): Vector[(Int, Int)] = {
    // Standard DP LCS
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
          dp(i)(j) = math.max(dp(i - 1)(j), dp(i)(j - 1))
        }
        j += 1
      }
      i += 1
    }

    val builder = Vector.newBuilder[(Int, Int)]
    i = m
    var j = n
    while (i > 0 && j > 0) {
      if (oldElems(i - 1) == newElems(j - 1)) {
        builder += ((i - 1, j - 1))
        i -= 1
        j -= 1
      } else if (dp(i - 1)(j) >= dp(i)(j - 1)) { // Prefer deleting (move up) vs inserting (move left) ?
        i -= 1
      } else {
        j -= 1
      }
    }
    builder.result().reverse
  }

  // --- LCS for Strings ---

  private def computeStringEdits(oldStr: String, newStr: String): Vector[StringOp] = {
    if (oldStr == newStr) return Vector.empty
    if (oldStr.isEmpty) return Vector(StringOp.Insert(0, newStr))
    if (newStr.isEmpty) return Vector(StringOp.Delete(0, oldStr.length))

    val matches = longestCommonSubsequenceIndicesStr(oldStr, newStr)
    val ops     = Vector.newBuilder[StringOp]

    var oldIdx = 0
    var newIdx = 0
    var cursor = 0

    matches.foreach { case (matchOld, matchNew) =>
      val deleteLen = matchOld - oldIdx
      if (deleteLen > 0) {
        ops += StringOp.Delete(cursor, deleteLen)
        // Cursor stays same
      }

      val insertLen = matchNew - newIdx
      if (insertLen > 0) {
        val substr = newStr.substring(newIdx, newIdx + insertLen)
        ops += StringOp.Insert(cursor, substr)
        cursor += insertLen
      }

      oldIdx = matchOld + 1
      newIdx = matchNew + 1
      cursor += 1
    }

    if (oldIdx < oldStr.length) {
      ops += StringOp.Delete(cursor, oldStr.length - oldIdx)
    }
    if (newIdx < newStr.length) {
      ops += StringOp.Insert(cursor, newStr.substring(newIdx))
    }

    ops.result()
  }

  private def longestCommonSubsequenceIndicesStr(s1: String, s2: String): Vector[(Int, Int)] = {
    // Same algorithm but for Char
    val m  = s1.length
    val n  = s2.length
    val dp = Array.ofDim[Int](m + 1, n + 1)

    var i = 1
    while (i <= m) {
      var j = 1
      while (j <= n) {
        if (s1.charAt(i - 1) == s2.charAt(j - 1)) {
          dp(i)(j) = dp(i - 1)(j - 1) + 1
        } else {
          dp(i)(j) = math.max(dp(i - 1)(j), dp(i)(j - 1))
        }
        j += 1
      }
      i += 1
    }

    val builder = Vector.newBuilder[(Int, Int)]
    i = m
    var j = n
    while (i > 0 && j > 0) {
      if (s1.charAt(i - 1) == s2.charAt(j - 1)) {
        builder += ((i - 1, j - 1))
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
}
