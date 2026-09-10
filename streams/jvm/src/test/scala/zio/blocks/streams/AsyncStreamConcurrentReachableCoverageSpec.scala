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

import zio.blocks.async._
import zio.blocks.chunk.Chunk
import zio.blocks.scope.Resource
import zio.blocks.streams.internal.AsyncConcurrentReaders
import zio.blocks.streams.io.Reader
import zio.test._

/**
 * Deterministic witnesses for reachable concurrent stream state-machine arms.
 */
object AsyncStreamConcurrentReachableCoverageSpec extends StreamsBaseSpec {
  private def async[A: JvmType.Infer](values: A*): Reader.AsyncReader[A] =
    Reader.fromChunk(Chunk.fromIterable(values)).toAsync

  private class FailingClose[A: JvmType.Infer](values: A*)(failure: Throwable) extends Reader.AsyncReader[A] {
    private val delegate                                                 = async(values: _*)
    override val jvmType                                                 = implicitly[JvmType.Infer[A]].jvmType
    def isClosed                                                         = delegate.isClosed
    def readable()                                                       = delegate.readable()
    def read[B >: A](sentinel: B)                                        = delegate.read(sentinel)
    override def readBoolean(sentinel: Int)(implicit ev: A <:< Boolean)  = delegate.readBoolean(sentinel)
    override def readByte()                                              = delegate.readByte()
    override def readChar(sentinel: Int)(implicit ev: A <:< Char)        = delegate.readChar(sentinel)
    override def readShort(sentinel: Int)(implicit ev: A <:< Short)      = delegate.readShort(sentinel)
    override def readInt(sentinel: Long)(implicit ev: A <:< Int)         = delegate.readInt(sentinel)
    override def readLong(sentinel: Long)(implicit ev: A <:< Long)       = delegate.readLong(sentinel)
    override def readFloat(sentinel: Double)(implicit ev: A <:< Float)   = delegate.readFloat(sentinel)
    override def readDouble(sentinel: Double)(implicit ev: A <:< Double) = delegate.readDouble(sentinel)
    def close()                                                          = delegate.close().flatMap(_ => Async.fail(failure))
  }

  def spec = suite("reachable async Stream/concurrent-reader decisions")(
    test("fromResource with unwrap closes a partially acquired scope and aggregates scope-close failures") {
      val acquire  = new RuntimeException("acquire")
      val close1   = new RuntimeException("close-1")
      val close2   = new RuntimeException("close-2")
      val resource = Resource
        .acquireRelease(1)(_ => throw close1)
        .flatMap(_ => Resource.acquireRelease(2)(_ => throw close2))
        .flatMap(_ => Resource[Int](throw acquire))
      runAsync(Stream.fromResource(resource)(_ => Stream.unwrap(Async.succeed(Stream.empty))).runDrainAsync.either).map(
        result => assertTrue(result == Left(acquire), acquire.getSuppressed.toSet == Set[Throwable](close2, close1))
      )
    },
    test("fromResource with unwrap reports every normal scope-close failure") {
      val close1   = new RuntimeException("close-1")
      val close2   = new RuntimeException("close-2")
      val resource = Resource
        .acquireRelease(1)(_ => throw close1)
        .flatMap(_ => Resource.acquireRelease(2)(_ => throw close2))
      runAsync(Stream.fromResource(resource)(n => Stream.unwrap(Async.succeed(Stream(n)))).runDrainAsync.either).map {
        result =>
          val failure = result.swap.toOption.get
          assertTrue(Set[Throwable](failure) ++ failure.getSuppressed == Set[Throwable](close1, close2))
      }
    },
    test("zip records first- and second-lane failures and joins both failing closes") {
      val pull       = new RuntimeException("pull")
      val leftClose  = new RuntimeException("left-close")
      val rightClose = new RuntimeException("right-close")
      val left       = new FailingClose[Any]()(leftClose) {
        override def read[B >: Any](sentinel: B): Async[B] = Async.fail(pull)
      }
      val right  = new FailingClose[Int](1)(rightClose)
      val reader = Stream
        .compileToReader(Stream.fromReader[Nothing, Any](left) && Stream.fromReader[Nothing, Int](right))
        .expectedAsync
      runAsync(reader.read[Any]("eof").either).map(result =>
        assertTrue(
          result == Left(pull),
          pull.getSuppressed.toList == List(leftClose),
          leftClose.getSuppressed.toList == List(rightClose)
        )
      )
    },
    test("zip cancellation makes a late completion stale") {
      val gate = new Completer[Any]
      val left = new Reader.AsyncReader[Any] {
        override val jvmType                      = JvmType.AnyRef
        def isClosed                              = Async.succeed(false)
        def readable()                            = Async.succeed(true)
        def close()                               = Async.succeed(())
        def read[B >: Any](sentinel: B): Async[B] = gate
      }
      val reader = Stream.compileToReader(Stream.fromReader[Nothing, Any](left) && Stream(1)).expectedAsync
      val pull   = reader.read[Any]("eof").start
      for {
        _ <- runAsync(reader.close())
        _  = gate.fail(new RuntimeException("stale"))
        v <- runAsync(pull)
      } yield assertTrue(v == null)
    },
    test("zip closes operations rejected at admission and makes concurrent failure stale") {
      type Zip = Stream.Zipped[Nothing, Any, Int, Any]
      val admission = (Stream.fromReader[Nothing, Any](async[Any](1)) && Stream(2)).asInstanceOf[Zip]
      admission.closeBeforeAsyncAdmissionForTest()
      val admissionReader = Stream.compileToReader(admission).expectedAsync.asInstanceOf[Reader.AsyncReader[Any]]

      val failure      = new RuntimeException("stale")
      val failedSource = new Reader.AsyncReader[Any] {
        override val jvmType                      = JvmType.AnyRef
        def isClosed                              = Async.succeed(false)
        def readable()                            = Async.succeed(true)
        def close()                               = Async.succeed(())
        def read[B >: Any](sentinel: B): Async[B] = Async.fail(failure)
      }
      val failing = (Stream.fromReader[Nothing, Any](failedSource) && Stream(2)).asInstanceOf[Zip]
      failing.closeBeforeAsyncFailureForTest()
      val failureReader = Stream.compileToReader(failing).expectedAsync.asInstanceOf[Reader.AsyncReader[Any]]
      for {
        rejected <- runAsync(admissionReader.read[Any]("eof"))
        stale    <- runAsync(failureReader.read[Any]("eof"))
        _        <- runAsync(admissionReader.close())
        _        <- runAsync(failureReader.close())
      } yield assertTrue(
        rejected == null,
        stale == null,
        admissionReader.isClosed.block,
        failureReader.isClosed.block
      )
    },
    test("lifecycle callback failures aggregate and reentrant close remains nonblocking") {
      val sourceClose                    = new RuntimeException("source-close")
      val callback                       = new RuntimeException("callback")
      val calls                          = new AtomicInteger
      var reader: Reader.SyncReader[Int] = null
      val source                         = new Reader.SyncReader[Int] {
        def isClosed                    = false
        def read[B >: Int](sentinel: B) = sentinel
        def close()                     = { reader.close(); throw sourceClose }
      }
      reader = Stream
        .compileToReader(Stream.fromReader[Nothing, Int](source).ensuring {
          calls.incrementAndGet(); throw callback
        })
        .expectedSync
      val result = scala.util.Try(reader.close()).failed.toOption
      assertTrue(result.contains(sourceClose), sourceClose.getSuppressed.toList == List(callback), calls.get == 1)
    },
    test("forced async unary and primitive Collected paths preserve order") {
      val stream = Stream
        .fromReader[Nothing, Int](async(1, 2, 3))
        .mapAsync(i => Async.reschedule(() => Async.succeed(i + 1)))
        .collect { case i if i != 3 => i.toLong }
      runAsync(stream.runCollectAsync).map(result => assertTrue(result == Right(Chunk(2L, 4L))))
    },
    test("mapPar catches effect construction and combines selector/upstream cleanup failures") {
      val construction = new RuntimeException("construction")
      val cleanup      = new RuntimeException("cleanup")
      val reader       = AsyncConcurrentReaders
        .mapPar[Int, Int](new FailingClose[Int](1)(cleanup), 1, _ => throw construction, JvmType.Int)
      runAsync(reader.readInt(-1L).either).map(result =>
        assertTrue(result == Left(construction), construction.getSuppressed.toList == List(cleanup))
      )
    },
    test("concurrent reader close rejects a stale bulk commit and terminal finish") {
      val bytes = AsyncConcurrentReaders.mapPar[Byte, Byte](async[Byte](1), 1, Async.succeed, JvmType.Byte)
      val dest  = new Array[Byte](1)
      AsyncConcurrentReaders.invalidateBeforeCommitForTest(bytes)
      val empty = AsyncConcurrentReaders.mapPar[Int, Int](async[Int](), 1, Async.succeed, JvmType.Int)
      AsyncConcurrentReaders.invalidateBeforeFinishForTest(empty)
      for {
        count <- runAsync(bytes.readBytes(dest, 0, 1))
        value <- runAsync(empty.readInt(-9L))
        _     <- runAsync(bytes.close())
        _     <- runAsync(empty.close())
      } yield assertTrue(count == -1, dest(0) == 0, value == -9L)
    },
    test("concurrent reader cancellation joins an in-flight handoff") {
      val callbackStarted = new Completer[Unit]
      val callbackResult  = new Completer[Int]
      val reader          = AsyncConcurrentReaders.mapPar[Int, Int](
        async(1),
        1,
        _ => { callbackStarted.succeed(()); callbackResult },
        JvmType.Int
      )
      var pull: Pollable[Long]        = null
      var first: Async.Running[Unit]  = null
      var second: Async.Running[Unit] = null
      pull = reader.readInt(-7L).asInstanceOf[Pollable[Long]]
      AsyncConcurrentReaders.afterBeginHandoffForTest(reader) { () =>
        first = Async.cancelWithCleanup(pull).start
        second = Async.cancelWithCleanup(pull).start
        val deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5)
        while (!AsyncConcurrentReaders.handoffCancellationClaimedForTest(reader) && System.nanoTime() < deadline)
          Thread.onSpinWait()
      }
      val running = pull.start
      for {
        _     <- runAsync(callbackStarted)
        _      = callbackResult.succeed(1)
        value <- runAsync(running)
        _     <- runAsync(first)
        _     <- runAsync(second)
        _     <- runAsync(reader.close())
      } yield assertTrue(value == -7L)
    },
    test("concurrent reader cancellation before child publication cancels the child") {
      val gate                        = new Completer[Int]
      val reader                      = AsyncConcurrentReaders.mapPar[Int, Int](async(1), 1, _ => gate, JvmType.Int)
      val pull                        = reader.readInt(-5L).asInstanceOf[Pollable[Long]]
      var cancel: Async.Running[Unit] = null
      AsyncConcurrentReaders.beforeRegistrationForTest(reader)(() => cancel = Async.cancelWithCleanup(pull).start)
      val running = pull.start
      for {
        _     <- runAsync(cancel)
        value <- runAsync(running)
        _     <- runAsync(reader.close())
      } yield assertTrue(value == -5L)
    }
  ) @@ TestAspect.sequential
}
