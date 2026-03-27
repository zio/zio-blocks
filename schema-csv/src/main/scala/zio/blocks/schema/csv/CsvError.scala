/*
 * Copyright 2024-2026 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
    override def getMessage: String = s"CSV error at row $row, column $column: $message"
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
    override def getMessage: String = s"CSV error at row $row, column $column: $message (field: $fieldName)"
  }
}
