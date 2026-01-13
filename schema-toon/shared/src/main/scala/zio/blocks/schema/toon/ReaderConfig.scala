package zio.blocks.schema.toon

/**
 * Configuration for [[zio.blocks.schema.toon.ToonReader]] that contains flags
 * for tuning of parsing behavior and security limits.
 *
 * All configuration parameters are initialized to recommended default values,
 * but in some cases they should be altered:
 *   - Increase `maxDepth` for deeply nested documents (default handles most
 *     cases)
 *   - Increase `maxDocumentSize` for very large documents
 *   - Enable `strictMode` for stricter validation of indentation
 *
 * @param maxDepth
 *   maximum nesting depth allowed (security: prevent stack overflow)
 * @param maxDocumentSize
 *   maximum document size in bytes (security: prevent memory exhaustion)
 * @param indentSize
 *   expected number of spaces per indentation level (default 2)
 * @param strictMode
 *   if true, enforce strict indentation validation (exact multiples of
 *   indentSize)
 * @param preferredBufSize
 *   preferred size (in bytes) of internal byte buffer when parsing from
 *   InputStream
 * @param preferredCharBufSize
 *   preferred size (in chars) of internal char buffer for parsing string values
 * @param checkForEndOfInput
 *   flag to check and raise an error if non-whitespace bytes are detected after
 *   successful parsing
 *
 * @see
 *   [[https://github.com/toon-format/spec TOON Specification]]
 */
class ReaderConfig private (
  val maxDepth: Int,
  val maxDocumentSize: Int,
  val indentSize: Int,
  val strictMode: Boolean,
  val preferredBufSize: Int,
  val preferredCharBufSize: Int,
  val maxBufSize: Int,
  val maxCharBufSize: Int,
  val checkForEndOfInput: Boolean
) extends Serializable {

  def withMaxDepth(maxDepth: Int): ReaderConfig = {
    if (maxDepth < 1) throw new IllegalArgumentException("'maxDepth' should be at least 1")
    if (maxDepth > 10000) throw new IllegalArgumentException("'maxDepth' should not exceed 10000")
    copy(maxDepth = maxDepth)
  }

  def withMaxDocumentSize(maxDocumentSize: Int): ReaderConfig = {
    if (maxDocumentSize < 1) throw new IllegalArgumentException("'maxDocumentSize' should be at least 1")
    copy(maxDocumentSize = maxDocumentSize)
  }

  def withIndentSize(indentSize: Int): ReaderConfig = {
    if (indentSize < 1) throw new IllegalArgumentException("'indentSize' should be at least 1")
    if (indentSize > 8) throw new IllegalArgumentException("'indentSize' should not exceed 8")
    copy(indentSize = indentSize)
  }

  def withStrictMode(strictMode: Boolean): ReaderConfig =
    copy(strictMode = strictMode)

  def withPreferredBufSize(preferredBufSize: Int): ReaderConfig = {
    if (preferredBufSize < 12) throw new IllegalArgumentException("'preferredBufSize' should be not less than 12")
    if (preferredBufSize > maxBufSize)
      throw new IllegalArgumentException("'preferredBufSize' should be not greater than 'maxBufSize'")
    copy(preferredBufSize = preferredBufSize)
  }

  def withPreferredCharBufSize(preferredCharBufSize: Int): ReaderConfig = {
    if (preferredCharBufSize < 0)
      throw new IllegalArgumentException("'preferredCharBufSize' should be not less than 0")
    if (preferredCharBufSize > maxCharBufSize)
      throw new IllegalArgumentException("'preferredCharBufSize' should be not greater than 'maxCharBufSize'")
    copy(preferredCharBufSize = preferredCharBufSize)
  }

  def withMaxBufSize(maxBufSize: Int): ReaderConfig = {
    if (maxBufSize < preferredBufSize)
      throw new IllegalArgumentException("'maxBufSize' should be not less than 'preferredBufSize'")
    if (maxBufSize > 2147483645)
      throw new IllegalArgumentException("'maxBufSize' should be not greater than 2147483645")
    copy(maxBufSize = maxBufSize)
  }

  def withMaxCharBufSize(maxCharBufSize: Int): ReaderConfig = {
    if (maxCharBufSize < preferredCharBufSize)
      throw new IllegalArgumentException("'maxCharBufSize' should be not less than 'preferredCharBufSize'")
    if (maxCharBufSize > 2147483645)
      throw new IllegalArgumentException("'maxCharBufSize' should be not greater than 2147483645")
    copy(maxCharBufSize = maxCharBufSize)
  }

  def withCheckForEndOfInput(checkForEndOfInput: Boolean): ReaderConfig =
    copy(checkForEndOfInput = checkForEndOfInput)

  private[this] def copy(
    maxDepth: Int = maxDepth,
    maxDocumentSize: Int = maxDocumentSize,
    indentSize: Int = indentSize,
    strictMode: Boolean = strictMode,
    preferredBufSize: Int = preferredBufSize,
    preferredCharBufSize: Int = preferredCharBufSize,
    maxBufSize: Int = maxBufSize,
    maxCharBufSize: Int = maxCharBufSize,
    checkForEndOfInput: Boolean = checkForEndOfInput
  ): ReaderConfig =
    new ReaderConfig(
      maxDepth = maxDepth,
      maxDocumentSize = maxDocumentSize,
      indentSize = indentSize,
      strictMode = strictMode,
      preferredBufSize = preferredBufSize,
      preferredCharBufSize = preferredCharBufSize,
      maxBufSize = maxBufSize,
      maxCharBufSize = maxCharBufSize,
      checkForEndOfInput = checkForEndOfInput
    )
}

object ReaderConfig
    extends ReaderConfig(
      maxDepth = 128,
      maxDocumentSize = 64 * 1024 * 1024, // 64MB
      indentSize = 2,
      strictMode = false,
      preferredBufSize = 32768,
      preferredCharBufSize = 4096,
      maxBufSize = 33554432,
      maxCharBufSize = 4194304,
      checkForEndOfInput = true
    )
