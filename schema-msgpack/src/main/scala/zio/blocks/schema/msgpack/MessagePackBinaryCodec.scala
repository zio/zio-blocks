package zio.blocks.schema.msgpack

import zio.blocks.schema.SchemaError.ExpectationMismatch
import zio.blocks.schema.{DynamicOptic, SchemaError}
import zio.blocks.schema.binding.RegisterOffset
import zio.blocks.schema.codec.BinaryCodec
import org.msgpack.core.{MessagePacker, MessageUnpacker, MessagePack}
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import scala.collection.immutable.ArraySeq
import scala.util.control.NonFatal

/**
 * MessagePack binary codec for ZIO Blocks Schema. Provides efficient binary
 * serialization using the MessagePack format.
 *
 * Key features:
 *   - Forward-compatible: Unknown fields are skipped gracefully
 *   - Order-independent record decoding
 *   - High performance with minimal allocations
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

  def decodeError(expectation: String): Nothing = throw new MessagePackBinaryCodecError(Nil, expectation)

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

  def decodeUnsafe(unpacker: MessageUnpacker): A

  def encode(value: A, packer: MessagePacker): Unit

  override def decode(input: ByteBuffer): Either[SchemaError, A] = {
    val len = input.remaining()
    val bs  = new Array[Byte](len)
    input.get(bs)
    decode(bs)
  }

  override def encode(value: A, output: ByteBuffer): Unit = {
    val bs = encode(value)
    output.put(bs)
  }

  def decode(input: Array[Byte]): Either[SchemaError, A] = {
    val unpacker = MessagePack.newDefaultUnpacker(input)
    try new Right(decodeUnsafe(unpacker))
    catch {
      case error if NonFatal(error) => new Left(toError(error))
    } finally {
      unpacker.close()
    }
  }

  def encode(value: A): Array[Byte] = {
    val output = new ByteArrayOutputStream()
    val packer = MessagePack.newDefaultPacker(output)
    try {
      encode(value, packer)
      packer.flush()
      output.toByteArray
    } finally {
      packer.close()
    }
  }

  def decode(input: java.io.InputStream): Either[SchemaError, A] = {
    val unpacker = MessagePack.newDefaultUnpacker(input)
    try new Right(decodeUnsafe(unpacker))
    catch {
      case error if NonFatal(error) => new Left(toError(error))
    } finally {
      unpacker.close()
    }
  }

  def encode(value: A, output: java.io.OutputStream): Unit = {
    val packer = MessagePack.newDefaultPacker(output)
    try {
      encode(value, packer)
      packer.flush()
    } finally {
      packer.close()
    }
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
    case _: java.io.EOFException => "Unexpected end of input"
    case e                       => e.getMessage
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

private class MessagePackBinaryCodecError(var spans: List[DynamicOptic.Node], message: String)
    extends Throwable(message, null, false, false) {
  override def getMessage: String = message
}
