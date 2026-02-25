package zio.blocks.schema.json

import zio.blocks.chunk.{Chunk, ChunkBuilder}
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
    if (source == target) JsonPatch.empty
    else if (source.jsonType ne target.jsonType) JsonPatch.root(new JsonPatch.Op.Set(target))
    else {
      source match {
        case obj: Json.Object   => diffObject(obj.value, target.asInstanceOf[Json.Object].value)
        case arr: Json.Array    => diffArray(arr.value, target.asInstanceOf[Json.Array].value)
        case str: Json.String   => diffString(str, target.asInstanceOf[Json.String])
        case num: Json.Number   => diffNumber(num.value, target.asInstanceOf[Json.Number].value)
        case bool: Json.Boolean =>
          val oldVal = bool.value
          val newVal = target.asInstanceOf[Json.Boolean].value
          if (oldVal == newVal) JsonPatch.empty
          else JsonPatch.root(new JsonPatch.Op.Set(target))
        case _ => JsonPatch.empty
      }
    }

  /**
   * Diff two JSON numbers by computing their delta. Uses NumberDelta to
   * represent the change.
   */
  private[this] def diffNumber(oldVal: BigDecimal, newVal: BigDecimal): JsonPatch =
    if (oldVal == newVal) JsonPatch.empty
    else JsonPatch.root(new JsonPatch.Op.PrimitiveDelta(new JsonPatch.PrimitiveOp.NumberDelta(newVal - oldVal)))

  /**
   * Diff two JSON strings using LCS algorithm. Uses StringEdit when more
   * compact than Set.
   */
  private[this] def diffString(oldStr: Json.String, newStr: Json.String): JsonPatch =
    if (oldStr.value == newStr.value) JsonPatch.empty
    else {
      val edits = computeStringEdits(oldStr.value, newStr.value)
      // Calculate the "size" of the edit operations vs just setting the new string.
      // Deletes only store metadata, so they count as a single unit regardless of length.
      val editSize = edits.foldLeft(0) { (acc, edit) =>
        acc + (edit match {
          case ins: JsonPatch.StringOp.Insert => ins.text.length
          case app: JsonPatch.StringOp.Append => app.text.length
          case mod: JsonPatch.StringOp.Modify => mod.text.length
          case _                              => 1
        })
      }
      JsonPatch.root(if (edits.nonEmpty && editSize < newStr.value.length) {
        new JsonPatch.Op.PrimitiveDelta(new JsonPatch.PrimitiveOp.StringEdit(edits))
      } else {
        new JsonPatch.Op.Set(newStr)
      })
    }

  /**
   * Compute string edit operations using LCS algorithm. Returns a sequence of
   * Insert/Delete operations with indices adjusted for previously applied
   * edits.
   */
  private[this] def computeStringEdits(oldStr: String, newStr: String): Chunk[JsonPatch.StringOp] = {
    if (oldStr == newStr) return Chunk.empty
    if (oldStr.isEmpty) return Chunk.single(new JsonPatch.StringOp.Insert(0, newStr))
    if (newStr.isEmpty) return Chunk.single(new JsonPatch.StringOp.Delete(0, oldStr.length))
    val lcs    = LCS.stringLCS(oldStr, newStr)
    val edits  = ChunkBuilder.make[JsonPatch.StringOp]()
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
      if (deleteLen > 0) edits.addOne(new JsonPatch.StringOp.Delete(cursor, deleteLen))
      // Insert characters from the new string that appear before the next LCS character.
      val insertStart = newIdx
      while (newIdx < newStr.length && newStr.charAt(newIdx) != targetChar) {
        newIdx += 1
      }
      if (newIdx > insertStart) {
        val text = newStr.substring(insertStart, newIdx)
        edits.addOne(new JsonPatch.StringOp.Insert(cursor, text))
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
      edits.addOne(new JsonPatch.StringOp.Delete(cursor, deleteLen))
    }
    // Insert any trailing characters from the new string.
    if (newIdx < newStr.length) {
      val text = newStr.substring(newIdx)
      if (text.nonEmpty) edits.addOne(new JsonPatch.StringOp.Insert(cursor, text))
    }
    edits.result()
  }

  /**
   * Diff two JSON arrays using LCS-based alignment. Produces
   * Insert/Delete/Append operations that describe how to transform the old
   * elements into the new ones.
   */
  private[this] def diffArray(oldElems: Chunk[Json], newElems: Chunk[Json]): JsonPatch =
    if (oldElems == newElems) JsonPatch.empty
    else if (oldElems.isEmpty) {
      JsonPatch.root(new JsonPatch.Op.ArrayEdit(Chunk.single(new JsonPatch.ArrayOp.Append(newElems))))
    } else if (newElems.isEmpty) {
      JsonPatch.root(new JsonPatch.Op.ArrayEdit(Chunk.single(new JsonPatch.ArrayOp.Delete(0, oldElems.length))))
    } else {
      val arrayOps = computeArrayOps(oldElems, newElems)
      if (arrayOps.isEmpty) JsonPatch.empty
      else JsonPatch.root(new JsonPatch.Op.ArrayEdit(arrayOps))
    }

  /**
   * Convert the difference between two arrays into ArrayOps using LCS
   * alignment.
   */
  private[this] def computeArrayOps(oldElems: Chunk[Json], newElems: Chunk[Json]): Chunk[JsonPatch.ArrayOp] = {
    val ops       = ChunkBuilder.make[JsonPatch.ArrayOp]()
    val matches   = LCS.indicesLCS(oldElems, newElems)(_ == _)
    var oldIdx    = 0
    var newIdx    = 0
    var cursor    = 0
    var curLength = oldElems.length

    def emitDelete(count: Int): Unit =
      if (count > 0) {
        ops.addOne(new JsonPatch.ArrayOp.Delete(cursor, count))
        curLength -= count
      }

    def emitInsert(values: Chunk[Json]): Unit =
      if (values.nonEmpty) {
        val insertionIndex = cursor
        if (insertionIndex == curLength) ops.addOne(new JsonPatch.ArrayOp.Append(values))
        else ops.addOne(new JsonPatch.ArrayOp.Insert(insertionIndex, values))
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
  private[this] def diffObject(oldFields: Chunk[(String, Json)], newFields: Chunk[(String, Json)]): JsonPatch = {
    val oldMap = new java.util.HashMap[String, Json](oldFields.length) {
      oldFields.foreach(kv => put(kv._1, kv._2))
    }
    val newSet = new java.util.HashSet[String](newFields.length)
    val ops    = ChunkBuilder.make[JsonPatch.ObjectOp]()
    newFields.foreach { kv =>
      val fieldName = kv._1
      val newValue  = kv._2
      newSet.add(fieldName)
      val oldValue = oldMap.get(fieldName)
      if (oldValue ne null) {
        if (oldValue != newValue) {
          val fieldPatch = diff(oldValue, newValue)
          if (!fieldPatch.isEmpty) ops.addOne(new JsonPatch.ObjectOp.Modify(fieldName, fieldPatch))
        }
      } else ops.addOne(new JsonPatch.ObjectOp.Add(fieldName, newValue))
    }
    oldFields.foreach { kv =>
      val fieldName = kv._1
      if (!newSet.contains(fieldName)) ops.addOne(new JsonPatch.ObjectOp.Remove(fieldName))
    }
    val result = ops.result()
    if (result.isEmpty) JsonPatch.empty
    else JsonPatch.root(new JsonPatch.Op.ObjectEdit(result))
  }
}
