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
import zio.blocks.streams.internal.StreamError
import zio.test._

import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.{Channels, Pipe, ReadableByteChannel, WritableByteChannel}
import scala.concurrent.ExecutionContext

object NioStreamsSinksSpec extends StreamsBaseSpec {

  private implicit val ec: ExecutionContext = ExecutionContext.global

  def spec: Spec[TestEnvironment, Any] = suite("NioStreams / NioSinks")(
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
        },
        test("unmanaged channel remains open after the stream completes") {
          val ch     = Channels.newChannel(new java.io.ByteArrayInputStream(Array[Byte](1, 2, 3)))
          val result = NioStreams.fromChannelUnmanaged(ch).runCollect
          assertTrue(result == Right(Chunk[Byte](1, 2, 3)), ch.isOpen)
        },
        test("replays a close-only failure and closes exactly once") {
          val closeFailure = new IOException("channel close")
          var closeCount   = 0
          val ch           = new ReadableByteChannel {
            def isOpen: Boolean            = true
            def read(dst: ByteBuffer): Int = -1
            def close(): Unit              = {
              closeCount += 1
              throw closeFailure
            }
          }
          val reader = NioStreams.fromChannel(ch).compile(0).expectedSync
          val first  = try { reader.close(); null }
          catch { case t: Throwable => t }
          val second = try { reader.close(); null }
          catch { case t: Throwable => t }

          assertTrue(first eq closeFailure, second eq closeFailure, closeCount == 1)
        },
        test("keeps read failure primary and suppresses close failure") {
          val readFailure  = new IOException("channel read")
          val closeFailure = new IOException("channel close")
          var closeCount   = 0
          val ch           = new ReadableByteChannel {
            def isOpen: Boolean            = true
            def read(dst: ByteBuffer): Int = throw readFailure
            def close(): Unit              = {
              closeCount += 1
              throw closeFailure
            }
          }
          val caught = try { NioStreams.fromChannel(ch).runDrain; null }
          catch { case t: Throwable => t }

          assertTrue(
            caught.isInstanceOf[StreamError],
            caught.asInstanceOf[StreamError].value.asInstanceOf[AnyRef] eq readFailure,
            caught.getSuppressed.toList == List(closeFailure),
            closeCount == 1
          )
        }
      )
    ),
    suite("NioSinks")(
      test("byte sinks accept deliberately boxed bytes") {
        val byteBuffer = ByteBuffer.allocate(1)
        val output     = new java.io.ByteArrayOutputStream
        val channel    = Channels.newChannel(output)
        val stream     = Stream.succeed[Byte](1)
        val buffered   = stream.run(NioSinks.fromByteBuffer(byteBuffer))
        val channeled  = stream.run(NioSinks.fromChannel(channel, 1))
        byteBuffer.flip()
        assertTrue(
          stream.elementRepresentation == ElementRepresentation.Boxed,
          buffered == Right(()),
          channeled == Right(()),
          byteBuffer.get() == 1.toByte,
          output.toByteArray.toList == List[Byte](1)
        )
      },
      test("all ByteBuffer sinks use their asynchronous drains") {
        val bytes   = ByteBuffer.allocate(2)
        val ints    = ByteBuffer.allocate(8)
        val longs   = ByteBuffer.allocate(16)
        val floats  = ByteBuffer.allocate(8)
        val doubles = ByteBuffer.allocate(16)
        for {
          rb <- runAsync(Stream[Byte](1, -1).runAsync(NioSinks.fromByteBuffer(bytes)))
          ri <- runAsync(Stream(1, 2).runAsync(NioSinks.fromByteBufferInt(ints)))
          rl <- runAsync(Stream(1L, 2L).runAsync(NioSinks.fromByteBufferLong(longs)))
          rf <- runAsync(Stream(1.0f, 2.0f).runAsync(NioSinks.fromByteBufferFloat(floats)))
          rd <- runAsync(Stream(1.0, 2.0).runAsync(NioSinks.fromByteBufferDouble(doubles)))
        } yield {
          bytes.flip(); ints.flip(); longs.flip(); floats.flip(); doubles.flip()
          assertTrue(
            rb == Right(()),
            ri == Right(()),
            rl == Right(()),
            rf == Right(()),
            rd == Right(()),
            bytes.get() == 1.toByte,
            bytes.get() == (-1).toByte,
            ints.getInt() == 1,
            ints.getInt() == 2,
            longs.getLong() == 1L,
            longs.getLong() == 2L,
            floats.getFloat() == 1.0f,
            floats.getFloat() == 2.0f,
            doubles.getDouble() == 1.0,
            doubles.getDouble() == 2.0
          )
        }
      },
      test("asynchronous channel sink writes bytes and preserves typed IOException") {
        val output  = new java.io.ByteArrayOutputStream
        val ok      = Channels.newChannel(output)
        val failure = new IOException("write")
        val failed  = new WritableByteChannel {
          def isOpen: Boolean             = true
          def close(): Unit               = ()
          def write(src: ByteBuffer): Int = throw failure
        }
        for {
          success <- runAsync(Stream[Byte](1, 2, 3).runAsync(NioSinks.fromChannel(ok, 2)))
          error   <- runAsync(Stream[Byte](1).runAsync(NioSinks.fromChannel(failed, 1)))
        } yield assertTrue(
          success == Right(()),
          output.toByteArray.toList == List[Byte](1, 2, 3),
          error == Left(failure)
        )
      },
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
        test("primitive sink values cannot collide with end-of-stream markers") {
          val ints         = Array(Int.MinValue, Int.MaxValue)
          val longs        = Array(Long.MinValue, Long.MaxValue)
          val floats       = Array(Float.MinValue, Float.MaxValue, Float.NaN)
          val doubles      = Array(Double.MinValue, Double.MaxValue, Double.NaN)
          val intBuffer    = ByteBuffer.allocate(ints.length * 4)
          val longBuffer   = ByteBuffer.allocate(longs.length * 8)
          val floatBuffer  = ByteBuffer.allocate(floats.length * 4)
          val doubleBuffer = ByteBuffer.allocate(doubles.length * 8)
          val intResult    = Stream.fromIterable(ints).run(NioSinks.fromByteBufferInt(intBuffer))
          val longResult   = Stream.fromIterable(longs).run(NioSinks.fromByteBufferLong(longBuffer))
          val floatResult  = Stream.fromIterable(floats).run(NioSinks.fromByteBufferFloat(floatBuffer))
          val doubleResult = Stream.fromIterable(doubles).run(NioSinks.fromByteBufferDouble(doubleBuffer))
          intBuffer.flip(); longBuffer.flip(); floatBuffer.flip(); doubleBuffer.flip()
          val actualInts    = Array.fill(ints.length)(intBuffer.getInt())
          val actualLongs   = Array.fill(longs.length)(longBuffer.getLong())
          val actualFloats  = Array.fill(floats.length)(floatBuffer.getFloat())
          val actualDoubles = Array.fill(doubles.length)(doubleBuffer.getDouble())
          assertTrue(
            intResult == Right(()),
            longResult == Right(()),
            floatResult == Right(()),
            doubleResult == Right(()),
            actualInts.toList == ints.toList,
            actualLongs.toList == longs.toList,
            actualFloats.take(2).toList == floats.take(2).toList,
            actualFloats.last.isNaN,
            actualDoubles.take(2).toList == doubles.take(2).toList,
            actualDoubles.last.isNaN
          )
        },
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
    suite("NioWriters deferred operations")(
      test("ByteBuffer writers perform writes only when the async mirror is driven") {
        val buffer = ByteBuffer.allocate(2)
        val writer = NioWriters.fromByteBuffer(buffer)
        val write  = writer.writeByteAsync(42.toByte)
        val before = buffer.position()
        for {
          accepted <- runAsync(write)
          _        <- runAsync(writer.closeAsync())
        } yield assertTrue(before == 0, accepted, buffer.position() == 1, writer.isClosed)
      }
    )
  )
}
