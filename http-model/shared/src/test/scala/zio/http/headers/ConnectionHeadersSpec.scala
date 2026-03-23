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
      },
      test("render other") {
        assertTrue(Connection.render(Connection.Other("upgrade")) == "upgrade")
      }
    ),
    suite("Upgrade")(
      test("parse and render") {
        val result = Upgrade.parse("websocket")
        assertTrue(
          result == Right(Upgrade("websocket")),
          result.map(_.headerName) == Right("upgrade")
        )
      },
      test("render") {
        assertTrue(Upgrade.render(Upgrade("websocket")) == "websocket")
      }
    ),
    suite("Te")(
      test("parse and render") {
        val result = Te.parse("trailers")
        assertTrue(
          result == Right(Te("trailers")),
          result.map(_.headerName) == Right("te")
        )
      },
      test("render") {
        assertTrue(Te.render(Te("trailers")) == "trailers")
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
