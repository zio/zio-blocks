package zio.blocks.schema.toon

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, InputStream}
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import zio.blocks.schema.SchemaError
import zio.blocks.schema.codec.BinaryCodec

/**
 * Abstract base class for TOON binary codecs.
 *
 * Provides encoding and decoding functionality for values of type `A` to and
 * from TOON format.
 *
 * @tparam A
 *   the type of values this codec handles
 */
abstract class ToonBinaryCodec[A] extends BinaryCodec[A] {

  /**
   * Decodes a value from TOON format.
   *
   * {{{
   * val reader = ToonReader("Alice")
   * val name = stringCodec.decodeValue(reader, "")
   * // Result: "Alice"
   * }}}
   *
   * @param in
   *   the TOON reader to read from
   * @param default
   *   the default value to use if decoding fails (may be null)
   * @return
   *   the decoded value
   */
  def decodeValue(in: ToonReader, default: A): A

  /**
   * Encodes a value to TOON format.
   *
   * {{{
   * val writer = ToonWriter()
   * stringCodec.encodeValue("Alice", writer)
   * // Result: "Alice" (or "\"Alice\"" if quoting is required)
   * }}}
   *
   * @param x
   *   the value to encode
   * @param out
   *   the TOON writer to write to
   */
  def encodeValue(x: A, out: ToonWriter): Unit

  /**
   * Decodes a key from TOON format.
   *
   * Used for map keys. Override this method for types that can be used as map
   * keys. Default implementation throws UnsupportedOperationException.
   *
   * @param in
   *   the TOON reader to read from
   * @return
   *   the decoded key
   * @throws UnsupportedOperationException
   *   if this type doesn't support key decoding
   */
  def decodeKey(in: ToonReader): A =
    throw new UnsupportedOperationException(s"Key decoding not supported for ${getClass.getSimpleName}")

  /**
   * Encodes a key to TOON format.
   *
   * Used for map keys. Override this method for types that can be used as map
   * keys. Default implementation throws UnsupportedOperationException.
   *
   * @param x
   *   the key to encode
   * @param out
   *   the TOON writer to write to
   * @throws UnsupportedOperationException
   *   if this type doesn't support key encoding
   */
  def encodeKey(x: A, out: ToonWriter): Unit =
    throw new UnsupportedOperationException(s"Key encoding not supported for ${getClass.getSimpleName}")

  override def decode(input: ByteBuffer): Either[SchemaError, A] = {
    // Simple wrapper: ByteBuffer -> bytes -> InputStream
    val bytes = new Array[Byte](input.remaining())
    input.get(bytes)
    decode(new ByteArrayInputStream(bytes))
  }

  /**
   * Decodes a value from an InputStream containing TOON data.
   *
   * {{{
   * val input = new ByteArrayInputStream("Alice".getBytes)
   * val result = stringCodec.decode(input)
   * // Result: Right("Alice")
   * }}}
   *
   * @param input
   *   the input stream to read from
   * @return
   *   either the decoded value or a schema error
   */
  def decode(input: InputStream): Either[SchemaError, A] =
    try {
      val reader = new ToonReader(ToonReaderConfig.default, input)
      Right(decodeValue(reader, null.asInstanceOf[A]))
    } catch {
      case e: Throwable => Left(SchemaError.expectationMismatch(Nil, e.getMessage))
    }

  override def encode(value: A, output: ByteBuffer): Unit = {
    val bytes = encodeToBytes(value)
    output.put(bytes)
  }

  /**
   * Encodes a value to a byte array in TOON format.
   *
   * {{{
   * val bytes = stringCodec.encodeToBytes("Alice")
   * // Result: Array[Byte] containing UTF-8 encoded "Alice"
   * }}}
   *
   * @param value
   *   the value to encode
   * @return
   *   the encoded bytes
   */
  def encodeToBytes(value: A): Array[Byte] = {
    val out    = new ByteArrayOutputStream()
    val writer = new ToonWriter(ToonWriterConfig.default, out)
    encodeValue(value, writer)
    writer.flush()
    out.toByteArray
  }

  /**
   * Encodes a value to a TOON format string.
   *
   * {{{
   * val toon = stringCodec.encodeToString("Alice")
   * // Result: "Alice"
   * }}}
   *
   * @param value
   *   the value to encode
   * @return
   *   the TOON format string
   */
  def encodeToString(value: A): String =
    new String(encodeToBytes(value), StandardCharsets.UTF_8)

  /**
   * Decodes a value from a TOON format string.
   *
   * {{{
   * val result = stringCodec.decodeFromString("Alice")
   * // Result: Right("Alice")
   * }}}
   *
   * @param value
   *   the TOON format string to decode
   * @return
   *   either the decoded value or a schema error
   */
  def decodeFromString(value: String): Either[SchemaError, A] =
    decode(new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8)))
}

object ToonBinaryCodec {

  // Pre-computed character flags for fast quoting check (0-127)
  // true = needs quoting
  private[this] val needsQuote: Array[Boolean] = {
    val arr = new Array[Boolean](128)
    // All control chars need quoting
    var i = 0
    while (i < 32) {
      arr(i) = true
      i += 1
    }
    // Special TOON delimiters
    arr(' ') = true
    arr(':') = true
    arr('"') = true
    arr('\\') = true
    arr(',') = true
    arr('[') = true
    arr(']') = true
    arr('\n') = true
    arr('\r') = true
    arr('\t') = true
    // DEL
    arr(127) = true
    arr
  }

  /**
   * Fast check if string needs quoting. Uses lookup table for ASCII chars,
   * falls back to range check for high chars.
   */
  @inline private[toon] def requiresQuoting(s: String): Boolean = {
    if (s.isEmpty) return true
    val len = s.length
    var i   = 0
    while (i < len) {
      val c = s.charAt(i)
      if (c < 128) {
        if (needsQuote(c)) return true
      } else {
        // Non-ASCII always needs quoting for safety
        return true
      }
      i += 1
    }
    false
  }

  // Cached integer strings for small values (0-999)
  private[this] val SMALL_INT_STRINGS: Array[String] = {
    val arr = new Array[String](1000)
    var i   = 0
    while (i < 1000) {
      arr(i) = i.toString
      i += 1
    }
    arr
  }

  /**
   * Fast integer to string conversion for small values.
   */
  @inline private[toon] def intToString(x: Int): String =
    if (x >= 0 && x < 1000) SMALL_INT_STRINGS(x)
    else x.toString

  /**
   * Codec for String values.
   *
   * Automatically quotes strings that contain special characters.
   *
   * {{{
   * val codec = ToonBinaryCodec.stringCodec
   * codec.encodeToString("hello")      // Result: "hello"
   * codec.encodeToString("hello world") // Result: "\"hello world\""
   * }}}
   */
  val stringCodec: ToonBinaryCodec[String] = new ToonBinaryCodec[String] {
    override def decodeValue(in: ToonReader, default: String): String = in.readString()
    override def encodeValue(x: String, out: ToonWriter): Unit        =
      if (requiresQuoting(x)) {
        out.writeQuotedString(x)
      } else {
        out.writeRaw(x)
      }
    override def decodeKey(in: ToonReader): String           = in.readKey()
    override def encodeKey(x: String, out: ToonWriter): Unit = out.writeRaw(x)
  }

  /**
   * Codec for Byte values.
   *
   * {{{
   * val codec = ToonBinaryCodec.byteCodec
   * codec.encodeToString(42.toByte)  // Result: "42"
   * codec.encodeToString(-128.toByte) // Result: "-128"
   * }}}
   */
  val byteCodec: ToonBinaryCodec[Byte] = new ToonBinaryCodec[Byte] {
    override def decodeValue(in: ToonReader, default: Byte): Byte = in.readInt().toByte
    override def encodeValue(x: Byte, out: ToonWriter): Unit      = out.writeRaw(x.toString)
  }

  /**
   * Codec for Short values.
   *
   * {{{
   * val codec = ToonBinaryCodec.shortCodec
   * codec.encodeToString(1000.toShort) // Result: "1000"
   * codec.encodeToString(-32768.toShort) // Result: "-32768"
   * }}}
   */
  val shortCodec: ToonBinaryCodec[Short] = new ToonBinaryCodec[Short] {
    override def decodeValue(in: ToonReader, default: Short): Short = in.readInt().toShort
    override def encodeValue(x: Short, out: ToonWriter): Unit       = out.writeRaw(x.toString)
  }

  /**
   * Codec for Char values.
   *
   * Encodes as a single-character string.
   *
   * {{{
   * val codec = ToonBinaryCodec.charCodec
   * codec.encodeToString('A') // Result: "A"
   * codec.encodeToString(' ') // Result: "\" \"" (quoted because space)
   * }}}
   */
  val charCodec: ToonBinaryCodec[Char] = new ToonBinaryCodec[Char] {
    override def decodeValue(in: ToonReader, default: Char): Char = {
      val s = in.readString()
      if (s.length == 1) s.charAt(0)
      else throw ToonCodecError.atLine(in.getLine, s"Expected single character, got: $s")
    }
    override def encodeValue(x: Char, out: ToonWriter): Unit = {
      val s = x.toString
      if (requiresQuoting(s)) {
        out.writeQuotedString(s)
      } else {
        out.writeRaw(s)
      }
    }
  }

  /**
   * Codec for Int values.
   *
   * Uses cached string representations for small values (0-999) for
   * performance.
   *
   * {{{
   * val codec = ToonBinaryCodec.intCodec
   * codec.encodeToString(42)   // Result: "42"
   * codec.encodeToString(-100) // Result: "-100"
   * }}}
   */
  val intCodec: ToonBinaryCodec[Int] = new ToonBinaryCodec[Int] {
    override def decodeValue(in: ToonReader, default: Int): Int = in.readInt()
    override def encodeValue(x: Int, out: ToonWriter): Unit     = out.writeRaw(intToString(x))
    override def decodeKey(in: ToonReader): Int                 = {
      // Read key enclosed in brackets: [42]
      in.consume('[')
      val value = in.readInt()
      in.consume(']')
      value
    }
    override def encodeKey(x: Int, out: ToonWriter): Unit = {
      out.writeRaw("[")
      out.writeRaw(intToString(x))
      out.writeRaw("]")
    }
  }

  /**
   * Codec for Long values.
   *
   * Uses cached string representations for small values (0-999) for
   * performance.
   *
   * {{{
   * val codec = ToonBinaryCodec.longCodec
   * codec.encodeToString(42L)          // Result: "42"
   * codec.encodeToString(9999999999L)  // Result: "9999999999"
   * }}}
   */
  val longCodec: ToonBinaryCodec[Long] = new ToonBinaryCodec[Long] {
    override def decodeValue(in: ToonReader, default: Long): Long = in.readString().toLong
    override def encodeValue(x: Long, out: ToonWriter): Unit      =
      // Use cached string for small values
      if (x >= 0 && x < 1000) out.writeRaw(SMALL_INT_STRINGS(x.toInt))
      else out.writeRaw(x.toString)
  }

  /**
   * Codec for Float values.
   *
   * NaN and Infinity are encoded as `null`. Normalizes -0.0 to 0.0.
   *
   * {{{
   * val codec = ToonBinaryCodec.floatCodec
   * codec.encodeToString(3.14f)     // Result: "3.14"
   * codec.encodeToString(Float.NaN) // Result: "null"
   * }}}
   */
  val floatCodec: ToonBinaryCodec[Float] = new ToonBinaryCodec[Float] {
    override def decodeValue(in: ToonReader, default: Float): Float = {
      val s = in.readString()
      if (s == "null") Float.NaN else s.toFloat
    }
    override def encodeValue(x: Float, out: ToonWriter): Unit =
      if (x.isNaN || x.isInfinite) {
        out.writeNull()
      } else {
        // Avoid -0.0 normalization allocation if not needed
        if (java.lang.Float.floatToRawIntBits(x) == 0x80000000) {
          out.writeRaw("0.0")
        } else {
          out.writeRaw(x.toString)
        }
      }
  }

  /**
   * Codec for Double values.
   *
   * NaN and Infinity are encoded as `null`. Normalizes -0.0 to 0.0.
   *
   * {{{
   * val codec = ToonBinaryCodec.doubleCodec
   * codec.encodeToString(3.14159)      // Result: "3.14159"
   * codec.encodeToString(Double.NaN)   // Result: "null"
   * }}}
   */
  val doubleCodec: ToonBinaryCodec[Double] = new ToonBinaryCodec[Double] {
    override def decodeValue(in: ToonReader, default: Double): Double = {
      val s = in.readString()
      if (s == "null") Double.NaN else s.toDouble
    }
    override def encodeValue(x: Double, out: ToonWriter): Unit =
      if (x.isNaN || x.isInfinite) {
        out.writeNull()
      } else {
        // Avoid -0.0 normalization allocation if not needed
        if (java.lang.Double.doubleToRawLongBits(x) == 0x8000000000000000L) {
          out.writeRaw("0.0")
        } else {
          out.writeRaw(x.toString)
        }
      }
  }

  /**
   * Codec for BigInt values.
   *
   * {{{
   * val codec = ToonBinaryCodec.bigIntCodec
   * codec.encodeToString(BigInt("123456789012345678901234567890"))
   * // Result: "123456789012345678901234567890"
   * }}}
   */
  val bigIntCodec: ToonBinaryCodec[BigInt] = new ToonBinaryCodec[BigInt] {
    override def decodeValue(in: ToonReader, default: BigInt): BigInt = BigInt(in.readString())
    override def encodeValue(x: BigInt, out: ToonWriter): Unit        =
      out.writeRaw(x.toString)
  }

  /**
   * Codec for BigDecimal values.
   *
   * Uses plain string representation to avoid scientific notation.
   *
   * {{{
   * val codec = ToonBinaryCodec.bigDecimalCodec
   * codec.encodeToString(BigDecimal("123.456")) // Result: "123.456"
   * }}}
   */
  val bigDecimalCodec: ToonBinaryCodec[BigDecimal] = new ToonBinaryCodec[BigDecimal] {
    override def decodeValue(in: ToonReader, default: BigDecimal): BigDecimal = BigDecimal(in.readString())
    override def encodeValue(x: BigDecimal, out: ToonWriter): Unit            =
      // Use toPlainString to avoid scientific notation
      out.writeRaw(x.bigDecimal.toPlainString)
  }

  /**
   * Codec for Boolean values.
   *
   * {{{
   * val codec = ToonBinaryCodec.booleanCodec
   * codec.encodeToString(true)  // Result: "true"
   * codec.encodeToString(false) // Result: "false"
   * }}}
   */
  val booleanCodec: ToonBinaryCodec[Boolean] = new ToonBinaryCodec[Boolean] {
    override def decodeValue(in: ToonReader, default: Boolean): Boolean = in.readBoolean()
    override def encodeValue(x: Boolean, out: ToonWriter): Unit         =
      // Use pre-computed string
      if (x) out.writeRaw("true") else out.writeRaw("false")
  }

  /**
   * Codec for Unit values.
   *
   * Encodes as empty string, decodes by ignoring input.
   */
  val unitCodec: ToonBinaryCodec[Unit] = new ToonBinaryCodec[Unit] {
    override def decodeValue(in: ToonReader, default: Unit): Unit = ()
    override def encodeValue(x: Unit, out: ToonWriter): Unit      = () // Write nothing
  }

  // java.time codecs - all use ISO-8601 format and are quoted

  /**
   * Codec for java.time.Instant values.
   *
   * Uses ISO-8601 format.
   *
   * {{{
   * val codec = ToonBinaryCodec.instantCodec
   * codec.encodeToString(Instant.parse("2023-01-15T10:30:00Z"))
   * // Result: "\"2023-01-15T10:30:00Z\""
   * }}}
   */
  val instantCodec: ToonBinaryCodec[java.time.Instant] = new ToonBinaryCodec[java.time.Instant] {
    override def decodeValue(in: ToonReader, default: java.time.Instant): java.time.Instant =
      java.time.Instant.parse(in.readString())
    override def encodeValue(x: java.time.Instant, out: ToonWriter): Unit =
      out.writeQuotedString(x.toString)
  }

  /**
   * Codec for java.time.LocalDate values.
   *
   * Uses ISO-8601 format (yyyy-MM-dd).
   *
   * {{{
   * val codec = ToonBinaryCodec.localDateCodec
   * codec.encodeToString(LocalDate.of(2023, 1, 15))
   * // Result: "\"2023-01-15\""
   * }}}
   */
  val localDateCodec: ToonBinaryCodec[java.time.LocalDate] = new ToonBinaryCodec[java.time.LocalDate] {
    override def decodeValue(in: ToonReader, default: java.time.LocalDate): java.time.LocalDate =
      java.time.LocalDate.parse(in.readString())
    override def encodeValue(x: java.time.LocalDate, out: ToonWriter): Unit =
      out.writeQuotedString(x.toString)
  }

  /**
   * Codec for java.time.LocalTime values.
   *
   * Uses ISO-8601 format (HH:mm:ss).
   *
   * {{{
   * val codec = ToonBinaryCodec.localTimeCodec
   * codec.encodeToString(LocalTime.of(10, 30, 0))
   * // Result: "\"10:30:00\""
   * }}}
   */
  val localTimeCodec: ToonBinaryCodec[java.time.LocalTime] = new ToonBinaryCodec[java.time.LocalTime] {
    override def decodeValue(in: ToonReader, default: java.time.LocalTime): java.time.LocalTime =
      java.time.LocalTime.parse(in.readString())
    override def encodeValue(x: java.time.LocalTime, out: ToonWriter): Unit =
      out.writeQuotedString(x.toString)
  }

  /**
   * Codec for java.time.LocalDateTime values.
   *
   * Uses ISO-8601 format (yyyy-MM-ddTHH:mm:ss).
   *
   * {{{
   * val codec = ToonBinaryCodec.localDateTimeCodec
   * codec.encodeToString(LocalDateTime.of(2023, 1, 15, 10, 30))
   * // Result: "\"2023-01-15T10:30:00\""
   * }}}
   */
  val localDateTimeCodec: ToonBinaryCodec[java.time.LocalDateTime] = new ToonBinaryCodec[java.time.LocalDateTime] {
    override def decodeValue(in: ToonReader, default: java.time.LocalDateTime): java.time.LocalDateTime =
      java.time.LocalDateTime.parse(in.readString())
    override def encodeValue(x: java.time.LocalDateTime, out: ToonWriter): Unit =
      out.writeQuotedString(x.toString)
  }

  /**
   * Codec for java.time.OffsetDateTime values.
   *
   * Uses ISO-8601 format with offset.
   *
   * {{{
   * val codec = ToonBinaryCodec.offsetDateTimeCodec
   * codec.encodeToString(OffsetDateTime.parse("2023-01-15T10:30:00+01:00"))
   * // Result: "\"2023-01-15T10:30:00+01:00\""
   * }}}
   */
  val offsetDateTimeCodec: ToonBinaryCodec[java.time.OffsetDateTime] = new ToonBinaryCodec[java.time.OffsetDateTime] {
    override def decodeValue(in: ToonReader, default: java.time.OffsetDateTime): java.time.OffsetDateTime =
      java.time.OffsetDateTime.parse(in.readString())
    override def encodeValue(x: java.time.OffsetDateTime, out: ToonWriter): Unit =
      out.writeQuotedString(x.toString)
  }

  /**
   * Codec for java.time.ZonedDateTime values.
   *
   * Uses ISO-8601 format with zone.
   *
   * {{{
   * val codec = ToonBinaryCodec.zonedDateTimeCodec
   * codec.encodeToString(ZonedDateTime.parse("2023-01-15T10:30:00+01:00[Europe/Paris]"))
   * // Result: "\"2023-01-15T10:30:00+01:00[Europe/Paris]\""
   * }}}
   */
  val zonedDateTimeCodec: ToonBinaryCodec[java.time.ZonedDateTime] = new ToonBinaryCodec[java.time.ZonedDateTime] {
    override def decodeValue(in: ToonReader, default: java.time.ZonedDateTime): java.time.ZonedDateTime =
      java.time.ZonedDateTime.parse(in.readString())
    override def encodeValue(x: java.time.ZonedDateTime, out: ToonWriter): Unit =
      out.writeQuotedString(x.toString)
  }

  /**
   * Codec for java.time.Duration values.
   *
   * Uses ISO-8601 duration format (PT...).
   *
   * {{{
   * val codec = ToonBinaryCodec.durationCodec
   * codec.encodeToString(Duration.ofHours(2))
   * // Result: "\"PT2H\""
   * }}}
   */
  val durationCodec: ToonBinaryCodec[java.time.Duration] = new ToonBinaryCodec[java.time.Duration] {
    override def decodeValue(in: ToonReader, default: java.time.Duration): java.time.Duration =
      java.time.Duration.parse(in.readString())
    override def encodeValue(x: java.time.Duration, out: ToonWriter): Unit =
      out.writeQuotedString(x.toString)
  }

  /**
   * Codec for java.time.Period values.
   *
   * Uses ISO-8601 period format (P...).
   *
   * {{{
   * val codec = ToonBinaryCodec.periodCodec
   * codec.encodeToString(Period.ofDays(7))
   * // Result: "\"P7D\""
   * }}}
   */
  val periodCodec: ToonBinaryCodec[java.time.Period] = new ToonBinaryCodec[java.time.Period] {
    override def decodeValue(in: ToonReader, default: java.time.Period): java.time.Period =
      java.time.Period.parse(in.readString())
    override def encodeValue(x: java.time.Period, out: ToonWriter): Unit =
      out.writeQuotedString(x.toString)
  }

  /**
   * Codec for java.time.ZoneId values.
   *
   * Encodes zone IDs as strings (e.g., "America/New_York", "UTC").
   *
   * {{{
   * val codec = ToonBinaryCodec.zoneIdCodec
   * codec.encodeToString(ZoneId.of("America/New_York"))
   * // Result: "\"America/New_York\""
   * }}}
   */
  val zoneIdCodec: ToonBinaryCodec[java.time.ZoneId] = new ToonBinaryCodec[java.time.ZoneId] {
    override def decodeValue(in: ToonReader, default: java.time.ZoneId): java.time.ZoneId =
      java.time.ZoneId.of(in.readString())
    override def encodeValue(x: java.time.ZoneId, out: ToonWriter): Unit =
      out.writeQuotedString(x.getId)
  }

  /**
   * Codec for java.time.ZoneOffset values.
   *
   * Encodes zone offsets as strings (e.g., "+01:00", "Z").
   *
   * {{{
   * val codec = ToonBinaryCodec.zoneOffsetCodec
   * codec.encodeToString(ZoneOffset.of("+01:00"))
   * // Result: "\"+01:00\""
   * }}}
   */
  val zoneOffsetCodec: ToonBinaryCodec[java.time.ZoneOffset] = new ToonBinaryCodec[java.time.ZoneOffset] {
    override def decodeValue(in: ToonReader, default: java.time.ZoneOffset): java.time.ZoneOffset =
      java.time.ZoneOffset.of(in.readString())
    override def encodeValue(x: java.time.ZoneOffset, out: ToonWriter): Unit =
      out.writeQuotedString(x.getId)
  }

  /**
   * Codec for java.util.UUID values.
   *
   * Encodes UUIDs in their canonical string format.
   *
   * {{{
   * val codec = ToonBinaryCodec.uuidCodec
   * codec.encodeToString(UUID.randomUUID())
   * // Result: "\"550e8400-e29b-41d4-a716-446655440000\""
   * }}}
   */
  val uuidCodec: ToonBinaryCodec[java.util.UUID] = new ToonBinaryCodec[java.util.UUID] {
    override def decodeValue(in: ToonReader, default: java.util.UUID): java.util.UUID =
      java.util.UUID.fromString(in.readString())
    override def encodeValue(x: java.util.UUID, out: ToonWriter): Unit =
      out.writeQuotedString(x.toString)
    override def decodeKey(in: ToonReader): java.util.UUID =
      java.util.UUID.fromString(in.readKey())
    override def encodeKey(x: java.util.UUID, out: ToonWriter): Unit =
      out.writeRaw(x.toString)
  }

  /**
   * Codec for java.util.Currency values.
   *
   * Encodes currencies by their ISO 4217 currency code.
   *
   * {{{
   * val codec = ToonBinaryCodec.currencyCodec
   * codec.encodeToString(Currency.getInstance("USD"))
   * // Result: "USD"
   * }}}
   */
  val currencyCodec: ToonBinaryCodec[java.util.Currency] = new ToonBinaryCodec[java.util.Currency] {
    override def decodeValue(in: ToonReader, default: java.util.Currency): java.util.Currency =
      java.util.Currency.getInstance(in.readString())
    override def encodeValue(x: java.util.Currency, out: ToonWriter): Unit =
      out.writeRaw(x.getCurrencyCode)
    override def decodeKey(in: ToonReader): java.util.Currency =
      java.util.Currency.getInstance(in.readKey())
    override def encodeKey(x: java.util.Currency, out: ToonWriter): Unit =
      out.writeRaw(x.getCurrencyCode)
  }

  /**
   * Codec for java.time.DayOfWeek values.
   */
  val dayOfWeekCodec: ToonBinaryCodec[java.time.DayOfWeek] = new ToonBinaryCodec[java.time.DayOfWeek] {
    override def decodeValue(in: ToonReader, default: java.time.DayOfWeek): java.time.DayOfWeek =
      java.time.DayOfWeek.valueOf(in.readString().toUpperCase)
    override def encodeValue(x: java.time.DayOfWeek, out: ToonWriter): Unit =
      out.writeRaw(x.name())
  }

  /**
   * Codec for java.time.Month values.
   */
  val monthCodec: ToonBinaryCodec[java.time.Month] = new ToonBinaryCodec[java.time.Month] {
    override def decodeValue(in: ToonReader, default: java.time.Month): java.time.Month =
      java.time.Month.valueOf(in.readString().toUpperCase)
    override def encodeValue(x: java.time.Month, out: ToonWriter): Unit =
      out.writeRaw(x.name())
  }

  /**
   * Codec for java.time.Year values.
   */
  val yearCodec: ToonBinaryCodec[java.time.Year] = new ToonBinaryCodec[java.time.Year] {
    override def decodeValue(in: ToonReader, default: java.time.Year): java.time.Year =
      java.time.Year.of(in.readInt())
    override def encodeValue(x: java.time.Year, out: ToonWriter): Unit =
      out.writeRaw(x.getValue.toString)
  }

  /**
   * Codec for java.time.YearMonth values.
   */
  val yearMonthCodec: ToonBinaryCodec[java.time.YearMonth] = new ToonBinaryCodec[java.time.YearMonth] {
    override def decodeValue(in: ToonReader, default: java.time.YearMonth): java.time.YearMonth =
      java.time.YearMonth.parse(in.readString())
    override def encodeValue(x: java.time.YearMonth, out: ToonWriter): Unit =
      out.writeQuotedString(x.toString)
  }

  /**
   * Codec for java.time.MonthDay values.
   */
  val monthDayCodec: ToonBinaryCodec[java.time.MonthDay] = new ToonBinaryCodec[java.time.MonthDay] {
    override def decodeValue(in: ToonReader, default: java.time.MonthDay): java.time.MonthDay =
      java.time.MonthDay.parse(in.readString())
    override def encodeValue(x: java.time.MonthDay, out: ToonWriter): Unit =
      out.writeQuotedString(x.toString)
  }

  /**
   * Codec for java.time.OffsetTime values.
   */
  val offsetTimeCodec: ToonBinaryCodec[java.time.OffsetTime] = new ToonBinaryCodec[java.time.OffsetTime] {
    override def decodeValue(in: ToonReader, default: java.time.OffsetTime): java.time.OffsetTime =
      java.time.OffsetTime.parse(in.readString())
    override def encodeValue(x: java.time.OffsetTime, out: ToonWriter): Unit =
      out.writeQuotedString(x.toString)
  }
}
