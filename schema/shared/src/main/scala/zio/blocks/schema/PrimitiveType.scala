package zio.blocks.schema

import zio.blocks.schema.DynamicOptic

sealed trait PrimitiveType[A] {
  def fromDynamicValue(value: DynamicValue): Either[SchemaError, A]

  def toDynamicValue(value: A): DynamicValue

  def validation: Validation[A]
}

object PrimitiveType {
  sealed trait Val[A <: AnyVal] extends PrimitiveType[A] {
    def fromDynamicValue(value: DynamicValue): Either[SchemaError, A] = value match {
      case DynamicValue.Primitive(primitiveValue) => fromPrimitiveValue(primitiveValue)
      case _ =>
        Left(
          SchemaError.invalidType(DynamicOptic.root, s"Expected a primitive value but got: $value")
        )
    }

    protected def fromPrimitiveValue(value: PrimitiveValue): Either[SchemaError, A]
  }

  sealed trait Ref[A <: AnyRef] extends PrimitiveType[A] {
    def fromDynamicValue(value: DynamicValue): Either[SchemaError, A] = value match {
      case DynamicValue.Primitive(primitiveValue) => fromPrimitiveValue(primitiveValue)
      case _ =>
        Left(
          SchemaError.invalidType(DynamicOptic.root, s"Expected a primitive value but got: $value")
        )
    }

    protected def fromPrimitiveValue(value: PrimitiveValue): Either[SchemaError, A]
  }

  case object Unit extends Val[scala.Unit] {
    def validation: Validation[scala.Unit] = Validation.None

    def toDynamicValue(value: scala.Unit): DynamicValue = DynamicValue.Primitive(PrimitiveValue.Unit)

    protected def fromPrimitiveValue(value: PrimitiveValue): Either[SchemaError, scala.Unit] = value match {
      case PrimitiveValue.Unit => Right(())
      case _                   => Left(SchemaError.invalidType(DynamicOptic.root, s"Expected Unit but got: $value"))
    }
  }

  case class Boolean(validation: Validation[scala.Boolean]) extends Val[scala.Boolean] {
    def toDynamicValue(value: scala.Boolean): DynamicValue = DynamicValue.Primitive(PrimitiveValue.Boolean(value))

    protected def fromPrimitiveValue(value: PrimitiveValue): Either[SchemaError, scala.Boolean] = value match {
      case PrimitiveValue.Boolean(b) => Right(b)
      case _                         => Left(SchemaError.invalidType(DynamicOptic.root, s"Expected Boolean but got: $value"))
    }
  }

  case class Byte(validation: Validation[scala.Byte]) extends Val[scala.Byte] {
    def toDynamicValue(value: scala.Byte): DynamicValue = DynamicValue.Primitive(PrimitiveValue.Byte(value))

    protected def fromPrimitiveValue(value: PrimitiveValue): Either[SchemaError, scala.Byte] = value match {
      case PrimitiveValue.Byte(b) => Right(b)
      case _                      => Left(SchemaError.invalidType(DynamicOptic.root, s"Expected Byte but got: $value"))
    }
  }

  case class Short(validation: Validation[scala.Short]) extends Val[scala.Short] {
    def toDynamicValue(value: scala.Short): DynamicValue = DynamicValue.Primitive(PrimitiveValue.Short(value))

    protected def fromPrimitiveValue(value: PrimitiveValue): Either[SchemaError, scala.Short] = value match {
      case PrimitiveValue.Short(s) => Right(s)
      case _                       => Left(SchemaError.invalidType(DynamicOptic.root, s"Expected Short but got: $value"))
    }
  }

  case class Int(validation: Validation[scala.Int]) extends Val[scala.Int] {
    def toDynamicValue(value: scala.Int): DynamicValue = DynamicValue.Primitive(PrimitiveValue.Int(value))

    protected def fromPrimitiveValue(value: PrimitiveValue): Either[SchemaError, scala.Int] = value match {
      case PrimitiveValue.Int(i) => Right(i)
      case _                     => Left(SchemaError.invalidType(DynamicOptic.root, s"Expected Int but got: $value"))
    }
  }

  case class Long(validation: Validation[scala.Long]) extends Val[scala.Long] {
    def toDynamicValue(value: scala.Long): DynamicValue = DynamicValue.Primitive(PrimitiveValue.Long(value))

    protected def fromPrimitiveValue(value: PrimitiveValue): Either[SchemaError, scala.Long] = value match {
      case PrimitiveValue.Long(l) => Right(l)
      case _                      => Left(SchemaError.invalidType(DynamicOptic.root, s"Expected Long but got: $value"))
    }
  }

  case class Float(validation: Validation[scala.Float]) extends Val[scala.Float] {
    def toDynamicValue(value: scala.Float): DynamicValue = DynamicValue.Primitive(PrimitiveValue.Float(value))

    protected def fromPrimitiveValue(value: PrimitiveValue): Either[SchemaError, scala.Float] = value match {
      case PrimitiveValue.Float(f) => Right(f)
      case _                       => Left(SchemaError.invalidType(DynamicOptic.root, s"Expected Float but got: $value"))
    }
  }

  case class Double(validation: Validation[scala.Double]) extends Val[scala.Double] {
    def toDynamicValue(value: scala.Double): DynamicValue = DynamicValue.Primitive(PrimitiveValue.Double(value))

    protected def fromPrimitiveValue(value: PrimitiveValue): Either[SchemaError, scala.Double] = value match {
      case PrimitiveValue.Double(d) => Right(d)
      case _                        => Left(SchemaError.invalidType(DynamicOptic.root, s"Expected Double but got: $value"))
    }
  }

  case class Char(validation: Validation[scala.Char]) extends Val[scala.Char] {
    def toDynamicValue(value: scala.Char): DynamicValue = DynamicValue.Primitive(PrimitiveValue.Char(value))

    protected def fromPrimitiveValue(value: PrimitiveValue): Either[SchemaError, scala.Char] = value match {
      case PrimitiveValue.Char(c) => Right(c)
      case _                      => Left(SchemaError.invalidType(DynamicOptic.root, s"Expected Char but got: $value"))
    }
  }

  case class String(validation: Validation[Predef.String]) extends Ref[Predef.String] {
    def toDynamicValue(value: Predef.String): DynamicValue = DynamicValue.Primitive(PrimitiveValue.String(value))

    protected def fromPrimitiveValue(value: PrimitiveValue): Either[SchemaError, Predef.String] = value match {
      case PrimitiveValue.String(s) => Right(s)
      case _                        => Left(SchemaError.invalidType(DynamicOptic.root, s"Expected String but got: $value"))
    }
  }

  case class BigInt(validation: Validation[scala.BigInt]) extends Ref[scala.BigInt] {
    def toDynamicValue(value: scala.BigInt): DynamicValue = DynamicValue.Primitive(PrimitiveValue.BigInt(value))

    protected def fromPrimitiveValue(value: PrimitiveValue): Either[SchemaError, scala.BigInt] = value match {
      case PrimitiveValue.BigInt(b) => Right(b)
      case _                        => Left(SchemaError.invalidType(DynamicOptic.root, s"Expected BigInt but got: $value"))
    }
  }

  case class BigDecimal(validation: Validation[scala.BigDecimal]) extends Ref[scala.BigDecimal] {
    def toDynamicValue(value: scala.BigDecimal): DynamicValue = DynamicValue.Primitive(PrimitiveValue.BigDecimal(value))

    protected def fromPrimitiveValue(value: PrimitiveValue): Either[SchemaError, scala.BigDecimal] = value match {
      case PrimitiveValue.BigDecimal(b) => Right(b)
      case _ =>
        Left(SchemaError.invalidType(DynamicOptic.root, s"Expected BigDecimal but got: $value"))
    }
  }

  case class DayOfWeek(validation: Validation[java.time.DayOfWeek]) extends Ref[java.time.DayOfWeek] {
    def toDynamicValue(value: java.time.DayOfWeek): DynamicValue =
      DynamicValue.Primitive(PrimitiveValue.DayOfWeek(value))

    protected def fromPrimitiveValue(value: PrimitiveValue): Either[SchemaError, java.time.DayOfWeek] = value match {
      case PrimitiveValue.DayOfWeek(d) => Right(d)
      case _ =>
        Left(SchemaError.invalidType(DynamicOptic.root, s"Expected DayOfWeek but got: $value"))
    }
  }

  case class Duration(validation: Validation[java.time.Duration]) extends Ref[java.time.Duration] {
    def toDynamicValue(value: java.time.Duration): DynamicValue = DynamicValue.Primitive(PrimitiveValue.Duration(value))

    protected def fromPrimitiveValue(value: PrimitiveValue): Either[SchemaError, java.time.Duration] = value match {
      case PrimitiveValue.Duration(d) => Right(d)
      case _ =>
        Left(SchemaError.invalidType(DynamicOptic.root, s"Expected Duration but got: $value"))
    }
  }

  case class Instant(validation: Validation[java.time.Instant]) extends Ref[java.time.Instant] {
    def toDynamicValue(value: java.time.Instant): DynamicValue = DynamicValue.Primitive(PrimitiveValue.Instant(value))

    protected def fromPrimitiveValue(value: PrimitiveValue): Either[SchemaError, java.time.Instant] = value match {
      case PrimitiveValue.Instant(i) => Right(i)
      case _                         => Left(SchemaError.invalidType(DynamicOptic.root, s"Expected Instant but got: $value"))
    }
  }

  case class LocalDate(validation: Validation[java.time.LocalDate]) extends Ref[java.time.LocalDate] {
    def toDynamicValue(value: java.time.LocalDate): DynamicValue =
      DynamicValue.Primitive(PrimitiveValue.LocalDate(value))

    protected def fromPrimitiveValue(value: PrimitiveValue): Either[SchemaError, java.time.LocalDate] = value match {
      case PrimitiveValue.LocalDate(d) => Right(d)
      case _ =>
        Left(SchemaError.invalidType(DynamicOptic.root, s"Expected LocalDate but got: $value"))
    }
  }

  case class LocalDateTime(validation: Validation[java.time.LocalDateTime]) extends Ref[java.time.LocalDateTime] {
    def toDynamicValue(value: java.time.LocalDateTime): DynamicValue =
      DynamicValue.Primitive(PrimitiveValue.LocalDateTime(value))

    protected def fromPrimitiveValue(value: PrimitiveValue): Either[SchemaError, java.time.LocalDateTime] =
      value match {
        case PrimitiveValue.LocalDateTime(d) => Right(d)
        case _ =>
          Left(SchemaError.invalidType(DynamicOptic.root, s"Expected LocalDateTime but got: $value"))
      }
  }

  case class LocalTime(validation: Validation[java.time.LocalTime]) extends Ref[java.time.LocalTime] {
    def toDynamicValue(value: java.time.LocalTime): DynamicValue =
      DynamicValue.Primitive(PrimitiveValue.LocalTime(value))

    protected def fromPrimitiveValue(value: PrimitiveValue): Either[SchemaError, java.time.LocalTime] = value match {
      case PrimitiveValue.LocalTime(t) => Right(t)
      case _ =>
        Left(SchemaError.invalidType(DynamicOptic.root, s"Expected LocalTime but got: $value"))
    }
  }

  case class Month(validation: Validation[java.time.Month]) extends Ref[java.time.Month] {
    def toDynamicValue(value: java.time.Month): DynamicValue = DynamicValue.Primitive(PrimitiveValue.Month(value))

    protected def fromPrimitiveValue(value: PrimitiveValue): Either[SchemaError, java.time.Month] = value match {
      case PrimitiveValue.Month(m) => Right(m)
      case _                       => Left(SchemaError.invalidType(DynamicOptic.root, s"Expected Month but got: $value"))
    }
  }

  case class MonthDay(validation: Validation[java.time.MonthDay]) extends Ref[java.time.MonthDay] {
    def toDynamicValue(value: java.time.MonthDay): DynamicValue = DynamicValue.Primitive(PrimitiveValue.MonthDay(value))

    protected def fromPrimitiveValue(value: PrimitiveValue): Either[SchemaError, java.time.MonthDay] = value match {
      case PrimitiveValue.MonthDay(m) => Right(m)
      case _ =>
        Left(SchemaError.invalidType(DynamicOptic.root, s"Expected MonthDay but got: $value"))
    }
  }

  case class OffsetDateTime(validation: Validation[java.time.OffsetDateTime]) extends Ref[java.time.OffsetDateTime] {
    def toDynamicValue(value: java.time.OffsetDateTime): DynamicValue =
      DynamicValue.Primitive(PrimitiveValue.OffsetDateTime(value))

    protected def fromPrimitiveValue(value: PrimitiveValue): Either[SchemaError, java.time.OffsetDateTime] =
      value match {
        case PrimitiveValue.OffsetDateTime(d) => Right(d)
        case _ =>
          Left(
            SchemaError.invalidType(DynamicOptic.root, s"Expected OffsetDateTime but got: $value")
          )
      }
  }

  case class OffsetTime(validation: Validation[java.time.OffsetTime]) extends Ref[java.time.OffsetTime] {
    def toDynamicValue(value: java.time.OffsetTime): DynamicValue =
      DynamicValue.Primitive(PrimitiveValue.OffsetTime(value))

    protected def fromPrimitiveValue(value: PrimitiveValue): Either[SchemaError, java.time.OffsetTime] = value match {
      case PrimitiveValue.OffsetTime(t) => Right(t)
      case _ =>
        Left(SchemaError.invalidType(DynamicOptic.root, s"Expected OffsetTime but got: $value"))
    }
  }

  case class Period(validation: Validation[java.time.Period]) extends Ref[java.time.Period] {
    def toDynamicValue(value: java.time.Period): DynamicValue = DynamicValue.Primitive(PrimitiveValue.Period(value))

    protected def fromPrimitiveValue(value: PrimitiveValue): Either[SchemaError, java.time.Period] = value match {
      case PrimitiveValue.Period(p) => Right(p)
      case _                        => Left(SchemaError.invalidType(DynamicOptic.root, s"Expected Period but got: $value"))
    }
  }

  case class Year(validation: Validation[java.time.Year]) extends Ref[java.time.Year] {
    def toDynamicValue(value: java.time.Year): DynamicValue = DynamicValue.Primitive(PrimitiveValue.Year(value))

    protected def fromPrimitiveValue(value: PrimitiveValue): Either[SchemaError, java.time.Year] = value match {
      case PrimitiveValue.Year(y) => Right(y)
      case _                      => Left(SchemaError.invalidType(DynamicOptic.root, s"Expected Year but got: $value"))
    }
  }

  case class YearMonth(validation: Validation[java.time.YearMonth]) extends Ref[java.time.YearMonth] {
    def toDynamicValue(value: java.time.YearMonth): DynamicValue =
      DynamicValue.Primitive(PrimitiveValue.YearMonth(value))

    protected def fromPrimitiveValue(value: PrimitiveValue): Either[SchemaError, java.time.YearMonth] = value match {
      case PrimitiveValue.YearMonth(y) => Right(y)
      case _ =>
        Left(SchemaError.invalidType(DynamicOptic.root, s"Expected YearMonth but got: $value"))
    }
  }

  case class ZoneId(validation: Validation[java.time.ZoneId]) extends Ref[java.time.ZoneId] {
    def toDynamicValue(value: java.time.ZoneId): DynamicValue = DynamicValue.Primitive(PrimitiveValue.ZoneId(value))

    protected def fromPrimitiveValue(value: PrimitiveValue): Either[SchemaError, java.time.ZoneId] = value match {
      case PrimitiveValue.ZoneId(z) => Right(z)
      case _                        => Left(SchemaError.invalidType(DynamicOptic.root, s"Expected ZoneId but got: $value"))
    }
  }

  case class ZoneOffset(validation: Validation[java.time.ZoneOffset]) extends Ref[java.time.ZoneOffset] {
    def toDynamicValue(value: java.time.ZoneOffset): DynamicValue =
      DynamicValue.Primitive(PrimitiveValue.ZoneOffset(value))

    protected def fromPrimitiveValue(value: PrimitiveValue): Either[SchemaError, java.time.ZoneOffset] = value match {
      case PrimitiveValue.ZoneOffset(z) => Right(z)
      case _ =>
        Left(SchemaError.invalidType(DynamicOptic.root, s"Expected ZoneOffset but got: $value"))
    }
  }

  case class ZonedDateTime(validation: Validation[java.time.ZonedDateTime]) extends Ref[java.time.ZonedDateTime] {
    def toDynamicValue(value: java.time.ZonedDateTime): DynamicValue =
      DynamicValue.Primitive(PrimitiveValue.ZonedDateTime(value))

    protected def fromPrimitiveValue(value: PrimitiveValue): Either[SchemaError, java.time.ZonedDateTime] =
      value match {
        case PrimitiveValue.ZonedDateTime(z) => Right(z)
        case _ =>
          Left(SchemaError.invalidType(DynamicOptic.root, s"Expected ZonedDateTime but got: $value"))
      }
  }

  case class UUID(validation: Validation[java.util.UUID]) extends Ref[java.util.UUID] {
    def toDynamicValue(value: java.util.UUID): DynamicValue = DynamicValue.Primitive(PrimitiveValue.UUID(value))

    protected def fromPrimitiveValue(value: PrimitiveValue): Either[SchemaError, java.util.UUID] = value match {
      case PrimitiveValue.UUID(u) => Right(u)
      case _                      => Left(SchemaError.invalidType(DynamicOptic.root, s"Expected UUID but got: $value"))
    }
  }

  case class Currency(validation: Validation[java.util.Currency]) extends Ref[java.util.Currency] {
    def toDynamicValue(value: java.util.Currency): DynamicValue = DynamicValue.Primitive(PrimitiveValue.Currency(value))

    protected def fromPrimitiveValue(value: PrimitiveValue): Either[SchemaError, java.util.Currency] = value match {
      case PrimitiveValue.Currency(c) => Right(c)
      case _ =>
        Left(SchemaError.invalidType(DynamicOptic.root, s"Expected Currency but got: $value"))
    }
  }
}
