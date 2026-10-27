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

import zio.blocks.async.Async
import zio.blocks.async._
import zio.blocks.streams.internal.StreamError
import zio.blocks.streams.io.Reader

import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.WritableByteChannel

/**
 * Sinks that write a stream into `java.nio` targets (JVM only): a `ByteBuffer`
 * written as bytes or as `Int`, `Long`, `Float` or `Double` values, and a
 * blocking `WritableByteChannel`. Each sink works with the blocking terminals
 * and with the `*Async` ones.
 *
 * The buffer sinks write at the buffer's position and advance it, encoding
 * multi-byte values in the buffer's byte order, and keep every value,
 * `Long.MaxValue`, `Double.MaxValue` and `NaN` included. When the buffer runs
 * out of room, the `BufferOverflowException` of `ByteBuffer#put` is not a typed
 * error: a blocking terminal throws it, and an `*Async` terminal's `Async`
 * fails with it. The values written before it stay in the buffer.
 */
object NioSinks {

  /**
   * Returns a sink that writes every byte of the stream into `buf`. A byte that
   * does not fit throws `BufferOverflowException` (see [[NioSinks]]).
   */
  def fromByteBuffer(buf: ByteBuffer): Sink[Nothing, Byte, Unit] =
    new Sink[Nothing, Byte, Unit] {
      private[streams] override def drain(reader: Reader.AsyncReader[_]): Async[Unit] =
        Sink.foldAsyncReader[Byte, Unit](reader, ()) { (_, b) =>
          buf.put(b)
          Async.succeed(())
        }

      private[streams] def drain(reader: Reader.SyncReader[_]): Unit =
        Sink.foldSyncReader[Byte, Unit](reader, ())((_, value) => buf.put(value))
    }

  /**
   * Returns a sink that writes every `Double` of the stream into `buf`, 8 bytes
   * each. A value that does not fit throws `BufferOverflowException` (see
   * [[NioSinks]]).
   */
  def fromByteBufferDouble(buf: ByteBuffer): Sink[Nothing, Double, Unit] =
    new Sink[Nothing, Double, Unit] {
      private[streams] override def drain(reader: Reader.AsyncReader[_]): Async[Unit] =
        Sink.foldAsyncReader[Double, Unit](reader, ()) { (_, value) =>
          buf.putDouble(value)
          Async.succeed(())
        }

      private[streams] def drain(reader: Reader.SyncReader[_]): Unit =
        Sink.foldSyncReader[Double, Unit](reader, ())((_, value) => buf.putDouble(value))
    }

  /**
   * Returns a sink that writes every `Float` of the stream into `buf`, 4 bytes
   * each. A value that does not fit throws `BufferOverflowException` (see
   * [[NioSinks]]).
   */
  def fromByteBufferFloat(buf: ByteBuffer): Sink[Nothing, Float, Unit] =
    new Sink[Nothing, Float, Unit] {
      private[streams] override def drain(reader: Reader.AsyncReader[_]): Async[Unit] =
        Sink.foldAsyncReader[Float, Unit](reader, ()) { (_, value) =>
          buf.putFloat(value)
          Async.succeed(())
        }

      private[streams] def drain(reader: Reader.SyncReader[_]): Unit =
        Sink.foldSyncReader[Float, Unit](reader, ())((_, value) => buf.putFloat(value))
    }

  /**
   * Returns a sink that writes every `Int` of the stream into `buf`, 4 bytes
   * each. A value that does not fit throws `BufferOverflowException` (see
   * [[NioSinks]]).
   */
  def fromByteBufferInt(buf: ByteBuffer): Sink[Nothing, Int, Unit] =
    new Sink[Nothing, Int, Unit] {
      private[streams] override def drain(reader: Reader.AsyncReader[_]): Async[Unit] =
        Sink.foldAsyncReader[Int, Unit](reader, ()) { (_, value) =>
          buf.putInt(value)
          Async.succeed(())
        }

      private[streams] def drain(reader: Reader.SyncReader[_]): Unit =
        Sink.foldSyncReader[Int, Unit](reader, ())((_, value) => buf.putInt(value))
    }

  /**
   * Returns a sink that writes every `Long` of the stream into `buf`, 8 bytes
   * each. A value that does not fit throws `BufferOverflowException` (see
   * [[NioSinks]]).
   */
  def fromByteBufferLong(buf: ByteBuffer): Sink[Nothing, Long, Unit] =
    new Sink[Nothing, Long, Unit] {
      private[streams] override def drain(reader: Reader.AsyncReader[_]): Async[Unit] =
        Sink.foldAsyncReader[Long, Unit](reader, ()) { (_, value) =>
          buf.putLong(value)
          Async.succeed(())
        }

      private[streams] def drain(reader: Reader.SyncReader[_]): Unit =
        Sink.foldSyncReader[Long, Unit](reader, ())((_, value) => buf.putLong(value))
    }

  /**
   * Returns a sink that writes every byte of the stream to `ch`, through a
   * buffer of `bufSize` bytes that it writes out when full and at the end of
   * the stream. It does not close `ch`.
   *
   * An `IOException` from `ch` ends the run with it as the typed error
   * (`Left(e)`). The asynchronous drain also calls `ch.write` directly, so a
   * channel that blocks also blocks the thread that runs an `*Async` terminal;
   * when `ch.write` writes nothing, that drain yields and tries again.
   */
  def fromChannel(ch: WritableByteChannel, bufSize: Int = 8192): Sink[IOException, Byte, Unit] =
    new Sink[IOException, Byte, Unit] {
      private def flush(buf: ByteBuffer): Async[Unit] = {
        buf.flip()
        def loop(budget: Int): Async[Unit] =
          if (!buf.hasRemaining) {
            buf.compact()
            Async.succeed(())
          } else
            try {
              val written = ch.write(buf)
              if (written > 0 && budget > 0) loop(budget - 1)
              else Sink.yieldEffect(loop(255))
            } catch { case error: IOException => Async.failTrusted(StreamError.sink(error)) }
        loop(255)
      }

      private[streams] override def drain(reader: Reader.AsyncReader[_]): Async[Unit] = {
        val buf = ByteBuffer.allocate(bufSize)
        Sink
          .foldAsyncReader[Byte, Unit](reader, ()) { (_, b) =>
            val flushed = if (buf.hasRemaining) Async.succeed(()) else flush(buf)
            flushed.map { _ => buf.put(b); () }
          }
          .flatMap(_ => flush(buf))
      }

      private[streams] def drain(reader: Reader.SyncReader[_]): Unit = {
        val buf = ByteBuffer.allocate(bufSize)
        Sink.foldSyncReader[Byte, Unit](reader, ()) { (_, b) =>
          if (!buf.hasRemaining) {
            buf.flip()
            try { while (buf.hasRemaining) ch.write(buf) }
            catch { case e: IOException => throw StreamError.sink(e) }
            buf.compact()
          }
          buf.put(b)
        }
        buf.flip()
        try { while (buf.hasRemaining) ch.write(buf) }
        catch { case e: IOException => throw StreamError.sink(e) }
      }
    }
}
