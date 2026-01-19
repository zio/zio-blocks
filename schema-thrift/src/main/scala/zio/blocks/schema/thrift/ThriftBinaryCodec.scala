package zio.blocks.schema.thrift

import org.apache.thrift.protocol.{TBinaryProtocol, TProtocol}
import zio.blocks.schema.{DynamicOptic, SchemaError}
import zio.blocks.schema.SchemaError.ExpectationMismatch
import zio.blocks.schema.binding.RegisterOffset
import zio.blocks.schema.codec.BinaryCodec

import java.nio.ByteBuffer
import scala.collection.immutable.ArraySeq
import scala.util.control.NonFatal

/**
 * Base class for Thrift binary codecs. Provides encoding and decoding of values
 * using Apache Thrift's TBinaryProtocol.
 *
 * @tparam A
 *   The type being encoded/decoded
 * @param valueType
 *   The primitive value type indicator for register optimization
 */
abstract class ThriftBinaryCodec[A](val valueType: Int = ThriftBinaryCodec.objectType) extends BinaryCodec[A] {

  /**
   * Returns the register offset for this value type, used for performance
   * optimization.
   */
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

  /**
   * Decodes a value from the Thrift protocol. May throw on malformed input.
   */
  def decodeUnsafe(protocol: TProtocol): A

  /**
   * Encodes a value to the Thrift protocol.
   */
  def encode(value: A, protocol: TProtocol): Unit

  /**
   * Throws a decode error with the given message.
   */
  def decodeError(expectation: String): Nothing =
    throw new ThriftBinaryCodecError(Nil, expectation)

  /**
   * Throws a decode error with a path span and underlying error.
   */
  def decodeError(span: DynamicOptic.Node, error: Throwable): Nothing = error match {
    case e: ThriftBinaryCodecError =>
      e.spans = new ::(span, e.spans)
      throw e
    case _ =>
      throw new ThriftBinaryCodecError(new ::(span, Nil), getMessage(error))
  }

  /**
   * Throws a decode error with two path spans and underlying error.
   */
  def decodeError(span1: DynamicOptic.Node, span2: DynamicOptic.Node, error: Throwable): Nothing = error match {
    case e: ThriftBinaryCodecError =>
      e.spans = new ::(span1, new ::(span2, e.spans))
      throw e
    case _ =>
      throw new ThriftBinaryCodecError(new ::(span1, new ::(span2, Nil)), getMessage(error))
  }

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
    decode(java.util.Arrays.copyOfRange(bs, pos, pos + len))
  }

  override def encode(value: A, output: ByteBuffer): Unit = {
    val bytes = encode(value)
    output.put(bytes)
  }

  /**
   * Decodes from a byte array.
   */
  def decode(input: Array[Byte]): Either[SchemaError, A] =
    try {
      val transport = new ChunkTransport.Read(input)
      val protocol  = new TBinaryProtocol(transport)
      new Right(decodeUnsafe(protocol))
    } catch {
      case error if NonFatal(error) => new Left(toError(error))
    }

  /**
   * Encodes to a byte array.
   */
  def encode(value: A): Array[Byte] = {
    val transport = new ChunkTransport.Write()
    val protocol  = new TBinaryProtocol(transport)
    encode(value, protocol)
    transport.toByteArray
  }

  private def toError(error: Throwable): SchemaError = new SchemaError(
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

  private def getMessage(error: Throwable): String = error match {
    case _: java.io.EOFException         => "Unexpected end of input"
    case _: org.apache.thrift.TException => s"Thrift protocol error: ${error.getMessage}"
    case e                               => e.getMessage
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

/**
 * Internal error class for tracking decode path during error propagation.
 */
private[thrift] class ThriftBinaryCodecError(var spans: List[DynamicOptic.Node], message: String)
    extends Throwable(message, null, false, false) {
  override def getMessage: String = message
}
