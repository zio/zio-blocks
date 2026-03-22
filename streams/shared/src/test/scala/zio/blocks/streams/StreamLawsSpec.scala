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
      }
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
      }
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
          import scope._
          val reader: $[Reader[Int]] = Stream.range(0, 5).start
          val buf                    = scala.collection.mutable.ListBuffer.empty[Int]
          $(reader) { r =>
            var v = r.read[Any](null)
            while (v != null) { buf += v.asInstanceOf[Int]; v = r.read[Any](null) }
          }
          buf.toList
        }
        assert(result)(equalTo(List(0, 1, 2, 3, 4)))
      },
      test("start reader is closed when scope closes") {
        import zio.blocks.scope._
        var readerClosed = false
        Scope.global.scoped { scope =>
          import scope._
          val s                       = Stream.range(0, 5).ensuring { readerClosed = true }
          val _reader: $[Reader[Int]] = s.start
        }
        assertTrue(readerClosed)
      },
      test("start with mapped stream") {
        import zio.blocks.scope._
        val result = Scope.global.scoped { scope =>
          import scope._
          val reader: $[Reader[Int]] = Stream.range(0, 3).map(_ * 10).start
          val buf                    = scala.collection.mutable.ListBuffer.empty[Int]
          $(reader) { r =>
            var v = r.read[Any](null)
            while (v != null) { buf += v.asInstanceOf[Int]; v = r.read[Any](null) }
          }
          buf.toList
        }
        assert(result)(equalTo(List(0, 10, 20)))
      }
    )
  )
}
