package zio.blocks.template

import zio.blocks.chunk.Chunk

final class PartialMultiAttribute(val name: String, val separator: Dom.AttributeSeparator) {

  def :=(value: String): Dom.Attribute =
    Dom.Attribute.KeyValue(name, Dom.AttributeValue.StringValue(value))

  def :=(value1: String, value2: String, rest: String*): Dom.Attribute =
    Dom.Attribute.KeyValue(name, Dom.AttributeValue.MultiValue(Chunk.from(value1 +: value2 +: rest), separator))

  def :=(values: Chunk[String]): Dom.Attribute =
    Dom.Attribute.KeyValue(name, Dom.AttributeValue.MultiValue(values, separator))

  def apply(values: String*): Dom.Attribute =
    if (values.length == 1) Dom.Attribute.KeyValue(name, Dom.AttributeValue.StringValue(values.head))
    else Dom.Attribute.KeyValue(name, Dom.AttributeValue.MultiValue(Chunk.from(values), separator))

  def apply(values: Iterable[String]): Dom.Attribute =
    Dom.Attribute.KeyValue(name, Dom.AttributeValue.MultiValue(Chunk.from(values), separator))
}
