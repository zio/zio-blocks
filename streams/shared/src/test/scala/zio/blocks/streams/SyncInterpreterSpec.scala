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

// Cross-platform semantic coverage.

import zio.blocks.async._
import zio.blocks.chunk.Chunk
import zio.blocks.streams.internal._
import zio.blocks.streams.io.Reader
import zio.test._

/**
 * Tests for SyncInterpreter V3: standalone stack machine with stage stack
 * support.
 *
 * Covers:
 *   - Single map (all 5 input types)
 *   - Cross-type map (7 crossings)
 *   - Chained maps (5 and 100)
 *   - Filter ops (all 5 types)
 *   - Filter + map interleaved
 *   - (ArrayStack removed — inlined into SyncInterpreter)
 *   - Helper functions (laneOf, outLaneOf, mapTag, filterTag,
 *     storageLaneOfMapTag, elemTypeOfLane)
 *   - PushOp: flatMap with single-element inner
 *   - PushOp: flatMap with multi-element inner
 *   - PushOp: nested flatMap
 *   - PushOp: flatMap with tail ops (reassociation)
 *   - Empty inner stream
 *   - Source exhaustion
 *   - addOp reassociation
 *   - Complex chains (map+flatMap+map, filter+flatMap, consecutive flatMaps,
 *     etc.)
 *   - outputType verification
 *   - Construction: fromStream, unsealed, apply
 *   - Reset and re-read
 *   - Close behavior (isClosed, read after close, double close)
 *   - skip(n)
 *   - readable()
 *   - jvmType for all lane types
 *   - Specialized read methods (readInt, readLong, readFloat, readDouble, read)
 *   - bridgeTag and bridgeFn static helpers
 *   - Large data (1000+ elements)
 *   - Edge cases (only filters, only maps, single-element, very long pipeline)
 */
object SyncInterpreterSpec extends StreamsBaseSpec {
  import SyncInterpreter._
  import OpTag.{mapTag, filterTag, storageLaneOfMapTag}

  override def aspects: zio.Chunk[TestAspectAtLeastR[TestEnvironment]] =
    if (TestPlatform.isJVM)
      zio.Chunk(TestAspect.timeout(zio.Duration.fromSeconds(90)), TestAspect.timed)
    else
      zio.Chunk(
        TestAspect.timeout(zio.Duration.fromSeconds(90)),
        TestAspect.timed,
        TestAspect.sequential,
        TestAspect.size(10)
      )

  private def readIntValue(p: SyncInterpreter, sentinel: Long): Long =
    p.asInstanceOf[Reader.SyncReader[Int]].readInt(sentinel)

  private def readLongValue(p: SyncInterpreter, sentinel: Long): Long =
    p.asInstanceOf[Reader.SyncReader[Long]].readLong(sentinel)

  private def readFloatValue(p: SyncInterpreter, sentinel: Double): Double =
    p.asInstanceOf[Reader.SyncReader[Float]].readFloat(sentinel)

  private def readDoubleValue(p: SyncInterpreter, sentinel: Double): Double =
    p.asInstanceOf[Reader.SyncReader[Double]].readDouble(sentinel)

  def spec = suite("SyncInterpreter V3")(
    singleMapSuite,
    crossTypeMapSuite,
    chainedMapsSuite,
    filterSuite,
    filterMapInterleavedSuite,
    // ArrayStack suite removed — ArrayStack eliminated (inlined into SyncInterpreter)
    helperFunctionSuite,
    pushOpSingleElementSuite,
    pushOpMultiElementSuite,
    pushOpNestedSuite,
    pushOpTailOpsSuite,
    emptyInnerStreamSuite,
    sourceExhaustionSuite,
    addOpReassociationSuite,
    complexChainSuite,
    outputTypeSuite,
    crossTypeSyncInterpreterSuite,
    depthBasedCompilationSuite,
    stackSafetySuite,
    constructionSuite,
    stickyFailureSuite,
    resetAndRereadSuite,
    closeBehaviorSuite,
    skipSuite,
    readableSuite,
    jvmTypeSuite,
    specializedReadSuite,
    bridgeHelperSuite,
    booleanLogicalTypeSuite,
    largeDataSuite,
    edgeCaseSuite
  )

  // ---- drain helpers ----

  private def drainInts(p: SyncInterpreter, sentinel: Long = Long.MinValue): List[Int] = {
    val buf = scala.collection.mutable.ListBuffer[Int]()
    var v   = readIntValue(p, sentinel)
    while (v != sentinel) {
      buf += v.toInt
      v = readIntValue(p, sentinel)
    }
    buf.toList
  }

  private def drainLongs(p: SyncInterpreter, sentinel: Long = Long.MaxValue): List[Long] = {
    val buf = scala.collection.mutable.ListBuffer[Long]()
    var v   = readLongValue(p, sentinel)
    while (v != sentinel) {
      buf += v
      v = readLongValue(p, sentinel)
    }
    buf.toList
  }

  private def drainFloats(p: SyncInterpreter, sentinel: Double = Double.MaxValue): List[Float] = {
    val buf = scala.collection.mutable.ListBuffer[Float]()
    var v   = readFloatValue(p, sentinel)
    while (v != sentinel) {
      buf += v.toFloat
      v = readFloatValue(p, sentinel)
    }
    buf.toList
  }

  private def drainDoubles(p: SyncInterpreter, sentinel: Double = Double.MaxValue): List[Double] = {
    val buf = scala.collection.mutable.ListBuffer[Double]()
    var v   = readDoubleValue(p, sentinel)
    while (v != sentinel) {
      buf += v
      v = readDoubleValue(p, sentinel)
    }
    buf.toList
  }

  private def drainGeneric(p: SyncInterpreter): List[Any] = {
    val sentinel: AnyRef = new AnyRef // unique sentinel object
    val buf              = scala.collection.mutable.ListBuffer[Any]()
    var v                = p.read(sentinel)
    while (v.asInstanceOf[AnyRef] ne sentinel) {
      buf += v
      v = p.read(sentinel)
    }
    buf.toList
  }

  // =========================================================================
  //  1. Single map, all 5 input types
  // =========================================================================

  val singleMapSuite = suite("Single map - all 5 input types")(
    test("Int source + MAP_II (tag 0)") {
      val source         = Reader.fromRange(0 until 5)
      val fn: Int => Int = _ + 1
      val p              = SyncInterpreter(source)
      p.addMap[Int, Int](LANE_I, OUT_I)(fn)
      val result = drainInts(p)
      assertTrue(result == List(1, 2, 3, 4, 5))
    },
    test("Long source + MAP_LL (tag 8)") {
      val source           = Reader.fromChunk(Chunk(10L, 20L, 30L))
      val fn: Long => Long = _ + 1L
      val p                = SyncInterpreter(source)
      p.addMap[Long, Long](LANE_L, OUT_L)(fn)
      val result = drainLongs(p)
      assertTrue(result == List(11L, 21L, 31L))
    },
    test("Float source + MAP_FF (tag 16)") {
      val source             = Reader.fromChunk(Chunk(1.0f, 2.0f, 3.0f))
      val fn: Float => Float = _ + 0.5f
      val p                  = SyncInterpreter(source)
      p.addMap[Float, Float](LANE_F, OUT_F)(fn)
      val result = drainFloats(p)
      assertTrue(result == List(1.5f, 2.5f, 3.5f))
    },
    test("Double source + MAP_DD (tag 24)") {
      val source               = Reader.fromChunk(Chunk(1.0, 2.0, 3.0))
      val fn: Double => Double = _ * 2.0
      val p                    = SyncInterpreter(source)
      p.addMap[Double, Double](LANE_D, OUT_D)(fn)
      val result = drainDoubles(p)
      assertTrue(result == List(2.0, 4.0, 6.0))
    },
    test("AnyRef source + MAP_RR (tag 34)") {
      val source               = Reader.fromIterable(List("hello", "world"))
      val fn: AnyRef => AnyRef = (s: AnyRef) => s.asInstanceOf[String].toUpperCase
      val p                    = SyncInterpreter(source)
      p.addMap[AnyRef, AnyRef](LANE_R, OUT_R)(fn)
      val result = drainGeneric(p)
      assertTrue(result == List("HELLO", "WORLD"))
    }
  )

  // =========================================================================
  //  2. Cross-type map
  // =========================================================================

  val crossTypeMapSuite = suite("Cross-type map")(
    test("Int->Long (tag 1)") {
      val source          = Reader.fromRange(0 until 5)
      val fn: Int => Long = _.toLong * 100L
      val p               = SyncInterpreter(source)
      p.addMap[Int, Long](LANE_I, OUT_L)(fn)
      val result = drainLongs(p)
      assertTrue(result == List(0L, 100L, 200L, 300L, 400L))
    },
    test("Int->Double (tag 3)") {
      val source            = Reader.fromRange(0 until 3)
      val fn: Int => Double = _.toDouble + 0.5
      val p                 = SyncInterpreter(source)
      p.addMap[Int, Double](LANE_I, OUT_D)(fn)
      val result = drainDoubles(p)
      assertTrue(result == List(0.5, 1.5, 2.5))
    },
    test("Int->Float (tag 2)") {
      val source           = Reader.fromRange(0 until 4)
      val fn: Int => Float = _.toFloat
      val p                = SyncInterpreter(source)
      p.addMap[Int, Float](LANE_I, OUT_F)(fn)
      val result = drainFloats(p)
      assertTrue(result == List(0.0f, 1.0f, 2.0f, 3.0f))
    },
    test("Int->AnyRef (tag 6)") {
      val source            = Reader.fromRange(0 until 3)
      val fn: Int => AnyRef = (i: Int) => s"v$i"
      val p                 = SyncInterpreter(source)
      p.addMap[Int, AnyRef](LANE_I, OUT_R)(fn)
      val result = drainGeneric(p)
      assertTrue(result == List("v0", "v1", "v2"))
    },
    test("Long->Double (tag 10)") {
      val source             = Reader.fromChunk(Chunk(1L, 2L, 3L))
      val fn: Long => Double = _.toDouble
      val p                  = SyncInterpreter(source)
      p.addMap[Long, Double](LANE_L, OUT_D)(fn)
      val result = drainDoubles(p)
      assertTrue(result == List(1.0, 2.0, 3.0))
    },
    test("AnyRef->Int (tag 28)") {
      val source            = Reader.fromIterable(List("hello", "ab", "x"))
      val fn: AnyRef => Int = (s: AnyRef) => s.asInstanceOf[String].length
      val p                 = SyncInterpreter(source)
      p.addMap[AnyRef, Int](LANE_R, OUT_I)(fn)
      val result = drainInts(p)
      assertTrue(result == List(5, 2, 1))
    },
    test("Double->Int (tag 21)") {
      val source            = Reader.fromChunk(Chunk(1.9, 2.1, 3.7))
      val fn: Double => Int = _.toInt
      val p                 = SyncInterpreter(source)
      p.addMap[Double, Int](LANE_D, OUT_I)(fn)
      val result = drainInts(p)
      assertTrue(result == List(1, 2, 3))
    }
  )

  // =========================================================================
  //  3. Chained maps
  // =========================================================================

  val chainedMapsSuite = suite("Chained maps")(
    test("5 chained MAP_II ops") {
      val source         = Reader.fromRange(0 until 10)
      val fn: Int => Int = _ + 1
      val p              = SyncInterpreter(source)
      var i              = 0; while (i < 5) { p.addMap[Int, Int](LANE_I, OUT_I)(fn); i += 1 }
      val result         = drainInts(p)
      val expected       = (0 until 10).map(_ + 5).toList
      assertTrue(result == expected)
    },
    test("100 chained MAP_II ops") {
      val source         = Reader.fromRange(0 until 5)
      val fn: Int => Int = _ + 1
      val p              = SyncInterpreter(source)
      var i              = 0; while (i < 100) { p.addMap[Int, Int](LANE_I, OUT_I)(fn); i += 1 }
      val result         = drainInts(p)
      val expected       = (0 until 5).map(_ + 100).toList
      assertTrue(result == expected)
    }
  )

  // =========================================================================
  //  4. Filter ops - all 5 filter types
  // =========================================================================

  val filterSuite = suite("Filter ops")(
    test("FILTER_I (tag 35) - Int") {
      val source               = Reader.fromRange(0 until 10)
      val pred: Int => Boolean = _ % 2 == 0
      val p                    = SyncInterpreter(source)
      p.addFilter[Int](LANE_I)(pred)
      val result = drainInts(p)
      assertTrue(result == List(0, 2, 4, 6, 8))
    },
    test("FILTER_L (tag 36) - Long") {
      val source                = Reader.fromChunk(Chunk(1L, 2L, 3L, 4L, 5L))
      val pred: Long => Boolean = _ > 3L
      val p                     = SyncInterpreter(source)
      p.addFilter[Long](LANE_L)(pred)
      val result = drainLongs(p)
      assertTrue(result == List(4L, 5L))
    },
    test("FILTER_F (tag 37) - Float") {
      val source                 = Reader.fromChunk(Chunk(1.0f, 2.0f, 3.0f, 4.0f, 5.0f))
      val pred: Float => Boolean = _ > 2.5f
      val p                      = SyncInterpreter(source)
      p.addFilter[Float](LANE_F)(pred)
      val result = drainFloats(p)
      assertTrue(result == List(3.0f, 4.0f, 5.0f))
    },
    test("FILTER_D (tag 38) - Double") {
      val source                  = Reader.fromChunk(Chunk(1.0, 2.0, 3.0, 4.0, 5.0))
      val pred: Double => Boolean = _ > 2.5
      val p                       = SyncInterpreter(source)
      p.addFilter[Double](LANE_D)(pred)
      val result = drainDoubles(p)
      assertTrue(result == List(3.0, 4.0, 5.0))
    },
    test("FILTER_R (tag 39) - AnyRef") {
      val source                  = Reader.fromIterable(List("hello", "", "world", ""))
      val pred: AnyRef => Boolean = (s: AnyRef) => s.asInstanceOf[String].nonEmpty
      val p                       = SyncInterpreter(source)
      p.addFilter[AnyRef](LANE_R)(pred)
      val result = drainGeneric(p)
      assertTrue(result == List("hello", "world"))
    }
  )

  // =========================================================================
  //  5. Filter + map interleaved
  // =========================================================================

  val filterMapInterleavedSuite = suite("Filter + map interleaved")(
    test("filter then map: evens * 3") {
      val source               = Reader.fromRange(0 until 10)
      val pred: Int => Boolean = _ % 2 == 0
      val fn: Int => Int       = _ * 3
      val p                    = SyncInterpreter(source)
      p.addFilter[Int](LANE_I)(pred)
      p.addMap[Int, Int](LANE_I, OUT_I)(fn)
      val result   = drainInts(p)
      val expected = (0 until 10).filter(_ % 2 == 0).map(_ * 3).toList
      assertTrue(result == expected)
    },
    test("map then filter: +1 then even") {
      val source               = Reader.fromRange(0 until 10)
      val fn: Int => Int       = _ + 1
      val pred: Int => Boolean = _ % 2 == 0
      val p                    = SyncInterpreter(source)
      p.addMap[Int, Int](LANE_I, OUT_I)(fn)
      p.addFilter[Int](LANE_I)(pred)
      val result   = drainInts(p)
      val expected = (0 until 10).map(_ + 1).filter(_ % 2 == 0).toList
      assertTrue(result == expected)
    },
    test("map + filter + map: Int->Long then filter then Long->Long") {
      val source                = Reader.fromRange(0 until 10)
      val fn1: Int => Long      = _.toLong
      val pred: Long => Boolean = _ > 3L
      val fn2: Long => Long     = _ * 10L
      val p                     = SyncInterpreter(source)
      p.addMap[Int, Long](LANE_I, OUT_L)(fn1)
      p.addFilter[Long](LANE_L)(pred)
      p.addMap[Long, Long](LANE_L, OUT_L)(fn2)
      val result   = drainLongs(p)
      val expected = (0 until 10).map(_.toLong).filter(_ > 3L).map(_ * 10L).toList
      assertTrue(result == expected)
    }
  )

  // =========================================================================
  //  6. ArrayStack tests — REMOVED (ArrayStack eliminated; inlined into SyncInterpreter)
  // =========================================================================

  // =========================================================================
  //  7. Helper function tests
  // =========================================================================

  val helperFunctionSuite = suite("Helper functions")(
    test("laneOf for all JvmType values") {
      assertTrue(
        laneOf(JvmType.Int) == LANE_I &&
          laneOf(JvmType.Boolean) == LANE_I &&
          laneOf(JvmType.Long) == LANE_L &&
          laneOf(JvmType.Float) == LANE_F &&
          laneOf(JvmType.Double) == LANE_D &&
          laneOf(JvmType.AnyRef) == LANE_R &&
          laneOf(JvmType.Byte) == LANE_I &&
          laneOf(JvmType.Short) == LANE_I &&
          laneOf(JvmType.Char) == LANE_I
      )
    },
    test("outLaneOf for all JvmType values") {
      assertTrue(
        outLaneOf(JvmType.Int) == OUT_I &&
          outLaneOf(JvmType.Long) == OUT_L &&
          outLaneOf(JvmType.Float) == OUT_F &&
          outLaneOf(JvmType.Double) == OUT_D &&
          outLaneOf(JvmType.Boolean) == OUT_I &&
          outLaneOf(JvmType.AnyRef) == OUT_R &&
          outLaneOf(JvmType.Byte) == OUT_I &&
          outLaneOf(JvmType.Short) == OUT_I &&
          outLaneOf(JvmType.Char) == OUT_I
      )
    },
    test("mapTag and filterTag spot checks") {
      assertTrue(
        mapTag(LANE_I, OUT_I) == 0 &&
          mapTag(LANE_I, OUT_L) == 1 &&
          mapTag(LANE_L, OUT_I) == 5 &&
          mapTag(LANE_L, OUT_L) == 6 &&
          mapTag(LANE_R, OUT_R) == 24 &&
          filterTag(LANE_I) == 25 &&
          filterTag(LANE_L) == 26 &&
          filterTag(LANE_F) == 27 &&
          filterTag(LANE_D) == 28 &&
          filterTag(LANE_R) == 29
      )
    },
    test("readTag preserves all eight physical identities independently of storage lanes") {
      assertTrue(
        OpTag.readTag(JvmType.Boolean) == OpTag.READ_B,
        OpTag.readTag(JvmType.Byte) == OpTag.READ_BY,
        OpTag.readTag(JvmType.Char) == OpTag.READ_C,
        OpTag.readTag(JvmType.Short) == OpTag.READ_S,
        OpTag.readTag(JvmType.Int) == OpTag.READ_I,
        OpTag.readTag(JvmType.Long) == OpTag.READ_L,
        OpTag.readTag(JvmType.Float) == OpTag.READ_F,
        OpTag.readTag(JvmType.Double) == OpTag.READ_D,
        OpTag.readTag(JvmType.AnyRef) == OpTag.READ_R,
        OpTag.storageLaneOfReadTag(OpTag.READ_B) == 0,
        OpTag.storageLaneOfReadTag(OpTag.READ_BY) == 0,
        OpTag.storageLaneOfReadTag(OpTag.READ_C) == 0,
        OpTag.storageLaneOfReadTag(OpTag.READ_S) == 0,
        OpTag.storageLaneOfReadTag(OpTag.READ_I) == 0,
        OpTag.storageLaneOfReadTag(OpTag.READ_L) == 1,
        OpTag.storageLaneOfReadTag(OpTag.READ_F) == 2,
        OpTag.storageLaneOfReadTag(OpTag.READ_D) == 3,
        OpTag.storageLaneOfReadTag(OpTag.READ_R) == 4
      )
    },
    test("storageLaneOfMapTag") {
      assertTrue(
        storageLaneOfMapTag(mapTag(LANE_I, OUT_I)) == LANE_I &&
          storageLaneOfMapTag(mapTag(LANE_I, OUT_L)) == LANE_L &&
          storageLaneOfMapTag(mapTag(LANE_I, OUT_F)) == LANE_F &&
          storageLaneOfMapTag(mapTag(LANE_I, OUT_D)) == LANE_D &&
          storageLaneOfMapTag(mapTag(LANE_I, OUT_R)) == LANE_R
      )
    },
    test("elemTypeOfLane") {
      assertTrue(
        elemTypeOfLane(LANE_I) == JvmType.Int &&
          elemTypeOfLane(LANE_L) == JvmType.Long &&
          elemTypeOfLane(LANE_F) == JvmType.Float &&
          elemTypeOfLane(LANE_D) == JvmType.Double &&
          elemTypeOfLane(LANE_R) == JvmType.AnyRef
      )
    }
  )

  val booleanLogicalTypeSuite = suite("Boolean logical type on I lane")(
    test("source preserves Boolean type, boxes generic reads, normalizes specialized reads, EOF, and reset") {
      val p      = SyncInterpreter.fromStream(Stream.fromChunk(Chunk(true, false, true)))
      val b      = p.asInstanceOf[Reader.SyncReader[Boolean]]
      val first  = p.read[Any](EndOfStream)
      val second = b.readBoolean(-7)
      val third  = b.readBoolean(-7)
      val eof    = b.readBoolean(-7)
      p.reset()
      assertTrue(
        p.outputLane == OUT_I,
        p.jvmType == JvmType.Boolean,
        first == java.lang.Boolean.TRUE,
        second == 0,
        third == 1,
        eof == -7,
        b.readBoolean(-7) == 1
      )
    },
    test("Boolean maps to every physical lane and reference") {
      def mapped[B](jt: JvmType)(f: Boolean => B): Any = {
        val p = SyncInterpreter(Reader.singleBoolean(true))
        p.addMap(JvmType.Boolean, jt)(f)
        p.read[Any](EndOfStream)
      }
      assertTrue(
        mapped(JvmType.Boolean)(!_) == java.lang.Boolean.FALSE,
        mapped(JvmType.Int)(b => if (b) 2 else 3) == 2,
        mapped(JvmType.Long)(b => if (b) 2L else 3L) == 2L,
        mapped(JvmType.Float)(b => if (b) 2f else 3f) == 2f,
        mapped(JvmType.Double)(b => if (b) 2d else 3d) == 2d,
        mapped(JvmType.AnyRef)(b => if (b) "yes" else "no") == "yes"
      )
    },
    test("every physical lane and reference maps to normalized Boolean") {
      def value(source: Reader.SyncReader[_], in: JvmType, f: Any): Int = {
        val p = SyncInterpreter(source)
        p.addMap[Any, Boolean](in, JvmType.Boolean)(f.asInstanceOf[Any => Boolean])
        p.asInstanceOf[Reader.SyncReader[Boolean]].readBoolean(-1)
      }
      assertTrue(
        value(Reader.fromChunk(Chunk(1)), JvmType.Int, ((i: Int) => i == 1)) == 1,
        value(Reader.fromChunk(Chunk(1L)), JvmType.Long, ((i: Long) => i == 1L)) == 1,
        value(Reader.fromChunk(Chunk(1f)), JvmType.Float, ((i: Float) => i == 1f)) == 1,
        value(Reader.fromChunk(Chunk(1d)), JvmType.Double, ((i: Double) => i == 1d)) == 1,
        value(Reader.fromIterable(List("x")), JvmType.AnyRef, ((i: AnyRef) => i == "x")) == 1
      )
    },
    test("Boolean filter and collect preserve callback and output semantics") {
      val filtered  = SyncInterpreter.fromStream(Stream.fromChunk(Chunk(true, false, true)).filter(identity))
      val collected = SyncInterpreter.fromStream(
        Stream.fromChunk(Chunk(true, false)).collect[Boolean] { case true => false }
      )
      assertTrue(
        drainGeneric(filtered) == List(true, true),
        filtered.jvmType == JvmType.Boolean,
        drainGeneric(collected) == List(false),
        collected.jvmType == JvmType.Boolean
      )
    },
    test("flatMap before and after Boolean maps restores logical frame type for nested Boolean streams") {
      val before = SyncInterpreter.fromStream(
        Stream.fromChunk(Chunk(true, false)).flatMap(b => Stream.fromChunk(Chunk(b, !b))).map(!_)
      )
      val after = SyncInterpreter.fromStream(
        Stream.fromChunk(Chunk(true, false)).map(!_).flatMap(b => Stream.fromChunk(Chunk(b, !b)))
      )
      assertTrue(
        drainGeneric(before) == List(false, true, true, false),
        drainGeneric(after) == List(false, true, true, false),
        before.jvmType == JvmType.Boolean,
        after.jvmType == JvmType.Boolean
      )
    },
    test("deep Stream compilation uses SyncInterpreter without confusing Int and Boolean") {
      var booleans: Stream[Nothing, Boolean] = Stream.fromChunk(Chunk(true, false))
      var i                                  = 0
      while (i < 101) { booleans = booleans.map(!_); i += 1 }
      val booleanReader = booleans.compile(0).expectedSync
      val intReader     = Stream.fromRange(0 until 2).map(_ + 1).map(_ + 1).compile(0).expectedSync
      assertTrue(
        booleanReader.isInstanceOf[SyncInterpreter],
        booleanReader.jvmType == JvmType.Boolean,
        booleanReader.read[Any](EndOfStream) == java.lang.Boolean.FALSE,
        intReader.jvmType == JvmType.Int
      )
    },
    test("push output type and post-push callbacks preserve logical Boolean and original Int values") {
      val intToBoolean = SyncInterpreter.fromStream(
        Stream.fromChunk(Chunk(1, 2)).flatMap(i => Stream.fromChunk(Chunk(i == 2)))
      )
      val booleanToInt = SyncInterpreter.fromStream(
        Stream
          .fromChunk(Chunk(true, false))
          .flatMap[Nothing, Nothing, Int](b => Stream.fromChunk(Chunk(if (b) 10 else 20)))
      )
      val postPush = SyncInterpreter.fromStream(
        Stream.fromChunk(Chunk(1)).flatMap(_ => Stream.fromChunk(Chunk(10))).map(_ > 5)
      )
      assertTrue(
        intToBoolean.jvmType == JvmType.Boolean,
        drainGeneric(intToBoolean) == List(false, true),
        booleanToInt.jvmType == JvmType.Int,
        drainGeneric(booleanToInt) == List(10, 20),
        drainGeneric(postPush) == List(true)
      )
    },
    test("nested push bridges Boolean to AnyRef without boxing it as Int") {
      val widened = SyncInterpreter.fromStream(
        Stream.fromChunk(Chunk(1)).flatMap { _ =>
          val inner: Stream[Nothing, Any] = Stream.fromChunk(Chunk(true))
          inner
        }
      )
      val nested = SyncInterpreter.fromStream(
        Stream
          .fromChunk(Chunk(true))
          .flatMap(b => Stream.fromChunk(Chunk(!b)))
          .flatMap(b => Stream.fromChunk(Chunk(b, !b)))
      )
      assertTrue(
        widened.jvmType == JvmType.AnyRef,
        drainGeneric(widened) == List(java.lang.Boolean.TRUE),
        nested.jvmType == JvmType.Boolean,
        drainGeneric(nested) == List(false, true)
      )
    },
    test("shallow Boolean map, filter, collect, and flatMap preserve callbacks, boxing, and sentinels") {
      val mapped     = Stream.fromChunk(Chunk(true, false)).map(!_).compile(0).expectedSync
      val filtered   = Stream.fromChunk(Chunk(false, true, false)).filter(identity).compile(0).expectedSync
      val collected  = Stream.fromChunk(Chunk(false, true)).collect { case true => false }.compile(0).expectedSync
      val flatMapped =
        Stream.fromChunk(Chunk(false, true)).flatMap(b => Stream.fromChunk(Chunk(!b))).compile(0).expectedAsync
      val intToBoolean = Stream.fromChunk(Chunk(0, 1)).map(_ != 0).compile(0).expectedSync
      assertTrue(
        mapped.jvmType == JvmType.Boolean,
        mapped.read[Any](EndOfStream) == java.lang.Boolean.FALSE,
        mapped.asInstanceOf[Reader.SyncReader[Boolean]].readBoolean(-9) == 1,
        mapped.asInstanceOf[Reader.SyncReader[Boolean]].readBoolean(-9) == -9,
        filtered.jvmType == JvmType.Boolean,
        filtered.read[Any](EndOfStream) == java.lang.Boolean.TRUE,
        (filtered.read[Any](EndOfStream).asInstanceOf[AnyRef] eq EndOfStream),
        collected.jvmType == JvmType.Boolean,
        collected.read[Any](EndOfStream) == java.lang.Boolean.FALSE,
        flatMapped.jvmType == JvmType.Boolean,
        flatMapped.read[Any](EndOfStream).block == java.lang.Boolean.TRUE,
        flatMapped.read[Any](EndOfStream).block == java.lang.Boolean.FALSE,
        intToBoolean.jvmType == JvmType.Boolean,
        intToBoolean.asInstanceOf[Reader.SyncReader[Boolean]].readBoolean(-7) == 0,
        intToBoolean.asInstanceOf[Reader.SyncReader[Boolean]].readBoolean(-7) == 1,
        intToBoolean.asInstanceOf[Reader.SyncReader[Boolean]].readBoolean(-7) == -7
      )
    },
    test("shallow Boolean delegated combinators preserve logical values") {
      val tappedValues = scala.collection.mutable.ArrayBuffer.empty[Boolean]
      val tapped       = Stream.fromChunk(Chunk(true, false)).tapEach(tappedValues += _).compile(0).expectedSync
      val distinct     = Stream.fromChunk(Chunk(true, true, false, false)).distinctBy(identity).compile(0).expectedSync
      val accumulated  = Stream
        .fromChunk(Chunk(true, false))
        .mapAccum(false) { (state, value) =>
          val next = state || value
          (next, next)
        }
        .compile(0)
        .expectedSync
      val scanned = Stream.fromChunk(Chunk(true, false)).scan(false)(_ || _).compile(0).expectedSync
      assertTrue(
        tapped.readUpToN[Boolean](4) == Chunk(true, false),
        tappedValues.toList == List(true, false),
        distinct.jvmType == JvmType.Boolean,
        distinct.readUpToN[Boolean](4) == Chunk(true, false),
        accumulated.jvmType == JvmType.Boolean,
        accumulated.readUpToN[Boolean](4) == Chunk(true, true),
        scanned.jvmType == JvmType.Boolean,
        scanned.readUpToN[Boolean](4) == Chunk(false, true, true)
      )
    },
    test("all shallow source lanes preserve Boolean map metadata and specialized reads") {
      val fromLong   = Stream.fromChunk(Chunk(1L)).map(_ == 1L).compile(0).expectedSync
      val fromFloat  = Stream.fromChunk(Chunk(1f)).map(_ == 1f).compile(0).expectedSync
      val fromDouble = Stream.fromChunk(Chunk(1d)).map(_ == 1d).compile(0).expectedSync
      val fromRef    = Stream.fromIterable(List("x")).map(_ == "x").compile(0).expectedSync
      val readers    = List(fromLong, fromFloat, fromDouble, fromRef)
      assertTrue(
        readers.forall(_.jvmType == JvmType.Boolean),
        readers.forall(_.asInstanceOf[Reader.SyncReader[Boolean]].readBoolean(-5) == 1),
        readers.forall(_.asInstanceOf[Reader.SyncReader[Boolean]].readBoolean(-5) == -5)
      )
    },
    test("Boolean readByte and shallow mapped skip use normalized I-lane values") {
      val direct = Stream.fromChunk(Chunk(true, false)).compile(0).expectedSync
      val deep   = SyncInterpreter.fromStream(Stream.fromChunk(Chunk(true, false)))
      val mapped = Stream.fromChunk(Chunk(true, false, true)).map(identity).compile(0).expectedSync
      mapped.skip(1)
      assertTrue(
        direct.readByte() == 1,
        direct.readByte() == 0,
        direct.readByte() == -1,
        deep.readByte() == 1,
        deep.readByte() == 0,
        deep.readByte() == -1,
        mapped.read[Any](EndOfStream) == java.lang.Boolean.FALSE
      )
    },
    test("deep widened concat preserves Boolean head values and reference tail metadata") {
      val head: Stream[Nothing, Any] = Stream.fromChunk(Chunk(true))
      val tail: Stream[Nothing, Any] = Stream.fromIterable(List("tail"))
      val concatenated               = SyncInterpreter.fromStream(head ++ tail)
      assertTrue(
        concatenated.jvmType == JvmType.AnyRef,
        drainGeneric(concatenated) == List(true, "tail")
      )
    }
  )

  // =========================================================================
  //  8. PushOp: flatMap with single-element inner
  // =========================================================================

  val pushOpSingleElementSuite = suite("PushOp - single-element inner")(
    test("outer 0..4, each produces single element (i * 10)") {
      val source                = Reader.fromRange(0 until 5)
      val pushFn: Int => AnyRef = (i: Int) => {
        Stream.fromChunk(Chunk(i * 10)).asInstanceOf[AnyRef]
      }
      val p = SyncInterpreter(source)
      p.addPush[Int](LANE_I)(pushFn)
      val result = drainInts(p)
      assertTrue(result == List(0, 10, 20, 30, 40))
    }
  )

  // =========================================================================
  //  9. PushOp: flatMap with multi-element inner
  // =========================================================================

  val pushOpMultiElementSuite = suite("PushOp - multi-element inner")(
    test("outer 1..3, each produces i elements: range(0, i)") {
      val source                = Reader.fromChunk(Chunk(1, 2, 3))
      val pushFn: Int => AnyRef = (i: Int) => {
        Stream.fromRange(0 until i).asInstanceOf[AnyRef]
      }
      val p = SyncInterpreter(source)
      p.addPush[Int](LANE_I)(pushFn)
      val result = drainInts(p)
      // 1 -> [0], 2 -> [0,1], 3 -> [0,1,2]
      assertTrue(result == List(0, 0, 1, 0, 1, 2))
    },
    test("outer 0..2, each produces 3 elements") {
      val source                = Reader.fromRange(0 until 3)
      val pushFn: Int => AnyRef = (i: Int) => {
        Stream.fromChunk(Chunk(i * 100, i * 100 + 1, i * 100 + 2)).asInstanceOf[AnyRef]
      }
      val p = SyncInterpreter(source)
      p.addPush[Int](LANE_I)(pushFn)
      val result = drainInts(p)
      assertTrue(result == List(0, 1, 2, 100, 101, 102, 200, 201, 202))
    }
  )

  // =========================================================================
  //  10. PushOp: nested flatMap
  // =========================================================================

  val pushOpNestedSuite = suite("PushOp - nested flatMap")(
    test("outer 0..1, each flatMaps to 0..1, each flatMaps to single element") {
      val source = Reader.fromRange(0 until 2)

      val outerPushFn: Int => AnyRef = (i: Int) => {
        Stream.fromChunk(Chunk(i * 10, i * 10 + 1)).flatMap((j: Int) => Stream.fromChunk(Chunk(j))).asInstanceOf[AnyRef]
      }

      val p = SyncInterpreter(source)
      p.addPush[Int](LANE_I)(outerPushFn)
      val result = drainInts(p)
      // outer 0 -> mid [0, 1] -> inner [0], [1] -> emit 0, 1
      // outer 1 -> mid [10, 11] -> inner [10], [11] -> emit 10, 11
      assertTrue(result == List(0, 1, 10, 11))
    }
  )

  // =========================================================================
  //  11. PushOp: flatMap with post-flatMap map (tail ops via reassociation)
  // =========================================================================

  val pushOpTailOpsSuite = suite("PushOp - tail ops (post-flatMap map)")(
    test("flatMap then map via reassociation") {
      val source                = Reader.fromRange(0 until 3)
      val pushFn: Int => AnyRef = (i: Int) => {
        Stream.fromChunk(Chunk(i * 10)).asInstanceOf[AnyRef]
      }
      val mapFn: Int => Int = _ + 1

      val p = SyncInterpreter(source)
      p.addPush[Int](LANE_I)(pushFn)
      p.addMap[Int, Int](LANE_I, OUT_I)(mapFn)
      val result = drainInts(p)
      // 0 -> flatMap -> [0] -> +1 -> [1]
      // 1 -> flatMap -> [10] -> +1 -> [11]
      // 2 -> flatMap -> [20] -> +1 -> [21]
      assertTrue(result == List(1, 11, 21))
    },
    test("flatMap then map via tail ops") {
      val source            = Reader.fromRange(0 until 3)
      val mapFn: Int => Int = _ + 1

      val pushFn: Int => AnyRef = (i: Int) => {
        Stream.fromChunk(Chunk(i * 10)).asInstanceOf[AnyRef]
      }

      val p = SyncInterpreter(source)
      p.addPush[Int](LANE_I)(pushFn)
      p.addMap[Int, Int](LANE_I, OUT_I)(mapFn)
      val result = drainInts(p)
      assertTrue(result == List(1, 11, 21))
    },
    test("flatMap then filter: only keep inner values > 5") {
      val source               = Reader.fromRange(0 until 5)
      val pred: Int => Boolean = _ > 5

      val pushFn: Int => AnyRef = (i: Int) => {
        Stream.fromChunk(Chunk(i * 10)).asInstanceOf[AnyRef]
      }

      val p = SyncInterpreter(source)
      p.addPush[Int](LANE_I)(pushFn)
      p.addFilter[Int](LANE_I)(pred)
      val result = drainInts(p)
      // 0*10=0 (filtered), 1*10=10 (pass), 2*10=20 (pass), 3*10=30 (pass), 4*10=40 (pass)
      assertTrue(result == List(10, 20, 30, 40))
    }
  )

  // =========================================================================
  //  12. Empty inner stream
  // =========================================================================

  val emptyInnerStreamSuite = suite("Empty inner stream")(
    test("some inner streams are empty, outer continues") {
      val source                = Reader.fromChunk(Chunk(0, 3, 0, 2))
      val pushFn: Int => AnyRef = (i: Int) => {
        Stream.fromRange(0 until i).asInstanceOf[AnyRef]
      }
      val p = SyncInterpreter(source)
      p.addPush[Int](LANE_I)(pushFn)
      val result = drainInts(p)
      // 0 -> empty, 3 -> [0,1,2], 0 -> empty, 2 -> [0,1]
      assertTrue(result == List(0, 1, 2, 0, 1))
    },
    test("all inner streams are empty") {
      val source                = Reader.fromChunk(Chunk(0, 0, 0))
      val pushFn: Int => AnyRef = (i: Int) => {
        Stream.fromRange(0 until i).asInstanceOf[AnyRef]
      }
      val p = SyncInterpreter(source)
      p.addPush[Int](LANE_I)(pushFn)
      val result = drainInts(p)
      assertTrue(result == List())
    }
  )

  // =========================================================================
  //  13. Source exhaustion
  // =========================================================================

  val sourceExhaustionSuite = suite("Source exhaustion")(
    test("pipeline with no ops returns elements directly") {
      val source = Reader.fromRange(0 until 5)
      val p      = SyncInterpreter(source)
      val result = drainInts(p)
      assertTrue(result == List(0, 1, 2, 3, 4))
    },
    test("empty source returns sentinel immediately") {
      val source   = Reader.fromRange(0 until 0)
      val p        = SyncInterpreter(source)
      val sentinel = Long.MinValue
      val v        = readIntValue(p, sentinel)
      assertTrue(v == sentinel)
    },
    test("sentinel returned when all elements consumed") {
      val source   = Reader.fromChunk(Chunk(42))
      val p        = SyncInterpreter(source)
      val sentinel = Long.MinValue
      val first    = readIntValue(p, sentinel)
      val second   = readIntValue(p, sentinel)
      assertTrue(first == 42L && second == sentinel)
    }
  )

  // =========================================================================
  //  14. addOp reassociation
  // =========================================================================

  val addOpReassociationSuite = suite("addOp reassociation")(
    test("adding MAP after non-PUSH appends to stage ops") {
      val source          = Reader.fromRange(0 until 5)
      val fn1: Int => Int = _ + 1
      val fn2: Int => Int = _ * 2

      val p = SyncInterpreter(source)
      p.addMap[Int, Int](LANE_I, OUT_I)(fn1)
      p.addMap[Int, Int](LANE_I, OUT_I)(fn2)

      val result   = drainInts(p)
      val expected = (0 until 5).map(i => (i + 1) * 2).toList
      assertTrue(result == expected)
    },
    test("adding MAP after PUSH reassociates into tail ops (behavioral)") {
      val source                = Reader.fromRange(0 until 1)
      val pushFn: Int => AnyRef = (i: Int) => {
        Stream.fromChunk(Chunk(i)).asInstanceOf[AnyRef]
      }
      val mapFn: Int => Int = _ + 1

      val p = SyncInterpreter(source)
      p.addPush[Int](LANE_I)(pushFn)
      p.addMap[Int, Int](LANE_I, OUT_I)(mapFn)

      val result = drainInts(p)
      // 0 -> flatMap -> [0] -> +1 -> [1]
      assertTrue(result == List(1))
    },
    test("nested reassociation: MAP after PUSH after PUSH (behavioral)") {
      val source = Reader.fromRange(0 until 1)

      val innerPushFn: Int => AnyRef = (i: Int) => {
        Stream.fromChunk(Chunk(i)).asInstanceOf[AnyRef]
      }

      val outerPushFn: Int => AnyRef = (i: Int) => {
        Stream.fromChunk(Chunk(i)).asInstanceOf[AnyRef]
      }

      val mapFn: Int => Int = _ + 1

      val p = SyncInterpreter(source)
      p.addPush[Int](LANE_I)(outerPushFn)
      p.addPush[Int](LANE_I)(innerPushFn)
      p.addMap[Int, Int](LANE_I, OUT_I)(mapFn)

      val result = drainInts(p)
      // 0 -> outerPush -> [0] -> innerPush -> [0] -> +1 -> [1]
      assertTrue(result == List(1))
    }
  )

  // =========================================================================
  //  15. Complex chain tests
  // =========================================================================

  val complexChainSuite = suite("Complex chains")(
    test("map(f).flatMap(g).map(h) - pre-flatMap map + post-flatMap map in tailOps") {
      // source: 0..4
      // map: * 2
      // flatMap: i -> Chunk(i, i+1)
      // map: + 100
      val source                = Reader.fromRange(0 until 5)
      val mapF: Int => Int      = _ * 2
      val pushFn: Int => AnyRef = (i: Int) => {
        Stream.fromChunk(Chunk(i, i + 1)).asInstanceOf[AnyRef]
      }
      val mapH: Int => Int = _ + 100

      val p = SyncInterpreter(source)
      p.addMap[Int, Int](LANE_I, OUT_I)(mapF)
      p.addPush[Int](LANE_I)(pushFn)
      p.addMap[Int, Int](LANE_I, OUT_I)(mapH)

      val result = drainInts(p)
      // 0*2=0 -> [0,1] -> [100,101]
      // 1*2=2 -> [2,3] -> [102,103]
      // 2*2=4 -> [4,5] -> [104,105]
      // 3*2=6 -> [6,7] -> [106,107]
      // 4*2=8 -> [8,9] -> [108,109]
      val expected = (0 until 5).flatMap { x =>
        val mapped = x * 2
        List(mapped + 100, mapped + 1 + 100)
      }.toList
      assertTrue(result == expected)
    },
    test("map(f).flatMap(g).filter(p) - filter in tailOps") {
      // source: 0..4
      // map: * 3
      // flatMap: i -> Chunk(i, i+1, i+2)
      // filter: _ % 2 == 0
      val source                = Reader.fromRange(0 until 3)
      val mapF: Int => Int      = _ * 3
      val pushFn: Int => AnyRef = (i: Int) => {
        Stream.fromChunk(Chunk(i, i + 1, i + 2)).asInstanceOf[AnyRef]
      }
      val pred: Int => Boolean = _ % 2 == 0

      val p = SyncInterpreter(source)
      p.addMap[Int, Int](LANE_I, OUT_I)(mapF)
      p.addPush[Int](LANE_I)(pushFn)
      p.addFilter[Int](LANE_I)(pred)

      val result = drainInts(p)
      // 0*3=0 -> [0,1,2] -> filter even -> [0,2]
      // 1*3=3 -> [3,4,5] -> filter even -> [4]
      // 2*3=6 -> [6,7,8] -> filter even -> [6,8]
      assertTrue(result == List(0, 2, 4, 6, 8))
    },
    test("map(f).filter(p).flatMap(g).map(h).filter(q) - everything mixed") {
      // source: 0..9
      // map: +1
      // filter: even
      // flatMap: i -> Chunk(i, i*10)
      // map: +1000
      // filter: _ < 1100
      val source                = Reader.fromRange(0 until 10)
      val mapF: Int => Int      = _ + 1
      val predP: Int => Boolean = _ % 2 == 0
      val pushFn: Int => AnyRef = (i: Int) => {
        Stream.fromChunk(Chunk(i, i * 10)).asInstanceOf[AnyRef]
      }
      val mapH: Int => Int      = _ + 1000
      val predQ: Int => Boolean = _ < 1100

      val p = SyncInterpreter(source)
      p.addMap[Int, Int](LANE_I, OUT_I)(mapF)
      p.addFilter[Int](LANE_I)(predP)
      p.addPush[Int](LANE_I)(pushFn)
      p.addMap[Int, Int](LANE_I, OUT_I)(mapH)
      p.addFilter[Int](LANE_I)(predQ)

      val result = drainInts(p)
      // After map(+1): 1,2,3,4,5,6,7,8,9,10
      // After filter(even): 2,4,6,8,10
      // After flatMap: 2,20, 4,40, 6,60, 8,80, 10,100
      // After map(+1000): 1002,1020, 1004,1040, 1006,1060, 1008,1080, 1010,1100
      // After filter(<1100): 1002,1020, 1004,1040, 1006,1060, 1008,1080, 1010
      val expected = List(1002, 1020, 1004, 1040, 1006, 1060, 1008, 1080, 1010)
      assertTrue(result == expected)
    },
    test("flatMap(g).flatMap(h) - two consecutive flatMaps") {
      // source: 0..2
      // flatMap g: i -> Chunk(i, i+10)
      // flatMap h: j -> Chunk(j*100, j*100+1)
      val source               = Reader.fromRange(0 until 3)
      val pushG: Int => AnyRef = (i: Int) => {
        Stream.fromChunk(Chunk(i, i + 10)).asInstanceOf[AnyRef]
      }
      val pushH: Int => AnyRef = (j: Int) => {
        Stream.fromChunk(Chunk(j * 100, j * 100 + 1)).asInstanceOf[AnyRef]
      }

      val p = SyncInterpreter(source)
      p.addPush[Int](LANE_I)(pushG)
      p.addPush[Int](LANE_I)(pushH)

      val result = drainInts(p)
      // 0 -> g -> [0,10] -> h -> [0,1], [1000,1001]
      // 1 -> g -> [1,11] -> h -> [100,101], [1100,1101]
      // 2 -> g -> [2,12] -> h -> [200,201], [1200,1201]
      val expected = List(0, 1, 1000, 1001, 100, 101, 1100, 1101, 200, 201, 1200, 1201)
      assertTrue(result == expected)
    },
    test("flatMap(g).map(f).flatMap(h) - map between two flatMaps (reassociation stress)") {
      // source: 0..1
      // flatMap g: i -> Chunk(i, i+1)
      // map f: *10
      // flatMap h: j -> Chunk(j, j+1)
      val source               = Reader.fromRange(0 until 2)
      val pushG: Int => AnyRef = (i: Int) => {
        Stream.fromChunk(Chunk(i, i + 1)).asInstanceOf[AnyRef]
      }
      val mapF: Int => Int     = _ * 10
      val pushH: Int => AnyRef = (j: Int) => {
        Stream.fromChunk(Chunk(j, j + 1)).asInstanceOf[AnyRef]
      }

      val p = SyncInterpreter(source)
      p.addPush[Int](LANE_I)(pushG)
      p.addMap[Int, Int](LANE_I, OUT_I)(mapF)
      p.addPush[Int](LANE_I)(pushH)

      val result = drainInts(p)
      // 0 -> g -> [0,1] -> map*10 -> [0,10]
      //   0 -> h -> [0,1]
      //   10 -> h -> [10,11]
      // 1 -> g -> [1,2] -> map*10 -> [10,20]
      //   10 -> h -> [10,11]
      //   20 -> h -> [20,21]
      val expected = List(0, 1, 10, 11, 10, 11, 20, 21)
      assertTrue(result == expected)
    },
    test("flatMap(g) where g returns empty for some elements") {
      val source                = Reader.fromRange(0 until 6)
      val pushFn: Int => AnyRef = (i: Int) => {
        // Even elements produce empty, odd produce [i]
        if (i % 2 == 0) {
          Stream.fromRange(0 until 0).asInstanceOf[AnyRef]
        } else {
          Stream.fromChunk(Chunk(i)).asInstanceOf[AnyRef]
        }
      }
      val p = SyncInterpreter(source)
      p.addPush[Int](LANE_I)(pushFn)
      val result = drainInts(p)
      assertTrue(result == List(1, 3, 5))
    },
    test("flatMap(g) with inner maps on the inner pipeline") {
      // source: 0..2
      // flatMap: each element produces an inner pipeline that has its own map +100
      val source                = Reader.fromRange(0 until 3)
      val pushFn: Int => AnyRef = (i: Int) => {
        Stream.fromChunk(Chunk(i, i + 1)).map((_: Int) + 100).asInstanceOf[AnyRef]
      }
      val p = SyncInterpreter(source)
      p.addPush[Int](LANE_I)(pushFn)
      val result = drainInts(p)
      // 0 -> inner [0,1] with +100 -> [100,101]
      // 1 -> inner [1,2] with +100 -> [101,102]
      // 2 -> inner [2,3] with +100 -> [102,103]
      assertTrue(result == List(100, 101, 101, 102, 102, 103))
    },
    test("cross-type flatMap: map(_.toLong).flatMap(l -> intStream)") {
      // source: Int 0..2
      // map Int->Long
      // flatMap Long -> inner Int pipeline
      val source             = Reader.fromRange(0 until 3)
      val mapIL: Int => Long = _.toLong + 100L

      val pushFn: Long => AnyRef = (l: Long) => {
        Stream.fromChunk(Chunk(l.toInt, l.toInt + 1)).asInstanceOf[AnyRef]
      }

      val p = SyncInterpreter(source)
      p.addMap[Int, Long](LANE_I, OUT_L)(mapIL)
      p.addPush[Long](LANE_L)(pushFn)

      val result = drainInts(p)
      // 0 -> 100L -> inner [100,101]
      // 1 -> 101L -> inner [101,102]
      // 2 -> 102L -> inner [102,103]
      assertTrue(result == List(100, 101, 101, 102, 102, 103))
    },
    test("filter rejecting ALL elements - empty result") {
      val source               = Reader.fromRange(0 until 10)
      val pred: Int => Boolean = _ > 100 // nothing passes
      val p                    = SyncInterpreter(source)
      p.addFilter[Int](LANE_I)(pred)
      val result = drainInts(p)
      assertTrue(result == List())
    },
    test("filter rejecting MOST elements") {
      val source               = Reader.fromRange(0 until 1000)
      val pred: Int => Boolean = _ == 999
      val p                    = SyncInterpreter(source)
      p.addFilter[Int](LANE_I)(pred)
      val result = drainInts(p)
      assertTrue(result == List(999))
    },
    test("100 chained maps followed by flatMap") {
      val source            = Reader.fromRange(0 until 3)
      val mapFn: Int => Int = _ + 1

      val p = SyncInterpreter(source)
      (0 until 100).foreach(_ => p.addMap[Int, Int](LANE_I, OUT_I)(mapFn))

      val pushFn: Int => AnyRef = (i: Int) => {
        Stream.fromChunk(Chunk(i, i + 1)).asInstanceOf[AnyRef]
      }
      p.addPush[Int](LANE_I)(pushFn)

      val result = drainInts(p)
      // 0+100=100 -> [100,101]
      // 1+100=101 -> [101,102]
      // 2+100=102 -> [102,103]
      assertTrue(result == List(100, 101, 101, 102, 102, 103))
    },
    test("flatMap(g).map(h).filter(p).map(k) - multiple post-flatMap ops") {
      // source: 0..3
      // flatMap: i -> Chunk(i*10, i*10+1)
      // map: +1
      // filter: even
      // map: *100
      val source                = Reader.fromRange(0 until 4)
      val pushFn: Int => AnyRef = (i: Int) => {
        Stream.fromChunk(Chunk(i * 10, i * 10 + 1)).asInstanceOf[AnyRef]
      }
      val mapH: Int => Int      = _ + 1
      val predP: Int => Boolean = _ % 2 == 0
      val mapK: Int => Int      = _ * 100

      val p = SyncInterpreter(source)
      p.addPush[Int](LANE_I)(pushFn)
      p.addMap[Int, Int](LANE_I, OUT_I)(mapH)
      p.addFilter[Int](LANE_I)(predP)
      p.addMap[Int, Int](LANE_I, OUT_I)(mapK)

      val result = drainInts(p)
      // 0 -> [0,1] -> +1 -> [1,2] -> filter even -> [2] -> *100 -> [200]
      // 1 -> [10,11] -> +1 -> [11,12] -> filter even -> [12] -> *100 -> [1200]
      // 2 -> [20,21] -> +1 -> [21,22] -> filter even -> [22] -> *100 -> [2200]
      // 3 -> [30,31] -> +1 -> [31,32] -> filter even -> [32] -> *100 -> [3200]
      assertTrue(result == List(200, 1200, 2200, 3200))
    }
  )

  // =========================================================================
  //  16. outputType tests
  // =========================================================================

  val outputTypeSuite = suite("outputType")(
    test("Int source with no ops has PInt outputType") {
      val source = Reader.fromRange(0 until 5)
      val p      = SyncInterpreter(source)
      assertTrue(p.outputType == JvmType.Int)
    },
    test("Int source with MAP_IL has PLong outputType") {
      val source          = Reader.fromRange(0 until 5)
      val fn: Int => Long = _.toLong
      val p               = SyncInterpreter(source)
      p.addMap[Int, Long](LANE_I, OUT_L)(fn)
      assertTrue(p.outputType == JvmType.Long)
    },
    test("Long source with MAP_LD has PDouble outputType") {
      val source             = Reader.fromChunk(Chunk(1L, 2L))
      val fn: Long => Double = _.toDouble
      val p                  = SyncInterpreter(source)
      p.addMap[Long, Double](LANE_L, OUT_D)(fn)
      assertTrue(p.outputType == JvmType.Double)
    }
  )

  // =========================================================================
  //  17. Cross-type pipeline tests — exercises type-crossing scenarios
  //      through SyncInterpreter, especially PushOp with post-flatMap ops that
  //      change types.
  // =========================================================================

  val crossTypeSyncInterpreterSuite = suite("Cross-type pipeline")(
    test("1. map Int→Long, drain via readLong") {
      val source          = Reader.fromRange(0 until 5)
      val fn: Int => Long = (i: Int) => i.toLong * 100L
      val p               = SyncInterpreter(source)
      p.addMap[Int, Long](LANE_I, OUT_L)(fn)
      val result = drainLongs(p)
      assertTrue(result == List(0L, 100L, 200L, 300L, 400L))
    },
    test("2. map Int→Double, drain via readDouble") {
      val source            = Reader.fromRange(0 until 3)
      val fn: Int => Double = (i: Int) => i.toDouble + 0.5
      val p                 = SyncInterpreter(source)
      p.addMap[Int, Double](LANE_I, OUT_D)(fn)
      val result = drainDoubles(p)
      assertTrue(result == List(0.5, 1.5, 2.5))
    },
    test("3. map Int→Float, drain via readFloat") {
      val source           = Reader.fromRange(0 until 3)
      val fn: Int => Float = (i: Int) => i.toFloat * 1.5f
      val p                = SyncInterpreter(source)
      p.addMap[Int, Float](LANE_I, OUT_F)(fn)
      val result = drainFloats(p)
      assertTrue(result == List(0.0f, 1.5f, 3.0f))
    },
    test("4. flatMap(Int→Int) then map(Int→Long) via addOp — reassociation outputLane") {
      // This tests the bug fix: MAP_IL goes into PushOp.tailOps via reassociation.
      // The OUTER stage's outputLane must remain LANE_I, not LANE_L.
      // The inner stage (created at runtime) will have the MAP_IL appended.
      val source                = Reader.fromRange(0 until 3)
      val pushFn: Int => AnyRef = (i: Int) => {
        Stream.fromChunk(Chunk(i * 10)).asInstanceOf[AnyRef]
      }
      val mapIL: Int => Long = (i: Int) => i.toLong

      val p = SyncInterpreter(source)
      p.addPush[Int](LANE_I)(pushFn)
      p.addMap[Int, Long](LANE_I, OUT_L)(mapIL)

      val result = drainLongs(p)
      // 0 -> flatMap -> [0] -> toLong -> [0L]
      // 1 -> flatMap -> [10] -> toLong -> [10L]
      // 2 -> flatMap -> [20] -> toLong -> [20L]
      assertTrue(result == List(0L, 10L, 20L))
    },
    test("5. flatMap(Int→Int) then map(Int→Double) via addOp, drain via readDouble") {
      val source                = Reader.fromRange(0 until 3)
      val pushFn: Int => AnyRef = (i: Int) => {
        Stream.fromChunk(Chunk(i)).asInstanceOf[AnyRef]
      }
      val mapID: Int => Double = (i: Int) => i.toDouble + 0.5

      val p = SyncInterpreter(source)
      p.addPush[Int](LANE_I)(pushFn)
      p.addMap[Int, Double](LANE_I, OUT_D)(mapID)

      val result = drainDoubles(p)
      assertTrue(result == List(0.5, 1.5, 2.5))
    },
    test("6. flatMap(Int→Int) then map(Int→AnyRef) via addOp, drain via drainGeneric") {
      val source                = Reader.fromRange(0 until 3)
      val pushFn: Int => AnyRef = (i: Int) => {
        Stream.fromChunk(Chunk(i)).asInstanceOf[AnyRef]
      }
      val mapIR: Int => AnyRef = (i: Int) => s"v$i"

      val p = SyncInterpreter(source)
      p.addPush[Int](LANE_I)(pushFn)
      p.addMap[Int, AnyRef](LANE_I, OUT_R)(mapIR)

      val result = drainGeneric(p)
      assertTrue(result == List("v0", "v1", "v2"))
    },
    test("7. map(Int→Long) then flatMap(Long→Long), drain via readLong") {
      // Cross-type map BEFORE a flatMap
      val source             = Reader.fromRange(0 until 3)
      val mapIL: Int => Long = (i: Int) => i.toLong * 10L

      val pushFn: Long => AnyRef = (l: Long) => {
        Stream.fromChunk(Chunk(l, l + 1L)).asInstanceOf[AnyRef]
      }
      val p = SyncInterpreter(source)
      p.addMap[Int, Long](LANE_I, OUT_L)(mapIL)
      p.addPush[Long](LANE_L)(pushFn)

      val result = drainLongs(p)
      // 0 -> 0L -> flatMap -> [0L, 1L]
      // 1 -> 10L -> flatMap -> [10L, 11L]
      // 2 -> 20L -> flatMap -> [20L, 21L]
      assertTrue(result == List(0L, 1L, 10L, 11L, 20L, 21L))
    },
    test("8. two chained maps Int→Long→Double, drain via readDouble") {
      val source                = Reader.fromRange(0 until 3)
      val mapIL: Int => Long    = (i: Int) => i.toLong
      val mapLD: Long => Double = (l: Long) => l.toDouble

      val p = SyncInterpreter(source)
      p.addMap[Int, Long](LANE_I, OUT_L)(mapIL)
      p.addMap[Long, Double](LANE_L, OUT_D)(mapLD)

      val result = drainDoubles(p)
      assertTrue(result == List(0.0, 1.0, 2.0))
    },
    test("9. outputType after cross-type map Int→Long") {
      val source             = Reader.fromRange(0 until 1)
      val mapIL: Int => Long = (i: Int) => i.toLong

      val p = SyncInterpreter(source)
      p.addMap[Int, Long](LANE_I, OUT_L)(mapIL)

      assertTrue(p.outputType == JvmType.Long)
    },
    test("10. outputType NOT changed by reassociated op into PushOp tailOps") {
      // After addOp(PushOp), the outer stage's outputLane is LANE_I (PushOp tag=40 >= 35, not updated).
      // Then addOp(MAP_IL) reassociates into tailOps and must NOT update outputLane.
      // So pipeline.outputType should remain PInt.
      val source                = Reader.fromRange(0 until 1)
      val pushFn: Int => AnyRef = (i: Int) => {
        Stream.fromChunk(Chunk(i)).asInstanceOf[AnyRef]
      }
      val mapIL: Int => Long = (i: Int) => i.toLong

      val p = SyncInterpreter(source)
      p.addPush[Int](LANE_I)(pushFn)
      p.addMap[Int, Long](LANE_I, OUT_L)(mapIL)

      // In the flat pipeline, outputLane reflects the final output type (after outgoing ops).
      // MAP_IL goes to outgoing, so outputType becomes Long.
      assertTrue(p.outputType == JvmType.Long)
    }
  )

  // =========================================================================
  //  18. Depth-based compilation: shallow Reader decorations vs deep SyncInterpreter
  // =========================================================================

  val depthBasedCompilationSuite = suite("Depth-based compilation")(
    test("shallow Int-to-Int map compiles to its specialized reader, not SyncInterpreter") {
      val reader = Stream.range(0, 10).map(_ + 1).compile(0).expectedSync
      assertTrue(reader.isInstanceOf[Reader.MappedIntInt]) &&
      assertTrue(!reader.isInstanceOf[SyncInterpreter])
    },
    test("shallow exact wide primitive maps compile to specialized readers") {
      val longReader   = Stream.fromArray(Array(1L)).filter(_ => true).map(_ + 1L).compile(0).expectedSync
      val floatReader  = Stream.fromArray(Array(1.0f)).filter(_ => true).map(_.toLong).compile(0).expectedSync
      val doubleReader = Stream.fromArray(Array(1.0d)).filter(_ => true).map(_.toLong).compile(0).expectedSync
      assertTrue(
        longReader.isInstanceOf[Reader.MappedLong],
        floatReader.isInstanceOf[Reader.MappedFloat],
        doubleReader.isInstanceOf[Reader.MappedDouble]
      )
    } @@ TestAspect.jvmOnly,
    test("shallow erased primitive maps retain the boxing-free interpreter") {
      val longSource: Stream[Nothing, AnyVal]   = Stream.fromArray(Array(1L)).filter(_ => true)
      val floatSource: Stream[Nothing, AnyVal]  = Stream.fromArray(Array(1.0f)).filter(_ => true)
      val doubleSource: Stream[Nothing, AnyVal] = Stream.fromArray(Array(1.0d)).filter(_ => true)
      val longReader                            = longSource.map((value: AnyVal) => value.asInstanceOf[Long] + 1L).compile(0).expectedSync
      val floatReader                           = floatSource.map((value: AnyVal) => value.asInstanceOf[Float].toLong).compile(0).expectedSync
      val doubleReader                          = doubleSource.map((value: AnyVal) => value.asInstanceOf[Double].toLong).compile(0).expectedSync
      assertTrue(
        longReader.isInstanceOf[SyncInterpreter],
        floatReader.isInstanceOf[SyncInterpreter],
        doubleReader.isInstanceOf[SyncInterpreter]
      )
    },
    test("shallow Int filter compiles to its specialized reader, not SyncInterpreter") {
      val reader = Stream.range(0, 10).filter(_ > 5).compile(0).expectedSync
      assertTrue(reader.isInstanceOf[Reader.FilteredIntInt]) &&
      assertTrue(!reader.isInstanceOf[SyncInterpreter])
    },
    test("shallow flatMap is a stable asynchronous runtime-transition boundary") {
      val reader = Stream.range(0, 3).flatMap(i => Stream.range(0, i)).compile(0).expectedAsync
      assertTrue(!reader.isInstanceOf[Reader.SyncReader[_]], reader.jvmType == JvmType.Int)
    },
    test("shallow flatMap close preserves the active inner close failure") {
      val closeFailure = new RuntimeException("inner-close")
      val inner        = new Reader.SyncReader[Int] {
        private var emitted                   = false
        override def jvmType: JvmType         = JvmType.Int
        def isClosed: Boolean                 = false
        def read[A1 >: Int](sentinel: A1): A1 =
          if (emitted) sentinel else { emitted = true; 1 }
        override def readInt(sentinel: Long)(implicit ev: Int <:< Int): Long =
          if (emitted) sentinel else { emitted = true; 1L }
        def close(): Unit = throw closeFailure
      }
      val reader = Stream.succeed(0).flatMap(_ => Stream.fromReader[Nothing, Int](inner)).compile(0).expectedAsync

      val value  = reader.readInt(Long.MinValue).block
      val caught = try { reader.close().block; null }
      catch { case t: Throwable => t }

      assertTrue(value == 1L, caught eq closeFailure)
    },
    test("shallow flatMap latches exhausted inner close failure") {
      val failure = new RuntimeException("transition-close")
      var closes  = 0; var inners = 0
      def hostile = new Reader.SyncReader[Int] {
        private var emitted                                                  = false
        override def jvmType: JvmType                                        = JvmType.Int
        def isClosed: Boolean                                                = false
        def read[A1 >: Int](sentinel: A1): A1                                = if (emitted) sentinel else { emitted = true; 1.asInstanceOf[A1] }
        override def readInt(sentinel: Long)(implicit ev: Int <:< Int): Long =
          if (emitted) sentinel else { emitted = true; 1L }
        def close(): Unit = { closes += 1; throw failure }
      }
      val reader =
        Stream
          .fromChunk(Chunk(1, 2))
          .flatMap { _ => inners += 1; Stream.fromReader[Nothing, Int](hostile) }
          .compile(0)
          .expectedAsync
      val value       = reader.readInt(Long.MinValue).block
      val first       = scala.util.Try(reader.readInt(Long.MinValue).block).failed.toOption.orNull
      val replay      = scala.util.Try(reader.readInt(Long.MinValue).block).failed.toOption.orNull
      val closeReplay = scala.util.Try(reader.close().block).failed.toOption.orNull

      assertTrue(value == 1L, first eq failure, replay eq failure, closeReplay eq failure, closes == 1, inners == 1)
    },
    test("forced interpreter latches exhausted inner close failure") {
      val failure = new RuntimeException("interpreter-transition-close")
      var closes  = 0; var inners = 0
      def hostile = new Reader.SyncReader[Int] {
        private var emitted                                                  = false
        override def jvmType: JvmType                                        = JvmType.Int
        def isClosed: Boolean                                                = false
        def read[A1 >: Int](sentinel: A1): A1                                = if (emitted) sentinel else { emitted = true; 1.asInstanceOf[A1] }
        override def readInt(sentinel: Long)(implicit ev: Int <:< Int): Long =
          if (emitted) sentinel else { emitted = true; 1L }
        def close(): Unit = { closes += 1; throw failure }
      }
      val stream      = Stream.fromChunk(Chunk(1, 2)).flatMap { _ => inners += 1; Stream.fromReader[Nothing, Int](hostile) }
      val reader      = SyncInterpreter.fromStream(stream).asInstanceOf[Reader.SyncReader[Int]]
      val value       = reader.readInt(Long.MinValue)
      val first       = scala.util.Try(reader.readInt(Long.MinValue)).failed.toOption.orNull
      val replay      = scala.util.Try(reader.readInt(Long.MinValue)).failed.toOption.orNull
      val resetReplay = scala.util.Try(reader.reset()).failed.toOption.orNull
      val closeReplay = scala.util.Try(reader.close()).failed.toOption.orNull

      assertTrue(
        value == 1L,
        first eq failure,
        replay eq failure,
        resetReplay == null,
        closeReplay == null,
        closes == 1,
        inners == 1
      )
    },
    test("shallow map on Ref stream compiles to MappedRef") {
      val reader = Stream.fromIterable(List("a", "b")).map(_.toUpperCase).compile(0).expectedSync
      assertTrue(reader.isInstanceOf[Reader.MappedRef])
    },
    test("101 chained maps compiles to SyncInterpreter") {
      var s: Stream[Nothing, Int] = Stream.range(0, 5)
      var i                       = 0
      while (i < 101) { s = s.map(_ + 1); i += 1 }
      val reader = s.compile(0).expectedSync
      assertTrue(reader.isInstanceOf[SyncInterpreter])
    },
    test("99 chained maps compile to one SyncInterpreter after the first thin wrapper") {
      var s: Stream[Nothing, Int] = Stream.range(0, 5)
      var i                       = 0
      while (i < 99) { s = s.map(_ + 1); i += 1 }
      val reader = s.compile(0).expectedSync
      assertTrue(reader.isInstanceOf[SyncInterpreter])
    },
    test("deep chain produces correct results via SyncInterpreter") {
      var s: Stream[Nothing, Int] = Stream.range(0, 5)
      var i                       = 0
      while (i < 101) { s = s.map(_ + 1); i += 1 }
      runAsync(s.runCollectAsync).map { result =>
        val expected = (0 until 5).map(_ + 101)
        assertTrue(result == Right(Chunk.fromIterable(expected)))

      }
    },
    test("shallow chain produces correct results via Reader decoration") {
      runAsync(Stream.range(0, 10).map(_ + 1).runCollectAsync).map { result =>
        assertTrue(result == Right(Chunk.fromIterable(1 to 10)))

      }
    },
    test("past cutoff: returns a single SyncInterpreter with correct results") {
      var s: Stream[Nothing, Int] = Stream.range(0, 5)
      var i                       = 0
      while (i < 105) { s = s.map(_ + 1); i += 1 }
      val reader = s.compile(0).expectedSync
      // Top-level is SyncInterpreter
      runAsync(s.runCollectAsync).map { result =>
        // Produces correct results (proves all 105 maps were fused into the pipeline)
        assertTrue(
          reader.isInstanceOf[SyncInterpreter],
          result == Right(Chunk.fromIterable((0 until 5).map(_ + 105)))
        )
      }
    },
    test("at cutoff boundary: map at depth 100 triggers SyncInterpreter, maps above add to it") {
      // 101 maps: depth 0..100. At depth 100, SyncInterpreter.fromStream builds the whole subtree.
      // Maps at depths 0..99 see SyncInterpreter from below, add their op, return the SAME instance.
      var s: Stream[Nothing, Int] = Stream.range(0, 3)
      var i                       = 0
      while (i < 101) { s = s.map(_ + 1); i += 1 }
      val reader = s.compile(0).expectedSync
      runAsync(s.runCollectAsync).map { result =>
        // Verify functional correctness — all 101 maps applied
        assertTrue(
          reader.isInstanceOf[SyncInterpreter],
          result == Right(Chunk.fromIterable((0 until 3).map(_ + 101)))
        )
      }
    },
    test("below cutoff: fusable Int operations upgrade the thin wrapper to SyncInterpreter") {
      var s: Stream[Nothing, Int] = Stream.range(0, 5)
      var i                       = 0
      while (i < 5) { s = s.map(_ + 1); i += 1 }
      val reader = s.compile(0).expectedSync
      assertTrue(reader.isInstanceOf[SyncInterpreter])
    },
    test("mixed ops below cutoff upgrade to SyncInterpreter") {
      val reader = Stream.range(0, 10).map(_ + 1).filter(_ % 2 == 0).map(_ * 3).compile(0).expectedSync
      assertTrue(reader.isInstanceOf[SyncInterpreter])
    }
  )

  // =========================================================================
  //  19. Stack safety: deeply nested flatMap
  // =========================================================================

  val stackSafetySuite = suite("Stack safety")(
    test("10,000 nested flatMaps compile and run without StackOverflow") {
      var s: Stream[Nothing, Int] = Stream.succeed(0)
      var i                       = 0
      while (i < 10000) {
        s = s.flatMap((_: Int) => Stream.succeed(1))
        i += 1
      }
      val blocking = s.runFoldLongBlocking(0L, _ + _)
      runAsync(s.runCollectAsync).map { result =>
        assertTrue(blocking == Right(1L), result == Right(Chunk(1)))
      }
    } @@ TestAspect.jvmOnly @@ TestAspect.timeout(zio.Duration.fromSeconds(90)),
    test("nested singleton flatMap fold falls back after a multi-element child") {
      val stream = Stream
        .succeed(0)
        .flatMap(_ => Stream.range(1, 3))
        .flatMap(value => Stream.succeed(value + 1))
        .flatMap(value => Stream.succeed(value * 2))
      assertTrue(stream.runFoldLongBlocking(0L, _ + _) == Right(10L))
    },
    test("singleton flatMap collapse is lane and terminal independent") {
      val stream = Stream
        .succeed(true)
        .flatMap(value => Stream.succeed(if (value) 2.toByte else 0.toByte))
        .flatMap(value => Stream.succeed((value + 1).toShort))
        .flatMap(value => Stream.succeed((value + 1).toChar))
        .flatMap(value => Stream.succeed(value.toInt + 1))
        .flatMap(value => Stream.succeed(value.toLong + 1L))
        .flatMap(value => Stream.succeed(value.toFloat + 0.5f))
        .flatMap(value => Stream.succeed(value.toDouble + 0.25d))
        .flatMap(value => Stream.succeed(value.toString))

      assertTrue(stream.runBlocking(Sink.collectAll[String]) == Right(Chunk("6.75")))
    },
    test("deep Long singleton flatMaps collapse for an Int accumulator") {
      var stream: Stream[Nothing, Long] = Stream.succeed(0L)
      var index                         = 0
      while (index < 10000) {
        stream = stream.flatMap(value => Stream.succeed(value + 1L))
        index += 1
      }
      val result =
        try stream.runFoldIntBlocking(0, (acc, value) => acc + value.toInt)
        catch { case cause: Throwable => throw new RuntimeException("deep Long collapse failed", cause) }
      assertTrue(result == Right(10000))
    } @@ TestAspect.jvmOnly,
    test("blocking singleton collapse remains lazy until reader demand") {
      val calls  = new java.util.concurrent.atomic.AtomicInteger(0)
      val stream = Stream.succeed(1).flatMap { value =>
        calls.incrementAndGet()
        Stream.succeed(value + 1)
      }
      val unopened = stream.compileBlocking(Stream.DefaultBufferSize)
      val before   = calls.get()
      unopened.close()
      val afterClose = calls.get()
      val reader     = stream.compileBlocking(Stream.DefaultBufferSize)
      val value      = reader.readIntPhysical(Long.MinValue)
      reader.close()

      assertTrue(before == 0, afterClose == 0, value == 2L, calls.get() == 1)
    },
    test("10,000 nested concats compile and run blocking without StackOverflow") {
      var s: Stream[Nothing, Int] = Stream.succeed(1)
      var i                       = 0
      while (i < 10000) {
        s = s ++ Stream.succeed(1)
        i += 1
      }
      assertTrue(s.runFoldLongBlocking(0L, _ + _) == Right(10001L))
    } @@ TestAspect.jvmOnly @@ TestAspect.timeout(zio.Duration.fromSeconds(90)),
    test("deep concat traversal is element and accumulator lane independent") {
      var longs: Stream[Nothing, Long]  = Stream.succeed(1L)
      var refs: Stream[Nothing, String] = Stream.succeed("a")
      var i                             = 0
      while (i < 10000) {
        longs = longs ++ Stream.succeed((i & 1).toLong + 1L)
        refs = refs ++ Stream.succeed(if ((i & 1) == 0) "b" else "cc")
        i += 1
      }
      val expectedLong  = 15001L
      val expectedChars = 15001
      val genericLong   = longs.runFoldGenericBlocking(
        Array(0, 0),
        (acc: Array[Int], value: Long) => {
          acc(0) += 1
          acc(1) += value.toInt
          acc
        }
      )
      assertTrue(
        longs.runFoldIntBlocking(0, (acc, value) => acc + value.toInt) == Right(expectedLong.toInt),
        longs.runFoldDoubleBlocking(0.0, _ + _) == Right(expectedLong.toDouble),
        genericLong.map(acc => (acc(0), acc(1))) == Right((10001, expectedLong.toInt)),
        refs.runFoldLongBlocking(0L, (acc, value) => acc + value.length) == Right(expectedChars.toLong),
        refs.runFoldGenericBlocking(
          (0, 0),
          (acc: (Int, Int), value: String) => (acc._1 + 1, acc._2 + value.length)
        ) == Right((10001, expectedChars))
      )
    } @@ TestAspect.jvmOnly @@ TestAspect.timeout(zio.Duration.fromSeconds(90))
  )

  // =========================================================================
  //  20. Construction: fromStream, unsealed, apply
  // =========================================================================

  val constructionSuite = suite("Construction")(
    test("SyncInterpreter.fromStream builds and seals a pipeline from a Stream") {
      val stream = Stream.fromChunk(Chunk(1, 2, 3)).map((_: Int) * 10)
      val p      = SyncInterpreter.fromStream(stream)
      val result = drainInts(p)
      assertTrue(result == List(10, 20, 30))
    },
    test("SyncInterpreter.fromStream with empty stream returns sentinel immediately") {
      val stream = Stream.fromChunk(Chunk.empty[Int])
      val p      = SyncInterpreter.fromStream(stream)
      assertTrue(readIntValue(p, Long.MinValue) == Long.MinValue)
    },
    test("SyncInterpreter.fromStream with filter + map") {
      val stream = Stream.fromRange(0 until 10).filter((_: Int) % 2 == 0).map((_: Int) + 100)
      val p      = SyncInterpreter.fromStream(stream)
      val result = drainInts(p)
      assertTrue(result == List(100, 102, 104, 106, 108))
    },
    test("SyncInterpreter.fromStream with flatMap") {
      val stream = Stream.fromChunk(Chunk(1, 2)).flatMap((i: Int) => Stream.fromChunk(Chunk(i, i * 10)))
      val p      = SyncInterpreter.fromStream(stream)
      val result = drainInts(p)
      assertTrue(result == List(1, 10, 2, 20))
    },
    test("SyncInterpreter.unsealed creates pipeline that can have ops added") {
      val source = Reader.fromRange(0 until 5)
      val p      = SyncInterpreter.unsealed(source)
      p.addMap[Int, Int](LANE_I, OUT_I)((_: Int) + 10)
      p.seal()
      val result = drainInts(p)
      assertTrue(result == List(10, 11, 12, 13, 14))
    },
    test("SyncInterpreter.unsealed without additional ops, then seal") {
      val source = Reader.fromRange(0 until 3)
      val p      = SyncInterpreter.unsealed(source)
      p.seal()
      val result = drainInts(p)
      assertTrue(result == List(0, 1, 2))
    },
    test("SyncInterpreter.apply creates a sealed pipeline from a Reader") {
      val source = Reader.fromRange(0 until 4)
      val p      = SyncInterpreter(source)
      // SyncInterpreter.apply calls seal() — further addMap still works (adds to sealed pipeline)
      p.addMap[Int, Int](LANE_I, OUT_I)((_: Int) * 2)
      val result = drainInts(p)
      assertTrue(result == List(0, 2, 4, 6))
    }
  )

  // =========================================================================
  //  21. Reset and re-read
  // =========================================================================

  val stickyFailureSuite = suite("Sticky committed failures")(
    test("generic source failure is replayed by identity through scalar, bulk, state, and controls") {
      val failure = new RuntimeException("read")
      var reads   = 0
      val source  = new Reader.SyncReader[String] {
        def isClosed: Boolean                    = false
        def close(): Unit                        = ()
        def read[A1 >: String](sentinel: A1): A1 = { reads += 1; throw failure }
        override def reset(): Unit               = ()
      }
      val p         = SyncInterpreter(source).asInstanceOf[Reader.SyncReader[String]]
      val first     = scala.util.Try(p.read("eof")).failed.toOption.orNull
      val scalar    = scala.util.Try(p.read(new String("eof"))).failed.toOption.orNull
      val bulk      = scala.util.Try(p.readN(2)).failed.toOption.orNull
      val readNZero = scala.util.Try(p.readN(0)).failed.toOption.orNull
      val upToZero  = scala.util.Try(p.readUpToN(0)).failed.toOption.orNull
      val skipZero  = scala.util.Try(p.skip(0)).failed.toOption.orNull
      val control   = scala.util.Try(p.setLimit(1)).failed.toOption.orNull

      assertTrue(
        first eq failure,
        scalar eq failure,
        bulk eq failure,
        readNZero eq failure,
        upToZero eq failure,
        skipZero eq failure,
        control eq failure,
        reads == 1,
        p.isClosed,
        !p.readable()
      )
    },
    test("zero-length caller-array reads validate first and then replay the failure by identity") {
      val failure = new RuntimeException("read")
      val source  = new Reader.SyncReader[Int] {
        override def jvmType: JvmType                                        = JvmType.Int
        def isClosed: Boolean                                                = false
        def close(): Unit                                                    = ()
        def read[A1 >: Int](sentinel: A1): A1                                = throw failure
        override def readInt(sentinel: Long)(implicit ev: Int <:< Int): Long = throw failure
      }
      val p     = SyncInterpreter(source)
      val first = scala.util.Try(readIntValue(p, -1)).failed.toOption.orNull
      val bytes = scala.util
        .Try(p.asInstanceOf[Reader.SyncReader[Byte]].readBytes(new Array[Byte](0), 0, 0))
        .failed
        .toOption
        .orNull
      val ints =
        scala.util.Try(p.asInstanceOf[Reader.SyncReader[Int]].readInts(new Array[Int](0), 0, 0)).failed.toOption.orNull
      val longs = scala.util
        .Try(p.asInstanceOf[Reader.SyncReader[Long]].readLongs(new Array[Long](0), 0, 0))
        .failed
        .toOption
        .orNull
      val floats = scala.util
        .Try(p.asInstanceOf[Reader.SyncReader[Float]].readFloats(new Array[Float](0), 0, 0))
        .failed
        .toOption
        .orNull
      val doubles = scala.util
        .Try(p.asInstanceOf[Reader.SyncReader[Double]].readDoubles(new Array[Double](0), 0, 0))
        .failed
        .toOption
        .orNull
      val invalid =
        scala.util.Try(p.asInstanceOf[Reader.SyncReader[Int]].readInts(new Array[Int](0), 1, 0)).failed.toOption.orNull

      assertTrue(
        first eq failure,
        bytes eq failure,
        ints eq failure,
        longs eq failure,
        floats eq failure,
        doubles eq failure,
        invalid.isInstanceOf[IndexOutOfBoundsException]
      )
    },
    test("primitive MAP, FILTER, and PUSH failures are each invoked once and replayed by identity") {
      val mapFailure = new RuntimeException("map")
      var maps       = 0
      val mapped     = SyncInterpreter(Reader.fromChunk(Chunk(1)))
      mapped.addMap[Int, Int](LANE_I, OUT_I) { _ => maps += 1; throw mapFailure }
      val map1          = scala.util.Try(readIntValue(mapped, -1)).failed.toOption.orNull
      val map2          = scala.util.Try(readIntValue(mapped, -1)).failed.toOption.orNull
      val filterFailure = new RuntimeException("filter")
      var filters       = 0
      val filtered      = SyncInterpreter(Reader.fromChunk(Chunk(1)))
      filtered.addFilter[Int](LANE_I) { _ => filters += 1; throw filterFailure }
      val filter1     = scala.util.Try(readIntValue(filtered, -1)).failed.toOption.orNull
      val filter2     = scala.util.Try(readIntValue(filtered, -1)).failed.toOption.orNull
      val pushFailure = new RuntimeException("push")
      var pushes      = 0
      val pushed      = SyncInterpreter(Reader.fromChunk(Chunk(1)))
      pushed.addPush[Int](LANE_I) { _ => pushes += 1; throw pushFailure }
      val push1 = scala.util.Try(readIntValue(pushed, -1)).failed.toOption.orNull
      val push2 = scala.util.Try(readIntValue(pushed, -1)).failed.toOption.orNull

      assertTrue(
        map1 eq mapFailure,
        map2 eq mapFailure,
        maps == 1,
        filter1 eq filterFailure,
        filter2 eq filterFailure,
        filters == 1,
        push1 eq pushFailure,
        push2 eq pushFailure,
        pushes == 1
      )
    },
    test("inner compilation and lane bridge failures are sticky and do not retry PUSH") {
      var compilePushes = 0
      val compiling     = SyncInterpreter(Reader.fromChunk(Chunk(1)))
      compiling.addPush[Int](LANE_I) { _ => compilePushes += 1; null }
      val compile1 = scala.util.Try(readIntValue(compiling, -1)).failed.toOption.orNull
      val compile2 = scala.util.Try(readIntValue(compiling, -1)).failed.toOption.orNull

      var bridgePushes  = 0
      val bridgeFailure = new RuntimeException("bridge")
      val badNumber     = new java.lang.Number {
        def intValue(): Int       = throw bridgeFailure
        def longValue(): Long     = throw bridgeFailure
        def floatValue(): Float   = throw bridgeFailure
        def doubleValue(): Double = throw bridgeFailure
      }
      val bridging = SyncInterpreter(Reader.fromChunk(Chunk(1)))
      bridging.addPush[Int](LANE_I) { _ =>
        bridgePushes += 1
        Stream.fromChunk(Chunk[java.lang.Number](badNumber))
      }
      val bridge1 = scala.util.Try(readIntValue(bridging, -1)).failed.toOption.orNull
      val bridge2 = scala.util.Try(readIntValue(bridging, -1)).failed.toOption.orNull

      assertTrue(
        compile1 ne null,
        compile2 eq compile1,
        compilePushes == 1,
        bridge1 eq bridgeFailure,
        bridge2 eq bridge1,
        bridgePushes == 1
      )
    },
    test("successful reset clears a sticky primitive read failure and starts a fresh close cycle") {
      val failure = new RuntimeException("once")
      var failed  = false; var value = 0; var closes = 0
      val source  = new Reader.SyncReader[Int] {
        override def jvmType: JvmType                                        = JvmType.Int
        def isClosed: Boolean                                                = false
        def close(): Unit                                                    = closes += 1
        def read[A1 >: Int](sentinel: A1): A1                                = readInt(Long.MinValue).asInstanceOf[A1]
        override def readInt(sentinel: Long)(implicit ev: Int <:< Int): Long =
          if (!failed) { failed = true; throw failure }
          else if (value == 0) { value = 1; 42L }
          else sentinel
        override def reset(): Unit = value = 0
      }
      val p      = SyncInterpreter(source)
      val first  = scala.util.Try(readIntValue(p, -1)).failed.toOption.orNull
      val replay = scala.util.Try(readIntValue(p, -1)).failed.toOption.orNull
      p.reset()
      val recovered = readIntValue(p, -1)
      p.close(); p.close()

      assertTrue(first eq failure, replay eq failure, recovered == 42L, closes == 1)
    },
    test("failed root reset retains root close ownership and ordered failure suppression") {
      val failure      = new RuntimeException("read")
      val resetFailure = new RuntimeException("reset")
      val closeFailure = new RuntimeException("close")
      var closes       = 0
      val source       = new Reader.SyncReader[Int] {
        override def jvmType: JvmType                                        = JvmType.Int
        def isClosed: Boolean                                                = false
        def read[A1 >: Int](sentinel: A1): A1                                = throw failure
        override def readInt(sentinel: Long)(implicit ev: Int <:< Int): Long = throw failure
        def close(): Unit                                                    = {
          closes += 1
          throw closeFailure
        }
        override def reset(): Unit = throw resetFailure
      }
      val p      = SyncInterpreter(source)
      val first  = scala.util.Try(readIntValue(p, -1)).failed.toOption.orNull
      val reset  = scala.util.Try(p.reset()).failed.toOption.orNull
      val closed = scala.util.Try(p.close()).failed.toOption.orNull

      assertTrue(
        first eq failure,
        reset eq failure,
        closed eq failure,
        failure.getSuppressed.toList == List(resetFailure, closeFailure),
        closes == 1,
        p.isClosed
      )
    },
    test("reentrant close after failed root reset joins cleanup without replaying prematurely") {
      val failure            = new RuntimeException("read")
      val resetFailure       = new RuntimeException("reset")
      var closes             = 0
      var cleanupDone        = false
      var p: SyncInterpreter = null
      val source             = new Reader.SyncReader[Int] {
        override def jvmType: JvmType                                        = JvmType.Int
        def isClosed: Boolean                                                = false
        def read[A1 >: Int](sentinel: A1): A1                                = throw failure
        override def readInt(sentinel: Long)(implicit ev: Int <:< Int): Long = throw failure
        def close(): Unit                                                    = {
          closes += 1
          p.close()
          cleanupDone = true
        }
        override def reset(): Unit = throw resetFailure
      }
      p = SyncInterpreter(source)
      val first  = scala.util.Try(readIntValue(p, -1)).failed.toOption.orNull
      val reset  = scala.util.Try(p.reset()).failed.toOption.orNull
      val closed = scala.util.Try(p.close()).failed.toOption.orNull
      val replay = scala.util.Try(p.close()).failed.toOption.orNull

      assertTrue(
        first eq failure,
        reset eq failure,
        closed eq failure,
        replay eq failure,
        failure.getSuppressed.toList == List(resetFailure),
        closes == 1,
        cleanupDone
      )
    },
    test("rejection-close installs fresh close ownership before reentrant root cleanup") {
      val cleanupFailure     = new RuntimeException("nested-close")
      var rootCloses         = 0
      var p: SyncInterpreter = null
      val root               = new Reader.SyncReader[Int] {
        def isClosed: Boolean                 = false
        def read[A1 >: Int](sentinel: A1): A1 = sentinel
        def close(): Unit                     = {
          rootCloses += 1
          p.close()
        }
        override def reset(): Unit = ()
      }
      val nested = new Reader.SyncReader[Int] {
        def isClosed: Boolean                 = false
        def read[A1 >: Int](sentinel: A1): A1 = sentinel
        def close(): Unit                     = throw cleanupFailure
      }
      p = SyncInterpreter(root)
      p.appendRead(nested)

      val reset  = scala.util.Try(p.reset()).failed.toOption.orNull
      val replay = scala.util.Try(p.close()).failed.toOption.orNull

      assertTrue(reset eq cleanupFailure, replay eq cleanupFailure, rootCloses == 1, p.isClosed)
    },
    test("reentrant close during nested reset cleanup detaches the nested reader and rejects reopening") {
      var nestedCloses       = 0
      var rootCloses         = 0
      var p: SyncInterpreter = null
      val root               = new Reader.SyncReader[Int] {
        def isClosed: Boolean                 = false
        def read[A1 >: Int](sentinel: A1): A1 = sentinel
        def close(): Unit                     = rootCloses += 1
        override def reset(): Unit            = ()
      }
      val nested = new Reader.SyncReader[Int] {
        def isClosed: Boolean                 = false
        def read[A1 >: Int](sentinel: A1): A1 = sentinel
        def close(): Unit                     = {
          nestedCloses += 1
          p.close()
        }
      }
      p = SyncInterpreter(root)
      p.appendRead(nested)

      val reset = scala.util.Try(p.reset()).failed.toOption.orNull
      p.close()

      assertTrue(
        reset.isInstanceOf[java.io.IOException],
        reset.getMessage == "Reader is closed",
        nestedCloses == 1,
        rootCloses == 1,
        p.isClosed
      )
    },
    test("reentrant close during root reset wins and rejection-closes the reopened root once") {
      var resets             = 0
      var rootCloses         = 0
      var p: SyncInterpreter = null
      val root               = new Reader.SyncReader[Int] {
        def isClosed: Boolean                 = false
        def read[A1 >: Int](sentinel: A1): A1 = sentinel
        def close(): Unit                     = rootCloses += 1
        override def reset(): Unit            = {
          resets += 1
          p.close()
        }
      }
      p = SyncInterpreter(root)

      val reset = scala.util.Try(p.reset()).failed.toOption.orNull
      p.close()

      assertTrue(
        reset.isInstanceOf[java.io.IOException],
        reset.getMessage == "Reader is closed",
        resets == 1,
        rootCloses == 1,
        p.isClosed
      )
    },
    test("reset is rejected from MAP, FILTER, PUSH, source read, readable, control, and root close") {
      val message                      = "Cannot reset reader from its active operation"
      def rejected(kind: Int): Boolean = {
        var p: SyncInterpreter = null
        def run(): Unit        = p.reset()
        val source             = new Reader.SyncReader[Int] {
          override def jvmType: JvmType         = JvmType.Int
          def isClosed: Boolean                 = false
          def read[A1 >: Int](sentinel: A1): A1 = {
            if (kind == 3) run()
            1.asInstanceOf[A1]
          }
          override def readInt(sentinel: Long)(implicit ev: Int <:< Int): Long = {
            if (kind == 3) run()
            1L
          }
          override def readable(): Boolean        = { if (kind == 4) run(); true }
          override def setLimit(n: Long): Boolean = { if (kind == 5) run(); true }
          def close(): Unit                       = if (kind == 6) run()
        }
        p = SyncInterpreter(source)
        if (kind == 0) p.addMap[Int, Int](LANE_I, OUT_I) { v => run(); v }
        else if (kind == 1) p.addFilter[Int](LANE_I) { v => run(); v != 0 }
        else if (kind == 2) p.addPush[Int](LANE_I) { v => run(); Stream.succeed(v) }
        val failure = scala.util.Try {
          if (kind == 4) p.readable()
          else if (kind == 5) p.setLimit(1L)
          else if (kind == 6) p.close()
          else readIntValue(p, -1L)
        }.failed.toOption.orNull
        failure.isInstanceOf[IllegalStateException] && failure.getMessage == message
      }

      assertTrue((0 to 6).forall(rejected))
    },
    test("reentrant public operations are rejected before a second read or state mutation") {
      def rejected(kind: Int): Boolean = {
        var reads              = 0
        var p: SyncInterpreter = null
        def reenter(): Unit    = {
          if (kind == 3) p.readable()
          else if (kind == 4) p.setLimit(1L)
          else p.readIntPhysical(-1L)
          ()
        }
        val source = new Reader.SyncReader[Int] {
          override def jvmType: JvmType                                        = JvmType.Int
          def isClosed: Boolean                                                = false
          def close(): Unit                                                    = ()
          def read[A1 >: Int](sentinel: A1): A1                                = readInt(-1L).asInstanceOf[A1]
          override def readInt(sentinel: Long)(implicit ev: Int <:< Int): Long = {
            reads += 1
            if (kind == 2) reenter()
            1L
          }
        }
        p = SyncInterpreter(source)
        if (kind == 0 || kind >= 3) p.addMap[Int, Int](LANE_I, OUT_I) { v => reenter(); v }
        else if (kind == 1) p.addFilter[Int](LANE_I) { v => reenter(); v != 0 }
        val failure = scala.util.Try(readIntValue(p, -1L)).failed.toOption.orNull
        failure.isInstanceOf[IllegalStateException] &&
        failure.getMessage == SyncInterpreter.ConcurrentOperationMessage && reads == 1
      }

      assertTrue((0 to 4).forall(rejected))
    },
    test("reset rejection close ignores a replay-equivalent typed failure") {
      val failure      = StreamError.source("typed")
      val resetFailure = new RuntimeException("reset")
      val replay       = StreamError.mapped(failure, "typed")
      val source       = new Reader.SyncReader[Int] {
        override def jvmType: JvmType                                        = JvmType.Int
        def isClosed: Boolean                                                = false
        def close(): Unit                                                    = throw replay
        override def reset(): Unit                                           = throw resetFailure
        def read[A1 >: Int](sentinel: A1): A1                                = throw failure
        override def readInt(sentinel: Long)(implicit ev: Int <:< Int): Long = throw failure
      }
      val p = SyncInterpreter(source)
      scala.util.Try(readIntValue(p, -1L))
      val rejected = scala.util.Try(p.reset()).failed.toOption.orNull

      assertTrue(
        rejected eq failure,
        failure.getSuppressed.toList == List(resetFailure),
        !failure.cleanupFailed
      )
    },
    test("close from MAP, source read, and control wins without stale success or further callbacks") {
      var mappedCallbacks         = 0
      var mapped: SyncInterpreter = null
      mapped = SyncInterpreter(Reader.fromChunk(Chunk(1)))
      mapped.addMap[Int, Int](LANE_I, OUT_I) { v => mappedCallbacks += 1; mapped.close(); v + 1 }
      mapped.addMap[Int, Int](LANE_I, OUT_I) { v => mappedCallbacks += 1; v + 1 }

      var sourceReads                     = 0
      var sourcePipeline: SyncInterpreter = null
      val source                          = new Reader.SyncReader[Int] {
        override def jvmType: JvmType                                        = JvmType.Int
        def isClosed: Boolean                                                = false
        def close(): Unit                                                    = ()
        def read[A1 >: Int](sentinel: A1): A1                                = readInt(-1L).asInstanceOf[A1]
        override def readInt(sentinel: Long)(implicit ev: Int <:< Int): Long = {
          sourceReads += 1; sourcePipeline.close(); 1L
        }
      }
      sourcePipeline = SyncInterpreter(source)

      var controlPipeline: SyncInterpreter = null
      val controlled                       = new Reader.SyncReader[Int] {
        def isClosed: Boolean                   = false
        def close(): Unit                       = ()
        def read[A1 >: Int](sentinel: A1): A1   = sentinel
        override def setLimit(n: Long): Boolean = { controlPipeline.close(); true }
      }
      controlPipeline = SyncInterpreter(controlled)
      val controlFailure = scala.util.Try(controlPipeline.setLimit(1L)).failed.toOption.orNull

      assertTrue(
        readIntValue(mapped, -1L) == -1L,
        mappedCallbacks == 1,
        readIntValue(sourcePipeline, -1L) == -1L,
        sourceReads == 1,
        controlFailure.isInstanceOf[java.io.IOException],
        controlFailure.getMessage == "Reader is closed",
        mapped.isClosed,
        sourcePipeline.isClosed,
        controlPipeline.isClosed
      )
    },
    test("close from PUSH prevents compilation and ownership of the returned inner stream") {
      var innerCloses = 0
      val inner       = new Reader.SyncReader[Int] {
        override def jvmType: JvmType         = JvmType.Int
        def isClosed: Boolean                 = false
        def close(): Unit                     = innerCloses += 1
        def read[A1 >: Int](sentinel: A1): A1 = throw new AssertionError("inner reader was installed")
      }
      var p: SyncInterpreter = null
      p = SyncInterpreter(Reader.fromChunk(Chunk(1)))
      p.addPush[Int](LANE_I) { _ =>
        p.close()
        Stream.fromReader[Nothing, Int](inner)
      }

      val value = readIntValue(p, -1L)
      p.close()

      assertTrue(value == -1L, p.isClosed, innerCloses == 0)
    },
    test("PUSH acquire-close prevents use and releases the acquired resource exactly once") {
      var acquires           = 0
      var uses               = 0
      var releases           = 0
      var p: SyncInterpreter = null
      p = SyncInterpreter(Reader.fromChunk(Chunk(1)))
      p.addPush[Int](LANE_I) { _ =>
        Stream.fromAcquireRelease(
          {
            acquires += 1
            p.close()
            new Object
          },
          (_: Object) => releases += 1
        ) { _ =>
          uses += 1
          Stream.succeed(2)
        }
      }

      val value  = readIntValue(p, -1L)
      val replay = scala.util.Try(p.close()).failed.toOption.orNull

      assertTrue(value == -1L, acquires == 1, uses == 0, releases == 1, replay == null, p.isClosed)
    },
    test("guarded child routes stale rollback cleanup to the parent close") {
      val releaseFailure     = new RuntimeException("release")
      var releases           = 0
      var p: SyncInterpreter = null
      p = SyncInterpreter(Reader.singleInt(1))
      p.addPush[Int](LANE_I) { _ =>
        Stream
          .fromAcquireRelease(new Object, (_: Object) => { releases += 1; throw releaseFailure }) { _ =>
            Stream.fromReader[Nothing, Int](new Reader.SyncReader[Int] {
              def isClosed: Boolean              = false
              def read[A >: Int](sentinel: A): A = {
                p.close()
                2.asInstanceOf[A]
              }
              def close(): Unit = ()
            })
          }
          .chunked(1)
          .map(_.head)
      }

      val value  = readIntValue(p, -1L)
      val replay = scala.util.Try(p.close()).failed.toOption.orNull

      assertTrue(value == -1L, replay eq releaseFailure, releases == 1, p.isClosed)
    },
    test("rejected acquire-release wrapper closes its child and releases its resource exactly once") {
      var childCloses        = 0
      var releases           = 0
      var p: SyncInterpreter = null
      val child              = new Reader.SyncReader[Int] {
        override def jvmType: JvmType         = { p.close(); JvmType.Int }
        def isClosed: Boolean                 = false
        def close(): Unit                     = childCloses += 1
        def read[A1 >: Int](sentinel: A1): A1 = sentinel
      }
      p = SyncInterpreter(Reader.fromChunk(Chunk(1)))
      p.addPush[Int](LANE_I) { _ =>
        Stream.fromAcquireRelease(new Object, (_: Object) => releases += 1)(_ => Stream.fromReader(child))
      }

      assertTrue(readIntValue(p, -1L) == -1L, childCloses == 1, releases == 1, p.isClosed)
    },
    test("CatchDefect remains outside the synchronous interpreter without inspecting recovery") {
      var inspections = 0
      var closes      = 0
      val source      = new Reader.SyncReader[Int] {
        override def jvmType: JvmType         = JvmType.Int
        def isClosed: Boolean                 = false
        def close(): Unit                     = closes += 1
        def read[A1 >: Int](sentinel: A1): A1 = throw new RuntimeException("stale")
      }
      val recovery = new PartialFunction[Throwable, Stream[Nothing, Int]] {
        def isDefinedAt(cause: Throwable): Boolean        = { inspections += 1; true }
        def apply(cause: Throwable): Stream[Nothing, Int] = Stream.succeed(2)
      }
      val rejected = scala.util
        .Try(
          SyncInterpreter.fromStream(Stream.fromReader[Nothing, Int](source).catchDefect(recovery))
        )
        .failed
        .toOption
        .orNull

      assertTrue(
        rejected eq Stream.AsyncBoundaryRequired,
        inspections == 0,
        closes == 0
      )
    },
    test("PUSH use-close prevents the returned reader factory and replays release failure only from close") {
      val releaseFailure     = new RuntimeException("release")
      var factories          = 0
      var releases           = 0
      var p: SyncInterpreter = null
      p = SyncInterpreter(Reader.fromChunk(Chunk(1)))
      p.addPush[Int](LANE_I) { _ =>
        Stream.fromAcquireRelease(
          new Object,
          (_: Object) => {
            releases += 1
            throw releaseFailure
          }
        ) { _ =>
          p.close()
          Stream.fromReader[Nothing, Int] {
            factories += 1
            Reader.fromChunk(Chunk(2))
          }
        }
      }

      val value  = readIntValue(p, -1L)
      val replay = scala.util.Try(p.close()).failed.toOption.orNull

      assertTrue(value == -1L, factories == 0, releases == 1, replay eq releaseFailure)
    },
    test("PUSH deferred-close prevents compilation of the returned stream") {
      var factories          = 0
      var p: SyncInterpreter = null
      p = SyncInterpreter(Reader.fromChunk(Chunk(1)))
      p.addPush[Int](LANE_I) { _ =>
        Stream.suspend {
          p.close()
          Stream.fromReader[Nothing, Int] {
            factories += 1
            Reader.fromChunk(Chunk(2))
          }
        }
      }

      assertTrue(readIntValue(p, -1L) == -1L, factories == 0, p.isClosed)
    },
    test("PUSH nested zip propagates materialization rejection before starting later factories") {
      val closeFailure       = new RuntimeException("left-close")
      var leftCloses         = 0
      var middleFactories    = 0
      var rightFactories     = 0
      var p: SyncInterpreter = null
      p = SyncInterpreter(Reader.fromChunk(Chunk(1)))
      p.addPush[Int](LANE_I) { _ =>
        val left = Stream.fromReader[Nothing, Int] {
          p.close()
          new Reader.SyncReader[Int] {
            def isClosed: Boolean                 = false
            def close(): Unit                     = { leftCloses += 1; throw closeFailure }
            def read[A1 >: Int](sentinel: A1): A1 = sentinel
          }
        }
        val middle = Stream.fromReader[Nothing, Int] {
          middleFactories += 1
          Reader.singleInt(2)
        }
        val right = Stream.fromReader[Nothing, Int] {
          rightFactories += 1
          Reader.singleInt(3)
        }
        (left && middle) && right
      }

      val value  = readIntValue(p, -1L)
      val replay = scala.util.Try(p.close()).failed.toOption.orNull

      assertTrue(
        value == -1L,
        replay eq closeFailure,
        leftCloses == 1,
        middleFactories == 0,
        rightFactories == 0,
        p.isClosed
      )
    },
    test("stale wrapper callbacks are discarded instead of becoming close failures") {
      def replayFor(stream: SyncInterpreter => Stream[Nothing, Any]): Throwable = {
        var p: SyncInterpreter = null
        p = SyncInterpreter(Reader.singleInt(1))
        p.addPush[Int](LANE_I)(_ => stream(p))
        p.read[Any](EndOfStream)
        scala.util.Try(p.close()).failed.toOption.orNull
      }

      val concatReplay = replayFor { p =>
        (Stream.empty: Stream[Nothing, Any]) ++ Stream.fromReader[Nothing, Any] {
          p.close()
          Reader.single[Any](1)(JvmType.Infer.boxed[Any])
        }
      }
      var combines  = 0
      val zipReplay = replayFor { p =>
        (Stream.succeed(1) && Stream.succeed(2)).map[Any] { value =>
          combines += 1
          p.close()
          value
        }
      }

      assertTrue(concatReplay == null, zipReplay == null, combines == 1)
    },
    test("zip materialization rejection routes left cleanup to parent close") {
      val cleanup            = new RuntimeException("left cleanup")
      var p: SyncInterpreter = null
      p = SyncInterpreter(Reader.singleInt(1))
      p.addPush[Int](LANE_I) { _ =>
        val left = Stream.fromReader[Nothing, Int](new Reader.SyncReader[Int] {
          def isClosed: Boolean              = false
          def read[A >: Int](sentinel: A): A = sentinel
          def close(): Unit                  = throw cleanup
        })
        val right = Stream.fromReader[Nothing, Int] {
          p.close()
          Reader.singleInt(2)
        }
        left && right
      }

      val value  = readIntValue(p, -1L)
      val replay = scala.util.Try(p.close()).failed.toOption.orNull

      assertTrue(value == -1L, replay eq cleanup, p.isClosed)
    },
    test("PUSH materialization rejection propagates through every reader-owning wrapper") {
      type Wrapped = Stream[Nothing, Any] => Stream[Nothing, Any]
      val wrappers = List[(String, Wrapped)](
        "concat"      -> ((stream: Stream[Nothing, Any]) => stream ++ Stream.empty),
        "buffer"      -> ((stream: Stream[Nothing, Any]) => stream.buffer(1)),
        "mapPar"      -> ((stream: Stream[Nothing, Any]) => stream.mapPar(1)(identity)),
        "chunked"     -> ((stream: Stream[Nothing, Any]) => stream.chunked(1).map[Any](identity)),
        "intersperse" -> ((stream: Stream[Nothing, Any]) => stream.intersperse(null)),
        "scan"        -> ((stream: Stream[Nothing, Any]) => stream.scan[Any](0)((_, value) => value)),
        "sliding"     -> ((stream: Stream[Nothing, Any]) => stream.sliding(1).map[Any](identity))
      )

      val failures = wrappers.flatMap { case (name, wrap) =>
        var rightFactories     = 0
        var p: SyncInterpreter = null
        p = SyncInterpreter(Reader.singleInt(1))
        p.addPush[Int](LANE_I) { _ =>
          val left = Stream.fromReader[Nothing, Int] {
            p.close()
            Reader.singleInt(1)
          }
          val right = Stream.fromReader[Nothing, Int] {
            rightFactories += 1
            Reader.singleInt(2)
          }
          wrap((left && right).map[Any](identity))
        }
        p.read[Any](EndOfStream)
        if (rightFactories == 0 && p.isClosed) None else Some(name)
      }

      val runtimeFailures = wrappers.filterNot { case (name, _) => name == "buffer" || name == "mapPar" }.flatMap {
        case (name, wrap) =>
          var rightPulls         = 0
          var combines           = 0
          var p: SyncInterpreter = null
          p = SyncInterpreter(Reader.singleInt(1))
          p.addPush[Int](LANE_I) { _ =>
            val left = Stream.fromReader[Nothing, Int](new Reader.SyncReader[Int] {
              def isClosed: Boolean              = false
              def read[A >: Int](sentinel: A): A = {
                p.close()
                1.asInstanceOf[A]
              }
              def close(): Unit = ()
            })
            val right = Stream.fromReader[Nothing, Int](new Reader.SyncReader[Int] {
              def isClosed: Boolean              = false
              def read[A >: Int](sentinel: A): A = {
                rightPulls += 1
                2.asInstanceOf[A]
              }
              def close(): Unit = ()
            })
            wrap((left && right).map[Any] { value => combines += 1; value })
          }
          p.read[Any](EndOfStream)
          if (!p.isClosed) p.read[Any](EndOfStream)
          if (rightPulls == 0 && combines == 0 && p.isClosed) None else Some(name)
      }

      assertTrue(failures.isEmpty, runtimeFailures.isEmpty)
    },
    test("stale materialization cleanup failures do not contaminate another interpreter") {
      def rejected(message: String): (Throwable, Throwable) = {
        val releaseFailure     = new RuntimeException(message)
        var p: SyncInterpreter = null
        p = SyncInterpreter(Reader.fromChunk(Chunk(1)))
        p.addPush[Int](LANE_I) { _ =>
          Stream.fromAcquireRelease(new Object, (_: Object) => throw releaseFailure) { _ =>
            p.close()
            Stream.succeed(2)
          }
        }
        val stale  = scala.util.Try(readIntValue(p, -1L)).failed.toOption.orNull
        val replay = scala.util.Try(p.close()).failed.toOption.orNull
        (stale, replay)
      }

      val (stale1, replay1) = rejected("first")
      val (stale2, replay2) = rejected("second")
      assertTrue(
        stale1 == null,
        stale2 == null,
        replay1.getMessage == "first",
        replay2.getMessage == "second",
        replay1 ne replay2,
        replay1.getSuppressed.isEmpty,
        replay2.getSuppressed.isEmpty
      )
    },
    test("CatchAll and CatchDefect materialization failure close their child exactly once") {
      def closesFor(stream: Reader.SyncReader[Int] => Stream[Any, Int]) = {
        var closes = 0
        val child  = new Reader.SyncReader[Int] {
          def isClosed: Boolean                 = false
          def read[A1 >: Int](sentinel: A1): A1 = sentinel
          def close(): Unit                     = closes += 1
          override def jvmType: JvmType         = throw new RuntimeException("jvmType")
        }
        stream(child).compile(0) match {
          case reader: Reader.AsyncReader[Int @unchecked] =>
            runAsync(reader.read(-1).either).map(result => (result.left.toOption.orNull, closes))
          case reader: Reader.SyncReader[Int @unchecked] =>
            zio.ZIO.succeed((scala.util.Try(reader.read(-1)).failed.toOption.orNull, closes))
        }
      }

      for {
        all    <- closesFor(r => Stream.fromReader[Nothing, Int](r).catchAll(_ => Stream.empty))
        defect <- closesFor(r => Stream.fromReader[Nothing, Int](r).catchDefect { case _ => Stream.empty })
      } yield {
        val (allFailure, allCloses)       = all
        val (defectFailure, defectCloses) = defect
        assertTrue(
          allFailure.getMessage == "jvmType",
          allCloses == 1,
          defectFailure == null,
          defectCloses == 1
        )
      }
    },
    test("close during source, readable, and control exceptions makes the exceptions stale") {
      val stale              = new RuntimeException("stale")
      var p: SyncInterpreter = null
      val source             = new Reader.SyncReader[Int] {
        override def jvmType: JvmType                                        = JvmType.Int
        def isClosed: Boolean                                                = false
        def close(): Unit                                                    = ()
        def read[A1 >: Int](sentinel: A1): A1                                = readInt(-1L).asInstanceOf[A1]
        override def readInt(sentinel: Long)(implicit ev: Int <:< Int): Long = { p.close(); throw stale }
      }
      p = SyncInterpreter(source)
      val scalar = readIntValue(p, -1L)

      var readablePipeline: SyncInterpreter = null
      val readableSource                    = new Reader.SyncReader[Int] {
        def isClosed: Boolean                 = false
        def close(): Unit                     = ()
        def read[A1 >: Int](sentinel: A1): A1 = sentinel
        override def readable(): Boolean      = { readablePipeline.close(); throw stale }
      }
      readablePipeline = SyncInterpreter(readableSource)

      var controlPipeline: SyncInterpreter = null
      val controlSource                    = new Reader.SyncReader[Int] {
        def isClosed: Boolean                   = false
        def close(): Unit                       = ()
        def read[A1 >: Int](sentinel: A1): A1   = sentinel
        override def setLimit(n: Long): Boolean = { controlPipeline.close(); throw stale }
      }
      controlPipeline = SyncInterpreter(controlSource)
      val control = scala.util.Try(controlPipeline.setLimit(1L)).failed.toOption.orNull

      assertTrue(
        scalar == -1L,
        !readablePipeline.readable(),
        control.isInstanceOf[java.io.IOException],
        control.getMessage == "Reader is closed"
      )
    },
    test("clean reset rejected by reentrant close suppresses rejection-close failure and close replays it") {
      val closeFailure       = new RuntimeException("rejection-close")
      var closes             = 0
      var p: SyncInterpreter = null
      val source             = new Reader.SyncReader[Int] {
        def isClosed: Boolean                 = false
        def read[A1 >: Int](sentinel: A1): A1 = sentinel
        def close(): Unit                     = { closes += 1; throw closeFailure }
        override def reset(): Unit            = p.close()
      }
      p = SyncInterpreter(source)
      val rejected = scala.util.Try(p.reset()).failed.toOption.orNull
      val replay1  = scala.util.Try(p.close()).failed.toOption.orNull
      val replay2  = scala.util.Try(p.close()).failed.toOption.orNull

      assertTrue(
        rejected.isInstanceOf[java.io.IOException],
        rejected.getMessage == "Reader is closed",
        rejected.getSuppressed.toList == List(closeFailure),
        replay1 eq closeFailure,
        replay2 eq closeFailure,
        closes == 1
      )
    },
    test("historical close failure is replayed only by close") {
      val failure = new RuntimeException("close")
      val source  = new Reader.SyncReader[Int] {
        def isClosed: Boolean                 = false
        def read[A1 >: Int](sentinel: A1): A1 = sentinel
        def close(): Unit                     = throw failure
      }
      val p               = SyncInterpreter(source)
      val first           = scala.util.Try(p.close()).failed.toOption.orNull
      val readableFailure = scala.util.Try(p.readable()).failed.toOption.orNull
      val control         = scala.util.Try(p.setLimit(1L)).failed.toOption.orNull
      val replay          = scala.util.Try(p.close()).failed.toOption.orNull
      assertTrue(
        first eq failure,
        readableFailure == null,
        !p.readable(),
        control.isInstanceOf[java.io.IOException],
        control.getMessage == "Reader is closed",
        replay eq failure
      )
    },
    test("READ close followed by EOF defers nested close to traversal order") {
      val order              = scala.collection.mutable.ListBuffer.empty[String]
      var p: SyncInterpreter = null
      val root               = new Reader.SyncReader[Int] {
        def isClosed: Boolean                 = false
        def read[A1 >: Int](sentinel: A1): A1 = 1.asInstanceOf[A1]
        def close(): Unit                     = order += "root"
      }
      val inner = new Reader.SyncReader[Int] {
        override def jvmType: JvmType                                        = JvmType.Int
        def isClosed: Boolean                                                = false
        def read[A1 >: Int](sentinel: A1): A1                                = sentinel
        override def readInt(sentinel: Long)(implicit ev: Int <:< Int): Long = { p.close(); sentinel }
        def close(): Unit                                                    = order += "inner"
      }
      p = SyncInterpreter.fromStream(
        Stream.fromReader[Nothing, Int](root).flatMap(_ => Stream.fromReader[Nothing, Int](inner))
      )
      readIntValue(p, -1L)
      assertTrue(order.toList == List("root", "inner"))
    },
    test("popAt reentrant close claims inner exactly once") {
      var closes             = 0
      var p: SyncInterpreter = null
      val inner              = new Reader.SyncReader[Int] {
        override def jvmType: JvmType                                        = JvmType.Int
        private var emitted                                                  = false
        def isClosed: Boolean                                                = false
        def read[A1 >: Int](sentinel: A1): A1                                = readInt(Long.MinValue).asInstanceOf[A1]
        override def readInt(sentinel: Long)(implicit ev: Int <:< Int): Long =
          if (emitted) sentinel else { emitted = true; 1L }
        def close(): Unit = { closes += 1; p.close() }
      }
      p = SyncInterpreter.fromStream(Stream.succeed(1).flatMap(_ => Stream.fromReader[Nothing, Int](inner)))
      val value = readIntValue(p, -1L)
      readIntValue(p, -1L)
      p.close()
      assertTrue(value == 1L, closes == 1)
    },
    test("popAt reentrant close and throw is replayed before remaining close failures") {
      val innerFailure       = new RuntimeException("inner")
      val rootFailure        = new RuntimeException("root")
      var p: SyncInterpreter = null
      val root               = new Reader.SyncReader[Int] {
        def isClosed: Boolean                 = false
        def read[A1 >: Int](sentinel: A1): A1 = 1.asInstanceOf[A1]
        def close(): Unit                     = throw rootFailure
      }
      val inner = new Reader.SyncReader[Int] {
        override def jvmType: JvmType                                        = JvmType.Int
        def isClosed: Boolean                                                = false
        def read[A1 >: Int](sentinel: A1): A1                                = sentinel
        override def readInt(sentinel: Long)(implicit ev: Int <:< Int): Long = sentinel
        def close(): Unit                                                    = { p.close(); throw innerFailure }
      }
      p = SyncInterpreter.fromStream(
        Stream.fromReader[Nothing, Int](root).flatMap(_ => Stream.fromReader[Nothing, Int](inner))
      )
      val first  = scala.util.Try(readIntValue(p, -1L)).failed.toOption.orNull
      val replay = scala.util.Try(p.close()).failed.toOption.orNull
      assertTrue(first == null, replay eq innerFailure, innerFailure.getSuppressed.toList == List(rootFailure))
    }
  )

  val resetAndRereadSuite = suite("Reset and re-read")(
    test("resetting excludes every public operation category and validates caller arrays first") {
      var p: SyncInterpreter = null
      var upstreamCalls      = 0
      var observed           = List.empty[Any]
      val root               = new Reader.SyncReader[Int] {
        override def jvmType: JvmType         = JvmType.Int
        def isClosed: Boolean                 = false
        def close(): Unit                     = ()
        def read[A1 >: Int](sentinel: A1): A1 = { upstreamCalls += 1; sentinel }
        override def reset(): Unit            = {
          val ints     = p.asInstanceOf[Reader.SyncReader[Int]]
          val booleans = p.asInstanceOf[Reader.SyncReader[Boolean]]
          val doubles  = p.asInstanceOf[Reader.SyncReader[Double]]
          val floats   = p.asInstanceOf[Reader.SyncReader[Float]]
          val longs    = p.asInstanceOf[Reader.SyncReader[Long]]
          val invalid  = scala.util.Try(ints.readInts(new Array[Int](1), 2, 0)).failed.toOption.orNull
          p.skip(100)
          observed = List(
            p.read("eof"),
            booleans.readBoolean(-1),
            ints.readInt(-2L),
            longs.readLong(-3L),
            floats.readFloat(-4.0),
            doubles.readDouble(-5.0),
            p.readN(3),
            p.readUpToN(3),
            ints.readInts(new Array[Int](1), 0, 1),
            ints.readInts(new Array[Int](0), 0, 0),
            p.readable(),
            p.isClosed,
            p.setLimit(1),
            p.setRepeat(),
            p.setSkip(1),
            invalid.isInstanceOf[IndexOutOfBoundsException]
          )
        }
      }
      p = SyncInterpreter(root)
      p.reset()

      assertTrue(
        observed == List(
          "eof",
          -1,
          -2L,
          -3L,
          -4.0,
          -5.0,
          Chunk.empty,
          Chunk.empty,
          -1,
          0,
          false,
          true,
          false,
          false,
          false,
          true
        ),
        upstreamCalls == 0,
        !p.isClosed
      )
    },
    test("read all, reset, read all again returns same elements") {
      val source = Reader.fromRange(0 until 5)
      val p      = SyncInterpreter.unsealed(source)
      p.addMap[Int, Int](LANE_I, OUT_I)((_: Int) + 1)
      p.seal()
      val first = drainInts(p)
      p.reset()
      val second = drainInts(p)
      assertTrue(first == List(1, 2, 3, 4, 5)) &&
      assertTrue(second == List(1, 2, 3, 4, 5))
    },
    test("reset after partial read starts over") {
      val source = Reader.fromRange(0 until 5)
      val p      = SyncInterpreter(source)
      val v1     = readIntValue(p, Long.MinValue)
      val v2     = readIntValue(p, Long.MinValue)
      p.reset()
      val result = drainInts(p)
      assertTrue(v1 == 0L && v2 == 1L) &&
      assertTrue(result == List(0, 1, 2, 3, 4))
    },
    test("reset on pipeline with filter") {
      val source = Reader.fromRange(0 until 10)
      val p      = SyncInterpreter.unsealed(source)
      p.addFilter[Int](LANE_I)((_: Int) % 3 == 0)
      p.seal()
      val first = drainInts(p)
      p.reset()
      val second = drainInts(p)
      assertTrue(first == List(0, 3, 6, 9)) &&
      assertTrue(second == List(0, 3, 6, 9))
    },
    test("reset on pipeline with map") {
      val source = Reader.fromChunk(Chunk(10L, 20L, 30L))
      val p      = SyncInterpreter.unsealed(source)
      p.addMap[Long, Long](LANE_L, OUT_L)((_: Long) * 2L)
      p.seal()
      val first = drainLongs(p)
      p.reset()
      val second = drainLongs(p)
      assertTrue(first == List(20L, 40L, 60L)) &&
      assertTrue(second == List(20L, 40L, 60L))
    },
    test("reset on pipeline with no ops") {
      val source = Reader.fromRange(0 until 3)
      val p      = SyncInterpreter(source)
      val first  = drainInts(p)
      p.reset()
      val second = drainInts(p)
      assertTrue(first == List(0, 1, 2)) &&
      assertTrue(second == List(0, 1, 2))
    },
    test("multiple resets") {
      val source = Reader.fromRange(0 until 3)
      val p      = SyncInterpreter.unsealed(source)
      p.addMap[Int, Int](LANE_I, OUT_I)((_: Int) * 5)
      p.seal()
      val r1 = drainInts(p)
      p.reset()
      val r2 = drainInts(p)
      p.reset()
      val r3 = drainInts(p)
      assertTrue(r1 == r2 && r2 == r3 && r1 == List(0, 5, 10))
    },
    test("reset via fromStream preserves all ops") {
      val stream = Stream.fromRange(0 until 5).map((_: Int) + 1).filter((_: Int) % 2 == 0)
      val p      = SyncInterpreter.fromStream(stream)
      val first  = drainInts(p)
      p.reset()
      val second = drainInts(p)
      assertTrue(first == List(2, 4)) &&
      assertTrue(second == List(2, 4))
    },
    test("reset preserves a nested reader close failure") {
      val closeFailure  = new RuntimeException("close")
      var closeAttempts = 0
      val nested        = new Reader.SyncReader[Int] {
        def isClosed: Boolean                 = false
        def read[A1 >: Int](sentinel: A1): A1 = sentinel
        def close(): Unit                     = {
          closeAttempts += 1
          throw closeFailure
        }
      }
      val p = SyncInterpreter(Reader.fromRange(0 until 3))
      p.appendRead(nested)

      val caught = try { p.reset(); null }
      catch { case t: Throwable => t }
      val retry = try { p.reset(); null }
      catch { case t: Throwable => t }

      assertTrue(caught eq closeFailure, retry == null, closeAttempts == 1)
    }
  )

  // =========================================================================
  //  22. Close behavior
  // =========================================================================

  val closeBehaviorSuite = suite("Close behavior")(
    test("close() sets isClosed to true") {
      val source      = Reader.fromRange(0 until 5)
      val p           = SyncInterpreter(source)
      val beforeClose = p.isClosed
      p.close()
      val afterClose = p.isClosed
      assertTrue(!beforeClose && afterClose)
    },
    test("read after close returns sentinel (readInt)") {
      val source = Reader.fromRange(0 until 5)
      val p      = SyncInterpreter(source)
      p.close()
      assertTrue(readIntValue(p, Long.MinValue) == Long.MinValue)
    },
    test("read after close returns sentinel (readLong)") {
      val source = Reader.fromChunk(Chunk(1L, 2L))
      val p      = SyncInterpreter(source)
      p.close()
      assertTrue(readLongValue(p, Long.MaxValue) == Long.MaxValue)
    },
    test("read after close returns sentinel (readFloat)") {
      val source = Reader.fromChunk(Chunk(1.0f, 2.0f))
      val p      = SyncInterpreter(source)
      p.close()
      assertTrue(readFloatValue(p, Double.MaxValue) == Double.MaxValue)
    },
    test("read after close returns sentinel (readDouble)") {
      val source = Reader.fromChunk(Chunk(1.0, 2.0))
      val p      = SyncInterpreter(source)
      p.close()
      assertTrue(readDoubleValue(p, Double.MaxValue) == Double.MaxValue)
    },
    test("read after close returns sentinel (generic read)") {
      val source = Reader.fromIterable(List("a", "b"))
      val p      = SyncInterpreter(source)
      p.close()
      val sentinel = new AnyRef
      assertTrue(p.read(sentinel).asInstanceOf[AnyRef] eq sentinel)
    },
    test("double close is idempotent") {
      val source = Reader.fromRange(0 until 5)
      val p      = SyncInterpreter(source)
      p.close()
      assertTrue(p.isClosed)
      p.close() // should not throw
      assertTrue(p.isClosed)
    },
    test("double close replays a failure without closing the source twice") {
      val closeFailure  = new RuntimeException("close")
      var closeAttempts = 0
      val source        = new Reader.SyncReader[Int] {
        def isClosed: Boolean                 = false
        def read[A1 >: Int](sentinel: A1): A1 = sentinel
        def close(): Unit                     = {
          closeAttempts += 1
          throw closeFailure
        }
      }
      val p = SyncInterpreter(source)

      val first = try { p.close(); null }
      catch { case t: Throwable => t }
      val second = try { p.close(); null }
      catch { case t: Throwable => t }

      assertTrue(first eq closeFailure, second eq closeFailure, closeAttempts == 1, p.isClosed)
    },
    test("successful reset after close reopens and starts a fresh exact-once close cycle") {
      var closes = 0
      val source = new Reader.SyncReader[Int] {
        private var next                      = 0
        override def jvmType: JvmType         = JvmType.Int
        def isClosed: Boolean                 = false
        def read[A1 >: Int](sentinel: A1): A1 =
          if (next < 2) { val value = next; next += 1; value }
          else sentinel
        override def readInt(sentinel: Long)(implicit ev: Int <:< Int): Long =
          if (next < 2) { val value = next; next += 1; value.toLong }
          else sentinel
        def close(): Unit          = closes += 1
        override def reset(): Unit = next = 0
      }
      val p = SyncInterpreter(source)

      p.close()
      p.close()
      p.reset()
      val values = drainInts(p)
      p.close()
      p.close()

      assertTrue(values == List(0, 1), closes == 2, p.isClosed)
    },
    test("failed root reset keeps the interpreter closed and preserves the close cycle") {
      val resetFailure = new RuntimeException("reset")
      var closes       = 0
      var resets       = 0
      val source       = new Reader.SyncReader[Int] {
        def isClosed: Boolean                 = false
        def read[A1 >: Int](sentinel: A1): A1 = 1
        def close(): Unit                     = closes += 1
        override def reset(): Unit            = { resets += 1; throw resetFailure }
      }
      val p = SyncInterpreter(source)
      p.close()

      val caught = scala.util.Try(p.reset()).failed.toOption.orNull
      val value  = scala.util.Try(readIntValue(p, Long.MinValue)).failed.toOption.orNull
      val replay = scala.util.Try(p.close()).failed.toOption.orNull

      assertTrue(
        caught eq resetFailure,
        value eq resetFailure,
        replay eq resetFailure,
        closes == 2,
        resets == 1,
        p.isClosed
      )
    },
    test("reset aggregates nested cleanup before root reset failure") {
      val cleanupFailure = new RuntimeException("nested-close")
      val resetFailure   = new RuntimeException("root-reset")
      val root           = new Reader.SyncReader[Int] {
        def isClosed: Boolean                 = false
        def read[A1 >: Int](sentinel: A1): A1 = sentinel
        def close(): Unit                     = ()
        override def reset(): Unit            = throw resetFailure
      }
      val nested = new Reader.SyncReader[Int] {
        def isClosed: Boolean                 = false
        def read[A1 >: Int](sentinel: A1): A1 = sentinel
        def close(): Unit                     = throw cleanupFailure
      }
      val p = SyncInterpreter(root)
      p.appendRead(nested)

      val caught = scala.util.Try(p.reset()).failed.toOption.orNull

      assertTrue(caught eq cleanupFailure, caught.getSuppressed.toList == List(resetFailure))
    },
    test("close preserves source failure order and is reentrant") {
      val firstFailure       = new RuntimeException("first")
      val secondFailure      = new RuntimeException("second")
      var firstAttempts      = 0
      var secondAttempts     = 0
      var p: SyncInterpreter = null
      val first              = new Reader.SyncReader[Int] {
        def isClosed: Boolean                 = false
        def read[A1 >: Int](sentinel: A1): A1 = sentinel
        def close(): Unit                     = {
          firstAttempts += 1
          p.close()
          throw firstFailure
        }
      }
      val second = new Reader.SyncReader[Int] {
        def isClosed: Boolean                 = false
        def read[A1 >: Int](sentinel: A1): A1 = sentinel
        def close(): Unit                     = {
          secondAttempts += 1
          throw secondFailure
        }
      }
      p = SyncInterpreter(first)
      p.appendRead(second)

      val caught = try { p.close(); null }
      catch { case t: Throwable => t }

      assertTrue(
        caught eq firstFailure,
        caught.getSuppressed.toList == List(secondFailure),
        firstAttempts == 1,
        secondAttempts == 1,
        p.isClosed
      )
    },
    test("partial read then close then read returns sentinel") {
      val source = Reader.fromRange(0 until 5)
      val p      = SyncInterpreter(source)
      val first  = readIntValue(p, Long.MinValue)
      p.close()
      val afterClose = readIntValue(p, Long.MinValue)
      assertTrue(first == 0L && afterClose == Long.MinValue)
    }
  )

  // =========================================================================
  //  23. skip(n)
  // =========================================================================

  val skipSuite = suite("skip")(
    test("skip 0 elements, read all") {
      val source = Reader.fromRange(0 until 5)
      val p      = SyncInterpreter(source)
      p.skip(0)
      val result = drainInts(p)
      assertTrue(result == List(0, 1, 2, 3, 4))
    },
    test("skip 3 elements from 5-element source") {
      val source = Reader.fromRange(0 until 5)
      val p      = SyncInterpreter(source)
      p.skip(3)
      val result = drainInts(p)
      assertTrue(result == List(3, 4))
    },
    test("skip all elements returns sentinel") {
      val source = Reader.fromRange(0 until 5)
      val p      = SyncInterpreter(source)
      p.skip(5)
      assertTrue(readIntValue(p, Long.MinValue) == Long.MinValue)
    },
    test("skip more than available returns sentinel") {
      val source = Reader.fromRange(0 until 3)
      val p      = SyncInterpreter(source)
      p.skip(100)
      assertTrue(readIntValue(p, Long.MinValue) == Long.MinValue)
    },
    test("skip on pipeline with map") {
      val source = Reader.fromRange(0 until 10)
      val p      = SyncInterpreter(source)
      p.addMap[Int, Int](LANE_I, OUT_I)((_: Int) * 10)
      p.skip(5)
      val result = drainInts(p)
      // Elements after skip: 50, 60, 70, 80, 90
      assertTrue(result == List(50, 60, 70, 80, 90))
    },
    test("skip on pipeline with filter") {
      val source = Reader.fromRange(0 until 20)
      val p      = SyncInterpreter(source)
      p.addFilter[Int](LANE_I)((_: Int) % 2 == 0)
      p.skip(3)
      // After filter: 0,2,4,6,8,10,12,14,16,18; skip 3 → 6,8,10,12,14,16,18
      val result = drainInts(p)
      assertTrue(result == List(6, 8, 10, 12, 14, 16, 18))
    },
    test("skip on Long pipeline") {
      val source = Reader.fromChunk(Chunk(10L, 20L, 30L, 40L, 50L))
      val p      = SyncInterpreter(source)
      p.skip(2)
      val result = drainLongs(p)
      assertTrue(result == List(30L, 40L, 50L))
    },
    test("skip on Float pipeline") {
      val source = Reader.fromChunk(Chunk(1.0f, 2.0f, 3.0f, 4.0f))
      val p      = SyncInterpreter(source)
      p.skip(1)
      val result = drainFloats(p)
      assertTrue(result == List(2.0f, 3.0f, 4.0f))
    },
    test("skip on Double pipeline") {
      val source = Reader.fromChunk(Chunk(1.0, 2.0, 3.0, 4.0))
      val p      = SyncInterpreter(source)
      p.skip(2)
      val result = drainDoubles(p)
      assertTrue(result == List(3.0, 4.0))
    },
    test("skip on AnyRef pipeline") {
      val source = Reader.fromIterable(List("a", "b", "c", "d"))
      val p      = SyncInterpreter(source)
      p.skip(2)
      val result = drainGeneric(p)
      assertTrue(result == List("c", "d"))
    }
  )

  // =========================================================================
  //  24. readable()
  // =========================================================================

  val readableSuite = suite("readable")(
    test("readable is true before reading from non-empty pipeline") {
      val source = Reader.fromRange(0 until 5)
      val p      = SyncInterpreter(source)
      assertTrue(p.readable())
    },
    test("readable is false after close") {
      val source = Reader.fromRange(0 until 5)
      val p      = SyncInterpreter(source)
      p.close()
      assertTrue(!p.readable())
    },
    test("readable on empty source") {
      // SyncInterpreter inherits default readable = !isClosed
      // An empty source pipeline is not "closed" until close() is called
      val source = Reader.fromRange(0 until 0)
      val p      = SyncInterpreter(source)
      // The pipeline itself is not closed, but the source is exhausted
      // readable() returns !isClosed which is true (pipeline not closed yet)
      assertTrue(!p.isClosed)
    },
    test("explicitly closed controls fail with the normalized IOException contract") {
      val p = SyncInterpreter(Reader.fromRange(0 until 3))
      p.close()
      val failures = List(
        scala.util.Try(p.setLimit(1L)).failed.toOption,
        scala.util.Try(p.setRepeat()).failed.toOption,
        scala.util.Try(p.setSkip(1L)).failed.toOption
      ).flatten
      assertTrue(
        failures.length == 3,
        failures.forall(_.isInstanceOf[java.io.IOException]),
        failures.forall(_.getMessage == "Reader is closed")
      )
    },
    test("exhaustion remains distinct from explicit close for controls") {
      val p = SyncInterpreter(Reader.singleInt(0))
      assertTrue(
        readIntValue(p, -1L) == 0L,
        readIntValue(p, -1L) == -1L,
        p.isClosed,
        !p.readable(),
        p.setRepeat(),
        !p.isClosed,
        readIntValue(p, -1L) == 0L
      )
    },
    test("root EOF is sticky and does not pull the source again") {
      var pulls  = 0
      val source = new Reader.SyncReader[Int] {
        override def jvmType: JvmType         = JvmType.Int
        def isClosed: Boolean                 = false
        def read[A1 >: Int](sentinel: A1): A1 = {
          pulls += 1
          sentinel
        }
        override def readInt(sentinel: Long)(implicit ev: Int <:< Int): Long = {
          pulls += 1
          sentinel
        }
        def close(): Unit = ()
      }
      val p = SyncInterpreter(source)
      assertTrue(
        readIntValue(p, -11L) == -11L,
        readIntValue(p, -22L) == -22L,
        p.read[Any](EndOfStream).asInstanceOf[AnyRef] eq EndOfStream,
        pulls == 1,
        p.isClosed,
        !p.readable()
      )
    },
    test("successful reset reopens sticky exhaustion") {
      val p = SyncInterpreter(Reader.singleInt(3))
      assertTrue(readIntValue(p, -1L) == 3L, readIntValue(p, -1L) == -1L, p.isClosed)
      p.reset()
      assertTrue(!p.isClosed, readIntValue(p, -1L) == 3L)
    }
  )

  // =========================================================================
  //  25. jvmType for all lane types
  // =========================================================================

  val jvmTypeSuite = suite("jvmType")(
    test("Int pipeline has JvmType.Int") {
      val p = SyncInterpreter(Reader.fromRange(0 until 1))
      assertTrue(p.jvmType == JvmType.Int)
    },
    test("Long pipeline has JvmType.Long") {
      val p = SyncInterpreter(Reader.fromChunk(Chunk(1L)))
      assertTrue(p.jvmType == JvmType.Long)
    },
    test("Float pipeline has JvmType.Float") {
      val p = SyncInterpreter(Reader.fromChunk(Chunk(1.0f)))
      assertTrue(p.jvmType == JvmType.Float)
    },
    test("Double pipeline has JvmType.Double") {
      val p = SyncInterpreter(Reader.fromChunk(Chunk(1.0)))
      assertTrue(p.jvmType == JvmType.Double)
    },
    test("AnyRef pipeline has JvmType.AnyRef") {
      val p = SyncInterpreter(Reader.fromIterable(List("hello")))
      assertTrue(p.jvmType == JvmType.AnyRef)
    },
    test("jvmType changes after cross-type map Int→Long") {
      val p      = SyncInterpreter(Reader.fromRange(0 until 1))
      val before = p.jvmType
      p.addMap[Int, Long](LANE_I, OUT_L)((_: Int).toLong)
      val after = p.jvmType
      assertTrue(before == JvmType.Int && after == JvmType.Long)
    },
    test("jvmType changes after cross-type map Int→Double") {
      val p = SyncInterpreter(Reader.fromRange(0 until 1))
      p.addMap[Int, Double](LANE_I, OUT_D)((_: Int).toDouble)
      assertTrue(p.jvmType == JvmType.Double)
    },
    test("jvmType changes after cross-type map Int→Float") {
      val p = SyncInterpreter(Reader.fromRange(0 until 1))
      p.addMap[Int, Float](LANE_I, OUT_F)((_: Int).toFloat)
      assertTrue(p.jvmType == JvmType.Float)
    },
    test("jvmType changes after cross-type map Int→AnyRef") {
      val p = SyncInterpreter(Reader.fromRange(0 until 1))
      p.addMap[Int, AnyRef](LANE_I, OUT_R)((i: Int) => s"v$i")
      assertTrue(p.jvmType == JvmType.AnyRef)
    },
    test("jvmType is AnyRef after close") {
      val p = SyncInterpreter(Reader.fromRange(0 until 1))
      p.close()
      assertTrue(p.jvmType == JvmType.AnyRef)
    }
  )

  // =========================================================================
  //  26. Specialized read methods
  // =========================================================================

  val specializedReadSuite = suite("Specialized read methods")(
    test("readInt on Int pipeline returns values without boxing") {
      val p  = SyncInterpreter(Reader.fromRange(0 until 3))
      val v1 = readIntValue(p, Long.MinValue)
      val v2 = readIntValue(p, Long.MinValue)
      val v3 = readIntValue(p, Long.MinValue)
      val v4 = readIntValue(p, Long.MinValue)
      assertTrue(v1 == 0L && v2 == 1L && v3 == 2L && v4 == Long.MinValue)
    },
    test("readLong on Long pipeline returns values without boxing") {
      val p  = SyncInterpreter(Reader.fromChunk(Chunk(100L, 200L)))
      val v1 = readLongValue(p, Long.MaxValue)
      val v2 = readLongValue(p, Long.MaxValue)
      val v3 = readLongValue(p, Long.MaxValue)
      assertTrue(v1 == 100L && v2 == 200L && v3 == Long.MaxValue)
    },
    test("readFloat on Float pipeline returns values without boxing") {
      val p  = SyncInterpreter(Reader.fromChunk(Chunk(1.5f, 2.5f)))
      val v1 = readFloatValue(p, Double.MaxValue)
      val v2 = readFloatValue(p, Double.MaxValue)
      val v3 = readFloatValue(p, Double.MaxValue)
      assertTrue(v1 == 1.5f.toDouble && v2 == 2.5f.toDouble && v3 == Double.MaxValue)
    },
    test("readDouble on Double pipeline returns values without boxing") {
      val p  = SyncInterpreter(Reader.fromChunk(Chunk(3.14, 2.71)))
      val v1 = readDoubleValue(p, Double.MaxValue)
      val v2 = readDoubleValue(p, Double.MaxValue)
      val v3 = readDoubleValue(p, Double.MaxValue)
      assertTrue(v1 == 3.14 && v2 == 2.71 && v3 == Double.MaxValue)
    },
    test("read[Any] on AnyRef pipeline returns boxed values") {
      val p        = SyncInterpreter(Reader.fromIterable(List("hello", "world")))
      val sentinel = new AnyRef
      val v1       = p.read(sentinel)
      val v2       = p.read(sentinel)
      val v3       = p.read(sentinel)
      assertTrue(v1 == "hello" && v2 == "world" && (v3.asInstanceOf[AnyRef] eq sentinel))
    },
    test("readInt with map on Int pipeline") {
      val p = SyncInterpreter(Reader.fromRange(0 until 3))
      p.addMap[Int, Int](LANE_I, OUT_I)((_: Int) + 100)
      val v1 = readIntValue(p, Long.MinValue)
      val v2 = readIntValue(p, Long.MinValue)
      val v3 = readIntValue(p, Long.MinValue)
      val v4 = readIntValue(p, Long.MinValue)
      assertTrue(v1 == 100L && v2 == 101L && v3 == 102L && v4 == Long.MinValue)
    },
    test("readLong with map on Long pipeline") {
      val p = SyncInterpreter(Reader.fromChunk(Chunk(1L, 2L, 3L)))
      p.addMap[Long, Long](LANE_L, OUT_L)((_: Long) * 10L)
      val v1 = readLongValue(p, Long.MaxValue)
      val v2 = readLongValue(p, Long.MaxValue)
      val v3 = readLongValue(p, Long.MaxValue)
      val v4 = readLongValue(p, Long.MaxValue)
      assertTrue(v1 == 10L && v2 == 20L && v3 == 30L && v4 == Long.MaxValue)
    },
    test("readFloat with map on Float pipeline") {
      val p = SyncInterpreter(Reader.fromChunk(Chunk(1.0f, 2.0f)))
      p.addMap[Float, Float](LANE_F, OUT_F)((_: Float) + 0.5f)
      val v1 = readFloatValue(p, Double.MaxValue)
      val v2 = readFloatValue(p, Double.MaxValue)
      val v3 = readFloatValue(p, Double.MaxValue)
      assertTrue(v1 == 1.5f.toDouble && v2 == 2.5f.toDouble && v3 == Double.MaxValue)
    },
    test("readDouble with map on Double pipeline") {
      val p = SyncInterpreter(Reader.fromChunk(Chunk(1.0, 2.0)))
      p.addMap[Double, Double](LANE_D, OUT_D)((_: Double) + 0.1)
      val v1 = readDoubleValue(p, Double.MaxValue)
      val v2 = readDoubleValue(p, Double.MaxValue)
      val v3 = readDoubleValue(p, Double.MaxValue)
      assertTrue(v1 == 1.1 && v2 == 2.1 && v3 == Double.MaxValue)
    },
    test("readInt with cross-type map Int→Long emits in Long lane via readLong") {
      val p = SyncInterpreter(Reader.fromRange(0 until 3))
      p.addMap[Int, Long](LANE_I, OUT_L)((_: Int).toLong * 100L)
      val v1 = readLongValue(p, Long.MaxValue)
      val v2 = readLongValue(p, Long.MaxValue)
      val v3 = readLongValue(p, Long.MaxValue)
      val v4 = readLongValue(p, Long.MaxValue)
      assertTrue(v1 == 0L && v2 == 100L && v3 == 200L && v4 == Long.MaxValue)
    }
  )

  // =========================================================================
  //  27. bridgeTag and bridgeFn static helpers
  // =========================================================================

  val bridgeHelperSuite = suite("bridgeTag and bridgeFn")(
    test("bridgeTag for Int→Long") {
      assertTrue(SyncInterpreter.bridgeTag(LANE_I, LANE_L) == OpTag.mapTag(LANE_I, LANE_L))
    },
    test("bridgeTag for Int→Float") {
      assertTrue(SyncInterpreter.bridgeTag(LANE_I, LANE_F) == OpTag.mapTag(LANE_I, LANE_F))
    },
    test("bridgeTag for Int→Double") {
      assertTrue(SyncInterpreter.bridgeTag(LANE_I, LANE_D) == OpTag.mapTag(LANE_I, LANE_D))
    },
    test("bridgeTag for Int→Ref") {
      assertTrue(SyncInterpreter.bridgeTag(LANE_I, LANE_R) == OpTag.mapTag(LANE_I, LANE_R))
    },
    test("bridgeTag for Long→Int") {
      assertTrue(SyncInterpreter.bridgeTag(LANE_L, LANE_I) == OpTag.mapTag(LANE_L, LANE_I))
    },
    test("bridgeTag for Long→Double") {
      assertTrue(SyncInterpreter.bridgeTag(LANE_L, LANE_D) == OpTag.mapTag(LANE_L, LANE_D))
    },
    test("bridgeTag for Double→Int") {
      assertTrue(SyncInterpreter.bridgeTag(LANE_D, LANE_I) == OpTag.mapTag(LANE_D, LANE_I))
    },
    test("bridgeTag for Ref→Int") {
      assertTrue(SyncInterpreter.bridgeTag(LANE_R, LANE_I) == OpTag.mapTag(LANE_R, LANE_I))
    },
    test("bridgeFn Int→Long converts correctly") {
      val fn = SyncInterpreter.bridgeFn(LANE_I, LANE_L).asInstanceOf[Int => Long]
      assertTrue(fn(42) == 42L && fn(-1) == -1L && fn(0) == 0L)
    },
    test("bridgeFn Int→Float converts correctly") {
      val fn = SyncInterpreter.bridgeFn(LANE_I, LANE_F).asInstanceOf[Int => Float]
      assertTrue(fn(42) == 42.0f && fn(0) == 0.0f)
    },
    test("bridgeFn Int→Double converts correctly") {
      val fn = SyncInterpreter.bridgeFn(LANE_I, LANE_D).asInstanceOf[Int => Double]
      assertTrue(fn(42) == 42.0 && fn(0) == 0.0)
    },
    test("bridgeFn Int→Ref boxes correctly") {
      val fn = SyncInterpreter.bridgeFn(LANE_I, LANE_R).asInstanceOf[Int => AnyRef]
      assertTrue(fn(42) == Int.box(42))
    },
    test("bridgeFn Long→Int converts correctly") {
      val fn = SyncInterpreter.bridgeFn(LANE_L, LANE_I).asInstanceOf[Long => Int]
      assertTrue(fn(42L) == 42 && fn(0L) == 0)
    },
    test("bridgeFn Long→Float converts correctly") {
      val fn = SyncInterpreter.bridgeFn(LANE_L, LANE_F).asInstanceOf[Long => Float]
      assertTrue(fn(42L) == 42.0f)
    },
    test("bridgeFn Long→Double converts correctly") {
      val fn = SyncInterpreter.bridgeFn(LANE_L, LANE_D).asInstanceOf[Long => Double]
      assertTrue(fn(42L) == 42.0)
    },
    test("bridgeFn Long→Ref boxes correctly") {
      val fn = SyncInterpreter.bridgeFn(LANE_L, LANE_R).asInstanceOf[Long => AnyRef]
      assertTrue(fn(42L) == Long.box(42L))
    },
    test("bridgeFn Float→Int converts correctly") {
      val fn = SyncInterpreter.bridgeFn(LANE_F, LANE_I).asInstanceOf[Float => Int]
      assertTrue(fn(42.9f) == 42)
    },
    test("bridgeFn Float→Long converts correctly") {
      val fn = SyncInterpreter.bridgeFn(LANE_F, LANE_L).asInstanceOf[Float => Long]
      assertTrue(fn(42.0f) == 42L)
    },
    test("bridgeFn Float→Double converts correctly") {
      val fn = SyncInterpreter.bridgeFn(LANE_F, LANE_D).asInstanceOf[Float => Double]
      assertTrue(fn(1.5f) == 1.5f.toDouble)
    },
    test("bridgeFn Float→Ref boxes correctly") {
      val fn = SyncInterpreter.bridgeFn(LANE_F, LANE_R).asInstanceOf[Float => AnyRef]
      assertTrue(fn(1.5f) == Float.box(1.5f))
    },
    test("bridgeFn Double→Int converts correctly") {
      val fn = SyncInterpreter.bridgeFn(LANE_D, LANE_I).asInstanceOf[Double => Int]
      assertTrue(fn(42.9) == 42)
    },
    test("bridgeFn Double→Long converts correctly") {
      val fn = SyncInterpreter.bridgeFn(LANE_D, LANE_L).asInstanceOf[Double => Long]
      assertTrue(fn(42.0) == 42L)
    },
    test("bridgeFn Double→Float converts correctly") {
      val fn = SyncInterpreter.bridgeFn(LANE_D, LANE_F).asInstanceOf[Double => Float]
      assertTrue(fn(1.5) == 1.5f)
    },
    test("bridgeFn Double→Ref boxes correctly") {
      val fn = SyncInterpreter.bridgeFn(LANE_D, LANE_R).asInstanceOf[Double => AnyRef]
      assertTrue(fn(1.5) == Double.box(1.5))
    },
    test("bridgeFn Ref→Int converts via Number.intValue") {
      val fn = SyncInterpreter.bridgeFn(LANE_R, LANE_I).asInstanceOf[AnyRef => Int]
      assertTrue(fn(Int.box(42)) == 42)
    },
    test("bridgeFn Ref→Long converts via Number.longValue") {
      val fn = SyncInterpreter.bridgeFn(LANE_R, LANE_L).asInstanceOf[AnyRef => Long]
      assertTrue(fn(Long.box(42L)) == 42L)
    },
    test("bridgeFn Ref→Float converts via Number.floatValue") {
      val fn = SyncInterpreter.bridgeFn(LANE_R, LANE_F).asInstanceOf[AnyRef => Float]
      assertTrue(fn(Float.box(1.5f)) == 1.5f)
    },
    test("bridgeFn Ref→Double converts via Number.doubleValue") {
      val fn = SyncInterpreter.bridgeFn(LANE_R, LANE_D).asInstanceOf[AnyRef => Double]
      assertTrue(fn(Double.box(1.5)) == 1.5)
    }
  )

  // =========================================================================
  //  28. Large data tests
  // =========================================================================

  val largeDataSuite = suite("Large data")(
    test("1000-element source with map") {
      val source = Reader.fromRange(0 until 1000)
      val p      = SyncInterpreter(source)
      p.addMap[Int, Int](LANE_I, OUT_I)((_: Int) + 1)
      val result = drainInts(p)
      assertTrue(result == (0 until 1000).map(_ + 1).toList)
    },
    test("10000-element source with filter") {
      val source = Reader.fromRange(0 until 10000)
      val p      = SyncInterpreter(source)
      p.addFilter[Int](LANE_I)((_: Int) % 100 == 0)
      val result = drainInts(p)
      assertTrue(result == (0 until 10000).filter(_ % 100 == 0).toList)
    },
    test("large source with map + filter + map chain") {
      val source = Reader.fromRange(0 until 5000)
      val p      = SyncInterpreter(source)
      p.addMap[Int, Int](LANE_I, OUT_I)((_: Int) * 2)
      p.addFilter[Int](LANE_I)((_: Int) % 3 == 0)
      p.addMap[Int, Int](LANE_I, OUT_I)((_: Int) + 1)
      val result   = drainInts(p)
      val expected = (0 until 5000).map(_ * 2).filter(_ % 3 == 0).map(_ + 1).toList
      assertTrue(result == expected)
    },
    test("large Long source") {
      val data   = Chunk.fromIterable((0L until 1000L).toList)
      val source = Reader.fromChunk(data)
      val p      = SyncInterpreter(source)
      p.addMap[Long, Long](LANE_L, OUT_L)((_: Long) + 1L)
      val result = drainLongs(p)
      assertTrue(result == (0L until 1000L).map(_ + 1L).toList)
    },
    test("large flatMap: each element expands to 10") {
      val source                = Reader.fromRange(0 until 100)
      val p                     = SyncInterpreter(source)
      val pushFn: Int => AnyRef = (i: Int) => {
        Stream.fromRange(0 until 10).map((_: Int) + i * 10).asInstanceOf[AnyRef]
      }
      p.addPush[Int](LANE_I)(pushFn)
      val result = drainInts(p)
      assertTrue(result.length == 1000) &&
      assertTrue(result.head == 0) &&
      assertTrue(result(10) == 10) &&
      assertTrue(result.last == 999)
    }
  )

  // =========================================================================
  //  29. Edge cases
  // =========================================================================

  val edgeCaseSuite = suite("Edge cases")(
    test("pipeline with only filters (no maps)") {
      val source = Reader.fromRange(0 until 20)
      val p      = SyncInterpreter(source)
      p.addFilter[Int](LANE_I)((_: Int) > 5)
      p.addFilter[Int](LANE_I)((_: Int) < 15)
      p.addFilter[Int](LANE_I)((_: Int) % 2 == 0)
      val result = drainInts(p)
      assertTrue(result == List(6, 8, 10, 12, 14))
    },
    test("pipeline with only maps (no filters)") {
      val source = Reader.fromRange(0 until 5)
      val p      = SyncInterpreter(source)
      p.addMap[Int, Int](LANE_I, OUT_I)((_: Int) + 1)
      p.addMap[Int, Int](LANE_I, OUT_I)((_: Int) * 2)
      p.addMap[Int, Int](LANE_I, OUT_I)((_: Int) + 100)
      val result   = drainInts(p)
      val expected = (0 until 5).map(i => ((i + 1) * 2) + 100).toList
      assertTrue(result == expected)
    },
    test("single-element source with map") {
      val source = Reader.fromChunk(Chunk(42))
      val p      = SyncInterpreter(source)
      p.addMap[Int, Int](LANE_I, OUT_I)((_: Int) * 2)
      val result = drainInts(p)
      assertTrue(result == List(84))
    },
    test("single-element source with filter pass") {
      val source = Reader.fromChunk(Chunk(42))
      val p      = SyncInterpreter(source)
      p.addFilter[Int](LANE_I)((_: Int) > 0)
      val result = drainInts(p)
      assertTrue(result == List(42))
    },
    test("single-element source with filter reject") {
      val source = Reader.fromChunk(Chunk(42))
      val p      = SyncInterpreter(source)
      p.addFilter[Int](LANE_I)((_: Int) < 0)
      val result = drainInts(p)
      assertTrue(result == List())
    },
    test("very long pipeline: 200 chained map ops") {
      val source = Reader.fromRange(0 until 3)
      val p      = SyncInterpreter(source)
      var i      = 0; while (i < 200) { p.addMap[Int, Int](LANE_I, OUT_I)((_: Int) + 1); i += 1 }
      val result = drainInts(p)
      assertTrue(result == List(200, 201, 202))
    },
    test("pipeline with alternating map-filter-map-filter pattern") {
      val source = Reader.fromRange(0 until 100)
      val p      = SyncInterpreter(source)
      p.addMap[Int, Int](LANE_I, OUT_I)((_: Int) + 1) // 1..100
      p.addFilter[Int](LANE_I)((_: Int) % 2 == 0) // even: 2,4,...,100
      p.addMap[Int, Int](LANE_I, OUT_I)((_: Int) / 2) // 1,2,...,50
      p.addFilter[Int](LANE_I)((_: Int) <= 5) // 1,2,3,4,5
      val result = drainInts(p)
      assertTrue(result == List(1, 2, 3, 4, 5))
    },
    test("flatMap that expands some elements to 0 and others to many") {
      val source                = Reader.fromRange(0 until 5)
      val pushFn: Int => AnyRef = (i: Int) => {
        Stream.fromRange(0 until (i % 3)).asInstanceOf[AnyRef]
      }
      val p = SyncInterpreter(source)
      p.addPush[Int](LANE_I)(pushFn)
      val result = drainInts(p)
      // 0 % 3 = 0 → empty
      // 1 % 3 = 1 → [0]
      // 2 % 3 = 2 → [0,1]
      // 3 % 3 = 0 → empty
      // 4 % 3 = 1 → [0]
      assertTrue(result == List(0, 0, 1, 0))
    },
    test("pipeline with filter that removes ALL Long elements") {
      val source = Reader.fromChunk(Chunk(1L, 2L, 3L))
      val p      = SyncInterpreter(source)
      p.addFilter[Long](LANE_L)((_: Long) > 100L)
      val result = drainLongs(p)
      assertTrue(result == List())
    },
    test("pipeline with filter that removes ALL AnyRef elements") {
      val source = Reader.fromIterable(List("a", "b", "c"))
      val p      = SyncInterpreter(source)
      p.addFilter[AnyRef](LANE_R)((s: AnyRef) => s.asInstanceOf[String].length > 10)
      val result = drainGeneric(p)
      assertTrue(result == List())
    },
    test("cross-lane pipeline: Int → Long → Double via chained maps") {
      val source = Reader.fromRange(0 until 3)
      val p      = SyncInterpreter(source)
      p.addMap[Int, Long](LANE_I, OUT_L)((_: Int).toLong)
      p.addMap[Long, Double](LANE_L, OUT_D)((_: Long).toDouble + 0.5)
      val result = drainDoubles(p)
      assertTrue(result == List(0.5, 1.5, 2.5))
    },
    test("cross-lane pipeline: Double → Int → AnyRef via chained maps") {
      val source = Reader.fromChunk(Chunk(1.0, 2.0, 3.0))
      val p      = SyncInterpreter(source)
      p.addMap[Double, Int](LANE_D, OUT_I)((_: Double).toInt)
      p.addMap[Int, AnyRef](LANE_I, OUT_R)((i: Int) => s"val=$i")
      val result = drainGeneric(p)
      assertTrue(result == List("val=1", "val=2", "val=3"))
    },
    test("pipeline with Long filter + Long map") {
      val source = Reader.fromChunk(Chunk(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L))
      val p      = SyncInterpreter(source)
      p.addFilter[Long](LANE_L)((_: Long) % 2L == 0L)
      p.addMap[Long, Long](LANE_L, OUT_L)((_: Long) * 100L)
      val result = drainLongs(p)
      assertTrue(result == List(200L, 400L, 600L, 800L, 1000L))
    },
    test("pipeline with Float filter") {
      val source = Reader.fromChunk(Chunk(1.0f, 2.0f, 3.0f, 4.0f, 5.0f))
      val p      = SyncInterpreter(source)
      p.addFilter[Float](LANE_F)((_: Float) > 2.5f)
      val result = drainFloats(p)
      assertTrue(result == List(3.0f, 4.0f, 5.0f))
    },
    test("pipeline with Double filter") {
      val source = Reader.fromChunk(Chunk(1.0, 2.0, 3.0, 4.0, 5.0))
      val p      = SyncInterpreter(source)
      p.addFilter[Double](LANE_D)((_: Double) > 3.0)
      val result = drainDoubles(p)
      assertTrue(result == List(4.0, 5.0))
    },
    test("readByte on Int pipeline") {
      val source = Reader.fromChunk(Chunk(65, 66, 67))
      val p      = SyncInterpreter(source)
      val b1     = p.readByte()
      val b2     = p.readByte()
      val b3     = p.readByte()
      val b4     = p.readByte()
      assertTrue(b1 == 65 && b2 == 66 && b3 == 67 && b4 == -1)
    },
    test("compileInterpreter chains one pipeline into another") {
      // Build an inner stream that includes a map, compile into SyncInterpreter via fromStream
      val innerStream = Stream.fromRange(0 until 3).map((_: Int) + 100)
      val p           = SyncInterpreter.fromStream(innerStream)
      val result      = drainInts(p)
      assertTrue(result == List(100, 101, 102))
    },
    test("pipeline reading from another pipeline via flatMap") {
      // Outer pipeline source → flatMap → inner pipeline compiled from stream
      val source                = Reader.fromRange(0 until 3)
      val pushFn: Int => AnyRef = (i: Int) => {
        // Inner stream compiles to a Reader (possibly SyncInterpreter) at runtime
        Stream.fromRange(0 until 2).map((_: Int) + i * 100).asInstanceOf[AnyRef]
      }
      val p = SyncInterpreter(source)
      p.addPush[Int](LANE_I)(pushFn)
      val result = drainInts(p)
      // 0 → [0, 1], 1 → [100, 101], 2 → [200, 201]
      assertTrue(result == List(0, 1, 100, 101, 200, 201))
    }
  )
}
