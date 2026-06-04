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
 * A blocking MPMC (multi-producer, multi-consumer) queue.
 *
 * Backed by [[MpmcRingBuffer]] for the fast path; uses
 * [[LockSupport.parkNanos]] for blocking when the buffer is full or empty.
 *
 * Multiple threads may call [[offer]] and [[take]] concurrently.
 *
 * ==Single-Waiter Design==
 *
 * Only one producer and one consumer can be fast-path woken at a time. The
 * queue maintains a single `producerWaiter` and `consumerWaiter` volatile slot.
 * When a thread blocks, it registers itself in the appropriate slot and calls
 * `LockSupport.parkNanos(this, 1000L)` (1 microsecond timeout). When the
 * opposite side has data/space, it unparks the registered waiter. Other waiting
 * threads wake up within 1 microsecond via the timeout and retry.
 *
 * This design is virtual-thread friendly: `parkNanos` does not OS-block on
 * virtual threads, allowing bounded re-checks without risk of permanent
 * parking.
 *
 * ==Happens-Before Guarantee==
 *
 * The pattern `producerWaiter = Thread.currentThread()` (volatile write)
 * followed by `if (closed) return false` (volatile read) ensures that a
 * `close()` call setting `closed = true` is always visible before the thread
 * parks. This prevents the race where a thread parks after close() has already
 * been called.
 *
 * ==close() Behavior==
 *
 * `close()` unparks only the registered `producerWaiter` and `consumerWaiter`.
 * Other waiting threads wake up within 1 microsecond via `parkNanos` timeout
 * and detect the closed flag on retry. This is intentional and documented.
 *
 * Null elements are not permitted.
 *
 * @param capacity
 *   The logical capacity. Rounded up to the next power of two >= 2 internally.
 */
final class BlockingMpmcQueue[A <: AnyRef](capacity: Int) {
  require(capacity >= 1, s"BlockingMpmcQueue requires capacity >= 1, got: $capacity")

  private val ringBuffer: MpmcRingBuffer[AnyRef] =
    new MpmcRingBuffer[AnyRef](nextPowerOfTwo(capacity))

  @volatile private var consumerWaiter: Thread = null
  @volatile private var producerWaiter: Thread = null
  @volatile private var closed: Boolean        = false

  /**
   * Offers an element to the queue, blocking until space is available or the
   * queue is closed. Returns `true` on success, `false` if the queue was
   * closed.
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
        LockSupport.parkNanos(this, 1000L)
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

  private def nextPowerOfTwo(n: Int): Int =
    if (n <= 1) 2
    else {
      val hob = Integer.highestOneBit(n - 1) << 1
      if (hob < 2) 2 else hob
    }
}
