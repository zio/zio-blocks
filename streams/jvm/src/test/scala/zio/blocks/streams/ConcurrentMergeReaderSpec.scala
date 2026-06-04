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
import zio.blocks.streams.internal.{ConcurrentMergeReader, EndOfStream}
import zio.blocks.streams.io.Reader
import zio.durationInt
import zio.test._

import java.util.concurrent.locks.LockSupport

object ConcurrentMergeReaderSpec extends StreamsBaseSpec {

  override def aspects: zio.Chunk[TestAspectAtLeastR[TestEnvironment]] =
    zio.Chunk(TestAspect.timeout(90.seconds), TestAspect.timed, TestAspect.sequential)

  def spec: Spec[TestEnvironment, Any] = suite("ConcurrentMergeReader")(
    test("basic merge: all elements from all inner streams are present") {
      ZIO.attemptBlocking {
        val inners = Chunk(
          Stream.range(0, 100),
          Stream.range(100, 200),
          Stream.range(200, 300)
        )

        val result = mergeStream(inners, maxOpen = 3).runCollect
        assertTrue(
          result match {
            case Right(chunk) =>
              val set = chunk.toSet
              set.size == 300 && (0 until 300).forall(set.contains)
            case _ => false
          }
        )
      }
    },
    test("empty outer stream completes immediately") {
      ZIO.attemptBlocking {
        val result = mergeStream[Int](Chunk.empty, maxOpen = 4).runCollect
        assertTrue(result == Right(Chunk.empty))
      }
    },
    test("empty inner streams complete cleanly") {
      ZIO.attemptBlocking {
        val empty  = Stream.fromChunk(Chunk.empty[Int])
        val inners = Chunk(empty, empty, empty)
        val result = mergeStream(inners, maxOpen = 3).runCollect
        assertTrue(result == Right(Chunk.empty))
      }
    },
    test("fail-fast: inner defect propagates without hang") {
      ZIO.attemptBlocking {
        val boom   = new RuntimeException("boom")
        val inners = Chunk(
          Stream.range(0, 1000),
          Stream.range(0, 1000).map(i => if (i == 500) throw boom else i),
          Stream.range(1000, 2000)
        )

        val attempted = scala.util.Try(mergeStream(inners, maxOpen = 3).runCollect)
        assertTrue(
          attempted.isFailure,
          attempted.failed.get.isInstanceOf[RuntimeException],
          attempted.failed.get.getMessage == "boom"
        )
      }
    },
    test("early termination: closing consumer stops workers") {
      ZIO.attemptBlocking {
        val baseline = Thread.getAllStackTraces.size()
        val inners   = Chunk(
          Stream.range(0, 200_000),
          Stream.range(200_000, 400_000),
          Stream.range(400_000, 600_000)
        )

        val reader =
          new ConcurrentMergeReader[Int](Reader.fromChunk(inners), maxOpen = 3, bufferSize = Stream.DefaultBufferSize)
        var i = 0
        while (i < 5) {
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
    test("maxOpen = 1 behaves sequentially and preserves all elements") {
      ZIO.attemptBlocking {
        val inners = Chunk(
          Stream.range(0, 50),
          Stream.range(50, 100),
          Stream.range(100, 150)
        )

        val result = mergeStream(inners, maxOpen = 1).runCollect
        assertTrue(
          result match {
            case Right(chunk) =>
              val set = chunk.toSet
              set.size == 150 && (0 until 150).forall(set.contains)
            case _ => false
          }
        )
      }
    },
    test("large merge: 100 inner streams x 1000 elements each has correct sum") {
      ZIO.attemptBlocking {
        val inners = Chunk.fromIterable(
          (0 until 100).map { i =>
            val start = i * 1000
            Stream.range(start, start + 1000)
          }
        )

        val expected = {
          val n = 100_000L
          n * (n - 1L) / 2L
        }

        val result = mergeStream(inners, maxOpen = 8).runFold(0L)(_ + _.toLong)
        assertTrue(result == Right(expected))
      }
    },
    test("mergeAll completes reliably under repeated runs") {
      ZIO.attemptBlocking {
        var i = 0
        while (i < 10) {
          val result = Stream
            .mergeAll(8)(
              Stream.fromIterable((0 until 16).map(j => Stream.range(j * 500, (j + 1) * 500)))
            )
            .runFold(0L)(_ + _.toLong)
          require(result == Right(31996000L), s"iteration $i: $result")
          i += 1
        }
        assertTrue(true)
      }
    } @@ TestAspect.timeout(60.seconds),
    test("mergeAll: error in one inner stream terminates all drainers promptly") {
      ZIO.attemptBlocking {
        val fast: Stream[String, Int]  = Stream.fromIterable(0 until 10) ++ Stream.fail("boom")
        val slow: Stream[Nothing, Int] = Stream.fromIterable(
          new Iterable[Int] {
            def iterator: Iterator[Int] = Iterator.continually { Thread.sleep(50); 42 }.take(10000)
          }
        )
        val result = Stream.mergeAll(2)(Stream.fromIterable(List(fast, slow))).runCollect
        assertTrue(result.isLeft)
      }
    } @@ TestAspect.timeout(10.seconds),
    test("mergeAll: concurrent errors from multiple inners do not corrupt state") {
      ZIO.attemptBlocking {
        var i = 0
        while (i < 30) {
          val streams = Stream.fromIterable(
            (0 until 8).map(j =>
              if (j % 2 == 0) Stream.range(0, 100) ++ Stream.fail(s"err-$j")
              else Stream.range(100, 200)
            )
          )
          val result = Stream.mergeAll(8)(streams).runCollect
          require(result.isLeft, s"iteration $i expected Left but got Right")
          i += 1
        }
        assertTrue(true)
      }
    } @@ TestAspect.timeout(60.seconds)
  )

  private def mergeStream[A](inners: Chunk[Stream[Any, A]], maxOpen: Int): Stream[Any, A] =
    Stream.fromReader[Any, A](
      new ConcurrentMergeReader[A](Reader.fromChunk(inners), maxOpen, bufferSize = Stream.DefaultBufferSize)
    )
}
