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
import zio.blocks.streams.io.Reader
import zio.test._
import zio.test.Assertion._
import StreamsGen._

import java.io.{ByteArrayInputStream, CharArrayReader, IOException, InputStream, Reader => JReader}

object ReaderSpec extends StreamsBaseSpec {

  /** Drain a `Reader[A]` to a `Chunk[A]` via `read()`. */
  private def drainAll[A](dq: Reader[A]): Chunk[A] = {
    val b = Chunk.newBuilder[A]
    var v = dq.read[Any](null)
    while (v != null) { b += v.asInstanceOf[A]; v = dq.read[Any](null) }
    b.result()
  }

  /** Drain exactly `n` elements via `read()`. */
  private def drainN[A](dq: Reader[A], n: Int): Chunk[A] = {
    val b = Chunk.newBuilder[A]
    var i = 0
    while (i < n) {
      val v = dq.read[Any](null)
      if (v != null) { b += v.asInstanceOf[A]; i += 1 }
      else i = n
    }
    b.result()
  }

  /**
   * Captures a throwable raised by `thunk`. Unlike `scala.util.Try`, this also
   * captures control throwables such as [[StreamError]] (which are excluded
   * from `NonFatal`), so it can observe typed stream errors raised during
   * reads.
   */
  private def caught(thunk: => Any): Throwable =
    try { thunk; null }
    catch { case t: Throwable => t }

  private def streamOf(bytes: Byte*): InputStream = new ByteArrayInputStream(bytes.toArray)
  private def emptyStream: InputStream            = new ByteArrayInputStream(Array.empty)

  private final class FailableInputStream(data: Array[Byte]) extends InputStream {
    private var pos          = 0
    @volatile var boom       = false
    override def read(): Int = {
      if (boom) throw new IOException("boom")
      if (pos >= data.length) -1
      else { val b = data(pos) & 0xff; pos += 1; b }
    }
    override def read(b: Array[Byte], off: Int, len: Int): Int = {
      if (boom) throw new IOException("boom")
      if (pos >= data.length) -1
      else {
        val n = math.min(len, data.length - pos)
        System.arraycopy(data, pos, b, off, n)
        pos += n
        n
      }
    }
  }

  private def readerOf(chars: Char*): JReader = new CharArrayReader(chars.toArray)
  private def emptyReader: JReader            = new CharArrayReader(Array.empty)

  private final class FailableReader(data: Array[Char]) extends JReader {
    private var pos                                              = 0
    @volatile var boom                                           = false
    override def read(buf: Array[Char], off: Int, len: Int): Int = {
      if (boom) throw new IOException("boom")
      if (pos >= data.length) -1
      else {
        val n = math.min(len, data.length - pos)
        System.arraycopy(data, pos, buf, off, n)
        pos += n
        n
      }
    }
    override def close(): Unit = ()
  }

  /** Chunked drain via the public `readUpToN` contract. */
  private def viaReadUpToN[A](s: Stream[Nothing, A]): List[A] = {
    val r = Stream.compileToReader(s)
    val b = List.newBuilder[A]
    var c = r.readUpToN[A](10)
    while (c.nonEmpty) { c.foreach(b += _); c = r.readUpToN[A](10) }
    b.result()
  }

  private val LMax = Long.MaxValue
  private val DMax = Double.MaxValue

  // > Stream.DepthCutoff(100) ops => inner compiles to an Interpreter, whose
  // isClosed stays false at natural EOF.
  private def deepInterpreter: Stream[Nothing, Int] =
    (0 until 150).foldLeft(Stream(1, 2))((s, _) => s.map(identity))

  private def collectViaReadUpToN(s: Stream[Nothing, Int], limit: Int): List[Int] = {
    val r = Stream.compileToReader(s)
    val b = List.newBuilder[Int]
    var n = 0
    var c = r.readUpToN[Int](2)
    while (c.nonEmpty && n < limit) {
      val it = c.iterator
      while (it.hasNext && n < limit) { b += it.next(); n += 1 }
      c = if (n < limit) r.readUpToN[Int](2) else Chunk.empty
    }
    b.result()
  }

  def spec: Spec[TestEnvironment, Any] = suite("Reader")(
    // ---- fromChunk -----------------------------------------------------------

    suite("fromChunk")(
      test("emits all elements in order") {
        check(genChunk(genInt)) { chunk =>
          val dq = Reader.fromChunk[Int](chunk)
          assert(drainAll(dq))(equalTo(chunk))
        }
      },
      test("read returns null after last element") {
        val dq = Reader.fromChunk[Int](Chunk(1, 2))
        dq.read[Any](null); dq.read[Any](null)
        assertTrue(dq.read[Any](null) == null)
      },
      test("isClosed false before exhausted, true after") {
        val dq = Reader.fromChunk[Int](Chunk(42))
        assertTrue(!dq.isClosed) &&
        assertTrue(dq.read[Any](null) == Int.box(42)) &&
        assertTrue(dq.isClosed)
      },
      test("isClosed transitions from false to true after exhaustion") {
        val dq = Reader.fromChunk[Int](Chunk(1))
        assertTrue(!dq.isClosed) &&
        assertTrue(dq.read[Any](null) == Int.box(1)) &&
        assertTrue(dq.isClosed)
      },
      test("readable() returns false on empty chunk") {
        val dq = Reader.fromChunk[Int](Chunk.empty)
        assertTrue(!dq.readable())
      },
      test("readable() returns true when elements remain, false after exhaustion") {
        val dq = Reader.fromChunk[Int](Chunk(10, 20))
        assertTrue(dq.readable()) &&
        assertTrue(dq.read[Any](null) == Int.box(10)) &&
        assertTrue(dq.readable()) &&
        assertTrue(dq.read[Any](null) == Int.box(20)) &&
        assertTrue(!dq.readable())
      },
      test("close marks reader as closed") {
        val dq = Reader.fromChunk[Int](Chunk(1, 2, 3))
        dq.close()
        assertTrue(dq.isClosed)
      },
      test("empty chunk closes immediately") {
        val dq = Reader.fromChunk[Int](Chunk.empty)
        assertTrue(dq.isClosed) &&
        assertTrue(dq.read[Any](null) == null)
      },
      test("single-threaded read() delivers each element exactly once") {
        val n     = 1000
        val chunk = Chunk.fromIterable(0 until n)
        val dq    = Reader.fromChunk[Int](chunk)
        assert(drainAll(dq))(equalTo(chunk))
      }
    ),

    // ---- fromRange -----------------------------------------------------------

    suite("fromRange")(
      test("emits range integers in order") {
        val dq = Reader.fromRange(1 to 5)
        assert(drainAll(dq))(equalTo(Chunk(1, 2, 3, 4, 5)))
      },
      test("empty range closes immediately") {
        val dq = Reader.fromRange(1 until 1)
        assertTrue(dq.isClosed) &&
        assertTrue(dq.read[Any](null) == null)
      },
      test("readable() is true while elements remain") {
        val dq = Reader.fromRange(1 to 3)
        assertTrue(dq.readable()) &&
        assertTrue(dq.read[Any](null) == Int.box(1)) &&
        assertTrue(dq.readable()) &&
        assertTrue(dq.read[Any](null) == Int.box(2)) &&
        assertTrue(dq.readable()) &&
        assertTrue(dq.read[Any](null) == Int.box(3)) &&
        assertTrue(!dq.readable())
      },
      test("single-threaded read() delivers each element exactly once") {
        val n  = 1000
        val dq = Reader.fromRange(0 until n)
        assert(drainAll(dq))(equalTo(Chunk.fromIterable(0 until n)))
      }
    ),

    // ---- fromIterable --------------------------------------------------------

    suite("fromIterable")(
      test("emits all elements in iteration order") {
        check(genChunk(genInt)) { chunk =>
          val dq = Reader.fromIterable[Int](chunk.toList)
          assert(drainAll(dq))(equalTo(chunk))
        }
      },
      test("empty iterable closes immediately") {
        val dq = Reader.fromIterable[Int](Nil)
        assertTrue(dq.isClosed) &&
        assertTrue(dq.read[Any](null) == null)
      },
      test("readable() returns false when empty") {
        val dq = Reader.fromIterable[Int](Nil)
        assertTrue(!dq.readable())
      }
    ),

    // ---- repeat --------------------------------------------------------------

    suite("repeat")(
      test("always returns the same element") {
        val dq = Reader.repeat[Int](7)
        assert(drainN(dq, 5))(equalTo(Chunk(7, 7, 7, 7, 7)))
      },
      test("isClosed is always false") {
        assertTrue(!Reader.repeat[Int](0).isClosed)
      },
      test("readable() always returns true for repeat") {
        val dq = Reader.repeat[String]("x")
        assertTrue(dq.readable())
      },
      test("close() terminates a repeat reader: read-after-close returns the sentinel [AdversarialRepeatCloseSpec]") {
        // Reader.close contract: "Signals close from the consumer side.
        // Implementations should set internal closed state" — and the pinned
        // read-after-close behavior of every sibling source (ConcatReader
        // BUG-R5-02, SkipLimit, chunk readers): after close(), reads return
        // the sentinel and isClosed reports true. A repeat-mode singleton's
        // close() is `if (mode != 2) ...`, i.e. a no-op, so a closed
        // Reader.repeat keeps emitting values forever and never reports
        // closed. (Any fix must keep `reset()` restoring repeat mode so the
        // zip eager-close + `repeated` restart cycle still replays.)
        val prim               = Reader.repeat[Int](7)
        val emittedBeforeClose = prim.read[Any](null) // sanity: emits before close
        prim.close()
        val generic = Reader.repeat[String]("x")
        generic.close()
        assertTrue(
          emittedBeforeClose == Int.box(7),
          prim.isClosed,
          prim.read[Any](null) == null,
          generic.isClosed,
          generic.read[Any](null) == null
        )
      }
    ),

    // ---- unfold --------------------------------------------------------------

    suite("unfold")(
      test("emits elements according to unfolding function") {
        val dq =
          Reader.unfold[Int, Int](0)(n => if (n < 5) Some((n * 2, n + 1)) else None)
        assert(drainAll(dq))(equalTo(Chunk(0, 2, 4, 6, 8)))
      },
      test("returns null immediately when f returns None from start") {
        val dq = Reader.unfold[Int, Int](0)(_ => None)
        assertTrue(dq.read[Any](null) == null)
      },
      test("isClosed false before exhausted, true after done signal consumed") {
        val dq =
          Reader.unfold[Int, Int](0)(n => if (n < 1) Some((n, n + 1)) else None)
        // Before any read(): open
        assertTrue(!dq.isClosed) &&
        // After taking the single element: still open — done signal not yet seen
        assertTrue(dq.read[Any](null) == Int.box(0)) &&
        assertTrue(!dq.isClosed) &&
        // After taking the done signal: closed
        assertTrue(dq.read[Any](null) == null) &&
        assertTrue(dq.isClosed)
      },
      test("readable() returns false when exhausted") {
        val dq = Reader.unfold[Int, Int](0)(_ => None)
        dq.read[Any](null) // exhaust it
        assertTrue(!dq.readable())
      },
      test("close marks finished") {
        val dq =
          Reader.unfold[Int, Int](0)(n => if (n < 10) Some((n, n + 1)) else None)
        dq.close()
        assertTrue(dq.isClosed)
      }
    ),

    // ---- repeated ------------------------------------------------------------

    suite("repeated")(
      test("restarts via reset and replays elements across cycles") {
        val inner = Reader.fromChunk[Int](Chunk(0, 1, 2))
        val dq    = Reader.repeated[Int](inner)
        // 3 cycles = 9 elements, same sequence each cycle
        assert(drainN(dq, 9))(equalTo(Chunk(0, 1, 2, 0, 1, 2, 0, 1, 2)))
      },
      test("isClosed false initially, reader repeats after clean close") {
        val inner = Reader.fromChunk[Int](Chunk(1))
        val dq    = Reader.repeated[Int](inner)
        assertTrue(!dq.isClosed) &&
        // First cycle
        assertTrue(dq.read[Any](null) == Int.box(1)) &&
        // Repeated — should reset and give element again
        assertTrue(dq.read[Any](null) == Int.box(1)) &&
        assertTrue(!dq.isClosed)
      },
      test(
        "specialized lane after error reports end-of-stream like the boxed lane [AdversarialRepeatedErrorStateSpec]"
      ) {
        // Repeated's boxed read() intentionally latches `done` when the inner
        // reader throws ("On error, re-throws" — and the stream is over: a
        // subsequent read returns the sentinel). The specialized lanes
        // (readInt/readLong/readFloat/readDouble/readUpToN) skip that latch, so
        // after the SAME error the Int lane keeps pulling the inner reader and
        // can emit elements after the failure — a lane-divergent state machine
        // observable through any manually-pulled Reader (Stream#start).
        final class FlakyInt extends Reader[Int] {
          private var calls                     = 0
          override def jvmType: JvmType         = JvmType.Int
          def isClosed: Boolean                 = false
          def read[A1 >: Int](sentinel: A1): A1 = {
            calls += 1
            if (calls == 1) throw new RuntimeException("boom") else Int.box(42).asInstanceOf[A1]
          }
          override def readInt(sentinel: Long)(implicit ev: Int <:< Int): Long = {
            calls += 1
            if (calls == 1) throw new RuntimeException("boom") else 42L
          }
          def close(): Unit          = ()
          override def reset(): Unit = ()
        }
        val boxed      = Reader.repeated[Int](new FlakyInt)
        val boxedThrew =
          try { val _ = boxed.read[Any](null); false }
          catch { case _: RuntimeException => true }
        val boxedAfter = boxed.read[Any](null)

        val lane      = Reader.repeated[Int](new FlakyInt)
        val laneThrew =
          try { val _ = lane.readInt(Long.MinValue); false }
          catch { case _: RuntimeException => true }
        val laneAfter = lane.readInt(Long.MinValue)

        assertTrue(boxedThrew, laneThrew, boxedAfter == null, laneAfter == Long.MinValue)
      }
    ),

    // ---- fromInputStream -----------------------------------------------------

    suite("fromInputStream")(
      test("read() returns each Byte (signed) then null on EOF") {
        val dq = Reader.fromInputStream(streamOf(1, 2, 3))
        assertTrue(dq.read[Any](null) == Byte.box(1.toByte)) &&
        assertTrue(dq.read[Any](null) == Byte.box(2.toByte)) &&
        assertTrue(dq.read[Any](null) == Byte.box(3.toByte)) &&
        assertTrue(dq.read[Any](null) == null)
      },
      test("read() returns null on empty stream") {
        val dq = Reader.fromInputStream(emptyStream)
        assertTrue(dq.read[Any](null) == null)
      },
      test("IOException during read throws StreamError and marks reader as closed") {
        val is = new FailableInputStream(Array[Byte](1))
        val dq = Reader.fromInputStream(is)
        assertTrue(dq.read[Any](null) == Byte.box(1.toByte))
        is.boom = true
        val result = caught(dq.read[Any](null))
        assertTrue(result != null) &&
        assertTrue(result.isInstanceOf[StreamError]) &&
        assertTrue(result.asInstanceOf[StreamError].value.isInstanceOf[IOException]) &&
        assertTrue(dq.isClosed)
      },
      test("readByte returns -1 on EOF") {
        val dq = Reader.fromInputStream(emptyStream)
        assert(dq.readByte())(equalTo(-1))
      },
      test("readByte returns byte value in [0,255]") {
        val dq = Reader.fromInputStream(streamOf(0xff.toByte))
        assert(dq.readByte())(equalTo(255))
      },
      test("readable returns false when no data") {
        assertTrue(!Reader.fromInputStream(emptyStream).readable())
      },
      test("readBytes returns -1 on empty stream") {
        val buf = new Array[Byte](4)
        assert(Reader.fromInputStream(emptyStream).readBytes(buf, 0, 4))(equalTo(-1))
      },
      test("readBytes(buf, 0, 0) returns 0") {
        assert(Reader.fromInputStream(emptyStream).readBytes(new Array[Byte](0), 0, 0))(equalTo(0))
      },
      test("readBytes reads data") {
        val dq  = Reader.fromInputStream(streamOf(10, 20, 30))
        val buf = new Array[Byte](3)
        val n   = dq.readBytes(buf, 0, 3)
        assertTrue(n > 0) && assert(buf.take(n).toList)(equalTo(List[Byte](10, 20, 30).take(n)))
      },
      test("isClosed is true after close") {
        val dq = Reader.fromInputStream(emptyStream)
        dq.close()
        assertTrue(dq.isClosed)
      },
      test("isClosed after EOF") {
        val dq = Reader.fromInputStream(emptyStream)
        dq.read[Any](null)
        assertTrue(dq.isClosed)
      },
      test("drains bytes in order via read()") {
        val dq = Reader.fromInputStream(streamOf(5, 6, 7))
        val b  = Chunk.newBuilder[Byte]
        var v  = dq.read[Any](null)
        while (v != null) { b += v.asInstanceOf[Byte]; v = dq.read[Any](null) }
        assert(b.result())(equalTo(Chunk(5.toByte, 6.toByte, 7.toByte)))
      },
      test("close is idempotent") {
        val dq = Reader.fromInputStream(streamOf(1, 2))
        dq.close()
        dq.close()
        assertTrue(dq.isClosed)
      },
      test("read returns null after explicit close") {
        val dq = Reader.fromInputStream(streamOf(1, 2, 3))
        dq.close()
        assertTrue(dq.read[Any](null) == null)
      },
      test("readBytes returns -1 after close") {
        val dq = Reader.fromInputStream(streamOf(1, 2))
        dq.close()
        val buf = new Array[Byte](4)
        assert(dq.readBytes(buf, 0, 4))(equalTo(-1))
      },
      test("readByte returns -1 after close") {
        val dq = Reader.fromInputStream(streamOf(10))
        dq.close()
        assert(dq.readByte())(equalTo(-1))
      },
      test("readBytes partial fill: stream has 2 bytes, request 5 returns 2") {
        val dq  = Reader.fromInputStream(streamOf(42, 99))
        val buf = new Array[Byte](5)
        val n   = dq.readBytes(buf, 0, 5)
        assertTrue(n == 2) && assert(buf.take(n).toList)(equalTo(List[Byte](42, 99)))
      },
      test("readBytes with offset: writes to correct position in buffer") {
        val dq  = Reader.fromInputStream(streamOf(10, 20))
        val buf = new Array[Byte](4)
        val n   = dq.readBytes(buf, 2, 2)
        assertTrue(n == 2) &&
        assertTrue(buf(0) == 0) &&
        assertTrue(buf(1) == 0) &&
        assertTrue(buf(2) == 10) &&
        assertTrue(buf(3) == 20)
      },
      test("readBytes invalid bounds: offset + len > buf.length throws IndexOutOfBoundsException") {
        val dq     = Reader.fromInputStream(streamOf(1, 2, 3, 4, 5))
        val buf    = new Array[Byte](4)
        val result = scala.util.Try(dq.readBytes(buf, 0, 10))
        assertTrue(result.isFailure) &&
        assertTrue(result.failed.get.isInstanceOf[IndexOutOfBoundsException])
      }
    ),

    // ---- fromReader ----------------------------------------------------------

    suite("fromReader")(
      test("fromJavaReader round-trips astral-plane code points (surrogate pairs) — convergence evidence") {
        // Unicode boundary probe: an emoji (U+1F600) and a CJK-extension code
        // point (U+20000) each encode as a surrogate PAIR; streaming them char
        // by char and re-assembling must reproduce the original string exactly
        // (no surrogate dropped, reordered, or replaced).
        val original = "a😀b𠀀c"
        val result   = Stream.fromJavaReader(new java.io.StringReader(original)).runCollect
        assertTrue(result.map(chars => new String(chars.toArray)) == Right(original))
      },
      test("read() returns boxed char then null on EOF") {
        val dq = Reader.fromReader(readerOf('a', 'b', 'c'))
        assertTrue(dq.read[Any](null) == Char.box('a')) &&
        assertTrue(dq.read[Any](null) == Char.box('b')) &&
        assertTrue(dq.read[Any](null) == Char.box('c')) &&
        assertTrue(dq.read[Any](null) == null)
      },
      test("IOException during read throws StreamError and marks reader as closed") {
        val r  = new FailableReader(Array('x'))
        val dq = Reader.fromReader(r)
        assertTrue(dq.read[Any](null) == Char.box('x'))
        r.boom = true
        val result = caught(dq.read[Any](null))
        assertTrue(result != null) &&
        assertTrue(result.isInstanceOf[StreamError]) &&
        assertTrue(result.asInstanceOf[StreamError].value.isInstanceOf[IOException]) &&
        assertTrue(dq.isClosed)
      },
      test("close() on empty reader marks it closed") {
        val dq = Reader.fromReader(emptyReader)
        dq.close()
        assertTrue(dq.isClosed)
      },
      test("isClosed is true after close") {
        val dq = Reader.fromReader(emptyReader)
        dq.close()
        assertTrue(dq.isClosed)
      },
      test("IOException on read() throws StreamError wrapping IOException") {
        val r  = new FailableReader(Array.empty)
        val dq = Reader.fromReader(r)
        r.boom = true
        val result = caught(dq.read[Any](null))
        assertTrue(result != null) &&
        assertTrue(result.isInstanceOf[StreamError]) &&
        assertTrue(result.asInstanceOf[StreamError].value.isInstanceOf[IOException])
      },
      test("close is idempotent") {
        val dq = Reader.fromReader(readerOf('a'))
        dq.close()
        dq.close()
        assertTrue(dq.isClosed)
      },
      test("read returns null after explicit close") {
        val dq = Reader.fromReader(readerOf('a', 'b'))
        dq.close()
        assertTrue(dq.read[Any](null) == null)
      },
      test("readable returns false after close") {
        val dq = Reader.fromReader(readerOf('x'))
        dq.close()
        assertTrue(!dq.readable())
      },
      test("drains chars in order via read()") {
        val dq = Reader.fromReader(readerOf('x', 'y', 'z'))
        val b  = Chunk.newBuilder[Char]
        var v  = dq.read[Any](null)
        while (v != null) { b += v.asInstanceOf[Char]; v = dq.read[Any](null) }
        assert(b.result())(equalTo(Chunk('x', 'y', 'z')))
      }
    ),

    // ---- Stream-level IOException surfacing -----------------------------------

    suite("Stream-level IOException surfacing")(
      test("fromInputStream surfaces IOException as Left") {
        val brokenStream = new InputStream {
          override def read(): Int = throw new IOException("broken")
        }
        val result = Stream.fromInputStream(brokenStream).runCollect
        assertTrue(result.isLeft) &&
        assertTrue(result.swap.getOrElse(null).isInstanceOf[IOException]) &&
        assertTrue(result.swap.getOrElse(null).getMessage == "broken")
      },
      test("fromJavaReader surfaces IOException as Left") {
        val brokenReader = new JReader {
          override def read(buf: Array[Char], off: Int, len: Int): Int =
            throw new IOException("broken reader")
          override def close(): Unit = ()
        }
        val result = Stream.fromJavaReader(brokenReader).runCollect
        assertTrue(result.isLeft) &&
        assertTrue(result.swap.getOrElse(null).isInstanceOf[IOException]) &&
        assertTrue(result.swap.getOrElse(null).getMessage == "broken reader")
      },
      test("fromInputStream surfaces mid-stream IOException as Left") {
        val fis = new FailableInputStream(Array[Byte](1, 2, 3))
        fis.boom = false
        val stream = Stream.fromInputStream(fis).map { v =>
          if (v == 2) fis.boom = true
          v
        }
        val result = stream.runCollect
        assertTrue(result.isLeft) &&
        assertTrue(result.swap.getOrElse(null).isInstanceOf[IOException])
      }
    ),

    // ---- readN ---------------------------------------------------------------

    suite("readN")(
      suite("Byte branch (InputStreamReader)")(
        test("reads bytes from InputStream") {
          val is     = new java.io.ByteArrayInputStream(Array[Byte](1, 2, 3, 4, 5))
          val reader = Stream.compileToReader(Stream.fromInputStreamUnmanaged(is))
          assertTrue(reader.readN[Byte](3) == Chunk[Byte](1, 2, 3))
        },
        test("successive readN calls advance InputStream position") {
          val is     = new java.io.ByteArrayInputStream(Array[Byte](10, 20, 30, 40, 50))
          val reader = Stream.compileToReader(Stream.fromInputStreamUnmanaged(is))
          val c1     = reader.readN[Byte](2)
          val c2     = reader.readN[Byte](2)
          val c3     = reader.readN[Byte](5)
          assertTrue(c1 == Chunk[Byte](10, 20)) &&
          assertTrue(c2 == Chunk[Byte](30, 40)) &&
          assertTrue(c3 == Chunk[Byte](50))
        },
        test("readN on exhausted InputStream returns empty chunk") {
          val is     = new java.io.ByteArrayInputStream(Array[Byte](1, 2))
          val reader = Stream.compileToReader(Stream.fromInputStreamUnmanaged(is))
          reader.readN[Byte](2)
          assertTrue(reader.readN[Byte](1) == Chunk.empty)
        },
        test("readN(0) on InputStream returns empty chunk") {
          val is     = new java.io.ByteArrayInputStream(Array[Byte](1, 2, 3))
          val reader = Stream.compileToReader(Stream.fromInputStreamUnmanaged(is))
          assertTrue(reader.readN[Byte](0) == Chunk.empty)
        }
      ),
      suite("FromRange override")(
        test("readN on range reader returns correct slice") {
          val reader = Stream.compileToReader(Stream.range(0, 10))
          assertTrue(reader.readN[Int](5) == Chunk(0, 1, 2, 3, 4))
        },
        test("successive FromRange readN calls advance position") {
          val reader = Stream.compileToReader(Stream.range(0, 7))
          val c1     = reader.readN[Int](3)
          val c2     = reader.readN[Int](3)
          val c3     = reader.readN[Int](5)
          assertTrue(c1 == Chunk(0, 1, 2)) &&
          assertTrue(c2 == Chunk(3, 4, 5)) &&
          assertTrue(c3 == Chunk(6))
        },
        test("FromRange readN with step > 1") {
          val reader = Stream.compileToReader(Stream.fromRange(Range(0, 20, 3)))
          assertTrue(reader.readN[Int](4) == Chunk(0, 3, 6, 9))
        },
        test("FromRange readN partial (n > remaining)") {
          val reader = Stream.compileToReader(Stream.range(0, 3))
          assertTrue(reader.readN[Int](100) == Chunk(0, 1, 2))
        }
      ),
      suite("Int branch (FromRange)")(
        test("reads at most n elements from range") {
          val reader = Stream.compileToReader(Stream.range(0, 10))
          assertTrue(reader.readN[Int](5) == Chunk(0, 1, 2, 3, 4))
        },
        test("successive readN calls advance position") {
          val reader = Stream.compileToReader(Stream.range(0, 7))
          val c1     = reader.readN[Int](3)
          val c2     = reader.readN[Int](3)
          val c3     = reader.readN[Int](3)
          assertTrue(c1 == Chunk(0, 1, 2)) &&
          assertTrue(c2 == Chunk(3, 4, 5)) &&
          assertTrue(c3 == Chunk(6))
        },
        test("readN(1) returns a single-element chunk") {
          val reader = Stream.compileToReader(Stream.range(0, 3))
          assertTrue(reader.readN[Int](1) == Chunk(0))
        },
        test("readN(100) on 5 elements returns all remaining elements") {
          val reader = Stream.compileToReader(Stream.range(0, 5))
          assertTrue(reader.readN[Int](100) == Chunk(0, 1, 2, 3, 4))
        },
        test("readN after exhausting reader returns empty chunk") {
          val reader = Stream.compileToReader(Stream.range(0, 2))
          val _      = reader.readN[Int](2)
          assertTrue(reader.readN[Int](2) == Chunk.empty)
        }
      ),
      suite("Long branch (FromChunkLong)")(
        test("reads long values from specialized chunk reader") {
          val reader = Stream.compileToReader(Stream.fromChunk(Chunk(1L, 2L, 3L, 4L, 5L)))
          assertTrue(reader.readN[Long](3) == Chunk(1L, 2L, 3L))
        },
        test("large n performs partial read and returns all remaining longs") {
          val reader = Stream.compileToReader(Stream.fromChunk(Chunk(1L, 2L, 3L, 4L, 5L)))
          assertTrue(reader.readN[Long](100) == Chunk(1L, 2L, 3L, 4L, 5L))
        }
      ),
      suite("Float branch (FromChunkFloat)")(
        test("reads float values from specialized chunk reader") {
          val reader = Stream.compileToReader(Stream.fromChunk(Chunk(1.0f, 2.0f, 3.0f)))
          assertTrue(reader.readN[Float](2) == Chunk(1.0f, 2.0f))
        },
        test("readN(0) returns empty chunk") {
          val reader = Stream.compileToReader(Stream.fromChunk(Chunk(1.0f, 2.0f, 3.0f)))
          assertTrue(reader.readN[Float](0) == Chunk.empty)
        }
      ),
      suite("Double branch (FromChunkDouble)")(
        test("reads double values from specialized chunk reader") {
          val reader = Stream.compileToReader(Stream.fromChunk(Chunk(1.0, 2.0, 3.0)))
          assertTrue(reader.readN[Double](2) == Chunk(1.0, 2.0))
        },
        test("remaining doubles can be read after initial readN") {
          val reader = Stream.compileToReader(Stream.fromChunk(Chunk(1.0, 2.0, 3.0)))
          val _      = reader.readN[Double](2)
          assertTrue(reader.readN[Double](2) == Chunk(3.0))
        }
      ),
      suite("AnyRef branch (FromIterable)")(
        test("reads reference values from iterable reader") {
          val reader = Stream.compileToReader(Stream.fromIterable(List("a", "b", "c")))
          assertTrue(reader.readN[String](2) == Chunk("a", "b"))
        },
        test("readN on an already-closed reader returns empty chunk") {
          val reader = Stream.compileToReader(Stream.fromIterable(List("a", "b", "c")))
          reader.close()
          assertTrue(reader.readN[String](1) == Chunk.empty)
        }
      ),
      suite("FromChunk* overrides")(
        test("FromChunkInt successive readN: full then partial") {
          val reader = Stream.compileToReader(Stream.fromChunk(Chunk(1, 2, 3, 4, 5)))
          val c1     = reader.readN[Int](3)
          val c2     = reader.readN[Int](3)
          assertTrue(c1 == Chunk(1, 2, 3)) &&
          assertTrue(c2 == Chunk(4, 5))
        },
        test("FromChunkLong readN returns correct typed Chunk[Long]") {
          val reader = Stream.compileToReader(Stream.fromChunk(Chunk(10L, 20L, 30L, 40L)))
          val result = reader.readN[Long](2)
          assertTrue(result == Chunk(10L, 20L))
        },
        test("FromChunkFloat readN returns correct typed Chunk[Float]") {
          val reader = Stream.compileToReader(Stream.fromChunk(Chunk(1.1f, 2.2f, 3.3f, 4.4f)))
          val result = reader.readN[Float](2)
          assertTrue(result == Chunk(1.1f, 2.2f))
        },
        test("FromChunkDouble readN returns correct typed Chunk[Double]") {
          val reader = Stream.compileToReader(Stream.fromChunk(Chunk(1.5, 2.5, 3.5, 4.5)))
          val result = reader.readN[Double](2)
          assertTrue(result == Chunk(1.5, 2.5))
        },
        test("FromChunk[String] readN returns correct elements") {
          val reader = Stream.compileToReader(Stream.fromChunk(Chunk("a", "b", "c")))
          val result = reader.readN[String](2)
          assertTrue(result == Chunk("a", "b"))
        },
        test("readN respects setLimit") {
          val reader = Stream.compileToReader(Stream.fromChunk(Chunk.fromIterable(0 until 10)))
          reader.setLimit(3)
          val result = reader.readN[Int](10)
          assertTrue(result == Chunk(0, 1, 2))
        },
        test("readN after exhaustion returns empty chunk and isClosed is true") {
          val reader = Stream.compileToReader(Stream.fromChunk(Chunk(1, 2, 3)))
          val _      = reader.readN[Int](3)
          val empty  = reader.readN[Int](5)
          assertTrue(empty == Chunk.empty) &&
          assertTrue(reader.isClosed)
        },
        test("readN on zero-length chunk returns empty") {
          val reader = Stream.compileToReader(Stream.fromChunk(Chunk.empty: Chunk[Int]))
          val result = reader.readN[Int](5)
          assertTrue(result == Chunk.empty)
        },
        test("setSkip then readN skips initial elements") {
          val reader = Stream.compileToReader(Stream.fromChunk(Chunk(10, 20, 30, 40, 50)))
          reader.setSkip(2)
          val result = reader.readN[Int](2)
          assertTrue(result == Chunk(30, 40))
        },
        // `setLimit`/`setSkip` are incremental, call-order-preserving pushdowns of
        // `take`/`drop` over the live window (not independent absolute params):
        // `setLimit(5)` first narrows to positions [0,5), then `setSkip(3)` drops
        // 3 of those, leaving [3,5). This mirrors `take(5).drop(3) == [3,5)` (the
        // take/drop composition law), unlike `drop(3).take(5) == [3,8)`.
        test("setLimit then setSkip interaction (take then drop = [3,5))") {
          val reader = Stream.compileToReader(Stream.fromChunk(Chunk.fromIterable(0 until 10)))
          reader.setLimit(5)
          reader.setSkip(3)
          val result = reader.readN[Int](10)
          assertTrue(result == Chunk(3, 4))
        },
        test("setSkip then setLimit interaction (drop then take = [3,8))") {
          val reader = Stream.compileToReader(Stream.fromChunk(Chunk.fromIterable(0 until 10)))
          reader.setSkip(3)
          reader.setLimit(5)
          val result = reader.readN[Int](10)
          assertTrue(result == Chunk(3, 4, 5, 6, 7))
        }
      )
    ),

    // ---- readUpToN -----------------------------------------------------------

    suite("readUpToN")(
      suite("regressions")(
        // Empty MIDDLE segment: head drains, then one readUpToN must skip the empty
        // middle AND reach the tail — two advances — but only one happens.
        test("concat_emptyMiddle_via_readUpToN_keepsTail [AdversarialConcatReadUpToNSpec]") {
          val s = Stream(1, 2) ++ Stream.empty ++ Stream(3, 4)
          assertTrue(viaReadUpToN(s) == List(1, 2, 3, 4))
        },
        test("control_concat_emptyMiddle_via_runCollect_isCorrect [AdversarialConcatReadUpToNSpec]") {
          val s = Stream(1, 2) ++ Stream.empty ++ Stream(3, 4)
          assertTrue(s.runCollect == Right(Chunk(1, 2, 3, 4)))
        },
        // Two empty middles require three consecutive advances.
        test("concat_twoEmptyMiddles_via_readUpToN_keepsTail [AdversarialConcatReadUpToNSpec]") {
          val s = Stream(1, 2) ++ Stream.empty ++ Stream.empty ++ Stream(3, 4)
          assertTrue(viaReadUpToN(s) == List(1, 2, 3, 4))
        },
        // A filter-emptied middle segment is exhausted-but-not-statically-empty.
        test("concat_filterEmptiedMiddle_via_readUpToN_keepsTail [AdversarialConcatReadUpToNSpec]") {
          val s = Stream(1, 2) ++ Stream(5, 6).filter(_ => false) ++ Stream(3, 4)
          assertTrue(viaReadUpToN(s) == List(1, 2, 3, 4))
        },
        // Convergence anchor: single empty HEAD needs only one advance and works,
        // isolating the defect to the MISSING LOOP rather than advance() itself.
        test("concat_emptyHead_via_readUpToN_works_convergenceAnchor [AdversarialConcatReadUpToNSpec]") {
          val s = Stream.empty ++ Stream(3, 4)
          assertTrue(viaReadUpToN(s) == List(3, 4))
        },
        // --- MappedLong: every output equals the Long sentinel -------------------
        test("mapLong_toLongMaxValue_readUpToN_returnsAllElements [AdversarialReadUpToNSentinelSpec]") {
          val s      = Stream.fromChunk(Chunk(10L, 20L, 30L)).map(_ => LMax)
          val reader = Stream.compileToReader(s)
          assertTrue(reader.readUpToN[Long](10) == Chunk(LMax, LMax, LMax))
        },
        // control: the identical stream via runCollect is correct (proves the
        // defect is isolated to readUpToN, not to map).
        test("control_mapLong_toLongMaxValue_runCollect_isCorrect [AdversarialReadUpToNSentinelSpec]") {
          val s = Stream.fromChunk(Chunk(10L, 20L, 30L)).map(_ => LMax)
          assertTrue(s.runCollect == Right(Chunk(LMax, LMax, LMax)))
        },
        // --- MappedInt (Int source -> Long output) -------------------------------
        test("mapIntToLong_LongMaxValue_readUpToN_returnsAllElements [AdversarialReadUpToNSentinelSpec]") {
          val s      = Stream.fromChunk(Chunk(1, 2, 3)).map(_ => LMax)
          val reader = Stream.compileToReader(s)
          assertTrue(reader.readUpToN[Long](10) == Chunk(LMax, LMax, LMax))
        },
        // --- MappedDouble: every output equals the Double sentinel ----------------
        test("mapDouble_toDoubleMaxValue_readUpToN_returnsAllElements [AdversarialReadUpToNSentinelSpec]") {
          val s      = Stream.fromChunk(Chunk(1.0, 2.0)).map(_ => DMax)
          val reader = Stream.compileToReader(s)
          assertTrue(reader.readUpToN[Double](10) == Chunk(DMax, DMax))
        },
        // --- FilteredLong: a genuine Long.MaxValue element passes the predicate ---
        test("filterLong_withLongMaxValueElement_readUpToN_doesNotTruncate [AdversarialReadUpToNSentinelSpec]") {
          val s      = Stream.fromChunk(Chunk(5L, LMax, 7L)).filter(_ => true)
          val reader = Stream.compileToReader(s)
          assertTrue(reader.readUpToN[Long](10) == Chunk(5L, LMax, 7L))
        },
        // --- Blast radius: concat segment advance uses current.readUpToN, so a
        // sentinel-valued first segment makes the WHOLE first segment vanish -------
        test("concat_mappedLongMaxValue_thenTail_readUpToN_keepsBothSegments [AdversarialReadUpToNSentinelSpec]") {
          val s      = Stream.fromChunk(Chunk(10L, 20L)).map(_ => LMax) ++ Stream.fromChunk(Chunk(7L))
          val reader = Stream.compileToReader(s)
          // pull repeatedly so segment advance is exercised
          val b = Chunk.newBuilder[Long]
          var c = reader.readUpToN[Long](10)
          while (c.nonEmpty) { b ++= c; c = reader.readUpToN[Long](10) }
          assertTrue(b.result() == Chunk(LMax, LMax, 7L))
        },
        test("deepInterpreter_repeated_via_readUpToN_repeatsForever [AdversarialRepeatedReadUpToNSpec]") {
          assertTrue(collectViaReadUpToN(deepInterpreter.repeated, 6) == List(1, 2, 1, 2, 1, 2))
        },
        test("control_deepInterpreter_repeated_via_readPath_repeats [AdversarialRepeatedReadUpToNSpec]") {
          assertTrue(deepInterpreter.repeated.take(6).runCollect == Right(Chunk(1, 2, 1, 2, 1, 2)))
        },
        test("control_chunk_repeated_via_readUpToN_repeats [AdversarialRepeatedReadUpToNSpec]") {
          assertTrue(collectViaReadUpToN(Stream(1, 2).repeated, 6) == List(1, 2, 1, 2, 1, 2))
        }
      )
    ),

    // ---- readLongs / readDoubles ---------------------------------------------

    suite("readLongs / readDoubles")(
      suite("regressions")(
        test("readLongs does not truncate at a real Long.MaxValue element [AdversarialBulkArraySentinelSpec]") {
          // Differential oracle: the boxed readAll path reads all three.
          val oracle = Reader.fromChunk(Chunk(1L, Long.MaxValue, 2L)).readAll[Long]()
          assertTrue(oracle == Chunk(1L, Long.MaxValue, 2L)) && {
            val r   = Reader.fromChunk(Chunk(1L, Long.MaxValue, 2L))
            val buf = new Array[Long](3)
            val n   = r.readLongs(buf, 0, 3)
            // Expected: 3 elements filled [1, Long.MaxValue, 2].
            // Buggy:    returns 1 (stops at the real Long.MaxValue treated as EOF).
            assertTrue(n == 3) &&
            assertTrue(buf.toList == List(1L, Long.MaxValue, 2L))
          }
        },
        test("readDoubles does not truncate at a real Double.MaxValue element [AdversarialBulkArraySentinelSpec]") {
          val oracle = Reader.fromChunk(Chunk(1.0, Double.MaxValue, 2.0)).readAll[Double]()
          assertTrue(oracle == Chunk(1.0, Double.MaxValue, 2.0)) && {
            val r   = Reader.fromChunk(Chunk(1.0, Double.MaxValue, 2.0))
            val buf = new Array[Double](3)
            val n   = r.readDoubles(buf, 0, 3)
            assertTrue(n == 3) &&
            assertTrue(buf.toList == List(1.0, Double.MaxValue, 2.0))
          }
        },
        // --- ConcatReader bulk array reads: a single non-looped advance() reports
        // EOF at an empty/exhausted MIDDLE segment, silently dropping every later
        // segment. The readUpToN sibling of this defect was fixed with a looped
        // advance (see "readUpToN"/"regressions" above); the readInts/readLongs/
        // readFloats/readDoubles paths still advance only once. -------------------
        test("concat_emptyMiddle_readInts_keepsTail [AdversarialConcatBulkReadSpec]") {
          // Control oracle: the scalar path over the same stream is correct.
          val s = Stream(1, 2) ++ Stream.empty ++ Stream(3)
          assertTrue(s.runCollect == Right(Chunk(1, 2, 3))) && {
            val r   = Stream.compileToReader(Stream(1, 2) ++ Stream.empty ++ Stream(3))
            val buf = new Array[Int](8)
            val b   = List.newBuilder[Int]
            var n   = r.readInts(buf, 0, 8)
            while (n > 0) { b ++= buf.take(n); n = r.readInts(buf, 0, 8) }
            // Expected [1, 2, 3]; buggy: readInts returns -1 after the empty
            // middle segment, losing the tail element 3.
            assertTrue(b.result() == List(1, 2, 3))
          }
        },
        test("concat_emptyMiddle_readLongs_keepsTail [AdversarialConcatBulkReadSpec]") {
          val r   = Stream.compileToReader(Stream(1L, 2L) ++ Stream.empty ++ Stream(3L))
          val buf = new Array[Long](8)
          val b   = List.newBuilder[Long]
          var n   = r.readLongs(buf, 0, 8)
          while (n > 0) { b ++= buf.take(n); n = r.readLongs(buf, 0, 8) }
          assertTrue(b.result() == List(1L, 2L, 3L))
        },
        test("concat_emptyMiddle_readDoubles_keepsTail [AdversarialConcatBulkReadSpec]") {
          val r   = Stream.compileToReader(Stream(1.0, 2.0) ++ Stream.empty ++ Stream(3.0))
          val buf = new Array[Double](8)
          val b   = List.newBuilder[Double]
          var n   = r.readDoubles(buf, 0, 8)
          while (n > 0) { b ++= buf.take(n); n = r.readDoubles(buf, 0, 8) }
          assertTrue(b.result() == List(1.0, 2.0, 3.0))
        },
        // Convergence anchor: an empty HEAD needs only one advance and works,
        // isolating the defect to the missing loop rather than advance() itself.
        test("concat_emptyHead_readLongs_works_convergenceAnchor [AdversarialConcatBulkReadSpec]") {
          val r   = Stream.compileToReader(Stream.empty ++ Stream(3L, 4L))
          val buf = new Array[Long](8)
          val b   = List.newBuilder[Long]
          var n   = r.readLongs(buf, 0, 8)
          while (n > 0) { b ++= buf.take(n); n = r.readLongs(buf, 0, 8) }
          assertTrue(b.result() == List(3L, 4L))
        }
      )
    ),

    // ---- sentinel semantics --------------------------------------------------

    suite("sentinel semantics")(
      suite("regressions")(
        test("count_longStreamContainingMaxValue_countsAllElements [AdversarialSentinelSpec]") {
          val result = Stream.fromChunk(Chunk(1L, Long.MaxValue, 3L)).count
          // Expected Right(3); actual Right(1) — Long.MaxValue read as end-of-stream.
          assertTrue(result == Right(3L))
        },
        test("runCollect_longStreamContainingMaxValue_preservesAllElements [AdversarialSentinelSpec]") {
          val result = Stream.fromChunk(Chunk(1L, Long.MaxValue, 3L)).runCollect
          assertTrue(result == Right(Chunk(1L, Long.MaxValue, 3L)))
        },
        test("runCollect_doubleStreamContainingMaxValue_preservesAllElements [AdversarialSentinelSpec]") {
          val result = Stream.fromChunk(Chunk(1.0, Double.MaxValue, 3.0)).runCollect
          assertTrue(result == Right(Chunk(1.0, Double.MaxValue, 3.0)))
        },
        test("take_longStreamContainingMaxValue_preservesAllElements [AdversarialSentinelSpec]") {
          val result = Stream.fromChunk(Chunk(1L, Long.MaxValue, 3L)).take(3).runCollect
          assertTrue(result == Right(Chunk(1L, Long.MaxValue, 3L)))
        },
        test("grouped_longStreamContainingMaxValue_preservesAllElements [AdversarialSentinelSpec]") {
          val result = Stream.fromChunk(Chunk(1L, Long.MaxValue, 3L)).grouped(3).runCollect
          assertTrue(result == Right(Chunk(Chunk(1L, Long.MaxValue, 3L))))
        },
        test("flatMap_longOuterMaxValue_isFlatMappedNotTruncated [AdversarialSentinelSpec]") {
          val result = Stream.fromChunk(Chunk(1L, Long.MaxValue, 3L)).flatMap(x => Stream.succeed(x)).runCollect
          assertTrue(result == Right(Chunk(1L, Long.MaxValue, 3L)))
        },
        test("flatMap_doubleOuterMaxValue_isFlatMappedNotTruncated [AdversarialSentinelSpec]") {
          val result = Stream.fromChunk(Chunk(1.0, Double.MaxValue, 3.0)).flatMap(x => Stream.succeed(x)).runCollect
          assertTrue(result == Right(Chunk(1.0, Double.MaxValue, 3.0)))
        },
        test("deepDoublePipeline_preservesMaxValue [AdversarialSentinelSpec]") {
          // A long identity-map chain forces the interpreter's specialized
          // `readDouble` EOF path (case 38) rather than a single source read.
          val deep =
            (0 until 120).foldLeft(Stream.fromChunk(Chunk(1.0, Double.MaxValue, 3.0)))((s, _) => s.map(identity))
          assertTrue(deep.runCollect == Right(Chunk(1.0, Double.MaxValue, 3.0)))
        },
        test("deepLongPipeline_preservesMaxValue [AdversarialSentinelSpec]") {
          val deep = (0 until 120).foldLeft(Stream.fromChunk(Chunk(1L, Long.MaxValue, 3L)))((s, _) => s.map(identity))
          assertTrue(deep.runCollect == Right(Chunk(1L, Long.MaxValue, 3L)))
        }
      )
    ),

    // ---- close law -----------------------------------------------------------

    suite("close law")(
      suite("regressions")(
        // BUG-R5-02: documented Reader law — "When isClosed and buffer is
        // empty: read(sentinel) returns the sentinel". ConcatReader.read does
        // not consult `done`, so a read AFTER close() re-closes the current
        // segment, MATERIALIZES the next tail segment, and returns its
        // elements instead of the sentinel.
        test("concatReader_readAfterClose_returnsSentinel [AdversarialReaderCloseLawSpec]") {
          val r     = Reader.fromChunk[Int](Chunk(1, 2)) ++ Reader.fromChunk[Int](Chunk(3, 4))
          val first = r.read[Any](null)
          r.close()
          val afterClose = r.read[Any](null)
          assertTrue(first == Int.box(1), r.isClosed, afterClose == null)
        },
        // Same root: a read PAST natural EOF runs advance() again, which closes
        // the final segment a second time — re-running its release finalizer
        // (release must run exactly once).
        test("concatReader_readPastEOF_runsReleaseExactlyOnce [AdversarialReaderCloseLawSpec]") {
          val releases = new java.util.concurrent.atomic.AtomicInteger(0)
          val r        = Reader.fromChunk[Int](Chunk(1)) ++
            Reader.fromChunk[Int](Chunk(2)).withRelease { () => releases.incrementAndGet(); () }
          val drained = List(r.read[Any](null), r.read[Any](null), r.read[Any](null))
          val again   = r.read[Any](null) // post-EOF read must be a benign no-op
          assertTrue(drained == List(1, 2, null), again == null, releases.get == 1)
        },
        // ConcatReader.close() READS the `currentClosed` idempotence flag but
        // never SETS it (the missed sibling of the ITER-5a fix, where
        // CatchAllReader/CatchDefectReader.close were made to set the shared
        // flag). reset() then guards re-closing only with `!current.isClosed`,
        // so a tail segment whose isClosed stays false after close() — e.g. a
        // repeat-mode singleton under `withRelease`/`ensuring` (BUG-R9-02) —
        // is finalized a SECOND time on the close()→reset() boundary
        // (reset-after-close is the pinned zip-eager-close + `repeated`
        // restart sequence). Release must run exactly once per close cycle.
        test("concatReader_closeThenReset_runsReleaseExactlyOnce [AdversarialReaderCloseLawSpec]") {
          val releases = new java.util.concurrent.atomic.AtomicInteger(0)
          val r        = Reader.fromChunk[Int](Chunk(1)) ++
            Reader.repeat[Int](7).withRelease { () => releases.incrementAndGet(); () }
          // Drain into the infinite tail segment so `current` is the tail.
          val drained = List(r.read[Any](null), r.read[Any](null))
          r.close() // finalizes the tail once
          r.reset() // must NOT finalize the already-closed tail again
          assertTrue(drained == List(1, 7), releases.get == 1)
        },
        // BUG-R5-03: base `readByte()` extracts the low byte for EVERY element
        // type (it has explicit Char/Boolean dispatch branches). The generic
        // chunk reader overrides it with a `java.lang.Number` cast, so a Char
        // (or Boolean) reader crashes with ClassCastException instead.
        test("fromChunk_charElements_readByteExtractsLowByte [AdversarialReadByteLaneSpec]") {
          val r: Reader[Char] = Reader.fromChunk[Char](Chunk('A', 'B'))
          assertTrue(r.readByte() == 0x41, r.readByte() == 0x42, r.readByte() == -1)
        }
      )
    ),
    run11SpiConformanceSuite
  )

  // ---- Run #11 convergence probes ------------------------------------------
  // Eleventh (convergence-verification) round: user-supplied minimal-but-lawful
  // Reader SPI implementations (only `read`/`close`/`isClosed` implemented)
  // driven through the base-class DEFAULT bulk/specialized methods and through
  // ConcatReader lane bridging. The library must hold its contracts for any
  // lawful SPI implementation, not just its own readers. Committed as
  // convergence evidence.

  /**
   * Minimal-but-lawful boxed Reader SPI implementation (default AnyRef lane).
   */
  private final class MinimalSpiReader[A](elems: Vector[A]) extends Reader[A] {
    private var idx                     = 0
    private var closed                  = false
    def isClosed: Boolean               = closed || idx >= elems.length
    def read[A1 >: A](sentinel: A1): A1 =
      if (closed || idx >= elems.length) sentinel
      else { val v: A1 = elems(idx); idx += 1; v }
    def close(): Unit = closed = true
  }

  /**
   * Minimal SPI reader advertising the Long lane while implementing only the
   * boxed `read`; the specialized defaults must keep it lossless, including
   * `lastReadWasEOF` disambiguation for real `Long.MaxValue` elements.
   */
  private final class MinimalLongTaggedSpiReader(elems: Vector[Long]) extends Reader[Long] {
    private var idx                        = 0
    private var closed                     = false
    override def jvmType: JvmType          = JvmType.Long
    def isClosed: Boolean                  = closed || idx >= elems.length
    def read[A1 >: Long](sentinel: A1): A1 =
      if (closed || idx >= elems.length) sentinel
      else { val v: A1 = elems(idx); idx += 1; v }
    def close(): Unit = closed = true
  }

  private def run11SpiConformanceSuite = suite("run11 SPI conformance")(
    test("minimalReader_readAll_readN_readUpToN_skip_defaults") {
      val all    = new MinimalSpiReader[Int]((0 until 10).toVector).readAll[Int]()
      val r      = new MinimalSpiReader[Int]((0 until 10).toVector)
      val first3 = r.readN[Int](3)
      val upTo4  = r.readUpToN[Int](4)
      r.skip(2L)
      val rest = r.readAll[Int]()
      assertTrue(
        all.toList == (0 until 10).toList,
        first3.toList == List(0, 1, 2),
        upTo4.toList == List(3, 4, 5, 6),
        rest.toList == List(9),
        r.readN[Int](5).isEmpty,
        r.readUpToN[Int](5).isEmpty
      )
    },
    test("minimalByteReader_readBytes_readByte_lowByteDefaults") {
      val r   = new MinimalSpiReader[Byte](Vector[Byte](1, 2, -1, 127))
      val buf = new Array[Byte](8)
      val n   = r.readBytes(buf, 0, 8)
      assertTrue(n == 4, buf.take(4).toList == List[Byte](1, 2, -1, 127), r.readByte() == -1)
    },
    test("minimalIntReader_readInts_bulkDefault") {
      val r   = new MinimalSpiReader[Int](Vector(5, 6, 7))
      val buf = new Array[Int](5)
      val n   = r.readInts(buf, 0, 5)
      assertTrue(n == 3, buf.take(3).toList == List(5, 6, 7), r.readInts(buf, 0, 5) == -1)
    },
    test("minimalLongTaggedReader_readLongs_realMaxValueElement_lossless") {
      val r   = new MinimalLongTaggedSpiReader(Vector(1L, Long.MaxValue, 3L))
      val buf = new Array[Long](8)
      val n   = r.readLongs(buf, 0, 8)
      assertTrue(n == 3, buf.take(3).toList == List(1L, Long.MaxValue, 3L), r.readLongs(buf, 0, 8) == -1)
    },
    test("minimalReader_concat_bothOrders_withLibraryReaders_laneBridged") {
      val viaCustomHead =
        (new MinimalSpiReader[Int](Vector(1, 2)) ++ Reader.fromChunk[Int](Chunk(3, 4))).readAll[Int]()
      val viaCustomTail =
        (Reader.fromChunk[Int](Chunk(1, 2)) ++ new MinimalSpiReader[Int](Vector(3, 4))).readAll[Int]()
      assertTrue(viaCustomHead.toList == List(1, 2, 3, 4), viaCustomTail.toList == List(1, 2, 3, 4))
    },
    test("minimalReader_skipPastEnd_returnsEarlyAndReadsSentinel") {
      val r = new MinimalSpiReader[Int](Vector(1, 2))
      r.skip(10L)
      assertTrue(r.read[Any](null) == null, r.isClosed)
    }
  )
}
