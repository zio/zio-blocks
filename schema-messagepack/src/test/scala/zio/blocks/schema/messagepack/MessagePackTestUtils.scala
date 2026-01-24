package zio.blocks.schema.messagepack

import zio.blocks.schema.{Schema, SchemaError}
import zio.test.Assertion._
import zio.test._

import java.nio.ByteBuffer
import java.util
import java.util.concurrent.ConcurrentHashMap
import scala.collection.immutable.ArraySeq

object MessagePackTestUtils {
  private[this] val codecs = new ConcurrentHashMap[Schema[?], MessagePackBinaryCodec[?]]()

  private[this] def codec[A](schema: Schema[A]): MessagePackBinaryCodec[A] =
    codecs.computeIfAbsent(schema, _.derive(MessagePackFormat.deriver)).asInstanceOf[MessagePackBinaryCodec[A]]

  def roundTrip[A](value: A, expectedLength: Int)(implicit schema: Schema[A]): TestResult =
    roundTrip(value, expectedLength, codec(schema))

  def roundTrip[A](value: A, expectedLength: Int, codec: MessagePackBinaryCodec[A]): TestResult = {
    val heapByteBuffer = ByteBuffer.allocate(maxBufSize)
    codec.encode(value, heapByteBuffer)
    val encodedBySchema1 = util.Arrays.copyOf(heapByteBuffer.array, heapByteBuffer.position)
    
    val directByteBuffer = ByteBuffer.allocate(maxBufSize)
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
    assert(codec.decode(toHeapByteBuffer(encodedBySchema1)))(isRight(equalTo(value))) // &&
    // assert(codec.decode(toDirectByteBuffer(encodedBySchema1)))(isRight(equalTo(value)))
  }

  def decodeError[A](bytes: Array[Byte], error: String)(implicit schema: Schema[A]): TestResult =
    decodeError(bytes, codec(schema), error)

  def decodeError[A](bytes: Array[Byte], codec: MessagePackBinaryCodec[A], error: String): TestResult =
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

  private[this] val maxBufSize = 2048
}
