package zio.blocks.schema.json

import zio.blocks.schema.{DynamicValue, PrimitiveValue}
import DynamicValue._

import scala.util.control.NoStackTrace

object Serde {
  private sealed trait SerdeError extends Exception with NoStackTrace
  private final case class UnsupportedPrimitiveValue(primitiveValue: PrimitiveValue) extends SerdeError {
    override def getMessage: String =
      s"Unsupported primitive kind ${primitiveValue.getClass.getSimpleName} of value $primitiveValue"
  }

  def fromJson(json: String): DynamicValue = ???

  def toJson(value: DynamicValue): String = encodeDynamicValue(value)

  private final def encodeDynamicValue(value: DynamicValue): String = value match {
    case Record(fields) =>
      val encodedFields = fields.map { case (k, v) => s""""${escapeString(k)}":${encodeDynamicValue(v)}""" }
      s"{${encodedFields.mkString(",")}}"

    case Variant(caseName, value) =>
      s"""{"${escapeString(caseName)}":${encodeDynamicValue(value)}}"""

    case Sequence(elements) =>
      s"[${elements.map(encodeDynamicValue).mkString(",")}]"

    case Map(entries) =>
      val encodedEntries = entries.map { case (key, value) =>
        val jsonKey = key match {
          case DynamicValue.Primitive(PrimitiveValue.String(strKey)) => s""""${escapeString(strKey)}""""
          // TODO: Should we have keys that are of other types?
          case _ => s""""${escapeString(encodeDynamicValue(key))}""""
        }
        s"""$jsonKey:${encodeDynamicValue(value)}"""
      }
      s"{${encodedEntries.mkString(",")}}"

    case Primitive(value) => encodePrimitiveValue(value)
    case Lazy(value)      => encodeDynamicValue(value())
  }

  private final def encodePrimitiveValue(value: PrimitiveValue): String = value match {
    case PrimitiveValue.Unit              => "null"
    case PrimitiveValue.String(str)       => s""""${escapeString(str)}""""
    case PrimitiveValue.Int(n)            => n.toString
    case PrimitiveValue.Long(n)           => n.toString
    case PrimitiveValue.Float(n)          => if (n.isNaN || n.isInfinite) "null" else n.toString
    case PrimitiveValue.Double(n)         => if (n.isNaN || n.isInfinite) "null" else n.toString
    case PrimitiveValue.Boolean(b)        => b.toString
    case PrimitiveValue.Byte(value)       => value.toString
    case PrimitiveValue.Short(value)      => value.toString
    case PrimitiveValue.Char(value)       => s""""${escapeString(value.toString)}""""
    case PrimitiveValue.BigInt(value)     => value.toString
    case PrimitiveValue.BigDecimal(value) => value.toString
    case _ @primeValue                    => throw UnsupportedPrimitiveValue(primeValue)
  }

  private final def escapeString(str: String): String =
    str.flatMap {
      case '"'          => "\\\""
      case '\\'         => "\\\\"
      case '\b'         => "\\b"
      case '\f'         => "\\f"
      case '\n'         => "\\n"
      case '\r'         => "\\r"
      case '\t'         => "\\t"
      case c if c < ' ' => "\\u%04x".format(c.toInt)
      case c            => c.toString
    }
}
