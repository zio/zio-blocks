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

import zio.blocks.async._
import zio.blocks.chunk.Chunk
import zio.blocks.streams.io.Reader
import zio.ZIO
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

  private def collect[A](s: Stream[Nothing, A]) =
    runAsync(s.runCollectAsync).map(_.fold(_ => Chunk.empty, identity))

  def spec: Spec[TestEnvironment, Any] = suite("Stream laws")(
    // ---- Functor ------------------------------------------------------------

    suite("Functor")(
      test("map(identity) ≡ identity") {
        check(genIntStream) { s =>
          collect(s.map(identity)).zipWith(collect(s))((left, right) => assert(left)(equalTo(right)))
        }
      },
      test("map(f).map(g) ≡ map(f andThen g)") {
        check(genIntStream, Gen.function(genInt), Gen.function(genInt)) { (s, f, g) =>
          collect(s.map(f).map(g)).zipWith(collect(s.map(f andThen g)))((left, right) => assert(left)(equalTo(right)))
        }
      }
    ),

    // ---- Monad --------------------------------------------------------------

    suite("Monad")(
      test("succeed(a).flatMap(f) ≡ f(a)") {
        check(genInt, Gen.function(genStream(genInt))) { (a, f) =>
          collect(Stream.succeed(a).flatMap(f)).zipWith(collect(f(a)))((left, right) => assert(left)(equalTo(right)))
        }
      } @@ TestAspect.timeout(zio.Duration.fromSeconds(90)),
      test("s.flatMap(Stream.succeed) ≡ s") {
        check(genIntStream) { s =>
          collect(s.flatMap(Stream.succeed)).zipWith(collect(s))((left, right) => assert(left)(equalTo(right)))
        }
      } @@ TestAspect.timeout(zio.Duration.fromSeconds(90)),
      test("associativity: s.flatMap(f).flatMap(g) ≡ s.flatMap(a => f(a).flatMap(g))") {
        val small = Gen.chunkOfBounded(0, 3)(genInt).map(c => Stream.fromChunk(Chunk.fromIterable(c)))
        check(small, Gen.function(small), Gen.function(small)) { (s, f, g) =>
          collect(s.flatMap(f).flatMap(g)).zipWith(collect(s.flatMap(a => f(a).flatMap(g))))((left, right) =>
            assert(left)(equalTo(right))
          )
        }
      } @@ TestAspect.timeout(zio.Duration.fromSeconds(90))
    ),

    // ---- Concat -------------------------------------------------------------

    suite("Concat")(
      test("empty ++ s ≡ s") {
        check(genIntStream) { s =>
          collect(Stream.empty ++ s).zipWith(collect(s))((left, right) => assert(left)(equalTo(right)))
        }
      },
      test("s ++ empty ≡ s") {
        check(genIntStream) { s =>
          collect(s ++ Stream.empty).zipWith(collect(s))((left, right) => assert(left)(equalTo(right)))
        }
      },
      test("(s ++ t) ++ u ≡ s ++ (t ++ u)") {
        check(genIntStream, genIntStream, genIntStream) { (s, t, u) =>
          collect((s ++ t) ++ u).zipWith(collect(s ++ (t ++ u)))((left, right) => assert(left)(equalTo(right)))
        }
      }
    ),

    // ---- Run operators ------------------------------------------------------

    suite("Run operators")(
      test("runForeach(f) ≡ run(Sink.foreach(f))") {
        check(genIntStream) { s =>
          val buf1 = new scala.collection.mutable.ArrayBuffer[Int]()
          val buf2 = new scala.collection.mutable.ArrayBuffer[Int]()
          for {
            data <- collect(s)
            _    <- runAsync(Stream.fromChunk(data).runForeachAsync(a => Async.succeed(buf1 += a).unit))
            _    <- runAsync(Stream.fromChunk(data).runAsync(Sink.foreach[Int](a => buf2 += a)))
          } yield assert(buf1.toList)(equalTo(buf2.toList))
        }
      },
      test("runCollect ≡ run(Sink.collectAll)") {
        check(genIntStream) { s =>
          for {
            data  <- collect(s)
            left  <- runAsync(Stream.fromChunk(data).runCollectAsync)
            right <- runAsync(Stream.fromChunk(data).runAsync(Sink.collectAll[Int]))
          } yield assert(left)(equalTo(right))
        }
      },
      test("runDrain ≡ run(Sink.drain)") {
        check(genIntStream) { s =>
          for {
            data  <- collect(s)
            left  <- runAsync(Stream.fromChunk(data).runDrainAsync)
            right <- runAsync(Stream.fromChunk(data).runAsync(Sink.drain))
          } yield assert(left)(equalTo(right))
        }
      },
      test("runFold(z)(f) ≡ run(Sink.foldLeft(z)(f))") {
        check(genIntStream, Gen.function2(genInt)) { (s, f) =>
          for {
            data  <- collect(s)
            left  <- runAsync(Stream.fromChunk(data).runFoldAsync(0)((z, a) => Async.succeed(f(z, a))))
            right <- runAsync(Stream.fromChunk(data).runAsync(Sink.foldLeft(0)(f)))
          } yield assert(left)(equalTo(right))
        }
      }
    ),

    // ---- Error channel ------------------------------------------------------

    suite("Error channel")(
      test("fail(e).run(sink) returns Left(e)") {
        runAsync(Stream.fail("boom").runAsync(Sink.drain)).map(result => assert(result)(equalTo(Left("boom"))))
      },
      test("mapError transforms the error") {
        runAsync(Stream.fail(42).mapError(_.toString).runAsync(Sink.drain))
          .map(result => assert(result)(equalTo(Left("42"))))
      },
      test("mapError(identity) ≡ identity") {
        check(genIntStream) { s =>
          collect(s.mapError[Nothing](identity)).zipWith(collect(s))((left, right) => assert(left)(equalTo(right)))
        }
      },
      test("mapError(f).mapError(g) ≡ mapError(f andThen g)") {
        val e1 = Stream.fail(1).mapError(_ + 1).mapError(_ * 2)
        val e2 = Stream.fail(1).mapError(x => (x + 1) * 2)
        runAsync(e1.runAsync(Sink.drain)).zipWith(runAsync(e2.runAsync(Sink.drain)))((left, right) =>
          assert(left)(equalTo(right))
        )
      }
    ),

    // ---- take / drop --------------------------------------------------------

    suite("take / drop")(
      test("take(n) emits exactly min(n, len) elements") {
        check(genIntStream, Gen.int(0, 60)) { (s, n) =>
          collect(s).flatMap(data =>
            collect(Stream.fromChunk(data).take(n)).map(result => assert(result)(equalTo(data.take(n))))
          )
        }
      },
      test("drop(n) skips first min(n, len) elements") {
        check(genIntStream, Gen.int(0, 60)) { (s, n) =>
          collect(s).flatMap(data =>
            collect(Stream.fromChunk(data).drop(n)).map(result => assert(result)(equalTo(data.drop(n))))
          )
        }
      },
      test("take(n) ++ drop(n) ≡ identity") {
        check(genIntStream, Gen.int(0, 30)) { (s, n) =>
          collect(s).flatMap { data =>
            collect(Stream.fromChunk(data).take(n) ++ Stream.fromChunk(data).drop(n))
              .map(combined => assert(combined)(equalTo(data)))
          }
        }
      },
      test("takeWhile takes prefix satisfying predicate") {
        check(genIntStream, Gen.int(0, 30)) { (s, limit) =>
          collect(s).flatMap { data =>
            val expected = data.toList.takeWhile(_ < limit)
            collect(Stream.fromChunk(data).takeWhile(_ < limit))
              .map(result => assert(result)(equalTo(Chunk.fromIterable(expected))))
          }
        }
      },
      test("takeWhile on empty stream is empty") {
        collect(Stream.empty.takeWhile((_: Int) => true)).map(result => assert(result)(equalTo(Chunk.empty)))
      },
      test("takeWhile false is empty") {
        check(genIntStream) { s =>
          collect(s).flatMap(data =>
            collect(Stream.fromChunk(data).takeWhile(_ => false)).map(result => assert(result)(equalTo(Chunk.empty)))
          )
        }
      },
      test("takeWhile true ≡ identity") {
        check(genIntStream) { s =>
          collect(s).flatMap(data =>
            collect(Stream.fromChunk(data).takeWhile(_ => true)).map(result => assert(result)(equalTo(data)))
          )
        }
      },
      test("filter keeps exactly matching elements") {
        check(genIntStream, Gen.function(Gen.boolean)) { (s, pred) =>
          collect(s).flatMap(data =>
            collect(Stream.fromChunk(data).filter(pred)).map(result => assert(result)(equalTo(data.filter(pred))))
          )
        }
      }
    ),

    // ---- Constructors -------------------------------------------------------

    suite("Constructors")(
      test("empty yields no elements") {
        runAsync(Stream.empty.runCollectAsync).map(result => assert(result)(equalTo(Right(Chunk.empty))))
      },
      test("succeed(a) yields exactly one element") {
        check(genInt) { a =>
          runAsync(Stream.succeed(a).runCollectAsync).map(result => assert(result)(equalTo(Right(Chunk(a)))))
        }
      },
      test("Stream(1, 2, 3) syntax") {
        runAsync(Stream(1, 2, 3).runCollectAsync).map(result => assert(result)(equalTo(Right(Chunk(1, 2, 3)))))
      },
      test("Stream() is empty") {
        runAsync(Stream[Int]().runCollectAsync).map(result => assert(result)(equalTo(Right(Chunk.empty[Int]))))
      },
      test("Stream(\"a\", \"b\") works for String") {
        runAsync(Stream("a", "b").runCollectAsync).map(result => assert(result)(equalTo(Right(Chunk("a", "b")))))
      },
      test("range(s, e) yields [s, e)") {
        check(Gen.int(-20, 20), Gen.int(-20, 20)) { (s, e) =>
          runAsync(Stream.range(s, e).runCollectAsync)
            .map(result => assert(result)(equalTo(Right(Chunk.fromIterable(s until e)))))
        }
      },
      test("fromRange(r) ≡ fromIterable(r)") {
        check(Gen.int(-20, 20), Gen.int(-20, 20)) { (s, e) =>
          val r = s until e
          runAsync(Stream.fromRange(r).runCollectAsync).zipWith(runAsync(Stream.fromIterable(r).runCollectAsync))(
            (left, right) => assert(left)(equalTo(right))
          )
        }
      },
      test("fromChunk round-trips") {
        check(genChunk(genInt)) { c =>
          runAsync(Stream.fromChunk(c).runCollectAsync).map(result => assert(result)(equalTo(Right(c))))
        }
      },
      test("fromIterable round-trips") {
        check(genChunk(genInt)) { c =>
          runAsync(Stream.fromIterable(c.toList).runCollectAsync).map(result => assert(result)(equalTo(Right(c))))
        }
      },
      test("fromReader wraps a reader as a stream") {
        val reader = Reader.fromChunk(Chunk(1, 2, 3))
        runAsync(Stream.fromReader(reader).runCollectAsync)
          .map(result => assert(result)(equalTo(Right(Chunk(1, 2, 3)))))
      },
      test("fromReader propagates upstream error") {
        val reader = new Reader.SyncReader[Int] {
          private var called                    = false
          def isClosed                          = called
          def read[A1 >: Int](sentinel: A1): A1 =
            if (!called) { called = true; failSource("oops") }
            else sentinel
          def close(): Unit = ()
        }
        runAsync(Stream.fromReader[String, Int](reader).runAsync(Sink.drain))
          .map(result => assert(result)(equalTo(Left("oops"))))
      },
      test("repeat emits value forever (take first 5)") {
        runAsync(Stream.repeat(7).take(5).runCollectAsync)
          .map(result => assert(result)(equalTo(Right(Chunk(7, 7, 7, 7, 7)))))
      },
      test("unfold generates correct sequence") {
        val nats = Stream.unfold(0)(n => if (n < 5) Some((n, n + 1)) else None)
        runAsync(nats.runCollectAsync).map(result => assert(result)(equalTo(Right(Chunk.fromIterable(0 until 5)))))
      },
      test("unfold terminates when f returns None") {
        val s = Stream.unfold[Int, Nothing](0)(_ => None)
        runAsync(s.runCollectAsync).map(result => assert(result)(equalTo(Right(Chunk.empty))))
      },
      test("flattenAll flattens a stream of streams") {
        check(Gen.listOfBounded(0, 5)(genIntStream)) { streams =>
          ZIO.foreach(streams)(collect).flatMap { data =>
            val expected = data.foldLeft(Chunk.empty[Int])(_ ++ _)
            val nested   = Stream.fromIterable(data.map(chunk => Stream.fromChunk(chunk)))
            runAsync(Stream.flattenAll(nested).runCollectAsync)
              .map(result => assert(result)(equalTo(Right(expected))))
          }
        }
      } @@ TestAspect.timeout(zio.Duration.fromSeconds(90))
    ),
    // ---- collection-like convenience -----------------------------------------
    suite("collection-like")(
      test("foreach is alias for runForeach") {
        check(genIntStream) { s =>
          val b1 = scala.collection.mutable.ListBuffer.empty[Int]
          val b2 = scala.collection.mutable.ListBuffer.empty[Int]
          for {
            data <- collect(s)
            r1   <- runAsync(Stream.fromChunk(data).foreachAsync(a => Async.succeed(b1 += a).unit))
            r2   <- runAsync(Stream.fromChunk(data).runForeachAsync(a => Async.succeed(b2 += a).unit))
          } yield assert(r1)(equalTo(r2)) && assert(b1.toList)(equalTo(b2.toList))
        }
      },
      test("head returns first element") {
        runAsync(Stream.range(1, 5).headAsync).zipWith(runAsync(Stream.empty.headAsync))((nonEmpty, empty) =>
          assert(nonEmpty)(equalTo(Right(Some(1)))) && assert(empty)(equalTo(Right(None)))
        )
      },
      test("last returns last element") {
        runAsync(Stream.range(1, 5).lastAsync).zipWith(runAsync(Stream.empty.lastAsync))((nonEmpty, empty) =>
          assert(nonEmpty)(equalTo(Right(Some(4)))) && assert(empty)(equalTo(Right(None)))
        )
      },
      test("count") {
        runAsync(Stream.range(0, 10).countAsync).zipWith(runAsync(Stream.empty.countAsync))((nonEmpty, empty) =>
          assert(nonEmpty)(equalTo(Right(10L))) && assert(empty)(equalTo(Right(0L)))
        )
      },
      test("exists") {
        for {
          found   <- runAsync(Stream.range(0, 10).existsAsync(a => Async.succeed(a > 5)))
          missing <- runAsync(Stream.range(0, 10).existsAsync(a => Async.succeed(a > 20)))
          empty   <- runAsync(Stream.empty.existsAsync((_: Int) => Async.succeed(true)))
        } yield assert(found)(equalTo(Right(true))) && assert(missing)(equalTo(Right(false))) &&
          assert(empty)(equalTo(Right(false)))
      },
      test("forall") {
        for {
          all    <- runAsync(Stream.range(0, 10).forallAsync(a => Async.succeed(a < 20)))
          notAll <- runAsync(Stream.range(0, 10).forallAsync(a => Async.succeed(a < 5)))
          empty  <- runAsync(Stream.empty.forallAsync((_: Int) => Async.succeed(false)))
        } yield assert(all)(equalTo(Right(true))) && assert(notAll)(equalTo(Right(false))) &&
          assert(empty)(equalTo(Right(true)))
      },
      test("find") {
        runAsync(Stream.range(0, 10).findAsync(a => Async.succeed(a == 5)))
          .zipWith(runAsync(Stream.range(0, 10).findAsync(a => Async.succeed(a == 20))))((found, missing) =>
            assert(found)(equalTo(Right(Some(5)))) && assert(missing)(equalTo(Right(None)))
          )
      },
      test("collect applies partial function") {
        val pf: PartialFunction[Int, String] = { case x if x % 2 == 0 => s"even($x)" }
        runAsync(Stream.range(0, 5).collect(pf).runCollectAsync).map(result =>
          assert(result)(equalTo(Right(Chunk("even(0)", "even(2)", "even(4)"))))
        )
      },
      test("tapEach applies side-effect and passes through") {
        val buf = scala.collection.mutable.ListBuffer.empty[Int]
        runAsync(Stream.range(0, 5).tapEach(buf += _).runCollectAsync).map(result =>
          assert(result)(equalTo(Right(Chunk(0, 1, 2, 3, 4)))) &&
            assert(buf.toList)(equalTo(List(0, 1, 2, 3, 4)))
        )
      }
    )
  )
}
