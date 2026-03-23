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

package zio.blocks.streams

import java.nio.ByteBuffer
import java.nio.channels.ReadableByteChannel

/**
 * JVM-only convenience constructors for creating [[Stream]] instances backed by
 * NIO [[ByteBuffer]]s and [[ReadableByteChannel]]s.
 *
 * Each method wraps an NIO source as a Stream.
 */
object NioStreams {

  /** Creates a stream of bytes from a [[ByteBuffer]]. */
  def fromByteBuffer(buf: ByteBuffer): Stream[Nothing, Byte] =
    Stream.fromReader(NioReaders.fromByteBuffer(buf))

  /**
   * Creates a stream of Doubles from a [[ByteBuffer]] (8 bytes per element).
   */
  def fromByteBufferDouble(buf: ByteBuffer): Stream[Nothing, Double] =
    Stream.fromReader(NioReaders.fromByteBufferDouble(buf))

  /** Creates a stream of Floats from a [[ByteBuffer]] (4 bytes per element). */
  def fromByteBufferFloat(buf: ByteBuffer): Stream[Nothing, Float] =
    Stream.fromReader(NioReaders.fromByteBufferFloat(buf))

  /** Creates a stream of Ints from a [[ByteBuffer]] (4 bytes per element). */
  def fromByteBufferInt(buf: ByteBuffer): Stream[Nothing, Int] =
    Stream.fromReader(NioReaders.fromByteBufferInt(buf))

  /** Creates a stream of Longs from a [[ByteBuffer]] (8 bytes per element). */
  def fromByteBufferLong(buf: ByteBuffer): Stream[Nothing, Long] =
    Stream.fromReader(NioReaders.fromByteBufferLong(buf))

  /**
   * Creates a buffered stream of bytes from a
   * [[java.nio.channels.ReadableByteChannel]]. Closes the channel when done.
   *
   * @param ch
   *   The channel to read from.
   * @param bufSize
   *   Internal buffer size in bytes (default 8192).
   */
  def fromChannel(ch: ReadableByteChannel, bufSize: Int = 8192): Stream[java.io.IOException, Byte] =
    Stream.fromAcquireRelease(
      ch,
      (c: ReadableByteChannel) =>
        try c.close()
        catch { case _: java.io.IOException => () }
    )(c => fromChannelUnmanaged(c, bufSize))

  /**
   * Creates a buffered stream of bytes from a
   * [[java.nio.channels.ReadableByteChannel]] without managing its lifecycle.
   * The caller is responsible for closing the channel.
   *
   * @param ch
   *   The channel to read from.
   * @param bufSize
   *   Internal buffer size in bytes (default 8192).
   */
  def fromChannelUnmanaged(ch: ReadableByteChannel, bufSize: Int = 8192): Stream[java.io.IOException, Byte] =
    Stream.fromReader(NioReaders.fromChannel(ch, bufSize))
}
