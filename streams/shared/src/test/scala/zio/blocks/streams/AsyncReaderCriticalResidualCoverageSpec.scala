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
import zio.blocks.streams.internal.{AsyncConcurrentReaders, AsyncStatefulReader}
import zio.blocks.streams.io.Reader
import zio.test._

/** Deterministic lifecycle and transition coverage for reader residuals. */
object AsyncReaderCriticalResidualCoverageSpec extends StreamsBaseSpec {
  private def source[A: JvmType.Infer](values: A*): Reader.AsyncReader[A] =
    Reader.fromChunk(Chunk.fromIterable(values)).toAsync

  private final class CancellableIntSource(cancelResult: Async[Unit]) extends Reader.AsyncReader[Int] {
    val entered = new Completer[Unit]

    override def jvmType                                                        = JvmType.Int
    def close()                                                                 = Async.succeed(())
    def isClosed                                                                = Async.succeed(false)
    def readable()                                                              = Async.succeed(true)
    def read[A >: Int](sentinel: A)                                             = readInt(Long.MinValue).map(v => if (v == Long.MinValue) sentinel else v.toInt)
    override def readInt(sentinel: Long)(implicit ev: Int <:< Int): Async[Long] = new Async.Operation[Long] {
      def poll(onComplete: Runnable): Async[Long]  = { entered.succeed(()); this }
      protected def cancelOperation(): Async[Unit] = cancelResult
    }
  }

  private final class EmptyIntSource(closeResult: Async[Unit], readResult: Async[Long])
      extends Reader.AsyncReader[Int] {
    val closeEntered = new Completer[Unit]

    override def jvmType                                                        = JvmType.Int
    def close()                                                                 = { closeEntered.succeed(()); closeResult }
    def isClosed                                                                = Async.succeed(false)
    def readable()                                                              = Async.succeed(false)
    def read[A >: Int](sentinel: A)                                             = readInt(Long.MinValue).map(v => if (v == Long.MinValue) sentinel else v.toInt)
    override def readInt(sentinel: Long)(implicit ev: Int <:< Int): Async[Long] = readResult
  }

  def spec = suite("async reader critical residual coverage")(
    test("stateful boundaries cover chunk and sliding terminal transitions") {
      val chunks      = AsyncStatefulReader.chunked(source(1, 2, 3), 2)
      val overlapping = AsyncStatefulReader.sliding(source(1, 2, 3, 4), 3, 1)
      val gapped      = AsyncStatefulReader.sliding(source(1, 2, 3, 4, 5), 2, 3)
      val partial     = AsyncStatefulReader.sliding(source(1, 2), 3, 1)
      for {
        cv <- runAsync(chunks.readAll[Chunk[Int]]())
        ov <- runAsync(overlapping.readAll[Chunk[Int]]())
        gv <- runAsync(gapped.readAll[Chunk[Int]]())
        pv <- runAsync(partial.readAll[Chunk[Int]]())
        cr <- runAsync(chunks.readable())
        or <- runAsync(overlapping.readable())
        _  <- runAsync(chunks.close())
        _  <- runAsync(overlapping.close())
      } yield assertTrue(
        cv == Chunk(Chunk(1, 2), Chunk(3)),
        ov == Chunk(Chunk(1, 2, 3), Chunk(2, 3, 4)),
        gv == Chunk(Chunk(1, 2), Chunk(4, 5)),
        pv == Chunk(Chunk(1, 2)),
        !cr,
        !or
      )
    },
    test("stateful close cancels pending readable and rejects its stale callback") {
      val entered = new Completer[Unit]
      val ready   = new Completer[Boolean]
      val closes  = new AtomicInteger
      val inner   = new Reader.AsyncReader[String] {
        def close()                        = { closes.incrementAndGet(); ready.succeed(true); Async.succeed(()) }
        def isClosed                       = Async.succeed(closes.get != 0)
        def readable()                     = { entered.succeed(()); ready }
        def read[A >: String](sentinel: A) = Async.succeed(sentinel)
      }
      val reader  = AsyncStatefulReader.buffered(inner, 2)
      val pending = reader.readable().start
      for {
        _      <- runAsync(entered)
        _      <- runAsync(reader.close())
        result <- runAsync(pending)
        closed <- runAsync(reader.isClosed)
      } yield assertTrue(!result, closed, closes.get == 1)
    },
    test("repeated crosses its empty-reset budget and then publishes a value") {
      val resets = new AtomicInteger
      val inner  = new Reader.AsyncReader[Int] {
        override def jvmType            = JvmType.Int
        def close()                     = Async.succeed(())
        override def reset()            = { resets.incrementAndGet(); Async.succeed(()) }
        def isClosed                    = Async.succeed(true)
        def readable()                  = Async.succeed(true)
        def read[A >: Int](sentinel: A) =
          if (resets.get >= 257) Async.succeed(9.asInstanceOf[A]) else Async.succeed(sentinel)
        override def readInt(sentinel: Long)(implicit ev: Int <:< Int): Async[Long] =
          if (resets.get >= 257) Async.succeed(9L) else Async.succeed(sentinel)
      }
      val repeated = Reader.repeated(inner)
      for {
        value <- runAsync(repeated.read[Int](-1))
        _     <- runAsync(repeated.close())
        end   <- runAsync(repeated.read[Int](-2))
      } yield assertTrue(value == 9, resets.get == 257, end == -2)
    },
    test("repeated close owns a pending readable reservation and rejects its stale result") {
      val entered = new Completer[Unit]
      val ready   = new Completer[Boolean]
      val closes  = new AtomicInteger
      val inner   = new Reader.AsyncReader[Int] {
        override def jvmType                                                        = JvmType.Int
        def close()                                                                 = { closes.incrementAndGet(); ready.succeed(true); Async.succeed(()) }
        def isClosed                                                                = Async.succeed(closes.get != 0)
        def readable()                                                              = { entered.succeed(()); ready }
        def read[A >: Int](sentinel: A): Async[A]                                   = Async.succeed(sentinel)
        override def readInt(sentinel: Long)(implicit ev: Int <:< Int): Async[Long] = Async.succeed(sentinel)
      }
      val repeated = Reader.repeated(inner)
      val pending  = repeated.readable().start
      for {
        _      <- runAsync(entered)
        _      <- runAsync(repeated.close())
        result <- runAsync(pending)
        closed <- runAsync(repeated.isClosed)
      } yield assertTrue(!result, closed, closes.get == 1)
    },
    test("unfold cancellation before polling, EOF reset, and close are stable") {
      val calls  = new AtomicInteger
      val reader = Reader.unfoldAsync[Int, Int](0) { state =>
        calls.incrementAndGet()
        Async.succeed(if (state == 0) Some((1, 1)) else None)
      }
      val unpolled = reader.read[Int](-1)
      for {
        _      <- runAsync(Async.cancelWithCleanup(unpolled.asInstanceOf[Pollable[Int]]))
        value  <- runAsync(reader.read[Int](-2))
        eof    <- runAsync(reader.read[Int](-3))
        _      <- runAsync(reader.reset())
        replay <- runAsync(reader.read[Int](-4))
        _      <- runAsync(reader.close())
        closed <- runAsync(reader.read[Int](-5))
      } yield assertTrue(value == 1, eof == -3, replay == 1, closed == -5, calls.get == 3)
    },
    test("concurrent pull cancellation claims a pending source and publishes its sentinel") {
      val input       = new CancellableIntSource(Async.succeed(()))
      val reader      = AsyncConcurrentReaders.mapPar[Int, Int](input, 1, Async.succeed, JvmType.Int)
      val operation   = reader.readInt(-11L).asInstanceOf[Pollable[Long]]
      val runningPull = operation.start
      for {
        _       <- runAsync(input.entered)
        cleanup <- runAsync(Async.cancelWithCleanup(operation)).either
        value   <- runAsync(runningPull)
      } yield assertTrue(cleanup == Right(()), value == -11L)
    },
    test("concurrent pull cancellation joins terminal finish and failure publication") {
      val finishGate     = new Completer[Unit]
      val failureGate    = new Completer[Unit]
      val readFailure    = new IllegalStateException("read")
      val finishingInput = new EmptyIntSource(finishGate, Async.succeed(Long.MinValue))
      val failingInput   = new EmptyIntSource(failureGate, Async.fail(readFailure))
      val finishing      = AsyncConcurrentReaders.mapPar[Int, Int](finishingInput, 1, Async.succeed, JvmType.Int)
      val failing        = AsyncConcurrentReaders.mapPar[Int, Int](failingInput, 1, Async.succeed, JvmType.Int)
      val finishOp       = finishing.readInt(-21L).asInstanceOf[Pollable[Long]]
      val failureOp      = failing.readInt(-22L).asInstanceOf[Pollable[Long]]
      val finishPull     = finishOp.start
      val failurePull    = failureOp.start
      for {
        _              <- runAsync(finishingInput.closeEntered)
        finishCancel   <- runAsync(Async.cancelWithCleanup(finishOp)).fork
        _               = finishGate.succeed(())
        finishCleanup  <- finishCancel.join
        finishValue    <- runAsync(finishPull)
        _              <- runAsync(failingInput.closeEntered)
        failureCancel  <- runAsync(Async.cancelWithCleanup(failureOp)).either.fork
        _               = failureGate.succeed(())
        failureCleanup <- failureCancel.join
        failureValue   <- runAsync(failurePull).either
      } yield assertTrue(
        finishCleanup == (),
        finishValue == -21L,
        failureCleanup.left.exists(_ eq readFailure),
        failureValue.left.exists(_ eq readFailure)
      )
    }
  ) @@ TestAspect.sequential
}
