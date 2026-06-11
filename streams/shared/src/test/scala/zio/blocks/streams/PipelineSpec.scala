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
import zio.test.Assertion._
import StreamsGen._
import scala.annotation.nowarn
import java.util.concurrent.atomic.AtomicInteger

@nowarn("msg=never used")
object PipelineSpec extends StreamsBaseSpec {

  def spec: Spec[TestEnvironment, Any] = suite("Pipeline")(
    mapSuite,
    filterSuite,
    takeSuite,
    takeWhileSuite,
    dropSuite,
    collectSuite,
    identitySuite,
    andThenSuite,
    viaApplyToStreamSuite,
    andThenSinkSuite,
    jvmTypeSuite,
    sinkSpecializationSuite,
    regressionSuite
  )

  // ---------- helpers ----------

  private val ints    = Chunk(1, 2, 3, 4, 5)
  private val longs   = Chunk(1L, 2L, 3L, 4L, 5L)
  private val floats  = Chunk(1.0f, 2.0f, 3.0f, 4.0f, 5.0f)
  private val doubles = Chunk(1.0, 2.0, 3.0, 4.0, 5.0)
  private val strings = List("hello", "world", "foo")

  private def collect[A](s: Stream[Nothing, A]): Chunk[A] =
    s.runCollect.fold[Chunk[A]](_ => Chunk.empty[A], x => x)

  /** A resource stream of [1,2,3,4,5] whose release increments `count`. */
  private def resourceStream(count: AtomicInteger): Stream[Nothing, Int] =
    Stream.fromAcquireRelease("res", (_: String) => { count.incrementAndGet(); () }) { _ =>
      Stream.fromChunk(Chunk(1, 2, 3, 4, 5))
    }

  private def resourceStreamCompose(count: AtomicInteger): Stream[Nothing, Int] =
    Stream.fromAcquireRelease((), (_: Unit) => { count.incrementAndGet(); () })(_ =>
      Stream.fromChunk(Chunk(1, 2, 3, 4, 5))
    )

  // =========================================================================
  //  map
  // =========================================================================

  val mapSuite = suite("map")(
    // ---- Int input (7 outputs) ----
    test("Int→Int (tag 0)") {
      val result = Stream.range(0, 5).map(_ + 1).runCollect
      assertTrue(result == Right(Chunk(1, 2, 3, 4, 5)))
    },
    test("Int→Long (tag 1)") {
      val result = Stream.range(0, 5).map(_.toLong).runCollect
      assertTrue(result == Right(Chunk(0L, 1L, 2L, 3L, 4L)))
    },
    test("Int→Float (tag 2)") {
      val result = Stream.range(0, 5).map(_.toFloat).runCollect
      assertTrue(result == Right(Chunk(0.0f, 1.0f, 2.0f, 3.0f, 4.0f)))
    },
    test("Int→Double (tag 3)") {
      val result = Stream.range(0, 5).map(_.toDouble).runCollect
      assertTrue(result == Right(Chunk(0.0, 1.0, 2.0, 3.0, 4.0)))
    },
    test("Int→Boolean (tag 4) — stored as boxed Boolean") {
      val result: Any = Stream.range(0, 5).map(_ > 2).runCollect
      assertTrue(result == Right(Chunk(false, false, false, true, true)))
    },
    test("Int→Unit (tag 5) — side effect") {
      var sum    = 0
      val result = Stream.range(0, 5).map { i => sum += i; () }.runDrain
      assertTrue(result == Right(()) && sum == 10)
    },
    test("Int→String (tag 6)") {
      val result = Stream.range(0, 5).map(_.toString).runCollect
      assertTrue(result == Right(Chunk("0", "1", "2", "3", "4")))
    },

    // ---- Long input (7 outputs) ----
    test("Long→Int (tag 7)") {
      val result = Stream.fromChunk(longs).map(_.toInt).runCollect
      assertTrue(result == Right(Chunk(1, 2, 3, 4, 5)))
    },
    test("Long→Long (tag 8)") {
      val result = Stream.fromChunk(longs).map(_ + 1L).runCollect
      assertTrue(result == Right(Chunk(2L, 3L, 4L, 5L, 6L)))
    },
    test("Long→Float (tag 9)") {
      val result = Stream.fromChunk(longs).map(_.toFloat).runCollect
      assertTrue(result == Right(Chunk(1.0f, 2.0f, 3.0f, 4.0f, 5.0f)))
    },
    test("Long→Double (tag 10)") {
      val result = Stream.fromChunk(longs).map(_.toDouble).runCollect
      assertTrue(result == Right(Chunk(1.0, 2.0, 3.0, 4.0, 5.0)))
    },
    test("Long→Boolean (tag 11) — stored as boxed Boolean") {
      val result: Any = Stream.fromChunk(longs).map(_ > 3L).runCollect
      assertTrue(result == Right(Chunk(false, false, false, true, true)))
    },
    test("Long→String (tag 13)") {
      val result = Stream.fromChunk(longs).map(_.toString).runCollect
      assertTrue(result == Right(Chunk("1", "2", "3", "4", "5")))
    },

    // ---- Float input (7 outputs) ----
    test("Float→Int (tag 14)") {
      val result = Stream.fromChunk(floats).map(_.toInt).runCollect
      assertTrue(result == Right(Chunk(1, 2, 3, 4, 5)))
    },
    test("Float→Long (tag 15)") {
      val result = Stream.fromChunk(floats).map(_.toLong).runCollect
      assertTrue(result == Right(Chunk(1L, 2L, 3L, 4L, 5L)))
    },
    test("Float→Float (tag 16)") {
      val result = Stream.fromChunk(floats).map(_ + 1.0f).runCollect
      assertTrue(result == Right(Chunk(2.0f, 3.0f, 4.0f, 5.0f, 6.0f)))
    },
    test("Float→Double (tag 17)") {
      val result = Stream.fromChunk(floats).map(_.toDouble).runCollect
      assertTrue(result == Right(Chunk(1.0, 2.0, 3.0, 4.0, 5.0)))
    },
    test("Float→Boolean (tag 18) — stored as boxed Boolean") {
      val result: Any = Stream.fromChunk(floats).map(_ > 2.5f).runCollect
      assertTrue(result == Right(Chunk(false, false, true, true, true)))
    },
    test("Float→String (tag 20)") {
      val result = Stream.fromChunk(floats).map(_.toString).runCollect
      // Use runtime toString to avoid JS vs JVM differences ("1" vs "1.0")
      assertTrue(result == Right(Chunk(1.0f.toString, 2.0f.toString, 3.0f.toString, 4.0f.toString, 5.0f.toString)))
    },

    // ---- Double input (7 outputs) ----
    test("Double→Int (tag 21)") {
      val result = Stream.fromChunk(doubles).map(_.toInt).runCollect
      assertTrue(result == Right(Chunk(1, 2, 3, 4, 5)))
    },
    test("Double→Long (tag 22)") {
      val result = Stream.fromChunk(doubles).map(_.toLong).runCollect
      assertTrue(result == Right(Chunk(1L, 2L, 3L, 4L, 5L)))
    },
    test("Double→Float (tag 23)") {
      val result = Stream.fromChunk(doubles).map(_.toFloat).runCollect
      assertTrue(result == Right(Chunk(1.0f, 2.0f, 3.0f, 4.0f, 5.0f)))
    },
    test("Double→Double (tag 24)") {
      val result = Stream.fromChunk(doubles).map(_ + 1.0).runCollect
      assertTrue(result == Right(Chunk(2.0, 3.0, 4.0, 5.0, 6.0)))
    },
    test("Double→Boolean (tag 25) — stored as boxed Boolean") {
      val result: Any = Stream.fromChunk(doubles).map(_ > 2.5).runCollect
      assertTrue(result == Right(Chunk(false, false, true, true, true)))
    },
    test("Double→String (tag 27)") {
      val result = Stream.fromChunk(doubles).map(_.toString).runCollect
      // Use runtime toString to avoid JS vs JVM differences ("1" vs "1.0")
      assertTrue(result == Right(Chunk(1.0.toString, 2.0.toString, 3.0.toString, 4.0.toString, 5.0.toString)))
    },

    // ---- AnyRef input (7 outputs) ----
    test("Ref→Int (tag 28)") {
      val result = Stream.fromIterable(strings).map(_.length).runCollect
      assertTrue(result == Right(Chunk(5, 5, 3)))
    },
    test("Ref→Long (tag 29)") {
      val result = Stream.fromIterable(strings).map(_.length.toLong).runCollect
      assertTrue(result == Right(Chunk(5L, 5L, 3L)))
    },
    test("Ref→Float (tag 30)") {
      val result = Stream.fromIterable(strings).map(_.length.toFloat).runCollect
      assertTrue(result == Right(Chunk(5.0f, 5.0f, 3.0f)))
    },
    test("Ref→Double (tag 31)") {
      val result = Stream.fromIterable(strings).map(_.length.toDouble).runCollect
      assertTrue(result == Right(Chunk(5.0, 5.0, 3.0)))
    },
    test("Ref→Boolean (tag 32) — stored as boxed Boolean") {
      val result: Any = Stream.fromIterable(strings).map(_.nonEmpty).runCollect
      assertTrue(result == Right(Chunk(true, true, true)))
    },
    test("Ref→String (tag 34)") {
      val result = Stream.fromIterable(strings).map(_.toUpperCase).runCollect
      assertTrue(result == Right(Chunk("HELLO", "WORLD", "FOO")))
    },
    test("map applies function to each element") {
      check(genIntStream, Gen.function(genInt)) { (s, f) =>
        val data = collect(s)
        assert(collect(Stream.fromChunk(data).map(f)))(equalTo(data.map(f)))
      }
    },
    test("map applyToSink == contramap on sink") {
      check(genIntStream, Gen.function(genInt)) { (s, f) =>
        val data = collect(s)
        val r1   = Stream
          .fromChunk(data)
          .run(
            Pipeline.map[Int, Int](f).andThenSink[Nothing, Chunk[Int]](Sink.collectAll[Int])
          )
        val r2 = Stream.fromChunk(data).run(Sink.collectAll[Int].contramap(f))
        assert(r1)(equalTo(r2))
      }
    }
  )

  // =========================================================================
  //  filter
  // =========================================================================

  val filterSuite = suite("filter")(
    test("Int filter (tag 35)") {
      val result = Stream.range(0, 10).filter(_ % 2 == 0).runCollect
      assertTrue(result == Right(Chunk(0, 2, 4, 6, 8)))
    },
    test("Long filter (tag 36)") {
      val result = Stream.fromChunk(longs).filter(_ > 2L).runCollect
      assertTrue(result == Right(Chunk(3L, 4L, 5L)))
    },
    test("Float filter (tag 37)") {
      val result = Stream.fromChunk(floats).filter(_ > 2.5f).runCollect
      assertTrue(result == Right(Chunk(3.0f, 4.0f, 5.0f)))
    },
    test("Double filter (tag 38)") {
      val result = Stream.fromChunk(doubles).filter(_ > 2.5).runCollect
      assertTrue(result == Right(Chunk(3.0, 4.0, 5.0)))
    },
    test("AnyRef filter (tag 39)") {
      val result = Stream.fromIterable(List("hello", "", "world")).filter(_.nonEmpty).runCollect
      assertTrue(result == Right(Chunk("hello", "world")))
    },
    test("filter keeps matching elements") {
      check(genIntStream, Gen.function(Gen.boolean)) { (s, pred) =>
        val data = collect(s)
        assert(collect(Stream.fromChunk(data).filter(pred)))(equalTo(data.filter(pred)))
      }
    },
    test("filter applyToSink == filter applyToStream then sink") {
      check(genIntStream, Gen.function(Gen.boolean)) { (s, pred) =>
        val data = collect(s)
        val r1   = Stream
          .fromChunk(data)
          .run(
            Pipeline.filter[Int](pred).andThenSink[Nothing, Chunk[Int]](Sink.collectAll[Int])
          )
        val r2 = Stream.fromChunk(data).filter(pred).run(Sink.collectAll[Int])
        assert(r1)(equalTo(r2))
      }
    }
  )

  // =========================================================================
  //  take
  // =========================================================================

  val takeSuite = suite("take")(
    test("range.take(5)") {
      val result = Stream.range(0, 100).take(5).runCollect
      assertTrue(result == Right(Chunk(0, 1, 2, 3, 4)))
    },
    test("range.map.take(3)") {
      val result = Stream.range(0, 100).map(_ + 1).take(3).runCollect
      assertTrue(result == Right(Chunk(1, 2, 3)))
    },
    test("take then map") {
      val result = Stream.range(0, 100).take(5).map(_ * 2).runCollect
      assertTrue(result == Right(Chunk(0, 2, 4, 6, 8)))
    },
    test("take(n) emits exactly min(n, len) elements") {
      check(genIntStream, Gen.int(0, 60)) { (s, n) =>
        val data = collect(s)
        assert(collect(Stream.fromChunk(data).take(n.toLong)))(equalTo(data.take(n)))
      }
    },
    test("take applyToSink == take applyToStream then sink") {
      check(genIntStream, Gen.int(0, 30)) { (s, n) =>
        val data = collect(s)
        val r1   = Stream
          .fromChunk(data)
          .run(
            Pipeline.take[Int](n.toLong).andThenSink[Nothing, Chunk[Int]](Sink.collectAll[Int])
          )
        val r2 = Stream.fromChunk(data).take(n.toLong).run(Sink.collectAll[Int])
        assert(r1)(equalTo(r2))
      }
    }
  )

  // =========================================================================
  //  takeWhile
  // =========================================================================

  val takeWhileSuite = suite("takeWhile")(
    test("range.takeWhile(_ < 5)") {
      val result = Stream.range(0, 100).takeWhile(_ < 5).runCollect
      assertTrue(result == Right(Chunk(0, 1, 2, 3, 4)))
    }
  )

  // =========================================================================
  //  drop
  // =========================================================================

  val dropSuite = suite("drop")(
    test("drop(3)") {
      val result = Stream.range(0, 10).drop(3).runCollect
      assertTrue(result == Right(Chunk.fromIterable(3 until 10)))
    },
    test("drop more than available") {
      val result = Stream.range(0, 5).drop(10).runCollect
      assertTrue(result == Right(Chunk.empty))
    },
    test("drop then map") {
      val result   = Stream.range(0, 10).drop(3).map(_ * 2).runCollect
      val expected = (3 until 10).map(_ * 2)
      assertTrue(result == Right(Chunk.fromIterable(expected)))
    },
    test("drop(n) skips first min(n, len) elements") {
      check(genIntStream, Gen.int(0, 60)) { (s, n) =>
        val data = collect(s)
        assert(collect(Stream.fromChunk(data).drop(n.toLong)))(equalTo(data.drop(n)))
      }
    }
  )

  // =========================================================================
  //  collect
  // =========================================================================

  val collectSuite = suite("collect")(
    test("collect applies partial function") {
      check(genIntStream) { s =>
        val data                          = collect(s)
        val pf: PartialFunction[Int, Int] = { case x if x > 0 => x * 2 }
        assert(collect(Stream.fromChunk(data).via(Pipeline.collect(pf))))(equalTo(data.collect(pf)))
      }
    }
  )

  // =========================================================================
  //  identity
  // =========================================================================

  val identitySuite = suite("identity")(
    test("Pipeline.identity passes all elements through") {
      check(genIntStream) { s =>
        assert(collect(s.via(Pipeline.identity[Int])))(equalTo(collect(s)))
      }
    }
  )

  // =========================================================================
  //  andThen
  // =========================================================================

  val andThenSuite = suite("andThen")(
    test("Map+Map same type — 1 Interpreter, 2 ops") {
      val result   = Stream.range(0, 5).map(_ + 1).map(_ * 2).runCollect
      val expected = (0 until 5).map(i => (i + 1) * 2)
      assertTrue(result == Right(Chunk.fromIterable(expected)))
    },
    test("Map+Filter — 1 Interpreter, 2 ops") {
      val result   = Stream.range(0, 10).map(_ + 1).filter(_ % 2 == 0).runCollect
      val expected = (0 until 10).map(_ + 1).filter(_ % 2 == 0)
      assertTrue(result == Right(Chunk.fromIterable(expected)))
    },
    test("Filter+Map — 1 Interpreter, 2 ops") {
      val result   = Stream.range(0, 10).filter(_ % 2 == 0).map(_ * 3).runCollect
      val expected = (0 until 10).filter(_ % 2 == 0).map(_ * 3)
      assertTrue(result == Right(Chunk.fromIterable(expected)))
    },
    test("Cross-type chain: Int→Long→Long") {
      val result = Stream.range(0, 5).map(_.toLong).map(_ + 1L).runCollect
      assertTrue(result == Right(Chunk(1L, 2L, 3L, 4L, 5L)))
    },
    test("Multi-crossing: Int→Long→Double→String") {
      val result = Stream.range(0, 3).map(_.toLong).map(_.toDouble).map(_.toString).runCollect
      // Use runtime toString to avoid JS vs JVM differences
      assertTrue(result == Right(Chunk(0.0.toString, 1.0.toString, 2.0.toString)))
    },
    test("Float chain: Int→Float→Float+filter") {
      val result = Stream.range(0, 5).map(_.toFloat).map(_ + 1.0f).filter(_ > 2.5f).runCollect
      assertTrue(result == Right(Chunk(3.0f, 4.0f, 5.0f)))
    },
    test("Chain ends at AnyRef: Int→String+filter") {
      val result = Stream.range(0, 5).map(_.toString).filter(_.nonEmpty).runCollect
      assertTrue(result == Right(Chunk("0", "1", "2", "3", "4")))
    },
    test("5 chained maps") {
      val result   = Stream.range(0, 10).map(_ + 1).map(_ + 1).map(_ + 1).map(_ + 1).map(_ + 1).runCollect
      val expected = (0 until 10).map(_ + 5)
      assertTrue(result == Right(Chunk.fromIterable(expected)))
    },
    suite("laws")(
      test("left identity: Pipeline.identity andThen p == p") {
        check(genIntStream, genIntPipeline) { (s, p) =>
          assert(collect((Pipeline.identity[Int] andThen p).applyToStream(s)))(
            equalTo(collect(p.applyToStream(s)))
          )
        }
      },
      test("right identity: p andThen Pipeline.identity == p") {
        check(genIntStream, genIntPipeline) { (s, p) =>
          assert(collect((p andThen Pipeline.identity[Int]).applyToStream(s)))(
            equalTo(collect(p.applyToStream(s)))
          )
        }
      },
      test("associativity: (p andThen q) andThen r == p andThen (q andThen r)") {
        check(genIntStream, genIntPipeline, genIntPipeline, genIntPipeline) { (s, p, q, r) =>
          assert(collect(((p andThen q) andThen r).applyToStream(s)))(
            equalTo(collect((p andThen (q andThen r)).applyToStream(s)))
          )
        }
      }
    )
  )

  // =========================================================================
  //  via / applyToStream
  // =========================================================================

  val viaApplyToStreamSuite = suite("via / applyToStream")(
    test("q.applyToStream(p.applyToStream(s)) == (p andThen q).applyToStream(s)") {
      check(genIntStream, genIntPipeline, genIntPipeline) { (s, p, q) =>
        assert(collect(q.applyToStream(p.applyToStream(s))))(
          equalTo(collect((p andThen q).applyToStream(s)))
        )
      }
    },
    test("p.applyToStream(s) == s.via(p)") {
      check(genIntStream, genIntPipeline) { (s, p) =>
        assert(collect(p.applyToStream(s)))(equalTo(collect(s.via(p))))
      }
    }
  )

  // =========================================================================
  //  andThenSink
  // =========================================================================

  val andThenSinkSuite = suite("andThenSink")(
    test("s.run(p andThenSink sink) == s.via(p).run(sink)") {
      check(genIntStream, genIntPipeline) { (s, p) =>
        val data = collect(s)
        val r1   = Stream.fromChunk(data).run(p andThenSink Sink.collectAll[Int])
        val r2   = Stream.fromChunk(data).via(p).run(Sink.collectAll[Int])
        assert(r1)(equalTo(r2))
      }
    },
    suite("regressions")(
      test("filter pipeline andThenSink: finalizer runs exactly once [AdversarialPipelineSinkSpec]") {
        val count  = new AtomicInteger(0)
        val sink   = Pipeline.filter[Int](_ % 2 == 0).andThenSink(Sink.collectAll[Int])
        val result = resourceStream(count).run(sink)
        assertTrue(result == Right(Chunk(2, 4))) &&
        assertTrue(count.get == 1)
      },
      test("drop pipeline andThenSink: finalizer runs exactly once [AdversarialPipelineSinkSpec]") {
        val count  = new AtomicInteger(0)
        val sink   = Pipeline.drop[Int](2).andThenSink(Sink.collectAll[Int])
        val result = resourceStream(count).run(sink)
        assertTrue(result == Right(Chunk(3, 4, 5))) &&
        assertTrue(count.get == 1)
      },
      test("take pipeline andThenSink: finalizer runs exactly once [AdversarialPipelineSinkSpec]") {
        val count  = new AtomicInteger(0)
        val sink   = Pipeline.take[Int](3).andThenSink(Sink.collectAll[Int])
        val result = resourceStream(count).run(sink)
        assertTrue(result == Right(Chunk(1, 2, 3))) &&
        assertTrue(count.get == 1)
      },
      test("collect pipeline andThenSink: finalizer runs exactly once [AdversarialPipelineSinkSpec]") {
        val count  = new AtomicInteger(0)
        val sink   = Pipeline.collect[Int, Int] { case n if n > 2 => n * 10 }.andThenSink(Sink.collectAll[Int])
        val result = resourceStream(count).run(sink)
        assertTrue(result == Right(Chunk(30, 40, 50))) &&
        assertTrue(count.get == 1)
      },
      // R6 convergence probe: chunked is the only grouping pipeline; through the
      // sink adapter it must group via readN on the borrowed (shielded) reader
      // and the resource finalizer must still run exactly once.
      test("chunked pipeline andThenSink: groups and finalizer runs exactly once [AdversarialPipelineSinkSpec]") {
        val count  = new AtomicInteger(0)
        val sink   = Pipeline.chunked[Int](2).andThenSink(Sink.collectAll[Chunk[Int]])
        val result = resourceStream(count).run(sink)
        assertTrue(result == Right(Chunk(Chunk(1, 2), Chunk(3, 4), Chunk(5)))) &&
        assertTrue(count.get == 1)
      },
      test("map pipeline andThenSink: finalizer runs exactly once (control) [AdversarialPipelineSinkSpec]") {
        val count  = new AtomicInteger(0)
        val sink   = Pipeline.map[Int, Int](_ + 100).andThenSink(Sink.collectAll[Int])
        val result = resourceStream(count).run(sink)
        assertTrue(result == Right(Chunk(101, 102, 103, 104, 105))) &&
        assertTrue(count.get == 1)
      },
      test("chunked_pipeline_andThenSink_value_and_finalizer_once [AdversarialPipelineSinkComposeSpec]") {
        val count = new AtomicInteger(0)
        val sink  = Pipeline.chunked[Int](2).andThenSink(Sink.collectAll[Chunk[Int]])
        val r     = resourceStreamCompose(count).run(sink)
        assertTrue(r == Right(Chunk(Chunk(1, 2), Chunk(3, 4), Chunk(5)))) &&
        assertTrue(count.get == 1)
      },
      test("buffer_pipeline_andThenSink_value_and_finalizer_once [AdversarialPipelineSinkComposeSpec]") {
        val count = new AtomicInteger(0)
        val sink  = Pipeline.buffer[Int](4).andThenSink(Sink.collectAll[Int])
        val r     = resourceStreamCompose(count).run(sink)
        assertTrue(r == Right(Chunk(1, 2, 3, 4, 5))) &&
        assertTrue(count.get == 1)
      },
      test("identity_pipeline_andThenSink [AdversarialPipelineSinkComposeSpec]") {
        val count = new AtomicInteger(0)
        val sink  = Pipeline.identity[Int].andThenSink(Sink.collectAll[Int])
        val r     = resourceStreamCompose(count).run(sink)
        assertTrue(r == Right(Chunk(1, 2, 3, 4, 5))) && assertTrue(count.get == 1)
      },
      test("composed_pipeline_andThenSink_matches_applyToStream [AdversarialPipelineSinkComposeSpec]") {
        val count = new AtomicInteger(0)
        val pipe  = Pipeline.map[Int, Int](_ + 1).andThen(Pipeline.filter[Int](_ % 2 == 0))
        val sink  = pipe.andThenSink(Sink.collectAll[Int])
        val r     = resourceStreamCompose(count).run(sink)
        // (+1) -> 2,3,4,5,6 ; filter even -> 2,4,6
        assertTrue(r == Right(Chunk(2, 4, 6))) && assertTrue(count.get == 1)
      },
      test("composed_three_stage_andThenSink [AdversarialPipelineSinkComposeSpec]") {
        val count = new AtomicInteger(0)
        val pipe  = Pipeline
          .map[Int, Int](_ * 10)
          .andThen(Pipeline.drop[Int](1))
          .andThen(Pipeline.take[Int](2))
        val sink = pipe.andThenSink(Sink.collectAll[Int])
        val r    = resourceStreamCompose(count).run(sink)
        // *10 -> 10,20,30,40,50 ; drop 1 -> 20,30,40,50 ; take 2 -> 20,30
        assertTrue(r == Right(Chunk(20, 30))) && assertTrue(count.get == 1)
      },
      test("chunked_pipeline_andThenSink_count [AdversarialPipelineSinkComposeSpec]") {
        val count = new AtomicInteger(0)
        val sink  = Pipeline.chunked[Int](2).andThenSink(Sink.count)
        val r     = resourceStreamCompose(count).run(sink)
        assertTrue(r == Right(3L)) && assertTrue(count.get == 1)
      },
      test("andThenSink_equals_applyToStream_then_run_differential [AdversarialPipelineSinkComposeSpec]") {
        val pipe      = Pipeline.map[Int, Int](_ + 1).andThen(Pipeline.filter[Int](_ > 2))
        val viaSink   = Stream.fromChunk(Chunk(1, 2, 3, 4)).run(pipe.andThenSink(Sink.collectAll[Int]))
        val viaStream = pipe.applyToStream(Stream.fromChunk(Chunk(1, 2, 3, 4))).runCollect
        assertTrue(viaSink == viaStream) && assertTrue(viaSink == Right(Chunk(3, 4, 5)))
      },
      test("buffer_pipeline_andThenSink_empty_stream [AdversarialPipelineSinkComposeSpec]") {
        val count = new AtomicInteger(0)
        val s     = Stream.fromAcquireRelease((), (_: Unit) => { count.incrementAndGet(); () })(_ =>
          (Stream.empty: Stream[Nothing, Int])
        )
        val sink = Pipeline.buffer[Int](4).andThenSink(Sink.collectAll[Int])
        val r    = s.run(sink)
        assertTrue(r == Right(Chunk.empty[Int])) && assertTrue(count.get == 1)
      }
    )
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
  //  Sink specialization
  // =========================================================================

  val sinkSpecializationSuite = suite("Sink specialization")(
    test("Float drain") {
      val result = Stream.fromChunk(floats).run(Sink.drain)
      assertTrue(result == Right(()))
    },
    test("Float count") {
      val result = Stream.fromChunk(floats).run(Sink.count)
      assertTrue(result == Right(5L))
    },
    test("Float foreach") {
      var sum    = 0.0f
      val result = Stream.fromChunk(floats).runForeach(f => sum += f)
      assertTrue(result == Right(()) && sum == 15.0f)
    },
    test("Int foldLeft") {
      val result = Stream.range(0, 5).runFold(0)(_ + _)
      assertTrue(result == Right(10))
    },
    test("Long foldLeft") {
      val result = Stream.fromChunk(longs).runFold(0L)(_ + _)
      assertTrue(result == Right(15L))
    },
    test("last on Int stream") {
      val result = Stream.range(0, 5).run(Sink.last[Int])
      assertTrue(result == Right(Some(4)))
    },
    test("last on empty stream") {
      val result = Stream.empty.run(Sink.last[Int])
      assertTrue(result == Right(None))
    },
    test("head on Int stream") {
      val result = Stream.range(0, 5).run(Sink.head[Int])
      assertTrue(result == Right(Some(0)))
    },
    test("head on empty stream") {
      val result = Stream.empty.run(Sink.head[Int])
      assertTrue(result == Right(None))
    },
    test("Sink.take(3) on Int stream") {
      val result = Stream.range(0, 10).run(Sink.take[Int](3))
      assertTrue(result == Right(Chunk(0, 1, 2)))
    }
  )

  // =========================================================================
  //  Regressions
  // =========================================================================

  val regressionSuite = suite("Regressions")(
    test("repeat(1).take(3)") {
      val result = Stream.repeat(1).take(3).runCollect
      assertTrue(result == Right(Chunk(1, 1, 1)))
    },
    test("fail + map identity") {
      val result: Either[String, Chunk[Int]] = (Stream.fail("err"): Stream[String, Int]).map(_ + 1).runCollect
      assertTrue(result == Left("err"))
    },
    test("concat range") {
      val result = (Stream.range(0, 3) ++ Stream.range(3, 6)).runCollect
      assertTrue(result == Right(Chunk(0, 1, 2, 3, 4, 5)))
    },
    test("flatMap range") {
      val result = Stream.range(0, 3).flatMap(i => Stream.range(0, i)).runCollect
      assertTrue(result == Right(Chunk(0, 0, 1)))
    },
    test("empty stream map") {
      val result = Stream.fromChunk(Chunk.empty[Int]).map(_ + 1).runCollect
      assertTrue(result == Right(Chunk.empty))
    },
    test("error propagation through Interpreter") {
      val result: Either[String, Chunk[Int]] = (Stream.fail("boom"): Stream[String, Int]).map(_ + 1).runCollect
      assertTrue(result == Left("boom"))
    },
    test("Float fromChunk round-trip") {
      val result = Stream.fromChunk(floats).runCollect
      assertTrue(result == Right(floats))
    }
  )
}
