package zio.blocks.streams

import zio.blocks.streams.internal._
import zio.blocks.streams.io.Reader

import java.nio.ByteBuffer
import java.nio.channels.ReadableByteChannel

/**
 * JVM-only factory methods for constructing [[Reader]] instances backed by NIO
 * [[ByteBuffer]]s and [[ReadableByteChannel]]s.
 */
object NioReaders {

  /**
   * Wraps a `ByteBuffer` as a `Reader[Byte]`. Reads from the buffer's current
   * position to its limit. Supports `reset()` to rewind to the original
   * position.
   */
  def fromByteBuffer(buf: ByteBuffer): Reader[Byte] =
    new ByteBufferReader(buf)

  /** Wraps a `ByteBuffer` as a `Reader[Double]` (8 bytes per element). */
  def fromByteBufferDouble(buf: ByteBuffer): Reader[Double] =
    new ByteBufferDoubleReader(buf)

  /** Wraps a `ByteBuffer` as a `Reader[Float]` (4 bytes per element). */
  def fromByteBufferFloat(buf: ByteBuffer): Reader[Float] =
    new ByteBufferFloatReader(buf)

  /** Wraps a `ByteBuffer` as a `Reader[Int]` (4 bytes per element). */
  def fromByteBufferInt(buf: ByteBuffer): Reader[Int] =
    new ByteBufferIntReader(buf)

  /** Wraps a `ByteBuffer` as a `Reader[Long]` (8 bytes per element). */
  def fromByteBufferLong(buf: ByteBuffer): Reader[Long] =
    new ByteBufferLongReader(buf)

  /**
   * Wraps a `ReadableByteChannel` as a buffered `Reader[Byte]`.
   *
   * @param bufSize
   *   Internal read buffer size in bytes (default 8192).
   */
  def fromChannel(ch: ReadableByteChannel, bufSize: Int = 8192): Reader[Byte] =
    new ChannelReader(ch, bufSize)
}
