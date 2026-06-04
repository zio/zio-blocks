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
import zio.blocks.streams.internal.EndOfStream
import zio.durationInt
import zio.test._

import java.util.concurrent.locks.LockSupport

object ConcurrentMapParReaderSpec extends StreamsBaseSpec {

  override def aspects: zio.Chunk[TestAspectAtLeastR[TestEnvironment]] =
    zio.Chunk(TestAspect.timeout(90.seconds), TestAspect.timed, TestAspect.sequential)

  def spec: Spec[TestEnvironment, Any] = suite("ConcurrentMapParReader")(
    test("basic correctness: mapPar doubles all elements") {
      ZIO.attemptBlocking {
        val result = mapParStream(Stream.range(0, 100), n = 4)(_ * 2).runCollect
        assertTrue(
          result match {
            case Right(chunk) =>
              val set = chunk.toSet
              set.size == 100 && (0 until 100).forall(i => set.contains(i * 2))
            case _ => false
          }
        )
      }
    },
    test("error in f propagates and does not hang") {
      ZIO.attemptBlocking {
        val boom      = new RuntimeException("boom-50")
        val attempted =
          scala.util.Try(mapParStream(Stream.range(0, 100), n = 4)(i => if (i == 50) throw boom else i).runCollect)
        assertTrue(
          attempted.isFailure,
          attempted.failed.get.isInstanceOf[RuntimeException],
          attempted.failed.get.getMessage == "boom-50"
        )
      }
    },
    test("mapPar completes reliably under repeated runs") {
      ZIO.attemptBlocking {
        var i = 0
        while (i < 10) {
          val result   = Stream.range(0, 10000).mapPar(4)(_ + 1).runFold(0L)(_ + _.toLong)
          val expected = (1L to 10000L).sum
          require(result == Right(expected), s"iteration $i: $result")
          i += 1
        }
        assertTrue(true)
      }
    } @@ TestAspect.timeout(60.seconds),
    test("mapPar: error in f is always observed by consumer") {
      ZIO.attemptBlocking {
        var i = 0
        while (i < 20) {
          val result = scala.util.Try(
            Stream
              .range(0, 10000)
              .mapPar(8) { x =>
                if (x == 5000) throw new RuntimeException("boom")
                x + 1
              }
              .runCollect
          )
          require(result.isFailure, s"iteration $i should fail but succeeded")
          i += 1
        }
        assertTrue(true)
      }
    } @@ TestAspect.timeout(30.seconds),
    test("mapPar: concurrent errors do not corrupt state or hang") {
      ZIO.attemptBlocking {
        var i = 0
        while (i < 20) {
          val result = scala.util.Try(
            Stream
              .range(0, 1000)
              .mapPar(8) { x =>
                if (x % 100 == 0) throw new RuntimeException(s"err-$x")
                x
              }
              .runCollect
          )
          require(result.isFailure, s"iteration $i should fail but succeeded")
          i += 1
        }
        assertTrue(true)
      }
    } @@ TestAspect.timeout(30.seconds),
    test("identity mapping with n=4 preserves all elements") {
      ZIO.attemptBlocking {
        val result = mapParStream(Stream.range(0, 100), n = 4)(identity).runCollect
        assertTrue(
          result match {
            case Right(chunk) =>
              val set = chunk.toSet
              set.size == 100 && (0 until 100).forall(set.contains)
            case _ => false
          }
        )
      }
    },
    test("early termination: close after 10 elements cleans up worker/coordinator threads") {
      ZIO.attemptBlocking {
        val baseline = Thread.getAllStackTraces.size()

        val reader = Platform.createMapParReader[Int, Int](
          Stream.range(0, 500_000).compile(0),
          4,
          identity,
          Stream.DefaultBufferSize,
          JvmType.Int,
          JvmType.Int
        )

        var i = 0
        while (i < 10) {
          val v = reader.read[Any](EndOfStream)
          assertTrue(v.asInstanceOf[AnyRef] ne EndOfStream)
          i += 1
        }

        reader.close()

        val deadline = System.nanoTime() + 2.seconds.toNanos
        var after    = Thread.getAllStackTraces.size()
        while (after > baseline + 4 && System.nanoTime() < deadline) {
          System.gc()
          LockSupport.parkNanos(10.millis.toNanos)
          after = Thread.getAllStackTraces.size()
        }

        assertTrue(after <= baseline + 4)
      }
    },
    test("n=1 behaves sequentially and emits all elements") {
      ZIO.attemptBlocking {
        val result = mapParStream(Stream.range(0, 100), n = 1)(identity).runCollect
        assertTrue(
          result match {
            case Right(chunk) =>
              val set = chunk.toSet
              set.size == 100 && (0 until 100).forall(set.contains)
            case _ => false
          }
        )
      }
    },
    test("null element preservation: null input and null output are preserved") {
      ZIO.attemptBlocking {
        val upstream = Stream.fromChunk(Chunk("a", null, "b"))
        val result   = mapParStream(upstream, n = 3)(identity).runCollect
        assertTrue(
          result match {
            case Right(chunk) =>
              val set = chunk.toSet
              set.size == 3 && set.contains("a") && set.contains("b") && set.contains(null)
            case _ => false
          }
        )
      }
    }
  )

  private def mapParStream[A, B](stream: Stream[Any, A], n: Int)(f: A => B): Stream[Any, B] =
    Stream.fromReader[Any, B](
      Platform
        .createMapParReader[A, B](stream.compile(0), n, f, Stream.DefaultBufferSize, JvmType.AnyRef, JvmType.AnyRef)
    )
}
