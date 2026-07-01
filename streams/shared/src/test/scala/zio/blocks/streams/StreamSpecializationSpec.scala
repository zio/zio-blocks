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

import zio._
import zio.blocks.chunk.Chunk
import zio.blocks.streams.io.Reader
import zio.test._

import java.io.ByteArrayOutputStream

object StreamSpecializationSpec extends StreamsBaseSpec {

  // ---------------------------------------------------------------------------
  // GenericFoldSpec helpers
  // ---------------------------------------------------------------------------
  private val n = 100

  // Helpers that force the GENERIC runFold[Z] path (not the specialized overloads)
  private def foldInts[Z](z: Z)(f: (Z, Int) => Z): Either[Nothing, Z]   = Stream.range(0, n).runFold[Z](z)(f)
  private def foldLongs[Z](z: Z)(f: (Z, Long) => Z): Either[Nothing, Z] =
    Stream.range(0, n).map(_.toLong).runFold[Z](z)(f)
  private def foldDoubles[Z](z: Z)(f: (Z, Double) => Z): Either[Nothing, Z] =
    Stream.range(0, n).map(_.toDouble).runFold[Z](z)(f)
  private def foldFloats[Z](z: Z)(f: (Z, Float) => Z): Either[Nothing, Z] =
    Stream.range(0, n).map(_.toFloat).runFold[Z](z)(f)

  private val intSum    = n * (n - 1) / 2
  private val longSum   = intSum.toLong
  private val doubleSum = intSum.toDouble
  private val floatSum  = intSum.toFloat

  // ---------------------------------------------------------------------------
  // AdversarialByteLaneSweepSpec helpers
  // ---------------------------------------------------------------------------
  private val bs: Stream[Nothing, Byte] = Stream(1.toByte, 2.toByte, 3.toByte)

  // ---------------------------------------------------------------------------
  // AdversarialCollectSpecializationSpec helpers
  // ---------------------------------------------------------------------------
  private def deep(s: Stream[Nothing, Int]): Stream[Nothing, Int] =
    (0 until 130).foldLeft(s)((acc, _) => acc.map(x => x))

  // ---------------------------------------------------------------------------
  // AdversarialFlatMapDeepByteCorruptionSpec helpers
  // ---------------------------------------------------------------------------
  // Force the inner stream past Stream.DepthCutoff (= 100) so it compiles to the
  // deep Interpreter rather than a shallow Mapped* chain.
  private def deepCorruption(s: Stream[Nothing, Int]): Stream[Nothing, Int] = {
    var r = s
    var i = 0
    while (i < 120) { r = r.map(_ + 0); i += 1 }
    r
  }

  // ---------------------------------------------------------------------------
  // AdversarialSpecializedLaneBoundarySpec helpers
  // ---------------------------------------------------------------------------
  private def lc(xs: Seq[Long]): Stream[Nothing, Long]     = Stream.fromChunk(Chunk.fromArray(xs.toArray))
  private def dc(xs: Seq[Double]): Stream[Nothing, Double] = Stream.fromChunk(Chunk.fromArray(xs.toArray))
  private def bc(xs: Seq[Byte]): Stream[Nothing, Byte]     = Stream.fromChunk(Chunk.fromArray(xs.toArray))

  // ---------------------------------------------------------------------------
  // AdversarialSpecializedLaneDifferentialSpec helpers
  // ---------------------------------------------------------------------------
  private def sInt(xs: Seq[Int]): Stream[Nothing, Int]          = Stream.fromChunk(Chunk.fromArray(xs.toArray))
  private def sLong(xs: Seq[Long]): Stream[Nothing, Long]       = Stream.fromChunk(Chunk.fromArray(xs.toArray))
  private def sFloat(xs: Seq[Float]): Stream[Nothing, Float]    = Stream.fromChunk(Chunk.fromArray(xs.toArray))
  private def sDouble(xs: Seq[Double]): Stream[Nothing, Double] = Stream.fromChunk(Chunk.fromArray(xs.toArray))
  private def sByte(xs: Seq[Byte]): Stream[Nothing, Byte]       = Stream.fromChunk(Chunk.fromArray(xs.toArray))

  // ---------------------------------------------------------------------------
  // AdversarialTakeWhileByteSpec helpers
  // ---------------------------------------------------------------------------
  private val bytes: Stream[Nothing, Byte] = Stream(1.toByte, 2.toByte, 3.toByte)

  def spec: Spec[TestEnvironment, Any] = suite("Stream specialization")(
    genericFoldSuite,
    byteLaneSuite,
    intLaneSuite,
    collectSpecializationSuite,
    cceAvoidanceSuite,
    byteLaneCorruptionSuite,
    mapCceAvoidanceSuite,
    primitiveExtremesSuite,
    specializedLaneBoundarySuite,
    specializedLaneDifferentialSuite,
    takeWhileByteLaneSuite
  )

  // ===========================================================================
  // Generic fold lane (GenericFoldSpec)
  // ===========================================================================
  val genericFoldSuite = suite("generic fold")(
    suite("Int elements")(
      test("Z=Int, A=Int")(assertTrue(foldInts[Int](0)((a, b) => a + b) == Right(intSum))),
      test("Z=Long, A=Int")(assertTrue(foldInts[Long](0L)((a, b) => a + b) == Right(longSum))),
      test("Z=Double, A=Int")(assertTrue(foldInts[Double](0.0)((a, b) => a + b) == Right(doubleSum))),
      test("Z=Float, A=Int")(assertTrue(foldInts[Float](0.0f)((a, b) => a + b) == Right(floatSum))),
      test("Z=String, A=Int")(assertTrue(foldInts[String]("")((a, b) => a + b.toString).map(_.length) == Right(n + 90)))
    ),

    suite("Long elements")(
      test("Z=Int, A=Long")(assertTrue(foldLongs[Int](0)((a, b) => a + b.toInt) == Right(intSum))),
      test("Z=Long, A=Long")(assertTrue(foldLongs[Long](0L)((a, b) => a + b) == Right(longSum))),
      test("Z=Double, A=Long")(assertTrue(foldLongs[Double](0.0)((a, b) => a + b) == Right(doubleSum))),
      test("Z=Float, A=Long")(assertTrue(foldLongs[Float](0.0f)((a, b) => a + b) == Right(floatSum))),
      test("Z=String, A=Long") {
        assertTrue(foldLongs[String]("")((a, b) => a + b.toString).map(_.length) == Right(n + 90))
      }
    ),

    suite("Double elements")(
      test("Z=Int, A=Double")(assertTrue(foldDoubles[Int](0)((a, b) => a + b.toInt) == Right(intSum))),
      test("Z=Long, A=Double")(assertTrue(foldDoubles[Long](0L)((a, b) => a + b.toLong) == Right(longSum))),
      test("Z=Double, A=Double")(assertTrue(foldDoubles[Double](0.0)((a, b) => a + b) == Right(doubleSum))),
      test("Z=Float, A=Double")(assertTrue(foldDoubles[Float](0.0f)((a, b) => a + b.toFloat) == Right(floatSum))),
      test("Z=String, A=Double") {
        assertTrue(foldDoubles[String]("")((a, b) => a + b.toInt.toString).map(_.length) == Right(n + 90))
      }
    ),

    suite("Float elements")(
      test("Z=Int, A=Float")(assertTrue(foldFloats[Int](0)((a, b) => a + b.toInt) == Right(intSum))),
      test("Z=Long, A=Float")(assertTrue(foldFloats[Long](0L)((a, b) => a + b.toLong) == Right(longSum))),
      test("Z=Double, A=Float")(assertTrue(foldFloats[Double](0.0)((a, b) => a + b) == Right(doubleSum))),
      test("Z=Float, A=Float")(assertTrue(foldFloats[Float](0.0f)((a, b) => a + b) == Right(floatSum))),
      test("Z=String, A=Float") {
        assertTrue(foldFloats[String]("")((a, b) => a + b.toInt.toString).map(_.length) == Right(n + 90))
      }
    ),

    suite("AnyRef elements")(
      test("Z=Long, A=String") {
        val result = Stream.fromIterable((0 until n).map(_.toString)).runFold[Long](0L)((a, b) => a + b.toLong)
        assertTrue(result == Right(longSum))
      },
      test("Z=String, A=String") {
        val result = Stream.fromIterable(Seq("a", "b", "c")).runFold[String]("")(_ + _)
        assertTrue(result == Right("abc"))
      }
    )
  )

  // ===========================================================================
  // Byte lane (ByteSpecializationSpec + AdversarialByteLaneSweepSpec regressions)
  // ===========================================================================
  val byteLaneSuite = suite("byte lane")(
    suite("JvmType.Infer")(
      test("resolves Byte for Byte") {
        assertTrue(JvmType.Infer.byte.jvmType == JvmType.Byte)
      },
      test("isByte returns true for Byte") {
        assertTrue(JvmType.Infer.byte.isByte)
      },
      test("isByte returns false for Int") {
        assertTrue(!JvmType.Infer.int.isByte)
      }
    ),
    suite("Reader Byte specialization")(
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
    ),
    suite("Stream round-trip")(
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
    ),
    suite("Edge cases")(
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
    ),
    suite("reset")(
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
    ),
    suite("Sink byte dispatch")(
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
    ),
    suite("Stream combinator byte dispatch")(
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
    ),
    suite("regressions")(
      // Byte lane (drained via readInt; Function1 not specialized on Byte)
      test("byte_collect [AdversarialByteLaneSweepSpec]")(assertTrue(bs.runCollect == Right(Chunk[Byte](1, 2, 3)))),
      test("byte_count [AdversarialByteLaneSweepSpec]")(assertTrue(bs.count == Right(3L))),
      test("byte_filter [AdversarialByteLaneSweepSpec]")(
        assertTrue(bs.filter(_ > 1).runCollect == Right(Chunk[Byte](2, 3)))
      ),
      test("byte_scan [AdversarialByteLaneSweepSpec]") {
        assertTrue(bs.scan(0.toByte)((a, b) => (a + b).toByte).runCollect == Right(Chunk[Byte](0, 1, 3, 6)))
      },
      test("byte_fold [AdversarialByteLaneSweepSpec]")(assertTrue(bs.runFold(0)((a, b) => a + b) == Right(6))),
      test("byte_grouped [AdversarialByteLaneSweepSpec]") {
        assertTrue(bs.grouped(2).runCollect.map(_.map(_.toList)) == Right(Chunk(List[Byte](1, 2), List[Byte](3))))
      },
      test("byte_sliding [AdversarialByteLaneSweepSpec]") {
        assertTrue(bs.sliding(2).runCollect.map(_.map(_.toList)) == Right(Chunk(List[Byte](1, 2), List[Byte](2, 3))))
      },
      test("byte_find [AdversarialByteLaneSweepSpec]")(assertTrue(bs.find(_ == 2.toByte) == Right(Some(2.toByte)))),
      test("byte_head_last [AdversarialByteLaneSweepSpec]")(
        assertTrue(bs.head == Right(Some(1.toByte)) && bs.last == Right(Some(3.toByte)))
      ),
      test("byte_negatives [AdversarialByteLaneSweepSpec]") {
        assertTrue(
          Stream((-1).toByte, Byte.MinValue, Byte.MaxValue).runCollect ==
            Right(Chunk[Byte](-1, Byte.MinValue, Byte.MaxValue))
        )
      },
      test("byte_repeated_take [AdversarialByteLaneSweepSpec]")(
        assertTrue(bs.repeated.take(4).runCollect == Right(Chunk[Byte](1, 2, 3, 1)))
      ),
      // BUG-N1 regression: map producing a Byte/Boolean is routed through AnyRef
      test("int_to_byte_map [AdversarialByteLaneSweepSpec]")(
        assertTrue(Stream(1, 2, 3).map(_.toByte).runCollect == Right(Chunk[Byte](1, 2, 3)))
      ),
      test("long_to_byte_map [AdversarialByteLaneSweepSpec]")(
        assertTrue(Stream(1L, 2L).map(_.toByte).runCollect == Right(Chunk[Byte](1, 2)))
      ),
      test("int_to_boolean_map [AdversarialByteLaneSweepSpec]") {
        assertTrue(Stream(1, 2, 3).map(_ % 2 == 0).runCollect == Right(Chunk(false, true, false)))
      },
      test("map_to_byte_then_takeWhile [AdversarialByteLaneSweepSpec]") {
        assertTrue(Stream(1, 2, 3).map(_.toByte).takeWhile(_ < 3).runCollect == Right(Chunk[Byte](1, 2)))
      },
      // in-band sentinel round-trips (BUG-004 family)
      test("long_sentinel_collect [AdversarialByteLaneSweepSpec]") {
        assertTrue(Stream(1L, Long.MaxValue, 2L).runCollect == Right(Chunk(1L, Long.MaxValue, 2L)))
      },
      test("double_sentinel_collect [AdversarialByteLaneSweepSpec]") {
        assertTrue(Stream(1.0, Double.MaxValue, 2.0).runCollect == Right(Chunk(1.0, Double.MaxValue, 2.0)))
      },
      test("long_sentinel_count_grouped [AdversarialByteLaneSweepSpec]") {
        assertTrue(Stream(1L, Long.MaxValue, 2L).count == Right(3L)) &&
        assertTrue(
          Stream(1L, Long.MaxValue, 2L).grouped(2).runCollect.map(_.map(_.toList)) ==
            Right(Chunk(List(1L, Long.MaxValue), List(2L)))
        )
      },
      test("long_sentinel_head_last_find [AdversarialByteLaneSweepSpec]") {
        assertTrue(Stream(Long.MaxValue, 1L).head == Right(Some(Long.MaxValue))) &&
        assertTrue(Stream(1L, Long.MaxValue).last == Right(Some(Long.MaxValue))) &&
        assertTrue(Stream(1L, Long.MaxValue).find(_ == Long.MaxValue) == Right(Some(Long.MaxValue)))
      },
      // cross-lane flatMap drives inner sentinel through specialized read
      test("int_flatMap_long_sentinel [AdversarialByteLaneSweepSpec]") {
        assertTrue(
          Stream(1, 2).flatMap(_ => Stream(Long.MaxValue, 5L)).runCollect ==
            Right(Chunk(Long.MaxValue, 5L, Long.MaxValue, 5L))
        )
      },
      test("int_flatMap_byte [AdversarialByteLaneSweepSpec]") {
        assertTrue(Stream(1, 2).flatMap(_ => Stream(7.toByte, 8.toByte)).runCollect == Right(Chunk[Byte](7, 8, 7, 8)))
      },
      // boundary take/drop
      test("take_zero_neg_max [AdversarialByteLaneSweepSpec]") {
        assertTrue(Stream(1, 2, 3).take(0).runCollect == Right(Chunk.empty[Int])) &&
        assertTrue(Stream(1, 2, 3).take(-1).runCollect == Right(Chunk.empty[Int])) &&
        assertTrue(Stream(1, 2, 3).take(Long.MaxValue).runCollect == Right(Chunk(1, 2, 3)))
      },
      test("drop_neg_max_order [AdversarialByteLaneSweepSpec]") {
        assertTrue(Stream(1, 2, 3).drop(-1).runCollect == Right(Chunk(1, 2, 3))) &&
        assertTrue(Stream.range(0, 10).drop(2).take(3).runCollect == Right(Chunk(2, 3, 4))) &&
        assertTrue(Stream.range(0, 10).take(5).drop(2).runCollect == Right(Chunk(2, 3, 4)))
      },
      // Short / Char / Boolean lanes (drained via boxed read) — takeWhile safe here
      test("short_takeWhile [AdversarialByteLaneSweepSpec]") {
        assertTrue(Stream(1.toShort, 2.toShort, 3.toShort).takeWhile(_ < 3).runCollect == Right(Chunk[Short](1, 2)))
      },
      test("char_takeWhile [AdversarialByteLaneSweepSpec]") {
        assertTrue(Stream('a', 'b', 'c').takeWhile(_ < 'c').runCollect == Right(Chunk('a', 'b')))
      },
      test("boolean_takeWhile [AdversarialByteLaneSweepSpec]") {
        assertTrue(Stream(true, true, false).takeWhile(x => x).runCollect == Right(Chunk(true, true)))
      },
      test("long_double_float_takeWhile [AdversarialByteLaneSweepSpec]") {
        assertTrue(Stream(1L, 2L, 3L).takeWhile(_ < 3L).runCollect == Right(Chunk(1L, 2L))) &&
        assertTrue(Stream(1.0, 2.0, 3.0).takeWhile(_ < 3.0).runCollect == Right(Chunk(1.0, 2.0))) &&
        assertTrue(Stream(1.0f, 2.0f, 3.0f).takeWhile(_ < 3.0f).runCollect == Right(Chunk(1.0f, 2.0f)))
      }
    )
  )

  // ===========================================================================
  // Int lane (IntSpecializationSpec)
  // ===========================================================================
  val intLaneSuite = suite("int lane")(
    suite("JvmType.Infer")(
      test("resolves Int for Int") {
        assertTrue(JvmType.Infer.int.jvmType == JvmType.Int)
      },
      test("resolves Long for Long") {
        assertTrue(JvmType.Infer.long.jvmType == JvmType.Long)
      },
      test("resolves Double for Double") {
        assertTrue(JvmType.Infer.double.jvmType == JvmType.Double)
      },
      test("resolves Byte for Byte") {
        assertTrue(JvmType.Infer.byte.jvmType == JvmType.Byte)
      },
      test("resolves AnyRef for String") {
        assertTrue(implicitly[JvmType.Infer[String]].jvmType == JvmType.AnyRef)
      },
      test("isByte returns true for Byte") {
        assertTrue(JvmType.Infer.byte.isByte)
      },
      test("isByte returns false for Int") {
        assertTrue(!JvmType.Infer.int.isByte)
      }
    ),
    suite("Reader Int specialization")(
      test("FromRange has jvmType Int") {
        val dq = Reader.fromRange(0 until 5)
        assertTrue(dq.jvmType eq JvmType.Int)
      },
      test("FromRange readInt returns correct elements") {
        val dq  = Reader.fromRange(0 until 5)
        val buf = scala.collection.mutable.ArrayBuffer[Int]()
        val s   = Long.MinValue; var v = dq.readInt(s)
        while (v != s) { buf += v.toInt; v = dq.readInt(s) }
        assertTrue(buf.toList == List(0, 1, 2, 3, 4))
      },
      test("readInt sequence matches take sequence for ranges") {
        check(Gen.int(0, 100), Gen.int(1, 100)) { (from, len) =>
          val until = from + len
          val dq1   = Reader.fromRange(from until until)
          val dq2   = Reader.fromRange(from until until)

          val via_take = {
            val buf = scala.collection.mutable.ArrayBuffer[Int]()
            var v   = dq1.read[Any](null)
            while (v != null) { buf += v.asInstanceOf[Int]; v = dq1.read[Any](null) }
            buf.toList
          }

          val via_readInt = {
            val buf = scala.collection.mutable.ArrayBuffer[Int]()
            val s   = Long.MinValue; var v = dq2.readInt(s)
            while (v != s) { buf += v.toInt; v = dq2.readInt(s) }
            buf.toList
          }

          assertTrue(via_take == via_readInt)
        }
      },
      test("repeat(Int) has jvmType Int") {
        val dq = Reader.repeat(42)
        assertTrue(dq.jvmType eq JvmType.Int)
      },
      test("repeat(Int) readInt always returns value") {
        val dq     = Reader.repeat(42)
        val s      = Long.MinValue
        val values = (0 until 10).map(_ => dq.readInt(s).toInt).toList
        assertTrue(values == List.fill(10)(42))
      },
      test("generic Reader readInt works via boxing default") {
        val dq = Reader.fromIterable(List(1, 2, 3))
        assertTrue(dq.jvmType eq JvmType.AnyRef)
        val buf = scala.collection.mutable.ArrayBuffer[Int]()
        val s   = Long.MinValue; var v = dq.readInt(s)
        while (v != s) { buf += v.toInt; v = dq.readInt(s) }
        assertTrue(buf.toList == List(1, 2, 3))
      }
    ),
    suite("SingletonInt")(
      test("Reader.single for Int produces SingletonPrim") {
        val dq = Reader.single(42)
        assertTrue(dq.isInstanceOf[Reader.SingletonPrim[_]])
      },
      test("SingletonInt readInt returns value then sentinel") {
        val dq     = Reader.single(42)
        val s      = Long.MinValue
        val first  = dq.readInt(s)
        val second = dq.readInt(s)
        assertTrue(first == 42L && second == s)
      },
      test("SingletonInt take returns Right then Left") {
        val dq     = Reader.single(42)
        val first  = dq.read[Any](null)
        val second = dq.read[Any](null)
        assertTrue(first == Int.box(42) && second == null)
      },
      test("Reader.single for String produces SingletonGeneric") {
        val dq = Reader.single("hello")
        assertTrue(dq.isInstanceOf[Reader.SingletonGeneric[_]])
      },
      test("Stream.succeed(Int) uses SingletonInt") {
        // Verify the stream pipeline actually uses specialized dequeues
        val result = Stream.succeed(42).runCollect
        assertTrue(result == Right(Chunk(42)))
      }
    ),
    suite("Specialized combinators")(
      test("range.map(+1) via readInt matches runCollect") {
        val result = Stream.range(0, 100).map(_ + 1).runCollect
        assertTrue(result == Right(Chunk.fromIterable(1 to 100)))
      },
      test("range.filter(even) via readInt matches runCollect") {
        val result = Stream.range(0, 100).filter(_ % 2 == 0).runCollect
        assertTrue(result == Right(Chunk.fromIterable((0 until 100).filter(_ % 2 == 0))))
      },
      test("range.map.filter pipeline produces specialized chain") {
        val result   = Stream.range(0, 10).map(_ * 2).filter(_ > 5).runCollect
        val expected = (0 until 10).map(_ * 2).filter(_ > 5)
        assertTrue(result == Right(Chunk.fromIterable(expected)))
      },
      test("range.take via TakenInt") {
        val result = Stream.range(0, 100).take(10).runCollect
        assertTrue(result == Right(Chunk.fromIterable(0 until 10)))
      },
      test("range.takeWhile via TakenWhileInt") {
        val result = Stream.range(0, 100).takeWhile(_ < 10).runCollect
        assertTrue(result == Right(Chunk.fromIterable(0 until 10)))
      },
      test("range.drop via DroppedInt") {
        val result = Stream.range(0, 20).drop(10).runCollect
        assertTrue(result == Right(Chunk.fromIterable(10 until 20)))
      },
      test("range ++ range via ConcatWithInt") {
        val result = (Stream.range(0, 5) ++ Stream.range(5, 10)).runCollect
        assertTrue(result == Right(Chunk.fromIterable(0 until 10)))
      },
      test("chained maps ×5 via specialized path") {
        val result   = Stream.range(0, 10).map(_ + 1).map(_ + 1).map(_ + 1).map(_ + 1).map(_ + 1).runCollect
        val expected = (0 until 10).map(_ + 5)
        assertTrue(result == Right(Chunk.fromIterable(expected)))
      },
      test("filter then map then fold Long") {
        val result   = Stream.range(0, 100).filter(_ % 2 == 0).map(_ + 1).runFold(0L)((acc, v) => acc + v)
        val expected = (0 until 100).filter(_ % 2 == 0).map(_ + 1).map(_.toLong).sum
        assertTrue(result == Right(expected))
      }
    ),
    suite("Specialized sinks")(
      test("runFold(Long) matches runFold[Z] for Int streams") {
        val n           = 1000
        val generic     = Stream.range(0, n).runFold[Long](0L)((acc, i) => acc + i)
        val specialized = Stream.range(0, n).runFold(0L)((acc, i) => acc + i)
        assertTrue(generic == specialized)
      },
      test("runFold(Int) matches manual sum") {
        val n        = 1000
        val expected = (0 until n).sum
        val result   = Stream.range(0, n).runFold(0)((acc, i) => acc + i)
        assertTrue(result == Right(expected))
      },
      test("Sink.sumInt uses specialized path") {
        val result   = Stream.range(0, 100).run(Sink.sumInt)
        val expected = (0 until 100).map(_.toLong).sum
        assertTrue(result == Right(expected))
      },
      test("drain on Int stream uses drainInt path") {
        val result = Stream.range(0, 10000).runDrain
        assertTrue(result == Right(()))
      },
      test("runFold(Double) matches manual sum for Int streams") {
        val n        = 1000
        val expected = (0 until n).map(_.toDouble).sum
        val result   = Stream.range(0, n).runFold(0.0)((acc, i) => acc + i)
        assertTrue(result == Right(expected))
      },
      test("runFold(Double) matches runFold[Z] for Int streams") {
        val n           = 1000
        val generic     = Stream.range(0, n).runFold[Double](0.0)((acc, i) => acc + i)
        val specialized = Stream.range(0, n).runFold(0.0)((acc, i) => acc + i)
        assertTrue(generic == specialized)
      },
      test("runFold(Double) works on Long streams") {
        val data     = Chunk.fromIterable((1L to 100L))
        val expected = (1L to 100L).map(_.toDouble).sum
        val result   = Stream.fromChunk(data).runFold(0.0)((acc, l) => acc + l)
        assertTrue(result == Right(expected))
      },
      test("runFold(Double) works on Float streams") {
        val data     = Chunk.fromIterable((1 to 50).map(_.toFloat))
        val expected = (1 to 50).map(_.toDouble).sum
        val result   = Stream.fromChunk(data).runFold(0.0)((acc, f) => acc + f)
        assertTrue(result == Right(expected))
      },
      test("runFold(Double) works on Double streams") {
        val data     = Chunk.fromIterable((1 to 50).map(_.toDouble))
        val expected = (1 to 50).map(_.toDouble).sum
        val result   = Stream.fromChunk(data).runFold(0.0)((acc, d) => acc + d)
        assertTrue(result == Right(expected))
      },
      test("runFold(Double) works on generic streams") {
        val data   = Chunk.fromIterable(List("a", "bb", "ccc"))
        val result = Stream.fromChunk(data).runFold(0.0)((acc, s) => acc + s.length)
        assertTrue(result == Right(6.0))
      },
      test("Sink.sumFloat uses specialized path for Float streams") {
        val data     = Chunk.fromIterable((1 to 100).map(_.toFloat))
        val expected = (1 to 100).map(_.toDouble).sum
        val result   = Stream.fromChunk(data).run(Sink.sumFloat)
        assertTrue(result == Right(expected))
      }
    ),

    // (isPure suite removed — isPure no longer exists; compile() is always the entry point)

    suite("FlatMap specialization")(
      test("flatMap with generic chunk inner stream") {
        // Test flatMap where inner streams use fromChunk with generic chunks (boxed ints)
        val genericChunk = Chunk.fromIterable(List(10, 20, 30))
        val result       = Stream.succeed(42).flatMap(_ => Stream.fromChunk(genericChunk)).runCollect
        assertTrue(result == Right(Chunk(10, 20, 30)))
      },
      test("flatMap with boxed function") {
        // Simulate the Gen.function pattern: f is captured as AnyRef function
        val f: Int => Stream[Nothing, Int]      = (i: Int) => Stream.fromChunk(Chunk(i * 10, i * 10 + 1))
        val boxedF: Any => Stream[Nothing, Int] = (a: Any) => f(a.asInstanceOf[Int])
        val result                              = Stream.succeed(5).flatMap(boxedF.asInstanceOf[Int => Stream[Nothing, Int]]).runCollect
        assertTrue(result == Right(Chunk(50, 51)))
      },
      test("flatMap like StreamLawsSpec") {
        // Reproduce exact StreamLawsSpec pattern
        import zio.test.Gen
        check(
          Gen.int(-1000, 1000),
          Gen.function(
            Gen.chunkOfBounded(0, 50)(Gen.int(-1000, 1000)).map(c => Stream.fromChunk(Chunk.fromIterable(c)))
          )
        ) { (a, f) =>
          val left  = Stream.succeed(a).flatMap(f).runCollect.fold(_ => Chunk.empty, identity)
          val right = f(a).runCollect.fold(_ => Chunk.empty, identity)
          assertTrue(left == right)
        }
      },
      test("flatMap(succeed) matches manual computation") {
        val result   = Stream.range(0, 100).flatMap(i => Stream.succeed(i * 2)).runCollect
        val expected = (0 until 100).map(_ * 2)
        assertTrue(result == Right(Chunk.fromIterable(expected)))
      },
      test("flatMap with runFold(Long) uses specialized path") {
        val result   = Stream.range(0, 100).flatMap(i => Stream.succeed(i * 2)).runFold(0L)((acc, i) => acc + i)
        val expected = (0 until 100).map(i => (i * 2).toLong).sum
        assertTrue(result == Right(expected))
      },
      test("flatMap error propagation") {
        val result: Either[String, Chunk[Int]] = Stream
          .range(0, 5)
          .flatMap { i =>
            if (i == 3) Stream.fail("boom")
            else Stream.succeed(i)
          }
          .runCollect
        assertTrue(result == Left("boom"))
      },
      test("flatMap with multi-element inner streams") {
        val result   = Stream.range(0, 3).flatMap(i => Stream.range(i * 10, i * 10 + 3)).runCollect
        val expected = Chunk(0, 1, 2, 10, 11, 12, 20, 21, 22)
        assertTrue(result == Right(expected))
      }
    ),
    suite("Edge cases")(
      test("Int.MinValue as stream element is handled correctly") {
        // This is the critical edge case the two-phase protocol solves
        val result = Stream.succeed(Int.MinValue).runCollect
        assertTrue(result == Right(Chunk(Int.MinValue)))
      },
      test("Int.MinValue through map") {
        val result = Stream.succeed(Int.MinValue).map(_ + 1).runCollect
        assertTrue(result == Right(Chunk(Int.MinValue + 1)))
      },
      test("Int.MinValue through filter") {
        val result = Stream.succeed(Int.MinValue).filter(_ == Int.MinValue).runCollect
        assertTrue(result == Right(Chunk(Int.MinValue)))
      },
      test("empty range produces no elements via specialized path") {
        val result = Stream.range(0, 0).map(_ + 1).runCollect
        assertTrue(result == Right(Chunk.empty))
      },
      test("repeated stream via specialized path") {
        val result = Stream.range(0, 3).repeated.take(9).runCollect
        assertTrue(result == Right(Chunk(0, 1, 2, 0, 1, 2, 0, 1, 2)))
      },
      test("drop then take via specialized path") {
        val result = Stream.range(0, 100).drop(10).take(5).runCollect
        assertTrue(result == Right(Chunk(10, 11, 12, 13, 14)))
      },
      test("mapError preserves elements via specialized path") {
        val result: Either[String, Chunk[Int]] = Stream.range(0, 5).mapError((_: Nothing) => "err").runCollect
        assertTrue(result == Right(Chunk(0, 1, 2, 3, 4)))
      }
    ),
    suite("reset")(
      test("FromRange reset replays all elements") {
        val dq = Reader.fromRange(0 until 5)
        val s  = Long.MinValue
        // Drain all elements
        var v = dq.readInt(s); while (v != s) v = dq.readInt(s)
        assertTrue(dq.isClosed) &&
        // Reset and drain again
        {
          dq.reset(); var buf = List.empty[Int]; v = dq.readInt(s);
          while (v != s) { buf = v.toInt :: buf; v = dq.readInt(s) }; assertTrue(buf.reverse == List(0, 1, 2, 3, 4))
        }
      },
      test("FromChunkInt reset replays all elements") {
        val dq = Reader.fromChunk(Chunk(10, 20, 30))
        val s  = Long.MinValue
        var v  = dq.readInt(s); while (v != s) v = dq.readInt(s)
        assertTrue(dq.isClosed) &&
        {
          dq.reset(); var buf = List.empty[Int]; v = dq.readInt(s);
          while (v != s) { buf = v.toInt :: buf; v = dq.readInt(s) }; assertTrue(buf.reverse == List(10, 20, 30))
        }
      },
      test("FromIterable reset replays from fresh iterator") {
        val dq = Reader.fromIterable(List("a", "b", "c"))
        dq.read[Any](null); dq.read[Any](null); dq.read[Any](null) // drain all
        dq.read[Any](null)                                         // trigger exhausted
        dq.reset()
        val v1 = dq.read[Any](null)
        val v2 = dq.read[Any](null)
        val v3 = dq.read[Any](null)
        assertTrue(v1 == "a", v2 == "b", v3 == "c")
      },
      test("SingletonPrim reset allows re-take") {
        val dq = Reader.single(42)
        val s  = Long.MinValue
        val v1 = dq.readInt(s)
        val v2 = dq.readInt(s)
        dq.reset()
        val v3 = dq.readInt(s)
        assertTrue(v1 == 42L, v2 == s, v3 == 42L)
      },
      test("Unfold reset restarts from initial state") {
        val dq = Reader.unfold[Int, Int](0)(s => if (s < 3) Some((s, s + 1)) else None)
        dq.read[Any](null); dq.read[Any](null); dq.read[Any](null); dq.read[Any](null) // drain + trigger close
        dq.reset()
        val v1 = dq.read[Any](null)
        val v2 = dq.read[Any](null)
        val v3 = dq.read[Any](null)
        assertTrue(v1 == Int.box(0), v2 == Int.box(1), v3 == Int.box(2))
      },
      test("Repeat-forever reset is no-op (infinite)") {
        val dq = Reader.repeat(42)
        assertTrue(dq.read[Any](null) == Int.box(42)) &&
        { dq.reset(); assertTrue(dq.read[Any](null) == Int.box(42)) }
      },
      test("InputStreamReader reset throws") {
        val is               = new java.io.ByteArrayInputStream(Array[Byte](1, 2, 3))
        val dq: Reader[Byte] = Reader.fromInputStream(is)
        assertTrue(
          try { dq.reset(); false }
          catch { case _: UnsupportedOperationException => true }
        )
      }
    )
  )

  // ===========================================================================
  // Collect specialization (AdversarialCollectSpecializationSpec)
  // ===========================================================================
  val collectSpecializationSuite = suite("collect specialization")(
    suite("regressions")(
      test("collect_byte_collect [AdversarialCollectSpecializationSpec]") {
        val s = Stream.range(0, 6).collect { case x if x % 2 == 0 => x.toByte }
        assertTrue(s.runCollect == Right(Chunk[Byte](0, 2, 4)))
      },
      test("collect_byte_count [AdversarialCollectSpecializationSpec]") {
        val s = Stream.range(0, 6).collect { case x if x % 2 == 0 => x.toByte }
        assertTrue(s.count == Right(3L))
      },
      test("collect_byte_take [AdversarialCollectSpecializationSpec]") {
        val s = Stream.range(0, 10).collect { case x => x.toByte }.take(3)
        assertTrue(s.runCollect == Right(Chunk[Byte](0, 1, 2)))
      },
      test("collect_byte_concat [AdversarialCollectSpecializationSpec]") {
        val a = Stream.range(0, 3).collect { case x => x.toByte }
        val b = Stream.range(3, 6).collect { case x => x.toByte }
        assertTrue((a ++ b).runCollect == Right(Chunk[Byte](0, 1, 2, 3, 4, 5)))
      },
      test("collect_byte_flatMapInner [AdversarialCollectSpecializationSpec]") {
        val s = Stream.range(0, 3).flatMap(i => Stream.range(0, 2).collect { case j => (i * 2 + j).toByte })
        assertTrue(s.runCollect == Right(Chunk[Byte](0, 1, 2, 3, 4, 5)))
      },
      test("collect_byte_deep_flatMapInner [AdversarialCollectSpecializationSpec]") {
        val s = Stream.range(0, 3).flatMap(i => deep(Stream.range(0, 2)).collect { case j => (i * 2 + j).toByte })
        assertTrue(s.runCollect == Right(Chunk[Byte](0, 1, 2, 3, 4, 5)))
      },
      test("collect_byte_fold [AdversarialCollectSpecializationSpec]") {
        val s = Stream.range(0, 6).collect { case x if x % 2 == 0 => x.toByte }
        assertTrue(s.runFold(0)((a, b) => a + b.toInt) == Right(6))
      },
      test("collect_byte_repeated_take [AdversarialCollectSpecializationSpec]") {
        val s = Stream.range(0, 3).collect { case x => x.toByte }.repeated.take(7)
        assertTrue(s.runCollect == Right(Chunk[Byte](0, 1, 2, 0, 1, 2, 0)))
      },
      test("collect_boolean_collect [AdversarialCollectSpecializationSpec]") {
        val s = Stream.range(0, 4).collect { case x => x % 2 == 0 }
        assertTrue(s.runCollect == Right(Chunk(true, false, true, false)))
      },
      test("collect_long_collect [AdversarialCollectSpecializationSpec]") {
        val s = Stream.range(0, 4).collect { case x => x.toLong }
        assertTrue(s.runCollect == Right(Chunk(0L, 1L, 2L, 3L)))
      },
      test("collect_short_collect [AdversarialCollectSpecializationSpec]") {
        val s = Stream.range(0, 4).collect { case x => x.toShort }
        assertTrue(s.runCollect == Right(Chunk[Short](0, 1, 2, 3)))
      }
    )
  )

  // ===========================================================================
  // CCE-avoidance (AdversarialFlatMapByteCCESpec)
  // ===========================================================================
  val cceAvoidanceSuite = suite("CCE-avoidance")(
    suite("regressions")(
      test("flatMap_innerMapIntToByte_collect [AdversarialFlatMapByteCCESpec]") {
        val s = Stream.range(0, 3).flatMap(i => Stream.range(0, 2).map(j => (i * 10 + j).toByte))
        assertTrue(s.runCollect == Right(Chunk[Byte](0, 1, 10, 11, 20, 21)))
      },
      test("flatMap_innerMapIntToByte_count [AdversarialFlatMapByteCCESpec]") {
        val s = Stream.range(0, 3).flatMap(_ => Stream.range(0, 2).map(_.toByte))
        assertTrue(s.count == Right(6L))
      },
      test("flatMap_innerMapIntToByte_foreach [AdversarialFlatMapByteCCESpec]") {
        var sum = 0
        val s   = Stream.range(0, 3).flatMap(i => Stream.range(0, 2).map(j => (i + j).toByte))
        val r   = s.runForeach(b => sum += b.toInt)
        assertTrue(r == Right(()) && sum == (0 + 1) + (1 + 2) + (2 + 3))
      },
      test("flatMap_innerMapIntToByte_fromOutputStream [AdversarialFlatMapByteCCESpec]") {
        val os = new ByteArrayOutputStream()
        val s  = Stream.range(0, 2).flatMap(i => Stream.range(0, 2).map(j => (i * 2 + j).toByte))
        val r  = s.run(Sink.fromOutputStream(os))
        assertTrue(r == Right(()) && os.toByteArray.toList == List[Byte](0, 1, 2, 3))
      },
      // The inner Mapped* variant is the trigger; vary its source lane to show the
      // bug is the inner map's broken readInt fast path, not the outer source lane.
      test("flatMap_innerMapLongToByte_collect [AdversarialFlatMapByteCCESpec]") {
        val s = Stream.range(0, 2).flatMap(_ => Stream(1L, 2L).map(_.toByte))
        assertTrue(s.runCollect == Right(Chunk[Byte](1, 2, 1, 2)))
      },
      test("flatMap_innerMapRefToByte_collect [AdversarialFlatMapByteCCESpec]") {
        val s = Stream.range(0, 2).flatMap(_ => Stream("7", "8").map(_.toInt.toByte))
        assertTrue(s.runCollect == Right(Chunk[Byte](7, 8, 7, 8)))
      },
      // Same broken Mapped*.readInt reached via ConcatReader.readInt (the sink
      // calls readInt directly for jvmType == Byte).
      test("concat_mapToByte_collect [AdversarialFlatMapByteCCESpec]") {
        val s = Stream.range(0, 2).map(_.toByte).concat(Stream.range(2, 4).map(_.toByte))
        assertTrue(s.runCollect == Right(Chunk[Byte](0, 1, 2, 3)))
      },
      test("concat_mapToByte_count [AdversarialFlatMapByteCCESpec]") {
        val s = Stream.range(0, 2).map(_.toByte).concat(Stream.range(2, 4).map(_.toByte))
        assertTrue(s.count == Right(4L))
      }
    )
  )

  // ===========================================================================
  // Byte lane corruption (AdversarialFlatMapDeepByteCorruptionSpec)
  // ===========================================================================
  val byteLaneCorruptionSuite = suite("byte lane corruption")(
    suite("regressions")(
      test("flatMap_deepByteInner_collect_preservesOuter [AdversarialFlatMapDeepByteCorruptionSpec]") {
        val s = Stream.range(0, 3).flatMap(i => deepCorruption(Stream.range(0, 2)).map(j => (i + j).toByte))
        assertTrue(s.runCollect == Right(Chunk[Byte](0, 1, 1, 2, 2, 3)))
      },
      test("flatMap_deepByteInner_foreach_preservesOuter [AdversarialFlatMapDeepByteCorruptionSpec]") {
        var acc = List.empty[Int]
        val s   = Stream.range(0, 3).flatMap(i => deepCorruption(Stream.range(0, 2)).map(j => (i + j).toByte))
        val r   = s.runForeach(b => acc = b.toInt :: acc)
        assertTrue(r == Right(()) && acc.reverse == List(0, 1, 1, 2, 2, 3))
      },
      // --- differential controls (must already pass; they prove the operation is
      // valid and only the Byte-via-readInt path corrupts) ---------------------
      test("control_deepByte_directCollect_correct [AdversarialFlatMapDeepByteCorruptionSpec]") {
        assertTrue(
          deepCorruption(Stream.range(0, 4)).map(j => (j + 10).toByte).runCollect == Right(Chunk[Byte](10, 11, 12, 13))
        )
      },
      test("control_deepByteInner_thenWidenToInt_correct [AdversarialFlatMapDeepByteCorruptionSpec]") {
        val s =
          Stream.range(0, 3).flatMap(i => deepCorruption(Stream.range(0, 2)).map(j => (i + j).toByte)).map(_.toInt)
        assertTrue(s.runCollect == Right(Chunk(0, 1, 1, 2, 2, 3)))
      },
      test("control_deepIntInner_correct [AdversarialFlatMapDeepByteCorruptionSpec]") {
        val s = Stream.range(0, 3).flatMap(i => deepCorruption(Stream.range(0, 2)).map(j => i + j))
        assertTrue(s.runCollect == Right(Chunk(0, 1, 1, 2, 2, 3)))
      }
    )
  )

  // ===========================================================================
  // map CCE-avoidance (AdversarialMapByteCCESpec)
  // ===========================================================================
  val mapCceAvoidanceSuite = suite("map CCE-avoidance")(
    suite("regressions")(
      // --- one failing case per Mapped* variant (all five share the bug) ------
      test("mapInt_toByte_collect [AdversarialMapByteCCESpec]") {
        assertTrue(Stream.range(0, 3).map(_.toByte).runCollect == Right(Chunk[Byte](0, 1, 2)))
      },
      test("mapLong_toByte_collect [AdversarialMapByteCCESpec]") {
        assertTrue(Stream(1L, 2L, 3L).map(_.toByte).runCollect == Right(Chunk[Byte](1, 2, 3)))
      },
      test("mapFloat_toByte_collect [AdversarialMapByteCCESpec]") {
        assertTrue(Stream(1.0f, 2.0f).map(_.toByte).runCollect == Right(Chunk[Byte](1, 2)))
      },
      test("mapDouble_toByte_collect [AdversarialMapByteCCESpec]") {
        assertTrue(Stream(1.0, 2.0).map(_.toByte).runCollect == Right(Chunk[Byte](1, 2)))
      },
      test("mapRef_toByte_collect [AdversarialMapByteCCESpec]") {
        assertTrue(Stream("1", "2").map(_.toInt.toByte).runCollect == Right(Chunk[Byte](1, 2)))
      },
      // --- distinct Byte-specialized sink consumers ----------------------------
      test("mapInt_toByte_count [AdversarialMapByteCCESpec]") {
        assertTrue(Stream.range(0, 3).map(_.toByte).count == Right(3L))
      },
      test("mapInt_toByte_foreach [AdversarialMapByteCCESpec]") {
        var sum = 0
        val r   = Stream.range(0, 4).map(_.toByte).runForeach(b => sum += b.toInt)
        assertTrue(r == Right(()) && sum == 6)
      },
      test("mapByteSource_toByte_collect [AdversarialMapByteCCESpec]") {
        assertTrue(
          Stream.fromChunk(Chunk[Byte](1, 2, 3)).map(b => (b + 1).toByte).runCollect == Right(Chunk[Byte](2, 3, 4))
        )
      },
      test("tapEach_byteSource [AdversarialMapByteCCESpec]") {
        var n   = 0
        val out = Stream.fromChunk(Chunk[Byte](1, 2, 3)).tapEach(_ => n += 1).runCollect
        assertTrue(out == Right(Chunk[Byte](1, 2, 3)) && n == 3)
      },
      test("fromOutputStream_on_mappedByte [AdversarialMapByteCCESpec]") {
        val os = new ByteArrayOutputStream()
        val r  = Stream.range(0, 3).map(_.toByte).run(Sink.fromOutputStream(os))
        assertTrue(r == Right(()) && os.toByteArray.toList == List[Byte](0, 1, 2))
      }
    )
  )

  // ===========================================================================
  // Primitive extremes (AdversarialPrimitiveExtremesSpec)
  // ===========================================================================
  val primitiveExtremesSuite = suite("primitive extremes")(
    suite("regressions")(
      // ---- sliding Long lane with the Long.MaxValue EOF sentinel as real data --
      test("sliding_longLane_containsMaxAndMin [AdversarialPrimitiveExtremesSpec]") {
        val s = Stream(Long.MaxValue, Long.MinValue, 0L).sliding(2, 1)
        assertTrue(
          s.runCollect == Right(
            Chunk(Chunk(Long.MaxValue, Long.MinValue), Chunk(Long.MinValue, 0L))
          )
        )
      },
      test("sliding_doubleLane_containsMaxValue [AdversarialPrimitiveExtremesSpec]") {
        val s = Stream(Double.MaxValue, 1.0, 2.0).sliding(2, 1)
        assertTrue(
          s.runCollect == Right(Chunk(Chunk(Double.MaxValue, 1.0), Chunk(1.0, 2.0)))
        )
      },
      test("grouped_doubleLane_containsMaxValue [AdversarialPrimitiveExtremesSpec]") {
        val s = Stream(Double.MaxValue, 1.0, 2.0).grouped(2)
        assertTrue(s.runCollect == Right(Chunk(Chunk(Double.MaxValue, 1.0), Chunk(2.0))))
      },
      // ---- scan Double lane accumulating exactly to Double.MaxValue ------------
      test("scan_doubleLane_reachesMaxValue_noTruncation [AdversarialPrimitiveExtremesSpec]") {
        val s = Stream(0.0).scan(Double.MaxValue)(_ + _) // [Max, Max]
        assertTrue(s.runCollect == Right(Chunk(Double.MaxValue, Double.MaxValue)))
      },
      // ---- Float infinity is NOT the Double.MaxValue sentinel ------------------
      test("grouped_floatLane_positiveInfinity [AdversarialPrimitiveExtremesSpec]") {
        val s = Stream(Float.PositiveInfinity, 1.0f, 2.0f).grouped(2)
        assertTrue(
          s.runCollect == Right(Chunk(Chunk(Float.PositiveInfinity, 1.0f), Chunk(2.0f)))
        )
      },
      test("scan_floatLane_overflowsToInfinity [AdversarialPrimitiveExtremesSpec]") {
        val s = Stream(Float.MaxValue).scan(Float.MaxValue)(_ + _) // [Max, +Inf]
        assertTrue(s.runCollect == Right(Chunk(Float.MaxValue, Float.PositiveInfinity)))
      },
      // ---- zip Long lane with the sentinel value ------------------------------
      test("zip_doubleLane_withMaxValue [AdversarialPrimitiveExtremesSpec]") {
        val left  = Stream(1, 2, 3)
        val right = Stream(Double.MaxValue, 0.0, Double.NegativeInfinity)
        val z     = left && right
        assertTrue(
          z.runCollect == Right(Chunk((1, Double.MaxValue), (2, 0.0), (3, Double.NegativeInfinity)))
        )
      },
      // ---- distinct Double lane with Double.MaxValue and infinities ------------
      test("distinct_doubleLane_extremes [AdversarialPrimitiveExtremesSpec]") {
        val s = Stream(Double.MaxValue, Double.MaxValue, Double.PositiveInfinity, Double.MaxValue).distinct
        assertTrue(s.runCollect == Right(Chunk(Double.MaxValue, Double.PositiveInfinity)))
      },
      // ---- grouped Long lane with Long.MaxValue under repeated ----------------
      test("grouped_longLane_maxValue_repeated [AdversarialPrimitiveExtremesSpec]") {
        val s = Stream(Long.MaxValue, 1L).grouped(2).repeated.take(3)
        assertTrue(
          s.runCollect == Right(Chunk(Chunk(Long.MaxValue, 1L), Chunk(Long.MaxValue, 1L), Chunk(Long.MaxValue, 1L)))
        )
      }
    )
  )

  // ===========================================================================
  // Specialized-lane boundary (AdversarialSpecializedLaneBoundarySpec)
  // ===========================================================================
  val specializedLaneBoundarySuite = suite("specialized-lane boundary")(
    suite("regressions")(
      test("Long lane: take/drop at extreme Long counts saturate correctly [AdversarialSpecializedLaneBoundarySpec]") {
        val data = (1L to 6L).toList
        assertTrue(lc(data).take(Long.MaxValue).runCollect == Right(Chunk.fromArray(data.toArray))) &&
        assertTrue(lc(data).take(Long.MinValue).runCollect == Right(Chunk.empty[Long])) &&
        assertTrue(lc(data).take(0L).runCollect == Right(Chunk.empty[Long])) &&
        assertTrue(lc(data).drop(Long.MaxValue).runCollect == Right(Chunk.empty[Long])) &&
        assertTrue(lc(data).drop(Long.MinValue).runCollect == Right(Chunk.fromArray(data.toArray))) &&
        // compose drop then take (window arithmetic order, BUG-B surface) on Long lane
        assertTrue(lc(data).drop(2L).take(2L).runCollect == Right(Chunk(3L, 4L)))
      },
      test(
        "Double lane: take/drop with NaN / MaxValue elements at boundaries [AdversarialSpecializedLaneBoundarySpec]"
      ) {
        val data = List(Double.NaN, Double.MaxValue, 1.0, Double.NaN)
        val got  = dc(data).drop(1L).take(2L).runCollect.toOption.get.toList
        val exp  = data.drop(1).take(2)
        val ok   = got.length == exp.length && got.zip(exp).forall { case (g, e) =>
          java.lang.Double.doubleToRawLongBits(g) == java.lang.Double.doubleToRawLongBits(e)
        }
        assertTrue(ok)
      },
      test("Byte lane: empty/single boundaries for sliding/chunked/scan [AdversarialSpecializedLaneBoundarySpec]") {
        val empty: Seq[Byte] = Seq.empty
        assertTrue(bc(empty).sliding(3).runCollect == Right(Chunk.empty[Chunk[Byte]])) &&
        assertTrue(bc(empty).chunked(3).runCollect == Right(Chunk.empty[Chunk[Byte]])) &&
        assertTrue(bc(empty).scan(0.toByte)((a, b) => (a + b).toByte).runCollect == Right(Chunk(0.toByte))) &&
        assertTrue(bc(Seq(7.toByte)).sliding(3).runCollect == Right(Chunk(Chunk(7.toByte)))) &&
        assertTrue(bc(Seq(7.toByte)).chunked(3).runCollect == Right(Chunk(Chunk(7.toByte)))) &&
        assertTrue(
          bc(Seq(7.toByte)).scan(0.toByte)((a, b) => (a + b).toByte).runCollect == Right(Chunk(0.toByte, 7.toByte))
        )
      },
      test(
        "Long lane: sliding window exactly equal to / larger than stream length [AdversarialSpecializedLaneBoundarySpec]"
      ) {
        val data = List(1L, 2L, 3L)
        assertTrue(lc(data).sliding(3).runCollect.toOption.get.map(_.toList).toList == List(List(1L, 2L, 3L))) &&
        assertTrue(lc(data).sliding(5).runCollect.toOption.get.map(_.toList).toList == List(List(1L, 2L, 3L))) &&
        // step exactly at and beyond length
        assertTrue(lc(data).sliding(1, 3).runCollect.toOption.get.map(_.toList).toList == List(List(1L))) &&
        assertTrue(lc(data).sliding(1, 5).runCollect.toOption.get.map(_.toList).toList == List(List(1L)))
      },
      test(
        "Long lane: empty stream through scan emits only init; count/fold are 0 [AdversarialSpecializedLaneBoundarySpec]"
      ) {
        val empty: Seq[Long] = Seq.empty
        assertTrue(lc(empty).scan(42L)(_ + _).runCollect == Right(Chunk(42L))) &&
        assertTrue(lc(empty).count == Right(0L)) &&
        assertTrue(lc(empty).runFold(0L)(_ + _) == Right(0L)) &&
        assertTrue(lc(empty).take(5L).runCollect == Right(Chunk.empty[Long])) &&
        assertTrue(lc(empty).drop(5L).runCollect == Right(Chunk.empty[Long]))
      }
    )
  )

  // ===========================================================================
  // Specialized-vs-generic differential (AdversarialSpecializedLaneDifferentialSpec)
  // ===========================================================================
  val specializedLaneDifferentialSuite = suite("specialized-vs-generic differential")(
    suite("regressions")(
      test(
        "sliding(n, step) — SPECIALIZED lanes match List.sliding across a matrix [AdversarialSpecializedLaneDifferentialSpec]"
      ) {
        val bad = scala.collection.mutable.ListBuffer.empty[String]
        for (len <- 0 to 9; n <- 1 to 5; step <- 1 to 5) {
          val ints                                          = (1 to len).toList
          val exp                                           = ints.sliding(n, step).map(_.toList).toList
          def chk(lane: String, got: List[List[Any]]): Unit =
            if (got != exp) bad += s"$lane len=$len n=$n step=$step exp=$exp got=$got"
          chk("Int", sInt(ints).sliding(n, step).runCollect.toOption.get.map(_.toList).toList)
          chk(
            "Long",
            sLong(ints.map(_.toLong)).sliding(n, step).runCollect.toOption.get.map(_.toList.map(_.toInt)).toList
          )
          chk(
            "Float",
            sFloat(ints.map(_.toFloat)).sliding(n, step).runCollect.toOption.get.map(_.toList.map(_.toInt)).toList
          )
          chk(
            "Double",
            sDouble(ints.map(_.toDouble)).sliding(n, step).runCollect.toOption.get.map(_.toList.map(_.toInt)).toList
          )
          chk(
            "Byte",
            sByte(ints.map(_.toByte)).sliding(n, step).runCollect.toOption.get.map(_.toList.map(_.toInt)).toList
          )
        }
        assertTrue(bad.toList == Nil)
      },
      test(
        "chunked(n) — SPECIALIZED lanes match List.grouped across a matrix [AdversarialSpecializedLaneDifferentialSpec]"
      ) {
        val bad = scala.collection.mutable.ListBuffer.empty[String]
        for (len <- 0 to 9; n <- 1 to 6) {
          val ints                                          = (1 to len).toList
          val exp                                           = ints.grouped(n).map(_.toList).toList
          def chk(lane: String, got: List[List[Any]]): Unit =
            if (got != exp) bad += s"$lane len=$len n=$n exp=$exp got=$got"
          chk("Int", sInt(ints).chunked(n).runCollect.toOption.get.map(_.toList).toList)
          chk("Long", sLong(ints.map(_.toLong)).chunked(n).runCollect.toOption.get.map(_.toList.map(_.toInt)).toList)
          chk("Float", sFloat(ints.map(_.toFloat)).chunked(n).runCollect.toOption.get.map(_.toList.map(_.toInt)).toList)
          chk(
            "Double",
            sDouble(ints.map(_.toDouble)).chunked(n).runCollect.toOption.get.map(_.toList.map(_.toInt)).toList
          )
          chk("Byte", sByte(ints.map(_.toByte)).chunked(n).runCollect.toOption.get.map(_.toList.map(_.toInt)).toList)
        }
        assertTrue(bad.toList == Nil)
      },
      test(
        "scan(z)(f) — SPECIALIZED lanes match List.scanLeft across a matrix [AdversarialSpecializedLaneDifferentialSpec]"
      ) {
        val bad = scala.collection.mutable.ListBuffer.empty[String]
        for (len <- 0 to 7) {
          val ints = (1 to len).toList
          val expI = ints.scanLeft(0)(_ + _)
          if (sInt(ints).scan(0)(_ + _).runCollect.toOption.get.toList != expI) bad += s"Int len=$len"
          val ls   = ints.map(_.toLong)
          val expL = ls.scanLeft(0L)(_ + _)
          if (sLong(ls).scan(0L)(_ + _).runCollect.toOption.get.toList != expL) bad += s"Long len=$len"
          val fs   = ints.map(_.toFloat)
          val expF = fs.scanLeft(0.0f)(_ + _)
          if (sFloat(fs).scan(0.0f)(_ + _).runCollect.toOption.get.toList != expF) bad += s"Float len=$len"
          val ds   = ints.map(_.toDouble)
          val expD = ds.scanLeft(0.0)(_ + _)
          if (sDouble(ds).scan(0.0)(_ + _).runCollect.toOption.get.toList != expD) bad += s"Double len=$len"
          val bs   = ints.map(_.toByte)
          val expB = bs.scanLeft(0.toByte)((a, b) => (a + b).toByte)
          if (sByte(bs).scan(0.toByte)((a, b) => (a + b).toByte).runCollect.toOption.get.toList != expB)
            bad += s"Byte len=$len"
        }
        assertTrue(bad.toList == Nil)
      },
      // ---- In-band sentinel disambiguation in the SPECIALIZED branches --------
      test(
        "Long lane: chunked/sliding/scan/distinct preserve real Long.MaxValue elements [AdversarialSpecializedLaneDifferentialSpec]"
      ) {
        val data = List(1L, Long.MaxValue, 3L, Long.MaxValue, Long.MaxValue)
        val ch   = sLong(data).chunked(2).runCollect.toOption.get.map(_.toList).toList
        val sl   = sLong(data).sliding(2).runCollect.toOption.get.map(_.toList).toList
        // scan that simply re-emits the incoming value as the new state surfaces
        // each (sentinel-valued) element through the Long-lane EOF disambiguation.
        val scn = sLong(data).scan(0L)((_, a) => a).runCollect.toOption.get.toList
        val dst = sLong(List(Long.MaxValue, Long.MaxValue, 1L)).distinct.runCollect.toOption.get.toList
        assertTrue(ch == data.grouped(2).map(_.toList).toList) &&
        assertTrue(sl == data.sliding(2).map(_.toList).toList) &&
        assertTrue(scn == 0L :: data) &&
        assertTrue(dst == List(Long.MaxValue, 1L))
      },
      test(
        "Double lane: chunked/sliding/scan preserve real Double.MaxValue and NaN elements [AdversarialSpecializedLaneDifferentialSpec]"
      ) {
        val data                                           = List(1.0, Double.MaxValue, Double.NaN, Double.MaxValue)
        val ch                                             = sDouble(data).chunked(2).runCollect.toOption.get.map(_.toList).toList
        val sl                                             = sDouble(data).sliding(3).runCollect.toOption.get.map(_.toList).toList
        val scn                                            = sDouble(data).scan(0.0)((_, a) => a).runCollect.toOption.get.toList
        def eqD(a: List[Double], b: List[Double]): Boolean =
          a.length == b.length && a.zip(b).forall { case (x, y) => x == y || (x.isNaN && y.isNaN) }
        assertTrue(ch.length == data.grouped(2).length) &&
        assertTrue(ch.zip(data.grouped(2).map(_.toList).toList).forall { case (g, e) => eqD(g, e) }) &&
        assertTrue(sl.zip(data.sliding(3).map(_.toList).toList).forall { case (g, e) => eqD(g, e) }) &&
        assertTrue(eqD(scn, 0.0 :: data))
      },
      // ---- reset() under repeated for the SPECIALIZED branches ----------------
      test(
        "repeated cycles SPECIALIZED sliding/chunked/scan correctly (Long lane) [AdversarialSpecializedLaneDifferentialSpec]"
      ) {
        val base = List(1L, 2L, 3L)
        val sl   = sLong(base).sliding(2).repeated.take(4).runCollect.toOption.get.map(_.toList).toList
        val ch   = sLong(base).chunked(2).repeated.take(4).runCollect.toOption.get.map(_.toList).toList
        val scn  = sLong(base).scan(0L)(_ + _).repeated.take(8).runCollect.toOption.get.toList
        // sliding(2) of [1,2,3] = [[1,2],[2,3]]; one cycle = 2 windows, so take(4) = 2 cycles.
        assertTrue(sl == List(List(1L, 2L), List(2L, 3L), List(1L, 2L), List(2L, 3L))) &&
        // chunked(2) of [1,2,3] = [[1,2],[3]]; take(4) = 2 cycles.
        assertTrue(ch == List(List(1L, 2L), List(3L), List(1L, 2L), List(3L))) &&
        // scan(0)(+) of [1,2,3] = [0,1,3,6]; cycle restarts the accumulator at 0.
        assertTrue(scn == List(0L, 1L, 3L, 6L, 0L, 1L, 3L, 6L))
      }
    )
  )

  // ===========================================================================
  // takeWhile byte lane (AdversarialTakeWhileByteSpec)
  // ===========================================================================
  val takeWhileByteLaneSuite = suite("takeWhile byte lane")(
    suite("regressions")(
      test("takeWhile_byteLane_collect_doesNotCCE [AdversarialTakeWhileByteSpec]") {
        // Oracle: Int-lane analogue yields Chunk(1, 2); Byte lane must match.
        assertTrue(bytes.takeWhile(_ < 3).runCollect == Right(Chunk[Byte](1, 2)))
      },
      test("takeWhile_byteLane_count_doesNotCCE [AdversarialTakeWhileByteSpec]") {
        assertTrue(bytes.takeWhile(_ < 3).count == Right(2L))
      },
      test("takeWhile_byteLane_fold_doesNotCCE [AdversarialTakeWhileByteSpec]") {
        assertTrue(bytes.takeWhile(_ < 3).runFold(0)((a, b) => a + b) == Right(3))
      },
      test("takeWhile_byteLane_find_doesNotCCE [AdversarialTakeWhileByteSpec]") {
        assertTrue(bytes.takeWhile(_ < 3).find(_ == 2.toByte) == Right(Some(2.toByte)))
      }
    )
  )
}
