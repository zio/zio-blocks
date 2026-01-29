package zio.blocks.schema.avro

import org.apache.avro.generic.{GenericDatumReader, GenericDatumWriter}
import org.apache.avro.io.{DecoderFactory, EncoderFactory}
import org.apache.avro.{Schema => AvroSchema}
import zio.blocks.schema.{Schema, SchemaError}
import zio.test.Assertion._
import zio.test._
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util
import java.util.concurrent.ConcurrentHashMap
import scala.collection.immutable.ArraySeq

object AvroTestUtils {
  private[this] val codecs = new ConcurrentHashMap[Schema[?], AvroBinaryCodec[?]]()

  private[this] def codec[A](schema: Schema[A]): AvroBinaryCodec[A] =
    codecs.computeIfAbsent(schema, (s: Schema[?]) => s.derive(AvroFormat.deriver)).asInstanceOf[AvroBinaryCodec[A]]

  def avroSchema[A](expectedAvroSchemaJson: String)(implicit schema: Schema[A]): TestResult =
    avroSchema(expectedAvroSchemaJson, codec(schema))

  def avroSchema[A](expectedAvroSchemaJson: String, codec: AvroBinaryCodec[A]): TestResult =
    assert(codec.avroSchema.toString)(equalTo(expectedAvroSchemaJson))

  def roundTrip[A](value: A, expectedLength: Int)(implicit schema: Schema[A]): TestResult =
    roundTrip(value, expectedLength, codec(schema))

  def roundTrip[A](value: A, expectedLength: Int, codec: AvroBinaryCodec[A]): TestResult = {
    val heapByteBuffer = ByteBuffer.allocate(maxBufSize)
    codec.encode(value, heapByteBuffer)
    val encodedBySchema1 = util.Arrays.copyOf(heapByteBuffer.array, heapByteBuffer.position)
    val directByteBuffer = ByteBuffer.allocateDirect(maxBufSize)
    codec.encode(value, directByteBuffer)
    val encodedBySchema2 = util.Arrays.copyOf(
      {
        val dup = directByteBuffer.duplicate()
        val out = new Array[Byte](dup.position)
        dup.position(0)
        dup.get(out)
        out
      },
      directByteBuffer.position
    )
    val output = new java.io.ByteArrayOutputStream(maxBufSize)
    codec.encode(value, output)
    output.close()
    val encodedBySchema3 = output.toByteArray
    val encodedBySchema4 = codec.encode(value)
    assert(encodedBySchema1.length)(equalTo(expectedLength)) &&
    assert(ArraySeq.unsafeWrapArray(encodedBySchema1))(equalTo(ArraySeq.unsafeWrapArray(encodedBySchema2))) &&
    assert(ArraySeq.unsafeWrapArray(encodedBySchema1))(equalTo(ArraySeq.unsafeWrapArray(encodedBySchema3))) &&
    assert(ArraySeq.unsafeWrapArray(encodedBySchema1))(equalTo(ArraySeq.unsafeWrapArray(encodedBySchema4))) &&
    assert(codec.decode(encodedBySchema1))(isRight(equalTo(value))) &&
    assert(codec.decode(toInputStream(encodedBySchema1)))(isRight(equalTo(value))) &&
    assert(codec.decode(toHeapByteBuffer(encodedBySchema1)))(isRight(equalTo(value))) &&
    assert(codec.decode(toDirectByteBuffer(encodedBySchema1)))(isRight(equalTo(value))) && {
      val avroSchema    = (new AvroSchema.Parser).parse(codec.avroSchema.toString)
      val binaryDecoder = DecoderFactory.get().binaryDecoder(encodedBySchema1, null)
      val datum         = new GenericDatumReader[Any](avroSchema).read(null, binaryDecoder)
      val encodedByAvro = new ByteArrayOutputStream(maxBufSize)
      val binaryEncoder = EncoderFactory.get().directBinaryEncoder(encodedByAvro, null)
      new GenericDatumWriter[Any](avroSchema).write(datum, binaryEncoder)
      assert(util.Arrays.compare(encodedBySchema1, encodedByAvro.toByteArray))(equalTo(0))
    }
  }

  def decodeError[A](bytes: Array[Byte], error: String)(implicit schema: Schema[A]): TestResult =
    decodeError(bytes, codec(schema), error)

  def decodeError[A](bytes: Array[Byte], codec: AvroBinaryCodec[A], error: String): TestResult =
    assert(codec.decode(bytes))(isLeft(hasError(error))) &&
      assert(codec.decode(toInputStream(bytes)))(isLeft(hasError(error))) &&
      assert(codec.decode(toHeapByteBuffer(bytes)))(isLeft(hasError(error))) &&
      assert(codec.decode(toDirectByteBuffer(bytes)))(isLeft(hasError(error)))

  private[this] def hasError(message: String) =
    hasField[SchemaError, String]("getMessage", _.getMessage, containsString(message))

  private[this] def toInputStream(bs: Array[Byte]): java.io.InputStream = new java.io.ByteArrayInputStream(bs)

  private[this] def toHeapByteBuffer(bs: Array[Byte]): ByteBuffer = ByteBuffer.wrap(bs)

  private[this] def toDirectByteBuffer(bs: Array[Byte]): ByteBuffer =
    ByteBuffer.allocateDirect(maxBufSize).put(bs).position(0).limit(bs.length)

  private[this] val maxBufSize = 1024
}
