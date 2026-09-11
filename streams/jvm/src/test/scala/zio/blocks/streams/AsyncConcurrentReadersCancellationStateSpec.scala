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
import java.util.concurrent.atomic.AtomicInteger

import zio.ZIO
import zio.blocks.async._
import zio.blocks.chunk.Chunk
import zio.blocks.streams.internal.AsyncConcurrentReaders
import zio.blocks.streams.io.Reader
import zio.test._

/**
 * State-machine tests for cancellation at concurrent-reader ownership
 * boundaries.
 */
object AsyncConcurrentReadersCancellationStateSpec extends ZIOSpecDefault {
  private implicit val executionContext: scala.concurrent.ExecutionContext =
    scala.concurrent.ExecutionContext.parasitic

  private val noop = new Runnable { def run(): Unit = () }

  private def runAsync[A](effect: Async[A]): ZIO[Any, Throwable, A] =
    ZIO.fromFuture(_ => effect.toFuture)

  private def source(values: Int*): Reader.AsyncReader[Int] =
    Reader.fromChunk(Chunk.fromIterable(values)).toAsync

  private final class GatedSource extends Reader.AsyncReader[Int] {
    val entered = new Completer[Unit]
    val value   = new Completer[Long]
    val closes  = new AtomicInteger

    override def jvmType: JvmType             = JvmType.Int
    def close(): Async[Unit]                  = { closes.incrementAndGet(); value.succeed(Long.MinValue); Async.succeed(()) }
    def isClosed: Async[Boolean]              = Async.succeed(closes.get != 0)
    def readable(): Async[Boolean]            = Async.succeed(closes.get == 0)
    def read[A >: Int](sentinel: A): Async[A] =
      readInt(Long.MinValue).map(v => if (v == Long.MinValue) sentinel else v.toInt.asInstanceOf[A])
    override def readInt(sentinel: Long)(implicit ev: Int <:< Int): Async[Long] = {
      entered.succeed(())
      value.map(v => if (v == Long.MinValue) sentinel else v)
    }
  }

  def spec = suite("AsyncConcurrentReaders cancellation state machine")(
    test("unstarted, active, and already completed pulls cancel idempotently") {
      val unstartedReader = AsyncConcurrentReaders.mapPar[Int, Int](source(1), 1, Async.succeed, JvmType.Int)
      val unstarted       = unstartedReader.readInt(-11L).asInstanceOf[Pollable[Long]]
      val gatedSource     = new GatedSource
      val activeReader    = AsyncConcurrentReaders.mapPar[Int, Int](gatedSource, 1, Async.succeed, JvmType.Int)
      val active          = activeReader.readInt(-12L).asInstanceOf[Pollable[Long]]
      active.poll(noop)
      val completedReader = AsyncConcurrentReaders.mapPar[Int, Int](source(13), 1, Async.succeed, JvmType.Int)
      val completed       = completedReader.readInt(-13L).start
      for {
        _  <- runAsync(Async.cancelWithCleanup(unstarted))
        _  <- runAsync(Async.cancelWithCleanup(unstarted))
        uv <- runAsync(unstarted)
        _  <- runAsync(gatedSource.entered)
        _  <- runAsync(Async.cancelWithCleanup(active))
        _  <- runAsync(Async.cancelWithCleanup(active))
        av <- runAsync(active)
        cv <- runAsync(completed)
        _  <- runAsync(Async.cancelWithCleanup(completed))
        _  <- runAsync(activeReader.close())
      } yield assertTrue(uv == -11L, av == -12L, cv == 13L, gatedSource.closes.get == 1)
    },
    test("completion and cancellation races publish exactly one legal outcome") {
      val rounds = 50
      ZIO
        .foreach(List.range(0, rounds)) { i =>
          val callbackEntered = new Completer[Unit]
          val callbackValue   = new Completer[Int]
          val reader          = AsyncConcurrentReaders.mapPar[Int, Int](
            source(i),
            1,
            value => { callbackEntered.succeed(()); callbackValue.map(_ => value) },
            JvmType.Int
          )
          val pull    = reader.readInt(-1L).asInstanceOf[Pollable[Long]]
          val running = pull.start
          for {
            _     <- runAsync(callbackEntered)
            gate   = new CountDownLatch(1)
            cancel = ZIO.attemptBlocking {
                       gate.await(5, TimeUnit.SECONDS)
                       Async.cancelWithCleanup(pull)
                     }.flatMap(runAsync)
            cf <- cancel.fork
            _   = gate.countDown()
            _   = callbackValue.succeed(i)
            _  <- cf.join
            v  <- runAsync(running)
            _  <- runAsync(reader.close())
            c  <- runAsync(reader.isClosed)
            r  <- runAsync(reader.readable())
          } yield assertTrue((v == i.toLong) || (v == -1L), c, !r)
        }
        .map(results => results.reduce(_ && _))
    }
  )
}
