package zio.blocks.streams

import zio.blocks.chunk.Chunk
import zio.blocks.streams.internal.StreamError
import zio.blocks.streams.io.Reader
import zio.test._
import zio.test.Assertion._

import java.io.{ByteArrayInputStream, CharArrayReader, IOException, InputStream, Reader => JReader}
object ReaderFromJavaSpec extends StreamsBaseSpec {

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

  def spec: Spec[TestEnvironment, Any] = suite("Reader.fromInputStream / fromReader")(
    suite("fromInputStream")(
      test("read() returns widened Int (0-255) then null on EOF") {
        val dq = Reader.fromInputStream(streamOf(1, 2, 3))
        assertTrue(dq.read[Any](null) == Int.box(1)) &&
        assertTrue(dq.read[Any](null) == Int.box(2)) &&
        assertTrue(dq.read[Any](null) == Int.box(3)) &&
        assertTrue(dq.read[Any](null) == null)
      },
      test("read() returns null on empty stream") {
        val dq = Reader.fromInputStream(emptyStream)
        assertTrue(dq.read[Any](null) == null)
      },
      test("IOException during read throws StreamError and marks reader as closed") {
        val is = new FailableInputStream(Array[Byte](1))
        val dq = Reader.fromInputStream(is)
        assertTrue(dq.read[Any](null) == Int.box(1))
        is.boom = true
        val result = scala.util.Try(dq.read[Any](null))
        assertTrue(result.isFailure) &&
        assertTrue(result.failed.get.isInstanceOf[StreamError]) &&
        assertTrue(result.failed.get.asInstanceOf[StreamError].value.isInstanceOf[IOException]) &&
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
        val b  = Chunk.newBuilder[Int]
        var v  = dq.read[Any](null)
        while (v != null) { b += v.asInstanceOf[Int]; v = dq.read[Any](null) }
        assert(b.result())(equalTo(Chunk(5, 6, 7)))
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
      }
    ),

    suite("fromReader")(
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
        val result = scala.util.Try(dq.read[Any](null))
        assertTrue(result.isFailure) &&
        assertTrue(result.failed.get.isInstanceOf[StreamError]) &&
        assertTrue(result.failed.get.asInstanceOf[StreamError].value.isInstanceOf[IOException]) &&
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
        val result = scala.util.Try(dq.read[Any](null))
        assertTrue(result.isFailure) &&
        assertTrue(result.failed.get.isInstanceOf[StreamError]) &&
        assertTrue(result.failed.get.asInstanceOf[StreamError].value.isInstanceOf[IOException])
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
    )
  )
}
