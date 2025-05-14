package zio.blocks.schema.json

import zio.blocks.schema.{DynamicValue, PrimitiveValue}
import zio.blocks.schema.DynamicValue.{Lazy, Map, Primitive, Record, Sequence, Variant}
import zio.blocks.schema.json.Serde.UnsupportedPrimitiveValue

object toJson extends (DynamicValue => String) {
  import StringOps._

  override def apply(value: DynamicValue): String = encodeDynamicValue(value)

  private final def encodeDynamicValue(value: DynamicValue): String = value match {
    case Record(fields) =>
      val encodedFields = fields.map { case (k, v) => s""""${k.escape}":${encodeDynamicValue(v)}""" }
      s"{${encodedFields.mkString(",")}}"

    case Variant(caseName, value) =>
      s"""{"${caseName.escape}":${encodeDynamicValue(value)}}"""

    case Sequence(elements) =>
      s"[${elements.map(encodeDynamicValue).mkString(",")}]"

    case Map(entries) =>
      val encodedEntries = entries.map { case (key, value) =>
        val jsonKey = key match {
          case DynamicValue.Primitive(PrimitiveValue.String(strKey)) => s""""${strKey.escape}""""
          // TODO: Should we have keys that are of other types?
          case _ => s""""${encodeDynamicValue(key).escape}""""
        }
        s"""$jsonKey:${encodeDynamicValue(value)}"""
      }
      s"{${encodedEntries.mkString(",")}}"

    case Primitive(value) => encodePrimitiveValue(value)
    case Lazy(value)      => encodeDynamicValue(value())
  }

  private final def encodePrimitiveValue(value: PrimitiveValue): String = value match {
    case PrimitiveValue.Unit              => "null"
    case PrimitiveValue.String(str)       => s""""${str.escape}""""
    case PrimitiveValue.Int(n)            => n.toString
    case PrimitiveValue.Long(n)           => n.toString
    case PrimitiveValue.Float(n)          => if (n.isNaN || n.isInfinite) "null" else n.toString
    case PrimitiveValue.Double(n)         => if (n.isNaN || n.isInfinite) "null" else n.toString
    case PrimitiveValue.Boolean(b)        => b.toString
    case PrimitiveValue.Byte(value)       => value.toString
    case PrimitiveValue.Short(value)      => value.toString
    case PrimitiveValue.Char(value)       => s""""${value.toString.escape}""""
    case PrimitiveValue.BigInt(value)     => value.toString
    case PrimitiveValue.BigDecimal(value) => value.toString

    // TODO: Add more primitive types here or get error.
    case _ @primeValue => throw UnsupportedPrimitiveValue(primeValue)
  }
}
