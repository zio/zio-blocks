package zio.blocks.schema.csv

/**
 * Configuration for CSV parsing and generation.
 *
 * @param delimiter
 *   The character used to separate fields (default: ',')
 * @param quoteChar
 *   The character used to quote fields containing special characters (default:
 *   '"')
 * @param escapeChar
 *   The character used to escape quote characters within quoted fields
 *   (default: '"')
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
  escapeChar: Char = '"',
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
