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
 * A sequential blocking SPMC ring buffer for generic reference types (Scala.js
 * implementation).
 *
 * Since Scala.js is single-threaded, blocking operations (`offer`, `take`)
 * throw `UnsupportedOperationException` — a single-threaded runtime cannot
 * block waiting for another thread to make progress. The non-blocking
 * operations (`tryOffer`, `tryTake`) delegate to the underlying
 * [[SpmcRingBuffer]].
 *
 * @param capacity
 *   the buffer capacity, must be a positive power of two
 */
final class BlockingSpmcRingBuffer[A <: AnyRef](val capacity: Int) {

  private val inner = new SpmcRingBuffer[A](capacity)

  /**
   * Not supported on Scala.js. Always throws `UnsupportedOperationException`.
   */
  def offer(a: A): Unit =
    throw new UnsupportedOperationException("BlockingSpmcRingBuffer.offer is not supported on Scala.js")

  /**
   * Not supported on Scala.js. Always throws `UnsupportedOperationException`.
   */
  def take(): A =
    throw new UnsupportedOperationException("BlockingSpmcRingBuffer.take is not supported on Scala.js")

  /**
   * Tries to insert an element without blocking.
   *
   * @param a
   *   the element to insert; must not be `null`
   * @throws NullPointerException
   *   if `a` is `null`
   * @return
   *   `true` if the element was inserted, `false` if the buffer is full
   */
  def tryOffer(a: A): Boolean = inner.offer(a)

  /**
   * Tries to remove an element without blocking.
   *
   * @return
   *   the element, or `null` if the buffer is empty
   */
  def tryTake(): A = inner.take()

  /** Returns the number of elements currently in the buffer. */
  def size: Int = inner.size

  /** Returns `true` if the buffer contains no elements. */
  def isEmpty: Boolean = inner.isEmpty

  /** Returns `true` if the buffer is full. */
  def isFull: Boolean = inner.isFull
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
