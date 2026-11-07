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

import java.util.concurrent.{CountDownLatch, TimeUnit}
import java.util.concurrent.atomic.{AtomicInteger, AtomicReference}

import zio.{Duration, ZIO}
import zio.blocks.async._
import zio.blocks.chunk.Chunk
import zio.blocks.streams.internal.AsyncConcurrentReaders
import zio.blocks.streams.io.Reader
import zio.test._

/**
 * Deterministic coverage for the final concurrent-reader and Stream helper
 * edges.
 */
object AsyncConcurrentStreamFinalCoverageSpec extends StreamsBaseSpec {
  private implicit val executionContext: scala.concurrent.ExecutionContext =
    scala.concurrent.ExecutionContext.parasitic

  private def run[A](effect: Async[A]): ZIO[Any, Throwable, A] = ZIO.fromFuture(_ => effect.toFuture)

  private def async(values: Int*): Reader.AsyncReader[Int] =
    Reader.fromChunk(Chunk.fromIterable(values)).toAsync

  def spec = suite("final async concurrent/Stream helper coverage")(
    test("a pending worker pull is cancelled and joined before concurrent-reader close completes") {
      val entered  = new Completer[Unit]
      val gate     = new Completer[Int]
      val closes   = new AtomicInteger
      val upstream = new Reader.AsyncReader[Int] {
        override val jvmType                                           = JvmType.Int
        def close()                                                    = { closes.incrementAndGet(); gate.succeed(Int.MinValue); Async.succeed(()) }
        def isClosed                                                   = Async.succeed(closes.get != 0)
        def readable()                                                 = Async.succeed(closes.get == 0)
        def read[A >: Int](sentinel: A)                                = readInt(Long.MinValue).map(v => if (v == Long.MinValue) sentinel else v.toInt)
        override def readInt(sentinel: Long)(implicit ev: Int <:< Int) = {
          entered.succeed(()); gate.map(v => if (v == Int.MinValue) sentinel else v.toLong)
        }
      }
      val reader = AsyncConcurrentReaders.mapPar[Int, Int](upstream, 1, Async.succeed, JvmType.Int)
      val pull   = reader.readInt(-9L).start
      for {
        _ <- run(entered)
        _ <- run(reader.close())
        v <- run(pull)
        _ <- run(reader.close())
      } yield assertTrue(v == -9L, closes.get == 1)
    },
    test("forced-async fused operators and normalized sync/async recovery preserve lane and order") {
      val syncRecovery  = Stream.fail("sync").catchAll(_ => Stream.unwrap(Async.succeed(Stream(1, 2))))
      val asyncRecovery =
        Stream.fail("async").catchAll(_ => Stream.unwrap(Async.succeed(Stream.fromReader[Nothing, Int](async(3, 4)))))
      val stream = (syncRecovery ++ asyncRecovery)
        .map(_ + 1)
        .mapAsync(i => Async.succeed(i * 2))
        .filter(_ != 6)
        .filterAsync(i => Async.succeed(i < 10))
      run(stream.runCollectAsync).map(result => assertTrue(result == Right(Chunk(4, 8))))
    },
    test("close requested during reset is deferred, finalized once, and rejects the reset") {
      val resetEntered = new CountDownLatch(1)
      val releaseReset = new CountDownLatch(1)
      val closeStarted = new CountDownLatch(1)
      val closeDone    = new CountDownLatch(1)
      val closed       = new AtomicInteger
      val finalized    = new AtomicInteger
      val source       = new Reader.SyncReader[Int] {
        def isClosed                                                   = closed.get != 0
        def read[A >: Int](sentinel: A)                                = sentinel
        override def readInt(sentinel: Long)(implicit ev: Int <:< Int) = sentinel
        override def reset(): Unit                                     = { resetEntered.countDown(); releaseReset.await(5, TimeUnit.SECONDS); () }
        def close(): Unit                                              = { closed.incrementAndGet(); () }
      }
      val reader = Stream
        .compileToReader(Stream.fromReader[Nothing, Int](source).ensuring(finalized.incrementAndGet()))
        .expectedSync
      val resetFailure = new AtomicReference[Throwable]
      val resetThread  = new Thread(() =>
        try reader.reset()
        catch { case t: Throwable => resetFailure.set(t) }
      )
      val closeThread = new Thread(() => {
        closeStarted.countDown()
        try reader.close()
        finally closeDone.countDown()
      })
      resetThread.start()
      if (!resetEntered.await(5, TimeUnit.SECONDS)) throw new AssertionError("reset did not start")
      closeThread.start()
      if (!closeStarted.await(5, TimeUnit.SECONDS)) throw new AssertionError("close did not start")
      val completedBeforeReset = closeDone.await(100, TimeUnit.MILLISECONDS)
      releaseReset.countDown()
      resetThread.join(5000)
      closeThread.join(5000)
      assertTrue(
        !resetThread.isAlive,
        !closeThread.isAlive,
        !completedBeforeReset,
        closed.get == 1,
        finalized.get == 1,
        resetFailure.get.isInstanceOf[UnsupportedOperationException]
      )
    },
    test("reentrant lifecycle close does not await its own incomplete completion") {
      val finalized                      = new AtomicInteger
      var reader: Reader.SyncReader[Int] = null
      reader = Stream.compileToReader(Stream(1).ensuring { finalized.incrementAndGet(); reader.close() }).expectedSync
      reader.close()
      reader.close()
      assertTrue(finalized.get == 1, reader.isClosed)
    }
  ) @@ TestAspect.sequential @@ TestAspect.timeout(Duration.fromSeconds(30))
}
