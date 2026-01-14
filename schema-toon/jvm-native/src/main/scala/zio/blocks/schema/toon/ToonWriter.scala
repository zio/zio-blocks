package zio.blocks.schema.toon

import java.io.OutputStream
import java.nio.{BufferOverflowException, ByteBuffer}
import java.time._
import java.util.UUID
import java.nio.charset.StandardCharsets.UTF_8
import zio.blocks.schema.{DynamicValue, PrimitiveValue}
import zio.blocks.schema.binding.Registers
import zio.blocks.schema.binding.RegisterOffset
import zio.blocks.schema.binding.RegisterOffset.RegisterOffset

/**
 * A writer for iterative serialization of TOON keys and values.
 *
 * TOON (Token-Oriented Object Notation) is a compact, human-readable format
 * optimized for LLM prompts. This writer handles the indentation-based syntax,
 * string quoting rules, and canonical number formatting required by the TOON
 * specification.
 *
 * @see
 *   [[https://github.com/toon-format/spec TOON Specification]]
 */
final class ToonWriter private[toon] (
  private[this] var buf: Array[Byte] = new Array[Byte](32768),
  private[this] var count: Int = 0,
  private[this] var limit: Int = 32768,
  private[this] var config: WriterConfig = null,
  private[this] var depth: Int = 0,
  private[this] var disableBufGrowing: Boolean = false,
  @scala.annotation.unused private[this] var bbuf: ByteBuffer = null,
  private[this] var out: OutputStream = null,
  private[this] var atLineStart: Boolean = true,
  // Default capacity: 32 objects, 32 ints, 16 longs, 16 doubles for primitive fields
  private[this] val stack: Registers = Registers(RegisterOffset(objects = 32, ints = 32, longs = 16, doubles = 16)),
  private[this] var top: RegisterOffset = -1L,
  private[this] var maxTop: RegisterOffset = 0L
) {

  /**
   * Returns true if this writer is currently in use.
   */
  private[toon] def isInUse: Boolean = top >= 0

  /**
   * Pushes register space onto the stack.
   */
  def push(offset: RegisterOffset): RegisterOffset = {
    val t = this.top
    this.top = t + offset
    maxTop = Math.max(maxTop, this.top)
    t
  }

  /**
   * Pops register space from the stack.
   */
  def pop(offset: RegisterOffset): Unit = top -= offset

  /**
   * Returns the registers for this writer.
   */
  def registers: Registers = this.stack

  /**
   * Writes a boolean value as a key.
   */
  def writeKey(x: Boolean): Unit = {
    writeIndentIfNeeded()
    writeBoolean(x)
    writeKeyValueSeparator()
  }

  /**
   * Writes a byte value as a key.
   */
  def writeKey(x: Byte): Unit = {
    writeIndentIfNeeded()
    writeByte(x)
    writeKeyValueSeparator()
  }

  /**
   * Writes a char value as a key.
   */
  def writeKey(x: Char): Unit = {
    writeIndentIfNeeded()
    writeQuotedChar(x)
    writeKeyValueSeparator()
  }

  /**
   * Writes a short value as a key.
   */
  def writeKey(x: Short): Unit = {
    writeIndentIfNeeded()
    writeShort(x)
    writeKeyValueSeparator()
  }

  /**
   * Writes an int value as a key.
   */
  def writeKey(x: Int): Unit = {
    writeIndentIfNeeded()
    writeInt(x)
    writeKeyValueSeparator()
  }

  /**
   * Writes a long value as a key.
   */
  def writeKey(x: Long): Unit = {
    writeIndentIfNeeded()
    writeLong(x)
    writeKeyValueSeparator()
  }

  /**
   * Writes a float value as a key.
   */
  def writeKey(x: Float): Unit = {
    writeIndentIfNeeded()
    writeFloat(x)
    writeKeyValueSeparator()
  }

  /**
   * Writes a double value as a key.
   */
  def writeKey(x: Double): Unit = {
    writeIndentIfNeeded()
    writeDouble(x)
    writeKeyValueSeparator()
  }

  /**
   * Writes a string value as a key.
   */
  def writeKey(x: String): Unit = {
    writeIndentIfNeeded()
    if (needsKeyQuoting(x)) {
      writeQuotedString(x)
    } else {
      writeRawString(x)
    }
    writeKeyValueSeparator()
  }

  /**
   * Writes a BigInt value as a key.
   */
  def writeKey(x: BigInt): Unit = {
    writeIndentIfNeeded()
    writeRawString(x.toString)
    writeKeyValueSeparator()
  }

  /**
   * Writes a BigDecimal value as a key.
   */
  def writeKey(x: BigDecimal): Unit = {
    writeIndentIfNeeded()
    writeCanonicalDecimal(x)
    writeKeyValueSeparator()
  }

  /**
   * Writes a Duration value as a key.
   */
  def writeKey(x: Duration): Unit = {
    writeIndentIfNeeded()
    writeRawString(x.toString)
    writeKeyValueSeparator()
  }

  /**
   * Writes an Instant value as a key.
   */
  def writeKey(x: Instant): Unit = {
    writeIndentIfNeeded()
    writeRawString(x.toString)
    writeKeyValueSeparator()
  }

  /**
   * Writes a LocalDate value as a key.
   */
  def writeKey(x: LocalDate): Unit = {
    writeIndentIfNeeded()
    writeRawString(x.toString)
    writeKeyValueSeparator()
  }

  /**
   * Writes a LocalDateTime value as a key.
   */
  def writeKey(x: LocalDateTime): Unit = {
    writeIndentIfNeeded()
    writeRawString(x.toString)
    writeKeyValueSeparator()
  }

  /**
   * Writes a LocalTime value as a key.
   */
  def writeKey(x: LocalTime): Unit = {
    writeIndentIfNeeded()
    writeRawString(x.toString)
    writeKeyValueSeparator()
  }

  /**
   * Writes a MonthDay value as a key.
   */
  def writeKey(x: MonthDay): Unit = {
    writeIndentIfNeeded()
    writeRawString(x.toString)
    writeKeyValueSeparator()
  }

  /**
   * Writes an OffsetDateTime value as a key.
   */
  def writeKey(x: OffsetDateTime): Unit = {
    writeIndentIfNeeded()
    writeRawString(x.toString)
    writeKeyValueSeparator()
  }

  /**
   * Writes an OffsetTime value as a key.
   */
  def writeKey(x: OffsetTime): Unit = {
    writeIndentIfNeeded()
    writeRawString(x.toString)
    writeKeyValueSeparator()
  }

  /**
   * Writes a Period value as a key.
   */
  def writeKey(x: Period): Unit = {
    writeIndentIfNeeded()
    writeRawString(x.toString)
    writeKeyValueSeparator()
  }

  /**
   * Writes a Year value as a key.
   */
  def writeKey(x: Year): Unit = {
    writeIndentIfNeeded()
    writeInt(x.getValue)
    writeKeyValueSeparator()
  }

  /**
   * Writes a YearMonth value as a key.
   */
  def writeKey(x: YearMonth): Unit = {
    writeIndentIfNeeded()
    writeRawString(x.toString)
    writeKeyValueSeparator()
  }

  /**
   * Writes a ZoneId value as a key.
   */
  def writeKey(x: ZoneId): Unit = {
    writeIndentIfNeeded()
    writeRawString(x.getId)
    writeKeyValueSeparator()
  }

  /**
   * Writes a ZoneOffset value as a key.
   */
  def writeKey(x: ZoneOffset): Unit = {
    writeIndentIfNeeded()
    writeRawString(x.getId)
    writeKeyValueSeparator()
  }

  /**
   * Writes a ZonedDateTime value as a key.
   */
  def writeKey(x: ZonedDateTime): Unit = {
    writeIndentIfNeeded()
    writeRawString(x.toString)
    writeKeyValueSeparator()
  }

  /**
   * Writes a UUID value as a key.
   */
  def writeKey(x: UUID): Unit = {
    writeIndentIfNeeded()
    writeRawString(x.toString)
    writeKeyValueSeparator()
  }

  /**
   * Writes a null value.
   */
  def writeNull(): Unit = {
    ensureCapacity(4)
    buf(count) = 'n'
    buf(count + 1) = 'u'
    buf(count + 2) = 'l'
    buf(count + 3) = 'l'
    count += 4
    atLineStart = false
  }

  /**
   * Writes a boolean value.
   */
  def writeVal(x: Boolean): Unit = writeBoolean(x)

  /**
   * Writes a byte value.
   */
  def writeVal(x: Byte): Unit = writeByte(x)

  /**
   * Writes a char value.
   */
  def writeVal(x: Char): Unit = writeQuotedChar(x)

  /**
   * Writes a short value.
   */
  def writeVal(x: Short): Unit = writeShort(x)

  /**
   * Writes an int value.
   */
  def writeVal(x: Int): Unit = writeInt(x)

  /**
   * Writes a long value.
   */
  def writeVal(x: Long): Unit = writeLong(x)

  /**
   * Writes a float value.
   */
  def writeVal(x: Float): Unit = writeFloat(x)

  /**
   * Writes a double value.
   */
  def writeVal(x: Double): Unit = writeDouble(x)

  /**
   * Writes a string value with proper quoting if needed.
   */
  def writeVal(x: String): Unit =
    if (x eq null) writeNull()
    else if (needsValueQuoting(x)) writeQuotedString(x)
    else writeRawString(x)

  /**
   * Writes a BigInt value.
   */
  def writeVal(x: BigInt): Unit =
    if (x eq null) writeNull()
    else writeRawString(x.toString)

  /**
   * Writes a BigDecimal value in canonical form (no exponent).
   */
  def writeVal(x: BigDecimal): Unit =
    if (x eq null) writeNull()
    else writeCanonicalDecimal(x)

  /**
   * Writes a Duration value.
   */
  def writeVal(x: Duration): Unit =
    if (x eq null) writeNull()
    else writeRawString(x.toString)

  /**
   * Writes an Instant value.
   */
  def writeVal(x: Instant): Unit =
    if (x eq null) writeNull()
    else writeRawString(x.toString)

  /**
   * Writes a LocalDate value.
   */
  def writeVal(x: LocalDate): Unit =
    if (x eq null) writeNull()
    else writeRawString(x.toString)

  /**
   * Writes a LocalDateTime value.
   */
  def writeVal(x: LocalDateTime): Unit =
    if (x eq null) writeNull()
    else writeRawString(x.toString)

  /**
   * Writes a LocalTime value.
   */
  def writeVal(x: LocalTime): Unit =
    if (x eq null) writeNull()
    else writeRawString(x.toString)

  /**
   * Writes a MonthDay value.
   */
  def writeVal(x: MonthDay): Unit =
    if (x eq null) writeNull()
    else writeRawString(x.toString)

  /**
   * Writes an OffsetDateTime value.
   */
  def writeVal(x: OffsetDateTime): Unit =
    if (x eq null) writeNull()
    else writeRawString(x.toString)

  /**
   * Writes an OffsetTime value.
   */
  def writeVal(x: OffsetTime): Unit =
    if (x eq null) writeNull()
    else writeRawString(x.toString)

  /**
   * Writes a Period value.
   */
  def writeVal(x: Period): Unit =
    if (x eq null) writeNull()
    else writeRawString(x.toString)

  /**
   * Writes a Year value.
   */
  def writeVal(x: Year): Unit =
    if (x eq null) writeNull()
    else writeInt(x.getValue)

  /**
   * Writes a YearMonth value.
   */
  def writeVal(x: YearMonth): Unit =
    if (x eq null) writeNull()
    else writeRawString(x.toString)

  /**
   * Writes a ZoneId value.
   */
  def writeVal(x: ZoneId): Unit =
    if (x eq null) writeNull()
    else writeRawString(x.getId)

  /**
   * Writes a ZoneOffset value.
   */
  def writeVal(x: ZoneOffset): Unit =
    if (x eq null) writeNull()
    else writeRawString(x.getId)

  /**
   * Writes a ZonedDateTime value.
   */
  def writeVal(x: ZonedDateTime): Unit =
    if (x eq null) writeNull()
    else writeRawString(x.toString)

  /**
   * Writes a UUID value.
   */
  def writeVal(x: UUID): Unit =
    if (x eq null) writeNull()
    else writeRawString(x.toString)

  /**
   * Writes a non-escaped ASCII value (for enum names, etc).
   */
  def writeNonEscapedAsciiVal(x: String): Unit =
    if (needsValueQuoting(x)) writeQuotedString(x)
    else writeRawString(x)

  /**
   * Writes a non-escaped ASCII key.
   */
  def writeNonEscapedAsciiKey(x: String): Unit = {
    writeIndentIfNeeded()
    if (needsKeyQuoting(x)) writeQuotedString(x)
    else writeRawString(x)
    writeKeyValueSeparator()
  }

  /**
   * Starts writing an object.
   */
  def writeObjectStart(): Unit = {
    // TOON objects are written with newline + indent, no braces
    depth += 1
    atLineStart = true
  }

  /**
   * Ends writing an object.
   */
  def writeObjectEnd(): Unit =
    depth -= 1

  /**
   * Starts writing an array with the given size.
   */
  def writeArrayStart(size: Int): Unit = {
    val delim = config.delimiter
    ensureCapacity(20)
    buf(count) = '['
    count += 1
    writeInt(size)
    if (delim.headerMarker.nonEmpty) {
      val marker = delim.headerMarker
      var i      = 0
      while (i < marker.length) {
        buf(count) = marker.charAt(i).toByte
        count += 1
        i += 1
      }
    }
    buf(count) = ']'
    buf(count + 1) = ':'
    buf(count + 2) = ' '
    count += 3
    atLineStart = false
  }

  /**
   * Ends writing an array.
   */
  def writeArrayEnd(): Unit = {
    // No explicit end marker in TOON arrays
  }

  /**
   * Writes a separator between array elements.
   */
  def writeElementSeparator(): Unit = {
    ensureCapacity(1)
    buf(count) = config.delimiter.char.toByte
    count += 1
    atLineStart = false
  }

  /**
   * Writes a separator between object fields.
   */
  def writeFieldSeparator(): Unit = {
    writeNewLine()
    atLineStart = true
  }

  /**
   * Throws an encoding error.
   */
  def encodeError(msg: String): Nothing =
    throw new ToonBinaryCodecError(Nil, msg)

  /**
   * Writes a DynamicValue.
   */
  def writeDynamicValue(x: DynamicValue): Unit = x match {
    case DynamicValue.Primitive(value) =>
      value match {
        case PrimitiveValue.Unit              => writeNull()
        case v: PrimitiveValue.Boolean        => writeVal(v.value)
        case v: PrimitiveValue.Byte           => writeVal(v.value)
        case v: PrimitiveValue.Short          => writeVal(v.value)
        case v: PrimitiveValue.Int            => writeVal(v.value)
        case v: PrimitiveValue.Long           => writeVal(v.value)
        case v: PrimitiveValue.Float          => writeVal(v.value)
        case v: PrimitiveValue.Double         => writeVal(v.value)
        case v: PrimitiveValue.Char           => writeVal(v.value)
        case v: PrimitiveValue.String         => writeVal(v.value)
        case v: PrimitiveValue.BigInt         => writeVal(v.value)
        case v: PrimitiveValue.BigDecimal     => writeVal(v.value)
        case v: PrimitiveValue.Duration       => writeVal(v.value)
        case v: PrimitiveValue.Instant        => writeVal(v.value)
        case v: PrimitiveValue.LocalDate      => writeVal(v.value)
        case v: PrimitiveValue.LocalDateTime  => writeVal(v.value)
        case v: PrimitiveValue.LocalTime      => writeVal(v.value)
        case v: PrimitiveValue.MonthDay       => writeVal(v.value)
        case v: PrimitiveValue.OffsetDateTime => writeVal(v.value)
        case v: PrimitiveValue.OffsetTime     => writeVal(v.value)
        case v: PrimitiveValue.Period         => writeVal(v.value)
        case v: PrimitiveValue.Year           => writeVal(v.value)
        case v: PrimitiveValue.YearMonth      => writeVal(v.value)
        case v: PrimitiveValue.ZoneId         => writeVal(v.value)
        case v: PrimitiveValue.ZoneOffset     => writeVal(v.value)
        case v: PrimitiveValue.ZonedDateTime  => writeVal(v.value)
        case v: PrimitiveValue.Currency       => writeNonEscapedAsciiVal(v.value.getCurrencyCode)
        case v: PrimitiveValue.UUID           => writeVal(v.value)
        case v: PrimitiveValue.DayOfWeek      => writeNonEscapedAsciiVal(v.value.toString)
        case v: PrimitiveValue.Month          => writeNonEscapedAsciiVal(v.value.toString)
      }
    case DynamicValue.Record(fields) =>
      writeObjectStart()
      var first = true
      fields.foreach { case (name, value) =>
        if (!first) writeFieldSeparator()
        writeKey(name)
        writeDynamicValue(value)
        first = false
      }
      writeObjectEnd()
    case DynamicValue.Variant(caseName, value) =>
      writeObjectStart()
      writeKey(caseName)
      writeDynamicValue(value)
      writeObjectEnd()
    case DynamicValue.Sequence(elements) =>
      writeArrayStart(elements.size)
      var first = true
      elements.foreach { elem =>
        if (!first) writeElementSeparator()
        writeDynamicValue(elem)
        first = false
      }
      writeArrayEnd()
    case DynamicValue.Map(entries) =>
      writeObjectStart()
      var first = true
      entries.foreach { case (key, value) =>
        if (!first) writeFieldSeparator()
        writeDynamicValueAsKey(key)
        writeDynamicValue(value)
        first = false
      }
      writeObjectEnd()
  }

  private def writeDynamicValueAsKey(x: DynamicValue): Unit = x match {
    case DynamicValue.Primitive(value) =>
      value match {
        case v: PrimitiveValue.String => writeKey(v.value)
        case v: PrimitiveValue.Int    => writeKey(v.value)
        case v: PrimitiveValue.Long   => writeKey(v.value)
        case _                        => encodeError("unsupported key type for TOON")
      }
    case _ => encodeError("unsupported key type for TOON")
  }

  // High-level write methods

  private[toon] def write[A](codec: ToonBinaryCodec[A], x: A, out: OutputStream, config: WriterConfig): Unit = {
    this.config = config
    this.out = out
    this.count = 0
    this.depth = 0
    this.top = 0
    this.atLineStart = true
    try {
      codec.encodeValue(x, this)
      flushToOutputStream()
    } finally {
      this.out = null
      this.top = -1
    }
  }

  private[toon] def write[A](codec: ToonBinaryCodec[A], x: A, config: WriterConfig): Array[Byte] = {
    this.config = config
    this.count = 0
    this.depth = 0
    this.top = 0
    this.atLineStart = true
    try {
      codec.encodeValue(x, this)
      java.util.Arrays.copyOf(buf, count)
    } finally {
      this.top = -1
    }
  }

  private[toon] def write[A](codec: ToonBinaryCodec[A], x: A, bbuf: ByteBuffer, config: WriterConfig): Unit = {
    this.config = config
    this.bbuf = bbuf
    this.count = 0
    this.depth = 0
    this.top = 0
    this.disableBufGrowing = true
    this.atLineStart = true
    try {
      codec.encodeValue(x, this)
      bbuf.put(buf, 0, count)
    } finally {
      this.bbuf = null
      this.disableBufGrowing = false
      this.top = -1
    }
  }

  private[toon] def writeToString[A](codec: ToonBinaryCodec[A], x: A, config: WriterConfig): String = {
    this.config = config
    this.count = 0
    this.depth = 0
    this.top = 0
    this.atLineStart = true
    try {
      codec.encodeValue(x, this)
      new String(buf, 0, count, UTF_8)
    } finally {
      this.top = -1
    }
  }

  // Private helper methods

  private def writeIndentIfNeeded(): Unit = {
    // Only indent for nested objects (depth > 1), not top-level
    if (atLineStart && depth > 1) {
      val spaces = (depth - 1) * config.indentSize
      ensureCapacity(spaces)
      var i = 0
      while (i < spaces) {
        buf(count + i) = ' '
        i += 1
      }
      count += spaces
    }
    atLineStart = false
  }

  private def writeKeyValueSeparator(): Unit = {
    ensureCapacity(2)
    buf(count) = ':'
    buf(count + 1) = ' '
    count += 2
  }

  private def writeNewLine(): Unit = {
    ensureCapacity(1)
    buf(count) = '\n'
    count += 1
    atLineStart = true
  }

  private def writeBoolean(x: Boolean): Unit = {
    if (x) {
      ensureCapacity(4)
      buf(count) = 't'
      buf(count + 1) = 'r'
      buf(count + 2) = 'u'
      buf(count + 3) = 'e'
      count += 4
    } else {
      ensureCapacity(5)
      buf(count) = 'f'
      buf(count + 1) = 'a'
      buf(count + 2) = 'l'
      buf(count + 3) = 's'
      buf(count + 4) = 'e'
      count += 5
    }
    atLineStart = false
  }

  private def writeByte(x: Byte): Unit = writeInt(x.toInt)

  private def writeShort(x: Short): Unit = writeInt(x.toInt)

  private def writeInt(x: Int): Unit = {
    ensureCapacity(11)
    count = writeIntToBuffer(x, buf, count)
    atLineStart = false
  }

  private def writeLong(x: Long): Unit = {
    ensureCapacity(20)
    count = writeLongToBuffer(x, buf, count)
    atLineStart = false
  }

  private def writeFloat(x: Float): Unit =
    if (x.isNaN || x.isInfinite) writeNull()
    else {
      val s = java.lang.Float.toString(x)
      writeCanonicalNumberString(s)
    }

  private def writeDouble(x: Double): Unit =
    if (x.isNaN || x.isInfinite) writeNull()
    else {
      val s = java.lang.Double.toString(x)
      writeCanonicalNumberString(s)
    }

  // Write a number string in canonical form
  // Avoids BigDecimal.toPlainString() which has bugs on Scala Native
  private def writeCanonicalNumberString(s: String): Unit = {
    // Check for exponent notation (E or e)
    val eIdx = {
      var i     = 0
      var found = -1
      while (i < s.length && found < 0) {
        val c = s.charAt(i)
        if (c == 'E' || c == 'e') found = i
        i += 1
      }
      found
    }

    if (eIdx < 0) {
      // No exponent - just strip trailing zeros from fractional part
      writeCanonicalSimpleNumber(s)
    } else {
      // Has exponent - need to expand to plain form
      writeExpandedExponentNumber(s, eIdx)
    }
  }

  // Write a simple number (no exponent) in canonical form
  private def writeCanonicalSimpleNumber(s: String): Unit = {
    // Handle -0 case
    if (s == "-0.0" || s == "-0") {
      writeRawString("0")
      return
    }

    // Find the decimal point
    val dotIdx = s.indexOf('.')
    if (dotIdx < 0) {
      // No decimal point, write as-is
      writeRawString(s)
    } else {
      // Find the last non-zero digit after decimal point
      var lastNonZero = s.length - 1
      while (lastNonZero > dotIdx && s.charAt(lastNonZero) == '0') {
        lastNonZero -= 1
      }

      if (lastNonZero == dotIdx) {
        // All zeros after decimal, write without decimal part
        writeRawString(s.substring(0, dotIdx))
      } else {
        // Write up to and including last non-zero
        writeRawString(s.substring(0, lastNonZero + 1))
      }
    }
  }

  // Expand a number with exponent to plain form
  private def writeExpandedExponentNumber(s: String, eIdx: Int): Unit = {
    val mantissa = s.substring(0, eIdx)
    val exp      = java.lang.Integer.parseInt(s.substring(eIdx + 1))

    // Parse mantissa parts
    val negative = mantissa.charAt(0) == '-'
    val start    = if (negative) 1 else 0
    val dotIdx   = mantissa.indexOf('.')
    val intPart  = if (dotIdx < 0) mantissa.substring(start) else mantissa.substring(start, dotIdx)
    val fracPart = if (dotIdx < 0) "" else mantissa.substring(dotIdx + 1)

    // Combine all digits
    val allDigits  = intPart + fracPart
    val currentExp = intPart.length - 1 // Current position of decimal after first digit
    val targetExp  = currentExp + exp   // Where decimal should be

    val result = new StringBuilder
    if (negative) result.append('-')

    if (targetExp < 0) {
      // Need leading zeros: 0.00...digits
      result.append("0.")
      var i = 0
      while (i < -targetExp - 1) {
        result.append('0')
        i += 1
      }
      result.append(allDigits)
    } else if (targetExp >= allDigits.length - 1) {
      // No decimal needed or trailing zeros
      result.append(allDigits)
      var i = allDigits.length - 1
      while (i < targetExp) {
        result.append('0')
        i += 1
      }
    } else {
      // Decimal in the middle
      result.append(allDigits.substring(0, targetExp + 1))
      result.append('.')
      result.append(allDigits.substring(targetExp + 1))
    }

    // Strip trailing zeros from result
    writeCanonicalSimpleNumber(result.toString)
  }

  private def writeCanonicalDecimal(x: BigDecimal): Unit = {
    // Use toString which is safer than toPlainString on Scala Native
    val str = x.underlying.stripTrailingZeros().toString
    writeCanonicalNumberString(str)
  }

  private def writeRawString(s: String): Unit = {
    val bytes = s.getBytes(UTF_8)
    ensureCapacity(bytes.length)
    System.arraycopy(bytes, 0, buf, count, bytes.length)
    count += bytes.length
    atLineStart = false
  }

  private def writeQuotedString(s: String): Unit = {
    ensureCapacity(s.length * 6 + 2) // Worst case: all chars need escaping
    buf(count) = '"'
    count += 1
    var i = 0
    while (i < s.length) {
      val c = s.charAt(i)
      c match {
        case '"'         => buf(count) = '\\'; buf(count + 1) = '"'; count += 2
        case '\\'        => buf(count) = '\\'; buf(count + 1) = '\\'; count += 2
        case '\n'        => buf(count) = '\\'; buf(count + 1) = 'n'; count += 2
        case '\r'        => buf(count) = '\\'; buf(count + 1) = 'r'; count += 2
        case '\t'        => buf(count) = '\\'; buf(count + 1) = 't'; count += 2
        case _ if c < 32 =>
          // Other control characters: use \uXXXX
          buf(count) = '\\'
          buf(count + 1) = 'u'
          buf(count + 2) = hexDigit((c >> 12) & 0xf)
          buf(count + 3) = hexDigit((c >> 8) & 0xf)
          buf(count + 4) = hexDigit((c >> 4) & 0xf)
          buf(count + 5) = hexDigit(c & 0xf)
          count += 6
        case _ =>
          val bytes = c.toString.getBytes(UTF_8)
          System.arraycopy(bytes, 0, buf, count, bytes.length)
          count += bytes.length
      }
      i += 1
    }
    buf(count) = '"'
    count += 1
    atLineStart = false
  }

  private def writeQuotedChar(c: Char): Unit =
    writeQuotedString(c.toString)

  private def hexDigit(n: Int): Byte =
    if (n < 10) ('0' + n).toByte else ('a' + n - 10).toByte

  /**
   * Checks if a string needs quoting when used as a key in TOON. Keys matching
   * `^[A-Za-z_][A-Za-z0-9_.]*$` may be unquoted.
   */
  private def needsKeyQuoting(s: String): Boolean = {
    if (s.isEmpty) return true
    val first = s.charAt(0)
    if (!((first >= 'A' && first <= 'Z') || (first >= 'a' && first <= 'z') || first == '_'))
      return true
    var i = 1
    while (i < s.length) {
      val c = s.charAt(i)
      if (!((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_' || c == '.'))
        return true
      i += 1
    }
    false
  }

  /**
   * Checks if a string needs quoting when used as a value in TOON.
   *
   * Strings MUST be quoted if:
   *   - Empty
   *   - Leading or trailing whitespace
   *   - Equals true, false, or null (case-sensitive)
   *   - Numeric-like (matches decimal/exponent patterns or leading zeros)
   *   - Contains colon, quote, backslash, brackets, braces
   *   - Contains control characters
   *   - Contains the active delimiter
   *   - Equals "-" or starts with hyphen
   */
  private def needsValueQuoting(s: String): Boolean = {
    if (s.isEmpty) return true
    if (s == "true" || s == "false" || s == "null") return true
    if (s == "-" || s.charAt(0) == '-') return true
    if (looksLikeNumber(s)) return true

    var i = 0
    while (i < s.length) {
      val c = s.charAt(i)
      // Spaces anywhere need quoting
      if (c == ' ') return true
      if (c == ':' || c == '"' || c == '\\' || c == '[' || c == ']' || c == '{' || c == '}')
        return true
      if (c < 32) return true // control characters
      if (c == config.delimiter.char) return true
      i += 1
    }
    false
  }

  private def looksLikeNumber(s: String): Boolean = {
    if (s.isEmpty) return false
    val first = s.charAt(0)
    // Starts with digit or minus followed by digit
    if (first >= '0' && first <= '9') return true
    if (first == '-' && s.length > 1 && s.charAt(1) >= '0' && s.charAt(1) <= '9') return true
    // Leading zero followed by more digits (like "05")
    if (first == '0' && s.length > 1 && s.charAt(1) >= '0' && s.charAt(1) <= '9') return true
    false
  }

  private def ensureCapacity(needed: Int): Unit =
    if (count + needed > limit) {
      if (disableBufGrowing) {
        throw new BufferOverflowException()
      }
      growBuffer(needed)
    }

  private def growBuffer(needed: Int): Unit = {
    val newSize = Math.max(buf.length * 2, count + needed)
    buf = java.util.Arrays.copyOf(buf, newSize)
    limit = newSize
  }

  private def flushToOutputStream(): Unit =
    if (out != null && count > 0) {
      out.write(buf, 0, count)
      count = 0
    }

  private def writeIntToBuffer(x: Int, buf: Array[Byte], pos: Int): Int = {
    var value = x
    var p     = pos
    if (value < 0) {
      buf(p) = '-'
      p += 1
      if (value == Int.MinValue) {
        // Special case for MinValue
        val s     = "-2147483648"
        val bytes = s.getBytes(UTF_8)
        System.arraycopy(bytes, 0, buf, pos, bytes.length)
        return pos + bytes.length
      }
      value = -value
    }
    // Count digits
    var digits = 1
    var temp   = value
    while (temp >= 10) {
      digits += 1
      temp /= 10
    }
    // Write digits
    var i = p + digits - 1
    temp = value
    while (temp >= 10) {
      buf(i) = ('0' + (temp % 10)).toByte
      temp /= 10
      i -= 1
    }
    buf(i) = ('0' + temp).toByte
    p + digits
  }

  private def writeLongToBuffer(x: Long, buf: Array[Byte], pos: Int): Int = {
    var value = x
    var p     = pos
    if (value < 0) {
      buf(p) = '-'
      p += 1
      if (value == Long.MinValue) {
        val s     = "-9223372036854775808"
        val bytes = s.getBytes(UTF_8)
        System.arraycopy(bytes, 0, buf, pos, bytes.length)
        return pos + bytes.length
      }
      value = -value
    }
    var digits = 1
    var temp   = value
    while (temp >= 10) {
      digits += 1
      temp /= 10
    }
    var i = p + digits - 1
    temp = value
    while (temp >= 10) {
      buf(i) = ('0' + (temp % 10)).toByte
      temp /= 10
      i -= 1
    }
    buf(i) = ('0' + temp).toByte
    p + digits
  }
}

object ToonWriter {
  private[toon] def apply(): ToonWriter = new ToonWriter()

  private[toon] def apply(config: WriterConfig): ToonWriter =
    new ToonWriter(config = config)
}
