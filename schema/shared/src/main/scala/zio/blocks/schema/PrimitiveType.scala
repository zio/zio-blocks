package zio.blocks.schema

import zio.blocks.schema.binding.{Binding, BindingType}
import zio.blocks.typeid.TypeId

sealed trait PrimitiveType[A] {
  def binding: Binding[BindingType.Primitive, A] = new Binding.Primitive[A]

  def fromDynamicValue(value: DynamicValue): Either[SchemaError, A] = fromDynamicValue(value, Nil)

  private[schema] def fromDynamicValue(value: DynamicValue, trace: List[DynamicOptic.Node]): Either[SchemaError, A]

  def toDynamicValue(value: A): DynamicValue

  def typeId: TypeId[A]

  def validation: Validation[A]
}

object PrimitiveType {
  case object Unit extends PrimitiveType[scala.Unit] {
    def validation: Validation[scala.Unit] = Validation.None

    def toDynamicValue(value: scala.Unit): DynamicValue = new DynamicValue.Primitive(PrimitiveValue.Unit)

    def typeId: TypeId[scala.Unit] = TypeId.unit

    private[schema] def fromDynamicValue(
      value: DynamicValue,
      trace: List[DynamicOptic.Node]
    ): Either[SchemaError, scala.Unit] =
      value match {
        case DynamicValue.Primitive(PrimitiveValue.Unit) => new Right(())
        case _                                           => new Left(SchemaError.expectationMismatch(trace, "Expected Unit"))
      }
  }

  case class Boolean(validation: Validation[scala.Boolean]) extends PrimitiveType[scala.Boolean] {
    def toDynamicValue(value: scala.Boolean): DynamicValue =
      new DynamicValue.Primitive(new PrimitiveValue.Boolean(value))

    def typeId: TypeId[scala.Boolean] = TypeId.boolean

    private[schema] def fromDynamicValue(
      value: DynamicValue,
      trace: List[DynamicOptic.Node]
    ): Either[SchemaError, scala.Boolean] =
      value match {
        case DynamicValue.Primitive(PrimitiveValue.Boolean(b)) => new Right(b)
        case _                                                 => new Left(SchemaError.expectationMismatch(trace, "Expected Boolean"))
      }
  }

  case class Byte(validation: Validation[scala.Byte]) extends PrimitiveType[scala.Byte] {
    def toDynamicValue(value: scala.Byte): DynamicValue = new DynamicValue.Primitive(new PrimitiveValue.Byte(value))

    def typeId: TypeId[scala.Byte] = TypeId.byte

    private[schema] def fromDynamicValue(
      value: DynamicValue,
      trace: List[DynamicOptic.Node]
    ): Either[SchemaError, scala.Byte] =
      value match {
        case DynamicValue.Primitive(PrimitiveValue.Byte(b)) => new Right(b)
        case _                                              => new Left(SchemaError.expectationMismatch(trace, "Expected Byte"))
      }
  }

  case class Short(validation: Validation[scala.Short]) extends PrimitiveType[scala.Short] {
    def toDynamicValue(value: scala.Short): DynamicValue = new DynamicValue.Primitive(new PrimitiveValue.Short(value))

    def typeId: TypeId[scala.Short] = TypeId.short

    private[schema] def fromDynamicValue(
      value: DynamicValue,
      trace: List[DynamicOptic.Node]
    ): Either[SchemaError, scala.Short] =
      value match {
        case DynamicValue.Primitive(PrimitiveValue.Short(s)) => new Right(s)
        case _                                               => new Left(SchemaError.expectationMismatch(trace, "Expected Short"))
      }
  }

  case class Int(validation: Validation[scala.Int]) extends PrimitiveType[scala.Int] {
    def toDynamicValue(value: scala.Int): DynamicValue = new DynamicValue.Primitive(new PrimitiveValue.Int(value))

    def typeId: TypeId[scala.Int] = TypeId.int

    private[schema] def fromDynamicValue(
      value: DynamicValue,
      trace: List[DynamicOptic.Node]
    ): Either[SchemaError, scala.Int] =
      value match {
        case DynamicValue.Primitive(PrimitiveValue.Int(i)) => new Right(i)
        case _                                             => new Left(SchemaError.expectationMismatch(trace, "Expected Int"))
      }
  }

  case class Long(validation: Validation[scala.Long]) extends PrimitiveType[scala.Long] {
    def toDynamicValue(value: scala.Long): DynamicValue = new DynamicValue.Primitive(new PrimitiveValue.Long(value))

    def typeId: TypeId[scala.Long] = TypeId.long

    private[schema] def fromDynamicValue(
      value: DynamicValue,
      trace: List[DynamicOptic.Node]
    ): Either[SchemaError, scala.Long] =
      value match {
        case DynamicValue.Primitive(PrimitiveValue.Long(l)) => new Right(l)
        case _                                              => new Left(SchemaError.expectationMismatch(trace, "Expected Long"))
      }
  }

  case class Float(validation: Validation[scala.Float]) extends PrimitiveType[scala.Float] {
    def toDynamicValue(value: scala.Float): DynamicValue = new DynamicValue.Primitive(new PrimitiveValue.Float(value))

    def typeId: TypeId[scala.Float] = TypeId.float

    private[schema] def fromDynamicValue(
      value: DynamicValue,
      trace: List[DynamicOptic.Node]
    ): Either[SchemaError, scala.Float] =
      value match {
        case DynamicValue.Primitive(PrimitiveValue.Float(f)) => Right(f)
        case _                                               => Left(SchemaError.expectationMismatch(trace, "Expected Float"))
      }
  }

  case class Double(validation: Validation[scala.Double]) extends PrimitiveType[scala.Double] {
    def toDynamicValue(value: scala.Double): DynamicValue = new DynamicValue.Primitive(new PrimitiveValue.Double(value))

    def typeId: TypeId[scala.Double] = TypeId.double

    private[schema] def fromDynamicValue(
      value: DynamicValue,
      trace: List[DynamicOptic.Node]
    ): Either[SchemaError, scala.Double] =
      value match {
        case DynamicValue.Primitive(PrimitiveValue.Double(d)) => new Right(d)
        case _                                                => new Left(SchemaError.expectationMismatch(trace, "Expected Double"))
      }
  }

  case class Char(validation: Validation[scala.Char]) extends PrimitiveType[scala.Char] {
    def toDynamicValue(value: scala.Char): DynamicValue = new DynamicValue.Primitive(new PrimitiveValue.Char(value))

    def typeId: TypeId[scala.Char] = TypeId.char

    private[schema] def fromDynamicValue(
      value: DynamicValue,
      trace: List[DynamicOptic.Node]
    ): Either[SchemaError, scala.Char] =
      value match {
        case DynamicValue.Primitive(PrimitiveValue.Char(c)) => new Right(c)
        case _                                              => new Left(SchemaError.expectationMismatch(trace, "Expected Char"))
      }
  }

  case class String(validation: Validation[Predef.String]) extends PrimitiveType[Predef.String] {
    def toDynamicValue(value: Predef.String): DynamicValue =
      new DynamicValue.Primitive(new PrimitiveValue.String(value))

    def typeId: TypeId[Predef.String] = TypeId.string

    private[schema] def fromDynamicValue(
      value: DynamicValue,
      trace: List[DynamicOptic.Node]
    ): Either[SchemaError, Predef.String] =
      value match {
        case DynamicValue.Primitive(PrimitiveValue.String(s)) => new Right(s)
        case _                                                => new Left(SchemaError.expectationMismatch(trace, "Expected String"))
      }
  }

  case class BigInt(validation: Validation[scala.BigInt]) extends PrimitiveType[scala.BigInt] {
    def toDynamicValue(value: scala.BigInt): DynamicValue = new DynamicValue.Primitive(new PrimitiveValue.BigInt(value))

    def typeId: TypeId[scala.BigInt] = TypeId.bigInt

    private[schema] def fromDynamicValue(
      value: DynamicValue,
      trace: List[DynamicOptic.Node]
    ): Either[SchemaError, scala.BigInt] =
      value match {
        case DynamicValue.Primitive(PrimitiveValue.BigInt(b)) => new Right(b)
        case _                                                => new Left(SchemaError.expectationMismatch(trace, "Expected BigInt"))
      }
  }

  case class BigDecimal(validation: Validation[scala.BigDecimal]) extends PrimitiveType[scala.BigDecimal] {
    def toDynamicValue(value: scala.BigDecimal): DynamicValue =
      new DynamicValue.Primitive(new PrimitiveValue.BigDecimal(value))

    def typeId: TypeId[scala.BigDecimal] = TypeId.bigDecimal

    private[schema] def fromDynamicValue(
      value: DynamicValue,
      trace: List[DynamicOptic.Node]
    ): Either[SchemaError, scala.BigDecimal] =
      value match {
        case DynamicValue.Primitive(PrimitiveValue.BigDecimal(b)) => new Right(b)
        case _                                                    => new Left(SchemaError.expectationMismatch(trace, "Expected BigDecimal"))
      }
  }

  case class DayOfWeek(validation: Validation[java.time.DayOfWeek]) extends PrimitiveType[java.time.DayOfWeek] {
    def toDynamicValue(value: java.time.DayOfWeek): DynamicValue =
      new DynamicValue.Primitive(new PrimitiveValue.DayOfWeek(value))

    def typeId: TypeId[java.time.DayOfWeek] = TypeId.dayOfWeek

    private[schema] def fromDynamicValue(
      value: DynamicValue,
      trace: List[DynamicOptic.Node]
    ): Either[SchemaError, java.time.DayOfWeek] = value match {
      case DynamicValue.Primitive(PrimitiveValue.DayOfWeek(d)) => new Right(d)
      case _                                                   => new Left(SchemaError.expectationMismatch(trace, "Expected DayOfWeek"))
    }
  }

  case class Duration(validation: Validation[java.time.Duration]) extends PrimitiveType[java.time.Duration] {
    def toDynamicValue(value: java.time.Duration): DynamicValue =
      new DynamicValue.Primitive(new PrimitiveValue.Duration(value))

    def typeId: TypeId[java.time.Duration] = TypeId.duration

    private[schema] def fromDynamicValue(
      value: DynamicValue,
      trace: List[DynamicOptic.Node]
    ): Either[SchemaError, java.time.Duration] =
      value match {
        case DynamicValue.Primitive(PrimitiveValue.Duration(d)) => new Right(d)
        case _                                                  => new Left(SchemaError.expectationMismatch(trace, "Expected Duration"))
      }
  }

  case class Instant(validation: Validation[java.time.Instant]) extends PrimitiveType[java.time.Instant] {
    def toDynamicValue(value: java.time.Instant): DynamicValue =
      new DynamicValue.Primitive(new PrimitiveValue.Instant(value))

    def typeId: TypeId[java.time.Instant] = TypeId.instant

    private[schema] def fromDynamicValue(
      value: DynamicValue,
      trace: List[DynamicOptic.Node]
    ): Either[SchemaError, java.time.Instant] =
      value match {
        case DynamicValue.Primitive(PrimitiveValue.Instant(i)) => new Right(i)
        case _                                                 => new Left(SchemaError.expectationMismatch(trace, "Expected Instant"))
      }
  }

  case class LocalDate(validation: Validation[java.time.LocalDate]) extends PrimitiveType[java.time.LocalDate] {
    def toDynamicValue(value: java.time.LocalDate): DynamicValue =
      new DynamicValue.Primitive(new PrimitiveValue.LocalDate(value))

    def typeId: TypeId[java.time.LocalDate] = TypeId.localDate

    private[schema] def fromDynamicValue(
      value: DynamicValue,
      trace: List[DynamicOptic.Node]
    ): Either[SchemaError, java.time.LocalDate] = value match {
      case DynamicValue.Primitive(PrimitiveValue.LocalDate(d)) => new Right(d)
      case _                                                   => new Left(SchemaError.expectationMismatch(trace, "Expected LocalDate"))
    }
  }

  case class LocalDateTime(validation: Validation[java.time.LocalDateTime])
      extends PrimitiveType[java.time.LocalDateTime] {
    def toDynamicValue(value: java.time.LocalDateTime): DynamicValue =
      new DynamicValue.Primitive(new PrimitiveValue.LocalDateTime(value))

    def typeId: TypeId[java.time.LocalDateTime] = TypeId.localDateTime

    private[schema] def fromDynamicValue(
      value: DynamicValue,
      trace: List[DynamicOptic.Node]
    ): Either[SchemaError, java.time.LocalDateTime] =
      value match {
        case DynamicValue.Primitive(PrimitiveValue.LocalDateTime(d)) => new Right(d)
        case _                                                       => new Left(SchemaError.expectationMismatch(trace, "Expected LocalDateTime"))
      }
  }

  case class LocalTime(validation: Validation[java.time.LocalTime]) extends PrimitiveType[java.time.LocalTime] {
    def toDynamicValue(value: java.time.LocalTime): DynamicValue =
      new DynamicValue.Primitive(new PrimitiveValue.LocalTime(value))

    def typeId: TypeId[java.time.LocalTime] = TypeId.localTime

    private[schema] def fromDynamicValue(
      value: DynamicValue,
      trace: List[DynamicOptic.Node]
    ): Either[SchemaError, java.time.LocalTime] = value match {
      case DynamicValue.Primitive(PrimitiveValue.LocalTime(t)) => new Right(t)
      case _                                                   => new Left(SchemaError.expectationMismatch(trace, "Expected LocalTime"))
    }
  }

  case class Month(validation: Validation[java.time.Month]) extends PrimitiveType[java.time.Month] {
    def toDynamicValue(value: java.time.Month): DynamicValue =
      new DynamicValue.Primitive(new PrimitiveValue.Month(value))

    def typeId: TypeId[java.time.Month] = TypeId.month

    private[schema] def fromDynamicValue(
      value: DynamicValue,
      trace: List[DynamicOptic.Node]
    ): Either[SchemaError, java.time.Month] =
      value match {
        case DynamicValue.Primitive(PrimitiveValue.Month(m)) => new Right(m)
        case _                                               => new Left(SchemaError.expectationMismatch(trace, "Expected Month"))
      }
  }

  case class MonthDay(validation: Validation[java.time.MonthDay]) extends PrimitiveType[java.time.MonthDay] {
    def toDynamicValue(value: java.time.MonthDay): DynamicValue =
      new DynamicValue.Primitive(new PrimitiveValue.MonthDay(value))

    def typeId: TypeId[java.time.MonthDay] = TypeId.monthDay

    private[schema] def fromDynamicValue(
      value: DynamicValue,
      trace: List[DynamicOptic.Node]
    ): Either[SchemaError, java.time.MonthDay] =
      value match {
        case DynamicValue.Primitive(PrimitiveValue.MonthDay(m)) => new Right(m)
        case _                                                  => new Left(SchemaError.expectationMismatch(trace, "Expected MonthDay"))
      }
  }

  case class OffsetDateTime(validation: Validation[java.time.OffsetDateTime])
      extends PrimitiveType[java.time.OffsetDateTime] {
    def toDynamicValue(value: java.time.OffsetDateTime): DynamicValue =
      new DynamicValue.Primitive(new PrimitiveValue.OffsetDateTime(value))

    def typeId: TypeId[java.time.OffsetDateTime] = TypeId.offsetDateTime

    private[schema] def fromDynamicValue(
      value: DynamicValue,
      trace: List[DynamicOptic.Node]
    ): Either[SchemaError, java.time.OffsetDateTime] =
      value match {
        case DynamicValue.Primitive(PrimitiveValue.OffsetDateTime(d)) => new Right(d)
        case _                                                        => new Left(SchemaError.expectationMismatch(trace, "Expected OffsetDateTime"))
      }
  }

  case class OffsetTime(validation: Validation[java.time.OffsetTime]) extends PrimitiveType[java.time.OffsetTime] {
    def toDynamicValue(value: java.time.OffsetTime): DynamicValue =
      new DynamicValue.Primitive(new PrimitiveValue.OffsetTime(value))

    def typeId: TypeId[java.time.OffsetTime] = TypeId.offsetTime

    private[schema] def fromDynamicValue(
      value: DynamicValue,
      trace: List[DynamicOptic.Node]
    ): Either[SchemaError, java.time.OffsetTime] = value match {
      case DynamicValue.Primitive(PrimitiveValue.OffsetTime(t)) => new Right(t)
      case _                                                    => new Left(SchemaError.expectationMismatch(trace, "Expected OffsetTime"))
    }
  }

  case class Period(validation: Validation[java.time.Period]) extends PrimitiveType[java.time.Period] {
    def toDynamicValue(value: java.time.Period): DynamicValue =
      new DynamicValue.Primitive(new PrimitiveValue.Period(value))

    def typeId: TypeId[java.time.Period] = TypeId.period

    private[schema] def fromDynamicValue(
      value: DynamicValue,
      trace: List[DynamicOptic.Node]
    ): Either[SchemaError, java.time.Period] =
      value match {
        case DynamicValue.Primitive(PrimitiveValue.Period(p)) => new Right(p)
        case _                                                => new Left(SchemaError.expectationMismatch(trace, "Expected Period"))
      }
  }

  case class Year(validation: Validation[java.time.Year]) extends PrimitiveType[java.time.Year] {
    def toDynamicValue(value: java.time.Year): DynamicValue = new DynamicValue.Primitive(new PrimitiveValue.Year(value))

    def typeId: TypeId[java.time.Year] = TypeId.year

    private[schema] def fromDynamicValue(
      value: DynamicValue,
      trace: List[DynamicOptic.Node]
    ): Either[SchemaError, java.time.Year] =
      value match {
        case DynamicValue.Primitive(PrimitiveValue.Year(y)) => new Right(y)
        case _                                              => new Left(SchemaError.expectationMismatch(trace, "Expected Year"))
      }
  }

  case class YearMonth(validation: Validation[java.time.YearMonth]) extends PrimitiveType[java.time.YearMonth] {
    def toDynamicValue(value: java.time.YearMonth): DynamicValue =
      new DynamicValue.Primitive(new PrimitiveValue.YearMonth(value))

    def typeId: TypeId[java.time.YearMonth] = TypeId.yearMonth

    private[schema] def fromDynamicValue(
      value: DynamicValue,
      trace: List[DynamicOptic.Node]
    ): Either[SchemaError, java.time.YearMonth] = value match {
      case DynamicValue.Primitive(PrimitiveValue.YearMonth(y)) => new Right(y)
      case _                                                   => new Left(SchemaError.expectationMismatch(trace, "Expected YearMonth"))
    }
  }

  case class ZoneId(validation: Validation[java.time.ZoneId]) extends PrimitiveType[java.time.ZoneId] {
    def toDynamicValue(value: java.time.ZoneId): DynamicValue =
      new DynamicValue.Primitive(new PrimitiveValue.ZoneId(value))

    def typeId: TypeId[java.time.ZoneId] = TypeId.zoneId

    private[schema] def fromDynamicValue(
      value: DynamicValue,
      trace: List[DynamicOptic.Node]
    ): Either[SchemaError, java.time.ZoneId] =
      value match {
        case DynamicValue.Primitive(PrimitiveValue.ZoneId(z)) => new Right(z)
        case _                                                => new Left(SchemaError.expectationMismatch(trace, "Expected ZoneId"))
      }
  }

  case class ZoneOffset(validation: Validation[java.time.ZoneOffset]) extends PrimitiveType[java.time.ZoneOffset] {
    def toDynamicValue(value: java.time.ZoneOffset): DynamicValue =
      new DynamicValue.Primitive(new PrimitiveValue.ZoneOffset(value))

    def typeId: TypeId[java.time.ZoneOffset] = TypeId.zoneOffset

    private[schema] def fromDynamicValue(
      value: DynamicValue,
      trace: List[DynamicOptic.Node]
    ): Either[SchemaError, java.time.ZoneOffset] = value match {
      case DynamicValue.Primitive(PrimitiveValue.ZoneOffset(z)) => new Right(z)
      case _                                                    => new Left(SchemaError.expectationMismatch(trace, "Expected ZoneOffset"))
    }
  }

  case class ZonedDateTime(validation: Validation[java.time.ZonedDateTime])
      extends PrimitiveType[java.time.ZonedDateTime] {
    def toDynamicValue(value: java.time.ZonedDateTime): DynamicValue =
      new DynamicValue.Primitive(new PrimitiveValue.ZonedDateTime(value))

    def typeId: TypeId[java.time.ZonedDateTime] = TypeId.zonedDateTime

    private[schema] def fromDynamicValue(
      value: DynamicValue,
      trace: List[DynamicOptic.Node]
    ): Either[SchemaError, java.time.ZonedDateTime] =
      value match {
        case DynamicValue.Primitive(PrimitiveValue.ZonedDateTime(z)) => new Right(z)
        case _                                                       => new Left(SchemaError.expectationMismatch(trace, "Expected ZonedDateTime"))
      }
  }

  case class UUID(validation: Validation[java.util.UUID]) extends PrimitiveType[java.util.UUID] {
    def toDynamicValue(value: java.util.UUID): DynamicValue =
      new DynamicValue.Primitive(new PrimitiveValue.UUID(value))

    def typeId: TypeId[java.util.UUID] = TypeId.uuid

    private[schema] def fromDynamicValue(
      value: DynamicValue,
      trace: List[DynamicOptic.Node]
    ): Either[SchemaError, java.util.UUID] =
      value match {
        case DynamicValue.Primitive(PrimitiveValue.UUID(u)) => new Right(u)
        case _                                              => new Left(SchemaError.expectationMismatch(trace, "Expected UUID"))
      }
  }

  case class Currency(validation: Validation[java.util.Currency]) extends PrimitiveType[java.util.Currency] {
    def toDynamicValue(value: java.util.Currency): DynamicValue =
      new DynamicValue.Primitive(new PrimitiveValue.Currency(value))

    def typeId: TypeId[java.util.Currency] = TypeId.currency

    private[schema] def fromDynamicValue(
      value: DynamicValue,
      trace: List[DynamicOptic.Node]
    ): Either[SchemaError, java.util.Currency] =
      value match {
        case DynamicValue.Primitive(PrimitiveValue.Currency(c)) => new Right(c)
        case _                                                  => new Left(SchemaError.expectationMismatch(trace, "Expected Currency"))
      }
  }
}
