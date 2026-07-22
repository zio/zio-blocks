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
  // The composed pushdown window, stored as DERIVED buffer positions: each
  // setSkip/setLimit composes against the live buffer state and snapshots the
  // result, so reset() restores the same composed window. Storing raw
  // skip/limit values and recomputing [skip, skip+limit) on reset both leaks
  // elements outside the window and re-emits dropped ones on replay
  // (BUG-R8-03). Negative windows clamp to empty like the base setters
  // (BUG-R8-02).
  private var windowStart: Int = originalPosition
  private var windowEnd: Int   = originalLimit
  private var done: Boolean    = false

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
    if (n <= 0 || done) return Chunk.empty
    // Bound the allocation by what is actually available so a huge `n`
    // (e.g. Int.MaxValue) over a small buffer cannot pre-allocate a multi-GB
    // array and OOM — mirroring `readN` here and the bounded sizing used by the
    // Int/Long/Double/Float ByteBuffer readers and the base `Reader.readUpToN`.
    val count = math.min(n, buffer.remaining())
    if (count <= 0) { done = true; return Chunk.empty }
    val arr  = new Array[Byte](count)
    val read = readBytes(arr, 0, count)(unsafeEvidence)
    if (read <= 0) Chunk.empty
    else if (read == count) Chunk.fromArray(arr).asInstanceOf[Chunk[A1]]
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
    buffer.limit(windowEnd)
    buffer.position(windowStart)
  }

  override def setLimit(n: Long): Boolean = {
    val clamped = math.max(0L, n)
    // Cap at the CURRENT live limit, not `originalLimit`: window ops compose
    // over the live window, so a later, larger take must not re-expand an
    // already-narrowed one (BUG-R9-01; List oracle take(2).take(5) == take(2)).
    val newLimit = math.min(buffer.limit().toLong, buffer.position().toLong + clamped).toInt
    buffer.limit(newLimit)
    windowEnd = newLimit
    true
  }

  override def setSkip(n: Long): Boolean = {
    val clamped = math.max(0L, n)
    val newPos  = math.min(buffer.position().toLong + clamped, buffer.limit().toLong).toInt
    buffer.position(newPos)
    windowStart = newPos
    true
  }

  override def skip(n: Long): Unit = {
    if (n <= 0) return
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
  // The composed pushdown window, stored as DERIVED buffer positions: each
  // setSkip/setLimit composes against the live buffer state and snapshots the
  // result, so reset() restores the same composed window. Storing raw
  // skip/limit values and recomputing [skip, skip+limit) on reset both leaks
  // elements outside the window and re-emits dropped ones on replay
  // (BUG-R8-03). Negative windows clamp to empty like the base setters
  // (BUG-R8-02).
  private var windowStart: Int = originalPosition
  private var windowEnd: Int   = originalLimit
  private var done: Boolean    = false

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
    buffer.limit(windowEnd)
    buffer.position(windowStart)
  }

  override def setLimit(n: Long): Boolean = {
    val clamped = math.max(0L, n)
    val bytesN  = if (clamped > Int.MaxValue / 4) Int.MaxValue.toLong else clamped * 4
    // Cap at the CURRENT live limit, not `originalLimit` (BUG-R9-01): window
    // ops compose over the live window.
    val newLimit = math.min(buffer.limit().toLong, buffer.position().toLong + bytesN).toInt
    buffer.limit(newLimit)
    windowEnd = newLimit
    true
  }

  override def setSkip(n: Long): Boolean = {
    val clamped   = math.max(0L, n)
    val bytesSkip = if (clamped > Int.MaxValue / 4) Int.MaxValue.toLong else clamped * 4
    val newPos    = math.min(buffer.position().toLong + bytesSkip, buffer.limit().toLong).toInt
    buffer.position(newPos)
    windowStart = newPos
    true
  }

  override def skip(n: Long): Unit = {
    if (n <= 0) return
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
  // The composed pushdown window, stored as DERIVED buffer positions: each
  // setSkip/setLimit composes against the live buffer state and snapshots the
  // result, so reset() restores the same composed window. Storing raw
  // skip/limit values and recomputing [skip, skip+limit) on reset both leaks
  // elements outside the window and re-emits dropped ones on replay
  // (BUG-R8-03). Negative windows clamp to empty like the base setters
  // (BUG-R8-02).
  private var windowStart: Int = originalPosition
  private var windowEnd: Int   = originalLimit
  private var done: Boolean    = false

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
    // Sentinel performance policy (AGENTS.md): the hot path stays a single
    // primitive comparison; the out-of-band EOF flag is consulted only on the
    // rare value/sentinel collision (short-circuit), keeping a real
    // Long.MaxValue element lossless at zero cost.
    var v = readLong(s)(unsafeEvidence)
    if (v == s && lastReadWasEOF) return Chunk.empty
    var i = 0
    while (!(v == s && lastReadWasEOF) && i < n) {
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
    if (done) { markReadEOF(); sentinel }
    else if (buffer.remaining() >= 8) { markReadValue(); buffer.getLong() }
    else { done = true; markReadEOF(); sentinel }

  override def reset(): Unit = {
    done = false
    buffer.limit(windowEnd)
    buffer.position(windowStart)
  }

  override def setLimit(n: Long): Boolean = {
    val clamped = math.max(0L, n)
    val bytesN  = if (clamped > Int.MaxValue / 8) Int.MaxValue.toLong else clamped * 8
    // Cap at the CURRENT live limit, not `originalLimit` (BUG-R9-01): window
    // ops compose over the live window.
    val newLimit = math.min(buffer.limit().toLong, buffer.position().toLong + bytesN).toInt
    buffer.limit(newLimit)
    windowEnd = newLimit
    true
  }

  override def setSkip(n: Long): Boolean = {
    val clamped   = math.max(0L, n)
    val bytesSkip = if (clamped > Int.MaxValue / 8) Int.MaxValue.toLong else clamped * 8
    val newPos    = math.min(buffer.position().toLong + bytesSkip, buffer.limit().toLong).toInt
    buffer.position(newPos)
    windowStart = newPos
    true
  }

  override def skip(n: Long): Unit = {
    if (n <= 0) return
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
  // The composed pushdown window, stored as DERIVED buffer positions: each
  // setSkip/setLimit composes against the live buffer state and snapshots the
  // result, so reset() restores the same composed window. Storing raw
  // skip/limit values and recomputing [skip, skip+limit) on reset both leaks
  // elements outside the window and re-emits dropped ones on replay
  // (BUG-R8-03). Negative windows clamp to empty like the base setters
  // (BUG-R8-02).
  private var windowStart: Int = originalPosition
  private var windowEnd: Int   = originalLimit
  private var done: Boolean    = false

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
    // Sentinel performance policy (AGENTS.md): the hot path stays a single
    // primitive comparison; the out-of-band EOF flag is consulted only on the
    // rare value/sentinel collision (short-circuit). The sentinel here is
    // statically Double.MaxValue (never NaN), so `doubleEOF`'s per-element
    // rawbits comparison is unnecessary — do not reintroduce it.
    var v = readDouble(s)(unsafeEvidence)
    if (v == s && lastReadWasEOF) return Chunk.empty
    var i = 0
    while (!(v == s && lastReadWasEOF) && i < n) {
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
    if (done) { markReadEOF(); sentinel }
    else if (buffer.remaining() >= 8) { markReadValue(); buffer.getDouble() }
    else { done = true; markReadEOF(); sentinel }

  override def reset(): Unit = {
    done = false
    buffer.limit(windowEnd)
    buffer.position(windowStart)
  }

  override def setLimit(n: Long): Boolean = {
    val clamped = math.max(0L, n)
    val bytesN  = if (clamped > Int.MaxValue / 8) Int.MaxValue.toLong else clamped * 8
    // Cap at the CURRENT live limit, not `originalLimit` (BUG-R9-01): window
    // ops compose over the live window.
    val newLimit = math.min(buffer.limit().toLong, buffer.position().toLong + bytesN).toInt
    buffer.limit(newLimit)
    windowEnd = newLimit
    true
  }

  override def setSkip(n: Long): Boolean = {
    val clamped   = math.max(0L, n)
    val bytesSkip = if (clamped > Int.MaxValue / 8) Int.MaxValue.toLong else clamped * 8
    val newPos    = math.min(buffer.position().toLong + bytesSkip, buffer.limit().toLong).toInt
    buffer.position(newPos)
    windowStart = newPos
    true
  }

  override def skip(n: Long): Unit = {
    if (n <= 0) return
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
  // The composed pushdown window, stored as DERIVED buffer positions: each
  // setSkip/setLimit composes against the live buffer state and snapshots the
  // result, so reset() restores the same composed window. Storing raw
  // skip/limit values and recomputing [skip, skip+limit) on reset both leaks
  // elements outside the window and re-emits dropped ones on replay
  // (BUG-R8-03). Negative windows clamp to empty like the base setters
  // (BUG-R8-02).
  private var windowStart: Int = originalPosition
  private var windowEnd: Int   = originalLimit
  private var done: Boolean    = false

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
    buffer.limit(windowEnd)
    buffer.position(windowStart)
  }

  override def setLimit(n: Long): Boolean = {
    val clamped = math.max(0L, n)
    val bytesN  = if (clamped > Int.MaxValue / 4) Int.MaxValue.toLong else clamped * 4
    // Cap at the CURRENT live limit, not `originalLimit` (BUG-R9-01): window
    // ops compose over the live window.
    val newLimit = math.min(buffer.limit().toLong, buffer.position().toLong + bytesN).toInt
    buffer.limit(newLimit)
    windowEnd = newLimit
    true
  }

  override def setSkip(n: Long): Boolean = {
    val clamped   = math.max(0L, n)
    val bytesSkip = if (clamped > Int.MaxValue / 4) Int.MaxValue.toLong else clamped * 4
    val newPos    = math.min(buffer.position().toLong + bytesSkip, buffer.limit().toLong).toInt
    buffer.position(newPos)
    windowStart = newPos
    true
  }

  override def skip(n: Long): Unit = {
    if (n <= 0) return
    val s = math.min(n, (buffer.remaining() / 4).toLong).toInt
    buffer.position(buffer.position() + s * 4)
  }
}
