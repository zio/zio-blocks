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
import zio.blocks.streams.internal.AsyncInterpreter
import zio.blocks.streams.io.Reader
import zio.test._

object AsyncInterpreterCloseResidualCoverageSpec extends StreamsBaseSpec {
  private val Noop = new Runnable { def run(): Unit = () }

  /*
   * Current residual checklist (/tmp/current2-AsyncInterpreter.scala.txt):
   * [x] beginInnerClose 3309/3312/3331/3332/3335: current and cancelled epochs,
   *     missing outer frame, existing claim, and close construction failure.
   * [x] finishInnerCloseSuccess 3401/3414/3418/3420/3421/3445/3449/3460/3468:
   *     stale claim, cancellation with/without rollback readers (including null
   *     slots), normal redrive/yield, cleanup success/failure, and read defect.
   * [x] finishInnerCloseFailure 3479/3482/3490/3492/3493/3515/3526/3529:
   *     stale claim, ordinary sticky failure, cancellation cleanup with empty,
   *     present, and null readers, and cleanup suppression.
   * [x] RootClose 2779-2797 and OwnedClose 2986-3068: leader/duplicate/reentrant
   *     poll, construction throw, asynchronous failure, cancellation before and
   *     after ownership, repeated/concurrent aliases, and successful replay.
   * [x] ReadersClose 3139-3260: unstarted/started/finished cancellation, self
   *     pending, ready replacements, duplicate wakes, poll/close failures, null
   *     failures, first-failure cleanup suppression, and every claimed reader.
   * [x] FinalizerReader 3102-3135: claim leader/follower, cancel-before-claim,
   *     pending duplicate close, success, throw, null, failed effect, and
   *     poll-time callback failure normalization.
   * [x] Nested frame restoration for Boolean, Byte, Char, Short, Int, Long,
   *     Float, Double, and AnyRef output lanes; no outer/root frame is harmless.
   */

  private final class Gate[A] extends Async.Operation[A] {
    private val result                           = new Completer[A]
    val polls                                    = new AtomicInteger(0)
    val cancels                                  = new AtomicInteger(0)
    def poll(wake: Runnable): Async[A]           = { polls.incrementAndGet(); result.poll(wake) }
    protected def cancelOperation(): Async[Unit] = { cancels.incrementAndGet(); Async.succeed(()) }
    def succeed(value: A): Unit                  = result.succeed(value)
    def fail(cause: Throwable): Unit             = result.fail(cause)
  }

  private final class SelfThen[A](result: => Async[A], duplicateWake: Boolean = false) extends Pollable[A] {
    val polls                          = new AtomicInteger(0)
    def poll(wake: Runnable): Async[A] =
      if (polls.getAndIncrement() == 0) {
        if (duplicateWake) { wake.run(); wake.run() }
        this
      } else result
  }

  private final class Replace[A](next: Pollable[A]) extends Pollable[A] {
    val polls                          = new AtomicInteger(0)
    def poll(wake: Runnable): Async[A] = { polls.incrementAndGet(); next }
  }

  private class Source(
    next: () => Async[Any] = () => Async.succeed("eof"),
    closeEffect: () => Async[Unit] = () => Async.succeed(()),
    lane: JvmType = JvmType.AnyRef
  ) extends Reader.AsyncReader[Any] {
    val reads                                                                         = new AtomicInteger(0)
    val closes                                                                        = new AtomicInteger(0)
    override def jvmType: JvmType                                                     = lane
    def read[A >: Any](sentinel: A): Async[A]                                         = { reads.incrementAndGet(); next().asInstanceOf[Async[A]] }
    override def readBoolean(sentinel: Int)(implicit ev: Any <:< Boolean): Async[Int] =
      read[Any](sentinel).map(v => if (v == sentinel) sentinel else if (v.asInstanceOf[Boolean]) 1 else 0)
    override def readByte(): Async[Int]                                         = read[Any](-1).map(v => if (v == -1) -1 else v.asInstanceOf[Byte].toInt & 0xff)
    override def readChar(sentinel: Int)(implicit ev: Any <:< Char): Async[Int] =
      read[Any](sentinel).map(v => if (v == sentinel) sentinel else v.asInstanceOf[Char].toInt)
    override def readShort(sentinel: Int)(implicit ev: Any <:< Short): Async[Int] =
      read[Any](sentinel).map(v => if (v == sentinel) sentinel else v.asInstanceOf[Short].toInt)
    override def readInt(sentinel: Long)(implicit ev: Any <:< Int): Async[Long] =
      read[Any](sentinel).map(v => if (v == sentinel) sentinel else v.asInstanceOf[Int].toLong)
    override def readLong(sentinel: Long)(implicit ev: Any <:< Long): Async[Long] =
      read[Any](sentinel).map(_.asInstanceOf[Long])
    override def readLongs(dest: Array[Long], offset: Int, length: Int)(implicit ev: Any <:< Long): Async[Int] =
      if (length == 0) Async.succeed(0)
      else readLong(Long.MinValue).map(v => if (v == Long.MinValue) -1 else { dest(offset) = v; 1 })
    override def readFloat(sentinel: Double)(implicit ev: Any <:< Float): Async[Double] =
      read[Any](sentinel).map(v => if (v == sentinel) sentinel else v.asInstanceOf[Float].toDouble)
    override def readDouble(sentinel: Double)(implicit ev: Any <:< Double): Async[Double] =
      read[Any](sentinel).map(_.asInstanceOf[Double])
    override def readDoubles(dest: Array[Double], offset: Int, length: Int)(implicit ev: Any <:< Double): Async[Int] =
      if (length == 0) Async.succeed(0)
      else readDouble(Double.NaN).map(v => if (v.isNaN) -1 else { dest(offset) = v; 1 })
    def readable(): Async[Boolean] = Async.succeed(true)
    def isClosed: Async[Boolean]   = Async.succeed(false)
    def close(): Async[Unit]       = { closes.incrementAndGet(); closeEffect() }
  }

  private class Eof(lane: JvmType, closeEffect: () => Async[Unit] = () => Async.succeed(()))
      extends Reader.AsyncReader[Any] {
    val closes                                                                                                 = new AtomicInteger(0)
    override def jvmType: JvmType                                                                              = lane
    def read[A >: Any](sentinel: A): Async[A]                                                                  = Async.succeed(sentinel)
    override def readBoolean(sentinel: Int)(implicit ev: Any <:< Boolean): Async[Int]                          = Async.succeed(sentinel)
    override def readByte(): Async[Int]                                                                        = Async.succeed(-1)
    override def readChar(sentinel: Int)(implicit ev: Any <:< Char): Async[Int]                                = Async.succeed(sentinel)
    override def readShort(sentinel: Int)(implicit ev: Any <:< Short): Async[Int]                              = Async.succeed(sentinel)
    override def readInt(sentinel: Long)(implicit ev: Any <:< Int): Async[Long]                                = Async.succeed(sentinel)
    override def readLong(sentinel: Long)(implicit ev: Any <:< Long): Async[Long]                              = Async.succeed(sentinel)
    override def readLongs(dest: Array[Long], offset: Int, length: Int)(implicit ev: Any <:< Long): Async[Int] =
      Async.succeed(-1)
    override def readFloat(sentinel: Double)(implicit ev: Any <:< Float): Async[Double]                              = Async.succeed(sentinel)
    override def readDouble(sentinel: Double)(implicit ev: Any <:< Double): Async[Double]                            = Async.succeed(sentinel)
    override def readDoubles(dest: Array[Double], offset: Int, length: Int)(implicit ev: Any <:< Double): Async[Int] =
      Async.succeed(-1)
    def readable(): Async[Boolean] = Async.succeed(false)
    def isClosed: Async[Boolean]   = Async.succeed(false)
    def close(): Async[Unit]       = { closes.incrementAndGet(); closeEffect() }
  }

  private def pull(interpreter: AsyncInterpreter, lane: JvmType): Async[Any] = (lane match {
    case JvmType.Boolean => interpreter.readBoolean(-1)
    case JvmType.Byte    => interpreter.readByte()
    case JvmType.Char    => interpreter.readChar(-1)
    case JvmType.Short   => interpreter.readShort(-1)
    case JvmType.Int     => interpreter.readInt(Long.MinValue)
    case JvmType.Long    => interpreter.readLong(Long.MinValue)
    case JvmType.Float   => interpreter.readFloat(Double.NaN)
    case JvmType.Double  => interpreter.readDouble(Double.NaN)
    case JvmType.AnyRef  => interpreter.read[Any]("eof")
  }).asInstanceOf[Async[Any]]

  private def suppressed(cause: Throwable): List[Throwable] = cause.getSuppressed.toList

  def spec = suite("AsyncInterpreter residual close coverage")(
    test("nested close restores every output lane and never closes the root") {
      val lanes = List(
        JvmType.Boolean,
        JvmType.Byte,
        JvmType.Char,
        JvmType.Short,
        JvmType.Int,
        JvmType.Long,
        JvmType.Float,
        JvmType.Double,
        JvmType.AnyRef
      )
      ZIO
        .foreach(lanes) { lane =>
          val value: Any = lane match {
            case JvmType.Boolean => true
            case JvmType.Byte    => 1.toByte
            case JvmType.Char    => 'a'
            case JvmType.Short   => 2.toShort
            case JvmType.Int     => 3
            case JvmType.Long    => 4L
            case JvmType.Float   => 5.0f
            case JvmType.Double  => 6.0d
            case JvmType.AnyRef  => "value"
          }
          val outer       = new Source(next = () => Async.succeed(value), lane = lane)
          val inner       = new Eof(lane)
          val interpreter = new AsyncInterpreter(outer)
          interpreter.installNestedFrameForTest(inner, lane)
          runAsync(pull(interpreter, lane)).map(_ => (inner.closes.get(), outer.closes.get()))
        }
        .map(counts => assertTrue(counts.forall(_ == (1, 0))))
    },
    test("a root without an outer frame closes once and replays through every alias") {
      val source      = new Source()
      val interpreter = new AsyncInterpreter(source)
      for {
        _ <- runAsync(interpreter.closeForTest())
        _ <- runAsync(interpreter.closeForTest())
        _ <- runAsync(Async.cancelWithCleanup(interpreter.closeForTest().asInstanceOf[Pollable[Unit]]))
      } yield assertTrue(source.closes.get() == 1)
    },
    test("direct root close cancellation starts and joins the real root-close operation") {
      val gate        = new Gate[Unit]
      val source      = new Source(closeEffect = () => gate)
      val interpreter = new AsyncInterpreter(source)
      val rootClose   = interpreter.rootCloseForTest()
      for {
        cancel <- runAsync(Async.cancelWithCleanup(rootClose.asInstanceOf[Pollable[Unit]])).fork
        _      <- ZIO.yieldNow
        _       = gate.succeed(())
        _      <- cancel.join
        _      <- runAsync(Async.cancelWithCleanup(rootClose.asInstanceOf[Pollable[Unit]]))
      } yield assertTrue(source.closes.get() == 1, gate.cancels.get() == 0)
    },
    test("nested close construction and poll failures become sticky without closing root") {
      val construction = new RuntimeException("construct")
      val polling      = new RuntimeException("poll")
      val outer1       = new Source()
      val outer2       = new Source()
      val throwing     = new Eof(JvmType.AnyRef) {
        override def close(): Async[Unit] = throw construction
      }
      val pollDefect = new Eof(
        JvmType.AnyRef,
        () =>
          new Async.Operation[Unit] {
            def poll(wake: Runnable): Async[Unit]        = throw polling
            protected def cancelOperation(): Async[Unit] = Async.succeed(())
          }
      )
      val first  = new AsyncInterpreter(outer1)
      val second = new AsyncInterpreter(outer2)
      first.installNestedFrameForTest(throwing, JvmType.AnyRef)
      second.installNestedFrameForTest(pollDefect, JvmType.AnyRef)
      for {
        a  <- runAsync(first.read[Any]("eof").either)
        ar <- runAsync(first.read[Any]("again").either)
        b  <- runAsync(second.read[Any]("eof").either)
      } yield assertTrue(a == Left(construction), ar == a, b == Left(polling), outer1.closes.get() == 0)
    },
    test("cancellation at a self-pending nested close joins current ownership and ignores stale wakes") {
      val gate        = new Gate[Unit]
      val self        = new SelfThen[Unit](gate, duplicateWake = true)
      val inner       = new Eof(JvmType.AnyRef, () => self)
      val outer       = new Source()
      val interpreter = new AsyncInterpreter(outer)
      interpreter.installNestedFrameForTest(inner, JvmType.AnyRef)
      val read    = interpreter.read[Any]("cancelled")
      val pending = read.asInstanceOf[Pollable[Any]].poll(Noop)
      val cleanup = Async.cancelWithCleanup(read.asInstanceOf[Pollable[Any]])
      gate.succeed(())
      for {
        _     <- runAsync(cleanup)
        value <- runAsync(interpreter.read[Any]("eof"))
      } yield assertTrue(
        pending.isInstanceOf[Pollable[_]],
        self.polls.get() == 2,
        gate.cancels.get() == 0,
        inner.closes.get() == 1,
        value == "eof"
      )
    },
    test("ReadersClose cancellation before claim, while pending, and after completion only joins") {
      val gate        = new Gate[Unit]
      val first       = new Source(closeEffect = () => gate)
      val second      = new Source()
      val interpreter = new AsyncInterpreter(new Source())
      val unstarted   = interpreter.closeReadersForTest(new Source())
      val operation   = interpreter.closeReadersForTest(first, second)
      for {
        _     <- runAsync(Async.cancelWithCleanup(unstarted.asInstanceOf[Pollable[Unit]]))
        fiber <- runAsync(Async.cancelWithCleanup(operation.asInstanceOf[Pollable[Unit]])).fork
        _     <- ZIO.yieldNow
        _      = gate.succeed(())
        _     <- fiber.join
        _     <- runAsync(Async.cancelWithCleanup(operation.asInstanceOf[Pollable[Unit]]))
      } yield assertTrue(first.closes.get() == 1, second.closes.get() == 1, gate.cancels.get() == 0)
    },
    test("ReadersClose handles self pending, replacement, duplicate wake, null failure, and suppression") {
      val firstFailure               = new RuntimeException("first")
      val laterFailure               = new RuntimeException("later")
      val terminal                   = new SelfThen[Unit](Async.fail(firstFailure), duplicateWake = true)
      val replacement                = new Replace[Unit](terminal)
      val calls                      = new AtomicInteger(0)
      def owner(effect: Async[Unit]) = new Source(closeEffect = () => { calls.incrementAndGet(); effect })
      val interpreter                = new AsyncInterpreter(new Source())
      for {
        result <- runAsync(
                    interpreter
                      .closeReadersForTest(
                        owner(replacement),
                        owner(Async.fail(laterFailure)),
                        owner(Async.fail(null)),
                        owner(Async.succeed(()))
                      )
                      .either
                  )
      } yield assertTrue(
        result == Left(firstFailure),
        suppressed(firstFailure) == List(laterFailure),
        replacement.polls.get() == 1,
        terminal.polls.get() == 2,
        calls.get() == 4
      )
    },
    test("trusted reader and nested-reader close failures preserve trusted failure delivery") {
      val readersFailure = new RuntimeException("trusted-readers-close")
      val nestedFailure  = new RuntimeException("trusted-inner-close")
      val interpreter    = new AsyncInterpreter(new Source())
      val outer          = new Source()
      val nested         = new Eof(JvmType.AnyRef, () => Async.failTrusted(nestedFailure))
      val nestedOwner    = new AsyncInterpreter(outer)
      nestedOwner.installNestedFrameForTest(nested, JvmType.AnyRef)
      for {
        readers <- runAsync(
                     interpreter
                       .closeReadersForTest(new Source(closeEffect = () => Async.failTrusted(readersFailure)))
                       .either
                   )
        inner <- runAsync(nestedOwner.read[Any]("eof").either)
      } yield assertTrue(readers == Left(readersFailure), inner == Left(nestedFailure))
    },
    test("root close captures construction throw and asynchronous failure exactly once") {
      val thrown   = new RuntimeException("thrown")
      val failed   = new RuntimeException("failed")
      val throwing = new Source() {
        override def close(): Async[Unit] = { closes.incrementAndGet(); throw thrown }
      }
      val failing = new Source(closeEffect = () => Async.fail(failed))
      val a       = new AsyncInterpreter(throwing)
      val b       = new AsyncInterpreter(failing)
      for {
        ar <- runAsync(a.closeForTest().either)
        aa <- runAsync(a.closeForTest().either)
        br <- runAsync(b.closeForTest().either)
        bb <- runAsync(b.closeForTest().either)
      } yield assertTrue(
        ar == Left(thrown),
        aa == ar,
        br == Left(failed),
        bb == br,
        throwing.closes.get() == 1,
        failing.closes.get() == 1
      )
    },
    test("owned close aliases race deterministically and retain one root owner") {
      val gate                               = new Gate[Unit]
      val source                             = new Source(closeEffect = () => gate)
      val interpreter                        = new AsyncInterpreter(source)
      def awaitPoll: ZIO[Any, Nothing, Unit] =
        if (gate.polls.get() > 0) ZIO.unit else ZIO.yieldNow *> ZIO.suspendSucceed(awaitPoll)
      for {
        one <- runAsync(interpreter.closeForTest().either).fork
        two <- runAsync(interpreter.closeForTest().either).fork
        _   <- awaitPoll
        _    = gate.succeed(())
        a   <- one.join
        b   <- two.join
      } yield assertTrue(a == Right(()), b == Right(()), source.closes.get() == 1, gate.cancels.get() == 0)
    },
    test("reentrant owned close from root close construction completes without duplicate ownership") {
      val nestedCalls                   = new AtomicInteger(0)
      var interpreter: AsyncInterpreter = null
      val source                        = new Source(closeEffect = () => {
        nestedCalls.incrementAndGet()
        interpreter.closeForTest()
      })
      interpreter = new AsyncInterpreter(source)
      for {
        _ <- runAsync(interpreter.closeForTest())
        _ <- runAsync(interpreter.closeForTest())
      } yield assertTrue(source.closes.get() == 1, nestedCalls.get() == 1)
    },
    test("finalizer claim is lazy, duplicate, cancellable, and normalizes all callback defects") {
      val calls       = new AtomicInteger(0)
      val gate        = new Gate[Unit]
      val thrown      = new RuntimeException("thrown")
      val failed      = new RuntimeException("failed")
      val interpreter = new AsyncInterpreter(new Source())
      interpreter.addAsyncFinalizer { () => calls.incrementAndGet(); Async.fail(failed) }
      interpreter.addAsyncFinalizer { () => calls.incrementAndGet(); null }
      interpreter.addAsyncFinalizer { () => calls.incrementAndGet(); throw thrown }
      interpreter.addAsyncFinalizer { () => calls.incrementAndGet(); gate }
      val close   = interpreter.closeForTest()
      val before  = calls.get()
      val _       = close.asInstanceOf[Pollable[Unit]].poll(Noop)
      val cleanup = Async.cancelWithCleanup(close.asInstanceOf[Pollable[Unit]])
      val _2      = close.asInstanceOf[Pollable[Unit]].poll(Noop)
      gate.succeed(())
      for {
        result <- runAsync(close.either)
        _      <- runAsync(cleanup.either)
        replay <- runAsync(interpreter.closeForTest().either)
      } yield {
        val cause = result.left.toOption.orNull
        assertTrue(
          before == 0,
          calls.get() == 4,
          gate.cancels.get() == 0,
          cause eq thrown,
          replay == result,
          suppressed(cause).size == 2
        )
      }
    },
    test("the finalizer adapter implements its complete inert Reader surface") {
      val calls       = new AtomicInteger
      val interpreter = new AsyncInterpreter(new Source())
      val reader      = interpreter.finalizerReaderForTest(() => Async.succeed { calls.incrementAndGet(); () })
      for {
        before <- runAsync(reader.isClosed)
        ready  <- runAsync(reader.readable())
        value  <- runAsync(reader.read[Any]("sentinel"))
        _      <- runAsync(reader.reset())
        _      <- runAsync(reader.close())
        after  <- runAsync(reader.isClosed)
        _      <- runAsync(reader.close())
      } yield assertTrue(
        reader.jvmType == JvmType.AnyRef,
        !before,
        !ready,
        value == "sentinel",
        after,
        calls.get() == 1
      )
    }
  )
}
