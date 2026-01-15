package zio.blocks.schema.bson

import org.bson.io.{BasicOutputBuffer, ByteBufferBsonInput}
import org.bson.{BsonBinaryReader, BsonBinaryWriter, ByteBufNIO}
import zio.blocks.schema.SchemaError.ExpectationMismatch
import zio.blocks.schema.{DynamicOptic, SchemaError}
import zio.blocks.schema.binding.RegisterOffset
import zio.blocks.schema.codec.BinaryCodec
import java.nio.ByteBuffer
import scala.collection.immutable.ArraySeq
import scala.util.control.NonFatal

abstract class BsonBinaryCodec[A](val valueType: Int = BsonBinaryCodec.objectType) extends BinaryCodec[A] {
  val valueOffset: RegisterOffset.RegisterOffset = valueType match {
    case BsonBinaryCodec.objectType  => RegisterOffset(objects = 1)
    case BsonBinaryCodec.booleanType => RegisterOffset(booleans = 1)
    case BsonBinaryCodec.byteType    => RegisterOffset(bytes = 1)
    case BsonBinaryCodec.charType    => RegisterOffset(chars = 1)
    case BsonBinaryCodec.shortType   => RegisterOffset(shorts = 1)
    case BsonBinaryCodec.floatType   => RegisterOffset(floats = 1)
    case BsonBinaryCodec.intType     => RegisterOffset(ints = 1)
    case BsonBinaryCodec.doubleType  => RegisterOffset(doubles = 1)
    case BsonBinaryCodec.longType    => RegisterOffset(longs = 1)
    case _                           => RegisterOffset.Zero
  }

  def decodeError(expectation: String): Nothing = throw new BsonBinaryCodecError(Nil, expectation)

  def decodeError(span: DynamicOptic.Node, error: Throwable): Nothing = error match {
    case e: BsonBinaryCodecError =>
      e.spans = new ::(span, e.spans)
      throw e
    case _ =>
      throw new BsonBinaryCodecError(new ::(span, Nil), getMessage(error))
  }

  def decodeError(span1: DynamicOptic.Node, span2: DynamicOptic.Node, error: Throwable): Nothing = error match {
    case e: BsonBinaryCodecError =>
      e.spans = new ::(span1, new ::(span2, e.spans))
      throw e
    case _ =>
      throw new BsonBinaryCodecError(new ::(span1, new ::(span2, Nil)), getMessage(error))
  }

  def decodeUnsafe(reader: BsonBinaryReader): A

  def encodeUnsafe(value: A, writer: BsonBinaryWriter): Unit

  override def decode(input: ByteBuffer): Either[SchemaError, A] = {
    val byteBuf   = new ByteBufNIO(input)
    val bsonInput = new ByteBufferBsonInput(byteBuf)
    val reader    = new BsonBinaryReader(bsonInput)
    try {
      reader.readStartDocument()
      reader.readName("v")
      val result = decodeUnsafe(reader)
      reader.readEndDocument()
      new Right(result)
    } catch {
      case error if NonFatal(error) => new Left(toError(error))
    } finally {
      reader.close()
    }
  }

  override def encode(value: A, output: ByteBuffer): Unit = {
    val buffer = new BasicOutputBuffer()
    val writer = new BsonBinaryWriter(buffer)
    try {
      writer.writeStartDocument()
      writer.writeName("v")
      encodeUnsafe(value, writer)
      writer.writeEndDocument()
      writer.flush()
      val bytes = buffer.toByteArray
      output.put(bytes)
    } finally {
      writer.close()
      buffer.close()
    }
  }

  def decode(input: Array[Byte]): Either[SchemaError, A] =
    decode(ByteBuffer.wrap(input))

  def encode(value: A): Array[Byte] = {
    val buffer = new BasicOutputBuffer()
    val writer = new BsonBinaryWriter(buffer)
    try {
      writer.writeStartDocument()
      writer.writeName("v")
      encodeUnsafe(value, writer)
      writer.writeEndDocument()
      writer.flush()
      buffer.toByteArray
    } finally {
      writer.close()
      buffer.close()
    }
  }

  def decode(input: java.io.InputStream): Either[SchemaError, A] = {
    val bytes = input.readAllBytes()
    decode(bytes)
  }

  def encode(value: A, output: java.io.OutputStream): Unit = {
    val bytes = encode(value)
    output.write(bytes)
  }

  private[this] def toError(error: Throwable): SchemaError = new SchemaError(
    new ::(
      error match {
        case e: BsonBinaryCodecError =>
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
    case _: java.io.EOFException                         => "Unexpected end of input"
    case _: org.bson.BsonSerializationException          => "BSON serialization error: " + error.getMessage
    case _: org.bson.BsonInvalidOperationException       => "Invalid BSON operation: " + error.getMessage
    case e if e.getMessage != null && e.getMessage != "" => e.getMessage
    case _                                               => "Unknown BSON error"
  }
}

object BsonBinaryCodec {
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

private[bson] class BsonBinaryCodecError(var spans: List[DynamicOptic.Node], message: String)
    extends Throwable(message, null, false, false) {
  override def getMessage: String = message
}

private[bson] class ByteArrayOutputStream extends java.io.OutputStream {
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
