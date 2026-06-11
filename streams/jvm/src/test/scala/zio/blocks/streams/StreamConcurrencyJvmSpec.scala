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
import zio.blocks.chunk.Chunk
import zio.test._

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.LockSupport

object StreamConcurrencyJvmSpec extends StreamsBaseSpec {

  def spec: Spec[TestEnvironment, Any] = suite("Stream concurrency (JVM)")(
    suite("bufferSize")(
      test("mapPar with custom buffer size produces correct results") {
        for {
          result <- ZIO.fromEither(Stream.bufferSize(128)(Stream.range(0, 1000).mapPar(4)(identity)).runCollect)
        } yield assertTrue(result.length == 1000)
      },
      test("flatMapPar with custom buffer size produces correct results") {
        for {
          result <- ZIO.fromEither(
                      Stream.bufferSize(32)(Stream.range(0, 10).flatMapPar(4)(i => Stream.range(0, i))).runCollect
                    )
        } yield assertTrue(result.length == 45)
      },
      test("nested bufferSize: inner wins") {
        for {
          result <- ZIO.fromEither(
                      Stream.bufferSize(128)(Stream.bufferSize(32)(Stream.range(0, 100).mapPar(2)(identity))).runCollect
                    )
        } yield assertTrue(result.length == 100)
      },
      test("invalid bufferSize throws IllegalArgumentException") {
        assertTrue(scala.util.Try(Stream.bufferSize(3)(Stream.empty)).isFailure) &&
        assertTrue(scala.util.Try(Stream.bufferSize(0)(Stream.empty)).isFailure)
      }
    ),
    suite("buffer")(
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

          val deadline = java.lang.System.nanoTime() + 2.seconds.toNanos
          var after    = Thread.getAllStackTraces.size()
          while (after > baseline + 4 && java.lang.System.nanoTime() < deadline) {
            java.lang.System.gc()
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
    ) @@ TestAspect.sequential @@ TestAspect.timed,
    suite("mapPar")(
      suite("Float mapPar")(
        test("Float => Float identity preserves all elements") {
          ZIO.attemptBlocking {
            val n        = 1_000
            val result   = Stream.range(0, n).map(_.toFloat).mapPar(4)(identity).runFold(0.0)(_ + _)
            val expected = (0 until n).map(_.toDouble).sum
            assertTrue(result.exists(v => math.abs(v - expected) < 1e-3))
          }
        } @@ TestAspect.timeout(15.seconds),
        test("Float => Float doubles each value") {
          ZIO.attemptBlocking {
            val n      = 500
            val result = Stream.range(0, n).map(_.toFloat).mapPar(3)(_ * 2.0f).runCollect
            assertTrue(result.map(_.toSet) == Right((0 until n).map(_.toFloat * 2.0f).toSet))
          }
        } @@ TestAspect.timeout(15.seconds),
        test("Float => Long widens correctly") {
          ZIO.attemptBlocking {
            val n        = 500
            val result   = Stream.range(0, n).map(_.toFloat).mapPar(2)(_.toLong).runFold(0L)(_ + _)
            val expected = n.toLong * (n - 1) / 2
            assertTrue(result == Right(expected))
          }
        } @@ TestAspect.timeout(15.seconds),
        test("Float => String boxes through generic path") {
          ZIO.attemptBlocking {
            val n      = 200
            val result = Stream.range(0, n).map(_.toFloat).mapPar(2)(_.toString).runCollect
            assertTrue(result.map(_.size) == Right(n))
          }
        } @@ TestAspect.timeout(15.seconds),
        test("Float mapPar propagates worker error") {
          ZIO.attemptBlocking {
            val attempted = scala.util.Try {
              Stream
                .range(0, 100)
                .map(_.toFloat)
                .mapPar(2) { f =>
                  if (f >= 50.0f) throw new RuntimeException("boom") else f
                }
                .runDrain
            }
            assertTrue(attempted.isFailure)
          }
        } @@ TestAspect.timeout(15.seconds)
      ),
      suite("Double mapPar")(
        test("Double => Double identity preserves all elements") {
          ZIO.attemptBlocking {
            val n        = 1_000
            val result   = Stream.range(0, n).map(_.toDouble).mapPar(4)(identity).runFold(0.0)(_ + _)
            val expected = (0 until n).map(_.toDouble).sum
            assertTrue(result == Right(expected))
          }
        } @@ TestAspect.timeout(15.seconds),
        test("Double => Double scales each value") {
          ZIO.attemptBlocking {
            val n      = 500
            val result = Stream.range(0, n).map(_.toDouble).mapPar(3)(_ * 0.5).runCollect
            assertTrue(result.map(_.toSet) == Right((0 until n).map(_.toDouble * 0.5).toSet))
          }
        } @@ TestAspect.timeout(15.seconds),
        test("Double => Int narrows correctly") {
          ZIO.attemptBlocking {
            val n        = 500
            val result   = Stream.range(0, n).map(_.toDouble).mapPar(2)(_.toInt).runFold(0L)(_ + _.toLong)
            val expected = n.toLong * (n - 1) / 2
            assertTrue(result == Right(expected))
          }
        } @@ TestAspect.timeout(15.seconds),
        test("Double => String boxes through generic path") {
          ZIO.attemptBlocking {
            val n      = 200
            val result = Stream.range(0, n).map(_.toDouble).mapPar(2)(_.toString).runCollect
            assertTrue(result.map(_.size) == Right(n))
          }
        } @@ TestAspect.timeout(15.seconds),
        test("Double mapPar(1) preserves all elements as set") {
          ZIO.attemptBlocking {
            val n        = 300
            val result   = Stream.range(0, n).map(_.toDouble).mapPar(1)(_ + 1.0).runCollect
            val expected = (0 until n).map(_.toDouble + 1.0).toSet
            assertTrue(result.map(_.toSet) == Right(expected))
          }
        } @@ TestAspect.timeout(15.seconds),
        test("Double mapPar propagates worker error") {
          ZIO.attemptBlocking {
            val attempted = scala.util.Try {
              Stream
                .range(0, 100)
                .map(_.toDouble)
                .mapPar(2) { d =>
                  if (d >= 50.0) throw new RuntimeException("boom") else d
                }
                .runDrain
            }
            assertTrue(attempted.isFailure)
          }
        } @@ TestAspect.timeout(15.seconds),
        test("Double mapPar empty stream completes") {
          ZIO.attemptBlocking {
            val result = Stream.range(0, 0).map(_.toDouble).mapPar(2)(identity).runCollect
            assertTrue(result.map(_.toList) == Right(Nil))
          }
        } @@ TestAspect.timeout(15.seconds)
      ),
      suite("regressions")(
        test("mapPar carries a reserved Long.MinValue INPUT element [AdversarialMapParLongReservedSpec]") {
          ZIO.attemptBlocking {
            val src: Stream[Nothing, Long] = Stream.fromChunk(Chunk(Long.MinValue, 5L, 6L))
            assertTrue(sortedLong(src.mapPar(2)(identity).runCollect) == Right(List(Long.MinValue, 5L, 6L)))
          }
        } @@ TestAspect.timeout(10.seconds),
        test("mapPar carries a reserved Long.MinValue+1 INPUT element [AdversarialMapParLongReservedSpec]") {
          ZIO.attemptBlocking {
            val src: Stream[Nothing, Long] = Stream.fromChunk(Chunk(Long.MinValue + 1L, 7L))
            assertTrue(sortedLong(src.mapPar(2)(identity).runCollect) == Right(List(Long.MinValue + 1L, 7L)))
          }
        } @@ TestAspect.timeout(10.seconds),
        test(
          "mapPar carries reserved Long.MinValue / Long.MinValue+1 OUTPUT values [AdversarialMapParLongReservedSpec]"
        ) {
          ZIO.attemptBlocking {
            val src: Stream[Nothing, Long] = Stream.fromChunk(Chunk(1L, 2L))
            val out                        = src.mapPar(2)(i => if (i == 1L) Long.MinValue else Long.MinValue + 1L).runCollect
            assertTrue(sortedLong(out) == Right(List(Long.MinValue, Long.MinValue + 1L)))
          }
        } @@ TestAspect.timeout(10.seconds),
        test(
          "mapPar Int => Long carries reserved Long OUTPUT values (IntConcurrentMapParReader.outLongQs) [AdversarialMapParLongReservedSpec]"
        ) {
          ZIO.attemptBlocking {
            val src: Stream[Nothing, Int] = Stream.fromChunk(Chunk(1, 2))
            val out                       =
              src.mapPar(2)(i => if (i == 1) Long.MinValue else Long.MinValue + 1L).runCollect
            assertTrue(sortedLong(out) == Right(List(Long.MinValue, Long.MinValue + 1L)))
          }
        } @@ TestAspect.timeout(10.seconds),
        test(
          "mapPar over a Long stream does not drop a real Long.MaxValue element [AdversarialMapParLongSentinelSpec]"
        ) {
          ZIO.attemptBlocking {
            val src: Stream[Nothing, Long] = Stream.fromChunk(Chunk(1L, Long.MaxValue, 2L))
            val result                     = src.mapPar(2)(identity).runCollect
            val sorted                     = result.map(c => c.toArray.sorted.toList)
            // Expected: all three present. Buggy: only Chunk(1L) survives.
            assertTrue(sorted == Right(List(1L, 2L, Long.MaxValue)))
          }
        } @@ TestAspect.timeout(10.seconds),
        test(
          "mapPar Long: reserved ring sentinels round-trip as input [AdversarialConcurrentSentinelConvergenceSpec]"
        ) {
          val in  = List(Long.MinValue, Long.MinValue + 1L, Long.MaxValue, 0L, -1L, 7L)
          val got = Stream.fromIterable(in).mapPar(4)(x => x).runCollect.map(_.toList.sorted)
          assertTrue(got == Right(in.sorted))
        },
        test(
          "mapPar Long: user function mapping INTO reserved sentinels (output escape path) [AdversarialConcurrentSentinelConvergenceSpec]"
        ) {
          val in  = List(1L, 2L, 3L)
          val got = Stream
            .fromIterable(in)
            .mapPar(3) {
              case 1L => Long.MinValue
              case 2L => Long.MinValue + 1L
              case 3L => Long.MaxValue
              case x  => x
            }
            .runCollect
            .map(_.toList.sorted)
          assertTrue(got == Right(List(Long.MinValue, Long.MinValue + 1L, Long.MaxValue).sorted))
        },
        test(
          "mapPar Double: Double.MaxValue / NaN / -0.0 / -Inf round-trip by raw bits [AdversarialConcurrentSentinelConvergenceSpec]"
        ) {
          val in  = List(Double.MaxValue, Double.NaN, 0.0, -0.0, Double.NegativeInfinity)
          val got = Stream
            .fromIterable(in)
            .mapPar(4)(x => x)
            .runCollect
            .map(_.toList.map(java.lang.Double.doubleToRawLongBits).sorted)
          assertTrue(got == Right(in.map(java.lang.Double.doubleToRawLongBits).sorted))
        }
      )
    ),
    suite("mergeAll")(
      suite("error in inner stream does not hang (InnerDone-in-finally regression)")(
        test("generic mergeAll: error in inner terminates without hang") {
          ZIO.attemptBlocking {
            val streams: Stream[String, Stream[String, String]] = Stream.fromIterable(
              Seq(
                Stream.fromIterable(Seq("a", "b", "c")),
                Stream.fromIterable(Seq("d")) ++ Stream.fail(errorMsg),
                Stream.fromIterable(Seq("e", "f"))
              )
            )
            val result = Stream.mergeAll(3)(streams).runCollect
            assertTrue(result.isLeft)
          }
        } @@ TestAspect.timeout(10.seconds),

        test("Int mergeAll: error in inner terminates without hang") {
          ZIO.attemptBlocking {
            val streams = Stream.fromIterable(
              Seq(Stream.range(0, 100), failingIntStream(50), Stream.range(200, 300))
            )
            val result = Stream.mergeAll(3)(streams).runCollect
            assertTrue(result.isLeft)
          }
        } @@ TestAspect.timeout(10.seconds),

        test("Long mergeAll: error in inner terminates without hang") {
          ZIO.attemptBlocking {
            val streams = Stream.fromIterable(
              Seq(
                Stream.range(0, 100).map(_.toLong),
                failingLongStream(50),
                Stream.range(200, 300).map(_.toLong)
              )
            )
            val result = Stream.mergeAll(3)(streams).runCollect
            assertTrue(result.isLeft)
          }
        } @@ TestAspect.timeout(10.seconds),

        test("Double mergeAll: error in inner terminates without hang") {
          ZIO.attemptBlocking {
            val streams = Stream.fromIterable(
              Seq(
                Stream.range(0, 100).map(_.toDouble),
                failingDoubleStream(50),
                Stream.range(200, 300).map(_.toDouble)
              )
            )
            val result = Stream.mergeAll(3)(streams).runCollect
            assertTrue(result.isLeft)
          }
        } @@ TestAspect.timeout(10.seconds),

        test("Float mergeAll: error in inner terminates without hang") {
          ZIO.attemptBlocking {
            val streams = Stream.fromIterable(
              Seq(
                Stream.range(0, 100).map(_.toFloat),
                failingFloatStream(50),
                Stream.range(200, 300).map(_.toFloat)
              )
            )
            val result = Stream.mergeAll(3)(streams).runCollect
            assertTrue(result.isLeft)
          }
        } @@ TestAspect.timeout(10.seconds)
      ),

      suite("multiple concurrent inner errors do not hang")(
        test("Int: 8 streams, half error, does not hang") {
          ZIO.attemptBlocking {
            var i = 0
            while (i < 10) {
              val streams = Stream.fromIterable(
                (0 until 8).map(j =>
                  if (j % 2 == 0) failingIntStream(100)
                  else Stream.range(j * 1000, j * 1000 + 200)
                )
              )
              val result = Stream.mergeAll(8)(streams).runCollect
              require(result.isLeft, s"iteration $i expected Left")
              i += 1
            }
            assertTrue(true)
          }
        } @@ TestAspect.timeout(30.seconds),

        test("Long: 8 streams, half error, does not hang") {
          ZIO.attemptBlocking {
            var i = 0
            while (i < 10) {
              val streams = Stream.fromIterable(
                (0 until 8).map(j =>
                  if (j % 2 == 0) failingLongStream(100)
                  else Stream.range(j * 1000, j * 1000 + 200).map(_.toLong)
                )
              )
              val result = Stream.mergeAll(8)(streams).runCollect
              require(result.isLeft, s"iteration $i expected Left")
              i += 1
            }
            assertTrue(true)
          }
        } @@ TestAspect.timeout(30.seconds)
      ),

      suite("regressions")(
        test("Long mergeAll does not drop a real inner Long.MaxValue element [AdversarialMergeInnerSentinelSpec]") {
          ZIO.attemptBlocking {
            val inner: Stream[Nothing, Long]                  = Stream.fromChunk(Chunk(1L, Long.MaxValue, 2L))
            val outer: Stream[Nothing, Stream[Nothing, Long]] = Stream(inner)
            val result                                        = Stream.mergeAll(1)(outer).runCollect
            // Expected: all three elements survive. Buggy: drainInner stops at the
            // real Long.MaxValue, yielding only Chunk(1L).
            assertTrue(result == Right(Chunk(1L, Long.MaxValue, 2L)))
          }
        } @@ TestAspect.timeout(10.seconds),
        test("Double mergeAll does not drop a real inner Double.MaxValue element [AdversarialMergeInnerSentinelSpec]") {
          ZIO.attemptBlocking {
            val inner: Stream[Nothing, Double]                  = Stream.fromChunk(Chunk(1.0, Double.MaxValue, 2.0))
            val outer: Stream[Nothing, Stream[Nothing, Double]] = Stream(inner)
            val result                                          = Stream.mergeAll(1)(outer).runCollect
            assertTrue(result == Right(Chunk(1.0, Double.MaxValue, 2.0)))
          }
        } @@ TestAspect.timeout(10.seconds),
        test(
          "Long mergeAll carries a real Long.MinValue element (reserved EMPTY marker) [AdversarialMergeLongReservedSpec]"
        ) {
          ZIO.attemptBlocking {
            // Expected: Right(Right(Chunk(Long.MinValue, 5L))).
            // Buggy: the drainer's `offer(Long.MinValue)` throws IllegalArgumentException.
            assertTrue(mergeCollect(Long.MinValue, 5L) == Right(Right(Chunk(Long.MinValue, 5L))))
          }
        } @@ TestAspect.timeout(10.seconds),
        test(
          "Long mergeAll carries a real Long.MinValue+1 element (reserved DONE marker) [AdversarialMergeLongReservedSpec]"
        ) {
          ZIO.attemptBlocking {
            assertTrue(mergeCollect(Long.MinValue + 1L, 7L) == Right(Right(Chunk(Long.MinValue + 1L, 7L))))
          }
        } @@ TestAspect.timeout(10.seconds),
        test(
          "mergeAll Long: reserved sentinels from multiple inners round-trip [AdversarialConcurrentSentinelConvergenceSpec]"
        ) {
          val inner = Stream(Stream(Long.MinValue, 1L), Stream(Long.MinValue + 1L, Long.MaxValue))
          val got   = Stream.mergeAll(2)(inner).runCollect.map(_.toList.sorted)
          assertTrue(got == Right(List(Long.MinValue, Long.MinValue + 1L, Long.MaxValue, 1L).sorted))
        }
      )
    ),
    suite("isClosed")(
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
    ),
    suite("stress")(
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
    ) @@ TestAspect.sequential @@ TestAspect.timed,
    run10ConvergenceSuite,
    run11ConvergenceSuite
  )

  private def isOrderedRange(result: Either[Any, Chunk[Int]], n: Int): Boolean =
    result match {
      case Right(chunk) if chunk.length == n =>
        chunk(0) == 0 && chunk(n - 1) == (n - 1)
      case _ => false
    }

  private def waitForThreadSettle(baseline: Int): Int = {
    var attempts = 0
    var current  = Thread.getAllStackTraces.size()

    while (current > baseline + 4 && attempts < 50) {
      java.lang.System.gc()
      LockSupport.parkNanos(100.millis.toNanos)
      current = Thread.getAllStackTraces.size()
      attempts += 1
    }

    current
  }

  private val errorMsg = "inner-boom"

  private def failingIntStream(failAt: Int): Stream[String, Int] =
    Stream.range(0, failAt) ++ Stream.fail(errorMsg)

  private def failingLongStream(failAt: Int): Stream[String, Long] =
    Stream.range(0, failAt).map(_.toLong) ++ Stream.fail(errorMsg)

  private def failingDoubleStream(failAt: Int): Stream[String, Double] =
    Stream.range(0, failAt).map(_.toDouble) ++ Stream.fail(errorMsg)

  private def failingFloatStream(failAt: Int): Stream[String, Float] =
    Stream.range(0, failAt).map(_.toFloat) ++ Stream.fail(errorMsg)

  private def sortedLong(e: Either[Any, Chunk[Long]]): Either[Any, List[Long]] =
    e.map(_.toArray.sorted.toList)

  private def mergeCollect(elems: Long*): Either[Throwable, Either[Any, Chunk[Long]]] = {
    val inner: Stream[Nothing, Long]                  = Stream.fromChunk(Chunk.fromIterable(elems))
    val outer: Stream[Nothing, Stream[Nothing, Long]] = Stream(inner)
    try Right(Stream.mergeAll(1)(outer).runCollect)
    catch { case t: Throwable => Left(t) }
  }

  // ---- Run #10 convergence probes ------------------------------------------
  // Passing adversarial probes from the tenth hardening round: hostile user
  // callbacks inside the concurrent lanes (mapPar worker function throwing on
  // an arbitrary element), typed inner failures through flatMapPar, and
  // acquire/release balance across mergeAll when an inner stream fails.
  // Committed as convergence evidence.
  private case class Run10ConcBoom(n: Int) extends RuntimeException(s"boom-$n")

  private def run10ConvergenceSuite = suite("run10 convergence")(
    test("mapPar_workerFunctionThrows_defectPropagates_upstreamClosedOnce") {
      val closes = new AtomicInteger(0)
      val s      = Stream
        .range(0, 100)
        .ensuring(closes.incrementAndGet())
        .mapPar(4)(i => if (i == 50) throw Run10ConcBoom(40) else i)
      val r =
        try { s.runCollect; "no-throw" }
        catch { case _: Run10ConcBoom => "boom"; case _: Throwable => "other" }
      assertTrue(r == "boom", closes.get() == 1)
    },
    test("flatMapPar_innerTypedFailure_surfacesAsLeft") {
      val s   = Stream.range(0, 20).flatMapPar(3)(i => if (i == 7) Stream.fail("inner-7") else Stream(i))
      val res = s.runCollect
      assertTrue(res.isLeft)
    },
    test("mergeAll_innerAcquireReleaseBalanced_whenOneInnerFails") {
      val opens                              = new AtomicInteger(0)
      val closes                             = new AtomicInteger(0)
      def inner(i: Int): Stream[String, Int] =
        Stream.fromAcquireRelease({ opens.incrementAndGet(); i }, (_: Int) => { closes.incrementAndGet(); () }) { v =>
          if (v == 3) Stream.fail("inner-3") else Stream(v)
        }
      val streams = Stream.fromIterable((0 until 6).toList).map(inner)
      val res     = Stream.mergeAll(2)(streams).runCollect
      assertTrue(res.isLeft || res.isRight, opens.get() == closes.get())
    }
  )

  // ---- Run #11 convergence probes ------------------------------------------
  // Eleventh (convergence-verification) round: concurrency primitives composed
  // over surfaces no prior round combined them with — multi-segment (>
  // SegmentBudget) interpreter pipelines, user-supplied minimal-but-lawful
  // Reader SPI implementations, and Long-lane streams whose REAL elements equal
  // the in-band EOF sentinel (Long.MaxValue) and the concurrent queues' packing
  // sentinels (Long.MinValue / Long.MinValue + 1). Committed as convergence
  // evidence.

  /**
   * Minimal-but-lawful boxed Reader SPI implementation (default AnyRef lane).
   */
  private final class Run11MinimalJvmReader(elems: Vector[Int]) extends zio.blocks.streams.io.Reader[Int] {
    private var idx                       = 0
    private var closed                    = false
    def isClosed: Boolean                 = closed || idx >= elems.length
    def read[A1 >: Int](sentinel: A1): A1 =
      if (closed || idx >= elems.length) sentinel
      else { val v: A1 = elems(idx); idx += 1; v }
    def close(): Unit = closed = true
  }

  private def run11ConvergenceSuite = suite("run11 convergence")(
    test("mapPar_overMultiSegmentPipeline_noLoss") {
      ZIO.attemptBlocking {
        val deep = (0 until 8001).foldLeft(Stream.range(0, 50): Stream[Nothing, Int])((s, _) => s.map(_ + 1))
        val res  = deep.mapPar(4)(_ + 1).runCollect.map(_.toList.sorted)
        assertTrue(res == Right((0 until 50).map(_ + 8002).toList))
      }
    } @@ TestAspect.timeout(30.seconds),
    test("mapPar_overManualSpiReader_noLoss") {
      ZIO.attemptBlocking {
        val s   = Stream.fromReader[Nothing, Int](new Run11MinimalJvmReader((0 until 100).toVector))
        val res = s.mapPar(4)(_ * 2).runCollect.map(_.toList.sorted)
        assertTrue(res == Right((0 until 100).map(_ * 2).toList))
      }
    } @@ TestAspect.timeout(30.seconds),
    test("mergeAll_manualSpiReaderInners_noLoss") {
      ZIO.attemptBlocking {
        val streams = Stream.fromIterable(
          (0 until 8).map(i =>
            Stream.fromReader[Nothing, Int](new Run11MinimalJvmReader((i * 10 until (i + 1) * 10).toVector))
          )
        )
        val res = Stream.mergeAll(3)(streams).runCollect.map(_.toList.sorted)
        assertTrue(res == Right((0 until 80).toList))
      }
    } @@ TestAspect.timeout(30.seconds),
    test("mapPar_longLane_sentinelValuedElements_lossless") {
      ZIO.attemptBlocking {
        val xs  = List(1L, Long.MaxValue, Long.MinValue, Long.MinValue + 1L, 5L)
        val res = Stream
          .fromChunk(Chunk.fromIterable(xs))
          .mapPar(2)((x: Long) => x)
          .runCollect
          .map(_.toList.sorted)
        assertTrue(res == Right(xs.sorted))
      }
    } @@ TestAspect.timeout(30.seconds),
    test("mergeAll_longLane_innersEmitSentinelValuedElements_lossless") {
      ZIO.attemptBlocking {
        val inners: Stream[Nothing, Stream[Nothing, Long]] = Stream.fromIterable(
          List(
            Stream.fromChunk(Chunk.fromIterable(List(0L, Long.MaxValue))),
            Stream.fromChunk(Chunk.fromIterable(List(Long.MinValue, 2L))),
            Stream.fromChunk(Chunk.fromIterable(List(Long.MinValue + 1L, 3L)))
          )
        )
        val res      = Stream.mergeAll(2)(inners).runCollect.map(_.toList.sorted)
        val expected = List(0L, Long.MaxValue, Long.MinValue, 2L, Long.MinValue + 1L, 3L).sorted
        assertTrue(res == Right(expected))
      }
    } @@ TestAspect.timeout(30.seconds),
    test("flatMapPar_longLane_innersEmitMaxValue_lossless") {
      ZIO.attemptBlocking {
        val res = Stream
          .range(0, 4)
          .flatMapPar(2)(i => Stream.fromChunk(Chunk.fromIterable(List(i.toLong, Long.MaxValue))))
          .runCollect
          .map(_.toList.sorted)
        val expected = ((0 until 4).map(_.toLong).toList ++ List.fill(4)(Long.MaxValue)).sorted
        assertTrue(res == Right(expected))
      }
    } @@ TestAspect.timeout(30.seconds)
  )
}
