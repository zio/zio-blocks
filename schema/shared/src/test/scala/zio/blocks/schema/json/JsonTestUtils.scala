package zio.blocks.schema.json

import zio.blocks.schema.Schema
import zio.test.Assertion._
import zio.test._
import java.nio.ByteBuffer
import java.util
import scala.collection.immutable.ArraySeq
import scala.util.Try

object JsonTestUtils {
  def roundTrip[A](value: A, expectedJson: String)(implicit schema: Schema[A]): TestResult =
    roundTrip(value, expectedJson, schema.derive(JsonFormat.deriver))

  def roundTrip[A](value: A, expectedJson: String, readerConfig: ReaderConfig, writerConfig: WriterConfig)(implicit
    schema: Schema[A]
  ): TestResult =
    roundTrip(value, expectedJson, schema.derive(JsonFormat.deriver), readerConfig, writerConfig)

  def roundTrip[A](
    value: A,
    expectedJson: String,
    codec: JsonBinaryCodec[A],
    readerConfig: ReaderConfig = ReaderConfig,
    writerConfig: WriterConfig = WriterConfig
  ): TestResult = {
    val heapByteBuffer = ByteBuffer.allocate(1024)
    codec.encode(value, heapByteBuffer, writerConfig)
    val encodedBySchema1 = util.Arrays.copyOf(heapByteBuffer.array, heapByteBuffer.position)
    val directByteBuffer = ByteBuffer.allocateDirect(1024)
    codec.encode(value, directByteBuffer, writerConfig)
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
    val output = new java.io.ByteArrayOutputStream(1024)
    codec.encode(value, output, writerConfig)
    output.close()
    val encodedBySchema3 = output.toByteArray
    val encodedBySchema4 = codec.encode(value, writerConfig)
    assert(new String(encodedBySchema1, "UTF-8"))(equalTo(expectedJson)) &&
    assert(ArraySeq.unsafeWrapArray(encodedBySchema1))(equalTo(ArraySeq.unsafeWrapArray(encodedBySchema2))) &&
    assert(ArraySeq.unsafeWrapArray(encodedBySchema1))(equalTo(ArraySeq.unsafeWrapArray(encodedBySchema3))) &&
    assert(ArraySeq.unsafeWrapArray(encodedBySchema1))(equalTo(ArraySeq.unsafeWrapArray(encodedBySchema4))) &&
    assert(codec.decode(encodedBySchema1, readerConfig))(isRight(equalTo(value))) &&
    assert(codec.decode(toInputStream(encodedBySchema1), readerConfig))(isRight(equalTo(value))) &&
    assert(codec.decode(toHeapByteBuffer(encodedBySchema1), readerConfig))(isRight(equalTo(value))) &&
    assert(codec.decode(toDirectByteBuffer(encodedBySchema1), readerConfig))(isRight(equalTo(value)))
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

  def encodeError[A](value: A, codec: JsonBinaryCodec[A], error: String): TestResult =
    assert(Try(codec.encode(value, ByteBuffer.allocate(1024))).toEither)(isLeft(hasError(error))) &&
      assert(Try(codec.encode(value, ByteBuffer.allocateDirect(1024))).toEither)(isLeft(hasError(error))) &&
      assert(Try(codec.encode(value, new java.io.ByteArrayOutputStream(1024))).toEither)(isLeft(hasError(error))) &&
      assert(Try(codec.encode(value)).toEither)(isLeft(hasError(error)))

  private[this] def hasError(message: String): Assertion[Throwable] =
    hasField[Throwable, String]("getMessage", _.getMessage, containsString(message))

  private[this] def toInputStream(bs: Array[Byte]): java.io.InputStream = new java.io.ByteArrayInputStream(bs)

  private[this] def toHeapByteBuffer(bs: Array[Byte]): ByteBuffer = ByteBuffer.wrap(bs)

  private[this] def toDirectByteBuffer(bs: Array[Byte]): ByteBuffer =
    ByteBuffer.allocateDirect(1024).put(bs).position(0).limit(bs.length)
}
