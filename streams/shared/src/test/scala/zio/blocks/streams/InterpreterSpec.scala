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

  def spec = suite("Interpreter V3")(
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
    edgeCaseSuite
  )

  // ---- drain helpers ----

  private def drainInts(p: Interpreter, sentinel: Long = Long.MinValue): List[Int] = {
    val buf = scala.collection.mutable.ListBuffer[Int]()
    var v   = p.readInt(sentinel)(using unsafeEvidence)
    while (v != sentinel) {
      buf += v.toInt
      v = p.readInt(sentinel)(using unsafeEvidence)
    }
    buf.toList
  }

  private def drainLongs(p: Interpreter, sentinel: Long = Long.MaxValue): List[Long] = {
    val buf = scala.collection.mutable.ListBuffer[Long]()
    var v   = p.readLong(sentinel)(using unsafeEvidence)
    while (v != sentinel) {
      buf += v
      v = p.readLong(sentinel)(using unsafeEvidence)
    }
    buf.toList
  }

  private def drainFloats(p: Interpreter, sentinel: Double = Double.MaxValue): List[Float] = {
    val buf = scala.collection.mutable.ListBuffer[Float]()
    var v   = p.readFloat(sentinel)(using unsafeEvidence)
    while (v != sentinel) {
      buf += v.toFloat
      v = p.readFloat(sentinel)(using unsafeEvidence)
    }
    buf.toList
  }

  private def drainDoubles(p: Interpreter, sentinel: Double = Double.MaxValue): List[Double] = {
    val buf = scala.collection.mutable.ListBuffer[Double]()
    var v   = p.readDouble(sentinel)(using unsafeEvidence)
    while (v != sentinel) {
      buf += v
      v = p.readDouble(sentinel)(using unsafeEvidence)
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
          laneOf(JvmType.Boolean) == LANE_I &&
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
      val p = Interpreter(source)
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
      val p = Interpreter(source)
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

      val p = Interpreter(source)
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

      val p = Interpreter(source)
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

      val p = Interpreter(source)
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

      val p = Interpreter(source)
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
      val p = Interpreter(source)
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
      val p = Interpreter(source)
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
      val p      = Interpreter(source)
      val result = drainInts(p)
      assertTrue(result == List(0, 1, 2, 3, 4))
    },
    test("empty source returns sentinel immediately") {
      val source   = Reader.fromRange(0 until 0)
      val p        = Interpreter(source)
      val sentinel = Long.MinValue
      val v        = p.readInt(sentinel)(using unsafeEvidence)
      assertTrue(v == sentinel)
    },
    test("sentinel returned when all elements consumed") {
      val source   = Reader.fromChunk(Chunk(42))
      val p        = Interpreter(source)
      val sentinel = Long.MinValue
      val first    = p.readInt(sentinel)(using unsafeEvidence)
      val second   = p.readInt(sentinel)(using unsafeEvidence)
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

      val p = Interpreter(source)
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

      val p = Interpreter(source)
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

      val p = Interpreter(source)
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

      val p = Interpreter(source)
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

      val p = Interpreter(source)
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

      val p = Interpreter(source)
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
      val p = Interpreter(source)
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
      val p = Interpreter(source)
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

      val p = Interpreter(source)
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

      val p = Interpreter(source)
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

      val p = Interpreter(source)
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

      val p = Interpreter(source)
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
      val p = Interpreter(source)
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
      p.addPush[Int](LANE_I)(pushFn)
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
      // 5 maps: source -> MappedInt -> MappedInt -> MappedInt -> MappedInt -> MappedInt
      var s: Stream[Nothing, Int] = Stream.range(0, 5)
      var i                       = 0
      while (i < 5) { s = s.map(_ + 1); i += 1 }
      val reader = s.compile(0)
      // Top level is MappedInt
      assertTrue(reader.isInstanceOf[Reader.MappedInt]) && {
        // Walk the chain — every layer should be MappedInt, none Interpreter
        var r: Reader[?] = reader
        var allMapped    = true
        var depth        = 0
        while (r.isInstanceOf[Reader.MappedInt]) {
          depth += 1
          r = r.asInstanceOf[Reader.MappedInt].source
        }
        // After 5 MappedInt wrappers, the leaf should be the range reader (not Interpreter)
        assertTrue(depth == 5) &&
        assertTrue(!r.isInstanceOf[Interpreter])
      }
    },
    test("mixed ops below cutoff: map + filter both decorate") {
      val reader = Stream.range(0, 10).map(_ + 1).filter(_ % 2 == 0).map(_ * 3).compile(0)
      // Outermost is MappedInt (last map), then FilteredInt, then MappedInt, then source
      assertTrue(reader.isInstanceOf[Reader.MappedInt]) && {
        val m1 = reader.asInstanceOf[Reader.MappedInt]
        assertTrue(m1.source.isInstanceOf[Reader.FilteredInt]) && {
          val f1 = m1.source.asInstanceOf[Reader.FilteredInt]
          assertTrue(f1.source.isInstanceOf[Reader.MappedInt]) && {
            val m2 = f1.source.asInstanceOf[Reader.MappedInt]
            assertTrue(!m2.source.isInstanceOf[Interpreter])
          }
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
      assertTrue(p.readInt(Long.MinValue)(using unsafeEvidence) == Long.MinValue)
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
      val v1     = p.readInt(Long.MinValue)(using unsafeEvidence)
      val v2     = p.readInt(Long.MinValue)(using unsafeEvidence)
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
      assertTrue(p.readInt(Long.MinValue)(using unsafeEvidence) == Long.MinValue)
    },
    test("read after close returns sentinel (readLong)") {
      val source = Reader.fromChunk(Chunk(1L, 2L))
      val p      = Interpreter(source)
      p.close()
      assertTrue(p.readLong(Long.MaxValue)(using unsafeEvidence) == Long.MaxValue)
    },
    test("read after close returns sentinel (readFloat)") {
      val source = Reader.fromChunk(Chunk(1.0f, 2.0f))
      val p      = Interpreter(source)
      p.close()
      assertTrue(p.readFloat(Double.MaxValue)(using unsafeEvidence) == Double.MaxValue)
    },
    test("read after close returns sentinel (readDouble)") {
      val source = Reader.fromChunk(Chunk(1.0, 2.0))
      val p      = Interpreter(source)
      p.close()
      assertTrue(p.readDouble(Double.MaxValue)(using unsafeEvidence) == Double.MaxValue)
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
      val first  = p.readInt(Long.MinValue)(using unsafeEvidence)
      p.close()
      val afterClose = p.readInt(Long.MinValue)(using unsafeEvidence)
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
      assertTrue(p.readInt(Long.MinValue)(using unsafeEvidence) == Long.MinValue)
    },
    test("skip more than available returns sentinel") {
      val source = Reader.fromRange(0 until 3)
      val p      = Interpreter(source)
      p.skip(100)
      assertTrue(p.readInt(Long.MinValue)(using unsafeEvidence) == Long.MinValue)
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
      val v1 = p.readInt(Long.MinValue)(using unsafeEvidence)
      val v2 = p.readInt(Long.MinValue)(using unsafeEvidence)
      val v3 = p.readInt(Long.MinValue)(using unsafeEvidence)
      val v4 = p.readInt(Long.MinValue)(using unsafeEvidence)
      assertTrue(v1 == 0L && v2 == 1L && v3 == 2L && v4 == Long.MinValue)
    },
    test("readLong on Long pipeline returns values without boxing") {
      val p  = Interpreter(Reader.fromChunk(Chunk(100L, 200L)))
      val v1 = p.readLong(Long.MaxValue)(using unsafeEvidence)
      val v2 = p.readLong(Long.MaxValue)(using unsafeEvidence)
      val v3 = p.readLong(Long.MaxValue)(using unsafeEvidence)
      assertTrue(v1 == 100L && v2 == 200L && v3 == Long.MaxValue)
    },
    test("readFloat on Float pipeline returns values without boxing") {
      val p  = Interpreter(Reader.fromChunk(Chunk(1.5f, 2.5f)))
      val v1 = p.readFloat(Double.MaxValue)(using unsafeEvidence)
      val v2 = p.readFloat(Double.MaxValue)(using unsafeEvidence)
      val v3 = p.readFloat(Double.MaxValue)(using unsafeEvidence)
      assertTrue(v1 == 1.5f.toDouble && v2 == 2.5f.toDouble && v3 == Double.MaxValue)
    },
    test("readDouble on Double pipeline returns values without boxing") {
      val p  = Interpreter(Reader.fromChunk(Chunk(3.14, 2.71)))
      val v1 = p.readDouble(Double.MaxValue)(using unsafeEvidence)
      val v2 = p.readDouble(Double.MaxValue)(using unsafeEvidence)
      val v3 = p.readDouble(Double.MaxValue)(using unsafeEvidence)
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
      val v1 = p.readInt(Long.MinValue)(using unsafeEvidence)
      val v2 = p.readInt(Long.MinValue)(using unsafeEvidence)
      val v3 = p.readInt(Long.MinValue)(using unsafeEvidence)
      val v4 = p.readInt(Long.MinValue)(using unsafeEvidence)
      assertTrue(v1 == 100L && v2 == 101L && v3 == 102L && v4 == Long.MinValue)
    },
    test("readLong with map on Long pipeline") {
      val p = Interpreter(Reader.fromChunk(Chunk(1L, 2L, 3L)))
      p.addMap[Long, Long](LANE_L, OUT_L)((_: Long) * 10L)
      val v1 = p.readLong(Long.MaxValue)(using unsafeEvidence)
      val v2 = p.readLong(Long.MaxValue)(using unsafeEvidence)
      val v3 = p.readLong(Long.MaxValue)(using unsafeEvidence)
      val v4 = p.readLong(Long.MaxValue)(using unsafeEvidence)
      assertTrue(v1 == 10L && v2 == 20L && v3 == 30L && v4 == Long.MaxValue)
    },
    test("readFloat with map on Float pipeline") {
      val p = Interpreter(Reader.fromChunk(Chunk(1.0f, 2.0f)))
      p.addMap[Float, Float](LANE_F, OUT_F)((_: Float) + 0.5f)
      val v1 = p.readFloat(Double.MaxValue)(using unsafeEvidence)
      val v2 = p.readFloat(Double.MaxValue)(using unsafeEvidence)
      val v3 = p.readFloat(Double.MaxValue)(using unsafeEvidence)
      assertTrue(v1 == 1.5f.toDouble && v2 == 2.5f.toDouble && v3 == Double.MaxValue)
    },
    test("readDouble with map on Double pipeline") {
      val p = Interpreter(Reader.fromChunk(Chunk(1.0, 2.0)))
      p.addMap[Double, Double](LANE_D, OUT_D)((_: Double) + 0.1)
      val v1 = p.readDouble(Double.MaxValue)(using unsafeEvidence)
      val v2 = p.readDouble(Double.MaxValue)(using unsafeEvidence)
      val v3 = p.readDouble(Double.MaxValue)(using unsafeEvidence)
      assertTrue(v1 == 1.1 && v2 == 2.1 && v3 == Double.MaxValue)
    },
    test("readInt with cross-type map Int→Long emits in Long lane via readLong") {
      val p = Interpreter(Reader.fromRange(0 until 3))
      p.addMap[Int, Long](LANE_I, OUT_L)((_: Int).toLong * 100L)
      val v1 = p.readLong(Long.MaxValue)(using unsafeEvidence)
      val v2 = p.readLong(Long.MaxValue)(using unsafeEvidence)
      val v3 = p.readLong(Long.MaxValue)(using unsafeEvidence)
      val v4 = p.readLong(Long.MaxValue)(using unsafeEvidence)
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
      p.addPush[Int](LANE_I)(pushFn)
      val result = drainInts(p)
      // 0 → [0, 1], 1 → [100, 101], 2 → [200, 201]
      assertTrue(result == List(0, 1, 100, 101, 200, 201))
    }
  )
}
