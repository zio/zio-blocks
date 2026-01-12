package zio.blocks.schema.toon

import zio.blocks.schema.{DynamicOptic, SchemaError}
import zio.blocks.schema.SchemaError.ExpectationMismatch
import zio.blocks.schema.binding.RegisterOffset
import zio.blocks.schema.codec.BinaryCodec

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets.UTF_8
import scala.collection.immutable.ArraySeq

/**
 * Abstract codec for TOON (Token-Oriented Object Notation) encoding/decoding.
 *
 * TOON is a compact, human-readable serialization format designed for LLM token
 * efficiency, achieving 30-60% reduction vs JSON while maintaining lossless
 * bidirectional conversion.
 *
 * @param valueType
 *   Optimization hint for primitive types
 */
abstract class ToonBinaryCodec[A](val valueType: Int = ToonBinaryCodec.objectType) extends BinaryCodec[A] {

  /**
   * Whether this codec encodes a nested/complex value that needs its own
   * indentation block.
   */
  def isNested: Boolean = false

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

  /** The null/default value for this type. */
  def nullValue: A = null.asInstanceOf[A]

  /** Encode a value to the ToonWriter. Core method to implement. */
  def encodeValue(x: A, out: ToonWriter): Unit

  /** Encode a value as a TOON string. */
  def encodeToString(value: A): String = {
    val writer = ToonWriter()
    encodeValue(value, writer)
    writer.result
  }

  /** Encode a value to UTF-8 bytes. */
  def encodeToBytes(value: A): Array[Byte] = encodeToString(value).getBytes(UTF_8)

  override def encode(value: A, output: ByteBuffer): Unit =
    output.put(encodeToBytes(value))

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

  def decodeBytes(bytes: Array[Byte], offset: Int, length: Int): Either[SchemaError, A]

  // Error helpers
  def decodeError(expectation: String): Nothing = throw new ToonBinaryCodecError(Nil, expectation)

  def decodeError(span: DynamicOptic.Node, error: Throwable): Nothing = error match {
    case e: ToonBinaryCodecError =>
      e.spans = new ::(span, e.spans)
      throw e
    case _ =>
      throw new ToonBinaryCodecError(new ::(span, Nil), getMessage(error))
  }

  protected def getMessage(error: Throwable): String = error match {
    case _: java.io.EOFException => "Unexpected end of input"
    case e                       => e.getMessage
  }

  protected def toError(error: Throwable): SchemaError = new SchemaError(
    new ::(
      error match {
        case e: ToonBinaryCodecError =>
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

  // === Primitive Codecs ===

  val unitCodec: ToonBinaryCodec[Unit] = new ToonBinaryCodec[Unit](unitType) {
    override def nullValue: Unit                                                                      = ()
    override def encodeValue(x: Unit, out: ToonWriter): Unit                                          = out.writeRaw("null")
    override def decodeBytes(bytes: Array[Byte], offset: Int, length: Int): Either[SchemaError, Unit] =
      Right(()) // Unit always succeeds
  }

  val booleanCodec: ToonBinaryCodec[Boolean] = new ToonBinaryCodec[Boolean](booleanType) {
    override def nullValue: Boolean                                                                      = false
    override def encodeValue(x: Boolean, out: ToonWriter): Unit                                          = out.writeBoolean(x)
    override def decodeBytes(bytes: Array[Byte], offset: Int, length: Int): Either[SchemaError, Boolean] = {
      val s = new String(bytes, offset, length, UTF_8).trim
      s match {
        case "true"  => Right(true)
        case "false" => Right(false)
        case _       => Left(toError(new ToonBinaryCodecError(Nil, s"expected boolean, got: $s")))
      }
    }
  }

  val byteCodec: ToonBinaryCodec[Byte] = new ToonBinaryCodec[Byte](byteType) {
    override def nullValue: Byte                                                                      = 0
    override def encodeValue(x: Byte, out: ToonWriter): Unit                                          = out.writeInt(x.toInt)
    override def decodeBytes(bytes: Array[Byte], offset: Int, length: Int): Either[SchemaError, Byte] =
      try Right(new String(bytes, offset, length, UTF_8).trim.toByte)
      catch {
        case e: NumberFormatException => Left(toError(new ToonBinaryCodecError(Nil, s"expected byte: ${e.getMessage}")))
      }
  }

  val shortCodec: ToonBinaryCodec[Short] = new ToonBinaryCodec[Short](shortType) {
    override def nullValue: Short                                                                      = 0
    override def encodeValue(x: Short, out: ToonWriter): Unit                                          = out.writeInt(x.toInt)
    override def decodeBytes(bytes: Array[Byte], offset: Int, length: Int): Either[SchemaError, Short] =
      try Right(new String(bytes, offset, length, UTF_8).trim.toShort)
      catch {
        case e: NumberFormatException =>
          Left(toError(new ToonBinaryCodecError(Nil, s"expected short: ${e.getMessage}")))
      }
  }

  val intCodec: ToonBinaryCodec[Int] = new ToonBinaryCodec[Int](intType) {
    override def nullValue: Int                                                                      = 0
    override def encodeValue(x: Int, out: ToonWriter): Unit                                          = out.writeInt(x)
    override def decodeBytes(bytes: Array[Byte], offset: Int, length: Int): Either[SchemaError, Int] =
      try Right(new String(bytes, offset, length, UTF_8).trim.toInt)
      catch {
        case e: NumberFormatException => Left(toError(new ToonBinaryCodecError(Nil, s"expected int: ${e.getMessage}")))
      }
  }

  val longCodec: ToonBinaryCodec[Long] = new ToonBinaryCodec[Long](longType) {
    override def nullValue: Long                                                                      = 0L
    override def encodeValue(x: Long, out: ToonWriter): Unit                                          = out.writeLong(x)
    override def decodeBytes(bytes: Array[Byte], offset: Int, length: Int): Either[SchemaError, Long] =
      try Right(new String(bytes, offset, length, UTF_8).trim.toLong)
      catch {
        case e: NumberFormatException => Left(toError(new ToonBinaryCodecError(Nil, s"expected long: ${e.getMessage}")))
      }
  }

  val floatCodec: ToonBinaryCodec[Float] = new ToonBinaryCodec[Float](floatType) {
    override def nullValue: Float                                                                      = 0.0f
    override def encodeValue(x: Float, out: ToonWriter): Unit                                          = out.writeFloat(x)
    override def decodeBytes(bytes: Array[Byte], offset: Int, length: Int): Either[SchemaError, Float] = {
      val s = new String(bytes, offset, length, UTF_8).trim
      if (s == "null") Right(Float.NaN)
      else
        try Right(s.toFloat)
        catch {
          case e: NumberFormatException =>
            Left(toError(new ToonBinaryCodecError(Nil, s"expected float: ${e.getMessage}")))
        }
    }
  }

  val doubleCodec: ToonBinaryCodec[Double] = new ToonBinaryCodec[Double](doubleType) {
    override def nullValue: Double                                                                      = 0.0
    override def encodeValue(x: Double, out: ToonWriter): Unit                                          = out.writeDouble(x)
    override def decodeBytes(bytes: Array[Byte], offset: Int, length: Int): Either[SchemaError, Double] = {
      val s = new String(bytes, offset, length, UTF_8).trim
      if (s == "null") Right(Double.NaN)
      else
        try Right(s.toDouble)
        catch {
          case e: NumberFormatException =>
            Left(toError(new ToonBinaryCodecError(Nil, s"expected double: ${e.getMessage}")))
        }
    }
  }

  val charCodec: ToonBinaryCodec[Char] = new ToonBinaryCodec[Char](charType) {
    override def nullValue: Char                                                                      = 0
    override def encodeValue(x: Char, out: ToonWriter): Unit                                          = out.writeString(x.toString)
    override def decodeBytes(bytes: Array[Byte], offset: Int, length: Int): Either[SchemaError, Char] = {
      val s = new String(bytes, offset, length, UTF_8)
      if (s.length == 1) Right(s.charAt(0))
      else Left(toError(new ToonBinaryCodecError(Nil, s"expected single character, got: $s")))
    }
  }

  val stringCodec: ToonBinaryCodec[String] = new ToonBinaryCodec[String] {
    override def nullValue: String                                                                      = ""
    override def encodeValue(x: String, out: ToonWriter): Unit                                          = out.writeString(x)
    override def decodeBytes(bytes: Array[Byte], offset: Int, length: Int): Either[SchemaError, String] =
      Right(new String(bytes, offset, length, UTF_8))
  }

  val bigIntCodec: ToonBinaryCodec[BigInt] = new ToonBinaryCodec[BigInt] {
    override def nullValue: BigInt                                                                      = BigInt(0)
    override def encodeValue(x: BigInt, out: ToonWriter): Unit                                          = out.writeBigInt(x)
    override def decodeBytes(bytes: Array[Byte], offset: Int, length: Int): Either[SchemaError, BigInt] =
      try Right(BigInt(new String(bytes, offset, length, UTF_8).trim))
      catch {
        case e: NumberFormatException =>
          Left(toError(new ToonBinaryCodecError(Nil, s"expected BigInt: ${e.getMessage}")))
      }
  }

  val bigDecimalCodec: ToonBinaryCodec[BigDecimal] = new ToonBinaryCodec[BigDecimal] {
    override def nullValue: BigDecimal                                                                      = BigDecimal(0)
    override def encodeValue(x: BigDecimal, out: ToonWriter): Unit                                          = out.writeBigDecimal(x)
    override def decodeBytes(bytes: Array[Byte], offset: Int, length: Int): Either[SchemaError, BigDecimal] =
      try Right(BigDecimal(new String(bytes, offset, length, UTF_8).trim))
      catch {
        case e: NumberFormatException =>
          Left(toError(new ToonBinaryCodecError(Nil, s"expected BigDecimal: ${e.getMessage}")))
      }
  }
}

private[toon] class ToonBinaryCodecError(var spans: List[DynamicOptic.Node], message: String)
    extends Throwable(message, null, false, false) {
  override def getMessage: String = message
}
