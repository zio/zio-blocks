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

import zio.blocks.streams.io.Writer

import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.WritableByteChannel

/**
 * Writer adapter that writes bytes to a `WritableByteChannel` with internal
 * buffering.
 */
private[streams] final class ChannelWriter(ch: WritableByteChannel, bufSize: Int) extends Writer[Byte] {

  // `failed` rejects further writes after an absorbed mid-write flush
  // IOException; `closed` records that the channel was finalized. They are
  // separate so a write failure cannot turn `close()` into a no-op and leak
  // the channel (it must be closed by some API path exactly once).
  private var closed = false
  private var failed = false
  private val buf    = ByteBuffer.allocate(bufSize)

  def close(): Unit =
    if (!closed) {
      closed = true
      // Surface I/O failures from the final flush/close rather than swallowing
      // them, and always close the channel even if the flush fails (Principle
      // 4) — mirroring `Writer.OutputStreamWriter.close()`.
      runBoth {
        buf.flip()
        while (buf.hasRemaining) ch.write(buf)
      }(ch.close())
    }

  def isClosed: Boolean = closed || failed

  def write(a: Byte): Boolean = writeByte(a)

  override def writeByte(b: Byte)(implicit ev: Byte <:< Byte): Boolean = {
    if (closed || failed) return false
    if (!buf.hasRemaining) { if (!flush()) return false }
    buf.put(b)
    true
  }

  override def writeBytes(arr: Array[Byte], offset: Int, len: Int)(implicit ev: Byte <:< Byte): Int = {
    if (closed || failed) return 0
    if (len == 0) return 0
    var written = 0
    var off     = offset
    var rem     = len
    while (rem > 0) {
      val space = buf.remaining()
      if (space <= 0) { if (!flush()) return written }
      val n = math.min(rem, buf.remaining())
      buf.put(arr, off, n)
      off += n
      rem -= n
      written += n
    }
    written
  }

  private def flush(): Boolean =
    try {
      buf.flip()
      while (buf.hasRemaining) ch.write(buf)
      buf.compact()
      true
    } catch {
      case _: IOException =>
        buf.compact()
        failed = true
        false
    }
}
