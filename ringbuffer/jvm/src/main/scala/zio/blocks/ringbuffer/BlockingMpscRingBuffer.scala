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
 * A blocking wrapper around [[MpscRingBuffer]] for Multiple Producer, Single
 * Consumer use cases.
 *
 * Provides [[offer]] (blocking insert) and [[take]] (blocking remove) in
 * addition to the non-blocking [[tryOffer]] and [[tryTake]] delegates.
 *
 * '''Blocking strategy''':
 *   - '''Consumer side''' (single waiter): volatile `Thread` field +
 *     `LockSupport.park/unpark` with Dekker-like protocol. Zero allocation,
 *     zero CAS for the common case.
 *   - '''Producer side''' (multiple waiters): `ReentrantLock` +
 *     `Condition notFull`. Multiple producers acquire the lock and await on the
 *     condition; consumers signal after a successful poll.
 *
 * '''Thread-safety contract''': Any number of producer threads may call
 * [[tryOffer]] or [[offer]] concurrently. Exactly one consumer thread may call
 * [[tryTake]] or [[take]]. Calling consumer methods from multiple threads
 * violates the single-consumer invariant.
 *
 * '''Null elements are not permitted.'''
 *
 * @param capacity
 *   the buffer capacity, must be a positive power of two
 * @tparam A
 *   the element type (must be a reference type)
 */
final class BlockingMpscRingBuffer[A <: AnyRef](val capacity: Int) {
  private val inner = new MpscRingBuffer[A](capacity)

  // --- Consumer blocking: single volatile Thread (only 1 consumer) ---
  @volatile private var waitingConsumer: Thread = null

  // --- Producer blocking: ReentrantLock + Condition (multiple producers) ---
  private val producerLock                        = new ReentrantLock()
  private val notFull                             = producerLock.newCondition()
  @volatile private var waitingProducerCount: Int = 0

  /**
   * Inserts an element, blocking the calling thread if the buffer is full.
   *
   * The fast path is lock-free: if `inner.offer` succeeds, no lock is acquired.
   * Only when the buffer is truly full does the producer acquire the
   * `producerLock` and await on the `notFull` condition.
   *
   * @param a
   *   the element to insert; must not be `null`
   * @throws NullPointerException
   *   if `a` is `null`
   * @throws InterruptedException
   *   if the calling thread is interrupted while waiting
   * @note
   *   May be called from any producer thread.
   */
  @throws[InterruptedException]
  def offer(a: A): Unit = {
    if (inner.offer(a)) { wakeConsumer(); return }
    // Fast path failed — buffer is full, need to block
    producerLock.lockInterruptibly()
    try {
      waitingProducerCount += 1
      try {
        while (!inner.offer(a)) notFull.await()
      } finally {
        waitingProducerCount -= 1
      }
    } finally {
      producerLock.unlock()
    }
    wakeConsumer()
  }

  /**
   * Removes and returns an element, blocking the calling thread if the buffer
   * is empty.
   *
   * Uses the Dekker-like volatile-Thread + park/unpark protocol. After
   * successfully taking, signals one waiting producer (if any) via the
   * `notFull` condition.
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
    if (fast ne null) { wakeProducers(); return fast }
    // Fast path failed — buffer is empty, prepare to park
    waitingConsumer = Thread.currentThread()
    try {
      var e = inner.take()
      while (e eq null) {
        LockSupport.park(this)
        if (Thread.interrupted()) throw new InterruptedException()
        e = inner.take()
      }
      wakeProducers()
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
   *   May be called from any producer thread.
   */
  def tryOffer(a: A): Boolean = {
    val r = inner.offer(a)
    if (r) { wakeConsumer() }
    r
  }

  /**
   * Tries to remove an element without blocking.
   *
   * If successful, signals one waiting producer (if any).
   *
   * @return
   *   the element, or `null` if the buffer is empty
   * @note
   *   Must be called from the consumer thread only.
   */
  def tryTake(): A = {
    val r = inner.take()
    if (r ne null) { wakeProducers() }
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

  private def wakeProducers(): Unit =
    if (waitingProducerCount > 0) {
      producerLock.lock()
      try notFull.signal()
      finally producerLock.unlock()
    }
}

object BlockingMpscRingBuffer {

  /**
   * Creates a new [[BlockingMpscRingBuffer]] with the given capacity.
   *
   * @param capacity
   *   the buffer capacity, must be a positive power of two
   * @tparam A
   *   the element type (must be a reference type)
   * @return
   *   a new blocking MPSC ring buffer
   */
  def apply[A <: AnyRef](capacity: Int): BlockingMpscRingBuffer[A] =
    new BlockingMpscRingBuffer[A](capacity)
}
