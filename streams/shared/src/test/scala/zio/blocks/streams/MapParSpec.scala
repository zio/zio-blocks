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

object MapParSpec extends StreamsBaseSpec {

  def spec: Spec[TestEnvironment, Any] = suite("MapPar")(
    test("basic correctness") {
      val expectedSum = 2 * (0 until 100).sum
      runAsync(Stream.range(0, 100).mapPar(4)(_ * 2).runCollectAsync)
        .map(result => assertTrue(result.exists(_.toList.sum == expectedSum)))
    },
    test("mapPar(1) degrades to ordered map") {
      val parallel   = Stream.range(0, 100).mapPar(1)(_ * 2)
      val sequential = Stream.range(0, 100).map(_ * 2)
      for {
        par <- runAsync(parallel.runCollectAsync)
        seq <- runAsync(sequential.runCollectAsync)
      } yield assertTrue(
        parallel.render == sequential.render,
        par == seq
      )
    },
    test("empty stream") {
      val empty: Stream[Nothing, Int] = Stream.empty
      runAsync(empty.mapPar(4)(identity).runCollectAsync)
        .map(result => assertTrue(result == Right(Chunk.empty)))
    },
    test("error in f propagates") {
      runAsync(
        Stream.range(0, 100).mapPar(4)(i => if (i == 50) throw new RuntimeException("boom") else i).runCollectAsync
      ).either.map(result => assertTrue(result.isLeft))
    },
    test("early termination with take(5) completes") {
      runAsync(Stream.range(0, 100000).mapPar(4)(_ * 2).take(5).runCollectAsync)
        .map(result => assertTrue(result.exists(_.length == 5)))
    },
    test("null element preservation") {
      runAsync(Stream("a", null.asInstanceOf[String], "b").mapPar(4)(identity).runCollectAsync)
        .map(result => assertTrue(result.exists(_.toList.contains(null))))
    }
  ) @@ TestAspect.sequential
}
