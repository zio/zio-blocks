package zio.blocks.schema.json

import zio.blocks.schema.DynamicOptic

// Computes minimal patches between two Json values using LCS algorithms.
private[json] object JsonDiffer {

  def diff(oldJson: Json, newJson: Json): JsonPatch =
    if (oldJson == newJson) JsonPatch.empty
    else
      (oldJson, newJson) match {
        case (Json.Num(oldS), Json.Num(newS))           => diffNumber(oldS, newS)
        case (Json.Str(oldS), Json.Str(newS))           => diffString(oldS, newS)
        case (Json.Arr(oldElems), Json.Arr(newElems))   => diffArray(oldElems, newElems)
        case (Json.Obj(oldFields), Json.Obj(newFields)) => diffObject(oldFields, newFields)
        case _                                          => JsonPatch.root(JsonPatch.Op.Set(newJson))
      }

  // Use Set instead of NumberDelta to preserve exact string representation
  // BigDecimal.toString can produce different representations (e.g., "0" vs "0.0")
  private def diffNumber(oldS: String, newS: String): JsonPatch =
    JsonPatch.root(JsonPatch.Op.Set(Json.Num(newS)))

  private def diffString(oldStr: String, newStr: String): JsonPatch =
    if (oldStr == newStr) JsonPatch.empty
    else {
      val edits    = computeStringEdits(oldStr, newStr)
      val editSize = edits.foldLeft(0) {
        case (acc, JsonPatch.StringOp.Insert(_, text))    => acc + text.length
        case (acc, JsonPatch.StringOp.Delete(_, _))       => acc + 1
        case (acc, JsonPatch.StringOp.Append(text))       => acc + text.length
        case (acc, JsonPatch.StringOp.Modify(_, _, text)) => acc + text.length
      }
      if (edits.nonEmpty && editSize < newStr.length)
        JsonPatch.root(JsonPatch.Op.PrimitiveDelta(JsonPatch.PrimitiveOp.StringEdit(edits)))
      else
        JsonPatch.root(JsonPatch.Op.Set(Json.Str(newStr)))
    }

  private def computeStringEdits(oldStr: String, newStr: String): Vector[JsonPatch.StringOp] = {
    if (oldStr == newStr) return Vector.empty
    if (oldStr.isEmpty) return Vector(JsonPatch.StringOp.Insert(0, newStr))
    if (newStr.isEmpty) return Vector(JsonPatch.StringOp.Delete(0, oldStr.length))

    val lcs    = longestCommonSubsequence(oldStr, newStr)
    val edits  = Vector.newBuilder[JsonPatch.StringOp]
    var oldIdx = 0
    var newIdx = 0
    var lcsIdx = 0
    var cursor = 0

    while (lcsIdx < lcs.length) {
      val targetChar  = lcs.charAt(lcsIdx)
      val deleteStart = oldIdx
      while (oldIdx < oldStr.length && oldStr.charAt(oldIdx) != targetChar) oldIdx += 1
      val deleteLen = oldIdx - deleteStart
      if (deleteLen > 0) edits += JsonPatch.StringOp.Delete(cursor, deleteLen)

      val insertStart = newIdx
      while (newIdx < newStr.length && newStr.charAt(newIdx) != targetChar) newIdx += 1
      if (newIdx > insertStart) {
        val text = newStr.substring(insertStart, newIdx)
        edits += JsonPatch.StringOp.Insert(cursor, text)
        cursor += text.length
      }

      oldIdx += 1
      newIdx += 1
      cursor += 1
      lcsIdx += 1
    }

    if (oldIdx < oldStr.length) edits += JsonPatch.StringOp.Delete(cursor, oldStr.length - oldIdx)
    if (newIdx < newStr.length) {
      val text = newStr.substring(newIdx)
      if (text.nonEmpty) edits += JsonPatch.StringOp.Insert(cursor, text)
    }
    edits.result()
  }

  private def longestCommonSubsequence(s1: String, s2: String): String = {
    val m  = s1.length
    val n  = s2.length
    val dp = Array.ofDim[Int](m + 1, n + 1)

    var i = 1
    while (i <= m) {
      var j = 1
      while (j <= n) {
        dp(i)(j) = if (s1(i - 1) == s2(j - 1)) dp(i - 1)(j - 1) + 1 else Math.max(dp(i - 1)(j), dp(i)(j - 1))
        j += 1
      }
      i += 1
    }

    val result = new StringBuilder
    i = m
    var j = n
    while (i > 0 && j > 0) {
      if (s1(i - 1) == s2(j - 1)) {
        result.insert(0, s1(i - 1))
        i -= 1
        j -= 1
      } else if (dp(i - 1)(j) > dp(i)(j - 1)) i -= 1
      else j -= 1
    }
    result.toString
  }

  private def diffArray(oldElems: Vector[Json], newElems: Vector[Json]): JsonPatch =
    if (oldElems == newElems) JsonPatch.empty
    else if (oldElems.isEmpty) JsonPatch.root(JsonPatch.Op.ArrayEdit(Vector(JsonPatch.ArrayOp.Append(newElems))))
    else if (newElems.isEmpty)
      JsonPatch.root(JsonPatch.Op.ArrayEdit(Vector(JsonPatch.ArrayOp.Delete(0, oldElems.length))))
    else {
      val arrayOps = computeArrayOps(oldElems, newElems)
      if (arrayOps.isEmpty) JsonPatch.empty else JsonPatch.root(JsonPatch.Op.ArrayEdit(arrayOps))
    }

  private def computeArrayOps(oldElems: Vector[Json], newElems: Vector[Json]): Vector[JsonPatch.ArrayOp] = {
    val ops       = Vector.newBuilder[JsonPatch.ArrayOp]
    val matches   = longestCommonSubsequenceIndices(oldElems, newElems)
    var oldIdx    = 0
    var newIdx    = 0
    var cursor    = 0
    var curLength = oldElems.length

    def emitDelete(count: Int): Unit =
      if (count > 0) {
        ops += JsonPatch.ArrayOp.Delete(cursor, count)
        curLength -= count
      }

    def emitInsert(values: Vector[Json]): Unit =
      if (values.nonEmpty) {
        if (cursor == curLength) ops += JsonPatch.ArrayOp.Append(values)
        else ops += JsonPatch.ArrayOp.Insert(cursor, values)
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

  private def longestCommonSubsequenceIndices(
    oldElems: Vector[Json],
    newElems: Vector[Json]
  ): Vector[(Int, Int)] = {
    val m  = oldElems.length
    val n  = newElems.length
    val dp = Array.ofDim[Int](m + 1, n + 1)

    var i = 1
    while (i <= m) {
      var j = 1
      while (j <= n) {
        dp(i)(j) =
          if (oldElems(i - 1) == newElems(j - 1)) dp(i - 1)(j - 1) + 1 else Math.max(dp(i - 1)(j), dp(i)(j - 1))
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
      } else if (dp(i - 1)(j) >= dp(i)(j - 1)) i -= 1
      else j -= 1
    }
    builder.result().reverse
  }

  private def diffObject(
    oldFields: Vector[(String, Json)],
    newFields: Vector[(String, Json)]
  ): JsonPatch = {
    val oldMap = oldFields.toMap
    val newMap = newFields.toMap
    val ops    = Vector.newBuilder[JsonPatch.JsonPatchOp]

    for ((fieldName, newValue) <- newFields) {
      oldMap.get(fieldName) match {
        case Some(oldValue) if oldValue != newValue =>
          val fieldPatch = diff(oldValue, newValue)
          if (!fieldPatch.isEmpty) {
            for (op <- fieldPatch.ops) {
              ops += JsonPatch.JsonPatchOp(
                new DynamicOptic(DynamicOptic.Node.Field(fieldName) +: op.path.nodes),
                op.op
              )
            }
          }
        case None =>
          ops += JsonPatch.JsonPatchOp(
            DynamicOptic.root,
            JsonPatch.Op.ObjectEdit(Vector(JsonPatch.ObjectOp.Add(fieldName, newValue)))
          )
        case _ =>
      }
    }

    for ((fieldName, _) <- oldFields if !newMap.contains(fieldName)) {
      ops += JsonPatch.JsonPatchOp(
        DynamicOptic.root,
        JsonPatch.Op.ObjectEdit(Vector(JsonPatch.ObjectOp.Remove(fieldName)))
      )
    }

    JsonPatch(ops.result())
  }
}
