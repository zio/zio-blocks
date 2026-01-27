package zio.blocks.schema

import zio.test._

/**
 * Coverage tests for PathParser to exercise all parse branches.
 */
object PathParserCoverageSpec extends SchemaBaseSpec {

  def spec: Spec[TestEnvironment, Any] = suite("PathParserCoverageSpec")(
    basicPathTests,
    indexPathTests,
    mapPathTests,
    variantPathTests,
    errorPathTests,
    escapedStringTests
  )

  val basicPathTests = suite("Basic path parsing")(
    test("Parse empty path returns empty") {
      val result = PathParser.parse("")
      assertTrue(result == Right(Vector.empty))
    },
    test("Parse simple field") {
      val result = PathParser.parse(".name")
      assertTrue(result.isRight)
    },
    test("Parse field without dot") {
      val result = PathParser.parse("username")
      assertTrue(result.isRight)
    },
    test("Parse field with underscore") {
      val result = PathParser.parse(".first_name")
      assertTrue(result.isRight)
    },
    test("Parse nested fields") {
      val result = PathParser.parse(".user.profile.email")
      assertTrue(result.isRight)
    }
  )

  val indexPathTests = suite("Index path parsing")(
    test("Parse single index") {
      val result = PathParser.parse("[0]")
      assertTrue(result.isRight)
    },
    test("Parse multiple indices") {
      val result = PathParser.parse("[0, 1, 2]")
      assertTrue(result.isRight)
    },
    test("Parse index range") {
      val result = PathParser.parse("[1:5]")
      assertTrue(result.isRight)
    },
    test("Parse elements with asterisk") {
      val result = PathParser.parse("[*]")
      assertTrue(result.isRight)
    },
    test("Parse elements with colon asterisk") {
      val result = PathParser.parse("[:*]")
      assertTrue(result.isRight)
    },
    test("Parse elements with asterisk colon") {
      val result = PathParser.parse("[*:]")
      assertTrue(result.isRight)
    }
  )

  val mapPathTests = suite("Map path parsing")(
    test("Parse map with string key") {
      val result = PathParser.parse("{\"key\"}")
      assertTrue(result.isRight)
    },
    test("Parse map with int key") {
      val result = PathParser.parse("{42}")
      assertTrue(result.isRight)
    },
    test("Parse map with negative int key") {
      val result = PathParser.parse("{-10}")
      assertTrue(result.isRight)
    },
    test("Parse map with boolean true key") {
      val result = PathParser.parse("{true}")
      assertTrue(result.isRight)
    },
    test("Parse map with boolean false key") {
      val result = PathParser.parse("{false}")
      assertTrue(result.isRight)
    },
    test("Parse map with char key") {
      val result = PathParser.parse("{'a'}")
      assertTrue(result.isRight)
    },
    test("Parse map values with asterisk") {
      val result = PathParser.parse("{*}")
      assertTrue(result.isRight)
    },
    test("Parse map keys with asterisk colon") {
      val result = PathParser.parse("{*:}")
      assertTrue(result.isRight)
    },
    test("Parse map values with colon asterisk") {
      val result = PathParser.parse("{:*}")
      assertTrue(result.isRight)
    },
    test("Parse multiple map keys") {
      val result = PathParser.parse("{\"k1\", \"k2\", \"k3\"}")
      assertTrue(result.isRight)
    }
  )

  val variantPathTests = suite("Variant path parsing")(
    test("Parse variant case") {
      val result = PathParser.parse("<SomeCase>")
      assertTrue(result.isRight)
    },
    test("Parse variant with underscore") {
      val result = PathParser.parse("<Some_Case>")
      assertTrue(result.isRight)
    },
    test("Parse field then variant") {
      val result = PathParser.parse(".option<Some>.value")
      assertTrue(result.isRight)
    }
  )

  val errorPathTests = suite("Error path handling")(
    test("Invalid identifier after dot") {
      val result = PathParser.parse(".")
      assertTrue(result.isLeft)
    },
    test("Invalid character") {
      val result = PathParser.parse("@invalid")
      assertTrue(result.isLeft)
    },
    test("Unterminated index") {
      val result = PathParser.parse("[1")
      assertTrue(result.isLeft)
    },
    test("Unterminated map") {
      val result = PathParser.parse("{\"key\"")
      assertTrue(result.isLeft)
    },
    test("Unterminated variant") {
      val result = PathParser.parse("<Case")
      assertTrue(result.isLeft)
    },
    test("Invalid map key identifier") {
      val result = PathParser.parse("{someIdent}")
      assertTrue(result.isLeft)
    },
    test("Unterminated string") {
      val result = PathParser.parse("{\"unterminated}")
      assertTrue(result.isLeft)
    },
    test("Empty char literal") {
      val result = PathParser.parse("{''}")
      assertTrue(result.isLeft)
    },
    test("Multi-char literal") {
      val result = PathParser.parse("{'ab'}")
      assertTrue(result.isLeft)
    },
    test("Integer overflow") {
      val result = PathParser.parse("[99999999999]")
      assertTrue(result.isLeft)
    }
  )

  val escapedStringTests = suite("Escaped string parsing")(
    test("Parse string with escaped quote") {
      val result = PathParser.parse("{\"hello\\\"world\"}")
      assertTrue(result.isRight)
    },
    test("Parse string with escaped backslash") {
      val result = PathParser.parse("{\"path\\\\to\\\\file\"}")
      assertTrue(result.isRight)
    },
    test("Parse string with escaped newline") {
      val result = PathParser.parse("{\"line1\\nline2\"}")
      assertTrue(result.isRight)
    },
    test("Parse string with escaped tab") {
      val result = PathParser.parse("{\"col1\\tcol2\"}")
      assertTrue(result.isRight)
    },
    test("Parse string with escaped return") {
      val result = PathParser.parse("{\"text\\rmore\"}")
      assertTrue(result.isRight)
    },
    test("Invalid escape sequence") {
      val result = PathParser.parse("{\"invalid\\x\"}")
      assertTrue(result.isLeft)
    },
    test("Parse char with escaped quote") {
      val result = PathParser.parse("{'\\''}")
      assertTrue(result.isRight)
    },
    test("Parse char with escaped backslash") {
      val result = PathParser.parse("{'\\\\'}")
      assertTrue(result.isRight)
    },
    test("Parse char with escaped newline") {
      val result = PathParser.parse("{'\\n'}")
      assertTrue(result.isRight)
    },
    test("Parse char with escaped tab") {
      val result = PathParser.parse("{'\\t'}")
      assertTrue(result.isRight)
    },
    test("Parse char with escaped return") {
      val result = PathParser.parse("{'\\r'}")
      assertTrue(result.isRight)
    },
    test("Char with invalid escape") {
      val result = PathParser.parse("{'\\x'}")
      assertTrue(result.isLeft)
    }
  )
}
