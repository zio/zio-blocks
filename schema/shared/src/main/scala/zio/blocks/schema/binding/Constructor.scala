package zio.blocks.schema.binding

import zio.blocks.schema.binding.RegisterOffset.RegisterOffset

/**
 * A {{Constructor}} is a typeclass that can construct a value of type `A` from
 * a set of registers.
 */
abstract class Constructor[+A] { self =>

  /**
   * The size of the registers required to construct a value of type `A`.
   */
  def usedRegisters: RegisterOffset

  /**
   * Constructs a value of type `A` from the registers.
   */
  def construct(in: Registers, baseOffset: RegisterOffset): A

  /**
   * Maps a function over the constructed value.
   */
  final def map[B](f: A => B): Constructor[B] =
    new Constructor[B] {
      def usedRegisters: RegisterOffset = self.usedRegisters

      def construct(in: Registers, baseOffset: RegisterOffset): B = f(self.construct(in, baseOffset))
    }

  /**
   * Combines two constructors into a single constructor that constructs a tuple
   * of the two values.
   */
  final def zip[B](that: Constructor[B]): Constructor[(A, B)] =
    new Constructor[(A, B)] {
      val usedRegisters: RegisterOffset = self.usedRegisters + that.usedRegisters

      def construct(in: Registers, baseOffset: RegisterOffset): (A, B) = {
        var offset = baseOffset
        (
          self.construct(in, offset),
          that.construct(in, { offset += this.usedRegisters; offset })
        )
      }
    }

}

object Constructor {
  private[this] val _of = new Constructor[AnyRef] {
    def usedRegisters: RegisterOffset = RegisterOffset(objects = 1)

    def construct(in: Registers, baseOffset: RegisterOffset): AnyRef = in.getObject(baseOffset, 0)
  }

  def of[A]: Constructor[A] = _of.asInstanceOf[Constructor[A]]

  val unit: Constructor[Unit] =
    new Constructor[Unit] {
      def usedRegisters: RegisterOffset = RegisterOffset.Zero

      def construct(in: Registers, baseOffset: RegisterOffset): Unit = ()
    }

  val boolean: Constructor[Boolean] =
    new Constructor[Boolean] {
      def usedRegisters: RegisterOffset = RegisterOffset(booleans = 1)

      def construct(in: Registers, baseOffset: RegisterOffset): Boolean = in.getBoolean(baseOffset, 0)
    }

  val byte: Constructor[Byte] =
    new Constructor[Byte] {
      def usedRegisters: RegisterOffset = RegisterOffset(bytes = 1)

      def construct(in: Registers, baseOffset: RegisterOffset): Byte = in.getByte(baseOffset, 0)
    }

  val short: Constructor[Short] =
    new Constructor[Short] {
      def usedRegisters: RegisterOffset = RegisterOffset(shorts = 1)

      def construct(in: Registers, baseOffset: RegisterOffset): Short = in.getShort(baseOffset, 0)
    }

  val int: Constructor[Int] =
    new Constructor[Int] {
      def usedRegisters: RegisterOffset = RegisterOffset(ints = 1)

      def construct(in: Registers, baseOffset: RegisterOffset): Int = in.getInt(baseOffset, 0)
    }

  val long: Constructor[Long] =
    new Constructor[Long] {
      def usedRegisters: RegisterOffset = RegisterOffset(longs = 1)

      def construct(in: Registers, baseOffset: RegisterOffset): Long = in.getLong(baseOffset, 0)
    }

  val float: Constructor[Float] =
    new Constructor[Float] {
      def usedRegisters: RegisterOffset = RegisterOffset(floats = 1)

      def construct(in: Registers, baseOffset: RegisterOffset): Float = in.getFloat(baseOffset, 0)
    }

  val double: Constructor[Double] =
    new Constructor[Double] {
      def usedRegisters: RegisterOffset = RegisterOffset(doubles = 1)

      def construct(in: Registers, baseOffset: RegisterOffset): Double = in.getDouble(baseOffset, 0)
    }

  val char: Constructor[Char] =
    new Constructor[Char] {
      def usedRegisters: RegisterOffset = RegisterOffset(chars = 1)

      def construct(in: Registers, baseOffset: RegisterOffset): Char = in.getChar(baseOffset, 0)
    }

  val string: Constructor[String] =
    new Constructor[String] {
      def usedRegisters: RegisterOffset = RegisterOffset(objects = 1)

      def construct(in: Registers, baseOffset: RegisterOffset): String =
        in.getObject(baseOffset, 0).asInstanceOf[String]
    }

  val bigInt: Constructor[BigInt] =
    new Constructor[BigInt] {
      def usedRegisters: RegisterOffset = RegisterOffset(objects = 1)

      def construct(in: Registers, baseOffset: RegisterOffset): BigInt =
        in.getObject(baseOffset, 0).asInstanceOf[BigInt]
    }

  val bigDecimal: Constructor[BigDecimal] =
    new Constructor[BigDecimal] {
      def usedRegisters: RegisterOffset = RegisterOffset(objects = 1)

      def construct(in: Registers, baseOffset: RegisterOffset): BigDecimal =
        in.getObject(baseOffset, 0).asInstanceOf[BigDecimal]
    }

  val dayOfWeek: Constructor[java.time.DayOfWeek] =
    new Constructor[java.time.DayOfWeek] {
      def usedRegisters: RegisterOffset = RegisterOffset(objects = 1)

      def construct(in: Registers, baseOffset: RegisterOffset): java.time.DayOfWeek =
        in.getObject(baseOffset, 0).asInstanceOf[java.time.DayOfWeek]
    }

  val duration: Constructor[java.time.Duration] =
    new Constructor[java.time.Duration] {
      def usedRegisters: RegisterOffset = RegisterOffset(objects = 1)

      def construct(in: Registers, baseOffset: RegisterOffset): java.time.Duration =
        in.getObject(baseOffset, 0).asInstanceOf[java.time.Duration]
    }

  val instant: Constructor[java.time.Instant] =
    new Constructor[java.time.Instant] {
      def usedRegisters: RegisterOffset = RegisterOffset(objects = 1)

      def construct(in: Registers, baseOffset: RegisterOffset): java.time.Instant =
        in.getObject(baseOffset, 0).asInstanceOf[java.time.Instant]
    }

  val localDate: Constructor[java.time.LocalDate] =
    new Constructor[java.time.LocalDate] {
      def usedRegisters: RegisterOffset = RegisterOffset(objects = 1)

      def construct(in: Registers, baseOffset: RegisterOffset): java.time.LocalDate =
        in.getObject(baseOffset, 0).asInstanceOf[java.time.LocalDate]
    }

  val localDateTime: Constructor[java.time.LocalDateTime] =
    new Constructor[java.time.LocalDateTime] {
      def usedRegisters: RegisterOffset = RegisterOffset(objects = 1)

      def construct(in: Registers, baseOffset: RegisterOffset): java.time.LocalDateTime =
        in.getObject(baseOffset, 0).asInstanceOf[java.time.LocalDateTime]
    }

  val localTime: Constructor[java.time.LocalTime] =
    new Constructor[java.time.LocalTime] {
      def usedRegisters: RegisterOffset = RegisterOffset(objects = 1)

      def construct(in: Registers, baseOffset: RegisterOffset): java.time.LocalTime =
        in.getObject(baseOffset, 0).asInstanceOf[java.time.LocalTime]
    }

  val month: Constructor[java.time.Month] =
    new Constructor[java.time.Month] {
      def usedRegisters: RegisterOffset = RegisterOffset(objects = 1)

      def construct(in: Registers, baseOffset: RegisterOffset): java.time.Month =
        in.getObject(baseOffset, 0).asInstanceOf[java.time.Month]
    }

  val monthDay: Constructor[java.time.MonthDay] =
    new Constructor[java.time.MonthDay] {
      def usedRegisters: RegisterOffset = RegisterOffset(objects = 1)

      def construct(in: Registers, baseOffset: RegisterOffset): java.time.MonthDay =
        in.getObject(baseOffset, 0).asInstanceOf[java.time.MonthDay]
    }

  val offsetDateTime: Constructor[java.time.OffsetDateTime] =
    new Constructor[java.time.OffsetDateTime] {
      def usedRegisters: RegisterOffset = RegisterOffset(objects = 1)

      def construct(in: Registers, baseOffset: RegisterOffset): java.time.OffsetDateTime =
        in.getObject(baseOffset, 0).asInstanceOf[java.time.OffsetDateTime]
    }

  val offsetTime: Constructor[java.time.OffsetTime] =
    new Constructor[java.time.OffsetTime] {
      def usedRegisters: RegisterOffset = RegisterOffset(objects = 1)

      def construct(in: Registers, baseOffset: RegisterOffset): java.time.OffsetTime =
        in.getObject(baseOffset, 0).asInstanceOf[java.time.OffsetTime]
    }

  val period: Constructor[java.time.Period] =
    new Constructor[java.time.Period] {
      def usedRegisters: RegisterOffset = RegisterOffset(objects = 1)

      def construct(in: Registers, baseOffset: RegisterOffset): java.time.Period =
        in.getObject(baseOffset, 0).asInstanceOf[java.time.Period]
    }

  val year: Constructor[java.time.Year] =
    new Constructor[java.time.Year] {
      def usedRegisters: RegisterOffset = RegisterOffset(objects = 1)

      def construct(in: Registers, baseOffset: RegisterOffset): java.time.Year =
        in.getObject(baseOffset, 0).asInstanceOf[java.time.Year]
    }

  val yearMonth: Constructor[java.time.YearMonth] =
    new Constructor[java.time.YearMonth] {
      def usedRegisters: RegisterOffset = RegisterOffset(objects = 1)

      def construct(in: Registers, baseOffset: RegisterOffset): java.time.YearMonth =
        in.getObject(baseOffset, 0).asInstanceOf[java.time.YearMonth]
    }

  val zoneId: Constructor[java.time.ZoneId] =
    new Constructor[java.time.ZoneId] {
      def usedRegisters: RegisterOffset = RegisterOffset(objects = 1)

      def construct(in: Registers, baseOffset: RegisterOffset): java.time.ZoneId =
        in.getObject(baseOffset, 0).asInstanceOf[java.time.ZoneId]
    }

  val zoneOffset: Constructor[java.time.ZoneOffset] =
    new Constructor[java.time.ZoneOffset] {
      def usedRegisters: RegisterOffset = RegisterOffset(objects = 1)

      def construct(in: Registers, baseOffset: RegisterOffset): java.time.ZoneOffset =
        in.getObject(baseOffset, 0).asInstanceOf[java.time.ZoneOffset]
    }

  val zonedDateTime: Constructor[java.time.ZonedDateTime] =
    new Constructor[java.time.ZonedDateTime] {
      def usedRegisters: RegisterOffset = RegisterOffset(objects = 1)

      def construct(in: Registers, baseOffset: RegisterOffset): java.time.ZonedDateTime =
        in.getObject(baseOffset, 0).asInstanceOf[java.time.ZonedDateTime]
    }

  val currency: Constructor[java.util.Currency] =
    new Constructor[java.util.Currency] {
      def usedRegisters: RegisterOffset = RegisterOffset(objects = 1)

      def construct(in: Registers, baseOffset: RegisterOffset): java.util.Currency =
        in.getObject(baseOffset, 0).asInstanceOf[java.util.Currency]
    }

  val uuid: Constructor[java.util.UUID] =
    new Constructor[java.util.UUID] {
      def usedRegisters: RegisterOffset = RegisterOffset(objects = 1)

      def construct(in: Registers, baseOffset: RegisterOffset): java.util.UUID =
        in.getObject(baseOffset, 0).asInstanceOf[java.util.UUID]
    }
}
