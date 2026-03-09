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
 * A sequential SPMC ring buffer for generic reference types (Scala.js
 * implementation).
 *
 * Since Scala.js is single-threaded, this implementation uses plain reads and
 * writes with no memory ordering primitives or locks. It provides the same API
 * surface as the JVM implementation for cross-platform compatibility.
 *
 * @param capacity
 *   the buffer capacity, must be a positive power of two
 */
final class SpmcRingBuffer[A <: AnyRef](val capacity: Int) {
  require(capacity > 0 && (capacity & (capacity - 1)) == 0, s"capacity must be a positive power of 2, got: $capacity")

  private val mask: Int             = capacity - 1
  private val buffer: Array[AnyRef] = new Array[AnyRef](capacity)
  private var producerIndex: Long   = 0L
  private var consumerIndex: Long   = 0L

  /**
   * Tries to insert an element into the buffer.
   *
   * @param a
   *   the element to insert; must not be `null`
   * @throws NullPointerException
   *   if the element is `null`
   * @return
   *   `true` if the element was successfully inserted, `false` if the buffer is
   *   full
   */
  def offer(a: A): Boolean = {
    if (a == null) throw new NullPointerException("offer(null) is not permitted")
    val pIdx = producerIndex
    val cIdx = consumerIndex
    if ((pIdx - cIdx).toInt == capacity) {
      false
    } else {
      buffer((pIdx & mask).toInt) = a.asInstanceOf[AnyRef]
      producerIndex = pIdx + 1L
      true
    }
  }

  /**
   * Tries to remove an element from the buffer.
   *
   * @return
   *   the element, or `null` if the buffer is empty
   */
  def take(): A = {
    val cIdx = consumerIndex
    val pIdx = producerIndex
    if (pIdx == cIdx) {
      null.asInstanceOf[A]
    } else {
      val offset  = (cIdx & mask).toInt
      val element = buffer(offset).asInstanceOf[A]
      buffer(offset) = null
      consumerIndex = cIdx + 1L
      element
    }
  }

  /** Returns the number of elements currently in the buffer. */
  def size: Int = (producerIndex - consumerIndex).toInt

  /** Returns `true` if the buffer contains no elements. */
  def isEmpty: Boolean = producerIndex == consumerIndex

  /** Returns `true` if the buffer is full. */
  def isFull: Boolean = (producerIndex - consumerIndex).toInt == capacity
}

object SpmcRingBuffer {

  /**
   * Creates a new [[SpmcRingBuffer]] with the given capacity.
   *
   * @param capacity
   *   the buffer capacity, must be a positive power of two
   * @tparam A
   *   the element type (must be a reference type)
   * @return
   *   a new SPMC ring buffer
   */
  def apply[A <: AnyRef](capacity: Int): SpmcRingBuffer[A] = new SpmcRingBuffer[A](capacity)
}
