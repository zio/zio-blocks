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
import zio.blocks.streams.internal._
import zio.blocks.streams.io.Reader
import zio.test._

/**
 * Tests for Interpreter V3: standalone stack machine with stage stack support.
 *
 * Covers:
 *   - Single map (all 5 input types)
 *   - Cross-type map (7 crossings)
 *   - Chained maps (5 and 100)
 *   - Filter ops (all 5 types)
 *   - Filter + map interleaved
 *   - (ArrayStack removed — inlined into Interpreter)
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
object InterpreterSpec extends StreamsBaseSpec {
  import Interpreter._
  import OpTag.{mapTag, filterTag, storageLaneOfMapTag}

  private def readIntValue(p: Interpreter, sentinel: Long): Long =
    p.asInstanceOf[Reader[Int]].readInt(sentinel)

  private def readLongValue(p: Interpreter, sentinel: Long): Long =
    p.asInstanceOf[Reader[Long]].readLong(sentinel)

  private def readFloatValue(p: Interpreter, sentinel: Double): Double =
    p.asInstanceOf[Reader[Float]].readFloat(sentinel)

  private def readDoubleValue(p: Interpreter, sentinel: Double): Double =
    p.asInstanceOf[Reader[Double]].readDouble(sentinel)

  def spec: Spec[TestEnvironment, Any] = suite("Interpreter")(
    singleMapSuite,
    crossTypeMapSuite,
    chainedMapsSuite,
    filterSuite,
    filterMapInterleavedSuite,
    // ArrayStack suite removed — ArrayStack eliminated (inlined into Interpreter)
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
    crossTypeInterpreterSuite,
    depthBasedCompilationSuite,
    stackSafetySuite,
    constructionSuite,
    resetAndRereadSuite,
    closeBehaviorSuite,
    skipSuite,
    readableSuite,
    jvmTypeSuite,
    specializedReadSuite,
    bridgeHelperSuite,
    largeDataSuite,
    edgeCaseSuite,
    coverageSuite,
    regressionsSuite
  )

  // ---- drain helpers ----

  private def drainInts(p: Interpreter, sentinel: Long = Long.MinValue): List[Int] = {
    val buf = scala.collection.mutable.ListBuffer[Int]()
    var v   = readIntValue(p, sentinel)
    while (v != sentinel) {
      buf += v.toInt
      v = readIntValue(p, sentinel)
    }
    buf.toList
  }

  private def drainLongs(p: Interpreter, sentinel: Long = Long.MaxValue): List[Long] = {
    val buf = scala.collection.mutable.ListBuffer[Long]()
    var v   = readLongValue(p, sentinel)
    while (v != sentinel) {
      buf += v
      v = readLongValue(p, sentinel)
    }
    buf.toList
  }

  private def drainFloats(p: Interpreter, sentinel: Double = Double.MaxValue): List[Float] = {
    val buf = scala.collection.mutable.ListBuffer[Float]()
    var v   = readFloatValue(p, sentinel)
    while (v != sentinel) {
      buf += v.toFloat
      v = readFloatValue(p, sentinel)
    }
    buf.toList
  }

  private def drainDoubles(p: Interpreter, sentinel: Double = Double.MaxValue): List[Double] = {
    val buf = scala.collection.mutable.ListBuffer[Double]()
    var v   = readDoubleValue(p, sentinel)
    while (v != sentinel) {
      buf += v
      v = readDoubleValue(p, sentinel)
    }
    buf.toList
  }

  private def drainGeneric(p: Interpreter): List[Any] = {
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
      val p              = Interpreter(source)
      p.addMap[Int, Int](LANE_I, OUT_I)(fn)
      val result = drainInts(p)
      assertTrue(result == List(1, 2, 3, 4, 5))
    },
    test("Long source + MAP_LL (tag 8)") {
      val source           = Reader.fromChunk(Chunk(10L, 20L, 30L))
      val fn: Long => Long = _ + 1L
      val p                = Interpreter(source)
      p.addMap[Long, Long](LANE_L, OUT_L)(fn)
      val result = drainLongs(p)
      assertTrue(result == List(11L, 21L, 31L))
    },
    test("Float source + MAP_FF (tag 16)") {
      val source             = Reader.fromChunk(Chunk(1.0f, 2.0f, 3.0f))
      val fn: Float => Float = _ + 0.5f
      val p                  = Interpreter(source)
      p.addMap[Float, Float](LANE_F, OUT_F)(fn)
      val result = drainFloats(p)
      assertTrue(result == List(1.5f, 2.5f, 3.5f))
    },
    test("Double source + MAP_DD (tag 24)") {
      val source               = Reader.fromChunk(Chunk(1.0, 2.0, 3.0))
      val fn: Double => Double = _ * 2.0
      val p                    = Interpreter(source)
      p.addMap[Double, Double](LANE_D, OUT_D)(fn)
      val result = drainDoubles(p)
      assertTrue(result == List(2.0, 4.0, 6.0))
    },
    test("AnyRef source + MAP_RR (tag 34)") {
      val source               = Reader.fromIterable(List("hello", "world"))
      val fn: AnyRef => AnyRef = (s: AnyRef) => s.asInstanceOf[String].toUpperCase
      val p                    = Interpreter(source)
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
      val p               = Interpreter(source)
      p.addMap[Int, Long](LANE_I, OUT_L)(fn)
      val result = drainLongs(p)
      assertTrue(result == List(0L, 100L, 200L, 300L, 400L))
    },
    test("Int->Double (tag 3)") {
      val source            = Reader.fromRange(0 until 3)
      val fn: Int => Double = _.toDouble + 0.5
      val p                 = Interpreter(source)
      p.addMap[Int, Double](LANE_I, OUT_D)(fn)
      val result = drainDoubles(p)
      assertTrue(result == List(0.5, 1.5, 2.5))
    },
    test("Int->Float (tag 2)") {
      val source           = Reader.fromRange(0 until 4)
      val fn: Int => Float = _.toFloat
      val p                = Interpreter(source)
      p.addMap[Int, Float](LANE_I, OUT_F)(fn)
      val result = drainFloats(p)
      assertTrue(result == List(0.0f, 1.0f, 2.0f, 3.0f))
    },
    test("Int->AnyRef (tag 6)") {
      val source            = Reader.fromRange(0 until 3)
      val fn: Int => AnyRef = (i: Int) => s"v$i"
      val p                 = Interpreter(source)
      p.addMap[Int, AnyRef](LANE_I, OUT_R)(fn)
      val result = drainGeneric(p)
      assertTrue(result == List("v0", "v1", "v2"))
    },
    test("Long->Double (tag 10)") {
      val source             = Reader.fromChunk(Chunk(1L, 2L, 3L))
      val fn: Long => Double = _.toDouble
      val p                  = Interpreter(source)
      p.addMap[Long, Double](LANE_L, OUT_D)(fn)
      val result = drainDoubles(p)
      assertTrue(result == List(1.0, 2.0, 3.0))
    },
    test("AnyRef->Int (tag 28)") {
      val source            = Reader.fromIterable(List("hello", "ab", "x"))
      val fn: AnyRef => Int = (s: AnyRef) => s.asInstanceOf[String].length
      val p                 = Interpreter(source)
      p.addMap[AnyRef, Int](LANE_R, OUT_I)(fn)
      val result = drainInts(p)
      assertTrue(result == List(5, 2, 1))
    },
    test("Double->Int (tag 21)") {
      val source            = Reader.fromChunk(Chunk(1.9, 2.1, 3.7))
      val fn: Double => Int = _.toInt
      val p                 = Interpreter(source)
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
      val p              = Interpreter(source)
      var i              = 0; while (i < 5) { p.addMap[Int, Int](LANE_I, OUT_I)(fn); i += 1 }
      val result         = drainInts(p)
      val expected       = (0 until 10).map(_ + 5).toList
      assertTrue(result == expected)
    },
    test("100 chained MAP_II ops") {
      val source         = Reader.fromRange(0 until 5)
      val fn: Int => Int = _ + 1
      val p              = Interpreter(source)
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
      val p                    = Interpreter(source)
      p.addFilter[Int](LANE_I)(pred)
      val result = drainInts(p)
      assertTrue(result == List(0, 2, 4, 6, 8))
    },
    test("FILTER_L (tag 36) - Long") {
      val source                = Reader.fromChunk(Chunk(1L, 2L, 3L, 4L, 5L))
      val pred: Long => Boolean = _ > 3L
      val p                     = Interpreter(source)
      p.addFilter[Long](LANE_L)(pred)
      val result = drainLongs(p)
      assertTrue(result == List(4L, 5L))
    },
    test("FILTER_F (tag 37) - Float") {
      val source                 = Reader.fromChunk(Chunk(1.0f, 2.0f, 3.0f, 4.0f, 5.0f))
      val pred: Float => Boolean = _ > 2.5f
      val p                      = Interpreter(source)
      p.addFilter[Float](LANE_F)(pred)
      val result = drainFloats(p)
      assertTrue(result == List(3.0f, 4.0f, 5.0f))
    },
    test("FILTER_D (tag 38) - Double") {
      val source                  = Reader.fromChunk(Chunk(1.0, 2.0, 3.0, 4.0, 5.0))
      val pred: Double => Boolean = _ > 2.5
      val p                       = Interpreter(source)
      p.addFilter[Double](LANE_D)(pred)
      val result = drainDoubles(p)
      assertTrue(result == List(3.0, 4.0, 5.0))
    },
    test("FILTER_R (tag 39) - AnyRef") {
      val source                  = Reader.fromIterable(List("hello", "", "world", ""))
      val pred: AnyRef => Boolean = (s: AnyRef) => s.asInstanceOf[String].nonEmpty
      val p                       = Interpreter(source)
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
      val p                    = Interpreter(source)
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
      val p                    = Interpreter(source)
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
      val p                     = Interpreter(source)
      p.addMap[Int, Long](LANE_I, OUT_L)(fn1)
      p.addFilter[Long](LANE_L)(pred)
      p.addMap[Long, Long](LANE_L, OUT_L)(fn2)
      val result   = drainLongs(p)
      val expected = (0 until 10).map(_.toLong).filter(_ > 3L).map(_ * 10L).toList
      assertTrue(result == expected)
    }
  )

  // =========================================================================
  //  6. ArrayStack tests — REMOVED (ArrayStack eliminated; inlined into Interpreter)
  // =========================================================================

  // =========================================================================
  //  7. Helper function tests
  // =========================================================================

  val helperFunctionSuite = suite("Helper functions")(
    test("laneOf for all JvmType values") {
      assertTrue(
        laneOf(JvmType.Int) == LANE_I &&
          // Boolean is boxed in the ref lane so laneOf matches outLaneOf(Boolean)
          // == OUT_R; a mismatch read a boxed Boolean from LANE_I -> CCE (ITER-9).
          laneOf(JvmType.Boolean) == LANE_R &&
          laneOf(JvmType.Long) == LANE_L &&
          laneOf(JvmType.Float) == LANE_F &&
          laneOf(JvmType.Double) == LANE_D &&
          laneOf(JvmType.AnyRef) == LANE_R &&
          laneOf(JvmType.Byte) == LANE_R &&
          laneOf(JvmType.Short) == LANE_R &&
          laneOf(JvmType.Char) == LANE_R
      )
    },
    test("outLaneOf for all JvmType values") {
      assertTrue(
        outLaneOf(JvmType.Int) == OUT_I &&
          outLaneOf(JvmType.Long) == OUT_L &&
          outLaneOf(JvmType.Float) == OUT_F &&
          outLaneOf(JvmType.Double) == OUT_D &&
          outLaneOf(JvmType.Boolean) == OUT_R &&
          outLaneOf(JvmType.AnyRef) == OUT_R &&
          outLaneOf(JvmType.Byte) == OUT_R &&
          outLaneOf(JvmType.Short) == OUT_R &&
          outLaneOf(JvmType.Char) == OUT_R
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

  // =========================================================================
  //  8. PushOp: flatMap with single-element inner
  // =========================================================================

  val pushOpSingleElementSuite = suite("PushOp - single-element inner")(
    test("outer 0..4, each produces single element (i * 10)") {
      val source                = Reader.fromRange(0 until 5)
      val pushFn: Int => AnyRef = (i: Int) => {
        Stream.fromChunk(Chunk(i * 10)).asInstanceOf[AnyRef]
      }
      val p = Interpreter(source)
      p.addPush[Int](LANE_I, LANE_I)(pushFn)
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
      val p = Interpreter(source)
      p.addPush[Int](LANE_I, LANE_I)(pushFn)
      val result = drainInts(p)
      // 1 -> [0], 2 -> [0,1], 3 -> [0,1,2]
      assertTrue(result == List(0, 0, 1, 0, 1, 2))
    },
    test("outer 0..2, each produces 3 elements") {
      val source                = Reader.fromRange(0 until 3)
      val pushFn: Int => AnyRef = (i: Int) => {
        Stream.fromChunk(Chunk(i * 100, i * 100 + 1, i * 100 + 2)).asInstanceOf[AnyRef]
      }
      val p = Interpreter(source)
      p.addPush[Int](LANE_I, LANE_I)(pushFn)
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

      val p = Interpreter(source)
      p.addPush[Int](LANE_I, LANE_I)(outerPushFn)
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

      val p = Interpreter(source)
      p.addPush[Int](LANE_I, LANE_I)(pushFn)
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

      val p = Interpreter(source)
      p.addPush[Int](LANE_I, LANE_I)(pushFn)
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

      val p = Interpreter(source)
      p.addPush[Int](LANE_I, LANE_I)(pushFn)
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
      val p = Interpreter(source)
      p.addPush[Int](LANE_I, LANE_I)(pushFn)
      val result = drainInts(p)
      // 0 -> empty, 3 -> [0,1,2], 0 -> empty, 2 -> [0,1]
      assertTrue(result == List(0, 1, 2, 0, 1))
    },
    test("all inner streams are empty") {
      val source                = Reader.fromChunk(Chunk(0, 0, 0))
      val pushFn: Int => AnyRef = (i: Int) => {
        Stream.fromRange(0 until i).asInstanceOf[AnyRef]
      }
      val p = Interpreter(source)
      p.addPush[Int](LANE_I, LANE_I)(pushFn)
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
      val p      = Interpreter(source)
      val result = drainInts(p)
      assertTrue(result == List(0, 1, 2, 3, 4))
    },
    test("empty source returns sentinel immediately") {
      val source   = Reader.fromRange(0 until 0)
      val p        = Interpreter(source)
      val sentinel = Long.MinValue
      val v        = readIntValue(p, sentinel)
      assertTrue(v == sentinel)
    },
    test("sentinel returned when all elements consumed") {
      val source   = Reader.fromChunk(Chunk(42))
      val p        = Interpreter(source)
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

      val p = Interpreter(source)
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

      val p = Interpreter(source)
      p.addPush[Int](LANE_I, LANE_I)(pushFn)
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

      val p = Interpreter(source)
      p.addPush[Int](LANE_I, LANE_I)(outerPushFn)
      p.addPush[Int](LANE_I, LANE_I)(innerPushFn)
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

      val p = Interpreter(source)
      p.addMap[Int, Int](LANE_I, OUT_I)(mapF)
      p.addPush[Int](LANE_I, LANE_I)(pushFn)
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

      val p = Interpreter(source)
      p.addMap[Int, Int](LANE_I, OUT_I)(mapF)
      p.addPush[Int](LANE_I, LANE_I)(pushFn)
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

      val p = Interpreter(source)
      p.addMap[Int, Int](LANE_I, OUT_I)(mapF)
      p.addFilter[Int](LANE_I)(predP)
      p.addPush[Int](LANE_I, LANE_I)(pushFn)
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

      val p = Interpreter(source)
      p.addPush[Int](LANE_I, LANE_I)(pushG)
      p.addPush[Int](LANE_I, LANE_I)(pushH)

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

      val p = Interpreter(source)
      p.addPush[Int](LANE_I, LANE_I)(pushG)
      p.addMap[Int, Int](LANE_I, OUT_I)(mapF)
      p.addPush[Int](LANE_I, LANE_I)(pushH)

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
      val p = Interpreter(source)
      p.addPush[Int](LANE_I, LANE_I)(pushFn)
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
      val p = Interpreter(source)
      p.addPush[Int](LANE_I, LANE_I)(pushFn)
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

      val p = Interpreter(source)
      p.addMap[Int, Long](LANE_I, OUT_L)(mapIL)
      p.addPush[Long](LANE_L, LANE_I)(pushFn)

      val result = drainInts(p)
      // 0 -> 100L -> inner [100,101]
      // 1 -> 101L -> inner [101,102]
      // 2 -> 102L -> inner [102,103]
      assertTrue(result == List(100, 101, 101, 102, 102, 103))
    },
    test("filter rejecting ALL elements - empty result") {
      val source               = Reader.fromRange(0 until 10)
      val pred: Int => Boolean = _ > 100 // nothing passes
      val p                    = Interpreter(source)
      p.addFilter[Int](LANE_I)(pred)
      val result = drainInts(p)
      assertTrue(result == List())
    },
    test("filter rejecting MOST elements") {
      val source               = Reader.fromRange(0 until 1000)
      val pred: Int => Boolean = _ == 999
      val p                    = Interpreter(source)
      p.addFilter[Int](LANE_I)(pred)
      val result = drainInts(p)
      assertTrue(result == List(999))
    },
    test("100 chained maps followed by flatMap") {
      val source            = Reader.fromRange(0 until 3)
      val mapFn: Int => Int = _ + 1

      val p = Interpreter(source)
      (0 until 100).foreach(_ => p.addMap[Int, Int](LANE_I, OUT_I)(mapFn))

      val pushFn: Int => AnyRef = (i: Int) => {
        Stream.fromChunk(Chunk(i, i + 1)).asInstanceOf[AnyRef]
      }
      p.addPush[Int](LANE_I, LANE_I)(pushFn)

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

      val p = Interpreter(source)
      p.addPush[Int](LANE_I, LANE_I)(pushFn)
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
      val p      = Interpreter(source)
      assertTrue(p.outputType == JvmType.Int)
    },
    test("Int source with MAP_IL has PLong outputType") {
      val source          = Reader.fromRange(0 until 5)
      val fn: Int => Long = _.toLong
      val p               = Interpreter(source)
      p.addMap[Int, Long](LANE_I, OUT_L)(fn)
      assertTrue(p.outputType == JvmType.Long)
    },
    test("Long source with MAP_LD has PDouble outputType") {
      val source             = Reader.fromChunk(Chunk(1L, 2L))
      val fn: Long => Double = _.toDouble
      val p                  = Interpreter(source)
      p.addMap[Long, Double](LANE_L, OUT_D)(fn)
      assertTrue(p.outputType == JvmType.Double)
    }
  )

  // =========================================================================
  //  17. Cross-type pipeline tests — exercises type-crossing scenarios
  //      through Interpreter, especially PushOp with post-flatMap ops that
  //      change types.
  // =========================================================================

  val crossTypeInterpreterSuite = suite("Cross-type pipeline")(
    test("1. map Int→Long, drain via readLong") {
      val source          = Reader.fromRange(0 until 5)
      val fn: Int => Long = (i: Int) => i.toLong * 100L
      val p               = Interpreter(source)
      p.addMap[Int, Long](LANE_I, OUT_L)(fn)
      val result = drainLongs(p)
      assertTrue(result == List(0L, 100L, 200L, 300L, 400L))
    },
    test("2. map Int→Double, drain via readDouble") {
      val source            = Reader.fromRange(0 until 3)
      val fn: Int => Double = (i: Int) => i.toDouble + 0.5
      val p                 = Interpreter(source)
      p.addMap[Int, Double](LANE_I, OUT_D)(fn)
      val result = drainDoubles(p)
      assertTrue(result == List(0.5, 1.5, 2.5))
    },
    test("3. map Int→Float, drain via readFloat") {
      val source           = Reader.fromRange(0 until 3)
      val fn: Int => Float = (i: Int) => i.toFloat * 1.5f
      val p                = Interpreter(source)
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

      val p = Interpreter(source)
      p.addPush[Int](LANE_I, LANE_I)(pushFn)
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

      val p = Interpreter(source)
      p.addPush[Int](LANE_I, LANE_I)(pushFn)
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

      val p = Interpreter(source)
      p.addPush[Int](LANE_I, LANE_I)(pushFn)
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
      val p = Interpreter(source)
      p.addMap[Int, Long](LANE_I, OUT_L)(mapIL)
      p.addPush[Long](LANE_L, LANE_L)(pushFn)

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

      val p = Interpreter(source)
      p.addMap[Int, Long](LANE_I, OUT_L)(mapIL)
      p.addMap[Long, Double](LANE_L, OUT_D)(mapLD)

      val result = drainDoubles(p)
      assertTrue(result == List(0.0, 1.0, 2.0))
    },
    test("9. outputType after cross-type map Int→Long") {
      val source             = Reader.fromRange(0 until 1)
      val mapIL: Int => Long = (i: Int) => i.toLong

      val p = Interpreter(source)
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

      val p = Interpreter(source)
      p.addPush[Int](LANE_I, LANE_I)(pushFn)
      p.addMap[Int, Long](LANE_I, OUT_L)(mapIL)

      // In the flat pipeline, outputLane reflects the final output type (after outgoing ops).
      // MAP_IL goes to outgoing, so outputType becomes Long.
      assertTrue(p.outputType == JvmType.Long)
    }
  )

  // =========================================================================
  //  18. Depth-based compilation: shallow Reader decorations vs deep Interpreter
  // =========================================================================

  val depthBasedCompilationSuite = suite("Depth-based compilation")(
    test("shallow map compiles to MappedInt, not Interpreter") {
      val reader = Stream.range(0, 10).map(_ + 1).compile(0)
      assertTrue(reader.isInstanceOf[Reader.MappedInt]) &&
      assertTrue(!reader.isInstanceOf[Interpreter])
    },
    test("shallow filter compiles to FilteredInt, not Interpreter") {
      val reader = Stream.range(0, 10).filter(_ > 5).compile(0)
      assertTrue(reader.isInstanceOf[Reader.FilteredInt]) &&
      assertTrue(!reader.isInstanceOf[Interpreter])
    },
    test("shallow flatMap compiles to FlatMappedInt, not Interpreter") {
      val reader = Stream.range(0, 3).flatMap(i => Stream.range(0, i)).compile(0)
      assertTrue(reader.isInstanceOf[Reader.FlatMappedInt]) &&
      assertTrue(!reader.isInstanceOf[Interpreter])
    },
    test("shallow map on Ref stream compiles to MappedRef") {
      val reader = Stream.fromIterable(List("a", "b")).map(_.toUpperCase).compile(0)
      assertTrue(reader.isInstanceOf[Reader.MappedRef])
    },
    test("101 chained maps compiles to Interpreter") {
      var s: Stream[Nothing, Int] = Stream.range(0, 5)
      var i                       = 0
      while (i < 101) { s = s.map(_ + 1); i += 1 }
      val reader = s.compile(0)
      assertTrue(reader.isInstanceOf[Interpreter])
    },
    test("99 chained maps does NOT compile to Interpreter") {
      var s: Stream[Nothing, Int] = Stream.range(0, 5)
      var i                       = 0
      while (i < 99) { s = s.map(_ + 1); i += 1 }
      val reader = s.compile(0)
      assertTrue(!reader.isInstanceOf[Interpreter])
    },
    test("deep chain produces correct results via Interpreter") {
      var s: Stream[Nothing, Int] = Stream.range(0, 5)
      var i                       = 0
      while (i < 101) { s = s.map(_ + 1); i += 1 }
      val result   = s.runCollect
      val expected = (0 until 5).map(_ + 101)
      assertTrue(result == Right(Chunk.fromIterable(expected)))
    },
    test("shallow chain produces correct results via Reader decoration") {
      val result = Stream.range(0, 10).map(_ + 1).runCollect
      assertTrue(result == Right(Chunk.fromIterable(1 to 10)))
    },
    test("past cutoff: returns a single Interpreter with correct results") {
      var s: Stream[Nothing, Int] = Stream.range(0, 5)
      var i                       = 0
      while (i < 105) { s = s.map(_ + 1); i += 1 }
      val reader = s.compile(0)
      // Top-level is Interpreter
      assertTrue(reader.isInstanceOf[Interpreter]) && {
        // Produces correct results (proves all 105 maps were fused into the pipeline)
        val result = s.runCollect
        assertTrue(result == Right(Chunk.fromIterable((0 until 5).map(_ + 105))))
      }
    },
    test("at cutoff boundary: map at depth 100 triggers Interpreter, maps above add to it") {
      // 101 maps: depth 0..100. At depth 100, Interpreter.fromStream builds the whole subtree.
      // Maps at depths 0..99 see Interpreter from below, add their op, return the SAME instance.
      var s: Stream[Nothing, Int] = Stream.range(0, 3)
      var i                       = 0
      while (i < 101) { s = s.map(_ + 1); i += 1 }
      val reader = s.compile(0)
      assertTrue(reader.isInstanceOf[Interpreter]) && {
        // Verify functional correctness — all 101 maps applied
        val result = s.runCollect
        assertTrue(result == Right(Chunk.fromIterable((0 until 3).map(_ + 101))))
      }
    },
    test("below cutoff: all operations are Reader decorations, no Interpreter") {
      // 5 consecutive Int=>Int maps fuse into ONE MappedInt whose function is
      // the flat array composition of the five map functions (ComposedIntArray;
      // 2 stages stay ComposedIntInt); the leaf is the range reader, never an
      // Interpreter.
      var s: Stream[Nothing, Int] = Stream.range(0, 5)
      var i                       = 0
      while (i < 5) { s = s.map(_ + 1); i += 1 }
      val reader = s.compile(0)
      // Top level is a single fused MappedInt
      assertTrue(reader.isInstanceOf[Reader.MappedInt]) && {
        val m = reader.asInstanceOf[Reader.MappedInt]
        // The five functions are composed, not stacked as nested readers
        assertTrue(m.f.isInstanceOf[Reader.ComposedIntArray]) &&
        assertTrue(!m.source.isInstanceOf[Reader.MappedInt]) &&
        assertTrue(!m.source.isInstanceOf[Interpreter]) && {
          // Functional correctness — all 5 maps applied
          val result = s.runCollect
          assertTrue(result == Right(Chunk.fromIterable((0 until 5).map(_ + 5))))
        }
      }
    },
    test("mixed ops below cutoff: map + filter both decorate") {
      val reader = Stream.range(0, 10).map(_ + 1).filter(_ % 2 == 0).map(_ * 3).compile(0)
      // The trailing `filter(Int).map(Int=>Int)` fuses into a single
      // `FilteredMappedInt` (perf: avoids a fragile 3-deep wrapper inline). Its
      // `source` is the first `map(_ + 1)` (a MappedInt), whose source is the
      // range reader (not an Interpreter, since we are below the depth cutoff).
      assertTrue(reader.isInstanceOf[Reader.FilteredMappedInt]) && {
        val fm = reader.asInstanceOf[Reader.FilteredMappedInt]
        assertTrue(fm.source.isInstanceOf[Reader.MappedInt]) && {
          val m2 = fm.source.asInstanceOf[Reader.MappedInt]
          assertTrue(!m2.source.isInstanceOf[Interpreter])
        }
      }
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
      val result = s.runCollect
      assertTrue(result == Right(Chunk(1)))
    } @@ TestAspect.jvmOnly
  )

  // =========================================================================
  //  20. Construction: fromStream, unsealed, apply
  // =========================================================================

  val constructionSuite = suite("Construction")(
    test("Interpreter.fromStream builds and seals a pipeline from a Stream") {
      val stream = Stream.fromChunk(Chunk(1, 2, 3)).map((_: Int) * 10)
      val p      = Interpreter.fromStream(stream)
      val result = drainInts(p)
      assertTrue(result == List(10, 20, 30))
    },
    test("Interpreter.fromStream with empty stream returns sentinel immediately") {
      val stream = Stream.fromChunk(Chunk.empty[Int])
      val p      = Interpreter.fromStream(stream)
      assertTrue(readIntValue(p, Long.MinValue) == Long.MinValue)
    },
    test("Interpreter.fromStream with filter + map") {
      val stream = Stream.fromRange(0 until 10).filter((_: Int) % 2 == 0).map((_: Int) + 100)
      val p      = Interpreter.fromStream(stream)
      val result = drainInts(p)
      assertTrue(result == List(100, 102, 104, 106, 108))
    },
    test("Interpreter.fromStream with flatMap") {
      val stream = Stream.fromChunk(Chunk(1, 2)).flatMap((i: Int) => Stream.fromChunk(Chunk(i, i * 10)))
      val p      = Interpreter.fromStream(stream)
      val result = drainInts(p)
      assertTrue(result == List(1, 10, 2, 20))
    },
    test("Interpreter.unsealed creates pipeline that can have ops added") {
      val source = Reader.fromRange(0 until 5)
      val p      = Interpreter.unsealed(source)
      p.addMap[Int, Int](LANE_I, OUT_I)((_: Int) + 10)
      p.seal()
      val result = drainInts(p)
      assertTrue(result == List(10, 11, 12, 13, 14))
    },
    test("Interpreter.unsealed without additional ops, then seal") {
      val source = Reader.fromRange(0 until 3)
      val p      = Interpreter.unsealed(source)
      p.seal()
      val result = drainInts(p)
      assertTrue(result == List(0, 1, 2))
    },
    test("Interpreter.apply creates a sealed pipeline from a Reader") {
      val source = Reader.fromRange(0 until 4)
      val p      = Interpreter(source)
      // Interpreter.apply calls seal() — further addMap still works (adds to sealed pipeline)
      p.addMap[Int, Int](LANE_I, OUT_I)((_: Int) * 2)
      val result = drainInts(p)
      assertTrue(result == List(0, 2, 4, 6))
    }
  )

  // =========================================================================
  //  21. Reset and re-read
  // =========================================================================

  val resetAndRereadSuite = suite("Reset and re-read")(
    test("read all, reset, read all again returns same elements") {
      val source = Reader.fromRange(0 until 5)
      val p      = Interpreter.unsealed(source)
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
      val p      = Interpreter(source)
      val v1     = readIntValue(p, Long.MinValue)
      val v2     = readIntValue(p, Long.MinValue)
      p.reset()
      val result = drainInts(p)
      assertTrue(v1 == 0L && v2 == 1L) &&
      assertTrue(result == List(0, 1, 2, 3, 4))
    },
    test("reset on pipeline with filter") {
      val source = Reader.fromRange(0 until 10)
      val p      = Interpreter.unsealed(source)
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
      val p      = Interpreter.unsealed(source)
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
      val p      = Interpreter(source)
      val first  = drainInts(p)
      p.reset()
      val second = drainInts(p)
      assertTrue(first == List(0, 1, 2)) &&
      assertTrue(second == List(0, 1, 2))
    },
    test("multiple resets") {
      val source = Reader.fromRange(0 until 3)
      val p      = Interpreter.unsealed(source)
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
      val p      = Interpreter.fromStream(stream)
      val first  = drainInts(p)
      p.reset()
      val second = drainInts(p)
      assertTrue(first == List(2, 4)) &&
      assertTrue(second == List(2, 4))
    }
  )

  // =========================================================================
  //  22. Close behavior
  // =========================================================================

  val closeBehaviorSuite = suite("Close behavior")(
    test("close() sets isClosed to true") {
      val source      = Reader.fromRange(0 until 5)
      val p           = Interpreter(source)
      val beforeClose = p.isClosed
      p.close()
      val afterClose = p.isClosed
      assertTrue(!beforeClose && afterClose)
    },
    test("read after close returns sentinel (readInt)") {
      val source = Reader.fromRange(0 until 5)
      val p      = Interpreter(source)
      p.close()
      assertTrue(readIntValue(p, Long.MinValue) == Long.MinValue)
    },
    test("read after close returns sentinel (readLong)") {
      val source = Reader.fromChunk(Chunk(1L, 2L))
      val p      = Interpreter(source)
      p.close()
      assertTrue(readLongValue(p, Long.MaxValue) == Long.MaxValue)
    },
    test("read after close returns sentinel (readFloat)") {
      val source = Reader.fromChunk(Chunk(1.0f, 2.0f))
      val p      = Interpreter(source)
      p.close()
      assertTrue(readFloatValue(p, Double.MaxValue) == Double.MaxValue)
    },
    test("read after close returns sentinel (readDouble)") {
      val source = Reader.fromChunk(Chunk(1.0, 2.0))
      val p      = Interpreter(source)
      p.close()
      assertTrue(readDoubleValue(p, Double.MaxValue) == Double.MaxValue)
    },
    test("read after close returns sentinel (generic read)") {
      val source = Reader.fromIterable(List("a", "b"))
      val p      = Interpreter(source)
      p.close()
      val sentinel = new AnyRef
      assertTrue(p.read(sentinel).asInstanceOf[AnyRef] eq sentinel)
    },
    test("double close is idempotent") {
      val source = Reader.fromRange(0 until 5)
      val p      = Interpreter(source)
      p.close()
      assertTrue(p.isClosed)
      p.close() // should not throw
      assertTrue(p.isClosed)
    },
    test("partial read then close then read returns sentinel") {
      val source = Reader.fromRange(0 until 5)
      val p      = Interpreter(source)
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
      val p      = Interpreter(source)
      p.skip(0)
      val result = drainInts(p)
      assertTrue(result == List(0, 1, 2, 3, 4))
    },
    test("skip 3 elements from 5-element source") {
      val source = Reader.fromRange(0 until 5)
      val p      = Interpreter(source)
      p.skip(3)
      val result = drainInts(p)
      assertTrue(result == List(3, 4))
    },
    test("skip all elements returns sentinel") {
      val source = Reader.fromRange(0 until 5)
      val p      = Interpreter(source)
      p.skip(5)
      assertTrue(readIntValue(p, Long.MinValue) == Long.MinValue)
    },
    test("skip more than available returns sentinel") {
      val source = Reader.fromRange(0 until 3)
      val p      = Interpreter(source)
      p.skip(100)
      assertTrue(readIntValue(p, Long.MinValue) == Long.MinValue)
    },
    test("skip on pipeline with map") {
      val source = Reader.fromRange(0 until 10)
      val p      = Interpreter(source)
      p.addMap[Int, Int](LANE_I, OUT_I)((_: Int) * 10)
      p.skip(5)
      val result = drainInts(p)
      // Elements after skip: 50, 60, 70, 80, 90
      assertTrue(result == List(50, 60, 70, 80, 90))
    },
    test("skip on pipeline with filter") {
      val source = Reader.fromRange(0 until 20)
      val p      = Interpreter(source)
      p.addFilter[Int](LANE_I)((_: Int) % 2 == 0)
      p.skip(3)
      // After filter: 0,2,4,6,8,10,12,14,16,18; skip 3 → 6,8,10,12,14,16,18
      val result = drainInts(p)
      assertTrue(result == List(6, 8, 10, 12, 14, 16, 18))
    },
    test("skip on Long pipeline") {
      val source = Reader.fromChunk(Chunk(10L, 20L, 30L, 40L, 50L))
      val p      = Interpreter(source)
      p.skip(2)
      val result = drainLongs(p)
      assertTrue(result == List(30L, 40L, 50L))
    },
    test("skip on Float pipeline") {
      val source = Reader.fromChunk(Chunk(1.0f, 2.0f, 3.0f, 4.0f))
      val p      = Interpreter(source)
      p.skip(1)
      val result = drainFloats(p)
      assertTrue(result == List(2.0f, 3.0f, 4.0f))
    },
    test("skip on Double pipeline") {
      val source = Reader.fromChunk(Chunk(1.0, 2.0, 3.0, 4.0))
      val p      = Interpreter(source)
      p.skip(2)
      val result = drainDoubles(p)
      assertTrue(result == List(3.0, 4.0))
    },
    test("skip on AnyRef pipeline") {
      val source = Reader.fromIterable(List("a", "b", "c", "d"))
      val p      = Interpreter(source)
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
      val p      = Interpreter(source)
      assertTrue(p.readable())
    },
    test("readable is false after close") {
      val source = Reader.fromRange(0 until 5)
      val p      = Interpreter(source)
      p.close()
      assertTrue(!p.readable())
    },
    test("readable on empty source") {
      // Interpreter inherits default readable = !isClosed
      // An empty source pipeline is not "closed" until close() is called
      val source = Reader.fromRange(0 until 0)
      val p      = Interpreter(source)
      // The pipeline itself is not closed, but the source is exhausted
      // readable() returns !isClosed which is true (pipeline not closed yet)
      assertTrue(!p.isClosed)
    }
  )

  // =========================================================================
  //  25. jvmType for all lane types
  // =========================================================================

  val jvmTypeSuite = suite("jvmType")(
    test("Int pipeline has JvmType.Int") {
      val p = Interpreter(Reader.fromRange(0 until 1))
      assertTrue(p.jvmType == JvmType.Int)
    },
    test("Long pipeline has JvmType.Long") {
      val p = Interpreter(Reader.fromChunk(Chunk(1L)))
      assertTrue(p.jvmType == JvmType.Long)
    },
    test("Float pipeline has JvmType.Float") {
      val p = Interpreter(Reader.fromChunk(Chunk(1.0f)))
      assertTrue(p.jvmType == JvmType.Float)
    },
    test("Double pipeline has JvmType.Double") {
      val p = Interpreter(Reader.fromChunk(Chunk(1.0)))
      assertTrue(p.jvmType == JvmType.Double)
    },
    test("AnyRef pipeline has JvmType.AnyRef") {
      val p = Interpreter(Reader.fromIterable(List("hello")))
      assertTrue(p.jvmType == JvmType.AnyRef)
    },
    test("jvmType changes after cross-type map Int→Long") {
      val p      = Interpreter(Reader.fromRange(0 until 1))
      val before = p.jvmType
      p.addMap[Int, Long](LANE_I, OUT_L)((_: Int).toLong)
      val after = p.jvmType
      assertTrue(before == JvmType.Int && after == JvmType.Long)
    },
    test("jvmType changes after cross-type map Int→Double") {
      val p = Interpreter(Reader.fromRange(0 until 1))
      p.addMap[Int, Double](LANE_I, OUT_D)((_: Int).toDouble)
      assertTrue(p.jvmType == JvmType.Double)
    },
    test("jvmType changes after cross-type map Int→Float") {
      val p = Interpreter(Reader.fromRange(0 until 1))
      p.addMap[Int, Float](LANE_I, OUT_F)((_: Int).toFloat)
      assertTrue(p.jvmType == JvmType.Float)
    },
    test("jvmType changes after cross-type map Int→AnyRef") {
      val p = Interpreter(Reader.fromRange(0 until 1))
      p.addMap[Int, AnyRef](LANE_I, OUT_R)((i: Int) => s"v$i")
      assertTrue(p.jvmType == JvmType.AnyRef)
    },
    test("jvmType is AnyRef after close") {
      val p = Interpreter(Reader.fromRange(0 until 1))
      p.close()
      assertTrue(p.jvmType == JvmType.AnyRef)
    }
  )

  // =========================================================================
  //  26. Specialized read methods
  // =========================================================================

  val specializedReadSuite = suite("Specialized read methods")(
    test("readInt on Int pipeline returns values without boxing") {
      val p  = Interpreter(Reader.fromRange(0 until 3))
      val v1 = readIntValue(p, Long.MinValue)
      val v2 = readIntValue(p, Long.MinValue)
      val v3 = readIntValue(p, Long.MinValue)
      val v4 = readIntValue(p, Long.MinValue)
      assertTrue(v1 == 0L && v2 == 1L && v3 == 2L && v4 == Long.MinValue)
    },
    test("readLong on Long pipeline returns values without boxing") {
      val p  = Interpreter(Reader.fromChunk(Chunk(100L, 200L)))
      val v1 = readLongValue(p, Long.MaxValue)
      val v2 = readLongValue(p, Long.MaxValue)
      val v3 = readLongValue(p, Long.MaxValue)
      assertTrue(v1 == 100L && v2 == 200L && v3 == Long.MaxValue)
    },
    test("readFloat on Float pipeline returns values without boxing") {
      val p  = Interpreter(Reader.fromChunk(Chunk(1.5f, 2.5f)))
      val v1 = readFloatValue(p, Double.MaxValue)
      val v2 = readFloatValue(p, Double.MaxValue)
      val v3 = readFloatValue(p, Double.MaxValue)
      assertTrue(v1 == 1.5f.toDouble && v2 == 2.5f.toDouble && v3 == Double.MaxValue)
    },
    test("readDouble on Double pipeline returns values without boxing") {
      val p  = Interpreter(Reader.fromChunk(Chunk(3.14, 2.71)))
      val v1 = readDoubleValue(p, Double.MaxValue)
      val v2 = readDoubleValue(p, Double.MaxValue)
      val v3 = readDoubleValue(p, Double.MaxValue)
      assertTrue(v1 == 3.14 && v2 == 2.71 && v3 == Double.MaxValue)
    },
    test("read[Any] on AnyRef pipeline returns boxed values") {
      val p        = Interpreter(Reader.fromIterable(List("hello", "world")))
      val sentinel = new AnyRef
      val v1       = p.read(sentinel)
      val v2       = p.read(sentinel)
      val v3       = p.read(sentinel)
      assertTrue(v1 == "hello" && v2 == "world" && (v3.asInstanceOf[AnyRef] eq sentinel))
    },
    test("readInt with map on Int pipeline") {
      val p = Interpreter(Reader.fromRange(0 until 3))
      p.addMap[Int, Int](LANE_I, OUT_I)((_: Int) + 100)
      val v1 = readIntValue(p, Long.MinValue)
      val v2 = readIntValue(p, Long.MinValue)
      val v3 = readIntValue(p, Long.MinValue)
      val v4 = readIntValue(p, Long.MinValue)
      assertTrue(v1 == 100L && v2 == 101L && v3 == 102L && v4 == Long.MinValue)
    },
    test("readLong with map on Long pipeline") {
      val p = Interpreter(Reader.fromChunk(Chunk(1L, 2L, 3L)))
      p.addMap[Long, Long](LANE_L, OUT_L)((_: Long) * 10L)
      val v1 = readLongValue(p, Long.MaxValue)
      val v2 = readLongValue(p, Long.MaxValue)
      val v3 = readLongValue(p, Long.MaxValue)
      val v4 = readLongValue(p, Long.MaxValue)
      assertTrue(v1 == 10L && v2 == 20L && v3 == 30L && v4 == Long.MaxValue)
    },
    test("readFloat with map on Float pipeline") {
      val p = Interpreter(Reader.fromChunk(Chunk(1.0f, 2.0f)))
      p.addMap[Float, Float](LANE_F, OUT_F)((_: Float) + 0.5f)
      val v1 = readFloatValue(p, Double.MaxValue)
      val v2 = readFloatValue(p, Double.MaxValue)
      val v3 = readFloatValue(p, Double.MaxValue)
      assertTrue(v1 == 1.5f.toDouble && v2 == 2.5f.toDouble && v3 == Double.MaxValue)
    },
    test("readDouble with map on Double pipeline") {
      val p = Interpreter(Reader.fromChunk(Chunk(1.0, 2.0)))
      p.addMap[Double, Double](LANE_D, OUT_D)((_: Double) + 0.1)
      val v1 = readDoubleValue(p, Double.MaxValue)
      val v2 = readDoubleValue(p, Double.MaxValue)
      val v3 = readDoubleValue(p, Double.MaxValue)
      assertTrue(v1 == 1.1 && v2 == 2.1 && v3 == Double.MaxValue)
    },
    test("readInt with cross-type map Int→Long emits in Long lane via readLong") {
      val p = Interpreter(Reader.fromRange(0 until 3))
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
      assertTrue(Interpreter.bridgeTag(LANE_I, LANE_L) == OpTag.mapTag(LANE_I, LANE_L))
    },
    test("bridgeTag for Int→Float") {
      assertTrue(Interpreter.bridgeTag(LANE_I, LANE_F) == OpTag.mapTag(LANE_I, LANE_F))
    },
    test("bridgeTag for Int→Double") {
      assertTrue(Interpreter.bridgeTag(LANE_I, LANE_D) == OpTag.mapTag(LANE_I, LANE_D))
    },
    test("bridgeTag for Int→Ref") {
      assertTrue(Interpreter.bridgeTag(LANE_I, LANE_R) == OpTag.mapTag(LANE_I, LANE_R))
    },
    test("bridgeTag for Long→Int") {
      assertTrue(Interpreter.bridgeTag(LANE_L, LANE_I) == OpTag.mapTag(LANE_L, LANE_I))
    },
    test("bridgeTag for Long→Double") {
      assertTrue(Interpreter.bridgeTag(LANE_L, LANE_D) == OpTag.mapTag(LANE_L, LANE_D))
    },
    test("bridgeTag for Double→Int") {
      assertTrue(Interpreter.bridgeTag(LANE_D, LANE_I) == OpTag.mapTag(LANE_D, LANE_I))
    },
    test("bridgeTag for Ref→Int") {
      assertTrue(Interpreter.bridgeTag(LANE_R, LANE_I) == OpTag.mapTag(LANE_R, LANE_I))
    },
    test("bridgeFn Int→Long converts correctly") {
      val fn = Interpreter.bridgeFn(LANE_I, LANE_L).asInstanceOf[Int => Long]
      assertTrue(fn(42) == 42L && fn(-1) == -1L && fn(0) == 0L)
    },
    test("bridgeFn Int→Float converts correctly") {
      val fn = Interpreter.bridgeFn(LANE_I, LANE_F).asInstanceOf[Int => Float]
      assertTrue(fn(42) == 42.0f && fn(0) == 0.0f)
    },
    test("bridgeFn Int→Double converts correctly") {
      val fn = Interpreter.bridgeFn(LANE_I, LANE_D).asInstanceOf[Int => Double]
      assertTrue(fn(42) == 42.0 && fn(0) == 0.0)
    },
    test("bridgeFn Int→Ref boxes correctly") {
      val fn = Interpreter.bridgeFn(LANE_I, LANE_R).asInstanceOf[Int => AnyRef]
      assertTrue(fn(42) == Int.box(42))
    },
    test("bridgeFn Long→Int converts correctly") {
      val fn = Interpreter.bridgeFn(LANE_L, LANE_I).asInstanceOf[Long => Int]
      assertTrue(fn(42L) == 42 && fn(0L) == 0)
    },
    test("bridgeFn Long→Float converts correctly") {
      val fn = Interpreter.bridgeFn(LANE_L, LANE_F).asInstanceOf[Long => Float]
      assertTrue(fn(42L) == 42.0f)
    },
    test("bridgeFn Long→Double converts correctly") {
      val fn = Interpreter.bridgeFn(LANE_L, LANE_D).asInstanceOf[Long => Double]
      assertTrue(fn(42L) == 42.0)
    },
    test("bridgeFn Long→Ref boxes correctly") {
      val fn = Interpreter.bridgeFn(LANE_L, LANE_R).asInstanceOf[Long => AnyRef]
      assertTrue(fn(42L) == Long.box(42L))
    },
    test("bridgeFn Float→Int converts correctly") {
      val fn = Interpreter.bridgeFn(LANE_F, LANE_I).asInstanceOf[Float => Int]
      assertTrue(fn(42.9f) == 42)
    },
    test("bridgeFn Float→Long converts correctly") {
      val fn = Interpreter.bridgeFn(LANE_F, LANE_L).asInstanceOf[Float => Long]
      assertTrue(fn(42.0f) == 42L)
    },
    test("bridgeFn Float→Double converts correctly") {
      val fn = Interpreter.bridgeFn(LANE_F, LANE_D).asInstanceOf[Float => Double]
      assertTrue(fn(1.5f) == 1.5f.toDouble)
    },
    test("bridgeFn Float→Ref boxes correctly") {
      val fn = Interpreter.bridgeFn(LANE_F, LANE_R).asInstanceOf[Float => AnyRef]
      assertTrue(fn(1.5f) == Float.box(1.5f))
    },
    test("bridgeFn Double→Int converts correctly") {
      val fn = Interpreter.bridgeFn(LANE_D, LANE_I).asInstanceOf[Double => Int]
      assertTrue(fn(42.9) == 42)
    },
    test("bridgeFn Double→Long converts correctly") {
      val fn = Interpreter.bridgeFn(LANE_D, LANE_L).asInstanceOf[Double => Long]
      assertTrue(fn(42.0) == 42L)
    },
    test("bridgeFn Double→Float converts correctly") {
      val fn = Interpreter.bridgeFn(LANE_D, LANE_F).asInstanceOf[Double => Float]
      assertTrue(fn(1.5) == 1.5f)
    },
    test("bridgeFn Double→Ref boxes correctly") {
      val fn = Interpreter.bridgeFn(LANE_D, LANE_R).asInstanceOf[Double => AnyRef]
      assertTrue(fn(1.5) == Double.box(1.5))
    },
    test("bridgeFn Ref→Int converts via Number.intValue") {
      val fn = Interpreter.bridgeFn(LANE_R, LANE_I).asInstanceOf[AnyRef => Int]
      assertTrue(fn(Int.box(42)) == 42)
    },
    test("bridgeFn Ref→Long converts via Number.longValue") {
      val fn = Interpreter.bridgeFn(LANE_R, LANE_L).asInstanceOf[AnyRef => Long]
      assertTrue(fn(Long.box(42L)) == 42L)
    },
    test("bridgeFn Ref→Float converts via Number.floatValue") {
      val fn = Interpreter.bridgeFn(LANE_R, LANE_F).asInstanceOf[AnyRef => Float]
      assertTrue(fn(Float.box(1.5f)) == 1.5f)
    },
    test("bridgeFn Ref→Double converts via Number.doubleValue") {
      val fn = Interpreter.bridgeFn(LANE_R, LANE_D).asInstanceOf[AnyRef => Double]
      assertTrue(fn(Double.box(1.5)) == 1.5)
    }
  )

  // =========================================================================
  //  28. Large data tests
  // =========================================================================

  val largeDataSuite = suite("Large data")(
    test("1000-element source with map") {
      val source = Reader.fromRange(0 until 1000)
      val p      = Interpreter(source)
      p.addMap[Int, Int](LANE_I, OUT_I)((_: Int) + 1)
      val result = drainInts(p)
      assertTrue(result == (0 until 1000).map(_ + 1).toList)
    },
    test("10000-element source with filter") {
      val source = Reader.fromRange(0 until 10000)
      val p      = Interpreter(source)
      p.addFilter[Int](LANE_I)((_: Int) % 100 == 0)
      val result = drainInts(p)
      assertTrue(result == (0 until 10000).filter(_ % 100 == 0).toList)
    },
    test("large source with map + filter + map chain") {
      val source = Reader.fromRange(0 until 5000)
      val p      = Interpreter(source)
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
      val p      = Interpreter(source)
      p.addMap[Long, Long](LANE_L, OUT_L)((_: Long) + 1L)
      val result = drainLongs(p)
      assertTrue(result == (0L until 1000L).map(_ + 1L).toList)
    },
    test("large flatMap: each element expands to 10") {
      val source                = Reader.fromRange(0 until 100)
      val p                     = Interpreter(source)
      val pushFn: Int => AnyRef = (i: Int) => {
        Stream.fromRange(0 until 10).map((_: Int) + i * 10).asInstanceOf[AnyRef]
      }
      p.addPush[Int](LANE_I, LANE_I)(pushFn)
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
      val p      = Interpreter(source)
      p.addFilter[Int](LANE_I)((_: Int) > 5)
      p.addFilter[Int](LANE_I)((_: Int) < 15)
      p.addFilter[Int](LANE_I)((_: Int) % 2 == 0)
      val result = drainInts(p)
      assertTrue(result == List(6, 8, 10, 12, 14))
    },
    test("pipeline with only maps (no filters)") {
      val source = Reader.fromRange(0 until 5)
      val p      = Interpreter(source)
      p.addMap[Int, Int](LANE_I, OUT_I)((_: Int) + 1)
      p.addMap[Int, Int](LANE_I, OUT_I)((_: Int) * 2)
      p.addMap[Int, Int](LANE_I, OUT_I)((_: Int) + 100)
      val result   = drainInts(p)
      val expected = (0 until 5).map(i => ((i + 1) * 2) + 100).toList
      assertTrue(result == expected)
    },
    test("single-element source with map") {
      val source = Reader.fromChunk(Chunk(42))
      val p      = Interpreter(source)
      p.addMap[Int, Int](LANE_I, OUT_I)((_: Int) * 2)
      val result = drainInts(p)
      assertTrue(result == List(84))
    },
    test("single-element source with filter pass") {
      val source = Reader.fromChunk(Chunk(42))
      val p      = Interpreter(source)
      p.addFilter[Int](LANE_I)((_: Int) > 0)
      val result = drainInts(p)
      assertTrue(result == List(42))
    },
    test("single-element source with filter reject") {
      val source = Reader.fromChunk(Chunk(42))
      val p      = Interpreter(source)
      p.addFilter[Int](LANE_I)((_: Int) < 0)
      val result = drainInts(p)
      assertTrue(result == List())
    },
    test("very long pipeline: 200 chained map ops") {
      val source = Reader.fromRange(0 until 3)
      val p      = Interpreter(source)
      var i      = 0; while (i < 200) { p.addMap[Int, Int](LANE_I, OUT_I)((_: Int) + 1); i += 1 }
      val result = drainInts(p)
      assertTrue(result == List(200, 201, 202))
    },
    test("pipeline with alternating map-filter-map-filter pattern") {
      val source = Reader.fromRange(0 until 100)
      val p      = Interpreter(source)
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
      val p = Interpreter(source)
      p.addPush[Int](LANE_I, LANE_I)(pushFn)
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
      val p      = Interpreter(source)
      p.addFilter[Long](LANE_L)((_: Long) > 100L)
      val result = drainLongs(p)
      assertTrue(result == List())
    },
    test("pipeline with filter that removes ALL AnyRef elements") {
      val source = Reader.fromIterable(List("a", "b", "c"))
      val p      = Interpreter(source)
      p.addFilter[AnyRef](LANE_R)((s: AnyRef) => s.asInstanceOf[String].length > 10)
      val result = drainGeneric(p)
      assertTrue(result == List())
    },
    test("cross-lane pipeline: Int → Long → Double via chained maps") {
      val source = Reader.fromRange(0 until 3)
      val p      = Interpreter(source)
      p.addMap[Int, Long](LANE_I, OUT_L)((_: Int).toLong)
      p.addMap[Long, Double](LANE_L, OUT_D)((_: Long).toDouble + 0.5)
      val result = drainDoubles(p)
      assertTrue(result == List(0.5, 1.5, 2.5))
    },
    test("cross-lane pipeline: Double → Int → AnyRef via chained maps") {
      val source = Reader.fromChunk(Chunk(1.0, 2.0, 3.0))
      val p      = Interpreter(source)
      p.addMap[Double, Int](LANE_D, OUT_I)((_: Double).toInt)
      p.addMap[Int, AnyRef](LANE_I, OUT_R)((i: Int) => s"val=$i")
      val result = drainGeneric(p)
      assertTrue(result == List("val=1", "val=2", "val=3"))
    },
    test("pipeline with Long filter + Long map") {
      val source = Reader.fromChunk(Chunk(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L))
      val p      = Interpreter(source)
      p.addFilter[Long](LANE_L)((_: Long) % 2L == 0L)
      p.addMap[Long, Long](LANE_L, OUT_L)((_: Long) * 100L)
      val result = drainLongs(p)
      assertTrue(result == List(200L, 400L, 600L, 800L, 1000L))
    },
    test("pipeline with Float filter") {
      val source = Reader.fromChunk(Chunk(1.0f, 2.0f, 3.0f, 4.0f, 5.0f))
      val p      = Interpreter(source)
      p.addFilter[Float](LANE_F)((_: Float) > 2.5f)
      val result = drainFloats(p)
      assertTrue(result == List(3.0f, 4.0f, 5.0f))
    },
    test("pipeline with Double filter") {
      val source = Reader.fromChunk(Chunk(1.0, 2.0, 3.0, 4.0, 5.0))
      val p      = Interpreter(source)
      p.addFilter[Double](LANE_D)((_: Double) > 3.0)
      val result = drainDoubles(p)
      assertTrue(result == List(4.0, 5.0))
    },
    test("readByte on Int pipeline") {
      val source = Reader.fromChunk(Chunk(65, 66, 67))
      val p      = Interpreter(source)
      val b1     = p.readByte()
      val b2     = p.readByte()
      val b3     = p.readByte()
      val b4     = p.readByte()
      assertTrue(b1 == 65 && b2 == 66 && b3 == 67 && b4 == -1)
    },
    test("compileInterpreter chains one pipeline into another") {
      // Build an inner stream that includes a map, compile into Interpreter via fromStream
      val innerStream = Stream.fromRange(0 until 3).map((_: Int) + 100)
      val p           = Interpreter.fromStream(innerStream)
      val result      = drainInts(p)
      assertTrue(result == List(100, 101, 102))
    },
    test("pipeline reading from another pipeline via flatMap") {
      // Outer pipeline source → flatMap → inner pipeline compiled from stream
      val source                = Reader.fromRange(0 until 3)
      val pushFn: Int => AnyRef = (i: Int) => {
        // Inner stream compiles to a Reader (possibly Interpreter) at runtime
        Stream.fromRange(0 until 2).map((_: Int) + i * 100).asInstanceOf[AnyRef]
      }
      val p = Interpreter(source)
      p.addPush[Int](LANE_I, LANE_I)(pushFn)
      val result = drainInts(p)
      // 0 → [0, 1], 1 → [100, 101], 2 → [200, 201]
      assertTrue(result == List(0, 1, 100, 101, 200, 201))
    }
  )

  // =========================================================================
  //  coverage — moved from InterpreterCoverageSpec
  // =========================================================================

  val coverageSuite = suite("coverage")(
    Coverage.allMapCrossingsSuite,
    Coverage.wrapLastReadSuite,
    Coverage.appendReadSuite,
    Coverage.closeSuppressedSuite
  )

  private object Coverage {
    import Interpreter._

    private def readIntValue(p: Interpreter, sentinel: Long): Long =
      p.asInstanceOf[Reader[Int]].readInt(sentinel)

    private def readLongValue(p: Interpreter, sentinel: Long): Long =
      p.asInstanceOf[Reader[Long]].readLong(sentinel)

    private def readFloatValue(p: Interpreter, sentinel: Double): Double =
      p.asInstanceOf[Reader[Float]].readFloat(sentinel)

    private def readDoubleValue(p: Interpreter, sentinel: Double): Double =
      p.asInstanceOf[Reader[Double]].readDouble(sentinel)

    // ---- drain helpers (same as InterpreterSpec) ----

    private def drainInts(p: Interpreter, sentinel: Long = Long.MinValue): List[Int] = {
      val buf = scala.collection.mutable.ListBuffer[Int]()
      var v   = readIntValue(p, sentinel)
      while (v != sentinel) { buf += v.toInt; v = readIntValue(p, sentinel) }
      buf.toList
    }

    private def drainLongs(p: Interpreter, sentinel: Long = Long.MaxValue): List[Long] = {
      val buf = scala.collection.mutable.ListBuffer[Long]()
      var v   = readLongValue(p, sentinel)
      while (v != sentinel) { buf += v; v = readLongValue(p, sentinel) }
      buf.toList
    }

    private def drainFloats(p: Interpreter, sentinel: Double = Double.MaxValue): List[Float] = {
      val buf = scala.collection.mutable.ListBuffer[Float]()
      var v   = readFloatValue(p, sentinel)
      while (v != sentinel) { buf += v.toFloat; v = readFloatValue(p, sentinel) }
      buf.toList
    }

    private def drainDoubles(p: Interpreter, sentinel: Double = Double.MaxValue): List[Double] = {
      val buf = scala.collection.mutable.ListBuffer[Double]()
      var v   = readDoubleValue(p, sentinel)
      while (v != sentinel) { buf += v; v = readDoubleValue(p, sentinel) }
      buf.toList
    }

    private def drainGeneric(p: Interpreter): List[Any] = {
      val sentinel: AnyRef = new AnyRef
      val buf              = scala.collection.mutable.ListBuffer[Any]()
      var v                = p.read(sentinel)
      while (v.asInstanceOf[AnyRef] ne sentinel) { buf += v; v = p.read(sentinel) }
      buf.toList
    }

    // =========================================================================
    //  All 25 addMap lane crossings
    // =========================================================================

    val allMapCrossingsSuite = suite("All 25 addMap lane crossings")(
      // ---- Int input (5 outputs) ----
      test("Int->Int (tag 0)") {
        val p = Interpreter(Reader.fromRange(0 until 3))
        p.addMap[Int, Int](LANE_I, OUT_I)((_: Int) + 10)
        assertTrue(drainInts(p) == List(10, 11, 12))
      },
      test("Int->Long (tag 1)") {
        val p = Interpreter(Reader.fromRange(0 until 3))
        p.addMap[Int, Long](LANE_I, OUT_L)((_: Int).toLong * 100L)
        assertTrue(drainLongs(p) == List(0L, 100L, 200L))
      },
      test("Int->Float (tag 2)") {
        val p = Interpreter(Reader.fromRange(0 until 3))
        p.addMap[Int, Float](LANE_I, OUT_F)((_: Int).toFloat + 0.5f)
        assertTrue(drainFloats(p) == List(0.5f, 1.5f, 2.5f))
      },
      test("Int->Double (tag 3)") {
        val p = Interpreter(Reader.fromRange(0 until 3))
        p.addMap[Int, Double](LANE_I, OUT_D)((_: Int).toDouble + 0.1)
        assertTrue(drainDoubles(p) == List(0.1, 1.1, 2.1))
      },
      test("Int->AnyRef (tag 4)") {
        val p = Interpreter(Reader.fromRange(0 until 3))
        p.addMap[Int, AnyRef](LANE_I, OUT_R)((i: Int) => s"v$i")
        assertTrue(drainGeneric(p) == List("v0", "v1", "v2"))
      },

      // ---- Long input (5 outputs) ----
      test("Long->Int (tag 5)") {
        val p = Interpreter(Reader.fromChunk(Chunk(10L, 20L, 30L)))
        p.addMap[Long, Int](LANE_L, OUT_I)((_: Long).toInt)
        assertTrue(drainInts(p) == List(10, 20, 30))
      },
      test("Long->Long (tag 6)") {
        val p = Interpreter(Reader.fromChunk(Chunk(1L, 2L, 3L)))
        p.addMap[Long, Long](LANE_L, OUT_L)((_: Long) * 5L)
        assertTrue(drainLongs(p) == List(5L, 10L, 15L))
      },
      test("Long->Float (tag 7)") {
        val p = Interpreter(Reader.fromChunk(Chunk(1L, 2L, 3L)))
        p.addMap[Long, Float](LANE_L, OUT_F)((_: Long).toFloat + 0.5f)
        assertTrue(drainFloats(p) == List(1.5f, 2.5f, 3.5f))
      },
      test("Long->Double (tag 8)") {
        val p = Interpreter(Reader.fromChunk(Chunk(1L, 2L, 3L)))
        p.addMap[Long, Double](LANE_L, OUT_D)((_: Long).toDouble)
        assertTrue(drainDoubles(p) == List(1.0, 2.0, 3.0))
      },
      test("Long->AnyRef (tag 9)") {
        val p = Interpreter(Reader.fromChunk(Chunk(1L, 2L, 3L)))
        p.addMap[Long, AnyRef](LANE_L, OUT_R)((l: Long) => s"L$l")
        assertTrue(drainGeneric(p) == List("L1", "L2", "L3"))
      },

      // ---- Float input (5 outputs) ----
      test("Float->Int (tag 10)") {
        val p = Interpreter(Reader.fromChunk(Chunk(1.9f, 2.1f, 3.7f)))
        p.addMap[Float, Int](LANE_F, OUT_I)((_: Float).toInt)
        assertTrue(drainInts(p) == List(1, 2, 3))
      },
      test("Float->Long (tag 11)") {
        val p = Interpreter(Reader.fromChunk(Chunk(1.0f, 2.0f, 3.0f)))
        p.addMap[Float, Long](LANE_F, OUT_L)((_: Float).toLong)
        assertTrue(drainLongs(p) == List(1L, 2L, 3L))
      },
      test("Float->Float (tag 12)") {
        val p = Interpreter(Reader.fromChunk(Chunk(1.0f, 2.0f, 3.0f)))
        p.addMap[Float, Float](LANE_F, OUT_F)((_: Float) * 2.0f)
        assertTrue(drainFloats(p) == List(2.0f, 4.0f, 6.0f))
      },
      test("Float->Double (tag 13)") {
        val p = Interpreter(Reader.fromChunk(Chunk(1.5f, 2.5f)))
        p.addMap[Float, Double](LANE_F, OUT_D)((_: Float).toDouble)
        assertTrue(drainDoubles(p) == List(1.5f.toDouble, 2.5f.toDouble))
      },
      test("Float->AnyRef (tag 14)") {
        val p = Interpreter(Reader.fromChunk(Chunk(1.5f, 2.5f)))
        p.addMap[Float, AnyRef](LANE_F, OUT_R)((f: Float) => s"F$f")
        assertTrue(drainGeneric(p) == List("F1.5", "F2.5"))
      },

      // ---- Double input (5 outputs) ----
      test("Double->Int (tag 15)") {
        val p = Interpreter(Reader.fromChunk(Chunk(1.9, 2.1, 3.7)))
        p.addMap[Double, Int](LANE_D, OUT_I)((_: Double).toInt)
        assertTrue(drainInts(p) == List(1, 2, 3))
      },
      test("Double->Long (tag 16)") {
        val p = Interpreter(Reader.fromChunk(Chunk(1.0, 2.0, 3.0)))
        p.addMap[Double, Long](LANE_D, OUT_L)((_: Double).toLong)
        assertTrue(drainLongs(p) == List(1L, 2L, 3L))
      },
      test("Double->Float (tag 17)") {
        val p = Interpreter(Reader.fromChunk(Chunk(1.5, 2.5)))
        p.addMap[Double, Float](LANE_D, OUT_F)((_: Double).toFloat)
        assertTrue(drainFloats(p) == List(1.5f, 2.5f))
      },
      test("Double->Double (tag 18)") {
        val p = Interpreter(Reader.fromChunk(Chunk(1.0, 2.0, 3.0)))
        p.addMap[Double, Double](LANE_D, OUT_D)((_: Double) * 2.0)
        assertTrue(drainDoubles(p) == List(2.0, 4.0, 6.0))
      },
      test("Double->AnyRef (tag 19)") {
        val p = Interpreter(Reader.fromChunk(Chunk(1.5, 2.5)))
        p.addMap[Double, AnyRef](LANE_D, OUT_R)((d: Double) => s"D$d")
        assertTrue(drainGeneric(p) == List("D1.5", "D2.5"))
      },

      // ---- AnyRef input (5 outputs) ----
      test("AnyRef->Int (tag 20)") {
        val p = Interpreter(Reader.fromIterable(List("hello", "ab", "x")))
        p.addMap[AnyRef, Int](LANE_R, OUT_I)((s: AnyRef) => s.asInstanceOf[String].length)
        assertTrue(drainInts(p) == List(5, 2, 1))
      },
      test("AnyRef->Long (tag 21)") {
        val p = Interpreter(Reader.fromIterable(List("hello", "ab")))
        p.addMap[AnyRef, Long](LANE_R, OUT_L)((s: AnyRef) => s.asInstanceOf[String].length.toLong)
        assertTrue(drainLongs(p) == List(5L, 2L))
      },
      test("AnyRef->Float (tag 22)") {
        val p = Interpreter(Reader.fromIterable(List("hello", "ab")))
        p.addMap[AnyRef, Float](LANE_R, OUT_F)((s: AnyRef) => s.asInstanceOf[String].length.toFloat)
        assertTrue(drainFloats(p) == List(5.0f, 2.0f))
      },
      test("AnyRef->Double (tag 23)") {
        val p = Interpreter(Reader.fromIterable(List("hello", "ab")))
        p.addMap[AnyRef, Double](LANE_R, OUT_D)((s: AnyRef) => s.asInstanceOf[String].length.toDouble)
        assertTrue(drainDoubles(p) == List(5.0, 2.0))
      },
      test("AnyRef->AnyRef (tag 24)") {
        val p = Interpreter(Reader.fromIterable(List("hello", "world")))
        p.addMap[AnyRef, AnyRef](LANE_R, OUT_R)((s: AnyRef) => s.asInstanceOf[String].toUpperCase)
        assertTrue(drainGeneric(p) == List("HELLO", "WORLD"))
      }
    )

    // =========================================================================
    //  wrapLastRead
    // =========================================================================

    val wrapLastReadSuite = suite("wrapLastRead")(
      test("wrapLastRead transforms the source reader") {
        val p = Interpreter.unsealed(Reader.fromRange(0 until 10))
        p.addMap[Int, Int](LANE_I, OUT_I)((_: Int) + 1)
        // Wrap the source reader to limit it to 3 elements
        p.wrapLastRead((r: Reader[Any]) => Reader.fromRange(0 until 3).asInstanceOf[Reader[Any]])
        p.seal()
        val result = drainInts(p)
        // The wrapped reader produces 0,1,2; then map +1 gives 1,2,3
        assertTrue(result == List(1, 2, 3))
      },
      test("wrapLastRead on pipeline with no ops") {
        val p = Interpreter.unsealed(Reader.fromRange(0 until 100))
        p.wrapLastRead((r: Reader[Any]) => Reader.fromRange(0 until 2).asInstanceOf[Reader[Any]])
        p.seal()
        val result = drainInts(p)
        assertTrue(result == List(0, 1))
      },
      test("wrapLastRead targets the last Read op in the pipeline") {
        // Build a pipeline with a source, then wrap it; the wrap should affect the source
        val p = Interpreter.unsealed(Reader.fromRange(0 until 5))
        p.addFilter[Int](LANE_I)((_: Int) % 2 == 0)
        p.wrapLastRead((_: Reader[Any]) => Reader.fromChunk(Chunk(10, 20, 30)).asInstanceOf[Reader[Any]])
        p.seal()
        val result = drainInts(p)
        // New source is [10, 20, 30], filter even -> [10, 20, 30]
        assertTrue(result == List(10, 20, 30))
      }
    )

    // =========================================================================
    //  close() suppressed exception accumulation
    // =========================================================================

    /** A Reader that throws the given exception on close. */
    private def failingCloseReader(ex: Throwable): Reader[Int] = new Reader[Int] {
      private var _closed                                                  = false
      override def jvmType: JvmType                                        = JvmType.Int
      def isClosed: Boolean                                                = _closed
      def read[A1 >: Int](sentinel: A1): A1                                = { _closed = true; sentinel }
      override def readInt(sentinel: Long)(implicit ev: Int <:< Int): Long = { _closed = true; sentinel }
      def close(): Unit                                                    = throw ex
    }

    val closeSuppressedSuite = suite("close() suppressed exceptions")(
      test("single reader close exception is thrown") {
        val ex = new RuntimeException("boom")
        val p  = Interpreter(failingCloseReader(ex))
        // drain so it's exhausted
        drainInts(p)
        val caught = try { p.close(); null }
        catch { case t: Throwable => t }
        assertTrue(caught eq ex) && assertTrue(caught.getSuppressed.isEmpty)
      },
      test("multiple reader close exceptions: first thrown, rest suppressed") {
        val ex1 = new RuntimeException("first")
        val ex2 = new RuntimeException("second")
        val ex3 = new RuntimeException("third")
        // Build pipeline with multiple read slots via unsealed + appendRead
        val p = Interpreter.unsealed(failingCloseReader(ex1))
        p.appendRead(failingCloseReader(ex2))
        p.appendRead(failingCloseReader(ex3))
        p.seal()
        val caught = try { p.close(); null }
        catch { case t: Throwable => t }
        // The interpreter should throw the first error and suppress the rest
        assertTrue(caught eq ex1) &&
        assertTrue(caught.getSuppressed.length == 2) &&
        assertTrue(caught.getSuppressed()(0) eq ex2) &&
        assertTrue(caught.getSuppressed()(1) eq ex3)
      },
      test("close sets closed = true even when exception is thrown") {
        val ex = new RuntimeException("fail")
        val p  = Interpreter(failingCloseReader(ex))
        drainInts(p)
        try p.close()
        catch { case _: Throwable => () }
        assertTrue(p.isClosed)
      },
      test("close with no errors does not throw") {
        val p = Interpreter(Reader.fromRange(0 until 3))
        drainInts(p)
        p.close()
        assertTrue(p.isClosed)
      }
    )

    // =========================================================================
    //  appendRead on a pipeline with existing ops
    // =========================================================================

    val appendReadSuite = suite("appendRead")(
      test("unsealed creates pipeline via appendRead, outputLane is Int") {
        val p = Interpreter.unsealed(Reader.fromRange(0 until 3))
        p.seal()
        assertTrue(p.outputType == JvmType.Int) && {
          val result = drainInts(p)
          assertTrue(result == List(0, 1, 2))
        }
      },
      test("unsealed with Long reader sets outputLane to Long") {
        val p = Interpreter.unsealed(Reader.fromChunk(Chunk(1L, 2L)))
        p.seal()
        assertTrue(p.outputType == JvmType.Long) && {
          val result = drainLongs(p)
          assertTrue(result == List(1L, 2L))
        }
      },
      test("unsealed with Double reader sets outputLane to Double") {
        val p = Interpreter.unsealed(Reader.fromChunk(Chunk(1.0, 2.0, 3.0)))
        p.seal()
        assertTrue(p.outputType == JvmType.Double) && {
          val result = drainDoubles(p)
          assertTrue(result == List(1.0, 2.0, 3.0))
        }
      },
      test("unsealed with Float reader sets outputLane to Float") {
        val p = Interpreter.unsealed(Reader.fromChunk(Chunk(1.0f, 2.0f)))
        p.seal()
        assertTrue(p.outputType == JvmType.Float) && {
          val result = drainFloats(p)
          assertTrue(result == List(1.0f, 2.0f))
        }
      },
      test("unsealed with AnyRef reader sets outputLane to AnyRef") {
        val p = Interpreter.unsealed(Reader.fromIterable(List("a", "b")))
        p.seal()
        assertTrue(p.outputType == JvmType.AnyRef) && {
          val result = drainGeneric(p)
          assertTrue(result == List("a", "b"))
        }
      }
    )
  }

  // =========================================================================
  //  regressions — moved from Adversarial* specs
  // =========================================================================

  val regressionsSuite = suite("regressions")(
    RegrCombinator.regrSuite,
    RegrLaneSentinel.regrSuite,
    RegrPathConvergence.regrSuite,
    RegrRefSourceLane.regrSuite,
    RegrRepeated.regrSuite,
    RegrFlatMapInnerSeal.regrSuite,
    RegrMixedConcatLane.regrSuite,
    RegrReadByteLane.regrSuite
  )

  // BUG-R6-01: the base `Reader.readByte()` contract extracts the low byte for
  // EVERY element type — Char uses its code point, Boolean maps to 1/0 — via
  // `Reader.anyToLowByte`, the BUG-R5-03 fix applied at all 8 ref-lane sites in
  // Reader.scala. The Interpreter's `readByte` override BYPASSES that helper
  // with a raw `java.lang.Number` cast (Interpreter.scala readByte), so a
  // deep (interpreter-compiled) Char or Boolean pipeline crashes with
  // ClassCastException where the equivalent shallow pipeline (see
  // ReaderSpec "fromChunk_charElements_readByteExtractsLowByte") returns the
  // low byte. Differential oracle: shallow vs deep compilation of the same
  // program must agree; the boxed-path control proves the deep stream itself
  // is valid and only the readByte lane dispatch is at fault.
  private object RegrReadByteLane {
    private def deepChar(s: Stream[Nothing, Char]): Stream[Nothing, Char] =
      (0 until 150).foldLeft(s)((acc, _) => acc.map(identity))
    private def deepBool(s: Stream[Nothing, Boolean]): Stream[Nothing, Boolean] =
      (0 until 150).foldLeft(s)((acc, _) => acc.map(identity))

    // The reads are captured into Either rather than asserted inline: on the
    // JVM the bug throws ClassCastException, but on Scala.js the bad cast is an
    // UndefinedBehaviorError that would otherwise kill the whole test runner.
    // The Left branch can never make the test pass — it fails the equality with
    // the throwable rendered in the diff — so the broad catch is safe here.
    private def capture3(r: io.Reader[?]): Either[String, List[Int]] =
      try Right(List(r.readByte(), r.readByte(), r.readByte()))
      catch { case t: Throwable => Left(t.toString) }

    val regrSuite = suite("AdversarialReadByteLaneSpec (interpreter)")(
      test("interpreter_charElements_readByteExtractsLowByte") {
        val r = Stream.compileToReader(deepChar(Stream.fromChunk(Chunk('A', 'B'))))
        assertTrue(capture3(r) == Right(List(0x41, 0x42, -1)))
      },
      test("interpreter_booleanElements_readByteExtractsLowBit") {
        val r = Stream.compileToReader(deepBool(Stream.fromChunk(Chunk(true, false))))
        assertTrue(capture3(r) == Right(List(1, 0, -1)))
      },
      // Differential control (passes): the same deep pipelines deliver their
      // elements through the generic boxed path, so the stream construction is
      // valid; only the interpreter's readByte lane dispatch diverges.
      test("interpreter_charElements_boxedPathDelivers") {
        assertTrue(deepChar(Stream.fromChunk(Chunk('A', 'B'))).runCollect == Right(Chunk('A', 'B')))
      }
    )
  }

  // BUG-R5-04: `Interpreter.wrapLastRead` rewrites the segment's last READ op
  // (including its lane TAG) but never refreshes the interpreter's
  // `outputLane`. A lane-CHANGING wrap — a mixed-lane `++`, whose ConcatReader
  // advertises the AnyRef lane over an Int-lane head — leaves `outputLane`
  // stale at Int, so the next fused op's `reconcileLane` inserts a bogus
  // Int->Ref bridge that overwrites the just-read ref register with the
  // never-written Int register: every element silently becomes 0.
  // Differential oracle: shallow vs deep (interpreter) compilation of the
  // same program must agree.
  private object RegrMixedConcatLane {
    private def deepRef(s: Stream[Nothing, Int | String]): Stream[Nothing, Int | String] =
      (0 until 150).foldLeft(s)((acc, _) => acc.map(x => x))

    val regrSuite = suite("AdversarialMixedConcatLaneSpec")(
      test("deep identity maps over a mixed-lane `++` preserve the elements") {
        val mixed: Stream[Nothing, Int | String] = Stream(1, 2, 3) ++ Stream("a", "b")
        val shallow                              = mixed.runCollect
        val deep                                 = deepRef(mixed).runCollect
        assertTrue(shallow.exists(_.length == 5), deep == shallow)
      }
    )
  }

  private object RegrCombinator {
    private def deepInt(s: Stream[Nothing, Int]): Stream[Nothing, Int] =
      (0 until 150).foldLeft(s)((acc, _) => acc.map(identity))

    val regrSuite = suite("AdversarialInterpreterCombinatorSpec")(
      // ---- direction 1: deep -> combinator ----
      test("deep_then_intersperse [AdversarialInterpreterCombinatorSpec]") {
        val s = deepInt(Stream.range(1, 4)).intersperse(0)
        assertTrue(s.runCollect == Right(Chunk(1, 0, 2, 0, 3)))
      },
      test("deep_then_intersperse_empty [AdversarialInterpreterCombinatorSpec]") {
        val s = deepInt(Stream.empty: Stream[Nothing, Int]).intersperse(0)
        assertTrue(s.runCollect == Right(Chunk.empty[Int]))
      },
      test("deep_then_chunked [AdversarialInterpreterCombinatorSpec]") {
        val s = deepInt(Stream.range(0, 7)).chunked(3)
        assertTrue(s.runCollect == Right(Chunk(Chunk(0, 1, 2), Chunk(3, 4, 5), Chunk(6))))
      },
      test("deep_then_grouped_alias [AdversarialInterpreterCombinatorSpec]") {
        val s = deepInt(Stream.range(0, 4)).grouped(2)
        assertTrue(s.runCollect == Right(Chunk(Chunk(0, 1), Chunk(2, 3))))
      },
      test("deep_then_chunked_one [AdversarialInterpreterCombinatorSpec]") {
        val s = deepInt(Stream.range(0, 3)).chunked(1)
        assertTrue(s.runCollect == Right(Chunk(Chunk(0), Chunk(1), Chunk(2))))
      },
      test("deep_then_chunked_bigger_than_len [AdversarialInterpreterCombinatorSpec]") {
        val s = deepInt(Stream.range(0, 3)).chunked(100)
        assertTrue(s.runCollect == Right(Chunk(Chunk(0, 1, 2))))
      },
      test("deep_then_chunked_empty [AdversarialInterpreterCombinatorSpec]") {
        val s = deepInt(Stream.empty: Stream[Nothing, Int]).chunked(3)
        assertTrue(s.runCollect == Right(Chunk.empty[Chunk[Int]]))
      },
      test("deep_then_mapAccum [AdversarialInterpreterCombinatorSpec]") {
        // running sum threaded as state, emit running total
        val s = deepInt(Stream.range(1, 5)).mapAccum(0)((acc, x) => (acc + x, acc + x))
        assertTrue(s.runCollect == Right(Chunk(1, 3, 6, 10)))
      },
      test("deep_then_collect [AdversarialInterpreterCombinatorSpec]") {
        val s = deepInt(Stream.range(0, 10)).collect { case n if n % 3 == 0 => n * 10 }
        assertTrue(s.runCollect == Right(Chunk(0, 30, 60, 90)))
      },
      test("deep_then_distinct [AdversarialInterpreterCombinatorSpec]") {
        val s = deepInt(Stream(1, 1, 2, 3, 3, 3, 1)).distinct
        assertTrue(s.runCollect == Right(Chunk(1, 2, 3)))
      },
      test("deep_then_scan [AdversarialInterpreterCombinatorSpec]") {
        val s = deepInt(Stream.range(1, 4)).scan(0)(_ + _)
        assertTrue(s.runCollect == Right(Chunk(0, 1, 3, 6)))
      },
      // ---- direction 2: combinator -> deep (interpreter consumes boxed FromReader) ----
      test("intersperse_then_deep [AdversarialInterpreterCombinatorSpec]") {
        val s = deepInt(Stream.range(1, 4).intersperse(0))
        assertTrue(s.runCollect == Right(Chunk(1, 0, 2, 0, 3)))
      },
      test("mapAccum_then_deep [AdversarialInterpreterCombinatorSpec]") {
        val s = deepInt(Stream.range(1, 5).mapAccum(0)((acc, x) => (acc + x, acc + x)))
        assertTrue(s.runCollect == Right(Chunk(1, 3, 6, 10)))
      },
      test("distinct_then_deep [AdversarialInterpreterCombinatorSpec]") {
        val s = deepInt(Stream(5, 5, 6, 7, 7).distinct)
        assertTrue(s.runCollect == Right(Chunk(5, 6, 7)))
      },
      test("scan_then_deep [AdversarialInterpreterCombinatorSpec]") {
        val s = deepInt(Stream.range(1, 4).scan(0)(_ + _))
        assertTrue(s.runCollect == Right(Chunk(0, 1, 3, 6)))
      },
      // ---- combined with early termination ----
      test("deep_then_intersperse_then_take_midSeparator [AdversarialInterpreterCombinatorSpec]") {
        // [1,0,2,0,3] take 2 -> [1,0]
        val s = deepInt(Stream.range(1, 4)).intersperse(0).take(2)
        assertTrue(s.runCollect == Right(Chunk(1, 0)))
      },
      test("deep_then_chunked_then_take [AdversarialInterpreterCombinatorSpec]") {
        val s = deepInt(Stream.range(0, 10)).chunked(3).take(2)
        assertTrue(s.runCollect == Right(Chunk(Chunk(0, 1, 2), Chunk(3, 4, 5))))
      },
      // ---- repeated over combinator at interpreter depth ----
      test("deep_then_scan_repeated_take [AdversarialInterpreterCombinatorSpec]") {
        val s = deepInt(Stream.range(1, 3)).scan(0)(_ + _).repeated.take(7)
        // one cycle of scan(1,2) over [1,2] = [0,1,3]; repeated
        assertTrue(s.runCollect == Right(Chunk(0, 1, 3, 0, 1, 3, 0)))
      },
      test("deep_then_intersperse_repeated_take [AdversarialInterpreterCombinatorSpec]") {
        val s = deepInt(Stream.range(1, 3)).intersperse(9).repeated.take(8)
        // one cycle: [1,9,2]; repeated
        assertTrue(s.runCollect == Right(Chunk(1, 9, 2, 1, 9, 2, 1, 9)))
      }
    )
  }

  private object RegrLaneSentinel {
    private val Depth = 160

    private def deepLong(s: Stream[Nothing, Long]): Stream[Nothing, Long] =
      (0 until Depth).foldLeft(s)((acc, _) => acc.map(x => x))
    private def deepDouble(s: Stream[Nothing, Double]): Stream[Nothing, Double] =
      (0 until Depth).foldLeft(s)((acc, _) => acc.map(x => x))
    private def deepByte(s: Stream[Nothing, Byte]): Stream[Nothing, Byte] =
      (0 until Depth).foldLeft(s)((acc, _) => acc.map(x => x))

    private def lc(xs: Seq[Long]): Stream[Nothing, Long]     = Stream.fromChunk(Chunk.fromArray(xs.toArray))
    private def dc(xs: Seq[Double]): Stream[Nothing, Double] = Stream.fromChunk(Chunk.fromArray(xs.toArray))
    private def bc(xs: Seq[Byte]): Stream[Nothing, Byte]     = Stream.fromChunk(Chunk.fromArray(xs.toArray))

    val regrSuite = suite("AdversarialInterpreterLaneSentinelSpec")(
      test(
        "Long lane: deep interpreter chain preserves real Long.MaxValue / Long.MinValue elements [AdversarialInterpreterLaneSentinelSpec]"
      ) {
        val data = List(1L, Long.MaxValue, -2L, Long.MinValue, Long.MaxValue, 0L)
        assertTrue(deepLong(lc(data)).runCollect == Right(Chunk.fromArray(data.toArray))) &&
        assertTrue(deepLong(lc(data)).count == Right(data.length.toLong)) &&
        assertTrue(deepLong(lc(data)).runFold(0L)((a, _) => a + 1L) == Right(data.length.toLong))
      },
      test(
        "Double lane: deep interpreter chain preserves real Double.MaxValue / NaN / -0.0 elements [AdversarialInterpreterLaneSentinelSpec]"
      ) {
        val data = List(1.0, Double.MaxValue, Double.NaN, -0.0, Double.MaxValue, 2.0)
        val got  = deepDouble(dc(data)).runCollect.toOption.get.toList
        val ok   = got.length == data.length && got.zip(data).forall { case (g, e) =>
          java.lang.Double.doubleToRawLongBits(g) == java.lang.Double.doubleToRawLongBits(e)
        }
        assertTrue(ok) &&
        assertTrue(deepDouble(dc(data)).count == Right(data.length.toLong))
      },
      test(
        "Byte lane: deep interpreter chain preserves all byte values incl. -1 / MinValue [AdversarialInterpreterLaneSentinelSpec]"
      ) {
        val data = List(0.toByte, (-1).toByte, Byte.MinValue, Byte.MaxValue, 127.toByte, (-128).toByte)
        assertTrue(deepByte(bc(data)).runCollect == Right(Chunk.fromArray(data.toArray))) &&
        assertTrue(deepByte(bc(data)).count == Right(data.length.toLong))
      },
      test(
        "Long-lane interpreter source feeding stateful scan preserves Long.MaxValue [AdversarialInterpreterLaneSentinelSpec]"
      ) {
        val data = List(10L, Long.MaxValue, 3L)
        val scn  = deepLong(lc(data)).scan(0L)((_, a) => a).runCollect.toOption.get.toList
        assertTrue(scn == 0L :: data)
      },
      test(
        "Long-lane interpreter source feeding chunked/sliding preserves Long.MaxValue [AdversarialInterpreterLaneSentinelSpec]"
      ) {
        val data = List(1L, Long.MaxValue, Long.MaxValue, 4L)
        val ch   = deepLong(lc(data)).chunked(2).runCollect.toOption.get.map(_.toList).toList
        val sl   = deepLong(lc(data)).sliding(2).runCollect.toOption.get.map(_.toList).toList
        assertTrue(ch == data.grouped(2).map(_.toList).toList) &&
        assertTrue(sl == data.sliding(2).map(_.toList).toList)
      },
      test(
        "Long-lane deep chain + scan + repeated restarts cleanly with sentinel data [AdversarialInterpreterLaneSentinelSpec]"
      ) {
        val data = List(1L, Long.MaxValue)
        val got  = deepLong(lc(data)).scan(0L)((_, a) => a).repeated.take(6).runCollect.toOption.get.toList
        // one cycle: scan re-emits [0, 1, MaxValue]; repeated
        assertTrue(got == List(0L, 1L, Long.MaxValue, 0L, 1L, Long.MaxValue))
      }
    )
  }

  private object RegrPathConvergence {
    // Force the interpreter fallback by chaining > DepthCutoff (100) identity maps.
    private def deepInt(s: Stream[Nothing, Int]): Stream[Nothing, Int] =
      (0 until 150).foldLeft(s)((acc, _) => acc.map(identity))
    private def deepLong(s: Stream[Nothing, Long]): Stream[Nothing, Long] =
      (0 until 150).foldLeft(s)((acc, _) => acc.map(identity))

    val regrSuite = suite("AdversarialInterpreterPathConvergenceSpec")(
      test("interp_take_then_drop [AdversarialInterpreterPathConvergenceSpec]") {
        val s = deepInt(Stream.range(0, 10)).take(5).drop(3)
        assertTrue(s.runCollect == Right(Chunk(3, 4)))
      },
      test("interp_drop_then_take [AdversarialInterpreterPathConvergenceSpec]") {
        val s = deepInt(Stream.range(0, 10)).drop(3).take(5)
        assertTrue(s.runCollect == Right(Chunk(3, 4, 5, 6, 7)))
      },
      test("interp_filter [AdversarialInterpreterPathConvergenceSpec]") {
        val s = deepInt(Stream.range(0, 10)).filter(_ % 2 == 0)
        assertTrue(s.runCollect == Right(Chunk(0, 2, 4, 6, 8)))
      },
      test("interp_long_maxvalue [AdversarialInterpreterPathConvergenceSpec]") {
        val s = deepLong(Stream(1L, Long.MaxValue, 3L))
        assertTrue(s.runCollect == Right(Chunk(1L, Long.MaxValue, 3L)))
      },
      test("interp_long_maxvalue_count [AdversarialInterpreterPathConvergenceSpec]") {
        val s = deepLong(Stream(1L, Long.MaxValue, Long.MaxValue, 3L))
        assertTrue(s.count == Right(4L))
      },
      test("interp_takeWhile [AdversarialInterpreterPathConvergenceSpec]") {
        val s = deepInt(Stream.range(0, 10)).takeWhile(_ < 4)
        assertTrue(s.runCollect == Right(Chunk(0, 1, 2, 3)))
      },
      test("interp_repeated [AdversarialInterpreterPathConvergenceSpec]") {
        val s = deepInt(Stream.range(0, 3)).repeated.take(7)
        assertTrue(s.runCollect == Right(Chunk(0, 1, 2, 0, 1, 2, 0)))
      },
      test("interp_then_scan [AdversarialInterpreterPathConvergenceSpec]") {
        val s = deepInt(Stream.range(1, 4)).scan(0)(_ + _)
        assertTrue(s.runCollect == Right(Chunk(0, 1, 3, 6)))
      },
      test("interp_double_nan_count [AdversarialInterpreterPathConvergenceSpec]") {
        val s = (0 until 150).foldLeft(Stream(1.0, Double.NaN, Double.MaxValue))((acc, _) => acc.map(identity))
        assertTrue(s.count == Right(3L))
      },
      test("interp_filter_take_drop_combo [AdversarialInterpreterPathConvergenceSpec]") {
        val s = deepInt(Stream.range(0, 20)).filter(_ % 2 == 0).drop(2).take(3)
        assertTrue(s.runCollect == Right(Chunk(4, 6, 8)))
      }
    )
  }

  private object RegrRefSourceLane {
    // Force the Interpreter path: DepthCutoff is 100, so 101 linear ops compile to
    // the segmented interpreter rather than the shallow reader-composition path.
    private val cutoff = 101

    private def deepLong(s: Stream[Nothing, Long]): Stream[Nothing, Long] = {
      var cur = s; var i = 0
      while (i < cutoff) { cur = cur.map(_ + 0L); i += 1 }
      cur
    }

    private def deepInt(s: Stream[Nothing, Int]): Stream[Nothing, Int] = {
      var cur = s; var i = 0
      while (i < cutoff) { cur = cur.map(_ + 0); i += 1 }
      cur
    }

    val regrSuite = suite("AdversarialInterpreterRefSourceLaneSpec")(
      test("interpreter_refSourceLongMap_preservesValues [AdversarialInterpreterRefSourceLaneSpec]") {
        val input  = List(1L, 2L, 3L)
        val result = deepLong(Stream.fromIterable(input)).runCollect
        assertTrue(result.map(_.toList) == Right(input))
      },
      test("interpreter_refSourceIntMap_preservesValues [AdversarialInterpreterRefSourceLaneSpec]") {
        val input  = List(10, 20, 30)
        val result = deepInt(Stream.fromIterable(input)).runCollect
        assertTrue(result.map(_.toList) == Right(input))
      }
    )
  }

  private object RegrRepeated {
    // Force the fused Interpreter compilation path with a long identity-map
    // prefix (mirrors AdversarialDeepFlatMapSpec), keeping the element lane.
    private def deepInt(s: Stream[Nothing, Int]): Stream[Nothing, Int] = {
      var r = s; var i = 0
      while (i < 150) { r = r.map((x: Int) => x); i += 1 }
      r
    }
    private def deepLong(s: Stream[Nothing, Long]): Stream[Nothing, Long] = {
      var r = s; var i = 0
      while (i < 150) { r = r.map((x: Long) => x); i += 1 }
      r
    }
    private def deepDouble(s: Stream[Nothing, Double]): Stream[Nothing, Double] = {
      var r = s; var i = 0
      while (i < 150) { r = r.map((x: Double) => x); i += 1 }
      r
    }
    private def deepFloat(s: Stream[Nothing, Float]): Stream[Nothing, Float] = {
      var r = s; var i = 0
      while (i < 150) { r = r.map((x: Float) => x); i += 1 }
      r
    }
    private def deepRef(s: Stream[Nothing, String]): Stream[Nothing, String] = {
      var r = s; var i = 0
      while (i < 150) { r = r.map((x: String) => x); i += 1 }
      r
    }

    val regrSuite = suite("AdversarialRepeatedInterpreterSpec")(
      test("deep Int stream repeated cycles across boundary (lane I) [AdversarialRepeatedInterpreterSpec]") {
        assertTrue(deepInt(Stream(1, 2)).repeated.take(5).runCollect == Right(Chunk(1, 2, 1, 2, 1)))
      },
      test("deep Long stream repeated cycles across boundary (lane L) [AdversarialRepeatedInterpreterSpec]") {
        assertTrue(deepLong(Stream(1L, 2L)).repeated.take(5).runCollect == Right(Chunk(1L, 2L, 1L, 2L, 1L)))
      },
      test("deep Double stream repeated cycles across boundary (lane D) [AdversarialRepeatedInterpreterSpec]") {
        assertTrue(deepDouble(Stream(1.0, 2.0)).repeated.take(5).runCollect == Right(Chunk(1.0, 2.0, 1.0, 2.0, 1.0)))
      },
      test("deep Float stream repeated cycles across boundary (lane F) [AdversarialRepeatedInterpreterSpec]") {
        assertTrue(
          deepFloat(Stream(1.0f, 2.0f)).repeated.take(5).runCollect == Right(Chunk(1.0f, 2.0f, 1.0f, 2.0f, 1.0f))
        )
      },
      test("deep Ref stream repeated cycles across boundary (lane R) — control [AdversarialRepeatedInterpreterSpec]") {
        assertTrue(deepRef(Stream("a", "b")).repeated.take(5).runCollect == Right(Chunk("a", "b", "a", "b", "a")))
      }
    )
  }

  // BUG: the INLINE interpreter driver (`compileInterpreterStackSafe`, used by
  // `handlePush` to compile a flatMap INNER stream into the running pipeline)
  // ignores `isSealBefore`. A seal-before node in the inner spine
  // (take/drop/takeWhile/concat) is compiled via `wrapLastRead`, which rewrites
  // only the inner SOURCE read op — re-ordering it BELOW any map/filter ops that
  // were fused after the read. The segmented driver and the shallow `compile`
  // path both apply these combinators ABOVE the fused upstream, so the deep
  // (interpreter) path silently produces different elements than the shallow
  // path for the same program. Differential oracle: shallow vs deep.
  private object RegrFlatMapInnerSeal {
    private def deepInt(s: Stream[Nothing, Int]): Stream[Nothing, Int] =
      (0 until 150).foldLeft(s)((acc, _) => acc.map(identity))

    val regrSuite = suite("AdversarialFlatMapInnerSealSpec")(
      test("flatMap inner `map ++ tail`: tail segment must NOT be re-mapped at interpreter depth") {
        def inner(i: Int): Stream[Nothing, Int] = Stream(1, 2).map(_ * 10) ++ Stream(100)
        val shallow                             = Stream(0, 1).flatMap(inner).runCollect
        val deep                                = deepInt(Stream(0, 1)).flatMap(inner).runCollect
        assertTrue(
          shallow == Right(Chunk(10, 20, 100, 10, 20, 100)),
          deep == shallow
        )
      },
      test("flatMap inner `map.takeWhile`: predicate must see POST-map elements at interpreter depth") {
        def inner(i: Int): Stream[Nothing, Int] = Stream.range(0, 5).map(_ * 10).takeWhile(_ < 30)
        val shallow                             = Stream(0).flatMap(inner).runCollect
        val deep                                = deepInt(Stream(0)).flatMap(inner).runCollect
        assertTrue(
          shallow == Right(Chunk(0, 10, 20)),
          deep == shallow
        )
      },
      test("flatMap inner `filter.take`: take must count POST-filter elements at interpreter depth") {
        def inner(i: Int): Stream[Nothing, Int] = Stream.range(0, 10).filter(_ % 2 == 1).take(2)
        val shallow                             = Stream(0).flatMap(inner).runCollect
        val deep                                = deepInt(Stream(0)).flatMap(inner).runCollect
        assertTrue(
          shallow == Right(Chunk(1, 3)),
          deep == shallow
        )
      },
      test("flatMap inner `filter.drop`: drop must discard POST-filter elements at interpreter depth") {
        def inner(i: Int): Stream[Nothing, Int] = Stream.range(0, 10).filter(_ % 2 == 1).drop(1)
        val shallow                             = Stream(0).flatMap(inner).runCollect
        val deep                                = deepInt(Stream(0)).flatMap(inner).runCollect
        assertTrue(
          shallow == Right(Chunk(3, 5, 7, 9)),
          deep == shallow
        )
      },
      // Control: a seal-before node with NOTHING fused above the inner source is
      // unaffected (wrapLastRead wraps the bare source read) — guards against
      // over-fixing.
      test("control: flatMap inner bare `take` agrees between shallow and deep paths") {
        def inner(i: Int): Stream[Nothing, Int] = Stream.range(0, 10).take(2)
        val shallow                             = Stream(0, 1).flatMap(inner).runCollect
        val deep                                = deepInt(Stream(0, 1)).flatMap(inner).runCollect
        assertTrue(
          shallow == Right(Chunk(0, 1, 0, 1)),
          deep == shallow
        )
      }
    )
  }
}
