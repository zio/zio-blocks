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

import java.util.concurrent.atomic.AtomicInteger
import zio.blocks.async.{Async, Completer}
import zio.blocks.chunk.Chunk
import zio.blocks.streams.io.Reader
import zio.test._

object MergeAllSpec extends StreamsBaseSpec {

  def spec: Spec[TestEnvironment, Any] = suite("MergeAll and flatMapPar")(
    test("basic mergeAll: all elements from all inner streams are present") {
      val streams = Stream(
        Stream.range(0, 100),
        Stream.range(100, 200),
        Stream.range(200, 300)
      )

      runAsync(Stream.mergeAll(3)(streams).runCollectAsync).map(result =>
        assertTrue(
          result match {
            case Right(values) =>
              val set = values.toSet
              set.size == 300 && (0 until 300).forall(set.contains)
            case Left(_) => false
          }
        )
      )
    },
    test("empty outer stream") {
      val emptyOuter: Stream[Nothing, Stream[Nothing, Int]] = Stream.empty
      runAsync(Stream.mergeAll(4)(emptyOuter).runCollectAsync)
        .map(result => assertTrue(result == Right(Chunk.empty)))
    },
    test("single inner stream") {
      runAsync(Stream.mergeAll(1)(Stream(Stream(1, 2, 3))).runCollectAsync)
        .map(result => assertTrue(result == Right(Chunk(1, 2, 3))))
    },
    test("ready asynchronous inner publishes an available prefix before awaiting its successor") {
      val second = new Completer[Int]
      val inner  = Stream.fromIterable(List(1, 2)).mapAsync(value => if (value == 1) Async.succeed(value) else second)
      val merged = Stream.bufferSize(16)(Stream.mergeAll(2)(Stream(inner)))
      runAsync(merged.runFoldAsync(List.empty[Int]) { (acc, value) =>
        if (value == 1) second.succeed(2)
        Async.succeed(value :: acc)
      }).map(result => assertTrue(result == Right(List(2, 1))))
    },
    test("maxOpen one preserves exact sequential order") {
      val streams = Stream(Stream(1, 2), Stream.empty, Stream(3), Stream(4, 5))
      runAsync(Stream.mergeAll(1)(streams).runCollectAsync)
        .map(result => assertTrue(result == Right(Chunk(1, 2, 3, 4, 5))))
    },
    test("maxOpen one preserves exact sequential order for mapped inner streams") {
      val streams = Stream.range(1, 5).map {
        case 2     => Stream.empty
        case value => Stream(value, -value)
      }
      runAsync(Stream.mergeAll(1)(streams).runCollectAsync)
        .map(result => assertTrue(result == Right(Chunk(1, -1, 3, -3, 4, -4))))
    },
    test("maxOpen one closes each inner before opening the next and closes the outer last") {
      val active                                  = new AtomicInteger
      val events                                  = new StringBuilder
      def inner(value: Int): Stream[Nothing, Int] =
        Stream.fromReader {
          Predef.assert(active.incrementAndGet() == 1)
          events.append(s"open$value;")
          Reader.single(value)
        }.ensuring {
          events.append(s"close$value;")
          active.decrementAndGet()
        }
      val streams = Stream(inner(1), inner(2)).ensuring(events.append("outer-close;"))
      runAsync(Stream.mergeAll(1)(streams).runCollectAsync).map(result =>
        assertTrue(
          result == Right(Chunk(1, 2)),
          active.get == 0,
          events.result() == "open1;close1;open2;close2;outer-close;"
        )
      )
    },
    test("maxOpen one preserves typed inner failure") {
      val streams: Stream[String, Stream[String, Int]] = Stream(Stream(1), Stream.fail("typed"), Stream(2))
      runAsync(Stream.mergeAll(1)(streams).runCollectAsync)
        .map(result => assertTrue(result == Left("typed")))
    },
    test("maxOpen one early termination closes the active inner and outer exactly once") {
      val innerCloses = new AtomicInteger
      val outerCloses = new AtomicInteger
      val inner       = Stream.range(0, 100).ensuring(innerCloses.incrementAndGet())
      val outer       = Stream(inner, inner).ensuring(outerCloses.incrementAndGet())
      runAsync(Stream.mergeAll(1)(outer).take(1).runCollectAsync).map(result =>
        assertTrue(result == Right(Chunk(0)), innerCloses.get == 1, outerCloses.get == 1)
      )
    },
    test("maxOpen one retains an asynchronous reader for an asynchronous outer stream") {
      val outer = Stream.fromReader[Nothing, Stream[Nothing, Int]](Reader.single(Stream(1)).toAsync)
      assertTrue(Stream.compileToReader(Stream.mergeAll(1)(outer)).isInstanceOf[Reader.AsyncReader[_]])
    },
    test("maxOpen larger than number of inner streams") {
      val streams = Stream(
        Stream.range(0, 10),
        Stream.range(10, 20),
        Stream.range(20, 30)
      )

      runAsync(Stream.mergeAll(100)(streams).runCollectAsync).map(result =>
        assertTrue(
          result match {
            case Right(values) =>
              val set = values.toSet
              set.size == 30 && (0 until 30).forall(set.contains)
            case Left(_) => false
          }
        )
      )
    },
    test("a perpetually ready inner cannot starve another ready inner") {
      val hot  = Stream.succeed(1).repeated
      val cold = Stream.succeed(2)
      runAsync(Stream.mergeAll(2)(Stream(hot, cold)).take(8).runCollectAsync)
        .map(result => assertTrue(result.exists(values => values.contains(2))))
    },
    test("error propagation from inner stream") {
      val streams: Stream[Nothing, Stream[String, Int]] = Stream(
        Stream(1, 2),
        Stream.fail("boom"),
        Stream(3, 4)
      )

      runAsync(Stream.mergeAll(3)(streams).runCollectAsync)
        .map(result => assertTrue(result == Left("boom")))
    },
    test("early termination with take(5) completes") {
      val streams = Stream(
        Stream.range(0, 100000),
        Stream.range(100000, 200000),
        Stream.range(200000, 300000)
      )

      runAsync(Stream.mergeAll(3)(streams).take(5).runCollectAsync)
        .map(result => assertTrue(result.exists(_.length == 5)))
    },
    test("flatMapPar basic") {
      val expected = Chunk.fromIterable(
        (0 until 10).flatMap(i => List(i, i * 10))
      )
      runAsync(Stream.range(0, 10).flatMapPar(4)(i => Stream(i, i * 10)).runCollectAsync)
        .map(result =>
          assertTrue(result.exists(values => values.length == 20 && values.toList.sorted == expected.toList.sorted))
        )
    },
    test("flatMapPar equals mergeAll(n)(map(f))") {
      val source = Stream.range(0, 50)
      val f      = (i: Int) => Stream(i, i + 1000)

      for {
        par       <- runAsync(source.flatMapPar(8)(f).runCollectAsync)
        desugared <- runAsync(Stream.mergeAll(8)(source.map(f)).runCollectAsync)
      } yield assertTrue(
        par match {
          case Right(p) =>
            desugared match {
              case Right(m) => p.toList.sorted == m.toList.sorted
              case Left(_)  => false
            }
          case Left(_) => false
        }
      )
    }
  ) @@ TestAspect.sequential
}
