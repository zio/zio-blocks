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
 * Streams over `java.nio` sources (JVM only): a `ByteBuffer` read as bytes or
 * as `Int`, `Long`, `Float` or `Double` values, and a blocking
 * `ReadableByteChannel`. Each one reads through the matching [[NioReaders]]
 * reader.
 *
 * The buffer streams consume the buffer: a run advances its position to the
 * limit, so running the same stream again emits nothing.
 */
object NioStreams {

  /** Returns a stream of the bytes from `buf`'s position to its limit. */
  def fromByteBuffer(buf: ByteBuffer): Stream[Nothing, Byte] =
    Stream.fromReader(NioReaders.fromByteBuffer(buf))

  /**
   * Returns a stream of the `Double`s in `buf`, 8 bytes each, in `buf`'s byte
   * order; fewer than 8 bytes left over at the end are not read.
   */
  def fromByteBufferDouble(buf: ByteBuffer): Stream[Nothing, Double] =
    Stream.fromReader(NioReaders.fromByteBufferDouble(buf))

  /**
   * Returns a stream of the `Float`s in `buf`, 4 bytes each, in `buf`'s byte
   * order; fewer than 4 bytes left over at the end are not read.
   */
  def fromByteBufferFloat(buf: ByteBuffer): Stream[Nothing, Float] =
    Stream.fromReader(NioReaders.fromByteBufferFloat(buf))

  /**
   * Returns a stream of the `Int`s in `buf`, 4 bytes each, in `buf`'s byte
   * order; fewer than 4 bytes left over at the end are not read.
   */
  def fromByteBufferInt(buf: ByteBuffer): Stream[Nothing, Int] =
    Stream.fromReader(NioReaders.fromByteBufferInt(buf))

  /**
   * Returns a stream of the `Long`s in `buf`, 8 bytes each, in `buf`'s byte
   * order; fewer than 8 bytes left over at the end are not read.
   */
  def fromByteBufferLong(buf: ByteBuffer): Stream[Nothing, Long] =
    Stream.fromReader(NioReaders.fromByteBufferLong(buf))

  /**
   * Returns a stream of the bytes of `ch`, read through a buffer of `bufSize`
   * bytes. The stream closes `ch` when it ends, fails or is stopped early (for
   * example by `take`), and an `IOException` from `ch` ends the run with it as
   * the typed error (`Left(e)`).
   *
   * `ch` must be in blocking mode: a channel read that returns no bytes ends
   * the stream early, with no error.
   */
  def fromChannel(ch: ReadableByteChannel, bufSize: Int = 8192): Stream[java.io.IOException, Byte] =
    Stream.fromAcquireRelease(ch, (c: ReadableByteChannel) => c.close())(c => fromChannelUnmanaged(c, bufSize))

  /**
   * [[fromChannel]] without closing `ch`: the caller owns `ch` and closes it.
   */
  def fromChannelUnmanaged(ch: ReadableByteChannel, bufSize: Int = 8192): Stream[java.io.IOException, Byte] =
    Stream.fromReader(NioReaders.fromChannelUnmanaged(ch, bufSize))
}
