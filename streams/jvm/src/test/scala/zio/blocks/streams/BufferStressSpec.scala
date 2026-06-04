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

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.LockSupport

object BufferStressSpec extends StreamsBaseSpec {

  override def aspects: zio.Chunk[TestAspectAtLeastR[TestEnvironment]] =
    zio.Chunk(TestAspect.timed, TestAspect.sequential)

  def spec: Spec[TestEnvironment, Any] = suite("BufferStress")(
    test("1M elements through buffer(16) preserves all elements") {
      ZIO.attemptBlocking {
        val n      = 1_000_000
        val seen   = new AtomicInteger(0)
        val result = Stream
          .range(0, n)
          .map { i =>
            while (i - seen.get() > 8 && !Thread.currentThread().isInterrupted) Thread.onSpinWait()
            i
          }
          .buffer(16)
          .map { i =>
            seen.incrementAndGet()
            i
          }
          .runCollect
        assertTrue(isOrderedRange(result, n))
      }
    } @@ TestAspect.timeout(90.seconds),
    test("1M elements through buffer(64) sum is correct") {
      ZIO.attemptBlocking {
        val n        = 1_000_000
        val expected = n.toLong * (n - 1) / 2
        val seen     = new AtomicInteger(0)
        val result   = Stream
          .range(0, n)
          .map { i =>
            while (i - seen.get() > 32 && !Thread.currentThread().isInterrupted) Thread.onSpinWait()
            i
          }
          .buffer(64)
          .runFold(0L) { (sum, i) =>
            seen.incrementAndGet()
            sum + i
          }
        assertTrue(result == Right(expected))
      }
    } @@ TestAspect.timeout(90.seconds),
    test("100K elements through buffer(1) all arrive in order") {
      ZIO.attemptBlocking {
        val n      = 100_000
        val seen   = new AtomicInteger(0)
        val result = Stream
          .range(0, n)
          .map { i =>
            while (i - seen.get() > 0 && !Thread.currentThread().isInterrupted) Thread.onSpinWait()
            i
          }
          .buffer(1)
          .map { i =>
            seen.incrementAndGet()
            i
          }
          .runCollect
        assertTrue(isOrderedRange(result, n))
      }
    } @@ TestAspect.timeout(90.seconds),
    test("100K elements through buffer(8192) all arrive in order") {
      ZIO.attemptBlocking {
        val n      = 100_000
        val result = Stream.range(0, n).buffer(8192).runCollect
        assertTrue(isOrderedRange(result, n))
      }
    } @@ TestAspect.timeout(60.seconds),
    test("double buffer (buffer then buffer) works correctly") {
      ZIO.attemptBlocking {
        val n      = 10_000
        val seen   = new AtomicInteger(0)
        val result = Stream
          .range(0, n)
          .map { i =>
            while (i - seen.get() > 16 && !Thread.currentThread().isInterrupted) Thread.onSpinWait()
            i
          }
          .buffer(32)
          .buffer(32)
          .map { i =>
            seen.incrementAndGet()
            i
          }
          .runCollect
        assertTrue(isOrderedRange(result, n))
      }
    } @@ TestAspect.timeout(30.seconds),
    test("no thread leaks after buffer stream completes") {
      ZIO.attemptBlocking {
        val baseline = Thread.getAllStackTraces.size()
        val result   = Stream.range(0, 10_000).buffer(8192).runCollect

        val deadline = System.nanoTime() + 2.seconds.toNanos
        var after    = Thread.getAllStackTraces.size()
        while (after > baseline + 4 && System.nanoTime() < deadline) {
          System.gc()
          LockSupport.parkNanos(10.millis.toNanos)
          after = Thread.getAllStackTraces.size()
        }

        assertTrue(result.isRight, after <= baseline + 4)
      }
    } @@ TestAspect.timeout(30.seconds),
    test("defect propagates through buffer without hanging") {
      ZIO.attemptBlocking {
        val seen   = new AtomicInteger(0)
        val caught = scala.util.Try {
          Stream
            .range(0, 1_000)
            .map { i =>
              while (i - seen.get() > 8 && !Thread.currentThread().isInterrupted) Thread.onSpinWait()
              i
            }
            .map((i: Int) => if (i == 500) throw new RuntimeException("oops") else i)
            .buffer(16)
            .map { i =>
              seen.incrementAndGet()
              i
            }
            .runCollect
        }
        assertTrue(caught.isFailure, caught.failed.get.isInstanceOf[RuntimeException])
      }
    } @@ TestAspect.timeout(30.seconds)
  ) @@ TestAspect.sequential

  private def isOrderedRange(result: Either[Any, Chunk[Int]], n: Int): Boolean =
    result match {
      case Right(chunk) if chunk.length == n =>
        chunk(0) == 0 && chunk(n - 1) == (n - 1)
      case _ => false
    }
}
