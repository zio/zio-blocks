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

package zio.blocks.streams.queues

import zio.blocks.ringbuffer.MpscRingBuffer

import java.util.concurrent.locks.LockSupport

/**
 * A blocking MPSC (multi-producer, single-consumer) queue.
 *
 * Backed by [[MpscRingBuffer]] for the fast path; uses [[LockSupport.park]] and
 * [[LockSupport.unpark]] for blocking when the buffer is full or empty.
 *
 * Multiple threads may call [[offer]] concurrently. Only a single thread may
 * call [[take]], [[drain]], [[isEmpty]], or [[isClosed]].
 *
 * Multi-producer parking: a single `producerWaiter` volatile slot is used. One
 * producer registers and gets fast-path unpark from consumer. Others use
 * `parkNanos(1000)` (1µs) to retry — VT-friendly, no starvation.
 *
 * Null elements are not permitted.
 *
 * @param capacity
 *   The logical capacity. Rounded up to the next power of two internally.
 */
final class BlockingMpscQueue[A <: AnyRef](capacity: Int) {
  require(capacity >= 1, s"BlockingMpscQueue requires capacity >= 1, got: $capacity")

  private val ringBuffer: MpscRingBuffer[AnyRef] =
    new MpscRingBuffer[AnyRef](nextPowerOfTwo(capacity))

  @volatile private var consumerWaiter: Thread = null
  @volatile private var producerWaiter: Thread = null
  @volatile private var closed: Boolean        = false

  /**
   * Offers an element to the queue, blocking until space is available or the
   * queue is closed. Returns `true` on success, `false` if the queue was
   * closed.
   */
  def offer(a: A): Boolean = {
    if (a == null) throw new NullPointerException("BlockingMpscQueue.offer(null) is not permitted")
    while (true) {
      if (closed) return false
      if (ringBuffer.offer(a.asInstanceOf[AnyRef])) {
        val w = consumerWaiter
        if (w ne null) LockSupport.unpark(w)
        return true
      }
      producerWaiter = Thread.currentThread()
      try {
        if (closed) return false
        if (ringBuffer.offer(a.asInstanceOf[AnyRef])) {
          val w = consumerWaiter
          if (w ne null) LockSupport.unpark(w)
          return true
        }
        LockSupport.parkNanos(this, 1000L)
      } finally {
        producerWaiter = null
      }
    }
    false
  }

  /**
   * Takes an element from the queue, blocking until one is available or the
   * queue is closed. Returns the element, or `null` if closed and empty.
   */
  def take(): A = {
    while (true) {
      val e = ringBuffer.take().asInstanceOf[A]
      if (e ne null) {
        val w = producerWaiter
        if (w ne null) LockSupport.unpark(w)
        return e
      }
      if (closed) return null.asInstanceOf[A]
      consumerWaiter = Thread.currentThread()
      try {
        val e2 = ringBuffer.take().asInstanceOf[A]
        if (e2 ne null) {
          val w = producerWaiter
          if (w ne null) LockSupport.unpark(w)
          return e2
        }
        if (closed) return null.asInstanceOf[A]
        LockSupport.park(this)
      } finally {
        consumerWaiter = null
      }
    }
    null.asInstanceOf[A]
  }

  /** Closes the queue, unblocking any waiting thread on either side. */
  def close(): Unit = {
    closed = true
    val cw = consumerWaiter
    if (cw ne null) LockSupport.unpark(cw)
    val pw = producerWaiter
    if (pw ne null) LockSupport.unpark(pw)
  }

  /** Whether the queue has been closed. */
  def isClosed: Boolean = closed

  /** Whether the ring buffer contains no elements. */
  def isEmpty: Boolean = ringBuffer.isEmpty

  /**
   * Drains up to `limit` elements from the queue and passes each to `consumer`.
   *
   * @param consumer
   *   callback invoked for each drained element
   * @param limit
   *   maximum number of elements to drain
   * @return
   *   number of elements drained
   */
  def drain(consumer: A => Unit, limit: Int): Int = {
    val drained = ringBuffer.drain(consumer.asInstanceOf[AnyRef => Unit], limit)
    val w       = producerWaiter
    if (w ne null) LockSupport.unpark(w)
    drained
  }

  private def nextPowerOfTwo(n: Int): Int =
    if (n <= 1) 1
    else Integer.highestOneBit(n - 1) << 1
}
