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

import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.ReadableByteChannel

/**
 * Reads bytes from a [[java.nio.channels.ReadableByteChannel]] through an
 * internal [[java.nio.ByteBuffer]]. Single-threaded; not safe for concurrent
 * use.
 */
private[streams] final class ChannelReader(ch: ReadableByteChannel, bufSize: Int) extends Reader[Byte] {

  private var st: Int = 0 // 0 = Open, 1 = Finished, 2 = Errored
  private val buf     = ByteBuffer.allocate(bufSize)

  // Start with buffer empty (position == limit) so first read triggers a fill.
  buf.flip()

  def close(): Unit = st = 1

  def isClosed: Boolean = st != 0

  override def jvmType: JvmType = JvmType.Byte

  def read[A1 >: Byte](sentinel: A1): A1 = {
    val b = readByte()
    if (b >= 0) Byte.box(b.toByte).asInstanceOf[A1] else sentinel
  }

  override def readable(): Boolean = buf.hasRemaining && st == 0

  override def readByte(): Int =
    if (st != 0) -1
    else if (buf.hasRemaining) (buf.get() & 0xff)
    else if (fill() && buf.hasRemaining) (buf.get() & 0xff)
    else -1

  override def readBytes(arr: Array[Byte], offset: Int, len: Int): Int =
    if (len == 0) 0
    else if (st != 0) -1
    else if (buf.hasRemaining) {
      val n = math.min(len, buf.remaining())
      buf.get(arr, offset, n)
      n
    } else if (fill() && buf.hasRemaining) {
      val n = math.min(len, buf.remaining())
      buf.get(arr, offset, n)
      n
    } else -1

  override def skip(n: Long): Unit = {
    var r = n; while (r > 0) { val b = readByte(); if (b < 0) r = 0 else r -= 1 }
  }

  /** Refill the internal buffer from the channel. Returns false if EOF. */
  private def fill(): Boolean =
    try {
      buf.compact()
      val n = ch.read(buf)
      buf.flip()
      if (n < 0) { st = 1; false }
      else buf.hasRemaining
    } catch {
      case e: IOException =>
        buf.flip()
        st = 2
        throw new StreamError(e)
    }
}
