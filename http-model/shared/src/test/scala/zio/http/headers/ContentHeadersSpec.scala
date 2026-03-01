package zio.http.headers

import zio.test._
import zio.blocks.chunk.Chunk
import zio.blocks.mediatype.MediaTypes

object ContentHeadersSpec extends ZIOSpecDefault {
  def spec: Spec[TestEnvironment, Any] = suite("ContentHeaders")(
    suite("ContentType")(
      test("parse application/json") {
        val result = ContentType.parse("application/json")
        assertTrue(
          result.isRight,
          result.map(_.headerName) == Right("content-type"),
          result.map(_.renderedValue) == Right("application/json")
        )
      },
      test("parse with charset") {
        val result = ContentType.parse("text/plain; charset=UTF-8")
        assertTrue(
          result.isRight,
          result.map(_.renderedValue) == Right("text/plain; charset=UTF-8")
        )
      },
      test("parse empty returns Left") {
        assertTrue(ContentType.parse("").isLeft)
      },
      test("render round-trip") {
        val ct = ContentType(zio.http.ContentType(MediaTypes.application.`json`))
        assertTrue(ContentType.render(ct) == "application/json")
      }
    ),
    suite("ContentLength")(
      test("parse valid") {
        val result = ContentLength.parse("1024")
        assertTrue(
          result == Right(ContentLength(1024L)),
          result.map(_.headerName) == Right("content-length"),
          result.map(_.renderedValue) == Right("1024")
        )
      },
      test("parse zero") {
        assertTrue(ContentLength.parse("0") == Right(ContentLength(0L)))
      },
      test("parse negative returns Left") {
        assertTrue(ContentLength.parse("-1").isLeft)
      },
      test("parse non-numeric returns Left") {
        assertTrue(ContentLength.parse("abc").isLeft)
      },
      test("render") {
        assertTrue(ContentLength.render(ContentLength(42L)) == "42")
      }
    ),
    suite("ContentEncoding")(
      test("parse gzip") {
        assertTrue(ContentEncoding.parse("gzip") == Right(ContentEncoding.GZip))
      },
      test("parse deflate") {
        assertTrue(ContentEncoding.parse("deflate") == Right(ContentEncoding.Deflate))
      },
      test("parse br") {
        assertTrue(ContentEncoding.parse("br") == Right(ContentEncoding.Br))
      },
      test("parse compress") {
        assertTrue(ContentEncoding.parse("compress") == Right(ContentEncoding.Compress))
      },
      test("parse identity") {
        assertTrue(ContentEncoding.parse("identity") == Right(ContentEncoding.Identity))
      },
      test("parse multiple comma-separated") {
        val result = ContentEncoding.parse("gzip,br")
        assertTrue(
          result == Right(ContentEncoding.Multiple(Chunk(ContentEncoding.GZip, ContentEncoding.Br)))
        )
      },
      test("parse invalid returns Left") {
        assertTrue(ContentEncoding.parse("unknown-encoding").isLeft)
      },
      test("render gzip") {
        assertTrue(ContentEncoding.render(ContentEncoding.GZip) == "gzip")
      },
      test("render multiple") {
        val m = ContentEncoding.Multiple(Chunk(ContentEncoding.GZip, ContentEncoding.Br))
        assertTrue(ContentEncoding.render(m) == "gzip, br")
      },
      test("round-trip") {
        val rendered = ContentEncoding.render(ContentEncoding.Deflate)
        assertTrue(ContentEncoding.parse(rendered) == Right(ContentEncoding.Deflate))
      }
    ),
    suite("ContentDisposition")(
      test("parse attachment without filename") {
        assertTrue(
          ContentDisposition.parse("attachment") == Right(ContentDisposition.Attachment(None))
        )
      },
      test("parse attachment with filename") {
        assertTrue(
          ContentDisposition.parse("""attachment; filename="report.pdf"""") ==
            Right(ContentDisposition.Attachment(Some("report.pdf")))
        )
      },
      test("parse inline without filename") {
        assertTrue(
          ContentDisposition.parse("inline") == Right(ContentDisposition.Inline(None))
        )
      },
      test("parse inline with filename") {
        assertTrue(
          ContentDisposition.parse("""inline; filename="image.png"""") ==
            Right(ContentDisposition.Inline(Some("image.png")))
        )
      },
      test("parse form-data with name only") {
        assertTrue(
          ContentDisposition.parse("""form-data; name="field1"""") ==
            Right(ContentDisposition.FormData("field1", None))
        )
      },
      test("parse form-data with name and filename") {
        assertTrue(
          ContentDisposition.parse("""form-data; name="file"; filename="upload.txt"""") ==
            Right(ContentDisposition.FormData("file", Some("upload.txt")))
        )
      },
      test("parse invalid returns Left") {
        assertTrue(ContentDisposition.parse("invalid-value").isLeft)
      },
      test("render attachment") {
        val h = ContentDisposition.Attachment(Some("file.txt"))
        assertTrue(ContentDisposition.render(h) == """attachment; filename="file.txt"""")
      },
      test("render inline no filename") {
        assertTrue(ContentDisposition.render(ContentDisposition.Inline(None)) == "inline")
      },
      test("render form-data") {
        val h = ContentDisposition.FormData("field", Some("doc.pdf"))
        assertTrue(ContentDisposition.render(h) == """form-data; name="field"; filename="doc.pdf"""")
      },
      test("round-trip attachment") {
        val original = ContentDisposition.Attachment(Some("test.csv"))
        val rendered = ContentDisposition.render(original)
        assertTrue(ContentDisposition.parse(rendered) == Right(original))
      }
    ),
    suite("ContentLanguage")(
      test("parse and render") {
        val result = ContentLanguage.parse("en-US")
        assertTrue(
          result == Right(ContentLanguage("en-US")),
          result.map(_.headerName) == Right("content-language"),
          result.map(_.renderedValue) == Right("en-US")
        )
      }
    ),
    suite("ContentLocation")(
      test("parse and render") {
        val result = ContentLocation.parse("/documents/foo")
        assertTrue(
          result == Right(ContentLocation("/documents/foo")),
          result.map(_.headerName) == Right("content-location"),
          result.map(_.renderedValue) == Right("/documents/foo")
        )
      }
    ),
    suite("ContentRange")(
      test("parse bytes start-end/total") {
        val result = ContentRange.parse("bytes 0-499/1234")
        assertTrue(result == Right(ContentRange("bytes", Some((0L, 499L)), Some(1234L))))
      },
      test("parse bytes start-end/*") {
        val result = ContentRange.parse("bytes 0-499/*")
        assertTrue(result == Right(ContentRange("bytes", Some((0L, 499L)), None)))
      },
      test("parse bytes */total") {
        val result = ContentRange.parse("bytes */1234")
        assertTrue(result == Right(ContentRange("bytes", None, Some(1234L))))
      },
      test("parse invalid returns Left") {
        assertTrue(ContentRange.parse("invalid").isLeft)
      },
      test("render start-end/total") {
        val h = ContentRange("bytes", Some((0L, 499L)), Some(1234L))
        assertTrue(ContentRange.render(h) == "bytes 0-499/1234")
      },
      test("render start-end/*") {
        val h = ContentRange("bytes", Some((0L, 499L)), None)
        assertTrue(ContentRange.render(h) == "bytes 0-499/*")
      },
      test("render */total") {
        val h = ContentRange("bytes", None, Some(1234L))
        assertTrue(ContentRange.render(h) == "bytes */1234")
      },
      test("round-trip") {
        val original = ContentRange("bytes", Some((100L, 200L)), Some(500L))
        val rendered = ContentRange.render(original)
        assertTrue(ContentRange.parse(rendered) == Right(original))
      }
    ),
    suite("ContentSecurityPolicy")(
      test("parse and render") {
        val csp    = "default-src 'self'; script-src 'unsafe-inline'"
        val result = ContentSecurityPolicy.parse(csp)
        assertTrue(
          result == Right(ContentSecurityPolicy(csp)),
          result.map(_.headerName) == Right("content-security-policy")
        )
      }
    ),
    suite("ContentTransferEncoding")(
      test("parse 7bit") {
        assertTrue(ContentTransferEncoding.parse("7bit") == Right(ContentTransferEncoding.SevenBit))
      },
      test("parse 8bit") {
        assertTrue(ContentTransferEncoding.parse("8bit") == Right(ContentTransferEncoding.EightBit))
      },
      test("parse binary") {
        assertTrue(ContentTransferEncoding.parse("binary") == Right(ContentTransferEncoding.Binary))
      },
      test("parse quoted-printable") {
        assertTrue(
          ContentTransferEncoding.parse("quoted-printable") == Right(ContentTransferEncoding.QuotedPrintable)
        )
      },
      test("parse base64") {
        assertTrue(ContentTransferEncoding.parse("base64") == Right(ContentTransferEncoding.Base64))
      },
      test("parse case-insensitive") {
        assertTrue(ContentTransferEncoding.parse("Base64") == Right(ContentTransferEncoding.Base64))
      },
      test("parse invalid returns Left") {
        assertTrue(ContentTransferEncoding.parse("unknown").isLeft)
      },
      test("render round-trip") {
        val rendered = ContentTransferEncoding.render(ContentTransferEncoding.QuotedPrintable)
        assertTrue(ContentTransferEncoding.parse(rendered) == Right(ContentTransferEncoding.QuotedPrintable))
      }
    ),
    suite("ContentMd5")(
      test("parse and render") {
        val md5    = "Q2hlY2sgSW50ZWdyaXR5IQ=="
        val result = ContentMd5.parse(md5)
        assertTrue(
          result == Right(ContentMd5(md5)),
          result.map(_.headerName) == Right("content-md5")
        )
      }
    ),
    suite("ContentBase")(
      test("parse and render") {
        val uri    = "http://www.example.com/"
        val result = ContentBase.parse(uri)
        assertTrue(
          result == Right(ContentBase(uri)),
          result.map(_.headerName) == Right("content-base")
        )
      }
    )
  )
}
