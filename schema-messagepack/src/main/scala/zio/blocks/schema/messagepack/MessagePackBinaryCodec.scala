package zio.blocks.schema.messagepack

import org.msgpack.core.{MessageBufferPacker, MessagePack, MessageUnpacker}
import zio.blocks.schema.SchemaError.ExpectationMismatch
import zio.blocks.schema.binding.RegisterOffset
import zio.blocks.schema.codec.BinaryCodec
import zio.blocks.schema.{DynamicOptic, SchemaError}

import java.nio.ByteBuffer
import scala.collection.immutable.ArraySeq
import scala.util.control.NonFatal

/**
 * Abstract class for encoding and decoding values of type `A` to and from
 * MessagePack binary format.
 *
 * @param valueType
 *   Integer representing the type of the value for register offset calculation.
 */
abstract class MessagePackBinaryCodec[A](val valueType: Int = MessagePackBinaryCodec.objectType) extends BinaryCodec[A] {

  val valueOffset: RegisterOffset.RegisterOffset = valueType match {
    case MessagePackBinaryCodec.objectType  => RegisterOffset(objects = 1)
    case MessagePackBinaryCodec.booleanType => RegisterOffset(booleans = 1)
    case MessagePackBinaryCodec.byteType    => RegisterOffset(bytes = 1)
    case MessagePackBinaryCodec.charType    => RegisterOffset(chars = 1)
    case MessagePackBinaryCodec.shortType   => RegisterOffset(shorts = 1)
    case MessagePackBinaryCodec.floatType   => RegisterOffset(floats = 1)
    case MessagePackBinaryCodec.intType     => RegisterOffset(ints = 1)
    case MessagePackBinaryCodec.doubleType  => RegisterOffset(doubles = 1)
    case MessagePackBinaryCodec.longType    => RegisterOffset(longs = 1)
    case _                                  => RegisterOffset.Zero
  }

  /**
   * Decodes a value of type `A` from a MessageUnpacker.
   */
  def decodeValue(unpacker: MessageUnpacker): A

  /**
   * Encodes a value of type `A` to a MessageBufferPacker.
   */
  def encodeValue(value: A, packer: MessageBufferPacker): Unit

  /**
   * Returns a default/null value for this type.
   */
  def nullValue: A = null.asInstanceOf[A]

  def decodeError(expectation: String): Nothing =
    throw new MessagePackBinaryCodecError(Nil, expectation)

  def decodeError(span: DynamicOptic.Node, error: Throwable): Nothing = error match {
    case e: MessagePackBinaryCodecError =>
      e.spans = new ::(span, e.spans)
      throw e
    case _ =>
      throw new MessagePackBinaryCodecError(new ::(span, Nil), getMessage(error))
  }

  override def decode(input: ByteBuffer): Either[SchemaError, A] = {
    var pos             = input.position
    val len             = input.limit - pos
    var bs: Array[Byte] = null
    if (input.hasArray) {
      bs = input.array()
      pos = input.arrayOffset() + pos
    } else {
      pos = 0
      bs = new Array[Byte](len)
      input.get(bs)
    }
    decode(bs, pos, len)
  }

  override def encode(value: A, output: ByteBuffer): Unit = {
    val bytes = encode(value)
    output.put(bytes)
  }

  def decode(input: Array[Byte]): Either[SchemaError, A] =
    decode(input, 0, input.length)

  def decode(input: Array[Byte], offset: Int, length: Int): Either[SchemaError, A] =
    try {
      val unpacker = MessagePack.newDefaultUnpacker(input, offset, length)
      try new Right(decodeValue(unpacker))
      finally unpacker.close()
    } catch {
      case error if NonFatal(error) => new Left(toError(error))
    }

  def encode(value: A): Array[Byte] = {
    val packer = MessagePack.newDefaultBufferPacker()
    try {
      encodeValue(value, packer)
      packer.toByteArray
    } finally packer.close()
  }

  def decode(input: java.io.InputStream): Either[SchemaError, A] =
    try {
      val unpacker = MessagePack.newDefaultUnpacker(input)
      try new Right(decodeValue(unpacker))
      finally unpacker.close()
    } catch {
      case error if NonFatal(error) => new Left(toError(error))
    }

  def encode(value: A, output: java.io.OutputStream): Unit = {
    val packer = MessagePack.newDefaultPacker(output)
    try {
      encodeValue(value, packer)
      packer.flush()
    } finally packer.close()
  }

  private[this] def toError(error: Throwable): SchemaError = new SchemaError(
    new ::(
      error match {
        case e: MessagePackBinaryCodecError =>
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
    case _: java.io.EOFException                        => "Unexpected end of input"
    case _: org.msgpack.core.MessageInsufficientBufferException => "Insufficient buffer for MessagePack decoding"
    case e                                              => e.getMessage
  }
}

object MessagePackBinaryCodec {
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

private[messagepack] class MessagePackBinaryCodecError(var spans: List[DynamicOptic.Node], message: String)
    extends Throwable(message, null, false, false) {
  override def getMessage: String = message
}
