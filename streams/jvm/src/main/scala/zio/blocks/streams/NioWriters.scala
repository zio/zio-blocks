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

import zio.blocks.streams.internal._
import zio.blocks.streams.io.Writer

import java.nio.ByteBuffer
import java.nio.channels.WritableByteChannel

/**
 * JVM-only factory methods for constructing [[Writer]] instances backed by NIO
 * [[ByteBuffer]]s and [[WritableByteChannel]]s.
 */
object NioWriters {

  /**
   * Wraps a `ByteBuffer` as a `Writer[Byte]`. Auto-closes when the buffer is
   * full.
   */
  def fromByteBuffer(buf: ByteBuffer): Writer[Byte] =
    new ByteBufferWriter(buf)

  /** Wraps a `ByteBuffer` as a `Writer[Double]` (8 bytes per element). */
  def fromByteBufferDouble(buf: ByteBuffer): Writer[Double] =
    new ByteBufferDoubleWriter(buf)

  /** Wraps a `ByteBuffer` as a `Writer[Float]` (4 bytes per element). */
  def fromByteBufferFloat(buf: ByteBuffer): Writer[Float] =
    new ByteBufferFloatWriter(buf)

  /** Wraps a `ByteBuffer` as a `Writer[Int]` (4 bytes per element). */
  def fromByteBufferInt(buf: ByteBuffer): Writer[Int] =
    new ByteBufferIntWriter(buf)

  /** Wraps a `ByteBuffer` as a `Writer[Long]` (8 bytes per element). */
  def fromByteBufferLong(buf: ByteBuffer): Writer[Long] =
    new ByteBufferLongWriter(buf)

  /**
   * Wraps a `WritableByteChannel` as a buffered `Writer[Byte]`.
   *
   * Closing the returned writer flushes the internal buffer and closes the
   * underlying channel. The writer takes ownership of `ch`.
   *
   * @param bufSize
   *   Internal write buffer size in bytes (default 8192).
   */
  def fromChannel(ch: WritableByteChannel, bufSize: Int = 8192): Writer[Byte] =
    new ChannelWriter(ch, bufSize)
}
