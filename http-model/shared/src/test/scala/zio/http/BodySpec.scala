package zio.http

import zio.test._
import zio.blocks.chunk.Chunk

object BodySpec extends HttpModelBaseSpec {
  def spec: Spec[TestEnvironment, Any] = suite("Body")(
    suite("empty")(
      test("has length 0") {
        assertTrue(Body.empty.length == 0)
      },
      test("is empty") {
        assertTrue(Body.empty.isEmpty)
      },
      test("is not nonEmpty") {
        assertTrue(!Body.empty.nonEmpty)
      },
      test("has no content type") {
        assertTrue(Body.empty.contentType.isEmpty)
      },
      test("toArray is empty array for empty body") {
        assertTrue(Body.empty.toArray.length == 0)
      }
    ),
    suite("fromArray")(
      test("wraps bytes correctly") {
        val bytes = Array[Byte](1, 2, 3)
        val body  = Body.fromArray(bytes)
        assertTrue(
          body.length == 3,
          body.toArray(0) == 1.toByte,
          body.toArray(1) == 2.toByte,
          body.toArray(2) == 3.toByte
        )
      },
      test("makes defensive copy") {
        val bytes = Array[Byte](1, 2, 3)
        val body  = Body.fromArray(bytes)
        bytes(0) = 99.toByte
        assertTrue(body.toArray(0) == 1.toByte)
      },
      test("preserves content type") {
        val ct   = Some(ContentType.`application/json`)
        val body = Body.fromArray(Array[Byte](1), ct)
        assertTrue(body.contentType == ct)
      },
      test("defaults to None content type") {
        val body = Body.fromArray(Array[Byte](1))
        assertTrue(body.contentType.isEmpty)
      }
    ),
    suite("fromChunk")(
      test("converts chunk to array") {
        val chunk = Chunk[Byte](10, 20, 30)
        val body  = Body.fromChunk(chunk)
        assertTrue(
          body.length == 3,
          body.toArray(0) == 10.toByte,
          body.toArray(1) == 20.toByte,
          body.toArray(2) == 30.toByte
        )
      },
      test("preserves content type") {
        val ct   = Some(ContentType.`text/html`)
        val body = Body.fromChunk(Chunk[Byte](1), ct)
        assertTrue(body.contentType == ct)
      }
    ),
    suite("fromString")(
      test("encodes with UTF-8 by default") {
        val body = Body.fromString("hello")
        assertTrue(body.asString() == "hello")
      },
      test("sets content type to text/plain with charset") {
        val body = Body.fromString("hello")
        assertTrue(
          body.contentType.isDefined,
          body.contentType.get.mediaType == zio.blocks.mediatype.MediaTypes.text.`plain`,
          body.contentType.get.charset == Some(Charset.UTF8)
        )
      },
      test("encodes with specified charset") {
        val body = Body.fromString("hello", Charset.ASCII)
        assertTrue(
          body.asString(Charset.ASCII) == "hello",
          body.contentType.get.charset == Some(Charset.ASCII)
        )
      }
    ),
    suite("asString")(
      test("decodes bytes to string with default charset") {
        val body = Body.fromString("hello world")
        assertTrue(body.asString() == "hello world")
      },
      test("round-trips with fromString") {
        val original = "Hello, 世界! 🌍"
        val body     = Body.fromString(original)
        assertTrue(body.asString() == original)
      }
    ),
    suite("asChunk")(
      test("returns correct chunk") {
        val bytes = Array[Byte](1, 2, 3)
        val body  = Body.fromArray(bytes)
        val chunk = body.asChunk
        assertTrue(
          chunk.length == 3,
          chunk(0) == 1.toByte,
          chunk(1) == 2.toByte,
          chunk(2) == 3.toByte
        )
      }
    ),
    suite("length / isEmpty / nonEmpty")(
      test("length returns correct size") {
        val body = Body.fromArray(Array[Byte](1, 2, 3, 4, 5))
        assertTrue(body.length == 5)
      },
      test("isEmpty for empty body") {
        assertTrue(Body.empty.isEmpty)
      },
      test("nonEmpty for non-empty body") {
        val body = Body.fromArray(Array[Byte](1))
        assertTrue(body.nonEmpty)
      },
      test("isEmpty is false for non-empty body") {
        val body = Body.fromString("x")
        assertTrue(!body.isEmpty)
      }
    ),
    suite("equals and hashCode")(
      test("two bodies with same bytes are equal") {
        val a = Body.fromArray(Array[Byte](1, 2, 3))
        val b = Body.fromArray(Array[Byte](1, 2, 3))
        assertTrue(a == b)
      },
      test("two bodies with different bytes are not equal") {
        val a = Body.fromArray(Array[Byte](1, 2, 3))
        val b = Body.fromArray(Array[Byte](4, 5, 6))
        assertTrue(a != b)
      },
      test("two bodies with same bytes but different content types are not equal") {
        val a = Body.fromArray(Array[Byte](1, 2, 3), Some(ContentType.`application/json`))
        val b = Body.fromArray(Array[Byte](1, 2, 3), Some(ContentType.`text/plain`))
        assertTrue(a != b)
      },
      test("equal bodies have same hashCode") {
        val a = Body.fromArray(Array[Byte](1, 2, 3))
        val b = Body.fromArray(Array[Byte](1, 2, 3))
        assertTrue(a.hashCode == b.hashCode)
      },
      test("body is not equal to non-Body") {
        val body = Body.fromArray(Array[Byte](1))
        assertTrue(!body.equals("not a body"))
      }
    ),
    suite("toString")(
      test("includes length and content type") {
        val body = Body.fromString("hello")
        val str  = body.toString
        assertTrue(str.contains("length=5"), str.contains("contentType="))
      }
    ),
    suite("content type propagation")(
      test("fromArray with content type propagates to body") {
        val ct   = ContentType.`application/octet-stream`
        val body = Body.fromArray(Array[Byte](0, 1), Some(ct))
        assertTrue(body.contentType == Some(ct))
      },
      test("fromChunk with content type propagates to body") {
        val ct   = ContentType.`application/json`
        val body = Body.fromChunk(Chunk[Byte](1, 2), Some(ct))
        assertTrue(body.contentType == Some(ct))
      }
    )
  )
}
