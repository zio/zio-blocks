package zio.blocks.streams

import zio.blocks.chunk.Chunk
import zio.blocks.streams.io.Writer
import zio.test._

import java.io.{ByteArrayOutputStream, StringWriter => JStringWriter}

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
      }
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
      }
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
      }
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
      }
    ),

    // ---- Writer.fromWriter (java.io.Writer) -----------------------------------

    suite("Writer.fromWriter")(
      test("writes chars correctly") {
        val sw = new JStringWriter()
        val w  = Writer.fromWriter(sw)
        w.write('H')
        w.write('i')
        assertTrue(sw.toString == "Hi")
      },
      test("close flushes and closes") {
        val sw      = new JStringWriter()
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
        val sw  = new JStringWriter()
        val w   = Writer.fromWriter(sw)
        val rem = w.writeAll(Chunk('a', 'b', 'c'))
        assertTrue(rem == Chunk.empty[Char]) &&
        assertTrue(sw.toString == "abc")
      },
      test("writeAll returns full chunk when closed") {
        val sw    = new JStringWriter()
        val w     = Writer.fromWriter(sw)
        val chunk = Chunk('a', 'b')
        w.close()
        assertTrue(w.writeAll(chunk) == chunk)
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
      }
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
      }
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
      }
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
      }
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
      }
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
        val w = Writer.fromWriter(new JStringWriter())
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
    )
  )
}
