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

package zio.http

import zio.test._
import zio.blocks.chunk.Chunk
import zio.blocks.streams.Stream

object BodySpec extends HttpModelBaseSpec {
  def spec: Spec[TestEnvironment, Any] = suite("Body")(
    suite("empty")(
      test("toChunk returns empty chunk") {
        assertTrue(Body.empty.toChunk == Chunk.empty[Byte])
      },
      test("length is Some(0L)") {
        assertTrue(Body.empty.length == Some(0L))
      },
      test("isEmpty is true") {
        assertTrue(Body.empty.isEmpty)
      },
      test("nonEmpty is false") {
        assertTrue(!Body.empty.nonEmpty)
      },
      test("has default content type") {
        assertTrue(Body.empty.contentType == ContentType.`application/octet-stream`)
      },
      test("toArray is empty array") {
        assertTrue(Body.empty.toArray.length == 0)
      }
    ),
    suite("fromChunk")(
      test("toChunk returns the chunk") {
        val chunk = Chunk[Byte](10, 20, 30)
        val body  = Body.fromChunk(chunk)
        assertTrue(body.toChunk == chunk)
      },
      test("toStream.runCollect returns Right(chunk)") {
        val chunk  = Chunk[Byte](10, 20, 30)
        val body   = Body.fromChunk(chunk)
        val result = body.toStream.runCollect
        assertTrue(result == Right(chunk))
      },
      test("length returns Some(chunk.length.toLong)") {
        val chunk = Chunk[Byte](1, 2, 3, 4, 5)
        val body  = Body.fromChunk(chunk)
        assertTrue(body.length == Some(5L))
      },
      test("preserves content type") {
        val ct   = ContentType.`text/html`
        val body = Body.fromChunk(Chunk[Byte](1), ct)
        assertTrue(body.contentType == ct)
      },
      test("defaults to application/octet-stream") {
        val body = Body.fromChunk(Chunk[Byte](1))
        assertTrue(body.contentType == ContentType.`application/octet-stream`)
      }
    ),
    suite("fromArray")(
      test("toChunk returns chunk with those bytes") {
        val bytes = Array[Byte](1, 2, 3)
        val body  = Body.fromArray(bytes)
        assertTrue(body.toChunk == Chunk[Byte](1, 2, 3))
      },
      test("toArray returns array matching bytes") {
        val bytes = Array[Byte](1, 2, 3)
        val body  = Body.fromArray(bytes)
        val arr   = body.toArray
        assertTrue(arr(0) == 1.toByte, arr(1) == 2.toByte, arr(2) == 3.toByte)
      },
      test("length returns Some(bytes.length.toLong)") {
        val bytes = Array[Byte](1, 2, 3)
        val body  = Body.fromArray(bytes)
        assertTrue(body.length == Some(3L))
      },
      test("wraps array without defensive copy — mutation is visible") {
        val bytes = Array[Byte](1, 2, 3)
        val body  = Body.fromArray(bytes)
        bytes(0) = 99.toByte
        assertTrue(body.toChunk(0) == 99.toByte)
      },
      test("preserves content type") {
        val ct   = ContentType.`application/json`
        val body = Body.fromArray(Array[Byte](1), ct)
        assertTrue(body.contentType == ct)
      },
      test("defaults to application/octet-stream") {
        val body = Body.fromArray(Array[Byte](1))
        assertTrue(body.contentType == ContentType.`application/octet-stream`)
      }
    ),
    suite("fromString")(
      test("asString returns the original string") {
        assertTrue(Body.fromString("hello").asString() == "hello")
      },
      test("sets content type to text/plain with UTF-8 charset") {
        val body = Body.fromString("hello")
        assertTrue(
          body.contentType.mediaType == zio.blocks.mediatype.MediaTypes.text.`plain`,
          body.contentType.charset == Some(Charset.UTF8)
        )
      },
      test("length returns Some(5L) for 'hello'") {
        assertTrue(Body.fromString("hello").length == Some(5L))
      },
      test("encodes with specified charset") {
        val body = Body.fromString("hello", Charset.ASCII)
        assertTrue(
          body.asString(Charset.ASCII) == "hello",
          body.contentType.charset == Some(Charset.ASCII)
        )
      },
      test("round-trips unicode") {
        val original = "Hello, 世界! 🌍"
        val body     = Body.fromString(original)
        assertTrue(body.asString() == original)
      }
    ),
    suite("fromStream")(
      test("toStream returns the stream") {
        val chunk  = Chunk[Byte](1, 2, 3)
        val stream = Stream.fromChunk(chunk)
        val body   = Body.fromStream(stream)
        assertTrue(body.toStream eq stream)
      },
      test("toChunk collects stream contents") {
        val chunk = Chunk[Byte](1, 2, 3)
        val body  = Body.fromStream(Stream.fromChunk(chunk))
        assertTrue(body.toChunk == chunk)
      },
      test("length delegates to stream.knownLength — Some for fromChunk") {
        val chunk = Chunk[Byte](1, 2, 3)
        val body  = Body.fromStream(Stream.fromChunk(chunk))
        assertTrue(body.length == Some(3L))
      },
      test("length returns None for fromIterable") {
        val body = Body.fromStream(Stream.fromIterable(List[Byte](1, 2, 3)))
        assertTrue(body.length == None)
      },
      test("preserves content type") {
        val ct   = ContentType.`application/json`
        val body = Body.fromStream(Stream.fromChunk(Chunk[Byte](1)), ct)
        assertTrue(body.contentType == ct)
      },
      test("defaults to application/octet-stream") {
        val body = Body.fromStream(Stream.fromChunk(Chunk[Byte](1)))
        assertTrue(body.contentType == ContentType.`application/octet-stream`)
      }
    ),
    suite("accessors")(
      test("toStream returns Stream[Nothing, Byte]") {
        val body: Body                    = Body.fromChunk(Chunk[Byte](1, 2))
        val stream: Stream[Nothing, Byte] = body.toStream
        assertTrue(stream.runCollect == Right(Chunk[Byte](1, 2)))
      },
      test("toChunk returns Chunk[Byte]") {
        val body: Body         = Body.fromChunk(Chunk[Byte](1, 2))
        val chunk: Chunk[Byte] = body.toChunk
        assertTrue(chunk == Chunk[Byte](1, 2))
      },
      test("toArray returns Array[Byte]") {
        val body: Body       = Body.fromChunk(Chunk[Byte](1, 2))
        val arr: Array[Byte] = body.toArray
        assertTrue(arr.length == 2, arr(0) == 1.toByte, arr(1) == 2.toByte)
      },
      test("asString decodes with charset") {
        val body = Body.fromString("hello world")
        assertTrue(body.asString() == "hello world")
      },
      test("isEmpty is correct") {
        assertTrue(Body.empty.isEmpty, !Body.fromChunk(Chunk[Byte](1)).isEmpty)
      },
      test("nonEmpty is correct") {
        assertTrue(!Body.empty.nonEmpty, Body.fromChunk(Chunk[Byte](1)).nonEmpty)
      },
      test("contentType accessor") {
        val body = Body.fromChunk(Chunk[Byte](1), ContentType.`application/json`)
        assertTrue(body.contentType == ContentType.`application/json`)
      }
    ),
    suite("equals and hashCode")(
      test("empty body equality") {
        assertTrue(Body.empty == Body.empty)
      },
      test("fromChunk with same chunk and contentType are equal") {
        val c1 = Chunk[Byte](1, 2, 3)
        val ct = ContentType.`application/json`
        assertTrue(Body.fromChunk(c1, ct) == Body.fromChunk(c1, ct))
      },
      test("different content types are not equal") {
        val c1 = Chunk[Byte](1, 2, 3)
        assertTrue(Body.fromChunk(c1, ContentType.`application/json`) != Body.fromChunk(c1, ContentType.`text/plain`))
      },
      test("fromChunk and fromArray with same bytes are equal") {
        val c1 = Chunk[Byte](1, 2, 3)
        val ct = ContentType.`application/json`
        assertTrue(Body.fromChunk(c1, ct) == Body.fromArray(c1.toArray, ct))
      },
      test("fromStream(fromIterable) bodies are not equal even with same data") {
        val ct = ContentType.`application/json`
        val a  = Body.fromStream(Stream.fromIterable(List[Byte](1)), ct)
        val b  = Body.fromStream(Stream.fromIterable(List[Byte](1)), ct)
        assertTrue(a != b)
      },
      test("fromStream(fromChunk) equals fromChunk with same data") {
        val c1 = Chunk[Byte](1, 2, 3)
        val ct = ContentType.`application/json`
        assertTrue(Body.fromStream(Stream.fromChunk(c1), ct) == Body.fromChunk(c1, ct))
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
      test("includes length and content type for known-length body") {
        val body = Body.fromString("hello")
        val str  = body.toString
        assertTrue(str.contains("length=5"), str.contains("contentType="))
      },
      test("shows unknown for stream without known length") {
        val body = Body.fromStream(Stream.fromIterable(List[Byte](1)))
        val str  = body.toString
        assertTrue(str.contains("length=unknown"))
      }
    )
  )
}
