package zio.blocks.schema.toon

import zio.blocks.schema.SchemaError.ExpectationMismatch
import zio.blocks.schema.{DynamicOptic, SchemaError}
import zio.blocks.schema.binding.RegisterOffset
import zio.blocks.schema.codec.BinaryCodec

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets.UTF_8
import java.time._
import java.util.{Currency, UUID}
import scala.annotation.switch
import scala.collection.immutable.ArraySeq
import scala.util.control.NonFatal

/**
 * Abstract class for encoding and decoding values of type `A` to and from TOON
 * (Token-Oriented Object Notation) format. Encapsulates logic to map TOON
 * representations into native type structures and vice versa, handling
 * serialization and deserialization as per TOON encoding standards using UTF-8
 * character set.
 *
 * TOON is a compact, human-readable format optimized for LLM prompts that
 * achieves 30-60% token reduction compared to JSON.
 *
 * @param valueType
 *   Integer representing the type of the value for encoding/decoding. Default
 *   is set to `ToonBinaryCodec.objectType`.
 *
 * @see
 *   [[https://github.com/toon-format/spec TOON Specification]]
 */
abstract class ToonBinaryCodec[A](val valueType: Int = ToonBinaryCodec.objectType) extends BinaryCodec[A] {

  /**
   * Computes the appropriate `RegisterOffset` based on the value type.
   */
  val valueOffset: RegisterOffset.RegisterOffset = (valueType: @switch) match {
    case 0 => RegisterOffset(objects = 1)
    case 1 => RegisterOffset(ints = 1)
    case 2 => RegisterOffset(longs = 1)
    case 3 => RegisterOffset(floats = 1)
    case 4 => RegisterOffset(doubles = 1)
    case 5 => RegisterOffset(booleans = 1)
    case 6 => RegisterOffset(bytes = 1)
    case 7 => RegisterOffset(chars = 1)
    case 8 => RegisterOffset(shorts = 1)
    case _ => RegisterOffset.Zero
  }

  /**
   * Attempts to decode a value of type `A` from the specified `ToonReader`.
   *
   * @param in
   *   an instance of `ToonReader` which provides access to the TOON input
   * @param default
   *   the placeholder value provided to initialize local variables
   */
  def decodeValue(in: ToonReader, default: A): A

  /**
   * Encodes the specified value using provided `ToonWriter`.
   *
   * @param x
   *   the value provided for serialization
   * @param out
   *   an instance of `ToonWriter` which provides access to TOON output
   */
  def encodeValue(x: A, out: ToonWriter): Unit

  /**
   * Returns some value that will be passed as the default parameter in
   * `decodeValue`.
   */
  def nullValue: A = null.asInstanceOf[A]

  /**
   * Attempts to decode a value of type `A` from the specified `ToonReader` as a
   * key.
   *
   * @param in
   *   an instance of `ToonReader` which provides access to the TOON input
   */
  def decodeKey(in: ToonReader): A = in.decodeError("decoding as TOON key is not supported")

  /**
   * Encodes the specified value using provided `ToonWriter` as a key.
   *
   * @param x
   *   the value provided for serialization
   * @param out
   *   an instance of `ToonWriter` which provides access to TOON output
   */
  def encodeKey(x: A, out: ToonWriter): Unit = out.encodeError("encoding as TOON key is not supported")

  /**
   * Decodes a value of type `A` from the given `ByteBuffer`.
   */
  override def decode(input: ByteBuffer): Either[SchemaError, A] = decode(input, ReaderConfig)

  /**
   * Encodes the specified value of type `A` into binary format and writes it to
   * the provided `ByteBuffer`.
   */
  override def encode(value: A, output: ByteBuffer): Unit = encode(value, output, WriterConfig)

  /**
   * Decodes a value of type `A` from the given `ByteBuffer` using the specified
   * `ReaderConfig`.
   */
  def decode(input: ByteBuffer, config: ReaderConfig): Either[SchemaError, A] =
    try {
      var reader = ToonBinaryCodec.readerPool.get
      if (reader.isInUse) reader = toonReader(Array.emptyByteArray, config)
      new Right(reader.read(this, input, config))
    } catch {
      case error if NonFatal(error) => new Left(toError(error))
    }

  /**
   * Encodes the specified value of type `A` into binary format using the
   * specified `WriterConfig`.
   */
  def encode(value: A, output: ByteBuffer, config: WriterConfig): Unit = {
    var writer = ToonBinaryCodec.writerPool.get
    if (writer.isInUse) writer = toonWriter(config)
    writer.write(this, value, output, config)
  }

  /**
   * Decodes a value of type `A` from the given byte array.
   */
  def decode(input: Array[Byte]): Either[SchemaError, A] = decode(input, ReaderConfig)

  /**
   * Encodes the specified value of type `A` into a byte array.
   */
  def encode(value: A): Array[Byte] = encode(value, WriterConfig)

  /**
   * Decodes a value of type `A` from the provided byte array using the
   * specified `ReaderConfig`.
   */
  def decode(input: Array[Byte], config: ReaderConfig): Either[SchemaError, A] =
    try {
      var reader = ToonBinaryCodec.readerPool.get
      if (reader.isInUse) reader = toonReader(input, config)
      new Right(reader.read(this, input, 0, input.length, config))
    } catch {
      case error if NonFatal(error) => new Left(toError(error))
    }

  /**
   * Encodes the specified value of type `A` into a byte array using the
   * provided `WriterConfig`.
   */
  def encode(value: A, config: WriterConfig): Array[Byte] = {
    var writer = ToonBinaryCodec.writerPool.get
    if (writer.isInUse) writer = toonWriter(config)
    writer.write(this, value, config)
  }

  /**
   * Decodes a value of type `A` from the provided `InputStream`.
   */
  def decode(input: java.io.InputStream): Either[SchemaError, A] = decode(input, ReaderConfig)

  /**
   * Encodes the specified value of type `A` to the provided `OutputStream`.
   */
  def encode(value: A, output: java.io.OutputStream): Unit = encode(value, output, WriterConfig)

  /**
   * Decodes a value of type `A` from the provided `InputStream` using the
   * specified `ReaderConfig`.
   */
  def decode(input: java.io.InputStream, config: ReaderConfig): Either[SchemaError, A] =
    try {
      var reader = ToonBinaryCodec.readerPool.get
      if (reader.isInUse) reader = toonReader(Array.emptyByteArray, config)
      new Right(reader.read(this, input, config))
    } catch {
      case error if NonFatal(error) => new Left(toError(error))
    }

  /**
   * Encodes the specified value of type `A` to the given `OutputStream` using
   * the provided `WriterConfig`.
   */
  def encode(value: A, output: java.io.OutputStream, config: WriterConfig): Unit = {
    var writer = ToonBinaryCodec.writerPool.get
    if (writer.isInUse) writer = toonWriter(config)
    writer.write(this, value, output, config)
  }

  /**
   * Decodes a value of type `A` from the given string.
   */
  def decode(input: String): Either[SchemaError, A] = decode(input, ReaderConfig)

  /**
   * Encodes the specified value of type `A` into a string.
   */
  def encodeToString(value: A): String = encodeToString(value, WriterConfig)

  /**
   * Decodes a value of type `A` from the provided string using the specified
   * `ReaderConfig`.
   */
  def decode(input: String, config: ReaderConfig): Either[SchemaError, A] =
    try {
      val buf    = input.getBytes(UTF_8)
      var reader = ToonBinaryCodec.readerPool.get
      if (reader.isInUse) reader = toonReader(buf, config)
      new Right(reader.read(this, buf, 0, buf.length, config))
    } catch {
      case error if NonFatal(error) => new Left(toError(error))
    }

  /**
   * Encodes the specified value of type `A` into a string using the provided
   * `WriterConfig`.
   */
  def encodeToString(value: A, config: WriterConfig): String = {
    var writer = ToonBinaryCodec.writerPool.get
    if (writer.isInUse) writer = toonWriter(config)
    writer.writeToString(this, value, config)
  }

  private[this] def toonReader(buf: Array[Byte], config: ReaderConfig): ToonReader =
    new ToonReader(buf = buf, config = config)

  private[this] def toonWriter(config: WriterConfig): ToonWriter =
    new ToonWriter(buf = Array.emptyByteArray, limit = 0, config = config)

  private[this] def toError(error: Throwable): SchemaError = new SchemaError(
    new ::(
      new ExpectationMismatch(
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
            new DynamicOptic(ArraySeq.unsafeWrapArray(array))
          case _ => DynamicOptic.root
        },
        error.getMessage
      ),
      Nil
    )
  )
}

/**
 * Companion object providing type constants and thread-local pools for TOON
 * encoding/decoding, along with primitive codecs.
 */
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

  private[toon] val readerPool: ThreadLocal[ToonReader] = new ThreadLocal[ToonReader] {
    override def initialValue(): ToonReader = new ToonReader
  }
  private[toon] val writerPool: ThreadLocal[ToonWriter] = new ThreadLocal[ToonWriter] {
    override def initialValue(): ToonWriter = new ToonWriter
  }

  val unitCodec: ToonBinaryCodec[Unit] = new ToonBinaryCodec[Unit](ToonBinaryCodec.unitType) {
    def decodeValue(in: ToonReader, default: Unit): Unit =
      if (in.isNextToken('n')) {
        in.rollbackToken()
        in.readNullOrError((), "expected null")
      } else in.decodeError("expected null")

    def encodeValue(x: Unit, out: ToonWriter): Unit = out.writeNull()
  }

  val booleanCodec: ToonBinaryCodec[Boolean] = new ToonBinaryCodec[Boolean](ToonBinaryCodec.booleanType) {
    def decodeValue(in: ToonReader, default: Boolean): Boolean = in.readBoolean()

    def encodeValue(x: Boolean, out: ToonWriter): Unit = out.writeVal(x)

    override def decodeKey(in: ToonReader): Boolean = in.readKeyAsBoolean()

    override def encodeKey(x: Boolean, out: ToonWriter): Unit = out.writeKey(x)
  }

  val byteCodec: ToonBinaryCodec[Byte] = new ToonBinaryCodec[Byte](ToonBinaryCodec.byteType) {
    def decodeValue(in: ToonReader, default: Byte): Byte = in.readByte()

    def encodeValue(x: Byte, out: ToonWriter): Unit = out.writeVal(x)

    override def decodeKey(in: ToonReader): Byte = in.readKeyAsByte()

    override def encodeKey(x: Byte, out: ToonWriter): Unit = out.writeKey(x)
  }

  val shortCodec: ToonBinaryCodec[Short] = new ToonBinaryCodec[Short](ToonBinaryCodec.shortType) {
    def decodeValue(in: ToonReader, default: Short): Short = in.readShort()

    def encodeValue(x: Short, out: ToonWriter): Unit = out.writeVal(x)

    override def decodeKey(in: ToonReader): Short = in.readKeyAsShort()

    override def encodeKey(x: Short, out: ToonWriter): Unit = out.writeKey(x)
  }

  val intCodec: ToonBinaryCodec[Int] = new ToonBinaryCodec[Int](ToonBinaryCodec.intType) {
    def decodeValue(in: ToonReader, default: Int): Int = in.readInt()

    def encodeValue(x: Int, out: ToonWriter): Unit = out.writeVal(x)

    override def decodeKey(in: ToonReader): Int = in.readKeyAsInt()

    override def encodeKey(x: Int, out: ToonWriter): Unit = out.writeKey(x)
  }

  val longCodec: ToonBinaryCodec[Long] = new ToonBinaryCodec[Long](ToonBinaryCodec.longType) {
    def decodeValue(in: ToonReader, default: Long): Long = in.readLong()

    def encodeValue(x: Long, out: ToonWriter): Unit = out.writeVal(x)

    override def decodeKey(in: ToonReader): Long = in.readKeyAsLong()

    override def encodeKey(x: Long, out: ToonWriter): Unit = out.writeKey(x)
  }

  val floatCodec: ToonBinaryCodec[Float] = new ToonBinaryCodec[Float](ToonBinaryCodec.floatType) {
    def decodeValue(in: ToonReader, default: Float): Float = in.readFloat()

    def encodeValue(x: Float, out: ToonWriter): Unit = out.writeVal(x)

    override def decodeKey(in: ToonReader): Float = in.readKeyAsFloat()

    override def encodeKey(x: Float, out: ToonWriter): Unit = out.writeKey(x)
  }

  val doubleCodec: ToonBinaryCodec[Double] = new ToonBinaryCodec[Double](ToonBinaryCodec.doubleType) {
    def decodeValue(in: ToonReader, default: Double): Double = in.readDouble()

    def encodeValue(x: Double, out: ToonWriter): Unit = out.writeVal(x)

    override def decodeKey(in: ToonReader): Double = in.readKeyAsDouble()

    override def encodeKey(x: Double, out: ToonWriter): Unit = out.writeKey(x)
  }

  val charCodec: ToonBinaryCodec[Char] = new ToonBinaryCodec[Char](ToonBinaryCodec.charType) {
    def decodeValue(in: ToonReader, default: Char): Char = in.readChar()

    def encodeValue(x: Char, out: ToonWriter): Unit = out.writeVal(x)

    override def decodeKey(in: ToonReader): Char = in.readKeyAsChar()

    override def encodeKey(x: Char, out: ToonWriter): Unit = out.writeKey(x)
  }

  val stringCodec: ToonBinaryCodec[String] = new ToonBinaryCodec[String]() {
    def decodeValue(in: ToonReader, default: String): String = in.readString(default)

    def encodeValue(x: String, out: ToonWriter): Unit = out.writeVal(x)

    override def decodeKey(in: ToonReader): String = in.readKeyAsString()

    override def encodeKey(x: String, out: ToonWriter): Unit = out.writeKey(x)
  }

  val bigIntCodec: ToonBinaryCodec[BigInt] = new ToonBinaryCodec[BigInt]() {
    def decodeValue(in: ToonReader, default: BigInt): BigInt = in.readBigInt(default)

    def encodeValue(x: BigInt, out: ToonWriter): Unit = out.writeVal(x)

    override def decodeKey(in: ToonReader): BigInt = in.readKeyAsBigInt()

    override def encodeKey(x: BigInt, out: ToonWriter): Unit = out.writeKey(x)
  }

  val bigDecimalCodec: ToonBinaryCodec[BigDecimal] = new ToonBinaryCodec[BigDecimal]() {
    def decodeValue(in: ToonReader, default: BigDecimal): BigDecimal = in.readBigDecimal(default)

    def encodeValue(x: BigDecimal, out: ToonWriter): Unit = out.writeVal(x)

    override def decodeKey(in: ToonReader): BigDecimal = in.readKeyAsBigDecimal()

    override def encodeKey(x: BigDecimal, out: ToonWriter): Unit = out.writeKey(x)
  }

  val dayOfWeekCodec: ToonBinaryCodec[DayOfWeek] = new ToonBinaryCodec[java.time.DayOfWeek]() {
    def decodeValue(in: ToonReader, default: java.time.DayOfWeek): java.time.DayOfWeek = {
      val code = in.readString(if (default eq null) null else default.toString)
      try java.time.DayOfWeek.valueOf(code)
      catch {
        case error if NonFatal(error) => in.decodeError("illegal day of week value")
      }
    }

    def encodeValue(x: java.time.DayOfWeek, out: ToonWriter): Unit = out.writeNonEscapedAsciiVal(x.toString)

    override def decodeKey(in: ToonReader): java.time.DayOfWeek = {
      val code = in.readKeyAsString()
      try java.time.DayOfWeek.valueOf(code)
      catch {
        case error if NonFatal(error) => in.decodeError("illegal day of week value")
      }
    }

    override def encodeKey(x: java.time.DayOfWeek, out: ToonWriter): Unit = out.writeNonEscapedAsciiKey(x.toString)
  }

  val durationCodec: ToonBinaryCodec[Duration] = new ToonBinaryCodec[java.time.Duration]() {
    def decodeValue(in: ToonReader, default: java.time.Duration): java.time.Duration = in.readDuration(default)

    def encodeValue(x: java.time.Duration, out: ToonWriter): Unit = out.writeVal(x)

    override def decodeKey(in: ToonReader): java.time.Duration = in.readKeyAsDuration()

    override def encodeKey(x: java.time.Duration, out: ToonWriter): Unit = out.writeKey(x)
  }

  val instantCodec: ToonBinaryCodec[Instant] = new ToonBinaryCodec[java.time.Instant]() {
    def decodeValue(in: ToonReader, default: java.time.Instant): java.time.Instant = in.readInstant(default)

    def encodeValue(x: java.time.Instant, out: ToonWriter): Unit = out.writeVal(x)

    override def decodeKey(in: ToonReader): java.time.Instant = in.readKeyAsInstant()

    override def encodeKey(x: java.time.Instant, out: ToonWriter): Unit = out.writeKey(x)
  }

  val localDateCodec: ToonBinaryCodec[LocalDate] = new ToonBinaryCodec[java.time.LocalDate]() {
    def decodeValue(in: ToonReader, default: java.time.LocalDate): java.time.LocalDate = in.readLocalDate(default)

    def encodeValue(x: java.time.LocalDate, out: ToonWriter): Unit = out.writeVal(x)

    override def decodeKey(in: ToonReader): java.time.LocalDate = in.readKeyAsLocalDate()

    override def encodeKey(x: java.time.LocalDate, out: ToonWriter): Unit = out.writeKey(x)
  }

  val localDateTimeCodec: ToonBinaryCodec[LocalDateTime] = new ToonBinaryCodec[java.time.LocalDateTime]() {
    def decodeValue(in: ToonReader, default: java.time.LocalDateTime): java.time.LocalDateTime =
      in.readLocalDateTime(default)

    def encodeValue(x: java.time.LocalDateTime, out: ToonWriter): Unit = out.writeVal(x)

    override def decodeKey(in: ToonReader): java.time.LocalDateTime = in.readKeyAsLocalDateTime()

    override def encodeKey(x: java.time.LocalDateTime, out: ToonWriter): Unit = out.writeKey(x)
  }

  val localTimeCodec: ToonBinaryCodec[LocalTime] = new ToonBinaryCodec[java.time.LocalTime]() {
    def decodeValue(in: ToonReader, default: java.time.LocalTime): java.time.LocalTime = in.readLocalTime(default)

    def encodeValue(x: java.time.LocalTime, out: ToonWriter): Unit = out.writeVal(x)

    override def decodeKey(in: ToonReader): java.time.LocalTime = in.readKeyAsLocalTime()

    override def encodeKey(x: java.time.LocalTime, out: ToonWriter): Unit = out.writeKey(x)
  }

  val monthCodec: ToonBinaryCodec[Month] = new ToonBinaryCodec[java.time.Month]() {
    def decodeValue(in: ToonReader, default: java.time.Month): java.time.Month = {
      val code = in.readString(if (default eq null) null else default.toString)
      try java.time.Month.valueOf(code)
      catch {
        case error if NonFatal(error) => in.decodeError("illegal month value")
      }
    }

    def encodeValue(x: java.time.Month, out: ToonWriter): Unit = out.writeNonEscapedAsciiVal(x.toString)

    override def decodeKey(in: ToonReader): java.time.Month = {
      val code = in.readKeyAsString()
      try java.time.Month.valueOf(code)
      catch {
        case error if NonFatal(error) => in.decodeError("illegal month value")
      }
    }

    override def encodeKey(x: java.time.Month, out: ToonWriter): Unit = out.writeNonEscapedAsciiKey(x.toString)
  }

  val monthDayCodec: ToonBinaryCodec[MonthDay] = new ToonBinaryCodec[java.time.MonthDay]() {
    def decodeValue(in: ToonReader, default: java.time.MonthDay): java.time.MonthDay = in.readMonthDay(default)

    def encodeValue(x: java.time.MonthDay, out: ToonWriter): Unit = out.writeVal(x)

    override def decodeKey(in: ToonReader): java.time.MonthDay = in.readKeyAsMonthDay()

    override def encodeKey(x: java.time.MonthDay, out: ToonWriter): Unit = out.writeKey(x)
  }

  val offsetDateTimeCodec: ToonBinaryCodec[OffsetDateTime] = new ToonBinaryCodec[java.time.OffsetDateTime]() {
    def decodeValue(in: ToonReader, default: java.time.OffsetDateTime): java.time.OffsetDateTime =
      in.readOffsetDateTime(default)

    def encodeValue(x: java.time.OffsetDateTime, out: ToonWriter): Unit = out.writeVal(x)

    override def decodeKey(in: ToonReader): java.time.OffsetDateTime = in.readKeyAsOffsetDateTime()

    override def encodeKey(x: java.time.OffsetDateTime, out: ToonWriter): Unit = out.writeKey(x)
  }

  val offsetTimeCodec: ToonBinaryCodec[OffsetTime] = new ToonBinaryCodec[java.time.OffsetTime]() {
    def decodeValue(in: ToonReader, default: java.time.OffsetTime): java.time.OffsetTime = in.readOffsetTime(default)

    def encodeValue(x: java.time.OffsetTime, out: ToonWriter): Unit = out.writeVal(x)

    override def decodeKey(in: ToonReader): java.time.OffsetTime = in.readKeyAsOffsetTime()

    override def encodeKey(x: java.time.OffsetTime, out: ToonWriter): Unit = out.writeKey(x)
  }

  val periodCodec: ToonBinaryCodec[Period] = new ToonBinaryCodec[java.time.Period]() {
    def decodeValue(in: ToonReader, default: java.time.Period): java.time.Period = in.readPeriod(default)

    def encodeValue(x: java.time.Period, out: ToonWriter): Unit = out.writeVal(x)

    override def decodeKey(in: ToonReader): java.time.Period = in.readKeyAsPeriod()

    override def encodeKey(x: java.time.Period, out: ToonWriter): Unit = out.writeKey(x)
  }

  val yearCodec: ToonBinaryCodec[Year] = new ToonBinaryCodec[java.time.Year]() {
    def decodeValue(in: ToonReader, default: java.time.Year): java.time.Year = in.readYear(default)

    def encodeValue(x: java.time.Year, out: ToonWriter): Unit = out.writeVal(x)

    override def decodeKey(in: ToonReader): java.time.Year = in.readKeyAsYear()

    override def encodeKey(x: java.time.Year, out: ToonWriter): Unit = out.writeKey(x)
  }

  val yearMonthCodec: ToonBinaryCodec[YearMonth] = new ToonBinaryCodec[java.time.YearMonth]() {
    def decodeValue(in: ToonReader, default: java.time.YearMonth): java.time.YearMonth = in.readYearMonth(default)

    def encodeValue(x: java.time.YearMonth, out: ToonWriter): Unit = out.writeVal(x)

    override def decodeKey(in: ToonReader): java.time.YearMonth = in.readKeyAsYearMonth()

    override def encodeKey(x: java.time.YearMonth, out: ToonWriter): Unit = out.writeKey(x)
  }

  val zoneIdCodec: ToonBinaryCodec[ZoneId] = new ToonBinaryCodec[java.time.ZoneId]() {
    def decodeValue(in: ToonReader, default: java.time.ZoneId): java.time.ZoneId = in.readZoneId(default)

    def encodeValue(x: java.time.ZoneId, out: ToonWriter): Unit = out.writeVal(x)

    override def decodeKey(in: ToonReader): java.time.ZoneId = in.readKeyAsZoneId()

    override def encodeKey(x: java.time.ZoneId, out: ToonWriter): Unit = out.writeKey(x)
  }

  val zoneOffsetCodec: ToonBinaryCodec[ZoneOffset] = new ToonBinaryCodec[java.time.ZoneOffset]() {
    def decodeValue(in: ToonReader, default: java.time.ZoneOffset): java.time.ZoneOffset = in.readZoneOffset(default)

    def encodeValue(x: java.time.ZoneOffset, out: ToonWriter): Unit = out.writeVal(x)

    override def decodeKey(in: ToonReader): java.time.ZoneOffset = in.readKeyAsZoneOffset()

    override def encodeKey(x: java.time.ZoneOffset, out: ToonWriter): Unit = out.writeKey(x)
  }

  val zonedDateTimeCodec: ToonBinaryCodec[ZonedDateTime] = new ToonBinaryCodec[java.time.ZonedDateTime]() {
    def decodeValue(in: ToonReader, default: java.time.ZonedDateTime): java.time.ZonedDateTime =
      in.readZonedDateTime(default)

    def encodeValue(x: java.time.ZonedDateTime, out: ToonWriter): Unit = out.writeVal(x)

    override def decodeKey(in: ToonReader): java.time.ZonedDateTime = in.readKeyAsZonedDateTime()

    override def encodeKey(x: java.time.ZonedDateTime, out: ToonWriter): Unit = out.writeKey(x)
  }

  val currencyCodec: ToonBinaryCodec[Currency] = new ToonBinaryCodec[java.util.Currency]() {
    def decodeValue(in: ToonReader, default: java.util.Currency): java.util.Currency = {
      val code = in.readString(if (default eq null) null else default.getCurrencyCode)
      try java.util.Currency.getInstance(code)
      catch {
        case error if NonFatal(error) => in.decodeError("illegal currency code")
      }
    }

    def encodeValue(x: java.util.Currency, out: ToonWriter): Unit = out.writeNonEscapedAsciiVal(x.getCurrencyCode)

    override def decodeKey(in: ToonReader): java.util.Currency = {
      val code = in.readKeyAsString()
      try java.util.Currency.getInstance(code)
      catch {
        case error if NonFatal(error) => in.decodeError("illegal currency code")
      }
    }

    override def encodeKey(x: java.util.Currency, out: ToonWriter): Unit =
      out.writeNonEscapedAsciiKey(x.getCurrencyCode)
  }

  val uuidCodec: ToonBinaryCodec[UUID] = new ToonBinaryCodec[java.util.UUID]() {
    def decodeValue(in: ToonReader, default: java.util.UUID): java.util.UUID = in.readUUID(default)

    def encodeValue(x: java.util.UUID, out: ToonWriter): Unit = out.writeVal(x)

    override def decodeKey(in: ToonReader): java.util.UUID = in.readKeyAsUUID()

    override def encodeKey(x: java.util.UUID, out: ToonWriter): Unit = out.writeKey(x)
  }
}

/**
 * Internal error class for TOON codec errors with path tracking.
 */
private[toon] class ToonBinaryCodecError(var spans: List[DynamicOptic.Node], message: String)
    extends Throwable(message, null, false, false) {
  override def getMessage: String = message
}
