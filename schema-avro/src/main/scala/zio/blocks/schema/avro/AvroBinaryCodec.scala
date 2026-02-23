package zio.blocks.schema.avro

import org.apache.avro.io.{BinaryDecoder, BinaryEncoder, DecoderFactory, DirectBinaryEncoder}
import org.apache.avro.{Schema => AvroSchema}
import zio.blocks.schema.SchemaError.ExpectationMismatch
import zio.blocks.schema.{DynamicOptic, SchemaError}
import zio.blocks.schema.binding.RegisterOffset
import zio.blocks.schema.codec.BinaryCodec
import java.io.OutputStream
import java.math.{BigInteger, MathContext}
import java.nio.ByteBuffer
import java.time._
import java.util.{Currency, UUID}
import scala.collection.immutable.ArraySeq
import scala.util.control.NonFatal

abstract class AvroBinaryCodec[A](val valueType: Int = AvroBinaryCodec.objectType) extends BinaryCodec[A] {
  val valueOffset: RegisterOffset.RegisterOffset = valueType match {
    case AvroBinaryCodec.objectType  => RegisterOffset(objects = 1)
    case AvroBinaryCodec.booleanType => RegisterOffset(booleans = 1)
    case AvroBinaryCodec.byteType    => RegisterOffset(bytes = 1)
    case AvroBinaryCodec.charType    => RegisterOffset(chars = 1)
    case AvroBinaryCodec.shortType   => RegisterOffset(shorts = 1)
    case AvroBinaryCodec.floatType   => RegisterOffset(floats = 1)
    case AvroBinaryCodec.intType     => RegisterOffset(ints = 1)
    case AvroBinaryCodec.doubleType  => RegisterOffset(doubles = 1)
    case AvroBinaryCodec.longType    => RegisterOffset(longs = 1)
    case _                           => RegisterOffset.Zero
  }

  def avroSchema: AvroSchema

  def decodeError(expectation: String): Nothing = throw new AvroBinaryCodecError(Nil, expectation)

  def decodeError(span: DynamicOptic.Node, error: Throwable): Nothing = error match {
    case e: AvroBinaryCodecError =>
      e.spans = new ::(span, e.spans)
      throw e
    case _ =>
      throw new AvroBinaryCodecError(new ::(span, Nil), getMessage(error))
  }

  def decodeError(span1: DynamicOptic.Node, span2: DynamicOptic.Node, error: Throwable): Nothing = error match {
    case e: AvroBinaryCodecError =>
      e.spans = new ::(span1, new ::(span2, e.spans))
      throw e
    case _ =>
      throw new AvroBinaryCodecError(new ::(span1, new ::(span2, Nil)), getMessage(error))
  }

  def decodeUnsafe(decoder: BinaryDecoder): A

  def encode(value: A, encoder: BinaryEncoder): Unit

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
    decode(DecoderFactory.get().binaryDecoder(bs, pos, len, null))
  }

  override def encode(value: A, output: ByteBuffer): Unit = encode(
    value,
    new OutputStream {
      override def write(b: Int): Unit = output.put(b.toByte)

      override def write(bs: Array[Byte], off: Int, len: Int): Unit = output.put(bs, off, len)
    }
  )

  def decode(input: Array[Byte]): Either[SchemaError, A] =
    decode(DecoderFactory.get().binaryDecoder(input, 0, input.length, null))

  def encode(value: A): Array[Byte] = {
    val output = new ByteArrayOutputStream
    encode(value, output)
    output.toByteArray
  }

  def decode(input: java.io.InputStream): Either[SchemaError, A] =
    decode(DecoderFactory.get().directBinaryDecoder(input, null))

  def encode(value: A, output: java.io.OutputStream): Unit = encode(value, new DirectBinaryEncoder(output) {})

  private[this] def decode(decoder: BinaryDecoder): Either[SchemaError, A] =
    try new Right(decodeUnsafe(decoder))
    catch {
      case error if NonFatal(error) => new Left(toError(error))
    }

  private[this] def toError(error: Throwable): SchemaError = new SchemaError(
    new ::(
      error match {
        case e: AvroBinaryCodecError =>
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

object AvroBinaryCodec {
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

  val unitCodec: AvroBinaryCodec[Unit] = new AvroBinaryCodec[Unit](AvroBinaryCodec.unitType) {
    val avroSchema: AvroSchema = AvroSchema.create(AvroSchema.Type.NULL)

    def decodeUnsafe(decoder: BinaryDecoder): Unit = ()

    def encode(value: Unit, encoder: BinaryEncoder): Unit = ()
  }

  val booleanCodec: AvroBinaryCodec[Boolean] = new AvroBinaryCodec[Boolean](AvroBinaryCodec.booleanType) {
    val avroSchema: AvroSchema = AvroSchema.create(AvroSchema.Type.BOOLEAN)

    def decodeUnsafe(decoder: BinaryDecoder): Boolean = decoder.readBoolean()

    def encode(value: Boolean, encoder: BinaryEncoder): Unit = encoder.writeBoolean(value)
  }

  val byteCodec: AvroBinaryCodec[Byte] = new AvroBinaryCodec[Byte](AvroBinaryCodec.byteType) {
    val avroSchema: AvroSchema = AvroSchema.create(AvroSchema.Type.INT)

    def decodeUnsafe(decoder: BinaryDecoder): Byte = {
      val x = decoder.readInt()
      if (x >= Byte.MinValue && x <= Byte.MaxValue) x.toByte
      else decodeError("Expected Byte")
    }

    def encode(value: Byte, encoder: BinaryEncoder): Unit = encoder.writeInt(value)
  }

  val shortCodec: AvroBinaryCodec[Short] = new AvroBinaryCodec[Short](AvroBinaryCodec.shortType) {
    val avroSchema: AvroSchema = byteCodec.avroSchema

    def decodeUnsafe(decoder: BinaryDecoder): Short = {
      val x = decoder.readInt()
      if (x >= Short.MinValue && x <= Short.MaxValue) x.toShort
      else decodeError("Expected Short")
    }

    def encode(value: Short, encoder: BinaryEncoder): Unit = encoder.writeInt(value)
  }

  val intCodec: AvroBinaryCodec[Int] = new AvroBinaryCodec[Int](AvroBinaryCodec.intType) {
    val avroSchema: AvroSchema = shortCodec.avroSchema

    def decodeUnsafe(decoder: BinaryDecoder): Int = decoder.readInt()

    def encode(value: Int, encoder: BinaryEncoder): Unit = encoder.writeInt(value)
  }

  val longCodec: AvroBinaryCodec[Long] = new AvroBinaryCodec[Long](AvroBinaryCodec.longType) {
    val avroSchema: AvroSchema = AvroSchema.create(AvroSchema.Type.LONG)

    def decodeUnsafe(decoder: BinaryDecoder): Long = decoder.readLong()

    def encode(value: Long, encoder: BinaryEncoder): Unit = encoder.writeLong(value)
  }

  val floatCodec: AvroBinaryCodec[Float] = new AvroBinaryCodec[Float](AvroBinaryCodec.floatType) {
    val avroSchema: AvroSchema = AvroSchema.create(AvroSchema.Type.FLOAT)

    def decodeUnsafe(decoder: BinaryDecoder): Float = decoder.readFloat()

    def encode(value: Float, encoder: BinaryEncoder): Unit = encoder.writeFloat(value)
  }

  val doubleCodec: AvroBinaryCodec[Double] = new AvroBinaryCodec[Double](AvroBinaryCodec.doubleType) {
    val avroSchema: AvroSchema = AvroSchema.create(AvroSchema.Type.DOUBLE)

    def decodeUnsafe(decoder: BinaryDecoder): Double = decoder.readDouble()

    def encode(value: Double, encoder: BinaryEncoder): Unit = encoder.writeDouble(value)
  }

  val charCodec: AvroBinaryCodec[Char] = new AvroBinaryCodec[Char](AvroBinaryCodec.charType) {
    val avroSchema: AvroSchema = intCodec.avroSchema

    def decodeUnsafe(decoder: BinaryDecoder): Char = {
      val x = decoder.readInt()
      if (x >= Char.MinValue && x <= Char.MaxValue) x.toChar
      else decodeError("Expected Char")
    }

    def encode(value: Char, encoder: BinaryEncoder): Unit = encoder.writeInt(value)
  }

  val stringCodec: AvroBinaryCodec[String] = new AvroBinaryCodec[String]() {
    val avroSchema: AvroSchema = AvroSchema.create(AvroSchema.Type.STRING)

    def decodeUnsafe(decoder: BinaryDecoder): String = decoder.readString()

    def encode(value: String, encoder: BinaryEncoder): Unit = encoder.writeString(value)
  }

  val bigIntCodec: AvroBinaryCodec[BigInt] = new AvroBinaryCodec[BigInt]() {
    val avroSchema: AvroSchema = AvroSchema.create(AvroSchema.Type.BYTES)

    def decodeUnsafe(decoder: BinaryDecoder): BigInt = BigInt(decoder.readBytes(null).array())

    def encode(value: BigInt, encoder: BinaryEncoder): Unit = encoder.writeBytes(value.toByteArray)
  }

  val bigDecimalCodec: AvroBinaryCodec[BigDecimal] = new AvroBinaryCodec[BigDecimal]() {
    val avroSchema: AvroSchema = {
      val intAvroSchema = intCodec.avroSchema
      val fields        = new java.util.ArrayList[AvroSchema.Field](4)
      fields.add(new AvroSchema.Field("mantissa", bigIntCodec.avroSchema))
      fields.add(new AvroSchema.Field("scale", intAvroSchema))
      fields.add(new AvroSchema.Field("precision", intAvroSchema))
      fields.add(new AvroSchema.Field("roundingMode", intAvroSchema))
      createAvroRecord("scala", "BigDecimal", fields)
    }

    def decodeUnsafe(decoder: BinaryDecoder): BigDecimal = {
      val mantissa     = decoder.readBytes(null).array()
      val scale        = decoder.readInt()
      val precision    = decoder.readInt()
      val roundingMode = java.math.RoundingMode.valueOf(decoder.readInt())
      val mc           = new MathContext(precision, roundingMode)
      new BigDecimal(new java.math.BigDecimal(new BigInteger(mantissa), scale), mc)
    }

    def encode(value: BigDecimal, encoder: BinaryEncoder): Unit = {
      val bd = value.underlying
      val mc = value.mc
      encoder.writeBytes(ByteBuffer.wrap(bd.unscaledValue.toByteArray))
      encoder.writeInt(bd.scale)
      encoder.writeInt(mc.getPrecision)
      encoder.writeInt(mc.getRoundingMode.ordinal)
    }
  }

  val dayOfWeekCodec: AvroBinaryCodec[DayOfWeek] = new AvroBinaryCodec[DayOfWeek]() {
    val avroSchema: AvroSchema = intCodec.avroSchema

    def decodeUnsafe(decoder: BinaryDecoder): DayOfWeek = DayOfWeek.of(decoder.readInt())

    def encode(value: DayOfWeek, encoder: BinaryEncoder): Unit = encoder.writeInt(value.getValue)
  }

  val durationCodec: AvroBinaryCodec[Duration] = new AvroBinaryCodec[Duration]() {
    val avroSchema: AvroSchema = {
      val fields = new java.util.ArrayList[AvroSchema.Field](2)
      fields.add(new AvroSchema.Field("seconds", longCodec.avroSchema))
      fields.add(new AvroSchema.Field("nano", intCodec.avroSchema))
      createAvroRecord("java.time", "Duration", fields)
    }

    def decodeUnsafe(decoder: BinaryDecoder): Duration = Duration.ofSeconds(decoder.readLong(), decoder.readInt())

    def encode(value: Duration, encoder: BinaryEncoder): Unit = {
      encoder.writeLong(value.getSeconds)
      encoder.writeInt(value.getNano)
    }
  }

  val instantCodec: AvroBinaryCodec[Instant] = new AvroBinaryCodec[Instant]() {
    val avroSchema: AvroSchema = {
      val fields = new java.util.ArrayList[AvroSchema.Field](2)
      fields.add(new AvroSchema.Field("epochSecond", longCodec.avroSchema))
      fields.add(new AvroSchema.Field("nano", intCodec.avroSchema))
      createAvroRecord("java.time", "Instant", fields)
    }

    def decodeUnsafe(decoder: BinaryDecoder): Instant = Instant.ofEpochSecond(decoder.readLong(), decoder.readInt())

    def encode(value: Instant, encoder: BinaryEncoder): Unit = {
      encoder.writeLong(value.getEpochSecond)
      encoder.writeInt(value.getNano)
    }
  }

  val localDateCodec: AvroBinaryCodec[LocalDate] = new AvroBinaryCodec[LocalDate]() {
    val avroSchema: AvroSchema = {
      val intAvroSchema = intCodec.avroSchema
      val fields        = new java.util.ArrayList[AvroSchema.Field](3)
      fields.add(new AvroSchema.Field("year", intAvroSchema))
      fields.add(new AvroSchema.Field("month", intAvroSchema))
      fields.add(new AvroSchema.Field("day", intAvroSchema))
      createAvroRecord("java.time", "LocalDate", fields)
    }

    def decodeUnsafe(decoder: BinaryDecoder): LocalDate =
      LocalDate.of(decoder.readInt(), decoder.readInt(), decoder.readInt())

    def encode(value: LocalDate, encoder: BinaryEncoder): Unit = {
      encoder.writeInt(value.getYear)
      encoder.writeInt(value.getMonthValue)
      encoder.writeInt(value.getDayOfMonth)
    }
  }

  val localDateTimeCodec: AvroBinaryCodec[LocalDateTime] = new AvroBinaryCodec[LocalDateTime]() {
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

    def decodeUnsafe(decoder: BinaryDecoder): LocalDateTime =
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

    def encode(value: LocalDateTime, encoder: BinaryEncoder): Unit = {
      encoder.writeInt(value.getYear)
      encoder.writeInt(value.getMonthValue)
      encoder.writeInt(value.getDayOfMonth)
      encoder.writeInt(value.getHour)
      encoder.writeInt(value.getMinute)
      encoder.writeInt(value.getSecond)
      encoder.writeInt(value.getNano)
    }
  }

  val localTimeCodec: AvroBinaryCodec[LocalTime] = new AvroBinaryCodec[LocalTime]() {
    val avroSchema: AvroSchema = {
      val intAvroSchema = intCodec.avroSchema
      val fields        = new java.util.ArrayList[AvroSchema.Field](4)
      fields.add(new AvroSchema.Field("hour", intAvroSchema))
      fields.add(new AvroSchema.Field("minute", intAvroSchema))
      fields.add(new AvroSchema.Field("second", intAvroSchema))
      fields.add(new AvroSchema.Field("nano", intAvroSchema))
      createAvroRecord("java.time", "LocalTime", fields)
    }

    def decodeUnsafe(decoder: BinaryDecoder): LocalTime =
      LocalTime.of(decoder.readInt(), decoder.readInt(), decoder.readInt(), decoder.readInt())

    def encode(value: LocalTime, encoder: BinaryEncoder): Unit = {
      encoder.writeInt(value.getHour)
      encoder.writeInt(value.getMinute)
      encoder.writeInt(value.getSecond)
      encoder.writeInt(value.getNano)
    }
  }

  val monthCodec: AvroBinaryCodec[Month] = new AvroBinaryCodec[Month]() {
    val avroSchema: AvroSchema = intCodec.avroSchema

    def decodeUnsafe(decoder: BinaryDecoder): Month = Month.of(decoder.readInt())

    def encode(value: Month, encoder: BinaryEncoder): Unit = encoder.writeInt(value.getValue)
  }

  val monthDayCodec: AvroBinaryCodec[MonthDay] = new AvroBinaryCodec[MonthDay]() {
    val avroSchema: AvroSchema = {
      val intAvroSchema = intCodec.avroSchema
      val fields        = new java.util.ArrayList[AvroSchema.Field](2)
      fields.add(new AvroSchema.Field("month", intAvroSchema))
      fields.add(new AvroSchema.Field("day", intAvroSchema))
      createAvroRecord("java.time", "MonthDay", fields)
    }

    def decodeUnsafe(decoder: BinaryDecoder): MonthDay = MonthDay.of(decoder.readInt(), decoder.readInt())

    def encode(value: MonthDay, encoder: BinaryEncoder): Unit = {
      encoder.writeInt(value.getMonthValue)
      encoder.writeInt(value.getDayOfMonth)
    }
  }

  val offsetDateTimeCodec: AvroBinaryCodec[OffsetDateTime] = new AvroBinaryCodec[OffsetDateTime]() {
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

    def decodeUnsafe(decoder: BinaryDecoder): OffsetDateTime =
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

    def encode(value: OffsetDateTime, encoder: BinaryEncoder): Unit = {
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

  val offsetTimeCodec: AvroBinaryCodec[OffsetTime] = new AvroBinaryCodec[OffsetTime]() {
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

    def decodeUnsafe(decoder: BinaryDecoder): OffsetTime =
      OffsetTime.of(
        decoder.readInt(),
        decoder.readInt(),
        decoder.readInt(),
        decoder.readInt(),
        ZoneOffset.ofTotalSeconds(decoder.readInt())
      )

    def encode(value: OffsetTime, encoder: BinaryEncoder): Unit = {
      encoder.writeInt(value.getHour)
      encoder.writeInt(value.getMinute)
      encoder.writeInt(value.getSecond)
      encoder.writeInt(value.getNano)
      encoder.writeInt(value.getOffset.getTotalSeconds)
    }
  }

  val periodCodec: AvroBinaryCodec[Period] = new AvroBinaryCodec[Period]() {
    val avroSchema: AvroSchema = {
      val intAvroSchema = intCodec.avroSchema
      val fields        = new java.util.ArrayList[AvroSchema.Field](3)
      fields.add(new AvroSchema.Field("years", intAvroSchema))
      fields.add(new AvroSchema.Field("months", intAvroSchema))
      fields.add(new AvroSchema.Field("days", intAvroSchema))
      createAvroRecord("java.time", "Period", fields)
    }

    def decodeUnsafe(decoder: BinaryDecoder): Period =
      Period.of(decoder.readInt(), decoder.readInt(), decoder.readInt())

    def encode(value: Period, encoder: BinaryEncoder): Unit = {
      encoder.writeInt(value.getYears)
      encoder.writeInt(value.getMonths)
      encoder.writeInt(value.getDays)
    }
  }

  val yearCodec: AvroBinaryCodec[Year] = new AvroBinaryCodec[Year]() {
    val avroSchema: AvroSchema = intCodec.avroSchema

    def decodeUnsafe(decoder: BinaryDecoder): Year = Year.of(decoder.readInt())

    def encode(value: Year, encoder: BinaryEncoder): Unit = encoder.writeInt(value.getValue)
  }

  val yearMonthCodec: AvroBinaryCodec[YearMonth] = new AvroBinaryCodec[YearMonth]() {
    val avroSchema: AvroSchema = {
      val intAvroSchema = intCodec.avroSchema
      val fields        = new java.util.ArrayList[AvroSchema.Field](2)
      fields.add(new AvroSchema.Field("year", intAvroSchema))
      fields.add(new AvroSchema.Field("month", intAvroSchema))
      createAvroRecord("java.time", "YearMonth", fields)
    }

    def decodeUnsafe(decoder: BinaryDecoder): YearMonth = YearMonth.of(decoder.readInt(), decoder.readInt())

    def encode(value: YearMonth, encoder: BinaryEncoder): Unit = {
      encoder.writeInt(value.getYear)
      encoder.writeInt(value.getMonthValue)
    }
  }

  val zoneIdCodec: AvroBinaryCodec[ZoneId] = new AvroBinaryCodec[ZoneId]() {
    val avroSchema: AvroSchema = stringCodec.avroSchema

    def decodeUnsafe(decoder: BinaryDecoder): ZoneId = ZoneId.of(decoder.readString())

    def encode(value: ZoneId, encoder: BinaryEncoder): Unit = encoder.writeString(value.toString)
  }

  val zoneOffsetCodec: AvroBinaryCodec[ZoneOffset] = new AvroBinaryCodec[ZoneOffset]() {
    val avroSchema: AvroSchema = intCodec.avroSchema

    def decodeUnsafe(decoder: BinaryDecoder): ZoneOffset =
      ZoneOffset.ofTotalSeconds(decoder.readInt())

    def encode(value: ZoneOffset, encoder: BinaryEncoder): Unit =
      encoder.writeInt(value.getTotalSeconds)
  }

  val zonedDateTimeCodec: AvroBinaryCodec[ZonedDateTime] = new AvroBinaryCodec[ZonedDateTime]() {
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

    def decodeUnsafe(decoder: BinaryDecoder): ZonedDateTime =
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

    def encode(value: ZonedDateTime, encoder: BinaryEncoder): Unit = {
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

  val currencyCodec: AvroBinaryCodec[Currency] = new AvroBinaryCodec[java.util.Currency]() {
    val avroSchema: AvroSchema = AvroSchema.createFixed("Currency", null, "java.util", 3)

    def decodeUnsafe(decoder: BinaryDecoder): java.util.Currency = {
      val bs = new Array[Byte](3)
      decoder.readFixed(bs, 0, 3)
      java.util.Currency.getInstance(new String(bs))
    }

    def encode(value: java.util.Currency, encoder: BinaryEncoder): Unit = {
      val s = value.toString
      encoder.writeFixed(Array(s.charAt(0).toByte, s.charAt(1).toByte, s.charAt(2).toByte))
    }
  }

  val uuidCodec: AvroBinaryCodec[UUID] = new AvroBinaryCodec[java.util.UUID]() {
    val avroSchema: AvroSchema = AvroSchema.createFixed("UUID", null, "java.util", 16)

    def decodeUnsafe(decoder: BinaryDecoder): java.util.UUID = {
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

    def encode(value: java.util.UUID, encoder: BinaryEncoder): Unit = {
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

private class AvroBinaryCodecError(var spans: List[DynamicOptic.Node], message: String)
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
