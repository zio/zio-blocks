package zio.http.headers

import zio.test._
import zio.blocks.chunk.Chunk

object WebSocketHeadersSpec extends ZIOSpecDefault {
  def spec: Spec[TestEnvironment, Any] = suite("WebSocketHeaders")(
    suite("SecWebSocketAccept")(
      test("parse and render") {
        val result = SecWebSocketAccept.parse("s3pPLMBiTxaQ9kYGzzhZRbK+xOo=")
        assertTrue(
          result == Right(SecWebSocketAccept("s3pPLMBiTxaQ9kYGzzhZRbK+xOo=")),
          result.map(_.headerName) == Right("sec-websocket-accept")
        )
      },
      test("render") {
        assertTrue(SecWebSocketAccept.render(SecWebSocketAccept("abc=")) == "abc=")
      }
    ),
    suite("SecWebSocketExtensions")(
      test("parse and render") {
        val result = SecWebSocketExtensions.parse("permessage-deflate")
        assertTrue(
          result == Right(SecWebSocketExtensions("permessage-deflate")),
          result.map(_.headerName) == Right("sec-websocket-extensions")
        )
      },
      test("render") {
        assertTrue(SecWebSocketExtensions.render(SecWebSocketExtensions("permessage-deflate")) == "permessage-deflate")
      }
    ),
    suite("SecWebSocketKey")(
      test("parse and render") {
        val result = SecWebSocketKey.parse("dGhlIHNhbXBsZSBub25jZQ==")
        assertTrue(
          result == Right(SecWebSocketKey("dGhlIHNhbXBsZSBub25jZQ==")),
          result.map(_.headerName) == Right("sec-websocket-key")
        )
      },
      test("render") {
        assertTrue(SecWebSocketKey.render(SecWebSocketKey("key123")) == "key123")
      }
    ),
    suite("SecWebSocketLocation")(
      test("parse and render") {
        val result = SecWebSocketLocation.parse("ws://example.com/chat")
        assertTrue(
          result == Right(SecWebSocketLocation("ws://example.com/chat")),
          result.map(_.headerName) == Right("sec-websocket-location")
        )
      },
      test("render") {
        assertTrue(SecWebSocketLocation.render(SecWebSocketLocation("ws://test")) == "ws://test")
      }
    ),
    suite("SecWebSocketOrigin")(
      test("parse and render") {
        val result = SecWebSocketOrigin.parse("http://example.com")
        assertTrue(
          result == Right(SecWebSocketOrigin("http://example.com")),
          result.map(_.headerName) == Right("sec-websocket-origin")
        )
      },
      test("render") {
        assertTrue(SecWebSocketOrigin.render(SecWebSocketOrigin("http://test")) == "http://test")
      }
    ),
    suite("SecWebSocketProtocol")(
      test("parse single") {
        assertTrue(SecWebSocketProtocol.parse("chat") == Right(SecWebSocketProtocol(Chunk("chat"))))
      },
      test("parse comma-separated") {
        val result = SecWebSocketProtocol.parse("chat, superchat")
        assertTrue(result == Right(SecWebSocketProtocol(Chunk("chat", "superchat"))))
      },
      test("parse empty returns Left") {
        assertTrue(SecWebSocketProtocol.parse("").isLeft)
      },
      test("render") {
        val h = SecWebSocketProtocol(Chunk("chat", "superchat"))
        assertTrue(SecWebSocketProtocol.render(h) == "chat, superchat")
      },
      test("header name") {
        assertTrue(SecWebSocketProtocol(Chunk("chat")).headerName == "sec-websocket-protocol")
      }
    ),
    suite("SecWebSocketVersion")(
      test("parse and render") {
        val result = SecWebSocketVersion.parse("13")
        assertTrue(
          result == Right(SecWebSocketVersion("13")),
          result.map(_.headerName) == Right("sec-websocket-version")
        )
      },
      test("render") {
        assertTrue(SecWebSocketVersion.render(SecWebSocketVersion("13")) == "13")
      }
    )
  )
}
