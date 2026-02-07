package zio.blocks.mediatype

import scala.compiletime.testing.{Error, typeCheckErrors}

import zio.test.*

object InterpolatorSpec extends MediaTypeBaseSpec {
  def spec = suite("mediaType interpolator (Scala 3)")(
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
        val errs: List[Error] = typeCheckErrors("""
          import zio.blocks.mediatype._
          mediaType""
        """)
        assertTrue(errs.exists(_.message.contains("cannot be empty")))
      },
      test("missing slash produces clear error") {
        val errs: List[Error] = typeCheckErrors("""
          import zio.blocks.mediatype._
          mediaType"applicationjson"
        """)
        assertTrue(errs.exists(_.message.contains("must contain '/'")))
      },
      test("empty main type produces clear error") {
        val errs: List[Error] = typeCheckErrors("""
          import zio.blocks.mediatype._
          mediaType"/json"
        """)
        assertTrue(errs.exists(_.message.contains("main type cannot be empty")))
      },
      test("empty subtype produces clear error") {
        val errs: List[Error] = typeCheckErrors("""
          import zio.blocks.mediatype._
          mediaType"application/"
        """)
        assertTrue(errs.exists(_.message.contains("subtype cannot be empty")))
      }
    )
  )
}
