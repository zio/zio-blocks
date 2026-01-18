/*
 * Copyright 2023 ZIO Blocks Maintainers
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

import java.io.OutputStream
import java.nio.charset.StandardCharsets.UTF_8
import scala.annotation.switch

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
  import ToonWriter._

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
  def exitInlineContext(): Unit  = inlineContext = false
  def isInlineContext: Boolean   = inlineContext

  def enterListItemContext(): Unit = listItemContext = true
  def exitListItemContext(): Unit  = listItemContext = false
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

  def writeNull(): Unit = writeRaw(nullBytes)

  def writeBoolean(x: Boolean): Unit =
    if (x) writeRaw(trueBytes) else writeRaw(falseBytes)

  def writeInt(x: Int): Unit = writeRaw(x.toString.getBytes(UTF_8))

  def writeLong(x: Long): Unit = writeRaw(x.toString.getBytes(UTF_8))

  def writeFloat(x: Float): Unit =
    if (x.isNaN || x.isInfinite) writeNull()
    else if (x == 0.0f) writeByte('0')
    else {
      val str = java.lang.Float.toString(x)
      writeDecimalString(str)
    }

  def writeDouble(x: Double): Unit =
    if (x.isNaN || x.isInfinite) writeNull()
    else if (x == 0.0 || x == -0.0) writeByte('0')
    else {
      val str = java.lang.Double.toString(x)
      writeDecimalString(str)
    }

  def writeBigDecimal(x: BigDecimal): Unit = {
    val str = x.underlying.toString
    writeDecimalString(str)
  }

  def writeBigInt(x: BigInt): Unit = writeRaw(x.toString.getBytes(UTF_8))

  def writeChar(c: Char): Unit = writeString(c.toString)

  def writeString(s: String): Unit =
    if (needsQuoting(s, if (inlineContext) activeDelimiter else null)) writeQuotedString(s)
    else writeRaw(s.getBytes(UTF_8))

  def writeString(s: String, delimiterOverride: Delimiter): Unit =
    if (needsQuoting(s, delimiterOverride)) writeQuotedString(s)
    else writeRaw(s.getBytes(UTF_8))

  def writeKey(key: String): Unit = {
    ensureIndent()
    if (isValidUnquotedKey(key)) writeRaw(key.getBytes(UTF_8))
    else writeQuotedString(key)
    writeByte(':')
    writeByte(' ')
  }

  def writeKeyOnly(key: String): Unit = {
    ensureIndent()
    if (isValidUnquotedKey(key)) writeRaw(key.getBytes(UTF_8))
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
      if (isValidUnquotedKey(key)) writeRaw(key.getBytes(UTF_8))
      else writeQuotedString(key)
    }
    writeByte('[')
    writeRaw(length.toString.getBytes(UTF_8))
    if (delim != Delimiter.Comma) writeByte(delim.char)
    writeByte(']')
    if (fields != null && fields.length > 0) {
      writeByte('{')
      var i = 0
      while (i < fields.length) {
        if (i > 0) writeByte(delim.char)
        val field = fields(i)
        if (isValidUnquotedKey(field)) writeRaw(field.getBytes(UTF_8))
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

  def writeTabularHeader(length: Int, fields: Vector[String]): Unit = {
    ensureIndent()
    writeByte('[')
    writeRaw(length.toString.getBytes(UTF_8))
    if (delimiter != Delimiter.Comma) writeByte(delimiter.char)
    writeByte(']')
    writeByte('{')
    var i = 0
    while (i < fields.length) {
      if (i > 0) writeByte(delimiter.char)
      val field = fields(i)
      if (isValidUnquotedKey(field)) writeRaw(field.getBytes(UTF_8))
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
      var i      = 0
      val spaces = depth * indentSize
      while (i < spaces) {
        writeByte(' ')
        i += 1
      }
      lineStart = false
    }

  private def writeDecimalString(str: String): Unit = {
    val eIdx   = str.indexOf('E')
    val result = if (eIdx < 0) {
      val e2Idx = str.indexOf('e')
      if (e2Idx < 0) stripTrailingZeros(str)
      else expandScientific(str, e2Idx)
    } else expandScientific(str, eIdx)
    writeRaw(result.getBytes(UTF_8))
  }

  private def expandScientific(str: String, eIdx: Int): String = {
    val mantissa = str.substring(0, eIdx)
    val exp      = str.substring(eIdx + 1).toInt

    val negative            = mantissa.startsWith("-")
    val mant                = if (negative) mantissa.substring(1) else mantissa
    val dotIdx              = mant.indexOf('.')
    val (intPart, fracPart) =
      if (dotIdx < 0) (mant, "")
      else (mant.substring(0, dotIdx), mant.substring(dotIdx + 1))
    val digits   = intPart + fracPart
    val pointPos = intPart.length + exp

    val plain =
      if (pointPos <= 0) {
        "0." + ("0" * -pointPos) + digits
      } else if (pointPos >= digits.length) {
        digits + ("0" * (pointPos - digits.length))
      } else {
        digits.substring(0, pointPos) + "." + digits.substring(pointPos)
      }

    val stripped = stripTrailingZeros(plain)
    if (negative) "-" + stripped else stripped
  }

  private def writeQuotedString(s: String): Unit = {
    writeByte('"')
    var i = 0
    while (i < s.length) {
      val c = s.charAt(i)
      (c: @switch) match {
        case '"' =>
          writeByte('\\')
          writeByte('"')
        case '\\' =>
          writeByte('\\')
          writeByte('\\')
        case '\n' =>
          writeByte('\\')
          writeByte('n')
        case '\r' =>
          writeByte('\\')
          writeByte('r')
        case '\t' =>
          writeByte('\\')
          writeByte('t')
        case _ =>
          if (c < 0x80) writeByte(c.toByte)
          else if (Character.isHighSurrogate(c) && i + 1 < s.length && Character.isLowSurrogate(s.charAt(i + 1))) {
            writeRaw(s.substring(i, i + 2).getBytes(UTF_8))
            i += 1
          } else writeRaw(Character.toString(c).getBytes(UTF_8))
      }
      i += 1
    }
    writeByte('"')
  }

  private def writeByte(b: Int): Unit = {
    if (count >= buf.length) buf = java.util.Arrays.copyOf(buf, buf.length << 1)
    buf(count) = b.toByte
    count += 1
  }

  private def writeRaw(bs: Array[Byte]): Unit = {
    val len    = bs.length
    val newLen = count + len
    if (newLen > buf.length) buf = java.util.Arrays.copyOf(buf, Math.max(buf.length << 1, newLen))
    System.arraycopy(bs, 0, buf, count, len)
    count = newLen
  }
}

object ToonWriter {
  private val nullBytes  = "null".getBytes(UTF_8)
  private val trueBytes  = "true".getBytes(UTF_8)
  private val falseBytes = "false".getBytes(UTF_8)

  private val validKeyPattern          = "^[A-Za-z_][A-Za-z0-9_.]*$".r.pattern
  private val identifierSegmentPattern = "^[A-Za-z_][A-Za-z0-9_]*$".r.pattern
  private val numericPattern           = "^-?\\d+(?:\\.\\d+)?(?:[eE][+-]?\\d+)?$".r.pattern
  private val leadingZeroPattern       = "^0\\d+$".r.pattern

  /**
   * Checks if a key segment is a valid IdentifierSegment per TOON spec.
   * Pattern: ^[A-Za-z_][A-Za-z0-9_]*$ (no dots, no hyphens, no special chars)
   */
  private[toon] def isIdentifierSegment(key: String): Boolean =
    key.nonEmpty && identifierSegmentPattern.matcher(key).matches()

  private val pool: ThreadLocal[ToonWriter] = new ThreadLocal[ToonWriter] {
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
  def fresh(config: WriterConfig): ToonWriter =
    new ToonWriter(
      new Array[Byte](64),
      0,
      config.indent,
      config.delimiter,
      config.keyFolding,
      config.flattenDepth,
      config.discriminatorField
    )

  private def isValidUnquotedKey(key: String): Boolean =
    key.nonEmpty && validKeyPattern.matcher(key).matches()

  private def needsQuoting(s: String, delimiter: Delimiter): Boolean = {
    if (s.isEmpty) return true
    if (s.charAt(0) == ' ' || s.charAt(s.length - 1) == ' ') return true
    if (s.charAt(0) == '-') return true
    if (s == "true" || s == "false" || s == "null") return true
    if (numericPattern.matcher(s).matches() || leadingZeroPattern.matcher(s).matches()) return true

    var i = 0
    while (i < s.length) {
      val c = s.charAt(i)
      if (c == '"' || c == '\\') return true
      if (c == '\n' || c == '\r' || c == '\t') return true
      if (c == ':') return true
      if (c == '[' || c == ']' || c == '{' || c == '}') return true
      if (delimiter != null && c == delimiter.char) return true
      i += 1
    }
    false
  }

  private def stripTrailingZeros(s: String): String = {
    if (!s.contains(".")) return s
    var end = s.length
    while (end > 0 && s.charAt(end - 1) == '0') end -= 1
    if (end > 0 && s.charAt(end - 1) == '.') end -= 1
    if (end == s.length) s else s.substring(0, end)
  }
}
