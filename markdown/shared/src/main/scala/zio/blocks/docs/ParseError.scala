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
