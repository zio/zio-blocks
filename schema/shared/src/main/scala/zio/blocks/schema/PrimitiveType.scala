package zio.blocks.schema

sealed trait PrimitiveType[A] {
  def validation: Validation[A]

  def toDynamicValue(value: A): DynamicValue
}

object PrimitiveType {
  sealed trait Val[A <: AnyVal] extends PrimitiveType[A]

  sealed trait Ref[A <: AnyRef] extends PrimitiveType[A]

  case object Unit extends Val[scala.Unit] {
    def validation: Validation[scala.Unit] = Validation.None

    def toDynamicValue(value: scala.Unit): DynamicValue = DynamicValue.Primitive(PrimitiveValue.Unit)
  }

  case class Boolean(validation: Validation[scala.Boolean]) extends Val[scala.Boolean] {
    def toDynamicValue(value: scala.Boolean): DynamicValue = DynamicValue.Primitive(PrimitiveValue.Boolean(value))
  }

  case class Byte(validation: Validation[scala.Byte]) extends Val[scala.Byte] {
    def toDynamicValue(value: scala.Byte): DynamicValue = DynamicValue.Primitive(PrimitiveValue.Byte(value))
  }

  case class Short(validation: Validation[scala.Short]) extends Val[scala.Short] {
    def toDynamicValue(value: scala.Short): DynamicValue = DynamicValue.Primitive(PrimitiveValue.Short(value))
  }

  case class Int(validation: Validation[scala.Int]) extends Val[scala.Int] {
    def toDynamicValue(value: scala.Int): DynamicValue = DynamicValue.Primitive(PrimitiveValue.Int(value))
  }

  case class Long(validation: Validation[scala.Long]) extends Val[scala.Long] {
    def toDynamicValue(value: scala.Long): DynamicValue = DynamicValue.Primitive(PrimitiveValue.Long(value))
  }

  case class Float(validation: Validation[scala.Float]) extends Val[scala.Float] {
    def toDynamicValue(value: scala.Float): DynamicValue = DynamicValue.Primitive(PrimitiveValue.Float(value))
  }

  case class Double(validation: Validation[scala.Double]) extends Val[scala.Double] {
    def toDynamicValue(value: scala.Double): DynamicValue = DynamicValue.Primitive(PrimitiveValue.Double(value))
  }

  case class Char(validation: Validation[scala.Char]) extends Val[scala.Char] {
    def toDynamicValue(value: scala.Char): DynamicValue = DynamicValue.Primitive(PrimitiveValue.Char(value))
  }

  case class String(validation: Validation[Predef.String]) extends Ref[Predef.String] {
    def toDynamicValue(value: Predef.String): DynamicValue = DynamicValue.Primitive(PrimitiveValue.String(value))
  }

  case class BigInt(validation: Validation[scala.BigInt]) extends Ref[scala.BigInt] {
    def toDynamicValue(value: scala.BigInt): DynamicValue = DynamicValue.Primitive(PrimitiveValue.BigInt(value))
  }

  case class BigDecimal(validation: Validation[scala.BigDecimal]) extends Ref[scala.BigDecimal] {
    def toDynamicValue(value: scala.BigDecimal): DynamicValue = DynamicValue.Primitive(PrimitiveValue.BigDecimal(value))
  }

  case class DayOfWeek(validation: Validation[java.time.DayOfWeek]) extends Ref[java.time.DayOfWeek] {
    def toDynamicValue(value: java.time.DayOfWeek): DynamicValue =
      DynamicValue.Primitive(PrimitiveValue.DayOfWeek(value))
  }

  case class Duration(validation: Validation[java.time.Duration]) extends Ref[java.time.Duration] {
    def toDynamicValue(value: java.time.Duration): DynamicValue = DynamicValue.Primitive(PrimitiveValue.Duration(value))
  }

  case class Instant(validation: Validation[java.time.Instant]) extends Ref[java.time.Instant] {
    def toDynamicValue(value: java.time.Instant): DynamicValue = DynamicValue.Primitive(PrimitiveValue.Instant(value))
  }

  case class LocalDate(validation: Validation[java.time.LocalDate]) extends Ref[java.time.LocalDate] {
    def toDynamicValue(value: java.time.LocalDate): DynamicValue =
      DynamicValue.Primitive(PrimitiveValue.LocalDate(value))
  }

  case class LocalDateTime(validation: Validation[java.time.LocalDateTime]) extends Ref[java.time.LocalDateTime] {
    def toDynamicValue(value: java.time.LocalDateTime): DynamicValue =
      DynamicValue.Primitive(PrimitiveValue.LocalDateTime(value))
  }

  case class LocalTime(validation: Validation[java.time.LocalTime]) extends Ref[java.time.LocalTime] {
    def toDynamicValue(value: java.time.LocalTime): DynamicValue =
      DynamicValue.Primitive(PrimitiveValue.LocalTime(value))
  }

  case class Month(validation: Validation[java.time.Month]) extends Ref[java.time.Month] {
    def toDynamicValue(value: java.time.Month): DynamicValue = DynamicValue.Primitive(PrimitiveValue.Month(value))
  }

  case class MonthDay(validation: Validation[java.time.MonthDay]) extends Ref[java.time.MonthDay] {
    def toDynamicValue(value: java.time.MonthDay): DynamicValue = DynamicValue.Primitive(PrimitiveValue.MonthDay(value))
  }

  case class OffsetDateTime(validation: Validation[java.time.OffsetDateTime]) extends Ref[java.time.OffsetDateTime] {
    def toDynamicValue(value: java.time.OffsetDateTime): DynamicValue =
      DynamicValue.Primitive(PrimitiveValue.OffsetDateTime(value))
  }

  case class OffsetTime(validation: Validation[java.time.OffsetTime]) extends Ref[java.time.OffsetTime] {
    def toDynamicValue(value: java.time.OffsetTime): DynamicValue =
      DynamicValue.Primitive(PrimitiveValue.OffsetTime(value))
  }

  case class Period(validation: Validation[java.time.Period]) extends Ref[java.time.Period] {
    def toDynamicValue(value: java.time.Period): DynamicValue = DynamicValue.Primitive(PrimitiveValue.Period(value))
  }

  case class Year(validation: Validation[java.time.Year]) extends Ref[java.time.Year] {
    def toDynamicValue(value: java.time.Year): DynamicValue = DynamicValue.Primitive(PrimitiveValue.Year(value))
  }

  case class YearMonth(validation: Validation[java.time.YearMonth]) extends Ref[java.time.YearMonth] {
    def toDynamicValue(value: java.time.YearMonth): DynamicValue =
      DynamicValue.Primitive(PrimitiveValue.YearMonth(value))
  }

  case class ZoneId(validation: Validation[java.time.ZoneId]) extends Ref[java.time.ZoneId] {
    def toDynamicValue(value: java.time.ZoneId): DynamicValue = DynamicValue.Primitive(PrimitiveValue.ZoneId(value))
  }

  case class ZoneOffset(validation: Validation[java.time.ZoneOffset]) extends Ref[java.time.ZoneOffset] {
    def toDynamicValue(value: java.time.ZoneOffset): DynamicValue =
      DynamicValue.Primitive(PrimitiveValue.ZoneOffset(value))
  }

  case class ZonedDateTime(validation: Validation[java.time.ZonedDateTime]) extends Ref[java.time.ZonedDateTime] {
    def toDynamicValue(value: java.time.ZonedDateTime): DynamicValue =
      DynamicValue.Primitive(PrimitiveValue.ZonedDateTime(value))
  }

  case class UUID(validation: Validation[java.util.UUID]) extends Ref[java.util.UUID] {
    def toDynamicValue(value: java.util.UUID): DynamicValue = DynamicValue.Primitive(PrimitiveValue.UUID(value))
  }

  case class Currency(validation: Validation[java.util.Currency]) extends Ref[java.util.Currency] {
    def toDynamicValue(value: java.util.Currency): DynamicValue = DynamicValue.Primitive(PrimitiveValue.Currency(value))
  }
}
