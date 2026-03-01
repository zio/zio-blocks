package zio.http.headers

import zio.test._
import zio.blocks.chunk.Chunk

object RoutingHeadersSpec extends ZIOSpecDefault {
  def spec: Spec[TestEnvironment, Any] = suite("RoutingHeaders")(
    suite("Host")(
      test("parse host only") {
        assertTrue(Host.parse("example.com") == Right(Host("example.com", None)))
      },
      test("parse host with port") {
        assertTrue(Host.parse("example.com:8080") == Right(Host("example.com", Some(8080))))
      },
      test("parse empty returns Left") {
        assertTrue(Host.parse("").isLeft)
      },
      test("parse invalid port returns Left") {
        assertTrue(Host.parse("example.com:abc").isLeft)
      },
      test("parse port out of range returns Left") {
        assertTrue(Host.parse("example.com:99999").isLeft)
      },
      test("render host only") {
        assertTrue(Host.render(Host("example.com", None)) == "example.com")
      },
      test("render host with port") {
        assertTrue(Host.render(Host("example.com", Some(443))) == "example.com:443")
      },
      test("header name") {
        assertTrue(Host("example.com", None).headerName == "host")
      }
    ),
    suite("Location")(
      test("parse and render") {
        val result = Location.parse("/redirect")
        assertTrue(
          result == Right(Location("/redirect")),
          result.map(_.headerName) == Right("location")
        )
      }
    ),
    suite("Origin")(
      test("parse null") {
        assertTrue(Origin.parse("null") == Right(Origin.Null_))
      },
      test("parse scheme://host") {
        assertTrue(Origin.parse("https://example.com") == Right(Origin.Value("https", "example.com", None)))
      },
      test("parse scheme://host:port") {
        assertTrue(
          Origin.parse("https://example.com:443") == Right(Origin.Value("https", "example.com", Some(443)))
        )
      },
      test("parse invalid returns Left") {
        assertTrue(Origin.parse("not-a-valid-origin").isLeft)
      },
      test("render null") {
        assertTrue(Origin.render(Origin.Null_) == "null")
      },
      test("render with port") {
        assertTrue(Origin.render(Origin.Value("https", "example.com", Some(443))) == "https://example.com:443")
      },
      test("render without port") {
        assertTrue(Origin.render(Origin.Value("https", "example.com", None)) == "https://example.com")
      },
      test("round-trip") {
        val original = Origin.Value("http", "localhost", Some(8080))
        val rendered = Origin.render(original)
        assertTrue(Origin.parse(rendered) == Right(original))
      },
      test("header name") {
        assertTrue(Origin.Null_.headerName == "origin")
      }
    ),
    suite("Referer")(
      test("parse and render") {
        val result = Referer.parse("https://example.com/page")
        assertTrue(
          result == Right(Referer("https://example.com/page")),
          result.map(_.headerName) == Right("referer")
        )
      }
    ),
    suite("Via")(
      test("parse single") {
        assertTrue(Via.parse("1.1 proxy") == Right(Via(Chunk("1.1 proxy"))))
      },
      test("parse comma-separated") {
        val result = Via.parse("1.0 fred, 1.1 example.com")
        assertTrue(result == Right(Via(Chunk("1.0 fred", "1.1 example.com"))))
      },
      test("parse empty returns Left") {
        assertTrue(Via.parse("").isLeft)
      },
      test("render") {
        val h = Via(Chunk("1.0 fred", "1.1 example.com"))
        assertTrue(Via.render(h) == "1.0 fred, 1.1 example.com")
      },
      test("header name") {
        assertTrue(Via(Chunk("1.1 proxy")).headerName == "via")
      }
    ),
    suite("Forwarded")(
      test("parse and render") {
        val result = Forwarded.parse("for=192.0.2.43")
        assertTrue(
          result == Right(Forwarded("for=192.0.2.43")),
          result.map(_.headerName) == Right("forwarded")
        )
      }
    ),
    suite("MaxForwards")(
      test("parse valid") {
        assertTrue(MaxForwards.parse("10") == Right(MaxForwards(10)))
      },
      test("parse zero") {
        assertTrue(MaxForwards.parse("0") == Right(MaxForwards(0)))
      },
      test("parse negative returns Left") {
        assertTrue(MaxForwards.parse("-1").isLeft)
      },
      test("parse non-numeric returns Left") {
        assertTrue(MaxForwards.parse("abc").isLeft)
      },
      test("render") {
        assertTrue(MaxForwards.render(MaxForwards(5)) == "5")
      },
      test("header name") {
        assertTrue(MaxForwards(0).headerName == "max-forwards")
      }
    ),
    suite("From")(
      test("parse and render") {
        val result = From.parse("user@example.com")
        assertTrue(
          result == Right(From("user@example.com")),
          result.map(_.headerName) == Right("from")
        )
      }
    )
  )
}
