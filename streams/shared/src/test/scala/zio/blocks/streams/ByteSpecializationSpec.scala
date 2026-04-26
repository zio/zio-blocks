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
import zio.blocks.streams.io.Reader
import zio.test._

/**
 * Tests for the Byte-specialized zero-boxing pipeline:
 *   - JvmType.Infer resolution for Byte
 *   - Reader.fromChunk dispatch to FromChunkByte
 *   - readByte / readBytes / readAll / read (boxed)
 *   - Stream.fromChunk round-trip for Byte chunks
 *   - Edge cases (Byte.MinValue, all 256 values)
 *   - reset, skip, setLimit, setSkip
 */
object ByteSpecializationSpec extends StreamsBaseSpec {

  def spec = suite("Byte Specialization")(
    jvmTypeInferSuite,
    byteReaderSuite,
    streamRoundTripSuite,
    edgeCaseSuite,
    resetSuite,
    sinkByteSuite,
    combinatorByteSuite
  )

  // ---------------------------------------------------------------------------
  // JvmType.Infer resolution
  // ---------------------------------------------------------------------------
  val jvmTypeInferSuite = suite("JvmType.Infer")(
    test("resolves Byte for Byte") {
      assertTrue(JvmType.Infer.byte.jvmType == JvmType.Byte)
    },
    test("isByte returns true for Byte") {
      assertTrue(JvmType.Infer.byte.isByte)
    },
    test("isByte returns false for Int") {
      assertTrue(!JvmType.Infer.int.isByte)
    }
  )

  // ---------------------------------------------------------------------------
  // Reader Byte specialization
  // ---------------------------------------------------------------------------
  val byteReaderSuite = suite("Reader Byte specialization")(
    test("fromChunk(Chunk[Byte]) has jvmType Byte") {
      val reader = Reader.fromChunk(Chunk[Byte](1, 2, 3))
      assertTrue(reader.jvmType eq JvmType.Byte)
    },
    test("readByte returns bytes as unsigned Int then -1") {
      val reader = Reader.fromChunk(Chunk[Byte](1, 2, 3))
      val b1     = reader.readByte()
      val b2     = reader.readByte()
      val b3     = reader.readByte()
      val b4     = reader.readByte()
      assertTrue(b1 == 1, b2 == 2, b3 == 3, b4 == -1)
    },
    test("readByte returns unsigned value for negative byte") {
      val reader = Reader.fromChunk(Chunk[Byte](-1))
      val b      = reader.readByte()
      assertTrue(b == 255)
    },
    test("readByte returns unsigned value for Byte.MinValue") {
      val reader = Reader.fromChunk(Chunk[Byte](Byte.MinValue))
      val b      = reader.readByte()
      assertTrue(b == 128)
    },
    test("read (boxed) returns elements then sentinel") {
      val reader = Reader.fromChunk(Chunk[Byte](10, 20, 30))
      val v1     = reader.read[Any](null)
      val v2     = reader.read[Any](null)
      val v3     = reader.read[Any](null)
      val v4     = reader.read[Any](null)
      assertTrue(
        v1 == Byte.box(10.toByte),
        v2 == Byte.box(20.toByte),
        v3 == Byte.box(30.toByte),
        v4 == null
      )
    },
    test("skip works correctly") {
      val reader = Reader.fromChunk(Chunk[Byte](1, 2, 3, 4, 5))
      reader.skip(2)
      val b1 = reader.readByte()
      val b2 = reader.readByte()
      val b3 = reader.readByte()
      val b4 = reader.readByte()
      assertTrue(b1 == 3, b2 == 4, b3 == 5, b4 == -1)
    },
    test("setLimit restricts element count") {
      val reader = Reader.fromChunk(Chunk[Byte](1, 2, 3, 4, 5))
      val ok     = reader.setLimit(3)
      val b1     = reader.readByte()
      val b2     = reader.readByte()
      val b3     = reader.readByte()
      val b4     = reader.readByte()
      assertTrue(ok, b1 == 1, b2 == 2, b3 == 3, b4 == -1)
    },
    test("setSkip advances start position") {
      val reader = Reader.fromChunk(Chunk[Byte](1, 2, 3, 4, 5))
      val ok     = reader.setSkip(2)
      val b1     = reader.readByte()
      val b2     = reader.readByte()
      val b3     = reader.readByte()
      val b4     = reader.readByte()
      assertTrue(ok, b1 == 3, b2 == 4, b3 == 5, b4 == -1)
    },
    test("readAll produces correct Chunk[Byte]") {
      val reader = Reader.fromChunk(Chunk[Byte](10, 20, 30))
      val result = reader.readAll[Byte]()
      assertTrue(result == Chunk[Byte](10, 20, 30))
    },
    test("readBytes bulk read works") {
      val reader = Reader.fromChunk(Chunk[Byte](1, 2, 3, 4, 5))
      val buf    = new Array[Byte](5)
      val n1     = reader.readBytes(buf, 0, 3)
      val n2     = reader.readBytes(buf, 3, 2)
      val n3     = reader.readBytes(buf, 0, 1)
      assertTrue(n1 == 3, n2 == 2, n3 == -1, buf.toList == List[Byte](1, 2, 3, 4, 5))
    },
    test("readable and isClosed track state") {
      val reader  = Reader.fromChunk(Chunk[Byte](1, 2))
      val beforeR = reader.readable()
      val beforeC = reader.isClosed
      reader.readByte(); reader.readByte()
      val afterR = reader.readable()
      val afterC = reader.isClosed
      assertTrue(beforeR, !beforeC, !afterR, afterC)
    }
  )

  // ---------------------------------------------------------------------------
  // Stream round-trip
  // ---------------------------------------------------------------------------
  val streamRoundTripSuite = suite("Stream round-trip")(
    test("Stream.fromChunk(Chunk[Byte]).runCollect round-trips correctly") {
      val chunk  = Chunk[Byte](1, 2, 3)
      val result = Stream.fromChunk(chunk).runCollect
      assertTrue(result == Right(Chunk[Byte](1, 2, 3)))
    },
    test("empty Chunk[Byte] round-trips to empty") {
      val result = Stream.fromChunk(Chunk.empty[Byte]).runCollect
      assertTrue(result == Right(Chunk.empty))
    },
    test("Stream.fromChunk(Chunk[Byte]).take works") {
      val chunk  = Chunk[Byte](1, 2, 3, 4, 5)
      val result = Stream.fromChunk(chunk).take(3).runCollect
      assertTrue(result == Right(Chunk[Byte](1, 2, 3)))
    },
    test("Stream.fromChunk(Chunk[Byte]).drop works") {
      val chunk  = Chunk[Byte](1, 2, 3, 4, 5)
      val result = Stream.fromChunk(chunk).drop(2).runCollect
      assertTrue(result == Right(Chunk[Byte](3, 4, 5)))
    }
  )

  // ---------------------------------------------------------------------------
  // Edge cases
  // ---------------------------------------------------------------------------
  val edgeCaseSuite = suite("Edge cases")(
    test("Byte.MinValue (-128) as stream element is handled correctly") {
      val result = Stream.fromChunk(Chunk[Byte](Byte.MinValue)).runCollect
      assertTrue(result == Right(Chunk[Byte](Byte.MinValue)))
    },
    test("Byte.MaxValue (127) as stream element is handled correctly") {
      val result = Stream.fromChunk(Chunk[Byte](Byte.MaxValue)).runCollect
      assertTrue(result == Right(Chunk[Byte](Byte.MaxValue)))
    },
    test("all 256 byte values round-trip correctly") {
      val allBytes = Chunk.fromArray((0 until 256).map(_.toByte).toArray)
      val result   = Stream.fromChunk(allBytes).runCollect
      assertTrue(result == Right(allBytes))
    },
    test("readByte returns unsigned values for all 256 byte values") {
      val allBytes = Chunk.fromArray((0 until 256).map(_.toByte).toArray)
      val reader   = Reader.fromChunk(allBytes)
      val values   = (0 until 256).map(_ => reader.readByte()).toList
      val eof      = reader.readByte()
      assertTrue(values == (0 until 256).toList, eof == -1)
    },
    test("negative bytes preserved in boxed read path") {
      val reader = Reader.fromChunk(Chunk[Byte](-128, -1, 0, 127))
      val v1     = reader.read[Any](null).asInstanceOf[java.lang.Byte].byteValue()
      val v2     = reader.read[Any](null).asInstanceOf[java.lang.Byte].byteValue()
      val v3     = reader.read[Any](null).asInstanceOf[java.lang.Byte].byteValue()
      val v4     = reader.read[Any](null).asInstanceOf[java.lang.Byte].byteValue()
      assertTrue(v1 == -128.toByte, v2 == -1.toByte, v3 == 0.toByte, v4 == 127.toByte)
    }
  )

  // ---------------------------------------------------------------------------
  // reset
  // ---------------------------------------------------------------------------
  val resetSuite = suite("reset")(
    test("FromChunkByte reset replays all elements") {
      val reader = Reader.fromChunk(Chunk[Byte](10, 20, 30))
      // Drain all elements
      reader.readByte(); reader.readByte(); reader.readByte()
      assertTrue(reader.isClosed) &&
      {
        reader.reset()
        val b1 = reader.readByte()
        val b2 = reader.readByte()
        val b3 = reader.readByte()
        val b4 = reader.readByte()
        assertTrue(b1 == 10, b2 == 20, b3 == 30, b4 == -1)
      }
    },
    test("reset after setSkip re-applies skip") {
      val reader = Reader.fromChunk(Chunk[Byte](1, 2, 3, 4, 5))
      reader.setSkip(2)
      // Drain
      reader.readByte(); reader.readByte(); reader.readByte()
      assertTrue(reader.isClosed) &&
      {
        reader.reset()
        val b1 = reader.readByte()
        val b2 = reader.readByte()
        val b3 = reader.readByte()
        val b4 = reader.readByte()
        assertTrue(b1 == 3, b2 == 4, b3 == 5, b4 == -1)
      }
    },
    test("reset after setLimit re-applies limit") {
      val reader = Reader.fromChunk(Chunk[Byte](1, 2, 3, 4, 5))
      reader.setLimit(3)
      // Drain
      reader.readByte(); reader.readByte(); reader.readByte()
      assertTrue(reader.isClosed) &&
      {
        reader.reset()
        val b1 = reader.readByte()
        val b2 = reader.readByte()
        val b3 = reader.readByte()
        val b4 = reader.readByte()
        assertTrue(b1 == 1, b2 == 2, b3 == 3, b4 == -1)
      }
    }
  )

  // ---------------------------------------------------------------------------
  // Sink byte dispatch
  // ---------------------------------------------------------------------------
  val sinkByteSuite = suite("Sink byte dispatch")(
    test("Sink.collectAll collects all bytes") {
      val result = Stream.fromChunk(Chunk[Byte](1, 2, 3)).run(Sink.collectAll[Byte])
      assertTrue(result == Right(Chunk[Byte](1, 2, 3)))
    },
    test("Sink.collectAll on empty byte stream") {
      val result = Stream.fromChunk(Chunk.empty[Byte]).run(Sink.collectAll[Byte])
      assertTrue(result == Right(Chunk.empty[Byte]))
    },
    test("Sink.count counts byte elements") {
      val result = Stream.fromChunk(Chunk[Byte](1, 2, 3)).run(Sink.count)
      assertTrue(result == Right(3L))
    },
    test("Sink.count on empty byte stream") {
      val result = Stream.fromChunk(Chunk.empty[Byte]).run(Sink.count)
      assertTrue(result == Right(0L))
    },
    test("Sink.drain consumes all bytes") {
      val result = Stream.fromChunk(Chunk[Byte](1, 2, 3)).run(Sink.drain)
      assertTrue(result == Right(()))
    },
    test("Sink.exists finds matching byte") {
      val result = Stream.fromChunk(Chunk[Byte](1, 2, 3)).run(Sink.exists[Byte](_ == 2.toByte))
      assertTrue(result == Right(true))
    },
    test("Sink.exists returns false when no match") {
      val result = Stream.fromChunk(Chunk[Byte](1, 2, 3)).run(Sink.exists[Byte](_ == 10.toByte))
      assertTrue(result == Right(false))
    },
    test("Sink.find locates matching byte") {
      val result = Stream.fromChunk(Chunk[Byte](1, 2, 3)).run(Sink.find[Byte](_ == 2.toByte))
      assertTrue(result == Right(Some(2.toByte)))
    },
    test("Sink.find returns None when no match") {
      val result = Stream.fromChunk(Chunk[Byte](1, 2, 3)).run(Sink.find[Byte](_ == 10.toByte))
      assertTrue(result == Right(None))
    },
    test("Sink.forall returns true when all match") {
      val result = Stream.fromChunk(Chunk[Byte](1, 2, 3)).run(Sink.forall[Byte](_ < 10.toByte))
      assertTrue(result == Right(true))
    },
    test("Sink.forall returns false when one fails") {
      val result = Stream.fromChunk(Chunk[Byte](1, 2, 3)).run(Sink.forall[Byte](_ < 2.toByte))
      assertTrue(result == Right(false))
    },
    test("Sink.foreach processes all bytes") {
      var sum    = 0
      val result = Stream.fromChunk(Chunk[Byte](1, 2, 3)).run(Sink.foreach[Byte](b => sum += b))
      assertTrue(result == Right(()), sum == 6)
    },
    test("Sink.foldLeft accumulates bytes") {
      val result = Stream.fromChunk(Chunk[Byte](1, 2, 3)).run(Sink.foldLeft[Byte, Int](0)(_ + _))
      assertTrue(result == Right(6))
    },
    test("Sink.foldLeft on empty byte stream") {
      val result = Stream.fromChunk(Chunk.empty[Byte]).run(Sink.foldLeft[Byte, Int](0)(_ + _))
      assertTrue(result == Right(0))
    },
    test("Sink.head returns first byte") {
      val result = Stream.fromChunk(Chunk[Byte](1, 2, 3)).run(Sink.head[Byte])
      assertTrue(result == Right(Some(1.toByte)))
    },
    test("Sink.head returns None on empty") {
      val result = Stream.fromChunk(Chunk.empty[Byte]).run(Sink.head[Byte])
      assertTrue(result == Right(None))
    },
    test("Sink.last returns last byte") {
      val result = Stream.fromChunk(Chunk[Byte](1, 2, 3)).run(Sink.last[Byte])
      assertTrue(result == Right(Some(3.toByte)))
    },
    test("Sink.last returns None on empty") {
      val result = Stream.fromChunk(Chunk.empty[Byte]).run(Sink.last[Byte])
      assertTrue(result == Right(None))
    },
    test("Sink.take(n) takes first n bytes") {
      val result = Stream.fromChunk(Chunk[Byte](1, 2, 3, 4, 5)).run(Sink.take[Byte](3))
      assertTrue(result == Right(Chunk[Byte](1, 2, 3)))
    },
    test("Sink.take(n) on shorter stream") {
      val result = Stream.fromChunk(Chunk[Byte](1, 2)).run(Sink.take[Byte](5))
      assertTrue(result == Right(Chunk[Byte](1, 2)))
    },
    test("Byte.MinValue handled by all sinks") {
      val chunk         = Chunk[Byte](Byte.MinValue, 0, Byte.MaxValue)
      val collectResult = Stream.fromChunk(chunk).run(Sink.collectAll[Byte])
      val countResult   = Stream.fromChunk(chunk).run(Sink.count)
      val headResult    = Stream.fromChunk(chunk).run(Sink.head[Byte])
      val lastResult    = Stream.fromChunk(chunk).run(Sink.last[Byte])
      assertTrue(
        collectResult == Right(chunk),
        countResult == Right(3L),
        headResult == Right(Some(Byte.MinValue)),
        lastResult == Right(Some(Byte.MaxValue))
      )
    }
  )

  // ---------------------------------------------------------------------------
  // Stream combinator byte dispatch
  // ---------------------------------------------------------------------------
  val combinatorByteSuite = suite("Stream combinator byte dispatch")(
    // grouped
    test("grouped groups byte elements into chunks") {
      val result = Stream.fromChunk(Chunk[Byte](1, 2, 3, 4, 5)).grouped(2).runCollect
      assertTrue(result == Right(Chunk(Chunk[Byte](1, 2), Chunk[Byte](3, 4), Chunk[Byte](5))))
    },
    test("grouped on empty byte stream") {
      val result = Stream.fromChunk(Chunk.empty[Byte]).grouped(3).runCollect
      assertTrue(result == Right(Chunk.empty))
    },
    test("grouped with n larger than stream") {
      val result = Stream.fromChunk(Chunk[Byte](1, 2)).grouped(5).runCollect
      assertTrue(result == Right(Chunk(Chunk[Byte](1, 2))))
    },
    test("grouped preserves Byte.MinValue and Byte.MaxValue") {
      val result = Stream.fromChunk(Chunk[Byte](Byte.MinValue, 0, Byte.MaxValue)).grouped(2).runCollect
      assertTrue(result == Right(Chunk(Chunk[Byte](Byte.MinValue, 0), Chunk[Byte](Byte.MaxValue))))
    },
    // sliding
    test("sliding produces correct byte windows") {
      val result = Stream.fromChunk(Chunk[Byte](1, 2, 3, 4, 5)).sliding(3, 1).runCollect
      assertTrue(
        result == Right(
          Chunk(
            Chunk[Byte](1, 2, 3),
            Chunk[Byte](2, 3, 4),
            Chunk[Byte](3, 4, 5)
          )
        )
      )
    },
    test("sliding with step > 1") {
      val result = Stream.fromChunk(Chunk[Byte](1, 2, 3, 4, 5)).sliding(2, 2).runCollect
      assertTrue(result == Right(Chunk(Chunk[Byte](1, 2), Chunk[Byte](3, 4), Chunk[Byte](5))))
    },
    test("sliding on empty byte stream") {
      val result = Stream.fromChunk(Chunk.empty[Byte]).sliding(3).runCollect
      assertTrue(result == Right(Chunk.empty))
    },
    test("sliding with step > window size") {
      val result = Stream.fromChunk(Chunk[Byte](1, 2, 3, 4, 5)).sliding(2, 3).runCollect
      assertTrue(result == Right(Chunk(Chunk[Byte](1, 2), Chunk[Byte](4, 5))))
    },
    // scan
    test("scan accumulates byte values") {
      val result = Stream.fromChunk(Chunk[Byte](1, 2, 3)).scan(0.toByte)((a, b) => (a + b).toByte).runCollect
      assertTrue(result == Right(Chunk[Byte](0, 1, 3, 6)))
    },
    test("scan on empty byte stream emits only init") {
      val result = Stream.fromChunk(Chunk.empty[Byte]).scan(10.toByte)((a, b) => (a + b).toByte).runCollect
      assertTrue(result == Right(Chunk[Byte](10)))
    },
    test("scan preserves negative byte accumulation") {
      val result =
        Stream.fromChunk(Chunk[Byte](Byte.MaxValue, 1)).scan(0.toByte)((a, b) => (a + b).toByte).runCollect
      assertTrue(result == Right(Chunk[Byte](0, 127, -128)))
    },
    // intersperse
    test("intersperse with byte separator") {
      val result = Stream.fromChunk(Chunk[Byte](1, 2, 3)).intersperse(0.toByte).runCollect
      assertTrue(result == Right(Chunk[Byte](1, 0, 2, 0, 3)))
    },
    test("intersperse on single byte element") {
      val result = Stream.fromChunk(Chunk[Byte](42)).intersperse(0.toByte).runCollect
      assertTrue(result == Right(Chunk[Byte](42)))
    },
    test("intersperse on empty byte stream") {
      val result = Stream.fromChunk(Chunk.empty[Byte]).intersperse(0.toByte).runCollect
      assertTrue(result == Right(Chunk.empty))
    }
  )
}
