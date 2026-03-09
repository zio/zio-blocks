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

import java.util.concurrent.locks.{LockSupport, ReentrantLock}

/**
 * A blocking SPMC (Single-Producer Multi-Consumer) ring buffer that layers
 * blocking semantics on top of the lock-free [[SpmcRingBuffer]].
 *
 * '''Design:'''
 *   - '''Producer blocking (when full):''' Uses a `volatile Thread` field with
 *     `LockSupport.park/unpark`. Since there is exactly one producer, only one
 *     thread can ever be parked on the full-buffer condition. This avoids any
 *     lock or CAS for the producer's blocking path.
 *   - '''Consumer blocking (when empty):''' Uses a `ReentrantLock` +
 *     `Condition notEmpty`. Multiple consumers acquire the lock and await on
 *     the condition; the producer signals after a successful offer.
 *
 * '''Fast-path optimization:''' On the fast path (buffer neither full nor
 * empty), no lock is acquired. The blocking machinery is only engaged when the
 * underlying non-blocking operation fails (i.e., `offer` returns `false` or
 * `poll` returns `null`).
 *
 * '''Null elements are not permitted.''' `offer(null)` and `tryOffer(null)`
 * throw `NullPointerException`.
 *
 * @param capacity
 *   the buffer capacity, must be a positive power of two
 */
final class BlockingSpmcRingBuffer[A <: AnyRef](val capacity: Int) {

  private val inner = new SpmcRingBuffer[A](capacity)

  /**
   * The producer thread that is currently parked waiting for space. Written
   * only by the single producer thread; read by consumer threads after a
   * successful `poll` to decide whether to unpark. Volatile ensures visibility
   * across threads.
   */
  @volatile private var waitingProducer: Thread = null

  // --- Consumer blocking: ReentrantLock + Condition (multiple consumers) ---
  private val consumerLock = new ReentrantLock()
  private val notEmpty     = consumerLock.newCondition()

  /**
   * Inserts an element, blocking the calling thread until space is available.
   *
   * On the fast path, delegates to `inner.offer`. If the buffer is full, the
   * producer parks via `LockSupport.park` using the Dekker-like protocol (store
   * thread reference, re-check condition, then park) to prevent lost wakeups.
   *
   * '''Thread safety:''' Must be called from the single producer thread only.
   * Calling from multiple threads concurrently results in undefined behavior.
   *
   * @param a
   *   the element to insert; must not be `null`
   * @throws NullPointerException
   *   if `a` is `null`
   * @throws InterruptedException
   *   if the calling thread is interrupted while waiting
   */
  @throws[InterruptedException]
  def offer(a: A): Unit = {
    if (a == null) throw new NullPointerException("offer(null) is not permitted")

    // Fast path: try non-blocking offer
    if (inner.offer(a)) {
      wakeConsumers()
      return
    }

    // Slow path: buffer is full, must block
    waitingProducer = Thread.currentThread()
    try {
      while (!inner.offer(a)) {
        LockSupport.park(this)
        if (Thread.interrupted()) throw new InterruptedException()
      }
    } finally {
      waitingProducer = null
    }
    wakeConsumers()
  }

  /**
   * Removes and returns an element, blocking the calling thread until one is
   * available.
   *
   * On the fast path, delegates to `inner.take`. If the buffer is empty,
   * acquires the `consumerLock` and awaits on the `notEmpty` condition. After a
   * successful poll (on either path), checks whether the producer is parked and
   * unparks it.
   *
   * '''Thread safety:''' Safe to call from any number of consumer threads
   * concurrently.
   *
   * @return
   *   the element (never `null`)
   * @throws InterruptedException
   *   if the calling thread is interrupted while waiting
   */
  @throws[InterruptedException]
  def take(): A = {
    // Fast path: try non-blocking take
    val fastResult = inner.take()
    if (fastResult != null) {
      wakeProducer()
      return fastResult
    }

    // Slow path: buffer is empty, need to block
    consumerLock.lockInterruptibly()
    try {
      var e = inner.take()
      while (e == null) {
        notEmpty.await()
        e = inner.take()
      }
      wakeProducer()
      e
    } finally {
      consumerLock.unlock()
    }
  }

  /**
   * Tries to insert an element without blocking.
   *
   * If the buffer is full, returns `false` immediately. On success, wakes one
   * waiting consumer if any are parked.
   *
   * '''Thread safety:''' Must be called from the single producer thread only.
   *
   * @param a
   *   the element to insert; must not be `null`
   * @throws NullPointerException
   *   if `a` is `null`
   * @return
   *   `true` if the element was inserted, `false` if the buffer is full
   */
  def tryOffer(a: A): Boolean = {
    val r = inner.offer(a)
    if (r) { wakeConsumers() }
    r
  }

  /**
   * Tries to remove an element without blocking.
   *
   * If the buffer is empty, returns `null` immediately. On success, wakes the
   * producer if it is parked.
   *
   * '''Thread safety:''' Safe to call from any consumer thread concurrently.
   *
   * @return
   *   the element, or `null` if the buffer is empty
   */
  def tryTake(): A = {
    val r = inner.take()
    if (r != null) wakeProducer()
    r
  }

  /**
   * Returns the number of elements currently in the buffer (approximate under
   * concurrency).
   */
  def size: Int = inner.size

  /**
   * Returns `true` if the buffer contains no elements (approximate under
   * concurrency).
   */
  def isEmpty: Boolean = inner.isEmpty

  /**
   * Returns `true` if the buffer is full (approximate under concurrency).
   */
  def isFull: Boolean = inner.isFull

  /**
   * Signals one waiting consumer that an element has been added. Acquires the
   * `consumerLock` and signals the `notEmpty` condition.
   */
  private def wakeConsumers(): Unit = {
    consumerLock.lock()
    try notEmpty.signal()
    finally consumerLock.unlock()
  }

  /**
   * Unparks the producer if it is currently parked waiting for space. A
   * volatile read of `waitingProducer` ensures visibility of the producer's
   * thread reference.
   */
  private def wakeProducer(): Unit = {
    val p = waitingProducer
    if (p != null) LockSupport.unpark(p)
  }
}

object BlockingSpmcRingBuffer {

  /**
   * Creates a new [[BlockingSpmcRingBuffer]] with the given capacity.
   *
   * @param capacity
   *   the buffer capacity, must be a positive power of two
   * @tparam A
   *   the element type (must be a reference type)
   * @return
   *   a new blocking SPMC ring buffer
   */
  def apply[A <: AnyRef](capacity: Int): BlockingSpmcRingBuffer[A] = new BlockingSpmcRingBuffer[A](capacity)
}
