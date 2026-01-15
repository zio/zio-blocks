package zio.blocks.schema.thrift

import zio.blocks.schema.codec.BinaryCodec
import zio.blocks.schema.SchemaError
import zio.blocks.schema.DynamicOptic
import zio.blocks.schema.SchemaError.ExpectationMismatch
import org.apache.thrift.protocol.{TProtocol, TBinaryProtocol}
import java.nio.ByteBuffer
import scala.util.control.NonFatal
import zio.blocks.chunk.Chunk

import zio.blocks.schema.binding.RegisterOffset

abstract class ThriftBinaryCodec[A](val valueType: Int = ThriftBinaryCodec.objectType) extends BinaryCodec[A] {
  val valueOffset: RegisterOffset.RegisterOffset = valueType match {
    case ThriftBinaryCodec.objectType  => RegisterOffset(objects = 1)
    case ThriftBinaryCodec.booleanType => RegisterOffset(booleans = 1)
    case ThriftBinaryCodec.byteType    => RegisterOffset(bytes = 1)
    case ThriftBinaryCodec.charType    => RegisterOffset(chars = 1)
    case ThriftBinaryCodec.shortType   => RegisterOffset(shorts = 1)
    case ThriftBinaryCodec.floatType   => RegisterOffset(floats = 1)
    case ThriftBinaryCodec.intType     => RegisterOffset(ints = 1)
    case ThriftBinaryCodec.doubleType  => RegisterOffset(doubles = 1)
    case ThriftBinaryCodec.longType    => RegisterOffset(longs = 1)
    case _                             => RegisterOffset.Zero
  }

  def encode(value: A, protocol: TProtocol): Unit
  def decodeUnsafe(protocol: TProtocol): A

  def encode(value: A): Chunk[Byte] = {
    val transport = new ThriftTransport.Write()
    val protocol  = new TBinaryProtocol(transport)
    encode(value, protocol)
    transport.chunk
  }

  def decode(input: Chunk[Byte]): Either[SchemaError, A] = {
    val transport = new ThriftTransport.Read(input)
    val protocol  = new TBinaryProtocol(transport)
    try {
      Right(decodeUnsafe(protocol))
    } catch {
      case error if NonFatal(error) => Left(toError(error))
    }
  }

  override def encode(value: A, output: ByteBuffer): Unit = {
    val transport = new ThriftTransport.Write()
    val protocol  = new TBinaryProtocol(transport)
    encode(value, protocol)
    val chunk = transport.chunk
    output.put(chunk.toArray)
  }

  override def decode(input: ByteBuffer): Either[SchemaError, A] = {
    val chunk = Chunk.fromByteBuffer(input)
    decode(chunk)
  }

  private[this] def toError(error: Throwable): SchemaError = new SchemaError(
    new ::(
      new ExpectationMismatch(DynamicOptic.root, getMessage(error)),
      Nil
    )
  )

  private[this] def getMessage(error: Throwable): String =
    if (error.getMessage != null) error.getMessage else error.getClass.getSimpleName

  protected def decodeError(message: String): Nothing =
    throw new RuntimeException(message)
}

object ThriftBinaryCodec {
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
}
