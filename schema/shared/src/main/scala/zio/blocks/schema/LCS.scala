package zio.blocks.schema

/**
 * LCS (Longest Common Subsequence) algorithms for computing minimal diffs.
 */
object LCS {
  
  /**
   * Compute the diff between two strings as a sequence of StringOps.
   * Uses a simple approach: find common prefix, common suffix, then handle the middle.
   */
  def stringDiff(oldStr: String, newStr: String): Vector[StringOp] = {
    if (oldStr == newStr) return Vector.empty
    if (oldStr.isEmpty) return Vector(StringOp.Insert(0, newStr))
    if (newStr.isEmpty) return Vector(StringOp.Delete(0, oldStr.length))
    
    // Find common prefix
    var prefixLen = 0
    val minLen = Math.min(oldStr.length, newStr.length)
    while (prefixLen < minLen && oldStr(prefixLen) == newStr(prefixLen)) {
      prefixLen += 1
    }
    
    // Find common suffix (not overlapping with prefix)
    var suffixLen = 0
    val maxSuffix = minLen - prefixLen
    while (suffixLen < maxSuffix && 
           oldStr(oldStr.length - 1 - suffixLen) == newStr(newStr.length - 1 - suffixLen)) {
      suffixLen += 1
    }
    
    // What's left in the middle
    val oldMiddleStart = prefixLen
    val oldMiddleEnd = oldStr.length - suffixLen
    val newMiddleStart = prefixLen
    val newMiddleEnd = newStr.length - suffixLen
    
    val oldMiddleLen = oldMiddleEnd - oldMiddleStart
    val newMiddle = newStr.substring(newMiddleStart, newMiddleEnd)
    
    val ops = Vector.newBuilder[StringOp]
    
    if (oldMiddleLen > 0) {
      ops += StringOp.Delete(prefixLen, oldMiddleLen)
    }
    if (newMiddle.nonEmpty) {
      ops += StringOp.Insert(prefixLen, newMiddle)
    }
    
    ops.result()
  }
  
  /**
   * Compute the diff between two sequences as a sequence of SeqOps.
   */
  def sequenceDiff(oldSeq: Vector[DynamicValue], newSeq: Vector[DynamicValue]): Vector[SeqOp] = {
    if (oldSeq == newSeq) return Vector.empty
    if (oldSeq.isEmpty) return Vector(SeqOp.Append(newSeq))
    if (newSeq.isEmpty) return Vector(SeqOp.Delete(0, oldSeq.length))
    
    // Find common prefix
    var prefixLen = 0
    val minLen = Math.min(oldSeq.length, newSeq.length)
    while (prefixLen < minLen && oldSeq(prefixLen) == newSeq(prefixLen)) {
      prefixLen += 1
    }
    
    // Find common suffix (not overlapping with prefix)
    var suffixLen = 0
    val maxSuffix = minLen - prefixLen
    while (suffixLen < maxSuffix && 
           oldSeq(oldSeq.length - 1 - suffixLen) == newSeq(newSeq.length - 1 - suffixLen)) {
      suffixLen += 1
    }
    
    // What's left in the middle
    val oldMiddleStart = prefixLen
    val oldMiddleEnd = oldSeq.length - suffixLen
    val newMiddleStart = prefixLen
    val newMiddleEnd = newSeq.length - suffixLen
    
    val oldMiddleLen = oldMiddleEnd - oldMiddleStart
    val newMiddle = newSeq.slice(newMiddleStart, newMiddleEnd)
    
    val ops = Vector.newBuilder[SeqOp]
    
    if (oldMiddleLen > 0) {
      ops += SeqOp.Delete(prefixLen, oldMiddleLen)
    }
    if (newMiddle.nonEmpty) {
      ops += SeqOp.Insert(prefixLen, newMiddle)
    }
    
    ops.result()
  }
}
