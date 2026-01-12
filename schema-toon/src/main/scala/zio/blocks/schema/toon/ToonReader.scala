package zio.blocks.schema.toon

import java.nio.charset.StandardCharsets.UTF_8

/**
 * A reader for deserializing TOON (Token-Oriented Object Notation) format.
 *
 * Handles:
 * - Primitives (strings, numbers, booleans, null)
 * - Records with indented key-value pairs
 * - Arrays in inline format: `[N]: a,b,c`
 * - ADTs with Key discriminator: `TypeName:` followed by indented content
 */
final class ToonReader private (input: String) {
  private[this] var pos   = 0
  private[this] val limit = input.length

  /** Returns current position for error reporting. */
  def position: Int = pos

  /** Returns true if there's more input. */
  def hasRemaining: Boolean = pos < limit

  /** Peek at current character without consuming. */
  def peek: Char = if (pos < limit) input.charAt(pos) else '\u0000'

  /** Skip whitespace (spaces, not newlines). */
  def skipSpaces(): Unit =
    while (pos < limit && input.charAt(pos) == ' ') pos += 1

  /** Skip to end of line or EOF. */
  def skipToEndOfLine(): Unit =
    while (pos < limit && input.charAt(pos) != '\n') pos += 1

  /** Skip a single newline if present. */
  def skipNewline(): Boolean =
    if (pos < limit && input.charAt(pos) == '\n') {
      pos += 1
      true
    } else false

  /** Read indentation level (count of leading spaces / 2). */
  def readIndentation(): Int = {
    var spaces = 0
    while (pos < limit && input.charAt(pos) == ' ') {
      spaces += 1
      pos += 1
    }
    spaces / 2
  }

  /** Read a key (up to `:` or `[`). Returns None if at end or no key. */
  def readKey(): Option[String] = {
    skipSpaces()
    if (!hasRemaining || peek == '\n') return None

    val start = pos
    // Key ends at `:` or `[` (for array header)
    while (pos < limit && input.charAt(pos) != ':' && input.charAt(pos) != '[' && input.charAt(pos) != '\n')
      pos += 1

    if (pos == start) None
    else {
      val key = input.substring(start, pos).trim
      if (key.isEmpty) None else Some(unquote(key))
    }
  }

  /** Consume expected character, throw if not found. */
  def expect(c: Char): Unit =
    if (pos < limit && input.charAt(pos) == c) pos += 1
    else throw new ToonReaderError(s"Expected '$c' at position $pos")

  /** Try to consume expected character, return true if found. */
  def tryConsume(c: Char): Boolean =
    if (pos < limit && input.charAt(pos) == c) {
      pos += 1
      true
    } else false

  /** Read a string value (until delimiter, newline, or end). */
  def readStringValue(delimiter: Char = ','): String = {
    skipSpaces()
    if (!hasRemaining || peek == '\n') return ""

    // Handle quoted strings
    if (peek == '"') {
      pos += 1
      val sb = new StringBuilder
      while (pos < limit && input.charAt(pos) != '"') {
        val c = input.charAt(pos)
        if (c == '\\' && pos + 1 < limit) {
          pos += 1
          val escaped = input.charAt(pos)
          escaped match {
            case 'n'  => sb.append('\n')
            case 'r'  => sb.append('\r')
            case 't'  => sb.append('\t')
            case '"'  => sb.append('"')
            case '\\' => sb.append('\\')
            case _    => sb.append(escaped)
          }
        } else {
          sb.append(c)
        }
        pos += 1
      }
      if (pos < limit) pos += 1 // consume closing quote
      sb.toString
    } else {
      // Unquoted string
      val start = pos
      while (pos < limit && input.charAt(pos) != delimiter && input.charAt(pos) != '\n')
        pos += 1
      input.substring(start, pos).trim
    }
  }

  /** Read the value part after `key: ` */
  def readInlineValue(): String = {
    skipSpaces()
    readStringValue()
  }

  /** Read array header like `[N]: ` and return N. */
  def readArrayHeader(): Int = {
    expect('[')
    val start = pos
    while (pos < limit && input.charAt(pos) != ']') pos += 1
    val size = input.substring(start, pos).toInt
    expect(']')
    expect(':')
    skipSpaces()
    size
  }

  /** Read a discriminator name (ends with `:`). */
  def readDiscriminator(): Option[String] = {
    skipSpaces()
    val start = pos
    while (pos < limit && input.charAt(pos) != ':' && input.charAt(pos) != '\n')
      pos += 1

    if (pos > start && pos < limit && input.charAt(pos) == ':') {
      val name = input.substring(start, pos).trim
      pos += 1 // consume ':'
      Some(unquote(name))
    } else {
      pos = start
      None
    }
  }

  /** Parse boolean value. */
  def parseBoolean(): Boolean =
    readStringValue() match {
      case "true"  => true
      case "false" => false
      case s       => throw new ToonReaderError(s"Expected boolean, got: $s")
    }

  /** Parse int value. */
  def parseInt(): Int =
    try readStringValue().toInt
    catch { case _: NumberFormatException => throw new ToonReaderError(s"Expected int at position $pos") }

  /** Parse long value. */
  def parseLong(): Long =
    try readStringValue().toLong
    catch { case _: NumberFormatException => throw new ToonReaderError(s"Expected long at position $pos") }

  /** Parse float value. */
  def parseFloat(): Float = {
    val s = readStringValue()
    if (s == "null") Float.NaN
    else try s.toFloat
    catch { case _: NumberFormatException => throw new ToonReaderError(s"Expected float at position $pos") }
  }

  /** Parse double value. */
  def parseDouble(): Double = {
    val s = readStringValue()
    if (s == "null") Double.NaN
    else try s.toDouble
    catch { case _: NumberFormatException => throw new ToonReaderError(s"Expected double at position $pos") }
  }

  /** Unquote a string if it's quoted. */
  private def unquote(s: String): String =
    if (s.length >= 2 && s.charAt(0) == '"' && s.charAt(s.length - 1) == '"')
      s.substring(1, s.length - 1)
    else s
}

object ToonReader {
  /** Create a new ToonReader from string. */
  def apply(input: String): ToonReader = new ToonReader(input)

  /** Create a new ToonReader from UTF-8 bytes. */
  def fromBytes(bytes: Array[Byte]): ToonReader = new ToonReader(new String(bytes, UTF_8))

  /** Create a new ToonReader from UTF-8 bytes with offset and length. */
  def fromBytes(bytes: Array[Byte], offset: Int, length: Int): ToonReader =
    new ToonReader(new String(bytes, offset, length, UTF_8))
}

/** Error thrown during TOON parsing. */
class ToonReaderError(message: String) extends Exception(message)
