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

private[ringbuffer] class MpmcPad0 {
  var p00, p01, p02, p03, p04, p05, p06, p07, p08, p09, p0a, p0b, p0c, p0d, p0e, p0f: Long = 0L
}

private[ringbuffer] class MpmcProducerFields extends MpmcPad0 {
  var producerIndex: Long = 0L
}

private[ringbuffer] class MpmcPad1 extends MpmcProducerFields {
  var p10, p11, p12, p13, p14, p15, p16, p17, p18, p19, p1a, p1b, p1c, p1d, p1e, p1f: Long = 0L
}

private[ringbuffer] class MpmcConsumerFields extends MpmcPad1 {
  var consumerIndex: Long = 0L
}

private[ringbuffer] class MpmcPad2 extends MpmcConsumerFields {
  var p20, p21, p22, p23, p24, p25, p26, p27, p28, p29, p2a, p2b, p2c, p2d, p2e, p2f: Long = 0L
}

/**
 * A high-performance, non-blocking MPMC (Multi-Producer Multi-Consumer) ring
 * buffer for generic reference types.
 *
 * Uses the Vyukov/Dmitry bounded MPMC queue algorithm with a parallel sequence
 * buffer. Each slot in the sequence buffer carries a stamp that indicates
 * whether the slot is available for writing (stamp == expected producer index)
 * or reading (stamp == expected consumer index + 1). Both `producerIndex` and
 * `consumerIndex` are advanced via CAS, allowing any number of threads to call
 * `offer` and `poll` concurrently.
 *
 * **Thread safety:** Fully thread-safe for any combination of concurrent
 * producers and consumers. No external synchronization is required.
 *
 * **Null elements are not permitted.** `offer(null)` throws
 * `NullPointerException`.
 *
 * @param capacity
 *   the buffer capacity, must be a power of two >= 2 (the sequence buffer
 *   algorithm requires at least 2 slots to distinguish written from consumed)
 */
final class MpmcRingBuffer[A <: AnyRef](val capacity: Int) extends MpmcPad2 {
  require(
    capacity >= 2 && (capacity & (capacity - 1)) == 0,
    s"capacity must be a power of 2 >= 2, got: $capacity"
  )

  import MpmcRingBuffer._

  private val mask: Int                   = capacity - 1
  private val buffer: Array[AnyRef]       = new Array[AnyRef](capacity)
  private val sequenceBuffer: Array[Long] = {
    val arr = new Array[Long](capacity)
    var i   = 0
    while (i < capacity) {
      arr(i) = i.toLong
      i += 1
    }
    arr
  }

  /**
   * Tries to insert an element into the buffer without blocking.
   *
   * Multiple threads may call this method concurrently. The method uses a CAS
   * loop on `producerIndex` and sequence stamps to coordinate between
   * producers.
   *
   * @param a
   *   the element to insert; must not be `null`
   * @throws java.lang.NullPointerException
   *   if the element is `null`
   * @return
   *   `true` if the element was successfully inserted, `false` if the buffer is
   *   full
   */
  def offer(a: A): Boolean = {
    if (a == null) throw new NullPointerException("offer(null) is not permitted")
    val buf    = buffer
    val seqBuf = sequenceBuffer
    val m      = mask

    while (true) {
      val pIdx      = PRODUCER_INDEX.getAcquire(this).asInstanceOf[Long]
      val seqOffset = (pIdx & m).toInt
      val seq       = SEQ_HANDLE.getAcquire(seqBuf, seqOffset).asInstanceOf[Long]
      val diff      = seq - pIdx

      if (diff == 0L) {
        // Slot is available for writing
        if (PRODUCER_INDEX.compareAndSet(this, pIdx, pIdx + 1L)) {
          ARRAY_HANDLE.setRelease(buf, seqOffset, a.asInstanceOf[AnyRef])
          SEQ_HANDLE.setRelease(seqBuf, seqOffset, pIdx + 1L)
          return true
        }
        // CAS failed, retry
      } else if (diff < 0L) {
        // Queue is full
        return false
      }
      // else diff > 0: another producer advanced past this slot, retry
    }
    false // unreachable, but needed for the compiler
  }

  /**
   * Tries to remove an element from the buffer without blocking.
   *
   * Multiple threads may call this method concurrently. The method uses a CAS
   * loop on `consumerIndex` and sequence stamps to coordinate between
   * consumers.
   *
   * @return
   *   the element, or `null` if the buffer is empty
   */
  def take(): A = {
    val buf    = buffer
    val seqBuf = sequenceBuffer
    val m      = mask
    val cap    = capacity

    while (true) {
      val cIdx      = CONSUMER_INDEX.getAcquire(this).asInstanceOf[Long]
      val seqOffset = (cIdx & m).toInt
      val seq       = SEQ_HANDLE.getAcquire(seqBuf, seqOffset).asInstanceOf[Long]
      val diff      = seq - (cIdx + 1L)

      if (diff == 0L) {
        // Element is ready for reading
        if (CONSUMER_INDEX.compareAndSet(this, cIdx, cIdx + 1L)) {
          val element = ARRAY_HANDLE.getAcquire(buf, seqOffset).asInstanceOf[A]
          ARRAY_HANDLE.set(buf, seqOffset, null) // Allow GC of consumed element
          SEQ_HANDLE.setRelease(seqBuf, seqOffset, cIdx + cap.toLong)
          return element
        }
        // CAS failed, retry
      } else if (diff < 0L) {
        // Queue is empty
        return null.asInstanceOf[A]
      }
      // else diff > 0: another consumer advanced past this slot, retry
    }
    null.asInstanceOf[A] // unreachable, but needed for the compiler
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
}

object MpmcRingBuffer {
  private val PRODUCER_INDEX: VarHandle =
    MethodHandles
      .privateLookupIn(classOf[MpmcProducerFields], MethodHandles.lookup())
      .findVarHandle(classOf[MpmcProducerFields], "producerIndex", classOf[Long])

  private val CONSUMER_INDEX: VarHandle =
    MethodHandles
      .privateLookupIn(classOf[MpmcConsumerFields], MethodHandles.lookup())
      .findVarHandle(classOf[MpmcConsumerFields], "consumerIndex", classOf[Long])

  private val ARRAY_HANDLE: VarHandle =
    MethodHandles.arrayElementVarHandle(classOf[Array[AnyRef]])

  private val SEQ_HANDLE: VarHandle =
    MethodHandles.arrayElementVarHandle(classOf[Array[Long]])

  /**
   * Creates a new [[MpmcRingBuffer]] with the given capacity.
   *
   * @param capacity
   *   the buffer capacity, must be a power of two >= 2
   * @tparam A
   *   the element type (must be a reference type)
   * @return
   *   a new MPMC ring buffer
   */
  def apply[A <: AnyRef](capacity: Int): MpmcRingBuffer[A] = new MpmcRingBuffer[A](capacity)
}
