package zio.blocks.http

import zio.test._

object CharsetSpec extends HttpModelBaseSpec {
  def spec: Spec[TestEnvironment, Any] = suite("Charset")(
    suite("names")(
      test("all charsets have correct IANA names") {
        assertTrue(
          Charset.UTF8.name == "UTF-8",
          Charset.ASCII.name == "US-ASCII",
          Charset.ISO_8859_1.name == "ISO-8859-1",
          Charset.UTF16.name == "UTF-16",
          Charset.UTF16BE.name == "UTF-16BE",
          Charset.UTF16LE.name == "UTF-16LE"
        )
      }
    ),
    suite("toString")(
      test("returns the IANA name") {
        assertTrue(
          Charset.UTF8.toString == "UTF-8",
          Charset.ASCII.toString == "US-ASCII",
          Charset.ISO_8859_1.toString == "ISO-8859-1"
        )
      }
    ),
    suite("fromString")(
      test("resolves UTF-8") {
        assertTrue(Charset.fromString("UTF-8") == Some(Charset.UTF8))
      },
      test("resolves UTF-8 case-insensitively") {
        assertTrue(Charset.fromString("utf-8") == Some(Charset.UTF8))
      },
      test("resolves UTF8 alias") {
        assertTrue(Charset.fromString("utf8") == Some(Charset.UTF8))
      },
      test("resolves ASCII alias") {
        assertTrue(Charset.fromString("ascii") == Some(Charset.ASCII))
      },
      test("resolves US-ASCII") {
        assertTrue(Charset.fromString("US-ASCII") == Some(Charset.ASCII))
      },
      test("resolves latin1 alias") {
        assertTrue(Charset.fromString("latin1") == Some(Charset.ISO_8859_1))
      },
      test("resolves LATIN-1 alias") {
        assertTrue(Charset.fromString("LATIN-1") == Some(Charset.ISO_8859_1))
      },
      test("resolves ISO-8859-1") {
        assertTrue(Charset.fromString("ISO-8859-1") == Some(Charset.ISO_8859_1))
      },
      test("resolves UTF-16") {
        assertTrue(Charset.fromString("UTF-16") == Some(Charset.UTF16))
      },
      test("resolves UTF-16BE") {
        assertTrue(Charset.fromString("UTF-16BE") == Some(Charset.UTF16BE))
      },
      test("resolves UTF-16LE") {
        assertTrue(Charset.fromString("UTF-16LE") == Some(Charset.UTF16LE))
      },
      test("returns None for unknown charset") {
        assertTrue(Charset.fromString("unknown") == None)
      }
    ),
    suite("render")(
      test("returns the IANA name") {
        assertTrue(
          Charset.render(Charset.UTF8) == "UTF-8",
          Charset.render(Charset.ASCII) == "US-ASCII",
          Charset.render(Charset.ISO_8859_1) == "ISO-8859-1",
          Charset.render(Charset.UTF16) == "UTF-16",
          Charset.render(Charset.UTF16BE) == "UTF-16BE",
          Charset.render(Charset.UTF16LE) == "UTF-16LE"
        )
      }
    ),
    suite("values")(
      test("contains all 6 charsets") {
        assertTrue(Charset.values.length == 6)
      },
      test("contains each charset") {
        assertTrue(
          Charset.values.contains(Charset.UTF8),
          Charset.values.contains(Charset.ASCII),
          Charset.values.contains(Charset.ISO_8859_1),
          Charset.values.contains(Charset.UTF16),
          Charset.values.contains(Charset.UTF16BE),
          Charset.values.contains(Charset.UTF16LE)
        )
      }
    )
  )
}
