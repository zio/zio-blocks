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
// Prevents false sharing between producer fields, producer limit, consumer fields.

private[ringbuffer] class MpscPad0 {
  var p00, p01, p02, p03, p04, p05, p06, p07, p08, p09, p0a, p0b, p0c, p0d, p0e, p0f: Long = 0L
}

private[ringbuffer] class MpscProducerFields extends MpscPad0 {
  var producerIndex: Long = 0L
}

private[ringbuffer] class MpscPad1 extends MpscProducerFields {
  var p10, p11, p12, p13, p14, p15, p16, p17, p18, p19, p1a, p1b, p1c, p1d, p1e, p1f: Long = 0L
}

private[ringbuffer] class MpscProducerLimitFields extends MpscPad1 {
  var producerLimit: Long = 0L // initialized to capacity in MpscRingBuffer constructor
}

private[ringbuffer] class MpscPad2 extends MpscProducerLimitFields {
  var p20, p21, p22, p23, p24, p25, p26, p27, p28, p29, p2a, p2b, p2c, p2d, p2e, p2f: Long = 0L
}

private[ringbuffer] class MpscConsumerFields extends MpscPad2 {
  var consumerIndex: Long = 0L
}

private[ringbuffer] class MpscPad3 extends MpscConsumerFields {
  var p30, p31, p32, p33, p34, p35, p36, p37, p38, p39, p3a, p3b, p3c, p3d, p3e, p3f: Long = 0L
}

/**
 * A bounded first-in, first-out queue of non-`null` references for many
 * producer threads and one consumer thread. Any number of threads may call
 * [[offer]] at once; call [[take]] and [[drain]] from one consumer thread only,
 * which nothing checks. Neither side blocks: `offer` returns `false` when the
 * buffer is full and `take` returns `null` when no element is ready.
 *
 * On the JVM producers claim a slot with a compare-and-set on the shared
 * producer index and then store the element, as in the JCTools
 * `MpscArrayQueue`; elements from one producer come out in the order that
 * producer offered them. A producer that has claimed a slot but not yet stored
 * its element makes `take` return `null` until it does, even when later slots
 * already hold elements. On Scala.js, which runs one thread, the class is a
 * plain array-backed queue with the same methods and results.
 *
 * `null` marks an empty slot, so `offer(null)` throws `NullPointerException`.
 *
 * {{{
 * val events = MpscRingBuffer[String](4)
 *
 * events.offer("user-42 signed in")  // true
 * events.offer("user-7 signed out")  // true
 *
 * val seen = List.newBuilder[String]
 * events.drain(e => seen += e, 10)   // 2
 * seen.result()                      // List(user-42 signed in, user-7 signed out)
 * events.take()                      // null
 * }}}
 *
 * @param capacity
 *   the number of slots: a power of two of at least 1 (1, 2, 4, ...); any other
 *   value throws `IllegalArgumentException`
 * @see
 *   [[https://zioblocks.com/reference/ringbuffer/mpsc/ MPSC RingBuffer]]
 */
final class MpscRingBuffer[A <: AnyRef](val capacity: Int) extends MpscPad3 {
  require(capacity > 0 && (capacity & (capacity - 1)) == 0, s"capacity must be a positive power of 2, got: $capacity")

  import MpscRingBuffer._

  private val mask: Long            = (capacity - 1).toLong
  private val buffer: Array[AnyRef] = new Array[AnyRef](capacity)

  // Initialize producerLimit to capacity so the first offer doesn't need to read consumerIndex
  producerLimit = capacity.toLong

  /**
   * Appends `a` and returns `true` if a slot is free; returns `false` when the
   * buffer is full. Any number of threads may call it at once: on the JVM a
   * producer that loses the compare-and-set for a slot to another producer
   * tries the next slot, so the call never blocks, but it can loop while other
   * producers keep winning.
   *
   * @throws java.lang.NullPointerException
   *   if `a` is `null`
   */
  def offer(a: A): Boolean = {
    if (a == null) throw new NullPointerException("offer(null) is not permitted")
    val buf = buffer
    val m   = mask
    val cap = m + 1L

    var pLimit     = PRODUCER_LIMIT.getAcquire(this).asInstanceOf[Long]
    var pIdx: Long = 0L

    while (true) {
      pIdx = PRODUCER_INDEX.getAcquire(this).asInstanceOf[Long]
      if (pIdx >= pLimit) {
        val cIdx = CONSUMER_INDEX.getAcquire(this).asInstanceOf[Long]
        pLimit = cIdx + cap

        if (pIdx >= pLimit) {
          return false // FULL
        } else {
          // Racy update is benign — worst case another producer also refreshes
          PRODUCER_LIMIT.setRelease(this, pLimit)
        }
      }

      if (PRODUCER_INDEX.compareAndSet(this, pIdx, pIdx + 1L)) {
        // Won CAS — write element to the claimed slot
        val offset = (pIdx & m).toInt
        ARRAY_HANDLE.setRelease(buf, offset, a.asInstanceOf[AnyRef])
        return true
      }
      // CAS failed — another producer won; retry
    }

    false // unreachable, satisfies compiler
  }

  /**
   * Removes and returns the oldest element, or returns `null`, without waiting,
   * when none is ready: the buffer is empty, or (JVM only) the producer that
   * claimed the next slot has not stored its element yet.
   *
   * Call it from the consumer thread only; nothing checks this. Two threads
   * taking at once on the JVM can receive the same element twice, lose
   * elements, and leave the buffer looking full for good. `MuxStream#receive`
   * and `MuxStream#takeOutbound` take from this buffer, so the same happens
   * there, a known bug.
   */
  def take(): A = {
    val buf    = buffer
    val m      = mask
    val cIdx   = consumerIndex // plain read — single consumer
    val offset = (cIdx & m).toInt

    val e = ARRAY_HANDLE.getAcquire(buf, offset).asInstanceOf[A]
    if (e eq null) return null.asInstanceOf[A]

    ARRAY_HANDLE.setRelease(buf, offset, null.asInstanceOf[AnyRef])
    CONSUMER_INDEX.setRelease(this, cIdx + 1L)
    e
  }

  /**
   * Returns the number of elements in the buffer. While other threads offer or
   * take (JVM only), the result is an estimate: the two indices are read one
   * after the other, so it can be out of date or larger than `capacity`. It is
   * never negative.
   */
  def size: Int = {
    val cIdx = CONSUMER_INDEX.getAcquire(this).asInstanceOf[Long]
    val pIdx = PRODUCER_INDEX.getAcquire(this).asInstanceOf[Long]
    Math.max(0, (pIdx - cIdx).toInt)
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
    (pIdx - cIdx).toInt >= capacity
  }

  /**
   * Removes up to `limit` elements, oldest first, and passes each one to
   * `consumer`; returns how many it removed, which is 0 when the buffer is
   * empty. Like [[take]], it stops at the first slot with no element ready, and
   * must be called from the consumer thread only.
   *
   * Each element leaves the buffer before `consumer` sees it, so when
   * `consumer` throws, that element and the ones before it stay removed, the
   * rest stay in the buffer, and the exception propagates.
   *
   * @throws java.lang.IllegalArgumentException
   *   if `limit` is negative
   */
  def drain(consumer: A => Unit, limit: Int): Int = {
    if (limit < 0) throw new IllegalArgumentException(s"limit is negative: $limit")
    val buf   = buffer
    val m     = mask
    var cIdx  = consumerIndex
    var count = 0

    while (count < limit) {
      val offset = (cIdx & m).toInt
      val e      = ARRAY_HANDLE.getAcquire(buf, offset).asInstanceOf[A]
      if (e eq null) return count
      ARRAY_HANDLE.setRelease(buf, offset, null.asInstanceOf[AnyRef])
      cIdx += 1L
      CONSUMER_INDEX.setRelease(this, cIdx)
      count += 1
      consumer(e)
    }
    count
  }
}

/**
 * Creates [[MpscRingBuffer]] instances.
 */
object MpscRingBuffer {
  private val PRODUCER_INDEX: VarHandle =
    MethodHandles
      .privateLookupIn(classOf[MpscProducerFields], MethodHandles.lookup())
      .findVarHandle(classOf[MpscProducerFields], "producerIndex", classOf[Long])

  private val PRODUCER_LIMIT: VarHandle =
    MethodHandles
      .privateLookupIn(classOf[MpscProducerLimitFields], MethodHandles.lookup())
      .findVarHandle(classOf[MpscProducerLimitFields], "producerLimit", classOf[Long])

  private val CONSUMER_INDEX: VarHandle =
    MethodHandles
      .privateLookupIn(classOf[MpscConsumerFields], MethodHandles.lookup())
      .findVarHandle(classOf[MpscConsumerFields], "consumerIndex", classOf[Long])

  private val ARRAY_HANDLE: VarHandle =
    MethodHandles.arrayElementVarHandle(classOf[Array[AnyRef]])

  /**
   * Creates an empty buffer with `capacity` slots, the same as
   * `new MpscRingBuffer[A](capacity)`.
   *
   * @throws java.lang.IllegalArgumentException
   *   if `capacity` is not a power of two of at least 1
   */
  def apply[A <: AnyRef](capacity: Int): MpscRingBuffer[A] = new MpscRingBuffer[A](capacity)
}
