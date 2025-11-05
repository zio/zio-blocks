package zio.blocks.avro

import org.apache.avro.io.{BinaryDecoder, BinaryEncoder, DecoderFactory, DirectBinaryEncoder}
import org.apache.avro.{Schema => AvroSchema}
import zio.blocks.schema.SchemaError.ExpectationMismatch
import zio.blocks.schema.{DynamicOptic, SchemaError}
import zio.blocks.schema.binding.RegisterOffset
import zio.blocks.schema.codec.BinaryCodec
import java.io.OutputStream
import java.nio.ByteBuffer
import scala.collection.immutable.ArraySeq
import scala.util.control.NonFatal

abstract class AvroBinaryCodec[A](val valueType: Int = AvroBinaryCodec.objectType) extends BinaryCodec[A] {
  val valueOffset: RegisterOffset.RegisterOffset = valueType match {
    case AvroBinaryCodec.objectType  => RegisterOffset(objects = 1)
    case AvroBinaryCodec.booleanType => RegisterOffset(booleans = 1)
    case AvroBinaryCodec.byteType    => RegisterOffset(bytes = 1)
    case AvroBinaryCodec.charType    => RegisterOffset(chars = 1)
    case AvroBinaryCodec.shortType   => RegisterOffset(shorts = 1)
    case AvroBinaryCodec.floatType   => RegisterOffset(floats = 1)
    case AvroBinaryCodec.intType     => RegisterOffset(ints = 1)
    case AvroBinaryCodec.doubleType  => RegisterOffset(doubles = 1)
    case AvroBinaryCodec.longType    => RegisterOffset(longs = 1)
    case _                           => RegisterOffset.Zero
  }

  def avroSchema: AvroSchema

  def decodeError(expectation: String): Nothing = throw new AvroBinaryCodecError(Nil, expectation)

  def decodeError(span: DynamicOptic.Node, error: Throwable): Nothing = error match {
    case e: AvroBinaryCodecError =>
      e.spans = new ::(span, e.spans)
      throw e
    case _ =>
      throw new AvroBinaryCodecError(new ::(span, Nil), getMessage(error))
  }

  def decodeError(span1: DynamicOptic.Node, span2: DynamicOptic.Node, error: Throwable): Nothing = error match {
    case e: AvroBinaryCodecError =>
      e.spans = new ::(span1, new ::(span2, e.spans))
      throw e
    case _ =>
      throw new AvroBinaryCodecError(new ::(span1, new ::(span2, Nil)), getMessage(error))
  }

  def decodeUnsafe(decoder: BinaryDecoder): A

  def encode(value: A, encoder: BinaryEncoder): Unit

  override def decode(input: ByteBuffer): Either[SchemaError, A] = {
    var pos             = input.position
    val len             = input.limit - pos
    var bs: Array[Byte] = null
    if (input.hasArray) bs = input.array()
    else {
      pos = 0
      bs = new Array[Byte](len)
      input.get(bs)
    }
    decode(DecoderFactory.get().binaryDecoder(bs, pos, len, null))
  }

  override def encode(value: A, output: ByteBuffer): Unit = encode(
    value,
    new OutputStream {
      override def write(b: Int): Unit = output.put(b.toByte)

      override def write(bs: Array[Byte], off: Int, len: Int): Unit = output.put(bs, off, len)
    }
  )

  def decode(input: Array[Byte]): Either[SchemaError, A] =
    decode(DecoderFactory.get().binaryDecoder(input, 0, input.length, null))

  def encode(value: A): Array[Byte] = {
    val output = new ByteArrayOutputStream
    encode(value, output)
    output.toByteArray
  }

  def decode(input: java.io.InputStream): Either[SchemaError, A] =
    decode(DecoderFactory.get().directBinaryDecoder(input, null))

  def encode(value: A, output: java.io.OutputStream): Unit = encode(value, new DirectBinaryEncoder(output) {})

  private[this] def decode(decoder: BinaryDecoder): Either[SchemaError, A] =
    try new Right(decodeUnsafe(decoder))
    catch {
      case error if NonFatal(error) => new Left(toError(error))
    }

  private[this] def toError(error: Throwable): SchemaError = new SchemaError(
    new ::(
      error match {
        case e: AvroBinaryCodecError =>
          var list  = e.spans
          val array = new Array[DynamicOptic.Node](list.size)
          var idx   = 0
          while (list ne Nil) {
            array(idx) = list.head
            idx += 1
            list = list.tail
          }
          new ExpectationMismatch(new DynamicOptic(ArraySeq.unsafeWrapArray(array)), e.getMessage)
        case _ => new ExpectationMismatch(DynamicOptic.root, getMessage(error))
      },
      Nil
    )
  )

  private[this] def getMessage(error: Throwable): String = error match {
    case _: java.io.EOFException => "Unexpected end of input"
    case e                       => e.getMessage
  }
}

object AvroBinaryCodec {
  val objectType  = 0
  val booleanType = 1
  val byteType    = 2
  val charType    = 3
  val shortType   = 4
  val floatType   = 5
  val intType     = 6
  val doubleType  = 7
  val longType    = 8
  val unitType    = 9

  val maxCollectionSize: Int = Integer.MAX_VALUE - 8
}

private class AvroBinaryCodecError(var spans: List[DynamicOptic.Node], message: String)
    extends Throwable(message, null, false, false) {
  override def getMessage: String = message
}

/**
 * Custom implementation replacing `java.io.ByteArrayOutputStream`.
 *
 * This class is used for performance optimization and to avoid unnecessary
 * overhead that occur with the standard `ByteArrayOutputStream`. The buffer
 * growth strategy doubles the buffer size when more space is needed, or grows
 * to fit the required length if doubling is insufficient. This minimizes the
 * number of allocations and copies, especially for large or unpredictable
 * output sizes.
 */
private class ByteArrayOutputStream extends java.io.OutputStream {
  private[this] var buf   = new Array[Byte](64)
  private[this] var count = 0

  override def write(b: Int): Unit = {
    if (count >= buf.length) buf = java.util.Arrays.copyOf(buf, buf.length << 1)
    buf(count) = b.toByte
    count += 1
  }

  override def write(bs: Array[Byte], off: Int, len: Int): Unit = {
    val newLen = count + len
    if (newLen > buf.length) buf = java.util.Arrays.copyOf(buf, Math.max(buf.length << 1, newLen))
    System.arraycopy(bs, off, buf, count, len)
    count = newLen
  }

  def toByteArray: Array[Byte] = java.util.Arrays.copyOf(buf, count)
}
