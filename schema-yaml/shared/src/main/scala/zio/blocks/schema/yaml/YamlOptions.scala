package zio.blocks.schema.yaml

/**
 * Configuration for [[YamlWriter]] output formatting.
 *
 * @param indentStep
 *   number of spaces per indentation level (default: 2)
 * @param flowStyle
 *   when true, emit collections in inline flow style
 * @param documentMarkers
 *   when true, prepend `---` document start marker
 */
final case class YamlOptions(
  indentStep: Int = 2,
  flowStyle: Boolean = false,
  documentMarkers: Boolean = false
)

object YamlOptions {
  val default: YamlOptions = YamlOptions()
  val pretty: YamlOptions  = YamlOptions(indentStep = 2, documentMarkers = true)
  val flow: YamlOptions    = YamlOptions(flowStyle = true)
}
