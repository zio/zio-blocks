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
import zio.blocks.streams.internal.AsyncInterpreter
import zio.blocks.streams.io.Reader
import zio.test._

object AsyncInterpreterCoverageSpec extends StreamsBaseSpec {
  private final class Pending[A] extends Async.Operation[A] {
    val entered                                  = new Completer[Unit]
    val cancellations                            = new AtomicInteger(0)
    def poll(onComplete: Runnable): Async[A]     = { entered.succeed(()); this }
    protected def cancelOperation(): Async[Unit] = {
      cancellations.incrementAndGet()
      Async.succeed(())
    }
  }

  private class IntSource(values0: List[Long], closeEffect: () => Async[Unit]) extends Reader.AsyncReader[Any] {
    private var values                                                          = values0
    val reads                                                                   = new AtomicInteger(0)
    val closes                                                                  = new AtomicInteger(0)
    override def jvmType: JvmType                                               = JvmType.Int
    def isClosed: Async[Boolean]                                                = Async.succeed(false)
    def readable(): Async[Boolean]                                              = Async.succeed(values.nonEmpty)
    def close(): Async[Unit]                                                    = { closes.incrementAndGet(); closeEffect() }
    def read[A >: Any](sentinel: A): Async[A]                                   = Async.succeed(sentinel)
    override def readInt(sentinel: Long)(implicit ev: Any <:< Int): Async[Long] = {
      reads.incrementAndGet()
      values match {
        case head :: tail => values = tail; Async.succeed(head)
        case Nil          => Async.succeed(Long.MinValue)
      }
    }
  }

  def spec = suite("AsyncInterpreter coverage")(
    test("cancelling a pending root read joins child cleanup and permits a fresh read") {
      val pending = new Pending[Long]
      val calls   = new AtomicInteger(0)
      val source  = new IntSource(Nil, () => Async.succeed(())) {
        override def readInt(sentinel: Long)(implicit ev: Any <:< Int): Async[Long] =
          if (calls.getAndIncrement() == 0) pending else Async.succeed(42L)
      }
      val interpreter = new AsyncInterpreter(source)
      val operation   = interpreter.readInt(-1L)
      for {
        fiber <- runAsync(operation).fork
        _     <- runAsync(pending.entered)
        _     <- runAsync(Async.cancelWithCleanup(operation.asInstanceOf[Pollable[Long]]))
        _     <- fiber.join
        next  <- runAsync(interpreter.readInt(-2L))
      } yield assertTrue(pending.cancellations.get() == 1, next == 42L, calls.get() == 2)
    },
    test("nested EOF closes the inner owner and resumes the outer scalar lane") {
      val outer       = new IntSource(List(9L), () => Async.succeed(()))
      val inner       = new IntSource(Nil, () => Async.succeed(()))
      val interpreter = new AsyncInterpreter(outer)
      interpreter.installNestedFrameForTest(inner, JvmType.Int)
      for {
        value <- runAsync(interpreter.readInt(-1L))
        eof   <- runAsync(interpreter.readInt(-7L))
      } yield assertTrue(value == 9L, eof == -7L, inner.closes.get() == 1, outer.closes.get() == 0)
    },
    test("terminal close runs finalizers once and suppresses their failure behind the primary") {
      val primary    = new RuntimeException("read")
      val cleanup    = new RuntimeException("cleanup")
      val finalizers = new AtomicInteger(0)
      val source     = new IntSource(Nil, () => Async.succeed(())) {
        override def readInt(sentinel: Long)(implicit ev: Any <:< Int): Async[Long] = Async.fail(primary)
      }
      val interpreter = new AsyncInterpreter(source)
      interpreter.addAsyncFinalizer { () => finalizers.incrementAndGet(); Async.fail(cleanup) }
      for {
        _      <- runAsync(interpreter.readInt(-1L).either)
        first  <- runAsync(interpreter.closeForTest().either)
        second <- runAsync(interpreter.closeForTest().either)
      } yield assertTrue(
        first == Left(primary),
        second == Left(primary),
        finalizers.get() == 1,
        primary.getSuppressed.toList == List(cleanup)
      )
    },
    test("failed transform materialization closes its installed source before replaying the defect") {
      val primary     = new RuntimeException("configure")
      val closes      = new AtomicInteger(0)
      val source      = new IntSource(List(1L), () => { closes.incrementAndGet(); Async.succeed(()) })
      val interpreter = AsyncInterpreter.transform(source)(_ => throw primary)
      for {
        failure <- runAsync(interpreter.readInt(-1L).either)
        closed  <- runAsync(interpreter.isClosed)
      } yield assertTrue(failure == Left(primary), closes.get() == 1, closed)
    }
  )
}
