package zio.blocks.schema.json

import zio.blocks.schema.{DynamicValue, PrimitiveValue}
import zio.blocks.schema.DynamicValue._

object dynamicValueFromJson extends (String => DynamicValue) {
  def apply(rawJson: String): DynamicValue = unsafeParse(rawJson)

  private def unsafeParse(rawJson: String): DynamicValue = (for {
    json        <- parseJson(rawJson)
    dynamicValue = jsonToDynamicValue(json)
  } yield dynamicValue).toTry.get

  private final def isValidLong(n: Double)   = { val l = n.toLong; l.toDouble == n && l != Long.MaxValue }
  private final def isValidFloat(n: Double)  = n.toFloat.toDouble == n
  private final def isValidDouble(n: Double) = !java.lang.Double.isNaN(n)

  private def jsonToDynamicValue(json: Json): DynamicValue = json match {
    case JsonNull                                  => Primitive(PrimitiveValue.Unit)
    case JsonBool(value)                           => Primitive(PrimitiveValue.Boolean(value))
    case JsonString(value)                         => Primitive(PrimitiveValue.String(value))
    case JsonArray(items)                          => Sequence(items.map(jsonToDynamicValue).toIndexedSeq)
    case JsonNumber(value) if value.isValidInt     => Primitive(PrimitiveValue.Int(value.toInt))
    case JsonNumber(value) if isValidFloat(value)  => Primitive(PrimitiveValue.Float(value.toFloat))
    case JsonNumber(value) if isValidDouble(value) => Primitive(PrimitiveValue.Double(value))
    case JsonNumber(value) if isValidLong(value)   => Primitive(PrimitiveValue.Long(value.toLong))
    case JsonNumber(value)                         =>
      // TODO: This conversion can and should be improved
      Primitive(PrimitiveValue.Double(value))

    case JsonObject(fields) =>
      Record(
        fields.toIndexedSeq.map { case (key, value) => key -> jsonToDynamicValue(value) }
      )
  }

}
