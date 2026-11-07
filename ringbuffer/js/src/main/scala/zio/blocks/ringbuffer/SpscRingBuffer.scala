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
 * thread and one consumer thread. Neither side blocks: [[offer]] returns
 * `false` when the buffer is full and [[take]] returns `null` when it is empty,
 * so the caller decides whether to retry, back off or drop the element.
 * [[fill]] and [[drain]] move several elements per call.
 *
 * On the JVM the two threads share no lock: a slot's `null` or non-`null`
 * content tells each side whether the slot is free (the FastFlow design), so on
 * the fast path neither side reads the other's index. Call `offer` and `fill`
 * from one thread only, and `take` and `drain` from one thread only; nothing
 * checks this. On Scala.js, which runs one thread, the class is a plain
 * array-backed queue with the same methods and results.
 *
 * `null` marks an empty slot, so `offer(null)` throws `NullPointerException`.
 *
 * {{{
 * val jobs = SpscRingBuffer[String](2)
 *
 * val consumer = new Thread(() => {
 *   var handled = 0
 *   while (handled < 3) {
 *     val job = jobs.take()
 *     if (job != null) {
 *       println(job)
 *       handled += 1
 *     }
 *   }
 * })
 * consumer.start()
 *
 * for (job <- List("resize-image", "send-email", "rebuild-index"))
 *   while (!jobs.offer(job)) Thread.onSpinWait()
 * consumer.join()
 * // resize-image
 * // send-email
 * // rebuild-index
 * }}}
 *
 * @param capacity
 *   the number of slots: a power of two of at least 1 (1, 2, 4, ...); any other
 *   value throws `IllegalArgumentException`
 * @see
 *   [[https://zioblocks.com/reference/ringbuffer/spsc/ SPSC RingBuffer]]
 */
final class SpscRingBuffer[A <: AnyRef](val capacity: Int) {
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
   * Removes and returns the oldest element, or returns `null`, without waiting,
   * when the buffer is empty. Call it from the consumer thread only.
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
   * after the other, so it can be out of date, negative, or larger than
   * `capacity`.
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
   * empty. Call it from the consumer thread only.
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

  /**
   * Calls `supplier` once per free slot, up to `limit` times, and appends each
   * result; returns how many elements it appended, which is 0 when the buffer
   * is full. It checks for a free slot before each call, so `supplier` never
   * runs for an element that does not fit. Call it from the producer thread
   * only.
   *
   * When `supplier` throws, the elements appended before stay in the buffer and
   * the exception propagates.
   *
   * @throws java.lang.IllegalArgumentException
   *   if `limit` is negative
   * @throws java.lang.NullPointerException
   *   if `supplier` returns `null`; the elements appended before stay in the
   *   buffer
   */
  def fill(supplier: () => A, limit: Int): Int = {
    if (limit < 0) throw new IllegalArgumentException(s"limit is negative: $limit")
    var count = 0
    while (count < limit) {
      val pIdx = producerIndex
      val cIdx = consumerIndex
      if ((pIdx - cIdx).toInt == capacity) return count
      val a = supplier()
      if (a == null) throw new NullPointerException("fill: supplier returned null")
      buffer((pIdx & mask).toInt) = a.asInstanceOf[AnyRef]
      producerIndex = pIdx + 1L
      count += 1
    }
    count
  }
}

/**
 * Creates [[SpscRingBuffer]] instances.
 */
object SpscRingBuffer {

  /**
   * Creates an empty buffer with `capacity` slots, the same as
   * `new SpscRingBuffer[A](capacity)`.
   *
   * @throws java.lang.IllegalArgumentException
   *   if `capacity` is not a power of two of at least 1
   */
  def apply[A <: AnyRef](capacity: Int): SpscRingBuffer[A] = new SpscRingBuffer[A](capacity)
}
