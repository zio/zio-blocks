package zio.blocks.docs

/**
 * A parse error with position information.
 *
 * Returned when the parser encounters invalid markdown syntax.
 *
 * @param message
 *   Human-readable error message
 * @param line
 *   Line number where the error occurred (1-based)
 * @param column
 *   Column number where the error occurred (1-based)
 * @param input
 *   The input line that caused the error
 */
final case class ParseError(
  message: String,
  line: Int,
  column: Int,
  input: String
) extends Product
    with Serializable {

  override def toString: String =
    s"ParseError at line $line, column $column: $message\n  $input"
}
