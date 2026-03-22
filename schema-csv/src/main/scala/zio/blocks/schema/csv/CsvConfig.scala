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
 * Configuration for CSV parsing and generation.
 *
 * @param delimiter
 *   The character used to separate fields (default: ',')
 * @param quoteChar
 *   The character used to quote fields containing special characters (default:
 *   '"'). Escaping within quoted fields is done by doubling the quote character
 *   per RFC 4180.
 * @param lineTerminator
 *   The line terminator sequence (default: "\r\n" for RFC 4180)
 * @param hasHeader
 *   Whether the first row contains column headers (default: true)
 * @param nullValue
 *   The string representation for null values (default: "")
 */
final case class CsvConfig(
  delimiter: Char = ',',
  quoteChar: Char = '"',
  lineTerminator: String = "\r\n",
  hasHeader: Boolean = true,
  nullValue: String = ""
)

object CsvConfig {

  /**
   * Default CSV configuration following RFC 4180 standard.
   */
  val default: CsvConfig = CsvConfig()

  /**
   * Tab-separated values (TSV) configuration preset.
   *
   * Uses tab ('\t') as the delimiter while maintaining RFC 4180 defaults for
   * other settings.
   */
  val tsv: CsvConfig = CsvConfig(delimiter = '\t')
}
