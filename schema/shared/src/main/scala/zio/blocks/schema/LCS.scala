package zio.blocks.schema

/**
 * Longest Common Subsequence (LCS) algorithm implementation.
 * Used for computing minimal edit sequences for strings and collections.
 */
object LCS {
  
  /**
   * Represents an edit operation in a diff.
   */
  sealed trait Edit[+A]
  
  object Edit {
    case class Insert[A](index: Int, value: A) extends Edit[A]
    case class Delete(index: Int) extends Edit[Nothing]
    case object Keep extends Edit[Nothing]
  }
  
  /**
   * Compute the LCS table for two sequences.
   * Returns a 2D array where table(i)(j) contains the length of LCS 
   * for prefixes xs(0..i-1) and ys(0..j-1).
   */
  private def computeTable[A](xs: IndexedSeq[A], ys: IndexedSeq[A]): Array[Array[Int]] = {
    val m = xs.length
    val n = ys.length
    val table = Array.ofDim[Int](m + 1, n + 1)
    
    var i = 1
    while (i <= m) {
      var j = 1
      while (j <= n) {
        if (xs(i - 1) == ys(j - 1)) {
          table(i)(j) = table(i - 1)(j - 1) + 1
        } else {
          table(i)(j) = Math.max(table(i - 1)(j), table(i)(j - 1))
        }
        j += 1
      }
      i += 1
    }
    
    table
  }
  
  /**
   * Compute string edit operations to transform `old` into `newStr`.
   * Uses LCS to find minimal edit sequence.
   */
  def diffStrings(old: String, newStr: String): Vector[StringOp] = {
    if (old == newStr) return Vector.empty
    if (old.isEmpty) return Vector(StringOp.Insert(0, newStr))
    if (newStr.isEmpty) return Vector(StringOp.Delete(0, old.length))
    
    val oldSeq = old.toIndexedSeq
    val newSeq = newStr.toIndexedSeq
    val table = computeTable(oldSeq, newSeq)
    
    val ops = Vector.newBuilder[StringOp]
    var i = old.length
    var j = newStr.length
    var pendingInsert: StringBuilder = null
    var insertIndex = 0
    var pendingDeleteStart = -1
    var pendingDeleteLength = 0
    
    while (i > 0 || j > 0) {
      if (i > 0 && j > 0 && oldSeq(i - 1) == newSeq(j - 1)) {
        // Flush any pending operations
        if (pendingInsert != null) {
          ops += StringOp.Insert(insertIndex, pendingInsert.reverseInPlace().toString)
          pendingInsert = null
        }
        if (pendingDeleteLength > 0) {
          ops += StringOp.Delete(pendingDeleteStart, pendingDeleteLength)
          pendingDeleteLength = 0
        }
        i -= 1
        j -= 1
      } else if (j > 0 && (i == 0 || table(i)(j - 1) >= table(i - 1)(j))) {
        // Insert from new
        if (pendingInsert == null) {
          pendingInsert = new StringBuilder
          insertIndex = i
        }
        pendingInsert.append(newSeq(j - 1))
        j -= 1
      } else {
        // Delete from old
        if (pendingInsert != null) {
          ops += StringOp.Insert(insertIndex, pendingInsert.reverseInPlace().toString)
          pendingInsert = null
        }
        if (pendingDeleteLength == 0) {
          pendingDeleteStart = i - 1
          pendingDeleteLength = 1
        } else {
          pendingDeleteStart = i - 1
          pendingDeleteLength += 1
        }
        i -= 1
      }
    }
    
    // Flush remaining operations
    if (pendingInsert != null) {
      ops += StringOp.Insert(insertIndex, pendingInsert.reverseInPlace().toString)
    }
    if (pendingDeleteLength > 0) {
      ops += StringOp.Delete(pendingDeleteStart, pendingDeleteLength)
    }
    
    // Reverse and normalize operations
    ops.result().reverse
  }
  
  /**
   * Compute sequence edit operations to transform `old` into `newSeq`.
   * Uses LCS to find minimal edit sequence.
   */
  def diffSequences[A](old: IndexedSeq[A], newSeq: IndexedSeq[A])(
    toDynamic: A => DynamicValue
  ): Vector[SeqOp] = {
    if (old == newSeq) return Vector.empty
    if (old.isEmpty) {
      return Vector(SeqOp.Append(newSeq.map(toDynamic).toVector))
    }
    if (newSeq.isEmpty) {
      return Vector(SeqOp.Delete(0, old.length))
    }
    
    val table = computeTable(old, newSeq)
    val ops = Vector.newBuilder[SeqOp]
    
    var i = old.length
    var j = newSeq.length
    var pendingInserts: Vector[DynamicValue] = Vector.empty
    var insertIndex = 0
    var deleteStart = -1
    var deleteCount = 0
    
    while (i > 0 || j > 0) {
      if (i > 0 && j > 0 && old(i - 1) == newSeq(j - 1)) {
        // Flush pending operations
        if (pendingInserts.nonEmpty) {
          ops += SeqOp.Insert(insertIndex, pendingInserts.reverse)
          pendingInserts = Vector.empty
        }
        if (deleteCount > 0) {
          ops += SeqOp.Delete(deleteStart, deleteCount)
          deleteCount = 0
        }
        i -= 1
        j -= 1
      } else if (j > 0 && (i == 0 || table(i)(j - 1) >= table(i - 1)(j))) {
        // Insert
        if (pendingInserts.isEmpty) {
          insertIndex = i
        }
        pendingInserts = toDynamic(newSeq(j - 1)) +: pendingInserts
        j -= 1
      } else {
        // Delete
        if (pendingInserts.nonEmpty) {
          ops += SeqOp.Insert(insertIndex, pendingInserts.reverse)
          pendingInserts = Vector.empty
        }
        if (deleteCount == 0) {
          deleteStart = i - 1
          deleteCount = 1
        } else {
          deleteStart = i - 1
          deleteCount += 1
        }
        i -= 1
      }
    }
    
    // Flush remaining
    if (pendingInserts.nonEmpty) {
      ops += SeqOp.Insert(insertIndex, pendingInserts.reverse)
    }
    if (deleteCount > 0) {
      ops += SeqOp.Delete(deleteStart, deleteCount)
    }
    
    ops.result().reverse
  }
}
