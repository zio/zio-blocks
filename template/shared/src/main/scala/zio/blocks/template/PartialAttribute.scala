package zio.blocks.template

final class PartialAttribute(val attrName: String) extends Modifier {

  def :=(value: String): Dom.Attribute =
    Dom.Attribute.KeyValue(attrName, Dom.AttributeValue.StringValue(value))

  def :=(value: Int): Dom.Attribute =
    Dom.Attribute.KeyValue(attrName, Dom.AttributeValue.StringValue(value.toString))

  def :=(value: Long): Dom.Attribute =
    Dom.Attribute.KeyValue(attrName, Dom.AttributeValue.StringValue(value.toString))

  def :=(value: Double): Dom.Attribute =
    Dom.Attribute.KeyValue(attrName, Dom.AttributeValue.StringValue(value.toString))

  def :=(value: Boolean): Dom.Attribute =
    Dom.Attribute.KeyValue(attrName, Dom.AttributeValue.BooleanValue(value))

  def :=(value: Js): Dom.Attribute =
    Dom.Attribute.KeyValue(attrName, Dom.AttributeValue.JsValue(value))

  def :=(values: Vector[String]): Dom.Attribute =
    Dom.Attribute.KeyValue(attrName, Dom.AttributeValue.MultiValue(values, ' '))

  def withSeparator(values: Vector[String], separator: Char): Dom.Attribute =
    Dom.Attribute.KeyValue(attrName, Dom.AttributeValue.MultiValue(values, separator))

  def applyTo(element: Dom.Element): Dom.Element =
    element.copy(attributes = element.attributes :+ Dom.Attribute.BooleanAttribute(attrName))
}
