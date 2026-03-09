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

private[ringbuffer] class SpmcPad0 {
  var p00, p01, p02, p03, p04, p05, p06, p07, p08, p09, p0a, p0b, p0c, p0d, p0e, p0f: Long = 0L
}

private[ringbuffer] class SpmcProducerFields extends SpmcPad0 {
  var producerIndex: Long = 0L
  var producerLimit: Long = 0L // look-ahead boundary; initially 0 forces first offer to slow path
}

private[ringbuffer] class SpmcPad1 extends SpmcProducerFields {
  var p10, p11, p12, p13, p14, p15, p16, p17, p18, p19, p1a, p1b, p1c, p1d, p1e, p1f: Long = 0L
}

private[ringbuffer] class SpmcConsumerFields extends SpmcPad1 {
  var consumerIndex: Long = 0L
}

private[ringbuffer] class SpmcPad2 extends SpmcConsumerFields {
  var p20, p21, p22, p23, p24, p25, p26, p27, p28, p29, p2a, p2b, p2c, p2d, p2e, p2f: Long = 0L
}

/**
 * A high-performance, non-blocking SPMC (Single-Producer Multi-Consumer) ring
 * buffer for generic reference types.
 *
 * The producer uses index-based capacity checking with a cached `producerLimit`
 * derived from `consumerIndex`. On the fast path, the producer avoids reading
 * the volatile `consumerIndex` entirely; the slow path re-reads it to refresh
 * the limit. The consumer side uses a CAS loop on `consumerIndex` to allow
 * multiple consumer threads to safely dequeue elements concurrently.
 *
 * '''Design:''' Consumers do not clear (null) array slots after reading.
 * Instead, the producer freely overwrites slots once index-based capacity
 * checking confirms the consumer has advanced past them. This eliminates the
 * producer-consumer slot-clearing race that would otherwise occur in SPMC when
 * a consumer CAS-claims a slot but hasn't yet nulled it. The element's validity
 * is determined solely by comparing `producerIndex` and `consumerIndex`, never
 * by null-checking slots.
 *
 * Optimized for SPMC: exactly one producer thread and any number of consumer
 * threads may access the buffer concurrently. Using multiple producer threads
 * results in undefined behavior.
 *
 * **Null elements are not permitted.** `offer(null)` throws
 * `NullPointerException`.
 *
 * @param capacity
 *   the buffer capacity, must be a positive power of two
 */
final class SpmcRingBuffer[A <: AnyRef](val capacity: Int) extends SpmcPad2 {
  require(capacity > 0 && (capacity & (capacity - 1)) == 0, s"capacity must be a positive power of 2, got: $capacity")

  import SpmcRingBuffer._

  private val mask: Long            = (capacity - 1).toLong
  private val buffer: Array[AnyRef] = new Array[AnyRef](capacity)

  /**
   * Tries to insert an element into the buffer without blocking.
   *
   * Uses index-based capacity checking with a cached `producerLimit` derived
   * from `consumerIndex`. On the fast path, the producer avoids reading the
   * volatile `consumerIndex`. When the cached limit is reached, the slow path
   * re-reads `consumerIndex` to refresh it.
   *
   * @param a
   *   the element to insert; must not be `null`
   * @throws NullPointerException
   *   if the element is `null`
   * @return
   *   `true` if the element was successfully inserted, `false` if the buffer is
   *   full
   * @note
   *   Must be called from the producer thread only.
   */
  def offer(a: A): Boolean = {
    if (a == null) throw new NullPointerException("offer(null) is not permitted")
    val pIdx = producerIndex

    if (pIdx >= producerLimit) {
      if (!offerSlowPath(pIdx)) return false
    }

    val offset = (pIdx & mask).toInt
    ARRAY_HANDLE.setRelease(buffer, offset, a.asInstanceOf[AnyRef])
    PRODUCER_INDEX.setRelease(this, pIdx + 1L)
    true
  }

  /**
   * Tries to remove an element from the buffer without blocking.
   *
   * Multiple consumer threads may call this method concurrently. Each consumer
   * reads the current `consumerIndex`, checks against `producerIndex` to detect
   * an empty buffer, reads the element optimistically, then attempts a CAS on
   * `consumerIndex` to claim the slot. Reading the element before the CAS is
   * critical: it ensures the consumer has the value before the producer can
   * overwrite the slot. If the CAS fails, another consumer won and the read is
   * discarded; the loop retries. Consumers do not clear slots; the producer
   * overwrites them on the next lap.
   *
   * @return
   *   the element, or `null` if the buffer is empty
   * @note
   *   Safe to call from any consumer thread concurrently.
   */
  def take(): A = {
    var cIdx = CONSUMER_INDEX.getVolatile(this).asInstanceOf[Long]
    while (true) {
      val pIdx = PRODUCER_INDEX.getAcquire(this).asInstanceOf[Long]
      if (pIdx == cIdx) return null.asInstanceOf[A]

      // Read the element BEFORE the CAS. Once we CAS consumerIndex, the producer is free to
      // overwrite this slot. By reading first, we guarantee we capture the value while it's
      // still valid (the producer can't be writing here because pIdx <= cIdx + capacity, and
      // the slot at cIdx hasn't been released to the producer yet).
      val offset = (cIdx & mask).toInt
      val e      = ARRAY_HANDLE.getAcquire(buffer, offset).asInstanceOf[A]

      // Re-read consumerIndex: if it changed since our initial read, another consumer claimed
      // this slot and the element we read may be from a different generation. Retry.
      if (cIdx != (CONSUMER_INDEX.getVolatile(this).asInstanceOf[Long])) {
        cIdx = CONSUMER_INDEX.getVolatile(this).asInstanceOf[Long]
      } else if (CONSUMER_INDEX.compareAndSet(this, cIdx, cIdx + 1L)) {
        return e
      } else {
        // CAS failed — another consumer won. Re-read and retry.
        cIdx = CONSUMER_INDEX.getVolatile(this).asInstanceOf[Long]
      }
    }
    null.asInstanceOf[A] // unreachable; satisfies compiler
  }

  /**
   * Returns the number of elements currently in the buffer (approximate under
   * concurrency).
   *
   * The value is a snapshot and may be stale by the time the caller acts on it.
   */
  def size: Int = {
    val cIdx = CONSUMER_INDEX.getVolatile(this).asInstanceOf[Long]
    val pIdx = PRODUCER_INDEX.getAcquire(this).asInstanceOf[Long]
    (pIdx - cIdx).toInt
  }

  /**
   * Returns `true` if the buffer contains no elements (approximate under
   * concurrency).
   *
   * The value is a snapshot and may be stale by the time the caller acts on it.
   */
  def isEmpty: Boolean = {
    val cIdx = CONSUMER_INDEX.getVolatile(this).asInstanceOf[Long]
    val pIdx = PRODUCER_INDEX.getAcquire(this).asInstanceOf[Long]
    pIdx == cIdx
  }

  /**
   * Returns `true` if the buffer is full (approximate under concurrency).
   *
   * The value is a snapshot and may be stale by the time the caller acts on it.
   */
  def isFull: Boolean = {
    val cIdx = CONSUMER_INDEX.getVolatile(this).asInstanceOf[Long]
    val pIdx = PRODUCER_INDEX.getAcquire(this).asInstanceOf[Long]
    (pIdx - cIdx).toInt == capacity
  }

  private def offerSlowPath(pIdx: Long): Boolean = {
    val cIdx     = CONSUMER_INDEX.getVolatile(this).asInstanceOf[Long]
    val newLimit = cIdx + capacity.toLong
    producerLimit = newLimit
    pIdx < newLimit
  }
}

object SpmcRingBuffer {
  private val PRODUCER_INDEX: VarHandle =
    MethodHandles
      .privateLookupIn(classOf[SpmcProducerFields], MethodHandles.lookup())
      .findVarHandle(classOf[SpmcProducerFields], "producerIndex", classOf[Long])

  private val CONSUMER_INDEX: VarHandle =
    MethodHandles
      .privateLookupIn(classOf[SpmcConsumerFields], MethodHandles.lookup())
      .findVarHandle(classOf[SpmcConsumerFields], "consumerIndex", classOf[Long])

  private val ARRAY_HANDLE: VarHandle =
    MethodHandles.arrayElementVarHandle(classOf[Array[AnyRef]])

  /**
   * Creates a new `SpmcRingBuffer` with the given capacity.
   *
   * @param capacity
   *   the buffer capacity, must be a positive power of two
   * @tparam A
   *   the element type (must be a reference type)
   * @return
   *   a new lock-free SPMC ring buffer
   */
  def apply[A <: AnyRef](capacity: Int): SpmcRingBuffer[A] = new SpmcRingBuffer[A](capacity)
}
