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

import zio.blocks.ringbuffer.SpscRingBuffer

import java.util.concurrent.locks.LockSupport

/**
 * A blocking single-producer single-consumer queue.
 *
 * Backed by [[SpscRingBuffer]] for the fast path; uses [[LockSupport.park]] and
 * [[LockSupport.unpark]] for blocking when the buffer is full or empty.
 *
 * Two separate waiter slots are maintained — `consumerWaiter` and
 * `producerWaiter` — so that each side registers in its own slot without
 * overwriting the other side's registration. A single shared slot would
 * introduce a lost-wakeup race: the producer could overwrite the consumer's
 * registration before the consumer parks, causing the consumer's park to return
 * via stale permit and then clear the producer's registration, leaving the
 * producer permanently blocked.
 *
 * Null elements are not permitted.
 *
 * @param capacity
 *   The logical capacity. Rounded up to the next power of two internally.
 */
final class BlockingSpscQueue[A <: AnyRef](capacity: Int) {
  require(capacity >= 1, s"BlockingSpscQueue requires capacity >= 1, got: $capacity")

  private val spinTries = 1024

  private val ringBuffer: SpscRingBuffer[AnyRef] =
    new SpscRingBuffer[AnyRef](nextPowerOfTwo(capacity))

  @volatile private var consumerWaiter: Thread = null
  @volatile private var producerWaiter: Thread = null
  @volatile private var closed: Boolean        = false

  /**
   * Offers an element to the queue, blocking until space is available or the
   * queue is closed. Returns `true` on success, `false` if the queue was
   * closed.
   */
  def offer(a: A): Boolean = {
    if (a == null) throw new NullPointerException("BlockingSpscQueue.offer(null) is not permitted")
    val element = a.asInstanceOf[AnyRef]
    while (true) {
      if (closed) return false
      if (ringBuffer.offer(element)) {
        val w = consumerWaiter
        if (w ne null) LockSupport.unpark(w)
        return true
      }
      producerWaiter = Thread.currentThread()
      try {
        if (closed) return false
        if (ringBuffer.offer(element)) {
          val w = consumerWaiter
          if (w ne null) LockSupport.unpark(w)
          return true
        }
        var spins = 0
        while (spins < spinTries) {
          if (closed) return false
          if (ringBuffer.offer(element)) {
            val w = consumerWaiter
            if (w ne null) LockSupport.unpark(w)
            return true
          }
          Thread.onSpinWait()
          spins += 1
        }
        LockSupport.park(this)
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
      // Drain-on-close: a producer may have offered + closed between our take()
      // and the closed read, so re-check the ring buffer after observing closed.
      if (closed) {
        val drained = ringBuffer.take().asInstanceOf[A]
        if (drained ne null) return drained
        return null.asInstanceOf[A]
      }
      consumerWaiter = Thread.currentThread()
      try {
        val e2 = ringBuffer.take().asInstanceOf[A]
        if (e2 ne null) {
          val w = producerWaiter
          if (w ne null) LockSupport.unpark(w)
          return e2
        }
        if (closed) {
          val drained = ringBuffer.take().asInstanceOf[A]
          if (drained ne null) return drained
          return null.asInstanceOf[A]
        }
        var spins = 0
        while (spins < spinTries) {
          val e3 = ringBuffer.take().asInstanceOf[A]
          if (e3 ne null) {
            val w = producerWaiter
            if (w ne null) LockSupport.unpark(w)
            return e3
          }
          if (closed) {
            val drained = ringBuffer.take().asInstanceOf[A]
            if (drained ne null) return drained
            return null.asInstanceOf[A]
          }
          Thread.onSpinWait()
          spins += 1
        }
        LockSupport.park(this)
      } finally {
        consumerWaiter = null
      }
    }
    null.asInstanceOf[A]
  }

  private[streams] def poll(): A = {
    val e = ringBuffer.take().asInstanceOf[A]
    if (e ne null) {
      val w = producerWaiter
      if (w ne null) LockSupport.unpark(w)
    }
    e
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
    if (n <= 1) 1
    else Integer.highestOneBit(n - 1) << 1
}
