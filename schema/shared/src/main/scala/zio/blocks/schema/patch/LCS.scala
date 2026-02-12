package zio.blocks.schema.patch

import zio.blocks.chunk.{Chunk, ChunkBuilder}

/**
 * Longest Common Subsequence algorithms shared between Differ and JsonPatch.
 */
private[schema] object LCS {

  /**
   * Compute the longest common subsequence of two strings using dynamic
   * programming. Returns the LCS string itself.
   */
  def stringLCS(s1: String, s2: String): String = {
    val m  = s1.length
    val n  = s2.length
    val dp = Array.ofDim[Int](m + 1, n + 1)
    var i  = 1
    while (i <= m) {
      var j = 1
      while (j <= n) {
        if (s1(i - 1) == s2(j - 1)) dp(i)(j) = dp(i - 1)(j - 1) + 1
        else dp(i)(j) = Math.max(dp(i - 1)(j), dp(i)(j - 1))
        j += 1
      }
      i += 1
    }
    val result = Array.newBuilder[Char]
    i = m
    var j = n
    while (i > 0 && j > 0) {
      if (s1(i - 1) == s2(j - 1)) {
        result.addOne(s1(i - 1))
        i -= 1
        j -= 1
      } else if (dp(i - 1)(j) > dp(i)(j - 1)) i -= 1
      else j -= 1
    }
    new String(result.result().reverse)
  }

  /**
   * Compute the longest common subsequence indices for two indexed sequences.
   * Returns pairs of (oldIndex, newIndex) for matching elements.
   *
   * The indices are returned in forward order (smallest to largest).
   *
   * @param oldSeq
   *   The original sequence
   * @param newSeq
   *   The new sequence
   * @param eq
   *   Equality function for comparing elements
   * @return
   *   Chunk of (oldIndex, newIndex) pairs for matching elements
   */
  def indicesLCS[A](oldSeq: IndexedSeq[A], newSeq: IndexedSeq[A])(eq: (A, A) => Boolean): Chunk[(Int, Int)] = {
    val m  = oldSeq.length
    val n  = newSeq.length
    val dp = Array.ofDim[Int](m + 1, n + 1)
    var i  = 1
    while (i <= m) {
      var j = 1
      while (j <= n) {
        if (eq(oldSeq(i - 1), newSeq(j - 1))) dp(i)(j) = dp(i - 1)(j - 1) + 1
        else dp(i)(j) = Math.max(dp(i - 1)(j), dp(i)(j - 1))
        j += 1
      }
      i += 1
    }
    val builder = ChunkBuilder.make[(Int, Int)]()
    i = m
    var j = n
    while (i > 0 && j > 0) {
      if (eq(oldSeq(i - 1), newSeq(j - 1))) {
        builder.addOne((i - 1, j - 1))
        i -= 1
        j -= 1
      } else if (dp(i - 1)(j) >= dp(i)(j - 1)) i -= 1
      else j -= 1
    }
    builder.result().reverse
  }
}
