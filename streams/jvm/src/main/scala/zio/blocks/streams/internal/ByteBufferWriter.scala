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
import zio.blocks.streams.io.Writer

import java.nio.ByteBuffer

/**
 * Writer that writes bytes to a `ByteBuffer`; auto-closes when buffer is full.
 */
private[streams] final class ByteBufferWriter(buffer: ByteBuffer) extends Writer[Byte] {

  private var closed = false

  def close(): Unit = closed = true

  def isClosed: Boolean = closed

  def write(a: Byte): Boolean = writeByte(a)

  override def writeByte(b: Byte)(using Byte <:< Byte): Boolean = {
    if (closed) return false
    if (buffer.hasRemaining) { buffer.put(b); true }
    else { closed = true; false }
  }

  override def writeBytes(buf: Array[Byte], offset: Int, len: Int)(using Byte <:< Byte): Int = {
    if (closed) return 0
    if (len == 0) return 0
    val rem = buffer.remaining()
    if (rem <= 0) { closed = true; return 0 }
    val n = math.min(len, rem)
    buffer.put(buf, offset, n)
    n
  }
}

/**
 * Writer that writes Ints to a `ByteBuffer` (4 bytes each); auto-closes when
 * full.
 */
private[streams] final class ByteBufferIntWriter(buffer: ByteBuffer) extends Writer[Int] {

  private var closed = false

  def close(): Unit = closed = true

  def isClosed: Boolean = closed

  override def jvmType: JvmType = JvmType.Int

  def write(a: Int): Boolean = writeInt(a)

  override def writeInt(value: Int)(using Int <:< Int): Boolean = {
    if (closed) return false
    if (buffer.remaining() >= 4) { buffer.putInt(value); true }
    else { closed = true; false }
  }
}

/**
 * Writer that writes Longs to a `ByteBuffer` (8 bytes each); auto-closes when
 * full.
 */
private[streams] final class ByteBufferLongWriter(buffer: ByteBuffer) extends Writer[Long] {

  private var closed = false

  def close(): Unit = closed = true

  def isClosed: Boolean = closed

  override def jvmType: JvmType = JvmType.Long

  def write(a: Long): Boolean = writeLong(a)

  override def writeLong(value: Long)(using Long <:< Long): Boolean = {
    if (closed) return false
    if (buffer.remaining() >= 8) { buffer.putLong(value); true }
    else { closed = true; false }
  }
}

/**
 * Writer that writes Doubles to a `ByteBuffer` (8 bytes each); auto-closes when
 * full.
 */
private[streams] final class ByteBufferDoubleWriter(buffer: ByteBuffer) extends Writer[Double] {

  private var closed = false

  def close(): Unit = closed = true

  def isClosed: Boolean = closed

  override def jvmType: JvmType = JvmType.Double

  def write(a: Double): Boolean = writeDouble(a)

  override def writeDouble(value: Double)(using Double <:< Double): Boolean = {
    if (closed) return false
    if (buffer.remaining() >= 8) { buffer.putDouble(value); true }
    else { closed = true; false }
  }
}

/**
 * Writer that writes Floats to a `ByteBuffer` (4 bytes each); auto-closes when
 * full.
 */
private[streams] final class ByteBufferFloatWriter(buffer: ByteBuffer) extends Writer[Float] {

  private var closed = false

  def close(): Unit = closed = true

  def isClosed: Boolean = closed

  override def jvmType: JvmType = JvmType.Float

  def write(a: Float): Boolean = writeFloat(a)

  override def writeFloat(value: Float)(using Float <:< Float): Boolean = {
    if (closed) return false
    if (buffer.remaining() >= 4) { buffer.putFloat(value); true }
    else { closed = true; false }
  }
}
