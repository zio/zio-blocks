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
import zio.test._
import zio.test.Assertion._
import StreamsGen._

object PipelineLawsSpec extends StreamsBaseSpec {

  private def collect[A](s: Stream[Nothing, A]) =
    runAsync(s.runCollectAsync).map(_.fold[Chunk[A]](_ => Chunk.empty[A], x => x))

  def spec: Spec[TestEnvironment, Any] = suite("Pipeline laws")(
    suite("Category")(
      test("left identity: Pipeline.identity andThen p == p") {
        check(genIntStream, genIntPipeline) { (s, p) =>
          for {
            left  <- collect((Pipeline.identity[Int] andThen p).applyToStream(s))
            right <- collect(p.applyToStream(s))
          } yield assert(left)(equalTo(right))
        }
      },
      test("right identity: p andThen Pipeline.identity == p") {
        check(genIntStream, genIntPipeline) { (s, p) =>
          for {
            left  <- collect((p andThen Pipeline.identity[Int]).applyToStream(s))
            right <- collect(p.applyToStream(s))
          } yield assert(left)(equalTo(right))
        }
      },
      test("associativity: (p andThen q) andThen r == p andThen (q andThen r)") {
        check(genIntStream, genIntPipeline, genIntPipeline, genIntPipeline) { (s, p, q, r) =>
          for {
            left  <- collect(((p andThen q) andThen r).applyToStream(s))
            right <- collect((p andThen (q andThen r)).applyToStream(s))
          } yield assert(left)(equalTo(right))
        }
      }
    ),

    suite("via / applyToStream consistency")(
      test("q.applyToStream(p.applyToStream(s)) == (p andThen q).applyToStream(s)") {
        check(genIntStream, genIntPipeline, genIntPipeline) { (s, p, q) =>
          for {
            left  <- collect(q.applyToStream(p.applyToStream(s)))
            right <- collect((p andThen q).applyToStream(s))
          } yield assert(left)(equalTo(right))
        }
      },
      test("p.applyToStream(s) == s.via(p)") {
        check(genIntStream, genIntPipeline) { (s, p) =>
          for {
            left  <- collect(p.applyToStream(s))
            right <- collect(s.via(p))
          } yield assert(left)(equalTo(right))
        }
      }
    ),

    suite("andThenSink")(
      test("s.run(p andThenSink sink) == s.via(p).run(sink)") {
        check(genIntStream, genIntPipeline) { (s, p) =>
          for {
            data <- collect(s)
            r1   <- runAsync(Stream.fromChunk(data).runAsync(p andThenSink Sink.collectAll[Int]))
            r2   <- runAsync(Stream.fromChunk(data).via(p).runAsync(Sink.collectAll[Int]))
          } yield assert(r1)(equalTo(r2))
        }
      }
    ),

    suite("Constructors")(
      test("every Pipeline factory executes both stream and sink application routes") {
        val source                              = Chunk(1, 2, 3, 4)
        def both[B](pipeline: Pipeline[Int, B]) =
          for {
            stream <- collect(pipeline.applyToStream(Stream.fromChunk(source)))
            sink   <- runAsync(Stream.fromChunk(source).runAsync(pipeline.applyToSink(Sink.collectAll[B])))
                      .map(_.fold[Chunk[B]](_ => Chunk.empty[B], identity))
          } yield (stream, sink)
        for {
          buffer     <- both(Pipeline.buffer[Int](2))
          chunked    <- both(Pipeline.chunked[Int](2))
          collected  <- both(Pipeline.collect[Int, Int] { case n if n % 2 == 0 => n * 10 })
          collectedA <- both(Pipeline.collectAsync[Int, Int](n => Async.succeed(Option.when(n % 2 == 0)(n * 10))))
          dropped    <- both(Pipeline.drop[Int](2))
          filtered   <- both(Pipeline.filter[Int](_ % 2 == 0))
          filteredA  <- both(Pipeline.filterAsync[Int](n => Async.succeed(n % 2 == 0)))
          identity   <- both(Pipeline.identity[Int])
          mapped     <- both(Pipeline.map[Int, Int](_ * 10))
          mappedA    <- both(Pipeline.mapAsync[Int, Int](n => Async.succeed(n * 10)))
          taken      <- both(Pipeline.take[Int](2))
        } yield assertTrue(
          buffer == ((source, source)),
          chunked == ((Chunk(Chunk(1, 2), Chunk(3, 4)), Chunk(Chunk(1, 2), Chunk(3, 4)))),
          collected == ((Chunk(20, 40), Chunk(20, 40))),
          collectedA == ((Chunk(20, 40), Chunk(20, 40))),
          dropped == ((Chunk(3, 4), Chunk(3, 4))),
          filtered == ((Chunk(2, 4), Chunk(2, 4))),
          filteredA == ((Chunk(2, 4), Chunk(2, 4))),
          identity == ((source, source)),
          mapped == ((Chunk(10, 20, 30, 40), Chunk(10, 20, 30, 40))),
          mappedA == ((Chunk(10, 20, 30, 40), Chunk(10, 20, 30, 40))),
          taken == ((Chunk(1, 2), Chunk(1, 2)))
        )
      },
      test("bottom-valued Pipeline map executes both stream and sink application routes") {
        val failure  = new RuntimeException("bottom map")
        val pipeline = Pipeline.map[Int](_ => throw failure)
        for {
          stream <- runAsync(pipeline.applyToStream(Stream(1)).runCollectAsync).either
          sink   <- runAsync(Stream(1).runAsync(pipeline.applyToSink(Sink.collectAll[Nothing]))).either
        } yield assertTrue(stream == Left(failure), sink == Left(failure))
      },
      test("preserving Pipeline routes execute every primitive lane and widened input") {
        def both[A](stream: Stream[Nothing, A], pipeline: Pipeline[A, A]) =
          for {
            streamResult <- runAsync(pipeline.applyToStream(stream).runCollectAsync)
            sinkResult   <- runAsync(stream.runAsync(pipeline.applyToSink(Sink.collectAll[A])))
          } yield (streamResult, sinkResult)
        val widened: Stream[Nothing, AnyVal] = Stream(1, 2)
        for {
          boolean <- both(Stream(true, false), Pipeline.filter[Boolean](_ => true))
          byte    <- both(Stream.fromArray(Array[Byte](1, 2)), Pipeline.filter[Byte](_ => true))
          char    <- both(Stream.fromArray(Array[Char]('a', 'b')), Pipeline.filter[Char](_ => true))
          short   <- both(Stream.fromArray(Array[Short](1, 2)), Pipeline.filter[Short](_ => true))
          int     <- both(Stream(1, 2), Pipeline.filter[Int](_ => true))
          long    <- both(Stream(1L, 2L), Pipeline.filter[Long](_ => true))
          float   <- both(Stream(1.0f, 2.0f), Pipeline.filter[Float](_ => true))
          double  <- both(Stream(1.0d, 2.0d), Pipeline.filter[Double](_ => true))
          wide    <- both(widened, Pipeline.filter[AnyVal](_ => true))
        } yield assertTrue(
          boolean == ((Right(Chunk(true, false)), Right(Chunk(true, false)))),
          byte == ((Right(Chunk[Byte](1, 2)), Right(Chunk[Byte](1, 2)))),
          char == ((Right(Chunk('a', 'b')), Right(Chunk('a', 'b')))),
          short == ((Right(Chunk[Short](1, 2)), Right(Chunk[Short](1, 2)))),
          int == ((Right(Chunk(1, 2)), Right(Chunk(1, 2)))),
          long == ((Right(Chunk(1L, 2L)), Right(Chunk(1L, 2L)))),
          float == ((Right(Chunk(1.0f, 2.0f)), Right(Chunk(1.0f, 2.0f)))),
          double == ((Right(Chunk(1.0d, 2.0d)), Right(Chunk(1.0d, 2.0d)))),
          wide == ((Right(Chunk[AnyVal](1, 2)), Right(Chunk[AnyVal](1, 2))))
        )
      },
      test("map applies function to each element") {
        check(genIntStream, Gen.function(genInt)) { (s, f) =>
          for {
            data   <- collect(s)
            result <- collect(Stream.fromChunk(data).map(f))
          } yield assert(result)(equalTo(data.map(f)))
        }
      },
      test("filter keeps matching elements") {
        check(genIntStream, Gen.function(Gen.boolean)) { (s, pred) =>
          for {
            data   <- collect(s)
            result <- collect(Stream.fromChunk(data).filter(pred))
          } yield assert(result)(equalTo(data.filter(pred)))
        }
      },
      test("take(n) emits exactly min(n, len) elements") {
        check(genIntStream, Gen.int(0, 60)) { (s, n) =>
          for {
            data   <- collect(s)
            result <- collect(Stream.fromChunk(data).take(n.toLong))
          } yield assert(result)(equalTo(data.take(n)))
        }
      },
      test("drop(n) skips first min(n, len) elements") {
        check(genIntStream, Gen.int(0, 60)) { (s, n) =>
          for {
            data   <- collect(s)
            result <- collect(Stream.fromChunk(data).drop(n.toLong))
          } yield assert(result)(equalTo(data.drop(n)))
        }
      },
      test("collect applies partial function") {
        check(genIntStream) { s =>
          val pf: PartialFunction[Int, Int] = { case x if x > 0 => x * 2 }
          for {
            data   <- collect(s)
            result <- collect(Stream.fromChunk(data).via(Pipeline.collect(pf)))
          } yield assert(result)(equalTo(data.collect(pf)))
        }
      },
      test("Pipeline.identity passes all elements through") {
        check(genIntStream) { s =>
          for {
            left  <- collect(s.via(Pipeline.identity[Int]))
            right <- collect(s)
          } yield assert(left)(equalTo(right))
        }
      },
      test("map applyToSink == contramap on sink") {
        check(genIntStream, Gen.function(genInt)) { (s, f) =>
          for {
            data <- collect(s)
            r1   <- runAsync(
                    Stream
                      .fromChunk(data)
                      .runAsync(
                        Pipeline.map[Int, Int](f).andThenSink[Nothing, Chunk[Int]](Sink.collectAll[Int])
                      )
                  )
            r2 <- runAsync(Stream.fromChunk(data).runAsync(Sink.collectAll[Int].contramap(f)))
          } yield assert(r1)(equalTo(r2))
        }
      },
      test("filter applyToSink == filter applyToStream then sink") {
        check(genIntStream, Gen.function(Gen.boolean)) { (s, pred) =>
          for {
            data <- collect(s)
            r1   <- runAsync(
                    Stream
                      .fromChunk(data)
                      .runAsync(
                        Pipeline.filter[Int](pred).andThenSink[Nothing, Chunk[Int]](Sink.collectAll[Int])
                      )
                  )
            r2 <- runAsync(Stream.fromChunk(data).filter(pred).runAsync(Sink.collectAll[Int]))
          } yield assert(r1)(equalTo(r2))
        }
      },
      test("take applyToSink == take applyToStream then sink") {
        check(genIntStream, Gen.int(0, 30)) { (s, n) =>
          for {
            data <- collect(s)
            r1   <- runAsync(
                    Stream
                      .fromChunk(data)
                      .runAsync(
                        Pipeline.take[Int](n.toLong).andThenSink[Nothing, Chunk[Int]](Sink.collectAll[Int])
                      )
                  )
            r2 <- runAsync(Stream.fromChunk(data).take(n.toLong).runAsync(Sink.collectAll[Int]))
          } yield assert(r1)(equalTo(r2))
        }
      }
    )
  )
}
