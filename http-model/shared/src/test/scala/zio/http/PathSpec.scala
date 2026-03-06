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
    ),
    suite("addLeadingSlash")(
      test("adds leading slash to path without one") {
        val p = Path("foo/bar").addLeadingSlash
        assertTrue(p.hasLeadingSlash, p.segments == Chunk("foo", "bar"))
      },
      test("idempotent on path that already has leading slash") {
        val p = Path("/foo").addLeadingSlash
        assertTrue(p.hasLeadingSlash, p.segments == Chunk("foo"))
      }
    ),
    suite("dropLeadingSlash")(
      test("drops leading slash from path with one") {
        val p = Path("/foo/bar").dropLeadingSlash
        assertTrue(!p.hasLeadingSlash, p.segments == Chunk("foo", "bar"))
      },
      test("no-op on path without leading slash") {
        val p = Path("foo").dropLeadingSlash
        assertTrue(!p.hasLeadingSlash)
      }
    ),
    suite("addTrailingSlash")(
      test("adds trailing slash on non-empty segments") {
        val p = Path("/foo/bar").addTrailingSlash
        assertTrue(p.trailingSlash, p.segments == Chunk("foo", "bar"))
      },
      test("no-op on empty segments") {
        val p = Path.empty.addTrailingSlash
        assertTrue(!p.trailingSlash)
      }
    ),
    suite("dropTrailingSlash")(
      test("drops trailing slash") {
        val p = Path("/foo/").dropTrailingSlash
        assertTrue(!p.trailingSlash, p.segments == Chunk("foo"))
      }
    ),
    suite("isRoot")(
      test("true for root path") {
        assertTrue(Path.root.isRoot)
      },
      test("false for empty path") {
        assertTrue(!Path.empty.isRoot)
      },
      test("false for non-root path with segments") {
        assertTrue(!Path("/foo").isRoot)
      }
    ),
    suite("startsWith")(
      test("true when prefix matches") {
        assertTrue(Path("/foo/bar/baz").startsWith(Path("foo/bar")))
      },
      test("false when prefix does not match") {
        assertTrue(!Path("/foo/bar").startsWith(Path("baz")))
      },
      test("true for empty prefix") {
        assertTrue(Path("/foo").startsWith(Path.empty))
      }
    ),
    suite("drop")(
      test("drop 0 returns same segments") {
        val p = Path("/foo/bar").drop(0)
        assertTrue(p.segments == Chunk("foo", "bar"))
      },
      test("drop 1 removes first segment") {
        val p = Path("/foo/bar/baz").drop(1)
        assertTrue(p.segments == Chunk("bar", "baz"))
      },
      test("drop all returns empty segments") {
        val p = Path("/foo/bar").drop(2)
        assertTrue(p.segments.isEmpty)
      }
    ),
    suite("take")(
      test("take 0 returns empty segments") {
        val p = Path("/foo/bar").take(0)
        assertTrue(p.segments.isEmpty)
      },
      test("take 1 returns first segment") {
        val p = Path("/foo/bar/baz").take(1)
        assertTrue(p.segments == Chunk("foo"))
      },
      test("take more than length returns all") {
        val p = Path("/foo/bar").take(5)
        assertTrue(p.segments == Chunk("foo", "bar"))
      }
    ),
    suite("dropRight")(
      test("drops last n segments") {
        val p = Path("/foo/bar/baz").dropRight(1)
        assertTrue(p.segments == Chunk("foo", "bar"))
      }
    ),
    suite("reverse")(
      test("reverses segment order") {
        val p = Path("/a/b/c").reverse
        assertTrue(p.segments == Chunk("c", "b", "a"))
      }
    ),
    suite("initial")(
      test("returns path without last segment") {
        val p = Path("/foo/bar/baz").initial
        assertTrue(p.segments == Chunk("foo", "bar"))
      },
      test("returns same path when empty") {
        val p = Path.empty.initial
        assertTrue(p.segments.isEmpty)
      }
    ),
    suite("last")(
      test("returns last segment on non-empty") {
        assertTrue(Path("/foo/bar/baz").last == Some("baz"))
      },
      test("returns None on empty") {
        assertTrue(Path.empty.last == None)
      }
    )
  )
}
