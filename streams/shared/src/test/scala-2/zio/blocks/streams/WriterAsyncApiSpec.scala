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

import zio.blocks.chunk.Chunk
import zio.blocks.streams.io.Writer
import zio.test._

object WriterAsyncApiSpec extends StreamsBaseSpec {
  private def deferredOperations(): Unit = {
    val writer = Writer.single[Int]
    val all    = (
      writer.closeAsync(),
      writer.failAsync(new Exception),
      writer.isClosedAsync,
      writer.jvmTypeAsync,
      writer.writeableAsync(),
      writer.writeAsync(1),
      writer.writeAllAsync(Chunk(1)),
      writer.writeIntAsync(1)
    )
    val primitives = (
      Writer.single[Boolean].writeBooleanAsync(true),
      Writer.single[Byte].writeByteAsync(1.toByte),
      Writer.single[Byte].writeBytesAsync(Array[Byte](1), 0, 1),
      Writer.single[Char].writeCharAsync('x'),
      Writer.single[Double].writeDoubleAsync(1.0),
      Writer.single[Float].writeFloatAsync(1.0f),
      Writer.single[Long].writeLongAsync(1L),
      Writer.single[Short].writeShortAsync(1.toShort)
    )
    val _ = (all, primitives)
  }

  def spec = suite("Cross-platform Writer async API")(test("Scala 2 deferred operation family is externally callable") {
    deferredOperations()
    assertTrue(true)
  })
}
