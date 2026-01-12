package zio.blocks.schema.toon

import zio.blocks.schema.{DynamicOptic, SchemaError}
import java.nio.charset.StandardCharsets

/**
 * A streaming writer for the TOON format.
 *
 * @param buf
 *   The output byte buffer
 * @param config
 *   The writer configuration
 */
final class ToonWriter(var buf: Array[Byte], val config: ToonWriterConfig) {
  var count: Int              = 0
  var currentIndentLevel: Int = 0

  // Helper to ensure we are at the start of a line with correct indentation
  var atStartOfLine: Boolean = true

  /**
   * Encodes an error message into a `SchemaError` and throws it.
   *
   * @param msg
   *   The error message.
   * @return
   *   Nothing, as it always throws an exception.
   */
  def encodeError(msg: String): Nothing = {
    val single = new SchemaError.ExpectationMismatch(DynamicOptic.root, msg)
    throw new SchemaError(new ::(single, Nil))
  }

  def writeRaw(s: String): Unit = {
    val bytes = s.getBytes(StandardCharsets.UTF_8)
    ensureCapacity(bytes.length)
    System.arraycopy(bytes, 0, buf, count, bytes.length)
    count += bytes.length
    atStartOfLine = false
  }

  def writeIndent(): Unit = {
    if (!atStartOfLine) newLine()
    val spaces = config.indentSize * currentIndentLevel
    writeRaw(" " * spaces)
  }

  def newLine(): Unit =
    if (!atStartOfLine) { // Prevent double newlines if already there
      writeRaw(config.lineEnding)
      atStartOfLine = true
    }

  def writeQuotedString(s: String): Unit = {
    writeRaw("\"")
    var i = 0
    while (i < s.length) {
      val c = s.charAt(i)
      c match {
        case '\n' => writeRaw("\\n")
        case '\r' => writeRaw("\\r")
        case '\t' => writeRaw("\\t")
        case '"'  => writeRaw("\\\"")
        case '\\' => writeRaw("\\\\")
        case _    =>
          writeRaw(c.toString)
      }
      i += 1
    }
    writeRaw("\"")
  }

  def writeRawString(s: String): Unit =
    writeRaw(s)

  def writeNull(): Unit =
    writeRaw("null")

  private def ensureCapacity(len: Int): Unit =
    if (count + len > buf.length) {
      val newSize = Math.max(buf.length * 2, count + len)
      val newBuf  = new Array[Byte](newSize)
      System.arraycopy(buf, 0, newBuf, 0, count)
      buf = newBuf
    }

  // --- Higher Level encoding helpers ---

  def writeKey(key: String): Unit = {
    writeIndent()
    // Keys are unquoted unless special characters
    if (requiresQuoting(key)) writeQuotedString(key) else writeRawString(key)
    writeRaw(": ")
  }

  def writeKeyNoColon(key: String): Unit = {
    writeIndent()
    if (requiresQuoting(key)) writeQuotedString(key) else writeRawString(key)
  }

  def requiresQuoting(s: String): Boolean = {
    if (s.isEmpty) return true
    val delimiter = config.delimiter
    if (s.charAt(0).isWhitespace || s.charAt(s.length - 1).isWhitespace) return true
    if (
      s.contains(delimiter) || s.contains(':') ||
      s.contains('{') || s.contains('}') ||
      s.contains('[') || s.contains(']') ||
      s.contains('\t')
    ) return true

    // Check if it looks like a primitive to avoid ambiguity
    if (s == "true" || s == "false" || s == "null") return true

    // Check if it looks like a number
    if (looksLikeNumber(s)) return true

    // Check for list item marker at start of unquoted string
    if (s == "-") return true
    if (s.startsWith("- ")) return true

    // Control characters check
    s.exists(c => c < 32)
  }

  private def looksLikeNumber(s: String): Boolean = {
    if (s == "0" || s == "-0") return true

    // We treat "05" as looking like a number (5) to force quoting
    // This allows robust handling of strings that might be parsed as numbers by generic parsers

    try {
      // Use java.math.BigDecimal for robust number parsing without scientific notation issues
      new java.math.BigDecimal(s)
      true
    } catch {
      case _: Exception => false
    }
  }
}
