package zio.blocks.schema

sealed trait PrimitiveValue {
  type Type

  def primitiveType: PrimitiveType[Type]

  def typeIndex: scala.Int

  def compare(that: PrimitiveValue): scala.Int

  final def >(that: PrimitiveValue): Boolean = compare(that) > 0

  final def >=(that: PrimitiveValue): Boolean = compare(that) >= 0

  final def <(that: PrimitiveValue): Boolean = compare(that) < 0

  final def <=(that: PrimitiveValue): Boolean = compare(that) <= 0
}

object PrimitiveValue {
  import zio.blocks.schema.binding._
  import zio.blocks.schema.binding.RegisterOffset.RegisterOffset
  case object Unit extends PrimitiveValue {
    type Type = scala.Unit

    def primitiveType: PrimitiveType[scala.Unit] = PrimitiveType.Unit

    def typeIndex: scala.Int = 0

    def compare(that: PrimitiveValue): scala.Int = that match {
      case _: Unit.type => 0
      case _            => -that.typeIndex
    }
  }

  case class Boolean(value: scala.Boolean) extends PrimitiveValue {
    type Type = scala.Boolean

    def primitiveType: PrimitiveType[scala.Boolean] = PrimitiveType.Boolean(Validation.None)

    def typeIndex: scala.Int = 1

    def compare(that: PrimitiveValue): scala.Int = that match {
      case x: Boolean => value.compare(x.value)
      case _          => 1 - that.typeIndex
    }
  }

  case class Byte(value: scala.Byte) extends PrimitiveValue {
    type Type = scala.Byte

    def primitiveType: PrimitiveType[scala.Byte] = PrimitiveType.Byte(Validation.None)

    def typeIndex: scala.Int = 2

    def compare(that: PrimitiveValue): scala.Int = that match {
      case x: Byte => value - x.value
      case _       => 2 - that.typeIndex
    }
  }

  case class Short(value: scala.Short) extends PrimitiveValue {
    type Type = scala.Short

    def primitiveType: PrimitiveType[scala.Short] = PrimitiveType.Short(Validation.None)

    def typeIndex: scala.Int = 3

    def compare(that: PrimitiveValue): scala.Int = that match {
      case x: Short => value - x.value
      case _        => 3 - that.typeIndex
    }
  }

  case class Int(value: scala.Int) extends PrimitiveValue {
    type Type = scala.Int

    def primitiveType: PrimitiveType[scala.Int] = PrimitiveType.Int(Validation.None)

    def typeIndex: scala.Int = 4

    def compare(that: PrimitiveValue): scala.Int = that match {
      case x: Int => value - x.value
      case _      => 4 - that.typeIndex
    }
  }

  case class Long(value: scala.Long) extends PrimitiveValue {
    type Type = scala.Long

    def primitiveType: PrimitiveType[scala.Long] = PrimitiveType.Long(Validation.None)

    def typeIndex: scala.Int = 5

    def compare(that: PrimitiveValue): scala.Int = that match {
      case x: Long => value.compare(x.value)
      case _       => 5 - that.typeIndex
    }
  }

  case class Float(value: scala.Float) extends PrimitiveValue {
    type Type = scala.Float

    def primitiveType: PrimitiveType[scala.Float] = PrimitiveType.Float(Validation.None)

    def typeIndex: scala.Int = 6

    def compare(that: PrimitiveValue): scala.Int = that match {
      case x: Float => value.compare(x.value)
      case _        => 6 - that.typeIndex
    }
  }

  case class Double(value: scala.Double) extends PrimitiveValue {
    type Type = scala.Double

    def primitiveType: PrimitiveType[scala.Double] = PrimitiveType.Double(Validation.None)

    def typeIndex: scala.Int = 7

    def compare(that: PrimitiveValue): scala.Int = that match {
      case x: Double => value.compare(x.value)
      case _         => 7 - that.typeIndex
    }
  }

  case class Char(value: scala.Char) extends PrimitiveValue {
    type Type = scala.Char

    def primitiveType: PrimitiveType[scala.Char] = PrimitiveType.Char(Validation.None)

    def typeIndex: scala.Int = 8

    def compare(that: PrimitiveValue): scala.Int = that match {
      case x: Char => value.compare(x.value)
      case _       => 8 - that.typeIndex
    }
  }

  case class String(value: Predef.String) extends PrimitiveValue {
    type Type = Predef.String

    def primitiveType: PrimitiveType[Predef.String] = PrimitiveType.String(Validation.None)

    def typeIndex: scala.Int = 9

    def compare(that: PrimitiveValue): scala.Int = that match {
      case x: String => value.compareTo(x.value)
      case _         => 9 - that.typeIndex
    }
  }

  case class BigInt(value: scala.BigInt) extends PrimitiveValue {
    type Type = scala.BigInt

    def primitiveType: PrimitiveType[scala.BigInt] = PrimitiveType.BigInt(Validation.None)

    def typeIndex: scala.Int = 10

    def compare(that: PrimitiveValue): scala.Int = that match {
      case x: BigInt => value.compareTo(x.value)
      case _         => 10 - that.typeIndex
    }
  }

  case class BigDecimal(value: scala.BigDecimal) extends PrimitiveValue {
    type Type = scala.BigDecimal

    def primitiveType: PrimitiveType[scala.BigDecimal] = PrimitiveType.BigDecimal(Validation.None)

    def typeIndex: scala.Int = 11

    def compare(that: PrimitiveValue): scala.Int = that match {
      case x: BigDecimal => value.compareTo(x.value)
      case _             => 11 - that.typeIndex
    }
  }

  case class DayOfWeek(value: java.time.DayOfWeek) extends PrimitiveValue {
    type Type = java.time.DayOfWeek

    def primitiveType: PrimitiveType[java.time.DayOfWeek] = PrimitiveType.DayOfWeek(Validation.None)

    def typeIndex: scala.Int = 12

    def compare(that: PrimitiveValue): scala.Int = that match {
      case x: DayOfWeek => value.compareTo(x.value)
      case _            => 12 - that.typeIndex
    }
  }

  case class Duration(value: java.time.Duration) extends PrimitiveValue {
    type Type = java.time.Duration

    def primitiveType: PrimitiveType[java.time.Duration] = PrimitiveType.Duration(Validation.None)

    def typeIndex: scala.Int = 13

    def compare(that: PrimitiveValue): scala.Int = that match {
      case x: Duration => value.compareTo(x.value)
      case _           => 13 - that.typeIndex
    }
  }

  case class Instant(value: java.time.Instant) extends PrimitiveValue {
    type Type = java.time.Instant

    def primitiveType: PrimitiveType[java.time.Instant] = PrimitiveType.Instant(Validation.None)

    def typeIndex: scala.Int = 14

    def compare(that: PrimitiveValue): scala.Int = that match {
      case x: Instant => value.compareTo(x.value)
      case _          => 14 - that.typeIndex
    }
  }

  case class LocalDate(value: java.time.LocalDate) extends PrimitiveValue {
    type Type = java.time.LocalDate

    def primitiveType: PrimitiveType[java.time.LocalDate] = PrimitiveType.LocalDate(Validation.None)

    def typeIndex: scala.Int = 15

    def compare(that: PrimitiveValue): scala.Int = that match {
      case x: LocalDate => value.compareTo(x.value)
      case _            => 15 - that.typeIndex
    }
  }

  case class LocalDateTime(value: java.time.LocalDateTime) extends PrimitiveValue {
    type Type = java.time.LocalDateTime

    def primitiveType: PrimitiveType[java.time.LocalDateTime] = PrimitiveType.LocalDateTime(Validation.None)

    def typeIndex: scala.Int = 16

    def compare(that: PrimitiveValue): scala.Int = that match {
      case x: LocalDateTime => value.compareTo(x.value)
      case _                => 16 - that.typeIndex
    }
  }

  case class LocalTime(value: java.time.LocalTime) extends PrimitiveValue {
    type Type = java.time.LocalTime

    def primitiveType: PrimitiveType[java.time.LocalTime] = PrimitiveType.LocalTime(Validation.None)

    def typeIndex: scala.Int = 17

    def compare(that: PrimitiveValue): scala.Int = that match {
      case x: LocalTime => value.compareTo(x.value)
      case _            => 17 - that.typeIndex
    }
  }

  case class Month(value: java.time.Month) extends PrimitiveValue {
    type Type = java.time.Month

    def primitiveType: PrimitiveType[java.time.Month] = PrimitiveType.Month(Validation.None)

    def typeIndex: scala.Int = 18

    def compare(that: PrimitiveValue): scala.Int = that match {
      case x: Month => value.compareTo(x.value)
      case _        => 18 - that.typeIndex
    }
  }

  case class MonthDay(value: java.time.MonthDay) extends PrimitiveValue {
    type Type = java.time.MonthDay

    def primitiveType: PrimitiveType[java.time.MonthDay] = PrimitiveType.MonthDay(Validation.None)

    def typeIndex: scala.Int = 19

    def compare(that: PrimitiveValue): scala.Int = that match {
      case x: MonthDay => value.compareTo(x.value)
      case _           => 19 - that.typeIndex
    }
  }

  case class OffsetDateTime(value: java.time.OffsetDateTime) extends PrimitiveValue {
    type Type = java.time.OffsetDateTime

    def primitiveType: PrimitiveType[java.time.OffsetDateTime] = PrimitiveType.OffsetDateTime(Validation.None)

    def typeIndex: scala.Int = 20

    def compare(that: PrimitiveValue): scala.Int = that match {
      case x: OffsetDateTime => value.compareTo(x.value)
      case _                 => 20 - that.typeIndex
    }
  }

  case class OffsetTime(value: java.time.OffsetTime) extends PrimitiveValue {
    type Type = java.time.OffsetTime

    def primitiveType: PrimitiveType[java.time.OffsetTime] = PrimitiveType.OffsetTime(Validation.None)

    def typeIndex: scala.Int = 21

    def compare(that: PrimitiveValue): scala.Int = that match {
      case x: OffsetTime => value.compareTo(x.value)
      case _             => 21 - that.typeIndex
    }
  }

  case class Period(value: java.time.Period) extends PrimitiveValue {
    type Type = java.time.Period

    def primitiveType: PrimitiveType[java.time.Period] = PrimitiveType.Period(Validation.None)

    def typeIndex: scala.Int = 22

    def compare(that: PrimitiveValue): scala.Int = that match {
      case x: Period =>
        val xv = x.value
        (value.toTotalMonths * 30.436875 + value.getDays).compareTo(xv.toTotalMonths * 30.436875 + xv.getDays)
      case _ => 22 - that.typeIndex
    }
  }

  case class Year(value: java.time.Year) extends PrimitiveValue {
    type Type = java.time.Year

    def primitiveType: PrimitiveType[java.time.Year] = PrimitiveType.Year(Validation.None)

    def typeIndex: scala.Int = 23

    def compare(that: PrimitiveValue): scala.Int = that match {
      case x: Year => value.compareTo(x.value)
      case _       => 23 - that.typeIndex
    }
  }

  case class YearMonth(value: java.time.YearMonth) extends PrimitiveValue {
    type Type = java.time.YearMonth

    def primitiveType: PrimitiveType[java.time.YearMonth] = PrimitiveType.YearMonth(Validation.None)

    def typeIndex: scala.Int = 24

    def compare(that: PrimitiveValue): scala.Int = that match {
      case x: YearMonth => value.compareTo(x.value)
      case _            => 24 - that.typeIndex
    }
  }

  case class ZoneId(value: java.time.ZoneId) extends PrimitiveValue {
    type Type = java.time.ZoneId

    def primitiveType: PrimitiveType[java.time.ZoneId] = PrimitiveType.ZoneId(Validation.None)

    def typeIndex: scala.Int = 25

    def compare(that: PrimitiveValue): scala.Int = that match {
      case x: ZoneId => value.getId.compareTo(x.value.getId)
      case _         => 25 - that.typeIndex
    }
  }

  case class ZoneOffset(value: java.time.ZoneOffset) extends PrimitiveValue {
    type Type = java.time.ZoneOffset

    def primitiveType: PrimitiveType[java.time.ZoneOffset] = PrimitiveType.ZoneOffset(Validation.None)

    def typeIndex: scala.Int = 26

    def compare(that: PrimitiveValue): scala.Int = that match {
      case x: ZoneOffset => value.compareTo(x.value)
      case _             => 26 - that.typeIndex
    }
  }

  case class ZonedDateTime(value: java.time.ZonedDateTime) extends PrimitiveValue {
    type Type = java.time.ZonedDateTime

    def primitiveType: PrimitiveType[java.time.ZonedDateTime] = PrimitiveType.ZonedDateTime(Validation.None)

    def typeIndex: scala.Int = 27

    def compare(that: PrimitiveValue): scala.Int = that match {
      case x: ZonedDateTime => value.compareTo(x.value)
      case _                => 27 - that.typeIndex
    }
  }

  case class Currency(value: java.util.Currency) extends PrimitiveValue {
    type Type = java.util.Currency

    def primitiveType: PrimitiveType[java.util.Currency] = PrimitiveType.Currency(Validation.None)

    def typeIndex: scala.Int = 28

    def compare(that: PrimitiveValue): scala.Int = that match {
      case x: Currency => value.getCurrencyCode.compareTo(x.value.getCurrencyCode)
      case _           => 28 - that.typeIndex
    }
  }

  case class UUID(value: java.util.UUID) extends PrimitiveValue {
    type Type = java.util.UUID

    def primitiveType: PrimitiveType[java.util.UUID] = PrimitiveType.UUID(Validation.None)

    def typeIndex: scala.Int = 29

    def compare(that: PrimitiveValue): scala.Int = that match {
      case x: UUID => value.compareTo(x.value)
      case _       => 29 - that.typeIndex
    }
  }

  implicit val ordering: Ordering[PrimitiveValue] = new Ordering[PrimitiveValue] {
    def compare(x: PrimitiveValue, y: PrimitiveValue): scala.Int = x.compare(y)
  }

  implicit lazy val unitSchema: Schema[Unit.type] = new Schema(
    reflect = new Reflect.Record[Binding, Unit.type](
      fields = Vector.empty,
      typeName = new TypeName(new Namespace(List("zio", "blocks", "schema", "PrimitiveValue")), "Unit"),
      recordBinding = new Binding.Record(
        constructor = new ConstantConstructor[Unit.type](Unit),
        deconstructor = new ConstantDeconstructor[Unit.type]
      ),
      modifiers = Vector.empty
    )
  )

  implicit lazy val booleanSchema: Schema[Boolean] = new Schema(
    reflect = new Reflect.Record[Binding, Boolean](
      fields = Vector(Schema[scala.Boolean].reflect.asTerm("value")),
      typeName = new TypeName(new Namespace(List("zio", "blocks", "schema", "PrimitiveValue")), "Boolean"),
      recordBinding = new Binding.Record(
        constructor = new Constructor[Boolean] {
          def usedRegisters: RegisterOffset                             = RegisterOffset(booleans = 1)
          def construct(in: Registers, offset: RegisterOffset): Boolean = new Boolean(in.getBoolean(offset))
        },
        deconstructor = new Deconstructor[Boolean] {
          def usedRegisters: RegisterOffset                                                = RegisterOffset(booleans = 1)
          def deconstruct(out: Registers, offset: RegisterOffset, in: Boolean): scala.Unit =
            out.setBoolean(offset, in.value)
        }
      ),
      modifiers = Vector.empty
    )
  )

  implicit lazy val byteSchema: Schema[Byte] = new Schema(
    reflect = new Reflect.Record[Binding, Byte](
      fields = Vector(Schema[scala.Byte].reflect.asTerm("value")),
      typeName = new TypeName(new Namespace(List("zio", "blocks", "schema", "PrimitiveValue")), "Byte"),
      recordBinding = new Binding.Record(
        constructor = new Constructor[Byte] {
          def usedRegisters: RegisterOffset                          = RegisterOffset(bytes = 1)
          def construct(in: Registers, offset: RegisterOffset): Byte = new Byte(in.getByte(offset))
        },
        deconstructor = new Deconstructor[Byte] {
          def usedRegisters: RegisterOffset                                             = RegisterOffset(bytes = 1)
          def deconstruct(out: Registers, offset: RegisterOffset, in: Byte): scala.Unit = out.setByte(offset, in.value)
        }
      ),
      modifiers = Vector.empty
    )
  )

  implicit lazy val shortSchema: Schema[Short] = new Schema(
    reflect = new Reflect.Record[Binding, Short](
      fields = Vector(Schema[scala.Short].reflect.asTerm("value")),
      typeName = new TypeName(new Namespace(List("zio", "blocks", "schema", "PrimitiveValue")), "Short"),
      recordBinding = new Binding.Record(
        constructor = new Constructor[Short] {
          def usedRegisters: RegisterOffset                           = RegisterOffset(shorts = 1)
          def construct(in: Registers, offset: RegisterOffset): Short = new Short(in.getShort(offset))
        },
        deconstructor = new Deconstructor[Short] {
          def usedRegisters: RegisterOffset                                              = RegisterOffset(shorts = 1)
          def deconstruct(out: Registers, offset: RegisterOffset, in: Short): scala.Unit =
            out.setShort(offset, in.value)
        }
      ),
      modifiers = Vector.empty
    )
  )

  implicit lazy val intSchema: Schema[Int] = new Schema(
    reflect = new Reflect.Record[Binding, Int](
      fields = Vector(Schema[scala.Int].reflect.asTerm("value")),
      typeName = new TypeName(new Namespace(List("zio", "blocks", "schema", "PrimitiveValue")), "Int"),
      recordBinding = new Binding.Record(
        constructor = new Constructor[Int] {
          def usedRegisters: RegisterOffset                         = RegisterOffset(ints = 1)
          def construct(in: Registers, offset: RegisterOffset): Int = new Int(in.getInt(offset))
        },
        deconstructor = new Deconstructor[Int] {
          def usedRegisters: RegisterOffset                                            = RegisterOffset(ints = 1)
          def deconstruct(out: Registers, offset: RegisterOffset, in: Int): scala.Unit = out.setInt(offset, in.value)
        }
      ),
      modifiers = Vector.empty
    )
  )

  implicit lazy val longSchema: Schema[Long] = new Schema(
    reflect = new Reflect.Record[Binding, Long](
      fields = Vector(Schema[scala.Long].reflect.asTerm("value")),
      typeName = new TypeName(new Namespace(List("zio", "blocks", "schema", "PrimitiveValue")), "Long"),
      recordBinding = new Binding.Record(
        constructor = new Constructor[Long] {
          def usedRegisters: RegisterOffset                          = RegisterOffset(longs = 1)
          def construct(in: Registers, offset: RegisterOffset): Long = new Long(in.getLong(offset))
        },
        deconstructor = new Deconstructor[Long] {
          def usedRegisters: RegisterOffset                                             = RegisterOffset(longs = 1)
          def deconstruct(out: Registers, offset: RegisterOffset, in: Long): scala.Unit = out.setLong(offset, in.value)
        }
      ),
      modifiers = Vector.empty
    )
  )

  implicit lazy val floatSchema: Schema[Float] = new Schema(
    reflect = new Reflect.Record[Binding, Float](
      fields = Vector(Schema[scala.Float].reflect.asTerm("value")),
      typeName = new TypeName(new Namespace(List("zio", "blocks", "schema", "PrimitiveValue")), "Float"),
      recordBinding = new Binding.Record(
        constructor = new Constructor[Float] {
          def usedRegisters: RegisterOffset                           = RegisterOffset(floats = 1)
          def construct(in: Registers, offset: RegisterOffset): Float = new Float(in.getFloat(offset))
        },
        deconstructor = new Deconstructor[Float] {
          def usedRegisters: RegisterOffset                                              = RegisterOffset(floats = 1)
          def deconstruct(out: Registers, offset: RegisterOffset, in: Float): scala.Unit =
            out.setFloat(offset, in.value)
        }
      ),
      modifiers = Vector.empty
    )
  )

  implicit lazy val doubleSchema: Schema[Double] = new Schema(
    reflect = new Reflect.Record[Binding, Double](
      fields = Vector(Schema[scala.Double].reflect.asTerm("value")),
      typeName = new TypeName(new Namespace(List("zio", "blocks", "schema", "PrimitiveValue")), "Double"),
      recordBinding = new Binding.Record(
        constructor = new Constructor[Double] {
          def usedRegisters: RegisterOffset                            = RegisterOffset(doubles = 1)
          def construct(in: Registers, offset: RegisterOffset): Double = new Double(in.getDouble(offset))
        },
        deconstructor = new Deconstructor[Double] {
          def usedRegisters: RegisterOffset                                               = RegisterOffset(doubles = 1)
          def deconstruct(out: Registers, offset: RegisterOffset, in: Double): scala.Unit =
            out.setDouble(offset, in.value)
        }
      ),
      modifiers = Vector.empty
    )
  )

  implicit lazy val charSchema: Schema[Char] = new Schema(
    reflect = new Reflect.Record[Binding, Char](
      fields = Vector(Schema[scala.Char].reflect.asTerm("value")),
      typeName = new TypeName(new Namespace(List("zio", "blocks", "schema", "PrimitiveValue")), "Char"),
      recordBinding = new Binding.Record(
        constructor = new Constructor[Char] {
          def usedRegisters: RegisterOffset                          = RegisterOffset(chars = 1)
          def construct(in: Registers, offset: RegisterOffset): Char = new Char(in.getChar(offset))
        },
        deconstructor = new Deconstructor[Char] {
          def usedRegisters: RegisterOffset                                             = RegisterOffset(chars = 1)
          def deconstruct(out: Registers, offset: RegisterOffset, in: Char): scala.Unit = out.setChar(offset, in.value)
        }
      ),
      modifiers = Vector.empty
    )
  )

  implicit lazy val stringSchema: Schema[String] = new Schema(
    reflect = new Reflect.Record[Binding, String](
      fields = Vector(Schema[java.lang.String].reflect.asTerm("value")),
      typeName = new TypeName(new Namespace(List("zio", "blocks", "schema", "PrimitiveValue")), "String"),
      recordBinding = new Binding.Record(
        constructor = new Constructor[String] {
          def usedRegisters: RegisterOffset                            = RegisterOffset(objects = 1)
          def construct(in: Registers, offset: RegisterOffset): String =
            new String(in.getObject(offset).asInstanceOf[java.lang.String])
        },
        deconstructor = new Deconstructor[String] {
          def usedRegisters: RegisterOffset                                               = RegisterOffset(objects = 1)
          def deconstruct(out: Registers, offset: RegisterOffset, in: String): scala.Unit =
            out.setObject(offset, in.value)
        }
      ),
      modifiers = Vector.empty
    )
  )

  implicit lazy val bigIntSchema: Schema[BigInt] = new Schema(
    reflect = new Reflect.Record[Binding, BigInt](
      fields = Vector(Schema[scala.BigInt].reflect.asTerm("value")),
      typeName = new TypeName(new Namespace(List("zio", "blocks", "schema", "PrimitiveValue")), "BigInt"),
      recordBinding = new Binding.Record(
        constructor = new Constructor[BigInt] {
          def usedRegisters: RegisterOffset                            = RegisterOffset(objects = 1)
          def construct(in: Registers, offset: RegisterOffset): BigInt =
            new BigInt(in.getObject(offset).asInstanceOf[scala.BigInt])
        },
        deconstructor = new Deconstructor[BigInt] {
          def usedRegisters: RegisterOffset                                               = RegisterOffset(objects = 1)
          def deconstruct(out: Registers, offset: RegisterOffset, in: BigInt): scala.Unit =
            out.setObject(offset, in.value)
        }
      ),
      modifiers = Vector.empty
    )
  )

  implicit lazy val bigDecimalSchema: Schema[BigDecimal] = new Schema(
    reflect = new Reflect.Record[Binding, BigDecimal](
      fields = Vector(Schema[scala.BigDecimal].reflect.asTerm("value")),
      typeName = new TypeName(new Namespace(List("zio", "blocks", "schema", "PrimitiveValue")), "BigDecimal"),
      recordBinding = new Binding.Record(
        constructor = new Constructor[BigDecimal] {
          def usedRegisters: RegisterOffset                                = RegisterOffset(objects = 1)
          def construct(in: Registers, offset: RegisterOffset): BigDecimal =
            new BigDecimal(in.getObject(offset).asInstanceOf[scala.BigDecimal])
        },
        deconstructor = new Deconstructor[BigDecimal] {
          def usedRegisters: RegisterOffset                                                   = RegisterOffset(objects = 1)
          def deconstruct(out: Registers, offset: RegisterOffset, in: BigDecimal): scala.Unit =
            out.setObject(offset, in.value)
        }
      ),
      modifiers = Vector.empty
    )
  )

  implicit lazy val dayOfWeekSchema: Schema[DayOfWeek] = new Schema(
    reflect = new Reflect.Record[Binding, DayOfWeek](
      fields = Vector(Schema[java.time.DayOfWeek].reflect.asTerm("value")),
      typeName = new TypeName(new Namespace(List("zio", "blocks", "schema", "PrimitiveValue")), "DayOfWeek"),
      recordBinding = new Binding.Record(
        constructor = new Constructor[DayOfWeek] {
          def usedRegisters: RegisterOffset                               = RegisterOffset(objects = 1)
          def construct(in: Registers, offset: RegisterOffset): DayOfWeek =
            new DayOfWeek(in.getObject(offset).asInstanceOf[java.time.DayOfWeek])
        },
        deconstructor = new Deconstructor[DayOfWeek] {
          def usedRegisters: RegisterOffset                                                  = RegisterOffset(objects = 1)
          def deconstruct(out: Registers, offset: RegisterOffset, in: DayOfWeek): scala.Unit =
            out.setObject(offset, in.value)
        }
      ),
      modifiers = Vector.empty
    )
  )

  implicit lazy val durationSchema: Schema[Duration] = new Schema(
    reflect = new Reflect.Record[Binding, Duration](
      fields = Vector(Schema[java.time.Duration].reflect.asTerm("value")),
      typeName = new TypeName(new Namespace(List("zio", "blocks", "schema", "PrimitiveValue")), "Duration"),
      recordBinding = new Binding.Record(
        constructor = new Constructor[Duration] {
          def usedRegisters: RegisterOffset                              = RegisterOffset(objects = 1)
          def construct(in: Registers, offset: RegisterOffset): Duration =
            new Duration(in.getObject(offset).asInstanceOf[java.time.Duration])
        },
        deconstructor = new Deconstructor[Duration] {
          def usedRegisters: RegisterOffset                                                 = RegisterOffset(objects = 1)
          def deconstruct(out: Registers, offset: RegisterOffset, in: Duration): scala.Unit =
            out.setObject(offset, in.value)
        }
      ),
      modifiers = Vector.empty
    )
  )

  implicit lazy val instantSchema: Schema[Instant] = new Schema(
    reflect = new Reflect.Record[Binding, Instant](
      fields = Vector(Schema[java.time.Instant].reflect.asTerm("value")),
      typeName = new TypeName(new Namespace(List("zio", "blocks", "schema", "PrimitiveValue")), "Instant"),
      recordBinding = new Binding.Record(
        constructor = new Constructor[Instant] {
          def usedRegisters: RegisterOffset                             = RegisterOffset(objects = 1)
          def construct(in: Registers, offset: RegisterOffset): Instant =
            new Instant(in.getObject(offset).asInstanceOf[java.time.Instant])
        },
        deconstructor = new Deconstructor[Instant] {
          def usedRegisters: RegisterOffset                                                = RegisterOffset(objects = 1)
          def deconstruct(out: Registers, offset: RegisterOffset, in: Instant): scala.Unit =
            out.setObject(offset, in.value)
        }
      ),
      modifiers = Vector.empty
    )
  )

  implicit lazy val localDateSchema: Schema[LocalDate] = new Schema(
    reflect = new Reflect.Record[Binding, LocalDate](
      fields = Vector(Schema[java.time.LocalDate].reflect.asTerm("value")),
      typeName = new TypeName(new Namespace(List("zio", "blocks", "schema", "PrimitiveValue")), "LocalDate"),
      recordBinding = new Binding.Record(
        constructor = new Constructor[LocalDate] {
          def usedRegisters: RegisterOffset                               = RegisterOffset(objects = 1)
          def construct(in: Registers, offset: RegisterOffset): LocalDate =
            new LocalDate(in.getObject(offset).asInstanceOf[java.time.LocalDate])
        },
        deconstructor = new Deconstructor[LocalDate] {
          def usedRegisters: RegisterOffset                                                  = RegisterOffset(objects = 1)
          def deconstruct(out: Registers, offset: RegisterOffset, in: LocalDate): scala.Unit =
            out.setObject(offset, in.value)
        }
      ),
      modifiers = Vector.empty
    )
  )

  implicit lazy val localDateTimeSchema: Schema[LocalDateTime] = new Schema(
    reflect = new Reflect.Record[Binding, LocalDateTime](
      fields = Vector(Schema[java.time.LocalDateTime].reflect.asTerm("value")),
      typeName = new TypeName(new Namespace(List("zio", "blocks", "schema", "PrimitiveValue")), "LocalDateTime"),
      recordBinding = new Binding.Record(
        constructor = new Constructor[LocalDateTime] {
          def usedRegisters: RegisterOffset                                   = RegisterOffset(objects = 1)
          def construct(in: Registers, offset: RegisterOffset): LocalDateTime =
            new LocalDateTime(in.getObject(offset).asInstanceOf[java.time.LocalDateTime])
        },
        deconstructor = new Deconstructor[LocalDateTime] {
          def usedRegisters: RegisterOffset                                                      = RegisterOffset(objects = 1)
          def deconstruct(out: Registers, offset: RegisterOffset, in: LocalDateTime): scala.Unit =
            out.setObject(offset, in.value)
        }
      ),
      modifiers = Vector.empty
    )
  )

  implicit lazy val localTimeSchema: Schema[LocalTime] = new Schema(
    reflect = new Reflect.Record[Binding, LocalTime](
      fields = Vector(Schema[java.time.LocalTime].reflect.asTerm("value")),
      typeName = new TypeName(new Namespace(List("zio", "blocks", "schema", "PrimitiveValue")), "LocalTime"),
      recordBinding = new Binding.Record(
        constructor = new Constructor[LocalTime] {
          def usedRegisters: RegisterOffset                               = RegisterOffset(objects = 1)
          def construct(in: Registers, offset: RegisterOffset): LocalTime =
            new LocalTime(in.getObject(offset).asInstanceOf[java.time.LocalTime])
        },
        deconstructor = new Deconstructor[LocalTime] {
          def usedRegisters: RegisterOffset                                                  = RegisterOffset(objects = 1)
          def deconstruct(out: Registers, offset: RegisterOffset, in: LocalTime): scala.Unit =
            out.setObject(offset, in.value)
        }
      ),
      modifiers = Vector.empty
    )
  )

  implicit lazy val monthSchema: Schema[Month] = new Schema(
    reflect = new Reflect.Record[Binding, Month](
      fields = Vector(Schema[java.time.Month].reflect.asTerm("value")),
      typeName = new TypeName(new Namespace(List("zio", "blocks", "schema", "PrimitiveValue")), "Month"),
      recordBinding = new Binding.Record(
        constructor = new Constructor[Month] {
          def usedRegisters: RegisterOffset                           = RegisterOffset(objects = 1)
          def construct(in: Registers, offset: RegisterOffset): Month =
            new Month(in.getObject(offset).asInstanceOf[java.time.Month])
        },
        deconstructor = new Deconstructor[Month] {
          def usedRegisters: RegisterOffset                                              = RegisterOffset(objects = 1)
          def deconstruct(out: Registers, offset: RegisterOffset, in: Month): scala.Unit =
            out.setObject(offset, in.value)
        }
      ),
      modifiers = Vector.empty
    )
  )

  implicit lazy val monthDaySchema: Schema[MonthDay] = new Schema(
    reflect = new Reflect.Record[Binding, MonthDay](
      fields = Vector(Schema[java.time.MonthDay].reflect.asTerm("value")),
      typeName = new TypeName(new Namespace(List("zio", "blocks", "schema", "PrimitiveValue")), "MonthDay"),
      recordBinding = new Binding.Record(
        constructor = new Constructor[MonthDay] {
          def usedRegisters: RegisterOffset                              = RegisterOffset(objects = 1)
          def construct(in: Registers, offset: RegisterOffset): MonthDay =
            new MonthDay(in.getObject(offset).asInstanceOf[java.time.MonthDay])
        },
        deconstructor = new Deconstructor[MonthDay] {
          def usedRegisters: RegisterOffset                                                 = RegisterOffset(objects = 1)
          def deconstruct(out: Registers, offset: RegisterOffset, in: MonthDay): scala.Unit =
            out.setObject(offset, in.value)
        }
      ),
      modifiers = Vector.empty
    )
  )

  implicit lazy val offsetDateTimeSchema: Schema[OffsetDateTime] = new Schema(
    reflect = new Reflect.Record[Binding, OffsetDateTime](
      fields = Vector(Schema[java.time.OffsetDateTime].reflect.asTerm("value")),
      typeName = new TypeName(new Namespace(List("zio", "blocks", "schema", "PrimitiveValue")), "OffsetDateTime"),
      recordBinding = new Binding.Record(
        constructor = new Constructor[OffsetDateTime] {
          def usedRegisters: RegisterOffset                                    = RegisterOffset(objects = 1)
          def construct(in: Registers, offset: RegisterOffset): OffsetDateTime =
            new OffsetDateTime(in.getObject(offset).asInstanceOf[java.time.OffsetDateTime])
        },
        deconstructor = new Deconstructor[OffsetDateTime] {
          def usedRegisters: RegisterOffset                                                       = RegisterOffset(objects = 1)
          def deconstruct(out: Registers, offset: RegisterOffset, in: OffsetDateTime): scala.Unit =
            out.setObject(offset, in.value)
        }
      ),
      modifiers = Vector.empty
    )
  )

  implicit lazy val offsetTimeSchema: Schema[OffsetTime] = new Schema(
    reflect = new Reflect.Record[Binding, OffsetTime](
      fields = Vector(Schema[java.time.OffsetTime].reflect.asTerm("value")),
      typeName = new TypeName(new Namespace(List("zio", "blocks", "schema", "PrimitiveValue")), "OffsetTime"),
      recordBinding = new Binding.Record(
        constructor = new Constructor[OffsetTime] {
          def usedRegisters: RegisterOffset                                = RegisterOffset(objects = 1)
          def construct(in: Registers, offset: RegisterOffset): OffsetTime =
            new OffsetTime(in.getObject(offset).asInstanceOf[java.time.OffsetTime])
        },
        deconstructor = new Deconstructor[OffsetTime] {
          def usedRegisters: RegisterOffset                                                   = RegisterOffset(objects = 1)
          def deconstruct(out: Registers, offset: RegisterOffset, in: OffsetTime): scala.Unit =
            out.setObject(offset, in.value)
        }
      ),
      modifiers = Vector.empty
    )
  )

  implicit lazy val periodSchema: Schema[Period] = new Schema(
    reflect = new Reflect.Record[Binding, Period](
      fields = Vector(Schema[java.time.Period].reflect.asTerm("value")),
      typeName = new TypeName(new Namespace(List("zio", "blocks", "schema", "PrimitiveValue")), "Period"),
      recordBinding = new Binding.Record(
        constructor = new Constructor[Period] {
          def usedRegisters: RegisterOffset                            = RegisterOffset(objects = 1)
          def construct(in: Registers, offset: RegisterOffset): Period =
            new Period(in.getObject(offset).asInstanceOf[java.time.Period])
        },
        deconstructor = new Deconstructor[Period] {
          def usedRegisters: RegisterOffset                                               = RegisterOffset(objects = 1)
          def deconstruct(out: Registers, offset: RegisterOffset, in: Period): scala.Unit =
            out.setObject(offset, in.value)
        }
      ),
      modifiers = Vector.empty
    )
  )

  implicit lazy val yearSchema: Schema[Year] = new Schema(
    reflect = new Reflect.Record[Binding, Year](
      fields = Vector(Schema[java.time.Year].reflect.asTerm("value")),
      typeName = new TypeName(new Namespace(List("zio", "blocks", "schema", "PrimitiveValue")), "Year"),
      recordBinding = new Binding.Record(
        constructor = new Constructor[Year] {
          def usedRegisters: RegisterOffset                          = RegisterOffset(objects = 1)
          def construct(in: Registers, offset: RegisterOffset): Year =
            new Year(in.getObject(offset).asInstanceOf[java.time.Year])
        },
        deconstructor = new Deconstructor[Year] {
          def usedRegisters: RegisterOffset                                             = RegisterOffset(objects = 1)
          def deconstruct(out: Registers, offset: RegisterOffset, in: Year): scala.Unit =
            out.setObject(offset, in.value)
        }
      ),
      modifiers = Vector.empty
    )
  )

  implicit lazy val yearMonthSchema: Schema[YearMonth] = new Schema(
    reflect = new Reflect.Record[Binding, YearMonth](
      fields = Vector(Schema[java.time.YearMonth].reflect.asTerm("value")),
      typeName = new TypeName(new Namespace(List("zio", "blocks", "schema", "PrimitiveValue")), "YearMonth"),
      recordBinding = new Binding.Record(
        constructor = new Constructor[YearMonth] {
          def usedRegisters: RegisterOffset                               = RegisterOffset(objects = 1)
          def construct(in: Registers, offset: RegisterOffset): YearMonth =
            new YearMonth(in.getObject(offset).asInstanceOf[java.time.YearMonth])
        },
        deconstructor = new Deconstructor[YearMonth] {
          def usedRegisters: RegisterOffset                                                  = RegisterOffset(objects = 1)
          def deconstruct(out: Registers, offset: RegisterOffset, in: YearMonth): scala.Unit =
            out.setObject(offset, in.value)
        }
      ),
      modifiers = Vector.empty
    )
  )

  implicit lazy val zoneIdSchema: Schema[ZoneId] = new Schema(
    reflect = new Reflect.Record[Binding, ZoneId](
      fields = Vector(Schema[java.time.ZoneId].reflect.asTerm("value")),
      typeName = new TypeName(new Namespace(List("zio", "blocks", "schema", "PrimitiveValue")), "ZoneId"),
      recordBinding = new Binding.Record(
        constructor = new Constructor[ZoneId] {
          def usedRegisters: RegisterOffset                            = RegisterOffset(objects = 1)
          def construct(in: Registers, offset: RegisterOffset): ZoneId =
            new ZoneId(in.getObject(offset).asInstanceOf[java.time.ZoneId])
        },
        deconstructor = new Deconstructor[ZoneId] {
          def usedRegisters: RegisterOffset                                               = RegisterOffset(objects = 1)
          def deconstruct(out: Registers, offset: RegisterOffset, in: ZoneId): scala.Unit =
            out.setObject(offset, in.value)
        }
      ),
      modifiers = Vector.empty
    )
  )

  implicit lazy val zoneOffsetSchema: Schema[ZoneOffset] = new Schema(
    reflect = new Reflect.Record[Binding, ZoneOffset](
      fields = Vector(Schema[java.time.ZoneOffset].reflect.asTerm("value")),
      typeName = new TypeName(new Namespace(List("zio", "blocks", "schema", "PrimitiveValue")), "ZoneOffset"),
      recordBinding = new Binding.Record(
        constructor = new Constructor[ZoneOffset] {
          def usedRegisters: RegisterOffset                                = RegisterOffset(objects = 1)
          def construct(in: Registers, offset: RegisterOffset): ZoneOffset =
            new ZoneOffset(in.getObject(offset).asInstanceOf[java.time.ZoneOffset])
        },
        deconstructor = new Deconstructor[ZoneOffset] {
          def usedRegisters: RegisterOffset                                                   = RegisterOffset(objects = 1)
          def deconstruct(out: Registers, offset: RegisterOffset, in: ZoneOffset): scala.Unit =
            out.setObject(offset, in.value)
        }
      ),
      modifiers = Vector.empty
    )
  )

  implicit lazy val zonedDateTimeSchema: Schema[ZonedDateTime] = new Schema(
    reflect = new Reflect.Record[Binding, ZonedDateTime](
      fields = Vector(Schema[java.time.ZonedDateTime].reflect.asTerm("value")),
      typeName = new TypeName(new Namespace(List("zio", "blocks", "schema", "PrimitiveValue")), "ZonedDateTime"),
      recordBinding = new Binding.Record(
        constructor = new Constructor[ZonedDateTime] {
          def usedRegisters: RegisterOffset                                   = RegisterOffset(objects = 1)
          def construct(in: Registers, offset: RegisterOffset): ZonedDateTime =
            new ZonedDateTime(in.getObject(offset).asInstanceOf[java.time.ZonedDateTime])
        },
        deconstructor = new Deconstructor[ZonedDateTime] {
          def usedRegisters: RegisterOffset                                                      = RegisterOffset(objects = 1)
          def deconstruct(out: Registers, offset: RegisterOffset, in: ZonedDateTime): scala.Unit =
            out.setObject(offset, in.value)
        }
      ),
      modifiers = Vector.empty
    )
  )

  implicit lazy val currencySchema: Schema[Currency] = new Schema(
    reflect = new Reflect.Record[Binding, Currency](
      fields = Vector(Schema[java.util.Currency].reflect.asTerm("value")),
      typeName = new TypeName(new Namespace(List("zio", "blocks", "schema", "PrimitiveValue")), "Currency"),
      recordBinding = new Binding.Record(
        constructor = new Constructor[Currency] {
          def usedRegisters: RegisterOffset                              = RegisterOffset(objects = 1)
          def construct(in: Registers, offset: RegisterOffset): Currency =
            new Currency(in.getObject(offset).asInstanceOf[java.util.Currency])
        },
        deconstructor = new Deconstructor[Currency] {
          def usedRegisters: RegisterOffset                                                 = RegisterOffset(objects = 1)
          def deconstruct(out: Registers, offset: RegisterOffset, in: Currency): scala.Unit =
            out.setObject(offset, in.value)
        }
      ),
      modifiers = Vector.empty
    )
  )

  implicit lazy val uuidSchema: Schema[UUID] = new Schema(
    reflect = new Reflect.Record[Binding, UUID](
      fields = Vector(Schema[java.util.UUID].reflect.asTerm("value")),
      typeName = new TypeName(new Namespace(List("zio", "blocks", "schema", "PrimitiveValue")), "UUID"),
      recordBinding = new Binding.Record(
        constructor = new Constructor[UUID] {
          def usedRegisters: RegisterOffset                          = RegisterOffset(objects = 1)
          def construct(in: Registers, offset: RegisterOffset): UUID =
            new UUID(in.getObject(offset).asInstanceOf[java.util.UUID])
        },
        deconstructor = new Deconstructor[UUID] {
          def usedRegisters: RegisterOffset                                             = RegisterOffset(objects = 1)
          def deconstruct(out: Registers, offset: RegisterOffset, in: UUID): scala.Unit =
            out.setObject(offset, in.value)
        }
      ),
      modifiers = Vector.empty
    )
  )

  implicit lazy val schema: Schema[PrimitiveValue] = new Schema(
    reflect = new Reflect.Variant[Binding, PrimitiveValue](
      cases = Vector(
        unitSchema.reflect.asTerm("Unit"),
        booleanSchema.reflect.asTerm("Boolean"),
        byteSchema.reflect.asTerm("Byte"),
        shortSchema.reflect.asTerm("Short"),
        intSchema.reflect.asTerm("Int"),
        longSchema.reflect.asTerm("Long"),
        floatSchema.reflect.asTerm("Float"),
        doubleSchema.reflect.asTerm("Double"),
        charSchema.reflect.asTerm("Char"),
        stringSchema.reflect.asTerm("String"),
        bigIntSchema.reflect.asTerm("BigInt"),
        bigDecimalSchema.reflect.asTerm("BigDecimal"),
        dayOfWeekSchema.reflect.asTerm("DayOfWeek"),
        durationSchema.reflect.asTerm("Duration"),
        instantSchema.reflect.asTerm("Instant"),
        localDateSchema.reflect.asTerm("LocalDate"),
        localDateTimeSchema.reflect.asTerm("LocalDateTime"),
        localTimeSchema.reflect.asTerm("LocalTime"),
        monthSchema.reflect.asTerm("Month"),
        monthDaySchema.reflect.asTerm("MonthDay"),
        offsetDateTimeSchema.reflect.asTerm("OffsetDateTime"),
        offsetTimeSchema.reflect.asTerm("OffsetTime"),
        periodSchema.reflect.asTerm("Period"),
        yearSchema.reflect.asTerm("Year"),
        yearMonthSchema.reflect.asTerm("YearMonth"),
        zoneIdSchema.reflect.asTerm("ZoneId"),
        zoneOffsetSchema.reflect.asTerm("ZoneOffset"),
        zonedDateTimeSchema.reflect.asTerm("ZonedDateTime"),
        currencySchema.reflect.asTerm("Currency"),
        uuidSchema.reflect.asTerm("UUID")
      ),
      typeName = new TypeName(new Namespace(List("zio", "blocks", "schema")), "PrimitiveValue"),
      variantBinding = new Binding.Variant(
        discriminator = new Discriminator[PrimitiveValue] {
          def discriminate(a: PrimitiveValue): scala.Int = a.typeIndex
        },
        matchers = Matchers(
          new Matcher[Unit.type] {
            def downcastOrNull(a: Any): Unit.type = a match {
              case x: Unit.type => x; case _ => null.asInstanceOf[Unit.type]
            }
          },
          new Matcher[Boolean] {
            def downcastOrNull(a: Any): Boolean = a match { case x: Boolean => x; case _ => null.asInstanceOf[Boolean] }
          },
          new Matcher[Byte] {
            def downcastOrNull(a: Any): Byte = a match { case x: Byte => x; case _ => null.asInstanceOf[Byte] }
          },
          new Matcher[Short] {
            def downcastOrNull(a: Any): Short = a match { case x: Short => x; case _ => null.asInstanceOf[Short] }
          },
          new Matcher[Int] {
            def downcastOrNull(a: Any): Int = a match { case x: Int => x; case _ => null.asInstanceOf[Int] }
          },
          new Matcher[Long] {
            def downcastOrNull(a: Any): Long = a match { case x: Long => x; case _ => null.asInstanceOf[Long] }
          },
          new Matcher[Float] {
            def downcastOrNull(a: Any): Float = a match { case x: Float => x; case _ => null.asInstanceOf[Float] }
          },
          new Matcher[Double] {
            def downcastOrNull(a: Any): Double = a match { case x: Double => x; case _ => null.asInstanceOf[Double] }
          },
          new Matcher[Char] {
            def downcastOrNull(a: Any): Char = a match { case x: Char => x; case _ => null.asInstanceOf[Char] }
          },
          new Matcher[String] {
            def downcastOrNull(a: Any): String = a match { case x: String => x; case _ => null.asInstanceOf[String] }
          },
          new Matcher[BigInt] {
            def downcastOrNull(a: Any): BigInt = a match { case x: BigInt => x; case _ => null.asInstanceOf[BigInt] }
          },
          new Matcher[BigDecimal] {
            def downcastOrNull(a: Any): BigDecimal = a match {
              case x: BigDecimal => x; case _ => null.asInstanceOf[BigDecimal]
            }
          },
          new Matcher[DayOfWeek] {
            def downcastOrNull(a: Any): DayOfWeek = a match {
              case x: DayOfWeek => x; case _ => null.asInstanceOf[DayOfWeek]
            }
          },
          new Matcher[Duration] {
            def downcastOrNull(a: Any): Duration = a match {
              case x: Duration => x; case _ => null.asInstanceOf[Duration]
            }
          },
          new Matcher[Instant] {
            def downcastOrNull(a: Any): Instant = a match { case x: Instant => x; case _ => null.asInstanceOf[Instant] }
          },
          new Matcher[LocalDate] {
            def downcastOrNull(a: Any): LocalDate = a match {
              case x: LocalDate => x; case _ => null.asInstanceOf[LocalDate]
            }
          },
          new Matcher[LocalDateTime] {
            def downcastOrNull(a: Any): LocalDateTime = a match {
              case x: LocalDateTime => x; case _ => null.asInstanceOf[LocalDateTime]
            }
          },
          new Matcher[LocalTime] {
            def downcastOrNull(a: Any): LocalTime = a match {
              case x: LocalTime => x; case _ => null.asInstanceOf[LocalTime]
            }
          },
          new Matcher[Month] {
            def downcastOrNull(a: Any): Month = a match { case x: Month => x; case _ => null.asInstanceOf[Month] }
          },
          new Matcher[MonthDay] {
            def downcastOrNull(a: Any): MonthDay = a match {
              case x: MonthDay => x; case _ => null.asInstanceOf[MonthDay]
            }
          },
          new Matcher[OffsetDateTime] {
            def downcastOrNull(a: Any): OffsetDateTime = a match {
              case x: OffsetDateTime => x; case _ => null.asInstanceOf[OffsetDateTime]
            }
          },
          new Matcher[OffsetTime] {
            def downcastOrNull(a: Any): OffsetTime = a match {
              case x: OffsetTime => x; case _ => null.asInstanceOf[OffsetTime]
            }
          },
          new Matcher[Period] {
            def downcastOrNull(a: Any): Period = a match { case x: Period => x; case _ => null.asInstanceOf[Period] }
          },
          new Matcher[Year] {
            def downcastOrNull(a: Any): Year = a match { case x: Year => x; case _ => null.asInstanceOf[Year] }
          },
          new Matcher[YearMonth] {
            def downcastOrNull(a: Any): YearMonth = a match {
              case x: YearMonth => x; case _ => null.asInstanceOf[YearMonth]
            }
          },
          new Matcher[ZoneId] {
            def downcastOrNull(a: Any): ZoneId = a match { case x: ZoneId => x; case _ => null.asInstanceOf[ZoneId] }
          },
          new Matcher[ZoneOffset] {
            def downcastOrNull(a: Any): ZoneOffset = a match {
              case x: ZoneOffset => x; case _ => null.asInstanceOf[ZoneOffset]
            }
          },
          new Matcher[ZonedDateTime] {
            def downcastOrNull(a: Any): ZonedDateTime = a match {
              case x: ZonedDateTime => x; case _ => null.asInstanceOf[ZonedDateTime]
            }
          },
          new Matcher[Currency] {
            def downcastOrNull(a: Any): Currency = a match {
              case x: Currency => x; case _ => null.asInstanceOf[Currency]
            }
          },
          new Matcher[UUID] {
            def downcastOrNull(a: Any): UUID = a match { case x: UUID => x; case _ => null.asInstanceOf[UUID] }
          }
        )
      ),
      modifiers = Vector.empty
    )
  )
}
