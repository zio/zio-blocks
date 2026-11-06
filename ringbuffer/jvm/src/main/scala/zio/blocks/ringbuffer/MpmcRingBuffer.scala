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
// Prevents false sharing on 64- and 128-byte cache lines (x86, ARM, Apple Silicon).

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
 * A bounded first-in, first-out queue of non-`null` references that any number
 * of threads may [[offer]] to and [[take]] from at the same time. Neither side
 * blocks: `offer` returns `false` when no slot is free and `take` returns
 * `null` when no element is ready.
 *
 * On the JVM it is Dmitry Vyukov's bounded MPMC queue: each slot carries a
 * sequence number that says whether the slot is ready to be written or read,
 * and producers and consumers each claim slots with a compare-and-set on their
 * own index. A thread that has claimed a slot and not finished with it holds
 * that slot up: until it finishes, `take` returns `null` when the next element
 * is still being stored and `offer` returns `false` when the next slot is still
 * being emptied, even if other slots are ready. On Scala.js, which runs one
 * thread, the class is a plain array-backed queue with the same methods and
 * results.
 *
 * `offer(null)` throws `NullPointerException`.
 *
 * {{{
 * val requests = MpmcRingBuffer[String](2)
 *
 * requests.offer("GET /orders/17") // true
 * requests.offer("GET /orders/18") // true
 * requests.offer("GET /orders/19") // false: both slots are taken
 * requests.isFull                  // true
 * requests.take()                  // GET /orders/17
 * requests.offer("GET /orders/19") // true
 * }}}
 *
 * @param capacity
 *   the number of slots: a power of two of at least 2 (2, 4, 8, ...), because
 *   with a single slot the sequence numbers cannot tell a written slot from an
 *   emptied one; any other value throws `IllegalArgumentException`
 * @see
 *   [[https://zioblocks.com/reference/ringbuffer/mpmc/ MPMC RingBuffer]]
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
   * Appends `a` and returns `true` if the next slot is free; returns `false`
   * when the buffer is full or (JVM only) the next slot is still being emptied
   * by a consumer. Any number of threads may call it at once: a producer that
   * loses the compare-and-set for a slot tries again, so the call never blocks,
   * but it can loop while other producers keep winning.
   *
   * @throws java.lang.NullPointerException
   *   if `a` is `null`
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
        // Full, or the next slot is still being emptied
        return false
      }
      // else diff > 0: another producer advanced past this slot, retry
    }
    false // unreachable, but needed for the compiler
  }

  /**
   * Removes and returns the oldest element, or returns `null` when the buffer
   * is empty or (JVM only) the next element is still being stored by a
   * producer. Any number of threads may call it at once: a consumer that loses
   * the compare-and-set for an element tries again, so the call never blocks,
   * but it can loop while other consumers keep winning. The slot is cleared, so
   * the taken element is not kept reachable by the buffer.
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
        // Empty, or the next element is still being stored
        return null.asInstanceOf[A]
      }
      // else diff > 0: another consumer advanced past this slot, retry
    }
    null.asInstanceOf[A] // unreachable, but needed for the compiler
  }

  /**
   * Returns the number of elements in the buffer. While other threads offer or
   * take (JVM only), the result is an estimate: the two indices are read one
   * after the other, so it can be out of date or larger than `capacity`.
   */
  def size: Int = {
    val cIdx = CONSUMER_INDEX.getAcquire(this).asInstanceOf[Long]
    val pIdx = PRODUCER_INDEX.getAcquire(this).asInstanceOf[Long]
    (pIdx - cIdx).toInt
  }

  /**
   * Returns `true` when the buffer holds no elements. While other threads offer
   * or take (JVM only), the answer can be out of date by the time it returns.
   */
  def isEmpty: Boolean = {
    val cIdx = CONSUMER_INDEX.getAcquire(this).asInstanceOf[Long]
    val pIdx = PRODUCER_INDEX.getAcquire(this).asInstanceOf[Long]
    pIdx == cIdx
  }

  /**
   * Returns `true` when every slot holds an element. While other threads offer
   * or take (JVM only), the answer can be out of date by the time it returns.
   */
  def isFull: Boolean = {
    val cIdx = CONSUMER_INDEX.getAcquire(this).asInstanceOf[Long]
    val pIdx = PRODUCER_INDEX.getAcquire(this).asInstanceOf[Long]
    (pIdx - cIdx).toInt == capacity
  }
}

/**
 * Creates [[MpmcRingBuffer]] instances.
 */
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
   * Creates an empty buffer with `capacity` slots, the same as
   * `new MpmcRingBuffer[A](capacity)`.
   *
   * @throws java.lang.IllegalArgumentException
   *   if `capacity` is not a power of two of at least 2
   */
  def apply[A <: AnyRef](capacity: Int): MpmcRingBuffer[A] = new MpmcRingBuffer[A](capacity)
}
