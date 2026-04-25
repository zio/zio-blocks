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

package zio.blocks.streams.internal

import zio.blocks.chunk.{Chunk, ChunkBuilder}

/**
 * A fixed-size, non-concurrent circular buffer specialized for unboxed `Byte`
 * values. Used internally by the `sliding` combinator to avoid O(n) array
 * copies on each step.
 */
private[streams] final class CircularBufferByte(capacity: Int) {
  private val arr  = new Array[Byte](capacity)
  private var head = 0
  private var len  = 0

  def size: Int       = len
  def isFull: Boolean = len == capacity

  def add(a: Byte): Unit = {
    val idx = (head + len) % capacity
    arr(idx) = a
    if (len == capacity) head = (head + 1) % capacity
    else len += 1
  }

  def get(i: Int): Byte = arr((head + i) % capacity)

  def shift(n: Int): Unit = {
    val drop = math.min(n, len)
    head = (head + drop) % capacity
    len -= drop
  }

  def toChunk: Chunk[Byte] = {
    val b = new ChunkBuilder.Byte()
    var i = 0
    while (i < len) { b.addOne(get(i)); i += 1 }
    b.result()
  }
}

/**
 * A fixed-size, non-concurrent circular buffer specialized for unboxed `Int`
 * values. Used internally by the `sliding` combinator to avoid O(n) array
 * copies on each step.
 */
private[streams] final class CircularBufferInt(capacity: Int) {
  private val arr  = new Array[Int](capacity)
  private var head = 0
  private var len  = 0

  def size: Int       = len
  def isFull: Boolean = len == capacity

  /**
   * Add an element. If the buffer is full, the oldest element is overwritten.
   */
  def add(a: Int): Unit = {
    val idx = (head + len) % capacity
    arr(idx) = a
    if (len == capacity) head = (head + 1) % capacity
    else len += 1
  }

  /** Get an element by logical index (0 = oldest). */
  def get(i: Int): Int = arr((head + i) % capacity)

  /** Remove the oldest `n` elements. */
  def shift(n: Int): Unit = {
    val drop = math.min(n, len)
    head = (head + drop) % capacity
    len -= drop
  }

  /** Copy the contents into a `Chunk[Int]`. */
  def toChunk: Chunk[Int] = {
    val b = new ChunkBuilder.Int()
    var i = 0
    while (i < len) { b.addOne(get(i)); i += 1 }
    b.result()
  }
}

/**
 * A fixed-size, non-concurrent circular buffer specialized for unboxed `Long`
 * values. Used internally by the `sliding` combinator to avoid O(n) array
 * copies on each step.
 */
private[streams] final class CircularBufferLong(capacity: Int) {
  private val arr  = new Array[Long](capacity)
  private var head = 0
  private var len  = 0

  def size: Int       = len
  def isFull: Boolean = len == capacity

  /**
   * Add an element. If the buffer is full, the oldest element is overwritten.
   */
  def add(a: Long): Unit = {
    val idx = (head + len) % capacity
    arr(idx) = a
    if (len == capacity) head = (head + 1) % capacity
    else len += 1
  }

  /** Get an element by logical index (0 = oldest). */
  def get(i: Int): Long = arr((head + i) % capacity)

  /** Remove the oldest `n` elements. */
  def shift(n: Int): Unit = {
    val drop = math.min(n, len)
    head = (head + drop) % capacity
    len -= drop
  }

  /** Copy the contents into a `Chunk[Long]`. */
  def toChunk: Chunk[Long] = {
    val b = new ChunkBuilder.Long()
    var i = 0
    while (i < len) { b.addOne(get(i)); i += 1 }
    b.result()
  }
}

/**
 * A fixed-size, non-concurrent circular buffer specialized for unboxed `Float`
 * values. Used internally by the `sliding` combinator to avoid O(n) array
 * copies on each step.
 */
private[streams] final class CircularBufferFloat(capacity: Int) {
  private val arr  = new Array[Float](capacity)
  private var head = 0
  private var len  = 0

  def size: Int       = len
  def isFull: Boolean = len == capacity

  /**
   * Add an element. If the buffer is full, the oldest element is overwritten.
   */
  def add(a: Float): Unit = {
    val idx = (head + len) % capacity
    arr(idx) = a
    if (len == capacity) head = (head + 1) % capacity
    else len += 1
  }

  /** Get an element by logical index (0 = oldest). */
  def get(i: Int): Float = arr((head + i) % capacity)

  /** Remove the oldest `n` elements. */
  def shift(n: Int): Unit = {
    val drop = math.min(n, len)
    head = (head + drop) % capacity
    len -= drop
  }

  /** Copy the contents into a `Chunk[Float]`. */
  def toChunk: Chunk[Float] = {
    val b = new ChunkBuilder.Float()
    var i = 0
    while (i < len) { b.addOne(get(i)); i += 1 }
    b.result()
  }
}

/**
 * A fixed-size, non-concurrent circular buffer specialized for unboxed `Double`
 * values. Used internally by the `sliding` combinator to avoid O(n) array
 * copies on each step.
 */
private[streams] final class CircularBufferDouble(capacity: Int) {
  private val arr  = new Array[Double](capacity)
  private var head = 0
  private var len  = 0

  def size: Int       = len
  def isFull: Boolean = len == capacity

  /**
   * Add an element. If the buffer is full, the oldest element is overwritten.
   */
  def add(a: Double): Unit = {
    val idx = (head + len) % capacity
    arr(idx) = a
    if (len == capacity) head = (head + 1) % capacity
    else len += 1
  }

  /** Get an element by logical index (0 = oldest). */
  def get(i: Int): Double = arr((head + i) % capacity)

  /** Remove the oldest `n` elements. */
  def shift(n: Int): Unit = {
    val drop = math.min(n, len)
    head = (head + drop) % capacity
    len -= drop
  }

  /** Copy the contents into a `Chunk[Double]`. */
  def toChunk: Chunk[Double] = {
    val b = new ChunkBuilder.Double()
    var i = 0
    while (i < len) { b.addOne(get(i)); i += 1 }
    b.result()
  }
}

/**
 * A fixed-size, non-concurrent circular buffer for boxed reference values. Used
 * internally by the `sliding` combinator to avoid O(n) array copies on each
 * step.
 */
private[streams] final class CircularBufferRef(capacity: Int) {
  private val arr  = new Array[AnyRef](capacity)
  private var head = 0
  private var len  = 0

  def size: Int       = len
  def isFull: Boolean = len == capacity

  /**
   * Add an element. If the buffer is full, the oldest element is overwritten.
   */
  def add(a: AnyRef): Unit = {
    val idx = (head + len) % capacity
    arr(idx) = a
    if (len == capacity) head = (head + 1) % capacity
    else len += 1
  }

  /** Get an element by logical index (0 = oldest). */
  def get(i: Int): AnyRef = arr((head + i) % capacity)

  /** Remove the oldest `n` elements. */
  def shift(n: Int): Unit = {
    val drop = math.min(n, len)
    var i    = 0
    while (i < drop) { arr((head + i) % capacity) = null; i += 1 }
    head = (head + drop) % capacity
    len -= drop
  }

  /** Copy the contents into a typed `Chunk[A]`. */
  def toChunk[A]: Chunk[A] = {
    val b = ChunkBuilder.make[A](len)
    var i = 0
    while (i < len) { b.addOne(get(i).asInstanceOf[A]); i += 1 }
    b.result()
  }
}
