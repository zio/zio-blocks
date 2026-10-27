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
 * Writers into `java.nio` targets (JVM only): a `ByteBuffer` written as bytes
 * or as `Int`, `Long`, `Float` or `Double` values, and a blocking
 * `WritableByteChannel`.
 *
 * The buffer writers write at the buffer's position and advance it, encoding
 * multi-byte values in the buffer's byte order. A `write` that finds no room
 * for its value returns `false` and closes the writer, so every later write
 * returns `false` too; `close()` leaves the buffer as it is.
 */
object NioWriters {

  /**
   * Returns a writer of bytes into `buf`. The first write that finds `buf` full
   * returns `false` and closes the writer.
   */
  def fromByteBuffer(buf: ByteBuffer): Writer[Byte] =
    new ByteBufferWriter(buf)

  /**
   * Returns a writer of `Double`s into `buf`, 8 bytes each. The first write
   * that finds fewer than 8 bytes left returns `false` and closes the writer.
   */
  def fromByteBufferDouble(buf: ByteBuffer): Writer[Double] =
    new ByteBufferDoubleWriter(buf)

  /**
   * Returns a writer of `Float`s into `buf`, 4 bytes each. The first write that
   * finds fewer than 4 bytes left returns `false` and closes the writer.
   */
  def fromByteBufferFloat(buf: ByteBuffer): Writer[Float] =
    new ByteBufferFloatWriter(buf)

  /**
   * Returns a writer of `Int`s into `buf`, 4 bytes each. The first write that
   * finds fewer than 4 bytes left returns `false` and closes the writer.
   */
  def fromByteBufferInt(buf: ByteBuffer): Writer[Int] =
    new ByteBufferIntWriter(buf)

  /**
   * Returns a writer of `Long`s into `buf`, 8 bytes each. The first write that
   * finds fewer than 8 bytes left returns `false` and closes the writer.
   */
  def fromByteBufferLong(buf: ByteBuffer): Writer[Long] =
    new ByteBufferLongWriter(buf)

  /**
   * Returns a writer of bytes into `ch`, through a buffer of `bufSize` bytes.
   * Bytes reach `ch` when a write finds the buffer full and on `close()`, which
   * writes what is left and closes `ch`; the writer owns `ch`. After `close()`,
   * `write` returns `false` and `writeBytes` returns 0.
   *
   * An `IOException` from `ch` is not thrown. Inside `close()`, it is ignored
   * and `ch` is still closed. When a write has to flush the buffer and the
   * flush fails, the write returns `false` (`writeBytes` returns the bytes it
   * took so far) and the writer reports closed. A known bug: `close()` then
   * does nothing, so `ch` is never closed and the bytes left in the buffer are
   * lost; close `ch` yourself after a failed write.
   */
  def fromChannel(ch: WritableByteChannel, bufSize: Int = 8192): Writer[Byte] =
    new ChannelWriter(ch, bufSize)
}
