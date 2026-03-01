package zio.http.headers

import zio.test._
import zio.blocks.chunk.Chunk
import zio.http.Method

object MiscHeadersSpec extends ZIOSpecDefault {
  def spec: Spec[TestEnvironment, Any] = suite("MiscHeaders")(
    suite("UserAgent")(
      test("parse and render") {
        val result = UserAgent.parse("Mozilla/5.0")
        assertTrue(
          result == Right(UserAgent("Mozilla/5.0")),
          result.map(_.headerName) == Right("user-agent")
        )
      },
      test("render") {
        assertTrue(UserAgent.render(UserAgent("Mozilla/5.0")) == "Mozilla/5.0")
      }
    ),
    suite("Server")(
      test("parse and render") {
        val result = Server.parse("Apache/2.4.1")
        assertTrue(
          result == Right(Server("Apache/2.4.1")),
          result.map(_.headerName) == Right("server")
        )
      },
      test("render") {
        assertTrue(Server.render(Server("Apache/2.4.1")) == "Apache/2.4.1")
      }
    ),
    suite("Date")(
      test("parse and render") {
        val result = Date.parse("Tue, 15 Nov 1994 08:12:31 GMT")
        assertTrue(
          result == Right(Date("Tue, 15 Nov 1994 08:12:31 GMT")),
          result.map(_.headerName) == Right("date")
        )
      },
      test("render") {
        assertTrue(Date.render(Date("Tue, 15 Nov 1994 08:12:31 GMT")) == "Tue, 15 Nov 1994 08:12:31 GMT")
      }
    ),
    suite("Link")(
      test("parse and render") {
        val result = Link.parse("""<https://example.com>; rel="preconnect"""")
        assertTrue(
          result == Right(Link("""<https://example.com>; rel="preconnect"""")),
          result.map(_.headerName) == Right("link")
        )
      },
      test("render") {
        val h = Link("<https://example.com>; rel=\"preconnect\"")
        assertTrue(Link.render(h) == "<https://example.com>; rel=\"preconnect\"")
      }
    ),
    suite("RetryAfter")(
      test("parse seconds") {
        val result = RetryAfter.parse("120")
        assertTrue(
          result == Right(RetryAfter("120")),
          result.map(_.headerName) == Right("retry-after")
        )
      },
      test("parse date") {
        val result = RetryAfter.parse("Fri, 31 Dec 1999 23:59:59 GMT")
        assertTrue(result == Right(RetryAfter("Fri, 31 Dec 1999 23:59:59 GMT")))
      },
      test("render") {
        assertTrue(RetryAfter.render(RetryAfter("120")) == "120")
      }
    ),
    suite("Allow")(
      test("parse single method") {
        assertTrue(Allow.parse("GET") == Right(Allow(Chunk(Method.GET))))
      },
      test("parse multiple methods") {
        val result = Allow.parse("GET, POST, PUT")
        assertTrue(result == Right(Allow(Chunk(Method.GET, Method.POST, Method.PUT))))
      },
      test("parse empty returns Left") {
        assertTrue(Allow.parse("").isLeft)
      },
      test("parse invalid method returns Left") {
        assertTrue(Allow.parse("INVALID").isLeft)
      },
      test("render") {
        val h = Allow(Chunk(Method.GET, Method.POST))
        assertTrue(Allow.render(h) == "GET, POST")
      },
      test("header name") {
        assertTrue(Allow(Chunk(Method.GET)).headerName == "allow")
      }
    ),
    suite("Expect")(
      test("parse and render") {
        val result = Expect.parse("100-continue")
        assertTrue(
          result == Right(Expect("100-continue")),
          result.map(_.headerName) == Right("expect")
        )
      },
      test("render") {
        assertTrue(Expect.render(Expect("100-continue")) == "100-continue")
      }
    ),
    suite("Range")(
      test("parse bytes range") {
        val result = Range.parse("bytes=0-499")
        assertTrue(result == Right(Range("bytes", "0-499")))
      },
      test("parse suffix range") {
        val result = Range.parse("bytes=-500")
        assertTrue(result == Right(Range("bytes", "-500")))
      },
      test("parse invalid returns Left") {
        assertTrue(Range.parse("invalid").isLeft)
      },
      test("render") {
        val h = Range("bytes", "0-499")
        assertTrue(Range.render(h) == "bytes=0-499")
      },
      test("round-trip") {
        val original = Range("bytes", "200-1000")
        val rendered = Range.render(original)
        assertTrue(Range.parse(rendered) == Right(original))
      },
      test("header name") {
        assertTrue(Range("bytes", "0-499").headerName == "range")
      }
    )
  )
}
