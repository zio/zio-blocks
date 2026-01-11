package zio.blocks.schema

object Diff {
  sealed trait Edit[+A]
  case class Keep[A](value: A)   extends Edit[A]
  case class Insert[A](value: A) extends Edit[A]
  case class Delete[A](value: A) extends Edit[A]

  /**
   * Computes the Longest Common Subsequence (LCS) based edit script to
   * transform xs into ys.
   */
  def diff[A](xs: IndexedSeq[A], ys: IndexedSeq[A]): Vector[Edit[A]] = {
    val m = xs.length
    val n = ys.length
    // DP table storing LCS lengths
    val dp = Array.ofDim[Int](m + 1, n + 1)

    for (i <- 1 to m; j <- 1 to n) {
      if (xs(i - 1) == ys(j - 1)) dp(i)(j) = dp(i - 1)(j - 1) + 1
      else dp(i)(j) = Math.max(dp(i - 1)(j), dp(i)(j - 1))
    }

    // Backtrack to generate edits in reverse order
    var i     = m
    var j     = n
    var edits = List.empty[Edit[A]]

    while (i > 0 || j > 0) {
      if (i > 0 && j > 0 && xs(i - 1) == ys(j - 1)) {
        edits = Keep(xs(i - 1)) :: edits
        i -= 1
        j -= 1
      } else if (j > 0 && (i == 0 || dp(i)(j - 1) >= dp(i - 1)(j))) {
        edits = Insert(ys(j - 1)) :: edits
        j -= 1
      } else if (i > 0 && (j == 0 || dp(i)(j - 1) < dp(i - 1)(j))) {
        edits = Delete(xs(i - 1)) :: edits
        i -= 1
      }
    }
    edits.toVector
  }
}
