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

import java.util.concurrent.atomic.AtomicInteger

import scala.concurrent.ExecutionContext

import zio._
import zio.blocks.async._
import zio.blocks.streams.io.Reader
import zio.test._

object AsyncOwnershipLaneSpec extends StreamsBaseSpec {
  private implicit val ec: ExecutionContext = new ExecutionContext {
    def execute(runnable: Runnable): Unit     = Async.schedule(runnable, forceMacrotask = false)
    def reportFailure(cause: Throwable): Unit = throw cause
  }

  private def run[A](effect: Async[A]): ZIO[Any, Throwable, A] = ZIO.fromFuture(_ => effect.toFuture)

  private def source[A: JvmType.Infer](values: Vector[A], acquisitions: AtomicInteger): Stream[Nothing, A] =
    Stream.fromReaderAsync[Nothing, A] {
      acquisitions.incrementAndGet()
      Async.succeed(Reader.fromIterable(values))
    }

  def spec: Spec[TestEnvironment with Scope, Any] = suite("Async ownership lanes")(
    test("source-owned map preserves every physical lane and remains lazy") {
      val acquisitions = new AtomicInteger
      val longs        = source(Vector(1L, 2L), acquisitions).map(_ + 10L)
      val doubles      = source(Vector(1.25, 2.5), acquisitions).map(_.toFloat)
      val refs         = source(Vector("a", "bb"), acquisitions).map(_ + "!")
      val before       = acquisitions.get
      for {
        longResult   <- run(longs.runCollectAsync)
        doubleResult <- run(doubles.runCollectAsync)
        refResult    <- run(refs.runCollectAsync)
      } yield assertTrue(
        before == 0,
        longResult == Right(zio.blocks.chunk.Chunk(11L, 12L)),
        doubleResult == Right(zio.blocks.chunk.Chunk(1.25f, 2.5f)),
        refResult == Right(zio.blocks.chunk.Chunk("a!", "bb!")),
        acquisitions.get == 3
      )
    },
    test("source-owned drop/take is lane-independent") {
      val acquisitions = new AtomicInteger
      for {
        booleanResult <- run(source(Vector(false, true, false, true), acquisitions).drop(1).take(2).runCollectAsync)
        floatResult   <- run(source(Vector(1.0f, 2.0f, 3.0f), acquisitions).drop(1).take(1).runCollectAsync)
        refResult     <- run(source(Vector("a", "b", "c"), acquisitions).drop(1).take(5).runCollectAsync)
      } yield assertTrue(
        booleanResult == Right(zio.blocks.chunk.Chunk(true, false)),
        floatResult == Right(zio.blocks.chunk.Chunk(2.0f)),
        refResult == Right(zio.blocks.chunk.Chunk("b", "c")),
        acquisitions.get == 3
      )
    },
    test("linear map/filter programs use shared semantics across lanes and cutoff boundaries") {
      val acquisitions = new AtomicInteger
      val longs        = (0 until 11)
        .foldLeft(source(Vector(1L, 2L, 3L), acquisitions)) { (stream, _) =>
          stream.map(_ + 1L)
        }
        .filter(_ % 2L == 0L)
      val doubles = (0 until 65)
        .foldLeft(source(Vector(1d, 2d), acquisitions)) { (stream, _) =>
          stream.map(_ + 0.5d)
        }
        .filter(_ > 33d)
      val refs = source(Vector("a", "bb", "ccc"), acquisitions)
        .map(_.length)
        .filter(_ > 1)
        .map(_.toLong)
      for {
        longResult   <- run(longs.runFoldAsync(0L)((acc, value) => Async.succeed(acc + value)))
        doubleResult <- run(doubles.runFoldAsync(0d)((acc, value) => Async.succeed(acc + value)))
        refResult    <- run(refs.runFoldAsync(0L)((acc, value) => Async.succeed(acc + value)))
      } yield assertTrue(
        longResult == Right(26L),
        doubleResult == Right(68.0d),
        refResult == Right(5L),
        acquisitions.get == 3
      )
    }
  )
}
