package zio.blocks.schema.messagepack

import org.msgpack.core.{MessagePack, MessagePacker, MessageUnpacker}
import zio.blocks.schema.DynamicOptic
import zio.blocks.schema.SchemaError
import zio.blocks.schema.SchemaError.ExpectationMismatch
import zio.blocks.schema.binding.RegisterOffset
import zio.blocks.schema.codec.BinaryCodec

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, InputStream, OutputStream}
import java.nio.ByteBuffer
import scala.collection.immutable.ArraySeq
import scala.util.control.NonFatal

/**
 * Abstract base class for MessagePack binary codecs.
 *
 * Provides encode/decode functionality using the msgpack-core library.
 * Implementations should override `encodeUnsafe` and `decodeUnsafe` to handle
 * the specific type encoding/decoding logic.
 */
abstract class MessagePackBinaryCodec[A](val valueType: Int = MessagePackBinaryCodec.objectType)
    extends BinaryCodec[A] {

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

  def decodeError(expectation: String): Nothing =
    throw new MessagePackBinaryCodecError(Nil, expectation)

  def decodeError(span: DynamicOptic.Node, error: Throwable): Nothing = error match {
    case e: MessagePackBinaryCodecError =>
      e.spans = new ::(span, e.spans)
      throw e
    case _ =>
      throw new MessagePackBinaryCodecError(new ::(span, Nil), getMessage(error))
  }

  def decodeError(span1: DynamicOptic.Node, span2: DynamicOptic.Node, error: Throwable): Nothing = error match {
    case e: MessagePackBinaryCodecError =>
      e.spans = new ::(span1, new ::(span2, e.spans))
      throw e
    case _ =>
      throw new MessagePackBinaryCodecError(new ::(span1, new ::(span2, Nil)), getMessage(error))
  }

  /** Decodes a value from a MessageUnpacker. Override this in subclasses. */
  def decodeUnsafe(unpacker: MessageUnpacker): A

  /** Encodes a value to a MessagePacker. Override this in subclasses. */
  def encode(value: A, packer: MessagePacker): Unit

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
    decode(new ByteArrayInputStream(bs, pos, len))
  }

  override def encode(value: A, output: ByteBuffer): Unit = {
    val bytes = encode(value)
    output.put(bytes)
  }

  def decode(input: Array[Byte]): Either[SchemaError, A] =
    decode(new ByteArrayInputStream(input))

  def encode(value: A): Array[Byte] = {
    val output = new ByteArrayOutputStream
    encode(value, output)
    output.toByteArray
  }

  def decode(input: InputStream): Either[SchemaError, A] = {
    val unpacker = MessagePack.newDefaultUnpacker(input)
    try new Right(decodeUnsafe(unpacker))
    catch {
      case error if NonFatal(error) => new Left(toError(error))
    } finally {
      unpacker.close()
    }
  }

  def encode(value: A, output: OutputStream): Unit = {
    val packer = MessagePack.newDefaultPacker(output)
    encode(value, packer)
    packer.close()
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
    case _: java.io.EOFException                                => "Unexpected end of input"
    case _: org.msgpack.core.MessageInsufficientBufferException => "Unexpected end of input"
    case e                                                      => if (e.getMessage != null) e.getMessage else e.getClass.getName
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

  /** Maximum allowed collection size to prevent DoS attacks. */
  val maxCollectionSize: Int = 10000000

  /** Maximum allowed binary size (100MB) to prevent DoS attacks. */
  val maxBinarySize: Int = 100 * 1024 * 1024
}

private[messagepack] class MessagePackBinaryCodecError(var spans: List[DynamicOptic.Node], message: String)
    extends Throwable(message, null, false, false) {
  override def getMessage: String = message
}
