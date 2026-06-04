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

  override def readBytes(arr: Array[Byte], offset: Int, len: Int)(implicit ev: Byte <:< Byte): Int =
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

  override def readN[A1 >: Byte](n: Int): Chunk[A1] = {
    if (n <= 0 || isClosed) return Chunk.empty
    if (n <= 8192) {
      val arr     = new Array[Byte](n)
      var total   = 0
      var stopped = false
      while (total < n && !isClosed && !stopped) {
        val read = readBytes(arr, total, n - total)
        if (read > 0) total += read
        else stopped = true // EOF, error, or non-blocking channel with no available data
      }
      if (total == 0) Chunk.empty
      else if (total == n) Chunk.fromArray(arr).asInstanceOf[Chunk[A1]]
      else Chunk.fromArray(java.util.Arrays.copyOf(arr, total)).asInstanceOf[Chunk[A1]]
    } else {
      val b       = new ChunkBuilder.Byte()
      val buf     = new Array[Byte](8192)
      var rem     = n
      var stopped = false
      while (rem > 0 && !isClosed && !stopped) {
        val read = readBytes(buf, 0, math.min(rem, 8192))
        if (read > 0) { var k = 0; while (k < read) { b.addOne(buf(k)); k += 1 }; rem -= read }
        else stopped = true
      }
      b.result().asInstanceOf[Chunk[A1]]
    }
  }

  override def readUpToN[A1 >: Byte](n: Int): Chunk[A1] = {
    if (n <= 0 || isClosed) return Chunk.empty
    val arr  = new Array[Byte](n)
    val read = readBytes(arr, 0, n)(unsafeEvidence)
    if (read <= 0) Chunk.empty
    else if (read == n) Chunk.fromArray(arr).asInstanceOf[Chunk[A1]]
    else Chunk.fromArray(java.util.Arrays.copyOf(arr, read)).asInstanceOf[Chunk[A1]]
  }

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
