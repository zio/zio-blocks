package zio.blocks.schema.toon

import zio.blocks.schema._
import zio.blocks.schema.toon._
import zio.test._
import java.nio.charset.StandardCharsets

object ToonTestUtils {
  def encode[A](schema: Schema[A], value: A): String = {
    val codec  = ToonBinaryCodecDeriver.derive(schema).asInstanceOf[ToonBinaryCodec[A]]
    val writer = new ToonWriter(new Array[Byte](4096), ToonWriterConfig)
    codec.encodeValue(value, writer)
    new String(writer.buf, 0, writer.count, StandardCharsets.UTF_8)
  }

  def decode[A](schema: Schema[A], input: String): A = {
    val codec  = ToonBinaryCodecDeriver.derive(schema).asInstanceOf[ToonBinaryCodec[A]]
    val bytes  = input.getBytes(StandardCharsets.UTF_8)
    val reader = new ToonReader(bytes, new Array[Char](1024), bytes.length, ToonReaderConfig)
    codec.decodeValue(reader, codec.nullValue)
  }

  def roundTrip[A](schema: Schema[A], value: A): Either[String, A] =
    try {
      val encoded = encode(schema, value)
      val decoded = decode(schema, encoded)
      if (decoded == value) Right(decoded)
      else Left(s"Round trip failed. Encoded: $encoded, Decoded: $decoded")
    } catch {
      case e: Exception => Left(e.getMessage)
    }

  def assertEncodes[A](schema: Schema[A], value: A, expected: String): TestResult = {
    val actual = encode(schema, value)
    assertTrue(actual == expected)
  }
}
