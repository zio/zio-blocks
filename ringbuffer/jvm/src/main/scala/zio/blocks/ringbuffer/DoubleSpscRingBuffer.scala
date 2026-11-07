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
 * A bounded first-in, first-out queue of `Double` values, stored without
 * boxing, for one producer thread and one consumer thread, with an in-band
 * end-of-stream marker. Call [[offer]] and [[offerDone]] from one producer
 * thread only, and [[take]], [[pollPacked]] and [[peek]] from one consumer
 * thread only; nothing checks this. Like [[SpscRingBuffer]], neither side
 * blocks and the slot itself tells each side whether it is free. JVM only.
 *
 * Each slot holds `doubleToRawLongBits` of the value. Two `NaN` bit patterns
 * are reserved: [[DoubleSpscRingBuffer.EMPTY_BITS]] (`0xfff8000000000001L`)
 * marks an empty slot and [[DoubleSpscRingBuffer.DONE_BITS]]
 * (`0xfff8000000000002L`) is the end marker. A value with either of these bit
 * patterns is stored as the bits of `Double.NaN` (`0x7ff8000000000000L`), so
 * its payload is lost, which [[pollPacked]] shows; it still reads back as a
 * `NaN`. Every other value, including every other `NaN` payload, is stored bit
 * for bit.
 *
 * The producer ends the stream with [[offerDone]]. The consumer reads with
 * [[pollPacked]] to tell the cases apart: it returns `EMPTY_BITS` when the
 * buffer is empty, `DONE_BITS` for the end marker, and otherwise the value's
 * raw bits (`java.lang.Double.longBitsToDouble(bits)`). [[take]] returns a
 * `NaN` for an empty buffer, for the end marker and for a stored `NaN` alike.
 *
 * @param capacity
 *   the number of slots: a power of two of at least 1 (1, 2, 4, ...); any other
 *   value throws `IllegalArgumentException`
 * @see
 *   [[https://zioblocks.com/reference/ringbuffer/spsc/ SPSC RingBuffer]]
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

  /**
   * Appends `a` and returns `true` if a slot is free; returns `false`, without
   * waiting, when the buffer is full. A value whose bits are `EMPTY_BITS` or
   * `DONE_BITS` is stored as `Double.NaN`. Call it from the producer thread
   * only.
   */
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
   * Appends the end-of-stream marker and returns `true` if a slot is free;
   * returns `false`, without waiting, when the buffer is full, and the caller
   * retries. The marker takes a slot and counts in [[size]]; [[pollPacked]]
   * returns `DONE_BITS` for it. Call it from the producer thread only, after
   * the last value.
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

  /**
   * Returns `true` when the next slot holds a value or the end marker, without
   * removing it. Call it from the consumer thread only.
   */
  def peek(): Boolean = {
    val offset = (consumerIndex & mask).toInt
    (LONG_HANDLE.getAcquire(data, offset): Long) != EMPTY_BITS
  }

  /**
   * Removes and returns the next value, or returns a `NaN` (with the bits
   * `EMPTY_BITS`) when the buffer is empty. When the next slot holds the end
   * marker, it removes the marker and returns a `NaN` with the bits
   * `DONE_BITS`, so use [[pollPacked]] to tell values, "empty" and "done"
   * apart. Call it from the consumer thread only.
   */
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
   * Removes the next slot's content and returns its raw bits:
   * [[DoubleSpscRingBuffer.EMPTY_BITS]] when the buffer is empty, in which case
   * nothing is removed; [[DoubleSpscRingBuffer.DONE_BITS]] for the end marker;
   * otherwise the value's bits, decoded with
   * `java.lang.Double.longBitsToDouble`. Call it from the consumer thread only.
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

  /**
   * Returns `true` when the buffer holds neither values nor the end marker.
   * While the other thread offers or takes, the answer can be out of date by
   * the time it returns.
   */
  def isEmpty: Boolean = {
    val cIdx = CONSUMER_INDEX.getAcquire(this).asInstanceOf[Long]
    val pIdx = PRODUCER_INDEX.getAcquire(this).asInstanceOf[Long]
    pIdx == cIdx
  }

  /**
   * Returns `true` when every slot holds a value or the end marker. While the
   * other thread offers or takes, the answer can be out of date by the time it
   * returns.
   */
  def isFull: Boolean = {
    val cIdx = CONSUMER_INDEX.getAcquire(this).asInstanceOf[Long]
    val pIdx = PRODUCER_INDEX.getAcquire(this).asInstanceOf[Long]
    (pIdx - cIdx).toInt == capacity
  }

  /**
   * Returns the number of slots in use, the end marker included. While the
   * other thread offers or takes, the result is an estimate: the two indices
   * are read one after the other, so it can be out of date, negative, or larger
   * than `capacity`.
   */
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

/**
 * Creates [[DoubleSpscRingBuffer]] instances and holds its marker values.
 */
object DoubleSpscRingBuffer {

  /**
   * The bits [[DoubleSpscRingBuffer#pollPacked]] returns when the buffer is
   * empty, a `NaN` pattern: `0xfff8000000000001L`.
   */
  final val EMPTY_BITS: Long = 0xfff8000000000001L

  /**
   * The bits of the end-of-stream marker that
   * [[DoubleSpscRingBuffer#offerDone]] appends, a `NaN` pattern:
   * `0xfff8000000000002L`.
   */
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

  /**
   * Creates an empty buffer with `capacity` slots, the same as
   * `new DoubleSpscRingBuffer(capacity)`.
   *
   * @throws java.lang.IllegalArgumentException
   *   if `capacity` is not a power of two of at least 1
   */
  def apply(capacity: Int): DoubleSpscRingBuffer = new DoubleSpscRingBuffer(capacity)
}
