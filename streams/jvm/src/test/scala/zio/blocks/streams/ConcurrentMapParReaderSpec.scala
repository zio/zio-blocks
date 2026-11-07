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

import java.util.concurrent.{CountDownLatch, TimeUnit}
import java.util.concurrent.atomic.{AtomicInteger, AtomicReference}

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
        val upstreamCloses = new AtomicInteger(0)
        val upstream       = new zio.blocks.streams.io.Reader.SyncReader[Int] {
          private var next                      = 0
          override def jvmType: JvmType         = JvmType.Int
          def isClosed: Boolean                 = false
          def read[A1 >: Int](sentinel: A1): A1 = {
            val value = next
            next += 1
            value
          }
          def close(): Unit                                                                                = { upstreamCloses.incrementAndGet(); () }
          override def readInts(dest: Array[Int], offset: Int, length: Int)(implicit ev: Int <:< Int): Int = {
            var i = 0
            while (i < length) { dest(offset + i) = next; next += 1; i += 1 }
            length
          }
        }

        val reader = Platform.createMapParReader[Int, Int](
          upstream,
          4,
          identity,
          Stream.DefaultBufferSize,
          JvmType.Int,
          JvmType.Int
        )

        var i = 0
        while (i < 10) {
          val v = reader.readInt(Long.MinValue)
          assertTrue(v != Long.MinValue)
          i += 1
        }

        reader.close()

        assertTrue(upstreamCloses.get() == 1)
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
    },
    test("upstream close failure is preserved") {
      ZIO.attemptBlocking {
        val closeFailure = new RuntimeException("close")
        val closes       = new AtomicInteger(0)
        val upstream     = new zio.blocks.streams.io.Reader.SyncReader[String] {
          def isClosed: Boolean                    = false
          def read[A1 >: String](sentinel: A1): A1 = sentinel
          def close(): Unit                        = {
            closes.incrementAndGet()
            throw closeFailure
          }
        }
        val reader = Platform.createMapParReader[String, String](
          upstream,
          1,
          identity,
          Stream.DefaultBufferSize,
          JvmType.AnyRef,
          JvmType.AnyRef
        )

        val caught = try { reader.read[Any](EndOfStream); null }
        catch { case t: Throwable => t }
        val firstClose = scala.util.Try(reader.close()).failed.toOption.orNull
        val nextClose  = scala.util.Try(reader.close()).failed.toOption.orNull

        assertTrue(caught eq closeFailure, firstClose eq closeFailure, nextClose eq closeFailure, closes.get() == 1)
      }
    },
    test("worker completion cannot overtake upstream close failure") {
      ZIO.attemptBlocking {
        val closeFailure = new RuntimeException("close")
        val closeEntered = new CountDownLatch(1)
        val releaseClose = new CountDownLatch(1)
        val readDone     = new CountDownLatch(1)
        val readFailure  = new AtomicReference[Throwable](null)
        val upstream     = new zio.blocks.streams.io.Reader.SyncReader[String] {
          def isClosed: Boolean                    = false
          def read[A1 >: String](sentinel: A1): A1 = sentinel
          def close(): Unit                        = {
            closeEntered.countDown()
            releaseClose.await()
            throw closeFailure
          }
        }
        val reader = Platform.createMapParReader[String, String](
          upstream,
          1,
          identity,
          Stream.DefaultBufferSize,
          JvmType.AnyRef,
          JvmType.AnyRef
        )
        val consumer = new Thread(() =>
          try { reader.read[Any](EndOfStream); () }
          catch { case cause: Throwable => readFailure.set(cause) }
          finally readDone.countDown()
        )
        consumer.setDaemon(true)

        consumer.start()
        val entered              = closeEntered.await(2, TimeUnit.SECONDS)
        val completedBeforeClose = readDone.await(1, TimeUnit.SECONDS)
        releaseClose.countDown()
        val completed = readDone.await(2, TimeUnit.SECONDS)
        val replayed  = scala.util.Try(reader.close()).failed.toOption.orNull

        assertTrue(
          entered,
          !completedBeforeClose,
          completed,
          readFailure.get() eq closeFailure,
          replayed eq closeFailure
        )
      }
    },
    test("upstream read failure remains primary and suppresses close failure") {
      ZIO.attemptBlocking {
        val readFailure  = new RuntimeException("read")
        val closeFailure = new RuntimeException("close")
        val upstream     = new zio.blocks.streams.io.Reader.SyncReader[String] {
          def isClosed: Boolean                    = false
          def read[A1 >: String](sentinel: A1): A1 = throw readFailure
          def close(): Unit                        = throw closeFailure
        }
        val reader = Platform.createMapParReader[String, String](
          upstream,
          1,
          identity,
          Stream.DefaultBufferSize,
          JvmType.AnyRef,
          JvmType.AnyRef
        )

        val caught = try { reader.read[Any](EndOfStream); null }
        catch { case t: Throwable => t }

        assertTrue(caught eq readFailure, caught.getSuppressed.toList == List(closeFailure))
      }
    },
    test("primitive mapPar preserves upstream close failure") {
      ZIO.attemptBlocking {
        val closeFailure = new RuntimeException("close")
        val closeEntered = new CountDownLatch(1)
        val releaseClose = new CountDownLatch(1)
        val readDone     = new CountDownLatch(1)
        val readFailure  = new AtomicReference[Throwable](null)
        val closes       = new AtomicInteger(0)
        val upstream     = new zio.blocks.streams.io.Reader.SyncReader[Int] {
          override def jvmType: JvmType                                        = JvmType.Int
          def isClosed: Boolean                                                = false
          def read[A1 >: Int](sentinel: A1): A1                                = sentinel
          override def readInt(sentinel: Long)(implicit ev: Int <:< Int): Long = sentinel
          def close(): Unit                                                    = {
            closes.incrementAndGet()
            closeEntered.countDown()
            releaseClose.await()
            throw closeFailure
          }
        }
        val reader = Platform.createMapParReader[Int, Int](
          upstream,
          1,
          identity,
          Stream.DefaultBufferSize,
          JvmType.Int,
          JvmType.Int
        )
        val consumer = new Thread(() =>
          try { reader.readInt(Long.MinValue); () }
          catch { case cause: Throwable => readFailure.set(cause) }
          finally readDone.countDown()
        )
        consumer.setDaemon(true)

        consumer.start()
        val entered              = closeEntered.await(2, TimeUnit.SECONDS)
        val completedBeforeClose = readDone.await(1, TimeUnit.SECONDS)
        releaseClose.countDown()
        val completed  = readDone.await(2, TimeUnit.SECONDS)
        val firstClose = scala.util.Try(reader.close()).failed.toOption.orNull
        val nextClose  = scala.util.Try(reader.close()).failed.toOption.orNull

        assertTrue(
          entered,
          !completedBeforeClose,
          completed,
          readFailure.get() eq closeFailure,
          firstClose eq closeFailure,
          nextClose eq closeFailure,
          closes.get() == 1
        )
      }
    },
    test("generic mapPar interrupted leading close completes close-hook-only cleanup") {
      ZIO.attemptBlocking {
        assertTrue(mapParInterruptedClose[String](JvmType.AnyRef))
      }
    },
    test("Int mapPar interrupted leading close completes close-hook-only cleanup") {
      ZIO.attemptBlocking {
        assertTrue(mapParInterruptedClose[Int](JvmType.Int))
      }
    },
    test("Long mapPar interrupted leading close completes close-hook-only cleanup") {
      ZIO.attemptBlocking {
        assertTrue(mapParInterruptedClose[Long](JvmType.Long))
      }
    },
    test("Float mapPar interrupted leading close completes close-hook-only cleanup") {
      ZIO.attemptBlocking {
        assertTrue(mapParInterruptedClose[Float](JvmType.Float))
      }
    },
    test("Double mapPar interrupted leading close completes close-hook-only cleanup") {
      ZIO.attemptBlocking {
        assertTrue(mapParInterruptedClose[Double](JvmType.Double))
      }
    },
    test("close wakes a blocked consumer with end-of-stream") {
      ZIO.attemptBlocking {
        val coordinatorEntered     = new CountDownLatch(1)
        val coordinatorInterrupted = new CountDownLatch(1)
        val releaseCoordinator     = new CountDownLatch(1)
        val readDone               = new CountDownLatch(1)
        val closeDone              = new CountDownLatch(1)
        val result                 = new AtomicReference[AnyRef](null)
        val readFailure            = new AtomicReference[Throwable](null)
        val upstream               = new zio.blocks.streams.io.Reader.SyncReader[String] {
          def isClosed: Boolean                                   = false
          def read[A1 >: String](sentinel: A1): A1                = sentinel
          override def readUpToN[A1 >: String](n: Int): Chunk[A1] = {
            coordinatorEntered.countDown()
            try new CountDownLatch(1).await()
            catch { case _: InterruptedException => coordinatorInterrupted.countDown() }
            releaseCoordinator.await()
            Chunk.empty
          }
          def close(): Unit = ()
        }
        val reader = Platform.createMapParReader[String, String](
          upstream,
          1,
          identity,
          Stream.DefaultBufferSize,
          JvmType.AnyRef,
          JvmType.AnyRef
        )
        val sentinel = new AnyRef
        val consumer = new Thread(() =>
          try result.set(reader.read[AnyRef](sentinel))
          catch { case cause: Throwable => readFailure.set(cause) }
          finally readDone.countDown()
        )
        val closer = new Thread(() =>
          try reader.close()
          finally closeDone.countDown()
        )
        consumer.setDaemon(true)
        closer.setDaemon(true)

        consumer.start()
        val entered = coordinatorEntered.await(2, TimeUnit.SECONDS)
        closer.start()
        val interrupted            = coordinatorInterrupted.await(2, TimeUnit.SECONDS)
        val completedBeforeRelease = readDone.await(1, TimeUnit.SECONDS)
        releaseCoordinator.countDown()
        val consumerCleanedUp = readDone.await(2, TimeUnit.SECONDS)
        val closeCompleted    = closeDone.await(2, TimeUnit.SECONDS)

        assertTrue(
          entered,
          interrupted,
          completedBeforeRelease,
          consumerCleanedUp,
          closeCompleted,
          result.get() eq sentinel,
          readFailure.get() == null
        )
      }
    },
    test("close wakes a blocked primitive consumer with end-of-stream") {
      ZIO.attemptBlocking {
        val coordinatorEntered     = new CountDownLatch(1)
        val coordinatorInterrupted = new CountDownLatch(1)
        val releaseCoordinator     = new CountDownLatch(1)
        val readDone               = new CountDownLatch(1)
        val closeDone              = new CountDownLatch(1)
        val result                 = new AtomicReference[java.lang.Long](null)
        val readFailure            = new AtomicReference[Throwable](null)
        val upstream               = new zio.blocks.streams.io.Reader.SyncReader[Int] {
          override def jvmType: JvmType                                                                   = JvmType.Int
          def isClosed: Boolean                                                                           = false
          def read[A1 >: Int](sentinel: A1): A1                                                           = sentinel
          override def readInts(buf: Array[Int], offset: Int, maxLen: Int)(implicit ev: Int <:< Int): Int = {
            coordinatorEntered.countDown()
            try new CountDownLatch(1).await()
            catch { case _: InterruptedException => coordinatorInterrupted.countDown() }
            releaseCoordinator.await()
            0
          }
          def close(): Unit = ()
        }
        val reader = Platform.createMapParReader[Int, Int](
          upstream,
          1,
          identity,
          Stream.DefaultBufferSize,
          JvmType.Int,
          JvmType.Int
        )
        val sentinel = Long.MinValue
        val consumer = new Thread(() =>
          try result.set(reader.readInt(sentinel))
          catch { case cause: Throwable => readFailure.set(cause) }
          finally readDone.countDown()
        )
        val closer = new Thread(() =>
          try reader.close()
          finally closeDone.countDown()
        )
        consumer.setDaemon(true)
        closer.setDaemon(true)

        consumer.start()
        val entered = coordinatorEntered.await(2, TimeUnit.SECONDS)
        closer.start()
        val interrupted            = coordinatorInterrupted.await(2, TimeUnit.SECONDS)
        val completedBeforeRelease = readDone.await(1, TimeUnit.SECONDS)
        releaseCoordinator.countDown()
        val consumerCleanedUp = readDone.await(2, TimeUnit.SECONDS)
        val closeCompleted    = closeDone.await(2, TimeUnit.SECONDS)

        assertTrue(
          entered,
          interrupted,
          completedBeforeRelease,
          consumerCleanedUp,
          closeCompleted,
          result.get().longValue() == sentinel,
          readFailure.get() == null
        )
      }
    }
  )

  private def mapParStream[A, B](stream: Stream[Any, A], n: Int)(f: A => B): Stream[Any, B] =
    Stream.fromReader[Any, B](
      Platform
        .createMapParReader[A, B](
          compileToSyncReader(stream),
          n,
          f,
          Stream.DefaultBufferSize,
          JvmType.AnyRef,
          JvmType.AnyRef
        )
    )

  private def mapParInterruptedClose[A](jvmType: JvmType): Boolean = {
    val closeFailure = new RuntimeException(s"$jvmType mapPar close")
    val upstream     = new ConcurrentCloseTestSupport.CloseHookReader[A](jvmType, closeFailure)
    val reader       = Platform.createMapParReader[A, A](
      upstream,
      1,
      identity,
      Stream.DefaultBufferSize,
      jvmType,
      jvmType
    )
    val workerEntered = upstream.readEntered.await(2, TimeUnit.SECONDS)
    val result        = ConcurrentCloseTestSupport.raceClose(reader, upstream)
    workerEntered && ConcurrentCloseTestSupport.correctFailure(result, closeFailure) && upstream.closes.get() == 1
  }
}
