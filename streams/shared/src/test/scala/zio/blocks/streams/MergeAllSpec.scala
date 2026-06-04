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

object MergeAllSpec extends StreamsBaseSpec {

  def spec: Spec[TestEnvironment, Any] = suite("MergeAll and flatMapPar")(
    test("basic mergeAll: all elements from all inner streams are present") {
      val streams = Stream(
        Stream.range(0, 100),
        Stream.range(100, 200),
        Stream.range(200, 300)
      )

      val result = Stream.mergeAll(3)(streams).runCollect

      assertTrue(
        result match {
          case Right(values) =>
            val set = values.toSet
            set.size == 300 && (0 until 300).forall(set.contains)
          case Left(_) => false
        }
      )
    },
    test("empty outer stream") {
      val emptyOuter: Stream[Nothing, Stream[Nothing, Int]] = Stream.empty
      val result                                            = Stream.mergeAll(4)(emptyOuter).runCollect
      assertTrue(result == Right(Chunk.empty))
    },
    test("single inner stream") {
      val result = Stream.mergeAll(1)(Stream(Stream(1, 2, 3))).runCollect
      assertTrue(result == Right(Chunk(1, 2, 3)))
    },
    test("maxOpen larger than number of inner streams") {
      val streams = Stream(
        Stream.range(0, 10),
        Stream.range(10, 20),
        Stream.range(20, 30)
      )

      val result = Stream.mergeAll(100)(streams).runCollect

      assertTrue(
        result match {
          case Right(values) =>
            val set = values.toSet
            set.size == 30 && (0 until 30).forall(set.contains)
          case Left(_) => false
        }
      )
    },
    test("error propagation from inner stream") {
      val streams: Stream[Nothing, Stream[String, Int]] = Stream(
        Stream(1, 2),
        Stream.fail("boom"),
        Stream(3, 4)
      )

      val result = Stream.mergeAll(3)(streams).runCollect
      assertTrue(result == Left("boom"))
    },
    test("early termination with take(5) completes") {
      val streams = Stream(
        Stream.range(0, 100000),
        Stream.range(100000, 200000),
        Stream.range(200000, 300000)
      )

      val result = Stream.mergeAll(3)(streams).take(5).runCollect
      assertTrue(result.exists(_.length == 5))
    },
    test("flatMapPar basic") {
      val result   = Stream.range(0, 10).flatMapPar(4)(i => Stream(i, i * 10)).runCollect
      val expected = Chunk.fromIterable(
        (0 until 10).flatMap(i => List(i, i * 10))
      )

      assertTrue(result.exists(values => values.length == 20 && values.toList.sorted == expected.toList.sorted))
    },
    test("flatMapPar equals mergeAll(n)(map(f))") {
      val source = Stream.range(0, 50)
      val f      = (i: Int) => Stream(i, i + 1000)

      val par       = source.flatMapPar(8)(f).runCollect
      val desugared = Stream.mergeAll(8)(source.map(f)).runCollect

      assertTrue(
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
