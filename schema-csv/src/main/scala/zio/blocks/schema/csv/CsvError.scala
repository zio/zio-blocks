package zio.blocks.schema.csv

/**
 * Sealed abstract class representing errors that occur during CSV parsing and
 * processing.
 *
 * CSV errors are zero-overhead exceptions (no stack trace) designed for
 * performance in CSV codec operations.
 */
sealed abstract class CsvError(val message: String) extends Throwable(message, null, false, false) {

  /** Row number where the error occurred (1-based). */
  def row: Int

  /** Column number where the error occurred (1-based). */
  def column: Int

  /** Human-readable formatted error message. */
  def formatMessage: String

  override def getMessage: String = formatMessage
}

object CsvError {

  /**
   * Parse error - occurs when CSV format is invalid.
   *
   * @param message
   *   The error message describing the parse failure
   * @param row
   *   Row number where the error occurred (1-based)
   * @param column
   *   Column number where the error occurred (1-based)
   */
  final case class ParseError(override val message: String, row: Int, column: Int) extends CsvError(message) {
    override def formatMessage: String = s"CSV error at row $row, column $column: $message"
  }

  /**
   * Type error - occurs when a field cannot be converted to the expected type.
   *
   * @param message
   *   The error message describing the type conversion failure
   * @param row
   *   Row number where the error occurred (1-based)
   * @param column
   *   Column number where the error occurred (1-based)
   * @param fieldName
   *   The name of the field that failed type conversion
   */
  final case class TypeError(override val message: String, row: Int, column: Int, fieldName: String)
      extends CsvError(message) {
    override def formatMessage: String = s"CSV error at row $row, column $column: $message (field: $fieldName)"
  }
}
