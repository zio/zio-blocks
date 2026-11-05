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
 * Joins rows of cell strings into CSV text, with the delimiter, quote character
 * and line terminator of a [[CsvConfig]]. Use it to write a header line, or a
 * format other than the one the derived codecs use, such as TSV.
 *
 * Cells are escaped as RFC 4180 describes:
 *   - A cell that holds the delimiter, the quote character, `\r` or `\n` is
 *     enclosed in quote characters.
 *   - A quote character inside such a cell is written twice.
 *   - Any other cell, leading and trailing spaces included, is written as it
 *     is.
 *
 * {{{
 * val row = CsvWriter.writeRow(IndexedSeq("t-1", "hall, north", "21.5"), CsvConfig.tsv)
 * // "t-1\thall, north\t21.5\r\n"
 * val quoted = CsvWriter.writeRow(IndexedSeq("t-1", "hall, north"), CsvConfig.default)
 * // "t-1,\"hall, north\"\r\n"
 * }}}
 *
 * @see
 *   [[https://zioblocks.com/reference/schema/built-in-codecs/csv/ CSV Codec Module]]
 */
object CsvWriter {

  /**
   * Writes one row: the escaped cells separated by `config.delimiter`, then
   * `config.lineTerminator`. An empty `fields` gives the line terminator alone.
   */
  def writeRow(fields: IndexedSeq[String], config: CsvConfig): String = {
    val sb = new java.lang.StringBuilder
    var i  = 0
    while (i < fields.length) {
      if (i > 0) sb.append(config.delimiter)
      sb.append(escapeField(fields(i), config))
      i += 1
    }
    sb.append(config.lineTerminator)
    sb.toString
  }

  /**
   * Writes a header row; it does the same as `writeRow` and names the intent.
   * Pass a [[CsvCodec]]'s `headerNames` to get the header line its rows match.
   */
  def writeHeader(names: IndexedSeq[String], config: CsvConfig): String =
    writeRow(names, config)

  /**
   * Writes a whole document: the header row, then each of `rows`, each line
   * ending with `config.lineTerminator`. It writes the header row even when
   * [[CsvConfig#hasHeader]] is `false`, and does not check that the rows have
   * as many cells as the header.
   */
  def writeAll(header: IndexedSeq[String], rows: Iterable[IndexedSeq[String]], config: CsvConfig): String = {
    val sb = new java.lang.StringBuilder
    sb.append(writeHeader(header, config))
    val iter = rows.iterator
    while (iter.hasNext) {
      sb.append(writeRow(iter.next(), config))
    }
    sb.toString
  }

  /**
   * Returns `field` enclosed in `config.quoteChar`, with each quote character
   * inside it doubled, when it holds the delimiter, the quote character, `\r`
   * or `\n`; otherwise returns it unchanged.
   */
  private def escapeField(field: String, config: CsvConfig): String = {
    val delimiter    = config.delimiter
    val quoteChar    = config.quoteChar
    var needsQuoting = false
    var hasQuote     = false
    var i            = 0
    while (i < field.length) {
      val c = field.charAt(i)
      if (c == delimiter || c == '\r' || c == '\n') {
        needsQuoting = true
      } else if (c == quoteChar) {
        needsQuoting = true
        hasQuote = true
      }
      i += 1
    }
    if (!needsQuoting) field
    else {
      val sb = new java.lang.StringBuilder(field.length + 2)
      sb.append(quoteChar)
      if (hasQuote) {
        i = 0
        while (i < field.length) {
          val c = field.charAt(i)
          if (c == quoteChar) sb.append(quoteChar)
          sb.append(c)
          i += 1
        }
      } else {
        sb.append(field)
      }
      sb.append(quoteChar)
      sb.toString
    }
  }
}
