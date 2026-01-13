package zio.blocks.schema.toon

/**
 * Represents the delimiter used to separate values in inline arrays and tabular
 * rows.
 *
 * TOON supports three delimiters:
 *   - '''Comma''' (default): `key[3]: a,b,c`
 *   - '''Tab''': `key[3\t]: a\tb\tc` (indicated by tab in header brackets)
 *   - '''Pipe''': `key[3|]: a|b|c` (indicated by pipe in header brackets)
 *
 * The active delimiter affects string quoting: strings containing the active
 * delimiter MUST be quoted.
 *
 * @see
 *   [[https://github.com/toon-format/spec TOON Specification]]
 */
sealed abstract class Delimiter(val char: Char, val headerMarker: String) {

  /**
   * Returns true if the given string contains this delimiter character.
   */
  def containedIn(s: String): Boolean = s.indexOf(char) >= 0
}

object Delimiter {

  /**
   * Comma delimiter (default). Header omits delimiter symbol.
   *
   * Example: `items[3]: a,b,c`
   */
  case object Comma extends Delimiter(',', "")

  /**
   * Tab delimiter. Header includes tab character inside brackets.
   *
   * Example: `items[3\t]: a\tb\tc`
   */
  case object Tab extends Delimiter('\t', "\t")

  /**
   * Pipe delimiter. Header includes pipe character inside brackets.
   *
   * Example: `items[3|]: a|b|c`
   */
  case object Pipe extends Delimiter('|', "|")

  /**
   * Parse a delimiter marker from a header bracket content.
   *
   * @param marker
   *   the marker string from the header (empty, tab, or pipe)
   * @return
   *   the corresponding Delimiter
   */
  def fromMarker(marker: String): Delimiter = marker match {
    case ""   => Comma
    case "\t" => Tab
    case "|"  => Pipe
    case _    => Comma // fallback to default
  }
}
