package zio.blocks.smithy

/**
 * Error type for Smithy parsing and processing.
 *
 * @param message
 *   descriptive error message
 * @param line
 *   line number where the error occurred (0-indexed)
 * @param column
 *   column number where the error occurred (0-indexed)
 * @param source
 *   optional source content (e.g., the Smithy input that caused the error)
 */
sealed trait SmithyError {
  def message: String
  def line: Int
  def column: Int
  def source: Option[String]

  /**
   * Formats the error into a readable message with location and optional source
   * context.
   *
   * Format: "Parse error at line X, column Y: <message>" If source is present:
   * appends "\nSource: <source>"
   */
  def formatMessage: String = {
    val locationMsg = s"Parse error at line $line, column $column: $message"
    source match {
      case Some(src) => s"$locationMsg\nSource: $src"
      case None      => locationMsg
    }
  }
}

object SmithyError {

  /**
   * Parse error - occurs when Smithy IDL is malformed or contains unexpected
   * tokens.
   *
   * @param message
   *   descriptive error message
   * @param line
   *   line number where parsing failed
   * @param column
   *   column number where parsing failed
   * @param source
   *   optional source code snippet for context
   */
  final case class ParseError(
    message: String,
    line: Int,
    column: Int,
    source: Option[String]
  ) extends SmithyError
}
