package zio.http.headers

import zio.test._
import zio.blocks.chunk.Chunk

object ConnectionHeadersSpec extends ZIOSpecDefault {
  def spec: Spec[TestEnvironment, Any] = suite("ConnectionHeaders")(
    suite("Connection")(
      test("parse close") {
        assertTrue(Connection.parse("close") == Right(Connection.Close))
      },
      test("parse keep-alive") {
        assertTrue(Connection.parse("keep-alive") == Right(Connection.KeepAlive))
      },
      test("parse case-insensitive") {
        assertTrue(Connection.parse("Close") == Right(Connection.Close))
      },
      test("parse other") {
        assertTrue(Connection.parse("upgrade") == Right(Connection.Other("upgrade")))
      },
      test("render close") {
        assertTrue(Connection.render(Connection.Close) == "close")
      },
      test("render keep-alive") {
        assertTrue(Connection.render(Connection.KeepAlive) == "keep-alive")
      },
      test("header name") {
        assertTrue(Connection.Close.headerName == "connection")
      }
    ),
    suite("Upgrade")(
      test("parse and render") {
        val result = Upgrade.parse("websocket")
        assertTrue(
          result == Right(Upgrade("websocket")),
          result.map(_.headerName) == Right("upgrade")
        )
      }
    ),
    suite("Te")(
      test("parse and render") {
        val result = Te.parse("trailers")
        assertTrue(
          result == Right(Te("trailers")),
          result.map(_.headerName) == Right("te")
        )
      }
    ),
    suite("Trailer")(
      test("parse single") {
        assertTrue(Trailer.parse("Expires") == Right(Trailer(Chunk("Expires"))))
      },
      test("parse comma-separated") {
        val result = Trailer.parse("Expires, Cache-Control")
        assertTrue(result == Right(Trailer(Chunk("Expires", "Cache-Control"))))
      },
      test("parse empty returns Left") {
        assertTrue(Trailer.parse("").isLeft)
      },
      test("render") {
        val h = Trailer(Chunk("Expires", "Cache-Control"))
        assertTrue(Trailer.render(h) == "Expires, Cache-Control")
      },
      test("header name") {
        assertTrue(Trailer(Chunk("X")).headerName == "trailer")
      }
    ),
    suite("TransferEncoding")(
      test("parse chunked") {
        assertTrue(TransferEncoding.parse("chunked") == Right(TransferEncoding.Chunked))
      },
      test("parse gzip") {
        assertTrue(TransferEncoding.parse("gzip") == Right(TransferEncoding.GZip))
      },
      test("parse compress") {
        assertTrue(TransferEncoding.parse("compress") == Right(TransferEncoding.Compress))
      },
      test("parse deflate") {
        assertTrue(TransferEncoding.parse("deflate") == Right(TransferEncoding.Deflate))
      },
      test("parse identity") {
        assertTrue(TransferEncoding.parse("identity") == Right(TransferEncoding.Identity))
      },
      test("parse multiple") {
        val result = TransferEncoding.parse("chunked,gzip")
        assertTrue(
          result == Right(TransferEncoding.Multiple(Chunk(TransferEncoding.Chunked, TransferEncoding.GZip)))
        )
      },
      test("parse invalid returns Left") {
        assertTrue(TransferEncoding.parse("unknown").isLeft)
      },
      test("render chunked") {
        assertTrue(TransferEncoding.render(TransferEncoding.Chunked) == "chunked")
      },
      test("render multiple") {
        val m = TransferEncoding.Multiple(Chunk(TransferEncoding.Chunked, TransferEncoding.GZip))
        assertTrue(TransferEncoding.render(m) == "chunked, gzip")
      },
      test("round-trip") {
        val rendered = TransferEncoding.render(TransferEncoding.Deflate)
        assertTrue(TransferEncoding.parse(rendered) == Right(TransferEncoding.Deflate))
      },
      test("header name") {
        assertTrue(TransferEncoding.Chunked.headerName == "transfer-encoding")
      }
    )
  )
}
