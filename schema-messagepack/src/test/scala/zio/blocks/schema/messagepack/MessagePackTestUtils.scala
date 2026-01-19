package zio.blocks.schema.messagepack

import zio.blocks.schema.{Schema, SchemaError}
import zio.test.Assertion._
import zio.test._
import java.nio.ByteBuffer
import java.util
import java.util.concurrent.ConcurrentHashMap
import scala.collection.immutable.ArraySeq

/**
 * Test utilities for MessagePack format tests.
 *
 * Provides helper methods for:
 *   - Round-trip encoding/decoding tests
 *   - Error condition tests
 *   - Hex string conversion utilities
 */
object MessagePackTestUtils {
  private[this] val codecs = new ConcurrentHashMap[Schema[?], MessagePackBinaryCodec[?]]()

  private[this] def codec[A](schema: Schema[A]): MessagePackBinaryCodec[A] =
    codecs.computeIfAbsent(schema, _.derive(MessagePackFormat.deriver)).asInstanceOf[MessagePackBinaryCodec[A]]

  /**
   * Test round-trip encoding and decoding of a value.
   *
   * @param value
   *   The value to encode and decode
   * @param schema
   *   The schema for the value type
   * @return
   *   TestResult asserting the round-trip is successful
   */
  def roundTrip[A](value: A)(implicit schema: Schema[A]): TestResult =
    roundTrip(value, codec(schema))

  /**
   * Test round-trip encoding and decoding of a value with expected encoded
   * length.
   *
   * @param value
   *   The value to encode and decode
   * @param expectedLength
   *   Expected byte length of encoded value
   * @param schema
   *   The schema for the value type
   * @return
   *   TestResult asserting the round-trip is successful and length matches
   */
  def roundTrip[A](value: A, expectedLength: Int)(implicit schema: Schema[A]): TestResult =
    roundTrip(value, expectedLength, codec(schema))

  /**
   * Test round-trip encoding and decoding with a specific codec.
   */
  def roundTrip[A](value: A, codec: MessagePackBinaryCodec[A]): TestResult = {
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
    assert(ArraySeq.unsafeWrapArray(encodedBySchema1))(equalTo(ArraySeq.unsafeWrapArray(encodedBySchema2))) &&
    assert(ArraySeq.unsafeWrapArray(encodedBySchema1))(equalTo(ArraySeq.unsafeWrapArray(encodedBySchema3))) &&
    assert(ArraySeq.unsafeWrapArray(encodedBySchema1))(equalTo(ArraySeq.unsafeWrapArray(encodedBySchema4))) &&
    assert(codec.decode(encodedBySchema1))(isRight(equalTo(value))) &&
    assert(codec.decode(toInputStream(encodedBySchema1)))(isRight(equalTo(value))) &&
    assert(codec.decode(toHeapByteBuffer(encodedBySchema1)))(isRight(equalTo(value))) &&
    assert(codec.decode(toOffsetHeapByteBuffer(encodedBySchema1)))(isRight(equalTo(value))) &&
    assert(codec.decode(toDirectByteBuffer(encodedBySchema1)))(isRight(equalTo(value)))
  }

  /**
   * Test round-trip encoding and decoding with a specific codec and expected
   * length.
   */
  def roundTrip[A](value: A, expectedLength: Int, codec: MessagePackBinaryCodec[A]): TestResult = {
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
    assert(codec.decode(toOffsetHeapByteBuffer(encodedBySchema1)))(isRight(equalTo(value))) &&
    assert(codec.decode(toDirectByteBuffer(encodedBySchema1)))(isRight(equalTo(value)))
  }

  /**
   * Test that decoding specific bytes produces an error.
   *
   * @param bytes
   *   The bytes to attempt to decode
   * @param error
   *   Expected error message substring
   * @param schema
   *   The schema for the value type
   * @return
   *   TestResult asserting decoding fails with expected error
   */
  def decodeError[A](bytes: Array[Byte], error: String)(implicit schema: Schema[A]): TestResult =
    decodeError(bytes, codec(schema), error)

  /**
   * Test that decoding specific bytes produces an error with a specific codec.
   */
  def decodeError[A](bytes: Array[Byte], codec: MessagePackBinaryCodec[A], error: String): TestResult =
    assert(codec.decode(bytes))(isLeft(hasError(error))) &&
      assert(codec.decode(toInputStream(bytes)))(isLeft(hasError(error))) &&
      assert(codec.decode(toHeapByteBuffer(bytes)))(isLeft(hasError(error))) &&
      assert(codec.decode(toOffsetHeapByteBuffer(bytes)))(isLeft(hasError(error))) &&
      assert(codec.decode(toDirectByteBuffer(bytes)))(isLeft(hasError(error)))

  /**
   * Convert bytes to a hex string for debugging.
   */
  def toHex(bytes: Array[Byte]): String =
    bytes.map(b => f"${b & 0xff}%02x").mkString

  /**
   * Convert a hex string to bytes.
   */
  def fromHex(hex: String): Array[Byte] = {
    val cleanHex = hex.replaceAll("\\s", "")
    cleanHex.grouped(2).map(Integer.parseInt(_, 16).toByte).toArray
  }

  private[this] def hasError(message: String) =
    hasField[SchemaError, String]("getMessage", _.getMessage, containsString(message))

  private[this] def toInputStream(bs: Array[Byte]): java.io.InputStream = new java.io.ByteArrayInputStream(bs)

  private[this] def toHeapByteBuffer(bs: Array[Byte]): ByteBuffer = ByteBuffer.wrap(bs)

  private[this] def toOffsetHeapByteBuffer(bs: Array[Byte]): ByteBuffer = {
    val prefix = 7
    val buf    = new Array[Byte](bs.length + prefix)
    System.arraycopy(bs, 0, buf, prefix, bs.length)
    ByteBuffer.wrap(buf).position(prefix).limit(prefix + bs.length).slice()
  }

  private[this] def toDirectByteBuffer(bs: Array[Byte]): ByteBuffer =
    ByteBuffer.allocateDirect(maxBufSize).put(bs).position(0).limit(bs.length)

  private[this] val maxBufSize = 65536
}
