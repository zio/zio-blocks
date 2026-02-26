package zio.blocks.schema.csv

/**
 * CSV row parsing utility using a state machine approach with proper error
 * reporting.
 *
 * Provides methods to parse individual rows, headers, and complete CSV
 * documents. Parsing follows RFC 4180 rules:
 *   - Unquoted fields end at delimiters, newlines, or EOF
 *   - Quoted fields preserve delimiters and newlines as literal content
 *   - Quote characters within quoted fields are escaped by doubling them
 *   - Handles CR, LF, and CRLF line endings uniformly
 */
object CsvReader {

  /**
   * Parser states for the CSV state machine.
   */
  private final val FieldStart      = 0
  private final val InUnquotedField = 1
  private final val InQuotedField   = 2
  private final val AfterQuote      = 3

  /**
   * Parses a single CSV row starting at the given offset.
   *
   * @param input
   *   the full CSV input string
   * @param offset
   *   the character position to start parsing from
   * @param config
   *   the CSV configuration controlling delimiter and quoting
   * @return
   *   `Right((fields, newOffset))` on success where `fields` contains the
   *   parsed field values and `newOffset` is the position after the consumed
   *   row, or `Left(CsvError.ParseError(...))` on malformed input
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
   * Parses just the header row (first row) from the CSV input.
   *
   * @param input
   *   the full CSV input string
   * @param config
   *   the CSV configuration
   * @return
   *   `Right((headerFields, offset))` on success, or
   *   `Left(CsvError.ParseError(...))` on malformed input
   */
  def readHeader(input: String, config: CsvConfig): Either[CsvError, (IndexedSeq[String], Int)] =
    readRow(input, 0, config)

  /**
   * Parses a complete CSV document including header and all data rows.
   *
   * @param input
   *   the full CSV input string
   * @param config
   *   the CSV configuration
   * @return
   *   `Right((header, dataRows))` on success where `header` contains column
   *   names (empty if `config.hasHeader` is false) and `dataRows` contains all
   *   data rows, or `Left(CsvError.ParseError(...))` on malformed input
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
          } else {
            // Safety: no progress made, break to avoid infinite loop
            offset = input.length
          }
          offset = newOff
      }
    }

    Right((header, rows.result()))
  }
}
