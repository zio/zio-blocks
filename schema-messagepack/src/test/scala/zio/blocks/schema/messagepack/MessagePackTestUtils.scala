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

  def roundTrip[A](value: A)(implicit schema: Schema[A]): TestResult =
    roundTrip(value, codec(schema))

  def roundTrip[A](value: A, codec: MessagePackBinaryCodec[A]): TestResult = {
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
    assert(ArraySeq.unsafeWrapArray(encodedBySchema1))(equalTo(ArraySeq.unsafeWrapArray(encodedBySchema2))) &&
    assert(ArraySeq.unsafeWrapArray(encodedBySchema1))(equalTo(ArraySeq.unsafeWrapArray(encodedBySchema3))) &&
    assert(ArraySeq.unsafeWrapArray(encodedBySchema1))(equalTo(ArraySeq.unsafeWrapArray(encodedBySchema4))) &&
    assert(codec.decode(encodedBySchema1))(isRight(customEquals(value))) &&
    assert(codec.decode(toInputStream(encodedBySchema1)))(isRight(customEquals(value))) &&
    assert(codec.decode(toHeapByteBuffer(encodedBySchema1)))(isRight(customEquals(value))) &&
    assert(codec.decode(toDirectByteBuffer(encodedBySchema1)))(isRight(customEquals(value)))
  }

  private def customEquals[A](expected: A): Assertion[A] = Assertion.assertion("customEquals") { actual =>
    (expected, actual) match {
      case (f1: Float, f2: Float)                   => java.lang.Float.compare(f1, f2) == 0
      case (d1: Double, d2: Double)                 => java.lang.Double.compare(d1, d2) == 0
      case (a1: Array[Unit], a2: Array[Unit])       => java.util.Arrays.equals(a1.map(_ => 1), a2.map(_ => 1))
      case (a1: Array[Boolean], a2: Array[Boolean]) => java.util.Arrays.equals(a1, a2)
      case (a1: Array[Byte], a2: Array[Byte])       => java.util.Arrays.equals(a1, a2)
      case (a1: Array[Short], a2: Array[Short])     => java.util.Arrays.equals(a1, a2)
      case (a1: Array[Char], a2: Array[Char])       => java.util.Arrays.equals(a1, a2)
      case (a1: Array[Int], a2: Array[Int])         => java.util.Arrays.equals(a1, a2)
      case (a1: Array[Long], a2: Array[Long])       => java.util.Arrays.equals(a1, a2)
      case (a1: Array[Float], a2: Array[Float])     => java.util.Arrays.equals(a1, a2)
      case (a1: Array[Double], a2: Array[Double])   => java.util.Arrays.equals(a1, a2)
      case (a1: Array[AnyRef], a2: Array[AnyRef])   => java.util.Arrays.deepEquals(a1, a2)
      case _                                        => expected == actual
    }
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

  private[this] val maxBufSize = 65536
}
