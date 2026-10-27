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
import zio.blocks.streams.io.Reader

import java.nio.ByteBuffer
import java.nio.channels.ReadableByteChannel

/**
 * Synchronous readers over `java.nio` sources (JVM only): a `ByteBuffer` read
 * as bytes or as `Int`, `Long`, `Float` or `Double` values, and a blocking
 * `ReadableByteChannel`. For channels that read asynchronously, use
 * [[AsyncNioReaders]].
 *
 * The buffer readers do not copy the buffer: each pull advances its position,
 * reading from the position it had when the reader was made up to its limit,
 * and the multi-byte readers decode values in the buffer's byte order.
 * `reset()` restores the original position and limit, and `close()` moves the
 * position to the limit.
 */
object NioReaders {

  /**
   * Returns a reader of the bytes from `buf`'s position to its limit. Each pull
   * advances `buf`'s position, and `reset()` rewinds it to where it started.
   */
  def fromByteBuffer(buf: ByteBuffer): Reader.SyncReader[Byte] =
    new ByteBufferReader(buf)

  /**
   * Returns a reader of the `Double`s in `buf`, 8 bytes each, in `buf`'s byte
   * order; fewer than 8 bytes left over at the end are not read.
   */
  def fromByteBufferDouble(buf: ByteBuffer): Reader.SyncReader[Double] =
    new ByteBufferDoubleReader(buf)

  /**
   * Returns a reader of the `Float`s in `buf`, 4 bytes each, in `buf`'s byte
   * order; fewer than 4 bytes left over at the end are not read.
   */
  def fromByteBufferFloat(buf: ByteBuffer): Reader.SyncReader[Float] =
    new ByteBufferFloatReader(buf)

  /**
   * Returns a reader of the `Int`s in `buf`, 4 bytes each, in `buf`'s byte
   * order; fewer than 4 bytes left over at the end are not read.
   */
  def fromByteBufferInt(buf: ByteBuffer): Reader.SyncReader[Int] =
    new ByteBufferIntReader(buf)

  /**
   * Returns a reader of the `Long`s in `buf`, 8 bytes each, in `buf`'s byte
   * order; fewer than 8 bytes left over at the end are not read.
   */
  def fromByteBufferLong(buf: ByteBuffer): Reader.SyncReader[Long] =
    new ByteBufferLongReader(buf)

  /**
   * Returns a reader of the bytes of `ch`, read through a buffer of `bufSize`
   * bytes. The reader owns `ch`: `close()` closes it once, and a failure to
   * close it is thrown by that `close()` and by every later one. Reaching the
   * end of `ch` does not close it.
   *
   * An `IOException` from `ch` is thrown wrapped in
   * `zio.blocks.streams.internal.StreamError`, whose `value` is the
   * `IOException`, and every later pull throws it again.
   *
   * `ch` must be in blocking mode: a channel read that returns no bytes is
   * taken as the end of the data (`readByte()` returns `-1`), although
   * `isClosed` stays `false`.
   */
  def fromChannel(ch: ReadableByteChannel, bufSize: Int = 8192): Reader.SyncReader[Byte] =
    new ChannelReader(ch, bufSize)

  /** [[fromChannel]] without closing `ch` when the reader closes. */
  private[streams] def fromChannelUnmanaged(ch: ReadableByteChannel, bufSize: Int): Reader.SyncReader[Byte] =
    new ChannelReader(ch, bufSize, closeChannel = false)
}
