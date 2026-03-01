package zio.http.headers

import zio.test._
import zio.blocks.chunk.Chunk
import zio.blocks.mediatype.MediaTypes

object NegotiationHeadersSpec extends ZIOSpecDefault {
  def spec: Spec[TestEnvironment, Any] = suite("NegotiationHeaders")(
    suite("Accept")(
      test("parse single media type") {
        val result = Accept.parse("application/json")
        assertTrue(
          result.isRight,
          result.map(_.headerName) == Right("accept"),
          result.map(_.mediaRanges.length) == Right(1),
          result.map(_.mediaRanges(0).mediaType.fullType) == Right("application/json"),
          result.map(_.mediaRanges(0).quality) == Right(1.0)
        )
      },
      test("parse multiple media types") {
        val result = Accept.parse("text/html, application/json")
        assertTrue(
          result.isRight,
          result.map(_.mediaRanges.length) == Right(2),
          result.map(_.mediaRanges(0).mediaType.fullType) == Right("text/html"),
          result.map(_.mediaRanges(1).mediaType.fullType) == Right("application/json")
        )
      },
      test("parse with quality factor") {
        val result = Accept.parse("text/html;q=0.9")
        assertTrue(
          result.isRight,
          result.map(_.mediaRanges(0).quality) == Right(0.9)
        )
      },
      test("parse empty returns Left") {
        assertTrue(Accept.parse("").isLeft)
      },
      test("render single") {
        val h = Accept(Chunk(Accept.MediaRange(MediaTypes.application.`json`)))
        assertTrue(Accept.render(h) == "application/json")
      },
      test("render with quality") {
        val h = Accept(Chunk(Accept.MediaRange(MediaTypes.text.`html`, 0.8)))
        assertTrue(Accept.render(h) == "text/html;q=0.8")
      },
      test("render multiple") {
        val h = Accept(
          Chunk(
            Accept.MediaRange(MediaTypes.text.`html`),
            Accept.MediaRange(MediaTypes.application.`json`)
          )
        )
        assertTrue(Accept.render(h) == "text/html, application/json")
      },
      test("round-trip") {
        val original = Accept(Chunk(Accept.MediaRange(MediaTypes.application.`json`)))
        val rendered = Accept.render(original)
        val parsed   = Accept.parse(rendered)
        assertTrue(
          parsed.isRight,
          parsed.map(_.mediaRanges(0).mediaType.fullType) == Right("application/json")
        )
      }
    ),
    suite("AcceptEncoding")(
      test("parse gzip") {
        assertTrue(AcceptEncoding.parse("gzip") == Right(AcceptEncoding.GZip(None)))
      },
      test("parse deflate") {
        assertTrue(AcceptEncoding.parse("deflate") == Right(AcceptEncoding.Deflate(None)))
      },
      test("parse br") {
        assertTrue(AcceptEncoding.parse("br") == Right(AcceptEncoding.Br(None)))
      },
      test("parse compress") {
        assertTrue(AcceptEncoding.parse("compress") == Right(AcceptEncoding.Compress(None)))
      },
      test("parse identity") {
        assertTrue(AcceptEncoding.parse("identity") == Right(AcceptEncoding.Identity(None)))
      },
      test("parse * (any)") {
        assertTrue(AcceptEncoding.parse("*") == Right(AcceptEncoding.Any(None)))
      },
      test("parse with weight") {
        assertTrue(AcceptEncoding.parse("gzip;q=0.8") == Right(AcceptEncoding.GZip(Some(0.8))))
      },
      test("parse multiple") {
        val result = AcceptEncoding.parse("gzip, br;q=0.5")
        assertTrue(
          result == Right(
            AcceptEncoding.Multiple(
              Chunk(
                AcceptEncoding.GZip(None),
                AcceptEncoding.Br(Some(0.5))
              )
            )
          )
        )
      },
      test("render gzip") {
        assertTrue(AcceptEncoding.render(AcceptEncoding.GZip(None)) == "gzip")
      },
      test("render with weight") {
        assertTrue(AcceptEncoding.render(AcceptEncoding.Br(Some(0.7))) == "br;q=0.7")
      },
      test("render multiple") {
        val m = AcceptEncoding.Multiple(
          Chunk(
            AcceptEncoding.GZip(None),
            AcceptEncoding.Deflate(Some(0.5))
          )
        )
        assertTrue(AcceptEncoding.render(m) == "gzip, deflate;q=0.5")
      },
      test("round-trip") {
        val rendered = AcceptEncoding.render(AcceptEncoding.Deflate(None))
        assertTrue(AcceptEncoding.parse(rendered) == Right(AcceptEncoding.Deflate(None)))
      }
    ),
    suite("AcceptLanguage")(
      test("parse single") {
        val result = AcceptLanguage.parse("en-US")
        assertTrue(
          result.isRight,
          result.map(_.headerName) == Right("accept-language"),
          result.map(_.languages.length) == Right(1),
          result.map(_.languages(0).tag) == Right("en-US"),
          result.map(_.languages(0).quality) == Right(1.0)
        )
      },
      test("parse multiple with quality") {
        val result = AcceptLanguage.parse("en-US, fr;q=0.9, de;q=0.8")
        assertTrue(
          result.isRight,
          result.map(_.languages.length) == Right(3),
          result.map(_.languages(0).tag) == Right("en-US"),
          result.map(_.languages(1).tag) == Right("fr"),
          result.map(_.languages(1).quality) == Right(0.9),
          result.map(_.languages(2).tag) == Right("de"),
          result.map(_.languages(2).quality) == Right(0.8)
        )
      },
      test("parse empty returns Left") {
        assertTrue(AcceptLanguage.parse("").isLeft)
      },
      test("render single") {
        val h = AcceptLanguage(Chunk(AcceptLanguage.LanguageRange("en")))
        assertTrue(AcceptLanguage.render(h) == "en")
      },
      test("render with quality") {
        val h = AcceptLanguage(Chunk(AcceptLanguage.LanguageRange("fr", 0.5)))
        assertTrue(AcceptLanguage.render(h) == "fr;q=0.5")
      },
      test("round-trip") {
        val original = AcceptLanguage(
          Chunk(
            AcceptLanguage.LanguageRange("en-US"),
            AcceptLanguage.LanguageRange("fr", 0.9)
          )
        )
        val rendered = AcceptLanguage.render(original)
        val parsed   = AcceptLanguage.parse(rendered)
        assertTrue(
          parsed.isRight,
          parsed.map(_.languages(0).tag) == Right("en-US"),
          parsed.map(_.languages(1).tag) == Right("fr")
        )
      }
    ),
    suite("AcceptRanges")(
      test("parse bytes") {
        assertTrue(AcceptRanges.parse("bytes") == Right(AcceptRanges.Bytes))
      },
      test("parse none") {
        assertTrue(AcceptRanges.parse("none") == Right(AcceptRanges.None_))
      },
      test("parse invalid returns Left") {
        assertTrue(AcceptRanges.parse("invalid").isLeft)
      },
      test("render bytes") {
        assertTrue(AcceptRanges.render(AcceptRanges.Bytes) == "bytes")
      },
      test("render none") {
        assertTrue(AcceptRanges.render(AcceptRanges.None_) == "none")
      },
      test("headerName") {
        assertTrue(AcceptRanges.name == "accept-ranges")
      },
      test("round-trip") {
        val rendered = AcceptRanges.render(AcceptRanges.Bytes)
        assertTrue(AcceptRanges.parse(rendered) == Right(AcceptRanges.Bytes))
      }
    ),
    suite("AcceptPatch")(
      test("parse single media type") {
        val result = AcceptPatch.parse("application/json")
        assertTrue(
          result.isRight,
          result.map(_.headerName) == Right("accept-patch"),
          result.map(_.mediaTypes.length) == Right(1),
          result.map(_.mediaTypes(0).fullType) == Right("application/json")
        )
      },
      test("parse multiple media types") {
        val result = AcceptPatch.parse("application/json, text/plain")
        assertTrue(
          result.isRight,
          result.map(_.mediaTypes.length) == Right(2)
        )
      },
      test("parse empty returns Left") {
        assertTrue(AcceptPatch.parse("").isLeft)
      },
      test("render") {
        val h = AcceptPatch(Chunk(MediaTypes.application.`json`, MediaTypes.text.`plain`))
        assertTrue(AcceptPatch.render(h) == "application/json, text/plain")
      },
      test("round-trip") {
        val original = AcceptPatch(Chunk(MediaTypes.application.`json`))
        val rendered = AcceptPatch.render(original)
        val parsed   = AcceptPatch.parse(rendered)
        assertTrue(
          parsed.isRight,
          parsed.map(_.mediaTypes(0).fullType) == Right("application/json")
        )
      }
    )
  )
}
