package zio.blocks.schema.toon

import zio.blocks.schema.{DynamicOptic, SchemaError}
import java.nio.charset.StandardCharsets

/**
 * A streaming reader for the TOON format.
 *
 * @param buf
 *   The input byte buffer
 * @param charBuf
 *   A reusable character buffer for string decoding
 * @param limit
 *   The size of the input buffer
 * @param config
 *   The reader configuration
 */
final class ToonReader(
  val buf: Array[Byte],
  val charBuf: Array[Char],
  val limit: Int,
  val config: ToonReaderConfig
) {
  var ptr: Int = 0

  /**
   * Throws a SchemaError indicating a decoding failure.
   *
   * @param msg
   *   The error message.
   * @return
   *   Nothing, as it always throws an exception.
   */
  def decodeError(msg: String): Nothing = {
    val single = new SchemaError.ExpectationMismatch(DynamicOptic.root, msg)
    throw new SchemaError(new ::(single, Nil))
  }

  def skipWhitespace(): Unit =
    while (ptr < limit && isWhitespace(buf(ptr))) {
      ptr += 1
    }

  def skipIndent(): Unit =
    while (ptr < limit && buf(ptr) == ' ') {
      ptr += 1
    }

  def isWhitespace(b: Byte): Boolean =
    b == ' ' || b == '\t' || b == '\n' || b == '\r'

  def isNewline(b: Byte): Boolean = b == '\n' || b == '\r'

  def measureIndent(): Int = {
    var i     = ptr
    var count = 0
    while (i < limit && buf(i) == ' ') {
      count += 1
      i += 1
    }
    count
  }

  def isDelimiter(b: Byte): Boolean =
    b == ',' || b == ':' || b == '\n' || b == '\r' || b == '[' || b == ']' || b == '{' || b == '}'

  def readString(): String = {
    skipWhitespace()
    if (ptr >= limit) decodeError("Unexpected end of input")
    if (buf(ptr) == '"') readQuotedString() else readUnquotedString()
  }

  def readQuotedString(): String = {
    ptr += 1 // Skip opening quote
    val sb      = new java.lang.StringBuilder()
    var escaped = false

    while (ptr < limit) {
      val b = buf(ptr)
      if (escaped) {
        val char = b match {
          case 'n'  => '\n'
          case 'r'  => '\r'
          case 't'  => '\t'
          case '"'  => '"'
          case '\\' => '\\'
          case _    => decodeError(s"Invalid escape sequence \\${b.toChar}")
        }
        sb.append(char)
        escaped = false
        ptr += 1
      } else if (b == '\\') {
        escaped = true
        ptr += 1
      } else if (b == '"') {
        ptr += 1 // Skip closing quote
        return sb.toString
      } else {
        // Handle UTF-8 multi-byte characters
        if ((b & 0x80) == 0) {
          sb.append(b.toChar)
          ptr += 1
        } else {
          val c = if ((b & 0x80) == 0) {
            ptr += 1
            b.toChar
          } else if ((b & 0xe0) == 0xc0) {
            if (ptr + 1 >= limit) decodeError("Truncated UTF-8")
            val b2 = buf(ptr + 1)
            ptr += 2
            (((b & 0x1f) << 6) | (b2 & 0x3f)).toChar
          } else if ((b & 0xf0) == 0xe0) {
            if (ptr + 2 >= limit) decodeError("Truncated UTF-8")
            val b2 = buf(ptr + 1)
            val b3 = buf(ptr + 2)
            ptr += 3
            (((b & 0x0f) << 12) | ((b2 & 0x3f) << 6) | (b3 & 0x3f)).toChar
          } else if ((b & 0xf8) == 0xf0) {
            // 4 bytes -> surrogate pair in Java Strings (UTF-16)
            if (ptr + 3 >= limit) decodeError("Truncated UTF-8")
            val b2 = buf(ptr + 1)
            val b3 = buf(ptr + 2)
            val b4 = buf(ptr + 3)
            ptr += 4
            val cp = ((b & 0x07) << 18) | ((b2 & 0x3f) << 12) | ((b3 & 0x3f) << 6) | (b4 & 0x3f)
            sb.appendCodePoint(cp)
            '\u0000' // Marker to skip append
          } else {
            decodeError("Invalid UTF-8 start byte")
          }

          if (c != '\u0000') sb.append(c)
        }
      }
    }
    decodeError("Unterminated quoted string")
  }

  def readUnquotedString(): String = {
    val start = ptr
    while (ptr < limit && !isDelimiter(buf(ptr))) {
      ptr += 1
    }
    new String(buf, start, ptr - start, StandardCharsets.UTF_8)
  }

  def readInt(): Int = {
    val s = readString()
    try { s.toInt }
    catch { case _: NumberFormatException => decodeError(s"Invalid integer: $s") }
  }

  def readBoolean(): Boolean = {
    val s = readString()
    if (s == "true") true else if (s == "false") false else decodeError(s"Invalid boolean: $s")
  }

  var lastArraySize: Int        = -1
  var lastArrayTabular: Boolean = false

  // Consumes expected key and returns true if found
  def expectKey(key: String): Unit = {
    skipWhitespace()
    val s = if (ptr < limit && buf(ptr) == '"') readQuotedString() else readUnquotedString()
    if (s != key) decodeError(s"Expected key '$key', got '$s'")

    // Check for array suffix [N]
    // Also check for tabular suffix {keys}
    // We skip them for now but capture N

    skipWhitespace()
    if (ptr < limit && buf(ptr) == '[') {
      ptr += 1 // Skip [
      val start = ptr
      while (ptr < limit && buf(ptr) != ']') ptr += 1
      val sizeStr = new String(buf, start, ptr - start, StandardCharsets.UTF_8)
      try { lastArraySize = sizeStr.toInt }
      catch { case _: NumberFormatException => decodeError(s"Invalid array size: $sizeStr") }
      if (ptr < limit) ptr += 1 else decodeError("Expected ']'")
    } else {
      lastArraySize = -1
    }

    skipWhitespace()
    if (ptr < limit && buf(ptr) == '{') {
      // Tabular keys detected
      lastArrayTabular = true
      ptr += 1
      while (ptr < limit && buf(ptr) != '}') ptr += 1
      if (ptr < limit) ptr += 1 else decodeError("Expected '}'")
    } else {
      lastArrayTabular = false
    }

    skipWhitespace()
    if (ptr < limit && buf(ptr) == ':') ptr += 1
    else decodeError(s"Expected ':' after key '$key', got '${if (ptr < limit) buf(ptr).toChar else "EOF"}' at $ptr")
  }

  def readArrayHeader(): Unit = {
    skipWhitespace()
    if (ptr < limit && buf(ptr) == '[') {
      ptr += 1 // Skip [
      val start = ptr
      while (ptr < limit && buf(ptr) != ']') ptr += 1
      val sizeStr = new String(buf, start, ptr - start, StandardCharsets.UTF_8)
      try { lastArraySize = sizeStr.toInt }
      catch { case _: NumberFormatException => decodeError(s"Invalid array size: $sizeStr") }
      if (ptr < limit) ptr += 1 else decodeError("Expected ']'")
    } else {
      lastArraySize = -1 // No header found
      return             // Optional header missing, potentially inline list without header (though spec implies header usually present for safe arrays)
    }

    skipWhitespace()
    if (ptr < limit && buf(ptr) == '{') {
      // Tabular keys detected
      lastArrayTabular = true
      ptr += 1
      while (ptr < limit && buf(ptr) != '}') ptr += 1
      if (ptr < limit) ptr += 1 else decodeError("Expected '}'")
    } else {
      lastArrayTabular = false
    }

    skipWhitespace()
    if (ptr < limit && buf(ptr) == ':') ptr += 1
    else decodeError(s"Expected ':' after array header, got '${if (ptr < limit) buf(ptr).toChar else "EOF"}' at $ptr")
  }

  def readKey(): String = {
    skipWhitespace()
    val key = if (ptr < limit && buf(ptr) == '"') readQuotedString() else readUnquotedString()
    skipWhitespace()
    if (ptr < limit && buf(ptr) == ':') ptr += 1
    else
      decodeError(
        s"readKey: Expected ':' after key '$key', got '${if (ptr < limit) buf(ptr).toChar else "EOF"}' at $ptr"
      )
    key
  }

  def peek(): Byte = if (ptr < limit) buf(ptr) else 0

  def peekNextSignificant(): Byte = {
    var i = ptr
    while (i < limit && isWhitespace(buf(i))) i += 1
    if (i < limit) buf(i) else 0
  }

  /**
   * Validates that the array has ended as expected. Useful when
   * strictArrayLength is true.
   */
  def validateArrayEnd(): Unit =
    if (config.strictArrayLength) {
      val next = peekNextSignificant()
      if (next == ',' || next == '-') {
        decodeError("Strict array validation failed: Found extra elements or delimiters after expected array size")
      }
    }
}
