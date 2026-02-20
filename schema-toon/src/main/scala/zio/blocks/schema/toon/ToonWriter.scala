package zio.blocks.schema.toon

import zio.blocks.chunk.Chunk
import java.io.OutputStream
import java.nio.charset.StandardCharsets.UTF_8
import scala.annotation.{switch, tailrec}

/**
 * A writer for iterative serialization of TOON keys and values.
 *
 * @param buf
 *   an internal buffer for writing TOON data
 * @param count
 *   the current position in the internal buffer
 * @param indentSize
 *   the number of spaces per indentation level
 * @param delimiter
 *   the delimiter for inline arrays
 * @param keyFolding
 *   strategy for folding nested keys
 * @param flattenDepth
 *   maximum depth for key folding
 * @param discriminatorField
 *   optional field name to use as discriminator for DynamicValue variants
 */
final class ToonWriter private (
  private[this] var buf: Array[Byte],
  private[this] var count: Int,
  private[toon] val indentSize: Int,
  private[toon] val delimiter: Delimiter,
  private[toon] val keyFolding: KeyFolding,
  private[toon] val flattenDepth: Int,
  private[toon] val discriminatorField: Option[String]
) {
  private[this] var depth: Int                 = 0
  private[this] var lineStart: Boolean         = true
  private[this] var activeDelimiter: Delimiter = delimiter
  private[this] var inlineContext: Boolean     = false
  private[this] var listItemContext: Boolean   = false

  def reset(): Unit = {
    count = 0
    depth = 0
    lineStart = true
    activeDelimiter = delimiter
    inlineContext = false
    listItemContext = false
  }

  def getDepth: Int = depth

  def incrementDepth(): Unit = depth += 1

  def decrementDepth(): Unit = if (depth > 0) depth -= 1

  def setActiveDelimiter(d: Delimiter): Unit = activeDelimiter = d

  def getActiveDelimiter: Delimiter = activeDelimiter

  def enterInlineContext(): Unit = inlineContext = true

  def exitInlineContext(): Unit = inlineContext = false

  def isInlineContext: Boolean = inlineContext

  def enterListItemContext(): Unit = listItemContext = true

  def exitListItemContext(): Unit = listItemContext = false

  def isInListItemContext: Boolean = listItemContext

  def toByteArray: Array[Byte] = java.util.Arrays.copyOf(buf, count)

  def toByteArrayTrimmed: Array[Byte] = {
    var end = count
    while (end > 0 && (buf(end - 1) == '\n' || buf(end - 1) == ' ')) end -= 1
    java.util.Arrays.copyOf(buf, end)
  }

  def encodeError(msg: String): Nothing = throw new ToonBinaryCodecError(Nil, msg)

  def writeToTrimmed(out: OutputStream): Unit = {
    var end = count
    while (end > 0 && (buf(end - 1) == '\n' || buf(end - 1) == ' ')) end -= 1
    out.write(buf, 0, end)
  }

  def writeNull(): Unit = writeRaw(ToonWriter.nullBytes)

  def writeBoolean(x: Boolean): Unit = writeRaw {
    if (x) ToonWriter.trueBytes
    else ToonWriter.falseBytes
  }

  def writeInt(x: Int): Unit = writeRaw(java.lang.Integer.toString(x).getBytes(UTF_8))

  def writeLong(x: Long): Unit = writeRaw(java.lang.Long.toString(x).getBytes(UTF_8))

  def writeFloat(x: Float): Unit =
    if (x.isNaN || x.isInfinite) writeNull()
    else if (x == 0.0f) writeByte('0')
    else writeDecimalString(java.lang.Float.toString(x))

  def writeDouble(x: Double): Unit =
    if (x.isNaN || x.isInfinite) writeNull()
    else if (x == 0.0 || x == -0.0) writeByte('0')
    else writeDecimalString(java.lang.Double.toString(x))

  def writeBigDecimal(x: BigDecimal): Unit = writeDecimalString(x.underlying.toString)

  def writeBigInt(x: BigInt): Unit = writeRaw(x.toString.getBytes(UTF_8))

  def writeChar(c: Char): Unit = writeString(c.toString)

  def writeString(s: String): Unit = writeString(
    s,
    if (inlineContext) activeDelimiter
    else null
  )

  def writeString(s: String, delimiterOverride: Delimiter): Unit =
    if (needsQuoting(s, delimiterOverride)) writeQuotedString(s)
    else writeUnquotedString(s)

  def writeKey(key: String): Unit = {
    ensureIndent()
    if (isValidUnquotedKey(key)) writeUnquotedString(key)
    else writeQuotedString(key)
    writeByte(':')
    writeByte(' ')
  }

  def writeKeyOnly(key: String): Unit = {
    ensureIndent()
    if (isValidUnquotedKey(key)) writeUnquotedString(key)
    else writeQuotedString(key)
    writeByte(':')
    newLine()
  }

  def writeArrayHeader(length: Int): Unit = writeArrayHeader(null, length, null, delimiter)

  def writeArrayHeader(key: String, length: Int): Unit = writeArrayHeader(key, length, null, delimiter)

  def writeArrayHeader(key: String, length: Int, fields: Array[String]): Unit =
    writeArrayHeader(key, length, fields, delimiter)

  def writeArrayHeader(key: String, length: Int, fields: Array[String], delim: Delimiter): Unit = {
    ensureIndent()
    if (key != null) {
      if (isValidUnquotedKey(key)) writeUnquotedString(key)
      else writeQuotedString(key)
    }
    writeByte('[')
    writeInt(length)
    if (delim != Delimiter.Comma) writeByte(delim.char)
    writeByte(']')
    if (fields != null && fields.length > 0) {
      writeByte('{')
      var i = 0
      while (i < fields.length) {
        if (i > 0) writeByte(delim.char)
        val field = fields(i)
        if (isValidUnquotedKey(field)) writeUnquotedString(field)
        else writeQuotedString(field)
        i += 1
      }
      writeByte('}')
    }
    writeByte(':')
  }

  def writeArrayHeaderInline(key: String, length: Int): Unit =
    writeArrayHeaderInline(key, length, delimiter)

  def writeArrayHeaderInline(key: String, length: Int, delim: Delimiter): Unit = {
    writeArrayHeader(key, length, null, delim)
    writeByte(' ')
  }

  def writeTabularHeader(length: Int, fields: Chunk[String]): Unit = {
    ensureIndent()
    writeByte('[')
    writeInt(length)
    if (delimiter != Delimiter.Comma) writeByte(delimiter.char)
    writeByte(']')
    writeByte('{')
    var i = 0
    while (i < fields.length) {
      if (i > 0) writeByte(delimiter.char)
      val field = fields(i)
      if (isValidUnquotedKey(field)) writeUnquotedString(field)
      else writeQuotedString(field)
      i += 1
    }
    writeByte('}')
    writeByte(':')
  }

  def writeListItemMarker(): Unit = {
    ensureIndent()
    writeByte('-')
    writeByte(' ')
  }

  def writeDelimiter(d: Delimiter): Unit = writeByte(d.char)

  def writeDelimiter(): Unit = writeByte(delimiter.char)

  def newLine(): Unit = {
    writeByte('\n')
    lineStart = true
  }

  def writeColonSpace(): Unit = {
    writeByte(':')
    writeByte(' ')
  }

  private[toon] def ensureIndent(): Unit =
    if (lineStart) {
      val newLen = depth * indentSize + count
      if (newLen > buf.length) buf = java.util.Arrays.copyOf(buf, Math.max(buf.length << 1, newLen))
      java.util.Arrays.fill(buf, count, newLen, ' ': Byte)
      count = newLen
      lineStart = false
    }

  private[this] def writeDecimalString(str: String): Unit = {
    val eIdx   = str.indexOf('E')
    val result =
      if (eIdx < 0) {
        val e2Idx = str.indexOf('e')
        if (e2Idx < 0) stripTrailingZeros(str)
        else expandScientific(str, e2Idx)
      } else expandScientific(str, eIdx)
    writeRaw(result.getBytes(UTF_8))
  }

  private[this] def expandScientific(str: String, eIdx: Int): String = stripTrailingZeros {
    val mantissa = str.substring(0, eIdx)
    val exp      = str.substring(eIdx + 1).toInt
    val mant     = mantissa.split('.')
    var digits   = mant(0)
    if (mant.length == 2) digits += mant(1)
    val pointPos = mant(0).length + exp
    if (pointPos <= 0) {
      val sb = new java.lang.StringBuilder(digits.length - pointPos + 2)
      sb.append('0').append('.')
      var i = -pointPos
      while (i > 0) {
        sb.append('0')
        i -= 1
      }
      sb.append(digits).toString
    } else if (pointPos >= digits.length) {
      val sb = new java.lang.StringBuilder(pointPos)
      sb.append(digits)
      var i = pointPos - digits.length
      while (i > 0) {
        sb.append('0')
        i -= 1
      }
      sb.toString
    } else digits.substring(0, pointPos) + '.' + digits.substring(pointPos)
  }

  private[this] def writeQuotedString(s: String): Unit = {
    writeByte('"')
    count = writeEscapedStringAsUtf8Bytes(s, 0, s.length, count, buf.length - 4)
    writeByte('"')
  }

  @tailrec
  private[this] def writeEscapedStringAsUtf8Bytes(s: String, from: Int, to: Int, pos: Int, posLim: Int): Int =
    if (from >= to) pos
    else if (pos >= posLim) {
      buf = java.util.Arrays.copyOf(buf, Math.max(buf.length << 1, count + (to - from) * 3))
      writeEscapedStringAsUtf8Bytes(s, from, to, pos, buf.length - 4)
    } else {
      val ch1 = s.charAt(from).toInt
      if (ch1 < 0x80) {
        (ch1: @switch) match {
          case '"' =>
            buf(pos) = '\\'
            buf(pos + 1) = '"'
            writeEscapedStringAsUtf8Bytes(s, from + 1, to, pos + 2, posLim)
          case '\\' =>
            buf(pos) = '\\'
            buf(pos + 1) = '\\'
            writeEscapedStringAsUtf8Bytes(s, from + 1, to, pos + 2, posLim)
          case '\n' =>
            buf(pos) = '\\'
            buf(pos + 1) = 'n'
            writeEscapedStringAsUtf8Bytes(s, from + 1, to, pos + 2, posLim)
          case '\r' =>
            buf(pos) = '\\'
            buf(pos + 1) = 'r'
            writeEscapedStringAsUtf8Bytes(s, from + 1, to, pos + 2, posLim)
          case '\t' =>
            buf(pos) = '\\'
            buf(pos + 1) = 't'
            writeEscapedStringAsUtf8Bytes(s, from + 1, to, pos + 2, posLim)
          case _ =>
            buf(pos) = ch1.toByte
            writeEscapedStringAsUtf8Bytes(s, from + 1, to, pos + 1, posLim)
        }
      } else if (ch1 < 0x800) { // 00000bbbbbaaaaaa (UTF-16 char) -> 110bbbbb 10aaaaaa (UTF-8 bytes)
        buf(pos) = (ch1 >> 6 | 0xc0).toByte
        buf(pos + 1) = (ch1 & 0x3f | 0x80).toByte
        writeEscapedStringAsUtf8Bytes(s, from + 1, to, pos + 2, posLim)
      } else if ((ch1 & 0xf800) != 0xd800) { // ccccbbbbbbaaaaaa (UTF-16 char) -> 1110cccc 10bbbbbb 10aaaaaa (UTF-8 bytes)
        buf(pos) = (ch1 >> 12 | 0xe0).toByte
        buf(pos + 1) = (ch1 >> 6 & 0x3f | 0x80).toByte
        buf(pos + 2) = (ch1 & 0x3f | 0x80).toByte
        writeEscapedStringAsUtf8Bytes(s, from + 1, to, pos + 3, posLim)
      } else { // 110110uuuuccccbb 110111bbbbaaaaaa (UTF-16 chars) -> 11110ddd 10ddcccc 10bbbbbb 10aaaaaa (UTF-8 bytes), where ddddd = uuuu + 1
        var ch2 = 0
        if (
          ch1 >= 0xdc00 || from + 1 >= to || {
            ch2 = s.charAt(from + 1).toInt
            (ch2 & 0xfc00) != 0xdc00
          }
        ) encodeError("Illegal surrogate pair")
        val cp = (ch1 << 10) + (ch2 - 56613888) // -56613888 == 0x10000 - (0xD800 << 10) - 0xDC00
        buf(pos) = (cp >> 18 | 0xf0).toByte
        buf(pos + 1) = (cp >> 12 & 0x3f | 0x80).toByte
        buf(pos + 2) = (cp >> 6 & 0x3f | 0x80).toByte
        buf(pos + 3) = (cp & 0x3f | 0x80).toByte
        writeEscapedStringAsUtf8Bytes(s, from + 2, to, pos + 4, posLim)
      }
    }

  private[this] def writeUnquotedString(s: String): Unit =
    count = writeStringAsUtf8Bytes(s, 0, s.length, count, buf.length - 4)

  @tailrec
  private[this] def writeStringAsUtf8Bytes(s: String, from: Int, to: Int, pos: Int, posLim: Int): Int =
    if (from >= to) pos
    else if (pos >= posLim) {
      buf = java.util.Arrays.copyOf(buf, Math.max(buf.length << 1, count + (to - from) * 3))
      writeStringAsUtf8Bytes(s, from, to, pos, buf.length - 4)
    } else {
      val ch1 = s.charAt(from).toInt
      if (ch1 < 0x80) {
        buf(pos) = ch1.toByte
        writeStringAsUtf8Bytes(s, from + 1, to, pos + 1, posLim)
      } else if (ch1 < 0x800) { // 00000bbbbbaaaaaa (UTF-16 char) -> 110bbbbb 10aaaaaa (UTF-8 bytes)
        buf(pos) = (ch1 >> 6 | 0xc0).toByte
        buf(pos + 1) = (ch1 & 0x3f | 0x80).toByte
        writeStringAsUtf8Bytes(s, from + 1, to, pos + 2, posLim)
      } else if ((ch1 & 0xf800) != 0xd800) { // ccccbbbbbbaaaaaa (UTF-16 char) -> 1110cccc 10bbbbbb 10aaaaaa (UTF-8 bytes)
        buf(pos) = (ch1 >> 12 | 0xe0).toByte
        buf(pos + 1) = (ch1 >> 6 & 0x3f | 0x80).toByte
        buf(pos + 2) = (ch1 & 0x3f | 0x80).toByte
        writeStringAsUtf8Bytes(s, from + 1, to, pos + 3, posLim)
      } else { // 110110uuuuccccbb 110111bbbbaaaaaa (UTF-16 chars) -> 11110ddd 10ddcccc 10bbbbbb 10aaaaaa (UTF-8 bytes), where ddddd = uuuu + 1
        var ch2 = 0
        if (
          ch1 >= 0xdc00 || from + 1 >= to || {
            ch2 = s.charAt(from + 1).toInt
            (ch2 & 0xfc00) != 0xdc00
          }
        ) encodeError("Illegal surrogate pair")
        val cp = (ch1 << 10) + (ch2 - 56613888) // -56613888 == 0x10000 - (0xD800 << 10) - 0xDC00
        buf(pos) = (cp >> 18 | 0xf0).toByte
        buf(pos + 1) = (cp >> 12 & 0x3f | 0x80).toByte
        buf(pos + 2) = (cp >> 6 & 0x3f | 0x80).toByte
        buf(pos + 3) = (cp & 0x3f | 0x80).toByte
        writeStringAsUtf8Bytes(s, from + 2, to, pos + 4, posLim)
      }
    }

  private[this] def writeByte(b: Int): Unit = {
    if (count >= buf.length) buf = java.util.Arrays.copyOf(buf, buf.length << 1)
    buf(count) = b.toByte
    count += 1
  }

  private[this] def writeRaw(bs: Array[Byte]): Unit = {
    val len    = bs.length
    val newLen = count + len
    if (newLen > buf.length) buf = java.util.Arrays.copyOf(buf, Math.max(buf.length << 1, newLen))
    System.arraycopy(bs, 0, buf, count, len)
    count = newLen
  }

  private[this] def isValidUnquotedKey(key: String): Boolean = {
    val len = key.length
    var i   = 0
    while (i < len) {
      val c = key.charAt(i)
      val a = c | 0x20
      if (!(a >= 'a' && a <= 'z' || c == '_' || i > 0 && (c >= '0' && c <= '9' || c == '.'))) {
        return false
      }
      i += 1
    }
    i != 0
  }

  private[this] def needsQuoting(s: String, delimiter: Delimiter): Boolean = {
    if (s.isEmpty) return true
    var c   = s.charAt(0)
    val len = s.length
    if (c == '-' || c == ' ' || s.charAt(len - 1) == ' ') return true
    if (s == "true" || s == "false" || s == "null") return true
    if (c >= '0' && c <= '9') {
      var hasDot = false
      var i      = 1
      while (
        i < len && {
          c = s.charAt(i)
          c >= '0' && c <= '9' || c == '.' && !hasDot && {
            hasDot = true
            true
          }
        }
      ) i += 1
      if (
        i < len && {
          c = s.charAt(i)
          c == 'e' || c == 'E'
        }
      ) {
        i += 1
        if (
          i < len && {
            c = s.charAt(i)
            c == '+' || c == '-'
          }
        ) {
          i += 1
        }
        while (
          i < len && {
            c = s.charAt(i)
            c >= '0' && c <= '9'
          }
        ) i += 1
      }
      if (i == len) return true
    }
    var i = 0
    while (i < len) {
      c = s.charAt(i)
      if (
        c == '"' || c == '\\' || c == '\n' || c == '\r' || c == '\t' || c == ':' || c == '[' || c == ']' || c == '{' || c == '}'
      ) return true
      if (delimiter != null && c == delimiter.char) return true
      i += 1
    }
    false
  }

  private[this] def stripTrailingZeros(s: String): String = {
    if (s.indexOf('.') < 0) return s
    var end = s.length
    while (end > 0 && s.charAt(end - 1) == '0') end -= 1
    if (end > 0 && s.charAt(end - 1) == '.') end -= 1
    if (end == s.length) s else s.substring(0, end)
  }
}

object ToonWriter {
  private val nullBytes  = "null".getBytes(UTF_8)
  private val trueBytes  = "true".getBytes(UTF_8)
  private val falseBytes = "false".getBytes(UTF_8)

  private[toon] def isIdentifierSegment(key: String): Boolean = {
    val len = key.length
    var i   = 0
    while (i < len) {
      val c = key.charAt(i)
      val a = c | 0x20
      if (!(a >= 'a' && a <= 'z' || c == '_' || i > 0 && (c >= '0' && c <= '9'))) {
        return false
      }
      i += 1
    }
    i != 0
  }

  private[this] val pool: ThreadLocal[ToonWriter] = new ThreadLocal[ToonWriter] {
    override def initialValue(): ToonWriter =
      new ToonWriter(new Array[Byte](1024), 0, 2, Delimiter.Comma, KeyFolding.Off, Int.MaxValue, None)
  }

  def apply(config: WriterConfig): ToonWriter = {
    val writer = pool.get()
    if (
      writer.indentSize == config.indent &&
      writer.delimiter == config.delimiter &&
      writer.keyFolding == config.keyFolding &&
      writer.flattenDepth == config.flattenDepth &&
      writer.discriminatorField == config.discriminatorField
    ) {
      writer.reset()
      writer
    } else {
      new ToonWriter(
        new Array[Byte](1024),
        0,
        config.indent,
        config.delimiter,
        config.keyFolding,
        config.flattenDepth,
        config.discriminatorField
      )
    }
  }

  /**
   * Creates a fresh writer that is NOT from the pool. Use this when you need a
   * temporary writer while another writer is in use (e.g., for encoding keys to
   * strings).
   */
  private[toon] def fresh(config: WriterConfig): ToonWriter =
    new ToonWriter(
      new Array[Byte](64),
      0,
      config.indent,
      config.delimiter,
      config.keyFolding,
      config.flattenDepth,
      config.discriminatorField
    )
}
