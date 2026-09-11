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

import zio.ZIO
import zio.blocks.async._
import zio.blocks.streams.internal.{AsyncInterpreter, StreamError}
import zio.blocks.streams.io.Reader
import zio.test._

/**
 * JVM-only because these tests deliberately coordinate blocking and concurrent
 * close operations.
 */
object AsyncInterpreterFinalCoverageSpec extends StreamsBaseSpec {
  private val noop = new Runnable { def run(): Unit = () }

  private final class Gate[A] extends Async.Operation[A] {
    private val result                           = new Completer[A]
    val polls                                    = new AtomicInteger
    val cancels                                  = new AtomicInteger
    def poll(wake: Runnable): Async[A]           = { polls.incrementAndGet(); result.poll(wake) }
    protected def cancelOperation(): Async[Unit] = { cancels.incrementAndGet(); Async.succeed(()) }
    def succeed(value: A): Unit                  = result.succeed(value)
    def fail(cause: Throwable): Unit             = result.fail(cause)
  }

  private class Source(
    readEffect: () => Async[Any],
    closeEffect: () => Async[Unit] = () => Async.succeed(())
  ) extends Reader.AsyncReader[Any] {
    val closes                                = new AtomicInteger
    def read[A >: Any](sentinel: A): Async[A] = readEffect().asInstanceOf[Async[A]]
    def readable(): Async[Boolean]            = Async.succeed(true)
    def isClosed: Async[Boolean]              = Async.succeed(false)
    def close(): Async[Unit]                  = { closes.incrementAndGet(); closeEffect() }
  }

  private def awaitPoll(gate: Gate[_]): ZIO[Any, Nothing, Unit] =
    if (gate.polls.get() != 0) ZIO.unit else ZIO.yieldNow *> ZIO.suspendSucceed(awaitPoll(gate))

  def spec = suite("AsyncInterpreter final critical coverage")(
    test("rejected completeRead redrive captures construction failure and remains sticky") {
      val calls   = new AtomicInteger
      val failure = new RuntimeException("redrive")
      val reader  = new Source(() =>
        if (calls.getAndIncrement() == 0) Async.succeed(1)
        else throw failure
      ) {
        override def jvmType                                                        = JvmType.Int
        override def readInt(sentinel: Long)(implicit ev: Any <:< Int): Async[Long] =
          if (calls.getAndIncrement() == 0) Async.succeed(1L) else throw failure
      }
      val interpreter = new AsyncInterpreter(reader)
      interpreter.addFilter[Int](JvmType.Int)(_ => false)
      assertTrue(
        interpreter.readInt(-1L).either.block == Left(failure),
        interpreter.readInt(-2L).either.block == Left(failure),
        calls.get() == 2
      )
    },
    test("recovery owner close is joined, never cancelled, before cancellation can invoke the handler") {
      val trigger     = StreamError.source("typed")
      val gate        = new Gate[Unit]
      val calls       = new AtomicInteger
      val source      = new Source(() => Async.failTrusted(trigger), () => gate)
      val interpreter = new AsyncInterpreter(source)
      interpreter.addAsyncCatchAll[String] { _ =>
        calls.incrementAndGet()
        Async.succeed(Stream.succeed("wrong": Any))
      }
      val read = interpreter.read[Any]("cancelled")
      read.asInstanceOf[Pollable[Any]].poll(noop)
      val cancellation = Async.cancelWithCleanup(read.asInstanceOf[Pollable[Any]])
      gate.succeed(())
      for {
        _    <- runAsync(cancellation)
        next <- runAsync(interpreter.read[Any]("eof"))
      } yield assertTrue(source.closes.get() == 1, gate.cancels.get() == 0, calls.get() == 0, next == "eof")
    },
    test("recovery owner close normalizes construction failure and cancellation joins its outcome") {
      val construction = new RuntimeException("close-construction")
      val failedClose  = new Source(() => Async.never) {
        override def close(): Async[Unit] = throw construction
      }
      val failedInterpreter = new AsyncInterpreter(failedClose)
      val cleanGate         = new Gate[Unit]
      val cleanInterpreter  = new AsyncInterpreter(new Source(() => Async.never, () => cleanGate))
      val cleanOwner        = cleanInterpreter.recoveryOwnerCloseForTest(new Source(() => Async.never, () => cleanGate))
      val cleanCancellation = Async.cancelWithCleanup(cleanOwner.asInstanceOf[Pollable[_]])
      cleanGate.succeed(())
      val failure            = new RuntimeException("close-failure")
      val failureGate        = new Gate[Unit]
      val failureReader      = new Source(() => Async.never, () => failureGate)
      val failureOwner       = new AsyncInterpreter(failureReader).recoveryOwnerCloseForTest(failureReader)
      val failedCancellation = Async.cancelWithCleanup(failureOwner.asInstanceOf[Pollable[_]])
      failureGate.fail(failure)
      for {
        constructed <- runAsync(failedInterpreter.recoveryOwnerCloseForTest(failedClose))
        clean       <- runAsync(cleanCancellation.either)
        failed      <- runAsync(failedCancellation.either)
      } yield assertTrue(constructed == Left(construction), clean == Right(()), failed == Left(failure))
    },
    test("rejected recovery materialization closes its detached reader on both close outcomes") {
      def run(closeFailure: Throwable): Async[Either[Throwable, Long]] = {
        val trigger     = StreamError.source("typed")
        val replacement = new Reader.SyncReader[String] {
          def read[A >: String](sentinel: A): A = "bad"
          def isClosed: Boolean                 = false
          def close(): Unit                     = if (closeFailure ne null) throw closeFailure
        }
        val source = new Source(() => Async.failTrusted(trigger)) {
          override def jvmType                                                        = JvmType.Int
          override def readInt(sentinel: Long)(implicit ev: Any <:< Int): Async[Long] = Async.failTrusted(trigger)
        }
        val interpreter = new AsyncInterpreter(source)
        interpreter.addAsyncCatchAll[String](_ =>
          Async.succeed(Stream.fromReader[Nothing, String](replacement).asInstanceOf[Stream[_, Any]])
        )
        interpreter.readInt(-1L).either
      }
      val closeFailure = new RuntimeException("replacement-close")
      for {
        clean  <- runAsync(run(null))
        failed <- runAsync(run(closeFailure))
      } yield assertTrue(
        clean.left.exists(_.isInstanceOf[IllegalArgumentException]),
        failed.left.exists(cause =>
          cause.isInstanceOf[IllegalArgumentException] && cause.getSuppressed.toList == List(closeFailure)
        )
      )
    },
    test("rejected materialization reader exposes its lifecycle contract") {
      val cleanup = new AtomicInteger
      val failure = new RuntimeException("materialization")
      val reader  = AsyncInterpreter.rejectedMaterializationReaderForTest(
        failure,
        Async.succeed { cleanup.incrementAndGet(); () }
      )
      for {
        initiallyClosed <- runAsync(reader.isClosed)
        readable        <- runAsync(reader.readable())
        result          <- runAsync(reader.read[Any]("eof").either)
        finallyClosed   <- runAsync(reader.isClosed)
        _               <- runAsync(reader.close())
      } yield assertTrue(
        reader.jvmType == JvmType.AnyRef,
        !initiallyClosed,
        !readable,
        result == Left(failure),
        finallyClosed,
        cleanup.get() == 1
      )
    },
    test("inner close success redrives the outer reader and failure is sticky") {
      def inner(closeEffect: Async[Unit]) = new Reader.AsyncReader[Any] {
        def read[A >: Any](sentinel: A): Async[A] = Async.succeed(sentinel)
        def readable(): Async[Boolean]            = Async.succeed(false)
        def isClosed: Async[Boolean]              = Async.succeed(false)
        def close(): Async[Unit]                  = closeEffect
      }
      val outerSuccess = new Source(() => Async.succeed("outer"))
      val successful   = new AsyncInterpreter(outerSuccess)
      successful.installNestedFrameForTest(inner(Async.succeed(())), JvmType.AnyRef)
      val failure   = new RuntimeException("inner-close")
      val outerFail = new Source(() => Async.succeed("unused"))
      val failing   = new AsyncInterpreter(outerFail)
      failing.installNestedFrameForTest(inner(Async.fail(failure)), JvmType.AnyRef)
      for {
        value  <- runAsync(successful.read[Any]("eof"))
        first  <- runAsync(failing.read[Any]("eof").either)
        replay <- runAsync(failing.read[Any]("again").either)
      } yield assertTrue(value == "outer", first == Left(failure), replay == first, outerSuccess.closes.get() == 0)
    },
    test("owned close aliases share one pending root close and cancellation only joins it") {
      val gate        = new Gate[Unit]
      val source      = new Source(() => Async.succeed("unused"), () => gate)
      val interpreter = new AsyncInterpreter(source)
      val close       = interpreter.closeForTest()
      for {
        first  <- runAsync(close.either).fork
        second <- runAsync(interpreter.closeForTest().either).fork
        joined <- runAsync(Async.cancelWithCleanup(close.asInstanceOf[Pollable[Unit]]).either).fork
        _      <- awaitPoll(gate)
        _       = gate.succeed(())
        a      <- first.join
        b      <- second.join
        c      <- joined.join
      } yield assertTrue(
        a == Right(()),
        b == Right(()),
        c == Right(()),
        source.closes.get() == 1,
        gate.cancels.get() == 0
      )
    }
  )
}
