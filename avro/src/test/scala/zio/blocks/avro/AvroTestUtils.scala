package zio.blocks.avro

import org.apache.avro.generic.{GenericDatumReader, GenericDatumWriter}
import org.apache.avro.io.{DecoderFactory, EncoderFactory}
import org.apache.avro.{Schema => AvroSchema}
import zio.blocks.schema.{Schema, SchemaError}
import zio.test.Assertion._
import zio.test._
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util

object AvroTestUtils {
  private[this] val header    = "+----------+-------------------------------------------------+------------------+"
  private[this] val colTitles = "|          |  0  1  2  3  4  5  6  7  8  9  a  b  c  d  e  f | 0123456789abcdef |"

  def hexDump(bytes: Array[Byte]): String = {
    val sb = new StringBuilder
    val ls = System.lineSeparator()
    sb.append(header).append(ls).append(colTitles).append(ls).append(header).append(ls)
    bytes.grouped(16).zipWithIndex.foreach { case (chunk, rowIndex) =>
      val offset    = f"${rowIndex * 16}%08x"
      val hexPart   = chunk.map(b => f"$b%02x").mkString(" ")
      val paddedHex = f"$hexPart%-47s"
      val asciiPart = chunk.map { byte =>
        val char = byte.toChar
        if (char >= 32 && char <= 126) char else '.'
      }.mkString
      val paddedAscii = f"$asciiPart%-16s"
      sb.append(f"| $offset | $paddedHex | $paddedAscii |").append(ls)
    }
    sb.append(header).append(ls).toString
  }

  def roundTrip[A](value: A, expectedLength: Int)(implicit schema: Schema[A]): TestResult =
    roundTrip(value, expectedLength, AvroSchemaCodec.toAvroSchema(schema), schema.derive(AvroFormat.deriver))

  def roundTrip[A](value: A, expectedLength: Int, avroSchema: AvroSchema, codec: AvroBinaryCodec[A]): TestResult = {
    val byteBuffer = ByteBuffer.allocate(1024)
    codec.encode(value, byteBuffer)
    val encodedBySchema = util.Arrays.copyOf(byteBuffer.array, byteBuffer.position)
    val output          = new java.io.ByteArrayOutputStream(1024)
    codec.encode(value, output)
    output.close()
    val encodedBySchema2 = output.toByteArray
    val encodedBySchema3 = codec.encode(value)
    assert(encodedBySchema.length)(equalTo(expectedLength)) &&
    assert(util.Arrays.compare(encodedBySchema, encodedBySchema2))(equalTo(0)) &&
    assert(util.Arrays.compare(encodedBySchema, encodedBySchema3))(equalTo(0)) &&
    assert(codec.decode(encodedBySchema))(isRight(equalTo(value))) &&
    assert(codec.decode(toInputStream(encodedBySchema)))(isRight(equalTo(value))) &&
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

  def shortRoundTrip[A](value: A, expectedLength: Int, codec: AvroBinaryCodec[A]): TestResult = {
    val byteBuffer = ByteBuffer.allocate(1024)
    codec.encode(value, byteBuffer)
    val encodedBySchema = util.Arrays.copyOf(byteBuffer.array, byteBuffer.position)
    val output          = new java.io.ByteArrayOutputStream(1024)
    codec.encode(value, output)
    output.close()
    val encodedBySchema2 = output.toByteArray
    val encodedBySchema3 = codec.encode(value)
    // println(hexDump(encodedBySchema))
    assert(encodedBySchema.length)(equalTo(expectedLength)) &&
    assert(util.Arrays.compare(encodedBySchema, encodedBySchema2))(equalTo(0)) &&
    assert(util.Arrays.compare(encodedBySchema, encodedBySchema3))(equalTo(0)) &&
    assert(codec.decode(encodedBySchema))(isRight(equalTo(value))) &&
    assert(codec.decode(toInputStream(encodedBySchema)))(isRight(equalTo(value))) &&
    assert(codec.decode(toHeapByteBuffer(encodedBySchema)))(isRight(equalTo(value))) &&
    assert(codec.decode(toDirectByteBuffer(encodedBySchema)))(isRight(equalTo(value)))
  }

  def decodeError[A](bytes: Array[Byte], codec: AvroBinaryCodec[A], expectedMessage: String): TestResult =
    decodeError(bytes, codec, SchemaError.expectationMismatch(Nil, expectedMessage))

  def decodeError[A](bytes: Array[Byte], codec: AvroBinaryCodec[A], error: SchemaError): TestResult =
    assert(codec.decode(bytes))(isLeft(equalTo(error))) &&
      assert(codec.decode(toInputStream(bytes)))(isLeft(equalTo(error))) &&
      assert(codec.decode(toHeapByteBuffer(bytes)))(isLeft(equalTo(error))) &&
      assert(codec.decode(toDirectByteBuffer(bytes)))(isLeft(equalTo(error)))

  private[this] def toInputStream(bs: Array[Byte]): java.io.InputStream = new java.io.ByteArrayInputStream(bs)

  private[this] def toHeapByteBuffer(bs: Array[Byte]): ByteBuffer = ByteBuffer.wrap(bs)

  private[this] def toDirectByteBuffer(bs: Array[Byte]): ByteBuffer =
    ByteBuffer.allocateDirect(1024).put(bs).position(0).limit(bs.length)
}
