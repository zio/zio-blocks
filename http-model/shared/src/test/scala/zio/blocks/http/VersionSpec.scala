package zio.blocks.http

import zio.test._

object VersionSpec extends HttpModelBaseSpec {
  def spec: Spec[TestEnvironment, Any] = suite("Version")(
    suite("case objects")(
      test("Http/1.0 has correct major and minor") {
        assertTrue(Version.`Http/1.0`.major == 1, Version.`Http/1.0`.minor == 0)
      },
      test("Http/1.1 has correct major and minor") {
        assertTrue(Version.`Http/1.1`.major == 1, Version.`Http/1.1`.minor == 1)
      },
      test("Http/2.0 has correct major and minor") {
        assertTrue(Version.`Http/2.0`.major == 2, Version.`Http/2.0`.minor == 0)
      },
      test("Http/3.0 has correct major and minor") {
        assertTrue(Version.`Http/3.0`.major == 3, Version.`Http/3.0`.minor == 0)
      }
    ),
    suite("text")(
      test("returns correct HTTP version string") {
        assertTrue(
          Version.`Http/1.0`.text == "HTTP/1.0",
          Version.`Http/1.1`.text == "HTTP/1.1",
          Version.`Http/2.0`.text == "HTTP/2.0",
          Version.`Http/3.0`.text == "HTTP/3.0"
        )
      }
    ),
    suite("values")(
      test("contains all 4 versions") {
        assertTrue(Version.values.length == 4)
      }
    ),
    suite("fromString")(
      test("returns Some for valid version string") {
        assertTrue(Version.fromString("HTTP/1.1") == Some(Version.`Http/1.1`))
      },
      test("returns Some for shorthand HTTP/2") {
        assertTrue(Version.fromString("HTTP/2") == Some(Version.`Http/2.0`))
      },
      test("returns Some for shorthand HTTP/3") {
        assertTrue(Version.fromString("HTTP/3") == Some(Version.`Http/3.0`))
      },
      test("returns None for invalid string") {
        assertTrue(Version.fromString("INVALID") == None)
      },
      test("resolves all versions") {
        assertTrue(
          Version.fromString("HTTP/1.0") == Some(Version.`Http/1.0`),
          Version.fromString("HTTP/1.1") == Some(Version.`Http/1.1`),
          Version.fromString("HTTP/2.0") == Some(Version.`Http/2.0`),
          Version.fromString("HTTP/3.0") == Some(Version.`Http/3.0`)
        )
      }
    ),
    suite("render")(
      test("returns the version text") {
        assertTrue(
          Version.render(Version.`Http/1.0`) == "HTTP/1.0",
          Version.render(Version.`Http/1.1`) == "HTTP/1.1",
          Version.render(Version.`Http/2.0`) == "HTTP/2.0"
        )
      }
    ),
    suite("toString")(
      test("returns the version text") {
        assertTrue(
          Version.`Http/1.0`.toString == "HTTP/1.0",
          Version.`Http/1.1`.toString == "HTTP/1.1",
          Version.`Http/2.0`.toString == "HTTP/2.0"
        )
      }
    )
  )
}
