package zio.http

import zio.test._
import zio.blocks.chunk.Chunk

object URLSpec extends HttpModelBaseSpec {
  def spec: Spec[TestEnvironment, Any] = suite("URL")(
    suite("parse absolute URLs")(
      test("http with host and path") {
        val result = URL.parse("http://example.com/path")
        assertTrue(
          result.isRight,
          result.toOption.get.scheme == Some(Scheme.HTTP),
          result.toOption.get.host == Some("example.com"),
          result.toOption.get.port == None,
          result.toOption.get.path.segments == Chunk("path"),
          result.toOption.get.path.hasLeadingSlash
        )
      },
      test("https with host") {
        val result = URL.parse("https://example.com")
        assertTrue(
          result.isRight,
          result.toOption.get.scheme == Some(Scheme.HTTPS),
          result.toOption.get.host == Some("example.com")
        )
      },
      test("custom scheme") {
        val result = URL.parse("ftp://files.example.com/pub")
        assertTrue(
          result.isRight,
          result.toOption.get.scheme == Some(Scheme.Custom("ftp")),
          result.toOption.get.host == Some("files.example.com"),
          result.toOption.get.path.segments == Chunk("pub")
        )
      },
      test("ws scheme") {
        val result = URL.parse("ws://echo.example.com/socket")
        assertTrue(
          result.isRight,
          result.toOption.get.scheme == Some(Scheme.WS),
          result.toOption.get.host == Some("echo.example.com")
        )
      },
      test("wss scheme") {
        val result = URL.parse("wss://secure.example.com/socket")
        assertTrue(
          result.isRight,
          result.toOption.get.scheme == Some(Scheme.WSS),
          result.toOption.get.host == Some("secure.example.com")
        )
      }
    ),
    suite("parse with port")(
      test("http with explicit port") {
        val result = URL.parse("http://localhost:8080/api")
        assertTrue(
          result.isRight,
          result.toOption.get.host == Some("localhost"),
          result.toOption.get.port == Some(8080),
          result.toOption.get.path.segments == Chunk("api")
        )
      },
      test("https with port 443") {
        val result = URL.parse("https://example.com:443/secure")
        assertTrue(
          result.isRight,
          result.toOption.get.port == Some(443)
        )
      }
    ),
    suite("parse with query params")(
      test("single query param") {
        val result = URL.parse("http://example.com/path?key=value")
        assertTrue(
          result.isRight,
          result.toOption.get.queryParams.getFirst("key") == Some("value")
        )
      },
      test("multiple query params") {
        val result = URL.parse("http://example.com/path?a=1&b=2")
        assertTrue(
          result.isRight,
          result.toOption.get.queryParams.getFirst("a") == Some("1"),
          result.toOption.get.queryParams.getFirst("b") == Some("2")
        )
      },
      test("encoded query params") {
        val result = URL.parse("http://example.com/search?q=hello%20world")
        assertTrue(
          result.isRight,
          result.toOption.get.queryParams.getFirst("q") == Some("hello world")
        )
      }
    ),
    suite("parse with fragment")(
      test("fragment after path") {
        val result = URL.parse("http://example.com/page#section")
        assertTrue(
          result.isRight,
          result.toOption.get.fragment == Some("section")
        )
      },
      test("fragment after query") {
        val result = URL.parse("http://example.com/page?key=value#frag")
        assertTrue(
          result.isRight,
          result.toOption.get.queryParams.getFirst("key") == Some("value"),
          result.toOption.get.fragment == Some("frag")
        )
      },
      test("empty fragment") {
        val result = URL.parse("http://example.com/page#")
        assertTrue(
          result.isRight,
          result.toOption.get.fragment == Some("")
        )
      }
    ),
    suite("parse relative URLs")(
      test("path only") {
        val result = URL.parse("/path/to/resource")
        assertTrue(
          result.isRight,
          result.toOption.get.scheme == None,
          result.toOption.get.host == None,
          result.toOption.get.path.segments == Chunk("path", "to", "resource"),
          result.toOption.get.isRelative
        )
      },
      test("path with query") {
        val result = URL.parse("/search?q=hello")
        assertTrue(
          result.isRight,
          result.toOption.get.scheme == None,
          result.toOption.get.queryParams.getFirst("q") == Some("hello")
        )
      },
      test("path with fragment") {
        val result = URL.parse("/page#top")
        assertTrue(
          result.isRight,
          result.toOption.get.fragment == Some("top")
        )
      },
      test("path with query and fragment") {
        val result = URL.parse("/page?key=val#bottom")
        assertTrue(
          result.isRight,
          result.toOption.get.queryParams.getFirst("key") == Some("val"),
          result.toOption.get.fragment == Some("bottom")
        )
      }
    ),
    suite("parse with userinfo")(
      test("userinfo is skipped during parsing") {
        val result = URL.parse("http://user:pass@host.com/path")
        assertTrue(
          result.isRight,
          result.toOption.get.host == Some("host.com"),
          result.toOption.get.path.segments == Chunk("path")
        )
      }
    ),
    suite("parse IPv6 hosts")(
      test("IPv6 localhost with port") {
        val result = URL.parse("http://[::1]:8080/path")
        assertTrue(
          result.isRight,
          result.toOption.get.host == Some("[::1]"),
          result.toOption.get.port == Some(8080),
          result.toOption.get.path.segments == Chunk("path")
        )
      },
      test("IPv6 full address") {
        val result = URL.parse("http://[2001:db8::1]/path")
        assertTrue(
          result.isRight,
          result.toOption.get.host == Some("[2001:db8::1]")
        )
      }
    ),
    suite("parse edge cases")(
      test("empty string fails") {
        assertTrue(URL.parse("").isLeft)
      },
      test("non-numeric port returns Left instead of throwing") {
        val result = URL.parse("http://example.com:abc/path")
        assertTrue(result.isLeft)
      },
      test("full URL with all components") {
        val result = URL.parse("http://example.com:8080/path/to/resource?key=value&a=b#frag")
        assertTrue(
          result.isRight,
          result.toOption.get.scheme == Some(Scheme.HTTP),
          result.toOption.get.host == Some("example.com"),
          result.toOption.get.port == Some(8080),
          result.toOption.get.path.segments == Chunk("path", "to", "resource"),
          result.toOption.get.queryParams.getFirst("key") == Some("value"),
          result.toOption.get.queryParams.getFirst("a") == Some("b"),
          result.toOption.get.fragment == Some("frag")
        )
      },
      test("host without path") {
        val result = URL.parse("http://example.com")
        assertTrue(
          result.isRight,
          result.toOption.get.path == Path.root
        )
      },
      test("scheme only with host slash") {
        val result = URL.parse("http://example.com/")
        assertTrue(
          result.isRight,
          result.toOption.get.host == Some("example.com"),
          result.toOption.get.path == Path.root
        )
      },
      test("just query string") {
        val result = URL.parse("?key=value")
        assertTrue(
          result.isRight,
          result.toOption.get.queryParams.getFirst("key") == Some("value"),
          result.toOption.get.path.isEmpty
        )
      }
    ),
    suite("encode")(
      test("encodes absolute URL") {
        val url = URL(
          scheme = Some(Scheme.HTTP),
          host = Some("example.com"),
          port = Some(8080),
          path = Path(Chunk("path", "to"), hasLeadingSlash = true, trailingSlash = false),
          queryParams = QueryParams("key" -> "value"),
          fragment = Some("frag")
        )
        assertTrue(url.encode == "http://example.com:8080/path/to?key=value#frag")
      },
      test("encodes relative URL") {
        val url = URL(
          scheme = None,
          host = None,
          port = None,
          path = Path(Chunk("path"), hasLeadingSlash = true, trailingSlash = false),
          queryParams = QueryParams.empty,
          fragment = None
        )
        assertTrue(url.encode == "/path")
      },
      test("encodes URL without port") {
        val url = URL(
          scheme = Some(Scheme.HTTPS),
          host = Some("example.com"),
          port = None,
          path = Path(Chunk("secure"), hasLeadingSlash = true, trailingSlash = false),
          queryParams = QueryParams.empty,
          fragment = None
        )
        assertTrue(url.encode == "https://example.com/secure")
      },
      test("encodes URL with empty query and no fragment") {
        val url = URL(
          scheme = Some(Scheme.HTTP),
          host = Some("example.com"),
          port = None,
          path = Path.root,
          queryParams = QueryParams.empty,
          fragment = None
        )
        assertTrue(url.encode == "http://example.com/")
      },
      test("encodes fragment with special characters") {
        val url = URL(
          scheme = Some(Scheme.HTTP),
          host = Some("example.com"),
          port = None,
          path = Path.root,
          queryParams = QueryParams.empty,
          fragment = Some("sec tion")
        )
        assertTrue(url.encode == "http://example.com/#sec%20tion")
      }
    ),
    suite("encode round-trip")(
      test("parse then encode for simple URL") {
        val input  = "http://example.com:8080/path?key=value#frag"
        val result = URL.parse(input).map(_.encode)
        assertTrue(result == Right(input))
      },
      test("parse then encode for relative URL") {
        val input  = "/path/to/resource?a=1&b=2"
        val result = URL.parse(input).map(_.encode)
        assertTrue(result == Right(input))
      },
      test("parse then encode preserves encoding") {
        val input  = "http://example.com/hello%20world?q=a%26b"
        val result = URL.parse(input).map(_.encode)
        assertTrue(result == Right(input))
      }
    ),
    suite("isAbsolute / isRelative")(
      test("absolute URL") {
        val url = URL.parse("http://example.com/path").toOption.get
        assertTrue(url.isAbsolute, !url.isRelative)
      },
      test("relative URL") {
        val url = URL.parse("/path").toOption.get
        assertTrue(!url.isAbsolute, url.isRelative)
      }
    ),
    suite("/ operator")(
      test("appends segment to URL") {
        val url = URL.parse("http://example.com/api").toOption.get / "users"
        assertTrue(
          url.path.segments == Chunk("api", "users"),
          url.scheme == Some(Scheme.HTTP),
          url.host == Some("example.com")
        )
      },
      test("appends segment to relative URL") {
        val url = URL.parse("/base").toOption.get / "child"
        assertTrue(url.path.segments == Chunk("base", "child"))
      }
    ),
    suite("?? operator")(
      test("adds query parameter") {
        val url = URL.parse("http://example.com/path").toOption.get ?? ("key", "value")
        assertTrue(url.queryParams.getFirst("key") == Some("value"))
      },
      test("adds multiple query parameters") {
        val url = URL.parse("http://example.com").toOption.get ?? ("a", "1") ?? ("b", "2")
        assertTrue(
          url.queryParams.getFirst("a") == Some("1"),
          url.queryParams.getFirst("b") == Some("2")
        )
      }
    ),
    suite("URL.root")(
      test("has HTTP scheme") {
        assertTrue(URL.root.scheme == Some(Scheme.HTTP))
      },
      test("has localhost host") {
        assertTrue(URL.root.host == Some("localhost"))
      },
      test("has no port") {
        assertTrue(URL.root.port == None)
      },
      test("has root path") {
        assertTrue(URL.root.path == Path.root)
      },
      test("has empty query params") {
        assertTrue(URL.root.queryParams.isEmpty)
      },
      test("has no fragment") {
        assertTrue(URL.root.fragment == None)
      }
    ),
    suite("URL.fromPath")(
      test("creates relative URL from path") {
        val url = URL.fromPath(Path("/api/users"))
        assertTrue(
          url.scheme == None,
          url.host == None,
          url.path.segments == Chunk("api", "users"),
          url.isRelative
        )
      },
      test("creates from root path") {
        val url = URL.fromPath(Path.root)
        assertTrue(
          url.path == Path.root,
          url.isRelative
        )
      }
    )
  )
}
