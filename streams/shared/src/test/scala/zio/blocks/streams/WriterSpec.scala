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
import zio.blocks.streams.io.Writer
import zio.test._
import zio.test.Assertion._

import java.io.{ByteArrayOutputStream, IOException, OutputStream, StringWriter, Writer => JWriter}

import scala.collection.mutable.ArrayBuffer

object WriterSpec extends StreamsBaseSpec {

  /** A simple collecting Writer[A] that stores written elements in a buffer. */
  private final class CollectingWriter[A] extends Writer[A] {
    private var _closed                       = false
    private val buf                           = scala.collection.mutable.ArrayBuffer.empty[A]
    def isClosed: Boolean                     = _closed
    def write(a: A): Boolean                  = { if (_closed) return false; buf += a; true }
    def close(): Unit                         = _closed = true
    def collected: List[A]                    = buf.toList
    private var _error: Option[Throwable]     = None
    override def fail(error: Throwable): Unit = {
      _error = Some(error)
      _closed = true
    }
    def error: Option[Throwable] = _error
  }

  /**
   * A Writer[A] that records fail vs close distinctly and throws on write after
   * fail.
   */
  private final class FailAwareWriter[A] extends Writer[A] {
    private var _closed                   = false
    private var _error: Option[Throwable] = None
    def isClosed: Boolean                 = _closed
    def write(a: A): Boolean              = {
      _error.foreach(throw _)
      if (_closed) return false
      true
    }
    def close(): Unit                         = _closed = true
    override def fail(error: Throwable): Unit = {
      _error = Some(error)
      _closed = true
    }
  }

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

  /** A writer that records writes and auto-closes after `capacity` elements. */
  private final class Recorder(capacity: Int) extends Writer[Int] {
    val received               = ArrayBuffer.empty[Int]
    var closeCount             = 0
    var failCount              = 0
    var lastFail: Throwable    = null
    private var closed         = false
    def isClosed: Boolean      = closed
    def write(a: Int): Boolean = {
      if (closed) return false
      received += a
      if (received.length >= capacity) closed = true
      true
    }
    def close(): Unit                         = { closeCount += 1; closed = true }
    override def fail(error: Throwable): Unit = { failCount += 1; lastFail = error; closed = true }
  }

  private final class ByteRecorder extends Writer[Byte] {
    val received                = ArrayBuffer.empty[Byte]
    private var closed          = false
    def isClosed: Boolean       = closed
    def write(a: Byte): Boolean = { if (closed) return false; received += a; true }
    def close(): Unit           = closed = true
  }

  /** Records whether it was failed (and with what) vs cleanly closed. */
  private final class FailRecordingWriter extends Writer[Int] {
    var failedWith: Throwable                 = null
    var cleanClosed: Boolean                  = false
    private var closed                        = false
    def isClosed: Boolean                     = closed
    def write(a: Int): Boolean                = !closed
    def close(): Unit                         = { if (!closed) cleanClosed = true; closed = true }
    override def fail(error: Throwable): Unit = { if (!closed) failedWith = error; closed = true }
  }

  private final class CountingWriter(rejectFirstWrite: Boolean, failWrite: Boolean) extends Writer[Int] {
    var closeCount             = 0
    private var closed         = false
    def isClosed: Boolean      = closed
    def write(a: Int): Boolean = {
      if (failWrite) throw new RuntimeException("self-write-boom")
      if (rejectFirstWrite) { false }
      else true
    }
    def close(): Unit = { closeCount += 1; closed = true }
  }

  def spec: Spec[TestEnvironment, Any] = suite("Writer")(
    // ---- Writer.closed --------------------------------------------------------

    suite("Writer.closed")(
      test("isClosed is true") {
        assertTrue(Writer.closed.isClosed)
      },
      test("write returns false") {
        assertTrue(!Writer.closed.write(42))
      },
      test("write returns false for any type") {
        assertTrue(!Writer.closed.write("hello"))
      },
      test("close is idempotent (no exception)") {
        val w = Writer.closed
        w.close()
        w.close()
        assertTrue(w.isClosed)
      },
      test("fail is idempotent (no exception)") {
        val w = Writer.closed
        w.fail(new RuntimeException("test"))
        assertTrue(w.isClosed)
      },
      test("writeable returns false") {
        assertTrue(!Writer.closed.writeable())
      },
      test("writeAll returns entire chunk") {
        val chunk = Chunk(1, 2, 3)
        assertTrue(Writer.closed.writeAll(chunk) == chunk)
      },
      suite("regressions")(
        test("closed_writer_rejects_and_isClosed [AdversarialWriterCompositionSpec]") {
          val w = Writer.closed
          assertTrue(w.isClosed) && assertTrue(!w.write(1)) && assertTrue(w.writeAll(Chunk(1, 2)) == Chunk(1, 2))
        }
      )
    ),

    // ---- Writer.single --------------------------------------------------------

    suite("Writer.single")(
      test("isClosed starts false") {
        assertTrue(!Writer.single[Int].isClosed)
      },
      test("accepts one element") {
        val w = Writer.single[Int]
        assertTrue(w.write(42))
      },
      test("rejects second element") {
        val w = Writer.single[Int]
        w.write(1)
        assertTrue(!w.write(2))
      },
      test("isClosed is true after writing one element") {
        val w = Writer.single[Int]
        w.write(99)
        assertTrue(w.isClosed)
      },
      test("close marks as closed") {
        val w = Writer.single[Int]
        w.close()
        assertTrue(w.isClosed)
      },
      test("write returns false after close without prior write") {
        val w = Writer.single[Int]
        w.close()
        assertTrue(!w.write(1))
      },
      test("writeable starts true, becomes false after write") {
        val w       = Writer.single[String]
        val wBefore = w.writeable()
        w.write("x")
        val wAfter = w.writeable()
        assertTrue(wBefore) &&
        assertTrue(!wAfter)
      },
      suite("regressions")(
        test("single_accepts_exactly_one [AdversarialWriterCompositionSpec]") {
          val w = Writer.single[Int]
          assertTrue(w.write(1)) && assertTrue(!w.write(2)) && assertTrue(w.isClosed)
        }
      )
    ),

    // ---- Writer.limited -------------------------------------------------------

    suite("Writer.limited")(
      test("accepts exactly n elements") {
        val inner = new CollectingWriter[Int]
        val w     = Writer.limited(inner, 3)
        assertTrue(w.write(1)) &&
        assertTrue(w.write(2)) &&
        assertTrue(w.write(3)) &&
        assertTrue(inner.collected == List(1, 2, 3))
      },
      test("rejects element n+1") {
        val inner = new CollectingWriter[Int]
        val w     = Writer.limited(inner, 2)
        w.write(10)
        w.write(20)
        assertTrue(!w.write(30))
      },
      test("isClosed is false before limit reached") {
        val inner = new CollectingWriter[Int]
        val w     = Writer.limited(inner, 5)
        assertTrue(!w.isClosed)
      },
      test("isClosed is true after limit reached") {
        val inner = new CollectingWriter[Int]
        val w     = Writer.limited(inner, 1)
        w.write(1)
        assertTrue(w.isClosed)
      },
      test("limited(0) is immediately closed") {
        val inner = new CollectingWriter[Int]
        val w     = Writer.limited(inner, 0)
        assertTrue(w.isClosed) &&
        assertTrue(!w.write(1))
      },
      test("close delegates to inner") {
        val inner = new CollectingWriter[Int]
        val w     = Writer.limited(inner, 5)
        w.close()
        assertTrue(inner.isClosed)
      },
      test("fail delegates to inner") {
        val inner = new CollectingWriter[Int]
        val w     = Writer.limited(inner, 5)
        val err   = new RuntimeException("boom")
        w.fail(err)
        assertTrue(inner.error.contains(err))
      },
      test("isClosed reflects inner closed state") {
        val inner = new CollectingWriter[Int]
        val w     = Writer.limited(inner, 10)
        inner.close()
        assertTrue(w.isClosed)
      },
      suite("regressions")(
        test("limited_zero_isClosed_rejects [AdversarialWriterCompositionSpec]") {
          val r = new Recorder(100)
          val w = Writer.limited(r, 0)
          assertTrue(w.isClosed) && assertTrue(!w.write(1)) && assertTrue(r.received.isEmpty)
        },
        test("limited_n_accepts_exactly_n [AdversarialWriterCompositionSpec]") {
          val r      = new Recorder(100)
          val w      = Writer.limited(r, 3)
          val suffix = w.writeAll(Chunk(1, 2, 3, 4, 5))
          assertTrue(suffix == Chunk(4, 5)) && assertTrue(r.received.toList == List(1, 2, 3)) && assertTrue(w.isClosed)
        },
        test("limited_forwards_close_to_inner [AdversarialWriterCompositionSpec]") {
          val r = new Recorder(100)
          val w = Writer.limited(r, 3)
          w.close()
          assertTrue(r.closeCount == 1)
        },
        test("limited_forwards_fail_to_inner [AdversarialWriterCompositionSpec]") {
          val r  = new Recorder(100)
          val w  = Writer.limited(r, 3)
          val ex = new RuntimeException("lim-boom")
          w.fail(ex)
          assertTrue(r.failCount == 1) && assertTrue(r.lastFail eq ex)
        }
      )
    ),

    // ---- Writer.fromOutputStream ----------------------------------------------

    suite("Writer.fromOutputStream")(
      test("writes bytes correctly") {
        val bos = new ByteArrayOutputStream()
        val w   = Writer.fromOutputStream(bos)
        w.write(65.toByte)
        w.write(66.toByte)
        assertTrue(bos.toByteArray.toList == List[Byte](65, 66))
      },
      test("close flushes and closes") {
        val bos     = new ByteArrayOutputStream()
        var flushed = false
        var closed  = false
        val tracker = new java.io.OutputStream {
          override def write(b: Int): Unit = bos.write(b)
          override def flush(): Unit       = { flushed = true; bos.flush() }
          override def close(): Unit       = { closed = true; bos.close() }
        }
        val w = Writer.fromOutputStream(tracker)
        w.write(1.toByte)
        w.close()
        assertTrue(flushed) && assertTrue(closed) && assertTrue(w.isClosed)
      },
      test("writeBytes writes slice correctly") {
        val bos  = new ByteArrayOutputStream()
        val w    = Writer.fromOutputStream(bos)
        val data = Array[Byte](10, 20, 30, 40, 50)
        val n    = w.writeBytes(data, 1, 3)
        assertTrue(n == 3) &&
        assertTrue(bos.toByteArray.toList == List[Byte](20, 30, 40))
      },
      test("writeByte writes single byte") {
        val bos = new ByteArrayOutputStream()
        val w   = Writer.fromOutputStream(bos)
        assertTrue(w.writeByte(0xff.toByte)) &&
        assertTrue((bos.toByteArray()(0) & 0xff) == 0xff)
      },
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
      },
      // Documented contract: "Calling `close()` flushes and closes the
      // underlying stream." A write IOException flips the SAME `closed` flag
      // that gates `close()`, so after a write failure `close()` becomes a
      // no-op and the underlying stream is NEVER closed — no API path can
      // release it (resource leak), and the failure was already swallowed.
      test("close_afterWriteFailure_stillClosesUnderlying [AdversarialWriterCloseAfterErrorSpec]") {
        var underlyingClosed = false
        val os               = new OutputStream {
          override def write(b: Int): Unit = throw new IOException("write-boom")
          override def close(): Unit       = underlyingClosed = true
        }
        val w        = Writer.fromOutputStream(os)
        val accepted = w.write(1.toByte) // IOException is absorbed; returns false
        w.close()
        assert(accepted)(isFalse) &&
        assert(underlyingClosed)(isTrue)
      }
    ),

    // ---- Writer.fromWriter (java.io.Writer) -----------------------------------

    suite("Writer.fromWriter")(
      test("writes chars correctly") {
        val sw = new StringWriter()
        val w  = Writer.fromWriter(sw)
        w.write('H')
        w.write('i')
        assertTrue(sw.toString == "Hi")
      },
      test("close flushes and closes") {
        val sw      = new StringWriter()
        var flushed = false
        var closed  = false
        val tracker = new java.io.Writer {
          override def write(cbuf: Array[Char], off: Int, len: Int): Unit = sw.write(cbuf, off, len)
          override def flush(): Unit                                      = { flushed = true; sw.flush() }
          override def close(): Unit                                      = { closed = true; sw.close() }
        }
        val w = Writer.fromWriter(tracker)
        w.write('x')
        w.close()
        assertTrue(flushed) && assertTrue(closed) && assertTrue(w.isClosed)
      },
      test("writeAll writes chunk of chars") {
        val sw  = new StringWriter()
        val w   = Writer.fromWriter(sw)
        val rem = w.writeAll(Chunk('a', 'b', 'c'))
        assertTrue(rem == Chunk.empty[Char]) &&
        assertTrue(sw.toString == "abc")
      },
      test("writeAll returns full chunk when closed") {
        val sw    = new StringWriter()
        val w     = Writer.fromWriter(sw)
        val chunk = Chunk('a', 'b')
        w.close()
        assertTrue(w.writeAll(chunk) == chunk)
      },
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
      },
      // Same defect as Writer.fromOutputStream: after a write IOException the
      // shared `closed` flag makes the user-facing `close()` a no-op, so the
      // underlying java.io.Writer is never closed (resource leak).
      test("close_afterWriteFailure_stillClosesUnderlying [AdversarialWriterCloseAfterErrorSpec]") {
        var underlyingClosed = false
        val jw               = new JWriter {
          override def write(cbuf: Array[Char], off: Int, len: Int): Unit = throw new IOException("write-boom")
          override def flush(): Unit                                      = ()
          override def close(): Unit                                      = underlyingClosed = true
        }
        val w        = Writer.fromWriter(jw)
        val accepted = w.write('x') // IOException is absorbed; returns false
        w.close()
        assert(accepted)(isFalse) &&
        assert(underlyingClosed)(isTrue)
      }
    ),

    // ---- Writer.concat --------------------------------------------------------

    suite("Writer.concat")(
      test("writes to first writer, then switches to second on close") {
        val first  = new CollectingWriter[Int]
        val second = new CollectingWriter[Int]
        val w      = first.concat(second)
        val ok1    = w.write(1)
        first.close()
        val ok2 = w.write(2)
        assertTrue(ok1) &&
        assertTrue(ok2) &&
        assertTrue(first.collected == List(1)) &&
        assertTrue(second.collected == List(2))
      },
      test("isClosed is false while first writer is open") {
        val first  = new CollectingWriter[Int]
        val second = new CollectingWriter[Int]
        val w      = first.concat(second)
        assertTrue(!w.isClosed)
      },
      test("isClosed is false after switching to open second writer") {
        val first  = new CollectingWriter[Int]
        val second = new CollectingWriter[Int]
        val w      = first.concat(second)
        first.close()
        // The switch happens on next write attempt
        w.write(1)
        assertTrue(!w.isClosed)
      },
      test("isClosed is true when second writer is closed") {
        val first  = new CollectingWriter[Int]
        val second = new CollectingWriter[Int]
        val w      = first.concat(second)
        first.close()
        w.write(1) // triggers switch
        second.close()
        assertTrue(w.isClosed)
      },
      test("close closes both writers") {
        val first  = new CollectingWriter[Int]
        val second = new CollectingWriter[Int]
        val w      = first.concat(second)
        w.close()
        assertTrue(first.isClosed)
      },
      test("close is terminal even before switching to next writer") {
        val first  = new CollectingWriter[Int]
        val second = new CollectingWriter[Int]
        val w      = first.concat(second)
        w.close()
        assertTrue(w.isClosed) &&
        assertTrue(!w.write(1)) &&
        assertTrue(first.collected.isEmpty) &&
        assertTrue(second.collected.isEmpty)
      },
      test("++ is alias for concat") {
        val first  = new CollectingWriter[Int]
        val second = new CollectingWriter[Int]
        val w      = first ++ second
        val ok1    = w.write(1)
        first.close()
        val ok2 = w.write(2)
        assertTrue(ok1) &&
        assertTrue(ok2) &&
        assertTrue(first.collected == List(1)) &&
        assertTrue(second.collected == List(2))
      },
      test("ConcatWith closes self when switched to next") {
        var selfClosed = 0
        var nextClosed = 0
        val self       = new Writer[Int] {
          private var closed         = false
          def isClosed: Boolean      = closed
          def write(a: Int): Boolean = { closed = true; false } // reject first write to trigger switch
          def close(): Unit          = { selfClosed += 1; closed = true }
        }
        lazy val next: Writer[Int] = new Writer[Int] {
          private var closed         = false
          def isClosed: Boolean      = closed
          def write(a: Int): Boolean = true
          def close(): Unit          = { nextClosed += 1; closed = true }
        }
        val concat = self.concat(next)
        concat.write(42) // triggers switch to next
        concat.close()
        assertTrue(selfClosed == 1) && assertTrue(nextClosed == 1)
      },
      test("ConcatWith does not double-close self when not switched") {
        var closeCount = 0
        val self       = new Writer[Int] {
          private var closed         = false
          def isClosed: Boolean      = closed
          def write(a: Int): Boolean = true
          def close(): Unit          = { closeCount += 1; closed = true }
        }
        lazy val next: Writer[Int] = Writer.closed
        val concat                 = self.concat(next)
        concat.close()
        assertTrue(closeCount == 1)
      },
      test("error on first writer propagates immediately") {
        val first  = new FailAwareWriter[Int]
        val second = new CollectingWriter[Int]
        val w      = first.concat(second)
        first.fail(new RuntimeException("boom"))
        val result = scala.util.Try(w.write(1))
        assertTrue(result.isFailure)
      },
      suite("regressions")(
        test("concat_switches_on_clean_close_of_self [AdversarialWriterCompositionSpec]") {
          val a = new Recorder(2) // auto-closes after 2
          val b = new Recorder(100)
          val w = a.concat[Int](b)
          // write 4 elements; first 2 go to a, then a closes, switch to b for rest.
          w.write(1); w.write(2); w.write(3); w.write(4)
          assertTrue(a.received.toList == List(1, 2)) && assertTrue(b.received.toList == List(3, 4))
        },
        test("concat_writeAll_spanning_boundary [AdversarialWriterCompositionSpec]") {
          val a      = new Recorder(2)
          val b      = new Recorder(100)
          val w      = a.concat[Int](b)
          val suffix = w.writeAll(Chunk(1, 2, 3, 4, 5))
          assertTrue(suffix == Chunk.empty[Int]) &&
          assertTrue(a.received.toList == List(1, 2)) &&
          assertTrue(b.received.toList == List(3, 4, 5))
        },
        test("concat_close_before_switch_finalizes_self_once [AdversarialWriterCompositionSpec]") {
          val a = new Recorder(100)
          val b = new Recorder(100)
          val w = a.concat[Int](b)
          w.write(1)
          w.close()
          // Not switched: only self closed. `b` is a lazy thunk never forced, so it
          // is never closed (acceptable: it was never acquired).
          assertTrue(a.closeCount == 1)
        },
        test("concat_close_after_switch_finalizes_both_once [AdversarialWriterCompositionSpec]") {
          val a = new Recorder(1) // auto-closes after 1
          val b = new Recorder(100)
          val w = a.concat[Int](b)
          w.write(1) // fills a (auto-close)
          w.write(2) // triggers switch to b
          w.close()
          assertTrue(a.closeCount == 1) && assertTrue(b.closeCount == 1)
        },
        test("concat_fail_after_switch_forwards_to_both [AdversarialWriterCompositionSpec]") {
          val a  = new Recorder(1)
          val b  = new Recorder(100)
          val w  = a.concat[Int](b)
          val ex = new RuntimeException("concat-fail")
          w.write(1) // fill a
          w.write(2) // switch to b
          w.fail(ex)
          assertTrue(a.failCount == 1) && assertTrue(b.failCount == 1) &&
          assertTrue(a.lastFail eq ex) && assertTrue(b.lastFail eq ex)
        },
        test("concat_fail_forwardsErrorToActiveWriter_notSwallowedAsCleanClose [AdversarialWriterConcatFailSpec]") {
          val self = new FailRecordingWriter
          val w    = self.concat[Int](Writer.closed.asInstanceOf[Writer[Int]])
          val boom = new RuntimeException("boom-sentinel")
          w.fail(boom)
          // Contract (sibling-consistent): fail must reach the active writer, not be
          // downgraded to a clean close.
          assertTrue(self.failedWith == boom, !self.cleanClosed)
        },
        test("next-thunk-throws: self finalizer runs exactly once [AdversarialWriterConcatSpec]") {
          val self      = new CountingWriter(rejectFirstWrite = true, failWrite = false)
          val w         = self.concat[Int](throw new RuntimeException("next-boom"))
          val attempted = scala.util.Try(w.write(1))
          // The switch failed; closing the concat writer must finalize self once.
          w.close()
          assertTrue(attempted.isFailure) && assertTrue(self.closeCount == 1)
        },
        test("self-write-throws: self finalizer runs exactly once [AdversarialWriterConcatSpec]") {
          val self      = new CountingWriter(rejectFirstWrite = false, failWrite = true)
          val w         = self.concat[Int](Writer.closed.asInstanceOf[Writer[Int]])
          val attempted = scala.util.Try(w.write(1))
          w.close()
          assertTrue(attempted.isFailure) && assertTrue(self.closeCount == 1)
        }
      )
    ),

    // ---- Writer.contramap -----------------------------------------------------

    suite("Writer.contramap")(
      test("transforms elements before writing") {
        val inner = new CollectingWriter[Int]
        val w     = inner.contramap[String](_.length)
        w.write("hi")
        w.write("hello")
        assertTrue(inner.collected == List(2, 5))
      },
      test("isClosed delegates to inner") {
        val inner  = new CollectingWriter[Int]
        val w      = inner.contramap[String](_.length)
        val before = w.isClosed
        inner.close()
        val after = w.isClosed
        assertTrue(!before) &&
        assertTrue(after)
      },
      test("close delegates to inner") {
        val inner = new CollectingWriter[Int]
        val w     = inner.contramap[String](_.length)
        w.close()
        assertTrue(inner.isClosed)
      },
      test("fail delegates to inner") {
        val inner = new CollectingWriter[Int]
        val w     = inner.contramap[String](_.length)
        val err   = new RuntimeException("test")
        w.fail(err)
        assertTrue(inner.error.contains(err))
      },
      test("write returns false when inner is closed") {
        val inner = new CollectingWriter[Int]
        val w     = inner.contramap[String](_.length)
        inner.close()
        assertTrue(!w.write("x"))
      },
      suite("regressions")(
        test("contramap_transforms_and_forwards [AdversarialWriterCompositionSpec]") {
          val r = new Recorder(100)
          val w = r.contramap[Int](_ * 100)
          w.write(1); w.write(2)
          assertTrue(r.received.toList == List(100, 200))
        },
        test("contramap_forwards_fail [AdversarialWriterCompositionSpec]") {
          val r  = new Recorder(100)
          val w  = r.contramap[Int](identity)
          val ex = new RuntimeException("cm-boom")
          w.fail(ex)
          assertTrue(r.failCount == 1) && assertTrue(r.lastFail eq ex)
        },
        test("contramap_forwards_writeable_accuracy [AdversarialWriteableAccuracySpec]") {
          // writeable() contract: "true if the next write would accept a value
          // without blocking". Sibling oracle: every Reader wrapper
          // (DelegatingReader and friends) forwards readable() so a buffered
          // underlying reader's accuracy survives wrapping. Writer.Contramapped
          // forwards write/close/fail/isClosed but inherits the default
          // `!isClosed` for writeable(), discarding the underlying writer's
          // accurate answer: it reports writeable()==true while the underlying
          // bounded writer reports false and the next write returns false.
          final class BoundedProbe extends Writer[Int] {
            var acceptsMore: Boolean          = true
            def isClosed: Boolean             = false
            def write(a: Int): Boolean        = acceptsMore
            def close(): Unit                 = ()
            override def writeable(): Boolean = acceptsMore
          }
          val bounded = new BoundedProbe
          bounded.acceptsMore = false
          val w = bounded.contramap[String](_.length)
          assertTrue(!bounded.writeable(), !w.write("x"), !w.writeable())
        },
        // Convergence probe (run 5): a three-deep composition —
        // contramap over (limited ++ overflow) — must route the element that
        // overflows the limit into the second writer un-dropped, transformed
        // exactly once, and close both writers exactly once.
        test("contramap_overLimitedConcat_routesOverflowLosslessly [AdversarialWriterCompositionSpec]") {
          val a = new CollectingWriter[String]
          val b = new CollectingWriter[String]
          val w = Writer.limited(a, 2).concat(b).contramap[Int](i => s"#$i")
          (1 to 4).foreach(i => w.write(i))
          w.close()
          assertTrue(
            a.collected == List("#1", "#2"),
            b.collected == List("#3", "#4"),
            a.isClosed,
            b.isClosed
          )
        }
      )
    ),

    // ---- writeAll -------------------------------------------------------------

    suite("writeAll")(
      test("writes all elements from chunk") {
        val inner = new CollectingWriter[Int]
        val rem   = inner.writeAll(Chunk(1, 2, 3))
        assertTrue(rem == Chunk.empty[Int]) &&
        assertTrue(inner.collected == List(1, 2, 3))
      },
      test("returns undelivered suffix when writer closes mid-way") {
        val w   = Writer.limited(new CollectingWriter[Int], 2)
        val rem = w.writeAll(Chunk(10, 20, 30, 40))
        assertTrue(rem.length == 2) &&
        assertTrue(rem == Chunk(30, 40))
      },
      test("returns entire chunk when writer is already closed") {
        val inner = new CollectingWriter[Int]
        inner.close()
        val chunk = Chunk(1, 2, 3)
        assertTrue(inner.writeAll(chunk) == chunk)
      },
      test("empty chunk returns empty chunk") {
        val inner = new CollectingWriter[Int]
        assertTrue(inner.writeAll(Chunk.empty[Int]) == Chunk.empty[Int])
      },
      suite("regressions")(
        test("writeAll_all_delivered_returns_empty [AdversarialWriterCompositionSpec]") {
          val r = new Recorder(100)
          assertTrue(r.writeAll(Chunk(1, 2, 3)) == Chunk.empty[Int]) && assertTrue(r.received.toList == List(1, 2, 3))
        },
        test("writeAll_partial_returns_undelivered_suffix [AdversarialWriterCompositionSpec]") {
          // Recorder auto-closes after 2; the 3rd write fails -> suffix from index 2.
          val r      = new Recorder(2)
          val suffix = r.writeAll(Chunk(10, 20, 30, 40))
          assertTrue(suffix == Chunk(30, 40)) && assertTrue(r.received.toList == List(10, 20))
        }
      )
    ),

    // ---- writeBytes / writeByte on non-OutputStream writers -------------------

    suite("writeBytes (base class default)")(
      test("writeBytes delegates to writeByte for each byte") {
        val bos = new ByteArrayOutputStream()
        val w   = Writer.fromOutputStream(bos)
        val buf = Array[Byte](1, 2, 3)
        val n   = w.writeBytes(buf, 0, 3)
        assertTrue(n == 3) &&
        assertTrue(bos.toByteArray.toList == List[Byte](1, 2, 3))
      },
      test("writeBytes stops early when writer closes") {
        val inner = Writer.limited(Writer.fromOutputStream(new ByteArrayOutputStream()), 2)
        val buf   = Array[Byte](1, 2, 3, 4, 5)
        val n     = inner.writeBytes(buf, 0, 5)
        assertTrue(n == 2)
      },
      test("writeBytes returns 0 on closed writer") {
        val bos = new ByteArrayOutputStream()
        val w   = Writer.fromOutputStream(bos)
        w.close()
        assertTrue(w.writeBytes(Array[Byte](1), 0, 1) == 0)
      },
      suite("regressions")(
        test("writeBytes_through_limited_respects_limit [AdversarialWriterCompositionSpec]") {
          val r = new ByteRecorder
          val w = Writer.limited(r, 3)
          val n = w.writeBytes(Array[Byte](1, 2, 3, 4, 5), 0, 5)
          assertTrue(n == 3) && assertTrue(r.received.toList == List[Byte](1, 2, 3))
        },
        test("writeBytes_len_zero_returns_zero [AdversarialWriterCompositionSpec]") {
          val r = new ByteRecorder
          val n = r.writeBytes(Array[Byte](1, 2, 3), 0, 0)
          assertTrue(n == 0) && assertTrue(r.received.isEmpty)
        }
      )
    ),

    // ---- isClosed state transitions -------------------------------------------

    suite("isClosed state transitions")(
      test("monotone: once true, never returns false") {
        val w      = Writer.single[Int]
        val before = w.isClosed
        w.close()
        val after1 = w.isClosed
        val after2 = w.isClosed
        assertTrue(!before) &&
        assertTrue(after1) &&
        assertTrue(after2)
      },
      test("single: transitions on first write") {
        val w      = Writer.single[Int]
        val before = w.isClosed
        w.write(1)
        val after = w.isClosed
        assertTrue(!before) &&
        assertTrue(after)
      },
      suite("regressions")(
        test("isClosed_monotone_after_close [AdversarialWriterCompositionSpec]") {
          val r = new Recorder(100)
          assertTrue(!r.isClosed)
          r.close()
          assertTrue(r.isClosed) && { r.write(1); assertTrue(r.isClosed) }
        }
      )
    ),

    // ---- close idempotency ----------------------------------------------------

    suite("close idempotency")(
      test("calling close multiple times on single does not throw") {
        val w = Writer.single[Int]
        w.close()
        w.close()
        w.close()
        assertTrue(w.isClosed)
      },
      test("calling close multiple times on limited does not throw") {
        val inner = new CollectingWriter[Int]
        val w     = Writer.limited(inner, 5)
        w.close()
        w.close()
        assertTrue(w.isClosed)
      },
      test("calling close multiple times on fromOutputStream does not throw") {
        val w = Writer.fromOutputStream(new ByteArrayOutputStream())
        w.close()
        w.close()
        assertTrue(w.isClosed)
      },
      test("calling close multiple times on fromWriter does not throw") {
        val w = Writer.fromWriter(new StringWriter())
        w.close()
        w.close()
        assertTrue(w.isClosed)
      }
    ),

    // ---- fail behavior --------------------------------------------------------

    suite("fail behavior")(
      test("fail on closed writer delegates to close by default") {
        val w = Writer.closed
        w.fail(new RuntimeException("err"))
        assertTrue(w.isClosed)
      },
      test("fail marks writer as closed") {
        val inner = new CollectingWriter[Int]
        inner.fail(new RuntimeException("test"))
        assertTrue(inner.isClosed)
      },
      test("fail stores error") {
        val inner = new CollectingWriter[Int]
        val err   = new RuntimeException("stored")
        inner.fail(err)
        assertTrue(inner.error.contains(err))
      }
    ),

    // ---- writeable ------------------------------------------------------------

    suite("writeable")(
      test("returns true when not closed") {
        val w = new CollectingWriter[Int]
        assertTrue(w.writeable())
      },
      test("returns false when closed") {
        val w = new CollectingWriter[Int]
        w.close()
        assertTrue(!w.writeable())
      }
    ),

    // ---- specialized write methods --------------------------------------------

    suite("specialized write methods")(
      test("writeBoolean delegates to write") {
        val inner = new CollectingWriter[Boolean]
        assertTrue(inner.writeBoolean(true)) &&
        assertTrue(inner.writeBoolean(false)) &&
        assertTrue(inner.collected == List(true, false))
      },
      test("writeChar delegates to write") {
        val inner = new CollectingWriter[Char]
        assertTrue(inner.writeChar('A')) &&
        assertTrue(inner.collected == List('A'))
      },
      test("writeInt delegates to write") {
        val inner = new CollectingWriter[Int]
        assertTrue(inner.writeInt(42)) &&
        assertTrue(inner.collected == List(42))
      },
      test("writeLong delegates to write") {
        val inner = new CollectingWriter[Long]
        assertTrue(inner.writeLong(100L)) &&
        assertTrue(inner.collected == List(100L))
      },
      test("writeDouble delegates to write") {
        val inner = new CollectingWriter[Double]
        assertTrue(inner.writeDouble(3.14)) &&
        assertTrue(inner.collected == List(3.14))
      },
      test("writeFloat delegates to write") {
        val inner = new CollectingWriter[Float]
        assertTrue(inner.writeFloat(1.5f)) &&
        assertTrue(inner.collected == List(1.5f))
      },
      test("writeShort delegates to write") {
        val inner = new CollectingWriter[Short]
        assertTrue(inner.writeShort(7.toShort)) &&
        assertTrue(inner.collected == List(7.toShort))
      }
    ),
    run10ConvergenceSuite,
    run11ConvergenceSuite
  )

  // ---- Run #10 convergence probes ------------------------------------------
  // Passing adversarial probes from the tenth hardening round: ConcatWith
  // switch losslessness for bulk writes, fail routing before/after the switch,
  // and writeable() accuracy through limited/contramapped wrappers. Committed
  // as convergence evidence.
  private case class Run10WriterBoom(n: Int) extends RuntimeException(s"boom-$n")

  private def run10ConvergenceSuite = suite("run10 convergence")(
    test("concatWith_writeAllSpansTheSwitchLosslessly") {
      val sw     = Writer.single[Int]
      var stored = List.empty[Int]
      val rec    = new Writer[Int] {
        private var closed         = false
        def isClosed: Boolean      = closed
        def write(a: Int): Boolean = { stored = stored :+ a; true }
        def close(): Unit          = closed = true
      }
      val w    = sw ++ rec
      val rest = w.writeAll(Chunk.fromIterable(List(1, 2, 3)))
      assertTrue(rest.isEmpty, stored == List(2, 3))
    },
    test("concatWith_failBeforeSwitchReachesSelfOnly_afterSwitchReachesBoth") {
      var selfFailed: Throwable                                     = null
      var nextFailed: Throwable                                     = null
      def mk(record: Throwable => Unit, capacity: Int): Writer[Int] = new Writer[Int] {
        private var n                         = capacity
        def isClosed: Boolean                 = n <= 0
        def write(a: Int): Boolean            = if (n <= 0) false else { n -= 1; true }
        def close(): Unit                     = n = 0
        override def fail(e: Throwable): Unit = { record(e); n = 0 }
      }
      val w1 = mk(selfFailed = _, 1) ++ mk(nextFailed = _, 10)
      w1.fail(Run10WriterBoom(10))
      val firstOnlySelf = (selfFailed == Run10WriterBoom(10)) && (nextFailed == null)
      selfFailed = null; nextFailed = null
      val w2 = mk(selfFailed = _, 1) ++ mk(nextFailed = _, 10)
      w2.write(1); w2.write(2) // second write triggers the switch
      w2.fail(Run10WriterBoom(11))
      assertTrue(firstOnlySelf, selfFailed == Run10WriterBoom(11), nextFailed == Run10WriterBoom(11))
    },
    test("limitedWriterZero_acceptsNothing_andContramapForwardsWriteableAccuracy") {
      val sw  = Writer.single[Int]
      val lim = Writer.limited(sw, 0L)
      val cm  = lim.contramap[String](_.length)
      assertTrue(!lim.writeable(), lim.isClosed, !cm.write("ab"), !cm.writeable())
    }
  )

  // ---- Run #11 convergence probes ------------------------------------------
  // Eleventh (convergence-verification) round: user-supplied minimal-but-lawful
  // capacity-bounded Writer SPI implementations driven through the library
  // combinators (`writeAll` leftover losslessness, `limited`, `contramap`,
  // `concat` switching at the USER writer's refusal boundary with exactly-once
  // finalization, and `fail` delegation into user overrides). Committed as
  // convergence evidence.
  private case class Run11WriterBoom(n: Int) extends RuntimeException(s"boom-$n")

  /** Minimal-but-lawful capacity-bounded user Writer SPI implementation. */
  private final class Run11CapWriter(cap: Int) extends Writer[Int] {
    val written                           = ArrayBuffer.empty[Int]
    var closeCalls                        = 0
    var failedWith: Throwable             = null
    private var closed                    = false
    def isClosed: Boolean                 = closed || written.size >= cap
    def write(a: Int): Boolean            = if (isClosed) false else { written += a; true }
    def close(): Unit                     = { if (!closed) closeCalls += 1; closed = true }
    override def fail(e: Throwable): Unit = { if (failedWith == null) failedWith = e; close() }
  }

  private def run11ConvergenceSuite = suite("run11 convergence")(
    test("manualCapWriter_writeAll_returnsExactUndeliveredSuffix") {
      val w    = new Run11CapWriter(3)
      val rest = w.writeAll(Chunk.fromIterable(List(1, 2, 3, 4, 5)))
      assertTrue(w.written.toList == List(1, 2, 3), rest.toList == List(4, 5), w.isClosed)
    },
    test("manualCapWriter_underLimited_smallerWindowWins") {
      val a    = new Run11CapWriter(10)
      val lim  = Writer.limited[Int](a, 4L)
      val rest = lim.writeAll(Chunk.fromIterable(List(1, 2, 3, 4, 5, 6)))
      assertTrue(a.written.toList == List(1, 2, 3, 4), rest.toList == List(5, 6), lim.isClosed, !lim.writeable())
    },
    test("manualCapWriter_underContramap_transformsBeforeStore") {
      val a  = new Run11CapWriter(8)
      val cm = a.contramap[String](_.length)
      assertTrue(cm.write("ab"), cm.write("abcd"), a.written.toList == List(2, 4), cm.writeable())
    },
    test("manualCapWriters_concat_switchesAtUserRefusal_andClosesBothOnce") {
      val a    = new Run11CapWriter(2)
      val b    = new Run11CapWriter(3)
      val w    = a ++ b
      val rest = w.writeAll(Chunk.fromIterable(List(1, 2, 3, 4, 5, 6)))
      w.close()
      assertTrue(
        a.written.toList == List(1, 2),
        b.written.toList == List(3, 4, 5),
        rest.toList == List(6),
        a.closeCalls == 1,
        b.closeCalls == 1
      )
    },
    test("manualCapWriters_concat_failDelegatesToUserOverrides") {
      val a1 = new Run11CapWriter(2); val b1 = new Run11CapWriter(3)
      val w1 = a1 ++ b1
      w1.fail(Run11WriterBoom(1))
      val a2 = new Run11CapWriter(1); val b2 = new Run11CapWriter(3)
      val w2 = a2 ++ b2
      w2.write(7); w2.write(8) // second write triggers the switch to b2
      w2.fail(Run11WriterBoom(2))
      assertTrue(
        a1.failedWith == Run11WriterBoom(1),
        b1.failedWith == null,
        a2.failedWith == Run11WriterBoom(2),
        b2.failedWith == Run11WriterBoom(2),
        a1.closeCalls == 1,
        a2.closeCalls == 1,
        b2.closeCalls == 1
      )
    }
  )
}
