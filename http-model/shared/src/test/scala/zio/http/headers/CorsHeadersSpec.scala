package zio.http.headers

import zio.test._
import zio.blocks.chunk.Chunk
import zio.http.Method

object CorsHeadersSpec extends ZIOSpecDefault {
  def spec: Spec[TestEnvironment, Any] = suite("CorsHeaders")(
    suite("AccessControlAllowOrigin")(
      test("parse *") {
        val result = AccessControlAllowOrigin.parse("*")
        assertTrue(
          result == Right(AccessControlAllowOrigin.All),
          result.map(_.headerName) == Right("access-control-allow-origin")
        )
      },
      test("parse specific origin") {
        val result = AccessControlAllowOrigin.parse("https://example.com")
        assertTrue(result == Right(AccessControlAllowOrigin.Specific("https://example.com")))
      },
      test("parse empty returns Left") {
        assertTrue(AccessControlAllowOrigin.parse("").isLeft)
      },
      test("render *") {
        assertTrue(AccessControlAllowOrigin.render(AccessControlAllowOrigin.All) == "*")
      },
      test("render specific") {
        assertTrue(
          AccessControlAllowOrigin.render(AccessControlAllowOrigin.Specific("https://example.com")) ==
            "https://example.com"
        )
      },
      test("round-trip") {
        val original = AccessControlAllowOrigin.Specific("https://test.com")
        val rendered = AccessControlAllowOrigin.render(original)
        assertTrue(AccessControlAllowOrigin.parse(rendered) == Right(original))
      }
    ),
    suite("AccessControlAllowMethods")(
      test("parse single method") {
        val result = AccessControlAllowMethods.parse("GET")
        assertTrue(
          result == Right(AccessControlAllowMethods(Chunk(Method.GET))),
          result.map(_.headerName) == Right("access-control-allow-methods")
        )
      },
      test("parse multiple methods") {
        val result = AccessControlAllowMethods.parse("GET, POST, PUT")
        assertTrue(result == Right(AccessControlAllowMethods(Chunk(Method.GET, Method.POST, Method.PUT))))
      },
      test("parse empty returns Left") {
        assertTrue(AccessControlAllowMethods.parse("").isLeft)
      },
      test("parse unknown method returns Left") {
        assertTrue(AccessControlAllowMethods.parse("UNKNOWN").isLeft)
      },
      test("render") {
        val h = AccessControlAllowMethods(Chunk(Method.GET, Method.POST))
        assertTrue(AccessControlAllowMethods.render(h) == "GET, POST")
      },
      test("round-trip") {
        val original = AccessControlAllowMethods(Chunk(Method.DELETE, Method.PATCH))
        val rendered = AccessControlAllowMethods.render(original)
        assertTrue(AccessControlAllowMethods.parse(rendered) == Right(original))
      }
    ),
    suite("AccessControlAllowHeaders")(
      test("parse single header") {
        val result = AccessControlAllowHeaders.parse("Content-Type")
        assertTrue(
          result == Right(AccessControlAllowHeaders(Chunk("Content-Type"))),
          result.map(_.headerName) == Right("access-control-allow-headers")
        )
      },
      test("parse multiple headers") {
        val result = AccessControlAllowHeaders.parse("Content-Type, Authorization")
        assertTrue(result == Right(AccessControlAllowHeaders(Chunk("Content-Type", "Authorization"))))
      },
      test("parse empty returns Left") {
        assertTrue(AccessControlAllowHeaders.parse("").isLeft)
      },
      test("render") {
        val h = AccessControlAllowHeaders(Chunk("Accept", "Origin"))
        assertTrue(AccessControlAllowHeaders.render(h) == "Accept, Origin")
      },
      test("round-trip") {
        val original = AccessControlAllowHeaders(Chunk("X-Custom"))
        val rendered = AccessControlAllowHeaders.render(original)
        assertTrue(AccessControlAllowHeaders.parse(rendered) == Right(original))
      }
    ),
    suite("AccessControlAllowCredentials")(
      test("parse true") {
        val result = AccessControlAllowCredentials.parse("true")
        assertTrue(
          result == Right(AccessControlAllowCredentials(true)),
          result.map(_.headerName) == Right("access-control-allow-credentials")
        )
      },
      test("parse false") {
        assertTrue(AccessControlAllowCredentials.parse("false") == Right(AccessControlAllowCredentials(false)))
      },
      test("parse invalid returns Left") {
        assertTrue(AccessControlAllowCredentials.parse("yes").isLeft)
      },
      test("render true") {
        assertTrue(AccessControlAllowCredentials.render(AccessControlAllowCredentials(true)) == "true")
      },
      test("render false") {
        assertTrue(AccessControlAllowCredentials.render(AccessControlAllowCredentials(false)) == "false")
      },
      test("round-trip") {
        val original = AccessControlAllowCredentials(true)
        val rendered = AccessControlAllowCredentials.render(original)
        assertTrue(AccessControlAllowCredentials.parse(rendered) == Right(original))
      }
    ),
    suite("AccessControlExposeHeaders")(
      test("parse single") {
        val result = AccessControlExposeHeaders.parse("X-Custom")
        assertTrue(
          result == Right(AccessControlExposeHeaders(Chunk("X-Custom"))),
          result.map(_.headerName) == Right("access-control-expose-headers")
        )
      },
      test("parse multiple") {
        val result = AccessControlExposeHeaders.parse("X-A, X-B")
        assertTrue(result == Right(AccessControlExposeHeaders(Chunk("X-A", "X-B"))))
      },
      test("parse empty returns Left") {
        assertTrue(AccessControlExposeHeaders.parse("").isLeft)
      },
      test("render") {
        val h = AccessControlExposeHeaders(Chunk("X-A", "X-B"))
        assertTrue(AccessControlExposeHeaders.render(h) == "X-A, X-B")
      },
      test("round-trip") {
        val original = AccessControlExposeHeaders(Chunk("Content-Length"))
        val rendered = AccessControlExposeHeaders.render(original)
        assertTrue(AccessControlExposeHeaders.parse(rendered) == Right(original))
      }
    ),
    suite("AccessControlMaxAge")(
      test("parse valid") {
        val result = AccessControlMaxAge.parse("3600")
        assertTrue(
          result == Right(AccessControlMaxAge(3600L)),
          result.map(_.headerName) == Right("access-control-max-age")
        )
      },
      test("parse zero") {
        assertTrue(AccessControlMaxAge.parse("0") == Right(AccessControlMaxAge(0L)))
      },
      test("parse negative returns Left") {
        assertTrue(AccessControlMaxAge.parse("-1").isLeft)
      },
      test("parse non-numeric returns Left") {
        assertTrue(AccessControlMaxAge.parse("abc").isLeft)
      },
      test("render") {
        assertTrue(AccessControlMaxAge.render(AccessControlMaxAge(7200L)) == "7200")
      },
      test("round-trip") {
        val original = AccessControlMaxAge(600L)
        val rendered = AccessControlMaxAge.render(original)
        assertTrue(AccessControlMaxAge.parse(rendered) == Right(original))
      }
    ),
    suite("AccessControlRequestHeaders")(
      test("parse single") {
        val result = AccessControlRequestHeaders.parse("Content-Type")
        assertTrue(
          result == Right(AccessControlRequestHeaders(Chunk("Content-Type"))),
          result.map(_.headerName) == Right("access-control-request-headers")
        )
      },
      test("parse multiple") {
        val result = AccessControlRequestHeaders.parse("Content-Type, Authorization")
        assertTrue(result == Right(AccessControlRequestHeaders(Chunk("Content-Type", "Authorization"))))
      },
      test("parse empty returns Left") {
        assertTrue(AccessControlRequestHeaders.parse("").isLeft)
      },
      test("render") {
        val h = AccessControlRequestHeaders(Chunk("Accept"))
        assertTrue(AccessControlRequestHeaders.render(h) == "Accept")
      },
      test("round-trip") {
        val original = AccessControlRequestHeaders(Chunk("X-Request-Id"))
        val rendered = AccessControlRequestHeaders.render(original)
        assertTrue(AccessControlRequestHeaders.parse(rendered) == Right(original))
      }
    ),
    suite("AccessControlRequestMethod")(
      test("parse GET") {
        val result = AccessControlRequestMethod.parse("GET")
        assertTrue(
          result == Right(AccessControlRequestMethod(Method.GET)),
          result.map(_.headerName) == Right("access-control-request-method")
        )
      },
      test("parse POST") {
        assertTrue(AccessControlRequestMethod.parse("POST") == Right(AccessControlRequestMethod(Method.POST)))
      },
      test("parse unknown returns Left") {
        assertTrue(AccessControlRequestMethod.parse("UNKNOWN").isLeft)
      },
      test("render") {
        assertTrue(AccessControlRequestMethod.render(AccessControlRequestMethod(Method.DELETE)) == "DELETE")
      },
      test("round-trip") {
        val original = AccessControlRequestMethod(Method.PUT)
        val rendered = AccessControlRequestMethod.render(original)
        assertTrue(AccessControlRequestMethod.parse(rendered) == Right(original))
      }
    )
  )
}
