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
 * An error from reading CSV text. [[CsvReader]] returns it in a `Left` when a
 * row is malformed: a quoted cell that is never closed, or a character other
 * than the delimiter or a line break right after a closing quote. It is a
 * `Throwable` with no stack trace and no cause, so it costs little to create.
 *
 * [[CsvCodec]]'s `decode` does not return it: it copies the text of
 * `getMessage` into a [[zio.blocks.schema.SchemaError]]. The module produces
 * only [[CsvError.ParseError]]; no code in it constructs [[CsvError.TypeError]]
 * (a known bug).
 *
 * {{{
 * val result = CsvReader.readRow("\"t-1\"x,21.5", 0, CsvConfig.default)
 * val text   = result.left.map(_.getMessage)
 * // Left(CSV error at row 1, column 6: Unexpected character 'x' after closing quote)
 * val parts = result.left.map(e => (e.row, e.column, e.message))
 * // Left((1,6,Unexpected character 'x' after closing quote))
 * }}}
 *
 * @see
 *   [[https://zioblocks.com/reference/schema/built-in-codecs/csv/ CSV Codec Module]]
 *
 * @param message
 *   The description of the error, without the row and column.
 */
sealed abstract class CsvError(val message: String) extends Throwable(message, null, false, false) {

  /**
   * The line of the error, counting from 1 at the start of the row being read,
   * not of the document (a known bug): [[CsvReader.readAll]] reports an error
   * on any line of a file as row 1, unless a quoted cell of the failing row
   * spans several lines.
   */
  def row: Int

  /**
   * The character position of the error within its line, counting from 1. For
   * an unclosed quoted cell it is one past the last character read.
   */
  def column: Int
}

/** The cases of [[CsvError]]. */
object CsvError {

  /**
   * Malformed CSV text, such as `Unclosed quoted field` or
   * `Unexpected character 'x' after closing quote`. Its `getMessage` adds the
   * position: `CSV error at row 1, column 6: ...`.
   *
   * @param message
   *   The description of the error, without the position.
   * @param row
   *   The line of the error; see [[CsvError#row]].
   * @param column
   *   The character position within the line, from 1.
   */
  final case class ParseError(override val message: String, row: Int, column: Int) extends CsvError(message) {
    override def getMessage: String = s"CSV error at row $row, column $column: $message"
  }

  /**
   * Meant for a cell that does not convert to its field's type, but no code in
   * the module constructs it (a known bug): [[CsvCodec]]'s `decode` reports
   * such a cell as a [[zio.blocks.schema.SchemaError]] instead. Its
   * `getMessage` is
   * `CSV error at row R, column C: <message> (field: <fieldName>)`.
   *
   * @param message
   *   The description of the error.
   * @param row
   *   The line of the error.
   * @param column
   *   The character position within the line, from 1.
   * @param fieldName
   *   The name of the field whose cell did not convert.
   */
  final case class TypeError(override val message: String, row: Int, column: Int, fieldName: String)
      extends CsvError(message) {
    override def getMessage: String = s"CSV error at row $row, column $column: $message (field: $fieldName)"
  }
}
