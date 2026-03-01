package zio.http

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
    ),
    suite("render edge cases")(
      test("render without leading slash") {
        val p = Path(Chunk("foo", "bar"), hasLeadingSlash = false, trailingSlash = false)
        assertTrue(p.render == "foo/bar")
      },
      test("render with trailing slash") {
        val p = Path(Chunk("foo"), hasLeadingSlash = true, trailingSlash = true)
        assertTrue(p.render == "/foo/")
      },
      test("render empty segments no trailing slash") {
        val p = Path(Chunk.empty[String], hasLeadingSlash = false, trailingSlash = true)
        assertTrue(p.render == "")
      }
    ),
    suite("encode edge cases")(
      test("encode without leading slash") {
        val p = Path(Chunk("a", "b"), hasLeadingSlash = false, trailingSlash = false)
        assertTrue(p.encode == "a/b")
      },
      test("encode empty segments with trailing slash") {
        val p = Path(Chunk.empty[String], hasLeadingSlash = true, trailingSlash = true)
        assertTrue(p.encode == "/")
      }
    ),
    suite("fromEncoded edge cases")(
      test("fromEncoded with trailing slash") {
        val p = Path.fromEncoded("/foo/bar/")
        assertTrue(
          p.segments == Chunk("foo", "bar"),
          p.hasLeadingSlash,
          p.trailingSlash
        )
      },
      test("fromEncoded empty string") {
        assertTrue(Path.fromEncoded("") == Path.empty)
      },
      test("fromEncoded just slash") {
        val p = Path.fromEncoded("/")
        assertTrue(p.hasLeadingSlash, p.segments.isEmpty)
      },
      test("fromEncoded relative with encoding") {
        val p = Path.fromEncoded("a%20b/c%20d")
        assertTrue(
          p.segments == Chunk("a b", "c d"),
          !p.hasLeadingSlash
        )
      }
    ),
    suite("++ edge cases")(
      test("empty ++ empty") {
        val p = Path.empty ++ Path.empty
        assertTrue(p == Path.empty)
      },
      test("root ++ path preserves leading slash from left") {
        val p = Path.root ++ Path("bar")
        assertTrue(p.hasLeadingSlash, p.segments == Chunk("bar"))
      },
      test("no trailing from left when other has no trailing") {
        val p = Path("/foo/") ++ Path("bar")
        assertTrue(!p.trailingSlash)
      }
    ),
    suite("Path companion")(
      test("render static method delegates to instance") {
        val p = Path("/foo/bar")
        assertTrue(Path.render(p) == p.render)
      },
      test("apply with slash-only trailing") {
        val p = Path("/foo/bar/")
        assertTrue(p.trailingSlash, p.hasLeadingSlash, p.segments == Chunk("foo", "bar"))
      }
    )
  )
}
