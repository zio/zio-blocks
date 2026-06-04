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

import zio.ZIO
import zio.blocks.chunk.Chunk
import zio.durationInt
import zio.test._

import java.util.concurrent.locks.LockSupport

object ConcurrentStressSpec extends StreamsBaseSpec {

  override def aspects: zio.Chunk[TestAspectAtLeastR[TestEnvironment]] =
    zio.Chunk(TestAspect.timed, TestAspect.sequential)

  def spec: Spec[TestEnvironment, Any] = suite("ConcurrentStress")(
    test("mergeAll - 1M elements no data loss") {
      ZIO.attemptBlocking {
        val n = 1_000_000L

        val streams = Stream.fromChunk(
          Chunk.fromIterable((0 until 1000).map(i => Stream.range(i * 1000, (i + 1) * 1000)))
        )

        val expected = n * (n - 1L) / 2L
        val result   = Stream.mergeAll(16)(streams).runFold(0L)(_ + _.toLong)

        assertTrue(result == Right(expected))
      }
    } @@ TestAspect.timeout(180.seconds),
    test("mapPar - 100K elements no data loss") {
      ZIO.attemptBlocking {
        val n        = 100_000
        val expected = (0L until n.toLong).sum * 2L
        val result   = Stream.range(0, n).mapPar(8)(_.toLong * 2L).runFold(0L)(_ + _)

        assertTrue(result == Right(expected))
      }
    } @@ TestAspect.timeout(90.seconds),
    test("mergeAll - no thread leak") {
      ZIO.attemptBlocking {
        val baseline = Thread.getAllStackTraces.size()

        val result = Stream
          .mergeAll(8)(
            Stream.fromChunk(
              Chunk.fromIterable((0 until 100).map(i => Stream.range(i * 100, (i + 1) * 100)))
            )
          )
          .runDrain

        val after = waitForThreadSettle(baseline)
        assertTrue(result == Right(()), after <= baseline + 4)
      }
    } @@ TestAspect.timeout(60.seconds),
    test("mapPar - no thread leak") {
      ZIO.attemptBlocking {
        val baseline = Thread.getAllStackTraces.size()

        val result = Stream
          .range(0, 100_000)
          .mapPar(8)(_ + 1)
          .runDrain

        val after = waitForThreadSettle(baseline)
        assertTrue(result == Right(()), after <= baseline + 4)
      }
    } @@ TestAspect.timeout(60.seconds),
    test("mergeAll - early termination no thread leak") {
      ZIO.attemptBlocking {
        val baseline = Thread.getAllStackTraces.size()

        val result = Stream
          .mergeAll(8)(
            Stream.fromChunk(
              Chunk.fromIterable((0 until 1000).map(i => Stream.range(i * 1000, (i + 1) * 1000)))
            )
          )
          .take(5)
          .runCollect

        Thread.sleep(500)
        val after = waitForThreadSettle(baseline)

        assertTrue(result.map(_.size) == Right(5), after <= baseline + 4)
      }
    } @@ TestAspect.timeout(60.seconds),
    test("mergeAll + mapPar pipeline") {
      ZIO.attemptBlocking {
        val result = Stream
          .mergeAll(4)(
            Stream.fromChunk(
              Chunk.fromIterable((0 until 16).map(i => Stream.range(i * 10, (i + 1) * 10)))
            )
          )
          .buffer(16)
          .mapPar(4)(_ + 1)
          .runCollect
          .map(_.toSet)

        val expected = Right((1 to 160).toSet)
        assertTrue(result == expected)
      }
    } @@ TestAspect.timeout(60.seconds)
  )

  private def waitForThreadSettle(baseline: Int): Int = {
    var attempts = 0
    var current  = Thread.getAllStackTraces.size()

    while (current > baseline + 4 && attempts < 50) {
      System.gc()
      LockSupport.parkNanos(100.millis.toNanos)
      current = Thread.getAllStackTraces.size()
      attempts += 1
    }

    current
  }
}
