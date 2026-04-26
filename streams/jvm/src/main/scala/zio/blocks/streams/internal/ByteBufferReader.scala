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

  override def readable(): Boolean = !done && buffer.hasRemaining

  override def readByte(): Int =
    if (done) -1
    else if (buffer.hasRemaining) (buffer.get() & 0xff)
    else { done = true; -1 }

  override def readBytes(buf: Array[Byte], offset: Int, len: Int): Int =
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

  override def readable(): Boolean = !done && buffer.remaining() >= 4

  override def readByte(): Int =
    if (done) -1
    else if (buffer.remaining() >= 4) (buffer.getInt() & 0xff)
    else { done = true; -1 }

  override def readInt(sentinel: Long)(using Int <:< Int): Long =
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

  override def readable(): Boolean = !done && buffer.remaining() >= 8

  override def readByte(): Int =
    if (done) -1
    else if (buffer.remaining() >= 8) (buffer.getLong().toInt & 0xff)
    else { done = true; -1 }

  override def readLong(sentinel: Long)(using Long <:< Long): Long =
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

  override def readable(): Boolean = !done && buffer.remaining() >= 8

  override def readByte(): Int =
    if (done) -1
    else if (buffer.remaining() >= 8) (buffer.getDouble().toInt & 0xff)
    else { done = true; -1 }

  override def readDouble(sentinel: Double)(using Double <:< Double): Double =
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

  override def readable(): Boolean = !done && buffer.remaining() >= 4

  override def readByte(): Int =
    if (done) -1
    else if (buffer.remaining() >= 4) (buffer.getFloat().toInt & 0xff)
    else { done = true; -1 }

  override def readFloat(sentinel: Double)(using Float <:< Float): Double =
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
