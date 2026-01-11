package zio.blocks.schema.toon

import zio.test._
import zio.blocks.schema.Schema

case class EdgeData(
  text: String,
  number: Int,
  flag: Boolean
)
object EdgeData {
  implicit val schema: Schema[EdgeData] = Schema.derived
}

/**
 * Edge case tests for various boundary conditions.
 */
object EdgeCaseSpec extends ZIOSpecDefault {
  def spec = suite("EdgeCase")(
    suite("Empty Values")(
      test("empty string") {
        val codec = Schema[String].derive(ToonFormat.deriver)
        assertTrue(codec.encodeToString("") == "\"\"")
      },
      test("empty list") {
        val codec = Schema[List[Int]].derive(ToonFormat.deriver)
        assertTrue(codec.encodeToString(List()) == "[0]: ")
      },
      test("empty map") {
        val codec = Schema[Map[String, Int]].derive(ToonFormat.deriver)
        assertTrue(codec.encodeToString(Map()) == "")
      },
      test("empty vector") {
        val codec = Schema[Vector[String]].derive(ToonFormat.deriver)
        assertTrue(codec.encodeToString(Vector()) == "[0]: ")
      }
    ),
    suite("Special Characters in Strings")(
      test("newline") {
        val codec   = Schema[String].derive(ToonFormat.deriver)
        val encoded = codec.encodeToString("line1\nline2")
        assertTrue(encoded == "\"line1\\nline2\"")
      },
      test("tab") {
        val codec   = Schema[String].derive(ToonFormat.deriver)
        val encoded = codec.encodeToString("col1\tcol2")
        assertTrue(encoded == "\"col1\\tcol2\"")
      },
      test("backslash") {
        val codec   = Schema[String].derive(ToonFormat.deriver)
        val encoded = codec.encodeToString("path\\to\\file")
        assertTrue(encoded == "\"path\\\\to\\\\file\"")
      },
      test("quotes") {
        val codec   = Schema[String].derive(ToonFormat.deriver)
        val encoded = codec.encodeToString("say \"hello\"")
        assertTrue(encoded == "\"say \\\"hello\\\"\"")
      },
      test("unicode") {
        val codec   = Schema[String].derive(ToonFormat.deriver)
        val encoded = codec.encodeToString("こんにちは")
        assertTrue(encoded.contains("こんにちは") || encoded.contains("\\u"))
      }
    ),
    suite("Numeric Edge Cases")(
      test("Int.MaxValue") {
        val codec = Schema[Int].derive(ToonFormat.deriver)
        assertTrue(codec.encodeToString(Int.MaxValue) == "2147483647")
      },
      test("Int.MinValue") {
        val codec = Schema[Int].derive(ToonFormat.deriver)
        assertTrue(codec.encodeToString(Int.MinValue) == "-2147483648")
      },
      test("Long.MaxValue") {
        val codec = Schema[Long].derive(ToonFormat.deriver)
        assertTrue(codec.encodeToString(Long.MaxValue) == "9223372036854775807")
      },
      test("Double.NaN") {
        val codec = Schema[Double].derive(ToonFormat.deriver)
        assertTrue(codec.encodeToString(Double.NaN) == "null")
      },
      test("Double.PositiveInfinity") {
        val codec = Schema[Double].derive(ToonFormat.deriver)
        assertTrue(codec.encodeToString(Double.PositiveInfinity) == "null")
      },
      test("Double.NegativeInfinity") {
        val codec = Schema[Double].derive(ToonFormat.deriver)
        assertTrue(codec.encodeToString(Double.NegativeInfinity) == "null")
      },
      test("negative zero normalized") {
        val codec = Schema[Double].derive(ToonFormat.deriver)
        assertTrue(codec.encodeToString(-0.0) == "0.0")
      },
      test("very small double") {
        val codec   = Schema[Double].derive(ToonFormat.deriver)
        val small   = 0.000000001
        val encoded = codec.encodeToString(small)
        assertTrue(encoded.contains("0.") || encoded.contains("E-"))
      },
      test("very large BigDecimal") {
        val codec   = Schema[BigDecimal].derive(ToonFormat.deriver)
        val big     = BigDecimal("12345678901234567890.12345678901234567890")
        val encoded = codec.encodeToString(big)
        assertTrue(!encoded.contains("E") && encoded.contains("12345678901234567890"))
      }
    ),
    suite("Whitespace Handling")(
      test("string with only spaces") {
        val codec   = Schema[String].derive(ToonFormat.deriver)
        val encoded = codec.encodeToString("   ")
        assertTrue(encoded == "\"   \"")
      },
      test("string starting with space") {
        val codec   = Schema[String].derive(ToonFormat.deriver)
        val encoded = codec.encodeToString(" hello")
        assertTrue(encoded == "\" hello\"")
      },
      test("string ending with space") {
        val codec   = Schema[String].derive(ToonFormat.deriver)
        val encoded = codec.encodeToString("hello ")
        assertTrue(encoded == "\"hello \"")
      }
    ),
    suite("TOON Special Characters")(
      test("string with colon") {
        val codec   = Schema[String].derive(ToonFormat.deriver)
        val encoded = codec.encodeToString("key:value")
        assertTrue(encoded == "\"key:value\"")
      },
      test("string with comma") {
        val codec   = Schema[String].derive(ToonFormat.deriver)
        val encoded = codec.encodeToString("a,b,c")
        assertTrue(encoded == "\"a,b,c\"")
      },
      test("string with brackets") {
        val codec   = Schema[String].derive(ToonFormat.deriver)
        val encoded = codec.encodeToString("[array]")
        // Brackets in string values are currently not quoted (they're only special in array syntax)
        assertTrue(encoded == "[array]" || encoded == "\"[array]\"")
      }
    )
  )
}
