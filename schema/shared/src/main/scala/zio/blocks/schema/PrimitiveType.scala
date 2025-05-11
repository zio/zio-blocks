package zio.blocks.schema

sealed trait PrimitiveType[A] {
  def fromDynamicValue(value: DynamicValue): Either[SchemaError, A]

  def toDynamicValue(value: A): DynamicValue

  def validation: Validation[A]
}

object PrimitiveType {
  sealed trait Val[A <: AnyVal] extends PrimitiveType[A] {
    def fromDynamicValue(value: DynamicValue): Either[SchemaError, A] = value match {
      case DynamicValue.Primitive(primitiveValue) => fromPrimitiveValue(primitiveValue)
      case _                                      => new Left(SchemaError.invalidType(DynamicOptic.root, "Expected a primitive value"))
    }

    protected def fromPrimitiveValue(value: PrimitiveValue): Either[SchemaError, A]
  }

  sealed trait Ref[A <: AnyRef] extends PrimitiveType[A] {
    def fromDynamicValue(value: DynamicValue): Either[SchemaError, A] = value match {
      case DynamicValue.Primitive(primitiveValue) => fromPrimitiveValue(primitiveValue)
      case _                                      => new Left(SchemaError.invalidType(DynamicOptic.root, "Expected a primitive value"))
    }

    protected def fromPrimitiveValue(value: PrimitiveValue): Either[SchemaError, A]
  }

  case object Unit extends Val[scala.Unit] {
    def validation: Validation[scala.Unit] = Validation.None

    def toDynamicValue(value: scala.Unit): DynamicValue = new DynamicValue.Primitive(PrimitiveValue.Unit)

    protected def fromPrimitiveValue(value: PrimitiveValue): Either[SchemaError, scala.Unit] = value match {
      case PrimitiveValue.Unit => new Right(())
      case _                   => new Left(SchemaError.invalidType(DynamicOptic.root, "Expected Unit"))
    }
  }

  case class Boolean(validation: Validation[scala.Boolean]) extends Val[scala.Boolean] {
    def toDynamicValue(value: scala.Boolean): DynamicValue =
      new DynamicValue.Primitive(new PrimitiveValue.Boolean(value))

    protected def fromPrimitiveValue(value: PrimitiveValue): Either[SchemaError, scala.Boolean] = value match {
      case PrimitiveValue.Boolean(b) => new Right(b)
      case _                         => new Left(SchemaError.invalidType(DynamicOptic.root, "Expected Boolean"))
    }
  }

  case class Byte(validation: Validation[scala.Byte]) extends Val[scala.Byte] {
    def toDynamicValue(value: scala.Byte): DynamicValue = new DynamicValue.Primitive(new PrimitiveValue.Byte(value))

    protected def fromPrimitiveValue(value: PrimitiveValue): Either[SchemaError, scala.Byte] = value match {
      case PrimitiveValue.Byte(b) => new Right(b)
      case _                      => new Left(SchemaError.invalidType(DynamicOptic.root, "Expected Byte"))
    }
  }

  case class Short(validation: Validation[scala.Short]) extends Val[scala.Short] {
    def toDynamicValue(value: scala.Short): DynamicValue = new DynamicValue.Primitive(new PrimitiveValue.Short(value))

    protected def fromPrimitiveValue(value: PrimitiveValue): Either[SchemaError, scala.Short] = value match {
      case PrimitiveValue.Short(s) => new Right(s)
      case _                       => new Left(SchemaError.invalidType(DynamicOptic.root, "Expected Short"))
    }
  }

  case class Int(validation: Validation[scala.Int]) extends Val[scala.Int] {
    def toDynamicValue(value: scala.Int): DynamicValue = new DynamicValue.Primitive(new PrimitiveValue.Int(value))

    protected def fromPrimitiveValue(value: PrimitiveValue): Either[SchemaError, scala.Int] = value match {
      case PrimitiveValue.Int(i) => new Right(i)
      case _                     => new Left(SchemaError.invalidType(DynamicOptic.root, "Expected Int"))
    }
  }

  case class Long(validation: Validation[scala.Long]) extends Val[scala.Long] {
    def toDynamicValue(value: scala.Long): DynamicValue = new DynamicValue.Primitive(new PrimitiveValue.Long(value))

    protected def fromPrimitiveValue(value: PrimitiveValue): Either[SchemaError, scala.Long] = value match {
      case PrimitiveValue.Long(l) => new Right(l)
      case _                      => new Left(SchemaError.invalidType(DynamicOptic.root, "Expected Long"))
    }
  }

  case class Float(validation: Validation[scala.Float]) extends Val[scala.Float] {
    def toDynamicValue(value: scala.Float): DynamicValue = new DynamicValue.Primitive(new PrimitiveValue.Float(value))

    protected def fromPrimitiveValue(value: PrimitiveValue): Either[SchemaError, scala.Float] = value match {
      case PrimitiveValue.Float(f) => Right(f)
      case _                       => Left(SchemaError.invalidType(DynamicOptic.root, "Expected Float"))
    }
  }

  case class Double(validation: Validation[scala.Double]) extends Val[scala.Double] {
    def toDynamicValue(value: scala.Double): DynamicValue = new DynamicValue.Primitive(new PrimitiveValue.Double(value))

    protected def fromPrimitiveValue(value: PrimitiveValue): Either[SchemaError, scala.Double] = value match {
      case PrimitiveValue.Double(d) => new Right(d)
      case _                        => new Left(SchemaError.invalidType(DynamicOptic.root, "Expected Double"))
    }
  }

  case class Char(validation: Validation[scala.Char]) extends Val[scala.Char] {
    def toDynamicValue(value: scala.Char): DynamicValue = new DynamicValue.Primitive(new PrimitiveValue.Char(value))

    protected def fromPrimitiveValue(value: PrimitiveValue): Either[SchemaError, scala.Char] = value match {
      case PrimitiveValue.Char(c) => new Right(c)
      case _                      => new Left(SchemaError.invalidType(DynamicOptic.root, "Expected Char"))
    }
  }

  case class String(validation: Validation[Predef.String]) extends Ref[Predef.String] {
    def toDynamicValue(value: Predef.String): DynamicValue =
      new DynamicValue.Primitive(new PrimitiveValue.String(value))

    protected def fromPrimitiveValue(value: PrimitiveValue): Either[SchemaError, Predef.String] = value match {
      case PrimitiveValue.String(s) => new Right(s)
      case _                        => new Left(SchemaError.invalidType(DynamicOptic.root, "Expected String"))
    }
  }

  case class BigInt(validation: Validation[scala.BigInt]) extends Ref[scala.BigInt] {
    def toDynamicValue(value: scala.BigInt): DynamicValue = new DynamicValue.Primitive(new PrimitiveValue.BigInt(value))

    protected def fromPrimitiveValue(value: PrimitiveValue): Either[SchemaError, scala.BigInt] = value match {
      case PrimitiveValue.BigInt(b) => new Right(b)
      case _                        => new Left(SchemaError.invalidType(DynamicOptic.root, "Expected BigInt"))
    }
  }

  case class BigDecimal(validation: Validation[scala.BigDecimal]) extends Ref[scala.BigDecimal] {
    def toDynamicValue(value: scala.BigDecimal): DynamicValue =
      new DynamicValue.Primitive(new PrimitiveValue.BigDecimal(value))

    protected def fromPrimitiveValue(value: PrimitiveValue): Either[SchemaError, scala.BigDecimal] = value match {
      case PrimitiveValue.BigDecimal(b) => new Right(b)
      case _                            => new Left(SchemaError.invalidType(DynamicOptic.root, "Expected BigDecimal"))
    }
  }

  case class DayOfWeek(validation: Validation[java.time.DayOfWeek]) extends Ref[java.time.DayOfWeek] {
    def toDynamicValue(value: java.time.DayOfWeek): DynamicValue =
      new DynamicValue.Primitive(new PrimitiveValue.DayOfWeek(value))

    protected def fromPrimitiveValue(value: PrimitiveValue): Either[SchemaError, java.time.DayOfWeek] = value match {
      case PrimitiveValue.DayOfWeek(d) => new Right(d)
      case _                           => new Left(SchemaError.invalidType(DynamicOptic.root, "Expected DayOfWeek"))
    }
  }

  case class Duration(validation: Validation[java.time.Duration]) extends Ref[java.time.Duration] {
    def toDynamicValue(value: java.time.Duration): DynamicValue =
      new DynamicValue.Primitive(new PrimitiveValue.Duration(value))

    protected def fromPrimitiveValue(value: PrimitiveValue): Either[SchemaError, java.time.Duration] = value match {
      case PrimitiveValue.Duration(d) => new Right(d)
      case _                          => new Left(SchemaError.invalidType(DynamicOptic.root, "Expected Duration"))
    }
  }

  case class Instant(validation: Validation[java.time.Instant]) extends Ref[java.time.Instant] {
    def toDynamicValue(value: java.time.Instant): DynamicValue =
      new DynamicValue.Primitive(new PrimitiveValue.Instant(value))

    protected def fromPrimitiveValue(value: PrimitiveValue): Either[SchemaError, java.time.Instant] = value match {
      case PrimitiveValue.Instant(i) => new Right(i)
      case _                         => new Left(SchemaError.invalidType(DynamicOptic.root, "Expected Instant"))
    }
  }

  case class LocalDate(validation: Validation[java.time.LocalDate]) extends Ref[java.time.LocalDate] {
    def toDynamicValue(value: java.time.LocalDate): DynamicValue =
      new DynamicValue.Primitive(new PrimitiveValue.LocalDate(value))

    protected def fromPrimitiveValue(value: PrimitiveValue): Either[SchemaError, java.time.LocalDate] = value match {
      case PrimitiveValue.LocalDate(d) => new Right(d)
      case _                           => new Left(SchemaError.invalidType(DynamicOptic.root, "Expected LocalDate"))
    }
  }

  case class LocalDateTime(validation: Validation[java.time.LocalDateTime]) extends Ref[java.time.LocalDateTime] {
    def toDynamicValue(value: java.time.LocalDateTime): DynamicValue =
      new DynamicValue.Primitive(new PrimitiveValue.LocalDateTime(value))

    protected def fromPrimitiveValue(value: PrimitiveValue): Either[SchemaError, java.time.LocalDateTime] =
      value match {
        case PrimitiveValue.LocalDateTime(d) => new Right(d)
        case _                               => new Left(SchemaError.invalidType(DynamicOptic.root, "Expected LocalDateTime"))
      }
  }

  case class LocalTime(validation: Validation[java.time.LocalTime]) extends Ref[java.time.LocalTime] {
    def toDynamicValue(value: java.time.LocalTime): DynamicValue =
      new DynamicValue.Primitive(new PrimitiveValue.LocalTime(value))

    protected def fromPrimitiveValue(value: PrimitiveValue): Either[SchemaError, java.time.LocalTime] = value match {
      case PrimitiveValue.LocalTime(t) => new Right(t)
      case _                           => new Left(SchemaError.invalidType(DynamicOptic.root, "Expected LocalTime"))
    }
  }

  case class Month(validation: Validation[java.time.Month]) extends Ref[java.time.Month] {
    def toDynamicValue(value: java.time.Month): DynamicValue =
      new DynamicValue.Primitive(new PrimitiveValue.Month(value))

    protected def fromPrimitiveValue(value: PrimitiveValue): Either[SchemaError, java.time.Month] = value match {
      case PrimitiveValue.Month(m) => new Right(m)
      case _                       => new Left(SchemaError.invalidType(DynamicOptic.root, "Expected Month"))
    }
  }

  case class MonthDay(validation: Validation[java.time.MonthDay]) extends Ref[java.time.MonthDay] {
    def toDynamicValue(value: java.time.MonthDay): DynamicValue =
      new DynamicValue.Primitive(new PrimitiveValue.MonthDay(value))

    protected def fromPrimitiveValue(value: PrimitiveValue): Either[SchemaError, java.time.MonthDay] = value match {
      case PrimitiveValue.MonthDay(m) => new Right(m)
      case _                          => new Left(SchemaError.invalidType(DynamicOptic.root, "Expected MonthDay"))
    }
  }

  case class OffsetDateTime(validation: Validation[java.time.OffsetDateTime]) extends Ref[java.time.OffsetDateTime] {
    def toDynamicValue(value: java.time.OffsetDateTime): DynamicValue =
      new DynamicValue.Primitive(new PrimitiveValue.OffsetDateTime(value))

    protected def fromPrimitiveValue(value: PrimitiveValue): Either[SchemaError, java.time.OffsetDateTime] =
      value match {
        case PrimitiveValue.OffsetDateTime(d) => new Right(d)
        case _                                => new Left(SchemaError.invalidType(DynamicOptic.root, "Expected OffsetDateTime"))
      }
  }

  case class OffsetTime(validation: Validation[java.time.OffsetTime]) extends Ref[java.time.OffsetTime] {
    def toDynamicValue(value: java.time.OffsetTime): DynamicValue =
      new DynamicValue.Primitive(new PrimitiveValue.OffsetTime(value))

    protected def fromPrimitiveValue(value: PrimitiveValue): Either[SchemaError, java.time.OffsetTime] = value match {
      case PrimitiveValue.OffsetTime(t) => new Right(t)
      case _                            => new Left(SchemaError.invalidType(DynamicOptic.root, "Expected OffsetTime"))
    }
  }

  case class Period(validation: Validation[java.time.Period]) extends Ref[java.time.Period] {
    def toDynamicValue(value: java.time.Period): DynamicValue =
      new DynamicValue.Primitive(new PrimitiveValue.Period(value))

    protected def fromPrimitiveValue(value: PrimitiveValue): Either[SchemaError, java.time.Period] = value match {
      case PrimitiveValue.Period(p) => new Right(p)
      case _                        => new Left(SchemaError.invalidType(DynamicOptic.root, "Expected Period"))
    }
  }

  case class Year(validation: Validation[java.time.Year]) extends Ref[java.time.Year] {
    def toDynamicValue(value: java.time.Year): DynamicValue = new DynamicValue.Primitive(new PrimitiveValue.Year(value))

    protected def fromPrimitiveValue(value: PrimitiveValue): Either[SchemaError, java.time.Year] = value match {
      case PrimitiveValue.Year(y) => new Right(y)
      case _                      => new Left(SchemaError.invalidType(DynamicOptic.root, "Expected Year"))
    }
  }

  case class YearMonth(validation: Validation[java.time.YearMonth]) extends Ref[java.time.YearMonth] {
    def toDynamicValue(value: java.time.YearMonth): DynamicValue =
      new DynamicValue.Primitive(new PrimitiveValue.YearMonth(value))

    protected def fromPrimitiveValue(value: PrimitiveValue): Either[SchemaError, java.time.YearMonth] = value match {
      case PrimitiveValue.YearMonth(y) => new Right(y)
      case _                           => new Left(SchemaError.invalidType(DynamicOptic.root, "Expected YearMonth"))
    }
  }

  case class ZoneId(validation: Validation[java.time.ZoneId]) extends Ref[java.time.ZoneId] {
    def toDynamicValue(value: java.time.ZoneId): DynamicValue =
      new DynamicValue.Primitive(new PrimitiveValue.ZoneId(value))

    protected def fromPrimitiveValue(value: PrimitiveValue): Either[SchemaError, java.time.ZoneId] = value match {
      case PrimitiveValue.ZoneId(z) => new Right(z)
      case _                        => new Left(SchemaError.invalidType(DynamicOptic.root, "Expected ZoneId"))
    }
  }

  case class ZoneOffset(validation: Validation[java.time.ZoneOffset]) extends Ref[java.time.ZoneOffset] {
    def toDynamicValue(value: java.time.ZoneOffset): DynamicValue =
      new DynamicValue.Primitive(new PrimitiveValue.ZoneOffset(value))

    protected def fromPrimitiveValue(value: PrimitiveValue): Either[SchemaError, java.time.ZoneOffset] = value match {
      case PrimitiveValue.ZoneOffset(z) => new Right(z)
      case _                            => new Left(SchemaError.invalidType(DynamicOptic.root, "Expected ZoneOffset"))
    }
  }

  case class ZonedDateTime(validation: Validation[java.time.ZonedDateTime]) extends Ref[java.time.ZonedDateTime] {
    def toDynamicValue(value: java.time.ZonedDateTime): DynamicValue =
      new DynamicValue.Primitive(new PrimitiveValue.ZonedDateTime(value))

    protected def fromPrimitiveValue(value: PrimitiveValue): Either[SchemaError, java.time.ZonedDateTime] =
      value match {
        case PrimitiveValue.ZonedDateTime(z) => new Right(z)
        case _                               => new Left(SchemaError.invalidType(DynamicOptic.root, "Expected ZonedDateTime"))
      }
  }

  case class UUID(validation: Validation[java.util.UUID]) extends Ref[java.util.UUID] {
    def toDynamicValue(value: java.util.UUID): DynamicValue =
      new DynamicValue.Primitive(new PrimitiveValue.UUID(value))

    protected def fromPrimitiveValue(value: PrimitiveValue): Either[SchemaError, java.util.UUID] = value match {
      case PrimitiveValue.UUID(u) => new Right(u)
      case _                      => new Left(SchemaError.invalidType(DynamicOptic.root, "Expected UUID"))
    }
  }

  case class Currency(validation: Validation[java.util.Currency]) extends Ref[java.util.Currency] {
    def toDynamicValue(value: java.util.Currency): DynamicValue =
      new DynamicValue.Primitive(new PrimitiveValue.Currency(value))

    protected def fromPrimitiveValue(value: PrimitiveValue): Either[SchemaError, java.util.Currency] = value match {
      case PrimitiveValue.Currency(c) => new Right(c)
      case _                          => new Left(SchemaError.invalidType(DynamicOptic.root, "Expected Currency"))
    }
  }
}
