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
      private[streams] override def drain(reader: Reader.AsyncReader[_]): Async[Unit] =
        Sink.foldAsyncReader[Byte, Unit](reader, ()) { (_, b) =>
          buf.put(b)
          Async.succeed(())
        }

      private[streams] def drain(reader: Reader.SyncReader[_]): Unit =
        Sink.foldSyncReader[Byte, Unit](reader, ())((_, value) => buf.put(value))
    }

  /**
   * Creates a sink that writes all stream Doubles into a [[ByteBuffer]] (8
   * bytes per element). Throws `BufferOverflowException` if the buffer has
   * insufficient remaining capacity.
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
   * Creates a sink that writes all stream Floats into a [[ByteBuffer]] (4 bytes
   * per element). Throws `BufferOverflowException` if the buffer has
   * insufficient remaining capacity.
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
   * Creates a sink that writes all stream Ints into a [[ByteBuffer]] (4 bytes
   * per element). Throws `BufferOverflowException` if the buffer has
   * insufficient remaining capacity.
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
   * Creates a sink that writes all stream Longs into a [[ByteBuffer]] (8 bytes
   * per element). Throws `BufferOverflowException` if the buffer has
   * insufficient remaining capacity.
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
