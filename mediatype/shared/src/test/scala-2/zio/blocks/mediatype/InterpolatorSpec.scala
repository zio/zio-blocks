package zio.blocks.mediatype

import zio.test._

object InterpolatorSpec extends MediaTypeBaseSpec {
  def spec = suite("mediaType interpolator (Scala 2)")(
    suite("valid media types")(
      test("parses simple type") {
        val mt = mediaType"application/json"
        assertTrue(mt.fullType == "application/json")
      },
      test("returns predefined instance for known types") {
        val mt = mediaType"application/json"
        assertTrue(mt eq MediaTypes.application.json)
      },
      test("parses type with parameters") {
        val mt = mediaType"text/html; charset=utf-8"
        assertTrue(
          mt.mainType == "text",
          mt.subType == "html",
          mt.parameters == Map("charset" -> "utf-8")
        )
      },
      test("creates new instance for unknown types") {
        val mt = mediaType"custom/unknown"
        assertTrue(
          mt.mainType == "custom",
          mt.subType == "unknown"
        )
      },
      test("handles wildcards") {
        val mt = mediaType"*/*"
        assertTrue(mt.fullType == "*/*")
      }
    ),
    suite("compile-time error messages")(
      test("empty string produces clear error") {
        typeCheck {
          """
          import zio.blocks.mediatype._
          mediaType""
          """
        }.map(result => assertTrue(result.isLeft && result.left.get.contains("cannot be empty")))
      },
      test("missing slash produces clear error") {
        typeCheck {
          """
          import zio.blocks.mediatype._
          mediaType"applicationjson"
          """
        }.map(result => assertTrue(result.isLeft && result.left.get.contains("must contain '/'")))
      },
      test("empty main type produces clear error") {
        typeCheck {
          """
          import zio.blocks.mediatype._
          mediaType"/json"
          """
        }.map(result => assertTrue(result.isLeft && result.left.get.contains("main type cannot be empty")))
      },
      test("empty subtype produces clear error") {
        typeCheck {
          """
          import zio.blocks.mediatype._
          mediaType"application/"
          """
        }.map(result => assertTrue(result.isLeft && result.left.get.contains("subtype cannot be empty")))
      },
      test("whitespace-only string produces clear error") {
        typeCheck {
          """
          import zio.blocks.mediatype._
          mediaType"   "
          """
        }.map(result => assertTrue(result.isLeft && result.left.get.contains("must contain '/'")))
      },
      test("only slash produces clear error") {
        typeCheck {
          """
          import zio.blocks.mediatype._
          mediaType"/"
          """
        }.map(result => assertTrue(result.isLeft && result.left.get.contains("main type cannot be empty")))
      },
      test("trailing whitespace in main type produces clear error") {
        typeCheck {
          """
          import zio.blocks.mediatype._
          mediaType"  /json"
          """
        }.map(result => assertTrue(result.isLeft && result.left.get.contains("main type cannot be empty")))
      },
      test("trailing whitespace in subtype produces clear error") {
        typeCheck {
          """
          import zio.blocks.mediatype._
          mediaType"text/  "
          """
        }.map(result => assertTrue(result.isLeft && result.left.get.contains("subtype cannot be empty")))
      },
      test("variable interpolation is rejected") {
        typeCheck {
          """
          import zio.blocks.mediatype._
          val x = "json"
          mediaType"application/$x"
          """
        }.map(result => assertTrue(result.isLeft && result.left.get.contains("does not support variable interpolation")))
      },
      test("multiple slashes handled correctly") {
        val mt = mediaType"text/vnd.api+json"
        assertTrue(
          mt.mainType == "text",
          mt.subType == "vnd.api+json"
        )
      }
    )
  )
}
