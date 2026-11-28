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

object TargetSpec extends HttpModelBaseSpec {

  private val validTargets: Chunk[String] = Chunk(
    "https://example.com",
    "https://example.com/",
    "https://example.com/api/orders/42",
    "https://example.com/api/orders/42?page=2&sort=price#items",
    "https://shop.example.com:8443/a/b?x=1#f",
    "http://example.com",
    "http://example.com/",
    "http://example.com/path",
    "http://example.com/path/",
    "http://example.com//path//",
    "http://example.com////",
    "http://example.com/path?",
    "http://example.com/path#",
    "http://example.com/path?#",
    "http://example.com#f?notquery",
    "http://example.com?key=val",
    "http://example.com:/path",
    "http://example.com:/",
    "http://example.com:0/",
    "http://example.com:+80/",
    "http://example.com:00080/",
    "http://example.com:65535/",
    "http://",
    "https://",
    "http:///path",
    "http://[::1]/path",
    "http://[::1]:8080/path",
    "http://[::1]:/path",
    "http://[::1/path",
    "http://user:pass@host.com:8080/path?q=1#f",
    "/",
    "//",
    "///",
    "////",
    "/api/orders",
    "/api/orders/",
    "/a//b",
    "/a//",
    "//a//",
    "//cdn.example.com/app.js",
    "api/orders",
    "api/orders/",
    "?key=value",
    "?",
    "#frag",
    "#",
    "/page#top",
    "/page?key=val#bottom",
    "/login?next=https://shop.example.com/cart",
    "http://shop.example.com?back=/cart",
    "http://shop.example.com?to=a@b.com",
    "https://example.com/a%2Fb/c%20d/e+f",
    "https://example.com/%2Fapi%2Forders",
    "https://example.com/caf%C3%A9/%E2%82%AC",
    "https://example.com/100%25/%25/%ZZ/%",
    "https://example.com/a%2fb",
    "https://example.com/a+b/c+d",
    "https://example.com/foo bar",
    "https://example.com/?q=tea+cups&r=tea%20cups",
    "https://example.com/a?b=c#d/e?f",
    "/%2F/%2f",
    "/%",
    "/a%",
    "/a%2",
    "/a%2G",
    "foo",
    "foo/bar",
    "foo://bar/baz",
    "ftp://files.example.com/pub",
    "ws://echo.example.com/socket",
    "wss://secure.example.com/socket",
    "mailto:user@example.com",
    "http://例え.テスト/パス?q=値#断片"
  )

  private val invalidTargets: Chunk[String] = Chunk(
    "",
    "http://example.com:abc/path",
    "http://example.com:99999/path",
    "http://example.com:65536/path",
    "http://example.com:-1/path",
    "http://example.com: 80/path",
    "http://example.com:99999999999999999999/path",
    "http://:8080/path",
    "http://[::1]:abc/path",
    "http://[::1]:99999/path",
    "http://[::1]:65536/path",
    "http://[::1]:-1/path",
    "http://[::1]:99999999999999999999/path"
  )

  private def compareWithUrlParse(raw: String): TestResult =
    (URL.parse(raw), Target.parse(raw)) match {
      case (Right(url), Right(target)) =>
        val segments = url.path.segments
        val decoded  = Chunk.fromIterable((0 until target.segmentCount).map(target.segment))
        assertTrue(
          target.raw == raw,
          target.segmentCount == segments.length,
          decoded == segments,
          target.url == url,
          target.path == url.path,
          target.queryParams == url.queryParams,
          target.fragment == url.fragment
        )
      case (Right(_), Left(actual)) =>
        throw new AssertionError(s"Target rejected '$raw': $actual")
      case (Left(expected), Right(_)) =>
        throw new AssertionError(s"Target accepted '$raw' but URL.parse rejected it: $expected")
      case (Left(expected), Left(actual)) =>
        assertTrue(expected == actual)
    }

  private def compareInvalid(raw: String): TestResult =
    (URL.parse(raw), Target.parse(raw)) match {
      case (Left(expected), Left(actual)) =>
        assertTrue(expected == actual)
      case (Left(expected), Right(_)) =>
        throw new AssertionError(s"Target accepted '$raw' but URL.parse rejected it: $expected")
      case (Right(_), _) =>
        throw new AssertionError(s"URL.parse accepted '$raw', which the invalid corpus expects to fail")
    }

  def spec: Spec[TestEnvironment, Any] =
    suite("Target")(
      suite("parse agrees with URL.parse, path and query accessors")(
        validTargets.map(raw => test(s"matches URL.parse for '$raw'")(compareWithUrlParse(raw))): _*
      ),
      suite("parse rejects exactly what URL.parse rejects")(
        invalidTargets.map(raw => test(s"rejects '$raw'")(compareInvalid(raw))): _*
      ),
      suite("segment accessors")(
        test("decode percent escapes and plus like Path.fromEncoded") {
          val raw    = "/a%2Fb/c%20d/e+f/%2f/%ZZ/%E2%82%AC/%25"
          val target = Target.parse(raw).toOption.get
          val path   = Path.fromEncoded(raw)
          assertTrue(
            target.segmentCount == path.segments.length,
            Chunk.fromIterable((0 until target.segmentCount).map(target.segment)) == path.segments,
            target.segment(0) == "a/b",
            target.segment(1) == "c d",
            target.segment(2) == "e+f",
            target.segment(3) == "/",
            target.segment(4) == "%ZZ",
            target.segment(5) == "€",
            target.segment(6) == "%"
          )
        },
        test("keep empty segments inside the path") {
          val target = Target.parse("/a//b").toOption.get
          assertTrue(
            target.segmentCount == 3,
            target.segment(0) == "a",
            target.segment(1) == "",
            target.segment(2) == "b"
          )
        },
        test("have no segments for empty and root paths") {
          assertTrue(
            Target.parse("https://example.com").toOption.get.segmentCount == 0,
            Target.parse("https://example.com/").toOption.get.segmentCount == 0,
            Target.parse("?q=1").toOption.get.segmentCount == 0,
            Target.parse("/").toOption.get.segmentCount == 0
          )
        },
        test("reject an index outside the path") {
          val target = Target.parse("/a").toOption.get
          assertTrue(
            scala.util.Try(target.segment(1)).isFailure,
            scala.util.Try(target.segment(-1)).isFailure
          )
        }
      ),
      suite("view accessors")(
        test("expose the URL parts lazily and cache the URL") {
          val target = Target.parse("https://example.com:8443/api/orders?page=2#items").toOption.get
          assertTrue(
            target.url eq target.url,
            target.path == Path(Chunk("api", "orders"), hasLeadingSlash = true, trailingSlash = false),
            target.queryParams.getFirst("page") == Some("2"),
            target.fragment == Some("items")
          )
        }
      ),
      suite("fromURL")(
        test("uses the encoded URL as raw text") {
          val url    = URL.parse("https://example.com/api/orders/42?page=2#items").toOption.get
          val target = Target.fromURL(url)
          assertTrue(
            target.raw == url.encode,
            target.url == URL.parse(url.encode).toOption.get,
            target.path == target.url.path,
            target.queryParams == target.url.queryParams,
            target.fragment == target.url.fragment,
            target.segmentCount == 3,
            target.segment(2) == "42"
          )
        },
        test("may re-render a host with an empty path") {
          val url    = URL(Some(Scheme.HTTP), Some("example.com"), None, Path.empty, QueryParams.empty, None)
          val target = Target.fromURL(url)
          assertTrue(
            url.encode == "http://example.com/",
            target.raw == "http://example.com/",
            target.path == Path.root,
            target.segmentCount == 0
          )
        },
        test("URL views throw for a target rendered from the empty URL") {
          val target = Target.fromURL(URL.fromPath(Path.empty))
          assertTrue(
            target.raw == "",
            scala.util.Try(target.url).isFailure,
            scala.util.Try(target.path).isFailure
          )
        }
      ),
      suite("equality and rendering")(
        test("compare by raw text") {
          val a = Target.parse("/a/b").toOption.get
          val b = Target.parse("/a/b").toOption.get
          val c = Target.parse("/a/c").toOption.get
          assertTrue(
            a == b,
            a.hashCode == b.hashCode,
            a != c,
            !a.equals("/a/b")
          )
        },
        test("toString returns the raw text") {
          val raw = "https://example.com/a%2Fb?x=1#f"
          assertTrue(Target.parse(raw).toOption.get.toString == raw)
        }
      )
    )
}
