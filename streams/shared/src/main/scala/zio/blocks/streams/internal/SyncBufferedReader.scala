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

import zio.blocks.chunk.Chunk
import zio.blocks.streams.JvmType
import zio.blocks.streams.io.Reader

/**
 * A lazy, synchronous buffered reader used by platforms without true
 * concurrency.
 */
private[streams] final class SyncBufferedReader[A](upstream: Reader.SyncReader[A], bufferSize: Int)
    extends Reader.SyncReader[A] {
  override val jvmType: JvmType = upstream.jvmType

  private val refs      = if (jvmType eq JvmType.AnyRef) new Array[AnyRef](bufferSize) else null
  private val booleans  = if (jvmType eq JvmType.Boolean) new Array[Boolean](bufferSize) else null
  private val bytes     = if (jvmType eq JvmType.Byte) new Array[Byte](bufferSize) else null
  private val chars     = if (jvmType eq JvmType.Char) new Array[Char](bufferSize) else null
  private val shorts    = if (jvmType eq JvmType.Short) new Array[Short](bufferSize) else null
  private val ints      = if (jvmType eq JvmType.Int) new Array[Int](bufferSize) else null
  private val longs     = if (jvmType eq JvmType.Long) new Array[Long](bufferSize) else null
  private val floats    = if (jvmType eq JvmType.Float) new Array[Float](bufferSize) else null
  private val doubles   = if (jvmType eq JvmType.Double) new Array[Double](bufferSize) else null
  private val longOne   = new Array[Long](1)
  private val doubleOne = new Array[Double](1)
  private var pos       = 0
  private var limit     = 0
  private var done      = false

  def isClosed: Boolean                                          = pos >= limit && (done || upstream.isClosed)
  override def readable(): Boolean                               = pos < limit || (!done && upstream.readable())
  override private[streams] def tryReadable: Reader.Availability =
    if (pos < limit) Reader.Available else if (done) Reader.Unavailable else upstream.tryReadable

  private def fill(): Unit = {
    pos = 0
    limit = 0
    while (limit < bufferSize && !done) {
      val available = jvmType match {
        case JvmType.Boolean =>
          val v = upstream.readBooleanPhysical(-1); if (v < 0) false else { booleans(limit) = v != 0; true }
        case JvmType.Byte =>
          val v = upstream.readBytePhysical(); if (v < 0) false else { bytes(limit) = v.toByte; true }
        case JvmType.Char =>
          val v = upstream.readCharPhysical(-1); if (v < 0) false else { chars(limit) = v.toChar; true }
        case JvmType.Short =>
          val v = upstream.readShortPhysical(Int.MinValue)
          if (v == Int.MinValue) false else { shorts(limit) = v.toShort; true }
        case JvmType.Int =>
          val v = upstream.readIntPhysical(Long.MinValue)
          if (v == Long.MinValue) false else { ints(limit) = v.toInt; true }
        case JvmType.Long  => upstream.readLongsPhysical(longs, limit, 1) > 0
        case JvmType.Float =>
          val v = upstream.readFloatPhysical(Double.MaxValue)
          if (v == Double.MaxValue) false else { floats(limit) = v.toFloat; true }
        case JvmType.Double => upstream.readDoublesPhysical(doubles, limit, 1) > 0
        case JvmType.AnyRef =>
          val v = upstream.read[Any](EndOfStream)
          if (v.asInstanceOf[AnyRef] eq EndOfStream) false else { refs(limit) = v.asInstanceOf[AnyRef]; true }
      }
      if (available) limit += 1 else done = true
    }
  }

  private def ensure(): Boolean = { if (pos >= limit && !done) fill(); pos < limit }

  def read[A1 >: A](sentinel: A1): A1 =
    if (!ensure()) sentinel
    else {
      val i = pos; pos += 1
      (jvmType match {
        case JvmType.Boolean => Boolean.box(booleans(i))
        case JvmType.Byte    => Byte.box(bytes(i))
        case JvmType.Char    => Char.box(chars(i))
        case JvmType.Short   => Short.box(shorts(i))
        case JvmType.Int     => Int.box(ints(i))
        case JvmType.Long    => Long.box(longs(i))
        case JvmType.Float   => Float.box(floats(i))
        case JvmType.Double  => Double.box(doubles(i))
        case JvmType.AnyRef  => refs(i)
      }).asInstanceOf[A1]
    }

  override def readBoolean(sentinel: Int)(implicit ev: A <:< Boolean): Int =
    if (!ensure()) sentinel else { val v = booleans(pos); pos += 1; if (v) 1 else 0 }
  override def readByte(): Int                                       = if (!ensure()) -1 else { val v = bytes(pos); pos += 1; v.toInt & 0xff }
  override def readChar(sentinel: Int)(implicit ev: A <:< Char): Int =
    if (!ensure()) sentinel else { val v = chars(pos); pos += 1; v.toInt }
  override def readShort(sentinel: Int)(implicit ev: A <:< Short): Int =
    if (!ensure()) sentinel else { val v = shorts(pos); pos += 1; v.toInt }
  override def readInt(sentinel: Long)(implicit ev: A <:< Int): Long =
    if (!ensure()) sentinel else { val v = ints(pos); pos += 1; v.toLong }
  override def readLong(sentinel: Long)(implicit ev: A <:< Long): Long =
    if (readLongs(longOne, 0, 1) < 0) sentinel else longOne(0)
  override def readFloat(sentinel: Double)(implicit ev: A <:< Float): Double =
    if (!ensure()) sentinel else { val v = floats(pos); pos += 1; v.toDouble }
  override def readDouble(sentinel: Double)(implicit ev: A <:< Double): Double =
    if (readDoubles(doubleOne, 0, 1) < 0) sentinel else doubleOne(0)

  override def readBytes(dest: Array[Byte], offset: Int, length: Int)(implicit ev: A <:< Byte): Int =
    copy(dest, offset, length, bytes)
  override def readInts(dest: Array[Int], offset: Int, length: Int)(implicit ev: A <:< Int): Int =
    copy(dest, offset, length, ints)
  override def readLongs(dest: Array[Long], offset: Int, length: Int)(implicit ev: A <:< Long): Int =
    copy(dest, offset, length, longs)
  override def readFloats(dest: Array[Float], offset: Int, length: Int)(implicit ev: A <:< Float): Int =
    copy(dest, offset, length, floats)
  override def readDoubles(dest: Array[Double], offset: Int, length: Int)(implicit ev: A <:< Double): Int =
    copy(dest, offset, length, doubles)

  private def copy[T](dest: Array[T], offset: Int, length: Int, source: Array[T]): Int = {
    Reader.validateArrayRange(dest, offset, length)
    if (length == 0) 0
    else if (!ensure()) -1
    else {
      val count = math.min(length, limit - pos)
      Array.copy(source, pos, dest, offset, count)
      pos += count
      count
    }
  }

  override def readUpToN[A1 >: A](n: Int): Chunk[A1] = super.readUpToN(n)
  def close(): Unit                                  = { done = true; upstream.close() }
}
