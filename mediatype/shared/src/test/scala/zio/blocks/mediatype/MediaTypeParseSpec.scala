package zio.blocks.mediatype

import zio.test._

object MediaTypeParseSpec extends MediaTypeBaseSpec {
  def spec = suite("MediaType.parse")(
    suite("valid media types")(
      test("parses simple type") {
        val result = MediaType.parse("application/json")
        assertTrue(result.isRight)
        assertTrue(result.toOption.get.fullType == "application/json")
      },
      test("returns predefined instance for known types") {
        val result = MediaType.parse("application/json")
        assertTrue(result.toOption.get `eq` MediaTypes.application.json)
      },
      test("parses type with parameters") {
        val result = MediaType.parse("text/html; charset=utf-8")
        assertTrue(
          result.isRight,
          result.toOption.get.mainType == "text",
          result.toOption.get.subType == "html",
          result.toOption.get.parameters == Map("charset" -> "utf-8")
        )
      },
      test("parses multiple parameters") {
        val result = MediaType.parse("multipart/form-data; boundary=xxx; charset=utf-8")
        assertTrue(
          result.isRight,
          result.toOption.get.parameters == Map("boundary" -> "xxx", "charset" -> "utf-8")
        )
      },
      test("ignores malformed parameters without equals sign") {
        val result = MediaType.parse("text/html; charset=utf-8; malformed")
        assertTrue(
          result.isRight,
          result.toOption.get.parameters == Map("charset" -> "utf-8")
        )
      },
      test("creates new instance for unknown types") {
        val result = MediaType.parse("custom/unknown")
        assertTrue(
          result.isRight,
          result.toOption.get.mainType == "custom",
          result.toOption.get.subType == "unknown"
        )
      },
      test("handles wildcards") {
        val result = MediaType.parse("*/*")
        assertTrue(
          result.isRight,
          result.toOption.get.fullType == "*/*"
        )
      },
      test("is case insensitive for lookup") {
        val result1 = MediaType.parse("APPLICATION/JSON")
        val result2 = MediaType.parse("application/json")
        assertTrue(
          result1.isRight,
          result2.isRight
        )
      }
    ),
    suite("invalid media types")(
      test("rejects empty string") {
        val result = MediaType.parse("")
        assertTrue(result.isLeft)
      },
      test("rejects missing slash") {
        val result = MediaType.parse("applicationjson")
        assertTrue(result.isLeft)
      },
      test("rejects empty main type") {
        val result = MediaType.parse("/json")
        assertTrue(result.isLeft)
      },
      test("rejects empty sub type") {
        val result = MediaType.parse("application/")
        assertTrue(result.isLeft)
      }
    ),
    suite("unsafeFromString")(
      test("returns MediaType for valid input") {
        val mt = MediaType.unsafeFromString("application/json")
        assertTrue(mt.fullType == "application/json")
      },
      test("throws for invalid input") {
        val result = scala.util.Try(MediaType.unsafeFromString("invalid"))
        assertTrue(result.isFailure)
      }
    )
  )
}
