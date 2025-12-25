package zio.blocks.schema

import zio.test._

object LCSAlgorithmSpec extends ZIOSpecDefault {
  def spec: Spec[TestEnvironment, Any] = suite("LCSAlgorithmSpec")(
    suite("DynamicPatch.StringOp.longestCommonSubsequence")(
      test("empty strings return empty LCS") {
        val result = DynamicPatch.StringOp.longestCommonSubsequence("", "")
        assertTrue(result.isEmpty)
      },
      test("one empty string returns empty LCS") {
        val result1 = DynamicPatch.StringOp.longestCommonSubsequence("", "abc")
        val result2 = DynamicPatch.StringOp.longestCommonSubsequence("abc", "")
        assertTrue(result1.isEmpty && result2.isEmpty)
      },
      test("identical strings return same string") {
        val input = "hello"
        val result = DynamicPatch.StringOp.longestCommonSubsequence(input, input)
        assertTrue(result == input)
      },
      test("no common characters return empty LCS") {
        val result = DynamicPatch.StringOp.longestCommonSubsequence("abc", "def")
        assertTrue(result.isEmpty)
      },
      test("partial overlap returns common subsequence") {
        val result = DynamicPatch.StringOp.longestCommonSubsequence("ABCD", "ACDF")
        assertTrue(result == "ACD")
      },
      test("complex overlap example 1") {
        val result = DynamicPatch.StringOp.longestCommonSubsequence("AGGTAB", "GXTXAYB")
        assertTrue(result == "GTAB")
      },
      test("complex overlap example 2") {
        val result = DynamicPatch.StringOp.longestCommonSubsequence("ABCDGH", "AEDFHR")
        assertTrue(result == "ADH")
      },
      test("one character strings with match") {
        val result = DynamicPatch.StringOp.longestCommonSubsequence("A", "A")
        assertTrue(result == "A")
      },
      test("one character strings without match") {
        val result = DynamicPatch.StringOp.longestCommonSubsequence("A", "B")
        assertTrue(result.isEmpty)
      },
      test("prefix match") {
        val result = DynamicPatch.StringOp.longestCommonSubsequence("ABC", "ABXYZ")
        assertTrue(result == "AB")
      },
      test("suffix match") {
        val result = DynamicPatch.StringOp.longestCommonSubsequence("XYZ", "ABCYZ")
        assertTrue(result == "YZ")
      },
      test("interleaved match") {
        val result = DynamicPatch.StringOp.longestCommonSubsequence("AXBYCZ", "ABC")
        assertTrue(result == "ABC")
      }
    ),
    suite("DynamicPatch.SeqOp.longestCommonSubsequence")(
      test("empty sequences return empty LCS") {
        val result = DynamicPatch.SeqOp.longestCommonSubsequence(Vector.empty, Vector.empty)
        assertTrue(result.isEmpty)
      },
      test("one empty sequence returns empty LCS") {
        val seq = Vector(DynamicValue.Primitive(PrimitiveValue.Int(1)))
        val result1 = DynamicPatch.SeqOp.longestCommonSubsequence(Vector.empty, seq)
        val result2 = DynamicPatch.SeqOp.longestCommonSubsequence(seq, Vector.empty)
        assertTrue(result1.isEmpty && result2.isEmpty)
      },
      test("identical sequences return same sequence") {
        val seq = Vector(
          DynamicValue.Primitive(PrimitiveValue.Int(1)),
          DynamicValue.Primitive(PrimitiveValue.Int(2)),
          DynamicValue.Primitive(PrimitiveValue.Int(3))
        )
        val result = DynamicPatch.SeqOp.longestCommonSubsequence(seq, seq)
        assertTrue(result == seq)
      },
      test("no common elements return empty LCS") {
        val seq1 = Vector(
          DynamicValue.Primitive(PrimitiveValue.Int(1)),
          DynamicValue.Primitive(PrimitiveValue.Int(2))
        )
        val seq2 = Vector(
          DynamicValue.Primitive(PrimitiveValue.Int(3)),
          DynamicValue.Primitive(PrimitiveValue.Int(4))
        )
        val result = DynamicPatch.SeqOp.longestCommonSubsequence(seq1, seq2)
        assertTrue(result.isEmpty)
      },
      test("partial overlap returns common subsequence") {
        val seq1 = Vector(
          DynamicValue.Primitive(PrimitiveValue.Int(1)),
          DynamicValue.Primitive(PrimitiveValue.Int(2)),
          DynamicValue.Primitive(PrimitiveValue.Int(3)),
          DynamicValue.Primitive(PrimitiveValue.Int(4))
        )
        val seq2 = Vector(
          DynamicValue.Primitive(PrimitiveValue.Int(1)),
          DynamicValue.Primitive(PrimitiveValue.Int(3)),
          DynamicValue.Primitive(PrimitiveValue.Int(4)),
          DynamicValue.Primitive(PrimitiveValue.Int(5))
        )
        val expected = Vector(
          DynamicValue.Primitive(PrimitiveValue.Int(1)),
          DynamicValue.Primitive(PrimitiveValue.Int(3)),
          DynamicValue.Primitive(PrimitiveValue.Int(4))
        )
        val result = DynamicPatch.SeqOp.longestCommonSubsequence(seq1, seq2)
        assertTrue(result == expected)
      },
      test("prefix match") {
        val seq1 = Vector(
          DynamicValue.Primitive(PrimitiveValue.Int(1)),
          DynamicValue.Primitive(PrimitiveValue.Int(2)),
          DynamicValue.Primitive(PrimitiveValue.Int(3))
        )
        val seq2 = Vector(
          DynamicValue.Primitive(PrimitiveValue.Int(1)),
          DynamicValue.Primitive(PrimitiveValue.Int(2)),
          DynamicValue.Primitive(PrimitiveValue.Int(5))
        )
        val expected = Vector(
          DynamicValue.Primitive(PrimitiveValue.Int(1)),
          DynamicValue.Primitive(PrimitiveValue.Int(2))
        )
        val result = DynamicPatch.SeqOp.longestCommonSubsequence(seq1, seq2)
        assertTrue(result == expected)
      },
      test("suffix match") {
        val seq1 = Vector(
          DynamicValue.Primitive(PrimitiveValue.Int(1)),
          DynamicValue.Primitive(PrimitiveValue.Int(2)),
          DynamicValue.Primitive(PrimitiveValue.Int(3))
        )
        val seq2 = Vector(
          DynamicValue.Primitive(PrimitiveValue.Int(5)),
          DynamicValue.Primitive(PrimitiveValue.Int(2)),
          DynamicValue.Primitive(PrimitiveValue.Int(3))
        )
        val expected = Vector(
          DynamicValue.Primitive(PrimitiveValue.Int(2)),
          DynamicValue.Primitive(PrimitiveValue.Int(3))
        )
        val result = DynamicPatch.SeqOp.longestCommonSubsequence(seq1, seq2)
        assertTrue(result == expected)
      },
      test("interleaved match") {
        val seq1 = Vector(
          DynamicValue.Primitive(PrimitiveValue.Int(1)),
          DynamicValue.Primitive(PrimitiveValue.Int(9)),
          DynamicValue.Primitive(PrimitiveValue.Int(2)),
          DynamicValue.Primitive(PrimitiveValue.Int(8)),
          DynamicValue.Primitive(PrimitiveValue.Int(3))
        )
        val seq2 = Vector(
          DynamicValue.Primitive(PrimitiveValue.Int(1)),
          DynamicValue.Primitive(PrimitiveValue.Int(2)),
          DynamicValue.Primitive(PrimitiveValue.Int(3))
        )
        val expected = Vector(
          DynamicValue.Primitive(PrimitiveValue.Int(1)),
          DynamicValue.Primitive(PrimitiveValue.Int(2)),
          DynamicValue.Primitive(PrimitiveValue.Int(3))
        )
        val result = DynamicPatch.SeqOp.longestCommonSubsequence(seq1, seq2)
        assertTrue(result == expected)
      },
      test("single element match") {
        val seq1 = Vector(DynamicValue.Primitive(PrimitiveValue.Int(1)))
        val seq2 = Vector(DynamicValue.Primitive(PrimitiveValue.Int(1)))
        val result = DynamicPatch.SeqOp.longestCommonSubsequence(seq1, seq2)
        assertTrue(result == seq1)
      },
      test("single element no match") {
        val seq1 = Vector(DynamicValue.Primitive(PrimitiveValue.Int(1)))
        val seq2 = Vector(DynamicValue.Primitive(PrimitiveValue.Int(2)))
        val result = DynamicPatch.SeqOp.longestCommonSubsequence(seq1, seq2)
        assertTrue(result.isEmpty)
      }
    ),
    suite("DynamicPatch.StringOp.diff roundtrip")(
      test("diff(a,b) applied to a equals b - identical strings") {
        val a = "hello"
        val b = "hello"
        val ops = DynamicPatch.StringOp.diff(a, b)
        val result = DynamicPatch.StringOp.apply(a, ops)
        assertTrue(result == Right(b))
      },
      test("diff(a,b) applied to a equals b - empty to non-empty") {
        val a = ""
        val b = "hello"
        val ops = DynamicPatch.StringOp.diff(a, b)
        val result = DynamicPatch.StringOp.apply(a, ops)
        assertTrue(result == Right(b))
      },
      test("diff(a,b) applied to a equals b - non-empty to empty") {
        val a = "hello"
        val b = ""
        val ops = DynamicPatch.StringOp.diff(a, b)
        val result = DynamicPatch.StringOp.apply(a, ops)
        assertTrue(result == Right(b))
      },
      test("diff(a,b) applied to a equals b - partial replacement") {
        val a = "hello"
        val b = "hallo"
        val ops = DynamicPatch.StringOp.diff(a, b)
        val result = DynamicPatch.StringOp.apply(a, ops)
        assertTrue(result == Right(b))
      },
      test("diff(a,b) applied to a equals b - insert middle") {
        val a = "ac"
        val b = "abc"
        val ops = DynamicPatch.StringOp.diff(a, b)
        val result = DynamicPatch.StringOp.apply(a, ops)
        assertTrue(result == Right(b))
      },
      test("diff(a,b) applied to a equals b - delete middle") {
        val a = "abc"
        val b = "ac"
        val ops = DynamicPatch.StringOp.diff(a, b)
        val result = DynamicPatch.StringOp.apply(a, ops)
        assertTrue(result == Right(b))
      },
      test("diff(a,b) applied to a equals b - complex transformation") {
        val a = "ABCDEF"
        val b = "AXCDEY"
        val ops = DynamicPatch.StringOp.diff(a, b)
        val result = DynamicPatch.StringOp.apply(a, ops)
        assertTrue(result == Right(b))
      },
      test("diff(a,b) applied to a equals b - no common chars") {
        val a = "abc"
        val b = "xyz"
        val ops = DynamicPatch.StringOp.diff(a, b)
        val result = DynamicPatch.StringOp.apply(a, ops)
        assertTrue(result == Right(b))
      }
    ),
    suite("DynamicPatch.SeqOp.diff roundtrip")(
      test("diff(a,b) applied to a equals b - identical sequences") {
        val a = Vector(
          DynamicValue.Primitive(PrimitiveValue.Int(1)),
          DynamicValue.Primitive(PrimitiveValue.Int(2))
        )
        val b = a
        val ops = DynamicPatch.SeqOp.diff(a, b)
        val result = applySeqOps(a, ops)
        assertTrue(result == Right(b))
      },
      test("diff(a,b) applied to a equals b - empty to non-empty") {
        val a = Vector.empty[DynamicValue]
        val b = Vector(
          DynamicValue.Primitive(PrimitiveValue.Int(1)),
          DynamicValue.Primitive(PrimitiveValue.Int(2))
        )
        val ops = DynamicPatch.SeqOp.diff(a, b)
        val result = applySeqOps(a, ops)
        assertTrue(result == Right(b))
      },
      test("diff(a,b) applied to a equals b - non-empty to empty") {
        val a = Vector(
          DynamicValue.Primitive(PrimitiveValue.Int(1)),
          DynamicValue.Primitive(PrimitiveValue.Int(2))
        )
        val b = Vector.empty[DynamicValue]
        val ops = DynamicPatch.SeqOp.diff(a, b)
        val result = applySeqOps(a, ops)
        assertTrue(result == Right(b))
      },
      test("diff(a,b) applied to a equals b - insert middle") {
        val a = Vector(
          DynamicValue.Primitive(PrimitiveValue.Int(1)),
          DynamicValue.Primitive(PrimitiveValue.Int(3))
        )
        val b = Vector(
          DynamicValue.Primitive(PrimitiveValue.Int(1)),
          DynamicValue.Primitive(PrimitiveValue.Int(2)),
          DynamicValue.Primitive(PrimitiveValue.Int(3))
        )
        val ops = DynamicPatch.SeqOp.diff(a, b)
        val result = applySeqOps(a, ops)
        assertTrue(result == Right(b))
      },
      test("diff(a,b) applied to a equals b - delete middle") {
        val a = Vector(
          DynamicValue.Primitive(PrimitiveValue.Int(1)),
          DynamicValue.Primitive(PrimitiveValue.Int(2)),
          DynamicValue.Primitive(PrimitiveValue.Int(3))
        )
        val b = Vector(
          DynamicValue.Primitive(PrimitiveValue.Int(1)),
          DynamicValue.Primitive(PrimitiveValue.Int(3))
        )
        val ops = DynamicPatch.SeqOp.diff(a, b)
        val result = applySeqOps(a, ops)
        assertTrue(result == Right(b))
      },
      test("diff(a,b) applied to a equals b - append") {
        val a = Vector(
          DynamicValue.Primitive(PrimitiveValue.Int(1))
        )
        val b = Vector(
          DynamicValue.Primitive(PrimitiveValue.Int(1)),
          DynamicValue.Primitive(PrimitiveValue.Int(2)),
          DynamicValue.Primitive(PrimitiveValue.Int(3))
        )
        val ops = DynamicPatch.SeqOp.diff(a, b)
        val result = applySeqOps(a, ops)
        assertTrue(result == Right(b))
      },
      test("diff(a,b) applied to a equals b - complex transformation") {
        val a = Vector(
          DynamicValue.Primitive(PrimitiveValue.Int(1)),
          DynamicValue.Primitive(PrimitiveValue.Int(2)),
          DynamicValue.Primitive(PrimitiveValue.Int(3)),
          DynamicValue.Primitive(PrimitiveValue.Int(4))
        )
        val b = Vector(
          DynamicValue.Primitive(PrimitiveValue.Int(1)),
          DynamicValue.Primitive(PrimitiveValue.Int(5)),
          DynamicValue.Primitive(PrimitiveValue.Int(3)),
          DynamicValue.Primitive(PrimitiveValue.Int(6))
        )
        val ops = DynamicPatch.SeqOp.diff(a, b)
        val result = applySeqOps(a, ops)
        assertTrue(result == Right(b))
      },
      test("diff(a,b) applied to a equals b - no common elements") {
        val a = Vector(
          DynamicValue.Primitive(PrimitiveValue.Int(1)),
          DynamicValue.Primitive(PrimitiveValue.Int(2))
        )
        val b = Vector(
          DynamicValue.Primitive(PrimitiveValue.Int(3)),
          DynamicValue.Primitive(PrimitiveValue.Int(4))
        )
        val ops = DynamicPatch.SeqOp.diff(a, b)
        val result = applySeqOps(a, ops)
        assertTrue(result == Right(b))
      }
    ),
    suite("DynamicPatch.MapOp.diff roundtrip")(
      test("diff(a,b) applied to a equals b - identical maps") {
        val a = Vector(
          DynamicValue.Primitive(PrimitiveValue.String("a")) -> DynamicValue.Primitive(PrimitiveValue.Int(1)),
          DynamicValue.Primitive(PrimitiveValue.String("b")) -> DynamicValue.Primitive(PrimitiveValue.Int(2))
        )
        val b = a
        val ops = DynamicPatch.MapOp.diff(a, b)
        val result = applyMapOps(a, ops)
        assertTrue(result == Right(b))
      },
      test("diff(a,b) applied to a equals b - empty to non-empty") {
        val a = Vector.empty[(DynamicValue, DynamicValue)]
        val b = Vector(
          DynamicValue.Primitive(PrimitiveValue.String("a")) -> DynamicValue.Primitive(PrimitiveValue.Int(1))
        )
        val ops = DynamicPatch.MapOp.diff(a, b)
        val result = applyMapOps(a, ops)
        assertTrue(result.isRight && result.toOption.get.toSet == b.toSet)
      },
      test("diff(a,b) applied to a equals b - non-empty to empty") {
        val a = Vector(
          DynamicValue.Primitive(PrimitiveValue.String("a")) -> DynamicValue.Primitive(PrimitiveValue.Int(1))
        )
        val b = Vector.empty[(DynamicValue, DynamicValue)]
        val ops = DynamicPatch.MapOp.diff(a, b)
        val result = applyMapOps(a, ops)
        assertTrue(result == Right(b))
      },
      test("diff(a,b) applied to a equals b - add new key") {
        val a = Vector(
          DynamicValue.Primitive(PrimitiveValue.String("a")) -> DynamicValue.Primitive(PrimitiveValue.Int(1))
        )
        val b = Vector(
          DynamicValue.Primitive(PrimitiveValue.String("a")) -> DynamicValue.Primitive(PrimitiveValue.Int(1)),
          DynamicValue.Primitive(PrimitiveValue.String("b")) -> DynamicValue.Primitive(PrimitiveValue.Int(2))
        )
        val ops = DynamicPatch.MapOp.diff(a, b)
        val result = applyMapOps(a, ops)
        assertTrue(result.isRight && result.toOption.get.toSet == b.toSet)
      },
      test("diff(a,b) applied to a equals b - remove key") {
        val a = Vector(
          DynamicValue.Primitive(PrimitiveValue.String("a")) -> DynamicValue.Primitive(PrimitiveValue.Int(1)),
          DynamicValue.Primitive(PrimitiveValue.String("b")) -> DynamicValue.Primitive(PrimitiveValue.Int(2))
        )
        val b = Vector(
          DynamicValue.Primitive(PrimitiveValue.String("a")) -> DynamicValue.Primitive(PrimitiveValue.Int(1))
        )
        val ops = DynamicPatch.MapOp.diff(a, b)
        val result = applyMapOps(a, ops)
        assertTrue(result.isRight && result.toOption.get.toSet == b.toSet)
      },
      test("diff(a,b) applied to a equals b - modify value") {
        val a = Vector(
          DynamicValue.Primitive(PrimitiveValue.String("a")) -> DynamicValue.Primitive(PrimitiveValue.Int(1))
        )
        val b = Vector(
          DynamicValue.Primitive(PrimitiveValue.String("a")) -> DynamicValue.Primitive(PrimitiveValue.Int(2))
        )
        val ops = DynamicPatch.MapOp.diff(a, b)
        val result = applyMapOps(a, ops)
        assertTrue(result.isRight && result.toOption.get.toSet == b.toSet)
      },
      test("diff(a,b) applied to a equals b - complex transformation") {
        val a = Vector(
          DynamicValue.Primitive(PrimitiveValue.String("a")) -> DynamicValue.Primitive(PrimitiveValue.Int(1)),
          DynamicValue.Primitive(PrimitiveValue.String("b")) -> DynamicValue.Primitive(PrimitiveValue.Int(2)),
          DynamicValue.Primitive(PrimitiveValue.String("c")) -> DynamicValue.Primitive(PrimitiveValue.Int(3))
        )
        val b = Vector(
          DynamicValue.Primitive(PrimitiveValue.String("a")) -> DynamicValue.Primitive(PrimitiveValue.Int(1)),
          DynamicValue.Primitive(PrimitiveValue.String("b")) -> DynamicValue.Primitive(PrimitiveValue.Int(99)),
          DynamicValue.Primitive(PrimitiveValue.String("d")) -> DynamicValue.Primitive(PrimitiveValue.Int(4))
        )
        val ops = DynamicPatch.MapOp.diff(a, b)
        val result = applyMapOps(a, ops)
        assertTrue(result.isRight && result.toOption.get.toSet == b.toSet)
      },
      test("diff(a,b) applied to a equals b - replace all keys") {
        val a = Vector(
          DynamicValue.Primitive(PrimitiveValue.String("a")) -> DynamicValue.Primitive(PrimitiveValue.Int(1)),
          DynamicValue.Primitive(PrimitiveValue.String("b")) -> DynamicValue.Primitive(PrimitiveValue.Int(2))
        )
        val b = Vector(
          DynamicValue.Primitive(PrimitiveValue.String("c")) -> DynamicValue.Primitive(PrimitiveValue.Int(3)),
          DynamicValue.Primitive(PrimitiveValue.String("d")) -> DynamicValue.Primitive(PrimitiveValue.Int(4))
        )
        val ops = DynamicPatch.MapOp.diff(a, b)
        val result = applyMapOps(a, ops)
        assertTrue(result.isRight && result.toOption.get.toSet == b.toSet)
      }
    )
  )

  // Helper function to apply sequence operations
  private def applySeqOps(
    seq: Vector[DynamicValue],
    ops: Vector[DynamicPatch.SeqOp]
  ): Either[SchemaError, Vector[DynamicValue]] = {
    var current = seq
    var idx = 0
    while (idx < ops.length) {
      ops(idx).applyTo(current, DynamicPatch.PatchMode.Strict) match {
        case Right(next) => current = next
        case left @ Left(_) => return left
      }
      idx += 1
    }
    Right(current)
  }

  // Helper function to apply map operations
  private def applyMapOps(
    map: Vector[(DynamicValue, DynamicValue)],
    ops: Vector[DynamicPatch.MapOp]
  ): Either[SchemaError, Vector[(DynamicValue, DynamicValue)]] = {
    var current = map
    var idx = 0
    while (idx < ops.length) {
      ops(idx).applyTo(current, DynamicPatch.PatchMode.Strict) match {
        case Right(next) => current = next
        case left @ Left(_) => return left
      }
      idx += 1
    }
    Right(current)
  }
}
