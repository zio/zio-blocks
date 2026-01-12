package zio.blocks.schema.toon

import java.nio.charset.StandardCharsets.UTF_8

/**
 * A writer for serializing values to TOON (Token-Oriented Object Notation)
 * format.
 *
 * TOON is an indentation-based format optimized for LLM token efficiency. This
 * writer handles:
 *   - Primitives (strings, numbers, booleans, null)
 *   - Records with indented key-value pairs
 *   - Arrays in inline format: `[N]: a,b,c`
 *   - ADTs with Key discriminator: `TypeName:` followed by indented content
 *
 * @param indentSize
 *   Number of spaces per indentation level (default: 2)
 */
final class ToonWriter(indentSize: Int = 2) {
  private[this] val buf         = new java.lang.StringBuilder(256)
  private[this] var indent      = 0
  private[this] var atLineStart = true

  /** Returns the current TOON output as a string. */
  def result: String = buf.toString

  /** Returns the current TOON output as UTF-8 bytes. */
  def toBytes: Array[Byte] = buf.toString.getBytes(UTF_8)

  /** Resets the writer for reuse. */
  def reset(): Unit = {
    buf.setLength(0)
    indent = 0
    atLineStart = true
  }

  // === Primitives ===

  /** Write a string value, quoting if necessary. */
  def writeString(s: String): Unit =
    if (requiresQuoting(s)) writeQuotedString(s)
    else buf.append(s)

  /** Write an unquoted string (caller ensures no quoting needed). */
  def writeRaw(s: String): Unit = buf.append(s)

  /** Write a quoted string with proper escaping. */
  def writeQuotedString(s: String): Unit = {
    buf.append('"')
    var i = 0
    while (i < s.length) {
      val c = s.charAt(i)
      c match {
        case '"'  => buf.append("\\\"")
        case '\\' => buf.append("\\\\")
        case '\n' => buf.append("\\n")
        case '\r' => buf.append("\\r")
        case '\t' => buf.append("\\t")
        case _    =>
          if (c < 32) buf.append(f"\\u${c.toInt}%04x")
          else buf.append(c)
      }
      i += 1
    }
    buf.append('"')
  }

  /** Write an integer value. */
  def writeInt(x: Int): Unit = buf.append(x)

  /** Write a long value. */
  def writeLong(x: Long): Unit = buf.append(x)

  /** Write a float value. NaN/Infinity become null. */
  def writeFloat(x: Float): Unit =
    if (x.isNaN || x.isInfinite) writeNull()
    else buf.append(x)

  /** Write a double value. NaN/Infinity become null. */
  def writeDouble(x: Double): Unit =
    if (x.isNaN || x.isInfinite) writeNull()
    else buf.append(x)

  /** Write a boolean value. */
  def writeBoolean(x: Boolean): Unit = buf.append(x)

  /** Write null. */
  def writeNull(): Unit = buf.append("null")

  /** Write a BigInt in decimal form. */
  def writeBigInt(x: BigInt): Unit = buf.append(x.toString)

  /** Write a BigDecimal in plain decimal form (no scientific notation). */
  def writeBigDecimal(x: BigDecimal): Unit = buf.append(x.bigDecimal.toPlainString)

  // === Structure ===

  /** Write a field key followed by `: `. Does not include value. */
  def writeKey(key: String): Unit = {
    writeIndentIfNeeded()
    writeString(key)
    buf.append(": ")
    atLineStart = false
  }

  /** Write a newline and mark that we're at line start. */
  def newLine(): Unit = {
    buf.append('\n')
    atLineStart = true
  }

  /** Increase indentation level. */
  def pushIndent(): Unit = indent += 1

  /** Decrease indentation level. */
  def popIndent(): Unit = {
    indent -= 1
    if (indent < 0) indent = 0
  }

  /** Write indentation if at start of line. */
  def writeIndentIfNeeded(): Unit =
    if (atLineStart) {
      var i      = 0
      val spaces = indent * indentSize
      while (i < spaces) {
        buf.append(' ')
        i += 1
      }
      atLineStart = false
    }

  // === Records ===

  /**
   * Start a nested object. Writes `:` and newline, increases indent. Called
   * after writeKey for nested records.
   */
  def startNestedObject(): Unit = {
    // Remove the `: ` that writeKey added, replace with just `:`
    if (buf.length >= 2 && buf.charAt(buf.length - 1) == ' ' && buf.charAt(buf.length - 2) == ':') {
      buf.setLength(buf.length - 1) // remove trailing space
    }
    newLine()
    pushIndent()
  }

  /** End a nested object. Decreases indent. */
  def endNestedObject(): Unit = popIndent()

  // === Arrays (Inline format only for MVP) ===

  /**
   * Write an inline array header: `[N]: ` where N is the element count. Caller
   * should then write comma-separated values.
   */
  def writeArrayHeader(size: Int): Unit = {
    writeIndentIfNeeded()
    buf.append('[')
    buf.append(size)
    buf.append("]: ")
    atLineStart = false
  }

  /**
   * Write an array header with field name: `fieldName[N]: `
   */
  def writeArrayHeaderWithKey(key: String, size: Int): Unit = {
    writeIndentIfNeeded()
    writeString(key)
    buf.append('[')
    buf.append(size)
    buf.append("]: ")
    atLineStart = false
  }

  /** Write a comma separator for array elements. */
  def writeArraySeparator(): Unit = buf.append(',')

  // === ADTs with Key discriminator ===

  /**
   * Write ADT discriminator in Key style: `TypeName:` followed by newline and
   * increased indent.
   */
  def writeDiscriminator(typeName: String): Unit = {
    writeIndentIfNeeded()
    writeString(typeName)
    buf.append(':')
    newLine()
    pushIndent()
  }

  /** End ADT discriminator block. Decreases indent. */
  def endDiscriminator(): Unit = popIndent()

  // === Helpers ===

  /** Check if a string requires quoting in TOON format. */
  private def requiresQuoting(s: String): Boolean = {
    if (s.isEmpty) return true
    if (s.charAt(0).isWhitespace) return true
    if (s.charAt(s.length - 1).isWhitespace) return true
    var i = 0
    while (i < s.length) {
      val c = s.charAt(i)
      if (c == ',' || c == ':' || c == '{' || c == '}' || c == '[' || c == ']' || c == '"' || c < 32)
        return true
      i += 1
    }
    // Check for TOON keywords that need quoting
    s == "true" || s == "false" || s == "null"
  }
}

object ToonWriter {

  /** Create a new ToonWriter with default settings. */
  def apply(): ToonWriter = new ToonWriter()

  /** Create a new ToonWriter with specified indent size. */
  def apply(indentSize: Int): ToonWriter = new ToonWriter(indentSize)
}
