package zio.blocks.schema.toon

import java.io.InputStream
import java.nio.ByteBuffer
import java.time._
import java.time.format.DateTimeParseException
import java.util.UUID
import java.nio.charset.StandardCharsets.UTF_8
import zio.blocks.schema.{DynamicValue, PrimitiveValue}
import zio.blocks.schema.binding.Registers
import zio.blocks.schema.binding.RegisterOffset
import zio.blocks.schema.binding.RegisterOffset.RegisterOffset

/**
 * A reader for iterative parsing of TOON documents.
 *
 * TOON (Token-Oriented Object Notation) is a compact, human-readable format
 * optimized for LLM prompts. This reader handles the indentation-based syntax,
 * string quoting rules, and various array formats required by the TOON
 * specification.
 *
 * @see
 *   [[https://github.com/toon-format/spec TOON Specification]]
 */
final class ToonReader private[toon] (
  private[this] var buf: Array[Byte] = new Array[Byte](32768),
  private[this] var head: Int = 0,
  private[this] var tail: Int = 0,
  private[this] var config: ReaderConfig = null,
  private[this] var depth: Int = 0,
  private[this] var line: Int = 1,
  private[this] var column: Int = 1,
  private[this] var in: InputStream = null,
  private[this] var tokenStart: Int = -1,
  private[this] var currentDelimiter: Delimiter = Delimiter.Comma,
  // Default capacity: 32 objects, 32 ints, 16 longs, 16 doubles for primitive fields
  private[this] val stack: Registers = Registers(RegisterOffset(objects = 32, ints = 32, longs = 16, doubles = 16)),
  private[this] var top: RegisterOffset = -1L,
  private[this] var maxTop: RegisterOffset = 0L
) {

  /**
   * Returns true if this reader is currently in use.
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
   * Returns the registers for this reader.
   */
  def registers: Registers = this.stack

  /**
   * Checks if the next token is the specified character.
   */
  def isNextToken(c: Char): Boolean = {
    val ch = nextToken()
    if (ch == c) true
    else {
      rollbackToken()
      false
    }
  }

  /**
   * Rolls back the last read token.
   */
  def rollbackToken(): Unit =
    if (tokenStart >= 0) head = tokenStart

  /**
   * Reads null or throws an error.
   */
  def readNullOrError[A](default: A, msg: String): A =
    if (readNull()) default
    else decodeError(msg)

  /**
   * Reads a null value.
   */
  def readNull(): Boolean =
    if (
      head + 4 <= tail &&
      buf(head) == 'n' &&
      buf(head + 1) == 'u' &&
      buf(head + 2) == 'l' &&
      buf(head + 3) == 'l'
    ) {
      head += 4
      column += 4
      true
    } else false

  /**
   * Reads a boolean value.
   */
  def readBoolean(): Boolean = {
    skipWhitespace()
    if (
      head + 4 <= tail &&
      buf(head) == 't' &&
      buf(head + 1) == 'r' &&
      buf(head + 2) == 'u' &&
      buf(head + 3) == 'e'
    ) {
      head += 4
      column += 4
      true
    } else if (
      head + 5 <= tail &&
      buf(head) == 'f' &&
      buf(head + 1) == 'a' &&
      buf(head + 2) == 'l' &&
      buf(head + 3) == 's' &&
      buf(head + 4) == 'e'
    ) {
      head += 5
      column += 5
      false
    } else decodeError("expected boolean")
  }

  /**
   * Reads a byte value.
   */
  def readByte(): Byte = {
    val n = readInt()
    if (n < Byte.MinValue || n > Byte.MaxValue) decodeError("byte overflow")
    n.toByte
  }

  /**
   * Reads a short value.
   */
  def readShort(): Short = {
    val n = readInt()
    if (n < Short.MinValue || n > Short.MaxValue) decodeError("short overflow")
    n.toShort
  }

  /**
   * Reads an int value.
   */
  def readInt(): Int = {
    skipWhitespace()
    var negative = false
    if (head < tail && buf(head) == '-') {
      negative = true
      head += 1
      column += 1
    }
    if (head >= tail || buf(head) < '0' || buf(head) > '9')
      decodeError("expected integer")

    var result = 0L
    while (head < tail && buf(head) >= '0' && buf(head) <= '9') {
      result = result * 10 + (buf(head) - '0')
      head += 1
      column += 1
    }
    if (negative) result = -result
    if (result < Int.MinValue || result > Int.MaxValue) decodeError("integer overflow")
    result.toInt
  }

  /**
   * Reads a long value.
   */
  def readLong(): Long = {
    skipWhitespace()
    var negative = false
    if (head < tail && buf(head) == '-') {
      negative = true
      head += 1
      column += 1
    }
    if (head >= tail || buf(head) < '0' || buf(head) > '9')
      decodeError("expected long")

    var result = BigInt(0)
    while (head < tail && buf(head) >= '0' && buf(head) <= '9') {
      result = result * 10 + (buf(head) - '0')
      head += 1
      column += 1
    }
    if (negative) result = -result
    if (result < Long.MinValue || result > Long.MaxValue) decodeError("long overflow")
    result.toLong
  }

  /**
   * Reads a float value.
   */
  def readFloat(): Float = {
    val s = readNumberString()
    try s.toFloat
    catch { case _: NumberFormatException => decodeError("invalid float") }
  }

  /**
   * Reads a double value.
   */
  def readDouble(): Double = {
    val s = readNumberString()
    try s.toDouble
    catch { case _: NumberFormatException => decodeError("invalid double") }
  }

  /**
   * Reads a char value.
   */
  def readChar(): Char = {
    val s = readString(null)
    if (s == null || s.length != 1) decodeError("expected single character")
    s.charAt(0)
  }

  /**
   * Reads a string value.
   */
  def readString(default: String): String = {
    skipWhitespace()
    if (head >= tail) {
      if (default != null) return default
      decodeError("unexpected end of input")
    }

    if (buf(head) == '"') {
      head += 1
      column += 1
      readQuotedString()
    } else if (readNull()) {
      default
    } else {
      readUnquotedValue()
    }
  }

  /**
   * Reads a BigInt value.
   */
  def readBigInt(default: BigInt): BigInt = {
    val s = readString(if (default == null) null else default.toString)
    if (s == null) default
    else
      try BigInt(s)
      catch { case _: NumberFormatException => decodeError("invalid BigInt") }
  }

  /**
   * Reads a BigDecimal value.
   */
  def readBigDecimal(default: BigDecimal): BigDecimal = {
    val s = readString(if (default == null) null else default.toString)
    if (s == null) default
    else
      try BigDecimal(s)
      catch { case _: NumberFormatException => decodeError("invalid BigDecimal") }
  }

  /**
   * Reads a Duration value.
   */
  def readDuration(default: Duration): Duration = {
    val s = readString(if (default == null) null else default.toString)
    if (s == null) default
    else
      try Duration.parse(s)
      catch { case _: DateTimeParseException => decodeError("invalid Duration") }
  }

  /**
   * Reads an Instant value.
   */
  def readInstant(default: Instant): Instant = {
    val s = readString(if (default == null) null else default.toString)
    if (s == null) default
    else
      try Instant.parse(s)
      catch { case _: DateTimeParseException => decodeError("invalid Instant") }
  }

  /**
   * Reads a LocalDate value.
   */
  def readLocalDate(default: LocalDate): LocalDate = {
    val s = readString(if (default == null) null else default.toString)
    if (s == null) default
    else
      try LocalDate.parse(s)
      catch { case _: DateTimeParseException => decodeError("invalid LocalDate") }
  }

  /**
   * Reads a LocalDateTime value.
   */
  def readLocalDateTime(default: LocalDateTime): LocalDateTime = {
    val s = readString(if (default == null) null else default.toString)
    if (s == null) default
    else
      try LocalDateTime.parse(s)
      catch { case _: DateTimeParseException => decodeError("invalid LocalDateTime") }
  }

  /**
   * Reads a LocalTime value.
   */
  def readLocalTime(default: LocalTime): LocalTime = {
    val s = readString(if (default == null) null else default.toString)
    if (s == null) default
    else
      try LocalTime.parse(s)
      catch { case _: DateTimeParseException => decodeError("invalid LocalTime") }
  }

  /**
   * Reads a MonthDay value.
   */
  def readMonthDay(default: MonthDay): MonthDay = {
    val s = readString(if (default == null) null else default.toString)
    if (s == null) default
    else
      try MonthDay.parse(s)
      catch { case _: DateTimeParseException => decodeError("invalid MonthDay") }
  }

  /**
   * Reads an OffsetDateTime value.
   */
  def readOffsetDateTime(default: OffsetDateTime): OffsetDateTime = {
    val s = readString(if (default == null) null else default.toString)
    if (s == null) default
    else
      try OffsetDateTime.parse(s)
      catch { case _: DateTimeParseException => decodeError("invalid OffsetDateTime") }
  }

  /**
   * Reads an OffsetTime value.
   */
  def readOffsetTime(default: OffsetTime): OffsetTime = {
    val s = readString(if (default == null) null else default.toString)
    if (s == null) default
    else
      try OffsetTime.parse(s)
      catch { case _: DateTimeParseException => decodeError("invalid OffsetTime") }
  }

  /**
   * Reads a Period value.
   */
  def readPeriod(default: Period): Period = {
    val s = readString(if (default == null) null else default.toString)
    if (s == null) default
    else
      try Period.parse(s)
      catch { case _: DateTimeParseException => decodeError("invalid Period") }
  }

  /**
   * Reads a Year value.
   */
  def readYear(default: Year): Year = {
    val _ = default // silence unused warning - parameter kept for API consistency
    val n = readInt()
    Year.of(n)
  }

  /**
   * Reads a YearMonth value.
   */
  def readYearMonth(default: YearMonth): YearMonth = {
    val s = readString(if (default == null) null else default.toString)
    if (s == null) default
    else
      try YearMonth.parse(s)
      catch { case _: DateTimeParseException => decodeError("invalid YearMonth") }
  }

  /**
   * Reads a ZoneId value.
   */
  def readZoneId(default: ZoneId): ZoneId = {
    val s = readString(if (default == null) null else default.getId)
    if (s == null) default
    else
      try ZoneId.of(s)
      catch { case _: DateTimeException => decodeError("invalid ZoneId") }
  }

  /**
   * Reads a ZoneOffset value.
   */
  def readZoneOffset(default: ZoneOffset): ZoneOffset = {
    val s = readString(if (default == null) null else default.getId)
    if (s == null) default
    else
      try ZoneOffset.of(s)
      catch { case _: DateTimeException => decodeError("invalid ZoneOffset") }
  }

  /**
   * Reads a ZonedDateTime value.
   */
  def readZonedDateTime(default: ZonedDateTime): ZonedDateTime = {
    val s = readString(if (default == null) null else default.toString)
    if (s == null) default
    else
      try ZonedDateTime.parse(s)
      catch { case _: DateTimeParseException => decodeError("invalid ZonedDateTime") }
  }

  /**
   * Reads a UUID value.
   */
  def readUUID(default: UUID): UUID = {
    val s = readString(if (default == null) null else default.toString)
    if (s == null) default
    else
      try UUID.fromString(s)
      catch { case _: IllegalArgumentException => decodeError("invalid UUID") }
  }

  // Key reading methods

  def readKeyAsBoolean(): Boolean = {
    val key = readKey()
    key.toBooleanOption.getOrElse(decodeError("expected boolean key"))
  }

  def readKeyAsByte(): Byte = {
    val key = readKey()
    key.toByteOption.getOrElse(decodeError("expected byte key"))
  }

  def readKeyAsShort(): Short = {
    val key = readKey()
    key.toShortOption.getOrElse(decodeError("expected short key"))
  }

  def readKeyAsInt(): Int = {
    val key = readKey()
    key.toIntOption.getOrElse(decodeError("expected int key"))
  }

  def readKeyAsLong(): Long = {
    val key = readKey()
    key.toLongOption.getOrElse(decodeError("expected long key"))
  }

  def readKeyAsFloat(): Float = {
    val key = readKey()
    key.toFloatOption.getOrElse(decodeError("expected float key"))
  }

  def readKeyAsDouble(): Double = {
    val key = readKey()
    key.toDoubleOption.getOrElse(decodeError("expected double key"))
  }

  def readKeyAsChar(): Char = {
    val key = readKey()
    if (key.length == 1) key.charAt(0)
    else decodeError("expected char key")
  }

  def readKeyAsString(): String = readKey()

  def readKeyAsBigInt(): BigInt = {
    val key = readKey()
    try BigInt(key)
    catch { case _: NumberFormatException => decodeError("expected BigInt key") }
  }

  def readKeyAsBigDecimal(): BigDecimal = {
    val key = readKey()
    try BigDecimal(key)
    catch { case _: NumberFormatException => decodeError("expected BigDecimal key") }
  }

  def readKeyAsDuration(): Duration = {
    val key = readKey()
    try Duration.parse(key)
    catch { case _: DateTimeParseException => decodeError("expected Duration key") }
  }

  def readKeyAsInstant(): Instant = {
    val key = readKey()
    try Instant.parse(key)
    catch { case _: DateTimeParseException => decodeError("expected Instant key") }
  }

  def readKeyAsLocalDate(): LocalDate = {
    val key = readKey()
    try LocalDate.parse(key)
    catch { case _: DateTimeParseException => decodeError("expected LocalDate key") }
  }

  def readKeyAsLocalDateTime(): LocalDateTime = {
    val key = readKey()
    try LocalDateTime.parse(key)
    catch { case _: DateTimeParseException => decodeError("expected LocalDateTime key") }
  }

  def readKeyAsLocalTime(): LocalTime = {
    val key = readKey()
    try LocalTime.parse(key)
    catch { case _: DateTimeParseException => decodeError("expected LocalTime key") }
  }

  def readKeyAsMonthDay(): MonthDay = {
    val key = readKey()
    try MonthDay.parse(key)
    catch { case _: DateTimeParseException => decodeError("expected MonthDay key") }
  }

  def readKeyAsOffsetDateTime(): OffsetDateTime = {
    val key = readKey()
    try OffsetDateTime.parse(key)
    catch { case _: DateTimeParseException => decodeError("expected OffsetDateTime key") }
  }

  def readKeyAsOffsetTime(): OffsetTime = {
    val key = readKey()
    try OffsetTime.parse(key)
    catch { case _: DateTimeParseException => decodeError("expected OffsetTime key") }
  }

  def readKeyAsPeriod(): Period = {
    val key = readKey()
    try Period.parse(key)
    catch { case _: DateTimeParseException => decodeError("expected Period key") }
  }

  def readKeyAsYear(): Year = {
    val key = readKey()
    try Year.of(key.toInt)
    catch { case _: Exception => decodeError("expected Year key") }
  }

  def readKeyAsYearMonth(): YearMonth = {
    val key = readKey()
    try YearMonth.parse(key)
    catch { case _: DateTimeParseException => decodeError("expected YearMonth key") }
  }

  def readKeyAsZoneId(): ZoneId = {
    val key = readKey()
    try ZoneId.of(key)
    catch { case _: DateTimeException => decodeError("expected ZoneId key") }
  }

  def readKeyAsZoneOffset(): ZoneOffset = {
    val key = readKey()
    try ZoneOffset.of(key)
    catch { case _: DateTimeException => decodeError("expected ZoneOffset key") }
  }

  def readKeyAsZonedDateTime(): ZonedDateTime = {
    val key = readKey()
    try ZonedDateTime.parse(key)
    catch { case _: DateTimeParseException => decodeError("expected ZonedDateTime key") }
  }

  def readKeyAsUUID(): UUID = {
    val key = readKey()
    try UUID.fromString(key)
    catch { case _: IllegalArgumentException => decodeError("expected UUID key") }
  }

  // Structural reading methods

  /**
   * Reads the start of an object (increases depth).
   */
  def readObjectStart(): Unit = {
    depth += 1
    if (depth > config.maxDepth) decodeError("maximum depth exceeded")
  }

  /**
   * Reads the end of an object.
   */
  def readObjectEnd(): Unit =
    depth -= 1

  /**
   * Checks if the current position is at the end of an object.
   */
  def isObjectEnd: Boolean = {
    skipWhitespace()
    // In TOON, object ends when indentation decreases or EOF
    if (head >= tail) return true
    // Only check indentation for nested objects (depth > 1)
    // Top-level records (depth = 1) don't require indentation
    if (depth > 1) {
      val currentIndent = countLeadingSpaces()
      currentIndent < (depth - 1) * config.indentSize
    } else {
      false
    }
  }

  /**
   * Reads a key from an object, or returns null if at object end.
   */
  def readKeyOrEnd(): String = {
    skipWhitespace()
    if (head >= tail) return null

    // Check indentation only for nested objects (depth > 1)
    // Top-level records (depth = 1) don't require indentation
    if (depth > 1) {
      val currentIndent  = countLeadingSpaces()
      val expectedIndent = (depth - 1) * config.indentSize
      if (currentIndent < expectedIndent) return null
    }

    readKey()
  }

  /**
   * Reads a key from the input.
   */
  def readKey(): String = {
    skipWhitespace()
    skipIndentation()

    val key = if (head < tail && buf(head) == '"') {
      head += 1
      column += 1
      readQuotedString()
    } else {
      readUnquotedKey()
    }

    // Expect colon and space
    skipWhitespace()
    if (head >= tail || buf(head) != ':')
      decodeError("expected ':' after key")
    head += 1
    column += 1

    // Skip space after colon
    if (head < tail && buf(head) == ' ') {
      head += 1
      column += 1
    }

    key
  }

  /**
   * Reads the start of an array.
   */
  def readArrayStart(): Unit = {
    skipWhitespace()
    if (head >= tail || buf(head) != '[')
      decodeError("expected '['")
    head += 1
    column += 1

    // Read and discard array length (reserved for future use)
    val _ = readInt()

    // Check for delimiter marker
    if (head < tail && buf(head) == '\t') {
      currentDelimiter = Delimiter.Tab
      head += 1
      column += 1
    } else if (head < tail && buf(head) == '|') {
      currentDelimiter = Delimiter.Pipe
      head += 1
      column += 1
    } else {
      currentDelimiter = Delimiter.Comma
    }

    if (head >= tail || buf(head) != ']')
      decodeError("expected ']'")
    head += 1
    column += 1

    if (head >= tail || buf(head) != ':')
      decodeError("expected ':'")
    head += 1
    column += 1

    // Skip space after colon
    if (head < tail && buf(head) == ' ') {
      head += 1
      column += 1
    }
  }

  /**
   * Reads the end of an array.
   */
  def readArrayEnd(): Unit = {
    // Arrays in TOON end implicitly
  }

  /**
   * Checks if at array end.
   */
  def isArrayEnd: Boolean = {
    skipWhitespace()
    // Skip element delimiter if present
    if (head < tail && buf(head) == currentDelimiter.char) {
      head += 1
      column += 1
      skipWhitespace()
    }
    head >= tail || buf(head) == '\n' || isObjectEnd
  }

  /**
   * Peeks at a discriminator field value.
   */
  def peekDiscriminatorField(fieldName: String): String = {
    // Save state
    val savedHead   = head
    val savedLine   = line
    val savedColumn = column

    try {
      // Look for the discriminator field
      while (!isObjectEnd) {
        val key = readKeyOrEnd()
        if (key == null) return null
        if (key == fieldName) {
          return readString(null)
        }
        skipValue()
      }
      null
    } finally {
      // Restore state
      head = savedHead
      line = savedLine
      column = savedColumn
    }
  }

  /**
   * Skips the current value.
   */
  def skipValue(): Unit = {
    skipWhitespace()
    if (head >= tail) return

    val c = buf(head)
    if (c == '"') {
      head += 1
      readQuotedString()
    } else if (c == '[') {
      readArrayStart()
      while (!isArrayEnd) skipValue()
      readArrayEnd()
    } else {
      // Skip unquoted value or nested object
      val startDepth = depth
      readObjectStart()
      while (depth > startDepth && !isObjectEnd) {
        val key = readKeyOrEnd()
        if (key != null) skipValue()
      }
      readObjectEnd()
    }
  }

  /**
   * Reads a DynamicValue.
   */
  def readDynamicValue(): DynamicValue = {
    skipWhitespace()
    if (head >= tail) decodeError("unexpected end of input")

    val c = buf(head)
    if (c == '"') {
      DynamicValue.Primitive(PrimitiveValue.String(readString(null)))
    } else if (c == '[') {
      readArrayStart()
      val elements = scala.collection.mutable.ArrayBuffer[DynamicValue]()
      while (!isArrayEnd) {
        elements += readDynamicValue()
      }
      readArrayEnd()
      DynamicValue.Sequence(elements.toVector)
    } else if (c == 't' || c == 'f') {
      DynamicValue.Primitive(PrimitiveValue.Boolean(readBoolean()))
    } else if (c == 'n') {
      readNull()
      DynamicValue.Primitive(PrimitiveValue.Unit)
    } else if (c == '-' || (c >= '0' && c <= '9')) {
      val s = readNumberString()
      if (s.contains('.')) DynamicValue.Primitive(PrimitiveValue.Double(s.toDouble))
      else DynamicValue.Primitive(PrimitiveValue.Long(s.toLong))
    } else {
      // Object
      readObjectStart()
      val fields = Vector.newBuilder[(String, DynamicValue)]
      var key    = readKeyOrEnd()
      while (key != null) {
        val value = readDynamicValue()
        fields += ((key, value))
        key = readKeyOrEnd()
      }
      readObjectEnd()
      DynamicValue.Record(fields.result())
    }
  }

  /**
   * Throws a decode error with line/column info.
   */
  def decodeError(msg: String): Nothing =
    throw new ToonBinaryCodecError(Nil, s"$msg at line $line, column $column")

  // High-level read methods

  private[toon] def read[A](codec: ToonBinaryCodec[A], bbuf: ByteBuffer, config: ReaderConfig): A = {
    this.config = config
    this.head = bbuf.position()
    this.tail = bbuf.limit()
    if (bbuf.hasArray) {
      this.buf = bbuf.array()
    } else {
      val bytes = new Array[Byte](tail - head)
      bbuf.get(bytes)
      this.buf = bytes
      this.head = 0
      this.tail = bytes.length
    }
    this.line = 1
    this.column = 1
    this.depth = 0
    this.top = 0
    try {
      codec.decodeValue(this, codec.nullValue)
    } finally {
      this.top = -1
    }
  }

  private[toon] def read[A](
    codec: ToonBinaryCodec[A],
    buf: Array[Byte],
    offset: Int,
    length: Int,
    config: ReaderConfig
  ): A = {
    this.config = config
    this.buf = buf
    this.head = offset
    this.tail = offset + length
    this.line = 1
    this.column = 1
    this.depth = 0
    this.top = 0
    try {
      codec.decodeValue(this, codec.nullValue)
    } finally {
      this.top = -1
    }
  }

  private[toon] def read[A](codec: ToonBinaryCodec[A], in: InputStream, config: ReaderConfig): A = {
    this.config = config
    this.in = in
    this.head = 0
    this.tail = 0
    this.line = 1
    this.column = 1
    this.depth = 0
    this.top = 0
    try {
      loadFromInputStream()
      codec.decodeValue(this, codec.nullValue)
    } finally {
      this.in = null
      this.top = -1
    }
  }

  // Private helper methods

  private def nextToken(): Int = {
    skipWhitespace()
    tokenStart = head
    if (head >= tail) -1
    else {
      val c = buf(head) & 0xff
      head += 1
      column += 1
      c
    }
  }

  private def skipWhitespace(): Unit =
    while (head < tail) {
      val c = buf(head)
      if (c == ' ') {
        head += 1
        column += 1
      } else if (c == '\n') {
        head += 1
        line += 1
        column = 1
      } else if (c == '\r') {
        head += 1
        if (head < tail && buf(head) == '\n') head += 1
        line += 1
        column = 1
      } else if (c == '\t') {
        head += 1
        column += 1
      } else {
        return
      }
    }

  private def skipIndentation(): Unit = {
    // Top-level records (depth = 1) have no indentation
    // Nested records use (depth - 1) * indentSize
    val expectedSpaces = if (depth > 1) (depth - 1) * config.indentSize else 0
    var spaces         = 0
    while (head < tail && spaces < expectedSpaces && buf(head) == ' ') {
      head += 1
      column += 1
      spaces += 1
    }
  }

  private def countLeadingSpaces(): Int = {
    var count = 0
    var pos   = head
    while (pos < tail && buf(pos) == ' ') {
      count += 1
      pos += 1
    }
    count
  }

  private def readQuotedString(): String = {
    val sb = new StringBuilder
    while (head < tail) {
      val c = buf(head)
      if (c == '"') {
        head += 1
        column += 1
        return sb.toString
      } else if (c == '\\') {
        head += 1
        column += 1
        if (head >= tail) decodeError("unexpected end of string")
        val escaped = buf(head)
        head += 1
        column += 1
        escaped match {
          case '"'  => sb.append('"')
          case '\\' => sb.append('\\')
          case 'n'  => sb.append('\n')
          case 'r'  => sb.append('\r')
          case 't'  => sb.append('\t')
          case 'u'  =>
            if (head + 4 > tail) decodeError("incomplete unicode escape")
            val hex = new String(buf, head, 4, UTF_8)
            head += 4
            column += 4
            sb.append(Integer.parseInt(hex, 16).toChar)
          case _ => decodeError(s"invalid escape character: ${escaped.toChar}")
        }
      } else {
        sb.append(c.toChar)
        head += 1
        column += 1
      }
    }
    decodeError("unterminated string")
  }

  private def readUnquotedKey(): String = {
    val start = head
    while (head < tail) {
      val c = buf(head)
      if (c == ':' || c == ' ' || c == '\n' || c == '\r') {
        val key = new String(buf, start, head - start, UTF_8)
        return key
      }
      head += 1
      column += 1
    }
    new String(buf, start, head - start, UTF_8)
  }

  private def readUnquotedValue(): String = {
    val start = head
    while (head < tail) {
      val c = buf(head)
      if (c == '\n' || c == '\r' || c == currentDelimiter.char) {
        val value = new String(buf, start, head - start, UTF_8).trim
        return value
      }
      head += 1
      column += 1
    }
    new String(buf, start, head - start, UTF_8).trim
  }

  private def readNumberString(): String = {
    skipWhitespace()
    val start = head
    while (head < tail) {
      val c = buf(head)
      if ((c >= '0' && c <= '9') || c == '.' || c == '-' || c == '+' || c == 'e' || c == 'E') {
        head += 1
        column += 1
      } else {
        return new String(buf, start, head - start, UTF_8)
      }
    }
    new String(buf, start, head - start, UTF_8)
  }

  private def loadFromInputStream(): Unit =
    if (in != null) {
      val available = in.available()
      val toRead    = Math.max(available, config.preferredBufSize)
      if (buf.length < toRead) buf = new Array[Byte](toRead)
      val read = in.read(buf)
      if (read > 0) {
        head = 0
        tail = read
      }
    }
}

object ToonReader {
  private[toon] def apply(): ToonReader = new ToonReader()

  private[toon] def apply(config: ReaderConfig): ToonReader =
    new ToonReader(config = config)
}
