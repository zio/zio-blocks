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

object ReaderJvmSpec extends StreamsBaseSpec {

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

  def spec: Spec[TestEnvironment, Any] = suite("Reader (JVM)")(
    suite("readN")(
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
      ),
      suite("regressions")(
        // ---- signed byte round-trip through read() ----
        test("byteBuffer_read_signedBytes_roundTrip [AdversarialByteLaneReaderSpec]") {
          val r = NioReaders.fromByteBuffer(ByteBuffer.wrap(Array[Byte](-128, -1, 0, 1, 127)))
          val c = r.readN[Byte](5)
          assertTrue(c == Chunk[Byte](-128, -1, 0, 1, 127))
        },
        test("byteBuffer_empty_readN_isEmpty_and_closes [AdversarialByteLaneReaderSpec]") {
          val r = NioReaders.fromByteBuffer(ByteBuffer.wrap(Array.emptyByteArray))
          assertTrue(r.readN[Byte](5) == Chunk.empty[Byte])
        },
        test("channel_readN_large_path_over_8192 [AdversarialByteLaneReaderSpec]") {
          val data = Array.tabulate[Byte](20000)(i => (i % 256 - 128).toByte)
          val r    = NioReaders.fromChannel(Channels.newChannel(new java.io.ByteArrayInputStream(data)), 4096)
          val c    = r.readN[Byte](20000)
          assertTrue(c.length == 20000) && assertTrue(c == Chunk.fromArray(data))
        }
      )
    ),
    suite("readUpToN")(
      suite("FromChunk[String] (generic)")(
        test("readUpToN(0) returns empty chunk") {
          val reader = Stream.compileToReader(Stream.fromChunk(Chunk("a", "b", "c")))
          assertTrue(reader.readUpToN[String](0) == Chunk.empty)
        },
        test("readUpToN(1) returns single element") {
          val reader = Stream.compileToReader(Stream.fromChunk(Chunk("a", "b", "c")))
          assertTrue(reader.readUpToN[String](1) == Chunk("a"))
        },
        test("readUpToN(n < total) returns exactly n elements") {
          val reader = Stream.compileToReader(Stream.fromChunk(Chunk("a", "b", "c", "d", "e")))
          assertTrue(reader.readUpToN[String](3) == Chunk("a", "b", "c"))
        },
        test("readUpToN(n > total) returns all remaining elements") {
          val reader = Stream.compileToReader(Stream.fromChunk(Chunk("a", "b", "c")))
          assertTrue(reader.readUpToN[String](100) == Chunk("a", "b", "c"))
        },
        test("readUpToN on exhausted reader returns empty chunk") {
          val reader = Stream.compileToReader(Stream.fromChunk(Chunk("a", "b")))
          val _      = reader.readUpToN[String](2)
          assertTrue(reader.readUpToN[String](5) == Chunk.empty)
        }
      ),
      suite("FromChunkInt")(
        test("readUpToN(0) returns empty chunk") {
          val reader = Stream.compileToReader(Stream.fromChunk(Chunk(1, 2, 3)))
          assertTrue(reader.readUpToN[Int](0) == Chunk.empty)
        },
        test("readUpToN(1) returns single element") {
          val reader = Stream.compileToReader(Stream.fromChunk(Chunk(1, 2, 3)))
          assertTrue(reader.readUpToN[Int](1) == Chunk(1))
        },
        test("readUpToN(n < total) returns exactly n elements") {
          val reader = Stream.compileToReader(Stream.fromChunk(Chunk(1, 2, 3, 4, 5)))
          assertTrue(reader.readUpToN[Int](3) == Chunk(1, 2, 3))
        },
        test("readUpToN(n > total) returns all remaining elements") {
          val reader = Stream.compileToReader(Stream.fromChunk(Chunk(1, 2, 3)))
          assertTrue(reader.readUpToN[Int](100) == Chunk(1, 2, 3))
        },
        test("readUpToN on exhausted reader returns empty chunk") {
          val reader = Stream.compileToReader(Stream.fromChunk(Chunk(1, 2, 3)))
          val _      = reader.readUpToN[Int](3)
          assertTrue(reader.readUpToN[Int](1) == Chunk.empty)
        },
        test("successive readUpToN calls advance position") {
          val reader = Stream.compileToReader(Stream.fromChunk(Chunk(10, 20, 30, 40, 50)))
          val c1     = reader.readUpToN[Int](2)
          val c2     = reader.readUpToN[Int](2)
          val c3     = reader.readUpToN[Int](5)
          assertTrue(c1 == Chunk(10, 20)) &&
          assertTrue(c2 == Chunk(30, 40)) &&
          assertTrue(c3 == Chunk(50))
        }
      ),
      suite("FromChunkLong")(
        test("readUpToN(0) returns empty chunk") {
          val reader = Stream.compileToReader(Stream.fromChunk(Chunk(1L, 2L, 3L)))
          assertTrue(reader.readUpToN[Long](0) == Chunk.empty)
        },
        test("readUpToN(1) returns single element") {
          val reader = Stream.compileToReader(Stream.fromChunk(Chunk(1L, 2L, 3L)))
          assertTrue(reader.readUpToN[Long](1) == Chunk(1L))
        },
        test("readUpToN(n < total) returns exactly n elements") {
          val reader = Stream.compileToReader(Stream.fromChunk(Chunk(10L, 20L, 30L, 40L, 50L)))
          assertTrue(reader.readUpToN[Long](3) == Chunk(10L, 20L, 30L))
        },
        test("readUpToN(n > total) returns all remaining elements") {
          val reader = Stream.compileToReader(Stream.fromChunk(Chunk(1L, 2L)))
          assertTrue(reader.readUpToN[Long](100) == Chunk(1L, 2L))
        },
        test("readUpToN on exhausted reader returns empty chunk") {
          val reader = Stream.compileToReader(Stream.fromChunk(Chunk(1L, 2L)))
          val _      = reader.readUpToN[Long](2)
          assertTrue(reader.readUpToN[Long](1) == Chunk.empty)
        }
      ),
      suite("FromChunkDouble")(
        test("readUpToN(0) returns empty chunk") {
          val reader = Stream.compileToReader(Stream.fromChunk(Chunk(1.0, 2.0, 3.0)))
          assertTrue(reader.readUpToN[Double](0) == Chunk.empty)
        },
        test("readUpToN(1) returns single element") {
          val reader = Stream.compileToReader(Stream.fromChunk(Chunk(1.0, 2.0, 3.0)))
          assertTrue(reader.readUpToN[Double](1) == Chunk(1.0))
        },
        test("readUpToN(n < total) returns exactly n elements") {
          val reader = Stream.compileToReader(Stream.fromChunk(Chunk(1.5, 2.5, 3.5, 4.5)))
          assertTrue(reader.readUpToN[Double](2) == Chunk(1.5, 2.5))
        },
        test("readUpToN(n > total) returns all remaining elements") {
          val reader = Stream.compileToReader(Stream.fromChunk(Chunk(1.0, 2.0)))
          assertTrue(reader.readUpToN[Double](100) == Chunk(1.0, 2.0))
        },
        test("readUpToN on exhausted reader returns empty chunk") {
          val reader = Stream.compileToReader(Stream.fromChunk(Chunk(1.0, 2.0)))
          val _      = reader.readUpToN[Double](2)
          assertTrue(reader.readUpToN[Double](1) == Chunk.empty)
        }
      ),
      suite("FromChunkFloat")(
        test("readUpToN(0) returns empty chunk") {
          val reader = Stream.compileToReader(Stream.fromChunk(Chunk(1.0f, 2.0f, 3.0f)))
          assertTrue(reader.readUpToN[Float](0) == Chunk.empty)
        },
        test("readUpToN(1) returns single element") {
          val reader = Stream.compileToReader(Stream.fromChunk(Chunk(1.0f, 2.0f, 3.0f)))
          assertTrue(reader.readUpToN[Float](1) == Chunk(1.0f))
        },
        test("readUpToN(n < total) returns exactly n elements") {
          val reader = Stream.compileToReader(Stream.fromChunk(Chunk(1.1f, 2.2f, 3.3f, 4.4f)))
          assertTrue(reader.readUpToN[Float](2) == Chunk(1.1f, 2.2f))
        },
        test("readUpToN(n > total) returns all remaining elements") {
          val reader = Stream.compileToReader(Stream.fromChunk(Chunk(1.0f, 2.0f)))
          assertTrue(reader.readUpToN[Float](100) == Chunk(1.0f, 2.0f))
        },
        test("readUpToN on exhausted reader returns empty chunk") {
          val reader = Stream.compileToReader(Stream.fromChunk(Chunk(1.0f, 2.0f)))
          val _      = reader.readUpToN[Float](2)
          assertTrue(reader.readUpToN[Float](1) == Chunk.empty)
        }
      ),
      suite("FromRange")(
        test("readUpToN(0) returns empty chunk") {
          val reader = Stream.compileToReader(Stream.range(0, 10))
          assertTrue(reader.readUpToN[Int](0) == Chunk.empty)
        },
        test("readUpToN(1) returns single element") {
          val reader = Stream.compileToReader(Stream.range(0, 10))
          assertTrue(reader.readUpToN[Int](1) == Chunk(0))
        },
        test("readUpToN(n < total) returns at most n elements") {
          val reader = Stream.compileToReader(Stream.range(0, 10))
          val chunk  = reader.readUpToN[Int](5)
          assertTrue(chunk.length <= 5 && chunk.length >= 1)
        },
        test("readUpToN(n > total) returns all remaining elements") {
          val reader = Stream.compileToReader(Stream.range(0, 3))
          val chunk  = reader.readUpToN[Int](100)
          assertTrue(chunk == Chunk(0, 1, 2))
        },
        test("readUpToN on exhausted reader returns empty chunk") {
          val reader = Stream.compileToReader(Stream.range(0, 2))
          val _      = reader.readUpToN[Int](2)
          assertTrue(reader.readUpToN[Int](1) == Chunk.empty)
        },
        test("successive readUpToN calls consume range in order") {
          val reader = Stream.compileToReader(Stream.range(0, 7))
          val c1     = reader.readUpToN[Int](3)
          val c1len  = c1.length
          val c2     = reader.readUpToN[Int](3)
          val c2len  = c2.length
          val c3     = reader.readUpToN[Int](10)
          assertTrue(c1 == Chunk.fromIterable(0 until c1len)) &&
          assertTrue(c2 == Chunk.fromIterable(c1len until (c1len + c2len))) &&
          assertTrue((c1 ++ c2 ++ c3) == Chunk(0, 1, 2, 3, 4, 5, 6))
        }
      ),
      suite("Filtered (Int)")(
        test("readUpToN returns at most n filtered elements") {
          val reader = Stream.compileToReader(Stream.range(0, 20).filter(_ % 2 == 0))
          val chunk  = reader.readUpToN[Int](5)
          assertTrue(chunk.length <= 5 && chunk.length >= 1) &&
          assertTrue(chunk.toList.forall(_ % 2 == 0))
        },
        test("readUpToN on empty filter result returns empty") {
          val reader = Stream.compileToReader(Stream.fromChunk(Chunk(1, 3, 5)).filter(_ % 2 == 0))
          assertTrue(reader.readUpToN[Int](5) == Chunk.empty)
        }
      ),
      suite("Mapped (Int)")(
        test("readUpToN returns at most n mapped elements") {
          val reader = Stream.compileToReader(Stream.range(0, 10).map(_ * 10))
          val chunk  = reader.readUpToN[Int](5)
          assertTrue(chunk.length <= 5 && chunk.length >= 1) &&
          assertTrue(chunk.toList.forall(_ % 10 == 0))
        },
        test("readUpToN on mapped reader successive calls consume in order") {
          val reader = Stream.compileToReader(Stream.fromChunk(Chunk(1, 2, 3, 4, 5)).map(_ * 2))
          val c1     = reader.readUpToN[Int](3)
          val c2     = reader.readUpToN[Int](3)
          assertTrue((c1 ++ c2) == Chunk(2, 4, 6, 8, 10))
        }
      ),
      suite("ConcatReader (boundary spanning)")(
        test("readUpToN spans concatenation boundary") {
          val s1     = Stream.fromChunk(Chunk(1, 2, 3))
          val s2     = Stream.fromChunk(Chunk(4, 5, 6))
          val reader = Stream.compileToReader(s1 ++ s2)
          val c1     = reader.readUpToN[Int](3)
          val c2     = reader.readUpToN[Int](3)
          val c3     = reader.readUpToN[Int](3)
          assertTrue((c1 ++ c2 ++ c3) == Chunk(1, 2, 3, 4, 5, 6))
        },
        test("readUpToN returns at least one element from each call until exhausted") {
          val s1     = Stream.fromChunk(Chunk("a", "b"))
          val s2     = Stream.fromChunk(Chunk("c", "d"))
          val reader = Stream.compileToReader(s1 ++ s2)
          var all    = Chunk.empty: Chunk[String]
          var done   = false
          while (!done) {
            val chunk = reader.readUpToN[String](2)
            if (chunk.isEmpty) done = true
            else all = all ++ chunk
          }
          assertTrue(all == Chunk("a", "b", "c", "d"))
        }
      ),
      suite("Taken")(
        test("readUpToN respects take limit") {
          val reader = Stream.compileToReader(Stream.range(0, 100).take(5))
          var all    = Chunk.empty: Chunk[Int]
          var done   = false
          while (!done) {
            val chunk = reader.readUpToN[Int](10)
            if (chunk.isEmpty) done = true
            else all = all ++ chunk
          }
          assertTrue(all == Chunk(0, 1, 2, 3, 4))
        },
        test("readUpToN(3) on take(3) returns exactly 3 elements") {
          val reader = Stream.compileToReader(Stream.range(0, 100).take(3))
          val chunk  = reader.readUpToN[Int](3)
          assertTrue(chunk == Chunk(0, 1, 2))
        }
      ),
      suite("TakenWhile")(
        test("readUpToN respects takeWhile predicate") {
          val reader = Stream.compileToReader(Stream.range(0, 100).takeWhile(_ < 5))
          var all    = Chunk.empty: Chunk[Int]
          var done   = false
          while (!done) {
            val chunk = reader.readUpToN[Int](10)
            if (chunk.isEmpty) done = true
            else all = all ++ chunk
          }
          assertTrue(all == Chunk(0, 1, 2, 3, 4))
        },
        test("readUpToN on takeWhile that immediately fails returns empty") {
          val reader = Stream.compileToReader(Stream.range(10, 20).takeWhile(_ < 5))
          assertTrue(reader.readUpToN[Int](5) == Chunk.empty)
        }
      ),
      suite("SkipLimitReader")(
        test("readUpToN respects skip and limit") {
          val reader = Stream.compileToReader(Stream.fromChunk(Chunk.fromIterable(0 until 20)).drop(5).take(5))
          var all    = Chunk.empty: Chunk[Int]
          var done   = false
          while (!done) {
            val chunk = reader.readUpToN[Int](10)
            if (chunk.isEmpty) done = true
            else all = all ++ chunk
          }
          assertTrue(all == Chunk(5, 6, 7, 8, 9))
        },
        test("readUpToN(2) on skip+limit reader returns at most 2 elements") {
          val reader = Stream.compileToReader(Stream.fromChunk(Chunk.fromIterable(0 until 20)).drop(3).take(10))
          val chunk  = reader.readUpToN[Int](2)
          assertTrue(chunk.length <= 2 && chunk.length >= 1)
        }
      ),
      suite("ConcurrentBufferedReader (JVM)")(
        test("readUpToN returns available elements") {
          val reader = Stream.compileToReader(Stream.fromChunk(Chunk(1, 2, 3, 4, 5)).buffer(4))
          Thread.sleep(50)
          var all  = Chunk.empty: Chunk[Int]
          var done = false
          while (!done) {
            val chunk = reader.readUpToN[Int](10)
            if (chunk.isEmpty) done = true
            else all = all ++ chunk
          }
          reader.close()
          assertTrue(all == Chunk(1, 2, 3, 4, 5))
        },
        test("readUpToN(0) returns empty on buffered reader") {
          val reader = Stream.compileToReader(Stream.fromChunk(Chunk(1, 2, 3)).buffer(4))
          val result = reader.readUpToN[Int](0)
          reader.close()
          assertTrue(result == Chunk.empty)
        }
      ),
      suite("ConcurrentMergeReader (JVM)")(
        test("readUpToN returns elements from merged streams") {
          val inner1 = Stream.fromChunk(Chunk(1, 2, 3))
          val inner2 = Stream.fromChunk(Chunk(4, 5, 6))
          val merged = Stream.mergeAll(2)(Stream.fromIterable(List(inner1, inner2)))
          val reader = Stream.compileToReader(merged)
          Thread.sleep(100)
          var all  = Chunk.empty: Chunk[Int]
          var done = false
          while (!done) {
            val chunk = reader.readUpToN[Int](10)
            if (chunk.isEmpty) done = true
            else all = all ++ chunk
          }
          reader.close()
          assertTrue(all.toSet == Set(1, 2, 3, 4, 5, 6)) &&
          assertTrue(all.length == 6)
        }
      ),
      suite("regressions")(
        // ---- readUpToN ----
        test("byteBuffer_readUpToN_returns_up_to_available [AdversarialByteLaneReaderSpec]") {
          val r = NioReaders.fromByteBuffer(ByteBuffer.wrap(Array[Byte](1, 2, 3)))
          // up to 10 available now => 3
          assertTrue(r.readUpToN[Byte](10) == Chunk[Byte](1, 2, 3))
        },
        test("byteBuffer_readUpToN_one [AdversarialByteLaneReaderSpec]") {
          val r = NioReaders.fromByteBuffer(ByteBuffer.wrap(Array[Byte](9, 8)))
          assertTrue(r.readUpToN[Byte](1) == Chunk[Byte](9)) &&
          assertTrue(r.readUpToN[Byte](1) == Chunk[Byte](8)) &&
          assertTrue(r.readUpToN[Byte](1) == Chunk.empty[Byte])
        },
        test("byteBuffer_readUpToN_zero_isEmpty [AdversarialByteLaneReaderSpec]") {
          val r = NioReaders.fromByteBuffer(ByteBuffer.wrap(Array[Byte](1, 2)))
          assertTrue(r.readUpToN[Byte](0) == Chunk.empty[Byte])
        },
        test("byteBuffer_readUpToN_after_partial_readN [AdversarialByteLaneReaderSpec]") {
          val r  = NioReaders.fromByteBuffer(ByteBuffer.wrap(Array[Byte](1, 2, 3, 4, 5)))
          val c1 = r.readN[Byte](2)
          val c2 = r.readUpToN[Byte](100)
          assertTrue(c1 == Chunk[Byte](1, 2)) && assertTrue(c2 == Chunk[Byte](3, 4, 5))
        },
        // ---- OBS-1 regression: readUpToN(huge n) must bound its allocation ----
        // Pre-fix the Byte readers pre-allocated `new Array[Byte](n)`, so a valid
        // `readUpToN(Int.MaxValue)` over a tiny buffer attempted a ~2 GB allocation
        // and threw OutOfMemoryError. The fix bounds the allocation by the bytes
        // actually available, so the call must return the few available bytes.
        test("byteBuffer_readUpToN_intMaxValue_returns_available_without_oom [AdversarialByteLaneReaderSpec]") {
          val r = NioReaders.fromByteBuffer(ByteBuffer.wrap(Array[Byte](1, 2, 3)))
          assertTrue(r.readUpToN[Byte](Int.MaxValue) == Chunk[Byte](1, 2, 3)) &&
          assertTrue(r.readUpToN[Byte](Int.MaxValue) == Chunk.empty[Byte])
        },
        test("channel_readUpToN_intMaxValue_returns_available_without_oom [AdversarialByteLaneReaderSpec]") {
          val r = NioReaders.fromChannel(channel(1, 2, 3, 4))
          // bufSize defaults to 8192, so all 4 bytes are in one internal buffer.
          assertTrue(r.readUpToN[Byte](Int.MaxValue) == Chunk[Byte](1, 2, 3, 4))
        },
        test("channel_readUpToN_returns_currently_available_after_one_fill [AdversarialByteLaneReaderSpec]") {
          // internal buffer = 2; readUpToN(10) should fill once and return up to 2.
          val r = NioReaders.fromChannel(channel(1, 2, 3, 4, 5), 2)
          val c = r.readUpToN[Byte](10)
          assertTrue(c.nonEmpty && c.length <= 2) && assertTrue(c == Chunk[Byte](1, 2))
        }
      )
    ),
    suite("byte-lane readers")(
      suite("regressions")(
        test("byteBuffer_readByte_returns_unsigned_0_255 [AdversarialByteLaneReaderSpec]") {
          val r  = NioReaders.fromByteBuffer(ByteBuffer.wrap(Array[Byte](-1, -128, 127)))
          val b0 = r.readByte()
          val b1 = r.readByte()
          val b2 = r.readByte()
          val b3 = r.readByte()
          assertTrue(b0 == 255) && assertTrue(b1 == 128) && assertTrue(b2 == 127) && assertTrue(b3 == -1)
        },
        // ---- EOF stability ----
        test("byteBuffer_readByte_after_eof_stays_minus1 [AdversarialByteLaneReaderSpec]") {
          val r = NioReaders.fromByteBuffer(ByteBuffer.wrap(Array[Byte](1)))
          assertTrue(r.readByte() == 1) &&
          assertTrue(r.readByte() == -1) &&
          assertTrue(r.readByte() == -1) &&
          assertTrue(r.isClosed)
        },
        test("byteBuffer_read_after_eof_returns_sentinel [AdversarialByteLaneReaderSpec]") {
          val r = NioReaders.fromByteBuffer(ByteBuffer.wrap(Array[Byte](7)))
          val s = new AnyRef
          assertTrue(r.read(s).asInstanceOf[AnyRef] != s) &&
          assertTrue((r.read(s).asInstanceOf[AnyRef] eq s)) &&
          assertTrue((r.read(s).asInstanceOf[AnyRef] eq s))
        },
        // ---- mixing readByte + readN ----
        test("byteBuffer_mix_readByte_then_readN [AdversarialByteLaneReaderSpec]") {
          val r = NioReaders.fromByteBuffer(ByteBuffer.wrap(Array[Byte](10, 20, 30, 40)))
          val b = r.readByte()
          val c = r.readN[Byte](10)
          assertTrue(b == 10) && assertTrue(c == Chunk[Byte](20, 30, 40))
        },
        // ---- reset replay ----
        test("byteBuffer_reset_replays_from_start [AdversarialByteLaneReaderSpec]") {
          val r  = NioReaders.fromByteBuffer(ByteBuffer.wrap(Array[Byte](1, 2, 3)))
          val c1 = r.readN[Byte](3)
          r.reset()
          val c2 = r.readN[Byte](3)
          assertTrue(c1 == Chunk[Byte](1, 2, 3)) && assertTrue(c2 == Chunk[Byte](1, 2, 3))
        },
        test("byteBuffer_reset_after_close_replays [AdversarialByteLaneReaderSpec]") {
          val r = NioReaders.fromByteBuffer(ByteBuffer.wrap(Array[Byte](5, 6)))
          r.close()
          assertTrue(r.isClosed)
          r.reset()
          assertTrue(!r.isClosed) && assertTrue(r.readN[Byte](2) == Chunk[Byte](5, 6))
        },
        // ---- skip ----
        test("byteBuffer_skip_then_read [AdversarialByteLaneReaderSpec]") {
          val r = NioReaders.fromByteBuffer(ByteBuffer.wrap(Array[Byte](1, 2, 3, 4, 5)))
          r.skip(2)
          assertTrue(r.readN[Byte](10) == Chunk[Byte](3, 4, 5))
        },
        test("byteBuffer_skip_beyond_end [AdversarialByteLaneReaderSpec]") {
          val r = NioReaders.fromByteBuffer(ByteBuffer.wrap(Array[Byte](1, 2)))
          r.skip(100)
          assertTrue(r.readByte() == -1)
        },
        // ---- ChannelReader: refills across small internal buffer ----
        test("channel_readByte_accumulates_across_refills [AdversarialByteLaneReaderSpec]") {
          val r = NioReaders.fromChannel(channel(1, 2, 3, 4, 5), 2)
          assertTrue(r.readByte() == 1) &&
          assertTrue(r.readByte() == 2) &&
          assertTrue(r.readByte() == 3) &&
          assertTrue(r.readByte() == 4) &&
          assertTrue(r.readByte() == 5) &&
          assertTrue(r.readByte() == -1)
        },
        test("channel_signed_bytes_roundtrip_via_stream_collect [AdversarialByteLaneReaderSpec]") {
          val data: Array[Byte] = Array[Byte](-128, -1, 0, 1, 127, -50, 50)
          val s                 = Stream.fromReader[Nothing, Byte](
            NioReaders.fromChannel(Channels.newChannel(new java.io.ByteArrayInputStream(data)), 3)
          )
          assertTrue(s.runCollect == Right(Chunk.fromArray(data)))
        },
        // ---- Int-lane ByteBuffer reader: readByte reads a whole int, low byte ----
        test("byteBufferInt_readByte_consumes_int_returns_low_byte [AdversarialByteLaneReaderSpec]") {
          val bb = ByteBuffer.allocate(8); bb.putInt(0x01020304); bb.putInt(0x0a0b0c0d); bb.flip()
          val r  = NioReaders.fromByteBufferInt(bb)
          val b0 = r.readByte()
          val b1 = r.readByte()
          assertTrue(b0 == 0x04) && assertTrue(b1 == 0x0d) && assertTrue(r.readByte() == -1)
        }
      )
    )
  )
}
