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

import zio.blocks.streams.JvmType
import zio.blocks.streams.io.Reader
import zio.blocks.chunk.{Chunk, ChunkBuilder}

import java.nio.ByteBuffer

/**
 * Reads bytes from a [[java.nio.ByteBuffer]]. Supports `reset()` via
 * `buffer.rewind()`.
 */
private[streams] final class ByteBufferReader(buffer: ByteBuffer) extends Reader[Byte] {

  private val originalLimit: Int    = buffer.limit()
  private val originalPosition: Int = buffer.position()
  private var limitN: Long          = Long.MaxValue
  private var skipN: Long           = 0
  private var done: Boolean         = false

  def close(): Unit = {
    buffer.position(buffer.limit())
    done = true
  }

  def isClosed: Boolean = done

  override def jvmType: JvmType = JvmType.Byte

  def read[A1 >: Byte](sentinel: A1): A1 = {
    val b = readByte()
    if (b >= 0) Byte.box(b.toByte).asInstanceOf[A1] else sentinel
  }

  override def readN[A1 >: Byte](n: Int): Chunk[A1] = {
    if (n <= 0 || done) return Chunk.empty
    val count = math.min(n, buffer.remaining())
    if (count == 0) { done = true; return Chunk.empty }
    val arr = new Array[Byte](count)
    buffer.get(arr, 0, count)
    if (buffer.remaining() == 0) done = true
    Chunk.fromArray(arr).asInstanceOf[Chunk[A1]]
  }

  override def readUpToN[A1 >: Byte](n: Int): Chunk[A1] = {
    if (n <= 0) return Chunk.empty
    val arr  = new Array[Byte](n)
    val read = readBytes(arr, 0, n)(unsafeEvidence)
    if (read <= 0) Chunk.empty
    else if (read == n) Chunk.fromArray(arr).asInstanceOf[Chunk[A1]]
    else Chunk.fromArray(java.util.Arrays.copyOf(arr, read)).asInstanceOf[Chunk[A1]]
  }

  override def readable(): Boolean = !done && buffer.hasRemaining

  override def readByte(): Int =
    if (done) -1
    else if (buffer.hasRemaining) (buffer.get() & 0xff)
    else { done = true; -1 }

  override def readBytes(buf: Array[Byte], offset: Int, len: Int)(implicit ev: Byte <:< Byte): Int =
    if (len == 0) 0
    else if (done) -1
    else {
      val rem = buffer.remaining()
      if (rem <= 0) { done = true; -1 }
      else {
        val n = math.min(len, rem)
        buffer.get(buf, offset, n)
        n
      }
    }

  override def reset(): Unit = {
    done = false
    val clampedSkip = math.max(0, if (skipN > Int.MaxValue) Int.MaxValue else skipN.toInt)
    val startPos    = math.min(originalPosition + clampedSkip, originalLimit)
    buffer.limit(originalLimit)
    buffer.position(startPos)
    if (limitN != Long.MaxValue) {
      buffer.limit(math.min(originalLimit, startPos + (if (limitN > Int.MaxValue) Int.MaxValue else limitN.toInt)))
    }
  }

  override def setLimit(n: Long): Boolean = {
    limitN = n
    val newLimit = math.min(originalLimit, buffer.position() + (if (n > Int.MaxValue) Int.MaxValue else n.toInt))
    buffer.limit(newLimit)
    true
  }

  override def setSkip(n: Long): Boolean = {
    skipN = n
    val clampedN = math.max(0, if (n > Int.MaxValue) Int.MaxValue else n.toInt)
    val newPos   = math.min(buffer.position() + clampedN, buffer.limit())
    buffer.position(newPos)
    true
  }

  override def skip(n: Long): Unit = {
    val s = math.min(n, buffer.remaining().toLong).toInt
    buffer.position(buffer.position() + s)
  }
}

/**
 * Reads Ints from a [[java.nio.ByteBuffer]] via `buffer.getInt()`. Avoids
 * boxing by overriding `readInt`.
 */
private[streams] final class ByteBufferIntReader(buffer: ByteBuffer) extends Reader[Int] {

  private val originalLimit: Int    = buffer.limit()
  private val originalPosition: Int = buffer.position()
  private var limitN: Long          = Long.MaxValue
  private var skipN: Long           = 0
  private var done: Boolean         = false

  def close(): Unit = { buffer.position(buffer.limit()); done = true }

  def isClosed: Boolean = done

  override def jvmType: JvmType = JvmType.Int

  def read[A1 >: Int](sentinel: A1): A1 =
    if (done) sentinel
    else if (buffer.remaining() >= 4) Int.box(buffer.getInt()).asInstanceOf[A1]
    else { done = true; sentinel }

  override def readN[A1 >: Int](n: Int): Chunk[A1] = {
    if (n <= 0 || done) return Chunk.empty
    val count = math.min(n, buffer.remaining() / 4)
    if (count == 0) { done = true; return Chunk.empty }
    val arr = new Array[Int](count)
    var i   = 0
    while (i < count) {
      arr(i) = buffer.getInt()
      i += 1
    }
    if (buffer.remaining() < 4) done = true
    Chunk.fromArray(arr).asInstanceOf[Chunk[A1]]
  }

  override def readUpToN[A1 >: Int](n: Int): Chunk[A1] = {
    if (n <= 0) return Chunk.empty
    val b = new ChunkBuilder.Int(); b.sizeHint(math.min(n, 64))
    val s = Long.MinValue
    var v = readInt(s)(unsafeEvidence)
    if (v == s) return Chunk.empty
    var i = 0
    while (v != s && i < n) {
      b.addOne(v.toInt); i += 1
      if (i < n) v = readInt(s)(unsafeEvidence)
    }
    b.result().asInstanceOf[Chunk[A1]]
  }

  override def readable(): Boolean = !done && buffer.remaining() >= 4

  override def readByte(): Int =
    if (done) -1
    else if (buffer.remaining() >= 4) (buffer.getInt() & 0xff)
    else { done = true; -1 }

  override def readInt(sentinel: Long)(implicit ev: Int <:< Int): Long =
    if (done) sentinel
    else if (buffer.remaining() >= 4) buffer.getInt().toLong
    else { done = true; sentinel }

  override def reset(): Unit = {
    done = false
    val clampedSkip = math.max(0L, skipN)
    val bytesSkip   = if (clampedSkip > Int.MaxValue / 4) Int.MaxValue else clampedSkip.toInt * 4
    val startPos    = math.min(originalPosition + bytesSkip, originalLimit)
    buffer.limit(originalLimit)
    buffer.position(startPos)
    if (limitN != Long.MaxValue) {
      val bytesN = if (limitN > Int.MaxValue / 4) Int.MaxValue else limitN.toInt * 4
      buffer.limit(math.min(originalLimit, startPos + bytesN))
    }
  }

  override def setLimit(n: Long): Boolean = {
    limitN = n
    val bytesN = if (n > Int.MaxValue / 4) Int.MaxValue else n.toInt * 4
    buffer.limit(math.min(originalLimit, buffer.position() + bytesN))
    true
  }

  override def setSkip(n: Long): Boolean = {
    skipN = n
    val clamped   = math.max(0L, n)
    val bytesSkip = if (clamped > Int.MaxValue / 4) Int.MaxValue else clamped.toInt * 4
    val newPos    = math.min(buffer.position() + bytesSkip, buffer.limit())
    buffer.position(newPos)
    true
  }

  override def skip(n: Long): Unit = {
    val s = math.min(n, (buffer.remaining() / 4).toLong).toInt
    buffer.position(buffer.position() + s * 4)
  }
}

/**
 * Reads Longs from a [[java.nio.ByteBuffer]] via `buffer.getLong()`.
 * Zero-boxing through `readLong`.
 */
private[streams] final class ByteBufferLongReader(buffer: ByteBuffer) extends Reader[Long] {

  private val originalLimit: Int    = buffer.limit()
  private val originalPosition: Int = buffer.position()
  private var limitN: Long          = Long.MaxValue
  private var skipN: Long           = 0
  private var done: Boolean         = false

  def close(): Unit = { buffer.position(buffer.limit()); done = true }

  def isClosed: Boolean = done

  override def jvmType: JvmType = JvmType.Long

  def read[A1 >: Long](sentinel: A1): A1 =
    if (done) sentinel
    else if (buffer.remaining() >= 8) Long.box(buffer.getLong()).asInstanceOf[A1]
    else { done = true; sentinel }

  override def readN[A1 >: Long](n: Int): Chunk[A1] = {
    if (n <= 0 || done) return Chunk.empty
    val count = math.min(n, buffer.remaining() / 8)
    if (count == 0) { done = true; return Chunk.empty }
    val arr = new Array[Long](count)
    var i   = 0
    while (i < count) {
      arr(i) = buffer.getLong()
      i += 1
    }
    if (buffer.remaining() < 8) done = true
    Chunk.fromArray(arr).asInstanceOf[Chunk[A1]]
  }

  override def readUpToN[A1 >: Long](n: Int): Chunk[A1] = {
    if (n <= 0) return Chunk.empty
    val b = new ChunkBuilder.Long(); b.sizeHint(math.min(n, 64))
    val s = Long.MaxValue
    var v = readLong(s)(unsafeEvidence)
    if (v == s) return Chunk.empty
    var i = 0
    while (v != s && i < n) {
      b.addOne(v); i += 1
      if (i < n) v = readLong(s)(unsafeEvidence)
    }
    b.result().asInstanceOf[Chunk[A1]]
  }

  override def readable(): Boolean = !done && buffer.remaining() >= 8

  override def readByte(): Int =
    if (done) -1
    else if (buffer.remaining() >= 8) (buffer.getLong().toInt & 0xff)
    else { done = true; -1 }

  override def readLong(sentinel: Long)(implicit ev: Long <:< Long): Long =
    if (done) sentinel
    else if (buffer.remaining() >= 8) buffer.getLong()
    else { done = true; sentinel }

  override def reset(): Unit = {
    done = false
    val clampedSkip = math.max(0L, skipN)
    val bytesSkip   = if (clampedSkip > Int.MaxValue / 8) Int.MaxValue else clampedSkip.toInt * 8
    val startPos    = math.min(originalPosition + bytesSkip, originalLimit)
    buffer.limit(originalLimit)
    buffer.position(startPos)
    if (limitN != Long.MaxValue) {
      val bytesN = if (limitN > Int.MaxValue / 8) Int.MaxValue else limitN.toInt * 8
      buffer.limit(math.min(originalLimit, startPos + bytesN))
    }
  }

  override def setLimit(n: Long): Boolean = {
    limitN = n
    val bytesN = if (n > Int.MaxValue / 8) Int.MaxValue else n.toInt * 8
    buffer.limit(math.min(originalLimit, buffer.position() + bytesN))
    true
  }

  override def setSkip(n: Long): Boolean = {
    skipN = n
    val clamped   = math.max(0L, n)
    val bytesSkip = if (clamped > Int.MaxValue / 8) Int.MaxValue else clamped.toInt * 8
    val newPos    = math.min(buffer.position() + bytesSkip, buffer.limit())
    buffer.position(newPos)
    true
  }

  override def skip(n: Long): Unit = {
    val s = math.min(n, (buffer.remaining() / 8).toLong).toInt
    buffer.position(buffer.position() + s * 8)
  }
}

/**
 * Reads Doubles from a [[java.nio.ByteBuffer]] via `buffer.getDouble()`.
 * Zero-boxing through `readDouble`.
 */
private[streams] final class ByteBufferDoubleReader(buffer: ByteBuffer) extends Reader[Double] {

  private val originalLimit: Int    = buffer.limit()
  private val originalPosition: Int = buffer.position()
  private var limitN: Long          = Long.MaxValue
  private var skipN: Long           = 0
  private var done: Boolean         = false

  def close(): Unit = { buffer.position(buffer.limit()); done = true }

  def isClosed: Boolean = done

  override def jvmType: JvmType = JvmType.Double

  def read[A1 >: Double](sentinel: A1): A1 =
    if (done) sentinel
    else if (buffer.remaining() >= 8) Double.box(buffer.getDouble()).asInstanceOf[A1]
    else { done = true; sentinel }

  override def readN[A1 >: Double](n: Int): Chunk[A1] = {
    if (n <= 0 || done) return Chunk.empty
    val count = math.min(n, buffer.remaining() / 8)
    if (count == 0) { done = true; return Chunk.empty }
    val arr = new Array[Double](count)
    var i   = 0
    while (i < count) {
      arr(i) = buffer.getDouble()
      i += 1
    }
    if (buffer.remaining() < 8) done = true
    Chunk.fromArray(arr).asInstanceOf[Chunk[A1]]
  }

  override def readUpToN[A1 >: Double](n: Int): Chunk[A1] = {
    if (n <= 0) return Chunk.empty
    val b = new ChunkBuilder.Double(); b.sizeHint(math.min(n, 64))
    val s = Double.MaxValue
    var v = readDouble(s)(unsafeEvidence)
    if (v == s) return Chunk.empty
    var i = 0
    while (v != s && i < n) {
      b.addOne(v); i += 1
      if (i < n) v = readDouble(s)(unsafeEvidence)
    }
    b.result().asInstanceOf[Chunk[A1]]
  }

  override def readable(): Boolean = !done && buffer.remaining() >= 8

  override def readByte(): Int =
    if (done) -1
    else if (buffer.remaining() >= 8) (buffer.getDouble().toInt & 0xff)
    else { done = true; -1 }

  override def readDouble(sentinel: Double)(implicit ev: Double <:< Double): Double =
    if (done) sentinel
    else if (buffer.remaining() >= 8) buffer.getDouble()
    else { done = true; sentinel }

  override def reset(): Unit = {
    done = false
    val clampedSkip = math.max(0L, skipN)
    val bytesSkip   = if (clampedSkip > Int.MaxValue / 8) Int.MaxValue else clampedSkip.toInt * 8
    val startPos    = math.min(originalPosition + bytesSkip, originalLimit)
    buffer.limit(originalLimit)
    buffer.position(startPos)
    if (limitN != Long.MaxValue) {
      val bytesN = if (limitN > Int.MaxValue / 8) Int.MaxValue else limitN.toInt * 8
      buffer.limit(math.min(originalLimit, startPos + bytesN))
    }
  }

  override def setLimit(n: Long): Boolean = {
    limitN = n
    val bytesN = if (n > Int.MaxValue / 8) Int.MaxValue else n.toInt * 8
    buffer.limit(math.min(originalLimit, buffer.position() + bytesN))
    true
  }

  override def setSkip(n: Long): Boolean = {
    skipN = n
    val clamped   = math.max(0L, n)
    val bytesSkip = if (clamped > Int.MaxValue / 8) Int.MaxValue else clamped.toInt * 8
    val newPos    = math.min(buffer.position() + bytesSkip, buffer.limit())
    buffer.position(newPos)
    true
  }

  override def skip(n: Long): Unit = {
    val s = math.min(n, (buffer.remaining() / 8).toLong).toInt
    buffer.position(buffer.position() + s * 8)
  }
}

/**
 * Reads Floats from a [[java.nio.ByteBuffer]] via `buffer.getFloat()`.
 * Zero-boxing through `readFloat`.
 */
private[streams] final class ByteBufferFloatReader(buffer: ByteBuffer) extends Reader[Float] {

  private val originalLimit: Int    = buffer.limit()
  private val originalPosition: Int = buffer.position()
  private var limitN: Long          = Long.MaxValue
  private var skipN: Long           = 0
  private var done: Boolean         = false

  def close(): Unit = { buffer.position(buffer.limit()); done = true }

  def isClosed: Boolean = done

  override def jvmType: JvmType = JvmType.Float

  def read[A1 >: Float](sentinel: A1): A1 =
    if (done) sentinel
    else if (buffer.remaining() >= 4) Float.box(buffer.getFloat()).asInstanceOf[A1]
    else { done = true; sentinel }

  override def readN[A1 >: Float](n: Int): Chunk[A1] = {
    if (n <= 0 || done) return Chunk.empty
    val count = math.min(n, buffer.remaining() / 4)
    if (count == 0) { done = true; return Chunk.empty }
    val arr = new Array[Float](count)
    var i   = 0
    while (i < count) {
      arr(i) = buffer.getFloat()
      i += 1
    }
    if (buffer.remaining() < 4) done = true
    Chunk.fromArray(arr).asInstanceOf[Chunk[A1]]
  }

  override def readUpToN[A1 >: Float](n: Int): Chunk[A1] = {
    if (n <= 0) return Chunk.empty
    val b = new ChunkBuilder.Float(); b.sizeHint(math.min(n, 64))
    val s = Double.MaxValue
    var v = readFloat(s)(unsafeEvidence)
    if (v == s) return Chunk.empty
    var i = 0
    while (v != s && i < n) {
      b.addOne(v.toFloat); i += 1
      if (i < n) v = readFloat(s)(unsafeEvidence)
    }
    b.result().asInstanceOf[Chunk[A1]]
  }

  override def readable(): Boolean = !done && buffer.remaining() >= 4

  override def readByte(): Int =
    if (done) -1
    else if (buffer.remaining() >= 4) (buffer.getFloat().toInt & 0xff)
    else { done = true; -1 }

  override def readFloat(sentinel: Double)(implicit ev: Float <:< Float): Double =
    if (done) sentinel
    else if (buffer.remaining() >= 4) buffer.getFloat().toDouble
    else { done = true; sentinel }

  override def reset(): Unit = {
    done = false
    val clampedSkip = math.max(0L, skipN)
    val bytesSkip   = if (clampedSkip > Int.MaxValue / 4) Int.MaxValue else clampedSkip.toInt * 4
    val startPos    = math.min(originalPosition + bytesSkip, originalLimit)
    buffer.limit(originalLimit)
    buffer.position(startPos)
    if (limitN != Long.MaxValue) {
      val bytesN = if (limitN > Int.MaxValue / 4) Int.MaxValue else limitN.toInt * 4
      buffer.limit(math.min(originalLimit, startPos + bytesN))
    }
  }

  override def setLimit(n: Long): Boolean = {
    limitN = n
    val bytesN = if (n > Int.MaxValue / 4) Int.MaxValue else n.toInt * 4
    buffer.limit(math.min(originalLimit, buffer.position() + bytesN))
    true
  }

  override def setSkip(n: Long): Boolean = {
    skipN = n
    val clamped   = math.max(0L, n)
    val bytesSkip = if (clamped > Int.MaxValue / 4) Int.MaxValue else clamped.toInt * 4
    val newPos    = math.min(buffer.position() + bytesSkip, buffer.limit())
    buffer.position(newPos)
    true
  }

  override def skip(n: Long): Unit = {
    val s = math.min(n, (buffer.remaining() / 4).toLong).toInt
    buffer.position(buffer.position() + s * 4)
  }
}
