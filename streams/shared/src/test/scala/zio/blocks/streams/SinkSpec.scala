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
import zio.test.Assertion._
import StreamsGen._

import java.io.{ByteArrayOutputStream, StringWriter}
import java.util.concurrent.atomic.AtomicInteger

object SinkSpec extends StreamsBaseSpec {

  private def run[A, Z](s: Stream[Nothing, A], sink: Sink[Nothing, A, Z]): Z =
    s.run(sink).fold(e => throw new RuntimeException(s"unexpected error: $e"), identity)

  def spec: Spec[TestEnvironment, Any] = suite("Sink")(
    suite("map")(
      test("map(identity) ≡ identity") {
        check(genIntStream) { s =>
          val data = s.runCollect.getOrElse(Chunk.empty)
          assert(run(Stream.fromChunk(data), Sink.collectAll[Int].map(identity)))(
            equalTo(run(Stream.fromChunk(data), Sink.collectAll[Int]))
          )
        }
      },
      test("map(f).map(g) ≡ map(f andThen g)") {
        check(genIntStream, Gen.function[Any, Long, Long](Gen.long), Gen.function[Any, Long, Long](Gen.long)) {
          (s, f, g) =>
            val data = s.runCollect.getOrElse(Chunk.empty)
            val r1   = run(Stream.fromChunk(data), Sink.sumInt.map(f).map(g))
            val r2   = run(Stream.fromChunk(data), Sink.sumInt.map(f andThen g))
            assert(r1)(equalTo(r2))
        }
      },
      suite("regressions")(
        test("map_result [AdversarialSinkCombinatorSpec]") {
          val sink = Sink.count.map(_ * 2)
          assertTrue(Stream.range(0, 4).run(sink) == Right(8L))
        }
      )
    ),
    suite("contramap")(
      test("contramap(identity) ≡ identity") {
        check(genIntStream) { s =>
          val data = s.runCollect.getOrElse(Chunk.empty)
          assert(run(Stream.fromChunk(data), Sink.collectAll[Int].contramap[Int](identity)))(
            equalTo(run(Stream.fromChunk(data), Sink.collectAll[Int]))
          )
        }
      },
      test("contramap(f).contramap(g) ≡ contramap(g andThen f)") {
        check(genIntStream, Gen.function[Any, Int, Int](genInt), Gen.function[Any, Int, Int](genInt)) { (s, f, g) =>
          val data = s.runCollect.getOrElse(Chunk.empty)
          val r1   = run(Stream.fromChunk(data), Sink.sumInt.contramap[Int](f).contramap(g))
          val r2   = run(Stream.fromChunk(data), Sink.sumInt.contramap[Int](g andThen f))
          assert(r1)(equalTo(r2))
        }
      },
      suite("regressions")(
        test("contramap_single [AdversarialSinkCombinatorSpec]") {
          val sink = Sink.collectAll[Int].contramap[Int](_ + 1)
          assertTrue(Stream.range(0, 3).run(sink) == Right(Chunk(1, 2, 3)))
        },
        test("contramap_compose_order [AdversarialSinkCombinatorSpec]") {
          // sink.contramap(g1).contramap(g2): g2 applied first, then g1.
          // value 1 -> g2(*10)=10 -> g1(+1)=11
          val sink = Sink.collectAll[Int].contramap[Int](_ + 1).contramap[Int](_ * 10)
          assertTrue(Stream.range(1, 3).run(sink) == Right(Chunk(11, 21)))
        },
        // Convergence probes (run 5): `Sink.Contramapped.drain` mutates a
        // sealed Interpreter in place via addMap; verify the pre-processor
        // applies and the lane stays honest on the deep (interpreter) path,
        // including a stream whose last structural op is a flatMap push.
        test("contramap_overInterpreterCompiledStream_appliesPreprocessor [AdversarialSinkInterpreterProbe]") {
          val deep = (0 until 150).foldLeft(Stream(1, 2, 3))((s, _) => s.map(identity))
          val r    = deep.run(Sink.collectAll[String].contramap[Int](i => "v" + i))
          assertTrue(r == Right(Chunk("v1", "v2", "v3")))
        },
        test("contramap_overInterpreterEndingInFlatMap_appliesPreprocessor [AdversarialSinkInterpreterProbe]") {
          val deep = (0 until 150).foldLeft(Stream(1, 2))((s, _) => s.map(identity)).flatMap(i => Stream(i, i))
          val r    = deep.run(Sink.collectAll[String].contramap[Int](i => "v" + i))
          assertTrue(r == Right(Chunk("v1", "v1", "v2", "v2")))
        },
        // R6 convergence probe: contramap routes through the boxed OUT_R lane on
        // an interpreter-compiled stream; a primitive-SPECIALIZED downstream sink
        // (sumLong) must then take its generic drain branch and still produce the
        // correct sum (lane honesty after a post-seal contramap append).
        test("contramap_overInterpreter_toPrimitiveSpecializedSink [AdversarialSinkInterpreterProbe]") {
          val deep = (0 until 150).foldLeft(Stream.range(1, 4))((s, _) => s.map(identity))
          val r    = deep.run(Sink.sumLong.contramap[Int](_.toLong))
          assertTrue(r == Right(6L))
        }
      )
    ),
    suite("mapError")(
      test("mapError identity ≡ identity on success") {
        check(genIntStream) { s =>
          val data = s.runCollect.getOrElse(Chunk.empty)
          assert(Stream.fromChunk(data).run(Sink.drain.mapError[Nothing](identity)))(equalTo(Right(())))
        }
      },
      test("mapError transforms the error") {
        val sink: Sink[String, Int, Unit] = Sink.drain.mapError[String](identity)
        assert(Stream.fail("oops").run(sink))(equalTo(Left("oops")))
      },
      suite("regressions")(
        test("mapError_appliesToSinkError [AdversarialSinkCombinatorSpec]") {
          val sink: Sink[String, Int, Nothing] = Sink.fail("boom").mapError[String](e => s"wrapped:$e")
          assertTrue(Stream.range(0, 3).run(sink) == Left("wrapped:boom"))
        },
        test("mapError_doesNotTouchStreamError [AdversarialSinkCombinatorSpec]") {
          // A stream-origin error must reach the Left untouched by the sink's mapError.
          val sink: Sink[String, Int, Chunk[Int]] = Sink.collectAll[Int].mapError[String](_ => "should-not-apply")
          val streamErr: Stream[String, Int]      = Stream.fail("streamboom")
          val r                                   = streamErr.run(sink)
          assertTrue(r == Left("streamboom"))
        }
      )
    ),
    suite("drain")(
      test("drain discards all elements") {
        check(genIntStream) { s =>
          assert(s.run(Sink.drain))(equalTo(Right(())))
        }
      },
      test("drain propagates upstream error") {
        assert(Stream.fail("err").run(Sink.drain))(equalTo(Left("err")))
      }
    ),
    suite("count")(
      test("count counts all elements") {
        check(genIntStream) { s =>
          val data = s.runCollect.getOrElse(Chunk.empty)
          assert(Stream.fromChunk(data).run(Sink.count))(equalTo(Right(data.length.toLong)))
        }
      },
      suite("regressions")(
        test("count_int [AdversarialSinkCombinatorSpec]") {
          assertTrue(Stream.range(0, 7).run(Sink.count) == Right(7L))
        },
        test("count_long_withMaxValue [AdversarialSinkCombinatorSpec]") {
          assertTrue(Stream.fromChunk(Chunk(Long.MaxValue, 1L, 2L)).run(Sink.count) == Right(3L))
        },
        test("count_empty [AdversarialSinkCombinatorSpec]") {
          assertTrue((Stream.empty: Stream[Nothing, Int]).run(Sink.count) == Right(0L))
        }
      )
    ),
    suite("collectAll")(
      test("collectAll collects all elements") {
        check(genIntStream) { s =>
          val data = s.runCollect.getOrElse(Chunk.empty)
          assert(Stream.fromChunk(data).run(Sink.collectAll[Int]))(equalTo(Right(data)))
        }
      },
      test("collectAll propagates upstream error") {
        assert(Stream.fail("err").run(Sink.collectAll[Int]))(equalTo(Left("err")))
      },
      suite("regressions")(
        test("collectAll_long_containsMaxValue_preserved [AdversarialSinkCombinatorSpec]") {
          val xs = List(Long.MaxValue, 1L, Long.MaxValue)
          val r  = Stream.fromChunk(Chunk.fromIterable(xs)).runCollect
          assertTrue(r == Right(Chunk(Long.MaxValue, 1L, Long.MaxValue)))
        },
        test("collectAll_double_containsMaxValue_preserved [AdversarialSinkCombinatorSpec]") {
          val xs = List(Double.MaxValue, 1.0, Double.MaxValue)
          val r  = Stream.fromChunk(Chunk.fromIterable(xs)).runCollect
          assertTrue(r == Right(Chunk(Double.MaxValue, 1.0, Double.MaxValue)))
        },
        test("collectAll_double_containsNaN_preserved [AdversarialSinkCombinatorSpec]") {
          val r = Stream.fromChunk(Chunk(Double.NaN, 1.0)).runCollect
          assertTrue(r.fold(_ => false, c => c.length == 2 && c(0).isNaN && c(1) == 1.0))
        },
        test("collectAll_int_matchesInput [AdversarialSinkCombinatorSpec]") {
          assertTrue(Stream.fromChunk(Chunk(1, 2, 3)).runCollect == Right(Chunk(1, 2, 3)))
        },
        test("collectAll_byte_matchesInput [AdversarialSinkCombinatorSpec]") {
          val xs: Chunk[Byte] = Chunk[Byte](-128, -1, 0, 1, 127)
          assertTrue(Stream.fromChunk(xs).runCollect == Right(xs))
        },
        test("collectAll_empty_isEmpty [AdversarialSinkCombinatorSpec]") {
          assertTrue((Stream.empty: Stream[Nothing, Int]).runCollect == Right(Chunk.empty[Int]))
        }
      )
    ),
    suite("foldLeft")(
      test("foldLeft agrees with Chunk.foldLeft") {
        check(genIntStream, genInt, Gen.function2(genInt)) { (s, z, f) =>
          val data = s.runCollect.getOrElse(Chunk.empty)
          assert(Stream.fromChunk(data).run(Sink.foldLeft(z)(f)))(
            equalTo(Right(data.foldLeft(z)(f)))
          )
        }
      },
      suite("regressions")(
        test("foldLeft_int_intAcc_matchesScala [AdversarialSinkCombinatorSpec]") {
          val xs = List(1, 2, 3, 4, 5)
          val r  = Stream.fromChunk(Chunk.fromIterable(xs)).run(Sink.foldLeft(0)(_ + _))
          assertTrue(r == Right(xs.foldLeft(0)(_ + _)))
        },
        test("foldLeft_int_longAcc_matchesScala [AdversarialSinkCombinatorSpec]") {
          val xs = List(1, 2, 3)
          val r  = Stream.fromChunk(Chunk.fromIterable(xs)).run(Sink.foldLeft(0L)((a, x) => a + x.toLong))
          assertTrue(r == Right(xs.foldLeft(0L)((a, x) => a + x.toLong)))
        },
        test("foldLeft_int_stringAcc_matchesScala [AdversarialSinkCombinatorSpec]") {
          val xs = List(1, 2, 3)
          val r  = Stream.fromChunk(Chunk.fromIterable(xs)).run(Sink.foldLeft("")((a, x) => a + x.toString))
          assertTrue(r == Right("123"))
        },
        test("foldLeft_long_longAcc_matchesScala [AdversarialSinkCombinatorSpec]") {
          val xs = List(10L, 20L, 30L)
          val r  = Stream.fromChunk(Chunk.fromIterable(xs)).run(Sink.foldLeft(0L)(_ + _))
          assertTrue(r == Right(60L))
        },
        test("foldLeft_double_doubleAcc_matchesScala [AdversarialSinkCombinatorSpec]") {
          val xs = List(1.5, 2.5, 3.0)
          val r  = Stream.fromChunk(Chunk.fromIterable(xs)).run(Sink.foldLeft(0.0)(_ + _))
          assertTrue(r == Right(7.0))
        },
        test("foldLeft_float_doubleAcc_matchesScala [AdversarialSinkCombinatorSpec]") {
          val xs = List(1.5f, 2.5f)
          val r  = Stream.fromChunk(Chunk.fromIterable(xs)).run(Sink.foldLeft(0.0)((a, x) => a + x.toDouble))
          assertTrue(r == Right(4.0))
        },
        test("foldLeft_empty_returnsZero [AdversarialSinkCombinatorSpec]") {
          val r = (Stream.empty: Stream[Nothing, Int]).run(Sink.foldLeft(42)(_ + _))
          assertTrue(r == Right(42))
        },
        test("foldLeft_long_containsMaxValue_notDroppedAsSentinel [AdversarialSinkCombinatorSpec]") {
          // Long lane uses Long.MaxValue as EOF sentinel; a real Long.MaxValue
          // element must still be folded (disambiguated). Differential vs Scala.
          val xs = List(1L, Long.MaxValue, 2L)
          val r  = Stream
            .fromChunk(Chunk.fromIterable(xs))
            .run(Sink.foldLeft(0L)((a, x) => a + (if (x == Long.MaxValue) 1L else x)))
          assertTrue(r == Right(4L))
        }
      )
    ),
    suite("foreach")(
      test("foreach calls f for each element") {
        check(genIntStream) { s =>
          val buf  = new scala.collection.mutable.ArrayBuffer[Int]()
          val data = s.runCollect.getOrElse(Chunk.empty)
          Stream.fromChunk(data).run(Sink.foreach[Int](a => buf += a))
          assert(buf.toList)(equalTo(data.toList))
        }
      }
    ),
    suite("head")(
      test("head returns first element") {
        check(genIntStream) { s =>
          val data = s.runCollect.getOrElse(Chunk.empty)
          assert(Stream.fromChunk(data).run(Sink.head[Int]))(
            equalTo(Right(data.headOption))
          )
        }
      },
      suite("regressions")(
        test("head_empty_none [AdversarialSinkCombinatorSpec]") {
          assertTrue((Stream.empty: Stream[Nothing, Int]).run(Sink.head[Int]) == Right(None))
        },
        test("head_nonEmpty [AdversarialSinkCombinatorSpec]") {
          assertTrue(Stream.range(5, 10).run(Sink.head[Int]) == Right(Some(5)))
        },
        test("head_shortCircuit_closesSourceOnce [AdversarialSinkCombinatorSpec]") {
          val closes = new AtomicInteger(0)
          val s      =
            Stream.fromAcquireRelease((), (_: Unit) => { closes.incrementAndGet(); () })(_ => Stream.range(0, 100))
          val r = s.run(Sink.head[Int])
          assertTrue(r == Right(Some(0))) && assertTrue(closes.get == 1)
        }
      )
    ),
    suite("last")(
      test("last returns last element") {
        check(genIntStream) { s =>
          val data = s.runCollect.getOrElse(Chunk.empty)
          assert(Stream.fromChunk(data).run(Sink.last[Int]))(
            equalTo(Right(data.lastOption))
          )
        }
      },
      suite("regressions")(
        test("last_empty_none [AdversarialSinkCombinatorSpec]") {
          assertTrue((Stream.empty: Stream[Nothing, Int]).run(Sink.last[Int]) == Right(None))
        },
        test("last_nonEmpty [AdversarialSinkCombinatorSpec]") {
          assertTrue(Stream.range(5, 10).run(Sink.last[Int]) == Right(Some(9)))
        },
        test("last_long_maxValue_preserved [AdversarialSinkCombinatorSpec]") {
          assertTrue(Stream.fromChunk(Chunk(1L, Long.MaxValue)).run(Sink.last[Long]) == Right(Some(Long.MaxValue)))
        }
      )
    ),
    suite("take")(
      test("take(n) collects first n elements") {
        check(genIntStream, Gen.int(0, 20)) { (s, n) =>
          val data = s.runCollect.getOrElse(Chunk.empty)
          assert(Stream.fromChunk(data).run(Sink.take[Int](n)))(
            equalTo(Right(data.take(n)))
          )
        }
      },
      suite("regressions")(
        test("take_zero_isEmpty [AdversarialSinkCombinatorSpec]") {
          assertTrue(Stream.range(0, 10).run(Sink.take[Int](0)) == Right(Chunk.empty[Int]))
        },
        test("take_exact_len [AdversarialSinkCombinatorSpec]") {
          assertTrue(Stream.range(0, 3).run(Sink.take[Int](3)) == Right(Chunk(0, 1, 2)))
        },
        test("take_more_than_len [AdversarialSinkCombinatorSpec]") {
          assertTrue(Stream.range(0, 3).run(Sink.take[Int](100)) == Right(Chunk(0, 1, 2)))
        },
        test("take_byte_partial [AdversarialSinkCombinatorSpec]") {
          assertTrue(Stream.fromChunk(Chunk[Byte](1, 2, 3, 4)).run(Sink.take[Byte](2)) == Right(Chunk[Byte](1, 2)))
        },
        test("take_generic_huge_n_shortStream_noOOM [AdversarialSinkCombinatorSpec]") {
          // take(n) takes UP TO n; a huge n over a short generic stream must not
          // pre-allocate n (BUG-P1 regression).
          val r = Stream.fromChunk(Chunk("a", "b")).run(Sink.take[String](Int.MaxValue))
          assertTrue(r == Right(Chunk("a", "b")))
        }
      )
    ),
    suite("find")(
      suite("regressions")(
        test("find_firstMatch_stopsEarly [AdversarialSinkCombinatorSpec]") {
          assertTrue(Stream.range(0, 100).run(Sink.find[Int](_ >= 3)) == Right(Some(3)))
        },
        test("find_noMatch_none [AdversarialSinkCombinatorSpec]") {
          assertTrue(Stream.range(0, 5).run(Sink.find[Int](_ > 100)) == Right(None))
        },
        test("find_byte_lane [AdversarialSinkCombinatorSpec]") {
          assertTrue(
            Stream.fromChunk(Chunk[Byte](1, 2, 3)).run(Sink.find[Byte](_ == 2.toByte)) == Right(Some(2.toByte))
          )
        },
        test("find_shortCircuit_closesSourceOnce [AdversarialSinkCombinatorSpec]") {
          val closes = new AtomicInteger(0)
          val s      =
            Stream.fromAcquireRelease((), (_: Unit) => { closes.incrementAndGet(); () })(_ => Stream.range(0, 100))
          val r = s.run(Sink.find[Int](_ == 5))
          assertTrue(r == Right(Some(5))) && assertTrue(closes.get == 1)
        }
      )
    ),
    suite("exists")(
      suite("regressions")(
        test("exists_true [AdversarialSinkCombinatorSpec]") {
          assertTrue(Stream.range(0, 5).run(Sink.exists[Int](_ == 3)) == Right(true))
        },
        test("exists_false_empty [AdversarialSinkCombinatorSpec]") {
          assertTrue((Stream.empty: Stream[Nothing, Int]).run(Sink.exists[Int](_ => true)) == Right(false))
        }
      )
    ),
    suite("forall")(
      suite("regressions")(
        test("forall_true_empty [AdversarialSinkCombinatorSpec]") {
          assertTrue((Stream.empty: Stream[Nothing, Int]).run(Sink.forall[Int](_ => false)) == Right(true))
        },
        test("forall_false_shortCircuit [AdversarialSinkCombinatorSpec]") {
          assertTrue(Stream.range(0, 100).run(Sink.forall[Int](_ < 3)) == Right(false))
        }
      )
    ),
    suite("sum")(
      test("sumInt sums all ints") {
        check(genIntStream) { s =>
          val data = s.runCollect.getOrElse(Chunk.empty)
          assert(Stream.fromChunk(data).run(Sink.sumInt))(
            equalTo(Right(data.foldLeft(0L)(_ + _.toLong)))
          )
        }
      },
      test("sumLong sums all longs") {
        check(Gen.chunkOfBounded(0, 20)(Gen.long(-1000, 1000)).map(Chunk.fromIterable(_))) { data =>
          assert(Stream.fromChunk(data).run(Sink.sumLong))(
            equalTo(Right(data.foldLeft(0L)(_ + _)))
          )
        }
      },
      test("sumFloat sums all floats into Double") {
        check(Gen.chunkOfBounded(0, 20)(Gen.int(-1000, 1000).map(_.toFloat)).map(Chunk.fromIterable(_))) { data =>
          assert(Stream.fromChunk(data).run(Sink.sumFloat))(
            equalTo(Right(data.foldLeft(0.0)(_ + _.toDouble)))
          )
        }
      },
      test("sumDouble sums all doubles") {
        check(Gen.chunkOfBounded(0, 20)(Gen.double(-1000.0, 1000.0)).map(Chunk.fromIterable(_))) { data =>
          assert(Stream.fromChunk(data).run(Sink.sumDouble))(
            equalTo(Right(data.foldLeft(0.0)(_ + _)))
          )
        }
      }
    ),
    suite("create")(
      test("create — primitive constructor receives dequeue and returns result") {
        val sink = Sink.create[Nothing, Int, Chunk[Int]] { dq =>
          val buf = new scala.collection.mutable.ArrayBuffer[Int]()
          var v   = dq.read[Any](null)
          while (v != null) { buf += v.asInstanceOf[Int]; v = dq.read[Any](null) }
          Chunk.fromIterable(buf)
        }
        check(genIntStream) { s =>
          val data = s.runCollect.getOrElse(Chunk.empty)
          assert(Stream.fromChunk(data).run(sink))(equalTo(Right(data)))
        }
      }
    ),
    suite("Java interop")(
      suite("fromOutputStream")(
        test("writes all bytes to the output stream") {
          val bos    = new ByteArrayOutputStream()
          val sink   = Sink.fromOutputStream(bos)
          val bytes  = Chunk[Byte](1, 2, 3)
          val result = Stream.fromChunk(bytes).run(sink)
          assertTrue(result == Right(())) &&
          assertTrue(bos.toByteArray.toList == List[Byte](1, 2, 3))
        },
        test("handles empty stream") {
          val bos    = new ByteArrayOutputStream()
          val sink   = Sink.fromOutputStream(bos)
          val result = Stream.fromChunk(Chunk.empty[Byte]).run(sink)
          assertTrue(result == Right(())) &&
          assertTrue(bos.size() == 0)
        },
        test("writes high bytes correctly (0xFF)") {
          val bos    = new ByteArrayOutputStream()
          val sink   = Sink.fromOutputStream(bos)
          val bytes  = Chunk[Byte](0xff.toByte)
          val result = Stream.fromChunk(bytes).run(sink)
          assertTrue(result == Right(())) &&
          assertTrue((bos.toByteArray()(0) & 0xff) == 0xff)
        },
        test("writes single byte stream") {
          val bos    = new ByteArrayOutputStream()
          val sink   = Sink.fromOutputStream(bos)
          val result = Stream.fromChunk(Chunk[Byte](42)).run(sink)
          assertTrue(result == Right(())) &&
          assertTrue(bos.toByteArray.toList == List[Byte](42))
        },
        test("writes multiple chunks in order") {
          val bos    = new ByteArrayOutputStream()
          val sink   = Sink.fromOutputStream(bos)
          val stream = Stream.fromChunk(Chunk[Byte](1, 2)) ++
            Stream.fromChunk(Chunk[Byte](3, 4))
          val result = stream.run(sink)
          assertTrue(result == Right(())) &&
          assertTrue(bos.toByteArray.toList == List[Byte](1, 2, 3, 4))
        },
        test("writes negative byte values correctly") {
          val bos    = new ByteArrayOutputStream()
          val sink   = Sink.fromOutputStream(bos)
          val bytes  = Chunk[Byte](-1, -128, 127)
          val result = Stream.fromChunk(bytes).run(sink)
          assertTrue(result == Right(())) &&
          assertTrue(bos.toByteArray.toList == List[Byte](-1, -128, 127))
        }
      ),
      suite("fromJavaWriter")(
        test("writes all chars to the writer") {
          val sw     = new StringWriter()
          val sink   = Sink.fromJavaWriter(sw)
          val result = Stream.fromJavaReader(new java.io.StringReader("hello")).run(sink)
          assertTrue(result == Right(())) &&
          assertTrue(sw.toString == "hello")
        },
        test("handles empty stream") {
          val sw     = new StringWriter()
          val sink   = Sink.fromJavaWriter(sw)
          val result = Stream.fromJavaReader(new java.io.StringReader("")).run(sink)
          assertTrue(result == Right(())) &&
          assertTrue(sw.toString == "")
        },
        test("writes unicode chars") {
          val sw     = new StringWriter()
          val sink   = Sink.fromJavaWriter(sw)
          val result = Stream.fromJavaReader(new java.io.StringReader("café")).run(sink)
          assertTrue(result == Right(())) &&
          assertTrue(sw.toString == "café")
        },
        test("writes single character stream") {
          val sw     = new StringWriter()
          val sink   = Sink.fromJavaWriter(sw)
          val result = Stream.fromJavaReader(new java.io.StringReader("X")).run(sink)
          assertTrue(result == Right(())) &&
          assertTrue(sw.toString == "X")
        },
        test("writes chars from chunk-based stream") {
          val sw     = new StringWriter()
          val sink   = Sink.fromJavaWriter(sw)
          val result = Stream.fromChunk(Chunk('a', 'b', 'c')).run(sink)
          assertTrue(result == Right(())) &&
          assertTrue(sw.toString == "abc")
        },
        test("writes multiple concatenated streams") {
          val sw     = new StringWriter()
          val sink   = Sink.fromJavaWriter(sw)
          val stream = Stream.fromJavaReader(new java.io.StringReader("hel")) ++
            Stream.fromJavaReader(new java.io.StringReader("lo"))
          val result = stream.run(sink)
          assertTrue(result == Right(())) &&
          assertTrue(sw.toString == "hello")
        }
      )
    ),
    run10ConvergenceSuite,
    run11ConvergenceSuite
  )

  // ---- Run #10 convergence probes ------------------------------------------
  // Passing adversarial probes from the tenth hardening round: sink-origin vs
  // stream-origin error routing through contramap/mapError/Pipeline, hostile
  // sink callbacks with exactly-once upstream finalization, sink reuse, and
  // accumulator-lane fold dispatch. Committed as convergence evidence.
  private case class Run10SinkBoom(n: Int) extends RuntimeException(s"boom-$n")

  private def run10ConvergenceSuite = suite("run10 convergence")(
    test("failThroughContramap_surfacesAsLeft_withNoElementConsumed") {
      val consumed = new AtomicInteger(0)
      val s        = Stream(0, 1, 2).tapEach(_ => consumed.incrementAndGet())
      val sink     = Sink.fail("sink-err").contramap[Int](identity)
      assertTrue(s.run(sink) == Left("sink-err"), consumed.get() == 0)
    },
    test("failMapError_throughPipelineAndThenSink_routesToMappedLeft") {
      val sink: Sink[String, Int, Nothing]   = Sink.fail("e1")
      val mapped: Sink[String, Int, Nothing] = sink.mapError(e => e + "-mapped")
      val piped                              = Pipeline.map[Int, Int](_ + 1).andThenSink(mapped)
      assertTrue(Stream(0, 1, 2).run(piped) == Left("e1-mapped"))
    },
    test("mapError_neverRewritesStreamOriginError") {
      val sink: Sink[String, Int, Chunk[Int]] =
        Sink.collectAll[Int].mapError((e: String) => e + "-SINKMAPPED")
      val res = (Stream(0, 1) ++ Stream.fail("stream-err")).run(sink)
      assertTrue(res == Left("stream-err"))
    },
    test("pipelineAndThenSink_streamErrorInsidePipedSection_surfacesAsLeft") {
      val piped = Pipeline.take[Int](10).andThenSink(Sink.collectAll[Int])
      val res   = (Stream(0, 1, 2) ++ Stream.fail("E")).run(piped)
      assertTrue(res == Left("E"))
    },
    test("contramappedSink_isReusableAcrossRuns") {
      val sink = Sink.collectAll[Int].contramap[Int](_ * 2)
      val r1   = Stream(0, 1, 2).run(sink).map(_.toList)
      val r2   = Stream(0, 1, 2).run(sink).map(_.toList)
      assertTrue(r1 == Right(List(0, 2, 4)), r2 == Right(List(0, 2, 4)))
    },
    test("foldLeftStepThrows_upstreamEnsuringFiresExactlyOnce") {
      val closes = new AtomicInteger(0)
      val s      = Stream(0, 1, 2, 3).ensuring(closes.incrementAndGet())
      val sink   = Sink.foldLeft[Int, Int](0)((acc, a) => if (a == 1) throw Run10SinkBoom(5) else acc + a)
      val r      =
        try { s.run(sink); "no-throw" }
        catch { case Run10SinkBoom(5) => "boom" }
      assertTrue(r == "boom", closes.get() == 1)
    },
    test("contramapGThrows_defectPropagates_ensuringFiresOnce") {
      val closes = new AtomicInteger(0)
      val s      = Stream(0, 1, 2).ensuring(closes.incrementAndGet())
      val sink   = Sink.collectAll[Int].contramap[Int](i => if (i == 1) throw Run10SinkBoom(6) else i)
      val r      =
        try { s.run(sink); "no-throw" }
        catch { case Run10SinkBoom(6) => "boom" }
      assertTrue(r == "boom", closes.get() == 1)
    },
    test("foldLeft_accumulatorLaneMatrix_booleanFloatString") {
      assertTrue(
        Stream(0, 1, 2).runFold(true)((b, i) => b && i >= 0) == Right(true),
        Stream(1L, 2L).runFold(true)((b, l) => b && l > 0L) == Right(true),
        Stream(1.5, 2.5).runFold(false)((b, d) => b || d > 2.0) == Right(true),
        Stream(1.5f).runFold(true)((b, f) => b && f > 1.0f) == Right(true),
        Stream(0, 1, 2, 3).run(Sink.foldLeft[Int, Float](0.5f)((f, i) => f + i.toFloat)) == Right(6.5f),
        Stream(0, 1, 2).runFold("")((s, i) => s + i) == Right("012"),
        Stream(1L, 2L).runFold("")((s, l) => s + l) == Right("12"),
        Stream(1.5).runFold("")((s, d) => s + d) == Right("1.5"),
        Stream(1.5f).runFold("")((s, f) => s + f) == Right("1.5"),
        Stream.fromChunk(Chunk.fromIterable(List[Byte](7))).runFold("")((s, b) => s + b) == Right("7")
      )
    },
    test("sinkCreate_readUpToNOverCatchAllRecovery_lossless") {
      val s    = (Stream(0, 1, 2) ++ Stream.fail("x")).catchAll(_ => Stream(100, 101, 102))
      val sink = Sink.create[Nothing, Int, List[Int]] { r =>
        var acc = List.empty[Int]
        var c   = r.readUpToN[Int](2)
        while (c.nonEmpty) { acc = acc ++ c.toList; c = r.readUpToN[Int](2) }
        acc
      }
      assertTrue(s.run(sink) == Right(List(0, 1, 2, 100, 101, 102)))
    }
  )

  // ---- Run #11 convergence probes ------------------------------------------
  // Eleventh (convergence-verification) round: user-written manual sinks via
  // `Sink.create` exercising paths run 10 did not — bulk array pulls
  // (`readLongs`) over streams whose elements equal the in-band Long.MaxValue
  // EOF sentinel (shallow AND deep/interpreter compilation), partial
  // consumption returning before EOF, and manual sinks composed under
  // Pipeline/contramap over flatMap and zip streams. Committed as convergence
  // evidence.
  private def run11ConvergenceSuite = suite("run11 convergence")(
    test("manualBulkReadLongsSink_overMaxValueElements_shallowAndDeep") {
      def bulkSink = Sink.create[Nothing, Long, List[Long]] { r =>
        val buf = new Array[Long](4)
        val acc = List.newBuilder[Long]
        var n   = r.readLongs(buf, 0, 4)
        while (n > 0) {
          var i = 0
          while (i < n) { acc += buf(i); i += 1 }
          n = r.readLongs(buf, 0, 4)
        }
        acc.result()
      }
      val xs      = List(1L, Long.MaxValue, 3L, Long.MaxValue, 5L)
      def base    = Stream.fromChunk(Chunk.fromIterable(xs))
      val shallow = base.run(bulkSink)
      val deep    = (0 until 101).foldLeft(base: Stream[Nothing, Long])((s, _) => s.map((x: Long) => x)).run(bulkSink)
      assertTrue(shallow == Right(xs), deep == Right(xs))
    },
    test("manualPartialSink_returnsBeforeEOF_streamFinalizerFiresOnce") {
      val fin  = new AtomicInteger(0)
      val head = Sink.create[Nothing, Int, Option[Int]](r => r.readUpToN[Int](1).headOption)
      val res  = Stream(1, 2, 3).ensuring { fin.incrementAndGet(); () }.run(head)
      assertTrue(res == Right(Some(1)), fin.get() == 1)
    },
    test("manualSumSink_underPipelineTakeAndContramap_overFlatMap_matchesViaStream") {
      def sumSink = Sink.create[Nothing, Int, Int] { r =>
        var sum = 0
        var c   = r.readUpToN[Int](3)
        while (c.nonEmpty) {
          val it = c.iterator
          while (it.hasNext) sum += it.next()
          c = r.readUpToN[Int](3)
        }
        sum
      }
      def s         = Stream.range(0, 6).flatMap(i => Stream(i, i * 10))
      val viaSink   = s.run(Pipeline.take[Int](5L).andThenSink(sumSink.contramap[Int](_ + 1)))
      val viaStream = s.take(5L).map(_ + 1).runFold(0)(_ + _)
      assertTrue(viaSink == viaStream, viaSink == Right(18))
    },
    test("manualBatchedSink_overZipStream_lossless") {
      def listSink = Sink.create[Nothing, (Int, Int), List[(Int, Int)]] { r =>
        var acc = List.empty[(Int, Int)]
        var c   = r.readUpToN[(Int, Int)](2)
        while (c.nonEmpty) { acc = acc ++ c.toList; c = r.readUpToN[(Int, Int)](2) }
        acc
      }
      val z = Stream(1, 2, 3) && Stream(10, 20, 30, 40)
      assertTrue(z.run(listSink) == Right(List((1, 10), (2, 20), (3, 30))))
    }
  )
}
