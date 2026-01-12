package zio.blocks.schema.toon

/**
 * Configuration for ToonWriter.
 *
 * @param indentSize
 *   Spaces per indentation level (default: 2)
 * @param preferredBufSize
 *   Preferred output buffer size
 * @param lineEnding
 *   Line ending style (LF recommended per spec)
 */
class ToonWriterConfig private (
  val indentSize: Int,
  val preferredBufSize: Int,
  val lineEnding: String,
  val delimiter: Char
) extends Serializable {
  def withIndentSize(size: Int): ToonWriterConfig =
    copy(indentSize = size)

  def withPreferredBufSize(size: Int): ToonWriterConfig =
    copy(preferredBufSize = size)

  def withLineEnding(ending: String): ToonWriterConfig =
    copy(lineEnding = ending)

  def withDelimiter(delim: Char): ToonWriterConfig =
    copy(delimiter = delim)

  private def copy(
    indentSize: Int = indentSize,
    preferredBufSize: Int = preferredBufSize,
    lineEnding: String = lineEnding,
    delimiter: Char = delimiter
  ): ToonWriterConfig = new ToonWriterConfig(
    indentSize,
    preferredBufSize,
    lineEnding,
    delimiter
  )
}

object ToonWriterConfig
    extends ToonWriterConfig(
      indentSize = 2,
      preferredBufSize = 32768,
      lineEnding = "\n",
      delimiter = ','
    )
