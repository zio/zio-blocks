package zio.blocks.schema.toon

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

object ToonTestUtils {
  def roundTrip[A](value: A, expectedToon: String)(implicit schema: Schema[A]): TestResult =
    roundTrip(value, expectedToon, getOrDeriveCodec(schema))

  def roundTrip[A](value: A, expectedToon: String, readerConfig: ReaderConfig, writerConfig: WriterConfig)(implicit
    schema: Schema[A]
  ): TestResult = roundTrip(value, expectedToon, getOrDeriveCodec(schema), readerConfig, writerConfig)

  def roundTrip[A](
    value: A,
    expectedToon: String,
    codec: ToonBinaryCodec[A],
    readerConfig: ReaderConfig = defaultReaderConfig,
    writerConfig: WriterConfig = defaultWriterConfig
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
    assert(new String(encodedBySchema1, UTF_8))(equalTo(expectedToon)) &&
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

  def decode[A](toon: String, expectedValue: A)(implicit schema: Schema[A]): TestResult =
    decode(toon, expectedValue, getOrDeriveCodec(schema))

  def decode[A](toon: String, expectedValue: A, readerConfig: ReaderConfig)(implicit schema: Schema[A]): TestResult =
    decode(toon, expectedValue, getOrDeriveCodec(schema), readerConfig)

  def decode[A](
    toon: String,
    expectedValue: A,
    codec: ToonBinaryCodec[A],
    readerConfig: ReaderConfig = defaultReaderConfig
  ): TestResult = {
    val toonBytes = toon.getBytes(UTF_8)
    assert(codec.decode(toonBytes, readerConfig))(isRight(equalTo(expectedValue))) &&
    assert(codec.decode(toInputStream(toonBytes), readerConfig))(isRight(equalTo(expectedValue))) &&
    assert(codec.decode(toHeapByteBuffer(toonBytes), readerConfig))(isRight(equalTo(expectedValue))) &&
    assert(codec.decode(toDirectByteBuffer(toonBytes), readerConfig))(isRight(equalTo(expectedValue))) &&
    assert(codec.decode(toon, readerConfig))(isRight(equalTo(expectedValue)))
  }

  def decodeError[A](invalidToon: String, error: String)(implicit schema: Schema[A]): TestResult =
    decodeError(invalidToon.getBytes(UTF_8), error)

  def decodeError[A](invalidToon: Array[Byte], error: String)(implicit schema: Schema[A]): TestResult =
    decodeError(invalidToon, error, getOrDeriveCodec(schema))

  def decodeError[A](invalidToon: String, error: String, codec: ToonBinaryCodec[A]): TestResult =
    decodeError(invalidToon.getBytes(UTF_8), error, codec)

  def decodeError[A](
    invalidToon: String,
    error: String,
    codec: ToonBinaryCodec[A],
    readerConfig: ReaderConfig
  ): TestResult =
    decodeError(invalidToon.getBytes(UTF_8), error, codec, readerConfig)

  def decodeError[A](invalidToon: Array[Byte], error: String, codec: ToonBinaryCodec[A]): TestResult =
    decodeError(invalidToon, error, codec, ReaderConfig)

  def decodeError[A](
    invalidToon: Array[Byte],
    error: String,
    codec: ToonBinaryCodec[A],
    readerConfig: ReaderConfig
  ): TestResult =
    assert(codec.decode(invalidToon, readerConfig))(isLeft(hasError(error))) &&
      assert(codec.decode(toInputStream(invalidToon), readerConfig))(isLeft(hasError(error))) &&
      assert(codec.decode(toHeapByteBuffer(invalidToon), readerConfig))(isLeft(hasError(error))) &&
      assert(codec.decode(toDirectByteBuffer(invalidToon), readerConfig))(isLeft(hasError(error))) &&
      {
        if (error.startsWith("malformed byte(s)") || error.startsWith("illegal surrogate")) assertTrue(true)
        else assert(codec.decode(new String(invalidToon, UTF_8), readerConfig))(isLeft(hasError(error)))
      }

  def encode[A](value: A, expectedToon: String)(implicit schema: Schema[A]): TestResult =
    encode(value, expectedToon, getOrDeriveCodec(schema))

  def encode[A](value: A, expectedToon: String, writerConfig: WriterConfig)(implicit schema: Schema[A]): TestResult =
    encode(value, expectedToon, getOrDeriveCodec(schema), writerConfig)

  def encode[A](
    value: A,
    expectedToon: String,
    codec: ToonBinaryCodec[A],
    writerConfig: WriterConfig = defaultWriterConfig
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
    assert(new String(encodedBySchema1, UTF_8))(equalTo(expectedToon)) &&
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

  def hasError(message: String): Assertion[Throwable] =
    hasField[Throwable, String]("getMessage", _.getMessage, containsString(message))

  private[this] def defaultReaderConfig =
    ReaderConfig
      .withPreferredBufSize(random.nextInt(11) + 12)
      .withPreferredCharBufSize(random.nextInt(11) + 12)
      .withMaxBufSize(maxBufSize)
      .withMaxCharBufSize(maxBufSize)
      .withCheckForEndOfInput(true)

  private[this] def defaultWriterConfig = WriterConfig.withPreferredBufSize(random.nextInt(11) + 1)

  private[this] def getOrDeriveCodec[A](schema: Schema[A]): ToonBinaryCodec[A] =
    codecs.computeIfAbsent(schema, _.derive(ToonBinaryCodecDeriver)).asInstanceOf[ToonBinaryCodec[A]]

  private[this] def toInputStream(bs: Array[Byte]): java.io.InputStream = new java.io.ByteArrayInputStream(bs)

  private[this] def toHeapByteBuffer(bs: Array[Byte]): ByteBuffer = ByteBuffer.wrap(bs)

  private[this] def toDirectByteBuffer(bs: Array[Byte]): ByteBuffer =
    ByteBuffer.allocateDirect(maxBufSize).put(bs).position(0).limit(bs.length)

  private[this] val codecs     = new ConcurrentHashMap[Schema[?], ToonBinaryCodec[?]]()
  private[this] val random     = new Random()
  private[this] val maxBufSize = 1024
}
