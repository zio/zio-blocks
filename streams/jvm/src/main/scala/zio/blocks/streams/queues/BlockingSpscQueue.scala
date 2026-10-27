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
 * A bounded, blocking queue for one producer thread and one consumer thread
 * (JVM only), on top of a lock-free ring buffer. A blocked side spins briefly
 * and then parks until the other side wakes it or the queue closes.
 *
 * At most one thread at a time may call [[offer]], and at most one thread at a
 * time may call [[take]]. Elements must not be `null`, because [[take]] uses
 * `null` to report a closed, empty queue.
 *
 * @param capacity
 *   The number of elements the queue holds, rounded up to a power of two (`3`
 *   holds 4). Throws `IllegalArgumentException` when it is below 1.
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
   * Adds `a`, blocking while the queue is full. Returns `true` once `a` is in
   * the queue, or `false`, without adding it, when the queue is closed. Throws
   * `NullPointerException` for a `null` element.
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

  /** Removes and returns the oldest element without blocking, or `null`. */
  private[streams] def poll(): A = {
    val e = ringBuffer.take().asInstanceOf[A]
    if (e ne null) {
      val w = producerWaiter
      if (w ne null) LockSupport.unpark(w)
    }
    e
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
    if (n <= 1) 1
    else Integer.highestOneBit(n - 1) << 1
}
