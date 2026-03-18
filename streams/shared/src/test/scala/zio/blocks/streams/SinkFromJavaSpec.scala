package zio.blocks.streams

import zio.blocks.chunk.Chunk
import zio.test._

import java.io.{ByteArrayOutputStream, StringWriter}

object SinkFromJavaSpec extends StreamsBaseSpec {

  def spec: Spec[TestEnvironment, Any] = suite("Sink.fromOutputStream / fromJavaWriter")(
    suite("fromOutputStream")(
      test("writes all bytes to the output stream") {
        val bos    = new ByteArrayOutputStream()
        val sink   = Sink.fromOutputStream(bos)
        val bytes  = Chunk[Byte](1, 2, 3)
        val result = Stream.fromChunk(bytes).run(sink)
        assertTrue(result == Right(())) &&
        assertTrue(bos.toByteArray.toList == List[Byte](1, 2, 3))
      },
      test("handles empty stream") {
        val bos    = new ByteArrayOutputStream()
        val sink   = Sink.fromOutputStream(bos)
        val result = Stream.fromChunk(Chunk.empty[Byte]).run(sink)
        assertTrue(result == Right(())) &&
        assertTrue(bos.size() == 0)
      },
      test("writes high bytes correctly (0xFF)") {
        val bos    = new ByteArrayOutputStream()
        val sink   = Sink.fromOutputStream(bos)
        val bytes  = Chunk[Byte](0xff.toByte)
        val result = Stream.fromChunk(bytes).run(sink)
        assertTrue(result == Right(())) &&
        assertTrue((bos.toByteArray()(0) & 0xff) == 0xff)
      },
      test("writes single byte stream") {
        val bos    = new ByteArrayOutputStream()
        val sink   = Sink.fromOutputStream(bos)
        val result = Stream.fromChunk(Chunk[Byte](42)).run(sink)
        assertTrue(result == Right(())) &&
        assertTrue(bos.toByteArray.toList == List[Byte](42))
      },
      test("writes multiple chunks in order") {
        val bos    = new ByteArrayOutputStream()
        val sink   = Sink.fromOutputStream(bos)
        val stream = Stream.fromChunk(Chunk[Byte](1, 2)) ++
          Stream.fromChunk(Chunk[Byte](3, 4))
        val result = stream.run(sink)
        assertTrue(result == Right(())) &&
        assertTrue(bos.toByteArray.toList == List[Byte](1, 2, 3, 4))
      },
      test("writes negative byte values correctly") {
        val bos    = new ByteArrayOutputStream()
        val sink   = Sink.fromOutputStream(bos)
        val bytes  = Chunk[Byte](-1, -128, 127)
        val result = Stream.fromChunk(bytes).run(sink)
        assertTrue(result == Right(())) &&
        assertTrue(bos.toByteArray.toList == List[Byte](-1, -128, 127))
      }
    ),
    suite("fromJavaWriter")(
      test("writes all chars to the writer") {
        val sw     = new StringWriter()
        val sink   = Sink.fromJavaWriter(sw)
        val result = Stream.fromJavaReader(new java.io.StringReader("hello")).run(sink)
        assertTrue(result == Right(())) &&
        assertTrue(sw.toString == "hello")
      },
      test("handles empty stream") {
        val sw     = new StringWriter()
        val sink   = Sink.fromJavaWriter(sw)
        val result = Stream.fromJavaReader(new java.io.StringReader("")).run(sink)
        assertTrue(result == Right(())) &&
        assertTrue(sw.toString == "")
      },
      test("writes unicode chars") {
        val sw     = new StringWriter()
        val sink   = Sink.fromJavaWriter(sw)
        val result = Stream.fromJavaReader(new java.io.StringReader("café")).run(sink)
        assertTrue(result == Right(())) &&
        assertTrue(sw.toString == "café")
      },
      test("writes single character stream") {
        val sw     = new StringWriter()
        val sink   = Sink.fromJavaWriter(sw)
        val result = Stream.fromJavaReader(new java.io.StringReader("X")).run(sink)
        assertTrue(result == Right(())) &&
        assertTrue(sw.toString == "X")
      },
      test("writes chars from chunk-based stream") {
        val sw     = new StringWriter()
        val sink   = Sink.fromJavaWriter(sw)
        val result = Stream.fromChunk(Chunk('a', 'b', 'c')).run(sink)
        assertTrue(result == Right(())) &&
        assertTrue(sw.toString == "abc")
      },
      test("writes multiple concatenated streams") {
        val sw     = new StringWriter()
        val sink   = Sink.fromJavaWriter(sw)
        val stream = Stream.fromJavaReader(new java.io.StringReader("hel")) ++
          Stream.fromJavaReader(new java.io.StringReader("lo"))
        val result = stream.run(sink)
        assertTrue(result == Right(())) &&
        assertTrue(sw.toString == "hello")
      }
    )
  )
}
