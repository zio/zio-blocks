package zio.blocks.schema.yaml

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
