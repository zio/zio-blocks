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
import zio.test._

object ReadNSpec extends StreamsBaseSpec {

  def spec: Spec[TestEnvironment, Any] = suite("Reader#readN")(
    suite("Byte branch (InputStreamReader)")(
      test("reads bytes from InputStream") {
        val is     = new java.io.ByteArrayInputStream(Array[Byte](1, 2, 3, 4, 5))
        val reader = Stream.compileToReader(Stream.fromInputStreamUnmanaged(is))
        assertTrue(reader.readN[Byte](3) == Chunk[Byte](1, 2, 3))
      },
      test("successive readN calls advance InputStream position") {
        val is     = new java.io.ByteArrayInputStream(Array[Byte](10, 20, 30, 40, 50))
        val reader = Stream.compileToReader(Stream.fromInputStreamUnmanaged(is))
        val c1     = reader.readN[Byte](2)
        val c2     = reader.readN[Byte](2)
        val c3     = reader.readN[Byte](5)
        assertTrue(c1 == Chunk[Byte](10, 20)) &&
        assertTrue(c2 == Chunk[Byte](30, 40)) &&
        assertTrue(c3 == Chunk[Byte](50))
      },
      test("readN on exhausted InputStream returns empty chunk") {
        val is     = new java.io.ByteArrayInputStream(Array[Byte](1, 2))
        val reader = Stream.compileToReader(Stream.fromInputStreamUnmanaged(is))
        reader.readN[Byte](2)
        assertTrue(reader.readN[Byte](1) == Chunk.empty)
      },
      test("readN(0) on InputStream returns empty chunk") {
        val is     = new java.io.ByteArrayInputStream(Array[Byte](1, 2, 3))
        val reader = Stream.compileToReader(Stream.fromInputStreamUnmanaged(is))
        assertTrue(reader.readN[Byte](0) == Chunk.empty)
      }
    ),
    suite("FromRange override")(
      test("readN on range reader returns correct slice") {
        val reader = Stream.compileToReader(Stream.range(0, 10))
        assertTrue(reader.readN[Int](5) == Chunk(0, 1, 2, 3, 4))
      },
      test("successive FromRange readN calls advance position") {
        val reader = Stream.compileToReader(Stream.range(0, 7))
        val c1     = reader.readN[Int](3)
        val c2     = reader.readN[Int](3)
        val c3     = reader.readN[Int](5)
        assertTrue(c1 == Chunk(0, 1, 2)) &&
        assertTrue(c2 == Chunk(3, 4, 5)) &&
        assertTrue(c3 == Chunk(6))
      },
      test("FromRange readN with step > 1") {
        val reader = Stream.compileToReader(Stream.fromRange(Range(0, 20, 3)))
        assertTrue(reader.readN[Int](4) == Chunk(0, 3, 6, 9))
      },
      test("FromRange readN partial (n > remaining)") {
        val reader = Stream.compileToReader(Stream.range(0, 3))
        assertTrue(reader.readN[Int](100) == Chunk(0, 1, 2))
      }
    ),
    suite("Int branch (FromRange)")(
      test("reads at most n elements from range") {
        val reader = Stream.compileToReader(Stream.range(0, 10))
        assertTrue(reader.readN[Int](5) == Chunk(0, 1, 2, 3, 4))
      },
      test("successive readN calls advance position") {
        val reader = Stream.compileToReader(Stream.range(0, 7))
        val c1     = reader.readN[Int](3)
        val c2     = reader.readN[Int](3)
        val c3     = reader.readN[Int](3)
        assertTrue(c1 == Chunk(0, 1, 2)) &&
        assertTrue(c2 == Chunk(3, 4, 5)) &&
        assertTrue(c3 == Chunk(6))
      },
      test("readN(1) returns a single-element chunk") {
        val reader = Stream.compileToReader(Stream.range(0, 3))
        assertTrue(reader.readN[Int](1) == Chunk(0))
      },
      test("readN(100) on 5 elements returns all remaining elements") {
        val reader = Stream.compileToReader(Stream.range(0, 5))
        assertTrue(reader.readN[Int](100) == Chunk(0, 1, 2, 3, 4))
      },
      test("readN after exhausting reader returns empty chunk") {
        val reader = Stream.compileToReader(Stream.range(0, 2))
        val _      = reader.readN[Int](2)
        assertTrue(reader.readN[Int](2) == Chunk.empty)
      }
    ),
    suite("Long branch (FromChunkLong)")(
      test("reads long values from specialized chunk reader") {
        val reader = Stream.compileToReader(Stream.fromChunk(Chunk(1L, 2L, 3L, 4L, 5L)))
        assertTrue(reader.readN[Long](3) == Chunk(1L, 2L, 3L))
      },
      test("large n performs partial read and returns all remaining longs") {
        val reader = Stream.compileToReader(Stream.fromChunk(Chunk(1L, 2L, 3L, 4L, 5L)))
        assertTrue(reader.readN[Long](100) == Chunk(1L, 2L, 3L, 4L, 5L))
      }
    ),
    suite("Float branch (FromChunkFloat)")(
      test("reads float values from specialized chunk reader") {
        val reader = Stream.compileToReader(Stream.fromChunk(Chunk(1.0f, 2.0f, 3.0f)))
        assertTrue(reader.readN[Float](2) == Chunk(1.0f, 2.0f))
      },
      test("readN(0) returns empty chunk") {
        val reader = Stream.compileToReader(Stream.fromChunk(Chunk(1.0f, 2.0f, 3.0f)))
        assertTrue(reader.readN[Float](0) == Chunk.empty)
      }
    ),
    suite("Double branch (FromChunkDouble)")(
      test("reads double values from specialized chunk reader") {
        val reader = Stream.compileToReader(Stream.fromChunk(Chunk(1.0, 2.0, 3.0)))
        assertTrue(reader.readN[Double](2) == Chunk(1.0, 2.0))
      },
      test("remaining doubles can be read after initial readN") {
        val reader = Stream.compileToReader(Stream.fromChunk(Chunk(1.0, 2.0, 3.0)))
        val _      = reader.readN[Double](2)
        assertTrue(reader.readN[Double](2) == Chunk(3.0))
      }
    ),
    suite("AnyRef branch (FromIterable)")(
      test("reads reference values from iterable reader") {
        val reader = Stream.compileToReader(Stream.fromIterable(List("a", "b", "c")))
        assertTrue(reader.readN[String](2) == Chunk("a", "b"))
      },
      test("readN on an already-closed reader returns empty chunk") {
        val reader = Stream.compileToReader(Stream.fromIterable(List("a", "b", "c")))
        reader.close()
        assertTrue(reader.readN[String](1) == Chunk.empty)
      }
    ),
    suite("FromChunk* overrides")(
      test("FromChunkInt successive readN: full then partial") {
        val reader = Stream.compileToReader(Stream.fromChunk(Chunk(1, 2, 3, 4, 5)))
        val c1     = reader.readN[Int](3)
        val c2     = reader.readN[Int](3)
        assertTrue(c1 == Chunk(1, 2, 3)) &&
        assertTrue(c2 == Chunk(4, 5))
      },
      test("FromChunkLong readN returns correct typed Chunk[Long]") {
        val reader = Stream.compileToReader(Stream.fromChunk(Chunk(10L, 20L, 30L, 40L)))
        val result = reader.readN[Long](2)
        assertTrue(result == Chunk(10L, 20L))
      },
      test("FromChunkFloat readN returns correct typed Chunk[Float]") {
        val reader = Stream.compileToReader(Stream.fromChunk(Chunk(1.1f, 2.2f, 3.3f, 4.4f)))
        val result = reader.readN[Float](2)
        assertTrue(result == Chunk(1.1f, 2.2f))
      },
      test("FromChunkDouble readN returns correct typed Chunk[Double]") {
        val reader = Stream.compileToReader(Stream.fromChunk(Chunk(1.5, 2.5, 3.5, 4.5)))
        val result = reader.readN[Double](2)
        assertTrue(result == Chunk(1.5, 2.5))
      },
      test("FromChunk[String] readN returns correct elements") {
        val reader = Stream.compileToReader(Stream.fromChunk(Chunk("a", "b", "c")))
        val result = reader.readN[String](2)
        assertTrue(result == Chunk("a", "b"))
      },
      test("readN respects setLimit") {
        val reader = Stream.compileToReader(Stream.fromChunk(Chunk.fromIterable(0 until 10)))
        reader.setLimit(3)
        val result = reader.readN[Int](10)
        assertTrue(result == Chunk(0, 1, 2))
      },
      test("readN after exhaustion returns empty chunk and isClosed is true") {
        val reader = Stream.compileToReader(Stream.fromChunk(Chunk(1, 2, 3)))
        val _      = reader.readN[Int](3)
        val empty  = reader.readN[Int](5)
        assertTrue(empty == Chunk.empty) &&
        assertTrue(reader.isClosed)
      },
      test("readN on zero-length chunk returns empty") {
        val reader = Stream.compileToReader(Stream.fromChunk(Chunk.empty: Chunk[Int]))
        val result = reader.readN[Int](5)
        assertTrue(result == Chunk.empty)
      },
      test("setSkip then readN skips initial elements") {
        val reader = Stream.compileToReader(Stream.fromChunk(Chunk(10, 20, 30, 40, 50)))
        reader.setSkip(2)
        val result = reader.readN[Int](2)
        assertTrue(result == Chunk(30, 40))
      },
      test("setLimit then setSkip interaction") {
        val reader = Stream.compileToReader(Stream.fromChunk(Chunk.fromIterable(0 until 10)))
        reader.setLimit(5)
        reader.setSkip(3)
        val result = reader.readN[Int](10)
        assertTrue(result == Chunk(3, 4, 5, 6, 7))
      }
    )
  )
}
