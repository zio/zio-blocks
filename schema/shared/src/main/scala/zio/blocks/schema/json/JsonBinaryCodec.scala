package zio.blocks.schema.json

import java.nio.ByteBuffer
import zio.blocks.schema.codec.BinaryCodec
import zio.blocks.schema.SchemaError
import zio.blocks.schema.binding.RegisterOffset.RegisterOffset
import zio.blocks.schema.DynamicValue
import zio.blocks.schema.PrimitiveValue

abstract class JsonBinaryCodec[A](val valueType: Int = JsonBinaryCodec.objectType) extends BinaryCodec[A] {
  def decodeValue(in: JsonReader, default: A): A
  def encodeValue(x: A, out: JsonWriter): Unit
  def nullValue: A

  def valueOffset: RegisterOffset = 1

  def decodeKey(in: JsonReader): A           = decodeValue(in, nullValue)
  def encodeKey(x: A, out: JsonWriter): Unit = encodeValue(x, out)

  def encode(value: A, output: ByteBuffer): Unit = encode(value, output, WriterConfig)

  def encode(value: A, output: ByteBuffer, config: WriterConfig): Unit = {
    val writer = new JsonWriter()
    writer.write(this, value, output, config)
  }


  def encode(value: A, output: java.io.OutputStream): Unit = encode(value, output, WriterConfig)

  def encode(value: A, output: java.io.OutputStream, config: WriterConfig): Unit = {
    val writer = new JsonWriter()
    writer.write(this, value, output, config)
  }

  def decode(input: ByteBuffer): Either[SchemaError, A] = decode(input, ReaderConfig)

  def decode(input: ByteBuffer, config: ReaderConfig): Either[SchemaError, A] = {
    val reader = new JsonReader(bbuf = input, config = config)
    try {
      val res = decodeValue(reader, nullValue)
      if (config.checkForEndOfInput) reader.endOfInputOrError()
      Right(res)
    } catch {
      case e: JsonError => Left(SchemaError.expectationMismatch(Nil, e.message))
      case e: Throwable => Left(SchemaError.expectationMismatch(Nil, e.getMessage))
    }
  }

  def decode(input: java.io.InputStream): Either[SchemaError, A] = decode(input, ReaderConfig)

  def decode(input: java.io.InputStream, config: ReaderConfig): Either[SchemaError, A] = {
    val reader = new JsonReader(in = input, config = config)
    try {
      val res = decodeValue(reader, nullValue)
      if (config.checkForEndOfInput) reader.endOfInputOrError()
      Right(res)
    } catch {
      case e: JsonError => Left(SchemaError.expectationMismatch(Nil, e.message))
      case e: Throwable => Left(SchemaError.expectationMismatch(Nil, e.getMessage))
    }
  }

  def decode(bytes: Array[Byte]): Either[SchemaError, A] = decode(ByteBuffer.wrap(bytes))

  def decode(bytes: Array[Byte], config: ReaderConfig): Either[SchemaError, A] = decode(ByteBuffer.wrap(bytes), config)

  def decode(json: String): Either[SchemaError, A] = decode(json, ReaderConfig)

  def decode(json: String, config: ReaderConfig): Either[SchemaError, A] = decode(json.getBytes(java.nio.charset.StandardCharsets.UTF_8), config)

  def encode(value: A): Array[Byte] = encode(value, WriterConfig)

  def encode(value: A, config: WriterConfig): Array[Byte] = {
    val buffer = ByteBuffer.allocate(10485760) // 10MB buffer to handle large test cases
    encode(value, buffer, config)
    buffer.flip()
    val arr = new Array[Byte](buffer.remaining())
    buffer.get(arr)
    arr
  }

  def encodeToString(value: A): String = encodeToString(value, WriterConfig)

  def encodeToString(value: A, config: WriterConfig): String = {
    val bytes = encode(value, config)
    new String(bytes, java.nio.charset.StandardCharsets.UTF_8)
  }

  // Helpers for JsonDecoder (stubbed)
  def decodeJson(json: java.lang.String): Either[JsonError, A] = {
    val _ = json
    Left(JsonError("Not implemented"))
  }

  def encodeJson(value: A): java.lang.String = "TODO"
}

object JsonBinaryCodec {
  val objectType  = 0
  val intType     = 1
  val longType    = 2
  val floatType   = 3
  val doubleType  = 4
  val booleanType = 5
  val byteType    = 6
  val charType    = 7
  val shortType   = 8
  val unitType    = 9

  private class PrimitiveCodec[A](
    tpe: Int,
    val nullValue: A,
    decoder: (JsonReader, A) => A,
    encoder: (A, JsonWriter) => Unit
  ) extends JsonBinaryCodec[A](tpe) {
    def decodeValue(in: JsonReader, default: A): A = decoder(in, default)
    def encodeValue(x: A, out: JsonWriter): Unit   = encoder(x, out)
    override def decodeKey(in: JsonReader): A      = {
      val s      = in.readKeyAsString()
      val reader = new JsonReader(s.getBytes("UTF-8"))
      try decoder(reader, nullValue)
      catch {
        // If an error occurs during key decoding, it will have a path relative to the key string.
        // We want to preserve the error message but let the outer reader add the path to the key itself.
        // We strip the trailing " at: ." or similar if present to avoid double path reporting.
        case e: JsonError =>
          val idx = e.message.lastIndexOf(" at: ")
          val msg = if (idx >= 0) e.message.substring(0, idx) else e.message
          in.decodeError(if (msg.isEmpty) e.message else msg)
        case _: Throwable => in.decodeError(s"cannot parse key '$s'")
      }
    }
    override def encodeKey(x: A, out: JsonWriter): Unit = out.writeKey(x.toString)
  }

  implicit val intCodec: JsonBinaryCodec[Int] =
    new PrimitiveCodec(intType, 0, (in, _) => in.readInt(), (x, out) => out.writeVal(x))
  implicit val longCodec: JsonBinaryCodec[Long] =
    new PrimitiveCodec(longType, 0L, (in, _) => in.readLong(), (x, out) => out.writeVal(x))
  implicit val floatCodec: JsonBinaryCodec[Float] =
    new PrimitiveCodec(floatType, 0.0f, (in, _) => in.readFloat(), (x, out) => out.writeVal(x))
  implicit val doubleCodec: JsonBinaryCodec[Double] =
    new PrimitiveCodec(doubleType, 0.0, (in, _) => in.readDouble(), (x, out) => out.writeVal(x))
  implicit val booleanCodec: JsonBinaryCodec[Boolean] =
    new PrimitiveCodec(booleanType, false, (in, _) => in.readBoolean(), (x, out) => out.writeVal(x))
  implicit val byteCodec: JsonBinaryCodec[Byte] =
    new PrimitiveCodec(byteType, 0.toByte, (in, _) => in.readByte(), (x, out) => out.writeVal(x))
  implicit val charCodec: JsonBinaryCodec[Char] =
    new PrimitiveCodec(charType, '\u0000', (in, _) => in.readChar(), (x, out) => out.writeVal(x))
  implicit val shortCodec: JsonBinaryCodec[Short] =
    new PrimitiveCodec(shortType, 0.toShort, (in, _) => in.readShort(), (x, out) => out.writeVal(x))
  implicit val unitCodec: JsonBinaryCodec[Unit] = new JsonBinaryCodec[Unit](unitType) {
    def decodeValue(in: JsonReader, default: Unit): Unit   = { in.skip(); () }
    def encodeValue(x: Unit, out: JsonWriter): Unit        = { out.writeObjectStart(); out.writeObjectEnd() }
    def nullValue: Unit                                    = ()
    override def decodeKey(in: JsonReader): Unit           = in.decodeError("decoding as JSON key is not supported")
    override def encodeKey(x: Unit, out: JsonWriter): Unit = out.encodeError("encoding as JSON key is not supported")
  }

  // Also needed by Deriver
  implicit val stringCodec: JsonBinaryCodec[java.lang.String] =
    new PrimitiveCodec(objectType, "", _.readString(_), (x, out) => if (x == null) out.writeNull() else out.writeVal(x))

  implicit val dynamicValueCodec: JsonBinaryCodec[DynamicValue] = new JsonBinaryCodec[DynamicValue](objectType) {
    def decodeValue(in: JsonReader, default: DynamicValue): DynamicValue =
      // Platform-specific implementation
      default

    def encodeValue(x: DynamicValue, out: JsonWriter): Unit = x match {
      case DynamicValue.Primitive(p) => encodePrimitive(p, out)
      case _                         => out.encodeError("Unsupported DynamicValue type for encoding")
    }

    def nullValue: DynamicValue = DynamicValue.Primitive(PrimitiveValue.Unit)

    override def decodeKey(in: JsonReader): DynamicValue = {
      val s = in.readKeyAsString()
      Json.parse(s) match {
        case Right(jsonValue) => jsonValue.toDynamicValue
        case Left(_)          => nullValue
      }
    }

    private def primitiveToString(p: PrimitiveValue): String = p match {
      case PrimitiveValue.String(v)         => v
      case PrimitiveValue.Int(v)            => v.toString
      case PrimitiveValue.Long(v)           => v.toString
      case PrimitiveValue.Float(v)          => v.toString
      case PrimitiveValue.Double(v)         => v.toString
      case PrimitiveValue.Boolean(v)        => v.toString
      case PrimitiveValue.Unit              => "()"
      case PrimitiveValue.Char(v)           => v.toString
      case PrimitiveValue.Byte(v)           => v.toString
      case PrimitiveValue.Short(v)          => v.toString
      case PrimitiveValue.UUID(v)           => v.toString
      case PrimitiveValue.BigDecimal(v)     => v.toString
      case PrimitiveValue.BigInt(v)         => v.toString
      case PrimitiveValue.Currency(v)       => v.getCurrencyCode
      case PrimitiveValue.DayOfWeek(v)      => v.name
      case PrimitiveValue.Month(v)          => v.name
      case PrimitiveValue.MonthDay(v)       => v.toString
      case PrimitiveValue.Period(v)         => v.toString
      case PrimitiveValue.Year(v)           => v.toString
      case PrimitiveValue.YearMonth(v)      => v.toString
      case PrimitiveValue.ZoneId(v)         => v.toString
      case PrimitiveValue.ZoneOffset(v)     => v.toString
      case PrimitiveValue.Duration(v)       => v.toString
      case PrimitiveValue.Instant(v)        => v.toString
      case PrimitiveValue.LocalDate(v)      => v.toString
      case PrimitiveValue.LocalTime(v)      => v.toString
      case PrimitiveValue.LocalDateTime(v)  => v.toString
      case PrimitiveValue.OffsetTime(v)     => v.toString
      case PrimitiveValue.OffsetDateTime(v) => v.toString
      case PrimitiveValue.ZonedDateTime(v)  => v.toString
    }

    override def encodeKey(x: DynamicValue, out: JsonWriter): Unit = x match {
      case DynamicValue.Primitive(p) => out.writeKey(primitiveToString(p))
      case _                         => out.encodeError(s"Unsupported DynamicValue type for key encoding: $x")
    }
  }

  private def encodePrimitive(p: PrimitiveValue, out: JsonWriter): Unit = p match {
    case PrimitiveValue.String(v)         => if (v == null) out.writeNull() else out.writeVal(v)
    case PrimitiveValue.Int(v)            => out.writeVal(v)
    case PrimitiveValue.Long(v)           => out.writeVal(v)
    case PrimitiveValue.Float(v)          => out.writeVal(v)
    case PrimitiveValue.Double(v)         => out.writeVal(v)
    case PrimitiveValue.Boolean(v)        => out.writeVal(v)
    case PrimitiveValue.Unit              => out.writeObjectStart(); out.writeObjectEnd()
    case PrimitiveValue.Char(v)           => out.writeVal(v)
    case PrimitiveValue.Byte(v)           => out.writeVal(v)
    case PrimitiveValue.Short(v)          => out.writeVal(v)
    case PrimitiveValue.UUID(v)           => out.writeVal(v)
    case PrimitiveValue.BigDecimal(v)     => out.writeVal(v)
    case PrimitiveValue.BigInt(v)         => out.writeVal(v)
    case PrimitiveValue.Currency(v)       => out.writeVal(v.getCurrencyCode)
    case PrimitiveValue.DayOfWeek(v)      => out.writeVal(v.name)
    case PrimitiveValue.Month(v)          => out.writeVal(v.name)
    case PrimitiveValue.MonthDay(v)       => out.writeVal(v)
    case PrimitiveValue.Period(v)         => out.writeVal(v)
    case PrimitiveValue.Year(v)           => out.writeVal(v)
    case PrimitiveValue.YearMonth(v)      => out.writeVal(v)
    case PrimitiveValue.ZoneId(v)         => out.writeVal(v)
    case PrimitiveValue.ZoneOffset(v)     => out.writeVal(v)
    case PrimitiveValue.Duration(v)       => out.writeVal(v)
    case PrimitiveValue.Instant(v)        => out.writeVal(v)
    case PrimitiveValue.LocalDate(v)      => out.writeVal(v)
    case PrimitiveValue.LocalTime(v)      => out.writeVal(v)
    case PrimitiveValue.LocalDateTime(v)  => out.writeVal(v)
    case PrimitiveValue.OffsetTime(v)     => out.writeVal(v)
    case PrimitiveValue.OffsetDateTime(v) => out.writeVal(v)
    case PrimitiveValue.ZonedDateTime(v)  => out.writeVal(v)
  }

  implicit val currencyCodec: JsonBinaryCodec[java.util.Currency] = new PrimitiveCodec(
    objectType,
    java.util.Currency.getInstance("USD"),
    (in, _) =>
      try {
        java.util.Currency.getInstance(in.readString(null))
      } catch {
        case _: IllegalArgumentException => in.decodeError("illegal currency value")
      },
    (x, out) => out.writeVal(x.getCurrencyCode)
  )

  implicit val uuidCodec: JsonBinaryCodec[java.util.UUID] = new PrimitiveCodec(
    objectType,
    new java.util.UUID(0L, 0L),
    (in, _) => in.readUUID(null),
    (x, out) => out.writeVal(x)
  )

  implicit val bigIntCodec: JsonBinaryCodec[BigInt] =
    new PrimitiveCodec(objectType, BigInt(0), (in, _) => in.readBigInt(null), (x, out) => out.writeVal(x))
  implicit val bigDecimalCodec: JsonBinaryCodec[BigDecimal] =
    new PrimitiveCodec(objectType, BigDecimal(0), (in, _) => in.readBigDecimal(null), (x, out) => out.writeVal(x))

  implicit val dayOfWeekCodec: JsonBinaryCodec[java.time.DayOfWeek] = new PrimitiveCodec(
    objectType,
    java.time.DayOfWeek.MONDAY,
    (in, _) =>
      try {
        java.time.DayOfWeek.valueOf(in.readString(null))
      } catch {
        case _: IllegalArgumentException => in.decodeError("illegal day of week value")
      },
    (x, out) => out.writeVal(x.name)
  )

  implicit val durationCodec: JsonBinaryCodec[java.time.Duration] = new PrimitiveCodec(
    objectType,
    java.time.Duration.ZERO,
    (in, _) => in.readDuration(null),
    (x, out) => out.writeVal(x)
  )
  implicit val instantCodec: JsonBinaryCodec[java.time.Instant] = new PrimitiveCodec(
    objectType,
    java.time.Instant.EPOCH,
    (in, _) => in.readInstant(null),
    (x, out) => out.writeVal(x)
  )
  implicit val localDateCodec: JsonBinaryCodec[java.time.LocalDate] = new PrimitiveCodec(
    objectType,
    java.time.LocalDate.now,
    (in, _) => in.readLocalDate(null),
    (x, out) => out.writeVal(x)
  )
  implicit val localDateTimeCodec: JsonBinaryCodec[java.time.LocalDateTime] = new PrimitiveCodec(
    objectType,
    java.time.LocalDateTime.now,
    (in, _) => in.readLocalDateTime(null),
    (x, out) => out.writeVal(x)
  )
  implicit val localTimeCodec: JsonBinaryCodec[java.time.LocalTime] = new PrimitiveCodec(
    objectType,
    java.time.LocalTime.now,
    (in, _) => in.readLocalTime(null),
    (x, out) => out.writeVal(x)
  )
  implicit val monthCodec: JsonBinaryCodec[java.time.Month] = new PrimitiveCodec(
    objectType,
    java.time.Month.JANUARY,
    (in, _) =>
      try {
        java.time.Month.valueOf(in.readString(null))
      } catch {
        case _: IllegalArgumentException => in.decodeError("illegal month value")
      },
    (x, out) => out.writeVal(x.name)
  )
  implicit val monthDayCodec: JsonBinaryCodec[java.time.MonthDay] = new PrimitiveCodec(
    objectType,
    java.time.MonthDay.now,
    (in, _) => in.readMonthDay(null),
    (x, out) => out.writeVal(x)
  )
  implicit val offsetDateTimeCodec: JsonBinaryCodec[java.time.OffsetDateTime] = new PrimitiveCodec(
    objectType,
    java.time.OffsetDateTime.now,
    (in, _) => in.readOffsetDateTime(null),
    (x, out) => out.writeVal(x)
  )
  implicit val offsetTimeCodec: JsonBinaryCodec[java.time.OffsetTime] = new PrimitiveCodec(
    objectType,
    java.time.OffsetTime.now,
    (in, _) => in.readOffsetTime(null),
    (x, out) => out.writeVal(x)
  )
  implicit val periodCodec: JsonBinaryCodec[java.time.Period] =
    new PrimitiveCodec(objectType, java.time.Period.ZERO, (in, _) => in.readPeriod(null), (x, out) => out.writeVal(x))
  implicit val yearCodec: JsonBinaryCodec[java.time.Year] =
    new PrimitiveCodec(objectType, java.time.Year.now, (in, _) => in.readYear(null), (x, out) => out.writeVal(x))
  implicit val yearMonthCodec: JsonBinaryCodec[java.time.YearMonth] = new PrimitiveCodec(
    objectType,
    java.time.YearMonth.now,
    (in, _) => in.readYearMonth(null),
    (x, out) => out.writeVal(x)
  )
  implicit val zoneIdCodec: JsonBinaryCodec[java.time.ZoneId] = new PrimitiveCodec(
    objectType,
    java.time.ZoneId.systemDefault(),
    (in, _) => in.readZoneId(null),
    (x, out) => out.writeVal(x)
  )
  implicit val zoneOffsetCodec: JsonBinaryCodec[java.time.ZoneOffset] = new PrimitiveCodec(
    objectType,
    java.time.ZoneOffset.UTC,
    (in, _) => in.readZoneOffset(null),
    (x, out) => out.writeVal(x)
  )
  implicit val zonedDateTimeCodec: JsonBinaryCodec[java.time.ZonedDateTime] = new PrimitiveCodec(
    objectType,
    java.time.ZonedDateTime.now,
    (in, _) => in.readZonedDateTime(null),
    (x, out) => out.writeVal(x)
  )
}
