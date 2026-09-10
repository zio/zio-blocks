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

import java.util.concurrent.atomic.{AtomicInteger, AtomicReference}

import zio.blocks.async._
import zio.blocks.streams.internal.{AsyncInterpreter, StreamError}
import zio.blocks.streams.io.Reader
import zio.test._

object AsyncInterpreterCloseRecoveryCoverageSpec extends StreamsBaseSpec {
  private val Noop = new Runnable { def run(): Unit = () }

  private final class CancellableGate[A] extends Async.Operation[A] {
    val cancels                                  = new AtomicInteger(0)
    val entered                                  = new Completer[Unit]
    protected def cancelOperation(): Async[Unit] = { cancels.incrementAndGet(); Async.succeed(()) }
    def poll(wake: Runnable): Async[A]           = { entered.succeed(()); this }
  }

  private final class Gate[A] extends Async.Operation[A] {
    private val result                           = new Completer[A]
    val entered                                  = new Completer[Unit]
    val polls                                    = new AtomicInteger(0)
    val cancels                                  = new AtomicInteger(0)
    def poll(wake: Runnable): Async[A]           = { polls.incrementAndGet(); entered.succeed(()); result.poll(wake) }
    protected def cancelOperation(): Async[Unit] = { cancels.incrementAndGet(); Async.succeed(()) }
    def succeed(value: A): Unit                  = result.succeed(value)
    def fail(cause: Throwable): Unit             = result.fail(cause)
  }

  private final class WakeTwiceThen[A](next: => Async[A]) extends Pollable[A] {
    val polls                          = new AtomicInteger(0)
    def poll(wake: Runnable): Async[A] =
      if (polls.getAndIncrement() == 0) { wake.run(); wake.run(); this }
      else next
  }

  private final class Replace[A](next: Pollable[A]) extends Pollable[A] {
    val polls                          = new AtomicInteger(0)
    def poll(wake: Runnable): Async[A] = { polls.incrementAndGet(); next }
  }

  private class Source(next: () => Async[Any], closeEffect: () => Async[Unit] = () => Async.succeed(()))
      extends Reader.AsyncReader[Any] {
    val reads                                 = new AtomicInteger(0)
    val closes                                = new AtomicInteger(0)
    def read[A >: Any](sentinel: A): Async[A] = { reads.incrementAndGet(); next().asInstanceOf[Async[A]] }
    def readable(): Async[Boolean]            = Async.succeed(true)
    def isClosed: Async[Boolean]              = Async.succeed(false)
    def close(): Async[Unit]                  = { closes.incrementAndGet(); closeEffect() }
  }

  private final class EofSource(closeEffect: () => Async[Unit] = () => Async.succeed(()))
      extends Reader.AsyncReader[Any] {
    val closes                                = new AtomicInteger(0)
    def read[A >: Any](sentinel: A): Async[A] = Async.succeed(sentinel)
    def readable(): Async[Boolean]            = Async.succeed(false)
    def isClosed: Async[Boolean]              = Async.succeed(false)
    def close(): Async[Unit]                  = { closes.incrementAndGet(); closeEffect() }
  }

  private def reader(closeEffect: => Async[Unit]): Source =
    new Source(() => Async.succeed("eof"), () => closeEffect)

  private def suppressed(cause: Throwable): List[Throwable] = cause.getSuppressed.toList

  def spec = suite("AsyncInterpreter close and recovery coverage")(
    test("closeReaders handles empty, one, and many readers lazily in order") {
      val events        = new AtomicReference[List[Int]](Nil)
      def owner(n: Int) = reader { events.updateAndGet(n :: _); Async.succeed(()) }
      val interpreter   = new AsyncInterpreter(reader(Async.succeed(())))
      val empty         = interpreter.closeReadersForTest()
      val one           = owner(1)
      val many          = List(owner(2), owner(3), owner(4))
      val operation     = interpreter.closeReadersForTest((one :: many): _*)
      for {
        _     <- runAsync(empty)
        before = events.get()
        _     <- runAsync(operation)
      } yield assertTrue(before.isEmpty, events.get().reverse == List(1, 2, 3, 4), one.closes.get() == 1)
    },
    test("closeReaders preserves the first failure and attaches later cleanup failures in order") {
      val first       = new RuntimeException("first")
      val second      = new RuntimeException("second")
      val third       = new RuntimeException("third")
      val interpreter = new AsyncInterpreter(reader(Async.succeed(())))
      for {
        result <-
          runAsync(
            interpreter
              .closeReadersForTest(reader(Async.fail(first)), reader(Async.fail(second)), reader(Async.fail(third)))
              .either
          )
      } yield assertTrue(result == Left(first), suppressed(first) == List(second, third))
    },
    test("closeReaders retains null failure semantics while still closing every reader") {
      val calls                      = new AtomicInteger(0)
      val later                      = new RuntimeException("later")
      def owner(effect: Async[Unit]) = reader { calls.incrementAndGet(); effect }
      val interpreter                = new AsyncInterpreter(reader(Async.succeed(())))
      for {
        nullFirst <- runAsync(interpreter.closeReadersForTest(owner(Async.fail(null)), owner(Async.fail(later))).either)
        nullLast  <- runAsync(interpreter.closeReadersForTest(owner(Async.fail(later)), owner(Async.fail(null))).either)
      } yield assertTrue(nullFirst == Left(null), nullLast == Left(later), calls.get() == 4)
    },
    test("ReadersClose follows ready pending replacements without cancelling them") {
      val gate        = new Gate[Unit]
      val replacement = new Replace[Unit](gate)
      val first       = new Replace[Unit](replacement)
      val owner       = reader(first)
      val operation   = new AsyncInterpreter(reader(Async.succeed(()))).closeReadersForTest(owner)
      val initial     = operation.asInstanceOf[Pollable[Unit]].poll(Noop)
      gate.succeed(())
      for {
        _ <- runAsync(operation)
      } yield assertTrue(
        initial.isInstanceOf[Pollable[_]],
        first.polls.get() == 1,
        replacement.polls.get() == 1,
        gate.polls.get() >= 1,
        gate.cancels.get() == 0,
        owner.closes.get() == 1
      )
    },
    test("ReadersClose coalesces reentrant duplicate wakeups and closes the next reader once") {
      val wake      = new WakeTwiceThen[Unit](Async.succeed(()))
      val first     = reader(wake)
      val second    = reader(Async.succeed(()))
      val operation = new AsyncInterpreter(reader(Async.succeed(()))).closeReadersForTest(first, second)
      for {
        _ <- runAsync(operation)
      } yield assertTrue(wake.polls.get() == 2, first.closes.get() == 1, second.closes.get() == 1)
    },
    test("ReadersClose captures reader close construction and poll defects and continues cleanup") {
      val construction = new RuntimeException("construction")
      val polling      = new RuntimeException("poll")
      val later        = reader(Async.succeed(()))
      val throwing     = new Source(() => Async.succeed("eof")) {
        override def close(): Async[Unit] = throw construction
      }
      val pollDefect = reader(new Async.Operation[Unit] {
        def poll(wake: Runnable): Async[Unit]        = throw polling
        protected def cancelOperation(): Async[Unit] = Async.succeed(())
      })
      val interpreter = new AsyncInterpreter(reader(Async.succeed(())))
      for {
        result <- runAsync(interpreter.closeReadersForTest(throwing, pollDefect, later).either)
      } yield assertTrue(
        result == Left(construction),
        suppressed(construction) == List(polling),
        later.closes.get() == 1
      )
    },
    test("combineCleanup runs both sides and preserves primary versus cleanup ordering") {
      val events                                  = new AtomicReference[List[String]](Nil)
      val first                                   = new RuntimeException("first")
      val second                                  = new RuntimeException("second")
      def mark(name: String, effect: Async[Unit]) =
        Async.succeed(()).flatMap { _ => events.updateAndGet(name :: _); effect }
      val interpreter = new AsyncInterpreter(reader(Async.succeed(())))
      for {
        both <- runAsync(
                  interpreter
                    .combineCleanupForTest(mark("first", Async.fail(first)), mark("second", Async.fail(second)))
                    .either
                )
        leftOk  <- runAsync(interpreter.combineCleanupForTest(Async.succeed(()), Async.fail(second)).either)
        rightOk <- runAsync(interpreter.combineCleanupForTest(Async.fail(first), Async.succeed(())).either)
      } yield assertTrue(
        both == Left(first),
        suppressed(first) == List(second),
        events.get().reverse == List("first", "second"),
        leftOk == Left(second),
        rightOk == Left(first)
      )
    },
    test("root and owned close are exactly once across duplicate polls") {
      val gate        = new Gate[Unit]
      val source      = new Source(() => Async.succeed("eof"), () => gate)
      val interpreter = new AsyncInterpreter(source)
      val close       = interpreter.closeForTest()
      val first       = close.asInstanceOf[Pollable[Unit]].poll(Noop)
      val duplicate   = close.asInstanceOf[Pollable[Unit]].poll(Noop)
      gate.succeed(())
      for {
        _ <- runAsync(close)
        _ <- runAsync(interpreter.closeForTest())
      } yield assertTrue(
        first.isInstanceOf[Pollable[_]],
        duplicate.isInstanceOf[Pollable[_]],
        source.closes.get() == 1,
        gate.cancels.get() == 0
      )
    },
    test("finalizers run inner-to-outer once and normalize throw null and failed callbacks") {
      val events      = new AtomicReference[List[Int]](Nil)
      val thrown      = new RuntimeException("thrown")
      val failed      = new RuntimeException("failed")
      val interpreter = new AsyncInterpreter(reader(Async.succeed(())))
      interpreter.addAsyncFinalizer { () => events.updateAndGet(1 :: _); Async.fail(failed) }
      interpreter.addAsyncFinalizer { () => events.updateAndGet(2 :: _); null }
      interpreter.addAsyncFinalizer { () => events.updateAndGet(3 :: _); throw thrown }
      for {
        first  <- runAsync(interpreter.closeForTest().either)
        replay <- runAsync(interpreter.closeForTest().either)
      } yield {
        val primary = first.left.toOption.orNull
        assertTrue(
          primary eq thrown,
          replay == first,
          events.get().reverse == List(3, 2, 1),
          suppressed(primary).size == 2
        )
      }
    },
    test("pending finalizer tolerates duplicate close and cancellation joins rather than cancels it") {
      val gate        = new Gate[Unit]
      val calls       = new AtomicInteger(0)
      val interpreter = new AsyncInterpreter(reader(Async.succeed(())))
      interpreter.addAsyncFinalizer { () => calls.incrementAndGet(); gate }
      val close   = interpreter.closeForTest()
      val _       = close.asInstanceOf[Pollable[Unit]].poll(Noop)
      val cleanup = Async.cancelWithCleanup(close.asInstanceOf[Pollable[Unit]])
      val _2      = close.asInstanceOf[Pollable[Unit]].poll(Noop)
      gate.succeed(())
      for {
        _ <- runAsync(cleanup)
        _ <- runAsync(close)
      } yield assertTrue(calls.get() == 1, gate.cancels.get() == 0)
    },
    test("nested close succeeds synchronously and restores the root reader") {
      val outer       = new Source(() => Async.succeed(9))
      val inner       = new EofSource
      val interpreter = new AsyncInterpreter(outer)
      interpreter.installNestedFrameForTest(inner, JvmType.AnyRef)
      for {
        value <- runAsync(interpreter.read[Any]("eof"))
      } yield assertTrue(value == 9, inner.closes.get() == 1, outer.closes.get() == 0)
    },
    test("nested close drives a replacement and duplicate reentrant wake exactly once") {
      val terminal    = new WakeTwiceThen[Unit](Async.succeed(()))
      val replacement = new Replace[Unit](terminal)
      val outer       = new Source(() => Async.succeed(7))
      val inner       = new EofSource(() => replacement)
      val interpreter = new AsyncInterpreter(outer)
      interpreter.installNestedFrameForTest(inner, JvmType.AnyRef)
      for {
        value <- runAsync(interpreter.read[Any]("eof"))
      } yield assertTrue(value == 7, inner.closes.get() == 1, replacement.polls.get() == 1, terminal.polls.get() == 2)
    },
    test("nested close failure is primary and becomes sticky") {
      val failure     = new RuntimeException("inner close")
      val outer       = new Source(() => Async.succeed(7))
      val inner       = new EofSource(() => Async.fail(failure))
      val interpreter = new AsyncInterpreter(outer)
      interpreter.installNestedFrameForTest(inner, JvmType.AnyRef)
      for {
        first  <- runAsync(interpreter.read[Any]("eof").either)
        replay <- runAsync(interpreter.read[Any]("again").either)
      } yield assertTrue(first == Left(failure), replay == Left(failure), outer.reads.get() == 0)
    },
    test("cancellation during nested close waits for close completion and leaves root reusable") {
      val gate        = new Gate[Unit]
      val outer       = new Source(() => Async.succeed(11))
      val inner       = new EofSource(() => gate)
      val interpreter = new AsyncInterpreter(outer)
      interpreter.installNestedFrameForTest(inner, JvmType.AnyRef)
      val pull    = interpreter.read[Any]("cancelled")
      val _       = pull.asInstanceOf[Pollable[Any]].poll(Noop)
      val cleanup = Async.cancelWithCleanup(pull.asInstanceOf[Pollable[Any]])
      gate.succeed(())
      for {
        _     <- runAsync(cleanup)
        value <- runAsync(interpreter.read[Any]("eof"))
      } yield assertTrue(value == 11, inner.closes.get() == 1, gate.cancels.get() == 0)
    },
    test("typed recovery covers ready success null stream and handler defect") {
      val trigger                                  = StreamError.source("typed")
      def recovered(effect: Async[Stream[_, Any]]) = {
        val interpreter = new AsyncInterpreter(new Source(() => Async.failTrusted(trigger)))
        interpreter.addAsyncCatchAll[String](_ => effect)
        interpreter
      }
      val defect = new RuntimeException("handler")
      for {
        value        <- runAsync(recovered(Async.succeed(Stream.succeed("ok": Any))).read[Any]("eof"))
        nullResult   <- runAsync(recovered(Async.succeed(null)).read[Any]("eof").either)
        defectResult <- runAsync(recovered(Async.fail(defect)).read[Any]("eof").either)
      } yield assertTrue(
        value == "ok",
        nullResult.left.exists(cause =>
          cause.isInstanceOf[NullPointerException] &&
            cause.getMessage == "async recovery callback returned null"
        ),
        defectResult == Left(defect)
      )
    },
    test("defect recovery covers None null Option and null failure") {
      val trigger                                                            = new RuntimeException("trigger")
      def recovered(cause: Throwable, effect: Async[Option[Stream[_, Any]]]) = {
        val interpreter = new AsyncInterpreter(new Source(() => Async.fail(cause)))
        interpreter.addAsyncCatchDefect { case _ => effect }
        interpreter
      }
      for {
        none <- runAsync(recovered(trigger, Async.succeed(None)).read[Any]("eof").either)
        nul  <- runAsync(recovered(trigger, Async.succeed(null)).read[Any]("eof").either)
        zero <- runAsync(recovered(null, Async.succeed(Some(Stream.succeed("zero": Any)))).read[Any]("eof"))
      } yield assertTrue(
        none == Left(trigger),
        nul.left.exists(cause =>
          cause.isInstanceOf[NullPointerException] &&
            cause.getMessage == "async recovery callback returned null"
        ),
        zero == "zero"
      )
    },
    test("recovery rejects undefined handlers null effects null streams and throwing replacement reads") {
      val trigger = new RuntimeException("trigger")
      val typed   = StreamError.source("typed")

      val undefined = new AsyncInterpreter(new Source(() => Async.fail(trigger)))
      undefined.addAsyncCatchDefect(PartialFunction.empty)

      val nullEffect = new AsyncInterpreter(new Source(() => Async.failTrusted(typed)))
      nullEffect.addAsyncCatchAll[String](_ => null.asInstanceOf[Async[Stream[_, Any]]])

      val nullStream = new AsyncInterpreter(new Source(() => Async.fail(trigger)))
      nullStream.addAsyncCatchDefect { case _ => Async.succeed(Some(null.asInstanceOf[Stream[_, Any]])) }

      val readFailure = new RuntimeException("replacement-read")
      val throwing    = new Reader.AsyncReader[Any] {
        def read[A >: Any](sentinel: A): Async[A] = throw readFailure
        def readable(): Async[Boolean]            = Async.succeed(true)
        def isClosed: Async[Boolean]              = Async.succeed(false)
        def close(): Async[Unit]                  = Async.succeed(())
      }
      val replacement = new AsyncInterpreter(new Source(() => Async.failTrusted(typed)))
      replacement.addAsyncCatchAll[String](_ => Async.succeed(Stream.fromReader[Nothing, Any](throwing)))

      for {
        rejected <- runAsync(undefined.read[Any]("eof").either)
        noEffect <- runAsync(nullEffect.read[Any]("eof").either)
        noStream <- runAsync(nullStream.read[Any]("eof").either)
        read     <- runAsync(replacement.read[Any]("eof").either)
      } yield assertTrue(
        rejected == Left(trigger),
        noEffect.left.exists(_.getMessage == "async recovery callback returned null"),
        noStream.left.exists(_.getMessage == "async recovery callback succeeded with null stream"),
        read == Left(readFailure)
      )
    },
    test("pending recovery handler coalesces duplicate wakeups and installs one replacement") {
      val trigger     = StreamError.source("typed")
      val terminal    = new WakeTwiceThen[Stream[_, Any]](Async.succeed(Stream.succeed("done": Any)))
      val calls       = new AtomicInteger(0)
      val interpreter = new AsyncInterpreter(new Source(() => Async.failTrusted(trigger)))
      interpreter.addAsyncCatchAll[String] { _ => calls.incrementAndGet(); terminal }
      for {
        value <- runAsync(interpreter.read[Any]("eof"))
      } yield assertTrue(value == "done", calls.get() == 1, terminal.polls.get() == 2)
    },
    test("cancellation during a pending recovery callback cancels callback cleanup and repairs root state") {
      val trigger     = StreamError.source("typed")
      val gate        = new CancellableGate[Stream[_, Any]]
      val interpreter = new AsyncInterpreter(new Source(() => Async.failTrusted(trigger)))
      interpreter.addAsyncCatchAll[String](_ => gate)
      val pull = interpreter.read[Any]("cancelled")
      val _    = pull.asInstanceOf[Pollable[Any]].poll(Noop)
      for {
        _    <- runAsync(gate.entered)
        _    <- runAsync(Async.cancelWithCleanup(pull.asInstanceOf[Pollable[Any]]))
        next <- runAsync(interpreter.read[Any]("eof"))
      } yield assertTrue(gate.cancels.get() == 1, next == "eof")
    },
    test("recovery waits for failed-owner close and cancellation joins that close without cancelling it") {
      val trigger     = StreamError.source("typed")
      val gate        = new Gate[Unit]
      val calls       = new AtomicInteger(0)
      val source      = new Source(() => Async.failTrusted(trigger), () => gate)
      val interpreter = new AsyncInterpreter(source)
      interpreter.addAsyncCatchAll[String] { _ => calls.incrementAndGet(); Async.succeed(Stream.succeed("bad": Any)) }
      val pull    = interpreter.read[Any]("cancelled")
      val _       = pull.asInstanceOf[Pollable[Any]].poll(Noop)
      val cleanup = Async.cancelWithCleanup(pull.asInstanceOf[Pollable[Any]])
      gate.succeed(())
      for {
        _ <- runAsync(cleanup)
      } yield assertTrue(source.closes.get() == 1, gate.cancels.get() == 0, calls.get() == 0)
    },
    test("multi-level cancellation rolls back every intermediate owner after innermost close success") {
      val gate         = new Gate[Unit]
      val root         = new Source(() => Async.succeed(1))
      val intermediate = new EofSource
      val innermost    = new EofSource(() => gate)
      val interpreter  = new AsyncInterpreter(root)
      interpreter.installNestedFrameForTest(intermediate, JvmType.AnyRef)
      interpreter.installNestedFrameForTest(innermost, JvmType.AnyRef)
      val pull = interpreter.read[Any]("cancelled")
      pull.asInstanceOf[Pollable[Any]].poll(Noop)
      for {
        _     <- runAsync(gate.entered)
        cancel = Async.cancelWithCleanup(pull.asInstanceOf[Pollable[Any]])
        _      = cancel.asInstanceOf[Pollable[Unit]].poll(Noop)
        _      = gate.succeed(())
        _     <- runAsync(cancel)
        value <- runAsync(interpreter.read[Any]("eof"))
      } yield assertTrue(
        value == 1,
        innermost.closes.get() == 1,
        intermediate.closes.get() == 1,
        root.closes.get() == 0,
        gate.cancels.get() == 0
      )
    },
    test("successful inner close captures a synchronous throw while redriving the restored outer reader") {
      val failure = new RuntimeException("outer read")
      val outer   = new Source(() => Async.succeed(1)) {
        override def read[A >: Any](sentinel: A): Async[A] = throw failure
      }
      val inner       = new EofSource
      val interpreter = new AsyncInterpreter(outer)
      interpreter.installNestedFrameForTest(inner, JvmType.AnyRef)
      for {
        result <- runAsync(interpreter.read[Any]("eof").either)
      } yield assertTrue(result == Left(failure), inner.closes.get() == 1)
    }
  )
}
