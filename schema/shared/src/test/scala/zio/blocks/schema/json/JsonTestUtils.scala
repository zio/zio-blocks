package zio.blocks.schema.json

import zio.blocks.schema.Schema
import zio.test.Assertion._
import zio.test._
import java.nio.ByteBuffer
import java.util
import java.util.Random
import java.util.concurrent.ConcurrentHashMap
import scala.collection.immutable.ArraySeq
import scala.util.Try

object JsonTestUtils {
  def roundTrip[A](value: A, expectedJson: String)(implicit schema: Schema[A]): TestResult =
    roundTrip(value, expectedJson, codec(schema))

  def roundTrip[A](value: A, expectedJson: String, readerConfig: ReaderConfig, writerConfig: WriterConfig)(implicit
    schema: Schema[A]
  ): TestResult = roundTrip(value, expectedJson, codec(schema), readerConfig, writerConfig)

  def roundTrip[A](
    value: A,
    expectedJson: String,
    codec: JsonBinaryCodec[A],
    readerConfig: ReaderConfig = readerConfig,
    writerConfig: WriterConfig = writerConfig
  ): TestResult = {
    val heapByteBuffer = ByteBuffer.allocate(maxBufSize)
    codec.encode(value, heapByteBuffer, writerConfig)
    val encodedBySchema1 = util.Arrays.copyOf(heapByteBuffer.array, heapByteBuffer.position)
    val directByteBuffer = ByteBuffer.allocateDirect(maxBufSize)
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
    val output = new java.io.ByteArrayOutputStream(maxBufSize)
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
    decode(json, expectedValue, codec(schema))

  def decode[A](json: String, expectedValue: A, readerConfig: ReaderConfig)(implicit schema: Schema[A]): TestResult =
    decode(json, expectedValue, codec(schema), readerConfig)

  def decode[A](
    json: String,
    expectedValue: A,
    codec: JsonBinaryCodec[A],
    readerConfig: ReaderConfig = readerConfig
  ): TestResult = {
    val jsonBytes = json.getBytes("UTF-8")
    assert(codec.decode(jsonBytes, readerConfig))(isRight(equalTo(expectedValue))) &&
    assert(codec.decode(toInputStream(jsonBytes), readerConfig))(isRight(equalTo(expectedValue))) &&
    assert(codec.decode(toHeapByteBuffer(jsonBytes), readerConfig))(isRight(equalTo(expectedValue))) &&
    assert(codec.decode(toDirectByteBuffer(jsonBytes), readerConfig))(isRight(equalTo(expectedValue)))
  }

  def decodeError[A](invalidJson: String, error: String)(implicit schema: Schema[A]): TestResult =
    decodeError(invalidJson.getBytes("UTF-8"), error)

  def decodeError[A](bytes: Array[Byte], error: String)(implicit schema: Schema[A]): TestResult = {
    val c = codec(schema)
    assert(c.decode(bytes))(isLeft(hasError(error))) &&
    assert(c.decode(toInputStream(bytes)))(isLeft(hasError(error))) &&
    assert(c.decode(toHeapByteBuffer(bytes)))(isLeft(hasError(error))) &&
    assert(c.decode(toDirectByteBuffer(bytes)))(isLeft(hasError(error)))
  }

  def encode[A](value: A, expectedJson: String)(implicit schema: Schema[A]): TestResult =
    encode(value, expectedJson, codec(schema))

  def encode[A](value: A, expectedJson: String, writerConfig: WriterConfig)(implicit schema: Schema[A]): TestResult =
    encode(value, expectedJson, codec(schema), writerConfig)

  def encode[A](
    value: A,
    expectedJson: String,
    codec: JsonBinaryCodec[A],
    writerConfig: WriterConfig = writerConfig
  ): TestResult = {
    val heapByteBuffer = ByteBuffer.allocate(maxBufSize)
    codec.encode(value, heapByteBuffer, writerConfig)
    val encodedBySchema1 = util.Arrays.copyOf(heapByteBuffer.array, heapByteBuffer.position)
    val directByteBuffer = ByteBuffer.allocateDirect(maxBufSize)
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
    val output = new java.io.ByteArrayOutputStream(maxBufSize)
    codec.encode(value, output, writerConfig)
    output.close()
    val encodedBySchema3 = output.toByteArray
    val encodedBySchema4 = codec.encode(value, writerConfig)
    assert(new String(encodedBySchema1, "UTF-8"))(equalTo(expectedJson)) &&
    assert(ArraySeq.unsafeWrapArray(encodedBySchema1))(equalTo(ArraySeq.unsafeWrapArray(encodedBySchema2))) &&
    assert(ArraySeq.unsafeWrapArray(encodedBySchema1))(equalTo(ArraySeq.unsafeWrapArray(encodedBySchema3))) &&
    assert(ArraySeq.unsafeWrapArray(encodedBySchema1))(equalTo(ArraySeq.unsafeWrapArray(encodedBySchema4)))
  }

  def encodeError[A](value: A, error: String)(implicit schema: Schema[A]): TestResult = {
    val c = codec(schema)
    assert(Try(c.encode(value)).toEither)(isLeft(hasError(error))) &&
    assert(Try(c.encode(value, ByteBuffer.allocate(maxBufSize))).toEither)(isLeft(hasError(error))) &&
    assert(Try(c.encode(value, ByteBuffer.allocateDirect(maxBufSize))).toEither)(isLeft(hasError(error))) &&
    assert(Try(c.encode(value, new java.io.ByteArrayOutputStream(maxBufSize))).toEither)(isLeft(hasError(error)))
  }

  def encodeError[A](value: A, error: String, writerConfig: WriterConfig)(implicit schema: Schema[A]): TestResult = {
    val c = codec(schema)
    assert(Try(c.encode(value, writerConfig)).toEither)(isLeft(hasError(error))) &&
    assert(Try(c.encode(value, ByteBuffer.allocate(maxBufSize), writerConfig)).toEither)(isLeft(hasError(error))) &&
    assert(Try(c.encode(value, ByteBuffer.allocateDirect(maxBufSize), writerConfig)).toEither)(
      isLeft(hasError(error))
    ) &&
    assert(Try(c.encode(value, new java.io.ByteArrayOutputStream(maxBufSize), writerConfig)).toEither)(
      isLeft(hasError(error))
    )
  }

  def hasError(message: String): Assertion[Throwable] =
    hasField[Throwable, String]("getMessage", _.getMessage, equalTo(message))

  private[this] def readerConfig =
    ReaderConfig
      .withPreferredBufSize(random.nextInt(11) + 13)
      .withPreferredCharBufSize(random.nextInt(11) + 13)
      .withMaxBufSize(maxBufSize)
      .withMaxCharBufSize(maxBufSize)
      .withCheckForEndOfInput(true)

  private[this] def writerConfig = WriterConfig.withPreferredBufSize(random.nextInt(11) + 3)

  private[this] def codec[A](schema: Schema[A]): JsonBinaryCodec[A] =
    codecs.computeIfAbsent(schema, _.derive(JsonFormat.deriver)).asInstanceOf[JsonBinaryCodec[A]]

  private[this] def toInputStream(bs: Array[Byte]): java.io.InputStream = new java.io.ByteArrayInputStream(bs)

  private[this] def toHeapByteBuffer(bs: Array[Byte]): ByteBuffer = ByteBuffer.wrap(bs)

  private[this] def toDirectByteBuffer(bs: Array[Byte]): ByteBuffer =
    ByteBuffer.allocateDirect(maxBufSize).put(bs).position(0).limit(bs.length)

  private[this] val codecs     = new ConcurrentHashMap[Schema[?], JsonBinaryCodec[?]]()
  private[this] val random     = new Random()
  private[this] val maxBufSize = 1024
}
