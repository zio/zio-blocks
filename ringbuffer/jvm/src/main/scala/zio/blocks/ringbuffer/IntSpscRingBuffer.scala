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

private[ringbuffer] class IntSpscPad0 {
  var p00, p01, p02, p03, p04, p05, p06, p07, p08, p09, p0a, p0b, p0c, p0d, p0e, p0f: Long = 0L
}

private[ringbuffer] class IntSpscProducerFields extends IntSpscPad0 {
  var producerIndex: Long = 0L
  var producerLimit: Long = 0L
}

private[ringbuffer] class IntSpscPad1 extends IntSpscProducerFields {
  var p10, p11, p12, p13, p14, p15, p16, p17, p18, p19, p1a, p1b, p1c, p1d, p1e, p1f: Long = 0L
}

private[ringbuffer] class IntSpscConsumerFields extends IntSpscPad1 {
  var consumerIndex: Long = 0L
}

private[ringbuffer] class IntSpscPad2 extends IntSpscConsumerFields {
  var p20, p21, p22, p23, p24, p25, p26, p27, p28, p29, p2a, p2b, p2c, p2d, p2e, p2f: Long = 0L
}

/**
 * High-performance non-blocking SPSC ring buffer specialized for `Int`.
 *
 * Layout: a single `Array[Long]`. Each slot packs both the data and the
 * occupancy/control signal into one 64-bit word:
 *   - bits 32..63: tag (`0` = empty, `1` = data, `2` = done)
 *   - bits 0..31: the user's `Int` value (only meaningful when tag == 1)
 *
 * This makes the slot's own value act as the FastFlow signal: every
 * insert/remove is a single memory-ordered op on a single cache line. Zero is
 * the empty marker because the array is zero-initialized; a stored value with a
 * non-zero tag is always non-zero regardless of the `Int` payload (including
 * `0`, `Int.MinValue`, `Int.MaxValue`, etc.).
 *
 * In-band "done" sentinel: producers can call `offerDone()` to insert an
 * end-of-stream marker after the last data value. Consumers reading via
 * `pollPacked()` receive `EMPTY_PACKED` for empty, `DONE_PACKED` for the
 * sentinel, or a `TAG_FULL | Int payload` value for data. This removes the need
 * for a separate control queue alongside the data queue.
 *
 * SPSC: one producer thread, one consumer thread.
 *
 * @param capacity
 *   must be a positive power of two
 */
final class IntSpscRingBuffer(val capacity: Int) extends IntSpscPad2 {
  require(capacity > 0 && (capacity & (capacity - 1)) == 0, s"capacity must be a positive power of 2, got: $capacity")

  import IntSpscRingBuffer._

  private val mask: Long          = (capacity - 1).toLong
  private val data: Array[Long]   = new Array[Long](capacity)
  private val lookAheadStep: Long = Math.max(1, Math.min(capacity / 4, 4096)).toLong

  def offer(a: Int): Boolean = {
    val pIdx = producerIndex
    if (pIdx >= producerLimit) {
      if (!offerSlowPath(pIdx)) return false
    }
    val offset = (pIdx & mask).toInt
    val packed = TAG_FULL | (a.toLong & LOW32_MASK)
    LONG_HANDLE.setRelease(data, offset, packed)
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
    LONG_HANDLE.setRelease(data, offset, DONE_PACKED)
    PRODUCER_INDEX.setOpaque(this, pIdx + 1L)
    true
  }

  def peek(): Boolean = {
    val offset = (consumerIndex & mask).toInt
    (LONG_HANDLE.getAcquire(data, offset): Long) != 0L
  }

  def take(): Int = {
    val cIdx   = consumerIndex
    val offset = (cIdx & mask).toInt
    val packed = (LONG_HANDLE.getAcquire(data, offset): Long)
    if (packed == 0L) return 0
    LONG_HANDLE.setRelease(data, offset, 0L)
    CONSUMER_INDEX.setOpaque(this, cIdx + 1L)
    packed.toInt
  }

  /**
   * Atomically polls the next slot and returns the raw packed value:
   *   - `EMPTY_PACKED` (`0L`) — slot is empty; consumer index is NOT advanced.
   *   - `DONE_PACKED` — in-band done sentinel; consumer index advanced.
   *   - any other value — packed data (`TAG_FULL | (a & LOW32_MASK)`); consumer
   *     index advanced. The payload is `packed.toInt`.
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

object IntSpscRingBuffer {

  /** Packed value returned by `pollPacked()` when the next slot is empty. */
  final val EMPTY_PACKED: Long = 0L

  /** Packed value returned by `pollPacked()` for the in-band done sentinel. */
  final val DONE_PACKED: Long = 2L << 32

  private final val TAG_FULL: Long   = 1L << 32
  private final val LOW32_MASK: Long = 0xffffffffL

  private val PRODUCER_INDEX: VarHandle =
    MethodHandles
      .privateLookupIn(classOf[IntSpscProducerFields], MethodHandles.lookup())
      .findVarHandle(classOf[IntSpscProducerFields], "producerIndex", classOf[Long])

  private val CONSUMER_INDEX: VarHandle =
    MethodHandles
      .privateLookupIn(classOf[IntSpscConsumerFields], MethodHandles.lookup())
      .findVarHandle(classOf[IntSpscConsumerFields], "consumerIndex", classOf[Long])

  private val LONG_HANDLE: VarHandle = MethodHandles.arrayElementVarHandle(classOf[Array[Long]])

  def apply(capacity: Int): IntSpscRingBuffer = new IntSpscRingBuffer(capacity)
}
