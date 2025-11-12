package zio.blocks.json

import zio.blocks.schema.{Schema, SchemaError}
import zio.test.Assertion._
import zio.test._
import java.nio.ByteBuffer
import java.util
import scala.collection.immutable.ArraySeq

object JsonTestUtils {
  def roundTrip[A](value: A, expectedJson: String)(implicit schema: Schema[A]): TestResult =
    roundTrip(value, expectedJson, schema.derive(JsonFormat.deriver))

  def roundTrip[A](value: A, expectedJson: String, codec: JsonBinaryCodec[A]): TestResult = {
    val byteBuffer = ByteBuffer.allocate(1024)
    codec.encode(value, byteBuffer)
    val encodedBySchema = util.Arrays.copyOf(byteBuffer.array, byteBuffer.position)
    val output          = new java.io.ByteArrayOutputStream(1024)
    codec.encode(value, output)
    output.close()
    val encodedBySchema2 = output.toByteArray
    val encodedBySchema3 = codec.encode(value)
    assert(new String(encodedBySchema, "UTF-8"))(equalTo(expectedJson)) &&
    assert(ArraySeq.unsafeWrapArray(encodedBySchema))(equalTo(ArraySeq.unsafeWrapArray(encodedBySchema2))) &&
    assert(ArraySeq.unsafeWrapArray(encodedBySchema))(equalTo(ArraySeq.unsafeWrapArray(encodedBySchema3))) &&
    assert(codec.decode(encodedBySchema))(isRight(equalTo(value))) &&
    assert(codec.decode(toInputStream(encodedBySchema)))(isRight(equalTo(value))) &&
    assert(codec.decode(toHeapByteBuffer(encodedBySchema)))(isRight(equalTo(value))) &&
    assert(codec.decode(toDirectByteBuffer(encodedBySchema)))(isRight(equalTo(value)))
  }

  def decode[A](json: String, expectedValue: A)(implicit schema: Schema[A]): TestResult =
    decode(json, expectedValue, schema.derive(JsonFormat.deriver))

  def decode[A](json: String, expectedValue: A, codec: JsonBinaryCodec[A]): TestResult = {
    val jsonBytes = json.getBytes("UTF-8")
    assert(codec.decode(jsonBytes))(isRight(equalTo(expectedValue))) &&
    assert(codec.decode(toInputStream(jsonBytes)))(isRight(equalTo(expectedValue))) &&
    assert(codec.decode(toHeapByteBuffer(jsonBytes)))(isRight(equalTo(expectedValue))) &&
    assert(codec.decode(toDirectByteBuffer(jsonBytes)))(isRight(equalTo(expectedValue)))
  }

  def decodeError[A](invalidJson: String, codec: JsonBinaryCodec[A], error: String): TestResult = {
    val bytes = invalidJson.getBytes("UTF-8")
    assert(codec.decode(bytes))(isLeft(hasError(error))) &&
    assert(codec.decode(toInputStream(bytes)))(isLeft(hasError(error))) &&
    assert(codec.decode(toHeapByteBuffer(bytes)))(isLeft(hasError(error))) &&
    assert(codec.decode(toDirectByteBuffer(bytes)))(isLeft(hasError(error)))
  }

  private[this] def hasError(message: String): Assertion[SchemaError] =
    hasField[SchemaError, String]("getMessage", _.getMessage, containsString(message))

  private[this] def toInputStream(bs: Array[Byte]): java.io.InputStream = new java.io.ByteArrayInputStream(bs)

  private[this] def toHeapByteBuffer(bs: Array[Byte]): ByteBuffer = ByteBuffer.wrap(bs)

  private[this] def toDirectByteBuffer(bs: Array[Byte]): ByteBuffer =
    ByteBuffer.allocateDirect(1024).put(bs).position(0).limit(bs.length)
}
