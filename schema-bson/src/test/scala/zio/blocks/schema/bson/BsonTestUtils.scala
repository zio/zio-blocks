package zio.blocks.schema.bson

import zio.blocks.schema.{Schema, SchemaError}
import zio.test.Assertion._
import zio.test._
import java.nio.ByteBuffer
import java.util
import java.util.concurrent.ConcurrentHashMap
import scala.collection.immutable.ArraySeq

object BsonTestUtils {
  private[this] val codecs = new ConcurrentHashMap[Schema[?], BsonCodec[?]]()

  private[this] def codec[A](schema: Schema[A]): BsonCodec[A] =
    codecs.computeIfAbsent(schema, _.derive(BsonFormat.deriver)).asInstanceOf[BsonCodec[A]]

  def roundTrip[A](value: A, expectedLength: Int)(implicit schema: Schema[A]): TestResult =
    roundTrip(value, expectedLength, codec(schema))

  def roundTrip[A](value: A, expectedLength: Int, codec: BsonCodec[A]): TestResult = {
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
    // Codec doesn't directly support generic OutputStream, but we can verify ByteBuffers
    // BsonCodec.encode(value, output) is not in our interface, only ByteBuffer.
    // However, we implemented BsonCodec.encode(value, output: ByteBuffer).
    // So we can stick to ByteBuffer checks.

    // Use ByteBuffer for encoding (there's no encode(value): Array[Byte] in BsonCodec)
    val buf4 = ByteBuffer.allocate(maxBufSize)
    codec.encode(value, buf4)
    val encodedBySchema4 = java.util.Arrays.copyOf(buf4.array, buf4.position)

    assert(encodedBySchema1.length)(equalTo(expectedLength)) &&
    assert(ArraySeq.unsafeWrapArray(encodedBySchema1))(equalTo(ArraySeq.unsafeWrapArray(encodedBySchema2))) &&
    assert(ArraySeq.unsafeWrapArray(encodedBySchema1))(equalTo(ArraySeq.unsafeWrapArray(encodedBySchema4))) &&
    // decode(Array[Byte]) doesn't exist, use ByteBuffer
    assert(codec.decode(toHeapByteBuffer(encodedBySchema1)))(isRight(equalTo(value))) &&
    assert(codec.decode(toDirectByteBuffer(encodedBySchema1)))(isRight(equalTo(value)))
  }

  def decodeError[A](bytes: Array[Byte], error: String)(implicit schema: Schema[A]): TestResult =
    decodeError(bytes, codec(schema), error)

  def decodeError[A](bytes: Array[Byte], codec: BsonCodec[A], error: String): TestResult =
    assert(codec.decode(toHeapByteBuffer(bytes)))(isLeft(hasError(error))) &&
      assert(codec.decode(toDirectByteBuffer(bytes)))(isLeft(hasError(error)))

  private[this] def hasError(message: String) =
    hasField[SchemaError, String]("getMessage", _.getMessage, containsString(message))

  private[this] def toHeapByteBuffer(bs: Array[Byte]): ByteBuffer = ByteBuffer.wrap(bs)

  private[this] def toDirectByteBuffer(bs: Array[Byte]): ByteBuffer =
    ByteBuffer.allocateDirect(maxBufSize).put(bs).position(0).limit(bs.length)

  private[this] val maxBufSize = 4096 // BSON docs can be larger
}
