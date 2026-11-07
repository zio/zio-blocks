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

import java.util.concurrent.{CountDownLatch, TimeUnit}
import java.util.concurrent.atomic.{AtomicInteger, AtomicReference}

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
        val innerCloses = new AtomicInteger(0)
        val outerCloses = new AtomicInteger(0)
        val allStarted  = new CountDownLatch(3)
        val inners      = Chunk.fromIterable((0 until 3).map { offset =>
          Stream.fromReader[Any, Int](new Reader.SyncReader[Int] {
            private var next                      = offset * 200000
            private var started                   = false
            def isClosed: Boolean                 = false
            def read[A1 >: Int](sentinel: A1): A1 = {
              if (!started) { started = true; allStarted.countDown() }
              val value = next
              next += 1
              value
            }
            def close(): Unit = { innerCloses.incrementAndGet(); () }
          })
        })
        val outer = new Reader.SyncReader[Stream[Any, Int]] {
          private val delegate                               = Reader.fromChunk(inners)
          def isClosed: Boolean                              = delegate.isClosed
          def read[A1 >: Stream[Any, Int]](sentinel: A1): A1 = delegate.read(sentinel)
          def close(): Unit                                  = { outerCloses.incrementAndGet(); delegate.close() }
        }

        val reader =
          new ConcurrentMergeReader[Int](outer, maxOpen = 3, bufferSize = Stream.DefaultBufferSize)
        var i = 0
        while (i < 5) {
          val v = reader.read[Any](EndOfStream)
          assertTrue(v.asInstanceOf[AnyRef] ne EndOfStream)
          i += 1
        }
        val started = allStarted.await(5, TimeUnit.SECONDS)
        reader.close()

        assertTrue(started, innerCloses.get() == 3, outerCloses.get() == 1)
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
    } @@ TestAspect.timeout(60.seconds),
    test("outer close failure is preserved") {
      ZIO.attemptBlocking {
        var iteration = 0
        var valid     = true
        while (iteration < 100) {
          val closeFailure = new RuntimeException(s"close-$iteration")
          val closes       = new AtomicInteger(0)
          val outer        = new Reader.SyncReader[Stream[Any, String]] {
            def isClosed: Boolean                                 = false
            def read[A1 >: Stream[Any, String]](sentinel: A1): A1 = sentinel
            def close(): Unit                                     = {
              closes.incrementAndGet()
              throw closeFailure
            }
          }
          val reader =
            new ConcurrentMergeReader[String](outer, maxOpen = 1, bufferSize = Stream.DefaultBufferSize)

          val caught = try { reader.read[Any](EndOfStream); null }
          catch { case t: Throwable => t }
          val firstClose = scala.util.Try(reader.close()).failed.toOption.orNull
          val nextClose  = scala.util.Try(reader.close()).failed.toOption.orNull

          valid &&= caught.eq(closeFailure) && firstClose.eq(closeFailure) && nextClose.eq(closeFailure) && closes
            .get() == 1
          iteration += 1
        }

        assertTrue(valid)
      }
    },
    test("outer read failure remains primary and suppresses close failure") {
      ZIO.attemptBlocking {
        val readFailure  = new RuntimeException("read")
        val closeFailure = new RuntimeException("close")
        val outer        = new Reader.SyncReader[Stream[Any, String]] {
          def isClosed: Boolean                                 = false
          def read[A1 >: Stream[Any, String]](sentinel: A1): A1 = throw readFailure
          def close(): Unit                                     = throw closeFailure
        }
        val reader = new ConcurrentMergeReader[String](outer, maxOpen = 1, bufferSize = Stream.DefaultBufferSize)

        val caught = try { reader.read[Any](EndOfStream); null }
        catch { case t: Throwable => t }

        assertTrue(caught eq readFailure, caught.getSuppressed.toList == List(closeFailure))
      }
    },
    test("inner close failure is preserved") {
      ZIO.attemptBlocking {
        val closeFailure = new RuntimeException("close")
        val inner        = Stream.fromReader[Any, String](new Reader.SyncReader[String] {
          def isClosed: Boolean                    = false
          def read[A1 >: String](sentinel: A1): A1 = sentinel
          def close(): Unit                        = throw closeFailure
        })
        val reader = new ConcurrentMergeReader[String](
          Reader.fromChunk(Chunk(inner)),
          maxOpen = 1,
          bufferSize = Stream.DefaultBufferSize
        )

        val caught = try { reader.read[Any](EndOfStream); null }
        catch { case t: Throwable => t }

        assertTrue(caught eq closeFailure)
      }
    },
    test("primitive merge preserves inner close failure") {
      ZIO.attemptBlocking {
        val closeFailure = new RuntimeException("close")
        val closes       = new AtomicInteger(0)
        val inner        = Stream.fromReader[Any, Int](new Reader.SyncReader[Int] {
          override def jvmType: JvmType                                        = JvmType.Int
          def isClosed: Boolean                                                = false
          def read[A1 >: Int](sentinel: A1): A1                                = sentinel
          override def readInt(sentinel: Long)(implicit ev: Int <:< Int): Long = sentinel
          def close(): Unit                                                    = {
            closes.incrementAndGet()
            throw closeFailure
          }
        })
        val reader = Platform.createMergeReader[Int](
          Reader.fromChunk(Chunk(inner)),
          1,
          Stream.DefaultBufferSize,
          JvmType.Int
        )

        val caught = try { reader.readInt(Long.MinValue); null }
        catch { case t: Throwable => t }
        val firstClose = scala.util.Try(reader.close()).failed.toOption.orNull
        val nextClose  = scala.util.Try(reader.close()).failed.toOption.orNull

        assertTrue(caught eq closeFailure, firstClose eq closeFailure, nextClose eq closeFailure, closes.get() == 1)
      }
    },
    test("Int merge waits for outer close before publishing completion") {
      ZIO.attemptBlocking {
        assertTrue(primitiveOuterCloseBarrier[Int](JvmType.Int)(_.readInt(Long.MinValue)))
      }
    },
    test("Long merge waits for outer close before publishing completion") {
      ZIO.attemptBlocking {
        assertTrue(primitiveOuterCloseBarrier[Long](JvmType.Long)(_.readLong(Long.MaxValue)))
      }
    },
    test("Float merge waits for outer close before publishing completion") {
      ZIO.attemptBlocking {
        assertTrue(primitiveOuterCloseBarrier[Float](JvmType.Float)(_.readFloat(Double.MaxValue)))
      }
    },
    test("Double merge waits for outer close before publishing completion") {
      ZIO.attemptBlocking {
        assertTrue(primitiveOuterCloseBarrier[Double](JvmType.Double)(_.readDouble(Double.MaxValue)))
      }
    },
    test("generic merge interrupted leading close completes outer close-hook-only cleanup") {
      ZIO.attemptBlocking(assertTrue(mergeOuterInterruptedClose[String](JvmType.AnyRef)))
    },
    test("Int merge interrupted leading close completes outer close-hook-only cleanup") {
      ZIO.attemptBlocking(assertTrue(mergeOuterInterruptedClose[Int](JvmType.Int)))
    },
    test("Long merge interrupted leading close completes outer close-hook-only cleanup") {
      ZIO.attemptBlocking(assertTrue(mergeOuterInterruptedClose[Long](JvmType.Long)))
    },
    test("Float merge interrupted leading close completes outer close-hook-only cleanup") {
      ZIO.attemptBlocking(assertTrue(mergeOuterInterruptedClose[Float](JvmType.Float)))
    },
    test("Double merge interrupted leading close completes outer close-hook-only cleanup") {
      ZIO.attemptBlocking(assertTrue(mergeOuterInterruptedClose[Double](JvmType.Double)))
    },
    test("generic merge interrupted leading close completes active inner close-hook-only cleanup") {
      ZIO.attemptBlocking(assertTrue(mergeInnerInterruptedClose[String](JvmType.AnyRef)))
    },
    test("Int merge interrupted leading close completes active inner close-hook-only cleanup") {
      ZIO.attemptBlocking(assertTrue(mergeInnerInterruptedClose[Int](JvmType.Int)))
    },
    test("Long merge interrupted leading close completes active inner close-hook-only cleanup") {
      ZIO.attemptBlocking(assertTrue(mergeInnerInterruptedClose[Long](JvmType.Long)))
    },
    test("Float merge interrupted leading close completes active inner close-hook-only cleanup") {
      ZIO.attemptBlocking(assertTrue(mergeInnerInterruptedClose[Float](JvmType.Float)))
    },
    test("Double merge interrupted leading close completes active inner close-hook-only cleanup") {
      ZIO.attemptBlocking(assertTrue(mergeInnerInterruptedClose[Double](JvmType.Double)))
    },
    test("early close releases an inner when its output queue is full") {
      ZIO.attemptBlocking {
        val queueFilled = new CountDownLatch(1)
        val innerClosed = new CountDownLatch(1)
        var closes      = 0
        val inner       = Stream.fromReader[Any, String](new Reader.SyncReader[String] {
          def isClosed: Boolean                    = false
          def read[A1 >: String](sentinel: A1): A1 = {
            queueFilled.countDown()
            "value".asInstanceOf[A1]
          }
          def close(): Unit = {
            closes += 1
            innerClosed.countDown()
          }
        })
        val reader = new ConcurrentMergeReader[String](Reader.fromChunk(Chunk(inner)), maxOpen = 1, bufferSize = 1)

        val filled = queueFilled.await(2, TimeUnit.SECONDS)
        reader.close()
        val released = innerClosed.await(2, TimeUnit.SECONDS)

        assertTrue(filled, released, closes == 1)
      }
    } @@ TestAspect.timeout(60.seconds),
    test("close wakes a blocked consumer with end-of-stream") {
      ZIO.attemptBlocking {
        val coordinatorEntered     = new CountDownLatch(1)
        val coordinatorInterrupted = new CountDownLatch(1)
        val releaseCoordinator     = new CountDownLatch(1)
        val readDone               = new CountDownLatch(1)
        val closeDone              = new CountDownLatch(1)
        val result                 = new AtomicReference[AnyRef](null)
        val readFailure            = new AtomicReference[Throwable](null)
        val outer                  = new Reader.SyncReader[Stream[Any, String]] {
          def isClosed: Boolean                                 = false
          def read[A1 >: Stream[Any, String]](sentinel: A1): A1 = {
            coordinatorEntered.countDown()
            try new CountDownLatch(1).await()
            catch { case _: InterruptedException => coordinatorInterrupted.countDown() }
            releaseCoordinator.await()
            sentinel
          }
          def close(): Unit = ()
        }
        val reader   = new ConcurrentMergeReader[String](outer, maxOpen = 1, bufferSize = Stream.DefaultBufferSize)
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
    } @@ TestAspect.timeout(60.seconds),
    test("close wakes a blocked primitive consumer with end-of-stream") {
      ZIO.attemptBlocking {
        val coordinatorEntered     = new CountDownLatch(1)
        val coordinatorInterrupted = new CountDownLatch(1)
        val releaseCoordinator     = new CountDownLatch(1)
        val readDone               = new CountDownLatch(1)
        val closeDone              = new CountDownLatch(1)
        val result                 = new AtomicReference[java.lang.Long](null)
        val readFailure            = new AtomicReference[Throwable](null)
        val outer                  = new Reader.SyncReader[Stream[Any, Int]] {
          def isClosed: Boolean                              = false
          def read[A1 >: Stream[Any, Int]](sentinel: A1): A1 = {
            coordinatorEntered.countDown()
            try new CountDownLatch(1).await()
            catch { case _: InterruptedException => coordinatorInterrupted.countDown() }
            releaseCoordinator.await()
            sentinel
          }
          def close(): Unit = ()
        }
        val reader   = Platform.createMergeReader[Int](outer, 1, Stream.DefaultBufferSize, JvmType.Int)
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
          Option(result.get()).exists(_.longValue() == sentinel),
          readFailure.get() == null
        )
      }
    } @@ TestAspect.timeout(60.seconds)
  )

  private def mergeStream[A](inners: Chunk[Stream[Any, A]], maxOpen: Int): Stream[Any, A] =
    Stream.fromReader[Any, A](
      new ConcurrentMergeReader[A](Reader.fromChunk(inners), maxOpen, bufferSize = Stream.DefaultBufferSize)
    )

  private def primitiveOuterCloseBarrier[A](jvmType: JvmType)(read: Reader.SyncReader[A] => Any): Boolean = {
    val closeEntered = new CountDownLatch(1)
    val releaseClose = new CountDownLatch(1)
    val readStarted  = new CountDownLatch(1)
    val readDone     = new CountDownLatch(1)
    val closeFailure = new RuntimeException(s"$jvmType outer close")
    val caught       = new AtomicReference[Throwable](null)
    val outer        = new Reader.SyncReader[Stream[Any, A]] {
      def isClosed: Boolean                            = false
      def read[A1 >: Stream[Any, A]](sentinel: A1): A1 = sentinel
      def close(): Unit                                = {
        closeEntered.countDown()
        releaseClose.await()
        throw closeFailure
      }
    }
    val reader   = Platform.createMergeReader[A](outer, 1, Stream.DefaultBufferSize, jvmType)
    val consumer = new Thread(new Runnable {
      def run(): Unit = {
        readStarted.countDown()
        try { read(reader); () }
        catch { case cause: Throwable => caught.set(cause) }
        finally readDone.countDown()
      }
    })
    consumer.setDaemon(true)

    val coordinatorBlocked = closeEntered.await(2, TimeUnit.SECONDS)
    consumer.start()
    val consumerStarted            = readStarted.await(2, TimeUnit.SECONDS)
    val completedWhileCloseBlocked = readDone.await(250, TimeUnit.MILLISECONDS)
    releaseClose.countDown()
    val failurePublished = readDone.await(2, TimeUnit.SECONDS)

    coordinatorBlocked && consumerStarted && !completedWhileCloseBlocked && failurePublished && (caught
      .get() eq closeFailure)
  }

  private def mergeOuterInterruptedClose[A](jvmType: JvmType): Boolean = {
    val closeFailure  = new RuntimeException(s"$jvmType merge outer close")
    val outer         = new ConcurrentCloseTestSupport.CloseHookReader[Stream[Any, A]](JvmType.AnyRef, closeFailure)
    val reader        = Platform.createMergeReader[A](outer, 1, Stream.DefaultBufferSize, jvmType)
    val workerEntered = outer.readEntered.await(2, TimeUnit.SECONDS)
    val result        = ConcurrentCloseTestSupport.raceClose(reader, outer)
    workerEntered && ConcurrentCloseTestSupport.correctFailure(result, closeFailure) && outer.closes.get() == 1
  }

  private def mergeInnerInterruptedClose[A](lane: JvmType): Boolean = {
    val closeFailure = new RuntimeException(s"$lane merge inner close")
    val closeHook    = new ConcurrentCloseTestSupport.CloseHookReader[A](JvmType.AnyRef, closeFailure)
    val innerReader  = new Reader.SyncReader[A] {
      override val jvmType: JvmType                                            = lane
      def isClosed: Boolean                                                    = closeHook.isClosed
      def read[A1 >: A](sentinel: A1): A1                                      = closeHook.read(sentinel)
      override def readBoolean(sentinel: Int)(implicit ev: A <:< Boolean): Int = {
        closeHook.read[Any](sentinel); sentinel
      }
      override def readByte(): Int                                                                     = { closeHook.read[Any](-1); -1 }
      override def readChar(sentinel: Int)(implicit ev: A <:< Char): Int                               = { closeHook.read[Any](sentinel); sentinel }
      override def readShort(sentinel: Int)(implicit ev: A <:< Short): Int                             = { closeHook.read[Any](sentinel); sentinel }
      override def readInt(sentinel: Long)(implicit ev: A <:< Int): Long                               = { closeHook.read[Any](sentinel); sentinel }
      override def readLong(sentinel: Long)(implicit ev: A <:< Long): Long                             = { closeHook.read[Any](sentinel); sentinel }
      override def readLongs(buf: Array[Long], offset: Int, length: Int)(implicit ev: A <:< Long): Int =
        closeHook.readLongs(buf, offset, length)(ev)
      override def readFloat(sentinel: Double)(implicit ev: A <:< Float): Double = {
        closeHook.read[Any](sentinel); sentinel
      }
      override def readDouble(sentinel: Double)(implicit ev: A <:< Double): Double = {
        closeHook.read[Any](sentinel); sentinel
      }
      override def readDoubles(buf: Array[Double], offset: Int, length: Int)(implicit ev: A <:< Double): Int =
        closeHook.readDoubles(buf, offset, length)(ev)
      def close(): Unit = closeHook.close()
    }
    val inner       = Stream.fromReader[Any, A](innerReader)
    val outerCloses = new AtomicInteger(0)
    val outer       = new Reader.SyncReader[Stream[Any, A]] {
      private var emitted                              = false
      def isClosed: Boolean                            = false
      def read[A1 >: Stream[Any, A]](sentinel: A1): A1 =
        if (!emitted) { emitted = true; inner }
        else sentinel
      def close(): Unit = { outerCloses.incrementAndGet(); () }
    }
    val reader         = Platform.createMergeReader[A](outer, 1, Stream.DefaultBufferSize, lane)
    val innerInstalled = closeHook.readEntered.await(2, TimeUnit.SECONDS)
    val result         = ConcurrentCloseTestSupport.raceClose(reader, closeHook)
    innerInstalled && ConcurrentCloseTestSupport.correctFailure(result, closeFailure) &&
    closeHook.closes.get() == 1 && outerCloses.get() == 1
  }
}
