package zio.blocks.schema.toon

import zio.blocks.schema.DynamicOptic
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets.UTF_8
import scala.annotation.switch

/**
 * A reader to parse TOON input iteratively.
 *
 * @param indentSize
 *   the expected number of spaces per indentation level
 * @param delimiter
 *   the expected delimiter for inline arrays
 * @param strict
 *   whether to enforce strict TOON parsing
 * @param expandPaths
 *   strategy for expanding dot-separated paths
 * @param discriminatorField
 *   optional field name to use as discriminator for DynamicValue variants
 */
final class ToonReader private[toon] (
  private[this] var indentSize: Int,
  private[this] var delimiter: Delimiter,
  private[this] var strict: Boolean,
  private[toon] var expandPaths: PathExpansion,
  private[toon] var discriminatorField: Option[String]
) {

  private[this] var lines: Array[String]       = null
  private[this] var lineIndex: Int             = 0
  private[this] var currentLine: String        = ""
  private[this] var currentDepth: Int          = 0
  private[this] var linePos: Int               = 0
  private[this] var activeDelimiter: Delimiter = delimiter
  private[this] var inUse: Boolean             = false
  private[this] var inlineContext: Boolean     = false

  // Mark/rollback support for DiscriminatorKind.None
  private[this] var markedLineIndex: Int      = -1
  private[this] var markedLinePos: Int        = -1
  private[this] var markedCurrentDepth: Int   = -1
  private[this] var markedCurrentLine: String = null

  /**
   * Indicates whether the reader is currently in use.
   *
   * @return
   *   true if the reader is in use, false otherwise
   */
  private[toon] def isInUse: Boolean = inUse

  /**
   * Returns whether strict mode is enabled. Used for path expansion conflict
   * detection.
   */
  private[toon] def isStrict: Boolean = strict

  def enterInlineContext(): Unit = inlineContext = true
  def exitInlineContext(): Unit  = inlineContext = false
  def isInlineContext: Boolean   = inlineContext

  /**
   * Marks the reader as in use and configures it with the given settings.
   */
  private[toon] def startUse(config: ReaderConfig): Unit = {
    inUse = true
    indentSize = config.indent
    delimiter = config.delimiter
    strict = config.strict
    expandPaths = config.expandPaths
    discriminatorField = config.discriminatorField
    activeDelimiter = config.delimiter
    inlineContext = false
  }

  /**
   * Marks the reader as no longer in use.
   */
  private[toon] def endUse(): Unit = {
    inUse = false
    lines = null
  }

  def reset(bytes: Array[Byte], offset: Int, length: Int): Unit = reset(new String(bytes, offset, length, UTF_8))

  def reset(content: String): Unit = {
    lines = content.split('\n')
    lineIndex = 0
    inlineContext = false
    if (lines.length > 0) {
      currentLine = lines(0)
      currentDepth = computeDepth(currentLine)
      linePos = currentDepth * indentSize
    }
  }

  def isFirstLine: Boolean = lineIndex == 0

  def hasMoreLines: Boolean = lineIndex < lines.length

  def hasMoreContent: Boolean = linePos < currentLine.length

  def getDepth: Int = currentDepth

  def setActiveDelimiter(d: Delimiter): Unit = activeDelimiter = d

  def getCurrentLine: String = currentLine

  def peekTrimmedContent: String = currentLine.substring(linePos).trim

  def advanceLine(): Unit = {
    lineIndex += 1
    if (lineIndex < lines.length) {
      currentLine = lines(lineIndex)
      currentDepth = computeDepth(currentLine)
      linePos = currentDepth * indentSize
    } else {
      currentLine = ""
      currentDepth = 0
      linePos = 0
    }
  }

  def skipBlankLines(): Unit =
    while (lineIndex < lines.length && isWhitespaceOnly(lines(lineIndex))) {
      lineIndex += 1
      if (lineIndex < lines.length) {
        currentLine = lines(lineIndex)
        currentDepth = computeDepth(currentLine)
        linePos = currentDepth * indentSize
      }
    }

  def skipBlankLinesInArray(isFirstItem: Boolean): Unit =
    while (lineIndex < lines.length && isWhitespaceOnly(lines(lineIndex))) {
      if (strict && !isFirstItem)
        decodeError("Blank lines are not allowed inside arrays/tabular blocks in strict mode")
      lineIndex += 1
      if (lineIndex < lines.length) {
        currentLine = lines(lineIndex)
        currentDepth = computeDepth(currentLine)
        linePos = currentDepth * indentSize
      }
    }

  private def isWhitespaceOnly(s: String): Boolean = {
    val len = s.length
    var i   = 0
    while (i < len) {
      if (!Character.isWhitespace(s.charAt(i))) return false
      i += 1
    }
    true
  }

  /**
   * Sets a mark at the current position. The reader can later be rolled back to
   * this position using `rollbackToMark()`.
   */
  def setMark(): Unit = {
    markedLineIndex = lineIndex
    markedLinePos = linePos
    markedCurrentDepth = currentDepth
    markedCurrentLine = currentLine
  }

  /**
   * Rolls back the reader to the previously marked position. Must be called
   * after `setMark()`.
   */
  def rollbackToMark(): Unit =
    if (markedLineIndex >= 0) {
      lineIndex = markedLineIndex
      linePos = markedLinePos
      currentDepth = markedCurrentDepth
      currentLine = markedCurrentLine
    }

  /**
   * Clears the mark without rolling back. Call this after successfully decoding
   * to allow the reader state to be garbage collected.
   */
  def resetMark(): Unit = {
    markedLineIndex = -1
    markedLinePos = -1
    markedCurrentDepth = -1
    markedCurrentLine = null
  }

  def isListItem: Boolean = {
    val trimmed = peekTrimmedContent
    trimmed.startsWith("- ") || trimmed == "-"
  }

  def consumeListItemMarker(): Boolean = {
    val trimmed = peekTrimmedContent
    if (trimmed.startsWith("- ")) {
      linePos = currentLine.indexOf("- ") + 2
      true
    } else if (trimmed == "-") {
      linePos = currentLine.indexOf('-') + 1
      true
    } else false
  }

  def readNull(): Unit = {
    val value = readPrimitiveToken()
    if (value != "null") decodeError(s"Expected null, got: $value")
  }

  def readBoolean(): Boolean = {
    val value = readPrimitiveToken()
    if (value == "true") true
    else if (value == "false") false
    else decodeError(s"Expected boolean, got: $value")
  }

  def readByte(): Byte = {
    val value = readPrimitiveToken()
    try value.toByte
    catch { case _: NumberFormatException => decodeError(s"Expected byte, got: $value") }
  }

  def readShort(): Short = {
    val value = readPrimitiveToken()
    try value.toShort
    catch { case _: NumberFormatException => decodeError(s"Expected short, got: $value") }
  }

  def readInt(): Int = {
    val value = readPrimitiveToken()
    try value.toInt
    catch { case _: NumberFormatException => decodeError(s"Expected int, got: $value") }
  }

  def readLong(): Long = {
    val value = readPrimitiveToken()
    try value.toLong
    catch { case _: NumberFormatException => decodeError(s"Expected long, got: $value") }
  }

  def readFloat(): Float = {
    val value = readPrimitiveToken()
    if (value == "null") Float.NaN
    else {
      try value.toFloat
      catch { case _: NumberFormatException => decodeError(s"Expected float, got: $value") }
    }
  }

  def readDouble(): Double = {
    val value = readPrimitiveToken()
    if (value == "null") Double.NaN
    else {
      try value.toDouble
      catch { case _: NumberFormatException => decodeError(s"Expected double, got: $value") }
    }
  }

  def readBigDecimal(): BigDecimal = {
    val value = readPrimitiveToken()
    try BigDecimal(value)
    catch { case _: NumberFormatException => decodeError(s"Expected BigDecimal, got: $value") }
  }

  def readBigInt(): BigInt = {
    val value = readPrimitiveToken()
    try BigInt(value)
    catch { case _: NumberFormatException => decodeError(s"Expected BigInt, got: $value") }
  }

  def readString(): String = readPrimitiveToken()

  def readKey(): String = {
    skipWhitespace()
    val colonIdx = findUnquotedColon(currentLine, linePos)
    if (colonIdx < 0) decodeError("Expected key:value, no colon found")
    val keyPart = currentLine.substring(linePos, colonIdx).trim
    val key     = if (keyPart.startsWith("\"")) unescapeString(keyPart) else keyPart
    linePos = colonIdx + 1
    skipWhitespace()
    key
  }

  def readKeyWithQuoteInfo(): (String, Boolean) = {
    skipWhitespace()
    val colonIdx = findUnquotedColon(currentLine, linePos)
    if (colonIdx < 0) decodeError("Expected key:value, no colon found")
    val keyPart   = currentLine.substring(linePos, colonIdx).trim
    val wasQuoted = keyPart.startsWith("\"")
    val key       = if (wasQuoted) unescapeString(keyPart) else keyPart
    linePos = colonIdx + 1
    skipWhitespace()
    (key, wasQuoted)
  }

  def readKeyWithArrayNotation(): String = {
    skipWhitespace()
    val colonIdx = findUnquotedColon(currentLine, linePos)
    if (colonIdx < 0) decodeError("Expected key:value, no colon found")
    val keyPart = currentLine.substring(linePos, colonIdx).trim
    linePos = colonIdx + 1
    skipWhitespace()
    if (keyPart.startsWith("\"")) unescapeString(keyPart) else keyPart
  }

  def parseArrayHeader(isInline: Boolean = false): ArrayHeader = {
    skipWhitespace()
    val bracketStart = findUnquotedChar(currentLine, '[', linePos)
    if (bracketStart < 0) decodeError("Expected array header with [")
    val key =
      if (bracketStart > linePos) {
        val keyPart = currentLine.substring(linePos, bracketStart).trim
        if (keyPart.startsWith("\"")) unescapeString(keyPart) else keyPart
      } else null
    val bracketEnd = currentLine.indexOf(']', bracketStart)
    if (bracketEnd < 0) decodeError("Expected closing ] in array header")
    val bracketContent        = currentLine.substring(bracketStart + 1, bracketEnd)
    val (length, delim)       = parseBracketContent(bracketContent)
    var fields: Array[String] = null
    var afterBracket          = bracketEnd + 1
    val len                   = currentLine.length - linePos
    if (afterBracket < len && currentLine.charAt(afterBracket) == '{') {
      val braceEnd = currentLine.indexOf('}', afterBracket)
      if (braceEnd < 0) decodeError("Expected closing } in field list")
      val fieldsContent = currentLine.substring(afterBracket + 1, braceEnd)
      fields = parseFieldList(fieldsContent, delim)
      afterBracket = braceEnd + 1
    }
    if (afterBracket >= len || currentLine.charAt(afterBracket) != ':') decodeError("Expected : after array header")
    activeDelimiter = delim
    if (isInline) linePos = afterBracket + 1
    else advanceLine()
    ArrayHeader(key, length, fields, delim)
  }

  def readInlineArray(): Array[String] = {
    skipWhitespace()
    val remaining = currentLine.substring(linePos).trim
    advanceLine()
    if (remaining.isEmpty) Array.empty[String]
    else splitByDelimiter(remaining, activeDelimiter)
  }

  def inferType(token: String): Any =
    if (token == "null") null
    else if (token == "true") true
    else if (token == "false") false
    else if (token.startsWith("\"")) unescapeString(token)
    else if (looksLikeNumber(token)) parseNumber(token)
    else token

  def decodeError(msg: String): Nothing = throw new ToonBinaryCodecError(Nil, msg)

  def decodeError(span: DynamicOptic.Node, error: Throwable): Nothing = error match {
    case e: ToonBinaryCodecError =>
      e.spans = new ::(span, e.spans)
      throw e
    case _ =>
      throw new ToonBinaryCodecError(new ::(span, Nil), error.getMessage)
  }

  private def readPrimitiveToken(): String = {
    skipWhitespace()
    val len = currentLine.length
    if (linePos >= len) {
      advanceLine()
      return if (lineIndex >= lines.length - 1) "" else readPrimitiveToken()
    }
    if (linePos + 1 < len && currentLine.charAt(linePos) == '"') {
      val endQuote = findEndQuote(currentLine, linePos + 1)
      if (endQuote < 0) decodeError("Unterminated string")
      val quoted = currentLine.substring(linePos, endQuote + 1)
      linePos = len
      unescapeString(quoted)
    } else {
      val token = if (inlineContext) {
        val delimIdx = findDelimiterIndex(currentLine, activeDelimiter, linePos)
        if (delimIdx >= 0) currentLine.substring(linePos, delimIdx)
        else currentLine.substring(linePos)
      } else currentLine.substring(linePos)
      linePos = len
      advanceLine()
      token
    }
  }

  private def skipWhitespace(): Unit =
    while (linePos < currentLine.length && currentLine.charAt(linePos) == ' ') linePos += 1

  private def computeDepth(line: String): Int = {
    var spaces = 0
    var i      = 0
    while (i < line.length) {
      val c = line.charAt(i)
      if (c == ' ') {
        spaces += 1
        i += 1
      } else if (c == '\t') {
        if (strict) decodeError("Tabs are not allowed in indentation")
        spaces += 1
        i += 1
      } else {
        i = line.length
      }
    }
    if (strict && indentSize > 0 && spaces % indentSize != 0 && !isWhitespaceOnly(line))
      decodeError(s"Indentation must be multiple of $indentSize spaces")
    if (indentSize > 0) spaces / indentSize else 0
  }

  private def parseBracketContent(content: String): (Int, Delimiter) = {
    val trimmed = content.trim
    if (trimmed.isEmpty) decodeError("Empty array length")

    val lastChar = trimmed.charAt(trimmed.length - 1)
    val delim    =
      if (lastChar == '\t') Delimiter.Tab
      else if (lastChar == '|') Delimiter.Pipe
      else Delimiter.Comma

    val lengthStr = if (delim != Delimiter.Comma) trimmed.dropRight(1).trim else trimmed
    val length    =
      try lengthStr.toInt
      catch { case _: NumberFormatException => decodeError(s"Invalid array length: $lengthStr") }
    (length, delim)
  }

  private def parseFieldList(content: String, delim: Delimiter): Array[String] =
    splitByDelimiter(content, delim).map { f =>
      val trimmed = f.trim
      if (trimmed.startsWith("\"")) unescapeString(trimmed) else trimmed
    }

  private def splitByDelimiter(s: String, delim: Delimiter): Array[String] = {
    val result  = new scala.collection.mutable.ArrayBuffer[String]()
    var start   = 0
    var inQuote = false
    var i       = 0
    while (i < s.length) {
      val c = s.charAt(i)
      if (c == '"' && !isEscaped(s, i)) inQuote = !inQuote
      else if (!inQuote && c == delim.char) {
        result.addOne(trimUnquoted(s.substring(start, i)))
        start = i + 1
      }
      i += 1
    }
    if (start <= s.length) result.addOne(trimUnquoted(s.substring(start)))
    result.toArray
  }

  private def trimUnquoted(s: String): String = s.trim

  private def findUnquotedColon(line: String, from: Int): Int = {
    var inQuote = false
    var i       = from
    while (i < line.length) {
      val c = line.charAt(i)
      if (c == '"' && !isEscaped(line, i)) inQuote = !inQuote
      else if (!inQuote && c == ':') return i
      i += 1
    }
    -1
  }

  private def findUnquotedChar(s: String, target: Char, from: Int): Int = {
    var inQuote = false
    var i       = from
    while (i < s.length) {
      val c = s.charAt(i)
      if (c == '"' && !isEscaped(s, i)) inQuote = !inQuote
      else if (!inQuote && c == target) return i
      i += 1
    }
    -1
  }

  private def findEndQuote(s: String, from: Int): Int = {
    var i = from
    while (i < s.length) {
      if (s.charAt(i) == '"' && !isEscaped(s, i)) return i
      i += 1
    }
    -1
  }

  private def findDelimiterIndex(s: String, delim: Delimiter, from: Int): Int = {
    var inQuote = false
    var i       = from
    while (i < s.length) {
      val c = s.charAt(i)
      if (c == '"' && !isEscaped(s, i)) inQuote = !inQuote
      else if (!inQuote && c == delim.char) return i
      i += 1
    }
    -1
  }

  private def isEscaped(s: String, pos: Int): Boolean = {
    if (pos == 0) return false
    var backslashCount = 0
    var j              = pos - 1
    while (j >= 0 && s.charAt(j) == '\\') {
      backslashCount += 1
      j -= 1
    }
    (backslashCount & 1) == 1
  }

  private def looksLikeNumber(s: String): Boolean = {
    if (s.isEmpty) return false
    val start = if (s.charAt(0) == '-') 1 else 0
    if (start >= s.length) return false

    val firstDigitIdx = start
    if (firstDigitIdx < s.length && s.charAt(firstDigitIdx) == '0') {
      if (firstDigitIdx + 1 < s.length) {
        val nextChar = s.charAt(firstDigitIdx + 1)
        if (nextChar >= '0' && nextChar <= '9') {
          return false
        }
      }
    }

    var hasDigit = false
    var hasDot   = false
    var i        = start
    while (i < s.length) {
      val c = s.charAt(i)
      if (c >= '0' && c <= '9') hasDigit = true
      else if (c == '.' && !hasDot) hasDot = true
      else if (c == 'e' || c == 'E') {
        i += 1
        if (i < s.length && (s.charAt(i) == '+' || s.charAt(i) == '-')) i += 1
        while (i < s.length && s.charAt(i) >= '0' && s.charAt(i) <= '9') i += 1
        return i == s.length && hasDigit
      } else return false
      i += 1
    }
    hasDigit
  }

  private def parseNumber(s: String): Any =
    if (s.indexOf('.') >= 0 || s.indexOf('e') >= 0 || s.indexOf('E') >= 0) {
      val d = s.toDouble
      if (d == d.toLong && d >= Long.MinValue && d <= Long.MaxValue) d.toLong
      else d
    } else {
      try s.toLong
      catch { case _: NumberFormatException => BigInt(s) }
    }

  private def unescapeString(s: String): String = {
    val lastIdx = s.length - 1
    if (lastIdx >= 1 && s.charAt(lastIdx) != '"') return s
    val sb = new java.lang.StringBuilder(lastIdx - 1)
    var i  = 1
    while (i < lastIdx) {
      var c = s.charAt(i)
      i += 1
      if (c == '\\') {
        if (i < lastIdx) {
          (s.charAt(i): @switch) match {
            case '"' =>
              c = '"'
              i += 1
            case '\\' =>
              c = '\\'
              i += 1
            case 'n' =>
              c = '\n'
              i += 1
            case 'r' =>
              c = '\r'
              i += 1
            case 't' =>
              c = '\t'
              i += 1
            case _ =>
              if (strict) decodeError(s"Invalid escape: \\${s.charAt(i)}")
          }
        } else if (strict) decodeError("Invalid escape: \\")
      }
      sb.append(c)
    }
    sb.toString
  }
}

object ToonReader {
  private val pool: ThreadLocal[ToonReader] = new ThreadLocal[ToonReader] {
    override def initialValue(): ToonReader =
      new ToonReader(2, Delimiter.Comma, strict = false, PathExpansion.Off, None)
  }

  /**
   * Gets a ToonReader from the pool, or creates a new one if the pooled reader
   * is in use.
   */
  def apply(config: ReaderConfig): ToonReader = {
    val reader = pool.get()
    if (reader.isInUse) {
      new ToonReader(config.indent, config.delimiter, config.strict, config.expandPaths, config.discriminatorField)
    } else {
      reader.startUse(config)
      reader
    }
  }

  /**
   * Creates a fresh ToonReader that is NOT from the pool.
   */
  def fresh(config: ReaderConfig): ToonReader =
    new ToonReader(config.indent, config.delimiter, config.strict, config.expandPaths, config.discriminatorField)

  def read[A](codec: ToonBinaryCodec[A], input: ByteBuffer, config: ReaderConfig): A = {
    val reader             = apply(config)
    var bytes: Array[Byte] = null
    var offset             = input.position()
    val length             = input.remaining()
    if (input.hasArray) {
      bytes = input.array()
      offset = input.arrayOffset() + offset
    } else {
      bytes = new Array[Byte](length)
      input.get(bytes)
      offset = 0
    }
    reader.reset(bytes, offset, length)
    try codec.decodeValue(reader, codec.nullValue)
    finally reader.endUse()
  }
}

final case class ArrayHeader(
  key: String,
  length: Int,
  fields: Array[String],
  delimiter: Delimiter
)

private[toon] class ToonBinaryCodecError(var spans: List[zio.blocks.schema.DynamicOptic.Node], message: String)
    extends Throwable(message, null, false, false) {
  override def getMessage: String = message
}
