package zio.blocks.schema.toon

import java.io.{ByteArrayOutputStream, OutputStream}
import java.nio.charset.StandardCharsets

/**
 * High-performance TOON format writer.
 *
 * Optimizations:
 *   - Pre-allocated byte arrays for common strings
 *   - Cached indentation strings up to max depth
 *   - Fast ASCII path for string escaping
 */
final class ToonWriter(
  config: ToonWriterConfig,
  out: OutputStream
) {
  import ToonWriter._

  private[this] var currentIndentLevel: Int = 0

  /**
   * Returns the current indentation level.
   */
  def getIndentLevel: Int = currentIndentLevel

  /**
   * Writes a raw string directly to the output. For frequently used short
   * strings, consider using writeRawBytes instead.
   */
  def writeRaw(s: String): Unit = {
    // Fast path for small ASCII strings
    val len = s.length
    if (len <= 16 && isAscii(s)) {
      var i = 0
      while (i < len) {
        out.write(s.charAt(i))
        i += 1
      }
    } else {
      out.write(s.getBytes(StandardCharsets.UTF_8))
    }
  }

  /**
   * Writes pre-encoded bytes directly (no allocation).
   */
  def writeRawBytes(bytes: Array[Byte]): Unit =
    out.write(bytes)

  /**
   * Writes the current indentation (currentIndentLevel * indentSize spaces).
   */
  def writeIndent(): Unit =
    if (currentIndentLevel > 0) {
      val depth = currentIndentLevel * config.indentSize
      if (depth < INDENT_CACHE.length) {
        out.write(INDENT_CACHE(depth))
      } else {
        var i = 0
        while (i < depth) {
          out.write(' ')
          i += 1
        }
      }
    }

  /**
   * Writes a newline character (using configured line ending).
   */
  def newLine(): Unit =
    out.write(NEWLINE_BYTES)

  /**
   * Writes a string enclosed in quotes with proper escaping. Optimized with
   * fast ASCII path.
   */
  def writeQuotedString(s: String): Unit = {
    out.write('"')
    val len = s.length
    var i   = 0
    while (i < len) {
      val c = s.charAt(i)
      if (c < 128) {
        // Fast ASCII path
        val escape = ESCAPE_TABLE(c)
        if (escape != 0) {
          out.write('\\')
          out.write(escape)
        } else if (c < 32) {
          // Control char - use unicode escape
          writeUnicodeEscape(c)
        } else {
          out.write(c)
        }
      } else if (c < 256) {
        // Extended ASCII - write directly (UTF-8 safe for Latin-1)
        out.write(c)
      } else {
        // Unicode - use escape or write UTF-8
        val bytes = Character.toString(c).getBytes(StandardCharsets.UTF_8)
        out.write(bytes)
      }
      i += 1
    }
    out.write('"')
  }

  @inline private def writeUnicodeEscape(c: Char): Unit = {
    out.write('\\')
    out.write('u')
    out.write(HEX_DIGITS((c >> 12) & 0xf))
    out.write(HEX_DIGITS((c >> 8) & 0xf))
    out.write(HEX_DIGITS((c >> 4) & 0xf))
    out.write(HEX_DIGITS(c & 0xf))
  }

  /**
   * Writes the literal "null".
   */
  def writeNull(): Unit =
    out.write(NULL_BYTES)

  /**
   * Increases the current indentation level by 1.
   */
  def indent(): Unit =
    currentIndentLevel += 1

  /**
   * Alias for indent() for clarity.
   */
  def increaseIndent(): Unit = indent()

  /**
   * Decreases the current indentation level by 1 (minimum 0).
   */
  def unindent(): Unit =
    if (currentIndentLevel > 0) currentIndentLevel -= 1

  /**
   * Alias for unindent() for clarity.
   */
  def decreaseIndent(): Unit = unindent()

  /**
   * Flushes the underlying output stream.
   */
  def flush(): Unit =
    out.flush()

  /**
   * Returns the written content as a String (only works if using
   * ByteArrayOutputStream).
   */
  override def toString: String = out match {
    case baos: ByteArrayOutputStream => baos.toString(StandardCharsets.UTF_8.name())
    case _                           => super.toString
  }

  @inline private def isAscii(s: String): Boolean = {
    val len = s.length
    var i   = 0
    while (i < len) {
      if (s.charAt(i) >= 128) return false
      i += 1
    }
    true
  }
}

object ToonWriter {
  // Pre-allocated byte arrays for common strings
  private[toon] val NULL_BYTES        = "null".getBytes(StandardCharsets.UTF_8)
  private[toon] val TRUE_BYTES        = "true".getBytes(StandardCharsets.UTF_8)
  private[toon] val FALSE_BYTES       = "false".getBytes(StandardCharsets.UTF_8)
  private[toon] val NEWLINE_BYTES     = "\n".getBytes(StandardCharsets.UTF_8)
  private[toon] val COLON_SPACE_BYTES = ": ".getBytes(StandardCharsets.UTF_8)
  private[toon] val DASH_SPACE_BYTES  = "- ".getBytes(StandardCharsets.UTF_8)

  // Hex digits for unicode escapes
  private val HEX_DIGITS = "0123456789abcdef".toCharArray

  // Escape table: maps char -> escape char (0 = no escape needed)
  private val ESCAPE_TABLE: Array[Byte] = {
    val table = new Array[Byte](128)
    table('"') = '"'.toByte
    table('\\') = '\\'.toByte
    table('\b') = 'b'.toByte
    table('\f') = 'f'.toByte
    table('\n') = 'n'.toByte
    table('\r') = 'r'.toByte
    table('\t') = 't'.toByte
    table
  }

  // Pre-computed indentation byte arrays (up to 32 spaces)
  private val INDENT_CACHE: Array[Array[Byte]] = {
    val max = 33
    val arr = new Array[Array[Byte]](max)
    var i   = 0
    while (i < max) {
      arr(i) = (" " * i).getBytes(StandardCharsets.UTF_8)
      i += 1
    }
    arr
  }

  // Thread-local pool for ByteArrayOutputStream reuse
  private val bufferPool = new ThreadLocal[ByteArrayOutputStream] {
    override def initialValue(): ByteArrayOutputStream = new ByteArrayOutputStream(256)
  }

  /**
   * Gets a ByteArrayOutputStream from the thread-local pool. The stream is
   * reset before being returned.
   */
  private[toon] def getPooledBuffer(): ByteArrayOutputStream = {
    val buf = bufferPool.get()
    buf.reset()
    buf
  }

  /**
   * Creates a ToonWriter that writes to an internal buffer. Use toString to get
   * the result.
   */
  def apply(config: ToonWriterConfig = ToonWriterConfig.default): ToonWriter =
    new ToonWriter(config, new ByteArrayOutputStream(256))

  /**
   * Creates a ToonWriter using a pooled buffer (more efficient for repeated
   * use).
   */
  def pooled(config: ToonWriterConfig = ToonWriterConfig.default): ToonWriter =
    new ToonWriter(config, getPooledBuffer())
}
