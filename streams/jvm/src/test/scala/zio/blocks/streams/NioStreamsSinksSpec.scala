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

object NioStreamsSinksSpec extends StreamsBaseSpec {

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
    )
  )
}
