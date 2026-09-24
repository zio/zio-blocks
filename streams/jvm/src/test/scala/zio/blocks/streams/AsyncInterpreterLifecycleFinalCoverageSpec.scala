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

import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger

import zio.ZIO
import zio.blocks.async._
import zio.blocks.streams.internal.{AsyncInterpreter, StreamError}
import zio.blocks.streams.io.Reader
import zio.test._

/** JVM-only lifecycle races whose synchronous reentrancy is intentional. */
object AsyncInterpreterLifecycleFinalCoverageSpec extends StreamsBaseSpec {
  private val noop = new Runnable { def run(): Unit = () }

  private final class Gate[A](cancelFailure: Throwable = null) extends Async.Operation[A] {
    private val result                           = new Completer[A]
    val polls                                    = new AtomicInteger
    val cancels                                  = new AtomicInteger
    def poll(wake: Runnable): Async[A]           = { polls.incrementAndGet(); result.poll(wake) }
    protected def cancelOperation(): Async[Unit] = {
      cancels.incrementAndGet()
      if (cancelFailure eq null) Async.succeed(()) else Async.fail(cancelFailure)
    }
    def succeed(value: A): Unit      = result.succeed(value)
    def fail(cause: Throwable): Unit = result.fail(cause)
  }

  private final class Replace[A](next: => Async[A], wake: Boolean = false) extends Pollable[A] {
    val polls                                = new AtomicInteger
    def poll(onComplete: Runnable): Async[A] = {
      polls.incrementAndGet()
      if (wake) { onComplete.run(); onComplete.run() }
      next
    }
  }

  private class Source(
    readEffect: () => Async[Any] = () => Async.succeed("value"),
    closeEffect: () => Async[Unit] = () => Async.succeed(())
  ) extends Reader.AsyncReader[Any] {
    val closes                                = new AtomicInteger
    def read[A >: Any](sentinel: A): Async[A] = readEffect().asInstanceOf[Async[A]]
    def readable(): Async[Boolean]            = Async.succeed(true)
    def isClosed: Async[Boolean]              = Async.succeed(false)
    def close(): Async[Unit]                  = { closes.incrementAndGet(); closeEffect() }
  }

  private def awaitPoll(gate: Gate[_]): ZIO[Any, Nothing, Unit] =
    if (gate.polls.get() > 0) ZIO.unit else ZIO.yieldNow *> ZIO.suspendSucceed(awaitPoll(gate))

  private def typed(source: Source)(handler: String => Async[Stream[_, Any]]): AsyncInterpreter = {
    val interpreter = new AsyncInterpreter(source)
    interpreter.addAsyncCatchAll[String](handler)
    interpreter
  }

  def spec = suite("AsyncInterpreter lifecycle final coverage")(
    test("typed and defect rejection, null recovery, and throwing handler construction are sticky") {
      val trusted = StreamError.source("typed")
      val thrown  = new RuntimeException("handler-construction")
      val reject  = new AsyncInterpreter(new Source(() => Async.failTrusted(trusted)))
      reject.addAsyncCatchDefect { case _ => Async.succeed(Some(Stream.empty)) }
      val nullRecovery = typed(new Source(() => Async.failTrusted(trusted)))(_ => Async.succeed(null))
      val construction = typed(new Source(() => Async.failTrusted(trusted)))(_ => throw thrown)
      for {
        a <- runAsync(reject.read[Any]("eof").either)
        b <- runAsync(nullRecovery.read[Any]("eof").either)
        c <- runAsync(construction.read[Any]("eof").either)
        d <- runAsync(construction.read[Any]("again").either)
      } yield assertTrue(
        a == Left(trusted),
        b.left.exists(_.isInstanceOf[NullPointerException]),
        c == Left(thrown),
        d == c
      )
    },
    test("recovery close owner is joined, ignores an ignorable replay, and rejects stale completion") {
      val trigger = StreamError.source("typed")
      val close   = new Gate[Unit]
      val handler = new Gate[Stream[_, Any]]
      val source  = new Source(() => Async.failTrusted(trigger), () => close)
      val i       = typed(source)(_ => handler)
      val pull    = i.read[Any]("cancelled")
      pull.asInstanceOf[Pollable[Any]].poll(noop)
      val cancel = Async.cancelWithCleanup(pull.asInstanceOf[Pollable[Any]])
      close.succeed(())
      handler.succeed(Stream.succeed("stale": Any))
      for {
        _    <- runAsync(cancel)
        next <- runAsync(i.read[Any]("eof"))
      } yield assertTrue(next == "eof", source.closes.get() == 1, close.cancels.get() == 0, handler.polls.get() == 0)
    },
    test("failed recovery owner close publishes the primary and joins cancellation") {
      val trigger = StreamError.source("typed")
      val cleanup = new RuntimeException("owner-close")
      val close   = new Gate[Unit]
      val i       = typed(new Source(() => Async.failTrusted(trigger), () => close))(_ => Async.succeed(Stream.empty))
      val pull    = i.read[Any]("cancelled")
      pull.asInstanceOf[Pollable[Any]].poll(noop)
      val cancel = Async.cancelWithCleanup(pull.asInstanceOf[Pollable[Any]])
      close.fail(cleanup)
      for {
        cancelled <- runAsync(cancel.either)
        result    <- runAsync(pull.either)
      } yield assertTrue(
        cancelled == Left(trigger),
        result == Left(trigger),
        trigger.getSuppressed.toList == List(cleanup)
      )
    },
    test("cancellation replacement and stale resume cannot publish after cancellation") {
      val gate        = new Gate[Any]
      val replacement = new Replace[Any](gate, wake = true)
      val source      = new Source(() => replacement)
      val i           = new AsyncInterpreter(source)
      val pull        = i.read[Any]("cancelled")
      pull.asInstanceOf[Pollable[Any]].poll(noop)
      val cancel = Async.cancelWithCleanup(pull.asInstanceOf[Pollable[Any]])
      gate.succeed("stale")
      for {
        _ <- runAsync(cancel)
        r <- runAsync(pull)
      } yield assertTrue(r == "cancelled", replacement.polls.get() == 1, gate.cancels.get() == 0)
    },
    test("cancellation before registration and after child registration publishes the cancellation sentinel") {
      val failure = new RuntimeException("cancel")
      val freshI  = new AsyncInterpreter(new Source())
      val fresh   = freshI.read[Any]("fresh-cancel")
      val gate    = new Gate[Any](failure)
      val failedI = new AsyncInterpreter(new Source(() => gate))
      val pending = failedI.read[Any]("cancelled")
      pending.asInstanceOf[Pollable[Any]].poll(noop)
      for {
        a <- runAsync(Async.cancelWithCleanup(fresh.asInstanceOf[Pollable[Any]]).either)
        b <- runAsync(Async.cancelWithCleanup(pending.asInstanceOf[Pollable[Any]]).either)
        _  = gate.fail(failure)
        c <- runAsync(pending.either)
        d <- runAsync(failedI.read[Any]("again").either)
      } yield assertTrue(a == Right(()), b == Right(()), c == Right("cancelled"), d == Left(failure))
    },
    test("readers-close coalesces wakes and cancellation only joins scheduling") {
      val gate        = new Gate[Unit]
      val replacement = new Replace[Unit](gate, wake = true)
      val first       = new Source(closeEffect = () => replacement)
      val second      = new Source()
      val i           = new AsyncInterpreter(new Source())
      val close       = i.closeReadersForTest(first, second)
      close.asInstanceOf[Pollable[Unit]].poll(noop)
      val cancel = Async.cancelWithCleanup(close.asInstanceOf[Pollable[Unit]])
      gate.succeed(())
      for {
        _ <- runAsync(cancel)
        _ <- runAsync(close)
      } yield assertTrue(
        first.closes.get() == 1,
        second.closes.get() == 1,
        replacement.polls.get() == 1,
        gate.cancels.get() == 0
      )
    },
    test("readers-close caller claims a pending wake before scheduling") {
      val gate        = new Gate[Unit]
      val reader      = new Source(closeEffect = () => gate)
      val interpreter = new AsyncInterpreter(new Source())
      val close       = interpreter.closeReadersForTest(reader)
      val _           = close.asInstanceOf[Pollable[Unit]].poll(noop)
      interpreter.wakeReadersCloseForTest(close)
      interpreter.wakeReadersCloseForTest(Async.succeed(()))
      val _ = close.asInstanceOf[Pollable[Unit]].poll(noop)
      gate.succeed(())
      close.block
      assertTrue(reader.closes.get() == 1, gate.polls.get() == 1)
    },
    test("root and owned close are reentrant, aliases join, and do not cancel their owner") {
      val gate                          = new Gate[Unit]
      val nested                        = new AtomicInteger
      var interpreter: AsyncInterpreter = null
      val source                        = new Source(closeEffect = () => {
        nested.incrementAndGet()
        interpreter.closeForTest().asInstanceOf[Pollable[Unit]].poll(noop)
        gate
      })
      interpreter = new AsyncInterpreter(source)
      val close = interpreter.closeForTest()
      for {
        one <- runAsync(close).fork
        two <- runAsync(interpreter.rootCloseForTest()).fork
        _   <- awaitPoll(gate)
        _    = gate.succeed(())
        _   <- one.join
        _   <- two.join
      } yield assertTrue(source.closes.get() == 1, nested.get() == 1, gate.cancels.get() == 0)
    },
    test("root close is reentrant from root-reader close construction") {
      var interpreter: AsyncInterpreter = null
      val source                        = new Source(closeEffect = () => {
        interpreter.rootCloseForTest().block
        Async.succeed(())
      })
      interpreter = new AsyncInterpreter(source)
      interpreter.rootCloseForTest().block
      assertTrue(source.closes.get() == 1)
    },
    test("control operations reject admission while owned close is pending") {
      val gate        = new Gate[Unit]
      val interpreter = new AsyncInterpreter(new Source(closeEffect = () => gate))
      val closing     = interpreter.closeForTest().start
      for {
        _        <- awaitPoll(gate)
        rejected <- runAsync(interpreter.setLimit(1).either)
        _         = gate.succeed(())
        _        <- runAsync(closing)
      } yield assertTrue(rejected.left.exists(_.isInstanceOf[IOException]), interpreter.isClosed.block)
    },
    test("control construction reentrant close abandons it and close aliases retain the result") {
      var interpreter: AsyncInterpreter = null
      val source                        = new Source() {
        override def setLimit(n: Long): Async[Boolean] = {
          interpreter.closeForTest().block
          Async.succeed(true)
        }
      }
      interpreter = new AsyncInterpreter(source)
      val result = interpreter.setLimit(1).either.block
      assertTrue(result.left.exists(_.isInstanceOf[IOException]), interpreter.isClosed.block, source.closes.get() == 1)
    },
    test("control child reentrant close cancels the running operation") {
      var interpreter: AsyncInterpreter = null
      val source                        = new Source() {
        override def setLimit(n: Long): Async[Boolean] =
          Async.deferCancelable(
            () => { interpreter.closeForTest().block; true },
            () => ()
          )
      }
      interpreter = new AsyncInterpreter(source)
      val result = interpreter.setLimit(1).either.block
      interpreter.closeForTest().block
      assertTrue(result.left.exists(_.isInstanceOf[IOException]), interpreter.isClosed.block, source.closes.get() == 1)
    },
    test("control cancellation before child publication joins the registered child") {
      val effect       = new Gate[Boolean]
      val registered   = new CountDownLatch(1)
      val cancellation = new CountDownLatch(1)
      val source       = new Source() {
        override def setLimit(n: Long): Async[Boolean] = effect
      }
      val interpreter = new AsyncInterpreter(source)
      interpreter.beforeControlRegistrationForTest { () => registered.countDown(); cancellation.await() }
      val operation = interpreter.setLimit(1)
      val _         = operation.start
      registered.await()
      val cleanup = Async.cancelWithCleanup(operation.asInstanceOf[Pollable[_]])
      cancellation.countDown()
      for {
        result <- runAsync(cleanup.either)
      } yield assertTrue(result == Right(()), effect.cancels.get() == 1)
    },
    test("owned close cancels a pending non-driver control") {
      val effect = new Gate[Boolean]
      val source = new Source() {
        override def setLimit(n: Long): Async[Boolean] = effect
      }
      val interpreter = new AsyncInterpreter(source)
      val _           = interpreter.setLimit(1).start
      for {
        _ <- awaitPoll(effect)
        _ <- runAsync(interpreter.closeForTest())
      } yield assertTrue(interpreter.isClosed.block, source.closes.get() == 1)
    },
    test("owned close defers cleanup when invoked by a bulk-control driver") {
      var interpreter: AsyncInterpreter = null
      val source                        = new Source() {
        override def jvmType                               = JvmType.Int
        override def read[A >: Any](sentinel: A): Async[A] =
          Async.deferCancelable(
            () => { interpreter.closeForTest().block; 1.asInstanceOf[A] },
            () => ()
          )
        override def readInt(sentinel: Long)(implicit ev: Any <:< Int): Async[Long] =
          Async.deferCancelable(
            () => { interpreter.closeForTest().block; 1L },
            () => ()
          )
      }
      interpreter = new AsyncInterpreter(source)
      val values = new Array[Int](1)
      val result = interpreter.readInts(values, 0, 1).block
      interpreter.closeForTest().block
      assertTrue(result == -1, interpreter.isClosed.block, source.closes.get() == 1)
    },
    test("owned close skips a nested reader already detached by recovery") {
      val root        = new Source()
      val nested      = new Source()
      val interpreter = new AsyncInterpreter(root)
      interpreter.installNestedFrameForTest(nested, JvmType.AnyRef)
      interpreter.clearIncomingReaderForTest(1)
      interpreter.closeForTest().block
      assertTrue(root.closes.get() == 1, nested.closes.get() == 0, interpreter.isClosed.block)
    },
    test("callback finalizer construction and effect failures publish once while close cancellation joins") {
      val gate         = new Gate[Unit]
      val construction = new RuntimeException("finalizer-construction")
      val effect       = new RuntimeException("finalizer-effect")
      val calls        = new AtomicInteger
      val i            = new AsyncInterpreter(new Source())
      i.addAsyncFinalizer { () => calls.incrementAndGet(); Async.fail(effect) }
      i.addAsyncFinalizer { () => calls.incrementAndGet(); throw construction }
      i.addAsyncFinalizer { () => calls.incrementAndGet(); gate }
      val close = i.closeForTest()
      close.asInstanceOf[Pollable[Unit]].poll(noop)
      val cancel = Async.cancelWithCleanup(close.asInstanceOf[Pollable[Unit]])
      gate.succeed(())
      for {
        result <- runAsync(close.either)
        joined <- runAsync(cancel.either)
        replay <- runAsync(i.closeForTest().either)
      } yield assertTrue(
        result == Left(construction),
        joined == result,
        replay == result,
        construction.getSuppressed.toList == List(effect),
        calls.get() == 3,
        gate.cancels.get() == 0
      )
    }
  )
}
