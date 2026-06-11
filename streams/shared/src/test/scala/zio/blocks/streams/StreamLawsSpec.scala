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
import zio.blocks.streams.internal.StreamError
import zio.blocks.streams.io.Reader
import zio.test._
import zio.test.Assertion._
import StreamsGen._

import java.util.concurrent.atomic.AtomicInteger

/**
 * Property-based tests for [[Stream]] algebraic laws and constructor coverage.
 *
 * Laws tested:
 *   - Functor: `map(id) ≡ id`, `map(f).map(g) ≡ map(f andThen g)`
 *   - Monad: left-identity, right-identity, associativity
 *   - Concat: left/right identity, associativity
 *   - Run: `runForeach ≡ run(foreach)`, `runCollect ≡ run(collectAll)`,
 *     `runDrain ≡ run(drain)`, `runFold ≡ run(foldLeft)`
 *   - Error: `fail(e)` surfaces as `Left(e)`, `mapError` laws
 *   - take/drop: spec'd lengths, `take(n) ++ drop(n) ≡ id`
 *   - filter: keeps exactly matching elements
 *
 * Every constructor (`empty`, `succeed`, `fail`, `range`, `fromRange`,
 * `fromChunk`, `fromIterable`, `fromReader`, `create`, `repeat`, `unfold`,
 * `flattenAll`) is exercised by at least one test.
 */
object StreamLawsSpec extends StreamsBaseSpec {

  private def collect[A](s: Stream[Nothing, A]): Chunk[A] =
    s.runCollect.fold(_ => Chunk.empty, identity)

  private def managed(opens: AtomicInteger, closes: AtomicInteger, elems: Int*): Stream[Nothing, Int] =
    Stream.fromAcquireRelease({ opens.incrementAndGet(); () }, (_: Unit) => { closes.incrementAndGet(); () })(_ =>
      Stream(elems: _*)
    )

  final case class Err(tag: Int)

  def spec: Spec[TestEnvironment, Any] = suite("Stream laws")(
    // ---- Functor ------------------------------------------------------------

    suite("Functor")(
      test("map(identity) ≡ identity") {
        check(genIntStream) { s =>
          assert(collect(s.map(identity)))(equalTo(collect(s)))
        }
      },
      test("map(f).map(g) ≡ map(f andThen g)") {
        check(genIntStream, Gen.function(genInt), Gen.function(genInt)) { (s, f, g) =>
          assert(collect(s.map(f).map(g)))(equalTo(collect(s.map(f andThen g))))
        }
      }
    ),

    // ---- Monad --------------------------------------------------------------

    suite("Monad")(
      test("succeed(a).flatMap(f) ≡ f(a)") {
        check(genInt, Gen.function(genStream(genInt))) { (a, f) =>
          assert(collect(Stream.succeed(a).flatMap(f)))(equalTo(collect(f(a))))
        }
      },
      test("s.flatMap(Stream.succeed) ≡ s") {
        check(genIntStream) { s =>
          assert(collect(s.flatMap(Stream.succeed)))(equalTo(collect(s)))
        }
      },
      test("associativity: s.flatMap(f).flatMap(g) ≡ s.flatMap(a => f(a).flatMap(g))") {
        val small = Gen.chunkOfBounded(0, 3)(genInt).map(c => Stream.fromChunk(Chunk.fromIterable(c)))
        check(small, Gen.function(small), Gen.function(small)) { (s, f, g) =>
          assert(collect(s.flatMap(f).flatMap(g)))(
            equalTo(collect(s.flatMap(a => f(a).flatMap(g))))
          )
        }
      }
    ),

    // ---- Concat -------------------------------------------------------------

    suite("Concat")(
      test("empty ++ s ≡ s") {
        check(genIntStream) { s =>
          assert(collect(Stream.empty ++ s))(equalTo(collect(s)))
        }
      },
      test("s ++ empty ≡ s") {
        check(genIntStream) { s =>
          assert(collect(s ++ Stream.empty))(equalTo(collect(s)))
        }
      },
      test("(s ++ t) ++ u ≡ s ++ (t ++ u)") {
        check(genIntStream, genIntStream, genIntStream) { (s, t, u) =>
          assert(collect((s ++ t) ++ u))(equalTo(collect(s ++ (t ++ u))))
        }
      }
    ),

    // ---- Run operators ------------------------------------------------------

    suite("Run operators")(
      test("runForeach(f) ≡ run(Sink.foreach(f))") {
        check(genIntStream) { s =>
          val buf1 = new scala.collection.mutable.ArrayBuffer[Int]()
          val buf2 = new scala.collection.mutable.ArrayBuffer[Int]()
          val data = collect(s)
          Stream.fromChunk(data).runForeach(a => buf1 += a)
          Stream.fromChunk(data).run(Sink.foreach[Int](a => buf2 += a))
          assert(buf1.toList)(equalTo(buf2.toList))
        }
      },
      test("runCollect ≡ run(Sink.collectAll)") {
        check(genIntStream) { s =>
          val data = collect(s)
          assert(Stream.fromChunk(data).runCollect)(
            equalTo(Stream.fromChunk(data).run(Sink.collectAll[Int]))
          )
        }
      },
      test("runDrain ≡ run(Sink.drain)") {
        check(genIntStream) { s =>
          val data = collect(s)
          assert(Stream.fromChunk(data).runDrain)(
            equalTo(Stream.fromChunk(data).run(Sink.drain))
          )
        }
      },
      test("runFold(z)(f) ≡ run(Sink.foldLeft(z)(f))") {
        check(genIntStream, Gen.function2(genInt)) { (s, f) =>
          val data = collect(s)
          assert(Stream.fromChunk(data).runFold(0)(f))(
            equalTo(Stream.fromChunk(data).run(Sink.foldLeft(0)(f)))
          )
        }
      }
    ),

    // ---- Error channel ------------------------------------------------------

    suite("Error channel")(
      test("fail(e).run(sink) returns Left(e)") {
        assert(Stream.fail("boom").run(Sink.drain))(equalTo(Left("boom")))
      },
      test("mapError transforms the error") {
        assert(Stream.fail(42).mapError(_.toString).run(Sink.drain))(equalTo(Left("42")))
      },
      test("mapError(identity) ≡ identity") {
        check(genIntStream) { s =>
          assert(collect(s.mapError[Nothing](identity)))(equalTo(collect(s)))
        }
      },
      test("mapError(f).mapError(g) ≡ mapError(f andThen g)") {
        val e1 = Stream.fail(1).mapError(_ + 1).mapError(_ * 2)
        val e2 = Stream.fail(1).mapError(x => (x + 1) * 2)
        assert(e1.run(Sink.drain))(equalTo(e2.run(Sink.drain)))
      },
      suite("regressions")(
        test("typed error preserved losslessly through map/filter/take/scan [AdversarialFusionLawConvergenceSpec]") {
          val e: Err              = Err(42)
          val s: Stream[Err, Int] = Stream.fail(e)
          assertTrue(s.map(_ + 1).runCollect == Left(e)) &&
          assertTrue(s.filter(_ => true).runCollect == Left(e)) &&
          assertTrue(s.take(3).runCollect == Left(e)) &&
          assertTrue(s.scan(0)(_ + _).runCollect == Left(e))
        },
        test(
          "concat: first error wins and short-circuits; mapError composes; catchAll recovers [AdversarialFusionLawConvergenceSpec]"
        ) {
          val ran                     = new java.util.concurrent.atomic.AtomicBoolean(false)
          val sc: Stream[String, Int] =
            (Stream.fail("first"): Stream[String, Int])
              .concat(Stream.defer(ran.set(true)).asInstanceOf[Stream[String, Int]])
          val sm: Stream[Int, Nothing] = Stream.fail(1)
          assertTrue(sc.runCollect == Left("first")) &&
          assertTrue(!ran.get()) &&
          assertTrue(Stream(1, 2, 3).concat(Stream.fail("boom")).runCollect == Left("boom")) &&
          assertTrue(sm.mapError(_ + 1).mapError(_ * 10).runCollect == sm.mapError(x => (x + 1) * 10).runCollect) &&
          assertTrue(
            (Stream.fail("e"): Stream[String, Int]).catchAll((msg: String) => Stream(msg.length)).runCollect ==
              Right(Chunk(1))
          )
        }
      )
    ),

    // ---- take / drop --------------------------------------------------------

    suite("take / drop")(
      test("take(n) emits exactly min(n, len) elements") {
        check(genIntStream, Gen.int(0, 60)) { (s, n) =>
          val data = collect(s)
          assert(collect(Stream.fromChunk(data).take(n)))(equalTo(data.take(n)))
        }
      },
      test("drop(n) skips first min(n, len) elements") {
        check(genIntStream, Gen.int(0, 60)) { (s, n) =>
          val data = collect(s)
          assert(collect(Stream.fromChunk(data).drop(n)))(equalTo(data.drop(n)))
        }
      },
      test("take(n) ++ drop(n) ≡ identity") {
        check(genIntStream, Gen.int(0, 30)) { (s, n) =>
          val data     = collect(s)
          val combined = collect(Stream.fromChunk(data).take(n) ++ Stream.fromChunk(data).drop(n))
          assert(combined)(equalTo(data))
        }
      },
      test("takeWhile takes prefix satisfying predicate") {
        check(genIntStream, Gen.int(0, 30)) { (s, limit) =>
          val data     = collect(s)
          val expected = data.toList.takeWhile(_ < limit)
          assert(collect(Stream.fromChunk(data).takeWhile(_ < limit)))(
            equalTo(Chunk.fromIterable(expected))
          )
        }
      },
      test("takeWhile on empty stream is empty") {
        assert(collect(Stream.empty.takeWhile((_: Int) => true)))(equalTo(Chunk.empty))
      },
      test("takeWhile false is empty") {
        check(genIntStream) { s =>
          assert(collect(Stream.fromChunk(collect(s)).takeWhile(_ => false)))(equalTo(Chunk.empty))
        }
      },
      test("takeWhile true ≡ identity") {
        check(genIntStream) { s =>
          val data = collect(s)
          assert(collect(Stream.fromChunk(data).takeWhile(_ => true)))(equalTo(data))
        }
      },
      test("filter keeps exactly matching elements") {
        check(genIntStream, Gen.function(Gen.boolean)) { (s, pred) =>
          val data = collect(s)
          assert(collect(Stream.fromChunk(data).filter(pred)))(equalTo(data.filter(pred)))
        }
      },
      suite("regressions")(
        test("take_take_minSemantics_smallerSecond [AdversarialValueLawsSpec]") {
          val s = Stream.range(0, 10).take(5).take(3)
          assertTrue(s.runCollect == Right(Chunk(0, 1, 2)))
        },
        test("take_take_minSemantics_smallerFirst [AdversarialValueLawsSpec]") {
          val s = Stream.range(0, 10).take(3).take(5)
          assertTrue(s.runCollect == Right(Chunk(0, 1, 2)))
        },
        test("drop_beyondLength_thenTake_isEmpty [AdversarialValueLawsSpec]") {
          val s = Stream.range(0, 3).drop(5).take(2)
          assertTrue(s.runCollect == Right(Chunk.empty[Int]))
        },
        test("drop_zero_isIdentity [AdversarialValueLawsSpec]") {
          val s = Stream.range(0, 4).drop(0)
          assertTrue(s.runCollect == Right(Chunk(0, 1, 2, 3)))
        },
        test("takeWhile_allFalse_isEmpty [AdversarialValueLawsSpec]") {
          val s = Stream.range(0, 5).takeWhile(_ => false)
          assertTrue(s.runCollect == Right(Chunk.empty[Int]))
        },
        test("takeWhile_allTrue_isAll [AdversarialValueLawsSpec]") {
          val s = Stream.range(0, 5).takeWhile(_ => true)
          assertTrue(s.runCollect == Right(Chunk(0, 1, 2, 3, 4)))
        }
      )
    ),

    // ---- Constructors -------------------------------------------------------

    suite("Constructors")(
      test("empty yields no elements") {
        assert(Stream.empty.runCollect)(equalTo(Right(Chunk.empty)))
      },
      test("succeed(a) yields exactly one element") {
        check(genInt) { a =>
          assert(Stream.succeed(a).runCollect)(equalTo(Right(Chunk(a))))
        }
      },
      test("Stream(1, 2, 3) syntax") {
        assert(Stream(1, 2, 3).runCollect)(equalTo(Right(Chunk(1, 2, 3))))
      },
      test("Stream() is empty") {
        assert(Stream[Int]().runCollect)(equalTo(Right(Chunk.empty[Int])))
      },
      test("Stream(\"a\", \"b\") works for String") {
        assert(Stream("a", "b").runCollect)(equalTo(Right(Chunk("a", "b"))))
      },
      test("range(s, e) yields [s, e)") {
        check(Gen.int(-20, 20), Gen.int(-20, 20)) { (s, e) =>
          assert(Stream.range(s, e).runCollect)(equalTo(Right(Chunk.fromIterable(s until e))))
        }
      },
      test("fromRange(r) ≡ fromIterable(r)") {
        check(Gen.int(-20, 20), Gen.int(-20, 20)) { (s, e) =>
          val r = s until e
          assert(Stream.fromRange(r).runCollect)(equalTo(Stream.fromIterable(r).runCollect))
        }
      },
      test("fromChunk round-trips") {
        check(genChunk(genInt)) { c =>
          assert(Stream.fromChunk(c).runCollect)(equalTo(Right(c)))
        }
      },
      test("fromIterable round-trips") {
        check(genChunk(genInt)) { c =>
          assert(Stream.fromIterable(c.toList).runCollect)(equalTo(Right(c)))
        }
      },
      test("fromReader wraps a reader as a stream") {
        val reader = Reader.fromChunk(Chunk(1, 2, 3))
        assert(Stream.fromReader(reader).runCollect)(equalTo(Right(Chunk(1, 2, 3))))
      },
      test("fromReader propagates upstream error") {
        val reader = new Reader[Int] {
          private var called                    = false
          def isClosed                          = called
          def read[A1 >: Int](sentinel: A1): A1 =
            if (!called) { called = true; throw new StreamError("oops") }
            else sentinel
          def close(): Unit = ()
        }
        assert(Stream.fromReader[String, Int](reader).run(Sink.drain))(equalTo(Left("oops")))
      },
      test("repeat emits value forever (take first 5)") {
        assert(Stream.repeat(7).take(5).runCollect)(equalTo(Right(Chunk(7, 7, 7, 7, 7))))
      },
      test("unfold generates correct sequence") {
        val nats = Stream.unfold(0)(n => if (n < 5) Some((n, n + 1)) else None)
        assert(nats.runCollect)(equalTo(Right(Chunk.fromIterable(0 until 5))))
      },
      test("unfold terminates when f returns None") {
        val s = Stream.unfold(0)(_ => None)
        assert(s.runCollect)(equalTo(Right(Chunk.empty)))
      },
      test("flattenAll flattens a stream of streams") {
        check(Gen.listOfBounded(0, 5)(genIntStream)) { streams =>
          val data     = streams.map(s => collect(s))
          val expected = data.foldLeft(Chunk.empty[Int])(_ ++ _)
          val nested   = Stream.fromIterable(streams.map(s => Stream.fromChunk(collect(s))))
          assert(Stream.flattenAll(nested).runCollect)(equalTo(Right(expected)))
        }
      }
    ),
    // ---- collection-like convenience -----------------------------------------
    suite("collection-like")(
      test("foreach is alias for runForeach") {
        check(genIntStream) { s =>
          val data = collect(s)
          val b1   = scala.collection.mutable.ListBuffer.empty[Int]
          val b2   = scala.collection.mutable.ListBuffer.empty[Int]
          val r1   = Stream.fromChunk(data).foreach(b1 += _)
          val r2   = Stream.fromChunk(data).runForeach(b2 += _)
          assert(r1)(equalTo(r2)) && assert(b1.toList)(equalTo(b2.toList))
        }
      },
      test("head returns first element") {
        assert(Stream.range(1, 5).head)(equalTo(Right(Some(1)))) &&
        assert(Stream.empty.head)(equalTo(Right(None)))
      },
      test("last returns last element") {
        assert(Stream.range(1, 5).last)(equalTo(Right(Some(4)))) &&
        assert(Stream.empty.last)(equalTo(Right(None)))
      },
      test("count") {
        assert(Stream.range(0, 10).count)(equalTo(Right(10L))) &&
        assert(Stream.empty.count)(equalTo(Right(0L)))
      },
      test("exists") {
        assert(Stream.range(0, 10).exists(_ > 5))(equalTo(Right(true))) &&
        assert(Stream.range(0, 10).exists(_ > 20))(equalTo(Right(false))) &&
        assert(Stream.empty.exists((_: Int) => true))(equalTo(Right(false)))
      },
      test("forall") {
        assert(Stream.range(0, 10).forall(_ < 20))(equalTo(Right(true))) &&
        assert(Stream.range(0, 10).forall(_ < 5))(equalTo(Right(false))) &&
        assert(Stream.empty.forall((_: Int) => false))(equalTo(Right(true)))
      },
      test("find") {
        assert(Stream.range(0, 10).find(_ == 5))(equalTo(Right(Some(5)))) &&
        assert(Stream.range(0, 10).find(_ == 20))(equalTo(Right(None)))
      },
      test("collect applies partial function") {
        val pf: PartialFunction[Int, String] = { case x if x % 2 == 0 => s"even($x)" }
        assert(Stream.range(0, 5).collect(pf).runCollect)(
          equalTo(Right(Chunk("even(0)", "even(2)", "even(4)")))
        )
      },
      test("tapEach applies side-effect and passes through") {
        val buf    = scala.collection.mutable.ListBuffer.empty[Int]
        val result = Stream.range(0, 5).tapEach(buf += _).runCollect
        assert(result)(equalTo(Right(Chunk(0, 1, 2, 3, 4)))) &&
        assert(buf.toList)(equalTo(List(0, 1, 2, 3, 4)))
      }
    ),
    // ---- start (Scope-based) -------------------------------------------------
    suite("start")(
      test("start returns a scoped reader that produces all elements") {
        import zio.blocks.scope._
        val result = Scope.global.scoped { scope =>
          implicit val implicitScope = scope
          val reader                 = Stream.range(0, 5).start
          val buf                    = scala.collection.mutable.ListBuffer.empty[Int]
          val r                      = implicitScope.leak(reader)
          var v                      = r.read[Any](null)
          while (v != null) { buf += v.asInstanceOf[Int]; v = r.read[Any](null) }
          buf.toList
        }
        assert(result)(equalTo(List(0, 1, 2, 3, 4)))
      },
      test("start reader is closed when scope closes") {
        import zio.blocks.scope._
        var readerClosed = false
        Scope.global.scoped { scope =>
          implicit val implicitScope = scope
          val s                      = Stream.range(0, 5).ensuring { readerClosed = true }
          val _reader                = s.start
        }
        assertTrue(readerClosed)
      },
      test("start with mapped stream") {
        import zio.blocks.scope._
        val result = Scope.global.scoped { scope =>
          implicit val implicitScope = scope
          val reader                 = Stream.range(0, 3).map(_ * 10).start
          val buf                    = scala.collection.mutable.ListBuffer.empty[Int]
          val r                      = implicitScope.leak(reader)
          var v                      = r.read[Any](null)
          while (v != null) { buf += v.asInstanceOf[Int]; v = r.read[Any](null) }
          buf.toList
        }
        assert(result)(equalTo(List(0, 10, 20)))
      }
    ),

    // ---- Fusion -------------------------------------------------------------

    suite("Fusion")(
      suite("regressions")(
        test("filter/map/take/drop fusion order matches List across a matrix [AdversarialFusionLawConvergenceSpec]") {
          // Accumulate mismatches into a plain list and assert once: a deep
          // `&&`-ed BoolAlgebra over the whole matrix stack-overflows the JS runtime.
          val in                                                                     = (0 until 12).toList
          def S                                                                      = Stream.fromIterable(in)
          val bad                                                                    = scala.collection.mutable.ListBuffer.empty[String]
          def chk(label: String, got: Either[Any, Chunk[Int]], exp: List[Int]): Unit =
            if (got != Right(Chunk.fromIterable(exp))) bad += label
          for (a <- 0 to 6; b <- 0 to 6) {
            chk(
              s"filter.take($a).drop($b)",
              S.filter(_ % 2 == 0).take(a.toLong).drop(b.toLong).runCollect,
              in.filter(_ % 2 == 0).take(a).drop(b)
            )
            chk(
              s"take($a).filter.drop($b)",
              S.take(a.toLong).filter(_ % 2 == 0).drop(b.toLong).runCollect,
              in.take(a).filter(_ % 2 == 0).drop(b)
            )
            chk(
              s"drop($a).take($b).filter",
              S.drop(a.toLong).take(b.toLong).filter(_ % 3 == 0).runCollect,
              in.drop(a).take(b).filter(_ % 3 == 0)
            )
            chk(
              s"map.take($a).drop($b)",
              S.map(_ + 100).take(a.toLong).drop(b.toLong).runCollect,
              in.map(_ + 100).take(a).drop(b)
            )
            chk(
              s"take($a).map.take($b)",
              S.take(a.toLong).map(_ * 2).take(b.toLong).runCollect,
              in.take(a).map(_ * 2).take(b)
            )
            chk(s"drop($a).drop($b)", S.drop(a.toLong).drop(b.toLong).runCollect, in.drop(a).drop(b))
            chk(s"take($a).take($b)", S.take(a.toLong).take(b.toLong).runCollect, in.take(a).take(b))
          }
          assertTrue(bad.toList == Nil)
        },
        test("collect fused with take/drop matches List.collect [AdversarialFusionLawConvergenceSpec]") {
          val in                            = (0 until 12).toList
          val pf: PartialFunction[Int, Int] = { case x if x % 2 == 0 => x * 10 }
          val bad                           = scala.collection.mutable.ListBuffer.empty[String]
          for (a <- 0 to 6; b <- 0 to 6)
            if (
              Stream.fromIterable(in).collect(pf).take(a.toLong).drop(b.toLong).runCollect !=
                Right(Chunk.fromIterable(in.collect(pf).take(a).drop(b)))
            ) bad += s"collect.take($a).drop($b)"
          assertTrue(bad.toList == Nil)
        },
        test(
          "algebraic laws: map fusion, filter fusion, concat assoc, take(min), distinct idempotent [AdversarialFusionLawConvergenceSpec]"
        ) {
          val in      = (0 until 30).toList
          val f       = (x: Int) => x + 1
          val g       = (x: Int) => x * 3
          val takeBad = scala.collection.mutable.ListBuffer.empty[String]
          for (n <- 0 to 6; m <- 0 to 6)
            if (
              Stream.fromIterable(in).take(n.toLong).take(m.toLong).runCollect !=
                Stream.fromIterable(in).take(math.min(n, m).toLong).runCollect
            ) takeBad += s"take($n).take($m)"
          assertTrue(
            Stream.fromIterable(in).map(f).map(g).runCollect == Stream.fromIterable(in).map(f andThen g).runCollect
          ) &&
          assertTrue(
            Stream.fromIterable(in).filter(_ % 2 == 0).filter(_ % 3 == 0).runCollect ==
              Stream.fromIterable(in).filter(x => x % 2 == 0 && x % 3 == 0).runCollect
          ) &&
          assertTrue(
            Stream(1, 2).concat(Stream(3)).concat(Stream(4, 5)).runCollect ==
              Stream(1, 2).concat(Stream(3).concat(Stream(4, 5))).runCollect
          ) &&
          assertTrue(
            Stream.fromIterable(List(1, 2, 2, 3, 1, 4)).distinct.runCollect ==
              Stream.fromIterable(List(1, 2, 2, 3, 1, 4)).distinct.distinct.runCollect
          ) &&
          assertTrue(takeBad.toList == Nil)
        },
        test("take(0) does not pull the source [AdversarialFusionLawConvergenceSpec]") {
          val reads = new java.util.concurrent.atomic.AtomicInteger(0)
          val s     = Stream.fromIterator(List(1, 2, 3).iterator.map { x => reads.incrementAndGet(); x })
          assertTrue(s.take(0).runCollect == Right(Chunk.empty[Int])) && assertTrue(reads.get() == 0)
        }
      )
    ),

    // ---- Stateful combinators -----------------------------------------------

    suite("Stateful combinators")(
      suite("regressions")(
        test("scan_onEmpty_emitsInit [AdversarialValueLawsSpec]") {
          // differential vs List.scanLeft: List[Int]().scanLeft(5)(_+_) == List(5)
          val s = (Stream.empty: Stream[Nothing, Int]).scan(5)(_ + _)
          assertTrue(s.runCollect == Right(Chunk(5)))
        },
        test("mapAccum_onEmpty_emitsEmpty [AdversarialValueLawsSpec]") {
          val s = (Stream.empty: Stream[Nothing, Int]).mapAccum(0)((acc, x) => (acc + x, x))
          assertTrue(s.runCollect == Right(Chunk.empty[Int]))
        },
        test("intersperse_onEmpty_isEmpty [AdversarialValueLawsSpec]") {
          val s = (Stream.empty: Stream[Nothing, Int]).intersperse(0)
          assertTrue(s.runCollect == Right(Chunk.empty[Int]))
        },
        test("intersperse_onSingleton_noSeparator [AdversarialValueLawsSpec]") {
          val s = Stream(1).intersperse(0)
          assertTrue(s.runCollect == Right(Chunk(1)))
        },
        test(
          "scan/mapAccum/intersperse/distinct/chunked restart cleanly under repeated.take [AdversarialFusionLawConvergenceSpec]"
        ) {
          assertTrue(Stream(1, 2).scan(0)(_ + _).repeated.take(6).runCollect == Right(Chunk(0, 1, 3, 0, 1, 3))) &&
          assertTrue(
            Stream(1, 2)
              .mapAccum(0) { (s, a) =>
                val s2 = s + a; (s2, s2)
              }
              .repeated
              .take(4)
              .runCollect ==
              Right(Chunk(1, 3, 1, 3))
          ) &&
          assertTrue(Stream(1, 2).intersperse(0).repeated.take(6).runCollect == Right(Chunk(1, 0, 2, 1, 0, 2))) &&
          assertTrue(Stream(1, 1, 2).distinct.repeated.take(4).runCollect == Right(Chunk(1, 2, 1, 2))) &&
          assertTrue(
            Stream(1, 2, 3).chunked(2).repeated.take(4).runCollect ==
              Right(Chunk(Chunk(1, 2), Chunk(3), Chunk(1, 2), Chunk(3)))
          )
        },
        test("scan on empty stream emits init only [AdversarialFusionLawConvergenceSpec]") {
          val s: Stream[Nothing, Int] = Stream.empty
          assertTrue(s.scan(99)(_ + _).runCollect == Right(Chunk(99)))
        }
      )
    ),

    // ---- Zip ----------------------------------------------------------------

    suite("Zip")(
      suite("regressions")(
        test("zip_emptyRight_closesManagedLeftOnce [AdversarialValueLawsSpec]") {
          val opens  = new AtomicInteger(0)
          val closes = new AtomicInteger(0)
          val z      = managed(opens, closes, 1, 2, 3) && (Stream.empty: Stream[Nothing, Int])
          val r      = z.runCollect
          assertTrue(r == Right(Chunk.empty[(Int, Int)])) &&
          assertTrue(opens.get() == 1) &&
          assertTrue(closes.get() == 1)
        },
        test("zip_emptyLeft_closesManagedRightOnce [AdversarialValueLawsSpec]") {
          val opens  = new AtomicInteger(0)
          val closes = new AtomicInteger(0)
          val z      = (Stream.empty: Stream[Nothing, Int]) && managed(opens, closes, 1, 2, 3)
          val r      = z.runCollect
          assertTrue(r == Right(Chunk.empty[(Int, Int)])) &&
          assertTrue(opens.get() == 1) &&
          assertTrue(closes.get() == 1)
        }
      )
    ),

    // ---- Resource management ------------------------------------------------

    suite("Resource management")(
      suite("regressions")(
        test("managed_repeated_take_acquiresAndReleasesOnce [AdversarialValueLawsSpec]") {
          // acquireRelease acquires once at compile; repeated replays via reset
          // without re-acquiring, so it must release exactly once at the end.
          val opens  = new AtomicInteger(0)
          val closes = new AtomicInteger(0)
          val s      = managed(opens, closes, 1, 2).repeated.take(5)
          val r      = s.runCollect
          assertTrue(r == Right(Chunk(1, 2, 1, 2, 1))) &&
          assertTrue(opens.get() == 1) &&
          assertTrue(closes.get() == 1)
        }
      )
    )
  )
}
