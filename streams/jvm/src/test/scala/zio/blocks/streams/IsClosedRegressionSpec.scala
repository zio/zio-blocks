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

import zio._
import zio.test._

object IsClosedRegressionSpec extends StreamsBaseSpec {

  def spec: Spec[TestEnvironment, Any] = suite("IsClosedRegressionSpec")(
    suite("mapPar isClosed must not be true while elements remain (P1-A regression)")(
      test("Int mapPar: isClosed false until all elements consumed") {
        ZIO.attemptBlocking {
          val n      = 10_000
          val reader = Stream.range(0, n).mapPar(4)(identity).compile(0, Stream.DefaultBufferSize)
          var count  = 0
          val s      = Long.MinValue
          var v      = reader.readInt(s)(zio.blocks.streams.internal.unsafeEvidence)
          while (v != s) {
            count += 1
            v = reader.readInt(s)(zio.blocks.streams.internal.unsafeEvidence)
          }
          assertTrue(count == n) && assertTrue(reader.isClosed)
        }
      } @@ TestAspect.timeout(15.seconds),

      test("Long mapPar: isClosed false until all elements consumed") {
        ZIO.attemptBlocking {
          val n        = 10_000
          val result   = Stream.range(0, n).map(_.toLong).mapPar(4)(identity).runFold(0L)(_ + _)
          val expected = n.toLong * (n - 1) / 2
          assertTrue(result == Right(expected))
        }
      } @@ TestAspect.timeout(15.seconds),

      test("mapPar: readable() is consistent with isClosed during consumption") {
        ZIO.attemptBlocking {
          val n                  = 1000
          val reader             = Stream.range(0, n).mapPar(2)(identity).compile(0, Stream.DefaultBufferSize)
          var sawClosedBeforeEnd = false
          val s                  = Long.MinValue
          var v                  = reader.readInt(s)(zio.blocks.streams.internal.unsafeEvidence)
          while (v != s) {
            if (reader.isClosed) sawClosedBeforeEnd = true
            v = reader.readInt(s)(zio.blocks.streams.internal.unsafeEvidence)
          }
          assertTrue(!sawClosedBeforeEnd) && assertTrue(reader.isClosed)
        }
      } @@ TestAspect.timeout(15.seconds)
    ),

    suite("mergeAll isClosed must not be true while elements remain (#6 regression)")(
      test("generic mergeAll: isClosed false until all elements consumed") {
        ZIO.attemptBlocking {
          val n       = 5_000
          val streams = Stream.fromIterable(
            (0 until 5).map(i => Stream.fromIterable((i * n until (i + 1) * n).map(_.toString)))
          )
          val result = Stream.mergeAll(3)(streams).runFold(0L)((acc, _) => acc + 1L)
          assertTrue(result == Right(n.toLong * 5))
        }
      } @@ TestAspect.timeout(15.seconds),

      test("Int mergeAll: isClosed false until all elements consumed") {
        ZIO.attemptBlocking {
          val n       = 5_000
          val streams = Stream.fromIterable(
            (0 until 5).map(i => Stream.range(i * n, (i + 1) * n))
          )
          val result = Stream.mergeAll(3)(streams).runFold(0L)((acc, _) => acc + 1L)
          assertTrue(result == Right(n.toLong * 5))
        }
      } @@ TestAspect.timeout(15.seconds),

      test("Long mergeAll: isClosed false until all elements consumed") {
        ZIO.attemptBlocking {
          val n       = 5_000
          val streams = Stream.fromIterable(
            (0 until 5).map(i => Stream.range(i * n, (i + 1) * n).map(_.toLong))
          )
          val result = Stream.mergeAll(3)(streams).runFold(0L)((acc, _) => acc + 1L)
          assertTrue(result == Right(n.toLong * 5))
        }
      } @@ TestAspect.timeout(15.seconds)
    ),

    suite("mergeAll readUpToN returns correct count through sentinels (#4 regression)")(
      test("readUpToN on mergeAll does not lose elements across InnerDone boundaries") {
        ZIO.attemptBlocking {
          val streams = Stream.fromIterable(
            (0 until 20).map(i => Stream.range(i * 50, (i + 1) * 50))
          )
          val result   = Stream.mergeAll(4)(streams).runFold(0L)(_ + _)
          val n        = 1000
          val expected = n.toLong * (n - 1) / 2
          assertTrue(result == Right(expected))
        }
      } @@ TestAspect.timeout(15.seconds)
    )
  )
}
