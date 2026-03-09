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

import java.util.concurrent.locks.LockSupport

/**
 * A blocking wrapper around [[SpscRingBuffer]] for Single Producer, Single
 * Consumer use cases.
 *
 * Provides [[offer]] (blocking insert) and [[take]] (blocking remove) in addition
 * to the non-blocking [[tryOffer]] and [[tryTake]] delegates. Uses the Dekker-like
 * volatile-Thread + `LockSupport.park/unpark` pattern recommended by the JDK
 * and JCTools for single-waiter blocking.
 *
 * '''Loom-friendly''': `LockSupport.park` correctly unmounts virtual threads
 * from carrier threads (no pinning). No `synchronized` blocks are used.
 *
 * '''Thread-safety contract''': Exactly one producer thread and one consumer
 * thread may access this buffer concurrently. The producer thread calls
 * [[tryOffer]] or [[offer]]; the consumer thread calls [[tryTake]] or [[take]]. Calling
 * both [[offer]] and [[tryOffer]] from different threads, or both [[take]] and
 * [[tryTake]] from different threads, violates the SPSC contract.
 *
 * '''Null elements are not permitted.'''
 *
 * @param capacity
 *   the buffer capacity, must be a positive power of two
 * @tparam A
 *   the element type (must be a reference type)
 */
final class BlockingSpscRingBuffer[A <: AnyRef](val capacity: Int) {
  private val inner = new SpscRingBuffer[A](capacity)

  // Waiter fields — written only by the owning side, read by the opposite side.
  // Volatile provides the necessary visibility for the Dekker-like protocol.
  @volatile private var waitingProducer: Thread = null
  @volatile private var waitingConsumer: Thread = null

  /**
   * Inserts an element, blocking the calling thread if the buffer is full.
   *
   * Uses the Dekker-like protocol: publish the waiting thread reference, then
   * re-check the buffer. This guarantees no lost wakeups — either the consumer
   * sees our thread reference and will unpark us, or we see the newly freed
   * slot and proceed without parking.
   *
   * '''Loom-friendly''': virtual threads unmount on `LockSupport.park`.
   *
   * @param a
   *   the element to insert; must not be `null`
   * @throws NullPointerException
   *   if `a` is `null`
   * @throws InterruptedException
   *   if the calling thread is interrupted while waiting
   * @note
   *   Must be called from the producer thread only.
   */
  @throws[InterruptedException]
  def offer(a: A): Unit = {
    if (inner.offer(a)) { wakeConsumer(); return }
    // Fast path failed — buffer is full, prepare to park
    waitingProducer = Thread.currentThread()
    try {
      // Dekker: re-check after publishing waiter
      while (!inner.offer(a)) {
        LockSupport.park(this)
        if (Thread.interrupted()) throw new InterruptedException()
      }
    } finally {
      waitingProducer = null
    }
    wakeConsumer()
  }

  /**
   * Removes and returns an element, blocking the calling thread if the buffer
   * is empty.
   *
   * Uses the same Dekker-like protocol as [[offer]] to prevent lost wakeups.
   *
   * '''Loom-friendly''': virtual threads unmount on `LockSupport.park`.
   *
   * @return
   *   the next element in FIFO order
   * @throws InterruptedException
   *   if the calling thread is interrupted while waiting
   * @note
   *   Must be called from the consumer thread only.
   */
  @throws[InterruptedException]
  def take(): A = {
    val fast = inner.take()
    if (fast ne null) { wakeProducer(); return fast }
    // Fast path failed — buffer is empty, prepare to park
    waitingConsumer = Thread.currentThread()
    try {
      var e = inner.take()
      while (e eq null) {
        LockSupport.park(this)
        if (Thread.interrupted()) throw new InterruptedException()
        e = inner.take()
      }
      // Capture result before finally block clears waitingConsumer.
      // We call wakeProducer inside the try because `e` is our local.
      wakeProducer()
      e
    } finally {
      waitingConsumer = null
    }
  }

  /**
   * Tries to insert an element without blocking.
   *
   * If successful, wakes the consumer if it is parked.
   *
   * @param a
   *   the element to insert; must not be `null`
   * @throws NullPointerException
   *   if `a` is `null`
   * @return
   *   `true` if the element was inserted, `false` if the buffer is full
   * @note
   *   Must be called from the producer thread only.
   */
  def tryOffer(a: A): Boolean = {
    val r = inner.offer(a)
    if (r) wakeConsumer()
    r
  }

  /**
   * Tries to remove an element without blocking.
   *
   * If successful, wakes the producer if it is parked.
   *
   * @return
   *   the element, or `null` if the buffer is empty
   * @note
   *   Must be called from the consumer thread only.
   */
  def tryTake(): A = {
    val r = inner.take()
    if (r ne null) wakeProducer()
    r
  }

  /**
   * Returns the approximate number of elements currently in the buffer.
   *
   * This is a snapshot that may be stale by the time the caller reads it.
   */
  def size: Int = inner.size

  /**
   * Returns `true` if the buffer appears empty (approximate under concurrency).
   */
  def isEmpty: Boolean = inner.isEmpty

  /**
   * Returns `true` if the buffer appears full (approximate under concurrency).
   */
  def isFull: Boolean = inner.isFull

  private def wakeConsumer(): Unit = {
    val c = waitingConsumer
    if (c ne null) LockSupport.unpark(c)
  }

  private def wakeProducer(): Unit = {
    val p = waitingProducer
    if (p ne null) LockSupport.unpark(p)
  }
}

object BlockingSpscRingBuffer {

  /**
   * Creates a new [[BlockingSpscRingBuffer]] with the given capacity.
   *
   * @param capacity
   *   the buffer capacity, must be a positive power of two
   * @tparam A
   *   the element type (must be a reference type)
   * @return
   *   a new blocking SPSC ring buffer
   */
  def apply[A <: AnyRef](capacity: Int): BlockingSpscRingBuffer[A] =
    new BlockingSpscRingBuffer[A](capacity)
}
