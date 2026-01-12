package zio.blocks.schema.toon

import zio.blocks.schema.{DynamicOptic, SchemaError}
import zio.blocks.schema.binding.RegisterOffset
import zio.blocks.schema.binding.RegisterOffset.RegisterOffset
import zio.blocks.schema.codec.BinaryCodec
import java.nio.ByteBuffer
import java.time._
import scala.annotation.switch

/**
 * Abstract codec for TOON encoding/decoding.
 *
 * @param valueType
 *   Optimization hint for primitive types
 */
abstract class ToonBinaryCodec[A](val valueType: Int = ToonBinaryCodec.objectType) extends BinaryCodec[A] {

  /**
   * Computes the appropriate RegisterOffset based on valueType for proper
   * register handling.
   */
  val valueOffset: RegisterOffset = (valueType: @switch) match {
    case 0  => RegisterOffset(objects = 1)
    case 1  => RegisterOffset(ints = 1)
    case 2  => RegisterOffset(longs = 1)
    case 3  => RegisterOffset(floats = 1)
    case 4  => RegisterOffset(doubles = 1)
    case 5  => RegisterOffset(booleans = 1)
    case 6  => RegisterOffset(bytes = 1)
    case 7  => RegisterOffset(chars = 1)
    case 8  => RegisterOffset(shorts = 1)
    case 10 => RegisterOffset(objects = 1)
    case _  => RegisterOffset.Zero
  }

  /**
   * Decode a value from a TOON reader.
   *
   * @param in
   *   The TOON reader providing input
   * @param default
   *   Default value for initialization
   * @return
   *   The decoded value
   */
  def decodeValue(in: ToonReader, default: A): A

  /**
   * Encode a value to a TOON writer.
   *
   * @param x
   *   The value to encode
   * @param out
   *   The TOON writer for output
   */
  def encodeValue(x: A, out: ToonWriter): Unit

  /**
   * Encode only the fields of a value (for flattened/embedded structures).
   * Defaults to encodeValue(x, out) for non-record types.
   */
  def encodeFields(x: A, out: ToonWriter): Unit = encodeValue(x, out)

  /**
   * Decode a value used as a map key.
   */
  def decodeKey(in: ToonReader): A =
    in.decodeError("decoding as TOON key is not supported")

  /**
   * Encode a value as a map key.
   */
  def encodeKey(x: A, out: ToonWriter): Unit =
    out.encodeError("encoding as TOON key is not supported")

  /**
   * The null/default value for this type.
   */
  def nullValue: A = null.asInstanceOf[A]

  // Explicitly expose record fields if available for Tabular format optimization
  // By default None. Record codecs will override this.
  val recordFields: Option[IndexedSeq[String]] = None

  /**
   * For record types, provides the complexity of the fields. True means the
   * field is a complex type (Record, Sequence, Map) and cannot be used in
   * Tabular format. False means the field is a primitive type (Int, String,
   * Date, etc.).
   */
  def recordFieldComplexities: Option[IndexedSeq[Boolean]] = None

  /**
   * Indicates if this codec handles a complex type (Record, Sequence, Map).
   * Primitives, Strings, Dates, etc. return false.
   */
  def isComplexType: Boolean = false

  /**
   * Indicates if this codec handles a Sequence. Used to support fused key[N]
   * syntax.
   */
  def isSequence: Boolean = false

  /**
   * Encodes only the values of a record, separated by comma. Used for Tabular
   * array format. Default implementation falls back to encodeValue (which is
   * wrong for tabular but safe).
   */
  def encodeValues(x: A, out: ToonWriter): Unit = encodeValue(x, out)

  /**
   * Decodes only the values of a record, separated by comma. Used for Tabular
   * array format decoding. Default implementation falls back to decodeValue.
   */
  def decodeValues(in: ToonReader, default: A): A = decodeValue(in, default)

  // Public API
  override def decode(input: ByteBuffer): Either[SchemaError, A] =
    decode(input, ToonReaderConfig)

  override def encode(value: A, output: ByteBuffer): Unit =
    encode(value, output, ToonWriterConfig)

  def decode(input: ByteBuffer, config: ToonReaderConfig): Either[SchemaError, A] =
    try {
      // Placeholder implementation
      val array = new Array[Byte](input.remaining())
      input.get(array)
      val reader = new ToonReader(array, new Array[Char](config.preferredCharBufSize), array.length, config)
      Right(decodeValue(reader, nullValue))
    } catch {
      case e: SchemaError => Left(e)
      case e: Throwable   =>
        val single = new SchemaError.ExpectationMismatch(DynamicOptic.root, "Decoding failed: " + e.getMessage)
        Left(new SchemaError(new ::(single, Nil)))
    }

  def encode(value: A, output: ByteBuffer, config: ToonWriterConfig): Unit = {
    // Placeholder implementation
    val writer = new ToonWriter(new Array[Byte](config.preferredBufSize), config)
    encodeValue(value, writer)
    output.put(writer.buf, 0, writer.count)
  }

  // Convenience methods for byte arrays and strings
  def decodeFromString(input: String): Either[SchemaError, A] = {
    val bytes = input.getBytes(java.nio.charset.StandardCharsets.UTF_8)
    decode(ByteBuffer.wrap(bytes))
  }

  def encodeToString(value: A): String = {
    val writer = new ToonWriter(new Array[Byte](ToonWriterConfig.preferredBufSize), ToonWriterConfig)
    encodeValue(value, writer)
    new String(writer.buf, 0, writer.count, java.nio.charset.StandardCharsets.UTF_8)
  }
}

object ToonBinaryCodec {
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
  val stringType  = 10

  // Predefined primitive codecs
  /** Codec for Unit type. represented as null in TOON. */
  val unitCodec: ToonBinaryCodec[Unit] = new ToonBinaryCodec[Unit](unitType) {
    def decodeValue(in: ToonReader, default: Unit): Unit = { in.readString(); () }
    def encodeValue(x: Unit, out: ToonWriter): Unit      = out.writeNull()
  }

  /** Codec for Boolean type. */
  val booleanCodec: ToonBinaryCodec[Boolean] = new ToonBinaryCodec[Boolean](booleanType) {
    def decodeValue(in: ToonReader, default: Boolean): Boolean = in.readBoolean()
    def encodeValue(x: Boolean, out: ToonWriter): Unit         = out.writeRaw(x.toString)
  }

  /** Codec for Int type. */
  val intCodec: ToonBinaryCodec[Int] = new ToonBinaryCodec[Int](intType) {
    def decodeValue(in: ToonReader, default: Int): Int = in.readInt()
    def encodeValue(x: Int, out: ToonWriter): Unit     = out.writeRaw(x.toString)
  }

  val longCodec: ToonBinaryCodec[Long] = new ToonBinaryCodec[Long](longType) {
    def decodeValue(in: ToonReader, default: Long): Long = in.readString().toLong
    def encodeValue(x: Long, out: ToonWriter): Unit      = out.writeRaw(x.toString)
  }

  val floatCodec: ToonBinaryCodec[Float] = new ToonBinaryCodec[Float](floatType) {
    def decodeValue(in: ToonReader, default: Float): Float = in.readString().toFloat
    def encodeValue(x: Float, out: ToonWriter): Unit       =
      if (x.isNaN || x.isInfinity) out.writeNull()
      else if (x == 0.0f && 1.0f / x < 0) out.writeRaw("0") // -0.0
      else out.writeRaw(PlatformSpecific.bigDecimalToPlainString(new java.math.BigDecimal(x.toString)))
  }

  val doubleCodec: ToonBinaryCodec[Double] = new ToonBinaryCodec[Double](doubleType) {
    def decodeValue(in: ToonReader, default: Double): Double = in.readString().toDouble
    def encodeValue(x: Double, out: ToonWriter): Unit        =
      if (x.isNaN || x.isInfinity) out.writeNull()
      else if (x == 0.0 && 1.0 / x < 0) out.writeRaw("0") // -0.0
      else
        out.writeRaw(
          PlatformSpecific.bigDecimalToPlainString(new java.math.BigDecimal(x.toString).stripTrailingZeros())
        )
  }

  val byteCodec: ToonBinaryCodec[Byte] = new ToonBinaryCodec[Byte](byteType) {
    def decodeValue(in: ToonReader, default: Byte): Byte = in.readString().toByte
    def encodeValue(x: Byte, out: ToonWriter): Unit      = out.writeRaw(x.toString)
  }

  val shortCodec: ToonBinaryCodec[Short] = new ToonBinaryCodec[Short](shortType) {
    def decodeValue(in: ToonReader, default: Short): Short = in.readString().toShort
    def encodeValue(x: Short, out: ToonWriter): Unit       = out.writeRaw(x.toString)
  }

  val charCodec: ToonBinaryCodec[Char] = new ToonBinaryCodec[Char](charType) {
    def decodeValue(in: ToonReader, default: Char): Char = {
      val s = in.readString()
      if (s.length != 1) in.decodeError(s"Expected char, got $s")
      s.charAt(0)
    }
    def encodeValue(x: Char, out: ToonWriter): Unit = out.writeRaw(x.toString)
  }

  val bigIntCodec: ToonBinaryCodec[BigInt] = new ToonBinaryCodec[BigInt](objectType) {
    def decodeValue(in: ToonReader, default: BigInt): BigInt = BigInt(in.readString())
    def encodeValue(x: BigInt, out: ToonWriter): Unit        = out.writeRaw(x.toString)
  }

  val bigDecimalCodec: ToonBinaryCodec[BigDecimal] = new ToonBinaryCodec[BigDecimal](objectType) {
    def decodeValue(in: ToonReader, default: BigDecimal): BigDecimal = BigDecimal(in.readString())
    def encodeValue(x: BigDecimal, out: ToonWriter): Unit            =
      if (x == BigDecimal(0) && x.signum < 0) out.writeRaw("0") // Normalize -0 to 0
      else out.writeRaw(PlatformSpecific.bigDecimalToPlainString(x.bigDecimal))
  }

  val uuidCodec: ToonBinaryCodec[java.util.UUID] = new ToonBinaryCodec[java.util.UUID](objectType) {
    def decodeValue(in: ToonReader, default: java.util.UUID): java.util.UUID =
      java.util.UUID.fromString(in.readString())
    def encodeValue(x: java.util.UUID, out: ToonWriter): Unit = out.writeRaw(x.toString)
  }

  /**
   * Codec for String type. Automatically handles quoting based on content and
   * configuration.
   */
  val stringCodec: ToonBinaryCodec[String] = new ToonBinaryCodec[String](stringType) {
    def decodeValue(in: ToonReader, default: String): String = in.readString()
    def encodeValue(x: String, out: ToonWriter): Unit        =
      if (out.requiresQuoting(x)) out.writeQuotedString(x) else out.writeRawString(x)
  }

  // Java Time Codecs
  // We use standard ISO string representations

  val instantCodec: ToonBinaryCodec[Instant] = new ToonBinaryCodec[Instant](objectType) {
    def decodeValue(in: ToonReader, default: Instant): Instant = Instant.parse(in.readString())
    def encodeValue(x: Instant, out: ToonWriter): Unit         = out.writeRaw(x.toString)
  }

  val localDateCodec: ToonBinaryCodec[LocalDate] = new ToonBinaryCodec[LocalDate](objectType) {
    def decodeValue(in: ToonReader, default: LocalDate): LocalDate = LocalDate.parse(in.readString())
    def encodeValue(x: LocalDate, out: ToonWriter): Unit           = out.writeRaw(x.toString)
  }

  val localTimeCodec: ToonBinaryCodec[LocalTime] = new ToonBinaryCodec[LocalTime](objectType) {
    def decodeValue(in: ToonReader, default: LocalTime): LocalTime = LocalTime.parse(in.readString())
    def encodeValue(x: LocalTime, out: ToonWriter): Unit           = out.writeRaw(x.toString)
  }

  val localDateTimeCodec: ToonBinaryCodec[LocalDateTime] = new ToonBinaryCodec[LocalDateTime](objectType) {
    def decodeValue(in: ToonReader, default: LocalDateTime): LocalDateTime = LocalDateTime.parse(in.readString())
    def encodeValue(x: LocalDateTime, out: ToonWriter): Unit               = out.writeRaw(x.toString)
  }

  val offsetDateTimeCodec: ToonBinaryCodec[OffsetDateTime] = new ToonBinaryCodec[OffsetDateTime](objectType) {
    def decodeValue(in: ToonReader, default: OffsetDateTime): OffsetDateTime = OffsetDateTime.parse(in.readString())
    def encodeValue(x: OffsetDateTime, out: ToonWriter): Unit                = out.writeRaw(x.toString)
  }

  val zonedDateTimeCodec: ToonBinaryCodec[ZonedDateTime] = new ToonBinaryCodec[ZonedDateTime](objectType) {
    def decodeValue(in: ToonReader, default: ZonedDateTime): ZonedDateTime = ZonedDateTime.parse(in.readString())
    def encodeValue(x: ZonedDateTime, out: ToonWriter): Unit               = out.writeRaw(x.toString)
  }

  val durationCodec: ToonBinaryCodec[Duration] = new ToonBinaryCodec[Duration](objectType) {
    def decodeValue(in: ToonReader, default: Duration): Duration = Duration.parse(in.readString())
    def encodeValue(x: Duration, out: ToonWriter): Unit          = out.writeRaw(x.toString)
  }

  val periodCodec: ToonBinaryCodec[Period] = new ToonBinaryCodec[Period](objectType) {
    def decodeValue(in: ToonReader, default: Period): Period = Period.parse(in.readString())
    def encodeValue(x: Period, out: ToonWriter): Unit        = out.writeRaw(x.toString)
  }

  val yearCodec: ToonBinaryCodec[Year] = new ToonBinaryCodec[Year](objectType) {
    def decodeValue(in: ToonReader, default: Year): Year = Year.parse(in.readString())
    def encodeValue(x: Year, out: ToonWriter): Unit      = out.writeRaw(x.toString)
  }

  val yearMonthCodec: ToonBinaryCodec[YearMonth] = new ToonBinaryCodec[YearMonth](objectType) {
    def decodeValue(in: ToonReader, default: YearMonth): YearMonth = YearMonth.parse(in.readString())
    def encodeValue(x: YearMonth, out: ToonWriter): Unit           = out.writeRaw(x.toString)
  }

  val monthDayCodec: ToonBinaryCodec[MonthDay] = new ToonBinaryCodec[MonthDay](objectType) {
    def decodeValue(in: ToonReader, default: MonthDay): MonthDay = MonthDay.parse(in.readString())
    def encodeValue(x: MonthDay, out: ToonWriter): Unit          = out.writeRaw(x.toString)
  }

  val zoneIdCodec: ToonBinaryCodec[ZoneId] = new ToonBinaryCodec[ZoneId](objectType) {
    def decodeValue(in: ToonReader, default: ZoneId): ZoneId = ZoneId.of(in.readString())
    def encodeValue(x: ZoneId, out: ToonWriter): Unit        = out.writeRaw(x.toString)
  }

  val zoneOffsetCodec: ToonBinaryCodec[ZoneOffset] = new ToonBinaryCodec[ZoneOffset](objectType) {
    def decodeValue(in: ToonReader, default: ZoneOffset): ZoneOffset = ZoneOffset.of(in.readString())
    def encodeValue(x: ZoneOffset, out: ToonWriter): Unit            = out.writeRaw(x.toString)
  }

  val monthCodec: ToonBinaryCodec[Month] = new ToonBinaryCodec[Month](objectType) {
    def decodeValue(in: ToonReader, default: Month): Month = Month.valueOf(in.readString().toUpperCase)
    def encodeValue(x: Month, out: ToonWriter): Unit       = out.writeRaw(x.name())
  }

  val dayOfWeekCodec: ToonBinaryCodec[DayOfWeek] = new ToonBinaryCodec[DayOfWeek](objectType) {
    def decodeValue(in: ToonReader, default: DayOfWeek): DayOfWeek = DayOfWeek.valueOf(in.readString().toUpperCase)
    def encodeValue(x: DayOfWeek, out: ToonWriter): Unit           = out.writeRaw(x.name())
  }

  val currencyCodec: ToonBinaryCodec[java.util.Currency] = new ToonBinaryCodec[java.util.Currency](objectType) {
    def decodeValue(in: ToonReader, default: java.util.Currency): java.util.Currency =
      java.util.Currency.getInstance(in.readString())
    def encodeValue(x: java.util.Currency, out: ToonWriter): Unit = out.writeRaw(x.getCurrencyCode)
  }
}
