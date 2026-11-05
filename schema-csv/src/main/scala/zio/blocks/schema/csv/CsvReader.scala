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
 * Splits CSV text into rows of cell strings, with the delimiter and quote
 * character of a [[CsvConfig]]. Use it to read a whole document, its header
 * included, or a format other than the one the derived codecs use, such as TSV;
 * then decode each row's cells yourself or with a [[CsvCodec]].
 *
 * It follows RFC 4180:
 *   - An unquoted cell ends at the delimiter, a line break or the end of the
 *     input. A quote character inside it is kept as text.
 *   - A quoted cell keeps delimiters and line breaks as text, and a doubled
 *     quote character inside it stands for one.
 *   - A row ends at `\r`, `\n` or `\r\n`, whatever [[CsvConfig#lineTerminator]]
 *     is.
 *
 * Cells are not trimmed, and rows may have different numbers of cells. Errors
 * are [[CsvError.ParseError]] values in a `Left`; nothing is thrown.
 *
 * @see
 *   [[https://zioblocks.com/reference/schema/built-in-codecs/csv/ CSV Codec Module]]
 */
object CsvReader {

  /** The states of the row parser. */
  private final val FieldStart      = 0
  private final val InUnquotedField = 1
  private final val InQuotedField   = 2
  private final val AfterQuote      = 3

  /**
   * Reads one row of `input` starting at `offset`. Call it again with the
   * returned offset to read the next row. At or past the end of `input` it
   * returns no cells and `offset` unchanged; an empty line gives one empty
   * cell. The row and column of an error count from `offset`, not from the
   * start of `input` (a known bug).
   *
   * {{{
   * val text  = "sensor,celsius\r\nt-1,\"21,5\"\r\n"
   * val first = CsvReader.readRow(text, 0, CsvConfig.default)
   * // Right((Vector(sensor, celsius),16))
   * val second = CsvReader.readRow(text, 16, CsvConfig.default)
   * // Right((Vector(t-1, 21,5),28))
   * }}}
   *
   * @param input
   *   The CSV text.
   * @param offset
   *   The index of the first character of the row.
   * @param config
   *   The delimiter and quote character to use.
   * @return
   *   The row's cells and the index just past its line break, or a
   *   [[CsvError.ParseError]] when the row is malformed.
   */
  def readRow(input: String, offset: Int, config: CsvConfig): Either[CsvError, (IndexedSeq[String], Int)] = {
    val len       = input.length
    val delimiter = config.delimiter
    val quoteChar = config.quoteChar

    if (offset >= len) return Right((Vector.empty, offset))

    val fields = Vector.newBuilder[String]
    val sb     = new java.lang.StringBuilder
    var state  = FieldStart
    var pos    = offset
    var row    = 1
    var col    = 1
    var done   = false

    while (pos <= len && !done) {
      if (pos == len) {
        state match {
          case FieldStart =>
            fields += sb.toString
            sb.setLength(0)
            done = true
          case InUnquotedField =>
            fields += sb.toString
            sb.setLength(0)
            done = true
          case InQuotedField =>
            return Left(CsvError.ParseError("Unclosed quoted field", row, col))
          case AfterQuote =>
            fields += sb.toString
            sb.setLength(0)
            done = true
        }
      } else {
        val c = input.charAt(pos)
        state match {
          case FieldStart =>
            if (c == quoteChar) {
              state = InQuotedField
              pos += 1
              col += 1
            } else if (c == delimiter) {
              fields += sb.toString
              sb.setLength(0)
              pos += 1
              col += 1
              // state stays FieldStart
            } else if (c == '\r') {
              fields += sb.toString
              sb.setLength(0)
              pos += 1
              if (pos < len && input.charAt(pos) == '\n') pos += 1
              done = true
            } else if (c == '\n') {
              fields += sb.toString
              sb.setLength(0)
              pos += 1
              done = true
            } else {
              sb.append(c)
              state = InUnquotedField
              pos += 1
              col += 1
            }

          case InUnquotedField =>
            if (c == delimiter) {
              fields += sb.toString
              sb.setLength(0)
              state = FieldStart
              pos += 1
              col += 1
            } else if (c == '\r') {
              fields += sb.toString
              sb.setLength(0)
              pos += 1
              if (pos < len && input.charAt(pos) == '\n') pos += 1
              done = true
            } else if (c == '\n') {
              fields += sb.toString
              sb.setLength(0)
              pos += 1
              done = true
            } else {
              sb.append(c)
              pos += 1
              col += 1
            }

          case InQuotedField =>
            if (c == quoteChar) {
              state = AfterQuote
              pos += 1
              col += 1
            } else {
              sb.append(c)
              pos += 1
              if (c == '\n') {
                row += 1
                col = 1
              } else if (c == '\r') {
                row += 1
                col = 1
                // Don't count \r\n as two row increments
                if (pos < len && input.charAt(pos) == '\n') {
                  sb.append('\n')
                  pos += 1
                }
              } else {
                col += 1
              }
            }

          case AfterQuote =>
            if (c == quoteChar) {
              // Escaped quote
              sb.append(quoteChar)
              state = InQuotedField
              pos += 1
              col += 1
            } else if (c == delimiter) {
              fields += sb.toString
              sb.setLength(0)
              state = FieldStart
              pos += 1
              col += 1
            } else if (c == '\r') {
              fields += sb.toString
              sb.setLength(0)
              pos += 1
              if (pos < len && input.charAt(pos) == '\n') pos += 1
              done = true
            } else if (c == '\n') {
              fields += sb.toString
              sb.setLength(0)
              pos += 1
              done = true
            } else {
              return Left(CsvError.ParseError(s"Unexpected character '${c}' after closing quote", row, col))
            }
        }
      }
    }

    Right((fields.result(), pos))
  }

  /**
   * Reads the first row of `input`, the same as `readRow(input, 0, config)`,
   * and returns its cells and the index where the next row starts. It reads
   * that row whatever [[CsvConfig#hasHeader]] is.
   */
  def readHeader(input: String, config: CsvConfig): Either[CsvError, (IndexedSeq[String], Int)] =
    readRow(input, 0, config)

  /**
   * Reads a whole document: the header row when [[CsvConfig#hasHeader]] is
   * `true`, then every data row. It stops at the first malformed row and
   * returns its error, whose row number counts from the start of that row, not
   * of the document (a known bug).
   *
   * Empty input gives no header and no rows. A blank line between rows becomes
   * a row with one empty cell; a blank last line is dropped.
   *
   * {{{
   * val lines  = "sensor,celsius\nt-1,21.5\nt-2,19.0\n"
   * val parsed = CsvReader.readAll(lines, CsvConfig.default)
   * // Right((Vector(sensor, celsius),Vector(Vector(t-1, 21.5), Vector(t-2, 19.0))))
   * }}}
   *
   * @return
   *   The header (empty when `hasHeader` is `false`) and the data rows, or the
   *   [[CsvError]] of the first malformed row.
   */
  def readAll(
    input: String,
    config: CsvConfig
  ): Either[CsvError, (IndexedSeq[String], IndexedSeq[IndexedSeq[String]])] = {
    if (input.isEmpty) {
      return Right((Vector.empty, Vector.empty))
    }

    var offset                     = 0
    val header: IndexedSeq[String] = if (config.hasHeader) {
      readRow(input, offset, config) match {
        case Left(err)               => return Left(err)
        case Right((fields, newOff)) =>
          offset = newOff
          fields
      }
    } else {
      Vector.empty
    }

    val rows = Vector.newBuilder[IndexedSeq[String]]
    while (offset < input.length) {
      readRow(input, offset, config) match {
        case Left(err)               => return Left(err)
        case Right((fields, newOff)) =>
          // Skip trailing empty rows (e.g. trailing newline producing empty row)
          if (newOff > offset) {
            // Only add non-empty rows — a single empty field from trailing newline should be skipped
            if (fields.length > 1 || (fields.length == 1 && fields(0).nonEmpty) || newOff < input.length) {
              rows += fields
            }
            offset = newOff
          } else {
            // Safety: no progress made, break to avoid infinite loop
            offset = input.length
          }
      }
    }

    Right((header, rows.result()))
  }
}
