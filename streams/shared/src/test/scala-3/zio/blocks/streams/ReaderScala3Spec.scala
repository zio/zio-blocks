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

object ReaderScala3Spec extends StreamsBaseSpec {

  def spec: Spec[TestEnvironment, Any] = suite("Reader (Scala 3)")(
    suite("readBytes")(
      suite("compile-time evidence soundness")(
        test("readBytes compiles for Reader[Byte]") {
          assertTrue(scala.compiletime.testing.typeChecks("""
import zio.blocks.chunk.Chunk
import zio.blocks.streams.io.Reader
val r: Reader[Byte] = Reader.fromChunk(Chunk[Byte](1, 2, 3))
val buf = new Array[Byte](4)
r.readBytes(buf, 0, 4)
"""))
        },
        test("readBytes does not compile for Reader[Int]") {
          assertTrue(!scala.compiletime.testing.typeChecks("""
import zio.blocks.chunk.Chunk
import zio.blocks.streams.io.Reader
val r: Reader[Int] = Reader.fromChunk(Chunk[Int](1, 2, 3))
val buf = new Array[Byte](4)
r.readBytes(buf, 0, 4)
"""))
        },
        test("readBytes does not compile for Reader[Char]") {
          assertTrue(!scala.compiletime.testing.typeChecks("""
import zio.blocks.chunk.Chunk
import zio.blocks.streams.io.Reader
val r: Reader[Char] = Reader.fromChunk(Chunk[Char]('a', 'b'))
val buf = new Array[Byte](4)
r.readBytes(buf, 0, 4)
"""))
        },
        test("Reader[Int] compile error mentions byte evidence") {
          val errors = scala.compiletime.testing.typeCheckErrors("""
import zio.blocks.chunk.Chunk
import zio.blocks.streams.io.Reader
val r: Reader[Int] = Reader.fromChunk(Chunk[Int](1, 2, 3))
val buf = new Array[Byte](4)
r.readBytes(buf, 0, 4)
""")
          assertTrue(errors.exists(_.message.contains("<:< Byte")))
        }
      ),
      suite("scope audit")(
        test("readByte remains unconstrained - deferred: existential callers in Sink/NioSinks") {
          // Sink.fromOutputStream, NioSinks.fromByteBuffer/fromChannel call readByte() on Reader[?].
          assertTrue(scala.compiletime.testing.typeChecks("""
import zio.blocks.chunk.Chunk
import zio.blocks.streams.io.Reader
val r: Reader[Int] = Reader.fromChunk(Chunk[Int](1, 2, 3))
r.readByte()
"""))
        },
        test("readAll remains polymorphic - correct by design via jvmType dispatch") {
          assertTrue(scala.compiletime.testing.typeChecks("""
import zio.blocks.chunk.Chunk
import zio.blocks.streams.io.Reader
val r: Reader[Int] = Reader.fromChunk(Chunk[Int](1, 2, 3))
r.readAll[Int]()
"""))
        },
        test("readN remains polymorphic - correct by design via jvmType dispatch") {
          assertTrue(scala.compiletime.testing.typeChecks("""
import zio.blocks.chunk.Chunk
import zio.blocks.streams.io.Reader
val r: Reader[Int] = Reader.fromChunk(Chunk[Int](1, 2, 3))
r.readN[Int](2)
"""))
        },
        test("skip remains unconstrained - independent of readBytes") {
          assertTrue(scala.compiletime.testing.typeChecks("""
import zio.blocks.chunk.Chunk
import zio.blocks.streams.io.Reader
val r: Reader[Int] = Reader.fromChunk(Chunk[Int](1, 2, 3))
r.skip(2)
"""))
        }
      )
    )
  )
}
