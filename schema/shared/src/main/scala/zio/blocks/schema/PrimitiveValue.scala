package zio.blocks.schema

sealed trait PrimitiveValue {
  type Type

  def primitiveType: PrimitiveType[Type]

  def typeIndex: scala.Int

  final def compare(that: PrimitiveValue): Int = PrimitiveValue.ordering.compare(this, that)

  final def >(that: PrimitiveValue): Boolean = compare(that) > 0

  final def >=(that: PrimitiveValue): Boolean = compare(that) >= 0

  final def <(that: PrimitiveValue): Boolean = compare(that) < 0

  final def <=(that: PrimitiveValue): Boolean = compare(that) <= 0
}

object PrimitiveValue {
  sealed trait Val extends PrimitiveValue {
    type Type <: AnyVal
  }

  sealed trait Ref extends PrimitiveValue {
    type Type <: AnyRef
  }

  case object Unit extends Val {
    type Type = scala.Unit

    def primitiveType: PrimitiveType[scala.Unit] = PrimitiveType.Unit

    def typeIndex: scala.Int = 0
  }

  case class Boolean(value: scala.Boolean) extends Val {
    type Type = scala.Boolean

    def primitiveType: PrimitiveType[scala.Boolean] = PrimitiveType.Boolean(Validation.None)

    def typeIndex: scala.Int = 1
  }

  case class Byte(value: scala.Byte) extends Val {
    type Type = scala.Byte

    def primitiveType: PrimitiveType[scala.Byte] = PrimitiveType.Byte(Validation.None)

    def typeIndex: scala.Int = 2
  }

  case class Short(value: scala.Short) extends Val {
    type Type = scala.Short

    def primitiveType: PrimitiveType[scala.Short] = PrimitiveType.Short(Validation.None)

    def typeIndex: scala.Int = 3
  }

  case class Int(value: scala.Int) extends Val {
    type Type = scala.Int

    def primitiveType: PrimitiveType[scala.Int] = PrimitiveType.Int(Validation.None)

    def typeIndex: scala.Int = 4
  }

  case class Long(value: scala.Long) extends Val {
    type Type = scala.Long

    def primitiveType: PrimitiveType[scala.Long] = PrimitiveType.Long(Validation.None)

    def typeIndex: scala.Int = 5
  }

  case class Float(value: scala.Float) extends Val {
    type Type = scala.Float

    def primitiveType: PrimitiveType[scala.Float] = PrimitiveType.Float(Validation.None)

    def typeIndex: scala.Int = 6
  }

  case class Double(value: scala.Double) extends Val {
    type Type = scala.Double

    def primitiveType: PrimitiveType[scala.Double] = PrimitiveType.Double(Validation.None)

    def typeIndex: scala.Int = 7
  }

  case class Char(value: scala.Char) extends Val {
    type Type = scala.Char

    def primitiveType: PrimitiveType[scala.Char] = PrimitiveType.Char(Validation.None)

    def typeIndex: scala.Int = 8
  }

  case class String(value: Predef.String) extends Ref {
    type Type = Predef.String

    def primitiveType: PrimitiveType[Predef.String] = PrimitiveType.String(Validation.None)

    def typeIndex: scala.Int = 9
  }

  case class BigInt(value: scala.BigInt) extends Ref {
    type Type = scala.BigInt

    def primitiveType: PrimitiveType[scala.BigInt] = PrimitiveType.BigInt(Validation.None)

    def typeIndex: scala.Int = 10
  }

  case class BigDecimal(value: scala.BigDecimal) extends Ref {
    type Type = scala.BigDecimal

    def primitiveType: PrimitiveType[scala.BigDecimal] = PrimitiveType.BigDecimal(Validation.None)

    def typeIndex: scala.Int = 11
  }

  case class DayOfWeek(value: java.time.DayOfWeek) extends Ref {
    type Type = java.time.DayOfWeek

    def primitiveType: PrimitiveType[java.time.DayOfWeek] = PrimitiveType.DayOfWeek(Validation.None)

    def typeIndex: scala.Int = 12
  }

  case class Duration(value: java.time.Duration) extends Ref {
    type Type = java.time.Duration

    def primitiveType: PrimitiveType[java.time.Duration] = PrimitiveType.Duration(Validation.None)

    def typeIndex: scala.Int = 13
  }

  case class Instant(value: java.time.Instant) extends Ref {
    type Type = java.time.Instant

    def primitiveType: PrimitiveType[java.time.Instant] = PrimitiveType.Instant(Validation.None)

    def typeIndex: scala.Int = 14
  }

  case class LocalDate(value: java.time.LocalDate) extends Ref {
    type Type = java.time.LocalDate

    def primitiveType: PrimitiveType[java.time.LocalDate] = PrimitiveType.LocalDate(Validation.None)

    def typeIndex: scala.Int = 15
  }

  case class LocalDateTime(value: java.time.LocalDateTime) extends Ref {
    type Type = java.time.LocalDateTime

    def primitiveType: PrimitiveType[java.time.LocalDateTime] = PrimitiveType.LocalDateTime(Validation.None)

    def typeIndex: scala.Int = 16
  }

  case class LocalTime(value: java.time.LocalTime) extends Ref {
    type Type = java.time.LocalTime

    def primitiveType: PrimitiveType[java.time.LocalTime] = PrimitiveType.LocalTime(Validation.None)

    def typeIndex: scala.Int = 17
  }

  case class Month(value: java.time.Month) extends Ref {
    type Type = java.time.Month

    def primitiveType: PrimitiveType[java.time.Month] = PrimitiveType.Month(Validation.None)

    def typeIndex: scala.Int = 18
  }

  case class MonthDay(value: java.time.MonthDay) extends Ref {
    type Type = java.time.MonthDay

    def primitiveType: PrimitiveType[java.time.MonthDay] = PrimitiveType.MonthDay(Validation.None)

    def typeIndex: scala.Int = 19
  }

  case class OffsetDateTime(value: java.time.OffsetDateTime) extends Ref {
    type Type = java.time.OffsetDateTime

    def primitiveType: PrimitiveType[java.time.OffsetDateTime] = PrimitiveType.OffsetDateTime(Validation.None)

    def typeIndex: scala.Int = 20
  }

  case class OffsetTime(value: java.time.OffsetTime) extends Ref {
    type Type = java.time.OffsetTime

    def primitiveType: PrimitiveType[java.time.OffsetTime] = PrimitiveType.OffsetTime(Validation.None)

    def typeIndex: scala.Int = 21
  }

  case class Period(value: java.time.Period) extends Ref {
    type Type = java.time.Period

    def primitiveType: PrimitiveType[java.time.Period] = PrimitiveType.Period(Validation.None)

    def typeIndex: scala.Int = 22
  }

  case class Year(value: java.time.Year) extends Ref {
    type Type = java.time.Year

    def primitiveType: PrimitiveType[java.time.Year] = PrimitiveType.Year(Validation.None)

    def typeIndex: scala.Int = 23
  }

  case class YearMonth(value: java.time.YearMonth) extends Ref {
    type Type = java.time.YearMonth

    def primitiveType: PrimitiveType[java.time.YearMonth] = PrimitiveType.YearMonth(Validation.None)

    def typeIndex: scala.Int = 24
  }

  case class ZoneId(value: java.time.ZoneId) extends Ref {
    type Type = java.time.ZoneId

    def primitiveType: PrimitiveType[java.time.ZoneId] = PrimitiveType.ZoneId(Validation.None)

    def typeIndex: scala.Int = 25
  }

  case class ZoneOffset(value: java.time.ZoneOffset) extends Ref {
    type Type = java.time.ZoneOffset

    def primitiveType: PrimitiveType[java.time.ZoneOffset] = PrimitiveType.ZoneOffset(Validation.None)

    def typeIndex: scala.Int = 26
  }

  case class ZonedDateTime(value: java.time.ZonedDateTime) extends Ref {
    type Type = java.time.ZonedDateTime

    def primitiveType: PrimitiveType[java.time.ZonedDateTime] = PrimitiveType.ZonedDateTime(Validation.None)

    def typeIndex: scala.Int = 27
  }

  case class Currency(value: java.util.Currency) extends Ref {
    type Type = java.util.Currency

    def primitiveType: PrimitiveType[java.util.Currency] = PrimitiveType.Currency(Validation.None)

    def typeIndex: scala.Int = 28
  }

  case class UUID(value: java.util.UUID) extends Ref {
    type Type = java.util.UUID

    def primitiveType: PrimitiveType[java.util.UUID] = PrimitiveType.UUID(Validation.None)

    def typeIndex: scala.Int = 29
  }

  implicit def ordering: Ordering[PrimitiveValue] = new Ordering[PrimitiveValue] {
    def period2Days(p: java.time.Period): scala.Int = (p.getYears() * 12 + p.getMonths()) * 30 + p.getDays()

    def currencyCompare(x: java.util.Currency, y: java.util.Currency): scala.Int =
      x.getCurrencyCode().compareTo(y.getCurrencyCode())

    def compare(x: PrimitiveValue, y: PrimitiveValue): scala.Int = (x, y) match {
      case (Unit, Unit)                           => 0
      case (Boolean(x), Boolean(y))               => x.compare(y)
      case (Byte(x), Byte(y))                     => x.compare(y)
      case (Short(x), Short(y))                   => x.compare(y)
      case (Int(x), Int(y))                       => x.compare(y)
      case (Long(x), Long(y))                     => x.compare(y)
      case (Float(x), Float(y))                   => x.compare(y)
      case (Double(x), Double(y))                 => x.compare(y)
      case (Char(x), Char(y))                     => x.compare(y)
      case (String(x), String(y))                 => x.compare(y)
      case (BigInt(x), BigInt(y))                 => x.compare(y)
      case (BigDecimal(x), BigDecimal(y))         => x.compare(y)
      case (DayOfWeek(x), DayOfWeek(y))           => x.compareTo(y)
      case (Duration(x), Duration(y))             => x.compareTo(y)
      case (Instant(x), Instant(y))               => x.compareTo(y)
      case (LocalDate(x), LocalDate(y))           => x.compareTo(y)
      case (LocalDateTime(x), LocalDateTime(y))   => x.compareTo(y)
      case (LocalTime(x), LocalTime(y))           => x.compareTo(y)
      case (Month(x), Month(y))                   => x.compareTo(y)
      case (MonthDay(x), MonthDay(y))             => x.compareTo(y)
      case (OffsetDateTime(x), OffsetDateTime(y)) => x.compareTo(y)
      case (OffsetTime(x), OffsetTime(y))         => x.compareTo(y)
      case (Period(x), Period(y))                 => period2Days(x).compareTo(period2Days(y))
      case (Year(x), Year(y))                     => x.compareTo(y)
      case (YearMonth(x), YearMonth(y))           => x.compareTo(y)
      case (ZoneId(x), ZoneId(y))                 => x.toString().compareTo(y.toString())
      case (ZoneOffset(x), ZoneOffset(y))         => x.compareTo(y)
      case (ZonedDateTime(x), ZonedDateTime(y))   => x.compareTo(y)
      case (Currency(x), Currency(y))             => currencyCompare(x, y)
      case (UUID(x), UUID(y))                     => x.compareTo(y)
      case (x, y)                                 => x.typeIndex.compareTo(y.typeIndex)
    }
  }
}
