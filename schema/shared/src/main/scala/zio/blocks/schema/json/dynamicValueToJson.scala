package zio.blocks.schema.json

import zio.blocks.schema.{DynamicValue, PrimitiveValue}
import zio.blocks.schema.DynamicValue._

private[json] final case class UnsupportedPrimitiveValue(primitiveValue: PrimitiveValue) extends Throwable {
  override def getMessage: String =
    s"Unsupported primitive kind ${primitiveValue.getClass.getSimpleName} of value $primitiveValue"
}

object dynamicValueToJson extends (DynamicValue => String) {
  override def apply(value: DynamicValue): String = encodeDynamicValue(value)

  private final def esc(raw: String): String = raw.flatMap {
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

  private final def encodeDynamicValue(value: DynamicValue): String = value match {
    case Record(fields) =>
      val encodedFields = fields.sortBy(_._1).map { case (k, v) => s""""${esc(k)}":${encodeDynamicValue(v)}""" }
      s"{${encodedFields.mkString(",")}}"

    case Variant(caseName, value) =>
      s"""{"${esc(caseName)}":${encodeDynamicValue(value)}}"""

    case Sequence(elements) =>
      s"[${elements.map(encodeDynamicValue).mkString(",")}]"

    case Map(entries) =>
      val encodedEntries = entries.map { case (key, value) =>
        val jsonKey = key match {
          case DynamicValue.Primitive(PrimitiveValue.String(strKey)) => s""""${esc(strKey)}""""
          // TODO: Should we have keys that are of other types?
          case _ => s""""${esc(encodeDynamicValue(key))}""""
        }
        s"""$jsonKey:${encodeDynamicValue(value)}"""
      }
      s"{${encodedEntries.mkString(",")}}"

    case Primitive(value) => encodePrimitiveValue(value)
  }

  private final def encodePrimitiveValue(value: PrimitiveValue): String = value match {
    case PrimitiveValue.Unit                  => "null"
    case PrimitiveValue.String(str)           => s""""${esc(str)}""""
    case PrimitiveValue.Int(n)                => n.toString
    case PrimitiveValue.Long(n)               => n.toString
    case PrimitiveValue.Float(n)              => if (n.isNaN || n.isInfinite) "null" else n.toString
    case PrimitiveValue.Double(n)             => if (n.isNaN || n.isInfinite) "null" else n.toString
    case PrimitiveValue.Boolean(b)            => b.toString
    case PrimitiveValue.Byte(value)           => value.toString
    case PrimitiveValue.Short(value)          => value.toString
    case PrimitiveValue.Char(value)           => s""""${esc(value.toString)}""""
    case PrimitiveValue.BigInt(value)         => value.toString
    case PrimitiveValue.BigDecimal(value)     => value.toString
    case PrimitiveValue.DayOfWeek(value)      => s""""$value""""
    case PrimitiveValue.Duration(value)       => s""""$value""""
    case PrimitiveValue.Instant(value)        => s""""$value""""
    case PrimitiveValue.LocalDate(value)      => s""""$value""""
    case PrimitiveValue.LocalDateTime(value)  => s""""$value""""
    case PrimitiveValue.LocalTime(value)      => s""""$value""""
    case PrimitiveValue.Month(value)          => s""""$value""""
    case PrimitiveValue.MonthDay(value)       => s""""$value""""
    case PrimitiveValue.OffsetDateTime(value) => s""""$value""""
    case PrimitiveValue.OffsetTime(value)     => s""""$value""""
    case PrimitiveValue.Period(value)         => s""""$value""""
    case PrimitiveValue.Year(value)           => s""""$value""""
    case PrimitiveValue.YearMonth(value)      => s""""$value""""
    case PrimitiveValue.ZoneId(value)         => s""""${esc(value.toString)}""""
    case PrimitiveValue.ZoneOffset(value)     => s""""$value""""
    case PrimitiveValue.ZonedDateTime(value)  => s""""${esc(value.toString)}""""
    case PrimitiveValue.Currency(value)       => s""""${esc(value.toString)}""""
    case PrimitiveValue.UUID(value)           => s""""$value""""
  }
}
