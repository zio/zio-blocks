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
final class MpmcRingBuffer[A <: AnyRef](val capacity: Int) {
  require(
    capacity >= 2 && (capacity & (capacity - 1)) == 0,
    s"capacity must be a power of 2 >= 2, got: $capacity"
  )

  private val mask: Int             = capacity - 1
  private val buffer: Array[AnyRef] = new Array[AnyRef](capacity)
  private var producerIndex: Long   = 0L
  private var consumerIndex: Long   = 0L

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
   * Removes and returns the oldest element, or returns `null` when the buffer
   * is empty or (JVM only) the next element is still being stored by a
   * producer. Any number of threads may call it at once: a consumer that loses
   * the compare-and-set for an element tries again, so the call never blocks,
   * but it can loop while other consumers keep winning. The slot is cleared, so
   * the taken element is not kept reachable by the buffer.
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
   * after the other, so it can be out of date or larger than `capacity`.
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
}

/**
 * Creates [[MpmcRingBuffer]] instances.
 */
object MpmcRingBuffer {

  /**
   * Creates an empty buffer with `capacity` slots, the same as
   * `new MpmcRingBuffer[A](capacity)`.
   *
   * @throws java.lang.IllegalArgumentException
   *   if `capacity` is not a power of two of at least 2
   */
  def apply[A <: AnyRef](capacity: Int): MpmcRingBuffer[A] = new MpmcRingBuffer[A](capacity)
}
