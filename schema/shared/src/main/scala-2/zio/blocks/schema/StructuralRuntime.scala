package zio.blocks.schema

import scala.language.dynamics

object StructuralRuntime {
  final class StructuralDynamic(private val map: Map[String, Any]) extends scala.Dynamic {
    def selectDynamic(name: String): Any = map.getOrElse(name, throw new NoSuchElementException(name))
    override def toString: String = s"StructuralDynamic($map)"
  }

  def fromDynamicValue(value: DynamicValue): StructuralDynamic = {
    def toAny(dv: DynamicValue): Any = dv match {
      case DynamicValue.Primitive(p) => primitiveToAny(p)
      case DynamicValue.Record(fields) => new StructuralDynamic(fields.map { case (k, v) => k -> toAny(v) }.toMap)
      case DynamicValue.Sequence(elems) => elems.map(toAny).toVector
      case DynamicValue.Map(entries) => entries.map { case (k, v) => toAny(k) -> toAny(v) }.toMap
      case DynamicValue.Variant(name, v) => (name, toAny(v))
    }

    def primitiveToAny(p: PrimitiveValue): Any = p match {
      case PrimitiveValue.Unit       => ()
      case PrimitiveValue.Boolean(v) => v
      case PrimitiveValue.Byte(v)    => v
      case PrimitiveValue.Short(v)   => v
      case PrimitiveValue.Int(v)     => v
      case PrimitiveValue.Long(v)    => v
      case PrimitiveValue.Float(v)   => v
      case PrimitiveValue.Double(v)  => v
      case PrimitiveValue.Char(v)    => v
      case PrimitiveValue.String(v)  => v
      case PrimitiveValue.BigInt(v)  => v
      case PrimitiveValue.BigDecimal(v) => v
      case PrimitiveValue.DayOfWeek(v)   => v
      case PrimitiveValue.Duration(v)    => v
      case PrimitiveValue.Instant(v)     => v
      case PrimitiveValue.LocalDate(v)   => v
      case PrimitiveValue.LocalDateTime(v) => v
      case PrimitiveValue.LocalTime(v)   => v
      case PrimitiveValue.Month(v)       => v
      case PrimitiveValue.MonthDay(v)    => v
      case PrimitiveValue.OffsetDateTime(v) => v
      case PrimitiveValue.OffsetTime(v)   => v
      case PrimitiveValue.Period(v)       => v
      case PrimitiveValue.Year(v)         => v
      case PrimitiveValue.YearMonth(v)    => v
      case PrimitiveValue.ZoneId(v)       => v
      case PrimitiveValue.ZoneOffset(v)   => v
      case PrimitiveValue.ZonedDateTime(v) => v
      case PrimitiveValue.Currency(v)     => v
      case PrimitiveValue.UUID(v)         => v
    }

    value match {
      case DynamicValue.Record(fields) => new StructuralDynamic(fields.map { case (k, v) => k -> toAny(v) }.toMap)
      case _ => throw new IllegalArgumentException("Expected DynamicValue.Record")
    }
  }
}
