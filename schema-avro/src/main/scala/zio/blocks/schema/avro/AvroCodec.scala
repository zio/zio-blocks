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

package zio.blocks.schema.avro

import org.apache.avro.io.{BinaryDecoder, BinaryEncoder, DecoderFactory, DirectBinaryEncoder}
import org.apache.avro.{Schema => AvroSchema}
import zio.blocks.schema.SchemaError.ExpectationMismatch
import zio.blocks.schema.{DynamicOptic, SchemaError}
import zio.blocks.schema.codec.BinaryCodec
import java.io.OutputStream
import java.math.{BigInteger, MathContext}
import java.nio.ByteBuffer
import java.time._
import java.util.{Currency, UUID}
import scala.collection.immutable.ArraySeq
import scala.util.control.NonFatal

abstract class AvroCodec[A] extends BinaryCodec[A] {
  def avroSchema: AvroSchema

  def decodeValue(decoder: BinaryDecoder): A

  def encodeValue(value: A, encoder: BinaryEncoder): Unit

  def error(expectation: String): Nothing = throw new AvroCodecError(Nil, expectation)

  def error(span: DynamicOptic.Node, error: Throwable): Nothing = error match {
    case e: AvroCodecError =>
      e.spans = new ::(span, e.spans)
      throw e
    case _ =>
      throw new AvroCodecError(new ::(span, Nil), getMessage(error))
  }

  def error(span1: DynamicOptic.Node, span2: DynamicOptic.Node, error: Throwable): Nothing = error match {
    case e: AvroCodecError =>
      e.spans = new ::(span1, new ::(span2, e.spans))
      throw e
    case _ =>
      throw new AvroCodecError(new ::(span1, new ::(span2, Nil)), getMessage(error))
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
    try new Right(decodeValue(DecoderFactory.get().binaryDecoder(bs, pos, len, null)))
    catch {
      case error if NonFatal(error) => new Left(toError(error))
    }
  }

  override def encode(value: A, output: ByteBuffer): Unit = encode(
    value,
    new OutputStream {
      override def write(b: Int): Unit = output.put(b.toByte)

      override def write(bs: Array[Byte], off: Int, len: Int): Unit = output.put(bs, off, len)
    }
  )

  def decode(input: Array[Byte]): Either[SchemaError, A] =
    try new Right(decodeValue(DecoderFactory.get().binaryDecoder(input, 0, input.length, null)))
    catch {
      case error if NonFatal(error) => new Left(toError(error))
    }

  def encode(value: A): Array[Byte] = {
    val output = new ByteArrayOutputStream
    encode(value, output)
    output.toByteArray
  }

  def decode(input: java.io.InputStream): Either[SchemaError, A] =
    try new Right(decodeValue(DecoderFactory.get().directBinaryDecoder(input, null)))
    catch {
      case error if NonFatal(error) => new Left(toError(error))
    }

  def encode(value: A, output: java.io.OutputStream): Unit = encodeValue(value, new DirectBinaryEncoder(output) {})

  private[this] def toError(error: Throwable): SchemaError = new SchemaError(
    new ::(
      error match {
        case e: AvroCodecError =>
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
    case _                       =>
      var msg = error.getMessage
      if (msg eq null) msg = s"${error.getClass.getName}: (no message)"
      msg
  }
}

object AvroCodec {
  val maxCollectionSize: Int = Integer.MAX_VALUE - 8

  val unitCodec: AvroCodec[Unit] = new AvroCodec[Unit] {
    val avroSchema: AvroSchema = AvroSchema.create(AvroSchema.Type.NULL)

    def decodeValue(decoder: BinaryDecoder): Unit = ()

    def encodeValue(value: Unit, encoder: BinaryEncoder): Unit = ()
  }

  val booleanCodec: AvroCodec[Boolean] = new AvroCodec[Boolean] {
    val avroSchema: AvroSchema = AvroSchema.create(AvroSchema.Type.BOOLEAN)

    def decodeValue(decoder: BinaryDecoder): Boolean = decoder.readBoolean()

    def encodeValue(value: Boolean, encoder: BinaryEncoder): Unit = encoder.writeBoolean(value)
  }

  val byteCodec: AvroCodec[Byte] = new AvroCodec[Byte] {
    val avroSchema: AvroSchema = AvroSchema.create(AvroSchema.Type.INT)

    def decodeValue(decoder: BinaryDecoder): Byte = {
      val x = decoder.readInt()
      if (x >= Byte.MinValue && x <= Byte.MaxValue) x.toByte
      else error("Expected Byte")
    }

    def encodeValue(value: Byte, encoder: BinaryEncoder): Unit = encoder.writeInt(value)
  }

  val shortCodec: AvroCodec[Short] = new AvroCodec[Short] {
    val avroSchema: AvroSchema = byteCodec.avroSchema

    def decodeValue(decoder: BinaryDecoder): Short = {
      val x = decoder.readInt()
      if (x >= Short.MinValue && x <= Short.MaxValue) x.toShort
      else error("Expected Short")
    }

    def encodeValue(value: Short, encoder: BinaryEncoder): Unit = encoder.writeInt(value)
  }

  val intCodec: AvroCodec[Int] = new AvroCodec[Int] {
    val avroSchema: AvroSchema = shortCodec.avroSchema

    def decodeValue(decoder: BinaryDecoder): Int = decoder.readInt()

    def encodeValue(value: Int, encoder: BinaryEncoder): Unit = encoder.writeInt(value)
  }

  val longCodec: AvroCodec[Long] = new AvroCodec[Long] {
    val avroSchema: AvroSchema = AvroSchema.create(AvroSchema.Type.LONG)

    def decodeValue(decoder: BinaryDecoder): Long = decoder.readLong()

    def encodeValue(value: Long, encoder: BinaryEncoder): Unit = encoder.writeLong(value)
  }

  val floatCodec: AvroCodec[Float] = new AvroCodec[Float] {
    val avroSchema: AvroSchema = AvroSchema.create(AvroSchema.Type.FLOAT)

    def decodeValue(decoder: BinaryDecoder): Float = decoder.readFloat()

    def encodeValue(value: Float, encoder: BinaryEncoder): Unit = encoder.writeFloat(value)
  }

  val doubleCodec: AvroCodec[Double] = new AvroCodec[Double] {
    val avroSchema: AvroSchema = AvroSchema.create(AvroSchema.Type.DOUBLE)

    def decodeValue(decoder: BinaryDecoder): Double = decoder.readDouble()

    def encodeValue(value: Double, encoder: BinaryEncoder): Unit = encoder.writeDouble(value)
  }

  val charCodec: AvroCodec[Char] = new AvroCodec[Char] {
    val avroSchema: AvroSchema = intCodec.avroSchema

    def decodeValue(decoder: BinaryDecoder): Char = {
      val x = decoder.readInt()
      if (x >= Char.MinValue && x <= Char.MaxValue) x.toChar
      else error("Expected Char")
    }

    def encodeValue(value: Char, encoder: BinaryEncoder): Unit = encoder.writeInt(value)
  }

  val stringCodec: AvroCodec[String] = new AvroCodec[String] {
    val avroSchema: AvroSchema = AvroSchema.create(AvroSchema.Type.STRING)

    def decodeValue(decoder: BinaryDecoder): String = decoder.readString()

    def encodeValue(value: String, encoder: BinaryEncoder): Unit = encoder.writeString(value)
  }

  val bigIntCodec: AvroCodec[BigInt] = new AvroCodec[BigInt] {
    val avroSchema: AvroSchema = AvroSchema.create(AvroSchema.Type.BYTES)

    def decodeValue(decoder: BinaryDecoder): BigInt = BigInt(decoder.readBytes(null).array())

    def encodeValue(value: BigInt, encoder: BinaryEncoder): Unit = encoder.writeBytes(value.toByteArray)
  }

  val bigDecimalCodec: AvroCodec[BigDecimal] = new AvroCodec[BigDecimal] {
    val avroSchema: AvroSchema = {
      val intAvroSchema = intCodec.avroSchema
      val fields        = new java.util.ArrayList[AvroSchema.Field](4)
      fields.add(new AvroSchema.Field("mantissa", bigIntCodec.avroSchema))
      fields.add(new AvroSchema.Field("scale", intAvroSchema))
      fields.add(new AvroSchema.Field("precision", intAvroSchema))
      fields.add(new AvroSchema.Field("roundingMode", intAvroSchema))
      createAvroRecord("scala", "BigDecimal", fields)
    }

    def decodeValue(decoder: BinaryDecoder): BigDecimal = {
      val mantissa     = decoder.readBytes(null).array()
      val scale        = decoder.readInt()
      val precision    = decoder.readInt()
      val roundingMode = java.math.RoundingMode.valueOf(decoder.readInt())
      val mc           = new MathContext(precision, roundingMode)
      new BigDecimal(new java.math.BigDecimal(new BigInteger(mantissa), scale), mc)
    }

    def encodeValue(value: BigDecimal, encoder: BinaryEncoder): Unit = {
      val bd = value.underlying
      val mc = value.mc
      encoder.writeBytes(ByteBuffer.wrap(bd.unscaledValue.toByteArray))
      encoder.writeInt(bd.scale)
      encoder.writeInt(mc.getPrecision)
      encoder.writeInt(mc.getRoundingMode.ordinal)
    }
  }

  val dayOfWeekCodec: AvroCodec[DayOfWeek] = new AvroCodec[DayOfWeek] {
    val avroSchema: AvroSchema = intCodec.avroSchema

    def decodeValue(decoder: BinaryDecoder): DayOfWeek = DayOfWeek.of(decoder.readInt())

    def encodeValue(value: DayOfWeek, encoder: BinaryEncoder): Unit = encoder.writeInt(value.getValue)
  }

  val durationCodec: AvroCodec[Duration] = new AvroCodec[Duration] {
    val avroSchema: AvroSchema = {
      val fields = new java.util.ArrayList[AvroSchema.Field](2)
      fields.add(new AvroSchema.Field("seconds", longCodec.avroSchema))
      fields.add(new AvroSchema.Field("nano", intCodec.avroSchema))
      createAvroRecord("java.time", "Duration", fields)
    }

    def decodeValue(decoder: BinaryDecoder): Duration = Duration.ofSeconds(decoder.readLong(), decoder.readInt())

    def encodeValue(value: Duration, encoder: BinaryEncoder): Unit = {
      encoder.writeLong(value.getSeconds)
      encoder.writeInt(value.getNano)
    }
  }

  val instantCodec: AvroCodec[Instant] = new AvroCodec[Instant] {
    val avroSchema: AvroSchema = {
      val fields = new java.util.ArrayList[AvroSchema.Field](2)
      fields.add(new AvroSchema.Field("epochSecond", longCodec.avroSchema))
      fields.add(new AvroSchema.Field("nano", intCodec.avroSchema))
      createAvroRecord("java.time", "Instant", fields)
    }

    def decodeValue(decoder: BinaryDecoder): Instant = Instant.ofEpochSecond(decoder.readLong(), decoder.readInt())

    def encodeValue(value: Instant, encoder: BinaryEncoder): Unit = {
      encoder.writeLong(value.getEpochSecond)
      encoder.writeInt(value.getNano)
    }
  }

  val localDateCodec: AvroCodec[LocalDate] = new AvroCodec[LocalDate] {
    val avroSchema: AvroSchema = {
      val intAvroSchema = intCodec.avroSchema
      val fields        = new java.util.ArrayList[AvroSchema.Field](3)
      fields.add(new AvroSchema.Field("year", intAvroSchema))
      fields.add(new AvroSchema.Field("month", intAvroSchema))
      fields.add(new AvroSchema.Field("day", intAvroSchema))
      createAvroRecord("java.time", "LocalDate", fields)
    }

    def decodeValue(decoder: BinaryDecoder): LocalDate =
      LocalDate.of(decoder.readInt(), decoder.readInt(), decoder.readInt())

    def encodeValue(value: LocalDate, encoder: BinaryEncoder): Unit = {
      encoder.writeInt(value.getYear)
      encoder.writeInt(value.getMonthValue)
      encoder.writeInt(value.getDayOfMonth)
    }
  }

  val localDateTimeCodec: AvroCodec[LocalDateTime] = new AvroCodec[LocalDateTime] {
    val avroSchema: AvroSchema = {
      val intAvroSchema = intCodec.avroSchema
      val fields        = new java.util.ArrayList[AvroSchema.Field](7)
      fields.add(new AvroSchema.Field("year", intAvroSchema))
      fields.add(new AvroSchema.Field("month", intAvroSchema))
      fields.add(new AvroSchema.Field("day", intAvroSchema))
      fields.add(new AvroSchema.Field("hour", intAvroSchema))
      fields.add(new AvroSchema.Field("minute", intAvroSchema))
      fields.add(new AvroSchema.Field("second", intAvroSchema))
      fields.add(new AvroSchema.Field("nano", intAvroSchema))
      createAvroRecord("java.time", "LocalDateTime", fields)
    }

    def decodeValue(decoder: BinaryDecoder): LocalDateTime =
      LocalDateTime
        .of(
          decoder.readInt(),
          decoder.readInt(),
          decoder.readInt(),
          decoder.readInt(),
          decoder.readInt(),
          decoder.readInt(),
          decoder.readInt()
        )

    def encodeValue(value: LocalDateTime, encoder: BinaryEncoder): Unit = {
      encoder.writeInt(value.getYear)
      encoder.writeInt(value.getMonthValue)
      encoder.writeInt(value.getDayOfMonth)
      encoder.writeInt(value.getHour)
      encoder.writeInt(value.getMinute)
      encoder.writeInt(value.getSecond)
      encoder.writeInt(value.getNano)
    }
  }

  val localTimeCodec: AvroCodec[LocalTime] = new AvroCodec[LocalTime] {
    val avroSchema: AvroSchema = {
      val intAvroSchema = intCodec.avroSchema
      val fields        = new java.util.ArrayList[AvroSchema.Field](4)
      fields.add(new AvroSchema.Field("hour", intAvroSchema))
      fields.add(new AvroSchema.Field("minute", intAvroSchema))
      fields.add(new AvroSchema.Field("second", intAvroSchema))
      fields.add(new AvroSchema.Field("nano", intAvroSchema))
      createAvroRecord("java.time", "LocalTime", fields)
    }

    def decodeValue(decoder: BinaryDecoder): LocalTime =
      LocalTime.of(decoder.readInt(), decoder.readInt(), decoder.readInt(), decoder.readInt())

    def encodeValue(value: LocalTime, encoder: BinaryEncoder): Unit = {
      encoder.writeInt(value.getHour)
      encoder.writeInt(value.getMinute)
      encoder.writeInt(value.getSecond)
      encoder.writeInt(value.getNano)
    }
  }

  val monthCodec: AvroCodec[Month] = new AvroCodec[Month] {
    val avroSchema: AvroSchema = intCodec.avroSchema

    def decodeValue(decoder: BinaryDecoder): Month = Month.of(decoder.readInt())

    def encodeValue(value: Month, encoder: BinaryEncoder): Unit = encoder.writeInt(value.getValue)
  }

  val monthDayCodec: AvroCodec[MonthDay] = new AvroCodec[MonthDay] {
    val avroSchema: AvroSchema = {
      val intAvroSchema = intCodec.avroSchema
      val fields        = new java.util.ArrayList[AvroSchema.Field](2)
      fields.add(new AvroSchema.Field("month", intAvroSchema))
      fields.add(new AvroSchema.Field("day", intAvroSchema))
      createAvroRecord("java.time", "MonthDay", fields)
    }

    def decodeValue(decoder: BinaryDecoder): MonthDay = MonthDay.of(decoder.readInt(), decoder.readInt())

    def encodeValue(value: MonthDay, encoder: BinaryEncoder): Unit = {
      encoder.writeInt(value.getMonthValue)
      encoder.writeInt(value.getDayOfMonth)
    }
  }

  val offsetDateTimeCodec: AvroCodec[OffsetDateTime] = new AvroCodec[OffsetDateTime] {
    val avroSchema: AvroSchema = {
      val intAvroSchema = intCodec.avroSchema
      val fields        = new java.util.ArrayList[AvroSchema.Field](8)
      fields.add(new AvroSchema.Field("year", intAvroSchema))
      fields.add(new AvroSchema.Field("month", intAvroSchema))
      fields.add(new AvroSchema.Field("day", intAvroSchema))
      fields.add(new AvroSchema.Field("hour", intAvroSchema))
      fields.add(new AvroSchema.Field("minute", intAvroSchema))
      fields.add(new AvroSchema.Field("second", intAvroSchema))
      fields.add(new AvroSchema.Field("nano", intAvroSchema))
      fields.add(new AvroSchema.Field("offset", intAvroSchema))
      createAvroRecord("java.time", "OffsetDateTime", fields)
    }

    def decodeValue(decoder: BinaryDecoder): OffsetDateTime =
      OffsetDateTime.of(
        decoder.readInt(),
        decoder.readInt(),
        decoder.readInt(),
        decoder.readInt(),
        decoder.readInt(),
        decoder.readInt(),
        decoder.readInt(),
        ZoneOffset.ofTotalSeconds(decoder.readInt())
      )

    def encodeValue(value: OffsetDateTime, encoder: BinaryEncoder): Unit = {
      encoder.writeInt(value.getYear)
      encoder.writeInt(value.getMonthValue)
      encoder.writeInt(value.getDayOfMonth)
      encoder.writeInt(value.getHour)
      encoder.writeInt(value.getMinute)
      encoder.writeInt(value.getSecond)
      encoder.writeInt(value.getNano)
      encoder.writeInt(value.getOffset.getTotalSeconds)
    }
  }

  val offsetTimeCodec: AvroCodec[OffsetTime] = new AvroCodec[OffsetTime] {
    val avroSchema: AvroSchema = {
      val intAvroSchema = intCodec.avroSchema
      val fields        = new java.util.ArrayList[AvroSchema.Field](5)
      fields.add(new AvroSchema.Field("hour", intAvroSchema))
      fields.add(new AvroSchema.Field("minute", intAvroSchema))
      fields.add(new AvroSchema.Field("second", intAvroSchema))
      fields.add(new AvroSchema.Field("nano", intAvroSchema))
      fields.add(new AvroSchema.Field("offset", intAvroSchema))
      createAvroRecord("java.time", "OffsetTime", fields)
    }

    def decodeValue(decoder: BinaryDecoder): OffsetTime =
      OffsetTime.of(
        decoder.readInt(),
        decoder.readInt(),
        decoder.readInt(),
        decoder.readInt(),
        ZoneOffset.ofTotalSeconds(decoder.readInt())
      )

    def encodeValue(value: OffsetTime, encoder: BinaryEncoder): Unit = {
      encoder.writeInt(value.getHour)
      encoder.writeInt(value.getMinute)
      encoder.writeInt(value.getSecond)
      encoder.writeInt(value.getNano)
      encoder.writeInt(value.getOffset.getTotalSeconds)
    }
  }

  val periodCodec: AvroCodec[Period] = new AvroCodec[Period] {
    val avroSchema: AvroSchema = {
      val intAvroSchema = intCodec.avroSchema
      val fields        = new java.util.ArrayList[AvroSchema.Field](3)
      fields.add(new AvroSchema.Field("years", intAvroSchema))
      fields.add(new AvroSchema.Field("months", intAvroSchema))
      fields.add(new AvroSchema.Field("days", intAvroSchema))
      createAvroRecord("java.time", "Period", fields)
    }

    def decodeValue(decoder: BinaryDecoder): Period = Period.of(decoder.readInt(), decoder.readInt(), decoder.readInt())

    def encodeValue(value: Period, encoder: BinaryEncoder): Unit = {
      encoder.writeInt(value.getYears)
      encoder.writeInt(value.getMonths)
      encoder.writeInt(value.getDays)
    }
  }

  val yearCodec: AvroCodec[Year] = new AvroCodec[Year] {
    val avroSchema: AvroSchema = intCodec.avroSchema

    def decodeValue(decoder: BinaryDecoder): Year = Year.of(decoder.readInt())

    def encodeValue(value: Year, encoder: BinaryEncoder): Unit = encoder.writeInt(value.getValue)
  }

  val yearMonthCodec: AvroCodec[YearMonth] = new AvroCodec[YearMonth] {
    val avroSchema: AvroSchema = {
      val intAvroSchema = intCodec.avroSchema
      val fields        = new java.util.ArrayList[AvroSchema.Field](2)
      fields.add(new AvroSchema.Field("year", intAvroSchema))
      fields.add(new AvroSchema.Field("month", intAvroSchema))
      createAvroRecord("java.time", "YearMonth", fields)
    }

    def decodeValue(decoder: BinaryDecoder): YearMonth = YearMonth.of(decoder.readInt(), decoder.readInt())

    def encodeValue(value: YearMonth, encoder: BinaryEncoder): Unit = {
      encoder.writeInt(value.getYear)
      encoder.writeInt(value.getMonthValue)
    }
  }

  val zoneIdCodec: AvroCodec[ZoneId] = new AvroCodec[ZoneId] {
    val avroSchema: AvroSchema = stringCodec.avroSchema

    def decodeValue(decoder: BinaryDecoder): ZoneId = ZoneId.of(decoder.readString())

    def encodeValue(value: ZoneId, encoder: BinaryEncoder): Unit = encoder.writeString(value.toString)
  }

  val zoneOffsetCodec: AvroCodec[ZoneOffset] = new AvroCodec[ZoneOffset] {
    val avroSchema: AvroSchema = intCodec.avroSchema

    def decodeValue(decoder: BinaryDecoder): ZoneOffset = ZoneOffset.ofTotalSeconds(decoder.readInt())

    def encodeValue(value: ZoneOffset, encoder: BinaryEncoder): Unit = encoder.writeInt(value.getTotalSeconds)
  }

  val zonedDateTimeCodec: AvroCodec[ZonedDateTime] = new AvroCodec[ZonedDateTime] {
    val avroSchema: AvroSchema = {
      val intAvroSchema = intCodec.avroSchema
      val fields        = new java.util.ArrayList[AvroSchema.Field](9)
      fields.add(new AvroSchema.Field("year", intAvroSchema))
      fields.add(new AvroSchema.Field("month", intAvroSchema))
      fields.add(new AvroSchema.Field("day", intAvroSchema))
      fields.add(new AvroSchema.Field("hour", intAvroSchema))
      fields.add(new AvroSchema.Field("minute", intAvroSchema))
      fields.add(new AvroSchema.Field("second", intAvroSchema))
      fields.add(new AvroSchema.Field("nano", intAvroSchema))
      fields.add(new AvroSchema.Field("offset", intAvroSchema))
      fields.add(new AvroSchema.Field("zone", stringCodec.avroSchema))
      createAvroRecord("java.time", "ZonedDateTime", fields)
    }

    def decodeValue(decoder: BinaryDecoder): ZonedDateTime =
      ZonedDateTime.ofInstant(
        LocalDateTime.of(
          decoder.readInt(),
          decoder.readInt(),
          decoder.readInt(),
          decoder.readInt(),
          decoder.readInt(),
          decoder.readInt(),
          decoder.readInt()
        ),
        ZoneOffset.ofTotalSeconds(decoder.readInt()),
        ZoneId.of(decoder.readString())
      )

    def encodeValue(value: ZonedDateTime, encoder: BinaryEncoder): Unit = {
      encoder.writeInt(value.getYear)
      encoder.writeInt(value.getMonthValue)
      encoder.writeInt(value.getDayOfMonth)
      encoder.writeInt(value.getHour)
      encoder.writeInt(value.getMinute)
      encoder.writeInt(value.getSecond)
      encoder.writeInt(value.getNano)
      encoder.writeInt(value.getOffset.getTotalSeconds)
      encoder.writeString(value.getZone.toString)
    }
  }

  val currencyCodec: AvroCodec[Currency] = new AvroCodec[java.util.Currency] {
    val avroSchema: AvroSchema = AvroSchema.createFixed("Currency", null, "java.util", 3)

    def decodeValue(decoder: BinaryDecoder): java.util.Currency = {
      val bs = new Array[Byte](3)
      decoder.readFixed(bs, 0, 3)
      java.util.Currency.getInstance(new String(bs))
    }

    def encodeValue(value: java.util.Currency, encoder: BinaryEncoder): Unit = {
      val s = value.toString
      encoder.writeFixed(Array(s.charAt(0).toByte, s.charAt(1).toByte, s.charAt(2).toByte))
    }
  }

  val uuidCodec: AvroCodec[UUID] = new AvroCodec[java.util.UUID] {
    val avroSchema: AvroSchema = AvroSchema.createFixed("UUID", null, "java.util", 16)

    def decodeValue(decoder: BinaryDecoder): java.util.UUID = {
      val bs = new Array[Byte](16)
      decoder.readFixed(bs)
      val hi =
        (bs(0) & 0xff).toLong << 56 |
          (bs(1) & 0xff).toLong << 48 |
          (bs(2) & 0xff).toLong << 40 |
          (bs(3) & 0xff).toLong << 32 |
          (bs(4) & 0xff).toLong << 24 |
          (bs(5) & 0xff) << 16 |
          (bs(6) & 0xff) << 8 |
          (bs(7) & 0xff)
      val lo =
        (bs(8) & 0xff).toLong << 56 |
          (bs(9) & 0xff).toLong << 48 |
          (bs(10) & 0xff).toLong << 40 |
          (bs(11) & 0xff).toLong << 32 |
          (bs(12) & 0xff).toLong << 24 |
          (bs(13) & 0xff) << 16 |
          (bs(14) & 0xff) << 8 |
          (bs(15) & 0xff)
      new java.util.UUID(hi, lo)
    }

    def encodeValue(value: java.util.UUID, encoder: BinaryEncoder): Unit = {
      val hi = value.getMostSignificantBits
      val lo = value.getLeastSignificantBits
      val bs = Array(
        (hi >> 56).toByte,
        (hi >> 48).toByte,
        (hi >> 40).toByte,
        (hi >> 32).toByte,
        (hi >> 24).toByte,
        (hi >> 16).toByte,
        (hi >> 8).toByte,
        hi.toByte,
        (lo >> 56).toByte,
        (lo >> 48).toByte,
        (lo >> 40).toByte,
        (lo >> 32).toByte,
        (lo >> 24).toByte,
        (lo >> 16).toByte,
        (lo >> 8).toByte,
        lo.toByte
      )
      encoder.writeFixed(bs)
    }
  }

  private[this] def createAvroRecord(
    namespace: String,
    name: String,
    fields: java.util.ArrayList[AvroSchema.Field]
  ): AvroSchema = AvroSchema.createRecord(name, null, namespace, false, fields)
}

private class AvroCodecError(var spans: List[DynamicOptic.Node], message: String)
    extends Throwable(message, null, false, false) {
  override def getMessage: String = message
}

/**
 * Custom implementation replacing `java.io.ByteArrayOutputStream`.
 *
 * This class is used for performance optimization and to avoid unnecessary
 * overhead that occurs with the standard `ByteArrayOutputStream`. The buffer
 * growth strategy doubles the buffer size when more space is needed, or grows
 * to fit the required length if doubling is insufficient. This minimizes the
 * number of allocations and copies, especially for large or unpredictable
 * output sizes.
 */
private class ByteArrayOutputStream extends java.io.OutputStream {
  private[this] var buf   = new Array[Byte](64)
  private[this] var count = 0

  override def write(b: Int): Unit = {
    if (count >= buf.length) buf = java.util.Arrays.copyOf(buf, buf.length << 1)
    buf(count) = b.toByte
    count += 1
  }

  override def write(bs: Array[Byte], off: Int, len: Int): Unit = {
    val newLen = count + len
    if (newLen > buf.length) buf = java.util.Arrays.copyOf(buf, Math.max(buf.length << 1, newLen))
    System.arraycopy(bs, off, buf, count, len)
    count = newLen
  }

  def toByteArray: Array[Byte] = java.util.Arrays.copyOf(buf, count)
}
