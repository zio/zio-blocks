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

private[ringbuffer] class LongSpscPad0 {
  var p00, p01, p02, p03, p04, p05, p06, p07, p08, p09, p0a, p0b, p0c, p0d, p0e, p0f: Long = 0L
}

private[ringbuffer] class LongSpscProducerFields extends LongSpscPad0 {
  var producerIndex: Long = 0L
  var producerLimit: Long = 0L
}

private[ringbuffer] class LongSpscPad1 extends LongSpscProducerFields {
  var p10, p11, p12, p13, p14, p15, p16, p17, p18, p19, p1a, p1b, p1c, p1d, p1e, p1f: Long = 0L
}

private[ringbuffer] class LongSpscConsumerFields extends LongSpscPad1 {
  var consumerIndex: Long = 0L
}

private[ringbuffer] class LongSpscPad2 extends LongSpscConsumerFields {
  var p20, p21, p22, p23, p24, p25, p26, p27, p28, p29, p2a, p2b, p2c, p2d, p2e, p2f: Long = 0L
}

/**
 * A bounded first-in, first-out queue of `Long` values, stored without boxing,
 * for one producer thread and one consumer thread, with an in-band
 * end-of-stream marker. Call [[offer]] and [[offerDone]] from one producer
 * thread only, and [[take]], [[pollPacked]] and [[peek]] from one consumer
 * thread only; nothing checks this. Like [[SpscRingBuffer]], neither side
 * blocks and the slot itself tells each side whether it is free. JVM only.
 *
 * Each slot holds the `Long` itself, so two values are reserved:
 * [[LongSpscRingBuffer.EMPTY]] (`Long.MinValue`) marks an empty slot and
 * [[LongSpscRingBuffer.DONE]] (`Long.MinValue + 1L`) is the end marker.
 * Offering either one throws `IllegalArgumentException`; every other `Long`
 * passes through unchanged.
 *
 * The producer ends the stream with [[offerDone]]. [[take]] and [[pollPacked]]
 * both return `EMPTY` when the buffer is empty, `DONE` for the end marker, and
 * otherwise the value.
 *
 * @param capacity
 *   the number of slots: a power of two of at least 1 (1, 2, 4, ...); any other
 *   value throws `IllegalArgumentException`
 * @see
 *   [[https://zioblocks.com/reference/ringbuffer/spsc/ SPSC RingBuffer]]
 */
final class LongSpscRingBuffer(val capacity: Int) extends LongSpscPad2 {
  require(capacity > 0 && (capacity & (capacity - 1)) == 0, s"capacity must be a positive power of 2, got: $capacity")

  import LongSpscRingBuffer._

  private val mask: Long        = (capacity - 1).toLong
  private val data: Array[Long] = {
    val a = new Array[Long](capacity)
    java.util.Arrays.fill(a, EMPTY)
    a
  }
  private val lookAheadStep: Long = Math.max(1, Math.min(capacity / 4, 4096)).toLong

  /**
   * Appends `a` and returns `true` if a slot is free; returns `false`, without
   * waiting, when the buffer is full. Call it from the producer thread only.
   *
   * @throws java.lang.IllegalArgumentException
   *   if `a` is `Long.MinValue` or `Long.MinValue + 1L`, the reserved
   *   [[LongSpscRingBuffer.EMPTY]] and [[LongSpscRingBuffer.DONE]] values
   */
  def offer(a: Long): Boolean = {
    if (a == EMPTY || a == DONE)
      throw new IllegalArgumentException(
        s"offer($a) is not permitted: Long.MinValue and Long.MinValue + 1L are reserved sentinels"
      )
    val pIdx = producerIndex
    if (pIdx >= producerLimit) {
      if (!offerSlowPath(pIdx)) return false
    }
    val offset = (pIdx & mask).toInt
    LONG_HANDLE.setRelease(data, offset, a)
    PRODUCER_INDEX.setOpaque(this, pIdx + 1L)
    true
  }

  /**
   * Appends the end-of-stream marker and returns `true` if a slot is free;
   * returns `false`, without waiting, when the buffer is full, and the caller
   * retries. The marker takes a slot and counts in [[size]]; [[pollPacked]]
   * returns `DONE` for it. Call it from the producer thread only, after the
   * last value.
   */
  def offerDone(): Boolean = {
    val pIdx = producerIndex
    if (pIdx >= producerLimit) {
      if (!offerSlowPath(pIdx)) return false
    }
    val offset = (pIdx & mask).toInt
    LONG_HANDLE.setRelease(data, offset, DONE)
    PRODUCER_INDEX.setOpaque(this, pIdx + 1L)
    true
  }

  /**
   * Appends the end marker like [[offerDone]], running `beforePublish` first,
   * only once a free slot is known and right before the marker is stored with
   * release ordering. Returns `false`, without running `beforePublish`, when
   * the buffer is full. A consumer that reads this marker also sees what
   * `beforePublish` wrote, which lets a caller pass a value beside the marker.
   * Producer thread only.
   */
  private[blocks] def offerDoneAfter(beforePublish: => Unit): Boolean = {
    val pIdx = producerIndex
    if (pIdx >= producerLimit) {
      if (!offerSlowPath(pIdx)) return false
    }
    beforePublish
    val offset = (pIdx & mask).toInt
    LONG_HANDLE.setRelease(data, offset, DONE)
    PRODUCER_INDEX.setOpaque(this, pIdx + 1L)
    true
  }

  /**
   * Returns `true` when the next slot holds a value or the end marker, without
   * removing it. Call it from the consumer thread only.
   */
  def peek(): Boolean = {
    val offset = (consumerIndex & mask).toInt
    (LONG_HANDLE.getAcquire(data, offset): Long) != EMPTY
  }

  /**
   * Removes and returns the next value, or returns [[LongSpscRingBuffer.EMPTY]]
   * without removing anything when the buffer is empty. When the next slot
   * holds the end marker, it removes the marker and returns
   * [[LongSpscRingBuffer.DONE]]. Call it from the consumer thread only.
   */
  def take(): Long = {
    val cIdx   = consumerIndex
    val offset = (cIdx & mask).toInt
    val e      = (LONG_HANDLE.getAcquire(data, offset): Long)
    if (e == EMPTY) return EMPTY
    LONG_HANDLE.setRelease(data, offset, EMPTY)
    CONSUMER_INDEX.setOpaque(this, cIdx + 1L)
    e
  }

  /**
   * Removes the next slot's content and returns it:
   * [[LongSpscRingBuffer.EMPTY]] when the buffer is empty, in which case
   * nothing is removed; [[LongSpscRingBuffer.DONE]] for the end marker;
   * otherwise the value. It returns the same as [[take]]. Call it from the
   * consumer thread only.
   */
  def pollPacked(): Long = {
    val cIdx   = consumerIndex
    val offset = (cIdx & mask).toInt
    val e      = (LONG_HANDLE.getAcquire(data, offset): Long)
    if (e == EMPTY) return EMPTY
    LONG_HANDLE.setRelease(data, offset, EMPTY)
    CONSUMER_INDEX.setOpaque(this, cIdx + 1L)
    e
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
    if ((LONG_HANDLE.getAcquire(data, lookAheadOffset): Long) == EMPTY) {
      producerLimit = pIdx + lookAheadStep
      true
    } else {
      val currentOffset = (pIdx & mask).toInt
      if ((LONG_HANDLE.getAcquire(data, currentOffset): Long) != EMPTY) {
        false
      } else {
        producerLimit = pIdx + 1L
        true
      }
    }
  }
}

/**
 * Creates [[LongSpscRingBuffer]] instances and holds its marker values.
 */
object LongSpscRingBuffer {

  /**
   * The value [[LongSpscRingBuffer#take]] and [[LongSpscRingBuffer#pollPacked]]
   * return when the buffer is empty: `Long.MinValue`. It cannot be offered.
   */
  final val EMPTY: Long = Long.MinValue

  /**
   * The end-of-stream marker that [[LongSpscRingBuffer#offerDone]] appends and
   * `take` and `pollPacked` return: `Long.MinValue + 1L`. It cannot be offered.
   */
  final val DONE: Long = Long.MinValue + 1L

  private val PRODUCER_INDEX: VarHandle =
    MethodHandles
      .privateLookupIn(classOf[LongSpscProducerFields], MethodHandles.lookup())
      .findVarHandle(classOf[LongSpscProducerFields], "producerIndex", classOf[Long])

  private val CONSUMER_INDEX: VarHandle =
    MethodHandles
      .privateLookupIn(classOf[LongSpscConsumerFields], MethodHandles.lookup())
      .findVarHandle(classOf[LongSpscConsumerFields], "consumerIndex", classOf[Long])

  private val LONG_HANDLE: VarHandle = MethodHandles.arrayElementVarHandle(classOf[Array[Long]])

  /**
   * Creates an empty buffer with `capacity` slots, the same as
   * `new LongSpscRingBuffer(capacity)`.
   *
   * @throws java.lang.IllegalArgumentException
   *   if `capacity` is not a power of two of at least 1
   */
  def apply(capacity: Int): LongSpscRingBuffer = new LongSpscRingBuffer(capacity)
}
