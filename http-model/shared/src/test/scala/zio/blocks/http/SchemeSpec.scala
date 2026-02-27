package zio.blocks.http

import zio.test._

object SchemeSpec extends HttpModelBaseSpec {
  def spec: Spec[TestEnvironment, Any] = suite("Scheme")(
    suite("HTTP")(
      test("has correct properties") {
        assertTrue(
          Scheme.HTTP.text == "http",
          Scheme.HTTP.defaultPort == Some(80),
          Scheme.HTTP.isSecure == false,
          Scheme.HTTP.isWebSocket == false
        )
      }
    ),
    suite("HTTPS")(
      test("has correct properties") {
        assertTrue(
          Scheme.HTTPS.text == "https",
          Scheme.HTTPS.defaultPort == Some(443),
          Scheme.HTTPS.isSecure == true,
          Scheme.HTTPS.isWebSocket == false
        )
      }
    ),
    suite("WS")(
      test("has correct properties") {
        assertTrue(
          Scheme.WS.text == "ws",
          Scheme.WS.defaultPort == Some(80),
          Scheme.WS.isSecure == false,
          Scheme.WS.isWebSocket == true
        )
      }
    ),
    suite("WSS")(
      test("has correct properties") {
        assertTrue(
          Scheme.WSS.text == "wss",
          Scheme.WSS.defaultPort == Some(443),
          Scheme.WSS.isSecure == true,
          Scheme.WSS.isWebSocket == true
        )
      }
    ),
    suite("Custom")(
      test("has correct properties") {
        val ftp = Scheme.Custom("ftp")
        assertTrue(
          ftp.text == "ftp",
          ftp.defaultPort == None,
          ftp.isSecure == false,
          ftp.isWebSocket == false
        )
      }
    ),
    suite("fromString")(
      test("resolves HTTP case-insensitively") {
        assertTrue(Scheme.fromString("HTTP") == Scheme.HTTP)
      },
      test("resolves https") {
        assertTrue(Scheme.fromString("https") == Scheme.HTTPS)
      },
      test("resolves ws") {
        assertTrue(Scheme.fromString("ws") == Scheme.WS)
      },
      test("resolves wss") {
        assertTrue(Scheme.fromString("WSS") == Scheme.WSS)
      },
      test("returns Custom for unknown scheme") {
        assertTrue(Scheme.fromString("ftp") == Scheme.Custom("ftp"))
      }
    ),
    suite("render")(
      test("returns the scheme text") {
        assertTrue(
          Scheme.render(Scheme.HTTP) == "http",
          Scheme.render(Scheme.HTTPS) == "https",
          Scheme.render(Scheme.WS) == "ws",
          Scheme.render(Scheme.WSS) == "wss"
        )
      }
    ),
    suite("toString")(
      test("returns the scheme text") {
        assertTrue(
          Scheme.HTTP.toString == "http",
          Scheme.HTTPS.toString == "https"
        )
      }
    )
  )
}
