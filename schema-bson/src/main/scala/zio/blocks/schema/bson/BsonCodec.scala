package zio.blocks.schema.bson

import org.bson.{BsonBinaryReader, BsonBinaryWriter, BsonReader, BsonWriter, ByteBufNIO}
import org.bson.io.{BasicOutputBuffer, ByteBufferBsonInput}
import zio.blocks.schema.{DynamicOptic, SchemaError}
import zio.blocks.schema.SchemaError.ExpectationMismatch
import zio.blocks.schema.binding.RegisterOffset
import zio.blocks.schema.codec.BinaryCodec
import java.nio.ByteBuffer
import scala.collection.immutable.ArraySeq
import scala.util.control.NonFatal

abstract class BsonCodec[A](val valueType: Int = BsonCodec.objectType) extends BinaryCodec[A] {

  val valueOffset: RegisterOffset.RegisterOffset = valueType match {
    case BsonCodec.objectType   => RegisterOffset(objects = 1)
    case BsonCodec.booleanType  => RegisterOffset(booleans = 1)
    case BsonCodec.byteType     => RegisterOffset(bytes = 1)
    case BsonCodec.charType     => RegisterOffset(chars = 1)
    case BsonCodec.shortType    => RegisterOffset(shorts = 1)
    case BsonCodec.floatType    => RegisterOffset(floats = 1)
    case BsonCodec.intType      => RegisterOffset(ints = 1)
    case BsonCodec.doubleType   => RegisterOffset(doubles = 1)
    case BsonCodec.longType     => RegisterOffset(longs = 1)
    case BsonCodec.stringType   => RegisterOffset(objects = 1)
    case BsonCodec.dateTimeType => RegisterOffset(longs = 1)
    case BsonCodec.decimalType  => RegisterOffset(objects = 1)
    case BsonCodec.binaryType   => RegisterOffset(objects = 1)
    case _                      => RegisterOffset.Zero
  }

  def decodeError(expectation: String): Nothing = throw new BsonCodecError(Nil, expectation)

  def decodeError(span: DynamicOptic.Node, error: Throwable): Nothing = error match {
    case e: BsonCodecError =>
      e.spans = new ::(span, e.spans)
      throw e
    case _ =>
      throw new BsonCodecError(new ::(span, Nil), getMessage(error))
  }

  def decodeError(span1: DynamicOptic.Node, span2: DynamicOptic.Node, error: Throwable): Nothing = error match {
    case e: BsonCodecError =>
      e.spans = new ::(span1, new ::(span2, e.spans))
      throw e
    case _ =>
      throw new BsonCodecError(new ::(span1, new ::(span2, Nil)), getMessage(error))
  }

  def decodeUnsafe(decoder: BsonReader): A

  def encode(value: A, encoder: BsonWriter): Unit

  override def decode(input: ByteBuffer): Either[SchemaError, A] = {
    val reader = new BsonBinaryReader(new ByteBufferBsonInput(new ByteBufNIO(input)))
    if (valueType != BsonCodec.objectType) {
      try {
        reader.readStartDocument()
        val name = reader.readName()
        if (name != "v") decodeError(s"Expected field 'v' for primitive wrapper, but found '$name'")
        val result = decode(reader)
        reader.readEndDocument()
        result
      } catch {
        case e: BsonCodecError => new Left(toError(e))
        case NonFatal(e)       => new Left(toError(e))
      }
    } else {
      decode(reader)
    }
  }

  override def encode(value: A, output: ByteBuffer): Unit = {
    val buffer = new BasicOutputBuffer()
    val writer = new BsonBinaryWriter(buffer)
    if (valueType != BsonCodec.objectType) {
      writer.writeStartDocument()
      writer.writeName("v")
      encode(value, writer)
      writer.writeEndDocument()
    } else {
      encode(value, writer)
    }
    writer.flush()
    val bytes = buffer.getInternalBuffer
    val size  = buffer.getPosition
    output.put(bytes, 0, size)
  }

  def decode(reader: BsonReader): Either[SchemaError, A] =
    try new Right(decodeUnsafe(reader))
    catch {
      case error if NonFatal(error) => new Left(toError(error))
    }

  private[this] def toError(error: Throwable): SchemaError = new SchemaError(
    new ::(
      error match {
        case e: BsonCodecError =>
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

object BsonCodec {
  val objectType   = 0
  val booleanType  = 1
  val byteType     = 2
  val charType     = 3
  val shortType    = 4
  val floatType    = 5
  val intType      = 6
  val doubleType   = 7
  val longType     = 8
  val unitType     = 9
  val stringType   = 10
  val dateTimeType = 11
  val decimalType  = 12
  val binaryType   = 13

  val maxCollectionSize: Int = Integer.MAX_VALUE - 8
}

private class BsonCodecError(var spans: List[DynamicOptic.Node], message: String)
    extends Throwable(message, null, false, false) {
  override def getMessage: String = message
}
