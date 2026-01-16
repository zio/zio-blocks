package zio.blocks.schema.toon

/**
 * Strategy for expanding dot-separated paths in TOON input.
 */
sealed abstract class PathExpansion

object PathExpansion {

  /** No path expansion - keys are treated as literals. */
  case object Off extends PathExpansion

  /**
   * Safe path expansion - dot-separated keys are expanded to nested records.
   */
  case object Safe extends PathExpansion
}

/**
 * Configuration for [[zio.blocks.schema.toon.ToonReader]] that contains params
 * for parsing TOON input and for tuning reader behavior.
 *
 * All configuration params are already initialized to default values, but in
 * some cases they should be altered:
 *   - change the expected indentation step (default 2 spaces per level)
 *   - change the expected delimiter for inline arrays (comma, tab, or pipe)
 *   - disable strict mode to allow lenient parsing of malformed input
 *   - enable path expansion to parse dot-separated keys as nested records
 *   - set discriminator field for DynamicValue variant decoding
 *
 * @param indent
 *   the expected number of spaces per indentation level (default: 2)
 * @param delimiter
 *   the expected delimiter character for inline arrays (default: Comma)
 * @param strict
 *   whether to enforce strict TOON parsing (default: true)
 * @param expandPaths
 *   strategy for expanding dot-separated paths (default: Off)
 * @param discriminatorField
 *   optional field name to use as discriminator for DynamicValue variants
 *   (default: None, which decodes as Record)
 */
class ReaderConfig private (
  val indent: Int,
  val delimiter: Delimiter,
  val strict: Boolean,
  val expandPaths: PathExpansion,
  val discriminatorField: Option[String]
) extends Serializable {

  def withIndent(indent: Int): ReaderConfig = {
    if (indent < 0) throw new IllegalArgumentException("'indent' should be not less than 0")
    copy(indent = indent)
  }

  def withDelimiter(delimiter: Delimiter): ReaderConfig =
    copy(delimiter = delimiter)

  def withStrict(strict: Boolean): ReaderConfig =
    copy(strict = strict)

  def withExpandPaths(expandPaths: PathExpansion): ReaderConfig =
    copy(expandPaths = expandPaths)

  def withDiscriminatorField(discriminatorField: Option[String]): ReaderConfig =
    copy(discriminatorField = discriminatorField)

  private[this] def copy(
    indent: Int = indent,
    delimiter: Delimiter = delimiter,
    strict: Boolean = strict,
    expandPaths: PathExpansion = expandPaths,
    discriminatorField: Option[String] = discriminatorField
  ): ReaderConfig =
    new ReaderConfig(
      indent = indent,
      delimiter = delimiter,
      strict = strict,
      expandPaths = expandPaths,
      discriminatorField = discriminatorField
    )
}

object ReaderConfig
    extends ReaderConfig(
      indent = 2,
      delimiter = Delimiter.Comma,
      strict = true,
      expandPaths = PathExpansion.Off,
      discriminatorField = None
    )
