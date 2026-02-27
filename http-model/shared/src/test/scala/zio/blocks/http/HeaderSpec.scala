package zio.blocks.http

import zio.test._
import zio.blocks.mediatype.MediaTypes

object HeaderSpec extends HttpModelBaseSpec {
  def spec: Spec[TestEnvironment, Any] = suite("Header")(
    suite("ContentType header")(
      test("parse round-trip for application/json") {
        val result = Header.ContentType.parse("application/json")
        assertTrue(
          result == Right(Header.ContentType(zio.blocks.http.ContentType(MediaTypes.application.`json`))),
          result.map(_.headerName) == Right("content-type"),
          result.map(_.renderedValue) == Right("application/json")
        )
      },
      test("parse round-trip for text/plain with charset") {
        val result = Header.ContentType.parse("text/plain; charset=UTF-8")
        assertTrue(
          result == Right(
            Header.ContentType(zio.blocks.http.ContentType(MediaTypes.text.`plain`, charset = Some(Charset.UTF8)))
          ),
          result.map(_.renderedValue) == Right("text/plain; charset=UTF-8")
        )
      },
      test("parse invalid content type returns Left") {
        val result = Header.ContentType.parse("")
        assertTrue(result.isLeft)
      },
      test("render delegates to ContentType.render") {
        val h = Header.ContentType(zio.blocks.http.ContentType(MediaTypes.application.`json`))
        assertTrue(Header.ContentType.render(h) == "application/json")
      },
      test("HeaderType name matches headerName") {
        assertTrue(Header.ContentType.name == "content-type")
      }
    ),
    suite("ContentLength header")(
      test("parse valid numeric value") {
        val result = Header.ContentLength.parse("1024")
        assertTrue(
          result == Right(Header.ContentLength(1024L)),
          result.map(_.headerName) == Right("content-length"),
          result.map(_.renderedValue) == Right("1024")
        )
      },
      test("parse zero") {
        val result = Header.ContentLength.parse("0")
        assertTrue(result == Right(Header.ContentLength(0L)))
      },
      test("parse invalid non-numeric value returns Left") {
        val result = Header.ContentLength.parse("abc")
        assertTrue(result.isLeft)
      },
      test("parse negative value returns Left") {
        val result = Header.ContentLength.parse("-1")
        assertTrue(result.isLeft)
      },
      test("render produces string of the length") {
        val h = Header.ContentLength(42L)
        assertTrue(Header.ContentLength.render(h) == "42")
      },
      test("HeaderType name matches headerName") {
        assertTrue(Header.ContentLength.name == "content-length")
      }
    ),
    suite("Host header")(
      test("parse host without port") {
        val result = Header.Host.parse("example.com")
        assertTrue(
          result == Right(Header.Host("example.com", None)),
          result.map(_.headerName) == Right("host"),
          result.map(_.renderedValue) == Right("example.com")
        )
      },
      test("parse host with port") {
        val result = Header.Host.parse("example.com:8080")
        assertTrue(
          result == Right(Header.Host("example.com", Some(8080))),
          result.map(_.renderedValue) == Right("example.com:8080")
        )
      },
      test("parse empty host returns Left") {
        val result = Header.Host.parse("")
        assertTrue(result.isLeft)
      },
      test("parse host with invalid port returns Left") {
        val result = Header.Host.parse("example.com:abc")
        assertTrue(result.isLeft)
      },
      test("render host without port") {
        val h = Header.Host("localhost", None)
        assertTrue(Header.Host.render(h) == "localhost")
      },
      test("render host with port") {
        val h = Header.Host("localhost", Some(3000))
        assertTrue(Header.Host.render(h) == "localhost:3000")
      },
      test("HeaderType name matches headerName") {
        assertTrue(Header.Host.name == "host")
      }
    ),
    suite("simple string headers")(
      test("Accept parse and render") {
        val result = Header.Accept.parse("text/html, application/json")
        assertTrue(
          result == Right(Header.Accept("text/html, application/json")),
          result.map(_.headerName) == Right("accept"),
          result.map(_.renderedValue) == Right("text/html, application/json")
        )
      },
      test("Authorization parse and render") {
        val result = Header.Authorization.parse("Bearer token123")
        assertTrue(
          result == Right(Header.Authorization("Bearer token123")),
          result.map(_.headerName) == Right("authorization"),
          result.map(_.renderedValue) == Right("Bearer token123")
        )
      },
      test("UserAgent parse and render") {
        val result = Header.UserAgent.parse("Mozilla/5.0")
        assertTrue(
          result == Right(Header.UserAgent("Mozilla/5.0")),
          result.map(_.headerName) == Right("user-agent"),
          result.map(_.renderedValue) == Right("Mozilla/5.0")
        )
      },
      test("CacheControl parse and render") {
        val result = Header.CacheControl.parse("no-cache, no-store")
        assertTrue(
          result == Right(Header.CacheControl("no-cache, no-store")),
          result.map(_.headerName) == Right("cache-control"),
          result.map(_.renderedValue) == Right("no-cache, no-store")
        )
      },
      test("Location parse and render") {
        val result = Header.Location.parse("https://example.com/new")
        assertTrue(
          result == Right(Header.Location("https://example.com/new")),
          result.map(_.headerName) == Right("location"),
          result.map(_.renderedValue) == Right("https://example.com/new")
        )
      },
      test("SetCookie parse and render") {
        val result = Header.SetCookie.parse("session=abc123; Path=/; HttpOnly")
        assertTrue(
          result == Right(Header.SetCookie("session=abc123; Path=/; HttpOnly")),
          result.map(_.headerName) == Right("set-cookie"),
          result.map(_.renderedValue) == Right("session=abc123; Path=/; HttpOnly")
        )
      },
      test("Cookie parse and render") {
        val result = Header.Cookie.parse("session=abc123; user=john")
        assertTrue(
          result == Right(Header.Cookie("session=abc123; user=john")),
          result.map(_.headerName) == Right("cookie"),
          result.map(_.renderedValue) == Right("session=abc123; user=john")
        )
      },
      test("ContentEncoding parse and render") {
        val result = Header.ContentEncoding.parse("gzip")
        assertTrue(
          result == Right(Header.ContentEncoding("gzip")),
          result.map(_.headerName) == Right("content-encoding"),
          result.map(_.renderedValue) == Right("gzip")
        )
      },
      test("TransferEncoding parse and render") {
        val result = Header.TransferEncoding.parse("chunked")
        assertTrue(
          result == Right(Header.TransferEncoding("chunked")),
          result.map(_.headerName) == Right("transfer-encoding"),
          result.map(_.renderedValue) == Right("chunked")
        )
      },
      test("Connection parse and render") {
        val result = Header.Connection.parse("keep-alive")
        assertTrue(
          result == Right(Header.Connection("keep-alive")),
          result.map(_.headerName) == Right("connection"),
          result.map(_.renderedValue) == Right("keep-alive")
        )
      },
      test("Origin parse and render") {
        val result = Header.Origin.parse("https://example.com")
        assertTrue(
          result == Right(Header.Origin("https://example.com")),
          result.map(_.headerName) == Right("origin"),
          result.map(_.renderedValue) == Right("https://example.com")
        )
      },
      test("Referer parse and render") {
        val result = Header.Referer.parse("https://example.com/page")
        assertTrue(
          result == Right(Header.Referer("https://example.com/page")),
          result.map(_.headerName) == Right("referer"),
          result.map(_.renderedValue) == Right("https://example.com/page")
        )
      },
      test("AcceptEncoding parse and render") {
        val result = Header.AcceptEncoding.parse("gzip, deflate, br")
        assertTrue(
          result == Right(Header.AcceptEncoding("gzip, deflate, br")),
          result.map(_.headerName) == Right("accept-encoding"),
          result.map(_.renderedValue) == Right("gzip, deflate, br")
        )
      }
    ),
    suite("Custom header")(
      test("construction and rendering") {
        val h = Header.Custom("x-request-id", "abc-123")
        assertTrue(
          h.headerName == "x-request-id",
          h.renderedValue == "abc-123"
        )
      },
      test("parse and render via HeaderType") {
        val result = Header.Custom.parse("some-value")
        assertTrue(
          result == Right(Header.Custom("x-custom", "some-value")),
          result.map(_.renderedValue) == Right("some-value")
        )
      },
      test("render via HeaderType") {
        val h = Header.Custom("x-trace-id", "trace-456")
        assertTrue(Header.Custom.render(h) == "trace-456")
      }
    ),
    suite("headerName is always lowercase")(
      test("all typed headers have lowercase names") {
        assertTrue(
          Header.ContentType.name == "content-type",
          Header.Accept.name == "accept",
          Header.Authorization.name == "authorization",
          Header.Host.name == "host",
          Header.UserAgent.name == "user-agent",
          Header.CacheControl.name == "cache-control",
          Header.ContentLength.name == "content-length",
          Header.Location.name == "location",
          Header.SetCookie.name == "set-cookie",
          Header.Cookie.name == "cookie",
          Header.ContentEncoding.name == "content-encoding",
          Header.TransferEncoding.name == "transfer-encoding",
          Header.Connection.name == "connection",
          Header.Origin.name == "origin",
          Header.Referer.name == "referer",
          Header.AcceptEncoding.name == "accept-encoding",
          Header.Custom.name == "x-custom"
        )
      }
    )
  )
}
