package zio.blocks.schema.toon

/**
 * Configuration for the TOON writer.
 *
 * @param throwWriterExceptionWithStackTrace
 *   whether to capture a stack trace when a writer exception occurs
 * @param preferredBufSize
 *   the preferred size of the internal buffer
 * @param indentSize
 *   the number of spaces to use for indentation
 * @param lineEnding
 *   the string to use for line endings
 * @param arrayFormat
 *   the format to use for serializing arrays
 */
final class ToonWriterConfig private (
  val throwWriterExceptionWithStackTrace: Boolean,
  val preferredBufSize: Int,
  val indentSize: Int,
  val lineEnding: String,
  val arrayFormat: ArrayFormat
)

object ToonWriterConfig {
  val default: ToonWriterConfig = new ToonWriterConfig(
    throwWriterExceptionWithStackTrace = false,
    preferredBufSize = 32768,
    indentSize = 2,
    lineEnding = "\n",
    arrayFormat = ArrayFormat.Auto
  )

  def apply(
    throwWriterExceptionWithStackTrace: Boolean = false,
    preferredBufSize: Int = 32768,
    indentSize: Int = 2,
    lineEnding: String = "\n",
    arrayFormat: ArrayFormat = ArrayFormat.Auto
  ): ToonWriterConfig = new ToonWriterConfig(
    throwWriterExceptionWithStackTrace,
    preferredBufSize,
    indentSize,
    lineEnding,
    arrayFormat
  )
}
