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

import zio.blocks.streams.io.Writer
import zio.test._
import zio.test.Assertion._

import java.io.{ByteArrayOutputStream, IOException, OutputStream, StringWriter, Writer => JWriter}

object WriterFromJavaSpec extends StreamsBaseSpec {

  private final class FailableByteArrayOutputStream extends OutputStream {
    private val buf                  = new ByteArrayOutputStream()
    @volatile var boom               = false
    def written: Array[Byte]         = buf.toByteArray
    override def write(b: Int): Unit = {
      if (boom) throw new IOException("boom")
      buf.write(b)
    }
    override def write(b: Array[Byte], off: Int, len: Int): Unit = {
      if (boom) throw new IOException("boom")
      buf.write(b, off, len)
    }
    override def flush(): Unit = ()
  }

  private final class TrackingOutputStream(delegate: OutputStream) extends OutputStream {
    @volatile var flushed                                        = false
    @volatile var closed                                         = false
    override def write(b: Int): Unit                             = delegate.write(b)
    override def write(b: Array[Byte], off: Int, len: Int): Unit = delegate.write(b, off, len)
    override def flush(): Unit                                   = { flushed = true; delegate.flush() }
    override def close(): Unit                                   = { closed = true; delegate.close() }
  }

  private final class TrackingWriter(delegate: JWriter) extends JWriter {
    @volatile var flushed                                           = false
    @volatile var closed                                            = false
    override def write(cbuf: Array[Char], off: Int, len: Int): Unit = delegate.write(cbuf, off, len)
    override def flush(): Unit                                      = { flushed = true; delegate.flush() }
    override def close(): Unit                                      = { closed = true; delegate.close() }
  }

  def spec: Spec[TestEnvironment, Any] = suite("Writer.fromOutputStream / fromWriter")(
    suite("fromOutputStream")(
      test("isClosed starts false") {
        val enq = Writer.fromOutputStream(new ByteArrayOutputStream())
        assert(enq.isClosed)(isFalse)
      },
      test("write writes byte to underlying stream") {
        val bos = new ByteArrayOutputStream()
        val enq = Writer.fromOutputStream(bos)
        val ok  = enq.write(65.toByte)
        assert(ok)(isTrue) &&
        assert(bos.toByteArray.toList)(equalTo(List[Byte](65)))
      },
      test("write returns false after close") {
        val enq = Writer.fromOutputStream(new ByteArrayOutputStream())
        enq.close()
        assert(enq.write(1.toByte))(isFalse)
      },
      test("writeByte writes directly to stream") {
        val bos = new ByteArrayOutputStream()
        val enq = Writer.fromOutputStream(bos)
        enq.writeByte(7.toByte)
        assert(bos.toByteArray.toList)(equalTo(List[Byte](7)))
      },
      test("writeBytes writes full slice to stream") {
        val bos  = new ByteArrayOutputStream()
        val enq  = Writer.fromOutputStream(bos)
        val data = Array[Byte](1, 2, 3, 4, 5)
        val n    = enq.writeBytes(data, 1, 3)
        assert(n)(equalTo(3)) &&
        assert(bos.toByteArray.toList)(equalTo(List[Byte](2, 3, 4)))
      },
      test("writeBytes with len=0 returns 0 without writing") {
        val bos = new ByteArrayOutputStream()
        val enq = Writer.fromOutputStream(bos)
        val n   = enq.writeBytes(new Array[Byte](0), 0, 0)
        assert(n)(equalTo(0)) &&
        assert(bos.size())(equalTo(0))
      },
      test("writeBytes returns 0 after close") {
        val bos = new ByteArrayOutputStream()
        val enq = Writer.fromOutputStream(bos)
        enq.close()
        val n = enq.writeBytes(Array[Byte](1, 2), 0, 2)
        assert(n)(equalTo(0))
      },
      test("writeAll(Chunk.empty) returns Chunk.empty without writing") {
        import zio.blocks.chunk.Chunk
        val bos = new ByteArrayOutputStream()
        val enq = Writer.fromOutputStream(bos)
        val rem = enq.writeAll(Chunk.empty[Byte])
        assert(rem)(equalTo(Chunk.empty[Byte])) &&
        assert(bos.size())(equalTo(0))
      },
      test("close(Right(())) flushes then closes the underlying stream") {
        val bos     = new ByteArrayOutputStream()
        val tracker = new TrackingOutputStream(bos)
        val enq     = Writer.fromOutputStream(tracker)
        enq.write(1.toByte)
        enq.close()
        assert(tracker.flushed)(isTrue) &&
        assert(tracker.closed)(isTrue) &&
        assert(enq.isClosed)(isTrue)
      },
      test("close is idempotent") {
        val enq = Writer.fromOutputStream(new ByteArrayOutputStream())
        enq.close()
        enq.close()
        assert(enq.isClosed)(isTrue)
      },
      test("close() flushes and closes the stream") {
        val bos     = new ByteArrayOutputStream()
        val tracker = new TrackingOutputStream(bos)
        val enq     = Writer.fromOutputStream(tracker)
        enq.close()
        assert(tracker.closed)(isTrue) &&
        assert(tracker.flushed)(isTrue) &&
        assert(enq.isClosed)(isTrue)
      },
      test("IOException during write returns false and marks closed") {
        val os  = new FailableByteArrayOutputStream()
        val enq = Writer.fromOutputStream(os)
        os.boom = true
        val ok = enq.write(1.toByte)
        assert(ok)(isFalse) &&
        assert(enq.isClosed)(isTrue)
      },
      test("IOException during writeBytes returns 0 and marks closed") {
        val os  = new FailableByteArrayOutputStream()
        val enq = Writer.fromOutputStream(os)
        os.boom = true
        val n = enq.writeBytes(Array[Byte](1, 2, 3), 0, 3)
        assert(n)(equalTo(0)) &&
        assert(enq.isClosed)(isTrue)
      }
    ),

    suite("fromWriter")(
      test("isClosed starts false") {
        val enq = Writer.fromWriter(new StringWriter())
        assert(enq.isClosed)(isFalse)
      },
      test("write writes char to underlying Writer") {
        val sw  = new StringWriter()
        val enq = Writer.fromWriter(sw)
        val ok  = enq.write('A')
        assert(ok)(isTrue) &&
        assert(sw.toString)(equalTo("A"))
      },
      test("write returns false after close") {
        val enq = Writer.fromWriter(new StringWriter())
        enq.close()
        assert(enq.write('Z'))(isFalse)
      },
      test("writeAll writes full chunk to underlying Writer as bulk array write") {
        import zio.blocks.chunk.Chunk
        val sw  = new StringWriter()
        val enq = Writer.fromWriter(sw)
        val rem = enq.writeAll(Chunk('h', 'i', '!'))
        assert(rem)(equalTo(Chunk.empty[Char])) &&
        assert(sw.toString)(equalTo("hi!"))
      },
      test("writeAll(Chunk.empty) returns Chunk.empty without writing") {
        import zio.blocks.chunk.Chunk
        val sw  = new StringWriter()
        val enq = Writer.fromWriter(sw)
        val rem = enq.writeAll(Chunk.empty[Char])
        assert(rem)(equalTo(Chunk.empty[Char])) &&
        assert(sw.toString)(equalTo(""))
      },
      test("close(Right(())) flushes then closes the underlying Writer") {
        val sw      = new StringWriter()
        val tracker = new TrackingWriter(sw)
        val enq     = Writer.fromWriter(tracker)
        enq.write('x')
        enq.close()
        assert(tracker.flushed)(isTrue) &&
        assert(tracker.closed)(isTrue) &&
        assert(enq.isClosed)(isTrue)
      },
      test("close is idempotent") {
        val enq = Writer.fromWriter(new StringWriter())
        enq.close()
        enq.close()
        assert(enq.isClosed)(isTrue)
      },
      test("close() flushes and closes Writer") {
        val sw      = new StringWriter()
        val tracker = new TrackingWriter(sw)
        val enq     = Writer.fromWriter(tracker)
        enq.close()
        assert(tracker.closed)(isTrue) &&
        assert(tracker.flushed)(isTrue) &&
        assert(enq.isClosed)(isTrue)
      }
    )
  )
}
