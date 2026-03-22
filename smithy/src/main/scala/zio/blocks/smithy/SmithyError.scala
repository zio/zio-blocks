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

package zio.blocks.smithy

/**
 * Error type for Smithy parsing and processing.
 *
 * @param message
 *   descriptive error message
 * @param line
 *   line number where the error occurred (1-indexed)
 * @param column
 *   column number where the error occurred (1-indexed)
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
