/*
 * Copyright 2024-2026 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zio.http

import zio.test._
import zio.blocks.chunk.Chunk

object URLSpec extends HttpModelBaseSpec {
  def spec: Spec[TestEnvironment, Any] = suite("URL")(
    suite("parse absolute URLs")(
      test("http with host and path") {
        val result = URL.parse("http://example.com/path")
        val url    = result.toOption.get
        assertTrue(
          result.isRight,
          url.scheme == Some(Scheme.HTTP),
          url.host == Some("example.com"),
          url.port == None,
          url.path.segments == Chunk("path"),
          url.path.hasLeadingSlash
        )
      },
      test("https with host") {
        val result = URL.parse("https://example.com")
        val url    = result.toOption.get
        assertTrue(
          result.isRight,
          url.scheme == Some(Scheme.HTTPS),
          url.host == Some("example.com")
        )
      },
      test("custom scheme") {
        val result = URL.parse("ftp://files.example.com/pub")
        val url    = result.toOption.get
        assertTrue(
          result.isRight,
          url.scheme == Some(Scheme.Custom("ftp")),
          url.host == Some("files.example.com"),
          url.path.segments == Chunk("pub")
        )
      },
      test("ws scheme") {
        val result = URL.parse("ws://echo.example.com/socket")
        val url    = result.toOption.get
        assertTrue(
          result.isRight,
          url.scheme == Some(Scheme.WS),
          url.host == Some("echo.example.com")
        )
      },
      test("wss scheme") {
        val result = URL.parse("wss://secure.example.com/socket")
        val url    = result.toOption.get
        assertTrue(
          result.isRight,
          url.scheme == Some(Scheme.WSS),
          url.host == Some("secure.example.com")
        )
      }
    ),
    suite("parse with port")(
      test("http with explicit port") {
        val result = URL.parse("http://localhost:8080/api")
        val url    = result.toOption.get
        assertTrue(
          result.isRight,
          url.host == Some("localhost"),
          url.port == Some(8080),
          url.path.segments == Chunk("api")
        )
      },
      test("https with port 443") {
        val result = URL.parse("https://example.com:443/secure")
        val url    = result.toOption.get
        assertTrue(
          result.isRight,
          url.port == Some(443)
        )
      }
    ),
    suite("parse with query params")(
      test("single query param") {
        val result = URL.parse("http://example.com/path?key=value")
        val url    = result.toOption.get
        assertTrue(
          result.isRight,
          url.queryParams.getFirst("key") == Some("value")
        )
      },
      test("multiple query params") {
        val result = URL.parse("http://example.com/path?a=1&b=2")
        val url    = result.toOption.get
        assertTrue(
          result.isRight,
          url.queryParams.getFirst("a") == Some("1"),
          url.queryParams.getFirst("b") == Some("2")
        )
      },
      test("encoded query params") {
        val result = URL.parse("http://example.com/search?q=hello%20world")
        val url    = result.toOption.get
        assertTrue(
          result.isRight,
          url.queryParams.getFirst("q") == Some("hello world")
        )
      }
    ),
    suite("parse with fragment")(
      test("fragment after path") {
        val result = URL.parse("http://example.com/page#section")
        val url    = result.toOption.get
        assertTrue(
          result.isRight,
          url.fragment == Some("section")
        )
      },
      test("fragment after query") {
        val result = URL.parse("http://example.com/page?key=value#frag")
        val url    = result.toOption.get
        assertTrue(
          result.isRight,
          url.queryParams.getFirst("key") == Some("value"),
          url.fragment == Some("frag")
        )
      },
      test("empty fragment") {
        val result = URL.parse("http://example.com/page#")
        val url    = result.toOption.get
        assertTrue(
          result.isRight,
          url.fragment == Some("")
        )
      }
    ),
    suite("parse relative URLs")(
      test("path only") {
        val result = URL.parse("/path/to/resource")
        val url    = result.toOption.get
        assertTrue(
          result.isRight,
          url.scheme == None,
          url.host == None,
          url.path.segments == Chunk("path", "to", "resource"),
          url.isRelative
        )
      },
      test("path with query") {
        val result = URL.parse("/search?q=hello")
        val url    = result.toOption.get
        assertTrue(
          result.isRight,
          url.scheme == None,
          url.queryParams.getFirst("q") == Some("hello")
        )
      },
      test("path with fragment") {
        val result = URL.parse("/page#top")
        val url    = result.toOption.get
        assertTrue(
          result.isRight,
          url.fragment == Some("top")
        )
      },
      test("path with query and fragment") {
        val result = URL.parse("/page?key=val#bottom")
        val url    = result.toOption.get
        assertTrue(
          result.isRight,
          url.queryParams.getFirst("key") == Some("val"),
          url.fragment == Some("bottom")
        )
      }
    ),
    suite("parse with userinfo")(
      test("userinfo is skipped during parsing") {
        val result = URL.parse("http://user:pass@host.com/path")
        val url    = result.toOption.get
        assertTrue(
          result.isRight,
          url.host == Some("host.com"),
          url.path.segments == Chunk("path")
        )
      }
    ),
    suite("parse IPv6 hosts")(
      test("IPv6 localhost with port") {
        val result = URL.parse("http://[::1]:8080/path")
        val url    = result.toOption.get
        assertTrue(
          result.isRight,
          url.host == Some("[::1]"),
          url.port == Some(8080),
          url.path.segments == Chunk("path")
        )
      },
      test("IPv6 full address") {
        val result = URL.parse("http://[2001:db8::1]/path")
        val url    = result.toOption.get
        assertTrue(
          result.isRight,
          url.host == Some("[2001:db8::1]")
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
        val url    = result.toOption.get
        assertTrue(
          result.isRight,
          url.scheme == Some(Scheme.HTTP),
          url.host == Some("example.com"),
          url.port == Some(8080),
          url.path.segments == Chunk("path", "to", "resource"),
          url.queryParams.getFirst("key") == Some("value"),
          url.queryParams.getFirst("a") == Some("b"),
          url.fragment == Some("frag")
        )
      },
      test("host without path") {
        val result = URL.parse("http://example.com")
        val url    = result.toOption.get
        assertTrue(
          result.isRight,
          url.path == Path.root
        )
      },
      test("scheme only with host slash") {
        val result = URL.parse("http://example.com/")
        val url    = result.toOption.get
        assertTrue(
          result.isRight,
          url.host == Some("example.com"),
          url.path == Path.root
        )
      },
      test("just query string") {
        val result = URL.parse("?key=value")
        val url    = result.toOption.get
        assertTrue(
          result.isRight,
          url.queryParams.getFirst("key") == Some("value"),
          url.path.isEmpty
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
    ),
    suite("parse IPv6 edge cases")(
      test("IPv6 without port") {
        val result = URL.parse("http://[::1]/path")
        val url    = result.toOption.get
        assertTrue(
          result.isRight,
          url.host == Some("[::1]"),
          url.port == None,
          url.path.segments == Chunk("path")
        )
      },
      test("IPv6 with unclosed bracket") {
        val result = URL.parse("http://[::1/path")
        val url    = result.toOption.get
        assertTrue(
          result.isRight,
          url.host == Some("[::1")
        )
      },
      test("IPv6 with invalid port") {
        val result = URL.parse("http://[::1]:abc/path")
        assertTrue(result.isLeft)
      },
      test("IPv6 with port out of range") {
        val result = URL.parse("http://[::1]:99999/path")
        assertTrue(result.isLeft)
      },
      test("IPv6 with empty port after colon") {
        val result = URL.parse("http://[::1]:/path")
        val url    = result.toOption.get
        assertTrue(
          result.isRight,
          url.host == Some("[::1]"),
          url.port == None
        )
      }
    ),
    suite("parse port edge cases")(
      test("port out of range (high)") {
        val result = URL.parse("http://example.com:70000/path")
        assertTrue(result.isLeft)
      },
      test("port out of range (negative via overflow)") {
        val result = URL.parse("http://example.com:-1/path")
        assertTrue(result.isLeft)
      },
      test("empty host before colon") {
        val result = URL.parse("http://:8080/path")
        assertTrue(result.isLeft)
      },
      test("host with empty port") {
        val result = URL.parse("http://example.com:/path")
        val url    = result.toOption.get
        assertTrue(
          result.isRight,
          url.host == Some("example.com"),
          url.port == None
        )
      }
    ),
    suite("parse authority end calculation")(
      test("authority ends at query when no slash") {
        val result = URL.parse("http://example.com?key=val")
        val url    = result.toOption.get
        assertTrue(
          result.isRight,
          url.host == Some("example.com"),
          url.queryParams.getFirst("key") == Some("val")
        )
      },
      test("authority ends at fragment when no slash or query") {
        val result = URL.parse("http://example.com#frag")
        val url    = result.toOption.get
        assertTrue(
          result.isRight,
          url.host == Some("example.com"),
          url.fragment == Some("frag")
        )
      },
      test("authority ends at earlier of query and fragment") {
        val result = URL.parse("http://example.com?q=1#f")
        val url    = result.toOption.get
        assertTrue(
          result.isRight,
          url.host == Some("example.com"),
          url.queryParams.getFirst("q") == Some("1"),
          url.fragment == Some("f")
        )
      },
      test("authority ends at fragment before query in URL string") {
        val result = URL.parse("http://example.com#f?notquery")
        val url    = result.toOption.get
        assertTrue(
          result.isRight,
          url.host == Some("example.com"),
          url.fragment == Some("f?notquery")
        )
      }
    ),
    suite("parse path variants")(
      test("relative path without scheme has decoded segments") {
        val result = URL.parse("foo")
        val url    = result.toOption.get
        assertTrue(
          result.isRight,
          url.path.segments == Chunk("foo"),
          url.scheme == None
        )
      },
      test("encoded fragment is decoded") {
        val result = URL.parse("http://example.com/page#sec%20tion")
        val url    = result.toOption.get
        assertTrue(
          result.isRight,
          url.fragment == Some("sec tion")
        )
      }
    ),
    suite("encode edge cases")(
      test("encode URL with host and empty path appends slash") {
        val url = URL(
          scheme = Some(Scheme.HTTP),
          host = Some("example.com"),
          port = None,
          path = Path.empty,
          queryParams = QueryParams.empty,
          fragment = None
        )
        assertTrue(url.encode == "http://example.com/")
      },
      test("encode URL without host and empty path") {
        val url = URL(
          scheme = None,
          host = None,
          port = None,
          path = Path.empty,
          queryParams = QueryParams.empty,
          fragment = None
        )
        assertTrue(url.encode == "")
      },
      test("encode URL with only query params") {
        val url = URL(
          scheme = None,
          host = None,
          port = None,
          path = Path.empty,
          queryParams = QueryParams("k" -> "v"),
          fragment = None
        )
        assertTrue(url.encode == "?k=v")
      },
      test("encode URL with only fragment") {
        val url = URL(
          scheme = None,
          host = None,
          port = None,
          path = Path.empty,
          queryParams = QueryParams.empty,
          fragment = Some("top")
        )
        assertTrue(url.encode == "#top")
      }
    ),
    suite("toString")(
      test("delegates to encode") {
        val url = URL.parse("http://example.com/path").toOption.get
        assertTrue(url.toString == url.encode)
      }
    ),
    suite("host")(
      test("sets host") {
        val url = URL.fromPath(Path.root).host("example.com")
        assertTrue(url.host == Some("example.com"))
      }
    ),
    suite("port")(
      test("sets port") {
        val url = URL.fromPath(Path.root).port(8080)
        assertTrue(url.port == Some(8080))
      }
    ),
    suite("scheme")(
      test("sets scheme") {
        val url = URL.fromPath(Path.root).scheme(Scheme.HTTPS)
        assertTrue(url.scheme == Some(Scheme.HTTPS))
      }
    ),
    suite("path (setter)")(
      test("sets path") {
        val url = URL.root.path(Path("/api/v2"))
        assertTrue(url.path.segments == Chunk("api", "v2"))
      }
    ),
    suite("fragment (setter)")(
      test("sets fragment") {
        val url = URL.root.fragment("section1")
        assertTrue(url.fragment == Some("section1"))
      }
    ),
    suite("addPath")(
      test("adds single segment") {
        val url = URL.parse("http://example.com/api").toOption.get.addPath("users")
        assertTrue(url.path.segments == Chunk("api", "users"))
      },
      test("adds multi-segment path") {
        val url = URL.parse("http://example.com/api").toOption.get.addPath(Path("v2/users"))
        assertTrue(url.path.segments == Chunk("api", "v2", "users"))
      }
    ),
    suite("addQueryParams")(
      test("combines query params") {
        val url = URL
          .parse("http://example.com?a=1")
          .toOption
          .get
          .addQueryParams(QueryParams("b" -> "2"))
        assertTrue(
          url.queryParams.getFirst("a") == Some("1"),
          url.queryParams.getFirst("b") == Some("2")
        )
      }
    ),
    suite("updateQueryParams")(
      test("updates query params via function") {
        val url = URL
          .parse("http://example.com?a=1")
          .toOption
          .get
          .updateQueryParams(_.add("b", "2"))
        assertTrue(url.queryParams.getFirst("b") == Some("2"))
      }
    ),
    suite("URL addLeadingSlash / dropLeadingSlash")(
      test("addLeadingSlash delegates to path") {
        val url = URL.fromPath(Path("foo")).addLeadingSlash
        assertTrue(url.path.hasLeadingSlash)
      },
      test("dropLeadingSlash delegates to path") {
        val url = URL.fromPath(Path("/foo")).dropLeadingSlash
        assertTrue(!url.path.hasLeadingSlash)
      }
    ),
    suite("URL addTrailingSlash / dropTrailingSlash")(
      test("addTrailingSlash delegates to path") {
        val url = URL.fromPath(Path("/foo")).addTrailingSlash
        assertTrue(url.path.trailingSlash)
      },
      test("dropTrailingSlash delegates to path") {
        val url = URL.fromPath(Path("/foo/")).dropTrailingSlash
        assertTrue(!url.path.trailingSlash)
      }
    ),
    suite("hostPort")(
      test("returns host:port when port is present") {
        val url = URL.parse("http://example.com:8080/path").toOption.get
        assertTrue(url.hostPort == Some("example.com:8080"))
      },
      test("returns host only when no port") {
        val url = URL.parse("http://example.com/path").toOption.get
        assertTrue(url.hostPort == Some("example.com"))
      },
      test("returns None when no host") {
        val url = URL.fromPath(Path("/path"))
        assertTrue(url.hostPort == None)
      }
    ),
    suite("relative")(
      test("strips scheme, host, and port") {
        val url = URL.parse("http://example.com:8080/api?key=val").toOption.get.relative
        assertTrue(
          url.scheme == None,
          url.host == None,
          url.port == None,
          url.path.segments == Chunk("api"),
          url.queryParams.getFirst("key") == Some("val")
        )
      }
    )
  )
}
