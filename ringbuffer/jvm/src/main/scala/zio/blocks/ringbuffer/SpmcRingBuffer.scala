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
 * A bounded first-in, first-out queue of non-`null` references for one producer
 * thread and many consumer threads. Call [[offer]] from one producer thread
 * only, which nothing checks; any number of threads may call [[take]] at once,
 * and each element goes to exactly one of them. Neither side blocks: `offer`
 * returns `false` when the buffer is full and `take` returns `null` when it is
 * empty.
 *
 * On the JVM consumers claim elements with a compare-and-set on the shared
 * consumer index, and a slot holds a live element only while its index lies
 * between the consumer and producer indices. Consumers never clear a slot, so a
 * taken element stays referenced by the buffer, and cannot be
 * garbage-collected, until the producer reuses that slot. On Scala.js, which
 * runs one thread, the class is a plain array-backed queue whose `take` clears
 * the slot.
 *
 * `offer(null)` throws `NullPointerException`.
 *
 * {{{
 * val tasks = SpmcRingBuffer[String](4)
 *
 * tasks.offer("compress chunk-1") // true
 * tasks.offer("compress chunk-2") // true
 * tasks.size                      // 2
 *
 * val worker = new Thread(() => println(tasks.take()))
 * worker.start()
 * worker.join()                   // prints compress chunk-1
 * tasks.take()                    // compress chunk-2
 * tasks.take()                    // null
 * }}}
 *
 * @param capacity
 *   the number of slots: a power of two of at least 1 (1, 2, 4, ...); any other
 *   value throws `IllegalArgumentException`
 * @see
 *   [[https://zioblocks.com/reference/ringbuffer/spmc/ SPMC RingBuffer]]
 */
final class SpmcRingBuffer[A <: AnyRef](val capacity: Int) extends SpmcPad2 {
  require(capacity > 0 && (capacity & (capacity - 1)) == 0, s"capacity must be a positive power of 2, got: $capacity")

  import SpmcRingBuffer._

  private val mask: Long            = (capacity - 1).toLong
  private val buffer: Array[AnyRef] = new Array[AnyRef](capacity)

  /**
   * Appends `a` and returns `true` if a slot is free; returns `false`, without
   * waiting, when the buffer is full. Call it from the producer thread only.
   *
   * @throws java.lang.NullPointerException
   *   if `a` is `null`
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
   * Removes and returns the oldest element, or returns `null` when the buffer
   * is empty. Any number of threads may call it at once: on the JVM a consumer
   * that loses the compare-and-set for an element to another consumer tries the
   * next one, so the call never blocks, but it can loop while other consumers
   * keep winning.
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
   * Returns the number of elements in the buffer. While other threads offer or
   * take (JVM only), the result is an estimate: the two indices are read one
   * after the other, so it can be out of date or larger than `capacity`.
   */
  def size: Int = {
    val cIdx = CONSUMER_INDEX.getVolatile(this).asInstanceOf[Long]
    val pIdx = PRODUCER_INDEX.getAcquire(this).asInstanceOf[Long]
    (pIdx - cIdx).toInt
  }

  /**
   * Returns `true` when the buffer holds no elements. While other threads offer
   * or take (JVM only), the answer can be out of date by the time it returns.
   */
  def isEmpty: Boolean = {
    val cIdx = CONSUMER_INDEX.getVolatile(this).asInstanceOf[Long]
    val pIdx = PRODUCER_INDEX.getAcquire(this).asInstanceOf[Long]
    pIdx == cIdx
  }

  /**
   * Returns `true` when every slot holds an element. While other threads offer
   * or take (JVM only), the answer can be out of date by the time it returns.
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

/**
 * Creates [[SpmcRingBuffer]] instances.
 */
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
   * Creates an empty buffer with `capacity` slots, the same as
   * `new SpmcRingBuffer[A](capacity)`.
   *
   * @throws java.lang.IllegalArgumentException
   *   if `capacity` is not a power of two of at least 1
   */
  def apply[A <: AnyRef](capacity: Int): SpmcRingBuffer[A] = new SpmcRingBuffer[A](capacity)
}
