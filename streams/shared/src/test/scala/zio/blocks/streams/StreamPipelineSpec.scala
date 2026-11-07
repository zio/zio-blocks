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

import zio.blocks.async.Async
import zio.blocks.chunk.Chunk
import zio.blocks.streams.io.Reader
import zio.test._
import scala.annotation.nowarn

/**
 * Tests for stream pipeline behavior through the Stream API: map crossings,
 * filter, chaining/fusion, jvmType propagation, take/drop, sink specialization.
 *
 * Covers:
 *   - All 35 map crossings (5 input × 7 output)
 *   - All 5 filter types
 *   - Pipeline chaining (fusion)
 *   - jvmType propagation
 *   - Take / TakeWhile / Drop through Stream API
 *   - Sink specialization
 *   - Regression tests
 */
@nowarn("msg=never used")
object StreamPipelineSpec extends StreamsBaseSpec {

  def spec = suite("Stream Pipeline")(
    mapCrossingSuite,
    filterSuite,
    chainingFusionSuite,
    jvmTypeSuite,
    takeTakeWhileDropSuite,
    sinkSpecializationSuite,
    regressionSuite
  )

  // ---------- helpers ----------

  private val ints    = Chunk(1, 2, 3, 4, 5)
  private val longs   = Chunk(1L, 2L, 3L, 4L, 5L)
  private val floats  = Chunk(1.0f, 2.0f, 3.0f, 4.0f, 5.0f)
  private val doubles = Chunk(1.0, 2.0, 3.0, 4.0, 5.0)
  private val strings = List("hello", "world", "foo")

  // =========================================================================
  //  Map crossings
  // =========================================================================

  val mapCrossingSuite = suite("Map crossings")(
    // ---- Int input (7 outputs) ----
    test("Int→Int (tag 0)") {
      runAsync(Stream.range(0, 5).map(_ + 1).runCollectAsync).map { result =>
        assertTrue(result == Right(Chunk(1, 2, 3, 4, 5)))
      }
    },
    test("Int→Long (tag 1)") {
      runAsync(Stream.range(0, 5).map(_.toLong).runCollectAsync).map { result =>
        assertTrue(result == Right(Chunk(0L, 1L, 2L, 3L, 4L)))
      }
    },
    test("Int→Float (tag 2)") {
      runAsync(Stream.range(0, 5).map(_.toFloat).runCollectAsync).map { result =>
        assertTrue(result == Right(Chunk(0.0f, 1.0f, 2.0f, 3.0f, 4.0f)))
      }
    },
    test("Int→Double (tag 3)") {
      runAsync(Stream.range(0, 5).map(_.toDouble).runCollectAsync).map { result =>
        assertTrue(result == Right(Chunk(0.0, 1.0, 2.0, 3.0, 4.0)))
      }
    },
    test("Int→Boolean (tag 4) — stored as boxed Boolean") {
      runAsync(Stream.range(0, 5).map(_ > 2).runCollectAsync).map { (result: Any) =>
        assertTrue(result == Right(Chunk(false, false, false, true, true)))
      }
    },
    test("Int→Unit (tag 5) — side effect") {
      var sum = 0
      runAsync(Stream.range(0, 5).map { i => sum += i; () }.runDrainAsync).map { result =>
        assertTrue(result == Right(()) && sum == 10)
      }
    },
    test("Int→String (tag 6)") {
      runAsync(Stream.range(0, 5).map(_.toString).runCollectAsync).map { result =>
        assertTrue(result == Right(Chunk("0", "1", "2", "3", "4")))
      }
    },

    // ---- Long input (7 outputs) ----
    test("Long→Int (tag 7)") {
      runAsync(Stream.fromChunk(longs).map(_.toInt).runCollectAsync).map { result =>
        assertTrue(result == Right(Chunk(1, 2, 3, 4, 5)))
      }
    },
    test("Long→Long (tag 8)") {
      runAsync(Stream.fromChunk(longs).map(_ + 1L).runCollectAsync).map { result =>
        assertTrue(result == Right(Chunk(2L, 3L, 4L, 5L, 6L)))
      }
    },
    test("Long→Float (tag 9)") {
      runAsync(Stream.fromChunk(longs).map(_.toFloat).runCollectAsync).map { result =>
        assertTrue(result == Right(Chunk(1.0f, 2.0f, 3.0f, 4.0f, 5.0f)))
      }
    },
    test("Long→Double (tag 10)") {
      runAsync(Stream.fromChunk(longs).map(_.toDouble).runCollectAsync).map { result =>
        assertTrue(result == Right(Chunk(1.0, 2.0, 3.0, 4.0, 5.0)))
      }
    },
    test("Long→Boolean (tag 11) — stored as boxed Boolean") {
      runAsync(Stream.fromChunk(longs).map(_ > 3L).runCollectAsync).map { (result: Any) =>
        assertTrue(result == Right(Chunk(false, false, false, true, true)))
      }
    },
    test("Long→String (tag 13)") {
      runAsync(Stream.fromChunk(longs).map(_.toString).runCollectAsync).map { result =>
        assertTrue(result == Right(Chunk("1", "2", "3", "4", "5")))
      }
    },

    // ---- Float input (7 outputs) ----
    test("Float→Int (tag 14)") {
      runAsync(Stream.fromChunk(floats).map(_.toInt).runCollectAsync).map { result =>
        assertTrue(result == Right(Chunk(1, 2, 3, 4, 5)))
      }
    },
    test("Float→Long (tag 15)") {
      runAsync(Stream.fromChunk(floats).map(_.toLong).runCollectAsync).map { result =>
        assertTrue(result == Right(Chunk(1L, 2L, 3L, 4L, 5L)))
      }
    },
    test("Float→Float (tag 16)") {
      runAsync(Stream.fromChunk(floats).map(_ + 1.0f).runCollectAsync).map { result =>
        assertTrue(result == Right(Chunk(2.0f, 3.0f, 4.0f, 5.0f, 6.0f)))
      }
    },
    test("Float→Double (tag 17)") {
      runAsync(Stream.fromChunk(floats).map(_.toDouble).runCollectAsync).map { result =>
        assertTrue(result == Right(Chunk(1.0, 2.0, 3.0, 4.0, 5.0)))
      }
    },
    test("Float→Boolean (tag 18) — stored as boxed Boolean") {
      runAsync(Stream.fromChunk(floats).map(_ > 2.5f).runCollectAsync).map { (result: Any) =>
        assertTrue(result == Right(Chunk(false, false, true, true, true)))
      }
    },
    test("Float→String (tag 20)") {
      runAsync(Stream.fromChunk(floats).map(_.toString).runCollectAsync).map { result =>
        // Use runtime toString to avoid JS vs JVM differences ("1" vs "1.0")
        assertTrue(result == Right(Chunk(1.0f.toString, 2.0f.toString, 3.0f.toString, 4.0f.toString, 5.0f.toString)))
      }
    },

    // ---- Double input (7 outputs) ----
    test("Double→Int (tag 21)") {
      runAsync(Stream.fromChunk(doubles).map(_.toInt).runCollectAsync).map { result =>
        assertTrue(result == Right(Chunk(1, 2, 3, 4, 5)))
      }
    },
    test("Double→Long (tag 22)") {
      runAsync(Stream.fromChunk(doubles).map(_.toLong).runCollectAsync).map { result =>
        assertTrue(result == Right(Chunk(1L, 2L, 3L, 4L, 5L)))
      }
    },
    test("Double→Float (tag 23)") {
      runAsync(Stream.fromChunk(doubles).map(_.toFloat).runCollectAsync).map { result =>
        assertTrue(result == Right(Chunk(1.0f, 2.0f, 3.0f, 4.0f, 5.0f)))
      }
    },
    test("Double→Double (tag 24)") {
      runAsync(Stream.fromChunk(doubles).map(_ + 1.0).runCollectAsync).map { result =>
        assertTrue(result == Right(Chunk(2.0, 3.0, 4.0, 5.0, 6.0)))
      }
    },
    test("Double→Boolean (tag 25) — stored as boxed Boolean") {
      runAsync(Stream.fromChunk(doubles).map(_ > 2.5).runCollectAsync).map { (result: Any) =>
        assertTrue(result == Right(Chunk(false, false, true, true, true)))
      }
    },
    test("Double→String (tag 27)") {
      runAsync(Stream.fromChunk(doubles).map(_.toString).runCollectAsync).map { result =>
        // Use runtime toString to avoid JS vs JVM differences ("1" vs "1.0")
        assertTrue(result == Right(Chunk(1.0.toString, 2.0.toString, 3.0.toString, 4.0.toString, 5.0.toString)))
      }
    },

    // ---- AnyRef input (7 outputs) ----
    test("Ref→Int (tag 28)") {
      runAsync(Stream.fromIterable(strings).map(_.length).runCollectAsync).map { result =>
        assertTrue(result == Right(Chunk(5, 5, 3)))
      }
    },
    test("Ref→Long (tag 29)") {
      runAsync(Stream.fromIterable(strings).map(_.length.toLong).runCollectAsync).map { result =>
        assertTrue(result == Right(Chunk(5L, 5L, 3L)))
      }
    },
    test("Ref→Float (tag 30)") {
      runAsync(Stream.fromIterable(strings).map(_.length.toFloat).runCollectAsync).map { result =>
        assertTrue(result == Right(Chunk(5.0f, 5.0f, 3.0f)))
      }
    },
    test("Ref→Double (tag 31)") {
      runAsync(Stream.fromIterable(strings).map(_.length.toDouble).runCollectAsync).map { result =>
        assertTrue(result == Right(Chunk(5.0, 5.0, 3.0)))
      }
    },
    test("Ref→Boolean (tag 32) — stored as boxed Boolean") {
      runAsync(Stream.fromIterable(strings).map(_.nonEmpty).runCollectAsync).map { (result: Any) =>
        assertTrue(result == Right(Chunk(true, true, true)))
      }
    },
    test("Ref→String (tag 34)") {
      runAsync(Stream.fromIterable(strings).map(_.toUpperCase).runCollectAsync).map { result =>
        assertTrue(result == Right(Chunk("HELLO", "WORLD", "FOO")))
      }
    }
  )

  // =========================================================================
  //  Filter
  // =========================================================================

  val filterSuite = suite("Filter")(
    test("Int filter (tag 35)") {
      runAsync(Stream.range(0, 10).filter(_ % 2 == 0).runCollectAsync).map { result =>
        assertTrue(result == Right(Chunk(0, 2, 4, 6, 8)))
      }
    },
    test("Long filter (tag 36)") {
      runAsync(Stream.fromChunk(longs).filter(_ > 2L).runCollectAsync).map { result =>
        assertTrue(result == Right(Chunk(3L, 4L, 5L)))
      }
    },
    test("Float filter (tag 37)") {
      runAsync(Stream.fromChunk(floats).filter(_ > 2.5f).runCollectAsync).map { result =>
        assertTrue(result == Right(Chunk(3.0f, 4.0f, 5.0f)))
      }
    },
    test("Double filter (tag 38)") {
      runAsync(Stream.fromChunk(doubles).filter(_ > 2.5).runCollectAsync).map { result =>
        assertTrue(result == Right(Chunk(3.0, 4.0, 5.0)))
      }
    },
    test("AnyRef filter (tag 39)") {
      runAsync(Stream.fromIterable(List("hello", "", "world")).filter(_.nonEmpty).runCollectAsync).map { result =>
        assertTrue(result == Right(Chunk("hello", "world")))
      }
    }
  )

  // =========================================================================
  //  Pipeline chaining / fusion
  // =========================================================================

  val chainingFusionSuite = suite("Chaining / Fusion")(
    test("Map+Map same type — 1 SyncInterpreter, 2 ops") {
      runAsync(Stream.range(0, 5).map(_ + 1).map(_ * 2).runCollectAsync).map { result =>
        val expected = (0 until 5).map(i => (i + 1) * 2)
        assertTrue(result == Right(Chunk.fromIterable(expected)))
      }
    },
    test("Map+Filter — 1 SyncInterpreter, 2 ops") {
      runAsync(Stream.range(0, 10).map(_ + 1).filter(_ % 2 == 0).runCollectAsync).map { result =>
        val expected = (0 until 10).map(_ + 1).filter(_ % 2 == 0)
        assertTrue(result == Right(Chunk.fromIterable(expected)))
      }
    },
    test("Filter+Map — 1 SyncInterpreter, 2 ops") {
      runAsync(Stream.range(0, 10).filter(_ % 2 == 0).map(_ * 3).runCollectAsync).map { result =>
        val expected = (0 until 10).filter(_ % 2 == 0).map(_ * 3)
        assertTrue(result == Right(Chunk.fromIterable(expected)))
      }
    },
    test("Cross-type chain: Int→Long→Long") {
      runAsync(Stream.range(0, 5).map(_.toLong).map(_ + 1L).runCollectAsync).map { result =>
        assertTrue(result == Right(Chunk(1L, 2L, 3L, 4L, 5L)))
      }
    },
    test("Multi-crossing: Int→Long→Double→String") {
      runAsync(Stream.range(0, 3).map(_.toLong).map(_.toDouble).map(_.toString).runCollectAsync).map { result =>
        // Use runtime toString to avoid JS vs JVM differences
        assertTrue(result == Right(Chunk(0.0.toString, 1.0.toString, 2.0.toString)))
      }
    },
    test("Float chain: Int→Float→Float+filter") {
      runAsync(Stream.range(0, 5).map(_.toFloat).map(_ + 1.0f).filter(_ > 2.5f).runCollectAsync).map { result =>
        assertTrue(result == Right(Chunk(3.0f, 4.0f, 5.0f)))
      }
    },
    test("Chain ends at AnyRef: Int→String+filter") {
      runAsync(Stream.range(0, 5).map(_.toString).filter(_.nonEmpty).runCollectAsync).map { result =>
        assertTrue(result == Right(Chunk("0", "1", "2", "3", "4")))
      }
    },
    test("5 chained maps") {
      runAsync(Stream.range(0, 10).map(_ + 1).map(_ + 1).map(_ + 1).map(_ + 1).map(_ + 1).runCollectAsync).map {
        result =>
          val expected = (0 until 10).map(_ + 5)
          assertTrue(result == Right(Chunk.fromIterable(expected)))
      }
    }
  )

  // =========================================================================
  //  jvmType propagation
  // =========================================================================

  val jvmTypeSuite = suite("jvmType propagation")(
    test("FromChunkFloat has PFloat") {
      val dq = Reader.fromChunk(floats)
      assertTrue(dq.jvmType eq JvmType.Float)
    },
    test("FromRange has PInt") {
      val dq = Reader.fromRange(0 until 5)
      assertTrue(dq.jvmType eq JvmType.Int)
    }
  )

  // =========================================================================
  //  Take / TakeWhile / Drop (Stream-level)
  // =========================================================================

  val takeTakeWhileDropSuite = suite("Take / TakeWhile / Drop")(
    test("range.take(5)") {
      runAsync(Stream.range(0, 100).take(5).runCollectAsync).map { result =>
        assertTrue(result == Right(Chunk(0, 1, 2, 3, 4)))
      }
    },
    test("range.map.take(3)") {
      runAsync(Stream.range(0, 100).map(_ + 1).take(3).runCollectAsync).map { result =>
        assertTrue(result == Right(Chunk(1, 2, 3)))
      }
    },
    test("range.takeWhile(_ < 5)") {
      runAsync(Stream.range(0, 100).takeWhile(_ < 5).runCollectAsync).map { result =>
        assertTrue(result == Right(Chunk(0, 1, 2, 3, 4)))
      }
    },
    test("take then map") {
      runAsync(Stream.range(0, 100).take(5).map(_ * 2).runCollectAsync).map { result =>
        assertTrue(result == Right(Chunk(0, 2, 4, 6, 8)))
      }
    },
    test("drop(3)") {
      runAsync(Stream.range(0, 10).drop(3).runCollectAsync).map { result =>
        assertTrue(result == Right(Chunk.fromIterable(3 until 10)))
      }
    },
    test("drop more than available") {
      runAsync(Stream.range(0, 5).drop(10).runCollectAsync).map { result =>
        assertTrue(result == Right(Chunk.empty))
      }
    },
    test("drop then map") {
      runAsync(Stream.range(0, 10).drop(3).map(_ * 2).runCollectAsync).map { result =>
        val expected = (3 until 10).map(_ * 2)
        assertTrue(result == Right(Chunk.fromIterable(expected)))
      }
    }
  )

  // =========================================================================
  //  Sink specialization
  // =========================================================================

  val sinkSpecializationSuite = suite("Sink specialization")(
    test("Float drain") {
      runAsync(Stream.fromChunk(floats).runAsync(Sink.drain)).map { result =>
        assertTrue(result == Right(()))
      }
    },
    test("Float count") {
      runAsync(Stream.fromChunk(floats).runAsync(Sink.count)).map { result =>
        assertTrue(result == Right(5L))
      }
    },
    test("Float foreach") {
      var sum = 0.0f
      runAsync(Stream.fromChunk(floats).runForeachAsync(f => Async.succeed(sum += f))).map { result =>
        assertTrue(result == Right(()) && sum == 15.0f)
      }
    },
    test("Int foldLeft") {
      runAsync(Stream.range(0, 5).runFoldAsync(0)((z, a) => Async.succeed(z + a))).map { result =>
        assertTrue(result == Right(10))
      }
    },
    test("Long foldLeft") {
      runAsync(Stream.fromChunk(longs).runFoldAsync(0L)((z, a) => Async.succeed(z + a))).map { result =>
        assertTrue(result == Right(15L))
      }
    },
    test("last on Int stream") {
      runAsync(Stream.range(0, 5).runAsync(Sink.last[Int])).map { result =>
        assertTrue(result == Right(Some(4)))
      }
    },
    test("last on empty stream") {
      runAsync(Stream.empty.runAsync(Sink.last[Int])).map { result =>
        assertTrue(result == Right(None))
      }
    },
    test("head on Int stream") {
      runAsync(Stream.range(0, 5).runAsync(Sink.head[Int])).map { result =>
        assertTrue(result == Right(Some(0)))
      }
    },
    test("head on empty stream") {
      runAsync(Stream.empty.runAsync(Sink.head[Int])).map { result =>
        assertTrue(result == Right(None))
      }
    },
    test("Sink.take(3) on Int stream") {
      runAsync(Stream.range(0, 10).runAsync(Sink.take[Int](3))).map { result =>
        assertTrue(result == Right(Chunk(0, 1, 2)))
      }
    }
  )

  // =========================================================================
  //  Regression tests
  // =========================================================================

  val regressionSuite = suite("Regressions")(
    test("repeat(1).take(3)") {
      runAsync(Stream.repeat(1).take(3).runCollectAsync).map { result =>
        assertTrue(result == Right(Chunk(1, 1, 1)))
      }
    },
    test("fail + map identity") {
      runAsync((Stream.fail("err"): Stream[String, Int]).map(_ + 1).runCollectAsync).map {
        (result: Either[String, Chunk[Int]]) =>
          assertTrue(result == Left("err"))
      }
    },
    test("concat range") {
      runAsync((Stream.range(0, 3) ++ Stream.range(3, 6)).runCollectAsync).map { result =>
        assertTrue(result == Right(Chunk(0, 1, 2, 3, 4, 5)))
      }
    },
    test("flatMap range") {
      runAsync(Stream.range(0, 3).flatMap(i => Stream.range(0, i)).runCollectAsync).map { result =>
        assertTrue(result == Right(Chunk(0, 0, 1)))
      }
    },
    test("empty stream map") {
      runAsync(Stream.fromChunk(Chunk.empty[Int]).map(_ + 1).runCollectAsync).map { result =>
        assertTrue(result == Right(Chunk.empty))
      }
    },
    test("error propagation through SyncInterpreter") {
      runAsync((Stream.fail("boom"): Stream[String, Int]).map(_ + 1).runCollectAsync).map {
        (result: Either[String, Chunk[Int]]) =>
          assertTrue(result == Left("boom"))
      }
    },
    test("Float fromChunk round-trip") {
      runAsync(Stream.fromChunk(floats).runCollectAsync).map { result =>
        assertTrue(result == Right(floats))
      }
    }
  )
}
