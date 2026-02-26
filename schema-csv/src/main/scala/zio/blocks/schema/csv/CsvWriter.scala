package zio.blocks.schema.csv

/**
 * CSV row serialization utility with RFC 4180-compliant field escaping.
 *
 * Provides methods to serialize individual rows, headers, and complete CSV
 * documents. All field values are escaped according to RFC 4180 rules:
 *   - Fields containing the delimiter, quote character, or newlines are quoted
 *   - Quote characters within fields are escaped by doubling them
 *   - Fields not requiring escaping are written as-is
 */
object CsvWriter {

  /**
   * Serializes a single row of fields as a CSV line.
   *
   * @param fields
   *   the field values to serialize
   * @param config
   *   the CSV configuration controlling delimiter, quoting, and line
   *   termination
   * @return
   *   a CSV-formatted row string terminated by `config.lineTerminator`
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
   * Serializes a header row. Semantically identical to `writeRow` but
   * communicates intent that the output represents column names.
   *
   * @param names
   *   the column header names
   * @param config
   *   the CSV configuration
   * @return
   *   a CSV-formatted header row string terminated by `config.lineTerminator`
   */
  def writeHeader(names: IndexedSeq[String], config: CsvConfig): String =
    writeRow(names, config)

  /**
   * Serializes a complete CSV document with a header row followed by data rows.
   *
   * @param header
   *   the column header names
   * @param rows
   *   the data rows, each an indexed sequence of field values
   * @param config
   *   the CSV configuration
   * @return
   *   a complete CSV document string with header and all data rows
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
   * Escapes a single field value per RFC 4180 rules.
   *
   * If the field contains the delimiter, the quote character, or any newline
   * character (CR or LF), it is surrounded with `config.quoteChar` and any
   * occurrences of `config.quoteChar` within the field are doubled.
   *
   * @param field
   *   the raw field value
   * @param config
   *   the CSV configuration
   * @return
   *   the escaped field string, quoted if necessary
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
