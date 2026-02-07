package zio.blocks.mediatype

import zio.test._
import zio.test.Assertion._

object InterpolatorSpec extends MediaTypeBaseSpec {
  def spec = suite("mediaType interpolator")(
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
      },
      test("handles complex subtypes") {
        val mt = mediaType"text/vnd.api+json"
        assertTrue(
          mt.mainType == "text",
          mt.subType == "vnd.api+json"
        )
      }
    ),
    suite("compile-time error messages")(
      test("empty string") {
        typeCheck {
          """
          import zio.blocks.mediatype._
          mediaType""
          """
        }.map(assert(_)(isLeft(containsString("Invalid media type: cannot be empty"))))
      },
      test("missing slash") {
        typeCheck {
          """
          import zio.blocks.mediatype._
          mediaType"applicationjson"
          """
        }.map(assert(_)(isLeft(containsString("Invalid media type: must contain '/' separator"))))
      },
      test("empty main type") {
        typeCheck {
          """
          import zio.blocks.mediatype._
          mediaType"/json"
          """
        }.map(assert(_)(isLeft(containsString("Invalid media type: main type cannot be empty"))))
      },
      test("empty subtype") {
        typeCheck {
          """
          import zio.blocks.mediatype._
          mediaType"application/"
          """
        }.map(assert(_)(isLeft(containsString("Invalid media type: subtype cannot be empty"))))
      },
      test("whitespace-only string") {
        typeCheck {
          """
          import zio.blocks.mediatype._
          mediaType"   "
          """
        }.map(assert(_)(isLeft(containsString("Invalid media type: must contain '/' separator"))))
      },
      test("only slash") {
        typeCheck {
          """
          import zio.blocks.mediatype._
          mediaType"/"
          """
        }.map(assert(_)(isLeft(containsString("Invalid media type: main type cannot be empty"))))
      },
      test("whitespace before slash") {
        typeCheck {
          """
          import zio.blocks.mediatype._
          mediaType"  /json"
          """
        }.map(assert(_)(isLeft(containsString("Invalid media type: main type cannot be empty"))))
      },
      test("whitespace after slash") {
        typeCheck {
          """
          import zio.blocks.mediatype._
          mediaType"text/  "
          """
        }.map(assert(_)(isLeft(containsString("Invalid media type: subtype cannot be empty"))))
      },
      test("variable interpolation rejected") {
        typeCheck {
          """
          import zio.blocks.mediatype._
          val x = "json"
          mediaType"application/$x"
          """
        }.map(assert(_)(isLeft(containsString("mediaType interpolator does not support variable interpolation"))))
      }
    )
  )
}
