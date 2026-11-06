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
 * The error [[Parser.parse]] returns, with the position it found it at.
 *
 * [[Parser]] reports three problems: a text whose first line is `---` and that
 * has another `---` line at least two lines further down (`Frontmatter is not
 * supported`), a line that starts with more than six `#` characters (`Invalid
 * heading level: 7 (max is 6)`), and a code fence that is never closed
 * (`Unclosed code fence`). Inside a list item the last two are not reported:
 * the item's text becomes a plain paragraph.
 *
 * @param message
 *   The human-readable message
 * @param line
 *   The 1-based line number. For an unclosed code fence it is the number of the
 *   input's last line (a final newline adds an empty last line), not the line
 *   of the opening fence. Inside a block quote, lines are counted from the
 *   first line of the quote
 * @param column
 *   The 1-based column; [[Parser]] always reports 1
 * @param input
 *   The text of the line the error is about: the opening fence of an unclosed
 *   code block, the `---` line of a frontmatter, or the heading line
 *
 * @example
 *   {{{
 * val error = Parser.parse("```scala\nval x = 1").left.toOption.get
 *
 * val summary = (error.line, error.column, error.message)
 * // (2,1,Unclosed code fence)
 * val shown = error.toString
 * // ParseError at line 2, column 1: Unclosed code fence
 * //   ```scala
 *   }}}
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
