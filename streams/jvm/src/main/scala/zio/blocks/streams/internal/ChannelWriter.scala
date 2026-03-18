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

  private var closed = false
  private val buf    = ByteBuffer.allocate(bufSize)

  def close(): Unit =
    if (!closed) {
      closed = true
      try {
        buf.flip()
        while (buf.hasRemaining) ch.write(buf)
      } catch { case _: IOException => () }
      finally {
        try ch.close()
        catch { case _: IOException => () }
      }
    }

  def isClosed: Boolean = closed

  def write(a: Byte): Boolean = writeByte(a)

  override def writeByte(b: Byte)(using Byte <:< Byte): Boolean = {
    if (closed) return false
    if (!buf.hasRemaining) { if (!flush()) return false }
    buf.put(b)
    true
  }

  override def writeBytes(arr: Array[Byte], offset: Int, len: Int)(using Byte <:< Byte): Int = {
    if (closed) return 0
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
        closed = true
        false
    }
}
