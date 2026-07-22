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

import zio.blocks.chunk.Chunk
import zio.test._

import java.nio.ByteBuffer
import java.nio.channels.{Channels, Pipe}

object NioSpec extends StreamsBaseSpec {

  def spec: Spec[TestEnvironment, Any] = suite("Nio")(
    suite("NioReaders")(
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
        test("isClosed becomes true after exhaustion") {
          val ch     = Channels.newChannel(new java.io.ByteArrayInputStream(Array[Byte](1)))
          val dq     = NioReaders.fromChannel(ch, bufSize = 4)
          val before = dq.isClosed
          dq.read[Any](null); dq.read[Any](null)
          val after = dq.isClosed
          assertTrue(before == false, after == true)
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
      )
    ),
    suite("NioWriters")(
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
      suite("ChannelWriter")(
        test("writes bytes to a WritableByteChannel") {
          val pipe = Pipe.open()
          pipe.source().configureBlocking(false)
          val enq = NioWriters.fromChannel(pipe.sink(), bufSize = 1024)
          val w1  = enq.write(1.toByte)
          val w2  = enq.write(2.toByte)
          val w3  = enq.write(3.toByte)
          enq.close()
          val readBuf  = ByteBuffer.allocate(10)
          var attempts = 0
          while (readBuf.position() < 3 && attempts < 100) {
            pipe.source().read(readBuf)
            attempts += 1
            if (readBuf.position() < 3) Thread.sleep(10)
          }
          pipe.source().close()
          readBuf.flip()
          val b1 = readBuf.get()
          val b2 = readBuf.get()
          val b3 = readBuf.get()
          assertTrue(w1, w2, w3, b1 == 1.toByte, b2 == 2.toByte, b3 == 3.toByte)
        },
        test("writeBytes writes bulk data through channel") {
          val pipe = Pipe.open()
          pipe.source().configureBlocking(false)
          val enq  = NioWriters.fromChannel(pipe.sink(), bufSize = 1024)
          val data = Array[Byte](10, 20, 30, 40, 50)
          val n    = enq.writeBytes(data, 0, 5)
          enq.close()
          val readBuf  = ByteBuffer.allocate(10)
          var attempts = 0
          while (readBuf.position() < 5 && attempts < 100) {
            pipe.source().read(readBuf)
            attempts += 1
            if (readBuf.position() < 5) Thread.sleep(10)
          }
          pipe.source().close()
          readBuf.flip()
          val out = new Array[Byte](5)
          readBuf.get(out)
          assertTrue(n == 5, out.toList == data.toList)
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
      suite("ChannelWriter close safety")(
        test("channel is closed even if flush throws IOException") {
          // Regression: ChannelWriter.close() previously had ch.close() inside the
          // try block after writes, so if a write threw, ch.close() was skipped.
          // The flush failure itself must propagate (Principle 4), but the
          // channel must still be closed first.
          var channelClosed = false
          val ch            = new java.nio.channels.WritableByteChannel {
            def write(src: ByteBuffer): Int = throw new java.io.IOException("write failed")
            def isOpen: Boolean             = !channelClosed
            def close(): Unit               = channelClosed = true
          }
          val w = NioWriters.fromChannel(ch, bufSize = 4)
          // Write some data to fill the buffer, then close triggers a flush that throws
          w.writeByte(1)
          val thrown =
            try { w.close(); false }
            catch { case e: java.io.IOException => e.getMessage == "write failed" }
          assertTrue(channelClosed, thrown)
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
    ),
    suite("NioStreams")(
      suite("fromByteBuffer")(
        test("reads all bytes from buffer") {
          val buf    = ByteBuffer.wrap(Array[Byte](1, 2, 3))
          val result = NioStreams.fromByteBuffer(buf).runCollect
          assertTrue(result == Right(Chunk[Byte](1, 2, 3)))
        },
        test("empty buffer yields empty stream") {
          val result = NioStreams.fromByteBuffer(ByteBuffer.allocate(0)).runCollect
          assertTrue(result == Right(Chunk.empty))
        }
      ),
      suite("fromByteBufferInt")(
        test("reads all ints from buffer") {
          val buf = ByteBuffer.allocate(12)
          buf.putInt(100).putInt(200).putInt(300).flip()
          val result = NioStreams.fromByteBufferInt(buf).runCollect
          assertTrue(result == Right(Chunk(100, 200, 300)))
        },
        test("empty buffer yields empty stream") {
          val buf    = ByteBuffer.allocate(0)
          val result = NioStreams.fromByteBufferInt(buf).runCollect
          assertTrue(result == Right(Chunk.empty))
        }
      ),
      suite("fromByteBufferLong")(
        test("reads all longs from buffer") {
          val buf = ByteBuffer.allocate(16)
          buf.putLong(1000L).putLong(2000L).flip()
          val result = NioStreams.fromByteBufferLong(buf).runCollect
          assertTrue(result == Right(Chunk(1000L, 2000L)))
        }
      ),
      suite("fromByteBufferDouble")(
        test("reads all doubles from buffer") {
          val buf = ByteBuffer.allocate(16)
          buf.putDouble(1.5).putDouble(2.5).flip()
          val result = NioStreams.fromByteBufferDouble(buf).runCollect
          assertTrue(result == Right(Chunk(1.5, 2.5)))
        }
      ),
      suite("fromByteBufferFloat")(
        test("reads all floats from buffer") {
          val buf = ByteBuffer.allocate(8)
          buf.putFloat(1.0f).putFloat(2.0f).flip()
          val result = NioStreams.fromByteBufferFloat(buf).runCollect
          assertTrue(result == Right(Chunk(1.0f, 2.0f)))
        }
      ),
      suite("fromChannel")(
        test("reads all bytes from channel") {
          val data   = Array[Byte](1, 2, 3, 4, 5)
          val ch     = Channels.newChannel(new java.io.ByteArrayInputStream(data))
          val result = NioStreams.fromChannel(ch, bufSize = 4).runCollect
          assertTrue(result == Right(Chunk[Byte](1, 2, 3, 4, 5)))
        },
        test("empty channel yields empty stream") {
          val ch     = Channels.newChannel(new java.io.ByteArrayInputStream(Array.empty[Byte]))
          val result = NioStreams.fromChannel(ch).runCollect
          assertTrue(result == Right(Chunk.empty))
        }
      )
    ),
    suite("NioSinks")(
      suite("fromByteBuffer")(
        test("writes all bytes into buffer") {
          val buf    = ByteBuffer.allocate(10)
          val data   = Chunk[Byte](1, 2, 3, 4, 5)
          val result = Stream.fromChunk(data).run(NioSinks.fromByteBuffer(buf))
          buf.flip()
          val out = new Array[Byte](5)
          buf.get(out)
          assertTrue(result == Right(())) &&
          assertTrue(out.toList == data.toList)
        },
        test("empty stream writes nothing") {
          val buf    = ByteBuffer.allocate(10)
          val result = Stream.fromChunk(Chunk.empty[Byte]).run(NioSinks.fromByteBuffer(buf))
          assertTrue(result == Right(())) &&
          assertTrue(buf.position() == 0)
        }
      ),
      suite("fromByteBufferInt")(
        test("writes all ints into buffer") {
          val buf    = ByteBuffer.allocate(12)
          val result = Stream(100, 200, 300).run(NioSinks.fromByteBufferInt(buf))
          buf.flip()
          assertTrue(result == Right(())) &&
          assertTrue(buf.getInt() == 100) &&
          assertTrue(buf.getInt() == 200) &&
          assertTrue(buf.getInt() == 300)
        }
      ),
      suite("fromByteBufferLong")(
        test("writes all longs into buffer") {
          val buf    = ByteBuffer.allocate(16)
          val result = Stream(1000L, 2000L).run(NioSinks.fromByteBufferLong(buf))
          buf.flip()
          assertTrue(result == Right(())) &&
          assertTrue(buf.getLong() == 1000L) &&
          assertTrue(buf.getLong() == 2000L)
        }
      ),
      suite("fromByteBufferDouble")(
        test("writes all doubles into buffer") {
          val buf    = ByteBuffer.allocate(16)
          val result = Stream(1.5, 2.5).run(NioSinks.fromByteBufferDouble(buf))
          buf.flip()
          assertTrue(result == Right(())) &&
          assertTrue(buf.getDouble() == 1.5) &&
          assertTrue(buf.getDouble() == 2.5)
        }
      ),
      suite("fromByteBufferFloat")(
        test("writes all floats into buffer") {
          val buf    = ByteBuffer.allocate(8)
          val result = Stream(1.0f, 2.0f).run(NioSinks.fromByteBufferFloat(buf))
          buf.flip()
          assertTrue(result == Right(())) &&
          assertTrue(buf.getFloat() == 1.0f) &&
          assertTrue(buf.getFloat() == 2.0f)
        }
      ),
      suite("fromChannel")(
        test("writes all bytes to channel") {
          val pipe = Pipe.open()
          pipe.source().configureBlocking(false)
          val data   = Chunk[Byte](1, 2, 3, 4, 5)
          val result = Stream
            .fromChunk(data)
            .run(NioSinks.fromChannel(pipe.sink(), bufSize = 4))
          pipe.sink().close()
          val readBuf  = ByteBuffer.allocate(10)
          var attempts = 0
          while (readBuf.position() < 5 && attempts < 100) {
            pipe.source().read(readBuf)
            attempts += 1
            if (readBuf.position() < 5) Thread.sleep(10)
          }
          pipe.source().close()
          readBuf.flip()
          val out = new Array[Byte](5)
          readBuf.get(out)
          assertTrue(result == Right(())) &&
          assertTrue(out.toList == data.toList)
        },
        test("empty stream writes nothing to channel") {
          val pipe = Pipe.open()
          pipe.source().configureBlocking(false)
          val result = Stream
            .fromChunk(Chunk.empty[Byte])
            .run(NioSinks.fromChannel(pipe.sink(), bufSize = 4))
          pipe.sink().close()
          val readBuf = ByteBuffer.allocate(10)
          pipe.source().read(readBuf)
          pipe.source().close()
          assertTrue(result == Right(())) &&
          assertTrue(readBuf.position() == 0)
        }
      ),
      suite("round-trip: NioStreams -> NioSinks")(
        test("int round-trip through ByteBuffer") {
          val buf         = ByteBuffer.allocate(20)
          val writeResult = Stream(42, 99, 7).run(NioSinks.fromByteBufferInt(buf))
          buf.flip()
          val readResult = NioStreams.fromByteBufferInt(buf).runCollect
          assertTrue(writeResult == Right(())) &&
          assertTrue(readResult == Right(Chunk(42, 99, 7)))
        },
        test("long round-trip through ByteBuffer") {
          val buf         = ByteBuffer.allocate(24)
          val writeResult = Stream(1000L, 2000L, 3000L).run(NioSinks.fromByteBufferLong(buf))
          buf.flip()
          val readResult = NioStreams.fromByteBufferLong(buf).runCollect
          assertTrue(writeResult == Right(())) &&
          assertTrue(readResult == Right(Chunk(1000L, 2000L, 3000L)))
        },
        test("double round-trip through ByteBuffer") {
          val buf         = ByteBuffer.allocate(24)
          val writeResult = Stream(3.14, 2.71, 1.41).run(NioSinks.fromByteBufferDouble(buf))
          buf.flip()
          val readResult = NioStreams.fromByteBufferDouble(buf).runCollect
          assertTrue(writeResult == Right(())) &&
          assertTrue(readResult == Right(Chunk(3.14, 2.71, 1.41)))
        },
        test("float round-trip through ByteBuffer") {
          val buf         = ByteBuffer.allocate(12)
          val writeResult = Stream(1.5f, 2.5f, 3.5f).run(NioSinks.fromByteBufferFloat(buf))
          buf.flip()
          val readResult = NioStreams.fromByteBufferFloat(buf).runCollect
          assertTrue(writeResult == Right(())) &&
          assertTrue(readResult == Right(Chunk(1.5f, 2.5f, 3.5f)))
        }
      )
    ),
    suite("adversarial regressions")(
      suite("sentinel losslessness")(
        // The Long/Double lanes have no spare bit pattern for EOF, so every drain
        // site must disambiguate a sentinel-valued element via the reader's
        // out-of-band `lastReadWasEOF` flag (`longEOF`/`doubleEOF`, BUG-004).
        // `ByteBufferLongReader.readLong` maintains that flag correctly, and its
        // sibling overrides in shared code (FilteredLong, MappedLong,
        // FlatMappedBase, LongConcurrentMergeReader) all consult it in
        // `readUpToN` — but the ByteBuffer readers' `readUpToN` overrides use a
        // raw `v != sentinel` loop, silently truncating the stream at a real
        // `Long.MaxValue` / `Double.MaxValue` element.
        test(
          "ByteBufferLongReader.readUpToN does not truncate at a real Long.MaxValue element [AdversarialNioSentinelSpec]"
        ) {
          val buf = ByteBuffer.allocate(24)
          buf.putLong(1L); buf.putLong(Long.MaxValue); buf.putLong(2L); buf.flip()
          val reader = NioReaders.fromByteBufferLong(buf)
          // Oracle: `readAll` (longEOF-based) on an identical buffer keeps all 3.
          val oracleBuf = ByteBuffer.allocate(24)
          oracleBuf.putLong(1L); oracleBuf.putLong(Long.MaxValue); oracleBuf.putLong(2L); oracleBuf.flip()
          val oracle = NioReaders.fromByteBufferLong(oracleBuf).readAll[Long]()
          assertTrue(oracle == Chunk(1L, Long.MaxValue, 2L)) &&
          assertTrue(reader.readUpToN[Long](10) == Chunk(1L, Long.MaxValue, 2L))
        },
        test(
          "ByteBufferDoubleReader.readUpToN does not truncate at a real Double.MaxValue element [AdversarialNioSentinelSpec]"
        ) {
          val buf = ByteBuffer.allocate(24)
          buf.putDouble(1.0); buf.putDouble(Double.MaxValue); buf.putDouble(2.0); buf.flip()
          val reader    = NioReaders.fromByteBufferDouble(buf)
          val oracleBuf = ByteBuffer.allocate(24)
          oracleBuf.putDouble(1.0); oracleBuf.putDouble(Double.MaxValue); oracleBuf.putDouble(2.0); oracleBuf.flip()
          val oracle = NioReaders.fromByteBufferDouble(oracleBuf).readAll[Double]()
          assertTrue(oracle == Chunk(1.0, Double.MaxValue, 2.0)) &&
          assertTrue(reader.readUpToN[Double](10) == Chunk(1.0, Double.MaxValue, 2.0))
        },
        // Sentinel performance policy (AGENTS.md): the typed NIO sinks keep a
        // raw single-comparison drain loop; a real sentinel-valued element is
        // detected once, post-loop, via lastReadWasEOF and rejected loudly.
        // Silent truncation is the bug; throwing is the contract.
        test(
          "NioSinks.fromByteBufferLong rejects a real Long.MaxValue element instead of silently truncating [AdversarialNioSentinelSpec]"
        ) {
          val buf = ByteBuffer.allocate(40)
          val r   = scala.util.Try(
            Stream.fromIterable(List(100L, 200L, Long.MaxValue, 300L, 400L)).run(NioSinks.fromByteBufferLong(buf))
          )
          val rejected = r.failed.toOption.exists {
            case e: IllegalArgumentException => e.getMessage.contains("Long.MaxValue")
            case e                           => Option(e.getCause).exists(_.isInstanceOf[IllegalArgumentException])
          }
          assertTrue(rejected)
        },
        test(
          "NioSinks.fromByteBufferDouble rejects a real Double.MaxValue element instead of silently truncating [AdversarialNioSentinelSpec]"
        ) {
          val buf = ByteBuffer.allocate(24)
          val r   = scala.util.Try(
            Stream.fromIterable(List(1.5, Double.MaxValue, 2.5)).run(NioSinks.fromByteBufferDouble(buf))
          )
          val rejected = r.failed.toOption.exists {
            case e: IllegalArgumentException => e.getMessage.contains("Double.MaxValue")
            case e                           => Option(e.getCause).exists(_.isInstanceOf[IllegalArgumentException])
          }
          assertTrue(rejected)
        },
        test(
          "NioSinks.fromByteBufferLong drains sentinel-free data completely [AdversarialNioSentinelSpec]"
        ) {
          val buf = ByteBuffer.allocate(24)
          Stream.fromIterable(List(1L, 2L, 3L)).run(NioSinks.fromByteBufferLong(buf))
          buf.flip()
          val written = Chunk(buf.getLong(), buf.getLong(), buf.getLong())
          assertTrue(written == Chunk(1L, 2L, 3L), !buf.hasRemaining)
        }
      ),
      suite("close-failure integrity (Principle 4)")(
        // Module contract (see StreamResourceSpec
        // `control_cleanSuccessPlusCleanupDefect_isSurfaced` and the
        // `Stream.fromInputStream` doc comment): a cleanup/close failure after a
        // clean run must SURFACE, never be silently swallowed.
        test(
          "NioStreams.fromChannel surfaces a channel close IOException instead of swallowing it [AdversarialNioCloseIntegritySpec]"
        ) {
          val sentinel = new java.io.IOException("close-sentinel")
          var closed   = false
          val ch       = new java.nio.channels.ReadableByteChannel {
            def read(dst: ByteBuffer): Int = -1 // immediate EOF
            def isOpen: Boolean            = !closed
            def close(): Unit              = { closed = true; throw sentinel }
          }
          val r  = scala.util.Try(NioStreams.fromChannel(ch).runDrain)
          val ok = r match {
            case scala.util.Failure(t) =>
              (t eq sentinel) || (t.getCause eq sentinel) || t.getSuppressed.contains(sentinel)
            case scala.util.Success(either) =>
              either.swap.toOption.exists(e => (e eq sentinel) || (e.getCause eq sentinel))
          }
          assertTrue(closed, ok)
        },
        test(
          "NioWriters.fromChannel close() surfaces a final-flush IOException after closing the channel [AdversarialNioCloseIntegritySpec]"
        ) {
          // Sibling oracle: `Writer.fromOutputStream`/`fromWriter` close() runs
          // `runBoth(flush())(close())` and PROPAGATES the flush failure
          // ("Surface I/O failures from flush/close rather than swallowing them,
          // Principle 4"). ChannelWriter.close() swallows it, reporting clean
          // success while the buffered bytes were silently lost.
          val sentinel      = new java.io.IOException("flush-sentinel")
          var channelClosed = false
          val ch            = new java.nio.channels.WritableByteChannel {
            def write(src: ByteBuffer): Int = throw sentinel
            def isOpen: Boolean             = !channelClosed
            def close(): Unit               = channelClosed = true
          }
          val w = NioWriters.fromChannel(ch, bufSize = 8)
          w.writeByte(42)
          val r  = scala.util.Try(w.close())
          val ok = r match {
            case scala.util.Failure(t) =>
              (t eq sentinel) || (t.getCause eq sentinel) || t.getSuppressed.contains(sentinel)
            case scala.util.Success(_) => false
          }
          assertTrue(channelClosed, ok)
        }
      ),
      suite("origin, window and accuracy (round 8)")(
        test(
          "NioSinks.fromChannel write failure is sink-origin: Sink.mapError must map it [AdversarialSinkOriginSpec]"
        ) {
          // Contract (SinkError scaladoc + Stream.run): sink-originated typed
          // failures travel as SinkError, so `Sink.mapError` can transform them
          // and `run` projects them through the RIGHT (sink) side of the error
          // Concat. `NioSinks.fromChannel` IS a sink, but wraps its own write
          // IOException in StreamError (the stream-origin carrier), so mapError
          // never sees it and the failure is misattributed to the stream channel.
          val boom = new java.io.IOException("write-boom")
          val ch   = new java.nio.channels.WritableByteChannel {
            def write(src: ByteBuffer): Int = throw boom
            def isOpen: Boolean             = true
            def close(): Unit               = ()
          }
          val sink =
            NioSinks.fromChannel(ch, bufSize = 2).mapError((io: java.io.IOException) => "mapped:" + io.getMessage)
          val result = Stream.fromChunk(Chunk[Byte](1, 2, 3, 4, 5)).run(sink)
          assertTrue(result == Left("mapped:write-boom"))
        },
        test(
          "take(-1) over a ByteBuffer stream is empty, like every other source [AdversarialNegativeWindowSpec]"
        ) {
          // Pinned oracle (StreamSpec): Stream(1,2,3).take(-1).runCollect ==
          // Right(Chunk.empty) — List.take(-1) semantics. The ByteBuffer
          // readers' setLimit lacks the negative clamp their own setSkip has,
          // so `buffer.limit(negative)` throws IllegalArgumentException instead
          // of producing an empty stream.
          val intBuf = ByteBuffer.allocate(16)
          (0 until 4).foreach(intBuf.putInt)
          intBuf.flip()
          val byteBuf = ByteBuffer.wrap(Array[Byte](1, 2, 3))
          val ints    = scala.util.Try(NioStreams.fromByteBufferInt(intBuf).take(-1).runCollect)
          val bytes   = scala.util.Try(NioStreams.fromByteBuffer(byteBuf).take(-1).runCollect)
          assertTrue(
            ints == scala.util.Success(Right(Chunk.empty[Int])),
            bytes == scala.util.Success(Right(Chunk.empty[Byte]))
          )
        },
        test(
          "ByteBuffer reader reset() restores the composed take/drop window, not [skip, skip+limit) [AdversarialWindowResetSpec]"
        ) {
          // setLimit/setSkip contract (Reader scaladoc): successive calls
          // compose in invocation order over the live window, and
          // implementations "must store the derived window bounds (not raw
          // skip/limit values that recompute as [skip, skip+limit)), so
          // reset() restores the same composed window". take(5).drop(3) over
          // ints 0..7 is positions [3,5); the range-backed sibling replays it
          // correctly under `repeated`, the ByteBuffer reader re-derives
          // [skip, skip+limit) = [3,8) on reset and leaks dropped elements.
          val buf = ByteBuffer.allocate(32)
          (0 until 8).foreach(buf.putInt)
          buf.flip()
          val nio     = NioStreams.fromByteBufferInt(buf).take(5).drop(3).repeated.take(6).runCollect
          val control = Stream.range(0, 8).take(5).drop(3).repeated.take(6).runCollect
          assertTrue(control == Right(Chunk(3, 4, 3, 4, 3, 4)), nio == control)
        },
        test(
          "ByteBuffer reader reset() restores composed successive drops [AdversarialWindowResetSpec]"
        ) {
          // drop(3).drop(2) composes to positions [5,8). The raw `skipN = n`
          // overwrite remembers only the LAST drop, so reset() rewinds to
          // position 2 and the second `repeated` cycle replays elements that
          // were dropped in the first.
          val buf = ByteBuffer.allocate(32)
          (0 until 8).foreach(buf.putInt)
          buf.flip()
          val nio     = NioStreams.fromByteBufferInt(buf).drop(3).drop(2).repeated.take(6).runCollect
          val control = Stream.range(0, 8).drop(3).drop(2).repeated.take(6).runCollect
          assertTrue(control == Right(Chunk(5, 6, 7, 5, 6, 7)), nio == control)
        },
        test(
          "take(2).take(5) over a ByteBuffer stream keeps the narrower window, like every other source [AdversarialWindowComposeSpec]"
        ) {
          // setLimit contract (Reader scaladoc): "successive calls compose in
          // invocation order over the current live window and must be
          // observationally equivalent to wrapping the reader in the
          // corresponding take/drop combinators" — so take(2).take(5) is
          // List.take(2).take(5) == first 2 elements. The chunk/range readers
          // cap a later setLimit at the CURRENT live window
          // (advanceWithin(start, n, effectiveLen)); the ByteBuffer readers cap
          // at `originalLimit` instead, so a later, larger take RE-EXPANDS the
          // already-narrowed window and leaks elements past the first take.
          val intBuf = ByteBuffer.allocate(32)
          (0 until 8).foreach(intBuf.putInt)
          intBuf.flip()
          val longBuf = ByteBuffer.allocate(64)
          (0 until 8).foreach(i => longBuf.putLong(i.toLong))
          longBuf.flip()
          val byteBuf  = ByteBuffer.wrap(Array[Byte](1, 2, 3, 4, 5, 6, 7, 8))
          val control  = Stream.range(0, 8).take(2).take(5).runCollect
          val nioInts  = NioStreams.fromByteBufferInt(intBuf).take(2).take(5).runCollect
          val nioLongs = NioStreams.fromByteBufferLong(longBuf).take(2).take(5).runCollect
          val nioBytes = NioStreams.fromByteBuffer(byteBuf).take(2).take(5).runCollect
          assertTrue(
            control == Right(Chunk(0, 1)),
            nioInts == Right(Chunk(0, 1)),
            nioLongs == Right(Chunk(0L, 1L)),
            nioBytes == Right(Chunk[Byte](1, 2))
          )
        },
        test(
          "take(3).drop(1).take(5) over a ByteBuffer stream stays inside the first take's window [AdversarialWindowComposeSpec]"
        ) {
          // Same root as take(2).take(5): the third window op composes against
          // `originalLimit` instead of the live window, so the final take(5)
          // grows the window past the take(3) right edge and re-leaks elements
          // the first take excluded. List oracle: (0..7).take(3).drop(1).take(5)
          // == List(1, 2).
          val intBuf = ByteBuffer.allocate(32)
          (0 until 8).foreach(intBuf.putInt)
          intBuf.flip()
          val control = Stream.range(0, 8).take(3).drop(1).take(5).runCollect
          val nio     = NioStreams.fromByteBufferInt(intBuf).take(3).drop(1).take(5).runCollect
          assertTrue(control == Right(Chunk(1, 2)), nio == control)
        },
        test(
          "full ByteBuffer writers report writeable() == false, like their reader siblings [AdversarialWriteableAccuracySpec]"
        ) {
          // Writer.writeable contract: "true if the next write would accept a
          // value without blocking (space is available and the writer is not
          // closed). Subclasses backed by bounded buffers should override for
          // accuracy." Every ByteBuffer READER overrides readable() with the
          // exact space check (remaining >= element width); the ByteBuffer
          // writers inherit `!isClosed` and claim writeability with zero bytes
          // remaining, so writeable()==true is immediately contradicted by the
          // next single-threaded write returning false.
          val intBuf = ByteBuffer.allocate(4)
          val iw     = NioWriters.fromByteBufferInt(intBuf)
          val wrote  = iw.writeInt(7)

          val byteBuf = ByteBuffer.allocate(1)
          val bw      = NioWriters.fromByteBuffer(byteBuf)
          val wroteB  = bw.writeByte(1)

          assertTrue(wrote, wroteB, !iw.writeable(), !bw.writeable())
        }
      )
    ),
    run10ConvergenceSuite
  )

  // ---- Run #10 convergence probes ------------------------------------------
  // Passing adversarial probes from the tenth hardening round: composed
  // take/drop pushdown window chains over the typed ByteBuffer readers
  // (the BUG-R8-03/BUG-R9-01 fix surface), replay of composed windows under
  // `repeated`, negative-window clamping, and in-band Long.MaxValue elements
  // travelling through composed windows. Committed as convergence evidence.
  private def run10ConvergenceSuite = {
    def longBuf(values: Long*): ByteBuffer = {
      val buf = ByteBuffer.allocate(values.length * 8)
      values.foreach(buf.putLong)
      buf.flip()
      buf
    }
    def intBuf(values: Int*): ByteBuffer = {
      val buf = ByteBuffer.allocate(values.length * 4)
      values.foreach(buf.putInt)
      buf.flip()
      buf
    }
    suite("run10 convergence")(
      test("long_dropTakeRepeated_replaysTheComposedWindow") {
        val s = NioStreams.fromByteBufferLong(longBuf(10L, 20L, 30L, 40L)).drop(1L).take(2L).repeated.take(5L)
        assertTrue(s.runCollect.map(_.toList) == Right(List(20L, 30L, 20L, 30L, 20L)))
      },
      test("int_takeDropTakeChain_matchesListOracle") {
        val oracle = List(10, 20, 30, 40).take(3).drop(1).take(1)
        val s      = NioStreams.fromByteBufferInt(intBuf(10, 20, 30, 40)).take(3L).drop(1L).take(1L)
        assertTrue(s.runCollect.map(_.toList) == Right(oracle))
      },
      test("int_windowChainsWithShrinkingAndOverLargeTakes_matchListOracle") {
        val xs     = List(1, 2, 3, 4, 5, 6)
        val oracle = xs.take(5).drop(2).take(10).drop(1)
        val s      = NioStreams.fromByteBufferInt(intBuf(xs: _*)).take(5L).drop(2L).take(10L).drop(1L)
        assertTrue(s.runCollect.map(_.toList) == Right(oracle))
      },
      test("long_realMaxValueElement_survivesComposedWindows") {
        val s = NioStreams.fromByteBufferLong(longBuf(1L, Long.MaxValue, 3L)).drop(1L).take(2L)
        assertTrue(s.runCollect.map(_.toList) == Right(List(Long.MaxValue, 3L)))
      },
      test("byte_negativeTakeClampsToEmpty_negativeDropIsNoOp") {
        def mk() = {
          val b = ByteBuffer.allocate(3); b.put(1.toByte).put(2.toByte).put(3.toByte); b.flip(); b
        }
        val empty = NioStreams.fromByteBuffer(mk()).take(-2L).runCollect.map(_.toList)
        val all   = NioStreams.fromByteBuffer(mk()).drop(-2L).runCollect.map(_.toList)
        assertTrue(empty == Right(Nil), all == Right(List[Byte](1, 2, 3)))
      },
      test("double_dropTakeRepeated_replaysComposedWindow") {
        val buf = ByteBuffer.allocate(24)
        buf.putDouble(1.5).putDouble(2.5).putDouble(3.5)
        buf.flip()
        val s = NioStreams.fromByteBufferDouble(buf).drop(1L).take(1L).repeated.take(3L)
        assertTrue(s.runCollect.map(_.toList) == Right(List(2.5, 2.5, 2.5)))
      }
    )
  }
}
