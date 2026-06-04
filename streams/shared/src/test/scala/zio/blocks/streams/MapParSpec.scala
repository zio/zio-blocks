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

import scala.util.Try
import zio.blocks.chunk.Chunk
import zio.test._

object MapParSpec extends StreamsBaseSpec {

  def spec: Spec[TestEnvironment, Any] = suite("MapPar")(
    test("basic correctness") {
      val result      = Stream.range(0, 100).mapPar(4)(_ * 2).runCollect
      val expectedSum = 2 * (0 until 100).sum

      assertTrue(result.exists(_.toList.sum == expectedSum))
    },
    test("mapPar(1) has same elements as map") {
      val par = Stream.range(0, 100).mapPar(1)(_ * 2).runCollect
      val seq = Stream.range(0, 100).map(_ * 2).runCollect

      assertTrue(
        par match {
          case Right(p) =>
            seq match {
              case Right(s) => p.toSet == s.toSet
              case Left(_)  => false
            }
          case Left(_) => false
        }
      )
    },
    test("empty stream") {
      val empty: Stream[Nothing, Int] = Stream.empty
      val result                      = empty.mapPar(4)(identity).runCollect
      assertTrue(result == Right(Chunk.empty))
    },
    test("error in f propagates") {
      val result = Try {
        Stream
          .range(0, 100)
          .mapPar(4)(i => if (i == 50) throw new RuntimeException("boom") else i)
          .runCollect
      }

      assertTrue(result.isFailure)
    },
    test("early termination with take(5) completes") {
      val result = Stream.range(0, 100000).mapPar(4)(_ * 2).take(5).runCollect
      assertTrue(result.exists(_.length == 5))
    },
    test("null element preservation") {
      val result = Stream("a", null.asInstanceOf[String], "b").mapPar(4)(identity).runCollect
      assertTrue(result.exists(_.toList.contains(null)))
    }
  ) @@ TestAspect.sequential
}
