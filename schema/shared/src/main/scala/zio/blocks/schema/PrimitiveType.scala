package zio.blocks.schema

sealed trait PrimitiveType[A] {
  def validation: Validation[A]
}

object PrimitiveType {
  sealed trait Val[A <: AnyVal] extends PrimitiveType[A]

  sealed trait Ref[A <: AnyRef] extends PrimitiveType[A]

  case object Unit extends Val[scala.Unit] {
    def validation: Validation[scala.Unit] = Validation.None
  }

  case class Boolean(validation: Validation[scala.Boolean]) extends Val[scala.Boolean]

  case class Byte(validation: Validation[scala.Byte]) extends Val[scala.Byte]

  case class Short(validation: Validation[scala.Short]) extends Val[scala.Short]

  case class Int(validation: Validation[scala.Int]) extends Val[scala.Int]

  case class Long(validation: Validation[scala.Long]) extends Val[scala.Long]

  case class Float(validation: Validation[scala.Float]) extends Val[scala.Float]

  case class Double(validation: Validation[scala.Double]) extends Val[scala.Double]

  case class Char(validation: Validation[scala.Char]) extends Val[scala.Char]

  case class String(validation: Validation[Predef.String]) extends Ref[Predef.String]

  case class BigInt(validation: Validation[scala.BigInt]) extends Ref[scala.BigInt]

  case class BigDecimal(validation: Validation[scala.BigDecimal]) extends Ref[scala.BigDecimal]

  case class DayOfWeek(validation: Validation[java.time.DayOfWeek]) extends Ref[java.time.DayOfWeek]

  case class Duration(validation: Validation[java.time.Duration]) extends Ref[java.time.Duration]

  case class Instant(validation: Validation[java.time.Instant]) extends Ref[java.time.Instant]

  case class LocalDate(validation: Validation[java.time.LocalDate]) extends Ref[java.time.LocalDate]

  case class LocalDateTime(validation: Validation[java.time.LocalDateTime]) extends Ref[java.time.LocalDateTime]

  case class LocalTime(validation: Validation[java.time.LocalTime]) extends Ref[java.time.LocalTime]

  case class Month(validation: Validation[java.time.Month]) extends Ref[java.time.Month]

  case class MonthDay(validation: Validation[java.time.MonthDay]) extends Ref[java.time.MonthDay]

  case class OffsetDateTime(validation: Validation[java.time.OffsetDateTime]) extends Ref[java.time.OffsetDateTime]

  case class OffsetTime(validation: Validation[java.time.OffsetTime]) extends Ref[java.time.OffsetTime]

  case class Period(validation: Validation[java.time.Period]) extends Ref[java.time.Period]

  case class Year(validation: Validation[java.time.Year]) extends Ref[java.time.Year]

  case class YearMonth(validation: Validation[java.time.YearMonth]) extends Ref[java.time.YearMonth]

  case class ZoneId(validation: Validation[java.time.ZoneId]) extends Ref[java.time.ZoneId]

  case class ZoneOffset(validation: Validation[java.time.ZoneOffset]) extends Ref[java.time.ZoneOffset]

  case class ZonedDateTime(validation: Validation[java.time.ZonedDateTime]) extends Ref[java.time.ZonedDateTime]

  case class UUID(validation: Validation[java.util.UUID]) extends Ref[java.util.UUID]

  case class Currency(validation: Validation[java.util.Currency]) extends Ref[java.util.Currency]
}
