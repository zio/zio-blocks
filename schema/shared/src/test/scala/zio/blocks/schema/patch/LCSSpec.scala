package zio.blocks.schema.patch

import zio.blocks.chunk.Chunk
import zio.blocks.schema.SchemaBaseSpec
import zio.test._

object LCSSpec extends SchemaBaseSpec {

  def spec = suite("LCS")(
    suite("stringLCS")(
      test("empty strings") {
        assertTrue(LCS.stringLCS("", "") == "")
      },
      test("one empty string") {
        assertTrue(LCS.stringLCS("abc", "") == "") &&
        assertTrue(LCS.stringLCS("", "abc") == "")
      },
      test("identical strings") {
        assertTrue(LCS.stringLCS("hello", "hello") == "hello")
      },
      test("no common characters") {
        assertTrue(LCS.stringLCS("abc", "xyz") == "")
      },
      test("single common character") {
        assertTrue(LCS.stringLCS("abc", "xay") == "a")
      },
      test("subsequence at start") {
        assertTrue(LCS.stringLCS("abcdef", "abcxyz") == "abc")
      },
      test("subsequence at end") {
        assertTrue(LCS.stringLCS("xyzabc", "123abc") == "abc")
      },
      test("interleaved subsequence") {
        assertTrue(LCS.stringLCS("axbycz", "abc") == "abc")
      },
      test("classic example: ABCDGH vs AEDFHR") {
        assertTrue(LCS.stringLCS("ABCDGH", "AEDFHR") == "ADH")
      },
      test("classic example: AGGTAB vs GXTXAYB") {
        assertTrue(LCS.stringLCS("AGGTAB", "GXTXAYB") == "GTAB")
      },
      test("repeated characters") {
        assertTrue(LCS.stringLCS("aaa", "aaaa") == "aaa")
      },
      test("case sensitive") {
        assertTrue(LCS.stringLCS("ABC", "abc") == "")
      }
    ),
    suite("indicesLCS")(
      test("empty sequences") {
        val result = LCS.indicesLCS(Chunk.empty[Int], Chunk.empty[Int])(_ == _)
        assertTrue(result == Chunk.empty)
      },
      test("one empty sequence") {
        val result1 = LCS.indicesLCS(Chunk(1, 2, 3), Chunk.empty[Int])(_ == _)
        val result2 = LCS.indicesLCS(Chunk.empty[Int], Chunk(1, 2, 3))(_ == _)
        assertTrue(result1 == Chunk.empty) &&
        assertTrue(result2 == Chunk.empty)
      },
      test("identical sequences") {
        val result = LCS.indicesLCS(Chunk(1, 2, 3), Chunk(1, 2, 3))(_ == _)
        assertTrue(result == Chunk((0, 0), (1, 1), (2, 2)))
      },
      test("no common elements") {
        val result = LCS.indicesLCS(Chunk(1, 2, 3), Chunk(4, 5, 6))(_ == _)
        assertTrue(result == Chunk.empty)
      },
      test("single common element") {
        val result = LCS.indicesLCS(Chunk(1, 2, 3), Chunk(4, 2, 5))(_ == _)
        assertTrue(result == Chunk((1, 1)))
      },
      test("subsequence at start") {
        val result = LCS.indicesLCS(Chunk(1, 2, 3, 4), Chunk(1, 2, 5, 6))(_ == _)
        assertTrue(result == Chunk((0, 0), (1, 1)))
      },
      test("subsequence at end") {
        val result = LCS.indicesLCS(Chunk(1, 2, 3, 4), Chunk(5, 6, 3, 4))(_ == _)
        assertTrue(result == Chunk((2, 2), (3, 3)))
      },
      test("interleaved subsequence") {
        val result = LCS.indicesLCS(Chunk(1, 9, 2, 8, 3), Chunk(1, 2, 3))(_ == _)
        assertTrue(result == Chunk((0, 0), (2, 1), (4, 2)))
      },
      test("with custom equality") {
        // Case-insensitive string comparison
        val result = LCS.indicesLCS(
          Chunk("A", "B", "C"),
          Chunk("a", "b", "c")
        )(_.equalsIgnoreCase(_))
        assertTrue(result == Chunk((0, 0), (1, 1), (2, 2)))
      },
      test("with duplicates - picks first match") {
        val result = LCS.indicesLCS(Chunk(1, 1, 2), Chunk(1, 2, 2))(_ == _)
        // LCS is [1, 2], matching indices should be valid
        assertTrue(result.length == 2) &&
        assertTrue(result.map(_._1).forall(i => i >= 0 && i < 3)) &&
        assertTrue(result.map(_._2).forall(i => i >= 0 && i < 3))
      },
      test("indices are in ascending order") {
        val result     = LCS.indicesLCS(Chunk(3, 1, 4, 1, 5, 9), Chunk(1, 4, 5))(_ == _)
        val oldIndices = result.map(_._1)
        val newIndices = result.map(_._2)
        assertTrue(oldIndices == oldIndices.sorted) &&
        assertTrue(newIndices == newIndices.sorted)
      },
      test("works with complex types") {
        case class Item(id: Int, name: String)
        val old  = Chunk(Item(1, "a"), Item(2, "b"), Item(3, "c"))
        val new_ = Chunk(Item(1, "x"), Item(4, "d"), Item(3, "c"))
        // Compare by id only
        val result = LCS.indicesLCS(old, new_)(_.id == _.id)
        assertTrue(result == Chunk((0, 0), (2, 2)))
      }
    ),
    suite("property-based")(
      test("stringLCS result is subsequence of both inputs") {
        check(Gen.string, Gen.string) { (s1, s2) =>
          val lcs = LCS.stringLCS(s1, s2)
          assertTrue(isSubsequence(lcs, s1)) &&
          assertTrue(isSubsequence(lcs, s2))
        }
      },
      test("stringLCS of identical strings is the string itself") {
        check(Gen.string) { s =>
          assertTrue(LCS.stringLCS(s, s) == s)
        }
      },
      test("stringLCS length <= min of input lengths") {
        check(Gen.string, Gen.string) { (s1, s2) =>
          val lcs = LCS.stringLCS(s1, s2)
          assertTrue(lcs.length <= Math.min(s1.length, s2.length))
        }
      },
      test("indicesLCS produces valid indices") {
        check(Gen.listOf(Gen.int(-10, 10)), Gen.listOf(Gen.int(-10, 10))) { (l1, l2) =>
          val v1     = Chunk.from(l1)
          val v2     = Chunk.from(l2)
          val result = LCS.indicesLCS(v1, v2)(_ == _)
          assertTrue(result.forall { case (i, j) =>
            i >= 0 && i < v1.length && j >= 0 && j < v2.length && v1(i) == v2(j)
          })
        }
      },
      test("indicesLCS indices are strictly increasing") {
        check(Gen.listOf(Gen.int(-10, 10)), Gen.listOf(Gen.int(-10, 10))) { (l1, l2) =>
          val v1         = Chunk.from(l1)
          val v2         = Chunk.from(l2)
          val result     = LCS.indicesLCS(v1, v2)(_ == _)
          val oldIndices = result.map(_._1)
          val newIndices = result.map(_._2)
          assertTrue(isStrictlyIncreasing(oldIndices)) &&
          assertTrue(isStrictlyIncreasing(newIndices))
        }
      }
    )
  )

  private def isSubsequence(sub: String, str: String): Boolean = {
    var subIdx = 0
    var strIdx = 0
    while (subIdx < sub.length && strIdx < str.length) {
      if (sub.charAt(subIdx) == str.charAt(strIdx)) {
        subIdx += 1
      }
      strIdx += 1
    }
    subIdx == sub.length
  }

  private def isStrictlyIncreasing(v: Chunk[Int]): Boolean =
    v.length <= 1 || v.zip(v.drop(1)).forall { case (a, b) => a < b }
}
