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

package zio.blocks.ringbuffer

import java.lang.invoke.{MethodHandles, VarHandle}

// Cache-line padding hierarchy (16 longs = 128 bytes per region, full cache line on Apple Silicon).
// Prevents false sharing on all architectures including 128-byte cache lines (Apple Silicon).

private[ringbuffer] class SpscPad0 {
  var p00, p01, p02, p03, p04, p05, p06, p07, p08, p09, p0a, p0b, p0c, p0d, p0e, p0f: Long = 0L
}

private[ringbuffer] class SpscProducerFields extends SpscPad0 {
  var producerIndex: Long = 0L
  var producerLimit: Long = 0L // look-ahead boundary; initially 0 forces first offer to slow path
}

private[ringbuffer] class SpscPad1 extends SpscProducerFields {
  var p10, p11, p12, p13, p14, p15, p16, p17, p18, p19, p1a, p1b, p1c, p1d, p1e, p1f: Long = 0L
}

private[ringbuffer] class SpscConsumerFields extends SpscPad1 {
  var consumerIndex: Long = 0L
}

private[ringbuffer] class SpscPad2 extends SpscConsumerFields {
  var p20, p21, p22, p23, p24, p25, p26, p27, p28, p29, p2a, p2b, p2c, p2d, p2e, p2f: Long = 0L
}

/**
 * A high-performance, non-blocking SPSC ring buffer for generic reference
 * types.
 *
 * Uses the FastFlow pattern: the array element's null/non-null state IS the
 * synchronization signal. The producer never reads `consumerIndex`; the
 * consumer never reads `producerIndex`. This minimizes cross-core cache
 * traffic.
 *
 * Optimized for SPSC (Single Producer, Single Consumer). Only one producer
 * thread and one consumer thread should access the buffer concurrently.
 *
 * **Null elements are not permitted.** `offer(null)` throws
 * `NullPointerException`.
 *
 * @param capacity
 *   the buffer capacity, must be a positive power of two
 */
final class SpscRingBuffer[A <: AnyRef](val capacity: Int) extends SpscPad2 {
  require(capacity > 0 && (capacity & (capacity - 1)) == 0, s"capacity must be a positive power of 2, got: $capacity")

  import SpscRingBuffer._

  private val mask: Long            = (capacity - 1).toLong
  private val buffer: Array[AnyRef] = new Array[AnyRef](capacity)
  private val lookAheadStep: Long   = Math.max(1, Math.min(capacity / 4, 4096)).toLong

  /**
   * Tries to insert an element into the buffer without blocking.
   *
   * @param a
   *   the element to insert; must not be `null`
   * @throws java.lang.NullPointerException
   *   if the element is `null`
   * @return
   *   `true` if the element was successfully inserted, `false` if the buffer is
   *   full
   * @note
   *   Must be called from the producer thread only.
   */
  def offer(a: A): Boolean = {
    if (a == null) throw new NullPointerException("offer(null) is not permitted")
    val buf  = buffer
    val m    = mask
    val pIdx = producerIndex

    if (pIdx >= producerLimit) {
      if (!offerSlowPath(buf, m, pIdx)) return false
    }

    val offset = (pIdx & m).toInt
    ARRAY_HANDLE.setRelease(buf, offset, a.asInstanceOf[AnyRef])
    PRODUCER_INDEX.setRelease(this, pIdx + 1L)
    true
  }

  /**
   * Tries to remove an element from the buffer without blocking. Uses FastFlow:
   * reads the array slot directly. Null = empty, non-null = data available.
   *
   * @return
   *   the element, or `null` if the buffer is empty
   * @note
   *   Must be called from the consumer thread only.
   */
  def take(): A = {
    val buf    = buffer
    val m      = mask
    val cIdx   = consumerIndex
    val offset = (cIdx & m).toInt

    val e = ARRAY_HANDLE.getAcquire(buf, offset).asInstanceOf[A]
    if (e eq null) return null.asInstanceOf[A]

    ARRAY_HANDLE.setRelease(buf, offset, null.asInstanceOf[AnyRef])
    CONSUMER_INDEX.setRelease(this, cIdx + 1L)
    e
  }

  /**
   * Returns the number of elements currently in the buffer (approximate under
   * concurrency).
   */
  def size: Int = {
    val cIdx = CONSUMER_INDEX.getAcquire(this).asInstanceOf[Long]
    val pIdx = PRODUCER_INDEX.getAcquire(this).asInstanceOf[Long]
    (pIdx - cIdx).toInt
  }

  /**
   * Returns `true` if the buffer contains no elements (approximate under
   * concurrency).
   */
  def isEmpty: Boolean = {
    val cIdx = CONSUMER_INDEX.getAcquire(this).asInstanceOf[Long]
    val pIdx = PRODUCER_INDEX.getAcquire(this).asInstanceOf[Long]
    pIdx == cIdx
  }

  /** Returns `true` if the buffer is full (approximate under concurrency). */
  def isFull: Boolean = {
    val cIdx = CONSUMER_INDEX.getAcquire(this).asInstanceOf[Long]
    val pIdx = PRODUCER_INDEX.getAcquire(this).asInstanceOf[Long]
    (pIdx - cIdx).toInt == capacity
  }

  /**
   * Drains up to `limit` elements from the buffer, passing each to the given
   * consumer callback.
   *
   * Elements are consumed in FIFO order. The consumer index is advanced after
   * each element is passed to the callback, so if the callback throws, all
   * previously consumed elements remain consumed and the buffer is in a
   * consistent state.
   *
   * @param consumer
   *   the callback invoked for each drained element
   * @param limit
   *   the maximum number of elements to drain; must be non-negative
   * @throws java.lang.IllegalArgumentException
   *   if `limit` is negative
   * @return
   *   the number of elements actually drained (0 if the buffer is empty)
   * @note
   *   Must be called from the consumer thread only.
   */
  def drain(consumer: A => Unit, limit: Int): Int = {
    if (limit < 0) throw new IllegalArgumentException(s"limit is negative: $limit")
    val buf   = buffer
    val m     = mask
    var cIdx  = consumerIndex
    var count = 0

    while (count < limit) {
      val offset = (cIdx & m).toInt
      val e      = ARRAY_HANDLE.getAcquire(buf, offset).asInstanceOf[A]
      if (e eq null) return count
      ARRAY_HANDLE.setRelease(buf, offset, null.asInstanceOf[AnyRef])
      cIdx += 1L
      CONSUMER_INDEX.setRelease(this, cIdx)
      count += 1
      consumer(e)
    }
    count
  }

  /**
   * Fills up to `limit` slots in the buffer by calling the supplier for each
   * new element.
   *
   * Elements are inserted in order. The producer index is advanced after each
   * element is stored. If the supplier throws, all previously inserted elements
   * remain in the buffer.
   *
   * @param supplier
   *   the callback invoked to produce each new element; must not return `null`
   * @param limit
   *   the maximum number of elements to insert; must be non-negative
   * @throws java.lang.IllegalArgumentException
   *   if `limit` is negative
   * @throws java.lang.NullPointerException
   *   if the supplier returns `null`
   * @return
   *   the number of elements actually inserted (0 if the buffer is full)
   * @note
   *   Must be called from the producer thread only.
   */
  def fill(supplier: () => A, limit: Int): Int = {
    if (limit < 0) throw new IllegalArgumentException(s"limit is negative: $limit")
    val buf   = buffer
    val m     = mask
    var pIdx  = producerIndex
    var pLim  = producerLimit
    var count = 0

    while (count < limit) {
      if (pIdx >= pLim) {
        val lookAheadOffset = ((pIdx + lookAheadStep) & m).toInt
        if ((ARRAY_HANDLE.getAcquire(buf, lookAheadOffset): AnyRef) eq null) {
          pLim = pIdx + lookAheadStep
        } else {
          val currentOffset = (pIdx & m).toInt
          if ((ARRAY_HANDLE.getAcquire(buf, currentOffset): AnyRef) ne null) {
            producerLimit = pLim
            return count
          }
          pLim = pIdx + 1
        }
      }
      val a = supplier()
      if (a == null) throw new NullPointerException("fill: supplier returned null")
      val offset = (pIdx & m).toInt
      ARRAY_HANDLE.setRelease(buf, offset, a.asInstanceOf[AnyRef])
      pIdx += 1L
      PRODUCER_INDEX.setRelease(this, pIdx)
      count += 1
    }
    producerLimit = pLim
    count
  }

  private def offerSlowPath(buf: Array[AnyRef], m: Long, pIdx: Long): Boolean = {
    val lookAheadOffset = ((pIdx + lookAheadStep) & m).toInt
    if ((ARRAY_HANDLE.getAcquire(buf, lookAheadOffset): AnyRef) eq null) {
      producerLimit = pIdx + lookAheadStep
      true
    } else {
      val currentOffset = (pIdx & m).toInt
      if ((ARRAY_HANDLE.getAcquire(buf, currentOffset): AnyRef) ne null) {
        false
      } else {
        true
      }
    }
  }
}

object SpscRingBuffer {
  private val PRODUCER_INDEX: VarHandle =
    MethodHandles
      .privateLookupIn(classOf[SpscProducerFields], MethodHandles.lookup())
      .findVarHandle(classOf[SpscProducerFields], "producerIndex", classOf[Long])

  private val CONSUMER_INDEX: VarHandle =
    MethodHandles
      .privateLookupIn(classOf[SpscConsumerFields], MethodHandles.lookup())
      .findVarHandle(classOf[SpscConsumerFields], "consumerIndex", classOf[Long])

  private val ARRAY_HANDLE: VarHandle =
    MethodHandles.arrayElementVarHandle(classOf[Array[AnyRef]])

  /**
   * Creates a new [[SpscRingBuffer]] with the given capacity.
   *
   * @param capacity
   *   the buffer capacity, must be a positive power of two
   * @tparam A
   *   the element type (must be a reference type)
   * @return
   *   a new SPSC ring buffer
   */
  def apply[A <: AnyRef](capacity: Int): SpscRingBuffer[A] = new SpscRingBuffer[A](capacity)
}
