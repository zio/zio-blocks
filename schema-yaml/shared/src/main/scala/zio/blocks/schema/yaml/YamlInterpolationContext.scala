package zio.blocks.schema.yaml

private[schema] sealed trait YamlInterpolationContext

private[schema] object YamlInterpolationContext {
  case object Key      extends YamlInterpolationContext
  case object Value    extends YamlInterpolationContext
  case object InString extends YamlInterpolationContext
}
