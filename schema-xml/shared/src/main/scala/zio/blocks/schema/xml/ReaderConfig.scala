package zio.blocks.schema.xml

/**
 * Configuration for XML reader behavior and limits.
 *
 * @param maxDepth
 *   Maximum nesting depth for XML elements (prevents stack overflow attacks)
 * @param maxAttributes
 *   Maximum number of attributes per element
 * @param maxTextLength
 *   Maximum length of text content
 * @param preserveWhitespace
 *   If true, preserve all whitespace in text nodes; if false, trim
 *   leading/trailing whitespace
 */
final case class ReaderConfig(
  maxDepth: Int = 1000,
  maxAttributes: Int = 1000,
  maxTextLength: Int = 10000000,
  preserveWhitespace: Boolean = false
)

object ReaderConfig {
  val default: ReaderConfig = ReaderConfig()
}
