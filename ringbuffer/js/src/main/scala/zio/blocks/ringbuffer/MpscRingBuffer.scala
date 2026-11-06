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
final class MpscRingBuffer[A <: AnyRef](val capacity: Int) {
  require(capacity > 0 && (capacity & (capacity - 1)) == 0, s"capacity must be a positive power of 2, got: $capacity")

  private val mask: Int             = capacity - 1
  private val buffer: Array[AnyRef] = new Array[AnyRef](capacity)
  private var producerIndex: Long   = 0L
  private var consumerIndex: Long   = 0L

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
    val pIdx = producerIndex
    val cIdx = consumerIndex
    if ((pIdx - cIdx).toInt == capacity) {
      false
    } else {
      buffer((pIdx & mask).toInt) = a.asInstanceOf[AnyRef]
      producerIndex = pIdx + 1L
      true
    }
  }

  /**
   * Removes and returns the oldest element, or returns `null`, without waiting,
   * when none is ready: the buffer is empty, or (JVM only) the producer that
   * claimed the next slot has not stored its element yet.
   *
   * Call it from the consumer thread only; nothing checks this. Two threads
   * taking at once on the JVM can receive the same element twice, lose
   * elements, and leave the buffer looking full for good, a known bug that
   * `MuxStream#receive` and `MuxStream#takeOutbound` inherit.
   */
  def take(): A = {
    val cIdx = consumerIndex
    val pIdx = producerIndex
    if (pIdx == cIdx) {
      null.asInstanceOf[A]
    } else {
      val offset  = (cIdx & mask).toInt
      val element = buffer(offset).asInstanceOf[A]
      buffer(offset) = null
      consumerIndex = cIdx + 1L
      element
    }
  }

  /**
   * Returns the number of elements in the buffer. While other threads offer or
   * take (JVM only), the result is an estimate: the two indices are read one
   * after the other, so it can be out of date or larger than `capacity`. It is
   * never negative.
   */
  def size: Int = (producerIndex - consumerIndex).toInt

  /**
   * Returns `true` when the buffer holds no elements. While other threads offer
   * or take (JVM only), the answer can be out of date by the time it returns.
   */
  def isEmpty: Boolean = producerIndex == consumerIndex

  /**
   * Returns `true` when every slot holds an element. While other threads offer
   * or take (JVM only), the answer can be out of date by the time it returns.
   */
  def isFull: Boolean = (producerIndex - consumerIndex).toInt == capacity

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
    var count = 0
    while (count < limit) {
      val cIdx = consumerIndex
      val pIdx = producerIndex
      if (pIdx == cIdx) return count
      val offset = (cIdx & mask).toInt
      val e      = buffer(offset).asInstanceOf[A]
      buffer(offset) = null
      consumerIndex = cIdx + 1L
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

  /**
   * Creates an empty buffer with `capacity` slots, the same as
   * `new MpscRingBuffer[A](capacity)`.
   *
   * @throws java.lang.IllegalArgumentException
   *   if `capacity` is not a power of two of at least 1
   */
  def apply[A <: AnyRef](capacity: Int): MpscRingBuffer[A] = new MpscRingBuffer[A](capacity)
}
