/*
 * Copyright 2018-2024 John A. De Goes and the ZIO Contributors
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

package zio.blocks.markdown

/**
 * A position-aware parse error for Markdown parsing.
 */
final case class ParseError(
  message: String,
  line: Int,
  column: Int,
  offendingInput: Option[String]
) extends Exception(ParseError.formatMessage(message, line, column, offendingInput)) {

  /**
   * Returns a formatted error message including position information.
   */
  def formattedMessage: String = getMessage

  /**
   * Creates a new error at the same position with a different message.
   */
  def withMessage(newMessage: String): ParseError = copy(message = newMessage)

  /**
   * Creates a new error with additional context about the offending input.
   */
  def withOffendingInput(input: String): ParseError = copy(offendingInput = Some(input))
}

object ParseError {

  /**
   * Creates an error at the given position.
   */
  def apply(message: String, line: Int, column: Int): ParseError =
    new ParseError(message, line, column, None)

  /**
   * Creates an error at position (1, 1) with the given message.
   */
  def apply(message: String): ParseError =
    new ParseError(message, 1, 1, None)

  private def formatMessage(message: String, line: Int, column: Int, offendingInput: Option[String]): String = {
    val posInfo   = s"at line $line, column $column"
    val inputInfo = offendingInput.map(s => s", near: ${truncate(s, 20)}").getOrElse("")
    s"$message ($posInfo$inputInfo)"
  }

  private def truncate(s: String, maxLen: Int): String =
    if (s.length <= maxLen) s"'$s'" else s"'${s.take(maxLen)}...'"
}
