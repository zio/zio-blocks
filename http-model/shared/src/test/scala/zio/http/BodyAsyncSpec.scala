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

import java.util.concurrent.atomic.AtomicInteger

import _root_.zio.ZIO
import _root_.zio.test._
import zio.blocks.async.{Async, _}
import zio.blocks.chunk.Chunk
import zio.blocks.mediatype.MediaTypes
import zio.blocks.streams.Stream
import zio.blocks.streams.io.Reader

object BodyAsyncSpec extends HttpModelBaseSpec with BodyAsyncSpecPlatform {

  private def run[A](effect: Async[A]) = ZIO.fromFuture(_ => effect.toFuture)

  def spec: Spec[TestEnvironment, Any] = suite("Body async materialization")(
    test("known and streamed bodies materialize without blocking") {
      val known    = Body.fromChunk(Chunk[Byte](1, 2, 3))
      val streamed = Body.fromStream(Stream(1.toByte, 2.toByte, 3.toByte))
      for {
        knownChunk    <- run(known.toChunkAsync)
        streamedChunk <- run(streamed.toChunkAsync)
        streamedArray <- run(streamed.toArrayAsync)
      } yield assertTrue(
        knownChunk == Chunk[Byte](1, 2, 3),
        streamedChunk == Chunk[Byte](1, 2, 3),
        streamedArray.sameElements(Array[Byte](1, 2, 3))
      )
    },
    test("string twins honor explicit and content-type charsets") {
      val body     = Body.fromString("héllo", Charset.ISO_8859_1)
      val declared = Body.fromArray(
        "héllo".getBytes(Charset.ISO_8859_1.name),
        ContentType(MediaTypes.text.`plain`, charset = Some(Charset.ISO_8859_1))
      )
      for {
        explicit <- run(body.asStringAsync(Charset.ISO_8859_1))
        content  <- run(declared.asStringFromContentTypeAsync)
        text     <- run(declared.textAsync)
      } yield assertTrue(explicit == "héllo", content == "héllo", text == "héllo")
    },
    test("native asynchronous body readers are closed exactly once after materialization") {
      val closes = new AtomicInteger
      val reader = Reader
        .fromChunk(Chunk[Byte](1, 2, 3))
        .toAsync
        .withReleaseAsync { () => closes.incrementAndGet(); Async.succeed(()) }
      val body = Body.fromStream(Stream.fromReader[Nothing, Byte](reader))
      for {
        chunk <- run(body.toChunkAsync)
      } yield assertTrue(chunk == Chunk[Byte](1, 2, 3), closes.get == 1)
    }
  )
}
