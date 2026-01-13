package zio.blocks.schema.json

/**
 * Configuration for [[zio.blocks.schema.json.JsonWriter]] that contains params
 * for formatting of output JSON and for tuning of preferred size for internal
 * byte buffer that is created on the writer instantiation and reused in runtime
 * for serialization of messages using any type of output except heap byte
 * buffers supplied as arguments. <br/> All configuration params already
 * initialized to default values, but in some cases they should be altered: <ul>
 * <li>turn on pretty printing by specifying of an indention step that is
 * greater than 0</li> <li>turn on escaping of Unicode characters to serialize
 * with only ASCII characters</li> <li>increase the preferred size of an
 * internal byte buffer to reduce the allocation rate of grown and then reduced
 * internal buffers when the serialized output size is greater than 32Kb</li>
 * </ul>
 * @param indentionStep
 *   a size of indention for pretty-printed formatting or 0 for compact output
 * @param escapeUnicode
 *   a flag to turn on hexadecimal escaping of all non-ASCII chars
 * @param preferredBufSize
 *   a preferred size (in bytes) of an internal byte buffer when writing to any
 *   type of output except pre-allocated byte arrays or heap byte buffers
 *   supplied as arguments
 */
class WriterConfig private (
  val indentionStep: Int,
  val preferredBufSize: Int,
  val escapeUnicode: Boolean
) extends Serializable {
  def withIndentionStep(indentionStep: Int): WriterConfig = {
    if (indentionStep < 0) throw new IllegalArgumentException("'indentionStep' should be not less than 0")
    copy(indentionStep = indentionStep)
  }

  def withEscapeUnicode(escapeUnicode: Boolean): WriterConfig =
    copy(escapeUnicode = escapeUnicode)

  def withPreferredBufSize(preferredBufSize: Int): WriterConfig = {
    if (preferredBufSize <= 0) throw new IllegalArgumentException("'preferredBufSize' should be not less than 1")
    copy(preferredBufSize = preferredBufSize)
  }

  private[this] def copy(
    indentionStep: Int = indentionStep,
    preferredBufSize: Int = preferredBufSize,
    escapeUnicode: Boolean = escapeUnicode
  ): WriterConfig =
    new WriterConfig(
      indentionStep = indentionStep,
      preferredBufSize = preferredBufSize,
      escapeUnicode = escapeUnicode
    )
}

object WriterConfig
    extends WriterConfig(
      indentionStep = 0,
      preferredBufSize = 32768,
      escapeUnicode = false
    )
