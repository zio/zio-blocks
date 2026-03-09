package zio.http

import zio.test._
import zio.blocks.mediatype.MediaTypes

object ContentTypeSpec extends HttpModelBaseSpec {
  def spec: Spec[TestEnvironment, Any] = suite("ContentType")(
    suite("construction")(
      test("with MediaType only") {
        val ct = ContentType(MediaTypes.application.`json`)
        assertTrue(
          ct.mediaType == MediaTypes.application.`json`,
          ct.charset.isEmpty,
          ct.boundary.isEmpty
        )
      },
      test("with charset") {
        val ct = ContentType(MediaTypes.text.`plain`, charset = Some(Charset.UTF8))
        assertTrue(
          ct.mediaType == MediaTypes.text.`plain`,
          ct.charset == Some(Charset.UTF8),
          ct.boundary.isEmpty
        )
      },
      test("with boundary") {
        val ct = ContentType(MediaTypes.multipart.`form-data`, boundary = Some(Boundary("abc123")))
        assertTrue(
          ct.mediaType == MediaTypes.multipart.`form-data`,
          ct.boundary == Some(Boundary("abc123")),
          ct.charset.isEmpty
        )
      }
    ),
    suite("render")(
      test("with just media type") {
        val ct = ContentType(MediaTypes.application.`json`)
        assertTrue(ct.render == "application/json")
      },
      test("with charset") {
        val ct = ContentType(MediaTypes.text.`plain`, charset = Some(Charset.UTF8))
        assertTrue(ct.render == "text/plain; charset=UTF-8")
      },
      test("with boundary") {
        val ct = ContentType(MediaTypes.multipart.`form-data`, boundary = Some(Boundary("abc123")))
        assertTrue(ct.render == "multipart/form-data; boundary=abc123")
      },
      test("with both charset and boundary") {
        val ct = ContentType(MediaTypes.text.`plain`, boundary = Some(Boundary("abc123")), charset = Some(Charset.UTF8))
        assertTrue(ct.render == "text/plain; charset=UTF-8; boundary=abc123")
      }
    ),
    suite("parse")(
      test("application/json round-trip") {
        val result = ContentType.parse("application/json")
        assertTrue(result == Right(ContentType(MediaTypes.application.`json`)))
      },
      test("text/plain; charset=utf-8 extracts charset") {
        val result = ContentType.parse("text/plain; charset=utf-8")
        assertTrue(result == Right(ContentType(MediaTypes.text.`plain`, charset = Some(Charset.UTF8))))
      },
      test("multipart/form-data; boundary=abc123 extracts boundary") {
        val result = ContentType.parse("multipart/form-data; boundary=abc123")
        assertTrue(result == Right(ContentType(MediaTypes.multipart.`form-data`, boundary = Some(Boundary("abc123")))))
      },
      test("invalid returns Left") {
        val result = ContentType.parse("invalid")
        assertTrue(result.isLeft)
      },
      test("empty string returns Left") {
        val result = ContentType.parse("")
        assertTrue(result.isLeft)
      },
      test("charset is case-insensitive") {
        val result = ContentType.parse("text/html; charset=UTF-8")
        assertTrue(result == Right(ContentType(MediaTypes.text.`html`, charset = Some(Charset.UTF8))))
      },
      test("parameters with spaces around equals") {
        val result = ContentType.parse("text/plain; charset = utf-8")
        assertTrue(result == Right(ContentType(MediaTypes.text.`plain`, charset = Some(Charset.UTF8))))
      }
    ),
    suite("convenience constructors")(
      test("application/json") {
        assertTrue(ContentType.`application/json`.mediaType == MediaTypes.application.`json`)
      },
      test("text/plain") {
        assertTrue(ContentType.`text/plain`.mediaType == MediaTypes.text.`plain`)
      },
      test("text/html") {
        assertTrue(ContentType.`text/html`.mediaType == MediaTypes.text.`html`)
      },
      test("application/octet-stream") {
        assertTrue(ContentType.`application/octet-stream`.mediaType == MediaTypes.application.`octet-stream`)
      }
    )
  )
}
