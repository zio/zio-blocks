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

package zio.blocks.streams

import zio.blocks.async._
import zio.blocks.chunk.Chunk
import zio.blocks.streams.io.Reader
import zio.test._

/**
 * JVM-only coverage for synchronous and protected asynchronous sink dispatch.
 */
object SinkAsyncLongDispatchJvmSpec extends StreamsBaseSpec {
  def spec = suite("asynchronous Long sink JVM dispatch")(
    test("dispatches sync, mapped async Int, and protected async Int readers without boxing") {
      val sink   = Sink.foldLeftAsync[Int, Long](0L)((acc, value) => Async.succeed(acc + value))
      val sync   = Reader.fromChunk(Chunk(1, 2, 3))
      val mapped = new Reader.MappedAsyncEffectIntInt(
        Reader.fromChunk(Chunk(1, 2, 3)).toAsync,
        value => Async.succeed(value + 1)
      )
      val protectedReader = Reader.fromChunk(Chunk(1, 2, 3)).toAsync
      val syncResult      = sink.drain(sync)
      val mappedResult    = sink.drain(mapped).block
      val protectedResult = Sink.readerCallbackAsync(protectedReader)(reader => sink.drain(reader)).block
      assertTrue(syncResult == 6L, mappedResult == 9L, protectedResult == 6L)
    }
  )
}
