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

import zio.blocks.ringbuffer.MpmcRingBuffer

import java.util.concurrent.locks.LockSupport

/**
 * A bounded, blocking queue for many producer and many consumer threads (JVM
 * only), on top of a lock-free ring buffer. Any number of threads may call
 * [[offer]] and [[take]] at once.
 *
 * A blocked thread parks for about a microsecond at a time and then retries.
 * The other side, and [[close]], can wake one waiting producer and one waiting
 * consumer early; the other waiting threads notice on their next retry.
 * Elements must not be `null`, because [[take]] uses `null` to report a closed,
 * empty queue.
 *
 * @param capacity
 *   The number of elements the queue holds, rounded up to a power of two of at
 *   least 2 (`1` holds 2, `3` holds 4). Throws `IllegalArgumentException` when
 *   it is below 1.
 */
final class BlockingMpmcQueue[A <: AnyRef](capacity: Int) {
  require(capacity >= 1, s"BlockingMpmcQueue requires capacity >= 1, got: $capacity")

  private val ringBuffer: MpmcRingBuffer[AnyRef] =
    new MpmcRingBuffer[AnyRef](nextPowerOfTwo(capacity))

  @volatile private var consumerWaiter: Thread = null
  @volatile private var producerWaiter: Thread = null
  @volatile private var closed: Boolean        = false

  /**
   * Adds `a`, blocking while the queue is full. Returns `true` once `a` is in
   * the queue, or `false`, without adding it, when the queue is closed. Throws
   * `NullPointerException` for a `null` element.
   */
  def offer(a: A): Boolean = {
    if (a == null) throw new NullPointerException("BlockingMpmcQueue.offer(null) is not permitted")
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
   * Removes and returns the oldest element, blocking while the queue is empty.
   * After [[close]], it still returns the elements left in the queue, and then
   * `null`.
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
        LockSupport.parkNanos(this, 1000L)
      } finally {
        consumerWaiter = null
      }
    }
    null.asInstanceOf[A]
  }

  /**
   * Closes the queue: later offers return `false`, and threads blocked in
   * [[offer]] or [[take]] wake up and see it.
   */
  def close(): Unit = {
    closed = true
    val cw = consumerWaiter
    if (cw ne null) LockSupport.unpark(cw)
    val pw = producerWaiter
    if (pw ne null) LockSupport.unpark(pw)
  }

  /** Returns `true` once [[close]] has been called. */
  def isClosed: Boolean = closed

  /**
   * Returns `true` when the queue holds no elements; with other threads
   * offering or taking, the answer may be stale by the time it returns.
   */
  def isEmpty: Boolean = ringBuffer.isEmpty

  private def nextPowerOfTwo(n: Int): Int =
    if (n <= 1) 2
    else {
      val hob = Integer.highestOneBit(n - 1) << 1
      if (hob < 2) 2 else hob
    }
}
