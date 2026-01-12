package zio.blocks.schema.toon

/**
 * Configuration for ToonReader.
 *
 * @param preferredBufSize
 *   Preferred byte buffer size
 * @param preferredCharBufSize
 *   Preferred char buffer size
 * @param maxBufSize
 *   Maximum byte buffer size
 * @param maxCharBufSize
 *   Maximum char buffer size
 * @param checkForEndOfInput
 *   Verify no trailing content after parsing
 * @param strictArrayLength
 *   Validate array length markers match actual count
 */
class ToonReaderConfig private (
  val preferredBufSize: Int,
  val preferredCharBufSize: Int,
  val maxBufSize: Int,
  val maxCharBufSize: Int,
  val checkForEndOfInput: Boolean,
  val strictArrayLength: Boolean
) extends Serializable {
  def withStrictArrayLength(strict: Boolean): ToonReaderConfig =
    copy(strictArrayLength = strict)

  def withPreferredBufSize(size: Int): ToonReaderConfig =
    copy(preferredBufSize = size)

  def withPreferredCharBufSize(size: Int): ToonReaderConfig =
    copy(preferredCharBufSize = size)

  def withMaxBufSize(size: Int): ToonReaderConfig =
    copy(maxBufSize = size)

  def withMaxCharBufSize(size: Int): ToonReaderConfig =
    copy(maxCharBufSize = size)

  def withCheckForEndOfInput(check: Boolean): ToonReaderConfig =
    copy(checkForEndOfInput = check)

  private def copy(
    preferredBufSize: Int = preferredBufSize,
    preferredCharBufSize: Int = preferredCharBufSize,
    maxBufSize: Int = maxBufSize,
    maxCharBufSize: Int = maxCharBufSize,
    checkForEndOfInput: Boolean = checkForEndOfInput,
    strictArrayLength: Boolean = strictArrayLength
  ): ToonReaderConfig = new ToonReaderConfig(
    preferredBufSize,
    preferredCharBufSize,
    maxBufSize,
    maxCharBufSize,
    checkForEndOfInput,
    strictArrayLength
  )
}

object ToonReaderConfig
    extends ToonReaderConfig(
      preferredBufSize = 32768,
      preferredCharBufSize = 4096,
      maxBufSize = 33554432,
      maxCharBufSize = 4194304,
      checkForEndOfInput = true,
      strictArrayLength = true
    )
