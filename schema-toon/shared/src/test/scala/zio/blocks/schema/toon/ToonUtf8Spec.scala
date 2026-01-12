package zio.blocks.schema.toon

import zio.test._

object ToonUtf8Spec extends ZIOSpecDefault {

  def parseString(input: String): String = {
    val codec  = ToonBinaryCodec.stringCodec
    val reader = new ToonReader(input.getBytes("UTF-8"), new Array[Char](1024), input.length, ToonReaderConfig)
    codec.decodeValue(reader, "")
  }

  def spec = suite("ToonUtf8Spec")(
    test("rejects unicode escapes (strict spec)") {
      // \u0041 = A
      assertTrue(scala.util.Try(parseString("\"\\u0041\"")).isFailure)
    },
    test("rejects unicode escape for Greek letter") {
      // \u03B1 = α (alpha)
      assertTrue(scala.util.Try(parseString("\"\\u03B1\"")).isFailure)
    },
    test("rejects unicode escape for Chinese character") {
      // \u4E2D = 中 (middle)
      assertTrue(scala.util.Try(parseString("\"\\u4E2D\"")).isFailure)
    },
    test("rejects multiple unicode escapes in sequence") {
      // \u0048\u0069 = Hi
      assertTrue(scala.util.Try(parseString("\"\\u0048\\u0069\"")).isFailure)
    },
    test("rejects unicode escapes mixed with regular text") {
      // Hello \u4E16\u754C
      assertTrue(scala.util.Try(parseString("\"Hello \\u4E16\\u754C\"")).isFailure)
    },
    test("rejects unicode escape at start") {
      assertTrue(scala.util.Try(parseString("\"\\u0048ello\"")).isFailure)
    },
    test("rejects unicode escape at end") {
      assertTrue(scala.util.Try(parseString("\"Hell\\u006F\"")).isFailure)
    },
    test("rejects unicode zero character") {
      assertTrue(scala.util.Try(parseString("\"\\u0000\"")).isFailure)
    },
    test("round-trip unicode through encoder and decoder") {
      val codec       = ToonBinaryCodec.stringCodec
      val testStrings = List(
        "A",        // ASCII
        "α",        // Greek
        "中",        // Chinese
        "Hello 世界", // Mixed
        "🚀",       // Emoji
        "café"      // Accented
      )

      val assertions = testStrings.map { str =>
        val encoded = codec.encodeToString(str)
        // If string contains special chars, it should be quoted.
        // We decode explicitly to check round trip
        val decoded = codec.decodeFromString(encoded)
        decoded match {
          case Right(d) => d == str
          case Left(_)  => false
        }
      }
      assertTrue(assertions.forall(identity))
    }
  )
}
