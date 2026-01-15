package zio.blocks.schema.thrift

import org.apache.thrift.protocol.TProtocol
import zio.blocks.schema.SchemaError.ExpectationMismatch
import zio.blocks.schema.{DynamicOptic, SchemaError}
import zio.blocks.schema.binding.RegisterOffset
import zio.blocks.schema.codec.BinaryCodec
import java.nio.ByteBuffer
import scala.collection.immutable.ArraySeq
import scala.util.control.NonFatal

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

  def decodeError(expectation: String): Nothing = throw new ThriftBinaryCodecError(Nil, expectation)

  def decodeError(span: DynamicOptic.Node, error: Throwable): Nothing = error match {
    case e: ThriftBinaryCodecError =>
      e.spans = new ::(span, e.spans)
      throw e
    case _ =>
      throw new ThriftBinaryCodecError(new ::(span, Nil), getMessage(error))
  }

  def decodeError(span1: DynamicOptic.Node, span2: DynamicOptic.Node, error: Throwable): Nothing = error match {
    case e: ThriftBinaryCodecError =>
      e.spans = new ::(span1, new ::(span2, e.spans))
      throw e
    case _ =>
      throw new ThriftBinaryCodecError(new ::(span1, new ::(span2, Nil)), getMessage(error))
  }

  def decodeUnsafe(protocol: TProtocol): A

  def encode(value: A, protocol: TProtocol): Unit

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
    val transport = new ChunkTransport(bs, pos, len)
    val protocol  = new org.apache.thrift.protocol.TCompactProtocol(transport)
    decode(protocol)
  }

  override def encode(value: A, output: ByteBuffer): Unit = {
    val transport = new ChunkTransport()
    val protocol  = new org.apache.thrift.protocol.TCompactProtocol(transport)
    encode(value, protocol)
    output.put(transport.getBuffer, 0, transport.getBufferPosition)
  }

  def decode(input: Array[Byte]): Either[SchemaError, A] = {
    val transport = new ChunkTransport(input, 0, input.length)
    val protocol  = new org.apache.thrift.protocol.TCompactProtocol(transport)
    decode(protocol)
  }

  def encode(value: A): Array[Byte] = {
    val transport = new ChunkTransport()
    val protocol  = new org.apache.thrift.protocol.TCompactProtocol(transport)
    encode(value, protocol)
    transport.toByteArray
  }

  def decode(input: java.io.InputStream): Either[SchemaError, A] = {
    val transport = new org.apache.thrift.transport.TIOStreamTransport(input)
    val protocol  = new org.apache.thrift.protocol.TCompactProtocol(transport)
    decode(protocol)
  }

  def encode(value: A, output: java.io.OutputStream): Unit = {
    val transport = new org.apache.thrift.transport.TIOStreamTransport(output)
    val protocol  = new org.apache.thrift.protocol.TCompactProtocol(transport)
    encode(value, protocol)
    transport.flush()
  }

  private[this] def decode(protocol: TProtocol): Either[SchemaError, A] =
    try new Right(decodeUnsafe(protocol))
    catch {
      case error if NonFatal(error) => new Left(toError(error))
    }

  private[this] def toError(error: Throwable): SchemaError = new SchemaError(
    new ::(
      error match {
        case e: ThriftBinaryCodecError =>
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
    case _: java.io.EOFException                            => "Unexpected end of input"
    case _: org.apache.thrift.transport.TTransportException => "Unexpected end of input"
    case e                                                  => e.getMessage
  }
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

  val maxCollectionSize: Int = Integer.MAX_VALUE - 8
}

private[thrift] class ThriftBinaryCodecError(var spans: List[DynamicOptic.Node], message: String)
    extends Throwable(message, null, false, false) {
  override def getMessage: String = message
}
