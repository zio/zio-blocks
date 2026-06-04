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

import java.nio.ByteBuffer
import java.nio.channels.{Channels, ReadableByteChannel}

object ReadNJvmSpec extends StreamsBaseSpec {

  private def intBuf(ints: Int*): ByteBuffer = {
    val bb = ByteBuffer.allocate(ints.size * 4)
    ints.foreach(bb.putInt)
    bb.flip()
    bb
  }

  private def longBuf(longs: Long*): ByteBuffer = {
    val bb = ByteBuffer.allocate(longs.size * 8)
    longs.foreach(bb.putLong)
    bb.flip()
    bb
  }

  private def doubleBuf(ds: Double*): ByteBuffer = {
    val bb = ByteBuffer.allocate(ds.size * 8)
    ds.foreach(bb.putDouble)
    bb.flip()
    bb
  }

  private def floatBuf(fs: Float*): ByteBuffer = {
    val bb = ByteBuffer.allocate(fs.size * 4)
    fs.foreach(bb.putFloat)
    bb.flip()
    bb
  }

  private def channel(bytes: Byte*): ReadableByteChannel =
    Channels.newChannel(new java.io.ByteArrayInputStream(bytes.toArray))

  def spec: Spec[TestEnvironment, Any] = suite("readN JVM overrides")(
    suite("NioReaders.fromByteBuffer (Byte)")(
      test("bulk read returns correct bytes") {
        val r = NioReaders.fromByteBuffer(ByteBuffer.wrap(Array[Byte](1, 2, 3, 4, 5)))
        assertTrue(r.readN[Byte](3) == Chunk[Byte](1, 2, 3))
      },
      test("successive reads advance position") {
        val r  = NioReaders.fromByteBuffer(ByteBuffer.wrap(Array[Byte](10, 20, 30, 40)))
        val c1 = r.readN[Byte](2)
        val c2 = r.readN[Byte](2)
        assertTrue(c1 == Chunk[Byte](10, 20)) && assertTrue(c2 == Chunk[Byte](30, 40))
      },
      test("readN beyond remaining returns all and closes") {
        val r = NioReaders.fromByteBuffer(ByteBuffer.wrap(Array[Byte](1, 2, 3)))
        assertTrue(r.readN[Byte](10) == Chunk[Byte](1, 2, 3)) && assertTrue(r.isClosed)
      },
      test("readN(0) returns empty") {
        val r = NioReaders.fromByteBuffer(ByteBuffer.wrap(Array[Byte](1, 2)))
        assertTrue(r.readN[Byte](0) == Chunk.empty)
      }
    ),
    suite("NioReaders.fromByteBufferInt (Int)")(
      test("bulk read returns correct ints") {
        val r = NioReaders.fromByteBufferInt(intBuf(10, 20, 30, 40, 50))
        assertTrue(r.readN[Int](3) == Chunk(10, 20, 30))
      },
      test("partial: n > available returns all") {
        val r = NioReaders.fromByteBufferInt(intBuf(1, 2, 3))
        assertTrue(r.readN[Int](10) == Chunk(1, 2, 3)) && assertTrue(r.isClosed)
      },
      test("successive reads") {
        val r  = NioReaders.fromByteBufferInt(intBuf(1, 2, 3, 4))
        val c1 = r.readN[Int](2)
        val c2 = r.readN[Int](2)
        assertTrue(c1 == Chunk(1, 2)) && assertTrue(c2 == Chunk(3, 4))
      }
    ),
    suite("NioReaders.fromByteBufferLong (Long)")(
      test("bulk read returns correct longs") {
        val r = NioReaders.fromByteBufferLong(longBuf(100L, 200L, 300L))
        assertTrue(r.readN[Long](2) == Chunk(100L, 200L))
      },
      test("partial: n > available returns all") {
        val r = NioReaders.fromByteBufferLong(longBuf(1L, 2L))
        assertTrue(r.readN[Long](100) == Chunk(1L, 2L)) && assertTrue(r.isClosed)
      }
    ),
    suite("NioReaders.fromByteBufferDouble (Double)")(
      test("bulk read returns correct doubles") {
        val r = NioReaders.fromByteBufferDouble(doubleBuf(1.1, 2.2, 3.3))
        assertTrue(r.readN[Double](2) == Chunk(1.1, 2.2))
      },
      test("successive reads") {
        val r  = NioReaders.fromByteBufferDouble(doubleBuf(1.0, 2.0, 3.0, 4.0))
        val c1 = r.readN[Double](2)
        val c2 = r.readN[Double](2)
        assertTrue(c1 == Chunk(1.0, 2.0)) && assertTrue(c2 == Chunk(3.0, 4.0))
      }
    ),
    suite("NioReaders.fromByteBufferFloat (Float)")(
      test("bulk read returns correct floats") {
        val r = NioReaders.fromByteBufferFloat(floatBuf(1.0f, 2.0f, 3.0f))
        assertTrue(r.readN[Float](3) == Chunk(1.0f, 2.0f, 3.0f))
      },
      test("readN(0) returns empty") {
        val r = NioReaders.fromByteBufferFloat(floatBuf(1.0f))
        assertTrue(r.readN[Float](0) == Chunk.empty)
      }
    ),
    suite("NioReaders.fromChannel (Byte)")(
      test("accumulates across buffer refills with small internal buffer") {
        val r = NioReaders.fromChannel(channel(1, 2, 3, 4, 5, 6), 2)
        assertTrue(r.readN[Byte](5) == Chunk[Byte](1, 2, 3, 4, 5))
      },
      test("successive readN calls advance position") {
        val r  = NioReaders.fromChannel(channel(10, 20, 30, 40), 2)
        val c1 = r.readN[Byte](2)
        val c2 = r.readN[Byte](2)
        assertTrue(c1 == Chunk[Byte](10, 20)) && assertTrue(c2 == Chunk[Byte](30, 40))
      },
      test("readN beyond end returns all available") {
        val r = NioReaders.fromChannel(channel(1, 2, 3), 4)
        assertTrue(r.readN[Byte](100) == Chunk[Byte](1, 2, 3)) && assertTrue(r.isClosed)
      }
    )
  )
}
