package zio.http.headers

import zio.test._
import zio.blocks.chunk.Chunk

object CachingHeadersSpec extends ZIOSpecDefault {
  def spec: Spec[TestEnvironment, Any] = suite("CachingHeaders")(
    suite("CacheControl")(
      test("parse no-cache") {
        assertTrue(CacheControl.parse("no-cache") == Right(CacheControl.NoCache))
      },
      test("parse no-store") {
        assertTrue(CacheControl.parse("no-store") == Right(CacheControl.NoStore))
      },
      test("parse public") {
        assertTrue(CacheControl.parse("public") == Right(CacheControl.Public))
      },
      test("parse private") {
        assertTrue(CacheControl.parse("private") == Right(CacheControl.Private))
      },
      test("parse must-revalidate") {
        assertTrue(CacheControl.parse("must-revalidate") == Right(CacheControl.MustRevalidate))
      },
      test("parse proxy-revalidate") {
        assertTrue(CacheControl.parse("proxy-revalidate") == Right(CacheControl.ProxyRevalidate))
      },
      test("parse immutable") {
        assertTrue(CacheControl.parse("immutable") == Right(CacheControl.Immutable))
      },
      test("parse only-if-cached") {
        assertTrue(CacheControl.parse("only-if-cached") == Right(CacheControl.OnlyIfCached))
      },
      test("parse must-understand") {
        assertTrue(CacheControl.parse("must-understand") == Right(CacheControl.MustUnderstand))
      },
      test("parse no-transform") {
        assertTrue(CacheControl.parse("no-transform") == Right(CacheControl.NoTransform))
      },
      test("parse max-age") {
        assertTrue(CacheControl.parse("max-age=3600") == Right(CacheControl.MaxAge(3600L)))
      },
      test("parse s-maxage") {
        assertTrue(CacheControl.parse("s-maxage=600") == Right(CacheControl.SMaxAge(600L)))
      },
      test("parse max-stale without value") {
        assertTrue(CacheControl.parse("max-stale") == Right(CacheControl.MaxStale(None)))
      },
      test("parse max-stale with value") {
        assertTrue(CacheControl.parse("max-stale=100") == Right(CacheControl.MaxStale(Some(100L))))
      },
      test("parse min-fresh") {
        assertTrue(CacheControl.parse("min-fresh=60") == Right(CacheControl.MinFresh(60L)))
      },
      test("parse stale-while-revalidate") {
        assertTrue(
          CacheControl.parse("stale-while-revalidate=30") == Right(CacheControl.StaleWhileRevalidate(30L))
        )
      },
      test("parse stale-if-error") {
        assertTrue(CacheControl.parse("stale-if-error=300") == Right(CacheControl.StaleIfError(300L)))
      },
      test("parse multiple directives") {
        val result = CacheControl.parse("no-cache, max-age=0")
        assertTrue(
          result == Right(
            CacheControl.Multiple(Chunk(CacheControl.NoCache, CacheControl.MaxAge(0L)))
          )
        )
      },
      test("parse empty returns Left") {
        assertTrue(CacheControl.parse("").isLeft)
      },
      test("parse unknown directive returns Left") {
        assertTrue(CacheControl.parse("unknown-directive").isLeft)
      },
      test("parse case-insensitive") {
        assertTrue(CacheControl.parse("No-Cache") == Right(CacheControl.NoCache))
      },
      test("render no-cache") {
        assertTrue(CacheControl.render(CacheControl.NoCache) == "no-cache")
      },
      test("render max-age") {
        assertTrue(CacheControl.render(CacheControl.MaxAge(3600L)) == "max-age=3600")
      },
      test("render max-stale without value") {
        assertTrue(CacheControl.render(CacheControl.MaxStale(None)) == "max-stale")
      },
      test("render max-stale with value") {
        assertTrue(CacheControl.render(CacheControl.MaxStale(Some(100L))) == "max-stale=100")
      },
      test("render multiple") {
        val m = CacheControl.Multiple(Chunk(CacheControl.NoStore, CacheControl.MaxAge(0L)))
        assertTrue(CacheControl.render(m) == "no-store, max-age=0")
      },
      test("header name") {
        assertTrue(CacheControl.NoCache.headerName == "cache-control")
      },
      test("round-trip max-age") {
        val original = CacheControl.MaxAge(7200L)
        val rendered = CacheControl.render(original)
        assertTrue(CacheControl.parse(rendered) == Right(original))
      }
    ),
    suite("ETag")(
      test("parse strong etag") {
        assertTrue(ETag.parse(""""abc123"""") == Right(ETag("abc123", weak = false)))
      },
      test("parse weak etag") {
        assertTrue(ETag.parse("""W/"abc123"""") == Right(ETag("abc123", weak = true)))
      },
      test("parse invalid returns Left") {
        assertTrue(ETag.parse("abc123").isLeft)
      },
      test("render strong etag") {
        assertTrue(ETag.render(ETag("abc", weak = false)) == """"abc"""")
      },
      test("render weak etag") {
        assertTrue(ETag.render(ETag("abc", weak = true)) == """W/"abc"""")
      },
      test("header name") {
        assertTrue(ETag("x", weak = false).headerName == "etag")
      },
      test("round-trip strong") {
        val original = ETag("v1", weak = false)
        val rendered = ETag.render(original)
        assertTrue(ETag.parse(rendered) == Right(original))
      },
      test("round-trip weak") {
        val original = ETag("v1", weak = true)
        val rendered = ETag.render(original)
        assertTrue(ETag.parse(rendered) == Right(original))
      }
    ),
    suite("IfMatch")(
      test("parse *") {
        assertTrue(IfMatch.parse("*") == Right(IfMatch.Any))
      },
      test("parse single etag") {
        val result = IfMatch.parse(""""abc"""")
        assertTrue(result == Right(IfMatch.ETags(Chunk(ETag("abc", weak = false)))))
      },
      test("parse multiple etags") {
        val result = IfMatch.parse(""""a", "b"""")
        assertTrue(
          result == Right(IfMatch.ETags(Chunk(ETag("a", weak = false), ETag("b", weak = false))))
        )
      },
      test("header name") {
        assertTrue(IfMatch.Any.headerName == "if-match")
      },
      test("render *") {
        assertTrue(IfMatch.render(IfMatch.Any) == "*")
      },
      test("render etags") {
        val h = IfMatch.ETags(Chunk(ETag("x", weak = false)))
        assertTrue(IfMatch.render(h) == """"x"""")
      }
    ),
    suite("IfNoneMatch")(
      test("parse *") {
        assertTrue(IfNoneMatch.parse("*") == Right(IfNoneMatch.Any))
      },
      test("parse single etag") {
        val result = IfNoneMatch.parse(""""abc"""")
        assertTrue(result == Right(IfNoneMatch.ETags(Chunk(ETag("abc", weak = false)))))
      },
      test("parse weak etag") {
        val result = IfNoneMatch.parse("""W/"abc"""")
        assertTrue(result == Right(IfNoneMatch.ETags(Chunk(ETag("abc", weak = true)))))
      },
      test("header name") {
        assertTrue(IfNoneMatch.Any.headerName == "if-none-match")
      },
      test("render *") {
        assertTrue(IfNoneMatch.render(IfNoneMatch.Any) == "*")
      }
    ),
    suite("IfModifiedSince")(
      test("parse and render") {
        val date   = "Sat, 29 Oct 1994 19:43:31 GMT"
        val result = IfModifiedSince.parse(date)
        assertTrue(
          result == Right(IfModifiedSince(date)),
          result.map(_.headerName) == Right("if-modified-since"),
          result.map(_.renderedValue) == Right(date)
        )
      }
    ),
    suite("IfUnmodifiedSince")(
      test("parse and render") {
        val date   = "Sat, 29 Oct 1994 19:43:31 GMT"
        val result = IfUnmodifiedSince.parse(date)
        assertTrue(
          result == Right(IfUnmodifiedSince(date)),
          result.map(_.headerName) == Right("if-unmodified-since")
        )
      }
    ),
    suite("IfRange")(
      test("parse and render") {
        val value  = """"etag123""""
        val result = IfRange.parse(value)
        assertTrue(
          result == Right(IfRange(value)),
          result.map(_.headerName) == Right("if-range")
        )
      }
    ),
    suite("Expires")(
      test("parse and render") {
        val date   = "Thu, 01 Dec 1994 16:00:00 GMT"
        val result = Expires.parse(date)
        assertTrue(
          result == Right(Expires(date)),
          result.map(_.headerName) == Right("expires")
        )
      }
    ),
    suite("Age")(
      test("parse valid") {
        val result = Age.parse("3600")
        assertTrue(
          result == Right(Age(3600L)),
          result.map(_.headerName) == Right("age"),
          result.map(_.renderedValue) == Right("3600")
        )
      },
      test("parse zero") {
        assertTrue(Age.parse("0") == Right(Age(0L)))
      },
      test("parse negative returns Left") {
        assertTrue(Age.parse("-1").isLeft)
      },
      test("parse non-numeric returns Left") {
        assertTrue(Age.parse("abc").isLeft)
      },
      test("render") {
        assertTrue(Age.render(Age(42L)) == "42")
      },
      test("round-trip") {
        val original = Age(100L)
        val rendered = Age.render(original)
        assertTrue(Age.parse(rendered) == Right(original))
      }
    ),
    suite("LastModified")(
      test("parse and render") {
        val date   = "Tue, 15 Nov 1994 12:45:26 GMT"
        val result = LastModified.parse(date)
        assertTrue(
          result == Right(LastModified(date)),
          result.map(_.headerName) == Right("last-modified")
        )
      }
    ),
    suite("Pragma")(
      test("parse and render") {
        val result = Pragma.parse("no-cache")
        assertTrue(
          result == Right(Pragma("no-cache")),
          result.map(_.headerName) == Right("pragma")
        )
      }
    ),
    suite("Vary")(
      test("parse *") {
        assertTrue(Vary.parse("*") == Right(Vary.Any))
      },
      test("parse single header") {
        assertTrue(Vary.parse("Accept") == Right(Vary.Headers(Chunk("Accept"))))
      },
      test("parse multiple headers") {
        val result = Vary.parse("Accept, Accept-Encoding")
        assertTrue(result == Right(Vary.Headers(Chunk("Accept", "Accept-Encoding"))))
      },
      test("parse empty returns Left") {
        assertTrue(Vary.parse("").isLeft)
      },
      test("header name") {
        assertTrue(Vary.Any.headerName == "vary")
      },
      test("render *") {
        assertTrue(Vary.render(Vary.Any) == "*")
      },
      test("render headers") {
        val h = Vary.Headers(Chunk("Accept", "Origin"))
        assertTrue(Vary.render(h) == "Accept, Origin")
      },
      test("round-trip") {
        val original = Vary.Headers(Chunk("Accept"))
        val rendered = Vary.render(original)
        assertTrue(Vary.parse(rendered) == Right(original))
      }
    )
  )
}
