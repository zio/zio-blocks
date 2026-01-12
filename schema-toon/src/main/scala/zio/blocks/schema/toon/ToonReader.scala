package zio.blocks.schema.toon

import java.io.{ByteArrayInputStream, InputStream}
import java.nio.charset.StandardCharsets

final class ToonReader(
  config: ToonReaderConfig,
  in: InputStream
) {
  // Simple buffer for lookahead
  private[this] var buffer: Int        = -1
  private[this] var line: Int          = 1
  private[this] var col: Int           = 0
  private[this] var currentIndent: Int = 0

  private def nextChar(): Int =
    if (buffer != -1) {
      val c = buffer
      buffer = -1
      c
    } else {
      val c = in.read()
      if (c != -1) {
        if (c == '\n') {
          line += 1
          col = 0
        } else {
          col += 1
        }
      }
      c
    }

  /**
   * Peek at the next character without consuming it.
   */
  def peek(): Char = {
    if (buffer == -1) {
      buffer = in.read()
    }
    if (buffer == -1) 0.toChar else buffer.toChar
  }

  /**
   * Returns the current line number (1-indexed).
   */
  def getLine: Int = line

  /**
   * Returns the current column number (0-indexed).
   */
  def getColumn: Int = col

  /**
   * Returns the current indentation level (in terms of indent units, not
   * spaces).
   */
  def getCurrentIndentLevel: Int = currentIndent

  /**
   * Reads a key (field name) from the input.
   */
  def readKey(): String = {
    skipWhitespace()
    readString()
  }

  /**
   * Reads a string value (quoted or unquoted).
   */
  def readString(): String = {
    skipWhitespace()
    val c = peek()
    if (c == '"') {
      nextChar() // consume opening quote
      val sb   = new StringBuilder
      var done = false
      while (!done) {
        val n = nextChar()
        if (n == -1) throw ToonCodecError.atLine(line, s"Unexpected EOF inside string")
        if (n == '"') {
          done = true
        } else if (n == '\\') {
          val escaped = nextChar()
          escaped match {
            case '"'  => sb.append('"')
            case '\\' => sb.append('\\')
            case 'b'  => sb.append('\b')
            case 'f'  => sb.append('\f')
            case 'n'  => sb.append('\n')
            case 'r'  => sb.append('\r')
            case 't'  => sb.append('\t')
            case 'u'  =>
              // Unicode handling
              val hex = new String(Array(nextChar().toChar, nextChar().toChar, nextChar().toChar, nextChar().toChar))
              sb.append(Integer.parseInt(hex, 16).toChar)
            case c => sb.append(c.toChar)
          }
        } else {
          sb.append(n.toChar)
        }
      }
      sb.toString()
    } else {
      // Read unquoted string (alphanumeric and common chars)
      val sb   = new StringBuilder
      var done = false
      while (!done) {
        val n = peek()
        if (n.isLetterOrDigit || n == '_' || n == '-' || n == '.') {
          sb.append(nextChar().toChar)
        } else {
          done = true
        }
      }
      sb.toString()
    }
  }

  /**
   * Reads an integer value.
   */
  def readInt(): Int = {
    skipWhitespace()
    val sb    = new StringBuilder
    var first = true
    var done  = false
    while (!done) {
      val n = peek()
      if (n.isDigit || (first && n == '-')) {
        sb.append(nextChar().toChar)
        first = false
      } else {
        done = true
      }
    }
    sb.toString().toInt
  }

  /**
   * Reads a boolean value.
   */
  def readBoolean(): Boolean = {
    skipWhitespace()
    val s = readString()
    s.toLowerCase match {
      case "true"  => true
      case "false" => false
      case _       => throw ToonCodecError.atLine(line, s"Invalid boolean value: $s")
    }
  }

  /**
   * Skips leading spaces and returns the number of spaces skipped. Updates
   * currentIndent based on the configured indentSize.
   */
  def skipIndentation(): Int = {
    var spaces = 0
    var done   = false
    while (!done) {
      val c = peek()
      if (c == ' ') {
        spaces += 1
        nextChar()
      } else {
        done = true
      }
    }

    // Validate indentation is a multiple of indentSize if strict mode
    if (config.strictArrayLength && spaces % 2 != 0) {
      throw ToonCodecError.atLine(line, s"Invalid indentation: $spaces spaces is not a multiple of 2")
    }

    currentIndent = spaces / 2 // Assuming indentSize of 2
    spaces
  }

  /**
   * Expects and consumes a colon character.
   */
  def expectColon(): Unit = {
    skipWhitespace()
    val c = nextChar()
    if (c != ':') {
      throw ToonCodecError.atLine(line, s"Expected ':' but got '${c.toChar}'")
    }
  }

  /**
   * Skips to the next line.
   */
  def skipToNextLine(): Unit = {
    var done = false
    while (!done) {
      val c = nextChar()
      if (c == '\n' || c == -1) {
        done = true
      }
    }
  }

  /**
   * Returns true if at end of input.
   */
  def isEof: Boolean = peek() == 0.toChar

  /**
   * Reads a long value.
   */
  def readLong(): Long = {
    skipWhitespace()
    val sb    = new StringBuilder
    var first = true
    var done  = false
    while (!done) {
      val n = peek()
      if (n.isDigit || (first && n == '-')) {
        sb.append(nextChar().toChar)
        first = false
      } else {
        done = true
      }
    }
    sb.toString().toLong
  }

  /**
   * Reads a double value.
   */
  def readDouble(): Double = {
    skipWhitespace()
    val s = readString()
    if (s == "null") Double.NaN else s.toDouble
  }

  /**
   * Reads a float value.
   */
  def readFloat(): Float = {
    skipWhitespace()
    val s = readString()
    if (s == "null") Float.NaN else s.toFloat
  }

  /**
   * Skips a newline character if present.
   *
   * @return
   *   true if a newline was skipped, false otherwise
   */
  def skipNewline(): Boolean =
    if (peek() == '\n') {
      nextChar()
      true
    } else {
      false
    }

  /**
   * Peeks at the next non-whitespace character on the current line.
   */
  def peekNextNonWhitespace(): Char = {
    skipWhitespace()
    peek()
  }

  /**
   * Reads content until a newline or end of input.
   */
  def readUntilNewline(): String = {
    val sb   = new StringBuilder
    var done = false
    while (!done) {
      val c = peek()
      if (c == '\n' || c == 0.toChar) {
        done = true
      } else {
        sb.append(nextChar().toChar)
      }
    }
    sb.toString().trim
  }

  /**
   * Reads a value that might be on the same line or on the next line with
   * indentation. Returns the value as a string.
   */
  def readValue(): String = {
    skipWhitespace()
    val c = peek()
    if (c == '\n' || c == 0.toChar) {
      // Value is on next line(s) - this is a nested structure
      ""
    } else {
      readUntilNewline()
    }
  }

  /**
   * Checks if current position is at a list item marker (- ).
   */
  def isListItem(): Boolean =
    peek() == '-'

  /**
   * Skips a list item marker (- ) if present.
   *
   * @return
   *   true if a list marker was skipped
   */
  def skipListMarker(): Boolean =
    if (peek() == '-') {
      nextChar()
      if (peek() == ' ') {
        nextChar()
      }
      true
    } else {
      false
    }

  /**
   * Consumes the specified character if it matches the next character.
   * @return
   *   true if the character was consumed, false otherwise
   */
  def consume(expected: Char): Boolean = {
    skipWhitespace()
    if (peek() == expected) {
      nextChar()
      true
    } else {
      false
    }
  }

  /**
   * Reads a single value from a comma-separated inline list. Stops at comma,
   * newline, or EOF.
   */
  def readInlineValue(): String = {
    skipWhitespace()
    val sb   = new StringBuilder
    var done = false
    while (!done) {
      val c = peek()
      if (c == ',' || c == '\n' || c == 0.toChar || c == ']') {
        done = true
      } else {
        sb.append(nextChar().toChar)
      }
    }
    sb.toString().trim
  }

  /**
   * Skips an entire value, including nested structures. Used for skipping
   * unknown fields.
   */
  def skipValue(): Unit = {
    skipWhitespace()
    val c = peek()
    if (c == '\n') {
      // Nested structure - skip until we return to current or lower indent
      val baseIndent = currentIndent
      skipNewline()
      var done = false
      while (!done && !isEof) {
        val spaces = skipIndentation()
        if (currentIndent <= baseIndent && peek() != '\n') {
          // Back to base level or higher - we're done
          done = true
        } else {
          // Skip this line
          skipToNextLine()
        }
      }
    } else if (c == '[') {
      // Array - skip entire array
      nextChar() // consume [
      // Read until matching ]
      var depth = 1
      while (depth > 0 && !isEof) {
        val n = nextChar()
        if (n == '[') depth += 1
        else if (n == ']') depth -= 1
      }
      // Skip rest of line if inline, or skip block
      if (!consume(':')) {
        // Not an array header, just bracket content
      } else {
        skipValue() // Recurse for the array contents
      }
    } else {
      // Simple value on same line
      readUntilNewline()
    }
  }

  /**
   * Reads a character directly. Exposed for parsing control.
   */
  def readChar(): Int = nextChar()

  private def skipWhitespace(): Unit = {
    var done = false
    while (!done) {
      val c = peek()
      if (c.isWhitespace && c != '\n') { // Don't skip newlines implicitly
        nextChar()
      } else {
        done = true
      }
    }
  }
}

object ToonReader {

  /**
   * Creates a ToonReader from a String input.
   */
  def apply(input: String, config: ToonReaderConfig = ToonReaderConfig.default): ToonReader =
    new ToonReader(config, new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)))
}
