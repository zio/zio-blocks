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
 * Scala.js stub for [[BlockingMpscRingBuffer]].
 *
 * Blocking operations ([[offer]] and [[take]]) are not supported on Scala.js
 * because JavaScript is single-threaded and cannot block. Non-blocking
 * delegates ([[tryOffer]], [[tryTake]], [[size]], [[isEmpty]], [[isFull]]) work
 * normally.
 *
 * @param capacity
 *   the buffer capacity, must be a positive power of two
 */
final class BlockingMpscRingBuffer[A <: AnyRef](val capacity: Int) {
  private val inner = new MpscRingBuffer[A](capacity)

  /**
   * Not supported on Scala.js.
   *
   * @throws UnsupportedOperationException
   *   always
   */
  def offer(a: A): Unit =
    throw new UnsupportedOperationException("BlockingMpscRingBuffer.offer is not supported on Scala.js")

  /**
   * Not supported on Scala.js.
   *
   * @throws UnsupportedOperationException
   *   always
   */
  def take(): A =
    throw new UnsupportedOperationException("BlockingMpscRingBuffer.take is not supported on Scala.js")

  /** Tries to insert an element without blocking. */
  def tryOffer(a: A): Boolean = inner.offer(a)

  /** Tries to remove an element without blocking. */
  def tryTake(): A = inner.take()

  /** Returns the number of elements currently in the buffer. */
  def size: Int = inner.size

  /** Returns `true` if the buffer contains no elements. */
  def isEmpty: Boolean = inner.isEmpty

  /** Returns `true` if the buffer is full. */
  def isFull: Boolean = inner.isFull
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
