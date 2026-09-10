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
import zio.blocks.streams.internal.StreamError
import zio.blocks.streams.io.Reader
import zio.test._

/** JVM-only accumulator coverage that waits for scheduler-thread polling. */
object SinkNativeAsyncIntAccumulatorSpec extends StreamsBaseSpec {
  private sealed trait Step[+A]
  private final case class Done[A](value: A)                          extends Step[A]
  private final case class Failed(error: Throwable, trusted: Boolean) extends Step[Nothing]
  private final case class Waiting[A](pollable: Pollable[A])          extends Step[A]

  private def step[A](effect: Async[A]): Step[A] =
    Async.foldStep(effect)(new Async.StepFold[A, Step[A]] {
      def success(value: A): Step[A]                         = Done(value)
      def failure(error: Throwable): Step[A]                 = Failed(error, trusted = false)
      override def trustedFailure(error: Throwable): Step[A] = Failed(error, trusted = true)
      def pending(pollable: Pollable[A]): Step[A]            = Waiting(pollable)
    })

  private def pollOnce[A](effect: Async[A]): Step[A] = step(effect) match {
    case Waiting(pollable) => step(pollable.poll(new Runnable { def run(): Unit = () }))
    case result            => result
  }

  private def awaitPoll(gate: Gate[_]): Boolean = {
    var attempts = 0
    while (gate.polls.get == 0 && attempts < 1000) {
      Thread.sleep(1L)
      attempts += 1
    }
    gate.polls.get > 0
  }

  private final class Gate[A] extends Async.Operation[A] {
    val polls                            = new AtomicInteger(0)
    val cancellations                    = new AtomicInteger(0)
    val cleanup                          = new Completer[Unit]
    private var result: Option[Async[A]] = None
    private var wake: Runnable           = null

    def poll(onComplete: Runnable): Async[A] = synchronized {
      polls.incrementAndGet()
      result match {
        case Some(value) => value
        case None        => wake = onComplete; this
      }
    }

    def complete(value: A): Unit              = finish(Async.succeed(value))
    private def finish(value: Async[A]): Unit = synchronized {
      result = Some(value)
      val callback = wake
      wake = null
      if (callback ne null) callback.run()
    }

    protected def cancelOperation(): Async[Unit] = {
      cancellations.incrementAndGet()
      cleanup
    }
  }

  private final class IntReader(reads0: Vector[() => Async[Long]]) extends Reader.AsyncReader[Int] {
    val reads         = new AtomicInteger(0)
    val closes        = new AtomicInteger(0)
    private var index = 0

    override def jvmType: JvmType                                               = JvmType.Int
    def close(): Async[Unit]                                                    = { closes.incrementAndGet(); Async.succeed(()) }
    def isClosed: Async[Boolean]                                                = Async.succeed(false)
    def readable(): Async[Boolean]                                              = Async.succeed(true)
    def read[A >: Int](sentinel: A): Async[A]                                   = Async.fail(new AssertionError("generic read used"))
    override def readInt(sentinel: Long)(implicit ev: Int <:< Int): Async[Long] = {
      reads.incrementAndGet()
      if (index == reads0.length) Async.succeed(sentinel)
      else {
        val read = reads0(index)
        index += 1
        read()
      }
    }
  }

  private trait Accumulator[A] {
    def name: String
    def zero: A
    def plus(acc: A, value: Int): A
    def fold(reader: Reader.AsyncReader[Int], callback: (A, Int) => Async[A]): Async[A]
  }

  private val accumulators: List[Accumulator[_]] = List(
    new Accumulator[Int] {
      val name                                                                      = "Int"; val zero = 0
      def plus(acc: Int, value: Int)                                                = acc + value
      def fold(reader: Reader.AsyncReader[Int], callback: (Int, Int) => Async[Int]) =
        Sink.foldNativeAsyncIntInt(reader, zero, callback)
    },
    new Accumulator[Float] {
      val name                                                                          = "Float"; val zero = 0.0f
      def plus(acc: Float, value: Int)                                                  = acc + value
      def fold(reader: Reader.AsyncReader[Int], callback: (Float, Int) => Async[Float]) =
        Sink.foldNativeAsyncIntFloat(reader, zero, callback)
    },
    new Accumulator[Double] {
      val name                                                                            = "Double"; val zero = 0.0d
      def plus(acc: Double, value: Int)                                                   = acc + value
      def fold(reader: Reader.AsyncReader[Int], callback: (Double, Int) => Async[Double]) =
        Sink.foldNativeAsyncIntDouble(reader, zero, callback)
    }
  )

  private def tests[A](accumulator: Accumulator[A]): Spec[Any, Nothing] = suite(accumulator.name)(
    test("uses exact Int physical reads for ready values and EOF without closing") {
      val reader = new IntReader(Vector(() => Async.succeed(2L), () => Async.succeed(3L)))
      val result = accumulator.fold(reader, (acc, value) => Async.succeed(accumulator.plus(acc, value))).block
      assertTrue(
        result == accumulator.plus(accumulator.plus(accumulator.zero, 2), 3),
        reader.reads.get == 3,
        reader.closes.get == 0
      )
    },
    test("resumes genuinely pending read values and EOF") {
      val valueGate       = new Gate[Long]
      val valueReader     = new IntReader(Vector(() => valueGate))
      val valueRun        = accumulator.fold(valueReader, (acc, value) => Async.succeed(accumulator.plus(acc, value))).start
      val valueWasPending = awaitPoll(valueGate)
      valueGate.complete(7L)

      val eofGate       = new Gate[Long]
      val eofReader     = new IntReader(Vector(() => eofGate))
      val eofRun        = accumulator.fold(eofReader, (acc, value) => Async.succeed(accumulator.plus(acc, value))).start
      val eofWasPending = awaitPoll(eofGate)
      eofGate.complete(Long.MinValue)

      assertTrue(
        valueWasPending,
        valueRun.block == accumulator.plus(accumulator.zero, 7),
        valueReader.reads.get == 2,
        eofWasPending,
        eofRun.block == accumulator.zero,
        eofReader.reads.get == 1,
        valueReader.closes.get == 0,
        eofReader.closes.get == 0
      )
    },
    test("resumes a genuinely pending fold callback") {
      val gate       = new Gate[A]
      val reader     = new IntReader(Vector(() => Async.succeed(11L)))
      val running    = accumulator.fold(reader, (_, _) => gate).start
      val wasPending = awaitPoll(gate)
      val expected   = accumulator.plus(accumulator.zero, 11)
      gate.complete(expected)
      assertTrue(wasPending, running.block == expected, reader.reads.get == 2, reader.closes.get == 0)
    },
    test("preserves read failures and normalizes callback StreamErrors") {
      val ordinaryRead     = new IllegalStateException("read")
      val trustedRead      = StreamError.source("trusted-read")
      val ordinaryCallback = new IllegalArgumentException("callback")
      val trustedCallback  = StreamError.source("trusted-callback")

      def failedRead(error: Throwable, trusted: Boolean) = {
        val reader = new IntReader(Vector(() => if (trusted) Async.failTrusted(error) else Async.fail(error)))
        (pollOnce(accumulator.fold(reader, (a, _) => Async.succeed(a))), reader)
      }
      def thrownRead(error: Throwable) = {
        val reader = new IntReader(Vector(() => throw error))
        (pollOnce(accumulator.fold(reader, (a, _) => Async.succeed(a))), reader)
      }
      def callback(effect: => Async[A]) =
        try pollOnce(accumulator.fold(new IntReader(Vector(() => Async.succeed(1L))), (_, _) => effect))
        catch { case error: Throwable => Failed(error, trusted = false) }

      val (ordinaryFailed, ordinaryReader)     = failedRead(ordinaryRead, trusted = false)
      val (trustedFailed, trustedReader)       = failedRead(trustedRead, trusted = true)
      val (ordinaryThrown, thrownReader)       = thrownRead(ordinaryRead)
      val (trustedThrown, trustedThrownReader) = thrownRead(trustedRead)
      val callbackOutcomes                     = List(
        callback(throw ordinaryCallback),
        callback(Async.fail(ordinaryCallback)),
        callback(throw trustedCallback),
        callback(Async.failTrusted(trustedCallback))
      )

      assertTrue(
        ordinaryFailed == Failed(ordinaryRead, trusted = false),
        trustedFailed == Failed(trustedRead, trusted = true),
        ordinaryThrown == Failed(ordinaryRead, trusted = false),
        trustedThrown == Failed(trustedRead, trusted = true),
        callbackOutcomes.take(2).forall(_ == Failed(ordinaryCallback, trusted = false)),
        callbackOutcomes.drop(2).forall {
          case Failed(error: StreamError, false) =>
            (error ne trustedCallback) && error.value == "trusted-callback" && !error.isTrusted
          case _ => false
        },
        List(ordinaryReader, trustedReader, thrownReader, trustedThrownReader).forall(_.closes.get == 0)
      )
    },
    test("drives replacement pollables for reads and callbacks") {
      def replacement[B](value: B): Pollable[B] = new Pollable[B] {
        def poll(onComplete: Runnable): Async[B] = { val _ = onComplete; Async.succeed(value) }
      }
      val readReader     = new IntReader(Vector(() => replacement(5L)))
      val readResult     = accumulator.fold(readReader, (a, n) => Async.succeed(accumulator.plus(a, n))).block
      val callbackReader = new IntReader(Vector(() => Async.succeed(6L)))
      val callbackResult =
        accumulator.fold(callbackReader, (_, n) => replacement(accumulator.plus(accumulator.zero, n))).block
      assertTrue(
        readResult == accumulator.plus(accumulator.zero, 5),
        callbackResult == accumulator.plus(accumulator.zero, 6)
      )
    },
    test("cancellation reaches pending reads, joins cleanup, and does not close") {
      val gate              = new Gate[Long]
      val reader            = new IntReader(Vector(() => gate))
      val running           = accumulator.fold(reader, (a, _) => Async.succeed(a)).start
      val readWasPending    = awaitPoll(gate)
      val cleanup           = Async.cancelWithCleanup(running)
      val cleanupWasPending = step(cleanup).isInstanceOf[Waiting[_]]
      gate.cleanup.succeed(())
      cleanup.block
      assertTrue(readWasPending, gate.cancellations.get == 1, cleanupWasPending, reader.closes.get == 0)
    },
    test("yields at the 1024-element fairness boundary") {
      val reader  = new IntReader(Vector.fill(1025)(() => Async.succeed(1L)))
      val effect  = accumulator.fold(reader, (a, n) => Async.succeed(accumulator.plus(a, n)))
      val yielded = step(effect).isInstanceOf[Waiting[_]]
      val result  = effect.block
      assertTrue(
        yielded,
        result == accumulator.plus(accumulator.zero, 1025),
        reader.reads.get == 1026,
        reader.closes.get == 0
      )
    }
  )

  def spec = suite("native async Int accumulator state machines")(
    accumulators.map(accumulator => tests(accumulator.asInstanceOf[Accumulator[Any]])): _*
  )
}
