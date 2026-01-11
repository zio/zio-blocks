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
        if (n == -1) throw new RuntimeException(s"Unexpected EOF inside string at line $line, col $col")
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
      case _       => throw new RuntimeException(s"Invalid boolean value: $s at line $line")
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
      throw new RuntimeException(s"Invalid indentation: $spaces spaces is not a multiple of 2 at line $line")
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
      throw new RuntimeException(s"Expected ':' but got '${c.toChar}' at line $line, col $col")
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
