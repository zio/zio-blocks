package zio.blocks.schema.toon

/**
 * Configuration for the TOON reader.
 *
 * @param throwReaderExceptionWithStackTrace
 *   whether to capture a stack trace when a reader exception occurs
 * @param preferredBufSize
 *   the preferred size of the internal buffer
 * @param preferredCharBufSize
 *   the preferred size of the internal char buffer
 * @param strictArrayLength
 *   whether to enforce strict array length checks
 */
final class ToonReaderConfig private (
  val throwReaderExceptionWithStackTrace: Boolean,
  val preferredBufSize: Int,
  val preferredCharBufSize: Int,
  val strictArrayLength: Boolean
)

object ToonReaderConfig {
  val default: ToonReaderConfig = new ToonReaderConfig(
    throwReaderExceptionWithStackTrace = false,
    preferredBufSize = 32768,
    preferredCharBufSize = 4096,
    strictArrayLength = false
  )

  def apply(
    throwReaderExceptionWithStackTrace: Boolean = false,
    preferredBufSize: Int = 32768,
    preferredCharBufSize: Int = 4096,
    strictArrayLength: Boolean = false
  ): ToonReaderConfig = new ToonReaderConfig(
    throwReaderExceptionWithStackTrace,
    preferredBufSize,
    preferredCharBufSize,
    strictArrayLength
  )
}
