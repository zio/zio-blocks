package zio.blocks.mediatype

import zio.test._
import zio.test.Assertion._

object InterpolatorSpec extends MediaTypeBaseSpec {
  def spec = suite("mt interpolator")(
    suite("valid media types")(
      test("parses simple type") {
        val result = mt"application/json"
        assertTrue(result.fullType == "application/json")
      },
      test("returns predefined instance for known types") {
        val result = mt"application/json"
        assertTrue(result eq MediaTypes.application.json)
      },
      test("parses type with parameters") {
        val result = mt"text/html; charset=utf-8"
        assertTrue(
          result.mainType == "text",
          result.subType == "html",
          result.parameters == Map("charset" -> "utf-8")
        )
      },
      test("creates new instance for unknown types") {
        val result = mt"custom/unknown"
        assertTrue(
          result.mainType == "custom",
          result.subType == "unknown"
        )
      },
      test("handles wildcards") {
        val result = mt"*/*"
        assertTrue(result.fullType == "*/*")
      },
      test("handles complex subtypes") {
        val result = mt"text/vnd.api+json"
        assertTrue(
          result.mainType == "text",
          result.subType == "vnd.api+json"
        )
      }
    ),
    suite("compile-time error messages")(
      test("empty string") {
        typeCheck {
          """
          import zio.blocks.mediatype._
          mt""
          """
        }.map(assert(_)(isLeft(containsString("Invalid media type: cannot be empty"))))
      },
      test("missing slash") {
        typeCheck {
          """
          import zio.blocks.mediatype._
          mt"applicationjson"
          """
        }.map(assert(_)(isLeft(containsString("Invalid media type: must contain '/' separator"))))
      },
      test("empty main type") {
        typeCheck {
          """
          import zio.blocks.mediatype._
          mt"/json"
          """
        }.map(assert(_)(isLeft(containsString("Invalid media type: main type cannot be empty"))))
      },
      test("empty subtype") {
        typeCheck {
          """
          import zio.blocks.mediatype._
          mt"application/"
          """
        }.map(assert(_)(isLeft(containsString("Invalid media type: subtype cannot be empty"))))
      },
      test("whitespace-only string") {
        typeCheck {
          """
          import zio.blocks.mediatype._
          mt"   "
          """
        }.map(assert(_)(isLeft(containsString("Invalid media type: must contain '/' separator"))))
      },
      test("only slash") {
        typeCheck {
          """
          import zio.blocks.mediatype._
          mt"/"
          """
        }.map(assert(_)(isLeft(containsString("Invalid media type: main type cannot be empty"))))
      },
      test("whitespace before slash") {
        typeCheck {
          """
          import zio.blocks.mediatype._
          mt"  /json"
          """
        }.map(assert(_)(isLeft(containsString("Invalid media type: main type cannot be empty"))))
      },
      test("whitespace after slash") {
        typeCheck {
          """
          import zio.blocks.mediatype._
          mt"text/  "
          """
        }.map(assert(_)(isLeft(containsString("Invalid media type: subtype cannot be empty"))))
      },
      test("variable interpolation rejected") {
        typeCheck {
          """
          import zio.blocks.mediatype._
          val x = "json"
          mt"application/$x"
          """
        }.map(assert(_)(isLeft(containsString("mt interpolator does not support variable interpolation"))))
      }
    )
  )
}
