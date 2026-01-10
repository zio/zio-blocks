package zio.blocks.schema

/**
 * LCS (Longest Common Subsequence) algorithms for computing minimal diffs.
 */
object LCS {

  /**
   * Compute the edit operations needed to transform `oldStr` into `newStr`.
   * Uses the classic LCS algorithm to find the minimal edit sequence.
   *
   * @return
   *   A vector of StringOp operations that transform oldStr to newStr
   */
  def diffStrings(oldStr: String, newStr: String): Vector[StringOp] = {
    if (oldStr == newStr) return Vector.empty
    if (oldStr.isEmpty) return Vector(StringOp.Insert(0, newStr))
    if (newStr.isEmpty) return Vector(StringOp.Delete(0, oldStr.length))

    // Compute LCS table
    val m   = oldStr.length
    val n   = newStr.length
    val lcs = Array.ofDim[Int](m + 1, n + 1)

    var i = 1
    while (i <= m) {
      var j = 1
      while (j <= n) {
        if (oldStr.charAt(i - 1) == newStr.charAt(j - 1)) {
          lcs(i)(j) = lcs(i - 1)(j - 1) + 1
        } else {
          lcs(i)(j) = Math.max(lcs(i - 1)(j), lcs(i)(j - 1))
        }
        j += 1
      }
      i += 1
    }

    // Backtrack to find edit operations
    val ops = Vector.newBuilder[StringOp]
    i = m
    var j = n

    // Track pending inserts and deletes for batching
    var pendingInsertIdx  = -1
    val pendingInsertText = new StringBuilder
    var pendingDeleteIdx  = -1
    var pendingDeleteLen  = 0

    def flushInsert(): Unit =
      if (pendingInsertIdx >= 0 && pendingInsertText.nonEmpty) {
        ops += StringOp.Insert(pendingInsertIdx, pendingInsertText.toString)
        pendingInsertIdx = -1
        pendingInsertText.clear()
      }

    def flushDelete(): Unit =
      if (pendingDeleteIdx >= 0 && pendingDeleteLen > 0) {
        ops += StringOp.Delete(pendingDeleteIdx, pendingDeleteLen)
        pendingDeleteIdx = -1
        pendingDeleteLen = 0
      }

    while (i > 0 || j > 0) {
      if (i > 0 && j > 0 && oldStr.charAt(i - 1) == newStr.charAt(j - 1)) {
        // Characters match, no operation needed
        flushInsert()
        flushDelete()
        i -= 1
        j -= 1
      } else if (j > 0 && (i == 0 || lcs(i)(j - 1) >= lcs(i - 1)(j))) {
        // Insert from new string
        flushDelete()
        if (pendingInsertIdx < 0) {
          pendingInsertIdx = i
        }
        pendingInsertText.insert(0, newStr.charAt(j - 1))
        j -= 1
      } else {
        // Delete from old string
        flushInsert()
        if (pendingDeleteIdx < 0 || pendingDeleteIdx != i - 1) {
          flushDelete()
          pendingDeleteIdx = i - 1
          pendingDeleteLen = 1
        } else {
          pendingDeleteIdx -= 1
          pendingDeleteLen += 1
        }
        i -= 1
      }
    }

    flushInsert()
    flushDelete()

    // Reverse since we built operations backwards
    ops.result().reverse
  }

  /**
   * Compute the edit operations needed to transform `oldSeq` into `newSeq`.
   * Uses the classic LCS algorithm to find the minimal edit sequence.
   *
   * @return
   *   A vector of SeqOp operations that transform oldSeq to newSeq
   */
  def diffSequences(oldSeq: Vector[DynamicValue], newSeq: Vector[DynamicValue]): Vector[SeqOp] = {
    if (oldSeq == newSeq) return Vector.empty
    if (oldSeq.isEmpty) return Vector(SeqOp.Append(newSeq))
    if (newSeq.isEmpty) return Vector(SeqOp.Delete(0, oldSeq.length))

    // Compute LCS table
    val m   = oldSeq.length
    val n   = newSeq.length
    val lcs = Array.ofDim[Int](m + 1, n + 1)

    var i = 1
    while (i <= m) {
      var j = 1
      while (j <= n) {
        if (oldSeq(i - 1) == newSeq(j - 1)) {
          lcs(i)(j) = lcs(i - 1)(j - 1) + 1
        } else {
          lcs(i)(j) = Math.max(lcs(i - 1)(j), lcs(i)(j - 1))
        }
        j += 1
      }
      i += 1
    }

    // Backtrack to find edit operations
    val ops = Vector.newBuilder[SeqOp]
    i = m
    var j = n

    // Track pending inserts and deletes for batching
    var pendingInsertIdx    = -1
    var pendingInsertValues = Vector.newBuilder[DynamicValue]
    var pendingDeleteIdx    = -1
    var pendingDeleteCount  = 0

    def flushInsert(): Unit = {
      val values = pendingInsertValues.result()
      if (pendingInsertIdx >= 0 && values.nonEmpty) {
        ops += SeqOp.Insert(pendingInsertIdx, values)
        pendingInsertIdx = -1
        pendingInsertValues = Vector.newBuilder[DynamicValue]
      }
    }

    def flushDelete(): Unit =
      if (pendingDeleteIdx >= 0 && pendingDeleteCount > 0) {
        ops += SeqOp.Delete(pendingDeleteIdx, pendingDeleteCount)
        pendingDeleteIdx = -1
        pendingDeleteCount = 0
      }

    while (i > 0 || j > 0) {
      if (i > 0 && j > 0 && oldSeq(i - 1) == newSeq(j - 1)) {
        // Elements match, no operation needed
        flushInsert()
        flushDelete()
        i -= 1
        j -= 1
      } else if (j > 0 && (i == 0 || lcs(i)(j - 1) >= lcs(i - 1)(j))) {
        // Insert from new sequence
        flushDelete()
        if (pendingInsertIdx < 0) {
          pendingInsertIdx = i
        }
        pendingInsertValues += newSeq(j - 1)
        j -= 1
      } else {
        // Delete from old sequence
        flushInsert()
        if (pendingDeleteIdx < 0 || pendingDeleteIdx != i - 1) {
          flushDelete()
          pendingDeleteIdx = i - 1
          pendingDeleteCount = 1
        } else {
          pendingDeleteIdx -= 1
          pendingDeleteCount += 1
        }
        i -= 1
      }
    }

    flushInsert()
    flushDelete()

    // Reverse and fix insert orders since we built operations backwards
    val rawOps = ops.result().reverse

    // Reorder inserts to put values in correct order
    rawOps.map {
      case SeqOp.Insert(idx, values) => SeqOp.Insert(idx, values.reverse)
      case other                     => other
    }
  }

  /**
   * Compute the edit operations needed to transform `oldMap` into `newMap`.
   *
   * @return
   *   A vector of MapOp operations that transform oldMap to newMap
   */
  def diffMaps(
    oldMap: Vector[(DynamicValue, DynamicValue)],
    newMap: Vector[(DynamicValue, DynamicValue)]
  ): Vector[MapOp] = {
    val ops      = Vector.newBuilder[MapOp]
    val oldKeys  = oldMap.map(_._1).toSet
    val newKeys  = newMap.map(_._1).toSet
    val oldIndex = oldMap.toMap

    // Removed keys
    oldKeys.foreach { key =>
      if (!newKeys.contains(key)) {
        ops += MapOp.Remove(key)
      }
    }

    // Added or modified keys
    newMap.foreach { case (key, newValue) =>
      oldIndex.get(key) match {
        case None =>
          ops += MapOp.Add(key, newValue)
        case Some(oldValue) if oldValue != newValue =>
          ops += MapOp.Modify(key, Operation.Set(newValue))
        case _ =>
        // Same value, no operation needed
      }
    }

    ops.result()
  }
}
