package zio.blocks.avro

import org.apache.avro.generic.{GenericDatumReader, GenericDatumWriter}
import org.apache.avro.io.{DecoderFactory, EncoderFactory}
import org.apache.avro.{Schema => AvroSchema}
import zio.blocks.schema.Schema
import zio.blocks.schema.codec.BinaryCodec
import zio.test.Assertion._
import zio.test._
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util

object AvroTestUtils {
  private[this] val header    = "+----------+-------------------------------------------------+------------------+"
  private[this] val colTitles = "|          |  0  1  2  3  4  5  6  7  8  9  a  b  c  d  e  f | 0123456789abcdef |"

  def hexDump(bytes: Array[Byte]): String = {
    val sb            = new StringBuilder
    val lineSeparator = System.lineSeparator()
    sb.append(header)
      .append(lineSeparator)
      .append(colTitles)
      .append(lineSeparator)
      .append(header)
      .append(lineSeparator)
    bytes.grouped(16).zipWithIndex.foreach { case (chunk, rowIndex) =>
      val offset    = f"${rowIndex * 16}%08x"
      val hexPart   = chunk.map(b => f"$b%02x").mkString(" ")
      val paddedHex = f"$hexPart%-47s"
      val asciiPart = chunk.map { byte =>
        val char = byte.toChar
        if (char >= 32 && char <= 126) char else '.'
      }.mkString
      val paddedAscii = f"$asciiPart%-16s"
      sb.append(f"| $offset | $paddedHex | $paddedAscii |")
        .append(lineSeparator)
    }
    sb.append(header).append(lineSeparator).toString
  }

  def roundTrip[A](value: A, expectedLength: Int)(implicit schema: Schema[A]): TestResult = {
    val avroSchema = AvroSchemaCodec.toAvroSchema(schema)
    val codec      = schema.derive(AvroFormat.deriver)
    roundTrip(value, expectedLength, avroSchema, codec)
  }

  def roundTrip[A](value: A, expectedLength: Int, avroSchema: AvroSchema, codec: BinaryCodec[A]): TestResult = {
    val encodedBySchema = encodeToByteArray(out => codec.encode(value, out))
    assert(encodedBySchema.length)(equalTo(expectedLength)) &&
    assert(codec.decode(toHeapByteBuffer(encodedBySchema)))(isRight(equalTo(value))) &&
    assert(codec.decode(toDirectByteBuffer(encodedBySchema)))(isRight(equalTo(value))) && {
      val binaryDecoder = DecoderFactory.get().binaryDecoder(encodedBySchema, null)
      val datum         = new GenericDatumReader[Any](avroSchema).read(null.asInstanceOf[Any], binaryDecoder)
      val encodedByAvro = new ByteArrayOutputStream(1024)
      val binaryEncoder = EncoderFactory.get().directBinaryEncoder(encodedByAvro, null)
      new GenericDatumWriter[Any](avroSchema).write(datum, binaryEncoder)
      assert(util.Arrays.compare(encodedBySchema, encodedByAvro.toByteArray))(equalTo(0))
    }
  }

  def shortRoundTrip[A](value: A, expectedLength: Int)(implicit schema: Schema[A]): TestResult =
    shortRoundTrip(value, expectedLength, schema.derive(AvroFormat.deriver))

  def shortRoundTrip[A](value: A, expectedLength: Int, codec: BinaryCodec[A]): TestResult = {
    val encodedBySchema = encodeToByteArray(out => codec.encode(value, out))
    // println(hexDump(encodedBySchema))
    assert(encodedBySchema.length)(equalTo(expectedLength)) &&
    assert(codec.decode(toHeapByteBuffer(encodedBySchema)))(isRight(equalTo(value))) &&
    assert(codec.decode(toDirectByteBuffer(encodedBySchema)))(isRight(equalTo(value)))
  }

  private[this] def encodeToByteArray(f: ByteBuffer => Unit): Array[Byte] = {
    val byteBuffer = ByteBuffer.allocate(1024)
    f(byteBuffer)
    util.Arrays.copyOf(byteBuffer.array, byteBuffer.position)
  }

  private[this] def toHeapByteBuffer(bs: Array[Byte]): ByteBuffer = ByteBuffer.wrap(bs)

  private[this] def toDirectByteBuffer(bs: Array[Byte]): ByteBuffer =
    ByteBuffer.allocateDirect(1024).put(bs).position(0).limit(bs.length)
}
