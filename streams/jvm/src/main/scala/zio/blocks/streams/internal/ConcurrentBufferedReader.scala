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

import zio.blocks.chunk.Chunk
import zio.blocks.ringbuffer.{FloatSpscRingBuffer, IntSpscRingBuffer}
import zio.blocks.streams.{JvmType, Platform}
import zio.blocks.streams.io.Reader
import zio.blocks.streams.queues.BlockingSpscQueue

import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.LockSupport

/** A bounded, primitive-specialized producer/consumer reader. */
private[streams] final class ConcurrentBufferedReader[A](upstream: Reader.SyncReader[A], bufferSize: Int)
    extends Reader.SyncReader[A] {
  import ConcurrentBufferedReader._

  override val jvmType: JvmType = upstream.jvmType
  private val intQueue          =
    if (isIntLane) new IntSpscRingBuffer(powerOfTwoCapacity(bufferSize)) else null
  private val longQueue =
    if ((jvmType eq JvmType.Long) || (jvmType eq JvmType.Double)) new FullDomainSpscRingBuffer(bufferSize) else null
  private val floatQueue =
    if (jvmType eq JvmType.Float) new FloatSpscRingBuffer(powerOfTwoCapacity(bufferSize)) else null
  private val refQueue = if (jvmType eq JvmType.AnyRef) new BlockingSpscQueue[AnyRef](bufferSize) else null

  @volatile private var producerError: Throwable = null
  @volatile private var consumerClosed           = false
  @volatile private var producerDone             = false
  @volatile private var consumerThread: Thread   = null
  private val upstreamClose                      = new ConcurrentShutdown.CloseOnce(upstream)
  private val closeLock                          = new AnyRef
  @volatile private var closeDone                = false
  private var closeStarted                       = false
  private var closeLeader: Thread                = null
  private val longScratch                        = new Array[Long](1)
  private val doubleScratch                      = new Array[Double](1)
  private val rawScratch                         = new Array[Long](1)

  private def isIntLane: Boolean =
    (jvmType eq JvmType.Boolean) || (jvmType eq JvmType.Byte) || (jvmType eq JvmType.Char) ||
      (jvmType eq JvmType.Short) || (jvmType eq JvmType.Int)

  private val producerTask: Runnable = new Runnable {
    def run(): Unit =
      try produce()
      catch {
        case _: Throwable if consumerClosed => ()
        case t: Throwable                   => recordProducerError(t)
      } finally {
        // The producer interrupt is this reader's private stop signal. Do not
        // leak it into an interrupt-sensitive upstream close (notably a nested
        // buffered reader).
        Thread.interrupted()
        upstreamClose.close(recordProducerError)
        producerDone = true
        if (refQueue ne null) refQueue.close()
        val c = consumerThread
        if (c ne null) LockSupport.unpark(c)
      }
  }

  private val producerThread = Platform.startVirtualThread(
    s"zio-blocks-buffer-${ConcurrentBufferedReader.counter.getAndIncrement()}",
    producerTask
  )

  private def produce(): Unit = jvmType match {
    case JvmType.Boolean => produceInts(() => upstream.readBooleanPhysical(-1), -1)
    case JvmType.Byte    => produceInts(() => upstream.readBytePhysical(), -1)
    case JvmType.Char    => produceInts(() => upstream.readCharPhysical(-1), -1)
    case JvmType.Short   => produceInts(() => upstream.readShortPhysical(Int.MinValue), Int.MinValue)
    case JvmType.Int     =>
      var run = true
      while (run && active) {
        val value = upstream.readIntPhysical(Long.MinValue)
        if (value == Long.MinValue) run = false else run = offerInt(value.toInt)
      }
    case JvmType.Long =>
      var run = true
      while (run && active) {
        val n = upstream.readLongsPhysical(longScratch, 0, 1)
        if (n < 0) run = false else run = offerLong(longScratch(0))
      }
    case JvmType.Float =>
      var run = true
      while (run && active) {
        val value = upstream.readFloatPhysical(Double.MaxValue)
        if (value == Double.MaxValue) run = false else run = offerFloat(value.toFloat)
      }
    case JvmType.Double =>
      var run = true
      while (run && active) {
        val n = upstream.readDoublesPhysical(doubleScratch, 0, 1)
        if (n < 0) run = false
        else run = offerLong(java.lang.Double.doubleToRawLongBits(doubleScratch(0)))
      }
    case JvmType.AnyRef =>
      var run = true
      while (run && active) {
        val value = upstream.read(EndOfStream)
        if (value.asInstanceOf[AnyRef] eq EndOfStream) run = false
        else run = refQueue.offer(if (value == null) NullSentinel else value.asInstanceOf[AnyRef])
      }
  }

  private def produceInts(read: () => Int, sentinel: Int): Unit = {
    var run = true
    while (run && active) {
      val value = read()
      if (value == sentinel) run = false else run = offerInt(value)
    }
  }
  private def active: Boolean                                          = !consumerClosed && !Thread.currentThread().isInterrupted
  private def offerInt(value: Int): Boolean                            = waitOffer(intQueue.offer(value))(() => intQueue.offer(value))
  private def offerFloat(value: Float): Boolean                        = waitOffer(floatQueue.offer(value))(() => floatQueue.offer(value))
  private def offerLong(value: Long): Boolean                          = waitOffer(longQueue.offer(value))(() => longQueue.offer(value))
  private def waitOffer(first: Boolean)(retry: () => Boolean): Boolean = {
    var offered = first
    while (!offered && active) {
      LockSupport.parkNanos(100000L)
      offered = retry()
    }
    if (offered) {
      val c = consumerThread
      if (c ne null) LockSupport.unpark(c)
    }
    offered
  }

  private def primitiveEmpty: Boolean =
    if (intQueue ne null) intQueue.isEmpty
    else if (floatQueue ne null) floatQueue.isEmpty
    else pollLongState(peek = true) == FullDomainSpscRingBuffer.Empty

  def isClosed: Boolean =
    consumerClosed || {
      val done = producerDone
      done && empty
    }
  override def readable(): Boolean = {
    val done = producerDone
    !consumerClosed && (!empty || !done)
  }
  override private[streams] def tryReadable: Reader.Availability = {
    val done = producerDone
    if (consumerClosed) Reader.Unavailable
    else if (!empty) Reader.Available
    else if (done) Reader.Unavailable
    else Reader.Unknown
  }
  private def empty: Boolean = if (refQueue ne null) refQueue.isEmpty else primitiveEmpty

  def read[A1 >: A](sentinel: A1): A1 =
    if (jvmType eq JvmType.AnyRef) {
      val v = takeRef()
      if (v eq EndOfStream) sentinel else if (v eq NullSentinel) null.asInstanceOf[A1] else v.asInstanceOf[A1]
    } else if (jvmType eq JvmType.Boolean)
      boxOrSentinel(readBooleanPhysical(-1), -1, sentinel)((value: Int) => Boolean.box(value != 0))
    else if (jvmType eq JvmType.Byte) boxOrSentinel(readBytePhysical(), -1, sentinel)(x => Byte.box(x.toByte))
    else if (jvmType eq JvmType.Char) boxOrSentinel(readCharPhysical(-1), -1, sentinel)(x => Char.box(x.toChar))
    else if (jvmType eq JvmType.Short)
      boxOrSentinel(readShortPhysical(Int.MinValue), Int.MinValue, sentinel)(x => Short.box(x.toShort))
    else if (jvmType eq JvmType.Int)
      boxOrSentinel(readIntPhysical(Long.MinValue), Long.MinValue, sentinel)(x => Int.box(x.toInt))
    else if (jvmType eq JvmType.Long)
      if (readLongsPhysical(longScratch, 0, 1) < 0) sentinel else Long.box(longScratch(0)).asInstanceOf[A1]
    else if (jvmType eq JvmType.Float)
      boxOrSentinel(readFloatPhysical(Double.MaxValue), Double.MaxValue, sentinel)(x => Float.box(x.toFloat))
    else if (readDoublesPhysical(doubleScratch, 0, 1) < 0) sentinel
    else Double.box(doubleScratch(0)).asInstanceOf[A1]
  private def boxOrSentinel[A1, B](value: B, end: B, sentinel: A1)(box: B => AnyRef): A1 =
    if (value == end) sentinel else box(value).asInstanceOf[A1]

  override def readBoolean(sentinel: Int)(implicit ev: A <:< Boolean): Int = takeInt(sentinel)
  override def readByte(): Int                                             = takeInt(-1)
  override def readChar(sentinel: Int)(implicit ev: A <:< Char): Int       = takeInt(sentinel)
  override def readShort(sentinel: Int)(implicit ev: A <:< Short): Int     = takeInt(sentinel)
  override def readInt(sentinel: Long)(implicit ev: A <:< Int): Long       = {
    val packed = takeIntPacked()
    if (packed == IntSpscRingBuffer.EMPTY_PACKED) sentinel else packed.toInt.toLong
  }
  override def readLong(sentinel: Long)(implicit ev: A <:< Long): Long =
    if (readLongsPhysical(longScratch, 0, 1) < 0) sentinel else longScratch(0)
  override def readFloat(sentinel: Double)(implicit ev: A <:< Float): Double = {
    val bits = takeFloatBits()
    if (bits == NoFloat) sentinel else java.lang.Float.intBitsToFloat(bits.toInt).toDouble
  }
  override def readDouble(sentinel: Double)(implicit ev: A <:< Double): Double =
    if (readDoublesPhysical(doubleScratch, 0, 1) < 0) sentinel else doubleScratch(0)

  private def takeInt(sentinel: Int): Int = {
    val packed = takeIntPacked()
    if (packed == IntSpscRingBuffer.EMPTY_PACKED) sentinel else packed.toInt
  }
  private def takeIntPacked(): Long = {
    val packed = blockingIntPacked()
    if (packed == IntSpscRingBuffer.EMPTY_PACKED) replayError()
    else LockSupport.unpark(producerThread)
    packed
  }
  private def takeFloatBits(): Long = {
    val packed = blockingFloatPacked()
    if (packed == FloatSpscRingBuffer.EMPTY_PACKED) { replayError(); NoFloat }
    else { LockSupport.unpark(producerThread); packed.toInt.toLong }
  }
  override def readBytes(dest: Array[Byte], offset: Int, length: Int)(implicit ev: A <:< Byte): Int =
    drainBytes(dest, offset, length)
  override def readInts(dest: Array[Int], offset: Int, length: Int)(implicit ev: A <:< Int): Int =
    drainInts(dest, offset, length)
  private def blockingIntPacked(): Long = {
    consumerThread = Thread.currentThread()
    var p       = intQueue.pollPacked()
    var waiting = true
    while (p == IntSpscRingBuffer.EMPTY_PACKED && !consumerClosed && waiting) {
      if (producerDone) {
        p = intQueue.pollPacked()
        waiting = false
      } else {
        LockSupport.parkNanos(100000L)
        p = intQueue.pollPacked()
      }
    }
    p
  }
  override def readLongs(dest: Array[Long], offset: Int, length: Int)(implicit ev: A <:< Long): Int =
    drainLongs(dest, offset, length)
  override def readDoubles(dest: Array[Double], offset: Int, length: Int)(implicit ev: A <:< Double): Int =
    drainDoubles(dest, offset, length)
  private def pollLongState(peek: Boolean): Int =
    // FullDomain has no non-destructive peek, so retain a probed value in primitive storage.
    if (peek) {
      if (pendingLong) FullDomainSpscRingBuffer.Data
      else if (longQueue.poll(rawScratch, 0) == FullDomainSpscRingBuffer.Data) {
        pendingLong = true; FullDomainSpscRingBuffer.Data
      } else FullDomainSpscRingBuffer.Empty
    } else if (pendingLong) { pendingLong = false; FullDomainSpscRingBuffer.Data }
    else longQueue.poll(rawScratch, 0)
  private var pendingLong = false

  private def blockingLongState(): Int = {
    consumerThread = Thread.currentThread()
    var state   = pollLongState(peek = false)
    var waiting = true
    while (state == FullDomainSpscRingBuffer.Empty && !consumerClosed && waiting) {
      if (producerDone) {
        state = pollLongState(peek = false)
        waiting = false
      } else {
        LockSupport.parkNanos(100000L)
        state = pollLongState(peek = false)
      }
    }
    state
  }

  override def readFloats(dest: Array[Float], offset: Int, length: Int)(implicit ev: A <:< Float): Int = {
    Reader.validateArrayRange(dest, offset, length)
    if (length == 0) 0
    else {
      var count = 0; var continue = true
      while (count < length && continue) {
        val p = if (count == 0) blockingFloatPacked() else floatQueue.pollPacked()
        if (p == 0L) continue = false
        else {
          dest(offset + count) = java.lang.Float.intBitsToFloat(p.toInt); count += 1; LockSupport.unpark(producerThread)
        }
      }
      if (count == 0) { replayError(); -1 }
      else count
    }
  }
  private def blockingFloatPacked(): Long = {
    consumerThread = Thread.currentThread()
    var p       = floatQueue.pollPacked()
    var waiting = true
    while (p == FloatSpscRingBuffer.EMPTY_PACKED && !consumerClosed && waiting) {
      if (producerDone) {
        p = floatQueue.pollPacked()
        waiting = false
      } else {
        LockSupport.parkNanos(100000L)
        p = floatQueue.pollPacked()
      }
    }
    p
  }

  override def readUpToN[A1 >: A](n: Int): Chunk[A1] = super.readUpToN(n)

  private def drainBytes(dest: Array[Byte], offset: Int, length: Int): Int = {
    Reader.validateArrayRange(dest, offset, length)
    if (length == 0) 0
    else {
      var count = 0
      var more  = true
      while (count < length && more) {
        val packed = if (count == 0) blockingIntPacked() else intQueue.pollPacked()
        if (packed == IntSpscRingBuffer.EMPTY_PACKED) more = false
        else {
          dest(offset + count) = packed.toByte
          count += 1
          LockSupport.unpark(producerThread)
        }
      }
      if (count == 0) { replayError(); -1 }
      else count
    }
  }

  private def drainDoubles(dest: Array[Double], offset: Int, length: Int): Int = {
    Reader.validateArrayRange(dest, offset, length)
    if (length == 0) 0
    else {
      var count = 0
      var more  = true
      while (count < length && more) {
        val state = if (count == 0) blockingLongState() else pollLongState(peek = false)
        if (state == FullDomainSpscRingBuffer.Data) {
          dest(offset + count) = java.lang.Double.longBitsToDouble(rawScratch(0))
          count += 1
          LockSupport.unpark(producerThread)
        } else more = false
      }
      if (count == 0) { replayError(); -1 }
      else count
    }
  }

  private def drainInts(dest: Array[Int], offset: Int, length: Int): Int = {
    Reader.validateArrayRange(dest, offset, length)
    if (length == 0) 0
    else {
      var count = 0
      var more  = true
      while (count < length && more) {
        val packed = if (count == 0) blockingIntPacked() else intQueue.pollPacked()
        if (packed == IntSpscRingBuffer.EMPTY_PACKED) more = false
        else {
          dest(offset + count) = packed.toInt
          count += 1
          LockSupport.unpark(producerThread)
        }
      }
      if (count == 0) { replayError(); -1 }
      else count
    }
  }

  private def drainLongs(dest: Array[Long], offset: Int, length: Int): Int = {
    Reader.validateArrayRange(dest, offset, length)
    if (length == 0) 0
    else {
      var count = 0
      var more  = true
      while (count < length && more) {
        val state = if (count == 0) blockingLongState() else pollLongState(peek = false)
        if (state == FullDomainSpscRingBuffer.Data) {
          dest(offset + count) = rawScratch(0)
          count += 1
          LockSupport.unpark(producerThread)
        } else more = false
      }
      if (count == 0) { replayError(); -1 }
      else count
    }
  }

  private def takeRef(): AnyRef = {
    val value = refQueue.take()
    if (value eq null) { replayError(); EndOfStream }
    else value
  }
  private def replayError(): Unit = { val e = producerError; if (e ne null) rethrow(e) }

  def close(): Unit = {
    val self   = Thread.currentThread()
    val leader = closeLock.synchronized {
      if (!closeStarted) { closeStarted = true; closeLeader = self; consumerClosed = true; true }
      else false
    }
    var interrupted: InterruptedException = null
    if (leader) {
      try {
        if (refQueue ne null) refQueue.close()
        LockSupport.unpark(producerThread)
        upstreamClose.close(recordProducerError)
        producerThread.interrupt()
        interrupted = ConcurrentShutdown.join(producerThread, interrupted)
      } finally closeLock.synchronized { closeDone = true; closeLeader = null; closeLock.notifyAll() }
    } else if (closeLeader ne self) closeLock.synchronized {
      while (!closeDone)
        try closeLock.wait()
        catch { case e: InterruptedException => if (interrupted eq null) interrupted = e }
    }
    ConcurrentShutdown.replay(producerError, interrupted)
  }

  private def recordProducerError(cause: Throwable): Unit = synchronized {
    if (producerError eq null) producerError = cause else if (producerError ne cause) producerError.addSuppressed(cause)
  }
  private def rethrow(t: Throwable): Nothing = t match { case se: StreamError => throw se; case _ => throw t }
}

private object ConcurrentBufferedReader {
  val counter                                 = new AtomicLong(0L)
  val NullSentinel: AnyRef                    = new AnyRef
  val NoFloat: Long                           = Long.MinValue
  private def powerOfTwoCapacity(n: Int): Int = {
    require(n > 0, s"bufferSize must be positive, got $n")
    Integer.highestOneBit(n)
  }
}
