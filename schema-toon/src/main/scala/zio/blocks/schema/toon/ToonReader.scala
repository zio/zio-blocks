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

  private[this] var data: String               = ""
  private[this] var lineIndex: Int             = 0
  private[this] var lineStart: Int             = 0
  private[this] var lineEnd: Int               = 0
  private[this] var currentLine: String        = ""
  private[this] var currentDepth: Int          = 0
  private[this] var linePos: Int               = 0
  private[this] var activeDelimiter: Delimiter = delimiter
  private[this] var inUse: Boolean             = false
  private[this] var inlineContext: Boolean     = false

  private[this] var markedLineIndex: Int      = -1
  private[this] var markedLineStart: Int      = -1
  private[this] var markedLineEnd: Int        = -1
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
    data = ""
  }

  def reset(bytes: Array[Byte], offset: Int, length: Int): Unit = reset(new String(bytes, offset, length, UTF_8))

  def reset(content: String): Unit = {
    data = content
    lineStart = 0
    lineEnd = data.indexOf('\n')
    if (lineEnd == -1) lineEnd = data.length
    lineIndex = 0
    inlineContext = false
    if (data.length > 0) {
      currentLine = data.substring(lineStart, lineEnd)
      currentDepth = computeDepth(currentLine)
      linePos = currentDepth * indentSize
    } else {
      currentLine = ""
      currentDepth = 0
      linePos = 0
    }
  }

  def hasMoreLines: Boolean = lineStart <= data.length

  def hasMoreContent: Boolean = linePos < currentLine.length

  def getDepth: Int = currentDepth

  def setActiveDelimiter(d: Delimiter): Unit = activeDelimiter = d

  def getCurrentLine: String = currentLine

  def peekTrimmedContent: String = currentLine.substring(linePos).trim

  def advanceLine(): Unit =
    if (lineEnd < data.length) {
      lineStart = lineEnd + 1
      lineEnd = data.indexOf('\n', lineStart)
      if (lineEnd == -1) lineEnd = data.length
      lineIndex += 1
      currentLine = data.substring(lineStart, lineEnd)
      currentDepth = computeDepth(currentLine)
      linePos = currentDepth * indentSize
    } else {
      lineStart = data.length + 1
      lineEnd = data.length
      currentLine = ""
      currentDepth = 0
      linePos = 0
    }

  def skipBlankLines(): Unit =
    while (hasMoreLines && isLineBlank(lineStart, lineEnd)) {
      advanceLine()
    }

  def skipBlankLinesInArray(isFirstItem: Boolean): Unit =
    while (hasMoreLines && isLineBlank(lineStart, lineEnd)) {
      if (strict && !isFirstItem)
        decodeError("Blank lines are not allowed inside arrays/tabular blocks in strict mode")
      advanceLine()
    }

  private def isLineBlank(start: Int, end: Int): Boolean = {
    var i = start
    while (i < end && data.charAt(i) <= ' ') i += 1
    i == end
  }

  /**
   * Sets a mark at the current position. The reader can later be rolled back to
   * this position using `rollbackToMark()`.
   */
  def setMark(): Unit = {
    markedLineIndex = lineIndex
    markedLineStart = lineStart
    markedLineEnd = lineEnd
    markedLinePos = linePos
    markedCurrentDepth = currentDepth
    markedCurrentLine = currentLine
  }

  def rollbackToMark(): Unit =
    if (markedLineStart >= 0) {
      lineIndex = markedLineIndex
      lineStart = markedLineStart
      lineEnd = markedLineEnd
      linePos = markedLinePos
      currentDepth = markedCurrentDepth
      currentLine = markedCurrentLine
    }

  def resetMark(): Unit = {
    markedLineIndex = -1
    markedLineStart = -1
    markedLineEnd = -1
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
    skipWhitespace()
    if (linePos >= currentLine.length) {
      if (lineEnd < data.length) {
        advanceLine()
        readBoolean()
      } else fallbackReadBoolean()
    } else {
      if (currentLine.startsWith("true", linePos)) {
        val next = linePos + 4
        if (isTokenEnd(next)) {
          linePos = next
          if (inlineContext) skipTrailingDelimiter() else advanceLine()
          true
        } else fallbackReadBoolean()
      } else if (currentLine.startsWith("false", linePos)) {
        val next = linePos + 5
        if (isTokenEnd(next)) {
          linePos = next
          if (inlineContext) skipTrailingDelimiter() else advanceLine()
          false
        } else fallbackReadBoolean()
      } else fallbackReadBoolean()
    }
  }

  private def fallbackReadBoolean(): Boolean = {
    val value = readPrimitiveToken()
    if (value == "true") true
    else if (value == "false") false
    else decodeError(s"Expected boolean, got: $value")
  }

  private def isTokenEnd(pos: Int): Boolean =
    pos >= currentLine.length || (inlineContext && currentLine.charAt(pos) == activeDelimiter.char) || currentLine
      .charAt(pos) <= ' '

  private def skipTrailingDelimiter(): Unit = {
    skipWhitespace()
    if (linePos < currentLine.length && currentLine.charAt(linePos) == activeDelimiter.char) {
      linePos += 1
    }
  }

  def readByte(): Byte = {
    skipWhitespace()
    if (linePos >= currentLine.length) {
      if (lineEnd < data.length) {
        advanceLine()
        readByte()
      } else fallbackReadByte()
    } else {
      var i   = linePos
      val len = currentLine.length
      var n   = 0
      var neg = false
      if (currentLine.charAt(i) == '-') {
        neg = true
        i += 1
      }
      val start = i
      while (i < len && { val c = currentLine.charAt(i); c >= '0' && c <= '9' }) {
        n = n * 10 + (currentLine.charAt(i) - '0')
        if (n > 128) return fallbackReadByte()
        i += 1
      }
      if (i == start || (i < len && { val c = currentLine.charAt(i); c == '.' || c == 'e' || c == 'E' }))
        return fallbackReadByte()

      if (!inlineContext) {
        var j = i
        while (j < len && currentLine.charAt(j) == ' ') j += 1
        if (j < len) return fallbackReadByte()
      }

      if ((neg && n > 128) || (!neg && n > 127)) return fallbackReadByte()

      linePos = i
      if (inlineContext) {
        skipWhitespace()
        if (linePos < len && currentLine.charAt(linePos) == activeDelimiter.char) linePos += 1
      } else advanceLine()

      if (neg) (-n).toByte else n.toByte
    }
  }

  private def fallbackReadByte(): Byte = {
    val value = readPrimitiveToken()
    try value.toByte
    catch { case _: NumberFormatException => decodeError(s"Expected byte, got: $value") }
  }

  def readShort(): Short = {
    skipWhitespace()
    if (linePos >= currentLine.length) {
      if (lineEnd < data.length) {
        advanceLine()
        readShort()
      } else fallbackReadShort()
    } else {
      var i   = linePos
      val len = currentLine.length
      var n   = 0
      var neg = false
      if (currentLine.charAt(i) == '-') {
        neg = true
        i += 1
      }
      val start = i
      while (i < len && { val c = currentLine.charAt(i); c >= '0' && c <= '9' }) {
        n = n * 10 + (currentLine.charAt(i) - '0')
        if (n > 32768) return fallbackReadShort()
        i += 1
      }
      if (i == start || (i < len && { val c = currentLine.charAt(i); c == '.' || c == 'e' || c == 'E' }))
        return fallbackReadShort()

      if (!inlineContext) {
        var j = i
        while (j < len && currentLine.charAt(j) == ' ') j += 1
        if (j < len) return fallbackReadShort()
      }

      if ((neg && n > 32768) || (!neg && n > 32767)) return fallbackReadShort()

      linePos = i
      if (inlineContext) {
        skipWhitespace()
        if (linePos < len && currentLine.charAt(linePos) == activeDelimiter.char) linePos += 1
      } else advanceLine()

      if (neg) (-n).toShort else n.toShort
    }
  }

  private def fallbackReadShort(): Short = {
    val value = readPrimitiveToken()
    try value.toShort
    catch { case _: NumberFormatException => decodeError(s"Expected short, got: $value") }
  }

  def readInt(): Int = {
    skipWhitespace()
    if (linePos >= currentLine.length) {
      if (lineEnd < data.length) {
        advanceLine()
        readInt()
      } else fallbackReadInt()
    } else {
      var i   = linePos
      val len = currentLine.length
      var n   = 0L
      var neg = false
      if (currentLine.charAt(i) == '-') {
        neg = true
        i += 1
      }
      val start = i
      while (i < len && { val c = currentLine.charAt(i); c >= '0' && c <= '9' }) {
        n = n * 10 + (currentLine.charAt(i) - '0')
        if (n > 2147483648L) return fallbackReadInt()
        i += 1
      }
      if (i == start || (i < len && { val c = currentLine.charAt(i); c == '.' || c == 'e' || c == 'E' }))
        return fallbackReadInt()

      if (!inlineContext) {
        var j = i
        while (j < len && currentLine.charAt(j) == ' ') j += 1
        if (j < len) return fallbackReadInt()
      }

      if ((neg && n > 2147483648L) || (!neg && n > 2147483647L)) return fallbackReadInt()

      linePos = i
      if (inlineContext) {
        skipWhitespace()
        if (linePos < len && currentLine.charAt(linePos) == activeDelimiter.char) linePos += 1
      } else advanceLine()

      if (neg) -n.toInt else n.toInt
    }
  }

  private def fallbackReadInt(): Int = {
    val value = readPrimitiveToken()
    try value.toInt
    catch { case _: NumberFormatException => decodeError(s"Expected int, got: $value") }
  }

  def readLong(): Long = {
    skipWhitespace()
    if (linePos >= currentLine.length) {
      if (lineEnd < data.length) {
        advanceLine()
        readLong()
      } else fallbackReadLong()
    } else {
      var i   = linePos
      val len = currentLine.length
      var n   = 0L
      var neg = false
      if (currentLine.charAt(i) == '-') {
        neg = true
        i += 1
      }
      val start = i
      while (i < len && { val c = currentLine.charAt(i); c >= '0' && c <= '9' }) {
        val d = currentLine.charAt(i) - '0'
        if (n > 922337203685477580L || (n == 922337203685477580L && d > 8)) return fallbackReadLong()
        n = n * 10 + d
        i += 1
      }
      if (i == start || (i < len && { val c = currentLine.charAt(i); c == '.' || c == 'e' || c == 'E' }))
        return fallbackReadLong()

      if (!inlineContext) {
        var j = i
        while (j < len && currentLine.charAt(j) == ' ') j += 1
        if (j < len) return fallbackReadLong()
      }

      if (!neg && n < 0) return fallbackReadLong() // Overflowed Long.MaxValue

      linePos = i
      if (inlineContext) {
        skipWhitespace()
        if (linePos < len && currentLine.charAt(linePos) == activeDelimiter.char) linePos += 1
      } else advanceLine()

      if (neg) -n else n
    }
  }

  private def fallbackReadLong(): Long = {
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

    var start = linePos
    var end   = colonIdx
    while (start < end && currentLine.charAt(start) == ' ') start += 1
    while (end > start && currentLine.charAt(end - 1) == ' ') end -= 1

    val key = if (start < end && currentLine.charAt(start) == '"') {
      unescapeString(currentLine.substring(start, end))
    } else {
      currentLine.substring(start, end)
    }
    linePos = colonIdx + 1
    skipWhitespace()
    key
  }

  def readKeyWithQuoteInfo(): (String, Boolean) = {
    skipWhitespace()
    val colonIdx = findUnquotedColon(currentLine, linePos)
    if (colonIdx < 0) decodeError("Expected key:value, no colon found")

    var start = linePos
    var end   = colonIdx
    while (start < end && currentLine.charAt(start) == ' ') start += 1
    while (end > start && currentLine.charAt(end - 1) == ' ') end -= 1

    val wasQuoted = start < end && currentLine.charAt(start) == '"'
    val key       = if (wasQuoted) {
      unescapeString(currentLine.substring(start, end))
    } else {
      currentLine.substring(start, end)
    }
    linePos = colonIdx + 1
    skipWhitespace()
    (key, wasQuoted)
  }

  def readKeyWithArrayNotation(): String = {
    skipWhitespace()
    val colonIdx = findUnquotedColon(currentLine, linePos)
    if (colonIdx < 0) decodeError("Expected key:value, no colon found")

    var start = linePos
    var end   = colonIdx
    while (start < end && currentLine.charAt(start) == ' ') start += 1
    while (end > start && currentLine.charAt(end - 1) == ' ') end -= 1

    val key = if (start < end && currentLine.charAt(start) == '"') {
      unescapeString(currentLine.substring(start, end))
    } else {
      currentLine.substring(start, end)
    }
    linePos = colonIdx + 1
    skipWhitespace()
    key
  }

  def parseArrayHeader(): ArrayHeader = {
    skipWhitespace()
    val content = currentLine.substring(linePos)

    val bracketStart = findUnquotedChar(content, '[')
    if (bracketStart < 0) decodeError("Expected array header with [")

    val key = if (bracketStart > 0) {
      val keyPart = content.substring(0, bracketStart).trim
      if (keyPart.startsWith("\"")) unescapeString(keyPart) else keyPart
    } else null

    val bracketEnd = content.indexOf(']', bracketStart)
    if (bracketEnd < 0) decodeError("Expected closing ] in array header")

    val bracketContent  = content.substring(bracketStart + 1, bracketEnd)
    val (length, delim) = parseBracketContent(bracketContent)

    var fields: Array[String] = null
    var afterBracket          = bracketEnd + 1

    if (afterBracket < content.length && content.charAt(afterBracket) == '{') {
      val braceEnd = content.indexOf('}', afterBracket)
      if (braceEnd < 0) decodeError("Expected closing } in field list")
      val fieldsContent = content.substring(afterBracket + 1, braceEnd)
      fields = parseFieldList(fieldsContent, delim)
      afterBracket = braceEnd + 1
    }

    if (afterBracket >= content.length || content.charAt(afterBracket) != ':')
      decodeError("Expected : after array header")

    activeDelimiter = delim
    advanceLine()

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
    if (linePos >= currentLine.length) {
      if (lineEnd < data.length) {
        advanceLine()
        return readPrimitiveToken()
      } else {
        advanceLine()
        return ""
      }
    }

    val start = linePos
    var end   = currentLine.length
    while (end > start && currentLine.charAt(end - 1) <= ' ') end -= 1

    if (start == end) {
      advanceLine()
      return ""
    }

    if (currentLine.charAt(start) == '"') {
      val endQuote = findEndQuote(currentLine, start + 1)
      if (endQuote < 0) decodeError("Unterminated string")
      val quoted = currentLine.substring(start, endQuote + 1)
      linePos = currentLine.length
      unescapeString(quoted)
    } else {
      val tokenEnd = if (inlineContext) {
        val delimIdx = findDelimiterIndex(currentLine, start, end, activeDelimiter)
        if (delimIdx >= 0) {
          var tEnd = delimIdx
          while (tEnd > start && currentLine.charAt(tEnd - 1) <= ' ') tEnd -= 1
          tEnd
        } else end
      } else end

      val token = currentLine.substring(start, tokenEnd)
      linePos = currentLine.length
      advanceLine()
      token
    }
  }

  private def findDelimiterIndex(s: String, start: Int, end: Int, delim: Delimiter): Int = {
    var inQuote = false
    var i       = start
    while (i < end) {
      val c = s.charAt(i)
      if (c == '"' && !isEscaped(s, i)) inQuote = !inQuote
      else if (!inQuote && c == delim.char) return i
      i += 1
    }
    -1
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
    if (strict && indentSize > 0 && spaces % indentSize != 0 && line.trim.nonEmpty)
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
        result += trimUnquoted(s.substring(start, i))
        start = i + 1
      }
      i += 1
    }
    if (start <= s.length) result += trimUnquoted(s.substring(start))
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

  private def findUnquotedChar(s: String, target: Char): Int = {
    var inQuote = false
    var i       = 0
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
    if (s.contains(".") || s.contains("e") || s.contains("E")) {
      val d = s.toDouble
      if (d == d.toLong && d >= Long.MinValue && d <= Long.MaxValue) d.toLong
      else d
    } else {
      try s.toLong
      catch { case _: NumberFormatException => BigInt(s) }
    }

  private def unescapeString(s: String): String = {
    if (!s.startsWith("\"") || !s.endsWith("\"")) return s
    val inner = s.substring(1, s.length - 1)
    val sb    = new StringBuilder(inner.length)
    var i     = 0
    while (i < inner.length) {
      val c = inner.charAt(i)
      if (c == '\\' && i + 1 < inner.length) {
        (inner.charAt(i + 1): @switch) match {
          case '"'  => sb.append('"'); i += 2
          case '\\' => sb.append('\\'); i += 2
          case 'n'  => sb.append('\n'); i += 2
          case 'r'  => sb.append('\r'); i += 2
          case 't'  => sb.append('\t'); i += 2
          case _    =>
            if (strict) decodeError(s"Invalid escape: \\${inner.charAt(i + 1)}")
            else { sb.append(c); i += 1 }
        }
      } else {
        sb.append(c)
        i += 1
      }
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
