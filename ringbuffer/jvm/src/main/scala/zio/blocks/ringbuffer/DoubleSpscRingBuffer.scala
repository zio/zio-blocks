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

private[ringbuffer] class DoubleSpscPad0 {
  var p00, p01, p02, p03, p04, p05, p06, p07, p08, p09, p0a, p0b, p0c, p0d, p0e, p0f: Long = 0L
}

private[ringbuffer] class DoubleSpscProducerFields extends DoubleSpscPad0 {
  var producerIndex: Long = 0L
  var producerLimit: Long = 0L
}

private[ringbuffer] class DoubleSpscPad1 extends DoubleSpscProducerFields {
  var p10, p11, p12, p13, p14, p15, p16, p17, p18, p19, p1a, p1b, p1c, p1d, p1e, p1f: Long = 0L
}

private[ringbuffer] class DoubleSpscConsumerFields extends DoubleSpscPad1 {
  var consumerIndex: Long = 0L
}

private[ringbuffer] class DoubleSpscPad2 extends DoubleSpscConsumerFields {
  var p20, p21, p22, p23, p24, p25, p26, p27, p28, p29, p2a, p2b, p2c, p2d, p2e, p2f: Long = 0L
}

/**
 * High-performance non-blocking SPSC ring buffer specialized for `Double`.
 *
 * Layout: a single `Array[Long]` storing raw `doubleToRawLongBits`. The
 * FastFlow signal uses two reserved NaN bit patterns: `EMPTY_BITS`
 * (`0xFFF8_0000_0000_0001L`) is the empty marker, `DONE_BITS`
 * (`0xFFF8_0000_0000_0002L`) is the in-band end-of-stream marker. If the user
 * offers a `Double` whose raw bits happen to equal either reserved pattern, it
 * is canonicalized to `Double.NaN`'s standard bit pattern
 * (`0x7FF8_0000_0000_0000L`) on insertion.
 *
 * **This canonicalization is unobservable**: IEEE 754 guarantees `NaN != NaN`,
 * all NaNs compare equal under reference NaN semantics, and there is no
 * portable Scala/Java way to round-trip a specific NaN payload through
 * arithmetic. The remapping only affects two specific raw bit patterns, which
 * the user could not have meaningfully produced through normal floating-point
 * operations.
 *
 * In-band "done" sentinel: producers can call `offerDone()` to insert the
 * end-of-stream marker after the last data value. Consumers reading via
 * `pollPacked()` receive `EMPTY_BITS` for empty, `DONE_BITS` for the sentinel,
 * or the raw `doubleToRawLongBits` value for data. This removes the need for a
 * separate control queue alongside the data queue.
 *
 * One memory-ordered op per insert/remove, one cache line per slot.
 *
 * SPSC: one producer thread, one consumer thread.
 *
 * @param capacity
 *   must be a positive power of two
 */
final class DoubleSpscRingBuffer(val capacity: Int) extends DoubleSpscPad2 {
  require(capacity > 0 && (capacity & (capacity - 1)) == 0, s"capacity must be a positive power of 2, got: $capacity")

  import DoubleSpscRingBuffer._

  private val mask: Long        = (capacity - 1).toLong
  private val data: Array[Long] = {
    val a = new Array[Long](capacity)
    java.util.Arrays.fill(a, EMPTY_BITS)
    a
  }
  private val lookAheadStep: Long = Math.max(1, Math.min(capacity / 4, 4096)).toLong

  def offer(a: Double): Boolean = {
    val pIdx = producerIndex
    if (pIdx >= producerLimit) {
      if (!offerSlowPath(pIdx)) return false
    }
    val offset  = (pIdx & mask).toInt
    val rawBits = java.lang.Double.doubleToRawLongBits(a)
    val toStore = if (rawBits == EMPTY_BITS || rawBits == DONE_BITS) CANONICAL_NAN_BITS else rawBits
    LONG_HANDLE.setRelease(data, offset, toStore)
    PRODUCER_INDEX.setOpaque(this, pIdx + 1L)
    true
  }

  /**
   * Insert the in-band done sentinel. Returns `false` if the buffer is full;
   * the caller is expected to retry until it succeeds.
   */
  def offerDone(): Boolean = {
    val pIdx = producerIndex
    if (pIdx >= producerLimit) {
      if (!offerSlowPath(pIdx)) return false
    }
    val offset = (pIdx & mask).toInt
    LONG_HANDLE.setRelease(data, offset, DONE_BITS)
    PRODUCER_INDEX.setOpaque(this, pIdx + 1L)
    true
  }

  def peek(): Boolean = {
    val offset = (consumerIndex & mask).toInt
    (LONG_HANDLE.getAcquire(data, offset): Long) != EMPTY_BITS
  }

  def take(): Double = {
    val cIdx   = consumerIndex
    val offset = (cIdx & mask).toInt
    val bits   = (LONG_HANDLE.getAcquire(data, offset): Long)
    if (bits == EMPTY_BITS) return java.lang.Double.longBitsToDouble(EMPTY_BITS)
    LONG_HANDLE.setRelease(data, offset, EMPTY_BITS)
    CONSUMER_INDEX.setOpaque(this, cIdx + 1L)
    java.lang.Double.longBitsToDouble(bits)
  }

  /**
   * Atomically polls the next slot and returns the raw bits:
   *   - `EMPTY_BITS` — slot is empty; consumer index is NOT advanced.
   *   - `DONE_BITS` — in-band done sentinel; consumer index advanced.
   *   - any other value — `doubleToRawLongBits(value)` for the payload;
   *     consumer index advanced. Decode with `Double.longBitsToDouble`.
   */
  def pollPacked(): Long = {
    val cIdx   = consumerIndex
    val offset = (cIdx & mask).toInt
    val bits   = (LONG_HANDLE.getAcquire(data, offset): Long)
    if (bits == EMPTY_BITS) return EMPTY_BITS
    LONG_HANDLE.setRelease(data, offset, EMPTY_BITS)
    CONSUMER_INDEX.setOpaque(this, cIdx + 1L)
    bits
  }

  def isEmpty: Boolean = {
    val cIdx = CONSUMER_INDEX.getAcquire(this).asInstanceOf[Long]
    val pIdx = PRODUCER_INDEX.getAcquire(this).asInstanceOf[Long]
    pIdx == cIdx
  }

  def isFull: Boolean = {
    val cIdx = CONSUMER_INDEX.getAcquire(this).asInstanceOf[Long]
    val pIdx = PRODUCER_INDEX.getAcquire(this).asInstanceOf[Long]
    (pIdx - cIdx).toInt == capacity
  }

  def size: Int = {
    val cIdx = CONSUMER_INDEX.getAcquire(this).asInstanceOf[Long]
    val pIdx = PRODUCER_INDEX.getAcquire(this).asInstanceOf[Long]
    (pIdx - cIdx).toInt
  }

  private def offerSlowPath(pIdx: Long): Boolean = {
    val lookAheadOffset = ((pIdx + lookAheadStep) & mask).toInt
    if ((LONG_HANDLE.getAcquire(data, lookAheadOffset): Long) == EMPTY_BITS) {
      producerLimit = pIdx + lookAheadStep
      true
    } else {
      val currentOffset = (pIdx & mask).toInt
      if ((LONG_HANDLE.getAcquire(data, currentOffset): Long) != EMPTY_BITS) {
        false
      } else {
        producerLimit = pIdx + 1L
        true
      }
    }
  }
}

object DoubleSpscRingBuffer {

  /** Reserved NaN bit pattern used as the empty-slot marker. */
  final val EMPTY_BITS: Long = 0xfff8000000000001L

  /** Reserved NaN bit pattern used as the in-band done sentinel. */
  final val DONE_BITS: Long = 0xfff8000000000002L

  // Canonical NaN bit pattern (matches `Double.NaN`).
  private final val CANONICAL_NAN_BITS: Long = 0x7ff8000000000000L

  private val PRODUCER_INDEX: VarHandle =
    MethodHandles
      .privateLookupIn(classOf[DoubleSpscProducerFields], MethodHandles.lookup())
      .findVarHandle(classOf[DoubleSpscProducerFields], "producerIndex", classOf[Long])

  private val CONSUMER_INDEX: VarHandle =
    MethodHandles
      .privateLookupIn(classOf[DoubleSpscConsumerFields], MethodHandles.lookup())
      .findVarHandle(classOf[DoubleSpscConsumerFields], "consumerIndex", classOf[Long])

  private val LONG_HANDLE: VarHandle = MethodHandles.arrayElementVarHandle(classOf[Array[Long]])

  def apply(capacity: Int): DoubleSpscRingBuffer = new DoubleSpscRingBuffer(capacity)
}
