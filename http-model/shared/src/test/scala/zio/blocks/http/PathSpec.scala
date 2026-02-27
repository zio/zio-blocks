package zio.blocks.http

import zio.test._
import zio.blocks.chunk.Chunk

object PathSpec extends HttpModelBaseSpec {
  def spec: Spec[TestEnvironment, Any] = suite("Path")(
    suite("empty and root")(
      test("empty path is empty") {
        assertTrue(Path.empty.isEmpty)
      },
      test("empty path has no leading slash") {
        assertTrue(!Path.empty.hasLeadingSlash)
      },
      test("root has leading slash") {
        assertTrue(Path.root.hasLeadingSlash)
      },
      test("root is not empty") {
        assertTrue(Path.root.nonEmpty)
      },
      test("empty != root") {
        assertTrue(Path.empty != Path.root)
      }
    ),
    suite("apply (from decoded string)")(
      test("parses /users/john doe") {
        val p = Path("/users/john doe")
        assertTrue(
          p.segments == Chunk("users", "john doe"),
          p.hasLeadingSlash,
          !p.trailingSlash
        )
      },
      test("parses relative path") {
        val p = Path("foo/bar")
        assertTrue(
          p.segments == Chunk("foo", "bar"),
          !p.hasLeadingSlash
        )
      },
      test("preserves trailing slash") {
        val p = Path("/foo/")
        assertTrue(
          p.segments == Chunk("foo"),
          p.hasLeadingSlash,
          p.trailingSlash
        )
      },
      test("leading slash without trailing") {
        val p = Path("/foo")
        assertTrue(
          p.hasLeadingSlash,
          !p.trailingSlash
        )
      },
      test("empty string returns empty path") {
        assertTrue(Path("") == Path.empty)
      },
      test("just slash returns root") {
        val p = Path("/")
        assertTrue(
          p.hasLeadingSlash,
          p.segments.isEmpty
        )
      }
    ),
    suite("fromEncoded")(
      test("decodes percent-encoded segments") {
        val p = Path.fromEncoded("/users/john%20doe")
        assertTrue(p.segments == Chunk("users", "john doe"))
      },
      test("decoded segments stored internally") {
        val p = Path.fromEncoded("/users/john%20doe")
        assertTrue(p.segments(1) == "john doe")
      },
      test("UTF-8 decoding: café") {
        val p = Path.fromEncoded("/caf%C3%A9")
        assertTrue(p.segments == Chunk("café"))
      }
    ),
    suite("encode")(
      test("encodes segments with percent encoding") {
        val p = Path(Chunk("users", "john doe"), hasLeadingSlash = true, trailingSlash = false)
        assertTrue(p.encode == "/users/john%20doe")
      },
      test("preserves trailing slash") {
        val p = Path(Chunk("foo"), hasLeadingSlash = true, trailingSlash = true)
        assertTrue(p.encode == "/foo/")
      }
    ),
    suite("render")(
      test("renders decoded segments") {
        val p = Path(Chunk("users", "john doe"), hasLeadingSlash = true, trailingSlash = false)
        assertTrue(p.render == "/users/john doe")
      }
    ),
    suite("round-trip")(
      test("fromEncoded(encode) equals original") {
        val original     = Path(Chunk("a", "b c", "d/e"), hasLeadingSlash = true, trailingSlash = false)
        val roundTripped = Path.fromEncoded(original.encode)
        assertTrue(roundTripped == original)
      },
      test("simple path round-trip") {
        val original     = Path("/foo/bar/baz")
        val roundTripped = Path.fromEncoded(original.encode)
        assertTrue(roundTripped == original)
      }
    ),
    suite("/ operator")(
      test("appends segment") {
        val p = Path("/foo") / "bar"
        assertTrue(
          p.segments == Chunk("foo", "bar"),
          p.hasLeadingSlash
        )
      },
      test("clears trailing slash") {
        val p = Path("/foo/") / "bar"
        assertTrue(!p.trailingSlash)
      }
    ),
    suite("++ operator")(
      test("concatenates paths") {
        val p = Path("/foo") ++ Path("bar/baz")
        assertTrue(
          p.segments == Chunk("foo", "bar", "baz"),
          p.hasLeadingSlash
        )
      },
      test("takes trailing slash from other") {
        val p = Path("/foo") ++ Path("bar/")
        assertTrue(p.trailingSlash)
      }
    ),
    suite("length")(
      test("returns number of segments") {
        assertTrue(
          Path.empty.length == 0,
          Path.root.length == 0,
          Path("/foo/bar").length == 2
        )
      }
    ),
    suite("toString")(
      test("uses render") {
        val p = Path("/foo/bar")
        assertTrue(p.toString == "/foo/bar")
      }
    )
  )
}
