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
import java.util.concurrent.atomic.{AtomicBoolean, AtomicInteger}

import zio.blocks.async._
import zio.blocks.streams.internal.{AsyncInterpreter, StreamError}
import zio.blocks.streams.io.Reader
import zio.test._

/**
 * JVM-only because these white-box tests intentionally exercise synchronous
 * blocking entry points.
 */
object AsyncInterpreterReadResidualCoverageSpec extends StreamsBaseSpec {
  private val noop = new Runnable { def run(): Unit = () }

  private final class Pending[A] extends Async.Operation[A] {
    var wake: Runnable                           = null
    val cancelled                                = new AtomicInteger
    def poll(onComplete: Runnable): Async[A]     = { wake = onComplete; this }
    protected def cancelOperation(): Async[Unit] = { cancelled.incrementAndGet(); Async.succeed(()) }
  }

  private final class Replacing[A](next: Async[A], reentrant: Boolean = false) extends Pollable[A] {
    def poll(onComplete: Runnable): Async[A] = {
      if (reentrant) onComplete.run()
      next
    }
  }

  private final class Gate[A] extends Async.Operation[A] {
    private val result                           = new Completer[A]
    val polls                                    = new AtomicInteger
    val cancels                                  = new AtomicInteger
    def poll(onComplete: Runnable): Async[A]     = { polls.incrementAndGet(); result.poll(onComplete) }
    protected def cancelOperation(): Async[Unit] = { cancels.incrementAndGet(); Async.succeed(()) }
    def succeed(value: A): Unit                  = result.succeed(value)
    def fail(cause: Throwable): Unit             = result.fail(cause)
  }

  private final class LateSuccess[A](value: A) extends Async.Operation[A] {
    var wake: Runnable                           = null
    @volatile var ready                          = false
    def poll(onComplete: Runnable): Async[A]     = if (ready) Async.succeed(value) else { wake = onComplete; this }
    protected def cancelOperation(): Async[Unit] = Async.succeed(())
  }

  private final class ThrowingRepoll(failure: Throwable) extends Async.Operation[Long] {
    var wake: Runnable                          = null
    val polls                                   = new AtomicInteger
    def poll(onComplete: Runnable): Async[Long] =
      if (polls.getAndIncrement() == 0) { wake = onComplete; this }
      else throw failure
    protected def cancelOperation(): Async[Unit] = Async.succeed(())
  }

  private final class ManuallyReady[A](value: A) extends Pollable[A] {
    @volatile var ready                      = false
    def poll(onComplete: Runnable): Async[A] = if (ready) Async.succeed(value) else this
  }

  private class LaneReader(lane: JvmType, values: List[Any]) extends Reader.AsyncReader[Any] {
    private var rest                      = values
    override def jvmType: JvmType         = lane
    private def next[A](eof: A): Async[A] = rest match {
      case head :: tail => rest = tail; Async.succeed(head.asInstanceOf[A])
      case Nil          => Async.succeed(eof)
    }
    def close(): Async[Unit]                                                                                   = Async.succeed(())
    def isClosed: Async[Boolean]                                                                               = Async.succeed(false)
    def readable(): Async[Boolean]                                                                             = Async.succeed(rest.nonEmpty)
    def read[A >: Any](sentinel: A): Async[A]                                                                  = next(sentinel)
    override def readBoolean(s: Int)(implicit ev: Any <:< Boolean): Async[Int]                                 = next(s)
    override def readByte(): Async[Int]                                                                        = next(-1)
    override def readChar(s: Int)(implicit ev: Any <:< Char): Async[Int]                                       = next(s)
    override def readShort(s: Int)(implicit ev: Any <:< Short): Async[Int]                                     = next(s)
    override def readInt(s: Long)(implicit ev: Any <:< Int): Async[Long]                                       = next(s)
    override def readLong(s: Long)(implicit ev: Any <:< Long): Async[Long]                                     = next(s)
    override def readFloat(s: Double)(implicit ev: Any <:< Float): Async[Double]                               = next(s)
    override def readDouble(s: Double)(implicit ev: Any <:< Double): Async[Double]                             = next(s)
    override def readLongs(dest: Array[Long], offset: Int, length: Int)(implicit ev: Any <:< Long): Async[Int] =
      next[AnyRef](null).map(value => if (value eq null) -1 else { dest(offset) = value.asInstanceOf[Long]; 1 })
    override def readDoubles(dest: Array[Double], offset: Int, length: Int)(implicit ev: Any <:< Double): Async[Int] =
      next[AnyRef](null).map(value => if (value eq null) -1 else { dest(offset) = value.asInstanceOf[Double]; 1 })
  }

  private def failedMap(f: Int => Async[Int]): Async[Either[Throwable, Long]] = {
    val i = AsyncInterpreter.fromStream(Stream(1))
    i.addAsyncMap(JvmType.Int, JvmType.Int)(f)
    i.readInt(-1L).either
  }

  private def cancelActive[A](interpreter: AsyncInterpreter, operation: => Async[A]): Async.Running[Unit] = {
    val cancelling                        = new CountDownLatch(1)
    var cancellation: Async.Running[Unit] = null
    val thread                            = new Thread(() => {
      val requested = Async.cancelWithCleanup(operation.asInstanceOf[Pollable[A]])
      Async.startRegistered(requested)(running => cancellation = running)
      while (!interpreter.activeReadCancellingForTest) Thread.onSpinWait()
      cancelling.countDown()
    })
    thread.start()
    cancelling.await()
    cancellation
  }

  /*
   * Residual checklist (current2 line numbers):
   * completeRead 1628-2042: lane matrix, rejection/redrive, callback success/failure,
   * pending/replacement/reentrant/cancel, collect/takeWhile/distinct/accum/push below.
   * poll 564-566, 1151-1240, 2779-2837, 2990-3059, 3191-3195, 4158:
   * pending/replacement/reentrant and primitive reads below.
   * consume 1462-1548: ready, failed, pending, replacement, reentrant and cancellation below.
   * scalarResult 3610-3617 and intValue 219-229: lane/sentinel tests below.
   * installPush 2636-2660: null, nested success, nested failure and outgoing continuation below.
   * handoffCancelled 3634-3643 and cancel 3797-3884: cancellation before/after poll below.
   * bridgeInput 795-800: all five physical storage lanes are crossed below.
   * resetEffect/cancelReset 676-694: reset after transformed reads below.
   */
  def spec = suite("AsyncInterpreter read residual coverage")(
    test("a driver relinquishes a yielded read when another task already owns its schedule") {
      val reads  = new AtomicInteger
      val gate   = new ManuallyReady[Boolean](false)
      val reader = new LaneReader(JvmType.Int, (0 until 2048).map(_.asInstanceOf[Any]).toList) {
        override def readInt(s: Long)(implicit ev: Any <:< Int): Async[Long] = {
          reads.incrementAndGet()
          super.readInt(s)
        }
      }
      val interpreter = new AsyncInterpreter(reader)
      var first       = true
      interpreter.addAsyncFilter[Int](JvmType.Int) { _ =>
        if (first) { first = false; gate }
        else Async.succeed(false)
      }
      val operation = interpreter.readInt(-1L)
      operation.asInstanceOf[Pollable[Long]].poll(noop)
      interpreter.markActiveReadScheduledForTest()
      gate.ready = true

      val resumed          = operation.asInstanceOf[Pollable[Long]].poll(noop)
      val yielded          = resumed.asInstanceOf[AnyRef] eq operation.asInstanceOf[AnyRef]
      val stoppedBeforeEof = reads.get() < 2048
      Async.cancelWithCleanup(operation.asInstanceOf[Pollable[Long]]).block

      assertTrue(yielded, stoppedBeforeEof)
    },
    test("pending callbacks cover self, replacement, reentrant, stale, and both cancellation timings") {
      val pending = new Pending[Any]
      val i       = new AsyncInterpreter(new LaneReader(JvmType.AnyRef, List.empty) {
        override def read[A >: Any](sentinel: A): Async[A] = pending.asInstanceOf[Async[A]]
      })
      val op   = i.read[Any]("eof")
      val self = op.asInstanceOf[Pollable[Any]].poll(noop)
      if (pending.wake eq null) op.asInstanceOf[Pollable[Any]].poll(noop)
      assertTrue(self.asInstanceOf[AnyRef] eq op.asInstanceOf[AnyRef], pending.wake ne null)
      val cancelled = Async.cancelWithCleanup(op.asInstanceOf[Pollable[Any]])
      if (pending.wake ne null) pending.wake.run()
      val before       = new AsyncInterpreter(Reader.single(1).toAsync).read[Int](-1)
      val beforeCancel = Async.cancelWithCleanup(before.asInstanceOf[Pollable[Int]])
      val replacement  = new AsyncInterpreter(new LaneReader(JvmType.AnyRef, Nil) {
        override def read[A >: Any](s: A): Async[A] =
          new Replacing[A](Async.succeed(7.asInstanceOf[A]), reentrant = true)
      }).read[Any]("eof")
      assertTrue(cancelled.block == (), pending.cancelled.get == 1, beforeCancel.block == (), replacement.block == 7)
    },
    test("physical lane conversions preserve values and sentinel collisions") {
      val cases = List(
        (JvmType.Int, Int.MinValue: Any, JvmType.Long, Long.box(Int.MinValue.toLong): AnyRef),
        (JvmType.Long, Long.MinValue: Any, JvmType.Float, Float.box(17.0f): AnyRef),
        (JvmType.Float, Double.NaN: Any, JvmType.Double, Double.box(Double.NaN): AnyRef),
        (JvmType.Double, Double.MaxValue: Any, JvmType.AnyRef, "x": AnyRef),
        (JvmType.AnyRef, null: Any, JvmType.Int, Int.box(23): AnyRef)
      )
      val ok = cases.map { case (in, raw, out, expected) =>
        val reader = new LaneReader(in, List(raw))
        val i      = new AsyncInterpreter(reader)
        i.addMap[Any, Any](in, out)(_ => expected)
        val actual = i.read[Any]("eof").block
        if (expected.isInstanceOf[java.lang.Double] && expected.asInstanceOf[Double].isNaN)
          actual.asInstanceOf[Double].isNaN
        else actual == expected
      }
      assertTrue(ok.forall(identity))
    },
    test("rejected filter loops, collect Some None null, and takeWhile terminate exactly") {
      val filter = AsyncInterpreter.fromStream(Stream(1, 2, 3, 4))
      filter.addAsyncFilter[Int](JvmType.Int)(n => Async.succeed(n == 4))
      val collect = AsyncInterpreter.fromStream(Stream(1, 2, 3))
      collect.addAsyncCollect[Int, Int](JvmType.Int, JvmType.Int) {
        case 1 => Async.succeed(None)
        case 2 => Async.succeed(Some(20))
        case _ => Async.succeed(null)
      }
      val take = AsyncInterpreter.fromStream(Stream(1, 2, 3))
      take.addAsyncTakeWhile[Int](JvmType.Int)(n => Async.succeed(n < 2))
      assertTrue(
        filter.readInt(-1).block == 4L,
        collect.readInt(-1).block == 20L,
        collect.readInt(-1).either.block.isLeft,
        take.readInt(-1).block == 1L,
        take.readInt(-1).block == -1L
      )
    },
    test("distinct accepts, rejects, and reports hash and equality failures") {
      final class BadHash   { override def hashCode(): Int = throw new RuntimeException("hash") }
      final class BadEquals {
        override def hashCode(): Int             = 1;
        override def equals(other: Any): Boolean = throw new RuntimeException("equals")
      }
      def distinct(keys: Int => Any): AsyncInterpreter = {
        val i = AsyncInterpreter.fromStream(Stream(1, 1, 2))
        i.addAsyncDistinctKey[Int, Any](JvmType.Int)(n => Async.succeed(keys(n)))
        i
      }
      val normal = distinct(identity)
      val hash   = distinct(_ => new BadHash)
      val equal  = distinct(_ => new BadEquals)
      assertTrue(
        normal.readInt(-1).block == 1L,
        normal.readInt(-1).block == 2L,
        hash.readInt(-1).either.block.isLeft,
        equal.readInt(-1).block == 1L,
        equal.readInt(-1).either.block.isLeft
      )
    },
    test("mapAccum rejects malformed and null tuples and converts every output lane") {
      val outs = List[(JvmType, Any)](
        JvmType.Boolean -> true,
        JvmType.Int     -> 2,
        JvmType.Long    -> 3L,
        JvmType.Float   -> 4.0f,
        JvmType.Double  -> 5.0,
        JvmType.AnyRef  -> "six"
      )
      val converted = outs.map { case (out, value) =>
        val i = AsyncInterpreter.fromStream(Stream(1))
        i.addAsyncMapAccum[Int, Int, Any](0, JvmType.Int, out)((_, _) => Async.succeed((1, value)))
        i.read[Any]("eof").block == value
      }
      def malformed(value: Any): Either[Throwable, Any] = {
        val i = AsyncInterpreter.fromStream(Stream(1))
        i.addAsyncMapAccum[Int, Int, Any](0, JvmType.Int, JvmType.AnyRef)((_, _) =>
          Async.succeed(value.asInstanceOf[(Int, Any)])
        )
        i.read[Any]("eof").either.block
      }
      assertTrue(converted.forall(identity), malformed(null).isLeft, malformed("not-a-tuple").isLeft)
    },
    test("pending mapAccum rejects a successful null tuple") {
      val gate        = new Gate[Any]
      val interpreter = AsyncInterpreter.fromStream(Stream(1))
      interpreter.addAsyncMapAccum[Int, Int, Any](0, JvmType.Int, JvmType.AnyRef)((_, _) =>
        gate.asInstanceOf[Async[(Int, Any)]]
      )
      val operation = interpreter.read[Any]("eof")
      operation.asInstanceOf[Pollable[Any]].poll(noop)
      gate.succeed(null)

      val failure = operation.either.block.left.toOption
      assertTrue(failure.exists(_.getMessage == "async MAP_ACCUM callback succeeded with null tuple"))
    },
    test("async maps commit Long Float and Double output lanes") {
      val long = AsyncInterpreter.fromStream(Stream(1))
      long.addAsyncMap[Int, Long](JvmType.Int, JvmType.Long)(n => Async.reschedule(() => Async.succeed(n.toLong + 1L)))
      val float = AsyncInterpreter.fromStream(Stream(2))
      float.addAsyncMap[Int, Float](JvmType.Int, JvmType.Float)(n =>
        Async.reschedule(() => Async.succeed(n.toFloat + 1.5f))
      )
      val double = AsyncInterpreter.fromStream(Stream(3))
      double.addAsyncMap[Int, Double](JvmType.Int, JvmType.Double)(n =>
        Async.reschedule(() => Async.succeed(n.toDouble + 2.5d))
      )

      assertTrue(
        long.readLong(-1L).block == 2L,
        float.readFloat(-1d).block == 3.5d,
        double.readDouble(-1d).block == 5.5d
      )
    },
    test("an interpreter reader accepts a fused filter before reading starts") {
      val reader   = AsyncInterpreter.fromStream(Stream(1, 2)).toReader[Int]
      val filtered = AsyncInterpreter.fuseFilter[Int](reader, JvmType.Int, _ == 2)
      assertTrue(filtered eq reader, filtered.readInt(-1L).block == 2L)
    },
    test("non-inline Int pushes install nested readers and accept post-push fusion") {
      val interpreter = AsyncInterpreter.fromStream(Stream(1))
      interpreter.addPush[Int](JvmType.Int, JvmType.Int)(n => Stream.fromIterable(List(n + 1)))
      interpreter.addPush[Int](JvmType.Int, JvmType.Long)(n => Stream.fromIterable(List(n.toLong + 1L)))
      val reader   = interpreter.toReader[Long]
      val filtered = AsyncInterpreter.fuseFilter[Long](reader, JvmType.Long, _ == 3L)
      assertTrue(filtered eq reader, filtered.readLong(-1L).block == 3L)
    },
    test("terminal Int-to-Long folds use ready suspended and limited interpreter paths") {
      val readyReader = AsyncInterpreter.fromStream(Stream(1, 2, 3)).toReader[Int]
      val ready       = Sink
        .foldLeftAsync[Int, Long](0L)((acc, value) => Async.succeed(acc + value))
        .drain(readyReader)

      val gate            = new Gate[Long]
      val suspendedReader = AsyncInterpreter.fromStream(Stream(1, 2, 3)).toReader[Int]
      val suspended       = Sink
        .foldLeftAsync[Int, Long](0L)((acc, value) => if (value == 2) gate else Async.succeed(acc + value))
        .drain(suspendedReader)
      suspended.asInstanceOf[Pollable[Long]].poll(noop)
      gate.succeed(3L)

      val limitedReader = AsyncInterpreter.fromStream(Stream(1, 2, 3)).toReader[Int]
      limitedReader.setLimit(2).block
      val limited = Sink
        .foldLeftAsync[Int, Long](0L)((acc, value) => Async.succeed(acc + value))
        .drain(limitedReader)

      assertTrue(ready.block == 6L, suspended.block == 6L, limited.block == 3L)
    },
    test("a fresh read is rejected while an unrelated control operation owns the interpreter") {
      val readableGate = new Gate[Boolean]
      val source       = new LaneReader(JvmType.Int, List(1L)) {
        override def readable(): Async[Boolean] = readableGate
      }
      val interpreter = new AsyncInterpreter(source)
      val readable    = interpreter.readable()
      readable.asInstanceOf[Pollable[Boolean]].poll(noop)
      val rejected = interpreter.readInt(-1L).either.block
      readableGate.succeed(true)
      assertTrue(rejected.isLeft, readable.block)
    },
    test("pending collect null success has an exact sticky failure") {
      val collectGate = new Gate[Option[Int]]
      val collect     = AsyncInterpreter.fromStream(Stream(1))
      collect.addAsyncCollect[Int, Int](JvmType.Int, JvmType.Int)(_ => collectGate)
      val collectRead = collect.readInt(-1L)
      collectRead.asInstanceOf[Pollable[Long]].poll(noop)
      collectGate.succeed(null)

      val collectFailure = collectRead.either.block.left.toOption
      val collectSticky  = collect.readInt(-2L).either.block.left.toOption
      assertTrue(
        collectFailure.exists(_.getMessage == "async COLLECT callback succeeded with null Option"),
        collectSticky.exists(_.getMessage == "async COLLECT callback succeeded with null Option")
      )
    },
    test("cancellation between async readiness and commit discards the callback value") {
      val gate        = new Gate[Int]
      val interpreter = AsyncInterpreter.fromStream(Stream(1))
      interpreter.addAsyncMap[Int, Int](JvmType.Int, JvmType.Int)(_ => gate)
      val operation = interpreter.readInt(-1L)
      operation.asInstanceOf[Pollable[Long]].poll(noop)
      var cancellation: Async.Running[Unit] = null
      interpreter.beforeAsyncReadyCommitForTest(() =>
        cancellation = Async.cancelWithCleanup(operation.asInstanceOf[Pollable[Long]]).start
      )

      gate.succeed(99)

      for {
        _     <- runAsync(cancellation)
        value <- runAsync(operation)
        next  <- runAsync(interpreter.readInt(-2L))
      } yield assertTrue(value == -1L, next == -2L)
    },
    test("cancellation winning inside primitive map callbacks rejects every exact commit") {
      def cancelAtCallback[A](
        interpreter: AsyncInterpreter,
        install: (() => Unit) => Unit,
        read: => Async[A],
        sentinel: A
      ): Boolean = {
        var operation: Async[A]               = null
        var cancellation: Async.Running[Unit] = null
        install(() => cancellation = cancelActive(interpreter, operation))
        operation = read
        operation.asInstanceOf[Pollable[A]].poll(noop)
        cancellation.block
        operation.block == sentinel
      }

      val long   = AsyncInterpreter.fromStream(Stream(1))
      val float  = AsyncInterpreter.fromStream(Stream(1))
      val double = AsyncInterpreter.fromStream(Stream(1))

      assertTrue(
        cancelAtCallback[Long](
          long,
          callback =>
            long.addMap[Int, Long](JvmType.Int, JvmType.Long) { _ =>
              callback(); 2L
            },
          long.readLong(-1L),
          -1L
        ),
        cancelAtCallback[Double](
          float,
          callback =>
            float.addMap[Int, Float](JvmType.Int, JvmType.Float) { _ =>
              callback(); 2.0f
            },
          float.readFloat(-1.0),
          -1.0
        ),
        cancelAtCallback[Double](
          double,
          callback =>
            double.addMap[Int, Double](JvmType.Int, JvmType.Double) { _ =>
              callback(); 2.0
            },
          double.readDouble(-1.0),
          -1.0
        )
      )
    },
    test("cancellation winning inside incoming and outgoing PUSH callbacks prevents installation") {
      var incomingOperation: Async[Long]            = null
      var incomingCancellation: Async.Running[Unit] = null
      val incoming                                  = AsyncInterpreter.fromStream(Stream(1))
      incoming.addPush[Int](JvmType.Int, JvmType.Int) { _ =>
        incomingCancellation = cancelActive(incoming, incomingOperation)
        Stream.succeed(2)
      }
      incomingOperation = incoming.readInt(-1L)
      incomingOperation.asInstanceOf[Pollable[Long]].poll(noop)
      incomingCancellation.block

      var outgoingOperation: Async[Long]            = null
      var outgoingCancellation: Async.Running[Unit] = null
      val outgoing                                  = AsyncInterpreter.fromStream(Stream(1))
      outgoing.addPush[Int](JvmType.Int, JvmType.Int)(Stream.succeed)
      outgoing.addPush[Int](JvmType.Int, JvmType.Int) { _ =>
        outgoingCancellation = cancelActive(outgoing, outgoingOperation)
        Stream.succeed(2)
      }
      outgoingOperation = outgoing.readInt(-1L)
      outgoingOperation.asInstanceOf[Pollable[Long]].poll(noop)
      outgoingCancellation.block

      assertTrue(incomingOperation.block == -1L, outgoingOperation.block == -1L)
    },
    test("cancellation between PUSH callback validation and materialization prevents admission") {
      val interpreter = AsyncInterpreter.fromStream(Stream(1))
      interpreter.addPush[Int](JvmType.Int, JvmType.Int)(_ => Stream(2, 3).take(1))
      var operation: Async[Long]            = null
      var cancellation: Async.Running[Unit] = null
      interpreter.beforePushMaterializationForTest(() => cancellation = cancelActive(interpreter, operation))
      operation = interpreter.readInt(-1L)
      operation.asInstanceOf[Pollable[Long]].poll(noop)
      cancellation.block
      assertTrue(operation.block == -1L, interpreter.readInt(-2L).block == -2L)
    },
    test("ready budget yields immediately before synchronous push installation") {
      def maps(interpreter: AsyncInterpreter): Unit =
        (1 to 255).foreach(_ => interpreter.addAsyncMap[Int, Int](JvmType.Int, JvmType.Int)(n => Async.succeed(n)))

      val syncReads = new AtomicInteger
      val syncPush  = AsyncInterpreter.fromStream(Stream(1))
      maps(syncPush)
      syncPush.addPush[Int](JvmType.Int, JvmType.Int)(_ =>
        Stream.succeed(3).map { value => syncReads.incrementAndGet(); value }
      )

      val syncResult = syncPush.readInt(-1L)
      assertTrue(syncReads.get() == 0)
      assertTrue(syncResult.block == 3L, syncReads.get() == 1)
    },
    test("synchronous outgoing push and filter dispatch float and double lanes") {
      val floatPush = AsyncInterpreter.fromStream(Stream(1.5f))
      floatPush.addPush[Float](JvmType.Float, JvmType.Float)(Stream.succeed)
      floatPush.addPush[Float](JvmType.Float, JvmType.Float)(value => Stream(value + 1.0f))
      val doublePush = AsyncInterpreter.fromStream(Stream(2.5d))
      doublePush.addPush[Double](JvmType.Double, JvmType.Double)(Stream.succeed)
      doublePush.addPush[Double](JvmType.Double, JvmType.Double)(value => Stream(value + 1.0d))
      val floatFilter = AsyncInterpreter.fromStream(Stream(1.0f, 2.0f))
      floatFilter.addPush[Float](JvmType.Float, JvmType.Float)(Stream.succeed)
      floatFilter.addFilter[Float](JvmType.Float)(_ == 2.0f)
      val doubleFilter = AsyncInterpreter.fromStream(Stream(1.0d, 2.0d))
      doubleFilter.addPush[Double](JvmType.Double, JvmType.Double)(Stream.succeed)
      doubleFilter.addFilter[Double](JvmType.Double)(_ == 2.0d)
      assertTrue(
        floatPush.readFloat(-1d).block == 2.5d,
        doublePush.readDouble(-1d).block == 3.5d,
        floatFilter.readFloat(-1d).block == 2.0d,
        doubleFilter.readDouble(-1d).block == 2.0d
      )
    },
    test("rejected value captures a synchronous throw constructing the redriven read") {
      val calls   = new AtomicInteger
      val failure = new RuntimeException("redrive-construction")
      val source  = new LaneReader(JvmType.Int, Nil) {
        override def readInt(s: Long)(implicit ev: Any <:< Int): Async[Long] =
          if (calls.getAndIncrement() == 0) Async.succeed(1L) else throw failure
      }
      val interpreter = new AsyncInterpreter(source)
      interpreter.addFilter[Int](JvmType.Int)(_ => false)
      val first  = interpreter.readInt(-1L).either.block
      val sticky = interpreter.readInt(-2L).either.block
      assertTrue(first == Left(failure), sticky == Left(failure), calls.get() == 2)
    },
    test("rejected redrive yields fairly and observes closure at both continuation boundaries") {
      val rejected = AsyncInterpreter.fromStream(Stream.fromIterable(1 to 300))
      rejected.addFilter[Int](JvmType.Int)(_ => false)

      var afterAcceptedFilter: AsyncInterpreter = null
      afterAcceptedFilter = AsyncInterpreter.fromStream(Stream(1))
      afterAcceptedFilter.addPush[Int](JvmType.Int, JvmType.Int)(Stream.succeed)
      afterAcceptedFilter.addFilter[Int](JvmType.Int) { _ =>
        afterAcceptedFilter.closeForTest().block
        true
      }
      afterAcceptedFilter.addMap[Int, Int](JvmType.Int, JvmType.Int)(_ + 1)

      var afterRejectedFilter: AsyncInterpreter = null
      afterRejectedFilter = AsyncInterpreter.fromStream(Stream(1))
      afterRejectedFilter.addFilter[Int](JvmType.Int) { _ =>
        afterRejectedFilter.closeForTest().block
        false
      }

      assertTrue(
        rejected.readInt(-1L).block == -1L,
        afterAcceptedFilter.readInt(-2L).block == -2L,
        afterRejectedFilter.readInt(-3L).block == -3L
      )
    },
    test("reentrant cancellation is observed at completeRead source synchronization checkpoints") {
      val late    = new LateSuccess[Int](1)
      val resumed = AsyncInterpreter.fromStream(Stream(1))
      resumed.addAsyncMap[Int, Int](JvmType.Int, JvmType.Int)(_ => late)
      val resumedRead = resumed.readInt(-1L)
      resumedRead.asInstanceOf[Pollable[Long]].poll(noop)
      val resumedCancel = Async.cancelWithCleanup(resumedRead.asInstanceOf[Pollable[Long]])
      late.ready = true
      if (late.wake ne null) late.wake.run()

      var incoming: AsyncInterpreter = null
      incoming = AsyncInterpreter.fromStream(Stream(1))
      incoming.addAsyncMap[Int, Int](JvmType.Int, JvmType.Int)(_ =>
        Async
          .succeed(new java.lang.Number {
            def intValue(): Int       = { incoming.closeForTest().block; 1 }
            def longValue(): Long     = 1L
            def floatValue(): Float   = 1.0f
            def doubleValue(): Double = 1.0d
          })
          .asInstanceOf[Async[Int]]
      )
      incoming.addMap[Int, Int](JvmType.Int, JvmType.Int)(_ + 1)

      var incomingPush: AsyncInterpreter = null
      incomingPush = AsyncInterpreter.fromStream(Stream(1))
      incomingPush.addPush[Int](JvmType.Int, JvmType.Int) { _ => incomingPush.closeForTest().block; Stream.succeed(2) }

      var outgoing: AsyncInterpreter = null
      outgoing = AsyncInterpreter.fromStream(Stream(1))
      outgoing.addAsyncMap[Int, Int](JvmType.Int, JvmType.Int)(_ =>
        Async
          .succeed(new java.lang.Number {
            def intValue(): Int       = { outgoing.closeForTest().block; 1 }
            def longValue(): Long     = 1L
            def floatValue(): Float   = 1.0f
            def doubleValue(): Double = 1.0d
          })
          .asInstanceOf[Async[Int]]
      )
      outgoing.addPush[Int](JvmType.Int, JvmType.Int)(Stream.succeed)

      var outgoingPush: AsyncInterpreter = null
      outgoingPush = AsyncInterpreter.fromStream(Stream(1))
      outgoingPush.addPush[Int](JvmType.Int, JvmType.Int)(Stream.succeed)
      outgoingPush.addPush[Int](JvmType.Int, JvmType.Int) { _ =>
        outgoingPush.closeForTest().block; Stream.succeed(2)
      }

      assertTrue(
        resumedCancel.block == (),
        incoming.readInt(-1L).block == -1L,
        incomingPush.readInt(-1L).block == -1L,
        outgoing.readInt(-1L).block == -1L,
        outgoingPush.readInt(-1L).block == -1L
      )
    },
    test("defect recovery observes cancellation at the materialization handoff") {
      val trigger     = new RuntimeException("recovery-race")
      val gate        = new Gate[Option[Stream[_, Any]]]
      val interpreter = new AsyncInterpreter(new LaneReader(JvmType.AnyRef, Nil) {
        override def read[A >: Any](sentinel: A): Async[A] = Async.fail(trigger)
      })
      interpreter.addAsyncCatchDefect { case cause if cause eq trigger => gate }

      val operation = interpreter.read[Any]("eof")
      operation.asInstanceOf[Pollable[Any]].poll(noop)
      var cancellation: Async.Running[Unit] = null
      interpreter.beforeRecoveryMaterializationForTest(() =>
        cancellation = Async.cancelWithCleanup(operation.asInstanceOf[Pollable[Any]]).start
      )
      gate.succeed(Some(Stream.succeed("ignored": Any)))

      for {
        _     <- runAsync(cancellation)
        value <- runAsync(operation)
        next  <- runAsync(interpreter.read[Any]("next"))
      } yield assertTrue(value == "eof", next == "next")
    },
    test("cancellation after failed-owner close wins before recovery commit") {
      val trigger     = new RuntimeException("recovery-close-race")
      val closeGate   = new Gate[Unit]
      val interpreter = new AsyncInterpreter(new LaneReader(JvmType.AnyRef, Nil) {
        override def read[A >: Any](sentinel: A): Async[A] = Async.fail(trigger)
        override def close(): Async[Unit]                  = closeGate
      })
      interpreter.addAsyncCatchDefect {
        case cause if cause eq trigger =>
          Async.succeed(Some(Stream.succeed("ignored": Any)))
      }
      val operation = interpreter.read[Any]("cancelled")
      operation.asInstanceOf[Pollable[Any]].poll(noop)
      val cancelled            = new AtomicBoolean(false)
      val cancellationFinished = new CountDownLatch(1)
      interpreter.beforeRecoveryCloseCommitForTest { () =>
        try Async.cancelWithCleanup(operation.asInstanceOf[Pollable[Any]]).block
        finally {
          cancelled.set(true)
          cancellationFinished.countDown()
        }
      }
      closeGate.succeed(())
      val result             = operation.block
      val cancellationJoined = cancellationFinished.await(5, TimeUnit.SECONDS)
      assertTrue(
        cancellationJoined,
        cancelled.get(),
        result == "cancelled",
        interpreter.read[Any]("next").block == "next"
      )
    },
    test("cancellation after async error-map readiness wins before commit") {
      val gate               = new Gate[String]
      var first              = true
      val source: LaneReader = new LaneReader(JvmType.AnyRef, Nil) {
        override def read[A >: Any](sentinel: A): Async[A] =
          if (first) { first = false; Async.failTrusted(StreamError.source("source")) }
          else Async.succeed(sentinel)
      }
      val interpreter = new AsyncInterpreter(source)
      interpreter.addAsyncErrorMap[String, String](_ => gate)
      var operation: Async[Any]             = null
      var cancellation: Async.Running[Unit] = null
      operation = interpreter.read[Any]("cancelled")
      operation.asInstanceOf[Pollable[Any]].poll(noop)
      interpreter.beforeAsyncErrorMapCommitForTest(() => cancellation = cancelActive(interpreter, operation))
      gate.succeed("ignored")
      cancellation.block
      assertTrue(operation.block == "cancelled", interpreter.read[Any]("next").block == "next")
    },
    test("cancellation before nested close admission rolls back the inner owner") {
      val closes = new AtomicInteger
      val outer  = new LaneReader(JvmType.Int, List(9L))
      val inner  = new LaneReader(JvmType.Int, Nil) {
        override def close(): Async[Unit] = { closes.incrementAndGet(); Async.succeed(()) }
      }
      val interpreter = new AsyncInterpreter(outer)
      interpreter.installNestedFrameForTest(inner, JvmType.Int)
      var operation: Async[Long]            = null
      var cancellation: Async.Running[Unit] = null
      interpreter.beforeInnerCloseForTest(() => cancellation = cancelActive(interpreter, operation))
      operation = interpreter.readInt(-1L)
      operation.asInstanceOf[Pollable[Long]].poll(noop)
      cancellation.block
      val next = interpreter.readInt(-2L).block
      assertTrue(operation.block == -1L, next == 9L, closes.get() == 1)
    },
    test("interpreter reader delegates collection, lifecycle, bulk, and control surfaces") {
      val refs       = AsyncInterpreter.fromStream(Stream("a", "b")).toReader[String]
      val floats     = AsyncInterpreter.fromStream(Stream(1.0f, 2.0f)).toReader[Float]
      val controls   = AsyncInterpreter.fromStream(Stream(1, 2, 3)).toReader[Int]
      val dest       = new Array[Float](2)
      val ready      = refs.readable().block
      val open       = !refs.isClosed.block
      val values     = refs.readUpToN[String](2).block
      val n          = floats.readFloats(dest, 0, dest.length).block
      val skipped    = controls.skip(1).either.block
      val limited    = controls.setLimit(1).block
      val repeated   = controls.setRepeat().block
      val setSkipped = controls.setSkip(1).block
      floats.reset().block
      refs.close().block
      assertTrue(
        ready,
        open,
        values == zio.blocks.chunk.Chunk("a", "b"),
        n == 2,
        dest.sameElements(Array(1.0f, 2.0f)),
        skipped.isRight,
        limited,
        !repeated,
        setSkipped,
        refs.isClosed.block
      )
    },
    test("callback throws, null effects, reset, and long rejection runs are deterministic") {
      val reset = AsyncInterpreter.fromStream(Stream(1, 2))
      reset.addAsyncMap[Int, Int](JvmType.Int, JvmType.Int)(n => Async.succeed(n + 1))
      val yielding = AsyncInterpreter.fromStream(Stream.fromIterable(1 to 200))
      yielding.addAsyncFilter[Int](JvmType.Int)(n => Async.succeed(n == 200))
      for {
        a            <- runAsync(reset.readInt(-1))
        _            <- runAsync(reset.reset())
        replay       <- runAsync(reset.readInt(-1))
        accepted     <- runAsync(yielding.readInt(-1))
        nullResult   <- runAsync(failedMap(_ => null))
        thrownResult <- runAsync(failedMap(_ => throw new RuntimeException("boom")))
      } yield assertTrue(
        nullResult.isLeft,
        thrownResult.isLeft,
        a == 2L,
        replay == 1L,
        accepted == 200L
      )
    },
    test("filter and mapAccum construction defects, repoll defects, and double skip are normalized") {
      val filterFailure = new RuntimeException("filter-construction")
      val filter        = AsyncInterpreter.fromStream(Stream(1))
      filter.addAsyncFilter[Int](JvmType.Int)(_ => throw filterFailure)

      val accumFailure = new RuntimeException("accum-construction")
      val accum        = AsyncInterpreter.fromStream(Stream(1))
      accum.addAsyncMapAccum[Int, Int, Int](0, JvmType.Int, JvmType.Int)((_, _) => throw accumFailure)

      val repollFailure = new RuntimeException("repoll")
      val repoll        = new ThrowingRepoll(repollFailure)
      val polling       = new AsyncInterpreter(new LaneReader(JvmType.Int, Nil) {
        override def readInt(sentinel: Long)(implicit ev: Any <:< Int): Async[Long] = repoll
      })
      val pending = polling.readInt(-1L)
      pending.asInstanceOf[Pollable[Long]].poll(noop)
      repoll.wake.run()

      val doubles = AsyncInterpreter.fromStream(Stream(1.0d, 2.0d)).toReader[Double]

      assertTrue(
        filter.readInt(-1L).either.block == Left(filterFailure),
        accum.readInt(-1L).either.block == Left(accumFailure),
        pending.either.block == Left(repollFailure),
        doubles.skip(0L).block == (),
        doubles.skip(1L).block == (),
        doubles.readDouble(-1.0d).block == 2.0d
      )
    }
  )
}
