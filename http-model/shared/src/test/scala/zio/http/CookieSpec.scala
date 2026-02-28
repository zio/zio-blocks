package zio.http

import zio.test._
import zio.blocks.chunk.Chunk

object CookieSpec extends HttpModelBaseSpec {
  def spec: Spec[TestEnvironment, Any] = suite("Cookie")(
    suite("RequestCookie")(
      suite("parseRequest")(
        test("parses single cookie") {
          val cookies = Cookie.parseRequest("name=value")
          assertTrue(cookies == Chunk(RequestCookie("name", "value")))
        },
        test("parses multiple cookies") {
          val cookies = Cookie.parseRequest("name1=val1; name2=val2; name3=val3")
          assertTrue(
            cookies == Chunk(
              RequestCookie("name1", "val1"),
              RequestCookie("name2", "val2"),
              RequestCookie("name3", "val3")
            )
          )
        },
        test("parses cookie with = in value") {
          val cookies = Cookie.parseRequest("token=abc=def=ghi")
          assertTrue(cookies == Chunk(RequestCookie("token", "abc=def=ghi")))
        },
        test("parses empty cookie header") {
          val cookies = Cookie.parseRequest("")
          assertTrue(cookies == Chunk.empty[RequestCookie])
        },
        test("parses cookie with special characters in value") {
          val cookies = Cookie.parseRequest("data=hello%20world+foo")
          assertTrue(cookies == Chunk(RequestCookie("data", "hello%20world+foo")))
        },
        test("trims whitespace from name and value") {
          val cookies = Cookie.parseRequest("name = value")
          assertTrue(cookies == Chunk(RequestCookie("name", "value")))
        }
      ),
      suite("renderRequest")(
        test("renders single cookie") {
          val rendered = Cookie.renderRequest(Chunk(RequestCookie("name", "value")))
          assertTrue(rendered == "name=value")
        },
        test("renders multiple cookies") {
          val rendered = Cookie.renderRequest(
            Chunk(
              RequestCookie("name1", "val1"),
              RequestCookie("name2", "val2")
            )
          )
          assertTrue(rendered == "name1=val1; name2=val2")
        },
        test("renders empty chunk as empty string") {
          val rendered = Cookie.renderRequest(Chunk.empty[RequestCookie])
          assertTrue(rendered == "")
        }
      )
    ),
    suite("ResponseCookie")(
      suite("parseResponse")(
        test("parses minimal cookie (name=value only)") {
          val result = Cookie.parseResponse("session=abc123")
          assertTrue(result == Right(ResponseCookie("session", "abc123")))
        },
        test("parses cookie with all attributes") {
          val result = Cookie.parseResponse(
            "id=abc; Domain=example.com; Path=/foo; Max-Age=3600; Secure; HttpOnly; SameSite=Strict"
          )
          assertTrue(
            result == Right(
              ResponseCookie(
                name = "id",
                value = "abc",
                domain = Some("example.com"),
                path = Some(Path("/foo")),
                maxAge = Some(3600L),
                isSecure = true,
                isHttpOnly = true,
                sameSite = Some(SameSite.Strict)
              )
            )
          )
        },
        test("parses cookie with subset of attributes") {
          val result = Cookie.parseResponse("token=xyz; Secure; Path=/api")
          assertTrue(
            result == Right(
              ResponseCookie(
                name = "token",
                value = "xyz",
                path = Some(Path("/api")),
                isSecure = true
              )
            )
          )
        },
        test("parses SameSite=Lax") {
          val result = Cookie.parseResponse("a=b; SameSite=Lax")
          assertTrue(result == Right(ResponseCookie("a", "b", sameSite = Some(SameSite.Lax))))
        },
        test("parses SameSite=None") {
          val result = Cookie.parseResponse("a=b; SameSite=None")
          assertTrue(result == Right(ResponseCookie("a", "b", sameSite = Some(SameSite.None_))))
        },
        test("parses attributes case-insensitively") {
          val result = Cookie.parseResponse("a=b; secure; httponly; samesite=strict; domain=x.com; path=/; max-age=10")
          assertTrue(
            result == Right(
              ResponseCookie(
                name = "a",
                value = "b",
                domain = Some("x.com"),
                path = Some(Path("/")),
                maxAge = Some(10L),
                isSecure = true,
                isHttpOnly = true,
                sameSite = Some(SameSite.Strict)
              )
            )
          )
        },
        test("returns Left for empty string") {
          val result = Cookie.parseResponse("")
          assertTrue(result.isLeft)
        },
        test("returns Left for missing name") {
          val result = Cookie.parseResponse("=value")
          assertTrue(result.isLeft)
        },
        test("parses cookie with = in value") {
          val result = Cookie.parseResponse("token=abc=def")
          assertTrue(result == Right(ResponseCookie("token", "abc=def")))
        },
        test("parses cookie with empty value") {
          val result = Cookie.parseResponse("deleted=")
          assertTrue(result == Right(ResponseCookie("deleted", "")))
        }
      ),
      suite("renderResponse")(
        test("renders minimal cookie") {
          val rendered = Cookie.renderResponse(ResponseCookie("session", "abc123"))
          assertTrue(rendered == "session=abc123")
        },
        test("renders cookie with all attributes") {
          val rendered = Cookie.renderResponse(
            ResponseCookie(
              name = "id",
              value = "abc",
              domain = Some("example.com"),
              path = Some(Path("/foo")),
              maxAge = Some(3600L),
              isSecure = true,
              isHttpOnly = true,
              sameSite = Some(SameSite.Strict)
            )
          )
          assertTrue(
            rendered == "id=abc; Domain=example.com; Path=/foo; Max-Age=3600; Secure; HttpOnly; SameSite=Strict"
          )
        },
        test("renders cookie with root path") {
          val rendered = Cookie.renderResponse(ResponseCookie("a", "b", path = Some(Path.root)))
          assertTrue(rendered == "a=b; Path=/")
        },
        test("renders SameSite=Lax") {
          val rendered = Cookie.renderResponse(ResponseCookie("a", "b", sameSite = Some(SameSite.Lax)))
          assertTrue(rendered == "a=b; SameSite=Lax")
        },
        test("renders SameSite=None") {
          val rendered = Cookie.renderResponse(ResponseCookie("a", "b", sameSite = Some(SameSite.None_)))
          assertTrue(rendered == "a=b; SameSite=None")
        }
      ),
      suite("round-trip")(
        test("parse/render round-trip for response cookie with all attributes") {
          val cookie = ResponseCookie(
            name = "sess",
            value = "abc",
            domain = Some("example.com"),
            path = Some(Path("/app")),
            maxAge = Some(7200L),
            isSecure = true,
            isHttpOnly = true,
            sameSite = Some(SameSite.Strict)
          )
          val rendered = Cookie.renderResponse(cookie)
          val reparsed = Cookie.parseResponse(rendered)
          assertTrue(reparsed == Right(cookie))
        },
        test("parse/render round-trip for minimal response cookie") {
          val cookie   = ResponseCookie("key", "val")
          val rendered = Cookie.renderResponse(cookie)
          val reparsed = Cookie.parseResponse(rendered)
          assertTrue(reparsed == Right(cookie))
        }
      )
    ),
    suite("SameSite")(
      test("Strict variant") {
        val s: SameSite = SameSite.Strict
        assertTrue(s == SameSite.Strict)
      },
      test("Lax variant") {
        val s: SameSite = SameSite.Lax
        assertTrue(s == SameSite.Lax)
      },
      test("None_ variant") {
        val s: SameSite = SameSite.None_
        assertTrue(s == SameSite.None_)
      }
    )
  )
}
