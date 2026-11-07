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

import zio.test._

object WriterAsyncApiSpec extends StreamsBaseSpec {
  def spec = suite("Cross-platform Writer async API")(
    test("deferred operation family is externally callable") {
      assertTrue(scala.compiletime.testing.typeChecks("""
        import zio.blocks.chunk.Chunk
        import zio.blocks.streams.io.Writer
        val w = Writer.single[Int]
        val all = (w.closeAsync(), w.failAsync(new Exception), w.isClosedAsync, w.jvmTypeAsync,
          w.writeableAsync(), w.writeAsync(1), w.writeAllAsync(Chunk(1)), w.writeIntAsync(1))
        val primitives = (Writer.single[Boolean].writeBooleanAsync(true),
          Writer.single[Byte].writeByteAsync(1), Writer.single[Byte].writeBytesAsync(Array[Byte](1), 0, 1),
          Writer.single[Char].writeCharAsync('x'), Writer.single[Double].writeDoubleAsync(1.0),
          Writer.single[Float].writeFloatAsync(1.0f), Writer.single[Long].writeLongAsync(1L),
          Writer.single[Short].writeShortAsync(1))
      """))
    },
    test("async composition operations remain absent") {
      val concatErrors = scala.compiletime.testing.typeCheckErrors("""
        import zio.blocks.async.Async
        import zio.blocks.streams.io.Writer
        Writer.single[Int].concatAsync(() => Async.succeed(Writer.single[Int]))
      """)
      val contramapErrors = scala.compiletime.testing.typeCheckErrors("""
        import zio.blocks.async.Async
        import zio.blocks.streams.io.Writer
        Writer.single[Int].contramapAsync[String](s => Async.succeed(s.length))
      """)
      assertTrue(concatErrors.nonEmpty, contramapErrors.nonEmpty)
    }
  )
}
