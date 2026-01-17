package zio.blocks.schema.bson

import org.bson.{BsonReader, BsonWriter, BsonBinaryReader, BsonBinaryWriter}
import org.bson.io.{BasicOutputBuffer, ByteBufferBsonInput}
import zio.blocks.schema.SchemaError.ExpectationMismatch
import zio.blocks.schema.{DynamicOptic, SchemaError}
import zio.blocks.schema.binding.RegisterOffset
import zio.blocks.schema.codec.BinaryCodec
import java.nio.ByteBuffer
import scala.collection.immutable.ArraySeq
import scala.util.control.NonFatal

/**
 * Binary codec for BSON format.
 * 
 * This codec provides encoding and decoding capabilities for BSON (Binary JSON),
 * the binary serialization format used by MongoDB and other systems.
 *
 * @tparam A The type to encode/decode
 * @param valueType The type identifier for register allocation
 */
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

  /**
   * Throws a decode error with the given expectation message.
   */
  def decodeError(expectation: String): Nothing = 
    throw new BsonBinaryCodecError(Nil, expectation)

  /**
   * Throws a decode error with span information.
   */
  def decodeError(span: DynamicOptic.Node, error: Throwable): Nothing = error match {
    case e: BsonBinaryCodecError =>
      e.spans = new ::(span, e.spans)
      throw e
    case _ =>
      throw new BsonBinaryCodecError(new ::(span, Nil), getMessage(error))
  }

  /**
   * Throws a decode error with multiple span information.
   */
  def decodeError(span1: DynamicOptic.Node, span2: DynamicOptic.Node, error: Throwable): Nothing = error match {
    case e: BsonBinaryCodecError =>
      e.spans = new ::(span1, new ::(span2, e.spans))
      throw e
    case _ =>
      throw new BsonBinaryCodecError(new ::(span1, new ::(span2, Nil)), getMessage(error))
  }

  /**
   * Decodes a value from a BSON reader.
   * This method may throw exceptions which will be caught and converted to SchemaError.
   */
  def decodeUnsafe(reader: BsonReader): A

  /**
   * Encodes a value to a BSON writer.
   */
  def encode(value: A, writer: BsonWriter): Unit

  override def decode(input: ByteBuffer): Either[SchemaError, A] = {
    var pos = input.position
    val len = input.limit - pos
    var bs: Array[Byte] = null
    
    if (input.hasArray) {
      bs = input.array()
    } else {
      pos = 0
      bs = new Array[Byte](len)
      input.get(bs)
    }
    
    decode(bs, pos, len)
  }

  override def encode(value: A, output: ByteBuffer): Unit = {
    val buffer = new BasicOutputBuffer()
    val writer = new BsonBinaryWriter(buffer)
    
    try {
      encode(value, writer)
      writer.flush()
      output.put(buffer.toByteArray)
    } finally {
      writer.close()
    }
  }

  /**
   * Decodes from a byte array with offset and length.
   */
  def decode(input: Array[Byte], offset: Int, length: Int): Either[SchemaError, A] = {
    val bsonInput = new ByteBufferBsonInput(ByteBuffer.wrap(input, offset, length))
    val reader = new BsonBinaryReader(bsonInput)
    
    try {
      new Right(decodeUnsafe(reader))
    } catch {
      case error if NonFatal(error) => new Left(toError(error))
    } finally {
      reader.close()
    }
  }

  /**
   * Decodes from a byte array.
   */
  def decode(input: Array[Byte]): Either[SchemaError, A] =
    decode(input, 0, input.length)

  /**
   * Encodes to a byte array.
   */
  def encode(value: A): Array[Byte] = {
    val buffer = new BasicOutputBuffer()
    val writer = new BsonBinaryWriter(buffer)
    
    try {
      encode(value, writer)
      writer.flush()
      buffer.toByteArray
    } finally {
      writer.close()
    }
  }

  private[this] def toError(error: Throwable): SchemaError = new SchemaError(
    new ::(
      error match {
        case e: BsonBinaryCodecError =>
          var list = e.spans
          val array = new Array[DynamicOptic.Node](list.size)
          var idx = 0
          while (list ne Nil) {
            array(idx) = list.head
            idx += 1
            list = list.tail
          }
          new ExpectationMismatch(new DynamicOptic(ArraySeq.unsafeWrapArray(array)), e.getMessage)
        case _ => 
          new ExpectationMismatch(DynamicOptic.root, getMessage(error))
      },
      Nil
    )
  )

  private[this] def getMessage(error: Throwable): String = error match {
    case _: java.io.EOFException => "Unexpected end of input"
    case e => e.getMessage
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

/**
 * Internal error class for BSON codec errors.
 */
private class BsonBinaryCodecError(var spans: List[DynamicOptic.Node], message: String)
    extends Throwable(message, null, false, false) {
  override def getMessage: String = message
}
