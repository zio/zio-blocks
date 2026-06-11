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

package zio.blocks.streams.internal

import zio.blocks.ringbuffer.{
  DoubleSpscRingBuffer,
  FloatSpscRingBuffer,
  IntSpscRingBuffer,
  LongSpscRingBuffer,
  SpscRingBuffer
}
import zio.blocks.streams.{JvmType, Platform}
import zio.blocks.streams.io.Reader

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.{AtomicReference, AtomicReferenceArray}
import java.util.concurrent.locks.LockSupport

/**
 * `Int`-specialized concurrent `mapPar` reader.
 *
 * The per-worker input queue is `IntSpscRingBuffer`; the worker reads primitive
 * `Int` values directly without boxing.
 *
 * The per-worker output queue is selected at construction time based on
 * `outType`:
 *   - `JvmType.Int` → `IntSpscRingBuffer`
 *   - `JvmType.Long` → `LongSpscRingBuffer`
 *   - `JvmType.Float` → `FloatSpscRingBuffer`
 *   - `JvmType.Double` → `DoubleSpscRingBuffer`
 *   - everything else → `SpscRingBuffer[AnyRef]` (boxed fallback for reference
 *     types and unspecialized primitives like `Byte`/`Short`/etc.)
 *
 * When the output queue is primitive there is **zero allocation** on the
 * worker-to-consumer hot path. End-of-stream is signalled with the in-band DONE
 * sentinel of each output queue; the consumer counts DONE markers and
 * terminates once all `n` workers have signalled.
 *
 * For the `Long` output specialization, `Long.MinValue` (EMPTY) and
 * `Long.MinValue + 1L` (DONE) are reserved ring sentinels but are nonetheless
 * valid, observable `Long` values. A mapping function returning either is
 * carried losslessly: the value is routed out-of-band through `outLongEscapes`
 * and signalled with a `DONE` token (a `DONE` with a non-null escape payload is
 * the escaped value; with no payload it is the real worker-done marker).
 */
private[streams] final class IntConcurrentMapParReader[B](
  upstream: Reader[Int],
  n: Int,
  f: Int => B,
  bufferSize: Int,
  outType: JvmType
) extends Reader[B] {
  import ConcurrentMapParReader._

  require(n >= 1, s"IntConcurrentMapParReader requires n >= 1, got $n")

  // Concrete primitive function types let the JVM use `Function1` specialization
  // (e.g. `apply$mcII$sp`) on the worker hot path.
  private val intToIntFn: (Int => Int) =
    if (outType eq JvmType.Int) f.asInstanceOf[Int => Int] else null
  private val intToLongFn: (Int => Long) =
    if (outType eq JvmType.Long) f.asInstanceOf[Int => Long] else null
  private val intToDoubleFn: (Int => Double) =
    if (outType eq JvmType.Double) f.asInstanceOf[Int => Double] else null
  private val intToFloatFn: (Int => Float) =
    if (outType eq JvmType.Float) f.asInstanceOf[Int => Float] else null

  // Output queues: exactly one of these is non-null per instance. The queue
  // fields (and `inputQueues`) are reassigned on `reset()` so `mapPar` can
  // replay a resettable upstream (e.g. under `repeated`); their null/non-null
  // pattern never changes after construction. All such reassignment happens on
  // the consumer thread only after the previous run's threads have fully
  // terminated (see `reset()`), so single-threaded mutation is safe there.
  private var outIntQs: Array[IntSpscRingBuffer] =
    if (outType eq JvmType.Int) Array.tabulate(n)(_ => new IntSpscRingBuffer(bufferSize)) else null
  private var outLongQs: Array[LongSpscRingBuffer] =
    if (outType eq JvmType.Long) Array.tabulate(n)(_ => new LongSpscRingBuffer(bufferSize)) else null
  // Out-of-band escape channel for reserved Long OUTPUT values (only when outputting Long).
  private var outLongEscapes: Array[ConcurrentLinkedQueue[java.lang.Long]] =
    if (outType eq JvmType.Long) Array.tabulate(n)(_ => new ConcurrentLinkedQueue[java.lang.Long]()) else null
  private var outFloatQs: Array[FloatSpscRingBuffer] =
    if (outType eq JvmType.Float) Array.tabulate(n)(_ => new FloatSpscRingBuffer(bufferSize)) else null
  private var outDoubleQs: Array[DoubleSpscRingBuffer] =
    if (outType eq JvmType.Double) Array.tabulate(n)(_ => new DoubleSpscRingBuffer(bufferSize)) else null
  private var outRefQs: Array[SpscRingBuffer[AnyRef]] =
    if ((outIntQs eq null) && (outLongQs eq null) && (outFloatQs eq null) && (outDoubleQs eq null))
      Array.tabulate(n)(_ => new SpscRingBuffer[AnyRef](bufferSize))
    else null

  @volatile private var consumerWaiter: Thread = null

  private var inputQueues: Array[IntSpscRingBuffer] =
    Array.tabulate(n)(_ => new IntSpscRingBuffer(bufferSize))
  private val workerWaiters = new AtomicReferenceArray[Thread](n)
  private val workerThreads = new AtomicReferenceArray[Thread](n)

  private val errorRef                            = new AtomicReference[Throwable](null)
  @volatile private var errorDelivered: Boolean   = false
  @volatile private var consumerClosed: Boolean   = false
  @volatile private var coordinatorThread: Thread = null.asInstanceOf[Thread]

  private val workerTasks: Array[Runnable] = Array.tabulate(n) { idx =>
    new Runnable {
      def run(): Unit = workerLoop(idx)
    }
  }

  private val coordinatorTask: Runnable = new Runnable {
    def run(): Unit =
      try {
        val coordBuf = new Array[Int](bufferSize)
        var scanIdx  = 0
        var running  = true

        while (running && !consumerClosed && !Thread.currentThread().isInterrupted) {
          val count = upstream.readInts(coordBuf, 0, bufferSize)(unsafeEvidence)
          if (count <= 0) {
            running = false
          } else {
            var j = 0
            while (j < count && running && !consumerClosed && !Thread.currentThread().isInterrupted) {
              val intVal     = coordBuf(j)
              var dispatched = false
              while (!dispatched && !consumerClosed && !Thread.currentThread().isInterrupted) {
                var i = 0
                while (i < n && !dispatched) {
                  val qIdx = (scanIdx + i) % n
                  if (inputQueues(qIdx).offer(intVal)) {
                    val ww = workerWaiters.get(qIdx)
                    if (ww ne null) LockSupport.unpark(ww)
                    scanIdx = (qIdx + 1) % n
                    dispatched = true
                  }
                  i += 1
                }
                if (!dispatched) {
                  LockSupport.parkNanos(this, 1000L)
                }
              }
              if (!dispatched) running = false
              j += 1
            }
          }
        }
      } catch {
        case t: Throwable =>
          recordError(t)
      } finally {
        signalWorkersToStop()
        // A close failure must surface (Principle 4): record it so the
        // consumer rethrows it from read() or close().
        try upstream.close()
        catch { case t: Throwable => recordError(t) }
      }
  }

  // Spawns the worker pool and the coordinator. Called from the constructor
  // and from `reset()` after all per-run fields have been reinitialized (the
  // `Runnable`s read instance fields, so they are reusable across runs).
  private def startThreads(): Unit = {
    Array.tabulate(n) { idx =>
      Platform.startVirtualThread(
        s"zio-blocks-mappar-worker-${counter.getAndIncrement()}-$idx",
        workerTasks(idx)
      )
    }
    coordinatorThread = Platform.startVirtualThread(
      s"zio-blocks-mappar-coordinator-${counter.getAndIncrement()}",
      coordinatorTask
    )
  }

  startThreads()

  def isClosed: Boolean = eofReturned || consumerClosed

  // Consumer-private state.
  private var scanStart: Int        = 0
  private var workersDoneCount: Int = 0
  private var eofReturned: Boolean  = false

  override def jvmType: JvmType = outType

  // ============================================================
  //  READ DISPATCH
  // ============================================================

  def read[B1 >: B](sentinel: B1): B1 =
    if (outIntQs ne null) {
      val v = pollIntOnce(IntSpscRingBuffer.EMPTY_PACKED)
      if (v == IntSpscRingBuffer.EMPTY_PACKED) sentinel
      else Int.box(v.toInt).asInstanceOf[B1]
    } else if (outLongQs ne null) {
      val v = pollLongOnce(LongSpscRingBuffer.EMPTY)
      if (lastReadWasEOF) sentinel
      else Long.box(v).asInstanceOf[B1]
    } else if (outFloatQs ne null) {
      val packed = pollFloatPacked()
      if (packed == FloatSpscRingBuffer.EMPTY_PACKED) sentinel
      else Float.box(java.lang.Float.intBitsToFloat(packed.toInt)).asInstanceOf[B1]
    } else if (outDoubleQs ne null) {
      val packed = pollDoublePacked()
      if (packed == DoubleSpscRingBuffer.EMPTY_BITS) sentinel
      else Double.box(java.lang.Double.longBitsToDouble(packed)).asInstanceOf[B1]
    } else {
      readRef(sentinel)
    }

  override def readInt(sentinel: Long)(implicit ev: B <:< Int): Long =
    if (outIntQs ne null) {
      val v = pollIntOnce(IntSpscRingBuffer.EMPTY_PACKED)
      if (v == IntSpscRingBuffer.EMPTY_PACKED) sentinel
      else v.toInt.toLong
    } else readIntFromRef(sentinel)

  override def readLong(sentinel: Long)(implicit ev: B <:< Long): Long =
    if (outLongQs ne null) {
      // pollLongOnce sets lastReadWasEOF authoritatively (a real value may equal
      // the EMPTY marker once reserved values are escaped).
      val v = pollLongOnce(LongSpscRingBuffer.EMPTY)
      if (lastReadWasEOF) sentinel else v
    } else readLongFromRef(sentinel)

  override def readFloat(sentinel: Double)(implicit ev: B <:< Float): Double =
    if (outFloatQs ne null) {
      val packed = pollFloatPacked()
      if (packed == FloatSpscRingBuffer.EMPTY_PACKED) sentinel
      else java.lang.Float.intBitsToFloat(packed.toInt).toDouble
    } else readFloatFromRef(sentinel)

  override def readDouble(sentinel: Double)(implicit ev: B <:< Double): Double =
    if (outDoubleQs ne null) {
      val packed = pollDoublePacked()
      if (packed == DoubleSpscRingBuffer.EMPTY_BITS) { markReadEOF(); sentinel }
      else { markReadValue(); java.lang.Double.longBitsToDouble(packed) }
    } else readDoubleFromRef(sentinel)

  // ============================================================
  //  PRIMITIVE OUTPUT POLLERS (zero-allocation hot paths)
  // ============================================================

  /**
   * Drives the consumer loop on an `Array[IntSpscRingBuffer]`. Returns the
   * packed `Long` data slot (always non-empty) on success, or the supplied
   * `emptyMarker` on real EOS (after counting all `n` worker DONE sentinels).
   *
   * The returned packed value is `TAG_FULL | (a & LOW32_MASK)` for data, so the
   * caller decodes via `.toInt`.
   */
  private def pollIntOnce(emptyMarker: Long): Long = {
    val self = Thread.currentThread()
    while (true) {
      consumerWaiter = self
      try {
        var i      = 0
        var sawAny = false
        while (i < n) {
          val qIdx   = (scanStart + i) % n
          val packed = outIntQs(qIdx).pollPacked()
          if (packed == IntSpscRingBuffer.DONE_PACKED) {
            workersDoneCount += 1
            sawAny = true
          } else if (packed != IntSpscRingBuffer.EMPTY_PACKED) {
            sawAny = true
            scanStart = (qIdx + 1) % n
            val err = errorRef.get()
            if (err ne null) rethrow(err)
            return packed
          }
          i += 1
        }
        val err = errorRef.get()
        if (err ne null) rethrow(err)
        if (workersDoneCount >= n) { eofReturned = true; return emptyMarker }
        if (!sawAny && errorRef.get() == null) LockSupport.park(this)
      } finally {
        consumerWaiter = null
      }
    }
    emptyMarker
  }

  // A real Long output value can equal `emptyMarker` (Long.MinValue) once
  // reserved values are escaped, so EOF cannot be inferred from the return value
  // alone. This poller sets the reader's `lastReadWasEOF` flag authoritatively
  // (`markReadValue` on data, `markReadEOF` on EOF); callers must consult that
  // flag rather than comparing the returned value to `emptyMarker`.
  private def pollLongOnce(emptyMarker: Long): Long = {
    val self = Thread.currentThread()
    while (true) {
      consumerWaiter = self
      try {
        var i      = 0
        var sawAny = false
        while (i < n) {
          val qIdx   = (scanStart + i) % n
          val packed = outLongQs(qIdx).pollPacked()
          if (packed == LongSpscRingBuffer.DONE) {
            val esc = outLongEscapes(qIdx).poll()
            if (esc ne null) {
              sawAny = true
              scanStart = (qIdx + 1) % n
              val err = errorRef.get()
              if (err ne null) rethrow(err)
              markReadValue()
              return esc.longValue()
            } else {
              workersDoneCount += 1
              sawAny = true
            }
          } else if (packed != LongSpscRingBuffer.EMPTY) {
            sawAny = true
            scanStart = (qIdx + 1) % n
            val err = errorRef.get()
            if (err ne null) rethrow(err)
            markReadValue()
            return packed
          }
          i += 1
        }
        val err = errorRef.get()
        if (err ne null) rethrow(err)
        if (workersDoneCount >= n) { eofReturned = true; markReadEOF(); return emptyMarker }
        if (!sawAny && errorRef.get() == null) LockSupport.park(this)
      } finally {
        consumerWaiter = null
      }
    }
    markReadEOF()
    emptyMarker
  }

  /** Returns `FloatSpscRingBuffer.EMPTY_PACKED` (0L) on real EOS. */
  private def pollFloatPacked(): Long = {
    val self = Thread.currentThread()
    while (true) {
      consumerWaiter = self
      try {
        var i      = 0
        var sawAny = false
        while (i < n) {
          val qIdx   = (scanStart + i) % n
          val packed = outFloatQs(qIdx).pollPacked()
          if (packed == FloatSpscRingBuffer.DONE_PACKED) {
            workersDoneCount += 1
            sawAny = true
          } else if (packed != FloatSpscRingBuffer.EMPTY_PACKED) {
            sawAny = true
            scanStart = (qIdx + 1) % n
            val err = errorRef.get()
            if (err ne null) rethrow(err)
            return packed
          }
          i += 1
        }
        val err = errorRef.get()
        if (err ne null) rethrow(err)
        if (workersDoneCount >= n) { eofReturned = true; return FloatSpscRingBuffer.EMPTY_PACKED }
        if (!sawAny && errorRef.get() == null) LockSupport.park(this)
      } finally {
        consumerWaiter = null
      }
    }
    FloatSpscRingBuffer.EMPTY_PACKED
  }

  /** Returns `DoubleSpscRingBuffer.EMPTY_BITS` on real EOS. */
  private def pollDoublePacked(): Long = {
    val self = Thread.currentThread()
    while (true) {
      consumerWaiter = self
      try {
        var i      = 0
        var sawAny = false
        while (i < n) {
          val qIdx   = (scanStart + i) % n
          val packed = outDoubleQs(qIdx).pollPacked()
          if (packed == DoubleSpscRingBuffer.DONE_BITS) {
            workersDoneCount += 1
            sawAny = true
          } else if (packed != DoubleSpscRingBuffer.EMPTY_BITS) {
            sawAny = true
            scanStart = (qIdx + 1) % n
            val err = errorRef.get()
            if (err ne null) rethrow(err)
            return packed
          }
          i += 1
        }
        val err = errorRef.get()
        if (err ne null) rethrow(err)
        if (workersDoneCount >= n) { eofReturned = true; return DoubleSpscRingBuffer.EMPTY_BITS }
        if (!sawAny && errorRef.get() == null) LockSupport.park(this)
      } finally {
        consumerWaiter = null
      }
    }
    DoubleSpscRingBuffer.EMPTY_BITS
  }

  // ============================================================
  //  REFERENCE-OUTPUT POLLERS (used when output type is not Int/Long/Float/Double)
  // ============================================================

  private def readRef[B1 >: B](sentinel: B1): B1 = {
    val self = Thread.currentThread()
    while (true) {
      consumerWaiter = self
      try {
        var i      = 0
        var sawAny = false
        while (i < n) {
          val qIdx = (scanStart + i) % n
          val item = outRefQs(qIdx).take()
          if (item ne null) {
            sawAny = true
            scanStart = (qIdx + 1) % n
            val err = errorRef.get()
            if (err ne null) rethrow(err)
            if (item eq NullSentinel) return null.asInstanceOf[B1]
            if (item eq AllWorkersDone) workersDoneCount += 1
            else return item.asInstanceOf[B1]
          }
          i += 1
        }
        val err = errorRef.get()
        if (err ne null) rethrow(err)
        if (workersDoneCount >= n) { eofReturned = true; return sentinel }
        if (!sawAny && errorRef.get() == null) LockSupport.park(this)
      } finally {
        consumerWaiter = null
      }
    }
    sentinel
  }

  private def readIntFromRef(sentinel: Long): Long = {
    val v = readRef[Any](EndOfStream)
    if (v.asInstanceOf[AnyRef] eq EndOfStream) sentinel
    else v.asInstanceOf[java.lang.Number].intValue().toLong
  }
  private def readLongFromRef(sentinel: Long): Long = {
    val v = readRef[Any](EndOfStream)
    if (v.asInstanceOf[AnyRef] eq EndOfStream) { markReadEOF(); sentinel }
    else { markReadValue(); v.asInstanceOf[java.lang.Number].longValue() }
  }
  private def readFloatFromRef(sentinel: Double): Double = {
    val v = readRef[Any](EndOfStream)
    if (v.asInstanceOf[AnyRef] eq EndOfStream) sentinel
    else v.asInstanceOf[java.lang.Number].floatValue().toDouble
  }
  private def readDoubleFromRef(sentinel: Double): Double = {
    val v = readRef[Any](EndOfStream)
    if (v.asInstanceOf[AnyRef] eq EndOfStream) { markReadEOF(); sentinel }
    else { markReadValue(); v.asInstanceOf[java.lang.Number].doubleValue() }
  }

  // ============================================================
  //  CLOSE / WORKER / ERROR PLUMBING
  // ============================================================

  def close(): Unit = {
    consumerClosed = true
    val cw = consumerWaiter
    if (cw ne null) LockSupport.unpark(cw)
    var i = 0
    while (i < n) {
      val ww = workerWaiters.get(i)
      if (ww ne null) LockSupport.unpark(ww)
      i += 1
    }

    val ct = coordinatorThread
    if (ct ne null) {
      ct.interrupt()
      ct.join(5000)
    }

    i = 0
    while (i < n) {
      val t = workerThreads.get(i)
      if (t ne null) {
        t.interrupt()
        t.join(5000)
      }
      i += 1
    }

    // A recorded error the consumer never observed via a read (e.g. an
    // upstream close failure after the last element) must still surface
    // (Principle 4): rethrow it exactly once at teardown.
    val err = errorRef.get()
    if ((err ne null) && !errorDelivered) { errorDelivered = true; rethrow(err) }
  }

  override def reset(): Unit = {
    // `mapPar` is a pure decoupling transform: it must not weaken
    // replayability.
    // 1) Fully terminate the current run, exactly as close() does — but discard
    //    any recorded error instead of rethrowing it (reset starts a fresh
    //    run). `Thread.join` establishes happens-before with the coordinator's
    //    and workers' termination, making the subsequent single-threaded
    //    mutation of the per-run fields safe.
    consumerClosed = true
    val cw = consumerWaiter
    if (cw ne null) LockSupport.unpark(cw)
    var i = 0
    while (i < n) {
      val ww = workerWaiters.get(i)
      if (ww ne null) LockSupport.unpark(ww)
      i += 1
    }
    val ct = coordinatorThread
    if (ct ne null) {
      ct.interrupt()
      ct.join(5000)
    }
    i = 0
    while (i < n) {
      val t = workerThreads.get(i)
      if (t ne null) {
        t.interrupt()
        t.join(5000)
      }
      i += 1
    }
    // 2) Replay the upstream. A genuine one-shot source throws
    //    UnsupportedOperationException here, which correctly propagates: a
    //    mapPar over a one-shot source is itself one-shot. (The coordinator
    //    already closed `upstream` in its finally block; resettable readers
    //    re-enable reads.)
    upstream.reset()
    // 3) Reinstate fresh per-run state (preserving the output-queue null/
    //    non-null pattern selected at construction) and respawn the threads.
    if (outIntQs ne null) outIntQs = Array.tabulate(n)(_ => new IntSpscRingBuffer(bufferSize))
    if (outLongQs ne null) {
      outLongQs = Array.tabulate(n)(_ => new LongSpscRingBuffer(bufferSize))
      outLongEscapes = Array.tabulate(n)(_ => new ConcurrentLinkedQueue[java.lang.Long]())
    }
    if (outFloatQs ne null) outFloatQs = Array.tabulate(n)(_ => new FloatSpscRingBuffer(bufferSize))
    if (outDoubleQs ne null) outDoubleQs = Array.tabulate(n)(_ => new DoubleSpscRingBuffer(bufferSize))
    if (outRefQs ne null) outRefQs = Array.tabulate(n)(_ => new SpscRingBuffer[AnyRef](bufferSize))
    inputQueues = Array.tabulate(n)(_ => new IntSpscRingBuffer(bufferSize))
    i = 0
    while (i < n) {
      workerWaiters.set(i, null)
      workerThreads.set(i, null)
      i += 1
    }
    errorRef.set(null)
    errorDelivered = false
    consumerClosed = false
    consumerWaiter = null
    scanStart = 0
    workersDoneCount = 0
    eofReturned = false
    startThreads()
  }

  private def workerLoop(idx: Int): Unit = {
    val self = Thread.currentThread()
    workerThreads.set(idx, self)

    var keepRunning = true

    while (keepRunning && !consumerClosed && !self.isInterrupted) {
      var packed = inputQueues(idx).pollPacked()
      if (packed == IntSpscRingBuffer.EMPTY_PACKED) {
        workerWaiters.set(idx, self)
        packed = inputQueues(idx).pollPacked()
        if (packed == IntSpscRingBuffer.EMPTY_PACKED) {
          if (!consumerClosed && !self.isInterrupted) LockSupport.park(this)
        }
        workerWaiters.set(idx, null)
      }
      if (packed == IntSpscRingBuffer.DONE_PACKED) {
        keepRunning = false
      } else if (packed != IntSpscRingBuffer.EMPTY_PACKED) {
        val input = packed.toInt
        try {
          if (intToIntFn ne null) {
            val out = intToIntFn(input)
            offerToIntOutput(idx, out, self) || { keepRunning = false; true }; ()
          } else if (intToLongFn ne null) {
            val out = intToLongFn(input)
            offerToLongOutput(idx, out, self) || { keepRunning = false; true }; ()
          } else if (intToDoubleFn ne null) {
            val out = intToDoubleFn(input)
            offerToDoubleOutput(idx, out, self) || { keepRunning = false; true }; ()
          } else if (intToFloatFn ne null) {
            val out = intToFloatFn(input)
            offerToFloatOutput(idx, out, self) || { keepRunning = false; true }; ()
          } else {
            val result          = f(input)
            val wrapped: AnyRef = if (result == null) NullSentinel else result.asInstanceOf[AnyRef]
            offerToRefOutput(idx, wrapped, self) || { keepRunning = false; true }; ()
          }
        } catch {
          case t: Throwable =>
            recordError(t)
            keepRunning = false
        }
      }
    }

    // Per-worker EOS marker on whichever output queue is in use.
    signalWorkerDone(idx, self)
  }

  /** Returns true on successful offer, false on consumer-closed/interrupt. */
  private def offerToIntOutput(idx: Int, value: Int, self: Thread): Boolean = {
    val q = outIntQs(idx)
    while (true) {
      if (consumerClosed || self.isInterrupted) return false
      if (q.offer(value)) {
        val cw = consumerWaiter
        if (cw ne null) LockSupport.unpark(cw)
        return true
      }
      LockSupport.parkNanos(this, 1000L)
    }
    false
  }

  private def offerToLongOutput(idx: Int, value: Long, self: Thread): Boolean = {
    val q = outLongQs(idx)
    if (value > LongSpscRingBuffer.DONE) {
      while (true) {
        if (consumerClosed || self.isInterrupted) return false
        if (q.offerNonReserved(value)) {
          val cw = consumerWaiter
          if (cw ne null) LockSupport.unpark(cw)
          return true
        }
        LockSupport.parkNanos(this, 1000L)
      }
      false
    } else {
      // Reserved output value: escape out-of-band via offerDoneAfter, which
      // enqueues into outLongEscapes only when a slot is available and just
      // before publishing the DONE token, so it is enqueued at most once and is
      // never orphaned.
      while (true) {
        if (consumerClosed || self.isInterrupted) return false
        if (q.offerDoneAfter(outLongEscapes(idx).offer(java.lang.Long.valueOf(value)): Unit)) {
          val cw = consumerWaiter
          if (cw ne null) LockSupport.unpark(cw)
          return true
        }
        LockSupport.parkNanos(this, 1000L)
      }
      false
    }
  }

  private def offerToFloatOutput(idx: Int, value: Float, self: Thread): Boolean = {
    val q = outFloatQs(idx)
    while (true) {
      if (consumerClosed || self.isInterrupted) return false
      if (q.offer(value)) {
        val cw = consumerWaiter
        if (cw ne null) LockSupport.unpark(cw)
        return true
      }
      LockSupport.parkNanos(this, 1000L)
    }
    false
  }

  private def offerToDoubleOutput(idx: Int, value: Double, self: Thread): Boolean = {
    val q = outDoubleQs(idx)
    while (true) {
      if (consumerClosed || self.isInterrupted) return false
      if (q.offer(value)) {
        val cw = consumerWaiter
        if (cw ne null) LockSupport.unpark(cw)
        return true
      }
      LockSupport.parkNanos(this, 1000L)
    }
    false
  }

  private def offerToRefOutput(idx: Int, wrapped: AnyRef, self: Thread): Boolean = {
    val q = outRefQs(idx)
    while (true) {
      if (consumerClosed || self.isInterrupted) return false
      if (q.offer(wrapped)) {
        val cw = consumerWaiter
        if (cw ne null) LockSupport.unpark(cw)
        return true
      }
      LockSupport.parkNanos(this, 1000L)
    }
    false
  }

  /** Per-worker DONE marker on whichever output queue is in use. */
  private def signalWorkerDone(idx: Int, self: Thread): Unit = {
    if (outIntQs ne null) {
      while (!outIntQs(idx).offerDone() && !consumerClosed && !self.isInterrupted) {
        LockSupport.parkNanos(this, 1000L)
      }
    } else if (outLongQs ne null) {
      while (!outLongQs(idx).offerDone() && !consumerClosed && !self.isInterrupted) {
        LockSupport.parkNanos(this, 1000L)
      }
    } else if (outFloatQs ne null) {
      while (!outFloatQs(idx).offerDone() && !consumerClosed && !self.isInterrupted) {
        LockSupport.parkNanos(this, 1000L)
      }
    } else if (outDoubleQs ne null) {
      while (!outDoubleQs(idx).offerDone() && !consumerClosed && !self.isInterrupted) {
        LockSupport.parkNanos(this, 1000L)
      }
    } else {
      while (!outRefQs(idx).offer(AllWorkersDone) && !consumerClosed && !self.isInterrupted) {
        LockSupport.parkNanos(this, 1000L)
      }
    }
    val cw = consumerWaiter
    if (cw ne null) LockSupport.unpark(cw)
  }

  private def signalWorkersToStop(): Unit = {
    var i = 0
    while (i < n) {
      // In-band: write the DONE sentinel directly into the worker's input queue
      // so it is naturally ordered after every prior data offer to that queue.
      while (!inputQueues(i).offerDone() && !consumerClosed && !Thread.currentThread().isInterrupted) {
        LockSupport.parkNanos(this, 1000L)
      }
      val ww = workerWaiters.get(i)
      if (ww ne null) LockSupport.unpark(ww)
      i += 1
    }
  }

  private def recordError(t: Throwable): Unit =
    if (errorRef.compareAndSet(null, t)) {
      consumerClosed = true
      val cw = consumerWaiter
      if (cw ne null) LockSupport.unpark(cw)
      var i = 0
      while (i < n) {
        val ww = workerWaiters.get(i)
        if (ww ne null) LockSupport.unpark(ww)
        i += 1
      }

      val ct = coordinatorThread
      if (ct ne null) ct.interrupt()

      i = 0
      while (i < n) {
        val wt = workerThreads.get(i)
        if (wt ne null) wt.interrupt()
        i += 1
      }
    }

  private def rethrow(t: Throwable): Nothing = {
    errorDelivered = true
    t match {
      case se: StreamError => throw se
      case _               => throw t
    }
  }
}
