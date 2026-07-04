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
 * High-performance non-blocking SPSC ring buffer specialized for `Long`.
 *
 * Layout: a single `Array[Long]`. Since every 64-bit pattern is a legal `Long`,
 * the FastFlow signal requires reserving sentinel values: `EMPTY`
 * (`Long.MinValue`) means "slot empty" and `DONE` (`Long.MinValue + 1L`) is the
 * in-band end-of-stream marker. The array is pre-filled with `EMPTY` at
 * construction.
 *
 * **`offer(EMPTY)` and `offer(DONE)` throw `IllegalArgumentException`** (the
 * same shape of contract as `SpscRingBuffer.offer(null)`). This costs two bit
 * patterns out of 2^64 in exchange for a single memory-ordered op per
 * insert/remove on a single cache line per slot, and removes the need for a
 * separate control queue.
 *
 * In-band "done" sentinel: producers can call `offerDone()` to insert the
 * end-of-stream marker after the last data value. Consumers reading via
 * `pollPacked()` receive `EMPTY` for empty, `DONE` for the sentinel, or the raw
 * `Long` payload for data.
 *
 * SPSC: one producer thread, one consumer thread.
 *
 * @param capacity
 *   must be a positive power of two
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

  def offer(a: Long): Boolean = {
    if (isReserved(a))
      throw new IllegalArgumentException(
        s"offer($a) is not permitted: Long.MinValue and Long.MinValue + 1L are reserved sentinels"
      )
    offerNonReserved(a)
  }

  /**
   * Unchecked insert for a value the caller has already proven is not a
   * reserved sentinel (i.e. `a > DONE`). This skips the `isReserved` guard so
   * callers on a hot path pay for the check exactly once. Callers that may hold
   * reserved values must route them out-of-band (see the escape protocol used
   * by the concurrent merge / mapPar readers) and never pass them here.
   */
  private[blocks] def offerNonReserved(a: Long): Boolean = {
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
   * Insert the in-band done sentinel. Returns `false` if the buffer is full;
   * the caller is expected to retry until it succeeds.
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
   * Atomically (with respect to the consumer) publish a `DONE` token, running
   * `beforePublish` first. This is the primitive used to carry an out-of-band
   * escape payload alongside a `DONE` marker: the caller passes an action that
   * enqueues the escape value, and this method runs it ONLY after it has proven
   * a slot is available and immediately BEFORE the release-store of `DONE`.
   *
   * Returns `false` (without running `beforePublish`) if the buffer is full, so
   * the escape is enqueued at most once and is never orphaned: a return of
   * `false` means nothing was published, while `true` means `beforePublish` ran
   * and the matching `DONE` was published with release semantics. The
   * `setRelease(DONE)` provides the happens-before edge so a consumer that
   * `getAcquire`-observes this `DONE` also observes the `beforePublish`
   * effects.
   *
   * Producer-thread-only, like [[offerDone]].
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

  def peek(): Boolean = {
    val offset = (consumerIndex & mask).toInt
    (LONG_HANDLE.getAcquire(data, offset): Long) != EMPTY
  }

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
   * Atomically polls the next slot:
   *   - `EMPTY` (`Long.MinValue`) — slot is empty; consumer index NOT advanced.
   *   - `DONE` (`Long.MinValue + 1L`) — in-band done sentinel; consumer index
   *     advanced.
   *   - any other value — the user-visible data; consumer index advanced.
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

object LongSpscRingBuffer {

  /** Reserved sentinel: slot is empty. */
  final val EMPTY: Long = Long.MinValue

  /** Reserved sentinel: in-band end-of-stream marker. */
  final val DONE: Long = Long.MinValue + 1L

  /**
   * True iff `a` is one of the two reserved sentinels (`EMPTY` or `DONE`).
   * Since `EMPTY == Long.MinValue` and `DONE == Long.MinValue + 1L` are the two
   * smallest `Long` values, this is a single signed comparison.
   */
  @inline private[blocks] def isReserved(a: Long): Boolean = a <= DONE

  private val PRODUCER_INDEX: VarHandle =
    MethodHandles
      .privateLookupIn(classOf[LongSpscProducerFields], MethodHandles.lookup())
      .findVarHandle(classOf[LongSpscProducerFields], "producerIndex", classOf[Long])

  private val CONSUMER_INDEX: VarHandle =
    MethodHandles
      .privateLookupIn(classOf[LongSpscConsumerFields], MethodHandles.lookup())
      .findVarHandle(classOf[LongSpscConsumerFields], "consumerIndex", classOf[Long])

  private val LONG_HANDLE: VarHandle = MethodHandles.arrayElementVarHandle(classOf[Array[Long]])

  def apply(capacity: Int): LongSpscRingBuffer = new LongSpscRingBuffer(capacity)
}
