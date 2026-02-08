package zio.blocks.schema.xml

/**
 * Configuration for XML writing.
 *
 * @param indentStep
 *   Number of spaces per indentation level. 0 means no indentation.
 * @param includeDeclaration
 *   Whether to include the XML declaration (<?xml version="1.0"?>).
 * @param encoding
 *   Character encoding to specify in the XML declaration.
 */
final case class WriterConfig(
  indentStep: Int = 0,
  includeDeclaration: Boolean = false,
  encoding: String = "UTF-8"
)

object WriterConfig {

  /** Default configuration with no indentation and no XML declaration. */
  val default: WriterConfig = WriterConfig()

  /** Configuration with pretty-printing enabled (2-space indentation). */
  val pretty: WriterConfig = WriterConfig(indentStep = 2)

  /** Configuration with XML declaration enabled. */
  val withDeclaration: WriterConfig = WriterConfig(includeDeclaration = true)
}
