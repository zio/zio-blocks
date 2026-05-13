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
import zio.blocks.ringbuffer.SpscRingBuffer
import zio.blocks.streams.{JvmType, ProducerSink, ProducerStreams}
import zio.blocks.streams.io.Reader

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.LockSupport

/**
 * Byte-specialized [[Reader]] backed by a [[SpscRingBuffer]]. Producers push
 * [[Chunk[Byte]]] objects via [[sink]]; the consumer pulls individual bytes via
 * [[readByte()]] and [[readBytes()]] without boxing.
 */
private[streams] final class ByteProducerReader(bufferSize: Int) extends Reader[Byte] {

  private val rb: SpscRingBuffer[AnyRef]           = new SpscRingBuffer[AnyRef](ProducerStreams.nextPowerOf2(bufferSize))
  private val terminated: AtomicBoolean            = new AtomicBoolean(false)
  private val cancelled: AtomicBoolean             = new AtomicBoolean(false)
  @volatile private var cancelCallback: () => Unit = null
  @volatile private var _closed: Boolean           = false
  @volatile private var _producerThread: Thread    = null
  @volatile private var _consumerThread: Thread    = null

  // Consumer-thread-only state for chunk draining (no volatile needed)
  private var currentChunk: Chunk[Byte] = null
  private var chunkIndex: Int           = 0

  def setCancelCallback(cb: () => Unit): Unit = cancelCallback = cb

  val sink: ProducerSink[Byte, Any] = new ProducerSink[Byte, Any] {
    def emit(a: Byte): Boolean = {
      if (terminated.get() || cancelled.get()) return false
      if (_producerThread eq null) _producerThread = Thread.currentThread()
      try {
        ProducerStreams.blockingOffer(rb, Byte.box(a), _consumerThread)
        !cancelled.get()
      } catch {
        case _: InterruptedException =>
          Thread.currentThread().interrupt()
          false
      }
    }

    override def emit(chunk: Chunk[Byte @unchecked]): Boolean = {
      if (chunk == null) throw new NullPointerException("ProducerSink.emit does not accept null values")
      if (chunk.isEmpty) return true
      if (terminated.get() || cancelled.get()) return false
      if (_producerThread eq null) _producerThread = Thread.currentThread()
      try {
        ProducerStreams.blockingOffer(rb, chunk.asInstanceOf[AnyRef], _consumerThread)
        !cancelled.get()
      } catch {
        case _: InterruptedException =>
          Thread.currentThread().interrupt()
          false
      }
    }

    def end(): Unit =
      if (terminated.compareAndSet(false, true)) {
        try ProducerStreams.blockingOffer(rb, ProducerStreams.EndMarker, _consumerThread)
        catch { case _: InterruptedException => Thread.currentThread().interrupt() }
      }

    def fail(error: Any): Unit =
      if (terminated.compareAndSet(false, true)) {
        try ProducerStreams.blockingOffer(rb, new ProducerStreams.FailMarker(error), _consumerThread)
        catch { case _: InterruptedException => Thread.currentThread().interrupt() }
      }
  }

  override def jvmType: JvmType = JvmType.Byte

  def isClosed: Boolean = _closed

  def read[A1 >: Byte](sentinel: A1): A1 = {
    val b = readByte()
    if (b >= 0) Byte.box(b.toByte).asInstanceOf[A1] else sentinel
  }

  override def readByte(): Int = {
    if (_consumerThread eq null) _consumerThread = Thread.currentThread()
    // Drain current chunk first — zero-boxing hot path via chunk.byte(i)
    if (currentChunk != null) {
      val b = (currentChunk.byte(chunkIndex) & 0xff)
      chunkIndex += 1
      if (chunkIndex >= currentChunk.length) currentChunk = null
      return b
    }
    if (_closed) return -1
    val item = ProducerStreams.blockingTake(rb, _producerThread)
    item match {
      case _ if item eq ProducerStreams.EndMarker =>
        _closed = true
        -1
      case fm: ProducerStreams.FailMarker =>
        _closed = true
        throw new StreamError(fm.error)
      case _ if item eq ProducerStreams.PoisonPill =>
        _closed = true
        -1
      case chunk: Chunk[_] =>
        val c = chunk.asInstanceOf[Chunk[Byte]]
        if (c.isEmpty) {
          readByte()
        } else {
          if (c.length > 1) {
            currentChunk = c
            chunkIndex = 1
          }
          (c.byte(0) & 0xff)
        }
      case _ =>
        // Single boxed byte
        (item.asInstanceOf[java.lang.Number].intValue() & 0xff)
    }
  }

  override def readBytes(buf: Array[Byte], offset: Int, len: Int): Int = {
    if (len == 0) return 0
    if (_consumerThread eq null) _consumerThread = Thread.currentThread()
    var written = 0

    // Drain current chunk first
    while (currentChunk != null && written < len) {
      buf(offset + written) = currentChunk.byte(chunkIndex)
      chunkIndex += 1
      written += 1
      if (chunkIndex >= currentChunk.length) currentChunk = null
    }
    if (written > 0) return written

    // No current chunk — pull from ring buffer
    if (_closed) return -1
    val item = ProducerStreams.blockingTake(rb, _producerThread)
    item match {
      case _ if item eq ProducerStreams.EndMarker =>
        _closed = true
        -1
      case fm: ProducerStreams.FailMarker =>
        _closed = true
        throw new StreamError(fm.error)
      case _ if item eq ProducerStreams.PoisonPill =>
        _closed = true
        -1
      case chunk: Chunk[_] =>
        val c = chunk.asInstanceOf[Chunk[Byte]]
        if (c.isEmpty) return readBytes(buf, offset, len)
        val toCopy = math.min(len, c.length)
        var i      = 0
        while (i < toCopy) {
          buf(offset + i) = c.byte(i)
          i += 1
        }
        if (toCopy < c.length) {
          currentChunk = c
          chunkIndex = toCopy
        }
        toCopy
      case _ =>
        buf(offset) = item.asInstanceOf[java.lang.Number].byteValue()
        1
    }
  }

  def close(): Unit =
    if (!_closed) {
      _closed = true
      if (cancelled.compareAndSet(false, true)) {
        terminated.set(true) // prevents end()/fail() from blocking
        val cb = cancelCallback
        rb.offer(ProducerStreams.PoisonPill) // non-blocking, best effort
        val pt = _producerThread
        if (pt ne null) LockSupport.unpark(pt)
        if (cb != null) {
          try cb()
          catch { case _: Throwable => () }
        }
      }
    }
}
