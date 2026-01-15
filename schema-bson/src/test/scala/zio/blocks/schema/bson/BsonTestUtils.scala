package zio.blocks.schema.bson

import zio.blocks.schema.{Schema, SchemaError}
import zio.test.Assertion._
import zio.test._
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap
import scala.collection.immutable.ArraySeq

object BsonTestUtils {
  private[this] val codecs = new ConcurrentHashMap[Schema[?], BsonBinaryCodec[?]]()

  private[this] def codec[A](schema: Schema[A]): BsonBinaryCodec[A] =
    codecs.computeIfAbsent(schema, _.derive(BsonFormat.deriver)).asInstanceOf[BsonBinaryCodec[A]]

  def roundTrip[A](value: A)(implicit schema: Schema[A]): TestResult =
    roundTrip(value, codec(schema))

  def roundTrip[A](value: A, codec: BsonBinaryCodec[A]): TestResult = {
    val heapByteBuffer = ByteBuffer.allocate(maxBufSize)
    codec.encode(value, heapByteBuffer)
    val encodedBySchema1 = java.util.Arrays.copyOf(heapByteBuffer.array, heapByteBuffer.position)
    val directByteBuffer = ByteBuffer.allocateDirect(maxBufSize)
    codec.encode(value, directByteBuffer)
    val encodedBySchema2 = {
      val dup = directByteBuffer.duplicate()
      val out = new Array[Byte](dup.position)
      dup.position(0)
      dup.get(out)
      out
    }
    val output = new java.io.ByteArrayOutputStream(maxBufSize)
    codec.encode(value, output)
    output.close()
    val encodedBySchema3 = output.toByteArray
    val encodedBySchema4 = codec.encode(value)
    assertTrue(encodedBySchema1.length > 0) &&
    assert(ArraySeq.unsafeWrapArray(encodedBySchema1))(equalTo(ArraySeq.unsafeWrapArray(encodedBySchema2))) &&
    assert(ArraySeq.unsafeWrapArray(encodedBySchema1))(equalTo(ArraySeq.unsafeWrapArray(encodedBySchema3))) &&
    assert(ArraySeq.unsafeWrapArray(encodedBySchema1))(equalTo(ArraySeq.unsafeWrapArray(encodedBySchema4))) &&
    assert(codec.decode(encodedBySchema1))(isRight(equalTo(value))) &&
    assert(codec.decode(toInputStream(encodedBySchema1)))(isRight(equalTo(value))) &&
    assert(codec.decode(toHeapByteBuffer(encodedBySchema1)))(isRight(equalTo(value)))
  }

  def decodeError[A](bytes: Array[Byte], error: String)(implicit schema: Schema[A]): TestResult =
    decodeError(bytes, codec(schema), error)

  def decodeError[A](bytes: Array[Byte], codec: BsonBinaryCodec[A], error: String): TestResult =
    assert(codec.decode(bytes))(isLeft(hasError(error)))

  private[this] def hasError(message: String) =
    hasField[SchemaError, String]("getMessage", _.getMessage, containsString(message))

  private[this] def toInputStream(bs: Array[Byte]): java.io.InputStream = new java.io.ByteArrayInputStream(bs)

  private[this] def toHeapByteBuffer(bs: Array[Byte]): ByteBuffer = ByteBuffer.wrap(bs)

  private[this] val maxBufSize = 65536
}
