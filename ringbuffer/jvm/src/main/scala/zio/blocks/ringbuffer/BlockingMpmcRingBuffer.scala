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

import java.util.concurrent.locks.ReentrantLock

/**
 * A blocking MPMC (Multi-Producer Multi-Consumer) ring buffer that layers
 * blocking semantics on top of the lock-free [[MpmcRingBuffer]].
 *
 * '''Design:''' Uses two separate `ReentrantLock` instances (like
 * `LinkedBlockingQueue`) to minimise contention between producers and
 * consumers. Each lock owns a `Condition` that waiting threads await on:
 *   - '''`producerLock` + `notFull`''' — producers acquire this lock and await
 *     when the buffer is full.
 *   - '''`consumerLock` + `notEmpty`''' — consumers acquire this lock and await
 *     when the buffer is empty.
 *
 * '''Fast-path optimization:''' The `put` method attempts `inner.offer` before
 * engaging the blocking machinery. If it succeeds, it signals `notEmpty` so a
 * waiting consumer can proceed. Similarly, `take` attempts `inner.poll` first.
 *
 * '''Null elements are not permitted.''' `offer(null)` and `tryOffer(null)` throw
 * `NullPointerException`.
 *
 * @param capacity
 *   the buffer capacity, must be a power of two >= 2
 */
final class BlockingMpmcRingBuffer[A <: AnyRef](val capacity: Int) {

  private val inner = new MpmcRingBuffer[A](capacity)

  // --- Producer blocking: ReentrantLock + Condition ---
  private val producerLock = new ReentrantLock()
  private val notFull      = producerLock.newCondition()

  // --- Consumer blocking: ReentrantLock + Condition ---
  private val consumerLock = new ReentrantLock()
  private val notEmpty     = consumerLock.newCondition()

  /**
   * Inserts an element, blocking the calling thread until space is available.
   *
   * Tries the lock-free fast path first via `inner.offer`. If that fails
   * (buffer full), acquires the `producerLock` and awaits on the `notFull`
   * condition, retrying after each wakeup. After a successful insert, signals
   * `notEmpty` so one waiting consumer can proceed.
   *
   * '''Thread safety:''' Safe to call from any number of producer threads
   * concurrently.
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
      signalNotEmpty()
      return
    }

    // Slow path: buffer is full, need to block
    producerLock.lockInterruptibly()
    try {
      while (!inner.offer(a)) notFull.await()
    } finally {
      producerLock.unlock()
    }
    signalNotEmpty()
  }

  /**
   * Removes and returns an element, blocking the calling thread until one is
   * available.
   *
   * Tries the lock-free fast path first via `inner.take`. If that returns
   * `null` (buffer empty), acquires the `consumerLock` and awaits on the
   * `notEmpty` condition, retrying after each wakeup. After a successful
   * removal, signals `notFull` so one waiting producer can proceed.
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
      signalNotFull()
      return fastResult
    }

    // Slow path: buffer is empty, need to block
    val e = {
      consumerLock.lockInterruptibly()
      try {
        var r = inner.take()
        while (r == null) {
          notEmpty.await()
          r = inner.take()
        }
        r
      } finally {
        consumerLock.unlock()
      }
    }
    signalNotFull()
    e
  }

  /**
   * Tries to insert an element without blocking.
   *
   * If the buffer is full, returns `false` immediately. On success, signals one
   * waiting consumer.
   *
   * '''Thread safety:''' Safe to call from any producer thread concurrently.
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
    if (r) { signalNotEmpty() }
    r
  }

  /**
   * Tries to remove an element without blocking.
   *
   * If the buffer is empty, returns `null` immediately. On success, signals one
   * waiting producer.
   *
   * '''Thread safety:''' Safe to call from any consumer thread concurrently.
   *
   * @return
   *   the element, or `null` if the buffer is empty
   */
  def tryTake(): A = {
    val r = inner.take()
    if (r != null) { signalNotFull() }
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
   * Signals one consumer waiting on the `notEmpty` condition. Acquires the
   * `consumerLock` briefly to perform the signal.
   */
  private def signalNotEmpty(): Unit = {
    consumerLock.lock()
    try notEmpty.signal()
    finally consumerLock.unlock()
  }

  /**
   * Signals one producer waiting on the `notFull` condition. Acquires the
   * `producerLock` briefly to perform the signal.
   */
  private def signalNotFull(): Unit = {
    producerLock.lock()
    try notFull.signal()
    finally producerLock.unlock()
  }
}

object BlockingMpmcRingBuffer {

  /**
   * Creates a new [[BlockingMpmcRingBuffer]] with the given capacity.
   *
   * @param capacity
   *   the buffer capacity, must be a power of two >= 2
   * @tparam A
   *   the element type (must be a reference type)
   * @return
   *   a new blocking MPMC ring buffer
   */
  def apply[A <: AnyRef](capacity: Int): BlockingMpmcRingBuffer[A] = new BlockingMpmcRingBuffer[A](capacity)
}
