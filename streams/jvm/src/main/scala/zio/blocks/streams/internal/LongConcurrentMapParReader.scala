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

import zio.blocks.ringbuffer.{FloatSpscRingBuffer, IntSpscRingBuffer, SpscRingBuffer}
import zio.blocks.streams.{JvmType, Platform}
import zio.blocks.streams.io.Reader

import java.util.concurrent.atomic.{AtomicReference, AtomicReferenceArray}
import java.util.concurrent.locks.LockSupport

/**
 * `Long`-specialized concurrent `mapPar` reader.
 *
 * The per-worker input and full-domain output queues represent empty and done
 * out of band, so every `Long` and raw `Double` bit pattern is preserved.
 *
 * The per-worker output queue is selected at construction time from `outType`:
 *   - `JvmType.Int` → `IntSpscRingBuffer`
 *   - `JvmType.Long` → a full-domain raw 64-bit SPSC queue
 *   - `JvmType.Float` → `FloatSpscRingBuffer`
 *   - `JvmType.Double` → a full-domain raw 64-bit SPSC queue
 *   - everything else → `SpscRingBuffer[AnyRef]` (boxed fallback)
 *
 * When the output queue is primitive there is **zero allocation** on the
 * worker-to-consumer hot path. End-of-stream is signalled with the in-band DONE
 * sentinel of each output queue; the consumer counts DONE markers and
 * terminates once all `n` workers have signalled.
 */
private[streams] final class LongConcurrentMapParReader[B](
  upstream: Reader.SyncReader[Long],
  n: Int,
  f: Long => B,
  bufferSize: Int,
  outType: JvmType
) extends Reader.SyncReader[B] {
  import ConcurrentMapParReader._

  require(n >= 1, s"LongConcurrentMapParReader requires n >= 1, got $n")

  private val longToBooleanFn: (Long => Boolean) =
    if (outType eq JvmType.Boolean) f.asInstanceOf[Long => Boolean] else null
  private val longToByteFn: (Long => Byte) =
    if (outType eq JvmType.Byte) f.asInstanceOf[Long => Byte] else null
  private val longToCharFn: (Long => Char) =
    if (outType eq JvmType.Char) f.asInstanceOf[Long => Char] else null
  private val longToIntFn: (Long => Int) =
    if (outType eq JvmType.Int) f.asInstanceOf[Long => Int] else null
  private val longToLongFn: (Long => Long) =
    if (outType eq JvmType.Long) f.asInstanceOf[Long => Long] else null
  private val longToDoubleFn: (Long => Double) =
    if (outType eq JvmType.Double) f.asInstanceOf[Long => Double] else null
  private val longToFloatFn: (Long => Float) =
    if (outType eq JvmType.Float) f.asInstanceOf[Long => Float] else null
  private val longToShortFn: (Long => Short) =
    if (outType eq JvmType.Short) f.asInstanceOf[Long => Short] else null

  private val outIntQs: Array[IntSpscRingBuffer] =
    if (
      (outType eq JvmType.Boolean) || (outType eq JvmType.Byte) || (outType eq JvmType.Char) ||
      (outType eq JvmType.Short) || (outType eq JvmType.Int)
    ) Array.tabulate(n)(_ => new IntSpscRingBuffer(bufferSize))
    else null
  private val outLongQs: Array[FullDomainSpscRingBuffer] =
    if (outType eq JvmType.Long) Array.tabulate(n)(_ => new FullDomainSpscRingBuffer(bufferSize)) else null
  private val outFloatQs: Array[FloatSpscRingBuffer] =
    if (outType eq JvmType.Float) Array.tabulate(n)(_ => new FloatSpscRingBuffer(bufferSize)) else null
  private val outDoubleQs: Array[FullDomainSpscRingBuffer] =
    if (outType eq JvmType.Double) Array.tabulate(n)(_ => new FullDomainSpscRingBuffer(bufferSize)) else null
  private val outRefQs: Array[SpscRingBuffer[AnyRef]] =
    if ((outIntQs eq null) && (outLongQs eq null) && (outFloatQs eq null) && (outDoubleQs eq null))
      Array.tabulate(n)(_ => new SpscRingBuffer[AnyRef](bufferSize))
    else null

  @volatile private var consumerWaiter: Thread = null

  private val inputQueues: Array[FullDomainSpscRingBuffer] =
    Array.tabulate(n)(_ => new FullDomainSpscRingBuffer(bufferSize))
  private val workerWaiters = new AtomicReferenceArray[Thread](n)
  private val workerThreads = new AtomicReferenceArray[Thread](n)

  private val errorRef                            = new AtomicReference[Throwable](null)
  @volatile private var consumerClosed: Boolean   = false
  @volatile private var coordinatorThread: Thread = null.asInstanceOf[Thread]
  private val upstreamClose                       = new ConcurrentShutdown.CloseOnce(upstream)
  private val closeLock                           = new AnyRef
  @volatile private var closeDone: Boolean        = false
  private var closeStarted: Boolean               = false
  private var closeLeader: Thread                 = null

  private val workerTasks: Array[Runnable] = Array.tabulate(n) { idx =>
    new Runnable {
      def run(): Unit = workerLoop(idx)
    }
  }

  Array.tabulate(n) { idx =>
    Platform.startVirtualThread(
      s"zio-blocks-mappar-worker-${counter.getAndIncrement()}-$idx",
      workerTasks(idx)
    )
  }

  private val coordinatorTask: Runnable = new Runnable {
    def run(): Unit = {
      var coordinatorFailure: Throwable = null
      try {
        val coordBuf = new Array[Long](bufferSize)
        var scanIdx  = 0
        var running  = true

        while (running && !consumerClosed && !Thread.currentThread().isInterrupted) {
          val count = upstream.readLongsPhysical(coordBuf, 0, bufferSize)
          if (count <= 0) {
            running = false
          } else {
            var j = 0
            while (j < count && running && !consumerClosed && !Thread.currentThread().isInterrupted) {
              val v          = coordBuf(j)
              var dispatched = false
              while (!dispatched && !consumerClosed && !Thread.currentThread().isInterrupted) {
                var i = 0
                while (i < n && !dispatched) {
                  val qIdx = (scanIdx + i) % n
                  if (inputQueues(qIdx).offer(v)) {
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
          coordinatorFailure = t
      } finally {
        upstreamClose.close { closeFailure =>
          if (coordinatorFailure eq null) coordinatorFailure = closeFailure
          else if (coordinatorFailure ne closeFailure) coordinatorFailure.addSuppressed(closeFailure)
        }
        if (coordinatorFailure ne null) recordError(coordinatorFailure)
        signalWorkersToStop()
      }
    }
  }

  coordinatorThread = Platform.startVirtualThread(
    s"zio-blocks-mappar-coordinator-${counter.getAndIncrement()}",
    coordinatorTask
  )

  def isClosed: Boolean = eofReturned || consumerClosed

  private var scanStart: Int        = 0
  private var workersDoneCount: Int = 0
  private var eofReturned: Boolean  = false
  private val rawScratch            = new Array[Long](1)

  override def jvmType: JvmType = outType

  // ============================================================
  //  READ DISPATCH
  // ============================================================

  def read[B1 >: B](sentinel: B1): B1 =
    if (outIntQs ne null) {
      val v = pollIntOnce(IntSpscRingBuffer.EMPTY_PACKED)
      if (v == IntSpscRingBuffer.EMPTY_PACKED) sentinel
      else boxIntLike(v.toInt).asInstanceOf[B1]
    } else if (outLongQs ne null) {
      if (pollFullDomain(outLongQs, rawScratch, 0, wait = true) < 0) sentinel
      else Long.box(rawScratch(0)).asInstanceOf[B1]
    } else if (outFloatQs ne null) {
      val packed = pollFloatPacked()
      if (packed == FloatSpscRingBuffer.EMPTY_PACKED) sentinel
      else Float.box(java.lang.Float.intBitsToFloat(packed.toInt)).asInstanceOf[B1]
    } else if (outDoubleQs ne null) {
      if (pollFullDomain(outDoubleQs, rawScratch, 0, wait = true) < 0) sentinel
      else Double.box(java.lang.Double.longBitsToDouble(rawScratch(0))).asInstanceOf[B1]
    } else {
      readRef(sentinel)
    }

  override def readBoolean(sentinel: Int)(implicit ev: B <:< Boolean): Int = pollIntLike(sentinel)

  override def readByte(): Int = pollIntLike(-1)

  override def readChar(sentinel: Int)(implicit ev: B <:< Char): Int = pollIntLike(sentinel)

  override def readInt(sentinel: Long)(implicit ev: B <:< Int): Long =
    if (outIntQs ne null) {
      val v = pollIntOnce(IntSpscRingBuffer.EMPTY_PACKED)
      if (v == IntSpscRingBuffer.EMPTY_PACKED) sentinel
      else v.toInt.toLong
    } else readIntFromRef(sentinel)

  override def readLong(sentinel: Long)(implicit ev: B <:< Long): Long =
    if (outLongQs ne null) {
      if (readLongs(rawScratch, 0, 1) < 0) sentinel else rawScratch(0)
    } else readLongFromRef(sentinel)

  override def readShort(sentinel: Int)(implicit ev: B <:< Short): Int = pollIntLike(sentinel)

  override def readFloat(sentinel: Double)(implicit ev: B <:< Float): Double =
    if (outFloatQs ne null) {
      val packed = pollFloatPacked()
      if (packed == FloatSpscRingBuffer.EMPTY_PACKED) sentinel
      else java.lang.Float.intBitsToFloat(packed.toInt).toDouble
    } else readFloatFromRef(sentinel)

  override def readDouble(sentinel: Double)(implicit ev: B <:< Double): Double =
    if (outDoubleQs ne null) {
      if (pollFullDomain(outDoubleQs, rawScratch, 0, wait = true) < 0) sentinel
      else java.lang.Double.longBitsToDouble(rawScratch(0))
    } else readDoubleFromRef(sentinel)

  override def readLongs(dest: Array[Long], offset: Int, length: Int)(implicit ev: B <:< Long): Int = {
    Reader.validateArrayRange(dest, offset, length)
    if (length == 0) 0
    else if (outLongQs eq null) {
      val value = readRef[Any](EndOfStream)
      if (value.asInstanceOf[AnyRef] eq EndOfStream) -1 else { dest(offset) = value.asInstanceOf[Long]; 1 }
    } else readFullDomain(outLongQs, dest, offset, length)
  }

  override def readDoubles(dest: Array[Double], offset: Int, length: Int)(implicit ev: B <:< Double): Int = {
    Reader.validateArrayRange(dest, offset, length)
    if (length == 0) 0
    else if (outDoubleQs eq null) {
      val value = readRef[Any](EndOfStream)
      if (value.asInstanceOf[AnyRef] eq EndOfStream) -1 else { dest(offset) = value.asInstanceOf[Double]; 1 }
    } else {
      val first = pollFullDomain(outDoubleQs, rawScratch, 0, wait = true)
      if (first < 0) -1
      else {
        dest(offset) = java.lang.Double.longBitsToDouble(rawScratch(0))
        var count = 1
        var next  = 1
        while (count < length && next > 0) {
          next = pollFullDomain(outDoubleQs, rawScratch, 0, wait = false)
          if (next > 0) { dest(offset + count) = java.lang.Double.longBitsToDouble(rawScratch(0)); count += 1 }
        }
        count
      }
    }
  }

  // ============================================================
  //  PRIMITIVE OUTPUT POLLERS (zero-allocation hot paths)
  // ============================================================

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
        if (consumerClosed) { eofReturned = true; return emptyMarker }
        if (workersDoneCount >= n) { finishEof(); return emptyMarker }
        if (!sawAny && errorRef.get() == null) LockSupport.park(this)
      } finally {
        consumerWaiter = null
      }
    }
    emptyMarker
  }

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
        if (consumerClosed) { eofReturned = true; return FloatSpscRingBuffer.EMPTY_PACKED }
        if (workersDoneCount >= n) { finishEof(); return FloatSpscRingBuffer.EMPTY_PACKED }
        if (!sawAny && errorRef.get() == null) LockSupport.park(this)
      } finally {
        consumerWaiter = null
      }
    }
    FloatSpscRingBuffer.EMPTY_PACKED
  }

  // ============================================================
  //  REFERENCE-OUTPUT POLLERS
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
        if (consumerClosed) { eofReturned = true; return sentinel }
        if (workersDoneCount >= n) { finishEof(); return sentinel }
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
    if (v.asInstanceOf[AnyRef] eq EndOfStream) sentinel
    else v.asInstanceOf[java.lang.Number].longValue()
  }
  private def readFloatFromRef(sentinel: Double): Double = {
    val v = readRef[Any](EndOfStream)
    if (v.asInstanceOf[AnyRef] eq EndOfStream) sentinel
    else v.asInstanceOf[java.lang.Number].floatValue().toDouble
  }
  private def readDoubleFromRef(sentinel: Double): Double = {
    val v = readRef[Any](EndOfStream)
    if (v.asInstanceOf[AnyRef] eq EndOfStream) sentinel
    else v.asInstanceOf[java.lang.Number].doubleValue()
  }

  // ============================================================
  //  CLOSE / WORKER / ERROR PLUMBING
  // ============================================================

  def close(): Unit = {
    val self   = Thread.currentThread()
    val leader = closeLock.synchronized {
      if (!closeStarted) { closeStarted = true; closeLeader = self; consumerClosed = true; true }
      else false
    }
    var interrupted: InterruptedException = null
    if (leader) try {
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
        upstreamClose.close(recordError)
        ct.interrupt()
        interrupted = ConcurrentShutdown.join(ct, interrupted)
      }

      i = 0
      while (i < n) {
        val t = workerThreads.get(i)
        if (t ne null) {
          t.interrupt()
          interrupted = ConcurrentShutdown.join(t, interrupted)
        }
        i += 1
      }
    } finally closeLock.synchronized { closeDone = true; closeLeader = null; closeLock.notifyAll() }
    else if (closeLeader ne self) closeLock.synchronized {
      while (!closeDone)
        try closeLock.wait()
        catch { case cause: InterruptedException => if (interrupted eq null) interrupted = cause }
    }
    ConcurrentShutdown.replay(errorRef.get(), interrupted)
  }

  private def workerLoop(idx: Int): Unit = {
    val self = Thread.currentThread()
    workerThreads.set(idx, self)

    var keepRunning = true
    val input       = new Array[Long](1)

    while (keepRunning && !consumerClosed && !self.isInterrupted) {
      var status = inputQueues(idx).poll(input, 0)
      if (status == FullDomainSpscRingBuffer.Empty) {
        workerWaiters.set(idx, self)
        status = inputQueues(idx).poll(input, 0)
        if (status == FullDomainSpscRingBuffer.Empty) {
          if (!consumerClosed && !self.isInterrupted) LockSupport.parkNanos(this, 1000L)
        }
        workerWaiters.set(idx, null)
      }
      if (status == FullDomainSpscRingBuffer.Done) {
        keepRunning = false
      } else if (status == FullDomainSpscRingBuffer.Data) {
        val value: Long = input(0)
        try {
          if (longToBooleanFn ne null) {
            val out = longToBooleanFn(value)
            offerToIntOutput(idx, if (out) 1 else 0, self) || { keepRunning = false; true }; ()
          } else if (longToByteFn ne null) {
            val out = longToByteFn(value)
            offerToIntOutput(idx, out.toInt & 0xff, self) || { keepRunning = false; true }; ()
          } else if (longToCharFn ne null) {
            val out = longToCharFn(value)
            offerToIntOutput(idx, out.toInt, self) || { keepRunning = false; true }; ()
          } else if (longToIntFn ne null) {
            val out = longToIntFn(value)
            offerToIntOutput(idx, out, self) || { keepRunning = false; true }; ()
          } else if (longToLongFn ne null) {
            val out = longToLongFn(value)
            offerToLongOutput(idx, out, self) || { keepRunning = false; true }; ()
          } else if (longToDoubleFn ne null) {
            val out = longToDoubleFn(value)
            offerToDoubleOutput(idx, out, self) || { keepRunning = false; true }; ()
          } else if (longToFloatFn ne null) {
            val out = longToFloatFn(value)
            offerToFloatOutput(idx, out, self) || { keepRunning = false; true }; ()
          } else if (longToShortFn ne null) {
            val out = longToShortFn(value)
            offerToIntOutput(idx, out.toInt, self) || { keepRunning = false; true }; ()
          } else {
            val result          = f(value)
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

    signalWorkerDone(idx, self)
  }

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
      if (q.offer(java.lang.Double.doubleToRawLongBits(value))) {
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

  private def signalWorkerDone(idx: Int, self: Thread): Unit = {
    if (outIntQs ne null) {
      while (!outIntQs(idx).offerDone() && !consumerClosed && !self.isInterrupted) {
        LockSupport.parkNanos(this, 1000L)
      }
    } else if (outLongQs ne null) {
      outLongQs(idx).offerDone()
    } else if (outFloatQs ne null) {
      while (!outFloatQs(idx).offerDone() && !consumerClosed && !self.isInterrupted) {
        LockSupport.parkNanos(this, 1000L)
      }
    } else if (outDoubleQs ne null) {
      outDoubleQs(idx).offerDone()
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
      inputQueues(i).offerDone()
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
    } else {
      val primary = errorRef.get()
      if ((primary ne null) && (primary ne t)) primary.addSuppressed(t)
    }

  private def boxIntLike(value: Int): AnyRef =
    if (outType eq JvmType.Boolean) Boolean.box(value != 0)
    else if (outType eq JvmType.Byte) Byte.box(value.toByte)
    else if (outType eq JvmType.Char) Char.box(value.toChar)
    else if (outType eq JvmType.Short) Short.box(value.toShort)
    else Int.box(value)

  private def pollIntLike(sentinel: Int): Int = {
    val value = pollIntOnce(IntSpscRingBuffer.EMPTY_PACKED)
    if (value == IntSpscRingBuffer.EMPTY_PACKED) sentinel else value.toInt
  }

  private def pollFullDomain(
    queues: Array[FullDomainSpscRingBuffer],
    dest: Array[Long],
    offset: Int,
    wait: Boolean
  ): Int = {
    while (true) {
      var i = 0
      while (i < n) {
        val qIdx   = (scanStart + i) % n
        val status = queues(qIdx).poll(dest, offset)
        if (status == FullDomainSpscRingBuffer.Done) workersDoneCount += 1
        else if (status == FullDomainSpscRingBuffer.Data) {
          scanStart = (qIdx + 1) % n
          val error = errorRef.get()
          if (error ne null) rethrow(error)
          return 1
        }
        i += 1
      }
      val error = errorRef.get()
      if (error ne null) rethrow(error)
      if (consumerClosed) { eofReturned = true; return -1 }
      if (workersDoneCount >= n) { finishEof(); return -1 }
      if (!wait) return 0
      consumerWaiter = Thread.currentThread()
      try LockSupport.parkNanos(this, 1000L)
      finally consumerWaiter = null
    }
    -1
  }

  private def finishEof(): Unit = {
    val interrupted = ConcurrentShutdown.join(coordinatorThread, null)
    ConcurrentShutdown.replay(errorRef.get(), interrupted)
    eofReturned = true
  }

  private def readFullDomain(
    queues: Array[FullDomainSpscRingBuffer],
    dest: Array[Long],
    offset: Int,
    length: Int
  ): Int = {
    val first = pollFullDomain(queues, dest, offset, wait = true)
    if (first < 0) -1
    else {
      var count = 1
      var next  = 1
      while (count < length && next > 0) {
        next = pollFullDomain(queues, dest, offset + count, wait = false)
        if (next > 0) count += 1
      }
      count
    }
  }

  private def rethrow(t: Throwable): Nothing = t match {
    case se: StreamError => throw se
    case _               => throw t
  }
}
