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
import zio.blocks.streams.io.Reader
import zio.durationInt
import zio.test._

import java.util.concurrent.atomic.{AtomicBoolean, AtomicInteger, AtomicReference}
import java.util.concurrent.TimeUnit
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
    test("completed primitive singleton buffers publish their final element before EOF") {
      ZIO.attemptBlocking {
        def verify[A](value: A)(implicit infer: JvmType.Infer[A]): Boolean = {
          var iteration = 0
          var valid     = true
          while (iteration < 100 && valid) {
            valid = Stream.succeed(value).buffer(1).runCollect == Right(Chunk(value))
            iteration += 1
          }
          valid
        }

        assertTrue(
          verify(true),
          verify(Byte.MinValue),
          verify(Char.MaxValue),
          verify(Short.MinValue),
          verify(Int.MinValue),
          verify(Long.MinValue),
          verify(Float.MinValue),
          verify(Double.MinValue)
        )
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
    } @@ TestAspect.timeout(30.seconds),
    test("upstream close failure propagates through buffer") {
      ZIO.attemptBlocking {
        val closeFailure = new RuntimeException("close")
        val upstream     = new Reader.SyncReader[Int] {
          def isClosed: Boolean                 = false
          def read[A1 >: Int](sentinel: A1): A1 = sentinel
          def close(): Unit                     = throw closeFailure
        }

        val caught = scala.util.Try(Stream.fromReader[Any, Int](upstream).buffer(1).runCollect)

        assertTrue(caught.failed.toOption.contains(closeFailure))
      }
    } @@ TestAspect.timeout(30.seconds),
    test("upstream read failure remains primary and suppresses close failure through buffer") {
      ZIO.attemptBlocking {
        val readFailure  = new RuntimeException("read")
        val closeFailure = new RuntimeException("close")
        val upstream     = new Reader.SyncReader[Int] {
          def isClosed: Boolean                 = false
          def read[A1 >: Int](sentinel: A1): A1 = throw readFailure
          def close(): Unit                     = throw closeFailure
        }

        val caught = scala.util.Try(Stream.fromReader[Any, Int](upstream).buffer(1).runCollect).failed.toOption.orNull

        assertTrue(caught eq readFailure, caught.getSuppressed.toList == List(closeFailure))
      }
    } @@ TestAspect.timeout(30.seconds),
    test("buffer close replays cleanup failure without closing upstream twice") {
      ZIO.attemptBlocking {
        val closeFailure = new RuntimeException("close")
        val closes       = new AtomicInteger(0)
        val upstream     = new Reader.SyncReader[Int] {
          def isClosed: Boolean                 = false
          def read[A1 >: Int](sentinel: A1): A1 = sentinel
          def close(): Unit                     = {
            closes.incrementAndGet()
            throw closeFailure
          }
        }
        val reader = Platform.createBufferedReader(upstream, 1)
        val seen   = new AtomicReference[Throwable](null)

        try reader.read[Any](new AnyRef)
        catch { case cause: Throwable => seen.set(cause) }
        val firstClose = scala.util.Try(reader.close()).failed.toOption.orNull
        val nextClose  = scala.util.Try(reader.close()).failed.toOption.orNull

        assertTrue(seen.get() eq closeFailure, firstClose eq closeFailure, nextClose eq closeFailure, closes.get() == 1)
      }
    } @@ TestAspect.timeout(30.seconds),
    test("interrupted leading buffer close still completes close-hook-only cleanup") {
      ZIO.attemptBlocking {
        val closeFailure  = new RuntimeException("buffer close")
        val upstream      = new ConcurrentCloseTestSupport.CloseHookReader[String](JvmType.AnyRef, closeFailure)
        val reader        = Platform.createBufferedReader(upstream, 1)
        val workerEntered = upstream.readEntered.await(2, TimeUnit.SECONDS)
        val result        = ConcurrentCloseTestSupport.raceClose(reader, upstream)

        assertTrue(
          workerEntered,
          ConcurrentCloseTestSupport.correctFailure(result, closeFailure),
          upstream.closes.get() == 1
        )
      }
    } @@ TestAspect.timeout(30.seconds),
    test("buffer close preserves a caller's pending interrupt without reporting stream failure") {
      ZIO.attemptBlocking {
        val reader = Platform.createBufferedReader(Reader.fromRange(0 until 100000), 32)
        val first  = reader.readInt(-1L)
        Thread.currentThread().interrupt()
        val failure   = scala.util.Try(reader.close()).failed.toOption
        val preserved = Thread.currentThread().isInterrupted
        Thread.interrupted()
        assertTrue(first == 0L, failure.isEmpty, preserved)
      }
    } @@ TestAspect.timeout(30.seconds),
    test("buffer cancellation discards a producer read rejected after upstream close") {
      ZIO.attemptBlocking {
        val physicalLaneChecked = new java.util.concurrent.CountDownLatch(1)
        val releaseLaneCheck    = new java.util.concurrent.CountDownLatch(1)
        val laneChecks          = new AtomicInteger(0)
        val closed              = new AtomicBoolean(false)
        val upstream            = new Reader.SyncReader[Int] {
          override def jvmType: JvmType =
            if (laneChecks.incrementAndGet() == 1) JvmType.Int
            else {
              physicalLaneChecked.countDown()
              releaseLaneCheck.await()
              if (closed.get()) JvmType.AnyRef else JvmType.Int
            }
          def isClosed: Boolean                 = closed.get()
          def read[A1 >: Int](sentinel: A1): A1 = sentinel
          def close(): Unit                     = { closed.set(true); releaseLaneCheck.countDown() }
        }
        val reader  = Platform.createBufferedReader(upstream, 1)
        val entered = physicalLaneChecked.await(5, TimeUnit.SECONDS)
        val failure = scala.util.Try(reader.close()).failed.toOption

        assertTrue(entered, failure.isEmpty, closed.get())
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
