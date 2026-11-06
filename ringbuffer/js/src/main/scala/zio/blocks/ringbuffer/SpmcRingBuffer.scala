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
final class SpmcRingBuffer[A <: AnyRef](val capacity: Int) {
  require(capacity > 0 && (capacity & (capacity - 1)) == 0, s"capacity must be a positive power of 2, got: $capacity")

  private val mask: Int             = capacity - 1
  private val buffer: Array[AnyRef] = new Array[AnyRef](capacity)
  private var producerIndex: Long   = 0L
  private var consumerIndex: Long   = 0L

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
   * is empty. Any number of threads may call it at once: on the JVM a consumer
   * that loses the compare-and-set for an element to another consumer tries the
   * next one, so the call never blocks, but it can loop while other consumers
   * keep winning.
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
 * Creates [[SpmcRingBuffer]] instances.
 */
object SpmcRingBuffer {

  /**
   * Creates an empty buffer with `capacity` slots, the same as
   * `new SpmcRingBuffer[A](capacity)`.
   *
   * @throws java.lang.IllegalArgumentException
   *   if `capacity` is not a power of two of at least 1
   */
  def apply[A <: AnyRef](capacity: Int): SpmcRingBuffer[A] = new SpmcRingBuffer[A](capacity)
}
