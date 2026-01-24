package zio.blocks.schema.thrift

import org.apache.thrift.protocol._
import org.apache.thrift.transport.TMemoryTransport
import zio.blocks.schema.{DynamicOptic, SchemaError}
import zio.blocks.schema.SchemaError.ExpectationMismatch
import zio.blocks.schema.binding.RegisterOffset
import zio.blocks.schema.codec.BinaryCodec
import java.nio.ByteBuffer
import java.time._
import java.util.{Currency, UUID}
import scala.collection.immutable.ArraySeq
import scala.util.control.NonFatal
import java.math.{BigInteger, MathContext}

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
   * Throws decode error with the given message.
   */
  def decodeError(expectation: String): Nothing = throw new ThriftBinaryCodecError(Nil, expectation)

  /**
   * Throws decode error with a path span and underlying error.
   */
  def decodeError(span: DynamicOptic.Node, error: Throwable): Nothing = error match {
    case e: ThriftBinaryCodecError =>
      e.spans = new ::(span, e.spans)
      throw e
    case _ =>
      throw new ThriftBinaryCodecError(new ::(span, Nil), getMessage(error))
  }

  /**
   * Throws decode error with two path spans and an underlying error.
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

  override def encode(value: A, output: ByteBuffer): Unit = output.put(encode(value))

  /**
   * Decodes from a byte array.
   */
  def decode(input: Array[Byte]): Either[SchemaError, A] =
    try {
      val transport = new TMemoryTransport(input)
      val protocol  = new TBinaryProtocol(transport)
      new Right(decodeUnsafe(protocol))
    } catch {
      case error if NonFatal(error) => new Left(toError(error))
    }

  /**
   * Encodes to a byte array.
   */
  def encode(value: A): Array[Byte] = {
    val transport = new TMemoryTransport(Array.emptyByteArray)
    encode(value, new TBinaryProtocol(transport))
    transport.getOutput.toByteArray
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

  val unitCodec: ThriftBinaryCodec[Unit] = new ThriftBinaryCodec[Unit](ThriftBinaryCodec.unitType) {
    def decodeUnsafe(protocol: TProtocol): Unit = ()

    def encode(value: Unit, protocol: TProtocol): Unit = ()
  }

  val booleanCodec: ThriftBinaryCodec[Boolean] = new ThriftBinaryCodec[Boolean](ThriftBinaryCodec.booleanType) {
    def decodeUnsafe(protocol: TProtocol): Boolean = protocol.readBool()

    def encode(value: Boolean, protocol: TProtocol): Unit = protocol.writeBool(value)
  }

  val byteCodec: ThriftBinaryCodec[Byte] = new ThriftBinaryCodec[Byte](ThriftBinaryCodec.byteType) {
    def decodeUnsafe(protocol: TProtocol): Byte = protocol.readByte()

    def encode(value: Byte, protocol: TProtocol): Unit = protocol.writeByte(value)
  }

  val shortCodec: ThriftBinaryCodec[Short] = new ThriftBinaryCodec[Short](ThriftBinaryCodec.shortType) {
    def decodeUnsafe(protocol: TProtocol): Short = protocol.readI16()

    def encode(value: Short, protocol: TProtocol): Unit = protocol.writeI16(value)
  }

  val intCodec: ThriftBinaryCodec[Int] = new ThriftBinaryCodec[Int](ThriftBinaryCodec.intType) {
    def decodeUnsafe(protocol: TProtocol): Int = protocol.readI32()

    def encode(value: Int, protocol: TProtocol): Unit = protocol.writeI32(value)
  }

  val longCodec: ThriftBinaryCodec[Long] = new ThriftBinaryCodec[Long](ThriftBinaryCodec.longType) {
    def decodeUnsafe(protocol: TProtocol): Long = protocol.readI64()

    def encode(value: Long, protocol: TProtocol): Unit = protocol.writeI64(value)
  }

  val floatCodec: ThriftBinaryCodec[Float] = new ThriftBinaryCodec[Float](ThriftBinaryCodec.floatType) {
    def decodeUnsafe(protocol: TProtocol): Float = protocol.readDouble().toFloat

    def encode(value: Float, protocol: TProtocol): Unit = protocol.writeDouble(value.toDouble)
  }

  val doubleCodec: ThriftBinaryCodec[Double] = new ThriftBinaryCodec[Double](ThriftBinaryCodec.doubleType) {
    def decodeUnsafe(protocol: TProtocol): Double = protocol.readDouble()

    def encode(value: Double, protocol: TProtocol): Unit = protocol.writeDouble(value)
  }

  val charCodec: ThriftBinaryCodec[Char] = new ThriftBinaryCodec[Char](ThriftBinaryCodec.charType) {
    def decodeUnsafe(protocol: TProtocol): Char = protocol.readI16().toChar

    def encode(value: Char, protocol: TProtocol): Unit = protocol.writeI16(value.toShort)
  }

  val stringCodec: ThriftBinaryCodec[String] = new ThriftBinaryCodec[String]() {
    def decodeUnsafe(protocol: TProtocol): String = protocol.readString()

    def encode(value: String, protocol: TProtocol): Unit = protocol.writeString(value)
  }

  val bigIntCodec: ThriftBinaryCodec[BigInt] = new ThriftBinaryCodec[BigInt]() {
    def decodeUnsafe(protocol: TProtocol): BigInt = {
      val buf = protocol.readBinary()
      val arr = new Array[Byte](buf.remaining())
      buf.get(arr)
      BigInt(new BigInteger(arr))
    }

    def encode(value: BigInt, protocol: TProtocol): Unit = protocol.writeBinary(ByteBuffer.wrap(value.toByteArray))
  }

  val bigDecimalCodec: ThriftBinaryCodec[BigDecimal] = new ThriftBinaryCodec[BigDecimal]() {
    def decodeUnsafe(protocol: TProtocol): BigDecimal = {
      protocol.readFieldBegin()
      val unscaledBuf = protocol.readBinary()
      val unscaledArr = new Array[Byte](unscaledBuf.remaining())
      unscaledBuf.get(unscaledArr)
      val unscaled = new BigInteger(unscaledArr)
      protocol.readFieldBegin()
      val precision = protocol.readI32()
      protocol.readFieldBegin()
      val scale = protocol.readI32()
      protocol.readFieldBegin() // read STOP
      new BigDecimal(new java.math.BigDecimal(unscaled, scale, new MathContext(precision)))
    }

    def encode(value: BigDecimal, protocol: TProtocol): Unit = {
      val bd = value.underlying()
      protocol.writeFieldBegin(new TField("unscaled", TType.STRING, 1))
      protocol.writeBinary(ByteBuffer.wrap(bd.unscaledValue().toByteArray))
      protocol.writeFieldBegin(new TField("precision", TType.I32, 2))
      protocol.writeI32(bd.precision())
      protocol.writeFieldBegin(new TField("scale", TType.I32, 3))
      protocol.writeI32(bd.scale())
      protocol.writeFieldStop()
    }
  }

  val dayOfWeekCodec: ThriftBinaryCodec[DayOfWeek] = new ThriftBinaryCodec[java.time.DayOfWeek]() {
    def decodeUnsafe(protocol: TProtocol): java.time.DayOfWeek = java.time.DayOfWeek.of(protocol.readByte().toInt)

    def encode(value: java.time.DayOfWeek, protocol: TProtocol): Unit = protocol.writeByte(value.getValue.toByte)
  }

  val monthCodec: ThriftBinaryCodec[Month] = new ThriftBinaryCodec[java.time.Month]() {
    def decodeUnsafe(protocol: TProtocol): java.time.Month = java.time.Month.of(protocol.readByte().toInt)

    def encode(value: java.time.Month, protocol: TProtocol): Unit = protocol.writeByte(value.getValue.toByte)
  }

  val yearCodec: ThriftBinaryCodec[Year] = new ThriftBinaryCodec[java.time.Year]() {
    def decodeUnsafe(protocol: TProtocol): java.time.Year = java.time.Year.of(protocol.readI32())

    def encode(value: java.time.Year, protocol: TProtocol): Unit = protocol.writeI32(value.getValue)
  }

  val monthDayCodec: ThriftBinaryCodec[MonthDay] = new ThriftBinaryCodec[java.time.MonthDay]() {
    def decodeUnsafe(protocol: TProtocol): java.time.MonthDay = java.time.MonthDay.parse(protocol.readString())

    def encode(value: java.time.MonthDay, protocol: TProtocol): Unit = protocol.writeString(value.toString)
  }

  val yearMonthCodec: ThriftBinaryCodec[YearMonth] = new ThriftBinaryCodec[java.time.YearMonth]() {
    def decodeUnsafe(protocol: TProtocol): java.time.YearMonth = java.time.YearMonth.parse(protocol.readString())

    def encode(value: java.time.YearMonth, protocol: TProtocol): Unit = {
      var str = value.toString
      if (value.getYear >= 10000) str = "+" + str
      protocol.writeString(str)
    }
  }

  val periodCodec: ThriftBinaryCodec[Period] = new ThriftBinaryCodec[java.time.Period]() {
    def decodeUnsafe(protocol: TProtocol): java.time.Period = java.time.Period.parse(protocol.readString())

    def encode(value: java.time.Period, protocol: TProtocol): Unit = protocol.writeString(value.toString)
  }

  val durationCodec: ThriftBinaryCodec[Duration] = new ThriftBinaryCodec[java.time.Duration]() {
    def decodeUnsafe(protocol: TProtocol): java.time.Duration = java.time.Duration.parse(protocol.readString())

    def encode(value: java.time.Duration, protocol: TProtocol): Unit = protocol.writeString(value.toString)
  }

  val instantCodec: ThriftBinaryCodec[Instant] = new ThriftBinaryCodec[java.time.Instant]() {
    def decodeUnsafe(protocol: TProtocol): java.time.Instant = java.time.Instant.parse(protocol.readString())

    def encode(value: java.time.Instant, protocol: TProtocol): Unit = protocol.writeString(value.toString)
  }

  val localDateCodec: ThriftBinaryCodec[LocalDate] = new ThriftBinaryCodec[java.time.LocalDate]() {
    def decodeUnsafe(protocol: TProtocol): java.time.LocalDate = java.time.LocalDate.parse(protocol.readString())

    def encode(value: java.time.LocalDate, protocol: TProtocol): Unit = protocol.writeString(value.toString)
  }

  val localTimeCodec: ThriftBinaryCodec[LocalTime] = new ThriftBinaryCodec[java.time.LocalTime]() {
    def decodeUnsafe(protocol: TProtocol): java.time.LocalTime = java.time.LocalTime.parse(protocol.readString())

    def encode(value: java.time.LocalTime, protocol: TProtocol): Unit = protocol.writeString(value.toString)
  }

  val localDateTimeCodec: ThriftBinaryCodec[LocalDateTime] = new ThriftBinaryCodec[java.time.LocalDateTime]() {
    def decodeUnsafe(protocol: TProtocol): java.time.LocalDateTime =
      java.time.LocalDateTime.parse(protocol.readString())

    def encode(value: java.time.LocalDateTime, protocol: TProtocol): Unit = protocol.writeString(value.toString)
  }

  val offsetTimeCodec: ThriftBinaryCodec[OffsetTime] = new ThriftBinaryCodec[java.time.OffsetTime]() {
    def decodeUnsafe(protocol: TProtocol): java.time.OffsetTime = java.time.OffsetTime.parse(protocol.readString())

    def encode(value: java.time.OffsetTime, protocol: TProtocol): Unit = protocol.writeString(value.toString)
  }

  val offsetDateTimeCodec: ThriftBinaryCodec[OffsetDateTime] = new ThriftBinaryCodec[java.time.OffsetDateTime]() {
    def decodeUnsafe(protocol: TProtocol): java.time.OffsetDateTime =
      java.time.OffsetDateTime.parse(protocol.readString())

    def encode(value: java.time.OffsetDateTime, protocol: TProtocol): Unit = protocol.writeString(value.toString)
  }

  val zonedDateTimeCodec: ThriftBinaryCodec[ZonedDateTime] = new ThriftBinaryCodec[java.time.ZonedDateTime]() {
    def decodeUnsafe(protocol: TProtocol): java.time.ZonedDateTime =
      java.time.ZonedDateTime.parse(protocol.readString())

    def encode(value: java.time.ZonedDateTime, protocol: TProtocol): Unit = protocol.writeString(value.toString)
  }

  val zoneIdCodec: ThriftBinaryCodec[ZoneId] = new ThriftBinaryCodec[java.time.ZoneId]() {
    def decodeUnsafe(protocol: TProtocol): java.time.ZoneId = java.time.ZoneId.of(protocol.readString())

    def encode(value: java.time.ZoneId, protocol: TProtocol): Unit = protocol.writeString(value.getId)
  }

  val zoneOffsetCodec: ThriftBinaryCodec[ZoneOffset] = new ThriftBinaryCodec[java.time.ZoneOffset]() {
    def decodeUnsafe(protocol: TProtocol): java.time.ZoneOffset =
      java.time.ZoneOffset.ofTotalSeconds(protocol.readI32())

    def encode(value: java.time.ZoneOffset, protocol: TProtocol): Unit = protocol.writeI32(value.getTotalSeconds)
  }

  val currencyCodec: ThriftBinaryCodec[Currency] = new ThriftBinaryCodec[java.util.Currency]() {
    def decodeUnsafe(protocol: TProtocol): java.util.Currency = java.util.Currency.getInstance(protocol.readString())

    def encode(value: java.util.Currency, protocol: TProtocol): Unit = protocol.writeString(value.getCurrencyCode)
  }

  val uuidCodec: ThriftBinaryCodec[UUID] = new ThriftBinaryCodec[java.util.UUID]() {
    def decodeUnsafe(protocol: TProtocol): java.util.UUID = java.util.UUID.fromString(protocol.readString())

    def encode(value: java.util.UUID, protocol: TProtocol): Unit = protocol.writeString(value.toString)
  }
}

/**
 * Internal error class for tracking the decode path during error propagation.
 */
private[thrift] class ThriftBinaryCodecError(var spans: List[DynamicOptic.Node], message: String)
    extends Throwable(message, null, false, false) {
  override def getMessage: String = message
}
