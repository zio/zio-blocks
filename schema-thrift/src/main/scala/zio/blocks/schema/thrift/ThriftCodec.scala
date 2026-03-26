/*
 * Copyright 2024-2026 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zio.blocks.schema.thrift

import org.apache.thrift.protocol._
import org.apache.thrift.transport.TMemoryTransport
import zio.blocks.schema.{DynamicOptic, SchemaError}
import zio.blocks.schema.SchemaError.ExpectationMismatch
import zio.blocks.schema.codec.BinaryCodec
import zio.blocks.schema.json.Json
import java.nio.ByteBuffer
import java.time._
import java.util.{Currency, UUID}
import scala.collection.immutable.ArraySeq
import scala.util.control.NonFatal
import java.math.{BigInteger, MathContext, RoundingMode}

/**
 * Base class for Thrift binary codecs. Provides encoding and decoding of values
 * using Apache Thrift's TBinaryProtocol.
 *
 * @tparam A
 *   The type being encoded/decoded
 */
abstract class ThriftCodec[A] extends BinaryCodec[A] {

  /**
   * Decodes a value from the Thrift protocol. May throw on malformed input.
   */
  def decodeValue(protocol: TProtocol): A

  /**
   * Encodes a value to the Thrift protocol.
   */
  def encodeValue(value: A, protocol: TProtocol): Unit

  /**
   * Throws decode error with the given message.
   */
  def error(expectation: String): Nothing = throw new ThriftCodecError(Nil, expectation)

  /**
   * Throws decode error with a path span and underlying error.
   */
  def error(span: DynamicOptic.Node, error: Throwable): Nothing = error match {
    case e: ThriftCodecError =>
      e.spans = new ::(span, e.spans)
      throw e
    case _ =>
      throw new ThriftCodecError(new ::(span, Nil), getMessage(error))
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
      new Right(decodeValue(protocol))
    } catch {
      case error if NonFatal(error) => new Left(toError(error))
    }

  /**
   * Encodes to a byte array.
   */
  def encode(value: A): Array[Byte] = {
    val transport = new TMemoryTransport(Array.emptyByteArray)
    encodeValue(value, new TBinaryProtocol(transport))
    transport.getOutput.toByteArray
  }

  private[this] def toError(error: Throwable): SchemaError = new SchemaError(
    new ::(
      error match {
        case e: ThriftCodecError =>
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
    case _: java.io.EOFException         => "Unexpected end of input"
    case _: org.apache.thrift.TException => s"Thrift protocol error: ${error.getMessage}"
    case _                               =>
      var msg = error.getMessage
      if (msg eq null) msg = s"${error.getClass.getName}: (no message)"
      msg
  }
}

object ThriftCodec {
  val maxCollectionSize: Int = Integer.MAX_VALUE - 8

  val unitCodec: ThriftCodec[Unit] = new ThriftCodec[Unit] {
    def decodeValue(protocol: TProtocol): Unit = ()

    def encodeValue(value: Unit, protocol: TProtocol): Unit = ()
  }

  val booleanCodec: ThriftCodec[Boolean] = new ThriftCodec[Boolean] {
    def decodeValue(protocol: TProtocol): Boolean = protocol.readBool()

    def encodeValue(value: Boolean, protocol: TProtocol): Unit = protocol.writeBool(value)
  }

  val byteCodec: ThriftCodec[Byte] = new ThriftCodec[Byte] {
    def decodeValue(protocol: TProtocol): Byte = protocol.readByte()

    def encodeValue(value: Byte, protocol: TProtocol): Unit = protocol.writeByte(value)
  }

  val shortCodec: ThriftCodec[Short] = new ThriftCodec[Short] {
    def decodeValue(protocol: TProtocol): Short = protocol.readI16()

    def encodeValue(value: Short, protocol: TProtocol): Unit = protocol.writeI16(value)
  }

  val intCodec: ThriftCodec[Int] = new ThriftCodec[Int] {
    def decodeValue(protocol: TProtocol): Int = protocol.readI32()

    def encodeValue(value: Int, protocol: TProtocol): Unit = protocol.writeI32(value)
  }

  val longCodec: ThriftCodec[Long] = new ThriftCodec[Long] {
    def decodeValue(protocol: TProtocol): Long = protocol.readI64()

    def encodeValue(value: Long, protocol: TProtocol): Unit = protocol.writeI64(value)
  }

  val floatCodec: ThriftCodec[Float] = new ThriftCodec[Float] {
    def decodeValue(protocol: TProtocol): Float = protocol.readDouble().toFloat

    def encodeValue(value: Float, protocol: TProtocol): Unit = protocol.writeDouble(value.toDouble)
  }

  val doubleCodec: ThriftCodec[Double] = new ThriftCodec[Double] {
    def decodeValue(protocol: TProtocol): Double = protocol.readDouble()

    def encodeValue(value: Double, protocol: TProtocol): Unit = protocol.writeDouble(value)
  }

  val charCodec: ThriftCodec[Char] = new ThriftCodec[Char] {
    def decodeValue(protocol: TProtocol): Char = protocol.readI16().toChar

    def encodeValue(value: Char, protocol: TProtocol): Unit = protocol.writeI16(value.toShort)
  }

  val stringCodec: ThriftCodec[String] = new ThriftCodec[String] {
    def decodeValue(protocol: TProtocol): String = protocol.readString()

    def encodeValue(value: String, protocol: TProtocol): Unit = protocol.writeString(value)
  }

  val bigIntCodec: ThriftCodec[BigInt] = new ThriftCodec[BigInt] {
    def decodeValue(protocol: TProtocol): BigInt = {
      val buf = protocol.readBinary()
      val arr = new Array[Byte](buf.remaining())
      buf.get(arr)
      BigInt(new BigInteger(arr))
    }

    def encodeValue(value: BigInt, protocol: TProtocol): Unit = protocol.writeBinary(ByteBuffer.wrap(value.toByteArray))
  }

  val bigDecimalCodec: ThriftCodec[BigDecimal] = new ThriftCodec[BigDecimal] {
    private[this] val mantissaField     = new TField("mantissa", TType.STRING, 1)
    private[this] val scaleField        = new TField("scale", TType.I32, 2)
    private[this] val precisionField    = new TField("precision", TType.I32, 3)
    private[this] val roundingModeField = new TField("roundingMode", TType.BYTE, 4)

    def decodeValue(protocol: TProtocol): BigDecimal = {
      protocol.readFieldBegin()
      val mantissaBuf = protocol.readBinary()
      val mantissaArr = new Array[Byte](mantissaBuf.remaining())
      mantissaBuf.get(mantissaArr)
      val mantissa = new BigInteger(mantissaArr)
      protocol.readFieldBegin()
      val scale = protocol.readI32()
      protocol.readFieldBegin()
      val precision = protocol.readI32()
      protocol.readFieldBegin()
      val roundingMode = protocol.readByte()
      protocol.readFieldBegin() // read STOP
      new BigDecimal(
        new java.math.BigDecimal(mantissa, scale, new MathContext(precision, RoundingMode.valueOf(roundingMode)))
      )
    }

    def encodeValue(value: BigDecimal, protocol: TProtocol): Unit = {
      val bd = value.underlying()
      val mc = value.mc
      protocol.writeFieldBegin(mantissaField)
      protocol.writeBinary(ByteBuffer.wrap(bd.unscaledValue().toByteArray))
      protocol.writeFieldBegin(scaleField)
      protocol.writeI32(bd.scale())
      protocol.writeFieldBegin(precisionField)
      protocol.writeI32(mc.getPrecision)
      protocol.writeFieldBegin(roundingModeField)
      protocol.writeByte(mc.getRoundingMode.ordinal.toByte)
      protocol.writeFieldStop()
    }
  }

  val dayOfWeekCodec: ThriftCodec[DayOfWeek] = new ThriftCodec[DayOfWeek] {
    def decodeValue(protocol: TProtocol): DayOfWeek = DayOfWeek.of(protocol.readByte().toInt)

    def encodeValue(value: DayOfWeek, protocol: TProtocol): Unit = protocol.writeByte(value.getValue.toByte)
  }

  val durationCodec: ThriftCodec[Duration] = new ThriftCodec[Duration] {
    def decodeValue(protocol: TProtocol): Duration =
      Json.durationRawCodec.decode(protocol.readString()) match {
        case Right(v)  => v
        case Left(err) => throw err
      }

    def encodeValue(value: Duration, protocol: TProtocol): Unit =
      protocol.writeString(Json.durationRawCodec.encodeToString(value))
  }

  val instantCodec: ThriftCodec[Instant] = new ThriftCodec[Instant] {
    def decodeValue(protocol: TProtocol): Instant =
      Json.instantRawCodec.decodeUnsafe(protocol.readString())

    def encodeValue(value: Instant, protocol: TProtocol): Unit =
      protocol.writeString(Json.instantRawCodec.encodeToString(value))
  }

  val localDateCodec: ThriftCodec[LocalDate] = new ThriftCodec[LocalDate] {
    def decodeValue(protocol: TProtocol): LocalDate =
      Json.localDateRawCodec.decodeUnsafe(protocol.readString())

    def encodeValue(value: LocalDate, protocol: TProtocol): Unit =
      protocol.writeString(Json.localDateRawCodec.encodeToString(value))
  }

  val localTimeCodec: ThriftCodec[LocalTime] = new ThriftCodec[LocalTime] {
    def decodeValue(protocol: TProtocol): LocalTime =
      Json.localTimeRawCodec.decodeUnsafe(protocol.readString())

    def encodeValue(value: LocalTime, protocol: TProtocol): Unit =
      protocol.writeString(Json.localTimeRawCodec.encodeToString(value))
  }

  val localDateTimeCodec: ThriftCodec[LocalDateTime] = new ThriftCodec[LocalDateTime] {
    def decodeValue(protocol: TProtocol): LocalDateTime =
      Json.localDateTimeRawCodec.decodeUnsafe(protocol.readString())

    def encodeValue(value: LocalDateTime, protocol: TProtocol): Unit =
      protocol.writeString(Json.localDateTimeRawCodec.encodeToString(value))
  }

  val monthCodec: ThriftCodec[Month] = new ThriftCodec[Month] {
    def decodeValue(protocol: TProtocol): Month = Month.of(protocol.readByte().toInt)

    def encodeValue(value: Month, protocol: TProtocol): Unit = protocol.writeByte(value.getValue.toByte)
  }

  val monthDayCodec: ThriftCodec[MonthDay] = new ThriftCodec[MonthDay] {
    def decodeValue(protocol: TProtocol): MonthDay =
      Json.monthDayRawCodec.decodeUnsafe(protocol.readString())

    def encodeValue(value: MonthDay, protocol: TProtocol): Unit =
      protocol.writeString(Json.monthDayRawCodec.encodeToString(value))
  }

  val offsetTimeCodec: ThriftCodec[OffsetTime] = new ThriftCodec[OffsetTime] {
    def decodeValue(protocol: TProtocol): OffsetTime =
      Json.offsetTimeRawCodec.decodeUnsafe(protocol.readString())

    def encodeValue(value: OffsetTime, protocol: TProtocol): Unit =
      protocol.writeString(Json.offsetTimeRawCodec.encodeToString(value))
  }

  val offsetDateTimeCodec: ThriftCodec[OffsetDateTime] = new ThriftCodec[OffsetDateTime] {
    def decodeValue(protocol: TProtocol): OffsetDateTime =
      Json.offsetDateTimeRawCodec.decodeUnsafe(protocol.readString())

    def encodeValue(value: OffsetDateTime, protocol: TProtocol): Unit =
      protocol.writeString(Json.offsetDateTimeRawCodec.encodeToString(value))
  }

  val periodCodec: ThriftCodec[Period] = new ThriftCodec[Period] {
    def decodeValue(protocol: TProtocol): Period =
      Json.periodRawCodec.decodeUnsafe(protocol.readString())

    def encodeValue(value: Period, protocol: TProtocol): Unit =
      protocol.writeString(Json.periodRawCodec.encodeToString(value))
  }

  val yearCodec: ThriftCodec[Year] = new ThriftCodec[Year] {
    def decodeValue(protocol: TProtocol): Year = Year.of(protocol.readI32())

    def encodeValue(value: Year, protocol: TProtocol): Unit = protocol.writeI32(value.getValue)
  }

  val yearMonthCodec: ThriftCodec[YearMonth] = new ThriftCodec[YearMonth] {
    def decodeValue(protocol: TProtocol): YearMonth = YearMonth.parse(protocol.readString())

    def encodeValue(value: YearMonth, protocol: TProtocol): Unit = protocol.writeString(value.toString)
  }

  val zoneIdCodec: ThriftCodec[ZoneId] = new ThriftCodec[ZoneId] {
    def decodeValue(protocol: TProtocol): ZoneId = ZoneId.of(protocol.readString())

    def encodeValue(value: ZoneId, protocol: TProtocol): Unit = protocol.writeString(value.getId)
  }

  val zoneOffsetCodec: ThriftCodec[ZoneOffset] = new ThriftCodec[ZoneOffset] {
    def decodeValue(protocol: TProtocol): ZoneOffset = ZoneOffset.ofTotalSeconds(protocol.readI32())

    def encodeValue(value: ZoneOffset, protocol: TProtocol): Unit = protocol.writeI32(value.getTotalSeconds)
  }

  val zonedDateTimeCodec: ThriftCodec[ZonedDateTime] = new ThriftCodec[ZonedDateTime] {
    def decodeValue(protocol: TProtocol): ZonedDateTime =
      Json.zonedDateTimeRawCodec.decodeUnsafe(protocol.readString())

    def encodeValue(value: ZonedDateTime, protocol: TProtocol): Unit =
      protocol.writeString(Json.zonedDateTimeRawCodec.encodeToString(value))
  }

  val currencyCodec: ThriftCodec[Currency] = new ThriftCodec[java.util.Currency] {
    def decodeValue(protocol: TProtocol): java.util.Currency = java.util.Currency.getInstance(protocol.readString())

    def encodeValue(value: java.util.Currency, protocol: TProtocol): Unit = protocol.writeString(value.getCurrencyCode)
  }

  val uuidCodec: ThriftCodec[UUID] = new ThriftCodec[java.util.UUID] {
    def decodeValue(protocol: TProtocol): java.util.UUID = java.util.UUID.fromString(protocol.readString())

    def encodeValue(value: java.util.UUID, protocol: TProtocol): Unit = protocol.writeString(value.toString)
  }
}

/**
 * Internal error class for tracking the path during error propagation.
 */
private class ThriftCodecError(var spans: List[DynamicOptic.Node], message: String)
    extends Throwable(message, null, false, false) {
  override def getMessage: String = message
}
