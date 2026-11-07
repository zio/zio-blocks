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

import zio.blocks.async._
import zio.blocks.chunk.Chunk
import zio.blocks.streams.internal.AsyncInterpreter
import zio.blocks.streams.io.Reader
import zio.test._

/**
 * Final JVM-only probes for the scalar interpreter's read/outgoing state
 * machine.
 */
object AsyncInterpreterReadFinalCoverageSpec extends StreamsBaseSpec {
  private val noop = new Runnable { def run(): Unit = () }

  private final class Gate[A] extends Async.Operation[A] {
    private val result                           = new Completer[A]
    private val polled                           = new CountDownLatch(1)
    val polls                                    = new AtomicInteger
    val cancels                                  = new AtomicInteger
    def poll(onComplete: Runnable): Async[A]     = { polls.incrementAndGet(); polled.countDown(); result.poll(onComplete) }
    protected def cancelOperation(): Async[Unit] = { cancels.incrementAndGet(); Async.succeed(()) }
    def awaitPoll(): Boolean                     = polled.await(5L, TimeUnit.SECONDS)
    def succeed(value: A): Unit                  = result.succeed(value)
    def fail(cause: Throwable): Unit             = result.fail(cause)
  }

  private class LaneReader(lane: JvmType, initial: List[Any], closeEffect: () => Async[Unit] = () => Async.succeed(()))
      extends Reader.AsyncReader[Any] {
    private var values                                             = initial
    private var closed                                             = false
    override def jvmType: JvmType                                  = lane
    override private[streams] def tryReadable: Reader.Availability =
      if (values.nonEmpty) Reader.Available else Reader.Unavailable
    def isClosed: Async[Boolean]               = Async.succeed(closed)
    def readable(): Async[Boolean]             = Async.succeed(values.nonEmpty)
    def close(): Async[Unit]                   = { closed = true; closeEffect() }
    override def reset(): Async[Unit]          = { values = initial; closed = false; Async.succeed(()) }
    private def next[A](sentinel: A): Async[A] = values match {
      case head :: tail => values = tail; Async.succeed(head.asInstanceOf[A])
      case Nil          => Async.succeed(sentinel)
    }
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

  private final class PendingLaneReader(override val jvmType: JvmType) extends Reader.AsyncReader[Any] {
    private val pending                                                                                        = new Gate[Any]
    def isClosed: Async[Boolean]                                                                               = Async.succeed(false)
    def readable(): Async[Boolean]                                                                             = Async.succeed(true)
    def close(): Async[Unit]                                                                                   = Async.succeed(())
    def read[A >: Any](sentinel: A): Async[A]                                                                  = pending.asInstanceOf[Async[A]]
    override def readBoolean(s: Int)(implicit ev: Any <:< Boolean): Async[Int]                                 = pending.asInstanceOf[Async[Int]]
    override def readByte(): Async[Int]                                                                        = pending.asInstanceOf[Async[Int]]
    override def readChar(s: Int)(implicit ev: Any <:< Char): Async[Int]                                       = pending.asInstanceOf[Async[Int]]
    override def readShort(s: Int)(implicit ev: Any <:< Short): Async[Int]                                     = pending.asInstanceOf[Async[Int]]
    override def readInt(s: Long)(implicit ev: Any <:< Int): Async[Long]                                       = pending.asInstanceOf[Async[Long]]
    override def readLong(s: Long)(implicit ev: Any <:< Long): Async[Long]                                     = pending.asInstanceOf[Async[Long]]
    override def readFloat(s: Double)(implicit ev: Any <:< Float): Async[Double]                               = pending.asInstanceOf[Async[Double]]
    override def readDouble(s: Double)(implicit ev: Any <:< Double): Async[Double]                             = pending.asInstanceOf[Async[Double]]
    override def readLongs(dest: Array[Long], offset: Int, length: Int)(implicit ev: Any <:< Long): Async[Int] =
      pending.asInstanceOf[Async[Int]]
    override def readDoubles(dest: Array[Double], offset: Int, length: Int)(implicit ev: Any <:< Double): Async[Int] =
      pending.asInstanceOf[Async[Int]]
  }

  private def chunkFor(lane: JvmType, values: List[Any]): Chunk[Any] =
    new AsyncInterpreter(new LaneReader(lane, values)).readN[Any](values.length).block

  def spec = suite("AsyncInterpreter final read coverage")(
    test("bulk zero and nonzero snapshots cover every primitive and reference lane") {
      val empty = List(
        JvmType.Boolean,
        JvmType.Byte,
        JvmType.Char,
        JvmType.Short,
        JvmType.Int,
        JvmType.Long,
        JvmType.Float,
        JvmType.Double,
        JvmType.AnyRef
      ).map(t => chunkFor(t, Nil))
      val full = List(
        chunkFor(JvmType.Boolean, List(1)),
        chunkFor(JvmType.Byte, List(2)),
        chunkFor(JvmType.Char, List(65)),
        chunkFor(JvmType.Short, List(3)),
        chunkFor(JvmType.Int, List(4L)),
        chunkFor(JvmType.Long, List(5L)),
        chunkFor(JvmType.Float, List(6.0d)),
        chunkFor(JvmType.Double, List(7.0d)),
        chunkFor(JvmType.AnyRef, List("r"))
      )
      assertTrue(
        empty.forall(_.isEmpty),
        full.map(_.head) == List(true, 2.toByte, 'A', 3.toShort, 4, 5L, 6.0f, 7.0d, "r")
      )
    },
    test("bulk cancellation snapshots every primitive and reference builder") {
      val results = List(
        JvmType.Boolean,
        JvmType.Byte,
        JvmType.Char,
        JvmType.Short,
        JvmType.Int,
        JvmType.Long,
        JvmType.Float,
        JvmType.Double,
        JvmType.AnyRef
      ).map { lane =>
        val operation = new AsyncInterpreter(new PendingLaneReader(lane)).readN[Any](1)
        operation.asInstanceOf[Pollable[Chunk[Any]]].poll(noop)
        Async.cancelWithCleanup(operation.asInstanceOf[Pollable[Chunk[Any]]]).block
        operation.block
      }
      assertTrue(results.forall(_.isEmpty))
    },
    test("unstarted chunk cancellation and partially committed array cancellation return their snapshots") {
      val unstarted = AsyncInterpreter.fromStream(Stream(1)).readN[Int](1)
      Async.cancelWithCleanup(unstarted.asInstanceOf[Pollable[Chunk[Int]]]).block

      val gate   = new Gate[Long]
      val calls  = new AtomicInteger
      val source = new LaneReader(JvmType.Int, List(1L, 2L)) {
        override def readInt(s: Long)(implicit ev: Any <:< Int): Async[Long] =
          if (calls.getAndIncrement() == 0) super.readInt(s) else gate
      }
      val values        = Array.fill(3)(-1)
      val partial       = new AsyncInterpreter(source).readInts(values, 0, values.length)
      var partialResult = Int.MinValue
      val driver        = new Thread(() => partialResult = partial.block)
      driver.start()
      val reachedPending = gate.awaitPoll()
      if (reachedPending) Async.cancelWithCleanup(partial.asInstanceOf[Pollable[Int]]).block
      driver.join()

      assertTrue(reachedPending, unstarted.block.isEmpty, partialResult == 1, values.sameElements(Array(1, -1, -1)))
    },
    test("Boolean and Char scalar conversions cross primitive and reference outgoing lanes") {
      val boolean = new AsyncInterpreter(new LaneReader(JvmType.Boolean, List(1)))
      boolean.addMap[Boolean, AnyRef](JvmType.Boolean, JvmType.AnyRef)(b => Boolean.box(b))
      val char = new AsyncInterpreter(new LaneReader(JvmType.Char, List(90)))
      char.addMap[Char, Int](JvmType.Char, JvmType.Int)(_.toInt)
      assertTrue(boolean.read[AnyRef](null).block == Boolean.box(true), char.readInt(-1L).block == 90L)
    },
    test("maps-only outgoing and post-push bridge retain lane values") {
      val bridged = new AsyncInterpreter(new LaneReader(JvmType.Int, List(1L)))
      bridged.addMap[Long, Long](JvmType.Long, JvmType.Long)(_ + 1L)
      val maps = AsyncInterpreter.fromStream(Stream(1))
      maps.addMap[Int, Long](JvmType.Int, JvmType.Long)(_.toLong + 1L)
      maps.addMap[Long, AnyRef](JvmType.Long, JvmType.AnyRef)(Long.box)
      val pushed = AsyncInterpreter.fromStream(Stream(2))
      pushed.addPush[Int](JvmType.Int, JvmType.Int)(n => Stream(n + 1))
      pushed.addMap[Long, Char](JvmType.Long, JvmType.Char)(n => (n + 64L).toChar)
      val incomingMapsOnly = maps.setLimit(1).block
      val outgoingMapsOnly = pushed.setLimit(1).block
      assertTrue(
        bridged.readLong(-1L).block == 2L,
        incomingMapsOnly,
        outgoingMapsOnly,
        maps.read[AnyRef](null).block == Long.box(2L),
        pushed.readChar(-1).block == 67
      )
    },
    test("reset nested readers and skip zero or EOF restore the outer reader") {
      val outer = new LaneReader(JvmType.Int, List(9L))
      val inner = new LaneReader(JvmType.Int, Nil)
      val i     = new AsyncInterpreter(outer)
      i.installNestedFrameForTest(inner, JvmType.Int)
      assertTrue(i.skip(0).block == (), i.readInt(-1L).block == 9L, inner.isClosed.block)
      i.reset().block
      assertTrue(i.skip(10).block == (), i.readInt(-7L).block == -7L)
    },
    test("reset closes a still-installed nested reader") {
      val closes = new AtomicInteger
      val outer  = new LaneReader(JvmType.Int, List(9L))
      val inner  = new LaneReader(JvmType.Int, Nil) {
        override def close(): Async[Unit] = { closes.incrementAndGet(); Async.succeed(()) }
      }
      val interpreter = new AsyncInterpreter(outer)
      interpreter.installNestedFrameForTest(inner, JvmType.Int)
      interpreter.reset().block
      assertTrue(closes.get() == 1, interpreter.readInt(-1L).block == 9L)
    },
    test("BulkYield is polled, run, and cancelled during long bulk and skip operations") {
      val values = (1 to 700).map(_.toLong).toList
      val i      = new AsyncInterpreter(new LaneReader(JvmType.Int, values))
      val dest   = new Array[Int](700)
      val read   = i.readInts(dest, 0, dest.length)
      read.asInstanceOf[Pollable[Int]].poll(noop)
      val result = read.block
      val skip   = new AsyncInterpreter(new LaneReader(JvmType.Int, values)).skip(700)
      skip.asInstanceOf[Pollable[Unit]].poll(noop)
      val cancelled = Async.cancelWithCleanup(skip.asInstanceOf[Pollable[Unit]]).block
      assertTrue(result == 700, dest.head == 1, dest.last == 700, cancelled == ())
    },
    test("BulkYield schedules only once and suppresses publication after cancellation") {
      val (completed, completedTask) = AsyncInterpreter.bulkYieldForTest(forceMacrotask = false)
      completed.poll(noop)
      completed.poll(noop)
      completedTask.run()
      val (cancelled, cancelledTask) = AsyncInterpreter.bulkYieldForTest(forceMacrotask = true)
      val cancellation               = Async.cancelWithCleanup(cancelled)
      cancelled.poll(noop)
      cancelledTask.run()
      assertTrue(completed.poll(noop).block == (), cancellation.block == ())
    },
    test("rejected reader controls and concurrent read rejection are stable") {
      val gate   = new Gate[Long]
      val source = new LaneReader(JvmType.Int, Nil) {
        override def isClosed: Async[Boolean]                                = throw new RuntimeException("closed-construction")
        override def readable(): Async[Boolean]                              = throw new RuntimeException("readable-construction")
        override def readInt(s: Long)(implicit ev: Any <:< Int): Async[Long] = gate
      }
      val i     = new AsyncInterpreter(source)
      val first = i.readInt(-1L)
      first.asInstanceOf[Pollable[Long]].poll(noop)
      val second = i.readInt(-2L).either.block
      gate.succeed(11L)
      assertTrue(second.isLeft, first.block == 11L, i.isClosed.either.block.isLeft, i.readable().either.block.isLeft)
    },
    test("pending repoll, stale completion, throwing leaf, and observer throw do not corrupt the next read") {
      val gate   = new Gate[Long]
      val calls  = new AtomicInteger
      val source = new LaneReader(JvmType.Int, Nil) {
        override def readInt(s: Long)(implicit ev: Any <:< Int): Async[Long] =
          if (calls.getAndIncrement() == 0) gate
          else if (calls.get() == 2) throw new RuntimeException("leaf")
          else Async.succeed(13L)
      }
      val i       = new AsyncInterpreter(source)
      val pending = i.readInt(-1L)
      pending.asInstanceOf[Pollable[Long]].poll(noop)
      pending
        .asInstanceOf[Pollable[Long]]
        .poll(new Runnable { def run(): Unit = throw new RuntimeException("observer") })
      try gate.succeed(12L)
      catch { case _: RuntimeException => () }
      val failed = i.readInt(-2L).either.block
      val sticky = i.readInt(-3L).either.block
      assertTrue(pending.block == 12L, failed.isLeft, sticky.isLeft, gate.polls.get() == 1)
    },
    test("scan conversion race and null mapAccum tuple fail without stale lane commits") {
      val scanGate = new Gate[AnyRef]
      val scan     = AsyncInterpreter.fromReaderWithAsyncScanForTest[AnyRef, Int](
        new LaneReader(JvmType.Int, List(1L)),
        "0",
        JvmType.Int,
        JvmType.AnyRef
      )((_, _) => scanGate)
      val initial = scan.read[AnyRef](null).block
      val pull    = scan.read[AnyRef](null)
      pull.asInstanceOf[Pollable[AnyRef]].poll(noop)
      val cancelled = Async.cancelWithCleanup(pull.asInstanceOf[Pollable[AnyRef]])
      scanGate.succeed("1")
      val accum = AsyncInterpreter.fromStream(Stream(1))
      accum.addAsyncMapAccum[Int, Int, Int](0, JvmType.Int, JvmType.Int)((_, _) => Async.succeed(null))
      assertTrue(initial == "0", cancelled.block == (), accum.readInt(-1L).either.block.isLeft)
    },
    test("async callback construction failures become sticky") {
      val map = AsyncInterpreter.fromStreamWithAsyncMapForTest[Int, Int](Stream(1), JvmType.Int, JvmType.Int)(_ =>
        throw new RuntimeException("map-construction")
      )
      assertTrue(
        map.readInt(-1L).either.block.isLeft,
        map.readInt(-2L).either.block.isLeft
      )
    },
    test("nested close success, failure, rollback, and cleanup throws preserve the primary failure") {
      val cleanup     = new RuntimeException("cleanup")
      val closeThrows = new LaneReader(JvmType.Int, Nil, () => throw cleanup)
      val closeFails  = new LaneReader(JvmType.Int, Nil, () => Async.fail(cleanup))
      val closeOk     = new LaneReader(JvmType.Int, Nil)
      val i           = new AsyncInterpreter(new LaneReader(JvmType.Int, Nil))
      val thrown      = try { i.closeReadersForTest(closeThrows).block; None }
      catch { case t: Throwable => Some(t) }
      val failed = i.closeReadersForTest(closeOk, closeFails).either.block
      assertTrue(thrown.contains(cleanup), failed == Left(cleanup), closeOk.isClosed.block, closeFails.isClosed.block)
    },
    test("incoming capacity reaches the 15-bit edge and scheduled redrive eventually yields") {
      val i = AsyncInterpreter.fromStream(Stream(1))
      var n = 0
      while (n < 32760) { i.addMap[Int, Int](JvmType.Int, JvmType.Int)(identity); n += 1 }
      val filtered = AsyncInterpreter.fromStream(Stream.fromIterable(1 to 700))
      filtered.addAsyncFilter[Int](JvmType.Int)(v => Async.succeed(v == 700))
      assertTrue(i.readInt(-1L).block == 1L, filtered.readInt(-1L).block == 700L, i.incomingCount <= 32767)
    },
    test("push installation rejects the two reserved incoming slots past the 15-bit limit") {
      val interpreter = AsyncInterpreter.fromStream(Stream(1))
      var n           = 0
      while (n < 32764) { interpreter.addMap[Int, Int](JvmType.Int, JvmType.Int)(identity); n += 1 }
      interpreter.addPush[Int](JvmType.Int, JvmType.Int)(value => Stream.fromIterable(List(value)))
      val result = interpreter.readInt(-1L).either.block
      assertTrue(
        result.left.exists(
          _.getMessage ==
            "Stream pipeline too deep: 32768 incoming operations exceeds the maximum of 32767. " +
            "Simplify the stream composition or reduce flatMap nesting depth."
        )
      )
    },
    test("outgoing construction rejects an operation past the 15-bit limit") {
      val interpreter = AsyncInterpreter.fromStream(Stream(1))
      interpreter.addPush[Int](JvmType.Int, JvmType.Int)(Stream.succeed)
      var n = 0
      while (n < 32767) { interpreter.addMap[Int, Int](JvmType.Int, JvmType.Int)(identity); n += 1 }
      val failure = try {
        interpreter.addMap[Int, Int](JvmType.Int, JvmType.Int)(identity)
        None
      } catch { case cause: IllegalStateException => Some(cause) }
      assertTrue(failure.exists(_.getMessage == "Stream pipeline too deep: 32768 outgoing operations"))
    }
  )
}
