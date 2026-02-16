package zio.blocks.schema.json

import zio.blocks.schema.Schema
import zio.test.Assertion._
import zio.test._
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets.UTF_8
import java.util
import java.util.Random
import java.util.concurrent.ConcurrentHashMap
import scala.collection.immutable.ArraySeq
import scala.util.Try

object JsonTestUtils {
  def roundTrip[A](value: A, expectedJson: String)(implicit schema: Schema[A]): TestResult =
    roundTrip(value, expectedJson, getOrDeriveCodec(schema))

  def roundTrip[A](value: A, expectedJson: String, readerConfig: ReaderConfig, writerConfig: WriterConfig)(implicit
    schema: Schema[A]
  ): TestResult = roundTrip(value, expectedJson, getOrDeriveCodec(schema), readerConfig, writerConfig)

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
    val encodedBySchema5 = codec.encodeToString(value, writerConfig).getBytes(UTF_8)
    assert(new String(encodedBySchema1, UTF_8))(equalTo(expectedJson)) &&
    assert(ArraySeq.unsafeWrapArray(encodedBySchema1))(equalTo(ArraySeq.unsafeWrapArray(encodedBySchema2))) &&
    assert(ArraySeq.unsafeWrapArray(encodedBySchema1))(equalTo(ArraySeq.unsafeWrapArray(encodedBySchema3))) &&
    assert(ArraySeq.unsafeWrapArray(encodedBySchema1))(equalTo(ArraySeq.unsafeWrapArray(encodedBySchema4))) &&
    assert(ArraySeq.unsafeWrapArray(encodedBySchema1))(equalTo(ArraySeq.unsafeWrapArray(encodedBySchema5))) &&
    assert(codec.decode(encodedBySchema1, readerConfig))(isRight(equalTo(value))) &&
    assert(codec.decode(toInputStream(encodedBySchema1), readerConfig))(isRight(equalTo(value))) &&
    assert(codec.decode(toHeapByteBuffer(encodedBySchema1), readerConfig))(isRight(equalTo(value))) &&
    assert(codec.decode(toDirectByteBuffer(encodedBySchema1), readerConfig))(isRight(equalTo(value))) &&
    assert(codec.decode(new String(encodedBySchema1, UTF_8), readerConfig))(isRight(equalTo(value)))
  }

  def decode[A](json: String, expectedValue: A)(implicit schema: Schema[A]): TestResult =
    decode(json, expectedValue, getOrDeriveCodec(schema))

  def decode[A](json: String, expectedValue: A, readerConfig: ReaderConfig)(implicit schema: Schema[A]): TestResult =
    decode(json, expectedValue, getOrDeriveCodec(schema), readerConfig)

  def decode[A](
    json: String,
    expectedValue: A,
    codec: JsonBinaryCodec[A],
    readerConfig: ReaderConfig = readerConfig
  ): TestResult = {
    val jsonBytes = json.getBytes(UTF_8)
    assert(codec.decode(jsonBytes, readerConfig))(isRight(equalTo(expectedValue))) &&
    assert(codec.decode(toInputStream(jsonBytes), readerConfig))(isRight(equalTo(expectedValue))) &&
    assert(codec.decode(toHeapByteBuffer(jsonBytes), readerConfig))(isRight(equalTo(expectedValue))) &&
    assert(codec.decode(toDirectByteBuffer(jsonBytes), readerConfig))(isRight(equalTo(expectedValue))) &&
    assert(codec.decode(json, readerConfig))(isRight(equalTo(expectedValue)))
  }

  def decodeError[A](invalidJson: String, error: String)(implicit schema: Schema[A]): TestResult =
    decodeError(invalidJson.getBytes(UTF_8), error)

  def decodeError[A](invalidJson: Array[Byte], error: String)(implicit schema: Schema[A]): TestResult =
    decodeError(invalidJson, error, getOrDeriveCodec(schema))

  def decodeError[A](invalidJson: String, error: String, codec: JsonBinaryCodec[A]): TestResult =
    decodeError(invalidJson.getBytes(UTF_8), error, codec)

  def decodeError[A](
    invalidJson: String,
    error: String,
    codec: JsonBinaryCodec[A],
    readerConfig: ReaderConfig
  ): TestResult =
    decodeError(invalidJson.getBytes(UTF_8), error, codec, readerConfig)

  def decodeError[A](invalidJson: Array[Byte], error: String, codec: JsonBinaryCodec[A]): TestResult =
    decodeError(invalidJson, error, codec, ReaderConfig)

  def decodeError[A](
    invalidJson: Array[Byte],
    error: String,
    codec: JsonBinaryCodec[A],
    readerConfig: ReaderConfig
  ): TestResult =
    assert(codec.decode(invalidJson, readerConfig))(isLeft(hasError(error))) &&
      assert(codec.decode(toInputStream(invalidJson), readerConfig))(isLeft(hasError(error))) &&
      assert(codec.decode(toHeapByteBuffer(invalidJson), readerConfig))(isLeft(hasError(error))) &&
      assert(codec.decode(toDirectByteBuffer(invalidJson), readerConfig))(isLeft(hasError(error))) &&
      {
        if (error.startsWith("malformed byte(s)") || error.startsWith("illegal surrogate")) assertTrue(true)
        else assert(codec.decode(new String(invalidJson, UTF_8), readerConfig))(isLeft(hasError(error)))
      }

  def encode[A](value: A, expectedJson: String)(implicit schema: Schema[A]): TestResult =
    encode(value, expectedJson, getOrDeriveCodec(schema))

  def encode[A](value: A, expectedJson: String, writerConfig: WriterConfig)(implicit schema: Schema[A]): TestResult =
    encode(value, expectedJson, getOrDeriveCodec(schema), writerConfig)

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
    val encodedBySchema5 = codec.encodeToString(value, writerConfig).getBytes(UTF_8)
    assert(new String(encodedBySchema1, UTF_8))(equalTo(expectedJson)) &&
    assert(ArraySeq.unsafeWrapArray(encodedBySchema1))(equalTo(ArraySeq.unsafeWrapArray(encodedBySchema2))) &&
    assert(ArraySeq.unsafeWrapArray(encodedBySchema1))(equalTo(ArraySeq.unsafeWrapArray(encodedBySchema3))) &&
    assert(ArraySeq.unsafeWrapArray(encodedBySchema1))(equalTo(ArraySeq.unsafeWrapArray(encodedBySchema4))) &&
    assert(ArraySeq.unsafeWrapArray(encodedBySchema1))(equalTo(ArraySeq.unsafeWrapArray(encodedBySchema5)))
  }

  def encodeError[A](value: A, error: String)(implicit schema: Schema[A]): TestResult = {
    val codec = getOrDeriveCodec(schema)
    assert(Try(codec.encode(value)).toEither)(isLeft(hasError(error))) &&
    assert(Try(codec.encode(value, ByteBuffer.allocate(maxBufSize))).toEither)(isLeft(hasError(error))) &&
    assert(Try(codec.encode(value, ByteBuffer.allocateDirect(maxBufSize))).toEither)(isLeft(hasError(error))) &&
    assert(Try(codec.encode(value, new java.io.ByteArrayOutputStream(maxBufSize))).toEither)(isLeft(hasError(error))) &&
    assert(Try(codec.encodeToString(value)).toEither)(isLeft(hasError(error)))
  }

  def encodeError[A](value: A, error: String, writerConfig: WriterConfig)(implicit schema: Schema[A]): TestResult = {
    val codec = getOrDeriveCodec(schema)
    assert(Try(codec.encode(value, writerConfig)).toEither)(isLeft(hasError(error))) &&
    assert(Try(codec.encode(value, ByteBuffer.allocate(maxBufSize), writerConfig)).toEither)(isLeft(hasError(error))) &&
    assert(Try(codec.encode(value, ByteBuffer.allocateDirect(maxBufSize), writerConfig)).toEither)(
      isLeft(hasError(error))
    ) &&
    assert(Try(codec.encode(value, new java.io.ByteArrayOutputStream(maxBufSize), writerConfig)).toEither)(
      isLeft(hasError(error))
    ) &&
    assert(Try(codec.encodeToString(value, writerConfig)).toEither)(isLeft(hasError(error)))
  }

  def hasError(message: String): Assertion[Throwable] =
    hasField[Throwable, String]("getMessage", _.getMessage, equalTo(message))

  private[this] def readerConfig =
    ReaderConfig
      .withPreferredBufSize(random.nextInt(11) + 12)
      .withPreferredCharBufSize(random.nextInt(11) + 12)
      .withMaxBufSize(maxBufSize)
      .withMaxCharBufSize(maxBufSize)
      .withCheckForEndOfInput(true)

  private[this] def writerConfig = WriterConfig.withPreferredBufSize(random.nextInt(11) + 1)

  private[this] def getOrDeriveCodec[A](schema: Schema[A]): JsonBinaryCodec[A] =
    codecs
      .computeIfAbsent(schema, (s: Schema[_]) => s.deriving(JsonBinaryCodecDeriver).derive)
      .asInstanceOf[JsonBinaryCodec[A]]

  private[this] def toInputStream(bs: Array[Byte]): java.io.InputStream = new java.io.ByteArrayInputStream(bs)

  private[this] def toHeapByteBuffer(bs: Array[Byte]): ByteBuffer = ByteBuffer.wrap(bs)

  private[this] def toDirectByteBuffer(bs: Array[Byte]): ByteBuffer =
    ByteBuffer.allocateDirect(maxBufSize).put(bs).position(0).limit(bs.length)

  private[this] val codecs     = new ConcurrentHashMap[Schema[?], JsonBinaryCodec[?]]()
  private[this] val random     = new Random()
  private[this] val maxBufSize = 16384
}
