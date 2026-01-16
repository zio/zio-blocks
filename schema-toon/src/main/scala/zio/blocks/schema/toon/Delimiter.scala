package zio.blocks.schema.toon

/**
 * Delimiter used to separate values in TOON inline arrays.
 *
 * @param char
 *   the delimiter character
 */
sealed abstract class Delimiter(val char: Char)

object Delimiter {
  case object Comma extends Delimiter(',')
  case object Tab   extends Delimiter('\t')
  case object Pipe  extends Delimiter('|')

  /**
   * Special delimiter that won't match any character in values - used for
   * reading complete values.
   */
  case object None extends Delimiter('\u0000')
}
