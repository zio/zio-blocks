package zio.blocks.schema.binding

import zio.blocks.schema.binding.RegisterOffset.RegisterOffset

/**
 * A {{Deconstructor}} is a typeclass that can deconstruct a value of type `A`
 * into a set of registers.
 */
abstract class Deconstructor[-A] { self =>
  def usedRegisters: RegisterOffset

  /**
   * Deconstructs a value of type `A` into the registers.
   */
  def deconstruct(out: Registers, baseOffset: RegisterOffset, in: A): Unit

  /**
   * Transforms this deconstructor into one that operates on a different type --
   * albeit one that can be converted to this type.
   */
  final def contramap[B](f: B => A): Deconstructor[B] =
    new Deconstructor[B] {
      def usedRegisters: RegisterOffset = self.usedRegisters

      def deconstruct(out: Registers, baseOffset: RegisterOffset, in: B): Unit =
        self.deconstruct(out, baseOffset, f(in))
    }

  /**
   * Combines two deconstructors into a single deconstructor that deconstructs a
   * tuple of the two values.
   */
  final def zip[B](that: Deconstructor[B]): Deconstructor[(A, B)] = new Deconstructor[(A, B)] {
    val usedRegisters: RegisterOffset = self.usedRegisters + that.usedRegisters

    def deconstruct(out: Registers, baseOffset: RegisterOffset, in: (A, B)): Unit = {
      self.deconstruct(out, baseOffset, in._1)
      that.deconstruct(out, baseOffset + self.usedRegisters, in._2)
    }
  }
}

object Deconstructor {
  private[this] val _of = new Deconstructor[AnyRef] {
    def usedRegisters: RegisterOffset = RegisterOffset(objects = 1)

    def deconstruct(out: Registers, baseOffset: RegisterOffset, in: AnyRef): Unit = out.setObject(baseOffset, 0, in)
  }

  def of[A]: Deconstructor[A] = _of.asInstanceOf[Deconstructor[A]]

  val unit: Deconstructor[Unit] =
    new Deconstructor[Unit] {
      def usedRegisters: RegisterOffset = RegisterOffset.Zero

      def deconstruct(out: Registers, baseOffset: RegisterOffset, in: Unit): Unit = ()
    }

  val boolean: Deconstructor[Boolean] =
    new Deconstructor[Boolean] {
      def usedRegisters: RegisterOffset = RegisterOffset(booleans = 1)

      def deconstruct(out: Registers, baseOffset: RegisterOffset, in: Boolean): Unit = out.setBoolean(baseOffset, 0, in)
    }

  val byte: Deconstructor[Byte] =
    new Deconstructor[Byte] {
      def usedRegisters: RegisterOffset = RegisterOffset(bytes = 1)

      def deconstruct(out: Registers, baseOffset: RegisterOffset, in: Byte): Unit = out.setByte(baseOffset, 0, in)
    }

  val short: Deconstructor[Short] =
    new Deconstructor[Short] {
      def usedRegisters: RegisterOffset = RegisterOffset(shorts = 1)

      def deconstruct(out: Registers, baseOffset: RegisterOffset, in: Short): Unit = out.setShort(baseOffset, 0, in)
    }

  val int: Deconstructor[Int] =
    new Deconstructor[Int] {
      def usedRegisters: RegisterOffset = RegisterOffset(ints = 1)

      def deconstruct(out: Registers, baseOffset: RegisterOffset, in: Int): Unit = out.setInt(baseOffset, 0, in)
    }

  val long: Deconstructor[Long] =
    new Deconstructor[Long] {
      def usedRegisters: RegisterOffset = RegisterOffset(longs = 1)

      def deconstruct(out: Registers, baseOffset: RegisterOffset, in: Long): Unit = out.setLong(baseOffset, 0, in)
    }

  val float: Deconstructor[Float] =
    new Deconstructor[Float] {
      def usedRegisters: RegisterOffset = RegisterOffset(floats = 1)

      def deconstruct(out: Registers, baseOffset: RegisterOffset, in: Float): Unit = out.setFloat(baseOffset, 0, in)
    }

  val double: Deconstructor[Double] =
    new Deconstructor[Double] {
      def usedRegisters: RegisterOffset = RegisterOffset(doubles = 1)

      def deconstruct(out: Registers, baseOffset: RegisterOffset, in: Double): Unit = out.setDouble(baseOffset, 0, in)
    }

  val char: Deconstructor[Char] =
    new Deconstructor[Char] {
      def usedRegisters: RegisterOffset = RegisterOffset(chars = 1)

      def deconstruct(out: Registers, baseOffset: RegisterOffset, in: Char): Unit = out.setChar(baseOffset, 0, in)
    }

  val string: Deconstructor[String] =
    new Deconstructor[String] {
      def usedRegisters: RegisterOffset = RegisterOffset(objects = 1)

      def deconstruct(out: Registers, baseOffset: RegisterOffset, in: String): Unit = out.setObject(baseOffset, 0, in)
    }

  val bigInt: Deconstructor[BigInt] =
    new Deconstructor[BigInt] {
      def usedRegisters: RegisterOffset = RegisterOffset(objects = 1)

      def deconstruct(out: Registers, baseOffset: RegisterOffset, in: BigInt): Unit = out.setObject(baseOffset, 0, in)
    }

  val bigDecimal: Deconstructor[BigDecimal] =
    new Deconstructor[BigDecimal] {
      def usedRegisters: RegisterOffset = RegisterOffset(objects = 1)

      def deconstruct(out: Registers, baseOffset: RegisterOffset, in: BigDecimal): Unit =
        out.setObject(baseOffset, 0, in)
    }

  val dayOfWeek: Deconstructor[java.time.DayOfWeek] =
    new Deconstructor[java.time.DayOfWeek] {
      def usedRegisters: RegisterOffset = RegisterOffset(objects = 1)

      def deconstruct(out: Registers, baseOffset: RegisterOffset, in: java.time.DayOfWeek): Unit =
        out.setObject(baseOffset, 0, in)
    }

  val duration: Deconstructor[java.time.Duration] =
    new Deconstructor[java.time.Duration] {
      def usedRegisters: RegisterOffset = RegisterOffset(objects = 1)

      def deconstruct(out: Registers, baseOffset: RegisterOffset, in: java.time.Duration): Unit =
        out.setObject(baseOffset, 0, in)
    }

  val instant: Deconstructor[java.time.Instant] =
    new Deconstructor[java.time.Instant] {
      def usedRegisters: RegisterOffset = RegisterOffset(objects = 1)

      def deconstruct(out: Registers, baseOffset: RegisterOffset, in: java.time.Instant): Unit =
        out.setObject(baseOffset, 0, in)
    }

  val localDate: Deconstructor[java.time.LocalDate] =
    new Deconstructor[java.time.LocalDate] {
      def usedRegisters: RegisterOffset = RegisterOffset(objects = 1)

      def deconstruct(out: Registers, baseOffset: RegisterOffset, in: java.time.LocalDate): Unit =
        out.setObject(baseOffset, 0, in)
    }

  val localDateTime: Deconstructor[java.time.LocalDateTime] =
    new Deconstructor[java.time.LocalDateTime] {
      def usedRegisters: RegisterOffset = RegisterOffset(objects = 1)

      def deconstruct(out: Registers, baseOffset: RegisterOffset, in: java.time.LocalDateTime): Unit =
        out.setObject(baseOffset, 0, in)
    }

  val localTime: Deconstructor[java.time.LocalTime] =
    new Deconstructor[java.time.LocalTime] {
      def usedRegisters: RegisterOffset = RegisterOffset(objects = 1)

      def deconstruct(out: Registers, baseOffset: RegisterOffset, in: java.time.LocalTime): Unit =
        out.setObject(baseOffset, 0, in)
    }

  val month: Deconstructor[java.time.Month] =
    new Deconstructor[java.time.Month] {
      def usedRegisters: RegisterOffset = RegisterOffset(objects = 1)

      def deconstruct(out: Registers, baseOffset: RegisterOffset, in: java.time.Month): Unit =
        out.setObject(baseOffset, 0, in)
    }

  val monthDay: Deconstructor[java.time.MonthDay] =
    new Deconstructor[java.time.MonthDay] {
      def usedRegisters: RegisterOffset = RegisterOffset(objects = 1)

      def deconstruct(out: Registers, baseOffset: RegisterOffset, in: java.time.MonthDay): Unit =
        out.setObject(baseOffset, 0, in)
    }

  val offsetDateTime: Deconstructor[java.time.OffsetDateTime] =
    new Deconstructor[java.time.OffsetDateTime] {
      def usedRegisters: RegisterOffset = RegisterOffset(objects = 1)

      def deconstruct(out: Registers, baseOffset: RegisterOffset, in: java.time.OffsetDateTime): Unit =
        out.setObject(baseOffset, 0, in)
    }

  val offsetTime: Deconstructor[java.time.OffsetTime] =
    new Deconstructor[java.time.OffsetTime] {
      def usedRegisters: RegisterOffset = RegisterOffset(objects = 1)

      def deconstruct(out: Registers, baseOffset: RegisterOffset, in: java.time.OffsetTime): Unit =
        out.setObject(baseOffset, 0, in)
    }

  val period: Deconstructor[java.time.Period] =
    new Deconstructor[java.time.Period] {
      def usedRegisters: RegisterOffset = RegisterOffset(objects = 1)

      def deconstruct(out: Registers, baseOffset: RegisterOffset, in: java.time.Period): Unit =
        out.setObject(baseOffset, 0, in)
    }

  val year: Deconstructor[java.time.Year] =
    new Deconstructor[java.time.Year] {
      def usedRegisters: RegisterOffset = RegisterOffset(objects = 1)

      def deconstruct(out: Registers, baseOffset: RegisterOffset, in: java.time.Year): Unit =
        out.setObject(baseOffset, 0, in)
    }

  val yearMonth: Deconstructor[java.time.YearMonth] =
    new Deconstructor[java.time.YearMonth] {
      def usedRegisters: RegisterOffset = RegisterOffset(objects = 1)

      def deconstruct(out: Registers, baseOffset: RegisterOffset, in: java.time.YearMonth): Unit =
        out.setObject(baseOffset, 0, in)
    }

  val zoneId: Deconstructor[java.time.ZoneId] =
    new Deconstructor[java.time.ZoneId] {
      def usedRegisters: RegisterOffset = RegisterOffset(objects = 1)

      def deconstruct(out: Registers, baseOffset: RegisterOffset, in: java.time.ZoneId): Unit =
        out.setObject(baseOffset, 0, in)
    }

  val zoneOffset: Deconstructor[java.time.ZoneOffset] =
    new Deconstructor[java.time.ZoneOffset] {
      def usedRegisters: RegisterOffset = RegisterOffset(objects = 1)

      def deconstruct(out: Registers, baseOffset: RegisterOffset, in: java.time.ZoneOffset): Unit =
        out.setObject(baseOffset, 0, in)
    }

  val zonedDateTime: Deconstructor[java.time.ZonedDateTime] =
    new Deconstructor[java.time.ZonedDateTime] {
      def usedRegisters: RegisterOffset = RegisterOffset(objects = 1)

      def deconstruct(out: Registers, baseOffset: RegisterOffset, in: java.time.ZonedDateTime): Unit =
        out.setObject(baseOffset, 0, in)
    }

  val currency: Deconstructor[java.util.Currency] =
    new Deconstructor[java.util.Currency] {
      def usedRegisters: RegisterOffset = RegisterOffset(objects = 1)

      def deconstruct(out: Registers, baseOffset: RegisterOffset, in: java.util.Currency): Unit =
        out.setObject(baseOffset, 0, in)
    }

  val uuid: Deconstructor[java.util.UUID] =
    new Deconstructor[java.util.UUID] {
      def usedRegisters: RegisterOffset = RegisterOffset(objects = 1)

      def deconstruct(out: Registers, baseOffset: RegisterOffset, in: java.util.UUID): Unit =
        out.setObject(baseOffset, 0, in)
    }
}
