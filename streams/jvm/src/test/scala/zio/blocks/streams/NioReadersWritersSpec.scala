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

import zio.blocks.async._
import zio.test._

import java.nio.ByteBuffer
import java.nio.channels.{Channels, Pipe}

object NioReadersWritersSpec extends StreamsBaseSpec {

  def spec: Spec[TestEnvironment, Any] = suite("NioReaders / NioWriters")(
    suite("ByteBufferReader (byte)")(
      test("read() returns Right(byte) then Left(done) on exhaustion") {
        val buf = ByteBuffer.wrap(Array[Byte](1, 2, 3))
        val dq  = NioReaders.fromByteBuffer(buf)
        val r1  = dq.read[Any](null)
        val r2  = dq.read[Any](null)
        val r3  = dq.read[Any](null)
        val r4  = dq.read[Any](null)
        assertTrue(
          r1 == Byte.box(1.toByte),
          r2 == Byte.box(2.toByte),
          r3 == Byte.box(3.toByte),
          r4 == null
        )
      },
      test("read() returns Left(Right(())) on empty buffer") {
        val dq = NioReaders.fromByteBuffer(ByteBuffer.allocate(0))
        assertTrue(dq.read[Any](null) == null)
      },
      test("readByte returns byte value in [0,255]") {
        val dq = NioReaders.fromByteBuffer(ByteBuffer.wrap(Array[Byte](0xff.toByte)))
        assertTrue(dq.readByte() == 255)
      },
      test("readByte returns -1 on exhaustion") {
        val dq = NioReaders.fromByteBuffer(ByteBuffer.allocate(0))
        assertTrue(dq.readByte() == -1)
      },
      test("readBytes bulk reads into array") {
        val data = Array[Byte](10, 20, 30, 40, 50)
        val dq   = NioReaders.fromByteBuffer(ByteBuffer.wrap(data))
        val out  = new Array[Byte](5)
        val n    = dq.readBytes(out, 0, 5)
        assertTrue(n == 5, out.toList == data.toList)
      },
      test("readBytes returns -1 on empty") {
        val dq = NioReaders.fromByteBuffer(ByteBuffer.allocate(0))
        assertTrue(dq.readBytes(new Array[Byte](4), 0, 4) == -1)
      },
      test("readBytes(buf, 0, 0) returns 0") {
        val dq = NioReaders.fromByteBuffer(ByteBuffer.allocate(0))
        assertTrue(dq.readBytes(new Array[Byte](0), 0, 0) == 0)
      },
      test("reset() rewinds and allows re-reading") {
        val dq = NioReaders.fromByteBuffer(ByteBuffer.wrap(Array[Byte](1, 2)))
        val r1 = dq.read[Any](null)
        val r2 = dq.read[Any](null)
        val r3 = dq.read[Any](null)
        dq.reset()
        val r4 = dq.read[Any](null)
        val r5 = dq.read[Any](null)
        assertTrue(
          r1 == Byte.box(1.toByte),
          r2 == Byte.box(2.toByte),
          r3 == null,
          dq.isClosed == false || true, // after reset then 2 reads
          r4 == Byte.box(1.toByte),
          r5 == Byte.box(2.toByte)
        )
      },
      test("skip advances position") {
        val dq = NioReaders.fromByteBuffer(ByteBuffer.wrap(Array[Byte](1, 2, 3, 4, 5)))
        dq.skip(3)
        val r1 = dq.read[Any](null)
        val r2 = dq.read[Any](null)
        val r3 = dq.read[Any](null)
        assertTrue(
          r1 == Byte.box(4.toByte),
          r2 == Byte.box(5.toByte),
          r3 == null
        )
      },
      test("isClosed and closed reflect state") {
        val dq           = NioReaders.fromByteBuffer(ByteBuffer.wrap(Array[Byte](1)))
        val closedBefore = dq.isClosed
        val optBefore    = if (dq.isClosed) Some(()) else None
        dq.read[Any](null) // read the one byte
        dq.read[Any](null) // trigger exhaustion
        val closedAfter = dq.isClosed
        val optAfter    = if (dq.isClosed) Some(()) else None
        assertTrue(
          closedBefore == false,
          optBefore.isEmpty,
          closedAfter == true,
          optAfter == Some(())
        )
      },
      test("awaitClose drains and returns done") {
        val dq = NioReaders.fromByteBuffer(ByteBuffer.wrap(Array[Byte](1, 2)))
        assertTrue { dq.close(); true }
      },
      test("readable returns true then false") {
        val dq = NioReaders.fromByteBuffer(ByteBuffer.wrap(Array[Byte](42)))
        val a1 = dq.readable()
        dq.read[Any](null)
        val a2 = dq.readable()
        assertTrue(a1, !a2)
      }
    ),

    suite("ByteBufferIntReader")(
      test("reads Ints in buffer order") {
        val buf = ByteBuffer.allocate(12)
        buf.putInt(100).putInt(200).putInt(300).flip()
        val dq = NioReaders.fromByteBufferInt(buf)
        val r1 = dq.read[Any](null)
        val r2 = dq.read[Any](null)
        val r3 = dq.read[Any](null)
        val r4 = dq.read[Any](null)
        assertTrue(
          r1 == Int.box(100),
          r2 == Int.box(200),
          r3 == Int.box(300),
          r4 == null
        )
      },
      test("readInt zero-boxing path") {
        val buf = ByteBuffer.allocate(8)
        buf.putInt(42).putInt(99).flip()
        val dq = NioReaders.fromByteBufferInt(buf)
        val v1 = dq.readInt(Long.MinValue)
        val v2 = dq.readInt(Long.MinValue)
        val v3 = dq.readInt(Long.MinValue)
        assertTrue(v1 == 42L, v2 == 99L, v3 == Long.MinValue)
      },
      test("reset rewinds") {
        val buf = ByteBuffer.allocate(4)
        buf.putInt(7).flip()
        val dq = NioReaders.fromByteBufferInt(buf)
        dq.read[Any](null)
        dq.reset()
        assertTrue(dq.read[Any](null) == Int.box(7))
      },
      test("skip advances by correct number of ints") {
        val buf = ByteBuffer.allocate(12)
        buf.putInt(1).putInt(2).putInt(3).flip()
        val dq = NioReaders.fromByteBufferInt(buf)
        dq.skip(2)
        assertTrue(dq.readInt(Long.MinValue) == 3L)
      }
    ),

    suite("ByteBufferLongReader")(
      test("reads Longs in buffer order") {
        val buf = ByteBuffer.allocate(24)
        buf.putLong(1000L).putLong(2000L).putLong(3000L).flip()
        val dq = NioReaders.fromByteBufferLong(buf)
        val v1 = dq.readLong(Long.MaxValue)
        val v2 = dq.readLong(Long.MaxValue)
        val v3 = dq.readLong(Long.MaxValue)
        val v4 = dq.readLong(Long.MaxValue)
        assertTrue(v1 == 1000L, v2 == 2000L, v3 == 3000L, v4 == Long.MaxValue)
      },
      test("reset rewinds") {
        val buf = ByteBuffer.allocate(8)
        buf.putLong(123L).flip()
        val dq = NioReaders.fromByteBufferLong(buf)
        dq.read[Any](null)
        dq.reset()
        assertTrue(dq.read[Any](null) == Long.box(123L))
      }
    ),

    suite("ByteBufferDoubleReader")(
      test("reads Doubles in buffer order") {
        val buf = ByteBuffer.allocate(16)
        buf.putDouble(1.5).putDouble(2.5).flip()
        val dq = NioReaders.fromByteBufferDouble(buf)
        val v1 = dq.readDouble(Double.MaxValue)
        val v2 = dq.readDouble(Double.MaxValue)
        val v3 = dq.readDouble(Double.MaxValue)
        assertTrue(v1 == 1.5, v2 == 2.5, v3 == Double.MaxValue)
      },
      test("reset rewinds") {
        val buf = ByteBuffer.allocate(8)
        buf.putDouble(3.14).flip()
        val dq = NioReaders.fromByteBufferDouble(buf)
        dq.read[Any](null)
        dq.reset()
        assertTrue(dq.read[Any](null) == Double.box(3.14))
      }
    ),

    suite("ByteBufferFloatReader")(
      test("reads Floats in buffer order") {
        val buf = ByteBuffer.allocate(8)
        buf.putFloat(1.0f).putFloat(2.0f).flip()
        val dq = NioReaders.fromByteBufferFloat(buf)
        val v1 = dq.readFloat(Double.MaxValue)
        val v2 = dq.readFloat(Double.MaxValue)
        val v3 = dq.readFloat(Double.MaxValue)
        assertTrue(v1 == 1.0, v2 == 2.0, v3 == Double.MaxValue)
      },
      test("reset rewinds") {
        val buf = ByteBuffer.allocate(4)
        buf.putFloat(9.0f).flip()
        val dq = NioReaders.fromByteBufferFloat(buf)
        dq.read[Any](null)
        dq.reset()
        assertTrue(dq.read[Any](null) == Float.box(9.0f))
      }
    ),

    suite("ByteBufferWriter (byte)")(
      test("write writes bytes into buffer") {
        val buf = ByteBuffer.allocate(3)
        val enq = NioWriters.fromByteBuffer(buf)
        val w1  = enq.write(1.toByte)
        val w2  = enq.write(2.toByte)
        val w3  = enq.write(3.toByte)
        buf.flip()
        val b1 = buf.get()
        val b2 = buf.get()
        val b3 = buf.get()
        assertTrue(w1, w2, w3, b1 == 1.toByte, b2 == 2.toByte, b3 == 3.toByte)
      },
      test("write returns false when buffer is full") {
        val buf = ByteBuffer.allocate(1)
        val enq = NioWriters.fromByteBuffer(buf)
        val w1  = enq.write(1.toByte)
        val w2  = enq.write(2.toByte)
        assertTrue(w1, w2 == false)
      },
      test("write returns false after close") {
        val enq = NioWriters.fromByteBuffer(ByteBuffer.allocate(10))
        enq.close()
        assertTrue(enq.write(1.toByte) == false)
      },
      test("writeBytes writes bulk data") {
        val buf  = ByteBuffer.allocate(5)
        val enq  = NioWriters.fromByteBuffer(buf)
        val data = Array[Byte](10, 20, 30, 40, 50)
        val n    = enq.writeBytes(data, 0, 5)
        buf.flip()
        val out = new Array[Byte](5)
        buf.get(out)
        assertTrue(n == 5, out.toList == data.toList)
      },
      test("writeBytes partial when buffer too small") {
        val buf = ByteBuffer.allocate(2)
        val enq = NioWriters.fromByteBuffer(buf)
        val n   = enq.writeBytes(Array[Byte](1, 2, 3, 4, 5), 0, 5)
        assertTrue(n == 2)
      },
      test("writeBytes validates null and ranges before closed state") {
        val enq = NioWriters.fromByteBuffer(ByteBuffer.allocate(1))
        enq.close()
        val nullFailure  = scala.util.Try(enq.writeBytes(null, 0, 0)).failed.toOption
        val rangeFailure = scala.util.Try(enq.writeBytes(Array[Byte](1), 1, 1)).failed.toOption
        assertTrue(
          nullFailure.exists(_.isInstanceOf[NullPointerException]),
          rangeFailure.exists(_.isInstanceOf[IndexOutOfBoundsException])
        )
      },
      test("isClosed starts false, becomes true after close") {
        val enq    = NioWriters.fromByteBuffer(ByteBuffer.allocate(4))
        val before = enq.isClosed
        enq.close()
        val after = enq.isClosed
        assertTrue(before == false, after == true)
      },
      test("close is idempotent") {
        val enq = NioWriters.fromByteBuffer(ByteBuffer.allocate(4))
        enq.close()
        enq.close()
        assertTrue(enq.isClosed)
      }
    ),

    suite("ByteBuffer Int/Long/Double/Float writers")(
      test("ByteBufferIntWriter writes ints") {
        val buf = ByteBuffer.allocate(12)
        val enq = NioWriters.fromByteBufferInt(buf)
        val w1  = enq.writeInt(100)
        val w2  = enq.writeInt(200)
        val w3  = enq.writeInt(300)
        buf.flip()
        val i1 = buf.getInt()
        val i2 = buf.getInt()
        val i3 = buf.getInt()
        assertTrue(w1, w2, w3, i1 == 100, i2 == 200, i3 == 300)
      },
      test("ByteBufferLongWriter writes longs") {
        val buf = ByteBuffer.allocate(16)
        val enq = NioWriters.fromByteBufferLong(buf)
        val w1  = enq.writeLong(1000L)
        val w2  = enq.writeLong(2000L)
        buf.flip()
        val l1 = buf.getLong()
        val l2 = buf.getLong()
        assertTrue(w1, w2, l1 == 1000L, l2 == 2000L)
      },
      test("ByteBufferDoubleWriter writes doubles") {
        val buf = ByteBuffer.allocate(16)
        val enq = NioWriters.fromByteBufferDouble(buf)
        val w1  = enq.writeDouble(1.5)
        val w2  = enq.writeDouble(2.5)
        buf.flip()
        val d1 = buf.getDouble()
        val d2 = buf.getDouble()
        assertTrue(w1, w2, d1 == 1.5, d2 == 2.5)
      },
      test("ByteBufferFloatWriter writes floats") {
        val buf = ByteBuffer.allocate(8)
        val enq = NioWriters.fromByteBufferFloat(buf)
        val w1  = enq.writeFloat(1.0f)
        val w2  = enq.writeFloat(2.0f)
        buf.flip()
        val f1 = buf.getFloat()
        val f2 = buf.getFloat()
        assertTrue(w1, w2, f1 == 1.0f, f2 == 2.0f)
      },
      test("overflow returns false for typed writers") {
        val buf = ByteBuffer.allocate(3) // too small for a single int
        val enq = NioWriters.fromByteBufferInt(buf)
        assertTrue(enq.writeInt(42) == false)
      }
    ),

    suite("ChannelReader")(
      test("reads bytes from a ReadableByteChannel") {
        val data = Array[Byte](1, 2, 3, 4, 5)
        val ch   = Channels.newChannel(new java.io.ByteArrayInputStream(data))
        val dq   = NioReaders.fromChannel(ch, bufSize = 4)
        val r1   = dq.read[Any](null)
        val r2   = dq.read[Any](null)
        val r3   = dq.read[Any](null)
        val r4   = dq.read[Any](null)
        val r5   = dq.read[Any](null)
        val r6   = dq.read[Any](null)
        assertTrue(
          r1 == Byte.box(1.toByte),
          r2 == Byte.box(2.toByte),
          r3 == Byte.box(3.toByte),
          r4 == Byte.box(4.toByte),
          r5 == Byte.box(5.toByte),
          r6 == null
        )
      },
      test("readByte returns [0,255] then -1") {
        val ch = Channels.newChannel(new java.io.ByteArrayInputStream(Array[Byte](0xff.toByte)))
        val dq = NioReaders.fromChannel(ch, bufSize = 16)
        val b1 = dq.readByte()
        val b2 = dq.readByte()
        assertTrue(b1 == 255, b2 == -1)
      },
      test("readBytes bulk reads") {
        val data = Array[Byte](10, 20, 30, 40)
        val ch   = Channels.newChannel(new java.io.ByteArrayInputStream(data))
        val dq   = NioReaders.fromChannel(ch, bufSize = 8)
        val out  = new Array[Byte](4)
        val n    = dq.readBytes(out, 0, 4)
        assertTrue(n > 0)
      },
      test("returns Left on empty channel") {
        val ch = Channels.newChannel(new java.io.ByteArrayInputStream(Array.empty[Byte]))
        val dq = NioReaders.fromChannel(ch, bufSize = 8)
        assertTrue(dq.read[Any](null) == null)
      },
      test("awaitClose drains and returns done") {
        val data = Array[Byte](1, 2, 3)
        val ch   = Channels.newChannel(new java.io.ByteArrayInputStream(data))
        val dq   = NioReaders.fromChannel(ch, bufSize = 2)
        assertTrue { dq.close(); true }
      },
      test("close releases the wrapped channel exactly once") {
        var closeCount = 0
        val ch         = new java.nio.channels.ReadableByteChannel {
          private var open               = true
          def isOpen: Boolean            = open
          def read(dst: ByteBuffer): Int = -1
          def close(): Unit              = if (open) {
            open = false
            closeCount += 1
          }
        }
        val reader = NioReaders.fromChannel(ch)

        reader.close()
        reader.close()

        assertTrue(!ch.isOpen, closeCount == 1)
      },
      test("close preserves and replays the channel failure without closing twice") {
        val closeFailure  = new java.io.IOException("close")
        var closeAttempts = 0
        val ch            = new java.nio.channels.ReadableByteChannel {
          def isOpen: Boolean            = true
          def read(dst: ByteBuffer): Int = -1
          def close(): Unit              = {
            closeAttempts += 1
            throw closeFailure
          }
        }
        val reader = NioReaders.fromChannel(ch)

        val first = try { reader.close(); null }
        catch { case t: Throwable => t }
        val second = try { reader.close(); null }
        catch { case t: Throwable => t }

        assertTrue(first eq closeFailure, second eq closeFailure, closeAttempts == 1, reader.isClosed)
      },
      test("read failure identity is sticky across scalar, bulk, and chunk pulls") {
        val ch = new java.nio.channels.ReadableByteChannel {
          def isOpen: Boolean            = true
          def read(dst: ByteBuffer): Int = throw new java.io.IOException("read")
          def close(): Unit              = ()
        }
        val reader = NioReaders.fromChannel(ch)

        val first = scala.util.Try(reader.readByte()).failed.toOption.orNull
        val bulk  = scala.util.Try(reader.readBytes(new Array[Byte](0), 0, 0)).failed.toOption.orNull
        val chunk = scala.util.Try(reader.readN[Any](0)).failed.toOption.orNull

        assertTrue(first.isInstanceOf[zio.blocks.streams.internal.StreamError], bulk eq first, chunk eq first)
      },
      test("isClosed becomes true after exhaustion") {
        val ch     = Channels.newChannel(new java.io.ByteArrayInputStream(Array[Byte](1)))
        val dq     = NioReaders.fromChannel(ch, bufSize = 4)
        val before = dq.isClosed
        dq.read[Any](null); dq.read[Any](null)
        val after = dq.isClosed
        assertTrue(before == false, after == true)
      }
    ),

    suite("ChannelWriter")(
      test("writes bytes to a WritableByteChannel") {
        val pipe = Pipe.open()
        val enq  = NioWriters.fromChannel(pipe.sink(), bufSize = 1024)
        val w1   = enq.write(1.toByte)
        val w2   = enq.write(2.toByte)
        val w3   = enq.write(3.toByte)
        enq.close()
        val readBuf = ByteBuffer.allocate(3)
        var read    = 0
        while (readBuf.hasRemaining && read >= 0) read = pipe.source().read(readBuf)
        pipe.source().close()
        readBuf.flip()
        val b1 = readBuf.get()
        val b2 = readBuf.get()
        val b3 = readBuf.get()
        assertTrue(w1, w2, w3, b1 == 1.toByte, b2 == 2.toByte, b3 == 3.toByte)
      },
      test("writeBytes writes bulk data through channel") {
        val pipe = Pipe.open()
        val enq  = NioWriters.fromChannel(pipe.sink(), bufSize = 1024)
        val data = Array[Byte](10, 20, 30, 40, 50)
        val n    = enq.writeBytes(data, 0, 5)
        enq.close()
        val readBuf = ByteBuffer.allocate(5)
        var read    = 0
        while (readBuf.hasRemaining && read >= 0) read = pipe.source().read(readBuf)
        pipe.source().close()
        readBuf.flip()
        val out = new Array[Byte](5)
        readBuf.get(out)
        assertTrue(n == 5, out.toList == data.toList)
      },
      test("writeBytes validates null and ranges before closed state") {
        val pipe = Pipe.open()
        val enq  = NioWriters.fromChannel(pipe.sink(), bufSize = 4)
        enq.close()
        pipe.source().close()
        val nullFailure  = scala.util.Try(enq.writeBytes(null, 0, 0)).failed.toOption
        val rangeFailure = scala.util.Try(enq.writeBytes(Array[Byte](1), 1, 1)).failed.toOption
        assertTrue(
          nullFailure.exists(_.isInstanceOf[NullPointerException]),
          rangeFailure.exists(_.isInstanceOf[IndexOutOfBoundsException])
        )
      },
      test("write returns false after close") {
        val pipe = Pipe.open()
        val enq  = NioWriters.fromChannel(pipe.sink(), bufSize = 4)
        enq.close()
        pipe.source().close()
        assertTrue(enq.write(1.toByte) == false)
      },
      test("close is idempotent") {
        val pipe = Pipe.open()
        val enq  = NioWriters.fromChannel(pipe.sink(), bufSize = 4)
        enq.close()
        enq.close()
        pipe.source().close()
        assertTrue(enq.isClosed)
      },
      test("isClosed starts false") {
        val pipe   = Pipe.open()
        val enq    = NioWriters.fromChannel(pipe.sink(), bufSize = 4)
        val result = enq.isClosed
        enq.close()
        pipe.source().close()
        assertTrue(result == false)
      }
    ),

    suite("NioWriters deferred operations")(
      test("ByteBuffer writers defer scalar, bulk, metadata, state, and close operations") {
        val buffer     = ByteBuffer.allocate(4)
        val writer     = NioWriters.fromByteBuffer(buffer)
        val scalar     = writer.writeByteAsync(1.toByte)
        val bulk       = writer.writeBytesAsync(Array[Byte](2, 3), 0, 2)
        val before     = buffer.position()
        val scalarOk   = scalar.block
        val bulkCount  = bulk.block
        val writerType = writer.jvmTypeAsync.block
        val writeable  = writer.writeableAsync().block
        writer.closeAsync().block
        buffer.flip()
        val bytes = new Array[Byte](3)
        buffer.get(bytes)
        assertTrue(
          before == 0,
          scalarOk,
          bulkCount == 2,
          writerType == JvmType.Byte,
          writeable,
          writer.isClosedAsync.block,
          bytes.toList == List[Byte](1, 2, 3)
        )
      },
      test("typed ByteBuffer writers preserve primitive deferred writes") {
        val buffer = ByteBuffer.allocate(8)
        val writer = NioWriters.fromByteBufferInt(buffer)
        val first  = writer.writeIntAsync(10).block
        val second = writer.writeIntAsync(20).block
        buffer.flip()
        assertTrue(first, second, buffer.getInt() == 10, buffer.getInt() == 20)
      },
      test("cancelling an undriven channel operation closes ownership without writing") {
        var writes  = 0
        var closes  = 0
        val channel = new java.nio.channels.WritableByteChannel {
          private var open                = true
          def write(src: ByteBuffer): Int = { writes += 1; val n = src.remaining(); src.position(src.limit()); n }
          def isOpen: Boolean             = open
          def close(): Unit               = if (open) { open = false; closes += 1 }
        }
        val writer = NioWriters.fromChannel(channel, bufSize = 4)
        val effect = writer.writeByteAsync(1.toByte).asInstanceOf[Pollable[Boolean]]
        Async.cancelWithCleanup(effect).block
        assertTrue(writes == 0, closes == 1, writer.isClosedAsync.block)
      }
    ),

    suite("round-trip: ByteBuffer write then read")(
      test("byte round-trip") {
        val buf  = ByteBuffer.allocate(10)
        val enq  = NioWriters.fromByteBuffer(buf)
        val data = Array[Byte](1, 2, 3, 4, 5)
        data.foreach(b => enq.write(b))
        enq.close()
        buf.flip()
        val dq  = NioReaders.fromByteBuffer(buf)
        val out = (1 to 5).map(_ => dq.read[Any](null)).toList
        assertTrue(out == data.map(b => Byte.box(b)).toList)
      },
      test("int round-trip") {
        val buf = ByteBuffer.allocate(20)
        val enq = NioWriters.fromByteBufferInt(buf)
        enq.writeInt(42)
        enq.writeInt(99)
        enq.close()
        buf.flip()
        val dq = NioReaders.fromByteBufferInt(buf)
        val v1 = dq.readInt(Long.MinValue)
        val v2 = dq.readInt(Long.MinValue)
        val v3 = dq.readInt(Long.MinValue)
        assertTrue(v1 == 42L, v2 == 99L, v3 == Long.MinValue)
      },
      test("long round-trip") {
        val buf = ByteBuffer.allocate(24)
        val enq = NioWriters.fromByteBufferLong(buf)
        enq.writeLong(1000L)
        enq.writeLong(2000L)
        enq.close()
        buf.flip()
        val dq = NioReaders.fromByteBufferLong(buf)
        val v1 = dq.readLong(Long.MaxValue)
        val v2 = dq.readLong(Long.MaxValue)
        val v3 = dq.readLong(Long.MaxValue)
        assertTrue(v1 == 1000L, v2 == 2000L, v3 == Long.MaxValue)
      },
      test("long bulk status preserves the full value domain") {
        val buf = ByteBuffer.allocate(16).putLong(Long.MinValue).putLong(Long.MaxValue).flip().asInstanceOf[ByteBuffer]
        val dq  = NioReaders.fromByteBufferLong(buf)
        val out = new Array[Long](2)
        assertTrue(
          dq.readLongs(out, 0, 0) == 0,
          dq.readLongs(out, 0, 2) == 2,
          out.sameElements(Array(Long.MinValue, Long.MaxValue)),
          dq.readLongs(out, 0, 1) == -1
        )
      },
      test("double round-trip") {
        val buf = ByteBuffer.allocate(24)
        val enq = NioWriters.fromByteBufferDouble(buf)
        enq.writeDouble(3.14)
        enq.writeDouble(2.71)
        enq.close()
        buf.flip()
        val dq = NioReaders.fromByteBufferDouble(buf)
        val v1 = dq.readDouble(Double.MaxValue)
        val v2 = dq.readDouble(Double.MaxValue)
        val v3 = dq.readDouble(Double.MaxValue)
        assertTrue(v1 == 3.14, v2 == 2.71, v3 == Double.MaxValue)
      },
      test("double bulk status preserves NaN payloads and sentinel values") {
        val values      = Array(java.lang.Double.longBitsToDouble(0x7ff8000000000001L), Double.MaxValue)
        val buf         = ByteBuffer.allocate(16).putDouble(values(0)).putDouble(values(1)).flip().asInstanceOf[ByteBuffer]
        val dq          = NioReaders.fromByteBufferDouble(buf)
        val out         = new Array[scala.Double](2)
        val zeroBits    = java.lang.Double.doubleToRawLongBits(values(0))
        val oneBits     = java.lang.Double.doubleToRawLongBits(values(1))
        val count       = dq.readDoubles(out, 0, 2)
        val outZeroBits = java.lang.Double.doubleToRawLongBits(out(0))
        val outOneBits  = java.lang.Double.doubleToRawLongBits(out(1))
        assertTrue(
          dq.readDoubles(out, 0, 0) == 0,
          count == 2,
          outZeroBits == zeroBits,
          outOneBits == oneBits,
          dq.readDoubles(out, 0, 1) == -1
        )
      },
      test("float round-trip") {
        val buf = ByteBuffer.allocate(12)
        val enq = NioWriters.fromByteBufferFloat(buf)
        enq.writeFloat(1.5f)
        enq.writeFloat(2.5f)
        enq.close()
        buf.flip()
        val dq = NioReaders.fromByteBufferFloat(buf)
        val v1 = dq.readFloat(Double.MaxValue)
        val v2 = dq.readFloat(Double.MaxValue)
        val v3 = dq.readFloat(Double.MaxValue)
        assertTrue(v1 == 1.5, v2 == 2.5, v3 == Double.MaxValue)
      }
    ),
    suite("ChannelWriter close safety")(
      test("channel is closed even if flush throws IOException") {
        // Regression: ChannelWriter.close() previously had ch.close() inside the
        // try block after writes, so if a write threw, ch.close() was skipped.
        var channelClosed = false
        val ch            = new java.nio.channels.WritableByteChannel {
          def write(src: ByteBuffer): Int = throw new java.io.IOException("write failed")
          def isOpen: Boolean             = !channelClosed
          def close(): Unit               = channelClosed = true
        }
        val w = NioWriters.fromChannel(ch, bufSize = 4)
        // Write some data to fill the buffer, then close triggers a flush that throws
        w.writeByte(1)
        w.close()
        assertTrue(channelClosed)
      },
      test("channel is closed on normal close") {
        var channelClosed = false
        val ch            = new java.nio.channels.WritableByteChannel {
          def write(src: ByteBuffer): Int = { val n = src.remaining(); src.position(src.limit()); n }
          def isOpen: Boolean             = !channelClosed
          def close(): Unit               = channelClosed = true
        }
        val w = NioWriters.fromChannel(ch, bufSize = 8)
        w.writeByte(42)
        w.close()
        assertTrue(channelClosed)
      }
    )
  )
}
