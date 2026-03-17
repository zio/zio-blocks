package zio.blocks.streams

import zio.blocks.streams.internal.{StreamError, unsafeEvidence}
import zio.blocks.streams.io.Reader

import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.WritableByteChannel

/**
 * JVM-only convenience constructors for creating [[Sink]] instances that write
 * to NIO [[ByteBuffer]]s and [[WritableByteChannel]]s.
 *
 * Each method creates a sink that drains the stream into the corresponding NIO
 * target.
 */
object NioSinks {

  /**
   * Creates a sink that writes all stream bytes into a [[ByteBuffer]]. Throws
   * `BufferOverflowException` if the buffer has insufficient remaining
   * capacity.
   */
  def fromByteBuffer(buf: ByteBuffer): Sink[Nothing, Byte, Unit] =
    new Sink[Nothing, Byte, Unit] {
      private[streams] def drain(reader: Reader[?]): Unit = {
        var b = reader.readByte()
        while (b >= 0) { buf.put(b.toByte); b = reader.readByte() }
      }
    }

  /**
   * Creates a sink that writes all stream Doubles into a [[ByteBuffer]] (8
   * bytes per element). Throws `BufferOverflowException` if the buffer has
   * insufficient remaining capacity.
   */
  def fromByteBufferDouble(buf: ByteBuffer): Sink[Nothing, Double, Unit] =
    new Sink[Nothing, Double, Unit] {
      private[streams] def drain(reader: Reader[?]): Unit = {
        val s = Double.MaxValue
        var v = reader.readDouble(s)(using unsafeEvidence)
        while (v != s) { buf.putDouble(v); v = reader.readDouble(s)(using unsafeEvidence) }
      }
    }

  /**
   * Creates a sink that writes all stream Floats into a [[ByteBuffer]] (4 bytes
   * per element). Throws `BufferOverflowException` if the buffer has
   * insufficient remaining capacity.
   */
  def fromByteBufferFloat(buf: ByteBuffer): Sink[Nothing, Float, Unit] =
    new Sink[Nothing, Float, Unit] {
      private[streams] def drain(reader: Reader[?]): Unit = {
        val s = Double.MaxValue
        var v = reader.readFloat(s)(using unsafeEvidence)
        while (v != s) { buf.putFloat(v.toFloat); v = reader.readFloat(s)(using unsafeEvidence) }
      }
    }

  /**
   * Creates a sink that writes all stream Ints into a [[ByteBuffer]] (4 bytes
   * per element). Throws `BufferOverflowException` if the buffer has
   * insufficient remaining capacity.
   */
  def fromByteBufferInt(buf: ByteBuffer): Sink[Nothing, Int, Unit] =
    new Sink[Nothing, Int, Unit] {
      private[streams] def drain(reader: Reader[?]): Unit = {
        val s = Long.MinValue
        var v = reader.readInt(s)(using unsafeEvidence)
        while (v != s) { buf.putInt(v.toInt); v = reader.readInt(s)(using unsafeEvidence) }
      }
    }

  /**
   * Creates a sink that writes all stream Longs into a [[ByteBuffer]] (8 bytes
   * per element). Throws `BufferOverflowException` if the buffer has
   * insufficient remaining capacity.
   */
  def fromByteBufferLong(buf: ByteBuffer): Sink[Nothing, Long, Unit] =
    new Sink[Nothing, Long, Unit] {
      private[streams] def drain(reader: Reader[?]): Unit = {
        val s = Long.MaxValue
        var v = reader.readLong(s)(using unsafeEvidence)
        while (v != s) { buf.putLong(v); v = reader.readLong(s)(using unsafeEvidence) }
      }
    }

  /**
   * A sink that writes all stream bytes to a
   * [[java.nio.channels.WritableByteChannel]] with internal buffering. Does not
   * close the channel.
   *
   * @param ch
   *   The channel to write to.
   * @param bufSize
   *   Internal buffer size in bytes (default 8192).
   */
  def fromChannel(ch: WritableByteChannel, bufSize: Int = 8192): Sink[IOException, Byte, Unit] =
    new Sink[IOException, Byte, Unit] {
      private[streams] def drain(reader: Reader[?]): Unit = {
        val buf = ByteBuffer.allocate(bufSize)
        var b   = reader.readByte()
        while (b >= 0) {
          if (!buf.hasRemaining) {
            buf.flip()
            try { while (buf.hasRemaining) ch.write(buf) }
            catch { case e: IOException => throw new StreamError(e) }
            buf.compact()
          }
          buf.put(b.toByte)
          b = reader.readByte()
        }
        buf.flip()
        try { while (buf.hasRemaining) ch.write(buf) }
        catch { case e: IOException => throw new StreamError(e) }
      }
    }
}
