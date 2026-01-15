package zio.blocks.schema.json

import java.io.OutputStream
import java.math.BigInteger
import java.nio.{BufferOverflowException, ByteBuffer}
import java.time._
import java.util.UUID
import zio.blocks.schema.binding.RegisterOffset
import zio.blocks.schema.binding.RegisterOffset.RegisterOffset
import zio.blocks.schema.binding.Registers
import zio.blocks.schema.json.JsonWriter._
import scala.annotation.{nowarn, tailrec}
import java.nio.charset.StandardCharsets.UTF_8
import java.lang.Long.compareUnsigned

/**
 * A writer for iterative serialization of JSON keys and values.
 *
 * @param buf
 *   an internal buffer for writing JSON data
 * @param count
 *   the current position in the internal buffer
 * @param limit
 *   the last position in the internal buffer
 * @param stack
 *   a pre-allocated stack of registers
 * @param top
 *   an offset of the stack top
 * @param maxTop
 *   a maximum offset of the stack top
 * @param config
 *   a writer configuration
 * @param indention
 *   the current indention level
 * @param comma
 *   a flag indicating if comma should precede the next element
 * @param disableBufGrowing
 *   a flag indicating if growing of the internal buffer is disabled
 * @param bbuf
 *   a byte buffer for writing JSON data
 * @param out
 *   the output stream for writing JSON data
 */
final class JsonWriter private[json] (
  private[this] var buf: Array[Byte] = new Array[Byte](32768),
  private[this] var count: Int = 0,
  private[this] var limit: Int = 32768,
  private[this] val stack: Registers = Registers(RegisterOffset(objects = 64, ints = 64)),
  private[this] var top: RegisterOffset = -1L,
  private[this] var maxTop: RegisterOffset = 0L,
  private[this] var config: WriterConfig = null,
  private[this] var indention: Int = 0,
  private[this] var comma: Boolean = false,
  private[this] var disableBufGrowing: Boolean = false,
  private[this] var bbuf: ByteBuffer = null,
  private[this] var out: OutputStream = null
) {

  /**
   * Writes a `Boolean` value as a JSON key.
   *
   * @param x
   *   the `Boolean` value to write
   */
  def writeKey(x: Boolean): Unit = {
    writeOptionalCommaAndIndentionBeforeKey()
    writeBytes('"')
    writeBoolean(x)
    writeParenthesesWithColon()
  }

  /**
   * Writes a `Byte` value as a JSON key.
   *
   * @param x
   *   the `Byte` value to write
   */
  def writeKey(x: Byte): Unit = {
    writeOptionalCommaAndIndentionBeforeKey()
    writeBytes('"')
    writeByte(x)
    writeParenthesesWithColon()
  }

  /**
   * Writes a `Char` value as a JSON key.
   *
   * @param x
   *   the `Char` value to write
   * @throws JsonBinaryCodecError
   *   in the case of `Char` value is a part of the surrogate pair
   */
  def writeKey(x: Char): Unit = {
    writeOptionalCommaAndIndentionBeforeKey()
    writeChar(x.toInt)
    writeColon()
  }

  /**
   * Writes a `Short` value as a JSON key.
   *
   * @param x
   *   the `Short` value to write
   */
  def writeKey(x: Short): Unit = {
    writeOptionalCommaAndIndentionBeforeKey()
    writeBytes('"')
    writeShort(x)
    writeParenthesesWithColon()
  }

  /**
   * Writes an `Int` value as a JSON key.
   *
   * @param x
   *   the `Int` value to write
   */
  def writeKey(x: Int): Unit = {
    writeOptionalCommaAndIndentionBeforeKey()
    writeBytes('"')
    writeInt(x)
    writeParenthesesWithColon()
  }

  /**
   * Writes a `Long` value as a JSON key.
   *
   * @param x
   *   the `Long` value to write
   */
  def writeKey(x: Long): Unit = {
    writeOptionalCommaAndIndentionBeforeKey()
    writeBytes('"')
    writeLong(x)
    writeParenthesesWithColon()
  }

  /**
   * Writes a `Float` value as a JSON key.
   *
   * @param x
   *   the `Float` value to write
   * @throws JsonBinaryCodecError
   *   if the value is non-finite
   */
  def writeKey(x: Float): Unit = {
    writeOptionalCommaAndIndentionBeforeKey()
    writeBytes('"')
    writeFloat(x)
    writeParenthesesWithColon()
  }

  /**
   * Writes a `Double` value as a JSON key.
   *
   * @param x
   *   the `Double` value to write
   * @throws JsonBinaryCodecError
   *   if the value is non-finite
   */
  def writeKey(x: Double): Unit = {
    writeOptionalCommaAndIndentionBeforeKey()
    writeBytes('"')
    writeDouble(x)
    writeParenthesesWithColon()
  }

  /**
   * Writes a `BigInt` value as a JSON key.
   *
   * @param x
   *   the `BigInt` value to write
   */
  def writeKey(x: BigInt): Unit = {
    writeOptionalCommaAndIndentionBeforeKey()
    writeBytes('"')
    if (x.isValidLong) writeLong(x.longValue)
    else writeBigInteger(x.bigInteger, null)
    writeParenthesesWithColon()
  }

  /**
   * Writes a `BigDecimal` value as a JSON key.
   *
   * @param x
   *   the `BigDecimal` value to write
   */
  def writeKey(x: BigDecimal): Unit = {
    writeOptionalCommaAndIndentionBeforeKey()
    writeBytes('"')
    writeBigDecimal(x.bigDecimal)
    writeParenthesesWithColon()
  }

  /**
   * Writes a [[java.util.UUID]] value as a JSON key.
   *
   * @param x
   *   the [[java.util.UUID]] value to write
   */
  def writeKey(x: UUID): Unit = {
    writeOptionalCommaAndIndentionBeforeKey()
    writeUUID(x.getMostSignificantBits, x.getLeastSignificantBits)
    writeColon()
  }

  /**
   * Writes a `String` value as a JSON key.
   *
   * @param x
   *   the `String` value to write
   * @throws JsonBinaryCodecError
   *   if the provided string has an illegal surrogate pair
   */
  def writeKey(x: String): Unit = {
    val indention = this.indention
    var pos       = ensureBufCapacity(indention + 10)
    val buf       = this.buf
    if (comma) {
      comma = false
      buf(pos) = ','
      pos += 1
      if (indention != 0) pos = writeIndention(buf, pos, indention)
    }
    buf(pos) = '"'
    pos += 1
    pos = writeString(x, 0, pos, buf, Math.min(x.length, limit - pos - 1) + pos)
    if (pos + 4 >= limit) pos = flushAndGrowBuf(4, pos)
    ByteArrayAccess.setInt(this.buf, pos, 0x203a22)
    if (indention > 0) pos += 1
    count = pos + 2
  }

  /**
   * Writes a `String` value that doesn't require encoding or escaping as a JSON
   * key.
   *
   * @note
   *   Use [[JsonWriter.isNonEscapedAscii]] for validation if the string is
   *   eligible for writing by this method.
   *
   * @param x
   *   the `String` value to write
   */
  def writeNonEscapedAsciiKey(x: String): Unit = {
    val len       = x.length
    val indention = this.indention
    val required  = indention + len + 10
    if (required <= config.preferredBufSize) {
      var pos = ensureBufCapacity(required)
      val buf = this.buf
      if (comma) {
        comma = false
        buf(pos) = ','
        pos += 1
        if (indention != 0) pos = writeIndention(buf, pos, indention)
      }
      buf(pos) = '"'
      pos += 1
      var i = 0
      while (i < len) {
        buf(pos) = x.charAt(i).toByte
        pos += 1
        i += 1
      }
      ByteArrayAccess.setInt(buf, pos, 0x203a22)
      if (indention > 0) pos += 1
      count = pos + 2
    } else writeLongNonEscapedAsciiKey(x)
  }

  /**
   * Writes a [[java.time.Duration]] value as a JSON key.
   *
   * @param x
   *   the [[java.time.Duration]] value to write
   */
  def writeKey(x: Duration): Unit = {
    writeOptionalCommaAndIndentionBeforeKey()
    writeDuration(x)
    writeColon()
  }

  /**
   * Writes a [[java.time.Duration]] value as a JSON key.
   *
   * @param x
   *   the [[java.time.Duration]] value to write
   */
  def writeKey(x: Instant): Unit = {
    writeOptionalCommaAndIndentionBeforeKey()
    writeInstant(x)
    writeColon()
  }

  /**
   * Writes a [[java.time.LocalDate]] value as a JSON key.
   *
   * @param x
   *   the [[java.time.LocalDate]] value to write
   */
  def writeKey(x: LocalDate): Unit = {
    writeOptionalCommaAndIndentionBeforeKey()
    writeLocalDate(x)
    writeColon()
  }

  /**
   * Writes a [[java.time.LocalDateTime]] value as a JSON key.
   *
   * @param x
   *   the [[java.time.LocalDateTime]] value to write
   */
  def writeKey(x: LocalDateTime): Unit = {
    writeOptionalCommaAndIndentionBeforeKey()
    writeLocalDateTime(x)
    writeColon()
  }

  /**
   * Writes a [[java.time.LocalTime]] value as a JSON key.
   *
   * @param x
   *   the [[java.time.LocalTime]] value to write
   */
  def writeKey(x: LocalTime): Unit = {
    writeOptionalCommaAndIndentionBeforeKey()
    writeLocalTime(x)
    writeColon()
  }

  /**
   * Writes a [[java.time.MonthDay]] value as a JSON key.
   *
   * @param x
   *   the [[java.time.MonthDay]] value to write
   */
  def writeKey(x: MonthDay): Unit = {
    writeOptionalCommaAndIndentionBeforeKey()
    writeMonthDay(x)
    writeColon()
  }

  /**
   * Writes a [[java.time.OffsetDateTime]] value as a JSON key.
   *
   * @param x
   *   the [[java.time.OffsetDateTime]] value to write
   */
  def writeKey(x: OffsetDateTime): Unit = {
    writeOptionalCommaAndIndentionBeforeKey()
    writeOffsetDateTime(x)
    writeColon()
  }

  /**
   * Writes a [[java.time.OffsetTime]] value as a JSON key.
   *
   * @param x
   *   the [[java.time.OffsetTime]] value to write
   */
  def writeKey(x: OffsetTime): Unit = {
    writeOptionalCommaAndIndentionBeforeKey()
    writeOffsetTime(x)
    writeColon()
  }

  /**
   * Writes a [[java.time.Period]] value as a JSON key.
   *
   * @param x
   *   the [[java.time.Period]] value to write
   */
  def writeKey(x: Period): Unit = {
    writeOptionalCommaAndIndentionBeforeKey()
    writePeriod(x)
    writeColon()
  }

  /**
   * Writes a [[java.time.Year]] value as a JSON key.
   *
   * @param x
   *   the [[java.time.Year]] value to write
   */
  def writeKey(x: Year): Unit = {
    writeOptionalCommaAndIndentionBeforeKey()
    writeYear(x)
    writeColon()
  }

  /**
   * Writes a [[java.time.YearMonth]] value as a JSON key.
   *
   * @param x
   *   the [[java.time.YearMonth]] value to write
   */
  def writeKey(x: YearMonth): Unit = {
    writeOptionalCommaAndIndentionBeforeKey()
    writeYearMonth(x)
    writeColon()
  }

  /**
   * Writes a [[java.time.ZonedDateTime]] value as a JSON key.
   *
   * @param x
   *   the [[java.time.ZonedDateTime]] value to write
   */
  def writeKey(x: ZonedDateTime): Unit = {
    writeOptionalCommaAndIndentionBeforeKey()
    writeZonedDateTime(x)
    writeColon()
  }

  /**
   * Writes a [[java.time.ZoneId]] value as a JSON key.
   *
   * @param x
   *   the [[java.time.ZoneId]] value to write
   */
  def writeKey(x: ZoneId): Unit = {
    writeOptionalCommaAndIndentionBeforeKey()
    writeZoneId(x)
    writeColon()
  }

  /**
   * Writes a [[java.time.ZoneOffset]] value as a JSON key.
   *
   * @param x
   *   the [[java.time.ZoneOffset]] value to write
   */
  def writeKey(x: ZoneOffset): Unit = {
    writeOptionalCommaAndIndentionBeforeKey()
    writeZoneOffset(x)
    writeColon()
  }

  def push(offset: RegisterOffset): RegisterOffset = {
    val top = this.top
    this.top = top + offset
    maxTop = Math.max(maxTop, this.top)
    top
  }

  def pop(offset: RegisterOffset): Unit = top -= offset

  def registers: Registers = this.stack

  /**
   * Throws a [[JsonBinaryCodecError]] with the given error message.
   *
   * @param msg
   *   the error message
   * @throws JsonBinaryCodecError
   *   always
   */
  def encodeError(msg: String): Nothing = throw new JsonBinaryCodecError(Nil, msg)

  /**
   * Writes a `BigDecimal` value as a JSON value.
   *
   * @param x
   *   the `BigDecimal` value to write
   */
  def writeVal(x: BigDecimal): Unit = {
    writeOptionalCommaAndIndentionBeforeValue()
    writeBigDecimal(x.bigDecimal)
  }

  /**
   * Writes a `BigInt` value as a JSON value.
   *
   * @param x
   *   the `BigInt` value to write
   */
  def writeVal(x: BigInt): Unit = {
    writeOptionalCommaAndIndentionBeforeValue()
    if (x.isValidLong) writeLong(x.longValue)
    else writeBigInteger(x.bigInteger, null)
  }

  /**
   * Writes a [[java.util.UUID]] value as a JSON value.
   *
   * @param x
   *   the [[java.util.UUID]] value to write
   */
  def writeVal(x: UUID): Unit = {
    writeOptionalCommaAndIndentionBeforeValue()
    writeUUID(x.getMostSignificantBits, x.getLeastSignificantBits)
  }

  /**
   * Writes a `String` value as a JSON value.
   *
   * @param x
   *   the `String` value to write
   * @throws JsonBinaryCodecError
   *   if the provided string has an illegal surrogate pair
   */
  def writeVal(x: String): Unit = {
    val indention = this.indention
    var pos       = ensureBufCapacity(indention + 10)
    val buf       = this.buf
    if (comma) {
      buf(pos) = ','
      pos += 1
      if (indention != 0) pos = writeIndention(buf, pos, indention)
    } else comma = true
    buf(pos) = '"'
    pos += 1
    pos = writeString(x, 0, pos, buf, Math.min(x.length, limit - pos - 1) + pos)
    this.buf(pos) = '"'
    count = pos + 1
  }

  /**
   * Writes a `String` value that doesn't require encoding or escaping as a JSON
   * value.
   *
   * @note
   *   Use [[JsonWriter.isNonEscapedAscii]] for validation if the string is
   *   eligible for writing by this method.
   *
   * @param x
   *   the `String` value to write
   */
  def writeNonEscapedAsciiVal(x: String): Unit = {
    val len       = x.length
    val indention = this.indention
    val required  = indention + len + 10
    if (required <= config.preferredBufSize) {
      var pos = ensureBufCapacity(required)
      val buf = this.buf
      if (comma) {
        buf(pos) = ','
        pos += 1
        if (indention != 0) pos = writeIndention(buf, pos, indention)
      } else comma = true
      buf(pos) = '"'
      pos += 1
      var i = 0
      while (i < len) {
        buf(pos) = x.charAt(i).toByte
        pos += 1
        i += 1
      }
      buf(pos) = '"'
      count = pos + 1
    } else writeLongNonEscapedAsciiVal(x)
  }

  /**
   * Writes a [[java.time.Duration]] value as a JSON value.
   *
   * @param x
   *   the [[java.time.Duration]] value to write
   */
  def writeVal(x: Duration): Unit = {
    writeOptionalCommaAndIndentionBeforeValue()
    writeDuration(x)
  }

  /**
   * Writes a [[java.time.Instant]] value as a JSON value.
   *
   * @param x
   *   the [[java.time.Instant]] value to write
   */
  def writeVal(x: Instant): Unit = {
    writeOptionalCommaAndIndentionBeforeValue()
    writeInstant(x)
  }

  /**
   * Writes a [[java.time.LocalDate]] value as a JSON value.
   *
   * @param x
   *   the [[java.time.LocalDate]] value to write
   */
  def writeVal(x: LocalDate): Unit = {
    writeOptionalCommaAndIndentionBeforeValue()
    writeLocalDate(x)
  }

  /**
   * Writes a [[java.time.LocalDateTime]] value as a JSON value.
   *
   * @param x
   *   the [[java.time.LocalDateTime]] value to write
   */
  def writeVal(x: LocalDateTime): Unit = {
    writeOptionalCommaAndIndentionBeforeValue()
    writeLocalDateTime(x)
  }

  /**
   * Writes a [[java.time.LocalTime]] value as a JSON value.
   *
   * @param x
   *   the [[java.time.LocalTime]] value to write
   */
  def writeVal(x: LocalTime): Unit = {
    writeOptionalCommaAndIndentionBeforeValue()
    writeLocalTime(x)
  }

  /**
   * Writes a [[java.time.MonthDay]] value as a JSON value.
   *
   * @param x
   *   the [[java.time.MonthDay]] value to write
   */
  def writeVal(x: MonthDay): Unit = {
    writeOptionalCommaAndIndentionBeforeValue()
    writeMonthDay(x)
  }

  /**
   * Writes a [[java.time.OffsetDateTime]] value as a JSON value.
   *
   * @param x
   *   the [[java.time.OffsetDateTime]] value to write
   */
  def writeVal(x: OffsetDateTime): Unit = {
    writeOptionalCommaAndIndentionBeforeValue()
    writeOffsetDateTime(x)
  }

  /**
   * Writes a [[java.time.OffsetTime]] value as a JSON value.
   *
   * @param x
   *   the [[java.time.OffsetTime]] value to write
   */
  def writeVal(x: OffsetTime): Unit = {
    writeOptionalCommaAndIndentionBeforeValue()
    writeOffsetTime(x)
  }

  /**
   * Writes a [[java.time.Period]] value as a JSON value.
   *
   * @param x
   *   the [[java.time.Period]] value to write
   */
  def writeVal(x: Period): Unit = {
    writeOptionalCommaAndIndentionBeforeValue()
    writePeriod(x)
  }

  /**
   * Writes a [[java.time.Year]] value as a JSON value.
   *
   * @param x
   *   the [[java.time.Year]] value to write
   */
  def writeVal(x: Year): Unit = {
    writeOptionalCommaAndIndentionBeforeValue()
    writeYear(x)
  }

  /**
   * Writes a [[java.time.YearMonth]] value as a JSON value.
   *
   * @param x
   *   the [[java.time.YearMonth]] value to write
   */
  def writeVal(x: YearMonth): Unit = {
    writeOptionalCommaAndIndentionBeforeValue()
    writeYearMonth(x)
  }

  /**
   * Writes a [[java.time.ZonedDateTime]] value as a JSON value.
   *
   * @param x
   *   the [[java.time.ZonedDateTime]] value to write
   */
  def writeVal(x: ZonedDateTime): Unit = {
    writeOptionalCommaAndIndentionBeforeValue()
    writeZonedDateTime(x)
  }

  /**
   * Writes a [[java.time.ZoneId]] value as a JSON value.
   *
   * @param x
   *   the [[java.time.ZoneId]] value to write
   */
  def writeVal(x: ZoneId): Unit = {
    writeOptionalCommaAndIndentionBeforeValue()
    writeZoneId(x)
  }

  /**
   * Writes a [[java.time.ZoneOffset]] value as a JSON value.
   *
   * @param x
   *   the [[java.time.ZoneOffset]] value to write
   */
  def writeVal(x: ZoneOffset): Unit = {
    writeOptionalCommaAndIndentionBeforeValue()
    writeZoneOffset(x)
  }

  /**
   * Writes a `Boolean` value as a JSON value.
   *
   * @param x
   *   the `Boolean` value to write
   */
  def writeVal(x: Boolean): Unit = {
    writeOptionalCommaAndIndentionBeforeValue()
    writeBoolean(x)
  }

  /**
   * Writes a `Byte` value as a JSON value.
   *
   * @param x
   *   the `Byte` value to write
   */
  def writeVal(x: Byte): Unit = {
    writeOptionalCommaAndIndentionBeforeValue()
    writeByte(x)
  }

  /**
   * Writes a `Short` value as a JSON value.
   *
   * @param x
   *   the `Short` value to write
   */
  def writeVal(x: Short): Unit = {
    writeOptionalCommaAndIndentionBeforeValue()
    writeShort(x)
  }

  /**
   * Writes a `Char` value as a JSON key.
   *
   * @param x
   *   the `Char` value to write
   * @throws JsonBinaryCodecError
   *   in the case of `Char` value is a part of the surrogate pair
   */
  def writeVal(x: Char): Unit = {
    writeOptionalCommaAndIndentionBeforeValue()
    writeChar(x.toInt)
  }

  /**
   * Writes an `Int` value as a JSON value.
   *
   * @param x
   *   the `Int` value to write
   */
  def writeVal(x: Int): Unit = {
    writeOptionalCommaAndIndentionBeforeValue()
    writeInt(x)
  }

  /**
   * Writes a `Long` value as a JSON value.
   *
   * @param x
   *   the `Long` value to write
   */
  def writeVal(x: Long): Unit = {
    writeOptionalCommaAndIndentionBeforeValue()
    writeLong(x)
  }

  /**
   * Writes a `Float` value as a JSON value.
   *
   * @param x
   *   the `Float` value to write
   * @throws JsonBinaryCodecError
   *   if the value is non-finite
   */
  def writeVal(x: Float): Unit = {
    writeOptionalCommaAndIndentionBeforeValue()
    writeFloat(x)
  }

  /**
   * Writes a `Double` value as a JSON value.
   *
   * @param x
   *   the `Double` value to write
   * @throws JsonBinaryCodecError
   *   if the value is non-finite
   */
  def writeVal(x: Double): Unit = {
    writeOptionalCommaAndIndentionBeforeValue()
    writeDouble(x)
  }

  /**
   * Writes a `BigDecimal` value as a JSON string value.
   *
   * @param x
   *   the `BigDecimal` value to write
   */
  def writeValAsString(x: BigDecimal): Unit = {
    writeOptionalCommaAndIndentionBeforeValue()
    writeBytes('"')
    writeBigDecimal(x.bigDecimal)
    writeBytes('"')
  }

  /**
   * Writes a `BigInt` value as a JSON string value.
   *
   * @param x
   *   the `BigInt` value to write
   */
  def writeValAsString(x: BigInt): Unit = {
    writeOptionalCommaAndIndentionBeforeValue()
    writeBytes('"')
    if (x.isValidLong) writeLong(x.longValue)
    else writeBigInteger(x.bigInteger, null)
    writeBytes('"')
  }

  /**
   * Writes a `Boolean` value as a JSON string value.
   *
   * @param x
   *   the `Boolean` value to write
   */
  def writeValAsString(x: Boolean): Unit = {
    writeOptionalCommaAndIndentionBeforeValue()
    writeBytes('"')
    writeBoolean(x)
    writeBytes('"')
  }

  /**
   * Writes a `Byte` value as a JSON string value.
   *
   * @param x
   *   the `Byte` value to write
   */
  def writeValAsString(x: Byte): Unit = {
    writeOptionalCommaAndIndentionBeforeValue()
    writeBytes('"')
    writeByte(x)
    writeBytes('"')
  }

  /**
   * Writes a `Short` value as a JSON string value.
   *
   * @param x
   *   the `Short` value to write
   */
  def writeValAsString(x: Short): Unit = {
    writeOptionalCommaAndIndentionBeforeValue()
    writeBytes('"')
    writeShort(x)
    writeBytes('"')
  }

  /**
   * Writes an `Int` value as a JSON string value.
   *
   * @param x
   *   the `Int` value to write
   */
  def writeValAsString(x: Int): Unit = {
    writeOptionalCommaAndIndentionBeforeValue()
    writeBytes('"')
    writeInt(x)
    writeBytes('"')
  }

  /**
   * Writes a `Long` value as a JSON string value.
   *
   * @param x
   *   the `Long` value to write
   */
  def writeValAsString(x: Long): Unit = {
    writeOptionalCommaAndIndentionBeforeValue()
    writeBytes('"')
    writeLong(x)
    writeBytes('"')
  }

  /**
   * Writes a `Float` value as a JSON string value.
   *
   * @param x
   *   the `Float` value to write
   * @throws JsonBinaryCodecError
   *   if the value is non-finite
   */
  def writeValAsString(x: Float): Unit = {
    writeOptionalCommaAndIndentionBeforeValue()
    writeBytes('"')
    writeFloat(x)
    writeBytes('"')
  }

  /**
   * Writes a `Double` value as a JSON string value.
   *
   * @param x
   *   the `Double` value to write
   * @throws JsonBinaryCodecError
   *   if the value is non-finite
   */
  def writeValAsString(x: Double): Unit = {
    writeOptionalCommaAndIndentionBeforeValue()
    writeBytes('"')
    writeDouble(x)
    writeBytes('"')
  }

  /**
   * Writes a byte array as a JSON raw binary value.
   *
   * @param bs
   *   the byte array to write
   */
  def writeRawVal(bs: Array[Byte]): Unit = {
    writeOptionalCommaAndIndentionBeforeValue()
    writeRawBytes(bs)
  }

  /**
   * Writes a JSON `null` value.
   */
  def writeNull(): Unit = {
    writeOptionalCommaAndIndentionBeforeValue()
    val pos = ensureBufCapacity(4)
    ByteArrayAccess.setInt(buf, pos, 0x6c6c756e)
    count = pos + 4
  }

  /**
   * Writes a JSON array start marker (`[`).
   */
  def writeArrayStart(): Unit = writeNestedStart('[')

  /**
   * Writes a JSON array end marker (`]`).
   */
  def writeArrayEnd(): Unit = writeNestedEnd(']')

  /**
   * Writes a JSON array start marker (`{`).
   */
  def writeObjectStart(): Unit = writeNestedStart('{')

  /**
   * Writes a JSON array end marker (`}`).
   */
  def writeObjectEnd(): Unit = writeNestedEnd('}')

  /**
   * Indicates whether the writer is currently in use.
   *
   * @return
   *   true if the writer is in use, false otherwise
   */
  private[json] def isInUse: Boolean = top >= 0

  /**
   * Writes JSON-encoded value of type `A` to an output stream.
   *
   * @param codec
   *   a JSON value codec for type `A`
   * @param x
   *   the value to encode
   * @param out
   *   the output stream to write to
   * @param config
   *   the writer configuration
   */
  private[json] def write[A](codec: JsonBinaryCodec[A], x: A, out: OutputStream, config: WriterConfig): Unit =
    try {
      top = 0
      maxTop = 0
      count = 0
      indention = 0
      comma = false
      disableBufGrowing = false
      this.out = out
      this.config = config
      if (limit < config.preferredBufSize) reallocateBufToPreferredSize()
      codec.encodeValue(x, this)
      out.write(buf, 0, count)
    } finally {
      this.out = null // don't close output stream
      if (limit > config.preferredBufSize) reallocateBufToPreferredSize()
      stack.clearObjects(maxTop)
      top = -1
    }

  /**
   * Encodes a value of type `A` to a byte array.
   *
   * @param codec
   *   a JSON value codec for type `A`
   * @param x
   *   the value to encode
   * @param config
   *   the writer configuration
   * @return
   *   the encoded JSON as a byte array
   */
  private[json] def write[A](codec: JsonBinaryCodec[A], x: A, config: WriterConfig): Array[Byte] =
    try {
      top = 0
      maxTop = 0
      count = 0
      indention = 0
      comma = false
      disableBufGrowing = false
      this.config = config
      codec.encodeValue(x, this)
      java.util.Arrays.copyOf(buf, count)
    } finally {
      if (limit > config.preferredBufSize) reallocateBufToPreferredSize()
      stack.clearObjects(maxTop)
      top = -1
    }

  /**
   * Encodes a value of type `A` into a byte buffer.
   *
   * @param codec
   *   JSON value codec for type `A`
   * @param x
   *   the value to encode
   * @param bbuf
   *   the target byte buffer
   * @param config
   *   the writer configuration
   */
  private[json] def write[A](codec: JsonBinaryCodec[A], x: A, bbuf: ByteBuffer, config: WriterConfig): Unit = {
    top = 0
    maxTop = 0
    indention = 0
    comma = false
    if (bbuf.hasArray) {
      val offset  = bbuf.arrayOffset
      val currBuf = this.buf
      try {
        this.buf = bbuf.array
        this.config = config
        count = bbuf.position() + offset
        limit = bbuf.limit() + offset
        disableBufGrowing = true
        codec.encodeValue(x, this)
      } catch {
        case _: ArrayIndexOutOfBoundsException => throw new BufferOverflowException
      } finally {
        setBuf(currBuf)
        bbuf.position(count - offset)
        stack.clearObjects(maxTop)
        top = -1
      }
    } else {
      try {
        this.bbuf = bbuf
        this.config = config
        count = 0
        disableBufGrowing = false
        if (limit < config.preferredBufSize) reallocateBufToPreferredSize()
        codec.encodeValue(x, this)
        bbuf.put(buf, 0, count)
      } finally {
        this.bbuf = null
        if (limit > config.preferredBufSize) reallocateBufToPreferredSize()
        stack.clearObjects(maxTop)
        top = -1
      }
    }
  }

  /**
   * Encodes a value of type `A` to a string.
   *
   * @param codec
   *   a JSON value codec for type `A`
   * @param x
   *   the value to encode
   * @param config
   *   the writer configuration
   * @return
   *   the encoded JSON as a string
   */
  private[json] def writeToString[A](codec: JsonBinaryCodec[A], x: A, config: WriterConfig): String =
    try {
      top = 0
      maxTop = 0
      count = 0
      indention = 0
      comma = false
      disableBufGrowing = false
      this.config = config
      codec.encodeValue(x, this)
      new String(buf, 0, count, UTF_8)
    } finally {
      if (limit > config.preferredBufSize) reallocateBufToPreferredSize()
      stack.clearObjects(maxTop)
      top = -1
    }

  private[this] def writeNestedStart(b: Byte): Unit = {
    writeOptionalCommaAndIndentionBeforeKey()
    writeBytes(b)
    val indentionStep = config.indentionStep
    if (indentionStep != 0) {
      indention += indentionStep
      writeIndention()
    }
  }

  private[this] def writeNestedEnd(b: Byte): Unit = {
    comma = true
    if (indention != 0) {
      indention -= config.indentionStep
      writeIndention()
    }
    writeBytes(b)
  }

  private[this] def writeOptionalCommaAndIndentionBeforeValue(): Unit =
    if (comma) {
      writeBytes(',')
      if (indention != 0) writeIndention()
    } else comma = true

  private[this] def writeOptionalCommaAndIndentionBeforeKey(): Unit =
    if (comma) {
      comma = false
      writeBytes(',')
      if (indention != 0) writeIndention()
    }

  private[this] def writeIndention(): Unit = {
    val n   = indention
    val pos = ensureBufCapacity(n + 8)
    count = writeIndention(buf, pos, n)
  }

  private[this] def writeIndention(buf: Array[Byte], p: Int, n: Int): Int = {
    var pos = p
    buf(pos) = '\n'
    pos += 1
    val posLim = pos + n
    while (pos < posLim) {
      ByteArrayAccess.setLong(buf, pos, 0x2020202020202020L)
      pos += 8
    }
    posLim
  }

  private[this] def writeParenthesesWithColon(): Unit = {
    var pos = ensureBufCapacity(4) // 4 == size of Int in bytes
    ByteArrayAccess.setInt(buf, pos, 0x203a22)
    if (indention > 0) pos += 1
    count = pos + 2
  }

  private[this] def writeColon(): Unit = {
    var pos = ensureBufCapacity(2)
    ByteArrayAccess.setShort(buf, pos, 0x203a)
    if (indention > 0) pos += 1
    count = pos + 1
  }

  private[this] def writeBytes(b: Byte): Unit = {
    var pos = count
    if (pos >= limit) pos = flushAndGrowBuf(1, pos)
    buf(pos) = b
    count = pos + 1
  }

  private[this] def writeRawBytes(bs: Array[Byte]): Unit = {
    var pos       = count
    var step      = Math.max(config.preferredBufSize, limit - pos)
    var remaining = bs.length
    var offset    = 0
    while (remaining > 0) {
      step = Math.min(step, remaining)
      if (pos + step > limit) pos = flushAndGrowBuf(step, pos)
      System.arraycopy(bs, offset, buf, pos, step)
      offset += step
      pos += step
      remaining -= step
    }
    count = pos
  }

  private[this] def writeLongNonEscapedAsciiKey(x: String): Unit = {
    writeOptionalCommaAndIndentionBeforeKey()
    writeBytes('"')
    var pos       = count
    var step      = Math.max(config.preferredBufSize, limit - pos)
    var remaining = x.length
    var offset    = 0
    while (remaining > 0) {
      step = Math.min(step, remaining)
      if (pos + step > limit) pos = flushAndGrowBuf(step, pos)
      val newOffset = offset + step
      x.getBytes(offset, newOffset, buf, pos): @nowarn
      offset = newOffset
      pos += step
      remaining -= step
    }
    count = pos
    writeParenthesesWithColon()
  }

  private[this] def writeLongNonEscapedAsciiVal(x: String): Unit = {
    writeOptionalCommaAndIndentionBeforeValue()
    writeBytes('"')
    var pos       = count
    var step      = Math.max(config.preferredBufSize, limit - pos)
    var remaining = x.length
    var offset    = 0
    while (remaining > 0) {
      step = Math.min(step, remaining)
      if (pos + step > limit) pos = flushAndGrowBuf(step, pos)
      val newOffset = offset + step
      x.getBytes(offset, newOffset, buf, pos): @nowarn
      offset = newOffset
      pos += step
      remaining -= step
    }
    count = pos
    writeBytes('"')
  }

  private[this] def writeZoneId(x: ZoneId): Unit = {
    val s   = x.getId
    val len = s.length
    var pos = ensureBufCapacity(len + 2)
    val buf = this.buf
    buf(pos) = '"'
    pos += 1
    s.getBytes(0, len, buf, pos): @nowarn
    pos += len
    buf(pos) = '"'
    count = pos + 1
  }

  private[this] def writeUUID(mostSigBits: Long, leastSigBits: Long): Unit = {
    val pos          = ensureBufCapacity(40) // 40 == 5 * size of Long in bytes
    val buf          = this.buf
    val ds           = lowerCaseHexDigits
    val mostSigBits1 = (mostSigBits >> 32).toInt
    val d1           = ds(mostSigBits1 >>> 24) << 8
    val d2           = ds(mostSigBits1 >> 16 & 0xff).toLong << 24
    val d3           = ds(mostSigBits1 >> 8 & 0xff).toLong << 40
    val d4           = ds(mostSigBits1 & 0xff)
    ByteArrayAccess.setLong(buf, pos, d1 | d2 | d3 | d4.toLong << 56 | 0x22)
    val mostSigBits2 = mostSigBits.toInt
    val d5           = ds(mostSigBits2 >>> 24) << 16
    val d6           = ds(mostSigBits2 >> 16 & 0xff).toLong << 32
    val d7           = ds(mostSigBits2 >> 8 & 0xff)
    ByteArrayAccess.setLong(buf, pos + 8, d4 >> 8 | d5 | d6 | d7.toLong << 56 | 0x2d000000002d00L)
    val d8            = ds(mostSigBits2 & 0xff) << 8
    val leastSigBits1 = (leastSigBits >> 32).toInt
    val d9            = ds(leastSigBits1 >>> 24).toLong << 32
    val d10           = ds(leastSigBits1 >> 16 & 0xff).toLong << 48
    ByteArrayAccess.setLong(buf, pos + 16, d7 >> 8 | d8 | d9 | d10 | 0x2d000000)
    val d11           = ds(leastSigBits1 >> 8 & 0xff) << 8
    val d12           = ds(leastSigBits1 & 0xff).toLong << 24
    val leastSigBits2 = leastSigBits.toInt
    val d13           = ds(leastSigBits2 >>> 24).toLong << 40
    val d14           = ds(leastSigBits2 >> 16 & 0xff)
    ByteArrayAccess.setLong(buf, pos + 24, d11 | d12 | d13 | d14.toLong << 56 | 0x2d)
    val d15 = ds(leastSigBits2 >> 8 & 0xff) << 8
    val d16 = ds(leastSigBits2 & 0xff).toLong << 24
    ByteArrayAccess.setLong(buf, pos + 32, d14 >> 8 | d15 | d16 | 0x220000000000L)
    count = pos + 38
  }

  @tailrec
  private[this] def writeString(s: String, from: Int, pos: Int, buf: Array[Byte], minLim: Int): Int =
    if (pos < minLim) {
      val ch = s.charAt(from).toInt
      buf(pos) = ch.toByte
      if (ch >= 0x20 && ch < 0x7f && ch != 0x22 && ch != 0x5c) writeString(s, from + 1, pos + 1, buf, minLim)
      else writeEscapedOrEncodedString(s, from, pos)
    } else if (s.length == from) pos
    else {
      val newPos = flushAndGrowBuf(2, pos)
      writeString(s, from, newPos, this.buf, Math.min(s.length - from, limit - newPos - 1) + newPos)
    }

  private[this] def writeEscapedOrEncodedString(s: String, from: Int, pos: Int): Int =
    if (config.escapeUnicode) writeEscapedString(s, from, s.length, pos, limit - 13, escapedChars, lowerCaseHexDigits)
    else writeEncodedString(s, from, s.length, pos, limit - 7, escapedChars)

  @tailrec
  private[this] def writeEncodedString(
    s: String,
    from: Int,
    to: Int,
    pos: Int,
    posLim: Int,
    escapedChars: Array[Byte]
  ): Int =
    if (from >= to) pos
    else if (pos >= posLim) writeEncodedString(s, from, to, flushAndGrowBuf(7, pos), limit - 6, escapedChars)
    else {
      val ch1 = s.charAt(from).toInt
      if (ch1 < 0x80) {
        val esc = escapedChars(ch1)
        if (esc == 0) { // 000000000aaaaaaa (UTF-16 char) -> 0aaaaaaa (UTF-8 byte)
          buf(pos) = ch1.toByte
          writeEncodedString(s, from + 1, to, pos + 1, posLim, escapedChars)
        } else if (esc > 0) {
          ByteArrayAccess.setShort(buf, pos, (esc << 8 | 0x5c).toShort)
          writeEncodedString(s, from + 1, to, pos + 2, posLim, escapedChars)
        } else
          writeEncodedString(
            s,
            from + 1,
            to,
            writeEscapedUnicode(ch1.toByte, pos, buf, lowerCaseHexDigits),
            posLim,
            escapedChars
          )
      } else if (ch1 < 0x800) { // 00000bbbbbaaaaaa (UTF-16 char) -> 110bbbbb 10aaaaaa (UTF-8 bytes)
        ByteArrayAccess.setShort(buf, pos, (ch1 >> 6 | (ch1 << 8 & 0x3f00) | 0x80c0).toShort)
        writeEncodedString(s, from + 1, to, pos + 2, posLim, escapedChars)
      } else if ((ch1 & 0xf800) != 0xd800) { // ccccbbbbbbaaaaaa (UTF-16 char) -> 1110cccc 10bbbbbb 10aaaaaa (UTF-8 bytes)
        ByteArrayAccess.setInt(buf, pos, ch1 >> 12 | (ch1 << 2 & 0x3f00) | (ch1 << 16 & 0x3f0000) | 0x8080e0)
        writeEncodedString(s, from + 1, to, pos + 3, posLim, escapedChars)
      } else { // 110110uuuuccccbb 110111bbbbaaaaaa (UTF-16 chars) -> 11110ddd 10ddcccc 10bbbbbb 10aaaaaa (UTF-8 bytes), where ddddd = uuuu + 1
        var ch2 = 0
        if (
          ch1 >= 0xdc00 || from + 1 >= to || {
            ch2 = s.charAt(from + 1).toInt
            (ch2 & 0xfc00) != 0xdc00
          }
        ) illegalSurrogateError()
        val cp = (ch1 << 10) + (ch2 - 56613888) // -56613888 == 0x10000 - (0xD800 << 10) - 0xDC00
        ByteArrayAccess.setInt(
          buf,
          pos,
          cp >> 18 | (cp >> 4 & 0x3f00) | (cp << 10 & 0x3f0000) | (cp << 24 & 0x3f000000) | 0x808080f0
        )
        writeEncodedString(s, from + 2, to, pos + 4, posLim, escapedChars)
      }
    }

  @tailrec
  private[this] def writeEscapedString(
    s: String,
    from: Int,
    to: Int,
    pos: Int,
    posLim: Int,
    escapedChars: Array[Byte],
    ds: Array[Short]
  ): Int =
    if (from >= to) pos
    else if (pos >= posLim) writeEscapedString(s, from, to, flushAndGrowBuf(13, pos), limit - 12, escapedChars, ds)
    else {
      val ch1 = s.charAt(from).toInt
      if (ch1 < 0x80) {
        val esc = escapedChars(ch1)
        if (esc == 0) {
          buf(pos) = ch1.toByte
          writeEscapedString(s, from + 1, to, pos + 1, posLim, escapedChars, ds)
        } else if (esc > 0) {
          ByteArrayAccess.setShort(buf, pos, (esc << 8 | 0x5c).toShort)
          writeEscapedString(s, from + 1, to, pos + 2, posLim, escapedChars, ds)
        } else
          writeEscapedString(s, from + 1, to, writeEscapedUnicode(ch1.toByte, pos, buf, ds), posLim, escapedChars, ds)
      } else if ((ch1 & 0xf800) != 0xd800) {
        writeEscapedString(s, from + 1, to, writeEscapedUnicode(ch1, pos, buf, ds), posLim, escapedChars, ds)
      } else {
        var ch2 = 0
        if (
          ch1 >= 0xdc00 || from + 1 >= to || {
            ch2 = s.charAt(from + 1).toInt
            (ch2 & 0xfc00) != 0xdc00
          }
        ) illegalSurrogateError()
        writeEscapedString(
          s,
          from + 2,
          to,
          writeEscapedUnicode(ch2, writeEscapedUnicode(ch1, pos, buf, ds), buf, ds),
          posLim,
          escapedChars,
          ds
        )
      }
    }

  private[this] def writeChar(ch: Int): Unit = {
    var pos = ensureBufCapacity(8) // 8 = size of Long in bytes
    if (ch < 0x80) {
      val esc = escapedChars(ch)
      if (esc == 0) { // 000000000aaaaaaa (UTF-16 char) -> 0aaaaaaa (UTF-8 byte)
        ByteArrayAccess.setInt(buf, pos, ch << 8 | 0x220022)
        pos += 3
      } else if (esc > 0) {
        ByteArrayAccess.setInt(buf, pos, esc << 16 | 0x22005c22)
        pos += 4
      } else {
        val ds = lowerCaseHexDigits
        ByteArrayAccess.setLong(buf, pos, ds(ch).toLong << 40 | 0x2200003030755c22L)
        pos += 8
      }
    } else if (config.escapeUnicode) {
      if ((ch & 0xf800) == 0xd800) illegalSurrogateError()
      val ds = lowerCaseHexDigits
      val d1 = ds(ch >> 8).toLong << 24
      val d2 = ds(ch & 0xff).toLong << 40
      ByteArrayAccess.setLong(buf, pos, d1 | d2 | 0x2200000000755c22L)
      pos += 8
    } else if (ch < 0x800) { // 00000bbbbbaaaaaa (UTF-16 char) -> 110bbbbb 10aaaaaa (UTF-8 bytes)
      ByteArrayAccess.setInt(buf, pos, (ch & 0x3f) << 16 | (ch & 0xfc0) << 2 | 0x2280c022)
      pos += 4
    } else if ((ch & 0xf800) != 0xd800) { // ccccbbbbbbaaaaaa (UTF-16 char) -> 1110cccc 10bbbbbb 10aaaaaa (UTF-8 bytes)
      ByteArrayAccess.setLong(buf, pos, ((ch & 0x3f) << 24 | (ch & 0xfc0) << 10 | (ch & 0xf000) >> 4) | 0x228080e022L)
      pos += 5
    } else illegalSurrogateError()
    count = pos
  }

  private[this] def writeEscapedUnicode(ch: Int, pos: Int, buf: Array[Byte], ds: Array[Short]): Int = {
    ByteArrayAccess.setShort(buf, pos, 0x755c)
    val d1 = ds(ch >> 8)
    val d2 = ds(ch & 0xff) << 16
    ByteArrayAccess.setInt(buf, pos + 2, d1 | d2)
    pos + 6
  }

  private[this] def writeEscapedUnicode(b: Byte, pos: Int, buf: Array[Byte], ds: Array[Short]): Int = {
    ByteArrayAccess.setInt(buf, pos, 0x3030755c)
    ByteArrayAccess.setShort(buf, pos + 4, ds(b & 0xff))
    pos + 6
  }

  private[this] def illegalSurrogateError(): Nothing = encodeError("illegal char sequence of surrogate pair")

  private[this] def writeBigInteger(x: BigInteger, ss: Array[BigInteger]): Unit = {
    val bitLen = x.bitLength
    if (bitLen < 64) writeLong(x.longValue)
    else {
      val n   = calculateTenPow18SquareNumber(bitLen)
      val ss1 =
        if (ss eq null) getTenPow18Squares(n)
        else ss
      val qr = x.divideAndRemainder(ss1(n))
      writeBigInteger(qr(0), ss1)
      writeBigIntegerRemainder(qr(1), n - 1, ss1)
    }
  }

  private[this] def writeBigIntegerRemainder(x: BigInteger, n: Int, ss: Array[BigInteger]): Unit =
    if (n < 0) count = write18Digits(Math.abs(x.longValue), ensureBufCapacity(18), buf, digits)
    else {
      val qr = x.divideAndRemainder(ss(n))
      writeBigIntegerRemainder(qr(0), n - 1, ss)
      writeBigIntegerRemainder(qr(1), n - 1, ss)
    }

  private[this] def writeBigDecimal(x: java.math.BigDecimal): Unit = {
    var exp = writeBigDecimal(x.unscaledValue, x.scale, 0, null)
    if (exp != 0) {
      var pos = ensureBufCapacity(12)
      val buf = this.buf
      val ds  = digits
      val s   = exp >> 63
      exp = (exp + s) ^ s
      ByteArrayAccess.setShort(buf, pos, (0x2b45L - (s << 9)).toShort)
      pos += 2
      var q = exp
      if (exp < 100000000L) {
        pos += digitCount(exp)
        count = pos
      } else {
        q = Math.multiplyHigh(exp, 6189700196426901375L) >>> 25 // divide a positive long by 100000000
        pos += digitCount(q)
        count = write8Digits(exp - q * 100000000L, pos, buf, ds)
      }
      writePositiveIntDigits(q.toInt, pos, buf, ds)
    }
  }

  private[this] def writeBigDecimal(x: BigInteger, scale: Int, blockScale: Int, ss: Array[BigInteger]): Long = {
    val bitLen = x.bitLength
    if (bitLen < 64) {
      val v       = x.longValue
      val pos     = ensureBufCapacity(28) // Long.MinValue.toString.length + 8 (for a leading zero, dot, and padding zeroes)
      val buf     = this.buf
      var lastPos = writeLong(v, pos, buf)
      val digits  = (v >> 63).toInt + lastPos - pos
      val dotOff  = scale.toLong - blockScale
      val exp     = (digits - 1) - dotOff
      if (scale >= 0 && exp >= -6) {
        if (exp < 0) lastPos = insertDotWithZeroes(digits, -1 - exp.toInt, lastPos, buf)
        else if (dotOff > 0) lastPos = insertDot(lastPos - dotOff.toInt, lastPos, buf)
        count = lastPos
        0
      } else {
        if (digits > 1 || blockScale > 0) lastPos = insertDot(lastPos - digits + 1, lastPos, buf)
        count = lastPos
        exp
      }
    } else {
      val n   = calculateTenPow18SquareNumber(bitLen)
      val ss1 =
        if (ss eq null) getTenPow18Squares(n)
        else ss
      val qr  = x.divideAndRemainder(ss1(n))
      val exp = writeBigDecimal(qr(0), scale, (18 << n) + blockScale, ss1)
      writeBigDecimalRemainder(qr(1), scale, blockScale, n - 1, ss1)
      exp
    }
  }

  private[this] def writeBigDecimalRemainder(
    x: BigInteger,
    scale: Int,
    blockScale: Int,
    n: Int,
    ss: Array[BigInteger]
  ): Unit =
    if (n < 0) {
      val pos     = ensureBufCapacity(19) // 18 digits and a place for optional dot
      val buf     = this.buf
      var lastPos = write18Digits(Math.abs(x.longValue), pos, buf, digits)
      val dotOff  = scale - blockScale
      if (dotOff > 0 && dotOff <= 18) lastPos = insertDot(lastPos - dotOff, lastPos, buf)
      count = lastPos
    } else {
      val qr = x.divideAndRemainder(ss(n))
      writeBigDecimalRemainder(qr(0), scale, (18 << n) + blockScale, n - 1, ss)
      writeBigDecimalRemainder(qr(1), scale, blockScale, n - 1, ss)
    }

  private[this] def calculateTenPow18SquareNumber(bitLen: Int): Int = {
    val m = Math.max(
      (bitLen * 71828554L >> 32).toInt - 1,
      1
    ) // Math.max((x.bitLength * Math.log(2) / Math.log(1e18)).toInt - 1, 1)
    31 - java.lang.Integer.numberOfLeadingZeros(m)
  }

  private[this] def insertDotWithZeroes(digits: Int, pad: Int, lastPos: Int, buf: Array[Byte]): Int = {
    var pos    = lastPos + pad + 1
    val numPos = pos - digits
    val off    = pad + 2
    while (pos > numPos) {
      buf(pos) = buf(pos - off)
      pos -= 1
    }
    val dotPos = pos - pad
    while (pos > dotPos) {
      buf(pos) = '0'
      pos -= 1
    }
    ByteArrayAccess.setShort(buf, dotPos - 1, 0x2e30)
    lastPos + off
  }

  private[this] def insertDot(dotPos: Int, lastPos: Int, buf: Array[Byte]): Int = {
    var pos = lastPos
    while (pos > dotPos) {
      buf(pos) = buf(pos - 1)
      pos -= 1
    }
    buf(dotPos) = '.'
    lastPos + 1
  }

  private[this] def writeBoolean(x: Boolean): Unit = {
    var pos = ensureBufCapacity(8) // bytes in Long
    if (x) {
      ByteArrayAccess.setInt(buf, pos, 0x65757274)
      pos += 4
    } else {
      ByteArrayAccess.setLong(buf, pos, 0x65736c6166L)
      pos += 5
    }
    count = pos
  }

  private[this] def writeByte(x: Byte): Unit = {
    var pos = ensureBufCapacity(5) // size of Int in bytes + one byte for the sign
    val buf = this.buf
    val ds  = digits
    var q0  = x.toInt
    if (q0 < 0) {
      q0 = -q0
      buf(pos) = '-'
      pos += 1
    }
    if (q0 < 10) {
      buf(pos) = (q0 | '0').toByte
      pos += 1
    } else if (q0 < 100) {
      ByteArrayAccess.setShort(buf, pos, ds(q0))
      pos += 2
    } else {
      ByteArrayAccess.setInt(buf, pos, ds(q0 - 100) << 8 | '1')
      pos += 3
    }
    count = pos
  }

  private[this] def writeDuration(x: Duration): Unit = {
    var pos       = ensureBufCapacity(40) // 40 == "PT-1111111111111111H-11M-11.111111111S".length + 2
    val buf       = this.buf
    var totalSecs = x.getSeconds
    var nano      = x.getNano
    ByteArrayAccess.setLong(buf, pos, 0x225330545022L)
    if ((totalSecs | nano) == 0) pos += 6
    else {
      pos += 3
      val isNeg = totalSecs < 0
      if (isNeg) totalSecs = (-nano >> 31) - totalSecs
      var hours      = 0L
      var secsOfHour = totalSecs.toInt
      if (totalSecs >= 3600) {
        hours = Math.multiplyHigh(totalSecs >> 4, 655884233731895169L) >> 3 // divide a positive long by 3600
        secsOfHour = (totalSecs - hours * 3600).toInt
      }
      val minutes = secsOfHour * 17477 >> 20 // divide a small positive int by 60
      val seconds = secsOfHour - minutes * 60
      val ds      = digits
      if (hours != 0) {
        if (isNeg) {
          buf(pos) = '-'
          pos += 1
        }
        var q       = hours
        var lastPos = pos
        if (hours < 100000000L) {
          lastPos += digitCount(hours)
          pos = lastPos
        } else {
          q = Math.multiplyHigh(hours, 6189700196426901375L) >>> 25 // divide a positive long by 100000000
          lastPos += digitCount(q)
          pos = write8Digits(hours - q * 100000000L, lastPos, buf, ds)
        }
        writePositiveIntDigits(q.toInt, lastPos, buf, ds)
        ByteArrayAccess.setShort(buf, pos, 0x2248)
        pos += 1
      }
      if (minutes != 0) {
        if (isNeg) {
          buf(pos) = '-'
          pos += 1
        }
        if (minutes < 10) {
          buf(pos) = (minutes | '0').toByte
          pos += 1
        } else {
          ByteArrayAccess.setShort(buf, pos, ds(minutes))
          pos += 2
        }
        ByteArrayAccess.setShort(buf, pos, 0x224d)
        pos += 1
      }
      if ((seconds | nano) != 0) {
        if (isNeg) {
          buf(pos) = '-'
          pos += 1
        }
        if (seconds < 10) {
          buf(pos) = (seconds | '0').toByte
          pos += 1
        } else {
          ByteArrayAccess.setShort(buf, pos, ds(seconds))
          pos += 2
        }
        if (nano != 0) {
          if (isNeg) nano = 1000000000 - nano
          val dotPos = pos
          pos = writeSignificantFractionDigits(nano, pos + 9, pos, buf, ds)
          buf(dotPos) = '.'
        }
        ByteArrayAccess.setShort(buf, pos, 0x2253)
        pos += 1
      }
      pos += 1
    }
    count = pos
  }

  private[this] def writeInstant(x: Instant): Unit = {
    val epochSecond = x.getEpochSecond
    if (epochSecond < 0) writeBeforeEpochInstant(epochSecond, x.getNano)
    else {
      val epochDay     = Math.multiplyHigh(epochSecond, 1749024623285053783L) >> 13 // epochSecond / 86400
      val marchZeroDay = epochDay + 719468                                          // 719468 == 719528 - 60 == days 0000 to 1970 - days 1st Jan to 1st Mar
      var year         = (Math.multiplyHigh(
        marchZeroDay * 400 + 591,
        4137408090565272301L
      ) >> 15).toInt // ((marchZeroDay * 400 + 591) / 146097).toInt
      var days           = year * 365L
      var year1374389535 = year * 1374389535L
      var century        = (year1374389535 >> 37).toInt
      var marchDayOfYear = (marchZeroDay - days).toInt - (year >> 2) + century - (century >> 2)
      if (marchDayOfYear < 0) {
        days -= 365
        year1374389535 -= 1374389535
        year -= 1
        century = (year1374389535 >> 37).toInt
        marchDayOfYear = (marchZeroDay - days).toInt - (year >> 2) + century - (century >> 2)
      }
      val marchMonth = marchDayOfYear * 17135 + 6854 >> 19 // (marchDayOfYear * 5 + 2) / 153
      val day        =
        marchDayOfYear - (marchMonth * 1002762 - 16383 >> 15) // marchDayOfYear - (marchMonth * 306 + 5) / 10 + 1
      val m          = 9 - marchMonth >> 4
      val month      = (m & -9 | 3) + marchMonth
      year -= m
      writeInstant(year, month, day, (epochSecond - epochDay * 86400).toInt, x.getNano)
    }
  }

  private[this] def writeBeforeEpochInstant(epochSecond: Long, nano: Int): Unit = {
    val epochDay =
      (Math.multiplyHigh(epochSecond - 86399, 1749024623285053783L) >> 13) + 1 // (epochSecond - 86399) / 86400
    var marchZeroDay        = epochDay + 719468                          // 719468 == 719528 - 60 == days 0000 to 1970 - days 1st Jan to 1st Mar
    val adjust400YearCycles = ((marchZeroDay + 1) * 7525902 >> 40).toInt // ((marchZeroDay + 1) / 146097).toInt - 1
    marchZeroDay -= adjust400YearCycles * 146097L
    var year = (Math.multiplyHigh(
      marchZeroDay * 400 + 591,
      4137408090565272301L
    ) >> 15).toInt // ((marchZeroDay * 400 + 591) / 146097).toInt
    var days           = year * 365L
    var year1374389535 = year * 1374389535L
    var century        = (year1374389535 >> 37).toInt
    var marchDayOfYear = (marchZeroDay - days).toInt - (year >> 2) + century - (century >> 2)
    if (marchDayOfYear < 0) {
      days -= 365
      year1374389535 -= 1374389535
      year -= 1
      century = (year1374389535 >> 37).toInt
      marchDayOfYear = (marchZeroDay - days).toInt - (year >> 2) + century - (century >> 2)
    }
    val marchMonth = marchDayOfYear * 17135 + 6854 >> 19                   // (marchDayOfYear * 5 + 2) / 153
    val day        = marchDayOfYear - (marchMonth * 1002762 - 16383 >> 15) // marchDayOfYear - (marchMonth * 306 + 5) / 10 + 1
    val m          = 9 - marchMonth >> 4
    val month      = (m & -9 | 3) + marchMonth
    year += adjust400YearCycles * 400 - m
    writeInstant(year, month, day, (epochSecond - epochDay * 86400).toInt, nano)
  }

  private[this] def writeInstant(year: Int, month: Int, day: Int, secsOfDay: Int, nano: Int): Unit = {
    var pos = ensureBufCapacity(39) // 39 == Instant.MAX.toString.length + 2
    val buf = this.buf
    val ds  = digits
    buf(pos) = '"'
    pos = writeYear(year, pos + 1, buf, ds)
    ByteArrayAccess.setLong(buf, pos, ds(month) << 8 | ds(day).toLong << 32 | 0x5400002d00002dL)
    pos += 7
    val y1 =
      secsOfDay * 37283 // Based on James Anhalt's algorithm: https://jk-jeon.github.io/posts/2022/02/jeaiii-algorithm/
    val y2 = (y1 & 0x7ffffff) * 15
    val y3 = (y2 & 0x1ffffff) * 15
    ByteArrayAccess.setLong(
      buf,
      pos,
      ds(y1 >>> 27) | ds(y2 >> 25).toLong << 24 | ds(y3 >> 23).toLong << 48 | 0x3a00003a0000L
    )
    pos += 8
    if (nano != 0) pos = writeNanos(nano, pos, buf, ds)
    ByteArrayAccess.setShort(buf, pos, 0x225a)
    count = pos + 2
  }

  private[this] def writeLocalDate(x: LocalDate): Unit = {
    var pos = ensureBufCapacity(19) // 19 == java.time.Year.MAX_VALUE.toString.length + 9
    val buf = this.buf
    val ds  = digits
    buf(pos) = '"'
    pos = writeYear(x.getYear, pos + 1, buf, ds)
    val d1 = ds(x.getMonthValue) << 8
    val d2 = ds(x.getDayOfMonth).toLong << 32
    ByteArrayAccess.setLong(buf, pos, d1 | d2 | 0x2200002d00002dL)
    count = pos + 7
  }

  private[this] def writeLocalDateTime(x: LocalDateTime): Unit = {
    var pos = ensureBufCapacity(37) // 37 == LocalDateTime.MAX.toString.length + 2
    val buf = this.buf
    val ds  = digits
    buf(pos) = '"'
    pos = writeLocalTime(x.toLocalTime, writeLocalDateWithT(x.toLocalDate, pos + 1, buf, ds), buf, ds)
    buf(pos) = '"'
    count = pos + 1
  }

  private[this] def writeLocalTime(x: LocalTime): Unit = {
    var pos = ensureBufCapacity(20) // 20 == LocalTime.MAX.toString.length + 2
    val buf = this.buf
    val ds  = digits
    buf(pos) = '"'
    pos = writeLocalTime(x, pos + 1, buf, ds)
    buf(pos) = '"'
    count = pos + 1
  }

  private[this] def writeMonthDay(x: MonthDay): Unit = {
    val pos = ensureBufCapacity(9) // 9 == "--01-01".length + 2
    val buf = this.buf
    val ds  = digits
    buf(pos) = '"'
    val d1 = ds(x.getMonthValue) << 16
    val d2 = ds(x.getDayOfMonth).toLong << 40
    ByteArrayAccess.setLong(buf, pos + 1, d1 | d2 | 0x2200002d00002d2dL)
    count = pos + 9
  }

  private[this] def writeOffsetDateTime(x: OffsetDateTime): Unit = {
    val pos = ensureBufCapacity(46) // 46 == "+999999999-12-31T23:59:59.999999999+00:00:01".length + 2
    val buf = this.buf
    val ds  = digits
    buf(pos) = '"'
    count = writeOffset(
      x.getOffset,
      writeLocalTime(x.toLocalTime, writeLocalDateWithT(x.toLocalDate, pos + 1, buf, ds), buf, ds),
      buf,
      ds
    )
  }

  private[this] def writeOffsetTime(x: OffsetTime): Unit = {
    val pos = ensureBufCapacity(29) // 29 == "00:00:07.999999998+00:00:08".length + 2
    val buf = this.buf
    val ds  = digits
    buf(pos) = '"'
    count = writeOffset(x.getOffset, writeLocalTime(x.toLocalTime, pos + 1, buf, ds), buf, ds)
  }

  private[this] def writePeriod(x: Period): Unit = {
    var pos    = ensureBufCapacity(39) // 39 == "P-2147483648Y-2147483648M-2147483648D".length + 2
    val buf    = this.buf
    val years  = x.getYears
    val months = x.getMonths
    val days   = x.getDays
    ByteArrayAccess.setLong(buf, pos, 0x2244305022L)
    if ((years | months | days) == 0) count = pos + 5
    else {
      pos += 2
      val ds      = digits
      var q0      = years
      var b: Byte = 'Y'
      while (true) {
        if (q0 != 0) {
          if (q0 < 0) {
            q0 = -q0
            ByteArrayAccess.setShort(buf, pos, 0x322d)
            pos += 1
            if (q0 == -2147483648) {
              q0 = 147483648
              pos += 1
            }
          }
          pos += digitCount(q0.toLong)
          writePositiveIntDigits(q0, pos, buf, ds)
          buf(pos) = b
          pos += 1
        }
        if (b == 'Y') {
          q0 = months
          b = 'M'
        } else if (b == 'M') {
          q0 = days
          b = 'D'
        } else {
          buf(pos) = '"'
          count = pos + 1
          return
        }
      }
    }
  }

  private[this] def writeYear(x: Year): Unit = {
    var pos = ensureBufCapacity(12) // 12 == "+999999999".length + 2
    val buf = this.buf
    buf(pos) = '"'
    pos = writeYear(x.getValue, pos + 1, buf, digits)
    buf(pos) = '"'
    count = pos + 1
  }

  private[this] def writeYearMonth(x: YearMonth): Unit = {
    var pos = ensureBufCapacity(15) // 15 == "+999999999-12".length + 2
    val buf = this.buf
    val ds  = digits
    buf(pos) = '"'
    pos = writeYear(x.getYear, pos + 1, buf, ds)
    ByteArrayAccess.setInt(buf, pos, ds(x.getMonthValue) << 8 | 0x2200002d)
    count = pos + 4
  }

  private[this] def writeZonedDateTime(x: ZonedDateTime): Unit = {
    var pos = ensureBufCapacity(46) // 46 == "+999999999-12-31T23:59:59.999999999+00:00:01".length + 2
    var buf = this.buf
    val ds  = digits
    buf(pos) = '"'
    pos = writeOffset(
      x.getOffset,
      writeLocalTime(x.toLocalTime, writeLocalDateWithT(x.toLocalDate, pos + 1, buf, ds), buf, ds),
      buf,
      ds
    )
    val zone = x.getZone
    if (!zone.isInstanceOf[ZoneOffset]) {
      buf(pos - 1) = '['
      val zoneId   = zone.getId
      val len      = zoneId.length
      val required = len + 3
      if (pos + required > limit) {
        pos = flushAndGrowBuf(required, pos)
        buf = this.buf
      }
      zoneId.getBytes(0, len, buf, pos): @nowarn
      pos += len
      ByteArrayAccess.setShort(buf, pos, 0x225d)
      pos += 2
    }
    count = pos
  }

  private[this] def writeZoneOffset(x: ZoneOffset): Unit = {
    var pos = ensureBufCapacity(12) // 12 == number of bytes in Long and Int
    val buf = this.buf
    var y   = x.getTotalSeconds
    if (y == 0) {
      ByteArrayAccess.setInt(buf, pos, 0x225a22)
      pos += 3
    } else {
      val ds = digits
      val s  = y >> 31
      y =
        ((y + s) ^ s) * 37283 // Based on James Anhalt's algorithm: https://jk-jeon.github.io/posts/2022/02/jeaiii-algorithm/
      val m = ds(y >>> 27) << 16 | 0x2230303a00002b22L - (s << 9)
      if ((y & 0x7ff8000) == 0) { // check if totalSeconds is divisible by 3600
        ByteArrayAccess.setLong(buf, pos, m)
        pos += 8
      } else {
        y &= 0x7ffffff
        y *= 15
        ByteArrayAccess.setLong(buf, pos, ds(y >> 25).toLong << 40 | m)
        if ((y & 0x1f80000) == 0) pos += 8 // check if totalSeconds is divisible by 60
        else {
          ByteArrayAccess.setInt(buf, pos + 7, ds((y & 0x1ffffff) * 15 >> 23) << 8 | 0x2200003a)
          pos += 11
        }
      }
    }
    count = pos
  }

  private[this] def writeLocalDateWithT(x: LocalDate, p: Int, buf: Array[Byte], ds: Array[Short]): Int = {
    val pos = writeYear(x.getYear, p, buf, ds)
    val d1  = ds(x.getMonthValue) << 8
    val d2  = ds(x.getDayOfMonth).toLong << 32
    ByteArrayAccess.setLong(buf, pos, d1 | d2 | 0x5400002d00002dL)
    pos + 7
  }

  private[this] def writeYear(year: Int, pos: Int, buf: Array[Byte], ds: Array[Short]): Int =
    if (year >= 0 && year < 10000) write4Digits(year, pos, buf, ds)
    else writeYearWithSign(year, pos, buf, ds)

  private[this] def writeYearWithSign(year: Int, p: Int, buf: Array[Byte], ds: Array[Short]): Int = {
    var q0      = year
    var pos     = p
    var b: Byte = '+'
    if (q0 < 0) {
      q0 = -q0
      b = '-'
    }
    buf(pos) = b
    pos += 1
    if (q0 < 10000) write4Digits(q0, pos, buf, ds)
    else {
      pos += digitCount(q0.toLong)
      writePositiveIntDigits(q0, pos, buf, ds)
      pos
    }
  }

  private[this] def writeLocalTime(x: LocalTime, pos: Int, buf: Array[Byte], ds: Array[Short]): Int = {
    val second = x.getSecond
    val nano   = x.getNano
    val d1     = ds(x.getHour) | 0x3a00003a0000L
    val d2     = ds(x.getMinute).toLong << 24
    if ((second | nano) == 0) {
      ByteArrayAccess.setLong(buf, pos, d1 | d2)
      pos + 5
    } else {
      val d3 = ds(second).toLong << 48
      ByteArrayAccess.setLong(buf, pos, d1 | d2 | d3)
      if (nano == 0) pos + 8
      else writeNanos(nano, pos + 8, buf, ds)
    }
  }

  private[this] def writeNanos(x: Int, pos: Int, buf: Array[Byte], ds: Array[Short]): Int = {
    val y1 =
      x * 1441151881L // Based on James Anhalt's algorithm for 9 digits: https://jk-jeon.github.io/posts/2022/02/jeaiii-algorithm/
    val y2 = (y1 & 0x1ffffffffffffffL) * 100
    var m  = y1 >>> 57 << 8 | ds((y2 >>> 57).toInt) << 16 | 0x302e
    if ((y2 & 0x1fffff800000000L) == 0) { // check if q0 is divisible by 1000000
      ByteArrayAccess.setInt(buf, pos, m.toInt)
      pos + 4
    } else {
      val y3 = (y2 & 0x1ffffffffffffffL) * 100
      val y4 = (y3 & 0x1ffffffffffffffL) * 100
      m |= ds((y3 >>> 57).toInt).toLong << 32
      val d = ds((y4 >>> 57).toInt)
      ByteArrayAccess.setLong(buf, pos, m | d.toLong << 48)
      if ((y4 & 0x1ff000000000000L) == 0 && d <= 0x3039) pos + 7 // check if x is divisible by 1000
      else {
        ByteArrayAccess.setShort(buf, pos + 8, ds(((y4 & 0x1ffffffffffffffL) * 100 >>> 57).toInt))
        pos + 10
      }
    }
  }

  private[this] def writeOffset(x: ZoneOffset, pos: Int, buf: Array[Byte], ds: Array[Short]): Int = {
    var y = x.getTotalSeconds
    if (y == 0) {
      ByteArrayAccess.setShort(buf, pos, 0x225a)
      pos + 2
    } else {
      val s = y >> 31
      y =
        ((y + s) ^ s) * 37283 // Based on James Anhalt's algorithm: https://jk-jeon.github.io/posts/2022/02/jeaiii-algorithm/
      val m = ds(y >>> 27) << 8 | 0x2230303a00002bL - (s << 1)
      if ((y & 0x7ff8000) == 0) { // check if totalSeconds is divisible by 3600
        ByteArrayAccess.setLong(buf, pos, m)
        pos + 7
      } else {
        y &= 0x7ffffff
        y *= 15
        ByteArrayAccess.setLong(buf, pos, ds(y >> 25).toLong << 32 | m)
        if ((y & 0x1f80000) == 0) pos + 7 // check if totalSeconds is divisible by 60
        else {
          ByteArrayAccess.setInt(buf, pos + 6, ds((y & 0x1ffffff) * 15 >> 23) << 8 | 0x2200003a)
          pos + 10
        }
      }
    }
  }

  private[this] def write3Digits(x: Int, pos: Int, buf: Array[Byte], ds: Array[Short]): Int = {
    val q1 = x * 1311 >> 17 // divide a small positive int by 100
    ByteArrayAccess.setInt(buf, pos, ds(x - q1 * 100) << 8 | q1 | '0')
    pos + 3
  }

  private[this] def write4Digits(x: Int, pos: Int, buf: Array[Byte], ds: Array[Short]): Int = {
    val q1 = x * 5243 >> 19 // divide a small positive int by 100
    val d1 = ds(x - q1 * 100) << 16
    val d2 = ds(q1)
    ByteArrayAccess.setInt(buf, pos, d1 | d2)
    pos + 4
  }

  private[this] def write8Digits(x: Long, pos: Int, buf: Array[Byte], ds: Array[Short]): Int = {
    val y1 =
      x * 140737489 // Based on James Anhalt's algorithm for 8 digits: https://jk-jeon.github.io/posts/2022/02/jeaiii-algorithm/
    val m1 = 0x7fffffffffffL
    val m2 = 100L
    val y2 = (y1 & m1) * m2
    val y3 = (y2 & m1) * m2
    val y4 = (y3 & m1) * m2
    val d1 = ds((y1 >> 47).toInt)
    val d2 = ds((y2 >> 47).toInt) << 16
    val d3 = ds((y3 >> 47).toInt).toLong << 32
    val d4 = ds((y4 >> 47).toInt).toLong << 48
    ByteArrayAccess.setLong(buf, pos, d1 | d2 | d3 | d4)
    pos + 8
  }

  private[this] def write18Digits(x: Long, pos: Int, buf: Array[Byte], ds: Array[Short]): Int = {
    val m1 = 6189700196426901375L
    val q1 = Math.multiplyHigh(x, m1) >>> 25  // divide a positive long by 100000000
    val q2 = Math.multiplyHigh(q1, m1) >>> 25 // divide a positive long by 100000000
    ByteArrayAccess.setShort(buf, pos, ds(q2.toInt))
    write8Digits(x - q1 * 100000000L, write8Digits(q1 - q2 * 100000000L, pos + 2, buf, ds), buf, ds)
  }

  private[this] def writeShort(x: Short): Unit = {
    var pos = ensureBufCapacity(9) // 8 bytes in long + a byte for the sign
    val buf = this.buf
    val ds  = digits
    var q0  = x.toInt
    if (q0 < 0) {
      q0 = -q0
      buf(pos) = '-'
      pos += 1
    }
    if (q0 < 100) {
      if (q0 < 10) {
        buf(pos) = (q0 | '0').toByte
        pos += 1
      } else {
        ByteArrayAccess.setShort(buf, pos, ds(q0))
        pos += 2
      }
    } else if (q0 < 10000) {
      val q1 = q0 * 5243 >> 19 // divide a small positive int by 100
      val d2 = ds(q0 - q1 * 100)
      if (q0 < 1000) {
        ByteArrayAccess.setInt(buf, pos, q1 | '0' | d2 << 8)
        pos += 3
      } else {
        ByteArrayAccess.setInt(buf, pos, ds(q1) | d2 << 16)
        pos += 4
      }
    } else {
      val y1 =
        q0 * 429497L // Based on James Anhalt's algorithm for 5 digits: https://jk-jeon.github.io/posts/2022/02/jeaiii-algorithm/
      val y2 = (y1 & 0xffffffffL) * 100
      val y3 = (y2 & 0xffffffffL) * 100
      val d1 = (y1 >> 32).toInt | '0'
      val d2 = ds((y2 >> 32).toInt) << 8
      val d3 = ds((y3 >> 32).toInt).toLong << 24
      ByteArrayAccess.setLong(buf, pos, d1 | d2 | d3)
      pos += 5
    }
    count = pos
  }

  private[this] def writeInt(x: Int): Unit = {
    var pos = ensureBufCapacity(11) // Int.MinValue.toString.length
    val buf = this.buf
    val ds  = digits
    var q0  = x
    if (x < 0) {
      q0 = -q0
      ByteArrayAccess.setShort(buf, pos, 0x322d)
      pos += 1
      if (q0 == x) {
        q0 = 147483648
        pos += 1
      }
    }
    pos += digitCount(q0.toLong)
    writePositiveIntDigits(q0, pos, buf, ds)
    count = pos
  }

  private[this] def writeLong(x: Long): Unit =
    count = writeLong(x, ensureBufCapacity(20), buf) // Long.MinValue.toString.length

  private[this] def writeLong(x: Long, p: Int, buf: Array[Byte]): Int = {
    var pos = p
    val ds  = digits
    var q0  = x
    if (x < 0) {
      q0 = -q0
      ByteArrayAccess.setInt(buf, pos, 0x3232392d)
      pos += 1
      if (q0 == x) {
        q0 = 3372036854775808L
        pos += 3
      }
    }
    val m1      = 100000000L
    var q2      = q0
    var lastPos = pos
    if (q0 < m1) {
      lastPos += digitCount(q0)
      pos = lastPos
    } else {
      val m2 = 6189700196426901375L
      val q1 = Math.multiplyHigh(q0, m2) >>> 25 // divide a positive long by 100000000
      if (q1 < m1) {
        q2 = q1
        lastPos += digitCount(q1)
        pos = lastPos
      } else {
        q2 = Math.multiplyHigh(q1, m2) >>> 25 // divide a small positive long by 100000000
        lastPos += digitCount(q2)
        pos = write8Digits(q1 - q2 * m1, lastPos, buf, ds)
      }
      pos = write8Digits(q0 - q1 * m1, pos, buf, ds)
    }
    writePositiveIntDigits(q2.toInt, lastPos, buf, ds)
    pos
  }

  // Based on the ingenious work of Xiang JunBo and Wang TieJun
  // "xjb: Fast Float to String Algorithm": https://github.com/xjb714/xjb/blob/4852e533287bd0e8d554c2a9f4cc6eaa93ca799f/fast_f2s.pdf
  // Sources with the license are here: https://github.com/xjb714/xjb
  private[this] def writeFloat(x: Float): Unit = {
    val bits = java.lang.Float.floatToRawIntBits(x)
    var pos  = ensureBufCapacity(15)
    val buf  = this.buf
    if (bits < 0) {
      buf(pos) = '-'
      pos += 1
    }
    if (bits << 1 == 0) {
      ByteArrayAccess.setInt(buf, pos, 0x302e30)
      pos += 3
    } else {
      val e2IEEE   = bits >> 23 & 0xff
      val m2IEEE   = bits & 0x7fffff
      var e2       = e2IEEE - 150
      var m2       = m2IEEE | 0x800000
      var m10, e10 = 0
      if (e2 == 0) m10 = m2
      else if ((e2 >= -23 && e2 < 0) && m2 << e2 == 0) m10 = m2 >> -e2
      else {
        if (e2IEEE == 0) {
          m2 = m2IEEE
          e2 = -149
        } else if (e2 == 105) illegalNumberError(x)
        if (m2IEEE == 0) e10 = (e2 * 315653 - 131237) >> 20
        else e10 = (e2 * 315653) >> 20
        val h     = (((e10 + 1) * -217707) >> 16) + e2
        val pow10 = floatPow10s(31 - e10)
        val hi64  = unsignedMultiplyHigh1(
          pow10,
          m2.toLong << (h + 37)
        ) // TODO: when dropping JDK 17 support replace by Math.unsignedMultiplyHigh(pow10, m2.toLong << (h + 37))
        m10 = (hi64 >>> 36).toInt * 10
        val halfUlpPlusEven = (pow10 >>> (28 - h)) + ((m2IEEE + 1) & 1)
        val dotOne          = hi64 & 0xfffffffffL
        if (
          {
            if (m2IEEE == 0) halfUlpPlusEven >>> 1
            else halfUlpPlusEven
          } <= dotOne
        ) {
          if (halfUlpPlusEven > 0xfffffffffL - dotOne) m10 += 10
          else m10 += ((dotOne * 20 + ((hi64 >>> 32).toInt & 0xf) + 0xffffffff9L) >>> 37).toInt
        }
        if (m2IEEE == 0 && ((e2 == -119) | (e2 == 64) | (e2 == 67))) m10 += 1
      }
      val len = digitCount(m10.toLong)
      e10 += len - 1
      val ds = digits
      if (e10 < -3 || e10 >= 7) {
        val lastPos = writeSignificantFractionDigits(m10, pos + len, pos, buf, ds)
        ByteArrayAccess.setShort(buf, pos, (buf(pos + 1) | 0x2e00).toShort)
        if (lastPos - 3 < pos) {
          buf(lastPos) = '0'
          pos = lastPos + 1
        } else pos = lastPos
        ByteArrayAccess.setShort(buf, pos, 0x2d45)
        pos += 1
        if (e10 < 0) {
          e10 = -e10
          pos += 1
        }
        if (e10 < 10) {
          buf(pos) = (e10 | '0').toByte
          pos += 1
        } else {
          ByteArrayAccess.setShort(buf, pos, ds(e10))
          pos += 2
        }
      } else if (e10 < 0) {
        val dotPos = pos + 1
        ByteArrayAccess.setInt(buf, pos, 0x30303030)
        pos -= e10
        pos = writeSignificantFractionDigits(m10, pos + len, pos, buf, ds)
        buf(dotPos) = '.'
      } else if (e10 < len - 1) {
        val lastPos = writeSignificantFractionDigits(m10, pos + len, pos, buf, ds)
        val bs      = ByteArrayAccess.getLong(buf, pos)
        val s       = e10 << 3
        val m       = 0xffffffffffff0000L << s
        val d1      = (~m & bs) >> 8
        val d2      = 0x2e00L << s
        val d3      = m & bs
        ByteArrayAccess.setLong(buf, pos, d1 | d2 | d3)
        pos = lastPos
      } else {
        pos += len
        writePositiveIntDigits(m10, pos, buf, ds)
        ByteArrayAccess.setShort(buf, pos, 0x302e)
        pos += 2
      }
    }
    count = pos
  }

  private[this] def unsignedMultiplyHigh1(x: Long, y: Long): Long =
    Math.multiplyHigh(x, y) + y // Use implementation that works only when x is negative and y is positive

  // Based on the ingenious work of Xiang JunBo and Wang TieJun
  // "xjb: Fast Float to String Algorithm": https://github.com/xjb714/xjb/blob/4852e533287bd0e8d554c2a9f4cc6eaa93ca799f/fast_f2s.pdf
  // Sources with the license are here: https://github.com/xjb714/xjb
  private[this] def writeDouble(x: Double): Unit = {
    val bits = java.lang.Double.doubleToRawLongBits(x)
    var pos  = ensureBufCapacity(25) // -1.2898455142673966E-135.toString.length + 1
    val buf  = this.buf
    if (bits < 0L) {
      buf(pos) = '-'
      pos += 1
    }
    if (bits << 1 == 0L) {
      ByteArrayAccess.setInt(buf, pos, 0x302e30)
      pos += 3
    } else {
      val e2IEEE = (bits >> 52).toInt & 0x7ff
      val m2IEEE = bits & 0xfffffffffffffL
      var e2     = e2IEEE - 1075
      var m2     = m2IEEE | 0x10000000000000L
      var m10    = 0L
      var e10    = 0
      if (e2 == 0) m10 = m2
      else if ((e2 >= -52 && e2 < 0) && m2 << e2 == 0) m10 = m2 >> -e2
      else {
        if (e2IEEE == 0) {
          m2 = m2IEEE
          e2 = -1074
        } else if (e2 == 972) illegalNumberError(x)
        if (m2IEEE == 0) e10 = (e2 * 315653 - 131237) >> 20
        else e10 = (e2 * 315653) >> 20
        val h       = (((e10 + 1) * -217707) >> 16) + e2
        val pow10s  = doublePow10s
        val i       = 292 - e10 << 1
        val pow10_1 = pow10s(i)
        val pow10_2 = pow10s(i + 1)
        val cb      = m2 << (h + 7)
        val lo64_1  = unsignedMultiplyHigh2(
          pow10_2,
          cb
        ) // TODO: when dropping JDK 17 support replace by Math.unsignedMultiplyHigh(pow10_2, cb)
        val lo64_2 = pow10_1 * cb
        var hi64   = unsignedMultiplyHigh2(
          pow10_1,
          cb
        ) // TODO: when dropping JDK 17 support replace by Math.unsignedMultiplyHigh(pow10_1, cb)
        val lo64 = lo64_1 + lo64_2
        hi64 += compareUnsigned(lo64, lo64_1) >>> 31
        m10 = (hi64 >>> 6) * 10L
        val halfUlpPlusEven = (pow10_1 >>> -h) + ((m2.toInt + 1) & 1)
        val dotOne          = (hi64 << 58) | (lo64 >>> 6)
        if (compareUnsigned(halfUlpPlusEven, -1 - dotOne) > 0) m10 += 10L
        else if (m2IEEE != 0) {
          if (compareUnsigned(halfUlpPlusEven, dotOne) <= 0) m10 = calculateM10(hi64, lo64, dotOne)
        } else {
          val tmp = (dotOne >>> 4) * 10L
          if (compareUnsigned((tmp << 4) >>> 4, (halfUlpPlusEven >>> 4) * 5L) > 0) m10 += (tmp >>> 60).toInt + 1
          else if (compareUnsigned(halfUlpPlusEven >>> 1, dotOne) <= 0) m10 = calculateM10(hi64, lo64, dotOne)
        }
      }
      val len = digitCount(m10)
      e10 += len - 1
      val ds = digits
      if (e10 < -3 || e10 >= 7) {
        val lastPos = writeSignificantFractionDigits(m10, pos + len, pos, buf, ds)
        ByteArrayAccess.setShort(buf, pos, (buf(pos + 1) | 0x2e00).toShort)
        if (lastPos - 3 < pos) {
          buf(lastPos) = '0'
          pos = lastPos + 1
        } else pos = lastPos
        ByteArrayAccess.setShort(buf, pos, 0x2d45)
        pos += 1
        if (e10 < 0) {
          e10 = -e10
          pos += 1
        }
        if (e10 < 10) {
          buf(pos) = (e10 | '0').toByte
          pos += 1
        } else if (e10 < 100) {
          ByteArrayAccess.setShort(buf, pos, ds(e10))
          pos += 2
        } else pos = write3Digits(e10, pos, buf, ds)
      } else if (e10 < 0) {
        val dotPos = pos + 1
        ByteArrayAccess.setInt(buf, pos, 0x30303030)
        pos -= e10
        pos = writeSignificantFractionDigits(m10, pos + len, pos, buf, ds)
        buf(dotPos) = '.'
      } else if (e10 < len - 1) {
        val lastPos = writeSignificantFractionDigits(m10, pos + len, pos, buf, ds)
        val bs      = ByteArrayAccess.getLong(buf, pos)
        val s       = e10 << 3
        val m       = 0xffffffffffff0000L << s
        val d1      = (~m & bs) >> 8
        val d2      = 0x2e00L << s
        val d3      = m & bs
        ByteArrayAccess.setLong(buf, pos, d1 | d2 | d3)
        pos = lastPos
      } else {
        pos += len
        writePositiveIntDigits(m10.toInt, pos, buf, ds)
        ByteArrayAccess.setShort(buf, pos, 0x302e)
        pos += 2
      }
    }
    count = pos
  }

  private[this] def calculateM10(hi: Long, lo: Long, dotOne: Long): Long =
    (hi * 10L + unsignedMultiplyHigh2(
      lo,
      10L
    ) + { // TODO: when dropping JDK 17 support replace by Math.unsignedMultiplyHigh(lo64, 10L)
      if (dotOne == 0x4000000000000000L) 0x1fL
      else 0x20L
    }) >>> 6

  private[this] def unsignedMultiplyHigh2(x: Long, y: Long): Long =
    Math.multiplyHigh(x, y) + (y & (x >> 63)) // Use implementation that works only when y is positive

  // Adoption of a nice trick from Daniel Lemire's blog that works for numbers up to 10^18:
  // https://lemire.me/blog/2021/06/03/computing-the-number-of-digits-of-an-integer-even-faster/
  private[this] def digitCount(x: Long): Int = (offsets(java.lang.Long.numberOfLeadingZeros(x)) + x >> 58).toInt

  private[this] def writeSignificantFractionDigits(
    x: Long,
    p: Int,
    pl: Int,
    buf: Array[Byte],
    ds: Array[Short]
  ): Int = {
    var q0     = x.toInt
    var pos    = p
    var posLim = pl
    if (q0 != x) {
      val q1    = (Math.multiplyHigh(x, 6189700196426901375L) >>> 25).toInt // divide a positive long by 100000000
      val r1    = (x - q1 * 100000000L).toInt
      val posm8 = pos - 8
      if (r1 == 0) {
        q0 = q1
        pos = posm8
      } else {
        writeFractionDigits(q1, posm8, posLim, buf, ds)
        q0 = r1
        posLim = posm8
      }
    }
    writeSignificantFractionDigits(q0, pos, posLim, buf, ds)
  }

  private[this] def writeSignificantFractionDigits(
    x: Int,
    p: Int,
    posLim: Int,
    buf: Array[Byte],
    ds: Array[Short]
  ): Int = {
    var q0  = x
    var q1  = 0
    var pos = p
    while ({
      val qp = q0 * 1374389535L
      q1 = (qp >> 37).toInt     // divide a positive int by 100
      (qp & 0x1fc0000000L) == 0 // check if q is divisible by 100
    }) {
      q0 = q1
      pos -= 2
    }
    val d = ds(q0 - q1 * 100)
    ByteArrayAccess.setShort(buf, pos - 1, d)
    writeFractionDigits(q1, pos - 2, posLim, buf, ds)
    pos + ((0x3039 - d) >>> 31)
  }

  private[this] def writeFractionDigits(x: Int, p: Int, posLim: Int, buf: Array[Byte], ds: Array[Short]): Unit = {
    var q0  = x
    var pos = p
    while (pos > posLim) {
      val q1 = (q0 * 1374389535L >> 37).toInt // divide a positive int by 100
      ByteArrayAccess.setShort(buf, pos - 1, ds(q0 - q1 * 100))
      q0 = q1
      pos -= 2
    }
  }

  private[this] def writePositiveIntDigits(x: Int, p: Int, buf: Array[Byte], ds: Array[Short]): Unit = {
    var q0  = x
    var pos = p
    while ({
      pos -= 2
      q0 >= 100
    }) {
      val q1 = (q0 * 1374389535L >> 37).toInt // divide a positive int by 100
      ByteArrayAccess.setShort(buf, pos, ds(q0 - q1 * 100))
      q0 = q1
    }
    if (q0 < 10) buf(pos + 1) = (q0 | '0').toByte
    else ByteArrayAccess.setShort(buf, pos, ds(q0))
  }

  private[this] def illegalNumberError(x: Float): Nothing = encodeError("illegal number: " + x)

  private[this] def illegalNumberError(x: Double): Nothing = encodeError("illegal number: " + x)

  private[this] def ensureBufCapacity(required: Int): Int = {
    val pos = count
    if (pos + required <= limit) pos
    else flushAndGrowBuf(required, pos)
  }

  private[this] def flushAndGrowBuf(required: Int, pos: Int): Int =
    if (bbuf ne null) {
      bbuf.put(buf, 0, pos)
      if (required > limit) growBuf(required)
      0
    } else if (out ne null) {
      out.write(buf, 0, pos)
      if (required > limit) growBuf(required)
      0
    } else if (disableBufGrowing) throw new ArrayIndexOutOfBoundsException("`buf` length exceeded")
    else {
      growBuf(pos + required)
      pos
    }

  private[this] def growBuf(required: Int): Unit =
    setBuf(java.util.Arrays.copyOf(buf, (-1 >>> Integer.numberOfLeadingZeros(limit | required)) + 1))

  private[this] def reallocateBufToPreferredSize(): Unit = setBuf(new Array[Byte](config.preferredBufSize))

  private[this] def setBuf(buf: Array[Byte]): Unit = {
    this.buf = buf
    limit = buf.length
  }
}

object JsonWriter {
  private final val escapedChars: Array[Byte] = Array(
    -1, -1, -1, -1, -1, -1, -1, -1, 98, 116, 110, -1, 102, 114, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
    -1, -1, -1, -1, 0, 0, 34, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
    0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 92, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
    0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, -1
  )
  private final val offsets = Array(5088146770730811392L, 5088146770730811392L, 5088146770730811392L,
    5088146770730811392L, 5088146770730811392L, 5088146770730811392L, 5088146770730811392L, 5088146770730811392L,
    4889916394579099648L, 4889916394579099648L, 4889916394579099648L, 4610686018427387904L, 4610686018427387904L,
    4610686018427387904L, 4610686018427387904L, 4323355642275676160L, 4323355642275676160L, 4323355642275676160L,
    4035215266123964416L, 4035215266123964416L, 4035215266123964416L, 3746993889972252672L, 3746993889972252672L,
    3746993889972252672L, 3746993889972252672L, 3458764413820540928L, 3458764413820540928L, 3458764413820540928L,
    3170534127668829184L, 3170534127668829184L, 3170534127668829184L, 2882303760517117440L, 2882303760517117440L,
    2882303760517117440L, 2882303760517117440L, 2594073385265405696L, 2594073385265405696L, 2594073385265405696L,
    2305843009203693952L, 2305843009203693952L, 2305843009203693952L, 2017612633060982208L, 2017612633060982208L,
    2017612633060982208L, 2017612633060982208L, 1729382256910170464L, 1729382256910170464L, 1729382256910170464L,
    1441151880758548720L, 1441151880758548720L, 1441151880758548720L, 1152921504606845976L, 1152921504606845976L,
    1152921504606845976L, 1152921504606845976L, 864691128455135132L, 864691128455135132L, 864691128455135132L,
    576460752303423478L, 576460752303423478L, 576460752303423478L, 576460752303423478L, 576460752303423478L,
    576460752303423478L, 576460752303423478L)
  private final val digits: Array[Short] = Array(
    12336, 12592, 12848, 13104, 13360, 13616, 13872, 14128, 14384, 14640, 12337, 12593, 12849, 13105, 13361, 13617,
    13873, 14129, 14385, 14641, 12338, 12594, 12850, 13106, 13362, 13618, 13874, 14130, 14386, 14642, 12339, 12595,
    12851, 13107, 13363, 13619, 13875, 14131, 14387, 14643, 12340, 12596, 12852, 13108, 13364, 13620, 13876, 14132,
    14388, 14644, 12341, 12597, 12853, 13109, 13365, 13621, 13877, 14133, 14389, 14645, 12342, 12598, 12854, 13110,
    13366, 13622, 13878, 14134, 14390, 14646, 12343, 12599, 12855, 13111, 13367, 13623, 13879, 14135, 14391, 14647,
    12344, 12600, 12856, 13112, 13368, 13624, 13880, 14136, 14392, 14648, 12345, 12601, 12857, 13113, 13369, 13625,
    13881, 14137, 14393, 14649
  )
  private final val lowerCaseHexDigits: Array[Short] = Array(
    12336, 12592, 12848, 13104, 13360, 13616, 13872, 14128, 14384, 14640, 24880, 25136, 25392, 25648, 25904, 26160,
    12337, 12593, 12849, 13105, 13361, 13617, 13873, 14129, 14385, 14641, 24881, 25137, 25393, 25649, 25905, 26161,
    12338, 12594, 12850, 13106, 13362, 13618, 13874, 14130, 14386, 14642, 24882, 25138, 25394, 25650, 25906, 26162,
    12339, 12595, 12851, 13107, 13363, 13619, 13875, 14131, 14387, 14643, 24883, 25139, 25395, 25651, 25907, 26163,
    12340, 12596, 12852, 13108, 13364, 13620, 13876, 14132, 14388, 14644, 24884, 25140, 25396, 25652, 25908, 26164,
    12341, 12597, 12853, 13109, 13365, 13621, 13877, 14133, 14389, 14645, 24885, 25141, 25397, 25653, 25909, 26165,
    12342, 12598, 12854, 13110, 13366, 13622, 13878, 14134, 14390, 14646, 24886, 25142, 25398, 25654, 25910, 26166,
    12343, 12599, 12855, 13111, 13367, 13623, 13879, 14135, 14391, 14647, 24887, 25143, 25399, 25655, 25911, 26167,
    12344, 12600, 12856, 13112, 13368, 13624, 13880, 14136, 14392, 14648, 24888, 25144, 25400, 25656, 25912, 26168,
    12345, 12601, 12857, 13113, 13369, 13625, 13881, 14137, 14393, 14649, 24889, 25145, 25401, 25657, 25913, 26169,
    12385, 12641, 12897, 13153, 13409, 13665, 13921, 14177, 14433, 14689, 24929, 25185, 25441, 25697, 25953, 26209,
    12386, 12642, 12898, 13154, 13410, 13666, 13922, 14178, 14434, 14690, 24930, 25186, 25442, 25698, 25954, 26210,
    12387, 12643, 12899, 13155, 13411, 13667, 13923, 14179, 14435, 14691, 24931, 25187, 25443, 25699, 25955, 26211,
    12388, 12644, 12900, 13156, 13412, 13668, 13924, 14180, 14436, 14692, 24932, 25188, 25444, 25700, 25956, 26212,
    12389, 12645, 12901, 13157, 13413, 13669, 13925, 14181, 14437, 14693, 24933, 25189, 25445, 25701, 25957, 26213,
    12390, 12646, 12902, 13158, 13414, 13670, 13926, 14182, 14438, 14694, 24934, 25190, 25446, 25702, 25958, 26214
  )
  private final val floatPow10s: Array[Long] = Array(
    0xcfb11ead453994bbL, // -32
    0x81ceb32c4b43fcf5L, // -31
    0xa2425ff75e14fc32L, // -30
    0xcad2f7f5359a3b3fL, // -29
    0xfd87b5f28300ca0eL, // -28
    0x9e74d1b791e07e49L, // -27
    0xc612062576589ddbL, // -26
    0xf79687aed3eec552L, // -25
    0x9abe14cd44753b53L, // -24
    0xc16d9a0095928a28L, // -23
    0xf1c90080baf72cb2L, // -22
    0x971da05074da7befL, // -21
    0xbce5086492111aebL, // -20
    0xec1e4a7db69561a6L, // -19
    0x9392ee8e921d5d08L, // -18
    0xb877aa3236a4b44aL, // -17
    0xe69594bec44de15cL, // -16
    0x901d7cf73ab0acdaL, // -15
    0xb424dc35095cd810L, // -14
    0xe12e13424bb40e14L, // -13
    0x8cbccc096f5088ccL, // -12
    0xafebff0bcb24aaffL, // -11
    0xdbe6fecebdedd5bfL, // -10
    0x89705f4136b4a598L, // -9
    0xabcc77118461cefdL, // -8
    0xd6bf94d5e57a42bdL, // -7
    0x8637bd05af6c69b6L, // -6
    0xa7c5ac471b478424L, // -5
    0xd1b71758e219652cL, // -4
    0x83126e978d4fdf3cL, // -3
    0xa3d70a3d70a3d70bL, // -2
    0xcccccccccccccccdL, // -1
    0x8000000000000000L, // 0
    0xa000000000000000L, // 1
    0xc800000000000000L, // 2
    0xfa00000000000000L, // 3
    0x9c40000000000000L, // 4
    0xc350000000000000L, // 5
    0xf424000000000000L, // 6
    0x9896800000000000L, // 7
    0xbebc200000000000L, // 8
    0xee6b280000000000L, // 9
    0x9502f90000000000L, // 10
    0xba43b74000000000L, // 11
    0xe8d4a51000000000L, // 12
    0x9184e72a00000000L, // 13
    0xb5e620f480000000L, // 14
    0xe35fa931a0000000L, // 15
    0x8e1bc9bf04000000L, // 16
    0xb1a2bc2ec5000000L, // 17
    0xde0b6b3a76400000L, // 18
    0x8ac7230489e80000L, // 19
    0xad78ebc5ac620000L, // 20
    0xd8d726b7177a8000L, // 21
    0x878678326eac9000L, // 22
    0xa968163f0a57b400L, // 23
    0xd3c21bcecceda100L, // 24
    0x84595161401484a0L, // 25
    0xa56fa5b99019a5c8L, // 26
    0xcecb8f27f4200f3aL, // 27
    0x813f3978f8940985L, // 28
    0xa18f07d736b90be6L, // 29
    0xc9f2c9cd04674edfL, // 30
    0xfc6f7c4045812297L, // 31
    0x9dc5ada82b70b59eL, // 32
    0xc5371912364ce306L, // 33
    0xf684df56c3e01bc7L, // 34
    0x9a130b963a6c115dL, // 35
    0xc097ce7bc90715b4L, // 36
    0xf0bdc21abb48db21L, // 37
    0x96769950b50d88f5L, // 38
    0xbc143fa4e250eb32L, // 39
    0xeb194f8e1ae525feL, // 40
    0x92efd1b8d0cf37bfL, // 41
    0xb7abc627050305aeL, // 42
    0xe596b7b0c643c71aL, // 43
    0x8f7e32ce7bea5c70L  // 44
  )
  private final val doublePow10s: Array[Long] = Array(
    0xcc5fc196fefd7d0cL, 0x1e53ed49a96272c9L, // -293
    0xff77b1fcbebcdc4fL, 0x25e8e89c13bb0f7bL, // -292
    0x9faacf3df73609b1L, 0x77b191618c54e9adL, // -291
    0xc795830d75038c1dL, 0xd59df5b9ef6a2418L, // -290
    0xf97ae3d0d2446f25L, 0x4b0573286b44ad1eL, // -289
    0x9becce62836ac577L, 0x4ee367f9430aec33L, // -288
    0xc2e801fb244576d5L, 0x229c41f793cda740L, // -287
    0xf3a20279ed56d48aL, 0x6b43527578c11110L, // -286
    0x9845418c345644d6L, 0x830a13896b78aaaaL, // -285
    0xbe5691ef416bd60cL, 0x23cc986bc656d554L, // -284
    0xedec366b11c6cb8fL, 0x2cbfbe86b7ec8aa9L, // -283
    0x94b3a202eb1c3f39L, 0x7bf7d71432f3d6aaL, // -282
    0xb9e08a83a5e34f07L, 0xdaf5ccd93fb0cc54L, // -281
    0xe858ad248f5c22c9L, 0xd1b3400f8f9cff69L, // -280
    0x91376c36d99995beL, 0x23100809b9c21fa2L, // -279
    0xb58547448ffffb2dL, 0xabd40a0c2832a78bL, // -278
    0xe2e69915b3fff9f9L, 0x16c90c8f323f516dL, // -277
    0x8dd01fad907ffc3bL, 0xae3da7d97f6792e4L, // -276
    0xb1442798f49ffb4aL, 0x99cd11cfdf41779dL, // -275
    0xdd95317f31c7fa1dL, 0x40405643d711d584L, // -274
    0x8a7d3eef7f1cfc52L, 0x482835ea666b2573L, // -273
    0xad1c8eab5ee43b66L, 0xda3243650005eed0L, // -272
    0xd863b256369d4a40L, 0x90bed43e40076a83L, // -271
    0x873e4f75e2224e68L, 0x5a7744a6e804a292L, // -270
    0xa90de3535aaae202L, 0x711515d0a205cb37L, // -269
    0xd3515c2831559a83L, 0x0d5a5b44ca873e04L, // -268
    0x8412d9991ed58091L, 0xe858790afe9486c3L, // -267
    0xa5178fff668ae0b6L, 0x626e974dbe39a873L, // -266
    0xce5d73ff402d98e3L, 0xfb0a3d212dc81290L, // -265
    0x80fa687f881c7f8eL, 0x7ce66634bc9d0b9aL, // -264
    0xa139029f6a239f72L, 0x1c1fffc1ebc44e81L, // -263
    0xc987434744ac874eL, 0xa327ffb266b56221L, // -262
    0xfbe9141915d7a922L, 0x4bf1ff9f0062baa9L, // -261
    0x9d71ac8fada6c9b5L, 0x6f773fc3603db4aaL, // -260
    0xc4ce17b399107c22L, 0xcb550fb4384d21d4L, // -259
    0xf6019da07f549b2bL, 0x7e2a53a146606a49L, // -258
    0x99c102844f94e0fbL, 0x2eda7444cbfc426eL, // -257
    0xc0314325637a1939L, 0xfa911155fefb5309L, // -256
    0xf03d93eebc589f88L, 0x793555ab7eba27cbL, // -255
    0x96267c7535b763b5L, 0x4bc1558b2f3458dfL, // -254
    0xbbb01b9283253ca2L, 0x9eb1aaedfb016f17L, // -253
    0xea9c227723ee8bcbL, 0x465e15a979c1caddL, // -252
    0x92a1958a7675175fL, 0x0bfacd89ec191ecaL, // -251
    0xb749faed14125d36L, 0xcef980ec671f667cL, // -250
    0xe51c79a85916f484L, 0x82b7e12780e7401bL, // -249
    0x8f31cc0937ae58d2L, 0xd1b2ecb8b0908811L, // -248
    0xb2fe3f0b8599ef07L, 0x861fa7e6dcb4aa16L, // -247
    0xdfbdcece67006ac9L, 0x67a791e093e1d49bL, // -246
    0x8bd6a141006042bdL, 0xe0c8bb2c5c6d24e1L, // -245
    0xaecc49914078536dL, 0x58fae9f773886e19L, // -244
    0xda7f5bf590966848L, 0xaf39a475506a899fL, // -243
    0x888f99797a5e012dL, 0x6d8406c952429604L, // -242
    0xaab37fd7d8f58178L, 0xc8e5087ba6d33b84L, // -241
    0xd5605fcdcf32e1d6L, 0xfb1e4a9a90880a65L, // -240
    0x855c3be0a17fcd26L, 0x5cf2eea09a550680L, // -239
    0xa6b34ad8c9dfc06fL, 0xf42faa48c0ea481fL, // -238
    0xd0601d8efc57b08bL, 0xf13b94daf124da27L, // -237
    0x823c12795db6ce57L, 0x76c53d08d6b70859L, // -236
    0xa2cb1717b52481edL, 0x54768c4b0c64ca6fL, // -235
    0xcb7ddcdda26da268L, 0xa9942f5dcf7dfd0aL, // -234
    0xfe5d54150b090b02L, 0xd3f93b35435d7c4dL, // -233
    0x9efa548d26e5a6e1L, 0xc47bc5014a1a6db0L, // -232
    0xc6b8e9b0709f109aL, 0x359ab6419ca1091cL, // -231
    0xf867241c8cc6d4c0L, 0xc30163d203c94b63L, // -230
    0x9b407691d7fc44f8L, 0x79e0de63425dcf1eL, // -229
    0xc21094364dfb5636L, 0x985915fc12f542e5L, // -228
    0xf294b943e17a2bc4L, 0x3e6f5b7b17b2939eL, // -227
    0x979cf3ca6cec5b5aL, 0xa705992ceecf9c43L, // -226
    0xbd8430bd08277231L, 0x50c6ff782a838354L, // -225
    0xece53cec4a314ebdL, 0xa4f8bf5635246429L, // -224
    0x940f4613ae5ed136L, 0x871b7795e136be9aL, // -223
    0xb913179899f68584L, 0x28e2557b59846e40L, // -222
    0xe757dd7ec07426e5L, 0x331aeada2fe589d0L, // -221
    0x9096ea6f3848984fL, 0x3ff0d2c85def7622L, // -220
    0xb4bca50b065abe63L, 0x0fed077a756b53aaL, // -219
    0xe1ebce4dc7f16dfbL, 0xd3e8495912c62895L, // -218
    0x8d3360f09cf6e4bdL, 0x64712dd7abbbd95dL, // -217
    0xb080392cc4349decL, 0xbd8d794d96aacfb4L, // -216
    0xdca04777f541c567L, 0xecf0d7a0fc5583a1L, // -215
    0x89e42caaf9491b60L, 0xf41686c49db57245L, // -214
    0xac5d37d5b79b6239L, 0x311c2875c522ced6L, // -213
    0xd77485cb25823ac7L, 0x7d633293366b828cL, // -212
    0x86a8d39ef77164bcL, 0xae5dff9c02033198L, // -211
    0xa8530886b54dbdebL, 0xd9f57f830283fdfdL, // -210
    0xd267caa862a12d66L, 0xd072df63c324fd7cL, // -209
    0x8380dea93da4bc60L, 0x4247cb9e59f71e6eL, // -208
    0xa46116538d0deb78L, 0x52d9be85f074e609L, // -207
    0xcd795be870516656L, 0x67902e276c921f8cL, // -206
    0x806bd9714632dff6L, 0x00ba1cd8a3db53b7L, // -205
    0xa086cfcd97bf97f3L, 0x80e8a40eccd228a5L, // -204
    0xc8a883c0fdaf7df0L, 0x6122cd128006b2ceL, // -203
    0xfad2a4b13d1b5d6cL, 0x796b805720085f82L, // -202
    0x9cc3a6eec6311a63L, 0xcbe3303674053bb1L, // -201
    0xc3f490aa77bd60fcL, 0xbedbfc4411068a9dL, // -200
    0xf4f1b4d515acb93bL, 0xee92fb5515482d45L, // -199
    0x991711052d8bf3c5L, 0x751bdd152d4d1c4bL, // -198
    0xbf5cd54678eef0b6L, 0xd262d45a78a0635eL, // -197
    0xef340a98172aace4L, 0x86fb897116c87c35L, // -196
    0x9580869f0e7aac0eL, 0xd45d35e6ae3d4da1L, // -195
    0xbae0a846d2195712L, 0x8974836059cca10aL, // -194
    0xe998d258869facd7L, 0x2bd1a438703fc94cL, // -193
    0x91ff83775423cc06L, 0x7b6306a34627ddd0L, // -192
    0xb67f6455292cbf08L, 0x1a3bc84c17b1d543L, // -191
    0xe41f3d6a7377eecaL, 0x20caba5f1d9e4a94L, // -190
    0x8e938662882af53eL, 0x547eb47b7282ee9dL, // -189
    0xb23867fb2a35b28dL, 0xe99e619a4f23aa44L, // -188
    0xdec681f9f4c31f31L, 0x6405fa00e2ec94d5L, // -187
    0x8b3c113c38f9f37eL, 0xde83bc408dd3dd05L, // -186
    0xae0b158b4738705eL, 0x9624ab50b148d446L, // -185
    0xd98ddaee19068c76L, 0x3badd624dd9b0958L, // -184
    0x87f8a8d4cfa417c9L, 0xe54ca5d70a80e5d7L, // -183
    0xa9f6d30a038d1dbcL, 0x5e9fcf4ccd211f4dL, // -182
    0xd47487cc8470652bL, 0x7647c32000696720L, // -181
    0x84c8d4dfd2c63f3bL, 0x29ecd9f40041e074L, // -180
    0xa5fb0a17c777cf09L, 0xf468107100525891L, // -179
    0xcf79cc9db955c2ccL, 0x7182148d4066eeb5L, // -178
    0x81ac1fe293d599bfL, 0xc6f14cd848405531L, // -177
    0xa21727db38cb002fL, 0xb8ada00e5a506a7dL, // -176
    0xca9cf1d206fdc03bL, 0xa6d90811f0e4851dL, // -175
    0xfd442e4688bd304aL, 0x908f4a166d1da664L, // -174
    0x9e4a9cec15763e2eL, 0x9a598e4e043287ffL, // -173
    0xc5dd44271ad3cdbaL, 0x40eff1e1853f29feL, // -172
    0xf7549530e188c128L, 0xd12bee59e68ef47dL, // -171
    0x9a94dd3e8cf578b9L, 0x82bb74f8301958cfL, // -170
    0xc13a148e3032d6e7L, 0xe36a52363c1faf02L, // -169
    0xf18899b1bc3f8ca1L, 0xdc44e6c3cb279ac2L, // -168
    0x96f5600f15a7b7e5L, 0x29ab103a5ef8c0baL, // -167
    0xbcb2b812db11a5deL, 0x7415d448f6b6f0e8L, // -166
    0xebdf661791d60f56L, 0x111b495b3464ad22L, // -165
    0x936b9fcebb25c995L, 0xcab10dd900beec35L, // -164
    0xb84687c269ef3bfbL, 0x3d5d514f40eea743L, // -163
    0xe65829b3046b0afaL, 0x0cb4a5a3112a5113L, // -162
    0x8ff71a0fe2c2e6dcL, 0x47f0e785eaba72acL, // -161
    0xb3f4e093db73a093L, 0x59ed216765690f57L, // -160
    0xe0f218b8d25088b8L, 0x306869c13ec3532dL, // -159
    0x8c974f7383725573L, 0x1e414218c73a13fcL, // -158
    0xafbd2350644eeacfL, 0xe5d1929ef90898fbL, // -157
    0xdbac6c247d62a583L, 0xdf45f746b74abf3aL, // -156
    0x894bc396ce5da772L, 0x6b8bba8c328eb784L, // -155
    0xab9eb47c81f5114fL, 0x066ea92f3f326565L, // -154
    0xd686619ba27255a2L, 0xc80a537b0efefebeL, // -153
    0x8613fd0145877585L, 0xbd06742ce95f5f37L, // -152
    0xa798fc4196e952e7L, 0x2c48113823b73705L, // -151
    0xd17f3b51fca3a7a0L, 0xf75a15862ca504c6L, // -150
    0x82ef85133de648c4L, 0x9a984d73dbe722fcL, // -149
    0xa3ab66580d5fdaf5L, 0xc13e60d0d2e0ebbbL, // -148
    0xcc963fee10b7d1b3L, 0x318df905079926a9L, // -147
    0xffbbcfe994e5c61fL, 0xfdf17746497f7053L, // -146
    0x9fd561f1fd0f9bd3L, 0xfeb6ea8bedefa634L, // -145
    0xc7caba6e7c5382c8L, 0xfe64a52ee96b8fc1L, // -144
    0xf9bd690a1b68637bL, 0x3dfdce7aa3c673b1L, // -143
    0x9c1661a651213e2dL, 0x06bea10ca65c084fL, // -142
    0xc31bfa0fe5698db8L, 0x486e494fcff30a63L, // -141
    0xf3e2f893dec3f126L, 0x5a89dba3c3efccfbL, // -140
    0x986ddb5c6b3a76b7L, 0xf89629465a75e01dL, // -139
    0xbe89523386091465L, 0xf6bbb397f1135824L, // -138
    0xee2ba6c0678b597fL, 0x746aa07ded582e2dL, // -137
    0x94db483840b717efL, 0xa8c2a44eb4571cddL, // -136
    0xba121a4650e4ddebL, 0x92f34d62616ce414L, // -135
    0xe896a0d7e51e1566L, 0x77b020baf9c81d18L, // -134
    0x915e2486ef32cd60L, 0x0ace1474dc1d122fL, // -133
    0xb5b5ada8aaff80b8L, 0x0d819992132456bbL, // -132
    0xe3231912d5bf60e6L, 0x10e1fff697ed6c6aL, // -131
    0x8df5efabc5979c8fL, 0xca8d3ffa1ef463c2L, // -130
    0xb1736b96b6fd83b3L, 0xbd308ff8a6b17cb3L, // -129
    0xddd0467c64bce4a0L, 0xac7cb3f6d05ddbdfL, // -128
    0x8aa22c0dbef60ee4L, 0x6bcdf07a423aa96cL, // -127
    0xad4ab7112eb3929dL, 0x86c16c98d2c953c7L, // -126
    0xd89d64d57a607744L, 0xe871c7bf077ba8b8L, // -125
    0x87625f056c7c4a8bL, 0x11471cd764ad4973L, // -124
    0xa93af6c6c79b5d2dL, 0xd598e40d3dd89bd0L, // -123
    0xd389b47879823479L, 0x4aff1d108d4ec2c4L, // -122
    0x843610cb4bf160cbL, 0xcedf722a585139bbL, // -121
    0xa54394fe1eedb8feL, 0xc2974eb4ee658829L, // -120
    0xce947a3da6a9273eL, 0x733d226229feea33L, // -119
    0x811ccc668829b887L, 0x0806357d5a3f5260L, // -118
    0xa163ff802a3426a8L, 0xca07c2dcb0cf26f8L, // -117
    0xc9bcff6034c13052L, 0xfc89b393dd02f0b6L, // -116
    0xfc2c3f3841f17c67L, 0xbbac2078d443ace3L, // -115
    0x9d9ba7832936edc0L, 0xd54b944b84aa4c0eL, // -114
    0xc5029163f384a931L, 0x0a9e795e65d4df12L, // -113
    0xf64335bcf065d37dL, 0x4d4617b5ff4a16d6L, // -112
    0x99ea0196163fa42eL, 0x504bced1bf8e4e46L, // -111
    0xc06481fb9bcf8d39L, 0xe45ec2862f71e1d7L, // -110
    0xf07da27a82c37088L, 0x5d767327bb4e5a4dL, // -109
    0x964e858c91ba2655L, 0x3a6a07f8d510f870L, // -108
    0xbbe226efb628afeaL, 0x890489f70a55368cL, // -107
    0xeadab0aba3b2dbe5L, 0x2b45ac74ccea842fL, // -106
    0x92c8ae6b464fc96fL, 0x3b0b8bc90012929eL, // -105
    0xb77ada0617e3bbcbL, 0x09ce6ebb40173745L, // -104
    0xe55990879ddcaabdL, 0xcc420a6a101d0516L, // -103
    0x8f57fa54c2a9eab6L, 0x9fa946824a12232eL, // -102
    0xb32df8e9f3546564L, 0x47939822dc96abfaL, // -101
    0xdff9772470297ebdL, 0x59787e2b93bc56f8L, // -100
    0x8bfbea76c619ef36L, 0x57eb4edb3c55b65bL, // -99
    0xaefae51477a06b03L, 0xede622920b6b23f2L, // -98
    0xdab99e59958885c4L, 0xe95fab368e45eceeL, // -97
    0x88b402f7fd75539bL, 0x11dbcb0218ebb415L, // -96
    0xaae103b5fcd2a881L, 0xd652bdc29f26a11aL, // -95
    0xd59944a37c0752a2L, 0x4be76d3346f04960L, // -94
    0x857fcae62d8493a5L, 0x6f70a4400c562ddcL, // -93
    0xa6dfbd9fb8e5b88eL, 0xcb4ccd500f6bb953L, // -92
    0xd097ad07a71f26b2L, 0x7e2000a41346a7a8L, // -91
    0x825ecc24c873782fL, 0x8ed400668c0c28c9L, // -90
    0xa2f67f2dfa90563bL, 0x728900802f0f32fbL, // -89
    0xcbb41ef979346bcaL, 0x4f2b40a03ad2ffbaL, // -88
    0xfea126b7d78186bcL, 0xe2f610c84987bfa9L, // -87
    0x9f24b832e6b0f436L, 0x0dd9ca7d2df4d7caL, // -86
    0xc6ede63fa05d3143L, 0x91503d1c79720dbcL, // -85
    0xf8a95fcf88747d94L, 0x75a44c6397ce912bL, // -84
    0x9b69dbe1b548ce7cL, 0xc986afbe3ee11abbL, // -83
    0xc24452da229b021bL, 0xfbe85badce996169L, // -82
    0xf2d56790ab41c2a2L, 0xfae27299423fb9c4L, // -81
    0x97c560ba6b0919a5L, 0xdccd879fc967d41bL, // -80
    0xbdb6b8e905cb600fL, 0x5400e987bbc1c921L, // -79
    0xed246723473e3813L, 0x290123e9aab23b69L, // -78
    0x9436c0760c86e30bL, 0xf9a0b6720aaf6522L, // -77
    0xb94470938fa89bceL, 0xf808e40e8d5b3e6aL, // -76
    0xe7958cb87392c2c2L, 0xb60b1d1230b20e05L, // -75
    0x90bd77f3483bb9b9L, 0xb1c6f22b5e6f48c3L, // -74
    0xb4ecd5f01a4aa828L, 0x1e38aeb6360b1af4L, // -73
    0xe2280b6c20dd5232L, 0x25c6da63c38de1b1L, // -72
    0x8d590723948a535fL, 0x579c487e5a38ad0fL, // -71
    0xb0af48ec79ace837L, 0x2d835a9df0c6d852L, // -70
    0xdcdb1b2798182244L, 0xf8e431456cf88e66L, // -69
    0x8a08f0f8bf0f156bL, 0x1b8e9ecb641b5900L, // -68
    0xac8b2d36eed2dac5L, 0xe272467e3d222f40L, // -67
    0xd7adf884aa879177L, 0x5b0ed81dcc6abb10L, // -66
    0x86ccbb52ea94baeaL, 0x98e947129fc2b4eaL, // -65
    0xa87fea27a539e9a5L, 0x3f2398d747b36225L, // -64
    0xd29fe4b18e88640eL, 0x8eec7f0d19a03aaeL, // -63
    0x83a3eeeef9153e89L, 0x1953cf68300424adL, // -62
    0xa48ceaaab75a8e2bL, 0x5fa8c3423c052dd8L, // -61
    0xcdb02555653131b6L, 0x3792f412cb06794eL, // -60
    0x808e17555f3ebf11L, 0xe2bbd88bbee40bd1L, // -59
    0xa0b19d2ab70e6ed6L, 0x5b6aceaeae9d0ec5L, // -58
    0xc8de047564d20a8bL, 0xf245825a5a445276L, // -57
    0xfb158592be068d2eL, 0xeed6e2f0f0d56713L, // -56
    0x9ced737bb6c4183dL, 0x55464dd69685606cL, // -55
    0xc428d05aa4751e4cL, 0xaa97e14c3c26b887L, // -54
    0xf53304714d9265dfL, 0xd53dd99f4b3066a9L, // -53
    0x993fe2c6d07b7fabL, 0xe546a8038efe402aL, // -52
    0xbf8fdb78849a5f96L, 0xde98520472bdd034L, // -51
    0xef73d256a5c0f77cL, 0x963e66858f6d4441L, // -50
    0x95a8637627989aadL, 0xdde7001379a44aa9L, // -49
    0xbb127c53b17ec159L, 0x5560c018580d5d53L, // -48
    0xe9d71b689dde71afL, 0xaab8f01e6e10b4a7L, // -47
    0x9226712162ab070dL, 0xcab3961304ca70e9L, // -46
    0xb6b00d69bb55c8d1L, 0x3d607b97c5fd0d23L, // -45
    0xe45c10c42a2b3b05L, 0x8cb89a7db77c506bL, // -44
    0x8eb98a7a9a5b04e3L, 0x77f3608e92adb243L, // -43
    0xb267ed1940f1c61cL, 0x55f038b237591ed4L, // -42
    0xdf01e85f912e37a3L, 0x6b6c46dec52f6689L, // -41
    0x8b61313bbabce2c6L, 0x2323ac4b3b3da016L, // -40
    0xae397d8aa96c1b77L, 0xabec975e0a0d081bL, // -39
    0xd9c7dced53c72255L, 0x96e7bd358c904a22L, // -38
    0x881cea14545c7575L, 0x7e50d64177da2e55L, // -37
    0xaa242499697392d2L, 0xdde50bd1d5d0b9eaL, // -36
    0xd4ad2dbfc3d07787L, 0x955e4ec64b44e865L, // -35
    0x84ec3c97da624ab4L, 0xbd5af13bef0b113fL, // -34
    0xa6274bbdd0fadd61L, 0xecb1ad8aeacdd58fL, // -33
    0xcfb11ead453994baL, 0x67de18eda5814af3L, // -32
    0x81ceb32c4b43fcf4L, 0x80eacf948770ced8L, // -31
    0xa2425ff75e14fc31L, 0xa1258379a94d028eL, // -30
    0xcad2f7f5359a3b3eL, 0x096ee45813a04331L, // -29
    0xfd87b5f28300ca0dL, 0x8bca9d6e188853fdL, // -28
    0x9e74d1b791e07e48L, 0x775ea264cf55347eL, // -27
    0xc612062576589ddaL, 0x95364afe032a819eL, // -26
    0xf79687aed3eec551L, 0x3a83ddbd83f52205L, // -25
    0x9abe14cd44753b52L, 0xc4926a9672793543L, // -24
    0xc16d9a0095928a27L, 0x75b7053c0f178294L, // -23
    0xf1c90080baf72cb1L, 0x5324c68b12dd6339L, // -22
    0x971da05074da7beeL, 0xd3f6fc16ebca5e04L, // -21
    0xbce5086492111aeaL, 0x88f4bb1ca6bcf585L, // -20
    0xec1e4a7db69561a5L, 0x2b31e9e3d06c32e6L, // -19
    0x9392ee8e921d5d07L, 0x3aff322e62439fd0L, // -18
    0xb877aa3236a4b449L, 0x09befeb9fad487c3L, // -17
    0xe69594bec44de15bL, 0x4c2ebe687989a9b4L, // -16
    0x901d7cf73ab0acd9L, 0x0f9d37014bf60a11L, // -15
    0xb424dc35095cd80fL, 0x538484c19ef38c95L, // -14
    0xe12e13424bb40e13L, 0x2865a5f206b06fbaL, // -13
    0x8cbccc096f5088cbL, 0xf93f87b7442e45d4L, // -12
    0xafebff0bcb24aafeL, 0xf78f69a51539d749L, // -11
    0xdbe6fecebdedd5beL, 0xb573440e5a884d1cL, // -10
    0x89705f4136b4a597L, 0x31680a88f8953031L, // -9
    0xabcc77118461cefcL, 0xfdc20d2b36ba7c3eL, // -8
    0xd6bf94d5e57a42bcL, 0x3d32907604691b4dL, // -7
    0x8637bd05af6c69b5L, 0xa63f9a49c2c1b110L, // -6
    0xa7c5ac471b478423L, 0x0fcf80dc33721d54L, // -5
    0xd1b71758e219652bL, 0xd3c36113404ea4a9L, // -4
    0x83126e978d4fdf3bL, 0x645a1cac083126eaL, // -3
    0xa3d70a3d70a3d70aL, 0x3d70a3d70a3d70a4L, // -2
    0xccccccccccccccccL, 0xcccccccccccccccdL, // -1
    0x8000000000000000L, 0x0000000000000000L, // 0
    0xa000000000000000L, 0x0000000000000000L, // 1
    0xc800000000000000L, 0x0000000000000000L, // 2
    0xfa00000000000000L, 0x0000000000000000L, // 3
    0x9c40000000000000L, 0x0000000000000000L, // 4
    0xc350000000000000L, 0x0000000000000000L, // 5
    0xf424000000000000L, 0x0000000000000000L, // 6
    0x9896800000000000L, 0x0000000000000000L, // 7
    0xbebc200000000000L, 0x0000000000000000L, // 8
    0xee6b280000000000L, 0x0000000000000000L, // 9
    0x9502f90000000000L, 0x0000000000000000L, // 10
    0xba43b74000000000L, 0x0000000000000000L, // 11
    0xe8d4a51000000000L, 0x0000000000000000L, // 12
    0x9184e72a00000000L, 0x0000000000000000L, // 13
    0xb5e620f480000000L, 0x0000000000000000L, // 14
    0xe35fa931a0000000L, 0x0000000000000000L, // 15
    0x8e1bc9bf04000000L, 0x0000000000000000L, // 16
    0xb1a2bc2ec5000000L, 0x0000000000000000L, // 17
    0xde0b6b3a76400000L, 0x0000000000000000L, // 18
    0x8ac7230489e80000L, 0x0000000000000000L, // 19
    0xad78ebc5ac620000L, 0x0000000000000000L, // 20
    0xd8d726b7177a8000L, 0x0000000000000000L, // 21
    0x878678326eac9000L, 0x0000000000000000L, // 22
    0xa968163f0a57b400L, 0x0000000000000000L, // 23
    0xd3c21bcecceda100L, 0x0000000000000000L, // 24
    0x84595161401484a0L, 0x0000000000000000L, // 25
    0xa56fa5b99019a5c8L, 0x0000000000000000L, // 26
    0xcecb8f27f4200f3aL, 0x0000000000000000L, // 27
    0x813f3978f8940984L, 0x4000000000000000L, // 28
    0xa18f07d736b90be5L, 0x5000000000000000L, // 29
    0xc9f2c9cd04674edeL, 0xa400000000000000L, // 30
    0xfc6f7c4045812296L, 0x4d00000000000000L, // 31
    0x9dc5ada82b70b59dL, 0xf020000000000000L, // 32
    0xc5371912364ce305L, 0x6c28000000000000L, // 33
    0xf684df56c3e01bc6L, 0xc732000000000000L, // 34
    0x9a130b963a6c115cL, 0x3c7f400000000000L, // 35
    0xc097ce7bc90715b3L, 0x4b9f100000000000L, // 36
    0xf0bdc21abb48db20L, 0x1e86d40000000000L, // 37
    0x96769950b50d88f4L, 0x1314448000000000L, // 38
    0xbc143fa4e250eb31L, 0x17d955a000000000L, // 39
    0xeb194f8e1ae525fdL, 0x5dcfab0800000000L, // 40
    0x92efd1b8d0cf37beL, 0x5aa1cae500000000L, // 41
    0xb7abc627050305adL, 0xf14a3d9e40000000L, // 42
    0xe596b7b0c643c719L, 0x6d9ccd05d0000000L, // 43
    0x8f7e32ce7bea5c6fL, 0xe4820023a2000000L, // 44
    0xb35dbf821ae4f38bL, 0xdda2802c8a800000L, // 45
    0xe0352f62a19e306eL, 0xd50b2037ad200000L, // 46
    0x8c213d9da502de45L, 0x4526f422cc340000L, // 47
    0xaf298d050e4395d6L, 0x9670b12b7f410000L, // 48
    0xdaf3f04651d47b4cL, 0x3c0cdd765f114000L, // 49
    0x88d8762bf324cd0fL, 0xa5880a69fb6ac800L, // 50
    0xab0e93b6efee0053L, 0x8eea0d047a457a00L, // 51
    0xd5d238a4abe98068L, 0x72a4904598d6d880L, // 52
    0x85a36366eb71f041L, 0x47a6da2b7f864750L, // 53
    0xa70c3c40a64e6c51L, 0x999090b65f67d924L, // 54
    0xd0cf4b50cfe20765L, 0xfff4b4e3f741cf6dL, // 55
    0x82818f1281ed449fL, 0xbff8f10e7a8921a5L, // 56
    0xa321f2d7226895c7L, 0xaff72d52192b6a0eL, // 57
    0xcbea6f8ceb02bb39L, 0x9bf4f8a69f764491L, // 58
    0xfee50b7025c36a08L, 0x02f236d04753d5b5L, // 59
    0x9f4f2726179a2245L, 0x01d762422c946591L, // 60
    0xc722f0ef9d80aad6L, 0x424d3ad2b7b97ef6L, // 61
    0xf8ebad2b84e0d58bL, 0xd2e0898765a7deb3L, // 62
    0x9b934c3b330c8577L, 0x63cc55f49f88eb30L, // 63
    0xc2781f49ffcfa6d5L, 0x3cbf6b71c76b25fcL, // 64
    0xf316271c7fc3908aL, 0x8bef464e3945ef7bL, // 65
    0x97edd871cfda3a56L, 0x97758bf0e3cbb5adL, // 66
    0xbde94e8e43d0c8ecL, 0x3d52eeed1cbea318L, // 67
    0xed63a231d4c4fb27L, 0x4ca7aaa863ee4bdeL, // 68
    0x945e455f24fb1cf8L, 0x8fe8caa93e74ef6bL, // 69
    0xb975d6b6ee39e436L, 0xb3e2fd538e122b45L, // 70
    0xe7d34c64a9c85d44L, 0x60dbbca87196b617L, // 71
    0x90e40fbeea1d3a4aL, 0xbc8955e946fe31ceL, // 72
    0xb51d13aea4a488ddL, 0x6babab6398bdbe42L, // 73
    0xe264589a4dcdab14L, 0xc696963c7eed2dd2L, // 74
    0x8d7eb76070a08aecL, 0xfc1e1de5cf543ca3L, // 75
    0xb0de65388cc8ada8L, 0x3b25a55f43294bccL, // 76
    0xdd15fe86affad912L, 0x49ef0eb713f39ebfL, // 77
    0x8a2dbf142dfcc7abL, 0x6e3569326c784338L, // 78
    0xacb92ed9397bf996L, 0x49c2c37f07965405L, // 79
    0xd7e77a8f87daf7fbL, 0xdc33745ec97be907L, // 80
    0x86f0ac99b4e8dafdL, 0x69a028bb3ded71a4L, // 81
    0xa8acd7c0222311bcL, 0xc40832ea0d68ce0dL, // 82
    0xd2d80db02aabd62bL, 0xf50a3fa490c30191L, // 83
    0x83c7088e1aab65dbL, 0x792667c6da79e0fbL, // 84
    0xa4b8cab1a1563f52L, 0x577001b891185939L, // 85
    0xcde6fd5e09abcf26L, 0xed4c0226b55e6f87L, // 86
    0x80b05e5ac60b6178L, 0x544f8158315b05b5L, // 87
    0xa0dc75f1778e39d6L, 0x696361ae3db1c722L, // 88
    0xc913936dd571c84cL, 0x03bc3a19cd1e38eaL, // 89
    0xfb5878494ace3a5fL, 0x04ab48a04065c724L, // 90
    0x9d174b2dcec0e47bL, 0x62eb0d64283f9c77L, // 91
    0xc45d1df942711d9aL, 0x3ba5d0bd324f8395L, // 92
    0xf5746577930d6500L, 0xca8f44ec7ee3647aL, // 93
    0x9968bf6abbe85f20L, 0x7e998b13cf4e1eccL, // 94
    0xbfc2ef456ae276e8L, 0x9e3fedd8c321a67fL, // 95
    0xefb3ab16c59b14a2L, 0xc5cfe94ef3ea101fL, // 96
    0x95d04aee3b80ece5L, 0xbba1f1d158724a13L, // 97
    0xbb445da9ca61281fL, 0x2a8a6e45ae8edc98L, // 98
    0xea1575143cf97226L, 0xf52d09d71a3293beL, // 99
    0x924d692ca61be758L, 0x593c2626705f9c57L, // 100
    0xb6e0c377cfa2e12eL, 0x6f8b2fb00c77836dL, // 101
    0xe498f455c38b997aL, 0x0b6dfb9c0f956448L, // 102
    0x8edf98b59a373fecL, 0x4724bd4189bd5eadL, // 103
    0xb2977ee300c50fe7L, 0x58edec91ec2cb658L, // 104
    0xdf3d5e9bc0f653e1L, 0x2f2967b66737e3eeL, // 105
    0x8b865b215899f46cL, 0xbd79e0d20082ee75L, // 106
    0xae67f1e9aec07187L, 0xecd8590680a3aa12L, // 107
    0xda01ee641a708de9L, 0xe80e6f4820cc9496L, // 108
    0x884134fe908658b2L, 0x3109058d147fdcdeL, // 109
    0xaa51823e34a7eedeL, 0xbd4b46f0599fd416L, // 110
    0xd4e5e2cdc1d1ea96L, 0x6c9e18ac7007c91bL, // 111
    0x850fadc09923329eL, 0x03e2cf6bc604ddb1L, // 112
    0xa6539930bf6bff45L, 0x84db8346b786151dL, // 113
    0xcfe87f7cef46ff16L, 0xe612641865679a64L, // 114
    0x81f14fae158c5f6eL, 0x4fcb7e8f3f60c07fL, // 115
    0xa26da3999aef7749L, 0xe3be5e330f38f09eL, // 116
    0xcb090c8001ab551cL, 0x5cadf5bfd3072cc6L, // 117
    0xfdcb4fa002162a63L, 0x73d9732fc7c8f7f7L, // 118
    0x9e9f11c4014dda7eL, 0x2867e7fddcdd9afbL, // 119
    0xc646d63501a1511dL, 0xb281e1fd541501b9L, // 120
    0xf7d88bc24209a565L, 0x1f225a7ca91a4227L, // 121
    0x9ae757596946075fL, 0x3375788de9b06959L, // 122
    0xc1a12d2fc3978937L, 0x0052d6b1641c83afL, // 123
    0xf209787bb47d6b84L, 0xc0678c5dbd23a49bL, // 124
    0x9745eb4d50ce6332L, 0xf840b7ba963646e1L, // 125
    0xbd176620a501fbffL, 0xb650e5a93bc3d899L, // 126
    0xec5d3fa8ce427affL, 0xa3e51f138ab4cebfL, // 127
    0x93ba47c980e98cdfL, 0xc66f336c36b10138L, // 128
    0xb8a8d9bbe123f017L, 0xb80b0047445d4185L, // 129
    0xe6d3102ad96cec1dL, 0xa60dc059157491e6L, // 130
    0x9043ea1ac7e41392L, 0x87c89837ad68db30L, // 131
    0xb454e4a179dd1877L, 0x29babe4598c311fcL, // 132
    0xe16a1dc9d8545e94L, 0xf4296dd6fef3d67bL, // 133
    0x8ce2529e2734bb1dL, 0x1899e4a65f58660dL, // 134
    0xb01ae745b101e9e4L, 0x5ec05dcff72e7f90L, // 135
    0xdc21a1171d42645dL, 0x76707543f4fa1f74L, // 136
    0x899504ae72497ebaL, 0x6a06494a791c53a9L, // 137
    0xabfa45da0edbde69L, 0x0487db9d17636893L, // 138
    0xd6f8d7509292d603L, 0x45a9d2845d3c42b7L, // 139
    0x865b86925b9bc5c2L, 0x0b8a2392ba45a9b3L, // 140
    0xa7f26836f282b732L, 0x8e6cac7768d7141fL, // 141
    0xd1ef0244af2364ffL, 0x3207d795430cd927L, // 142
    0x8335616aed761f1fL, 0x7f44e6bd49e807b9L, // 143
    0xa402b9c5a8d3a6e7L, 0x5f16206c9c6209a7L, // 144
    0xcd036837130890a1L, 0x36dba887c37a8c10L, // 145
    0x802221226be55a64L, 0xc2494954da2c978aL, // 146
    0xa02aa96b06deb0fdL, 0xf2db9baa10b7bd6dL, // 147
    0xc83553c5c8965d3dL, 0x6f92829494e5acc8L, // 148
    0xfa42a8b73abbf48cL, 0xcb772339ba1f17faL, // 149
    0x9c69a97284b578d7L, 0xff2a760414536efcL, // 150
    0xc38413cf25e2d70dL, 0xfef5138519684abbL, // 151
    0xf46518c2ef5b8cd1L, 0x7eb258665fc25d6aL, // 152
    0x98bf2f79d5993802L, 0xef2f773ffbd97a62L, // 153
    0xbeeefb584aff8603L, 0xaafb550ffacfd8fbL, // 154
    0xeeaaba2e5dbf6784L, 0x95ba2a53f983cf39L, // 155
    0x952ab45cfa97a0b2L, 0xdd945a747bf26184L, // 156
    0xba756174393d88dfL, 0x94f971119aeef9e5L, // 157
    0xe912b9d1478ceb17L, 0x7a37cd5601aab85eL, // 158
    0x91abb422ccb812eeL, 0xac62e055c10ab33bL, // 159
    0xb616a12b7fe617aaL, 0x577b986b314d600aL, // 160
    0xe39c49765fdf9d94L, 0xed5a7e85fda0b80cL, // 161
    0x8e41ade9fbebc27dL, 0x14588f13be847308L, // 162
    0xb1d219647ae6b31cL, 0x596eb2d8ae258fc9L, // 163
    0xde469fbd99a05fe3L, 0x6fca5f8ed9aef3bcL, // 164
    0x8aec23d680043beeL, 0x25de7bb9480d5855L, // 165
    0xada72ccc20054ae9L, 0xaf561aa79a10ae6bL, // 166
    0xd910f7ff28069da4L, 0x1b2ba1518094da05L, // 167
    0x87aa9aff79042286L, 0x90fb44d2f05d0843L, // 168
    0xa99541bf57452b28L, 0x353a1607ac744a54L, // 169
    0xd3fa922f2d1675f2L, 0x42889b8997915ce9L, // 170
    0x847c9b5d7c2e09b7L, 0x69956135febada12L, // 171
    0xa59bc234db398c25L, 0x43fab9837e699096L, // 172
    0xcf02b2c21207ef2eL, 0x94f967e45e03f4bcL, // 173
    0x8161afb94b44f57dL, 0x1d1be0eebac278f6L, // 174
    0xa1ba1ba79e1632dcL, 0x6462d92a69731733L, // 175
    0xca28a291859bbf93L, 0x7d7b8f7503cfdcffL, // 176
    0xfcb2cb35e702af78L, 0x5cda735244c3d43fL, // 177
    0x9defbf01b061adabL, 0x3a0888136afa64a8L, // 178
    0xc56baec21c7a1916L, 0x088aaa1845b8fdd1L, // 179
    0xf6c69a72a3989f5bL, 0x8aad549e57273d46L, // 180
    0x9a3c2087a63f6399L, 0x36ac54e2f678864cL, // 181
    0xc0cb28a98fcf3c7fL, 0x84576a1bb416a7deL, // 182
    0xf0fdf2d3f3c30b9fL, 0x656d44a2a11c51d6L, // 183
    0x969eb7c47859e743L, 0x9f644ae5a4b1b326L, // 184
    0xbc4665b596706114L, 0x873d5d9f0dde1fefL, // 185
    0xeb57ff22fc0c7959L, 0xa90cb506d155a7ebL, // 186
    0x9316ff75dd87cbd8L, 0x09a7f12442d588f3L, // 187
    0xb7dcbf5354e9beceL, 0x0c11ed6d538aeb30L, // 188
    0xe5d3ef282a242e81L, 0x8f1668c8a86da5fbL, // 189
    0x8fa475791a569d10L, 0xf96e017d694487bdL, // 190
    0xb38d92d760ec4455L, 0x37c981dcc395a9adL, // 191
    0xe070f78d3927556aL, 0x85bbe253f47b1418L, // 192
    0x8c469ab843b89562L, 0x93956d7478ccec8fL, // 193
    0xaf58416654a6babbL, 0x387ac8d1970027b3L, // 194
    0xdb2e51bfe9d0696aL, 0x06997b05fcc0319fL, // 195
    0x88fcf317f22241e2L, 0x441fece3bdf81f04L, // 196
    0xab3c2fddeeaad25aL, 0xd527e81cad7626c4L, // 197
    0xd60b3bd56a5586f1L, 0x8a71e223d8d3b075L, // 198
    0x85c7056562757456L, 0xf6872d5667844e4aL, // 199
    0xa738c6bebb12d16cL, 0xb428f8ac016561dcL, // 200
    0xd106f86e69d785c7L, 0xe13336d701beba53L, // 201
    0x82a45b450226b39cL, 0xecc0024661173474L, // 202
    0xa34d721642b06084L, 0x27f002d7f95d0191L, // 203
    0xcc20ce9bd35c78a5L, 0x31ec038df7b441f5L, // 204
    0xff290242c83396ceL, 0x7e67047175a15272L, // 205
    0x9f79a169bd203e41L, 0x0f0062c6e984d387L, // 206
    0xc75809c42c684dd1L, 0x52c07b78a3e60869L, // 207
    0xf92e0c3537826145L, 0xa7709a56ccdf8a83L, // 208
    0x9bbcc7a142b17ccbL, 0x88a66076400bb692L, // 209
    0xc2abf989935ddbfeL, 0x6acff893d00ea436L, // 210
    0xf356f7ebf83552feL, 0x0583f6b8c4124d44L, // 211
    0x98165af37b2153deL, 0xc3727a337a8b704bL, // 212
    0xbe1bf1b059e9a8d6L, 0x744f18c0592e4c5dL, // 213
    0xeda2ee1c7064130cL, 0x1162def06f79df74L, // 214
    0x9485d4d1c63e8be7L, 0x8addcb5645ac2ba9L, // 215
    0xb9a74a0637ce2ee1L, 0x6d953e2bd7173693L, // 216
    0xe8111c87c5c1ba99L, 0xc8fa8db6ccdd0438L, // 217
    0x910ab1d4db9914a0L, 0x1d9c9892400a22a3L, // 218
    0xb54d5e4a127f59c8L, 0x2503beb6d00cab4cL, // 219
    0xe2a0b5dc971f303aL, 0x2e44ae64840fd61eL, // 220
    0x8da471a9de737e24L, 0x5ceaecfed289e5d3L, // 221
    0xb10d8e1456105dadL, 0x7425a83e872c5f48L, // 222
    0xdd50f1996b947518L, 0xd12f124e28f7771aL, // 223
    0x8a5296ffe33cc92fL, 0x82bd6b70d99aaa70L, // 224
    0xace73cbfdc0bfb7bL, 0x636cc64d1001550cL, // 225
    0xd8210befd30efa5aL, 0x3c47f7e05401aa4fL, // 226
    0x8714a775e3e95c78L, 0x65acfaec34810a72L, // 227
    0xa8d9d1535ce3b396L, 0x7f1839a741a14d0eL, // 228
    0xd31045a8341ca07cL, 0x1ede48111209a051L, // 229
    0x83ea2b892091e44dL, 0x934aed0aab460433L, // 230
    0xa4e4b66b68b65d60L, 0xf81da84d56178540L, // 231
    0xce1de40642e3f4b9L, 0x36251260ab9d668fL, // 232
    0x80d2ae83e9ce78f3L, 0xc1d72b7c6b42601aL, // 233
    0xa1075a24e4421730L, 0xb24cf65b8612f820L, // 234
    0xc94930ae1d529cfcL, 0xdee033f26797b628L, // 235
    0xfb9b7cd9a4a7443cL, 0x169840ef017da3b2L, // 236
    0x9d412e0806e88aa5L, 0x8e1f289560ee864fL, // 237
    0xc491798a08a2ad4eL, 0xf1a6f2bab92a27e3L, // 238
    0xf5b5d7ec8acb58a2L, 0xae10af696774b1dcL, // 239
    0x9991a6f3d6bf1765L, 0xacca6da1e0a8ef2aL, // 240
    0xbff610b0cc6edd3fL, 0x17fd090a58d32af4L, // 241
    0xeff394dcff8a948eL, 0xddfc4b4cef07f5b1L, // 242
    0x95f83d0a1fb69cd9L, 0x4abdaf101564f98fL, // 243
    0xbb764c4ca7a4440fL, 0x9d6d1ad41abe37f2L, // 244
    0xea53df5fd18d5513L, 0x84c86189216dc5eeL, // 245
    0x92746b9be2f8552cL, 0x32fd3cf5b4e49bb5L, // 246
    0xb7118682dbb66a77L, 0x3fbc8c33221dc2a2L, // 247
    0xe4d5e82392a40515L, 0x0fabaf3feaa5334bL, // 248
    0x8f05b1163ba6832dL, 0x29cb4d87f2a7400fL, // 249
    0xb2c71d5bca9023f8L, 0x743e20e9ef511013L, // 250
    0xdf78e4b2bd342cf6L, 0x914da9246b255417L, // 251
    0x8bab8eefb6409c1aL, 0x1ad089b6c2f7548fL, // 252
    0xae9672aba3d0c320L, 0xa184ac2473b529b2L, // 253
    0xda3c0f568cc4f3e8L, 0xc9e5d72d90a2741fL, // 254
    0x8865899617fb1871L, 0x7e2fa67c7a658893L, // 255
    0xaa7eebfb9df9de8dL, 0xddbb901b98feeab8L, // 256
    0xd51ea6fa85785631L, 0x552a74227f3ea566L, // 257
    0x8533285c936b35deL, 0xd53a88958f872760L, // 258
    0xa67ff273b8460356L, 0x8a892abaf368f138L, // 259
    0xd01fef10a657842cL, 0x2d2b7569b0432d86L, // 260
    0x8213f56a67f6b29bL, 0x9c3b29620e29fc74L, // 261
    0xa298f2c501f45f42L, 0x8349f3ba91b47b90L, // 262
    0xcb3f2f7642717713L, 0x241c70a936219a74L, // 263
    0xfe0efb53d30dd4d7L, 0xed238cd383aa0111L, // 264
    0x9ec95d1463e8a506L, 0xf4363804324a40abL, // 265
    0xc67bb4597ce2ce48L, 0xb143c6053edcd0d6L, // 266
    0xf81aa16fdc1b81daL, 0xdd94b7868e94050bL, // 267
    0x9b10a4e5e9913128L, 0xca7cf2b4191c8327L, // 268
    0xc1d4ce1f63f57d72L, 0xfd1c2f611f63a3f1L, // 269
    0xf24a01a73cf2dccfL, 0xbc633b39673c8cedL, // 270
    0x976e41088617ca01L, 0xd5be0503e085d814L, // 271
    0xbd49d14aa79dbc82L, 0x4b2d8644d8a74e19L, // 272
    0xec9c459d51852ba2L, 0xddf8e7d60ed1219fL, // 273
    0x93e1ab8252f33b45L, 0xcabb90e5c942b504L, // 274
    0xb8da1662e7b00a17L, 0x3d6a751f3b936244L, // 275
    0xe7109bfba19c0c9dL, 0x0cc512670a783ad5L, // 276
    0x906a617d450187e2L, 0x27fb2b80668b24c6L, // 277
    0xb484f9dc9641e9daL, 0xb1f9f660802dedf7L, // 278
    0xe1a63853bbd26451L, 0x5e7873f8a0396974L, // 279
    0x8d07e33455637eb2L, 0xdb0b487b6423e1e9L, // 280
    0xb049dc016abc5e5fL, 0x91ce1a9a3d2cda63L, // 281
    0xdc5c5301c56b75f7L, 0x7641a140cc7810fcL, // 282
    0x89b9b3e11b6329baL, 0xa9e904c87fcb0a9eL, // 283
    0xac2820d9623bf429L, 0x546345fa9fbdcd45L, // 284
    0xd732290fbacaf133L, 0xa97c177947ad4096L, // 285
    0x867f59a9d4bed6c0L, 0x49ed8eabcccc485eL, // 286
    0xa81f301449ee8c70L, 0x5c68f256bfff5a75L, // 287
    0xd226fc195c6a2f8cL, 0x73832eec6fff3112L, // 288
    0x83585d8fd9c25db7L, 0xc831fd53c5ff7eacL, // 289
    0xa42e74f3d032f525L, 0xba3e7ca8b77f5e56L, // 290
    0xcd3a1230c43fb26fL, 0x28ce1bd2e55f35ecL, // 291
    0x80444b5e7aa7cf85L, 0x7980d163cf5b81b4L, // 292
    0xa0555e361951c366L, 0xd7e105bcc3326220L, // 293
    0xc86ab5c39fa63440L, 0x8dd9472bf3fefaa8L, // 294
    0xfa856334878fc150L, 0xb14f98f6f0feb952L, // 295
    0x9c935e00d4b9d8d2L, 0x6ed1bf9a569f33d4L, // 296
    0xc3b8358109e84f07L, 0x0a862f80ec4700c9L, // 297
    0xf4a642e14c6262c8L, 0xcd27bb612758c0fbL, // 298
    0x98e7e9cccfbd7dbdL, 0x8038d51cb897789dL, // 299
    0xbf21e44003acdd2cL, 0xe0470a63e6bd56c4L, // 300
    0xeeea5d5004981478L, 0x1858ccfce06cac75L, // 301
    0x95527a5202df0ccbL, 0x0f37801e0c43ebc9L, // 302
    0xbaa718e68396cffdL, 0xd30560258f54e6bbL, // 303
    0xe950df20247c83fdL, 0x47c6b82ef32a206aL, // 304
    0x91d28b7416cdd27eL, 0x4cdc331d57fa5442L, // 305
    0xb6472e511c81471dL, 0xe0133fe4adf8e953L, // 306
    0xe3d8f9e563a198e5L, 0x58180fddd97723a7L, // 307
    0x8e679c2f5e44ff8fL, 0x570f09eaa7ea7649L, // 308
    0xb201833b35d63f73L, 0x2cd2cc6551e513dbL, // 309
    0xde81e40a034bcf4fL, 0xf8077f7ea65e58d2L, // 310
    0x8b112e86420f6191L, 0xfb04afaf27faf783L, // 311
    0xadd57a27d29339f6L, 0x79c5db9af1f9b564L, // 312
    0xd94ad8b1c7380874L, 0x18375281ae7822bdL, // 313
    0x87cec76f1c830548L, 0x8f2293910d0b15b6L, // 314
    0xa9c2794ae3a3c69aL, 0xb2eb3875504ddb23L, // 315
    0xd433179d9c8cb841L, 0x5fa60692a46151ecL, // 316
    0x849feec281d7f328L, 0xdbc7c41ba6bcd334L, // 317
    0xa5c7ea73224deff3L, 0x12b9b522906c0801L, // 318
    0xcf39e50feae16befL, 0xd768226b34870a01L, // 319
    0x81842f29f2cce375L, 0xe6a1158300d46641L, // 320
    0xa1e53af46f801c53L, 0x60495ae3c1097fd1L, // 321
    0xca5e89b18b602368L, 0x385bb19cb14bdfc5L, // 322
    0xfcf62c1dee382c42L, 0x46729e03dd9ed7b6L, // 323
    0x9e19db92b4e31ba9L, 0x6c07a2c26a8346d2L  // 324
  )
  @volatile private[this] var tenPow18Squares: Array[BigInteger] = Array(BigInteger.valueOf(1000000000000000000L))

  final private def getTenPow18Squares(n: Int): Array[BigInteger] = {
    var ss = tenPow18Squares
    var i  = ss.length
    if (n >= i) {
      var s = ss(i - 1)
      ss = java.util.Arrays.copyOf(ss, n + 1)
      while (i <= n) {
        s = s.multiply(s)
        ss(i) = s
        i += 1
      }
      tenPow18Squares = ss
    }
    ss
  }

  /**
   * Checks if a string does not require JSON escaping or encoding.
   *
   * @param s
   *   the string to check
   * @return
   *   `true` if the string has basic ASCII characters only (code point less
   *   than `0x80` that does not need JSON escaping)
   */
  final def isNonEscapedAscii(s: String): Boolean = {
    val len = s.length
    var idx = 0
    while (
      idx < len && {
        val ch = s.charAt(idx)
        ch < 0x80 && escapedChars(ch.toInt) == 0
      }
    ) idx += 1
    idx == len
  }
}
