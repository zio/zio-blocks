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
// Prevents false sharing between producer fields, producer limit, consumer fields.

private[ringbuffer] class MpscPad0 {
  var p00, p01, p02, p03, p04, p05, p06, p07, p08, p09, p0a, p0b, p0c, p0d, p0e, p0f: Long = 0L
}

private[ringbuffer] class MpscProducerFields extends MpscPad0 {
  var producerIndex: Long = 0L
}

private[ringbuffer] class MpscPad1 extends MpscProducerFields {
  var p10, p11, p12, p13, p14, p15, p16, p17, p18, p19, p1a, p1b, p1c, p1d, p1e, p1f: Long = 0L
}

private[ringbuffer] class MpscProducerLimitFields extends MpscPad1 {
  var producerLimit: Long = 0L // initialized to capacity in MpscRingBuffer constructor
}

private[ringbuffer] class MpscPad2 extends MpscProducerLimitFields {
  var p20, p21, p22, p23, p24, p25, p26, p27, p28, p29, p2a, p2b, p2c, p2d, p2e, p2f: Long = 0L
}

private[ringbuffer] class MpscConsumerFields extends MpscPad2 {
  var consumerIndex: Long = 0L
}

private[ringbuffer] class MpscPad3 extends MpscConsumerFields {
  var p30, p31, p32, p33, p34, p35, p36, p37, p38, p39, p3a, p3b, p3c, p3d, p3e, p3f: Long = 0L
}

/**
 * A high-performance, non-blocking Multi-Producer Single-Consumer (MPSC) ring
 * buffer for generic reference types.
 *
 * Multiple threads may call [[offer]] concurrently, but only a single consumer
 * thread may call [[take]]. This is enforced by contract — no runtime check is
 * performed for the single-consumer invariant.
 *
 * The algorithm follows the JCTools `MpscArrayQueue` design:
 *   - Producers claim a slot via CAS on `producerIndex`, then write the element
 *     with release semantics.
 *   - The consumer reads elements using acquire semantics. A `null` slot
 *     indicates either an empty queue or a producer that has claimed a slot but
 *     has not yet written the element (the "FastFlow" pattern). In the latter
   *     case, `take` returns `null` rather than spinning, following `relaxedPoll`
 *     semantics.
 *   - A cached `producerLimit` avoids reading `consumerIndex` on every offer,
 *     reducing cross-core traffic.
 *
 * **Null elements are not permitted.** `offer(null)` throws
 * `NullPointerException`.
 *
 * @param capacity
 *   the buffer capacity, must be a positive power of two
 */
final class MpscRingBuffer[A <: AnyRef](val capacity: Int) extends MpscPad3 {
  require(capacity > 0 && (capacity & (capacity - 1)) == 0, s"capacity must be a positive power of 2, got: $capacity")

  import MpscRingBuffer._

  private val mask: Long            = (capacity - 1).toLong
  private val buffer: Array[AnyRef] = new Array[AnyRef](capacity)

  // Initialize producerLimit to capacity so the first offer doesn't need to read consumerIndex
  producerLimit = capacity.toLong

  /**
   * Tries to insert an element into the buffer without blocking.
   *
   * Any thread may call this method concurrently. The implementation uses a CAS
   * loop to claim a slot in the circular buffer, then writes the element with
   * release semantics.
   *
   * @param a
   *   the element to insert; must not be `null`
   * @throws NullPointerException
   *   if the element is `null`
   * @return
   *   `true` if the element was successfully inserted, `false` if the buffer is
   *   full
   */
  def offer(a: A): Boolean = {
    if (a == null) throw new NullPointerException("offer(null) is not permitted")
    val buf = buffer
    val m   = mask
    val cap = m + 1L

    var pLimit     = PRODUCER_LIMIT.getAcquire(this).asInstanceOf[Long]
    var pIdx: Long = 0L

    while (true) {
      pIdx = PRODUCER_INDEX.getAcquire(this).asInstanceOf[Long]
      if (pIdx >= pLimit) {
        val cIdx = CONSUMER_INDEX.getAcquire(this).asInstanceOf[Long]
        pLimit = cIdx + cap

        if (pIdx >= pLimit) {
          return false // FULL
        } else {
          // Racy update is benign — worst case another producer also refreshes
          PRODUCER_LIMIT.setRelease(this, pLimit)
        }
      }

      if (PRODUCER_INDEX.compareAndSet(this, pIdx, pIdx + 1L)) {
        // Won CAS — write element to the claimed slot
        val offset = (pIdx & m).toInt
        ARRAY_HANDLE.setRelease(buf, offset, a.asInstanceOf[AnyRef])
        return true
      }
      // CAS failed — another producer won; retry
    }

    false // unreachable, satisfies compiler
  }

  /**
   * Tries to remove an element from the buffer without blocking.
   *
   * Uses the FastFlow pattern: reads the array slot directly with acquire
   * semantics. A `null` value means either the buffer is empty or a producer
   * has claimed the slot but has not yet written the element. In both cases,
   * `null` is returned (relaxed poll semantics).
   *
   * @return
   *   the element, or `null` if the buffer is empty (or a producer is
   *   mid-write)
   * @note
   *   Must be called from the consumer thread only.
   */
  def take(): A = {
    val buf    = buffer
    val m      = mask
    val cIdx   = consumerIndex // plain read — single consumer
    val offset = (cIdx & m).toInt

    val e = ARRAY_HANDLE.getAcquire(buf, offset).asInstanceOf[A]
    if (e eq null) return null.asInstanceOf[A]

    ARRAY_HANDLE.setRelease(buf, offset, null.asInstanceOf[AnyRef])
    CONSUMER_INDEX.setRelease(this, cIdx + 1L)
    e
  }

  /**
   * Returns the approximate number of elements currently in the buffer.
   *
   * Under concurrent access this value is a snapshot and may be stale by the
   * time the caller observes it.
   */
  def size: Int = {
    val cIdx = CONSUMER_INDEX.getAcquire(this).asInstanceOf[Long]
    val pIdx = PRODUCER_INDEX.getAcquire(this).asInstanceOf[Long]
    Math.max(0, (pIdx - cIdx).toInt)
  }

  /**
   * Returns `true` if the buffer appears to contain no elements (approximate
   * under concurrency).
   */
  def isEmpty: Boolean = {
    val cIdx = CONSUMER_INDEX.getAcquire(this).asInstanceOf[Long]
    val pIdx = PRODUCER_INDEX.getAcquire(this).asInstanceOf[Long]
    pIdx == cIdx
  }

  /**
   * Returns `true` if the buffer appears to be full (approximate under
   * concurrency).
   */
  def isFull: Boolean = {
    val cIdx = CONSUMER_INDEX.getAcquire(this).asInstanceOf[Long]
    val pIdx = PRODUCER_INDEX.getAcquire(this).asInstanceOf[Long]
    (pIdx - cIdx).toInt >= capacity
  }

  /**
   * Drains up to `limit` elements from the buffer, passing each to the given
   * consumer callback.
   *
   * Elements are consumed in FIFO order. Uses relaxed poll semantics: stops at
   * the first `null` slot (either empty or producer mid-write).
   *
   * @param consumer
   *   the callback invoked for each drained element
   * @param limit
   *   the maximum number of elements to drain; must be non-negative
   * @throws IllegalArgumentException
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
}

object MpscRingBuffer {
  private val PRODUCER_INDEX: VarHandle =
    MethodHandles
      .privateLookupIn(classOf[MpscProducerFields], MethodHandles.lookup())
      .findVarHandle(classOf[MpscProducerFields], "producerIndex", classOf[Long])

  private val PRODUCER_LIMIT: VarHandle =
    MethodHandles
      .privateLookupIn(classOf[MpscProducerLimitFields], MethodHandles.lookup())
      .findVarHandle(classOf[MpscProducerLimitFields], "producerLimit", classOf[Long])

  private val CONSUMER_INDEX: VarHandle =
    MethodHandles
      .privateLookupIn(classOf[MpscConsumerFields], MethodHandles.lookup())
      .findVarHandle(classOf[MpscConsumerFields], "consumerIndex", classOf[Long])

  private val ARRAY_HANDLE: VarHandle =
    MethodHandles.arrayElementVarHandle(classOf[Array[AnyRef]])

  /**
   * Creates a new `MpscRingBuffer` with the given capacity (must be a power of
   * 2).
   */
  def apply[A <: AnyRef](capacity: Int): MpscRingBuffer[A] = new MpscRingBuffer[A](capacity)
}
