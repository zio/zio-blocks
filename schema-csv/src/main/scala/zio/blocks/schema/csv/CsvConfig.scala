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
 * Settings for [[CsvReader]] and [[CsvWriter]]: the delimiter, the quote
 * character, the line terminator and whether a document starts with a header
 * row. Codecs derived with [[CsvFormat]] ignore it and always use
 * [[CsvConfig.default]], so use a custom config when you read or write rows of
 * cells yourself, such as a semicolon-separated export.
 *
 * {{{
 * val semicolons = CsvConfig(delimiter = ';', lineTerminator = "\n")
 * val text = CsvWriter.writeAll(
 *   IndexedSeq("sensor", "celsius"),
 *   List(IndexedSeq("t-1", "21,5"), IndexedSeq("t-2", "19,0")),
 *   semicolons
 * )
 * // "sensor;celsius\nt-1;21,5\nt-2;19,0\n"
 * val parsed = CsvReader.readAll(text, semicolons)
 * // Right((Vector(sensor, celsius),Vector(Vector(t-1, 21,5), Vector(t-2, 19,0))))
 * }}}
 *
 * @see
 *   [[https://zioblocks.com/reference/schema/built-in-codecs/csv/ CSV Codec Module]]
 *
 * @param delimiter
 *   The character between two cells of a row. Default: `,`.
 * @param quoteChar
 *   The character that encloses a cell holding the delimiter, this character or
 *   a line break; inside such a cell it is written twice, as RFC 4180 does with
 *   `"`. Default: `"`.
 * @param lineTerminator
 *   What [[CsvWriter]] writes after each row. Default: `\r\n`, as in RFC 4180.
 *   [[CsvReader]] does not read it: it ends a row at `\r`, `\n` or `\r\n`
 *   whatever this setting is.
 * @param hasHeader
 *   Whether [[CsvReader.readAll]] treats the first row as the header. Default:
 *   `true`. [[CsvWriter.writeAll]] writes its header row either way.
 * @param nullValue
 *   Meant as the text of a missing value, but no code reads it, so it has no
 *   effect (a known bug). Default: the empty string.
 */
final case class CsvConfig(
  delimiter: Char = ',',
  quoteChar: Char = '"',
  lineTerminator: String = "\r\n",
  hasHeader: Boolean = true,
  nullValue: String = ""
)

/** The predefined [[CsvConfig]] values. */
object CsvConfig {

  /**
   * The RFC 4180 settings: `,` between cells, `"` for quoting, `\r\n` after
   * each row, and a header row. The derived codecs always use it.
   */
  val default: CsvConfig = CsvConfig()

  /**
   * The tab-separated values (TSV) settings: [[CsvConfig.default]] with a tab
   * as the delimiter. A cell that holds a tab is quoted with `"`.
   */
  val tsv: CsvConfig = CsvConfig(delimiter = '\t')
}
