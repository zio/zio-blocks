package zio.blocks.schema.toon

/**
 * Configuration for [[zio.blocks.schema.toon.ToonWriter]] that contains params
 * for formatting of output TOON and for tuning of preferred size for internal
 * byte buffer.
 *
 * All configuration parameters are initialized to default values, but in some
 * cases they should be altered:
 *   - Change `indentSize` for different indentation widths (default 2)
 *   - Change `delimiter` for tab-separated or pipe-separated output
 *   - Set `arrayFormat` to force a specific array representation
 *   - Enable `escapeUnicode` for ASCII-only output
 *
 * @param indentSize
 *   number of spaces per indentation level (default 2, per TOON spec)
 * @param delimiter
 *   delimiter character for inline arrays and tabular rows (default Comma)
 * @param arrayFormat
 *   array format selection strategy (default Auto)
 * @param escapeUnicode
 *   flag to turn on hexadecimal escaping of all non-ASCII chars
 * @param preferredBufSize
 *   preferred size (in bytes) of internal byte buffer when writing to
 *   OutputStream
 *
 * @see
 *   [[https://github.com/toon-format/spec TOON Specification]]
 */
class WriterConfig private (
  val indentSize: Int,
  val delimiter: Delimiter,
  val arrayFormat: ArrayFormat,
  val escapeUnicode: Boolean,
  val preferredBufSize: Int
) extends Serializable {

  def withIndentSize(indentSize: Int): WriterConfig = {
    if (indentSize < 1) throw new IllegalArgumentException("'indentSize' should be at least 1")
    if (indentSize > 8) throw new IllegalArgumentException("'indentSize' should not exceed 8")
    copy(indentSize = indentSize)
  }

  def withDelimiter(delimiter: Delimiter): WriterConfig =
    copy(delimiter = delimiter)

  def withArrayFormat(arrayFormat: ArrayFormat): WriterConfig =
    copy(arrayFormat = arrayFormat)

  def withEscapeUnicode(escapeUnicode: Boolean): WriterConfig =
    copy(escapeUnicode = escapeUnicode)

  def withPreferredBufSize(preferredBufSize: Int): WriterConfig = {
    if (preferredBufSize <= 0) throw new IllegalArgumentException("'preferredBufSize' should be at least 1")
    copy(preferredBufSize = preferredBufSize)
  }

  private[this] def copy(
    indentSize: Int = indentSize,
    delimiter: Delimiter = delimiter,
    arrayFormat: ArrayFormat = arrayFormat,
    escapeUnicode: Boolean = escapeUnicode,
    preferredBufSize: Int = preferredBufSize
  ): WriterConfig =
    new WriterConfig(
      indentSize = indentSize,
      delimiter = delimiter,
      arrayFormat = arrayFormat,
      escapeUnicode = escapeUnicode,
      preferredBufSize = preferredBufSize
    )
}

object WriterConfig
    extends WriterConfig(
      indentSize = 2,
      delimiter = Delimiter.Comma,
      arrayFormat = ArrayFormat.Auto,
      escapeUnicode = false,
      preferredBufSize = 32768
    )
