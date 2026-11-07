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

private[ringbuffer] class FloatSpscPad0 {
  var p00, p01, p02, p03, p04, p05, p06, p07, p08, p09, p0a, p0b, p0c, p0d, p0e, p0f: Long = 0L
}

private[ringbuffer] class FloatSpscProducerFields extends FloatSpscPad0 {
  var producerIndex: Long = 0L
  var producerLimit: Long = 0L
}

private[ringbuffer] class FloatSpscPad1 extends FloatSpscProducerFields {
  var p10, p11, p12, p13, p14, p15, p16, p17, p18, p19, p1a, p1b, p1c, p1d, p1e, p1f: Long = 0L
}

private[ringbuffer] class FloatSpscConsumerFields extends FloatSpscPad1 {
  var consumerIndex: Long = 0L
}

private[ringbuffer] class FloatSpscPad2 extends FloatSpscConsumerFields {
  var p20, p21, p22, p23, p24, p25, p26, p27, p28, p29, p2a, p2b, p2c, p2d, p2e, p2f: Long = 0L
}

/**
 * A bounded first-in, first-out queue of `Float` values, stored without boxing,
 * for one producer thread and one consumer thread, with an in-band
 * end-of-stream marker. Call [[offer]] and [[offerDone]] from one producer
 * thread only, and [[take]], [[pollPacked]] and [[peek]] from one consumer
 * thread only; nothing checks this. Like [[SpscRingBuffer]], neither side
 * blocks and the slot itself tells each side whether it is free. JVM only.
 *
 * Each slot is one `Long`: the high 32 bits are a tag (`0` = empty, `1` =
 * value, `2` = end marker) and the low 32 bits hold `floatToRawIntBits` of the
 * value. An empty slot is `0L` and a filled slot never is, so every `Float` is
 * stored bit for bit, `0.0f`, `-0.0f`, infinities and every `NaN` payload
 * included.
 *
 * The producer ends the stream with [[offerDone]]. The consumer reads with
 * [[pollPacked]] to tell the cases apart: it returns
 * [[FloatSpscRingBuffer.EMPTY_PACKED]] when the buffer is empty,
 * [[FloatSpscRingBuffer.DONE_PACKED]] for the end marker, and otherwise a
 * tagged `Long` whose low 32 bits are the value's bits
 * (`java.lang.Float.intBitsToFloat(packed.toInt)`). [[take]] returns `0.0f` for
 * an empty buffer, for the end marker and for the value `0.0f` alike.
 *
 * @param capacity
 *   the number of slots: a power of two of at least 1 (1, 2, 4, ...); any other
 *   value throws `IllegalArgumentException`
 * @see
 *   [[https://zioblocks.com/reference/ringbuffer/spsc/ SPSC RingBuffer]]
 */
final class FloatSpscRingBuffer(val capacity: Int) extends FloatSpscPad2 {
  require(capacity > 0 && (capacity & (capacity - 1)) == 0, s"capacity must be a positive power of 2, got: $capacity")

  import FloatSpscRingBuffer._

  private val mask: Long          = (capacity - 1).toLong
  private val data: Array[Long]   = new Array[Long](capacity)
  private val lookAheadStep: Long = Math.max(1, Math.min(capacity / 4, 4096)).toLong

  /**
   * Appends `a` and returns `true` if a slot is free; returns `false`, without
   * waiting, when the buffer is full. Call it from the producer thread only.
   */
  def offer(a: Float): Boolean = {
    val pIdx = producerIndex
    if (pIdx >= producerLimit) {
      if (!offerSlowPath(pIdx)) return false
    }
    val offset = (pIdx & mask).toInt
    val packed = TAG_FULL | (java.lang.Float.floatToRawIntBits(a).toLong & LOW32_MASK)
    LONG_HANDLE.setRelease(data, offset, packed)
    PRODUCER_INDEX.setOpaque(this, pIdx + 1L)
    true
  }

  /**
   * Appends the end-of-stream marker and returns `true` if a slot is free;
   * returns `false`, without waiting, when the buffer is full, and the caller
   * retries. The marker takes a slot and counts in [[size]]; [[pollPacked]]
   * returns `DONE_PACKED` for it. Call it from the producer thread only, after
   * the last value.
   */
  def offerDone(): Boolean = {
    val pIdx = producerIndex
    if (pIdx >= producerLimit) {
      if (!offerSlowPath(pIdx)) return false
    }
    val offset = (pIdx & mask).toInt
    LONG_HANDLE.setRelease(data, offset, DONE_PACKED)
    PRODUCER_INDEX.setOpaque(this, pIdx + 1L)
    true
  }

  /**
   * Returns `true` when the next slot holds a value or the end marker, without
   * removing it. Call it from the consumer thread only.
   */
  def peek(): Boolean = {
    val offset = (consumerIndex & mask).toInt
    (LONG_HANDLE.getAcquire(data, offset): Long) != 0L
  }

  /**
   * Removes and returns the next value, or returns `0.0f` when the buffer is
   * empty. When the next slot holds the end marker, it removes the marker and
   * returns `0.0f` as well, so use [[pollPacked]] when `0.0f`, "empty" and
   * "done" must be told apart. Call it from the consumer thread only.
   */
  def take(): Float = {
    val cIdx   = consumerIndex
    val offset = (cIdx & mask).toInt
    val packed = (LONG_HANDLE.getAcquire(data, offset): Long)
    if (packed == 0L) return 0.0f
    LONG_HANDLE.setRelease(data, offset, 0L)
    CONSUMER_INDEX.setOpaque(this, cIdx + 1L)
    java.lang.Float.intBitsToFloat(packed.toInt)
  }

  /**
   * Removes the next slot's content and returns it as a raw `Long`:
   * [[FloatSpscRingBuffer.EMPTY_PACKED]] (`0L`) when the buffer is empty, in
   * which case nothing is removed; [[FloatSpscRingBuffer.DONE_PACKED]] for the
   * end marker; otherwise the tagged value, decoded with
   * `java.lang.Float.intBitsToFloat(packed.toInt)`. Call it from the consumer
   * thread only.
   */
  def pollPacked(): Long = {
    val cIdx   = consumerIndex
    val offset = (cIdx & mask).toInt
    val packed = (LONG_HANDLE.getAcquire(data, offset): Long)
    if (packed == 0L) return 0L
    LONG_HANDLE.setRelease(data, offset, 0L)
    CONSUMER_INDEX.setOpaque(this, cIdx + 1L)
    packed
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
    if ((LONG_HANDLE.getAcquire(data, lookAheadOffset): Long) == 0L) {
      producerLimit = pIdx + lookAheadStep
      true
    } else {
      val currentOffset = (pIdx & mask).toInt
      if ((LONG_HANDLE.getAcquire(data, currentOffset): Long) != 0L) {
        false
      } else {
        producerLimit = pIdx + 1L
        true
      }
    }
  }
}

/**
 * Creates [[FloatSpscRingBuffer]] instances and holds its marker values.
 */
object FloatSpscRingBuffer {

  /**
   * The value `pollPacked` returns when the buffer is empty: `0L`.
   */
  final val EMPTY_PACKED: Long = 0L

  /**
   * The value `pollPacked` returns for the end-of-stream marker that
   * `offerDone` appends: `2L << 32`.
   */
  final val DONE_PACKED: Long = 2L << 32

  private final val TAG_FULL: Long   = 1L << 32
  private final val LOW32_MASK: Long = 0xffffffffL

  private val PRODUCER_INDEX: VarHandle =
    MethodHandles
      .privateLookupIn(classOf[FloatSpscProducerFields], MethodHandles.lookup())
      .findVarHandle(classOf[FloatSpscProducerFields], "producerIndex", classOf[Long])

  private val CONSUMER_INDEX: VarHandle =
    MethodHandles
      .privateLookupIn(classOf[FloatSpscConsumerFields], MethodHandles.lookup())
      .findVarHandle(classOf[FloatSpscConsumerFields], "consumerIndex", classOf[Long])

  private val LONG_HANDLE: VarHandle = MethodHandles.arrayElementVarHandle(classOf[Array[Long]])

  /**
   * Creates an empty buffer with `capacity` slots, the same as
   * `new FloatSpscRingBuffer(capacity)`.
   *
   * @throws java.lang.IllegalArgumentException
   *   if `capacity` is not a power of two of at least 1
   */
  def apply(capacity: Int): FloatSpscRingBuffer = new FloatSpscRingBuffer(capacity)
}
