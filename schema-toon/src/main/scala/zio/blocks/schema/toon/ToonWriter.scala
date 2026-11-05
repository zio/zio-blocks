/*
 * Copyright 2024-2026 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zio.blocks.schema.toon

import zio.blocks.chunk.Chunk
import zio.blocks.schema.json.JsonCodec

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
 *   the default delimiter for inline arrays and table rows
 * @param keyFolding
 *   strategy for folding nested keys
 * @param flattenDepth
 *   the maximum number of keys that key folding joins into one dotted key
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

  /**
   * Clears the written output and returns the writer to depth 0, the start of a
   * line, the construction-time delimiter, and outside inline and list item
   * contexts.
   */
  def reset(): Unit = {
    count = 0
    depth = 0
    lineStart = true
    activeDelimiter = delimiter
    inlineContext = false
    listItemContext = false
  }

  /**
   * Returns the current indentation depth, counted in indentation levels.
   */
  def getDepth: Int = depth

  /**
   * Increases the indentation depth by one level.
   */
  def incrementDepth(): Unit = depth += 1

  /**
   * Decreases the indentation depth by one level; at depth 0 it does nothing.
   */
  def decrementDepth(): Unit = if (depth > 0) depth -= 1

  /**
   * Sets the delimiter that string quoting checks use while the writer is in an
   * inline context.
   */
  def setActiveDelimiter(d: Delimiter): Unit = activeDelimiter = d

  /**
   * Returns the delimiter that string quoting checks use while the writer is in
   * an inline context.
   */
  def getActiveDelimiter: Delimiter = activeDelimiter

  /**
   * Marks the writer as writing inline values, so that `writeString` also
   * quotes strings that contain the active delimiter.
   */
  def enterInlineContext(): Unit = inlineContext = true

  /**
   * Clears the inline context set by `enterInlineContext`.
   */
  def exitInlineContext(): Unit = inlineContext = false

  /**
   * Returns whether the writer is in an inline context.
   */
  def isInlineContext: Boolean = inlineContext

  /**
   * Marks the writer as writing inside a list item.
   */
  def enterListItemContext(): Unit = listItemContext = true

  /**
   * Clears the list item context set by `enterListItemContext`.
   */
  def exitListItemContext(): Unit = listItemContext = false

  /**
   * Returns whether the writer is in a list item context.
   */
  def isInListItemContext: Boolean = listItemContext

  /**
   * Returns a copy of the bytes written so far, as UTF-8.
   */
  def toByteArray: Array[Byte] = java.util.Arrays.copyOf(buf, count)

  /**
   * Returns a copy of the bytes written so far, as UTF-8, without trailing
   * newline and space characters.
   */
  def toByteArrayTrimmed: Array[Byte] = {
    var end = count
    while (end > 0 && (buf(end - 1) == '\n' || buf(end - 1) == ' ')) end -= 1
    java.util.Arrays.copyOf(buf, end)
  }

  private[this] def encodeError(msg: String): Nothing = throw new ToonCodecError(Nil, msg)

  /**
   * Writes the bytes written so far to `out`, without trailing newline and
   * space characters.
   *
   * @param out
   *   the stream to write to
   */
  def writeToTrimmed(out: OutputStream): Unit = {
    var end = count
    while (end > 0 && (buf(end - 1) == '\n' || buf(end - 1) == ' ')) end -= 1
    out.write(buf, 0, end)
  }

  /**
   * Writes the value `null`.
   */
  def writeNull(): Unit = writeRaw(ToonWriter.nullBytes)

  /**
   * Writes `true` or `false`.
   */
  def writeBoolean(x: Boolean): Unit = writeRaw {
    if (x) ToonWriter.trueBytes
    else ToonWriter.falseBytes
  }

  /**
   * Writes `x` as decimal digits.
   */
  def writeInt(x: Int): Unit = writeRaw(JsonCodec.intCodec.encode(x))

  /**
   * Writes `x` as decimal digits.
   */
  def writeLong(x: Long): Unit = writeRaw(JsonCodec.longCodec.encode(x))

  /**
   * Writes `x` in plain decimal notation without an exponent or trailing zeros.
   * A zero is written as `0`; `NaN` and the infinities are written as `null`.
   */
  def writeFloat(x: Float): Unit =
    if (x.isNaN || x.isInfinite) writeNull()
    else if (x == 0.0f) writeByte('0')
    else writeDecimalString(JsonCodec.floatCodec.encodeToString(x))

  /**
   * Writes `x` in plain decimal notation without an exponent or trailing zeros.
   * A zero is written as `0`; `NaN` and the infinities are written as `null`.
   */
  def writeDouble(x: Double): Unit =
    if (x.isNaN || x.isInfinite) writeNull()
    else if (x == 0.0 || x == -0.0) writeByte('0')
    else writeDecimalString(JsonCodec.doubleCodec.encodeToString(x))

  /**
   * Writes `x` in plain decimal notation without an exponent or trailing zeros.
   */
  def writeBigDecimal(x: BigDecimal): Unit = writeDecimalString(JsonCodec.bigDecimalCodec.encodeToString(x))

  /**
   * Writes `x` as decimal digits.
   */
  def writeBigInt(x: BigInt): Unit = writeRaw(JsonCodec.bigIntCodec.encode(x))

  /**
   * Writes `c` as a string value, quoted when `writeString` would quote it.
   */
  def writeChar(c: Char): Unit = writeString(c.toString)

  /**
   * Writes `s` as a string value, quoted when it needs quoting; in an inline
   * context the active delimiter also triggers quoting.
   */
  def writeString(s: String): Unit = writeString(
    s,
    if (inlineContext) activeDelimiter
    else null
  )

  /**
   * Writes `s` as a string value, quoted when it needs quoting. When
   * `delimiterOverride` is not `null`, a string that contains that delimiter is
   * quoted too.
   *
   * @param delimiterOverride
   *   the delimiter to check for, or `null` for none
   */
  def writeString(s: String, delimiterOverride: Delimiter): Unit =
    if (needsQuoting(s, delimiterOverride)) writeQuotedString(s)
    else writeUnquotedString(s)

  /**
   * Writes `key` followed by a colon and a space, indented to the current depth
   * when at the start of a line. The key is quoted when it is not a valid
   * unquoted key.
   */
  def writeKey(key: String): Unit = {
    ensureIndent()
    if (isValidUnquotedKey(key)) writeUnquotedString(key)
    else writeQuotedString(key)
    writeByte(':')
    writeByte(' ')
  }

  /**
   * Writes `key` followed by a colon and a newline, indented to the current
   * depth when at the start of a line. The key is quoted when it is not a valid
   * unquoted key.
   */
  def writeKeyOnly(key: String): Unit = {
    ensureIndent()
    if (isValidUnquotedKey(key)) writeUnquotedString(key)
    else writeQuotedString(key)
    writeByte(':')
    newLine()
  }

  /**
   * Writes a header without a key and fields, `[length]:`, using the writer's
   * delimiter.
   */
  def writeArrayHeader(length: Int): Unit = writeArrayHeader(null, length, null, delimiter)

  /**
   * Writes a header without fields, `key[length]:`, using the writer's
   * delimiter.
   */
  def writeArrayHeader(key: String, length: Int): Unit = writeArrayHeader(key, length, null, delimiter)

  /**
   * Writes a header such as `key[length]{a,b}:` using the writer's delimiter.
   */
  def writeArrayHeader(key: String, length: Int, fields: Array[String]): Unit =
    writeArrayHeader(key, length, fields, delimiter)

  /**
   * Writes an array header such as `key[length]{a,b}:`. A `null` key or
   * `fields` omits that part, and a delimiter other than a comma is written
   * inside the brackets after the length and between the field names. A key or
   * field name that is not a valid unquoted key (letters, digits, underscores
   * and dots, not starting with a digit or dot) is quoted.
   *
   * @param key
   *   the key before the bracket, or `null` for none
   * @param length
   *   the declared number of items
   * @param fields
   *   the field names of a tabular header, or `null` for none
   * @param delim
   *   the delimiter the header declares
   */
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

  /**
   * Writes a header like `writeArrayHeader` with no fields, followed by a
   * space, using the writer's delimiter.
   */
  def writeArrayHeaderInline(key: String, length: Int): Unit = writeArrayHeaderInline(key, length, delimiter)

  /**
   * Writes a header like `writeArrayHeader` with no fields, followed by a
   * space, so that inline values can follow.
   */
  def writeArrayHeaderInline(key: String, length: Int, delim: Delimiter): Unit = {
    writeArrayHeader(key, length, null, delim)
    writeByte(' ')
  }

  /**
   * Writes a keyless tabular header such as `[length]{a,b}:` using the writer's
   * delimiter.
   *
   * @param length
   *   the number of rows
   * @param fields
   *   the column names
   */
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

  /**
   * Writes the list item marker `- `, indented to the current depth when at the
   * start of a line.
   */
  def writeListItemMarker(): Unit = {
    ensureIndent()
    writeByte('-')
    writeByte(' ')
  }

  /**
   * Writes the delimiter `d`.
   */
  def writeDelimiter(d: Delimiter): Unit = writeByte(d.char)

  /**
   * Writes the writer's delimiter.
   */
  def writeDelimiter(): Unit = writeByte(delimiter.char)

  /**
   * Writes a newline; the next write starts a new, indented line.
   */
  def newLine(): Unit = {
    writeByte('\n')
    lineStart = true
  }

  /**
   * Writes a colon followed by a space.
   */
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
      if (!(a >= 'a' && a <= 'z' || c == '_' || i > 0 && (c >= '0' && c <= '9' || c == '.'))) return false
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
        ) i += 1
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

/**
 * The companion of `ToonWriter`; creates writers from a `WriterConfig`.
 */
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
      if (!(a >= 'a' && a <= 'z' || c == '_' || i > 0 && (c >= '0' && c <= '9'))) return false
      i += 1
    }
    i != 0
  }

  private[this] val pool: ThreadLocal[ToonWriter] = new ThreadLocal[ToonWriter] {
    override def initialValue(): ToonWriter =
      new ToonWriter(new Array[Byte](1024), 0, 2, Delimiter.Comma, KeyFolding.Off, Int.MaxValue, None)
  }

  /**
   * Returns the current thread's pooled writer, cleared, when its settings
   * equal `config`, and a new writer otherwise.
   */
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
