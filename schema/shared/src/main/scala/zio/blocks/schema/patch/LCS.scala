package zio.blocks.schema.patch

/**
 * Longest Common Subsequence algorithms shared between Differ and JsonPatch.
 */
private[schema] object LCS {

  /**
   * Compute the longest common subsequence of two strings using dynamic
   * programming. Returns the LCS string itself.
   */
  def stringLCS(s1: String, s2: String): String = {
    val m = s1.length
    val n = s2.length

    // DP table where dp(i)(j) = length of LCS of s1[0..i) and s2[0..j)
    val dp = Array.ofDim[Int](m + 1, n + 1)

    // Fill the DP table
    var i = 1
    while (i <= m) {
      var j = 1
      while (j <= n) {
        if (s1(i - 1) == s2(j - 1)) {
          dp(i)(j) = dp(i - 1)(j - 1) + 1
        } else {
          dp(i)(j) = Math.max(dp(i - 1)(j), dp(i)(j - 1))
        }
        j += 1
      }
      i += 1
    }

    // Reconstruct the LCS
    val result = new StringBuilder
    i = m
    var j = n
    while (i > 0 && j > 0) {
      if (s1(i - 1) == s2(j - 1)) {
        result.insert(0, s1(i - 1))
        i -= 1
        j -= 1
      } else if (dp(i - 1)(j) > dp(i)(j - 1)) {
        i -= 1
      } else {
        j -= 1
      }
    }

    result.toString
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
   *   Vector of (oldIndex, newIndex) pairs for matching elements
   */
  def indicesLCS[A](oldSeq: IndexedSeq[A], newSeq: IndexedSeq[A])(eq: (A, A) => Boolean): Vector[(Int, Int)] = {
    val m  = oldSeq.length
    val n  = newSeq.length
    val dp = Array.ofDim[Int](m + 1, n + 1)

    var i = 1
    while (i <= m) {
      var j = 1
      while (j <= n) {
        if (eq(oldSeq(i - 1), newSeq(j - 1))) {
          dp(i)(j) = dp(i - 1)(j - 1) + 1
        } else {
          dp(i)(j) = Math.max(dp(i - 1)(j), dp(i)(j - 1))
        }
        j += 1
      }
      i += 1
    }

    val builder = Vector.newBuilder[(Int, Int)]
    i = m
    var j = n
    while (i > 0 && j > 0) {
      if (eq(oldSeq(i - 1), newSeq(j - 1))) {
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
}
