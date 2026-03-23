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

/**
 * Laws and unit tests for [[Sink]].
 *
 * Laws tested:
 *   - Functor: `map(id) ≡ id`, `map(f).map(g) ≡ map(f andThen g)`
 *   - Contravariant: `contramap(id) ≡ id`,
 *     `contramap(f).contramap(g) ≡ contramap(g andThen f)`
 *   - mapError identity
 *
 * Constructors tested: `drain`, `count`, `collectAll`, `foldLeft`, `foreach`,
 * `head`, `last`, `take`, `sumInt`, `sumLong`, `create`.
 */
object SinkLawsSpec extends StreamsBaseSpec {

  private def run[A, Z](s: Stream[Nothing, A], sink: Sink[Nothing, A, Z]): Z =
    s.run(sink).fold(e => throw new RuntimeException(s"unexpected error: $e"), identity)

  def spec: Spec[TestEnvironment, Any] = suite("Sink laws")(
    // ---- Functor ------------------------------------------------------------

    suite("Functor")(
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
      }
    ),

    // ---- Contravariant functor ----------------------------------------------

    suite("Contravariant")(
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
      }
    ),

    // ---- mapError -----------------------------------------------------------

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
      }
    ),

    // ---- Constructors -------------------------------------------------------

    suite("Constructors")(
      test("drain discards all elements") {
        check(genIntStream) { s =>
          assert(s.run(Sink.drain))(equalTo(Right(())))
        }
      },
      test("count counts all elements") {
        check(genIntStream) { s =>
          val data = s.runCollect.getOrElse(Chunk.empty)
          assert(Stream.fromChunk(data).run(Sink.count))(equalTo(Right(data.length.toLong)))
        }
      },
      test("collectAll collects all elements") {
        check(genIntStream) { s =>
          val data = s.runCollect.getOrElse(Chunk.empty)
          assert(Stream.fromChunk(data).run(Sink.collectAll[Int]))(equalTo(Right(data)))
        }
      },
      test("foldLeft agrees with Chunk.foldLeft") {
        check(genIntStream, genInt, Gen.function2(genInt)) { (s, z, f) =>
          val data = s.runCollect.getOrElse(Chunk.empty)
          assert(Stream.fromChunk(data).run(Sink.foldLeft(z)(f)))(
            equalTo(Right(data.foldLeft(z)(f)))
          )
        }
      },
      test("foreach calls f for each element") {
        check(genIntStream) { s =>
          val buf  = new scala.collection.mutable.ArrayBuffer[Int]()
          val data = s.runCollect.getOrElse(Chunk.empty)
          Stream.fromChunk(data).run(Sink.foreach[Int](a => buf += a))
          assert(buf.toList)(equalTo(data.toList))
        }
      },
      test("head returns first element") {
        check(genIntStream) { s =>
          val data = s.runCollect.getOrElse(Chunk.empty)
          assert(Stream.fromChunk(data).run(Sink.head[Int]))(
            equalTo(Right(data.headOption))
          )
        }
      },
      test("last returns last element") {
        check(genIntStream) { s =>
          val data = s.runCollect.getOrElse(Chunk.empty)
          assert(Stream.fromChunk(data).run(Sink.last[Int]))(
            equalTo(Right(data.lastOption))
          )
        }
      },
      test("take(n) collects first n elements") {
        check(genIntStream, Gen.int(0, 20)) { (s, n) =>
          val data = s.runCollect.getOrElse(Chunk.empty)
          assert(Stream.fromChunk(data).run(Sink.take[Int](n)))(
            equalTo(Right(data.take(n)))
          )
        }
      },
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
      },
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
      },
      test("drain propagates upstream error") {
        assert(Stream.fail("err").run(Sink.drain))(equalTo(Left("err")))
      },
      test("collectAll propagates upstream error") {
        assert(Stream.fail("err").run(Sink.collectAll[Int]))(equalTo(Left("err")))
      }
    )
  )
}
