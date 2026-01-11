package zio.blocks.schema.toon

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, InputStream}
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import zio.blocks.schema.SchemaError
import zio.blocks.schema.codec.BinaryCodec

abstract class ToonBinaryCodec[A] extends BinaryCodec[A] {
  def decodeValue(in: ToonReader, default: A): A
  def encodeValue(x: A, out: ToonWriter): Unit

  def decodeKey(in: ToonReader): A           = ???
  def encodeKey(x: A, out: ToonWriter): Unit = ???

  override def decode(input: ByteBuffer): Either[SchemaError, A] = {
    // Simple wrapper: ByteBuffer -> bytes -> InputStream
    val bytes = new Array[Byte](input.remaining())
    input.get(bytes)
    decode(new ByteArrayInputStream(bytes))
  }

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

  def encodeToBytes(value: A): Array[Byte] = {
    val out    = new ByteArrayOutputStream()
    val writer = new ToonWriter(ToonWriterConfig.default, out)
    encodeValue(value, writer)
    writer.flush()
    out.toByteArray
  }

  def encodeToString(value: A): String =
    new String(encodeToBytes(value), StandardCharsets.UTF_8)
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

  // Pre-allocated byte arrays for common strings
  private[this] val TRUE_BYTES  = "true".getBytes(java.nio.charset.StandardCharsets.UTF_8)
  private[this] val FALSE_BYTES = "false".getBytes(java.nio.charset.StandardCharsets.UTF_8)
  private[this] val NULL_BYTES  = "null".getBytes(java.nio.charset.StandardCharsets.UTF_8)

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

  val stringCodec: ToonBinaryCodec[String] = new ToonBinaryCodec[String] {
    override def decodeValue(in: ToonReader, default: String): String = in.readString()
    override def encodeValue(x: String, out: ToonWriter): Unit        =
      if (requiresQuoting(x)) {
        out.writeQuotedString(x)
      } else {
        out.writeRaw(x)
      }
  }

  val intCodec: ToonBinaryCodec[Int] = new ToonBinaryCodec[Int] {
    override def decodeValue(in: ToonReader, default: Int): Int = in.readInt()
    override def encodeValue(x: Int, out: ToonWriter): Unit     = out.writeRaw(intToString(x))
  }

  val longCodec: ToonBinaryCodec[Long] = new ToonBinaryCodec[Long] {
    override def decodeValue(in: ToonReader, default: Long): Long = in.readString().toLong
    override def encodeValue(x: Long, out: ToonWriter): Unit      =
      // Use cached string for small values
      if (x >= 0 && x < 1000) out.writeRaw(SMALL_INT_STRINGS(x.toInt))
      else out.writeRaw(x.toString)
  }

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

  val bigIntCodec: ToonBinaryCodec[BigInt] = new ToonBinaryCodec[BigInt] {
    override def decodeValue(in: ToonReader, default: BigInt): BigInt = BigInt(in.readString())
    override def encodeValue(x: BigInt, out: ToonWriter): Unit        =
      out.writeRaw(x.toString)
  }

  val bigDecimalCodec: ToonBinaryCodec[BigDecimal] = new ToonBinaryCodec[BigDecimal] {
    override def decodeValue(in: ToonReader, default: BigDecimal): BigDecimal = BigDecimal(in.readString())
    override def encodeValue(x: BigDecimal, out: ToonWriter): Unit            =
      // Use toPlainString to avoid scientific notation
      out.writeRaw(x.bigDecimal.toPlainString)
  }

  val booleanCodec: ToonBinaryCodec[Boolean] = new ToonBinaryCodec[Boolean] {
    override def decodeValue(in: ToonReader, default: Boolean): Boolean = in.readBoolean()
    override def encodeValue(x: Boolean, out: ToonWriter): Unit         =
      // Use pre-computed string
      if (x) out.writeRaw("true") else out.writeRaw("false")
  }

  val unitCodec: ToonBinaryCodec[Unit] = new ToonBinaryCodec[Unit] {
    override def decodeValue(in: ToonReader, default: Unit): Unit = ()
    override def encodeValue(x: Unit, out: ToonWriter): Unit      = () // Write nothing
  }

  // java.time codecs - all use ISO-8601 format and are quoted
  val instantCodec: ToonBinaryCodec[java.time.Instant] = new ToonBinaryCodec[java.time.Instant] {
    override def decodeValue(in: ToonReader, default: java.time.Instant): java.time.Instant =
      java.time.Instant.parse(in.readString())
    override def encodeValue(x: java.time.Instant, out: ToonWriter): Unit =
      out.writeQuotedString(x.toString)
  }

  val localDateCodec: ToonBinaryCodec[java.time.LocalDate] = new ToonBinaryCodec[java.time.LocalDate] {
    override def decodeValue(in: ToonReader, default: java.time.LocalDate): java.time.LocalDate =
      java.time.LocalDate.parse(in.readString())
    override def encodeValue(x: java.time.LocalDate, out: ToonWriter): Unit =
      out.writeQuotedString(x.toString)
  }

  val localTimeCodec: ToonBinaryCodec[java.time.LocalTime] = new ToonBinaryCodec[java.time.LocalTime] {
    override def decodeValue(in: ToonReader, default: java.time.LocalTime): java.time.LocalTime =
      java.time.LocalTime.parse(in.readString())
    override def encodeValue(x: java.time.LocalTime, out: ToonWriter): Unit =
      out.writeQuotedString(x.toString)
  }

  val localDateTimeCodec: ToonBinaryCodec[java.time.LocalDateTime] = new ToonBinaryCodec[java.time.LocalDateTime] {
    override def decodeValue(in: ToonReader, default: java.time.LocalDateTime): java.time.LocalDateTime =
      java.time.LocalDateTime.parse(in.readString())
    override def encodeValue(x: java.time.LocalDateTime, out: ToonWriter): Unit =
      out.writeQuotedString(x.toString)
  }

  val offsetDateTimeCodec: ToonBinaryCodec[java.time.OffsetDateTime] = new ToonBinaryCodec[java.time.OffsetDateTime] {
    override def decodeValue(in: ToonReader, default: java.time.OffsetDateTime): java.time.OffsetDateTime =
      java.time.OffsetDateTime.parse(in.readString())
    override def encodeValue(x: java.time.OffsetDateTime, out: ToonWriter): Unit =
      out.writeQuotedString(x.toString)
  }

  val zonedDateTimeCodec: ToonBinaryCodec[java.time.ZonedDateTime] = new ToonBinaryCodec[java.time.ZonedDateTime] {
    override def decodeValue(in: ToonReader, default: java.time.ZonedDateTime): java.time.ZonedDateTime =
      java.time.ZonedDateTime.parse(in.readString())
    override def encodeValue(x: java.time.ZonedDateTime, out: ToonWriter): Unit =
      out.writeQuotedString(x.toString)
  }

  val durationCodec: ToonBinaryCodec[java.time.Duration] = new ToonBinaryCodec[java.time.Duration] {
    override def decodeValue(in: ToonReader, default: java.time.Duration): java.time.Duration =
      java.time.Duration.parse(in.readString())
    override def encodeValue(x: java.time.Duration, out: ToonWriter): Unit =
      out.writeQuotedString(x.toString)
  }

  val periodCodec: ToonBinaryCodec[java.time.Period] = new ToonBinaryCodec[java.time.Period] {
    override def decodeValue(in: ToonReader, default: java.time.Period): java.time.Period =
      java.time.Period.parse(in.readString())
    override def encodeValue(x: java.time.Period, out: ToonWriter): Unit =
      out.writeQuotedString(x.toString)
  }
}
