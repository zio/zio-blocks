package zio.blocks.schema.toon

import zio.blocks.schema.{DynamicOptic, SchemaError}
import zio.blocks.schema.binding.RegisterOffset
import zio.blocks.schema.codec.BinaryCodec

import java.nio.ByteBuffer

/**
 * Abstract codec for TOON (Token-Oriented Object Notation) encoding/decoding.
 *
 * TOON is a compact, human-readable serialization format designed for LLM token efficiency,
 * achieving 30-60% reduction vs JSON while maintaining lossless bidirectional conversion.
 *
 * @param valueType Optimization hint for primitive types
 */
abstract class ToonBinaryCodec[A](val valueType: Int = ToonBinaryCodec.objectType) extends BinaryCodec[A] {
  val valueOffset: RegisterOffset.RegisterOffset = valueType match {
    case ToonBinaryCodec.objectType  => RegisterOffset(objects = 1)
    case ToonBinaryCodec.booleanType => RegisterOffset(booleans = 1)
    case ToonBinaryCodec.byteType    => RegisterOffset(bytes = 1)
    case ToonBinaryCodec.charType    => RegisterOffset(chars = 1)
    case ToonBinaryCodec.shortType   => RegisterOffset(shorts = 1)
    case ToonBinaryCodec.floatType   => RegisterOffset(floats = 1)
    case ToonBinaryCodec.intType     => RegisterOffset(ints = 1)
    case ToonBinaryCodec.doubleType  => RegisterOffset(doubles = 1)
    case ToonBinaryCodec.longType    => RegisterOffset(longs = 1)
    case _                           => RegisterOffset.Zero
  }

  def decodeError(expectation: String): Nothing = throw new ToonBinaryCodecError(Nil, expectation)

  def decodeError(span: DynamicOptic.Node, error: Throwable): Nothing = error match {
    case e: ToonBinaryCodecError =>
      e.spans = new ::(span, e.spans)
      throw e
    case _ =>
      throw new ToonBinaryCodecError(new ::(span, Nil), getMessage(error))
  }

  def decodeError(span1: DynamicOptic.Node, span2: DynamicOptic.Node, error: Throwable): Nothing = error match {
    case e: ToonBinaryCodecError =>
      e.spans = new ::(span1, new ::(span2, e.spans))
      throw e
    case _ =>
      throw new ToonBinaryCodecError(new ::(span1, new ::(span2, Nil)), getMessage(error))
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
    decodeBytes(bs, pos, len)
  }

  override def encode(value: A, output: ByteBuffer): Unit = {
    val bytes = encodeToBytes(value)
    output.put(bytes)
  }

  def decodeBytes(bytes: Array[Byte], offset: Int, length: Int): Either[SchemaError, A]

  def encodeToBytes(value: A): Array[Byte]

  protected def getMessage(error: Throwable): String = error match {
    case _: java.io.EOFException => "Unexpected end of input"
    case e                       => e.getMessage
  }
}

object ToonBinaryCodec {
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

private[toon] class ToonBinaryCodecError(var spans: List[DynamicOptic.Node], message: String)
    extends Throwable(message, null, false, false) {
  override def getMessage: String = message
}
