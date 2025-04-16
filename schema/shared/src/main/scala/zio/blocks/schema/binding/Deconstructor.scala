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
  final def zip[B](that: Deconstructor[B]): Deconstructor[(A, B)] = Deconstructor.tuple2(self, that)
}
object Deconstructor {
  private val _of = new Deconstructor[AnyRef] {
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

  val none: Deconstructor[None.type] =
    new Deconstructor[None.type] {
      def usedRegisters: RegisterOffset = RegisterOffset.Zero

      def deconstruct(out: Registers, baseOffset: RegisterOffset, in: None.type): Unit = ()
    }

  def some[A](d: Deconstructor[A]): Deconstructor[Some[A]] = d.contramap(_.get)

  def left[A, B](d: Deconstructor[A]): Deconstructor[Left[A, B]] = d.contramap(_.value)

  def right[A, B](d: Deconstructor[B]): Deconstructor[Right[A, B]] = d.contramap(_.value)

  def tuple2[A, B](_1: Deconstructor[A], _2: Deconstructor[B]): Deconstructor[(A, B)] =
    new Deconstructor[(A, B)] {
      val usedRegisters: RegisterOffset = _1.usedRegisters + _2.usedRegisters

      def deconstruct(out: Registers, baseOffset: RegisterOffset, in: (A, B)): Unit = {
        _1.deconstruct(out, baseOffset, in._1)
        _2.deconstruct(out, baseOffset + _1.usedRegisters, in._2)
      }
    }

  def tuple3[A, B, C](_1: Deconstructor[A], _2: Deconstructor[B], _3: Deconstructor[C]): Deconstructor[(A, B, C)] =
    new Deconstructor[(A, B, C)] {
      val usedRegisters: RegisterOffset = _1.usedRegisters + _2.usedRegisters + _3.usedRegisters

      def deconstruct(out: Registers, baseOffset: RegisterOffset, in: (A, B, C)): Unit = {
        var offset = baseOffset
        _1.deconstruct(out, offset, in._1)
        _2.deconstruct(out, { offset += _1.usedRegisters; offset }, in._2)
        _3.deconstruct(out, { offset += _2.usedRegisters; offset }, in._3)
      }
    }

  def tuple4[A, B, C, D](
    _1: Deconstructor[A],
    _2: Deconstructor[B],
    _3: Deconstructor[C],
    _4: Deconstructor[D]
  ): Deconstructor[(A, B, C, D)] =
    new Deconstructor[(A, B, C, D)] {
      val usedRegisters: RegisterOffset = _1.usedRegisters + _2.usedRegisters + _3.usedRegisters + _4.usedRegisters

      def deconstruct(out: Registers, baseOffset: RegisterOffset, in: (A, B, C, D)): Unit = {
        var offset = baseOffset
        _1.deconstruct(out, offset, in._1)
        _2.deconstruct(out, { offset += _1.usedRegisters; offset }, in._2)
        _3.deconstruct(out, { offset += _2.usedRegisters; offset }, in._3)
        _4.deconstruct(out, { offset += _3.usedRegisters; offset }, in._4)
      }
    }

  def tuple5[A, B, C, D, E](
    _1: Deconstructor[A],
    _2: Deconstructor[B],
    _3: Deconstructor[C],
    _4: Deconstructor[D],
    _5: Deconstructor[E]
  ): Deconstructor[(A, B, C, D, E)] =
    new Deconstructor[(A, B, C, D, E)] {
      val usedRegisters: RegisterOffset =
        _1.usedRegisters + _2.usedRegisters + _3.usedRegisters + _4.usedRegisters + _5.usedRegisters

      def deconstruct(out: Registers, baseOffset: RegisterOffset, in: (A, B, C, D, E)): Unit = {
        var offset = baseOffset
        _1.deconstruct(out, offset, in._1)
        _2.deconstruct(out, { offset += _1.usedRegisters; offset }, in._2)
        _3.deconstruct(out, { offset += _2.usedRegisters; offset }, in._3)
        _4.deconstruct(out, { offset += _3.usedRegisters; offset }, in._4)
        _5.deconstruct(out, { offset += _4.usedRegisters; offset }, in._5)
      }
    }

  def tuple6[A, B, C, D, E, F](
    _1: Deconstructor[A],
    _2: Deconstructor[B],
    _3: Deconstructor[C],
    _4: Deconstructor[D],
    _5: Deconstructor[E],
    _6: Deconstructor[F]
  ): Deconstructor[(A, B, C, D, E, F)] =
    new Deconstructor[(A, B, C, D, E, F)] {
      val usedRegisters: RegisterOffset =
        _1.usedRegisters + _2.usedRegisters + _3.usedRegisters + _4.usedRegisters + _5.usedRegisters + _6.usedRegisters

      def deconstruct(out: Registers, baseOffset: RegisterOffset, in: (A, B, C, D, E, F)): Unit = {
        var offset = baseOffset
        _1.deconstruct(out, offset, in._1)
        _2.deconstruct(out, { offset += _1.usedRegisters; offset }, in._2)
        _3.deconstruct(out, { offset += _2.usedRegisters; offset }, in._3)
        _4.deconstruct(out, { offset += _3.usedRegisters; offset }, in._4)
        _5.deconstruct(out, { offset += _4.usedRegisters; offset }, in._5)
        _6.deconstruct(out, { offset += _5.usedRegisters; offset }, in._6)
      }
    }

  def tuple7[A, B, C, D, E, F, G](
    _1: Deconstructor[A],
    _2: Deconstructor[B],
    _3: Deconstructor[C],
    _4: Deconstructor[D],
    _5: Deconstructor[E],
    _6: Deconstructor[F],
    _7: Deconstructor[G]
  ): Deconstructor[(A, B, C, D, E, F, G)] =
    new Deconstructor[(A, B, C, D, E, F, G)] {
      val usedRegisters: RegisterOffset =
        _1.usedRegisters + _2.usedRegisters + _3.usedRegisters + _4.usedRegisters + _5.usedRegisters +
          _6.usedRegisters + _7.usedRegisters

      def deconstruct(out: Registers, baseOffset: RegisterOffset, in: (A, B, C, D, E, F, G)): Unit = {
        var offset = baseOffset
        _1.deconstruct(out, offset, in._1)
        _2.deconstruct(out, { offset += _1.usedRegisters; offset }, in._2)
        _3.deconstruct(out, { offset += _2.usedRegisters; offset }, in._3)
        _4.deconstruct(out, { offset += _3.usedRegisters; offset }, in._4)
        _5.deconstruct(out, { offset += _4.usedRegisters; offset }, in._5)
        _6.deconstruct(out, { offset += _5.usedRegisters; offset }, in._6)
        _7.deconstruct(out, { offset += _6.usedRegisters; offset }, in._7)
      }
    }

  def tuple8[A, B, C, D, E, F, G, H](
    _1: Deconstructor[A],
    _2: Deconstructor[B],
    _3: Deconstructor[C],
    _4: Deconstructor[D],
    _5: Deconstructor[E],
    _6: Deconstructor[F],
    _7: Deconstructor[G],
    _8: Deconstructor[H]
  ): Deconstructor[(A, B, C, D, E, F, G, H)] =
    new Deconstructor[(A, B, C, D, E, F, G, H)] {
      val usedRegisters: RegisterOffset =
        _1.usedRegisters + _2.usedRegisters + _3.usedRegisters + _4.usedRegisters + _5.usedRegisters +
          _6.usedRegisters + _7.usedRegisters + _8.usedRegisters

      def deconstruct(out: Registers, baseOffset: RegisterOffset, in: (A, B, C, D, E, F, G, H)): Unit = {
        var offset = baseOffset
        _1.deconstruct(out, offset, in._1)
        _2.deconstruct(out, { offset += _1.usedRegisters; offset }, in._2)
        _3.deconstruct(out, { offset += _2.usedRegisters; offset }, in._3)
        _4.deconstruct(out, { offset += _3.usedRegisters; offset }, in._4)
        _5.deconstruct(out, { offset += _4.usedRegisters; offset }, in._5)
        _6.deconstruct(out, { offset += _5.usedRegisters; offset }, in._6)
        _7.deconstruct(out, { offset += _6.usedRegisters; offset }, in._7)
        _8.deconstruct(out, { offset += _7.usedRegisters; offset }, in._8)
      }
    }

  def tuple9[A, B, C, D, E, F, G, H, I](
    _1: Deconstructor[A],
    _2: Deconstructor[B],
    _3: Deconstructor[C],
    _4: Deconstructor[D],
    _5: Deconstructor[E],
    _6: Deconstructor[F],
    _7: Deconstructor[G],
    _8: Deconstructor[H],
    _9: Deconstructor[I]
  ): Deconstructor[(A, B, C, D, E, F, G, H, I)] =
    new Deconstructor[(A, B, C, D, E, F, G, H, I)] {
      val usedRegisters: RegisterOffset =
        _1.usedRegisters + _2.usedRegisters + _3.usedRegisters + _4.usedRegisters + _5.usedRegisters +
          _6.usedRegisters + _7.usedRegisters + _8.usedRegisters + _9.usedRegisters

      def deconstruct(out: Registers, baseOffset: RegisterOffset, in: (A, B, C, D, E, F, G, H, I)): Unit = {
        var offset = baseOffset
        _1.deconstruct(out, offset, in._1)
        _2.deconstruct(out, { offset += _1.usedRegisters; offset }, in._2)
        _3.deconstruct(out, { offset += _2.usedRegisters; offset }, in._3)
        _4.deconstruct(out, { offset += _3.usedRegisters; offset }, in._4)
        _5.deconstruct(out, { offset += _4.usedRegisters; offset }, in._5)
        _6.deconstruct(out, { offset += _5.usedRegisters; offset }, in._6)
        _7.deconstruct(out, { offset += _6.usedRegisters; offset }, in._7)
        _8.deconstruct(out, { offset += _7.usedRegisters; offset }, in._8)
        _9.deconstruct(out, { offset += _8.usedRegisters; offset }, in._9)
      }
    }

  def tuple10[A, B, C, D, E, F, G, H, I, J](
    _1: Deconstructor[A],
    _2: Deconstructor[B],
    _3: Deconstructor[C],
    _4: Deconstructor[D],
    _5: Deconstructor[E],
    _6: Deconstructor[F],
    _7: Deconstructor[G],
    _8: Deconstructor[H],
    _9: Deconstructor[I],
    _10: Deconstructor[J]
  ): Deconstructor[(A, B, C, D, E, F, G, H, I, J)] =
    new Deconstructor[(A, B, C, D, E, F, G, H, I, J)] {
      val usedRegisters: RegisterOffset =
        _1.usedRegisters + _2.usedRegisters + _3.usedRegisters + _4.usedRegisters + _5.usedRegisters +
          _6.usedRegisters + _7.usedRegisters + _8.usedRegisters + _9.usedRegisters + _10.usedRegisters

      def deconstruct(out: Registers, baseOffset: RegisterOffset, in: (A, B, C, D, E, F, G, H, I, J)): Unit = {
        var offset = baseOffset
        _1.deconstruct(out, offset, in._1)
        _2.deconstruct(out, { offset += _1.usedRegisters; offset }, in._2)
        _3.deconstruct(out, { offset += _2.usedRegisters; offset }, in._3)
        _4.deconstruct(out, { offset += _3.usedRegisters; offset }, in._4)
        _5.deconstruct(out, { offset += _4.usedRegisters; offset }, in._5)
        _6.deconstruct(out, { offset += _5.usedRegisters; offset }, in._6)
        _7.deconstruct(out, { offset += _6.usedRegisters; offset }, in._7)
        _8.deconstruct(out, { offset += _7.usedRegisters; offset }, in._8)
        _9.deconstruct(out, { offset += _8.usedRegisters; offset }, in._9)
        _10.deconstruct(out, { offset += _9.usedRegisters; offset }, in._10)
      }
    }

  def tuple11[A, B, C, D, E, F, G, H, I, J, K](
    _1: Deconstructor[A],
    _2: Deconstructor[B],
    _3: Deconstructor[C],
    _4: Deconstructor[D],
    _5: Deconstructor[E],
    _6: Deconstructor[F],
    _7: Deconstructor[G],
    _8: Deconstructor[H],
    _9: Deconstructor[I],
    _10: Deconstructor[J],
    _11: Deconstructor[K]
  ): Deconstructor[(A, B, C, D, E, F, G, H, I, J, K)] =
    new Deconstructor[(A, B, C, D, E, F, G, H, I, J, K)] {
      val usedRegisters: RegisterOffset =
        _1.usedRegisters + _2.usedRegisters + _3.usedRegisters + _4.usedRegisters + _5.usedRegisters +
          _6.usedRegisters + _7.usedRegisters + _8.usedRegisters + _9.usedRegisters + _10.usedRegisters +
          _11.usedRegisters

      def deconstruct(out: Registers, baseOffset: RegisterOffset, in: (A, B, C, D, E, F, G, H, I, J, K)): Unit = {
        var offset = baseOffset
        _1.deconstruct(out, offset, in._1)
        _2.deconstruct(out, { offset += _1.usedRegisters; offset }, in._2)
        _3.deconstruct(out, { offset += _2.usedRegisters; offset }, in._3)
        _4.deconstruct(out, { offset += _3.usedRegisters; offset }, in._4)
        _5.deconstruct(out, { offset += _4.usedRegisters; offset }, in._5)
        _6.deconstruct(out, { offset += _5.usedRegisters; offset }, in._6)
        _7.deconstruct(out, { offset += _6.usedRegisters; offset }, in._7)
        _8.deconstruct(out, { offset += _7.usedRegisters; offset }, in._8)
        _9.deconstruct(out, { offset += _8.usedRegisters; offset }, in._9)
        _10.deconstruct(out, { offset += _9.usedRegisters; offset }, in._10)
        _11.deconstruct(out, { offset += _10.usedRegisters; offset }, in._11)
      }
    }

  def tuple12[A, B, C, D, E, F, G, H, I, J, K, L](
    _1: Deconstructor[A],
    _2: Deconstructor[B],
    _3: Deconstructor[C],
    _4: Deconstructor[D],
    _5: Deconstructor[E],
    _6: Deconstructor[F],
    _7: Deconstructor[G],
    _8: Deconstructor[H],
    _9: Deconstructor[I],
    _10: Deconstructor[J],
    _11: Deconstructor[K],
    _12: Deconstructor[L]
  ): Deconstructor[(A, B, C, D, E, F, G, H, I, J, K, L)] =
    new Deconstructor[(A, B, C, D, E, F, G, H, I, J, K, L)] {
      val usedRegisters: RegisterOffset =
        _1.usedRegisters + _2.usedRegisters + _3.usedRegisters + _4.usedRegisters + _5.usedRegisters +
          _6.usedRegisters + _7.usedRegisters + _8.usedRegisters + _9.usedRegisters + _10.usedRegisters +
          _11.usedRegisters + _12.usedRegisters

      def deconstruct(out: Registers, baseOffset: RegisterOffset, in: (A, B, C, D, E, F, G, H, I, J, K, L)): Unit = {
        var offset = baseOffset
        _1.deconstruct(out, offset, in._1)
        _2.deconstruct(out, { offset += _1.usedRegisters; offset }, in._2)
        _3.deconstruct(out, { offset += _2.usedRegisters; offset }, in._3)
        _4.deconstruct(out, { offset += _3.usedRegisters; offset }, in._4)
        _5.deconstruct(out, { offset += _4.usedRegisters; offset }, in._5)
        _6.deconstruct(out, { offset += _5.usedRegisters; offset }, in._6)
        _7.deconstruct(out, { offset += _6.usedRegisters; offset }, in._7)
        _8.deconstruct(out, { offset += _7.usedRegisters; offset }, in._8)
        _9.deconstruct(out, { offset += _8.usedRegisters; offset }, in._9)
        _10.deconstruct(out, { offset += _9.usedRegisters; offset }, in._10)
        _11.deconstruct(out, { offset += _10.usedRegisters; offset }, in._11)
        _12.deconstruct(out, { offset += _11.usedRegisters; offset }, in._12)
      }
    }

  def tuple13[A, B, C, D, E, F, G, H, I, J, K, L, M](
    _1: Deconstructor[A],
    _2: Deconstructor[B],
    _3: Deconstructor[C],
    _4: Deconstructor[D],
    _5: Deconstructor[E],
    _6: Deconstructor[F],
    _7: Deconstructor[G],
    _8: Deconstructor[H],
    _9: Deconstructor[I],
    _10: Deconstructor[J],
    _11: Deconstructor[K],
    _12: Deconstructor[L],
    _13: Deconstructor[M]
  ): Deconstructor[(A, B, C, D, E, F, G, H, I, J, K, L, M)] =
    new Deconstructor[(A, B, C, D, E, F, G, H, I, J, K, L, M)] {
      val usedRegisters: RegisterOffset =
        _1.usedRegisters + _2.usedRegisters + _3.usedRegisters + _4.usedRegisters + _5.usedRegisters +
          _6.usedRegisters + _7.usedRegisters + _8.usedRegisters + _9.usedRegisters + _10.usedRegisters +
          _11.usedRegisters + _12.usedRegisters + _13.usedRegisters

      def deconstruct(out: Registers, baseOffset: RegisterOffset, in: (A, B, C, D, E, F, G, H, I, J, K, L, M)): Unit = {
        var offset = baseOffset
        _1.deconstruct(out, offset, in._1)
        _2.deconstruct(out, { offset += _1.usedRegisters; offset }, in._2)
        _3.deconstruct(out, { offset += _2.usedRegisters; offset }, in._3)
        _4.deconstruct(out, { offset += _3.usedRegisters; offset }, in._4)
        _5.deconstruct(out, { offset += _4.usedRegisters; offset }, in._5)
        _6.deconstruct(out, { offset += _5.usedRegisters; offset }, in._6)
        _7.deconstruct(out, { offset += _6.usedRegisters; offset }, in._7)
        _8.deconstruct(out, { offset += _7.usedRegisters; offset }, in._8)
        _9.deconstruct(out, { offset += _8.usedRegisters; offset }, in._9)
        _10.deconstruct(out, { offset += _9.usedRegisters; offset }, in._10)
        _11.deconstruct(out, { offset += _10.usedRegisters; offset }, in._11)
        _12.deconstruct(out, { offset += _11.usedRegisters; offset }, in._12)
        _13.deconstruct(out, { offset += _12.usedRegisters; offset }, in._13)
      }
    }

  def tuple14[A, B, C, D, E, F, G, H, I, J, K, L, M, N](
    _1: Deconstructor[A],
    _2: Deconstructor[B],
    _3: Deconstructor[C],
    _4: Deconstructor[D],
    _5: Deconstructor[E],
    _6: Deconstructor[F],
    _7: Deconstructor[G],
    _8: Deconstructor[H],
    _9: Deconstructor[I],
    _10: Deconstructor[J],
    _11: Deconstructor[K],
    _12: Deconstructor[L],
    _13: Deconstructor[M],
    _14: Deconstructor[N]
  ): Deconstructor[(A, B, C, D, E, F, G, H, I, J, K, L, M, N)] =
    new Deconstructor[(A, B, C, D, E, F, G, H, I, J, K, L, M, N)] {
      val usedRegisters: RegisterOffset =
        _1.usedRegisters + _2.usedRegisters + _3.usedRegisters + _4.usedRegisters + _5.usedRegisters +
          _6.usedRegisters + _7.usedRegisters + _8.usedRegisters + _9.usedRegisters + _10.usedRegisters +
          _11.usedRegisters + _12.usedRegisters + _13.usedRegisters + _14.usedRegisters

      def deconstruct(
        out: Registers,
        baseOffset: RegisterOffset,
        in: (A, B, C, D, E, F, G, H, I, J, K, L, M, N)
      ): Unit = {
        var offset = baseOffset
        _1.deconstruct(out, offset, in._1)
        _2.deconstruct(out, { offset += _1.usedRegisters; offset }, in._2)
        _3.deconstruct(out, { offset += _2.usedRegisters; offset }, in._3)
        _4.deconstruct(out, { offset += _3.usedRegisters; offset }, in._4)
        _5.deconstruct(out, { offset += _4.usedRegisters; offset }, in._5)
        _6.deconstruct(out, { offset += _5.usedRegisters; offset }, in._6)
        _7.deconstruct(out, { offset += _6.usedRegisters; offset }, in._7)
        _8.deconstruct(out, { offset += _7.usedRegisters; offset }, in._8)
        _9.deconstruct(out, { offset += _8.usedRegisters; offset }, in._9)
        _10.deconstruct(out, { offset += _9.usedRegisters; offset }, in._10)
        _11.deconstruct(out, { offset += _10.usedRegisters; offset }, in._11)
        _12.deconstruct(out, { offset += _11.usedRegisters; offset }, in._12)
        _13.deconstruct(out, { offset += _12.usedRegisters; offset }, in._13)
        _14.deconstruct(out, { offset += _13.usedRegisters; offset }, in._14)
      }
    }

  def tuple15[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O](
    _1: Deconstructor[A],
    _2: Deconstructor[B],
    _3: Deconstructor[C],
    _4: Deconstructor[D],
    _5: Deconstructor[E],
    _6: Deconstructor[F],
    _7: Deconstructor[G],
    _8: Deconstructor[H],
    _9: Deconstructor[I],
    _10: Deconstructor[J],
    _11: Deconstructor[K],
    _12: Deconstructor[L],
    _13: Deconstructor[M],
    _14: Deconstructor[N],
    _15: Deconstructor[O]
  ): Deconstructor[(A, B, C, D, E, F, G, H, I, J, K, L, M, N, O)] =
    new Deconstructor[(A, B, C, D, E, F, G, H, I, J, K, L, M, N, O)] {
      val usedRegisters: RegisterOffset =
        _1.usedRegisters + _2.usedRegisters + _3.usedRegisters + _4.usedRegisters + _5.usedRegisters +
          _6.usedRegisters + _7.usedRegisters + _8.usedRegisters + _9.usedRegisters + _10.usedRegisters +
          _11.usedRegisters + _12.usedRegisters + _13.usedRegisters + _14.usedRegisters + _15.usedRegisters

      def deconstruct(
        out: Registers,
        baseOffset: RegisterOffset,
        in: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O)
      ): Unit = {
        var offset = baseOffset
        _1.deconstruct(out, offset, in._1)
        _2.deconstruct(out, { offset += _1.usedRegisters; offset }, in._2)
        _3.deconstruct(out, { offset += _2.usedRegisters; offset }, in._3)
        _4.deconstruct(out, { offset += _3.usedRegisters; offset }, in._4)
        _5.deconstruct(out, { offset += _4.usedRegisters; offset }, in._5)
        _6.deconstruct(out, { offset += _5.usedRegisters; offset }, in._6)
        _7.deconstruct(out, { offset += _6.usedRegisters; offset }, in._7)
        _8.deconstruct(out, { offset += _7.usedRegisters; offset }, in._8)
        _9.deconstruct(out, { offset += _8.usedRegisters; offset }, in._9)
        _10.deconstruct(out, { offset += _9.usedRegisters; offset }, in._10)
        _11.deconstruct(out, { offset += _10.usedRegisters; offset }, in._11)
        _12.deconstruct(out, { offset += _11.usedRegisters; offset }, in._12)
        _13.deconstruct(out, { offset += _12.usedRegisters; offset }, in._13)
        _14.deconstruct(out, { offset += _13.usedRegisters; offset }, in._14)
        _15.deconstruct(out, { offset += _14.usedRegisters; offset }, in._15)
      }
    }

  def tuple16[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P](
    _1: Deconstructor[A],
    _2: Deconstructor[B],
    _3: Deconstructor[C],
    _4: Deconstructor[D],
    _5: Deconstructor[E],
    _6: Deconstructor[F],
    _7: Deconstructor[G],
    _8: Deconstructor[H],
    _9: Deconstructor[I],
    _10: Deconstructor[J],
    _11: Deconstructor[K],
    _12: Deconstructor[L],
    _13: Deconstructor[M],
    _14: Deconstructor[N],
    _15: Deconstructor[O],
    _16: Deconstructor[P]
  ): Deconstructor[(A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P)] =
    new Deconstructor[(A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P)] {
      val usedRegisters: RegisterOffset =
        _1.usedRegisters + _2.usedRegisters + _3.usedRegisters + _4.usedRegisters + _5.usedRegisters +
          _6.usedRegisters + _7.usedRegisters + _8.usedRegisters + _9.usedRegisters + _10.usedRegisters +
          _11.usedRegisters + _12.usedRegisters + _13.usedRegisters + _14.usedRegisters + _15.usedRegisters +
          _16.usedRegisters

      def deconstruct(
        out: Registers,
        baseOffset: RegisterOffset,
        in: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P)
      ): Unit = {
        var offset = baseOffset
        _1.deconstruct(out, offset, in._1)
        _2.deconstruct(out, { offset += _1.usedRegisters; offset }, in._2)
        _3.deconstruct(out, { offset += _2.usedRegisters; offset }, in._3)
        _4.deconstruct(out, { offset += _3.usedRegisters; offset }, in._4)
        _5.deconstruct(out, { offset += _4.usedRegisters; offset }, in._5)
        _6.deconstruct(out, { offset += _5.usedRegisters; offset }, in._6)
        _7.deconstruct(out, { offset += _6.usedRegisters; offset }, in._7)
        _8.deconstruct(out, { offset += _7.usedRegisters; offset }, in._8)
        _9.deconstruct(out, { offset += _8.usedRegisters; offset }, in._9)
        _10.deconstruct(out, { offset += _9.usedRegisters; offset }, in._10)
        _11.deconstruct(out, { offset += _10.usedRegisters; offset }, in._11)
        _12.deconstruct(out, { offset += _11.usedRegisters; offset }, in._12)
        _13.deconstruct(out, { offset += _12.usedRegisters; offset }, in._13)
        _14.deconstruct(out, { offset += _13.usedRegisters; offset }, in._14)
        _15.deconstruct(out, { offset += _14.usedRegisters; offset }, in._15)
        _16.deconstruct(out, { offset += _15.usedRegisters; offset }, in._16)
      }
    }

  def tuple17[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q](
    _1: Deconstructor[A],
    _2: Deconstructor[B],
    _3: Deconstructor[C],
    _4: Deconstructor[D],
    _5: Deconstructor[E],
    _6: Deconstructor[F],
    _7: Deconstructor[G],
    _8: Deconstructor[H],
    _9: Deconstructor[I],
    _10: Deconstructor[J],
    _11: Deconstructor[K],
    _12: Deconstructor[L],
    _13: Deconstructor[M],
    _14: Deconstructor[N],
    _15: Deconstructor[O],
    _16: Deconstructor[P],
    _17: Deconstructor[Q]
  ): Deconstructor[(A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q)] =
    new Deconstructor[(A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q)] {
      val usedRegisters: RegisterOffset =
        _1.usedRegisters + _2.usedRegisters + _3.usedRegisters + _4.usedRegisters + _5.usedRegisters +
          _6.usedRegisters + _7.usedRegisters + _8.usedRegisters + _9.usedRegisters + _10.usedRegisters +
          _11.usedRegisters + _12.usedRegisters + _13.usedRegisters + _14.usedRegisters + _15.usedRegisters +
          _16.usedRegisters + _17.usedRegisters

      def deconstruct(
        out: Registers,
        baseOffset: RegisterOffset,
        in: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q)
      ): Unit = {
        var offset = baseOffset
        _1.deconstruct(out, offset, in._1)
        _2.deconstruct(out, { offset += _1.usedRegisters; offset }, in._2)
        _3.deconstruct(out, { offset += _2.usedRegisters; offset }, in._3)
        _4.deconstruct(out, { offset += _3.usedRegisters; offset }, in._4)
        _5.deconstruct(out, { offset += _4.usedRegisters; offset }, in._5)
        _6.deconstruct(out, { offset += _5.usedRegisters; offset }, in._6)
        _7.deconstruct(out, { offset += _6.usedRegisters; offset }, in._7)
        _8.deconstruct(out, { offset += _7.usedRegisters; offset }, in._8)
        _9.deconstruct(out, { offset += _8.usedRegisters; offset }, in._9)
        _10.deconstruct(out, { offset += _9.usedRegisters; offset }, in._10)
        _11.deconstruct(out, { offset += _10.usedRegisters; offset }, in._11)
        _12.deconstruct(out, { offset += _11.usedRegisters; offset }, in._12)
        _13.deconstruct(out, { offset += _12.usedRegisters; offset }, in._13)
        _14.deconstruct(out, { offset += _13.usedRegisters; offset }, in._14)
        _15.deconstruct(out, { offset += _14.usedRegisters; offset }, in._15)
        _16.deconstruct(out, { offset += _15.usedRegisters; offset }, in._16)
        _17.deconstruct(out, { offset += _16.usedRegisters; offset }, in._17)
      }
    }

  def tuple18[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R](
    _1: Deconstructor[A],
    _2: Deconstructor[B],
    _3: Deconstructor[C],
    _4: Deconstructor[D],
    _5: Deconstructor[E],
    _6: Deconstructor[F],
    _7: Deconstructor[G],
    _8: Deconstructor[H],
    _9: Deconstructor[I],
    _10: Deconstructor[J],
    _11: Deconstructor[K],
    _12: Deconstructor[L],
    _13: Deconstructor[M],
    _14: Deconstructor[N],
    _15: Deconstructor[O],
    _16: Deconstructor[P],
    _17: Deconstructor[Q],
    _18: Deconstructor[R]
  ): Deconstructor[(A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R)] =
    new Deconstructor[(A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R)] {
      val usedRegisters: RegisterOffset =
        _1.usedRegisters + _2.usedRegisters + _3.usedRegisters + _4.usedRegisters + _5.usedRegisters +
          _6.usedRegisters + _7.usedRegisters + _8.usedRegisters + _9.usedRegisters + _10.usedRegisters +
          _11.usedRegisters + _12.usedRegisters + _13.usedRegisters + _14.usedRegisters + _15.usedRegisters +
          _16.usedRegisters + _17.usedRegisters + _18.usedRegisters

      def deconstruct(
        out: Registers,
        baseOffset: RegisterOffset,
        in: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R)
      ): Unit = {
        var offset = baseOffset
        _1.deconstruct(out, offset, in._1)
        _2.deconstruct(out, { offset += _1.usedRegisters; offset }, in._2)
        _3.deconstruct(out, { offset += _2.usedRegisters; offset }, in._3)
        _4.deconstruct(out, { offset += _3.usedRegisters; offset }, in._4)
        _5.deconstruct(out, { offset += _4.usedRegisters; offset }, in._5)
        _6.deconstruct(out, { offset += _5.usedRegisters; offset }, in._6)
        _7.deconstruct(out, { offset += _6.usedRegisters; offset }, in._7)
        _8.deconstruct(out, { offset += _7.usedRegisters; offset }, in._8)
        _9.deconstruct(out, { offset += _8.usedRegisters; offset }, in._9)
        _10.deconstruct(out, { offset += _9.usedRegisters; offset }, in._10)
        _11.deconstruct(out, { offset += _10.usedRegisters; offset }, in._11)
        _12.deconstruct(out, { offset += _11.usedRegisters; offset }, in._12)
        _13.deconstruct(out, { offset += _12.usedRegisters; offset }, in._13)
        _14.deconstruct(out, { offset += _13.usedRegisters; offset }, in._14)
        _15.deconstruct(out, { offset += _14.usedRegisters; offset }, in._15)
        _16.deconstruct(out, { offset += _15.usedRegisters; offset }, in._16)
        _17.deconstruct(out, { offset += _16.usedRegisters; offset }, in._17)
        _18.deconstruct(out, { offset += _17.usedRegisters; offset }, in._18)
      }
    }

  def tuple19[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S](
    _1: Deconstructor[A],
    _2: Deconstructor[B],
    _3: Deconstructor[C],
    _4: Deconstructor[D],
    _5: Deconstructor[E],
    _6: Deconstructor[F],
    _7: Deconstructor[G],
    _8: Deconstructor[H],
    _9: Deconstructor[I],
    _10: Deconstructor[J],
    _11: Deconstructor[K],
    _12: Deconstructor[L],
    _13: Deconstructor[M],
    _14: Deconstructor[N],
    _15: Deconstructor[O],
    _16: Deconstructor[P],
    _17: Deconstructor[Q],
    _18: Deconstructor[R],
    _19: Deconstructor[S]
  ): Deconstructor[(A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S)] =
    new Deconstructor[(A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S)] {
      val usedRegisters: RegisterOffset =
        _1.usedRegisters + _2.usedRegisters + _3.usedRegisters + _4.usedRegisters + _5.usedRegisters +
          _6.usedRegisters + _7.usedRegisters + _8.usedRegisters + _9.usedRegisters + _10.usedRegisters +
          _11.usedRegisters + _12.usedRegisters + _13.usedRegisters + _14.usedRegisters + _15.usedRegisters +
          _16.usedRegisters + _17.usedRegisters + _18.usedRegisters + _19.usedRegisters

      def deconstruct(
        out: Registers,
        baseOffset: RegisterOffset,
        in: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S)
      ): Unit = {
        var offset = baseOffset
        _1.deconstruct(out, offset, in._1)
        _2.deconstruct(out, { offset += _1.usedRegisters; offset }, in._2)
        _3.deconstruct(out, { offset += _2.usedRegisters; offset }, in._3)
        _4.deconstruct(out, { offset += _3.usedRegisters; offset }, in._4)
        _5.deconstruct(out, { offset += _4.usedRegisters; offset }, in._5)
        _6.deconstruct(out, { offset += _5.usedRegisters; offset }, in._6)
        _7.deconstruct(out, { offset += _6.usedRegisters; offset }, in._7)
        _8.deconstruct(out, { offset += _7.usedRegisters; offset }, in._8)
        _9.deconstruct(out, { offset += _8.usedRegisters; offset }, in._9)
        _10.deconstruct(out, { offset += _9.usedRegisters; offset }, in._10)
        _11.deconstruct(out, { offset += _10.usedRegisters; offset }, in._11)
        _12.deconstruct(out, { offset += _11.usedRegisters; offset }, in._12)
        _13.deconstruct(out, { offset += _12.usedRegisters; offset }, in._13)
        _14.deconstruct(out, { offset += _13.usedRegisters; offset }, in._14)
        _15.deconstruct(out, { offset += _14.usedRegisters; offset }, in._15)
        _16.deconstruct(out, { offset += _15.usedRegisters; offset }, in._16)
        _17.deconstruct(out, { offset += _16.usedRegisters; offset }, in._17)
        _18.deconstruct(out, { offset += _17.usedRegisters; offset }, in._18)
        _19.deconstruct(out, { offset += _18.usedRegisters; offset }, in._19)
      }
    }

  def tuple20[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T](
    _1: Deconstructor[A],
    _2: Deconstructor[B],
    _3: Deconstructor[C],
    _4: Deconstructor[D],
    _5: Deconstructor[E],
    _6: Deconstructor[F],
    _7: Deconstructor[G],
    _8: Deconstructor[H],
    _9: Deconstructor[I],
    _10: Deconstructor[J],
    _11: Deconstructor[K],
    _12: Deconstructor[L],
    _13: Deconstructor[M],
    _14: Deconstructor[N],
    _15: Deconstructor[O],
    _16: Deconstructor[P],
    _17: Deconstructor[Q],
    _18: Deconstructor[R],
    _19: Deconstructor[S],
    _20: Deconstructor[T]
  ): Deconstructor[(A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T)] =
    new Deconstructor[(A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T)] {
      val usedRegisters: RegisterOffset =
        _1.usedRegisters + _2.usedRegisters + _3.usedRegisters + _4.usedRegisters + _5.usedRegisters +
          _6.usedRegisters + _7.usedRegisters + _8.usedRegisters + _9.usedRegisters + _10.usedRegisters +
          _11.usedRegisters + _12.usedRegisters + _13.usedRegisters + _14.usedRegisters + _15.usedRegisters +
          _16.usedRegisters + _17.usedRegisters + _18.usedRegisters + _19.usedRegisters + _20.usedRegisters

      def deconstruct(
        out: Registers,
        baseOffset: RegisterOffset,
        in: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T)
      ): Unit = {
        var offset = baseOffset
        _1.deconstruct(out, offset, in._1)
        _2.deconstruct(out, { offset += _1.usedRegisters; offset }, in._2)
        _3.deconstruct(out, { offset += _2.usedRegisters; offset }, in._3)
        _4.deconstruct(out, { offset += _3.usedRegisters; offset }, in._4)
        _5.deconstruct(out, { offset += _4.usedRegisters; offset }, in._5)
        _6.deconstruct(out, { offset += _5.usedRegisters; offset }, in._6)
        _7.deconstruct(out, { offset += _6.usedRegisters; offset }, in._7)
        _8.deconstruct(out, { offset += _7.usedRegisters; offset }, in._8)
        _9.deconstruct(out, { offset += _8.usedRegisters; offset }, in._9)
        _10.deconstruct(out, { offset += _9.usedRegisters; offset }, in._10)
        _11.deconstruct(out, { offset += _10.usedRegisters; offset }, in._11)
        _12.deconstruct(out, { offset += _11.usedRegisters; offset }, in._12)
        _13.deconstruct(out, { offset += _12.usedRegisters; offset }, in._13)
        _14.deconstruct(out, { offset += _13.usedRegisters; offset }, in._14)
        _15.deconstruct(out, { offset += _14.usedRegisters; offset }, in._15)
        _16.deconstruct(out, { offset += _15.usedRegisters; offset }, in._16)
        _17.deconstruct(out, { offset += _16.usedRegisters; offset }, in._17)
        _18.deconstruct(out, { offset += _17.usedRegisters; offset }, in._18)
        _19.deconstruct(out, { offset += _18.usedRegisters; offset }, in._19)
        _20.deconstruct(out, { offset += _19.usedRegisters; offset }, in._20)
      }
    }

  def tuple21[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, U](
    _1: Deconstructor[A],
    _2: Deconstructor[B],
    _3: Deconstructor[C],
    _4: Deconstructor[D],
    _5: Deconstructor[E],
    _6: Deconstructor[F],
    _7: Deconstructor[G],
    _8: Deconstructor[H],
    _9: Deconstructor[I],
    _10: Deconstructor[J],
    _11: Deconstructor[K],
    _12: Deconstructor[L],
    _13: Deconstructor[M],
    _14: Deconstructor[N],
    _15: Deconstructor[O],
    _16: Deconstructor[P],
    _17: Deconstructor[Q],
    _18: Deconstructor[R],
    _19: Deconstructor[S],
    _20: Deconstructor[T],
    _21: Deconstructor[U]
  ): Deconstructor[(A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, U)] =
    new Deconstructor[(A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, U)] {
      val usedRegisters: RegisterOffset =
        _1.usedRegisters + _2.usedRegisters + _3.usedRegisters + _4.usedRegisters + _5.usedRegisters +
          _6.usedRegisters + _7.usedRegisters + _8.usedRegisters + _9.usedRegisters + _10.usedRegisters +
          _11.usedRegisters + _12.usedRegisters + _13.usedRegisters + _14.usedRegisters + _15.usedRegisters +
          _16.usedRegisters + _17.usedRegisters + _18.usedRegisters + _19.usedRegisters + _20.usedRegisters +
          _21.usedRegisters

      def deconstruct(
        out: Registers,
        baseOffset: RegisterOffset,
        in: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, U)
      ): Unit = {
        var offset = baseOffset
        _1.deconstruct(out, offset, in._1)
        _2.deconstruct(out, { offset += _1.usedRegisters; offset }, in._2)
        _3.deconstruct(out, { offset += _2.usedRegisters; offset }, in._3)
        _4.deconstruct(out, { offset += _3.usedRegisters; offset }, in._4)
        _5.deconstruct(out, { offset += _4.usedRegisters; offset }, in._5)
        _6.deconstruct(out, { offset += _5.usedRegisters; offset }, in._6)
        _7.deconstruct(out, { offset += _6.usedRegisters; offset }, in._7)
        _8.deconstruct(out, { offset += _7.usedRegisters; offset }, in._8)
        _9.deconstruct(out, { offset += _8.usedRegisters; offset }, in._9)
        _10.deconstruct(out, { offset += _9.usedRegisters; offset }, in._10)
        _11.deconstruct(out, { offset += _10.usedRegisters; offset }, in._11)
        _12.deconstruct(out, { offset += _11.usedRegisters; offset }, in._12)
        _13.deconstruct(out, { offset += _12.usedRegisters; offset }, in._13)
        _14.deconstruct(out, { offset += _13.usedRegisters; offset }, in._14)
        _15.deconstruct(out, { offset += _14.usedRegisters; offset }, in._15)
        _16.deconstruct(out, { offset += _15.usedRegisters; offset }, in._16)
        _17.deconstruct(out, { offset += _16.usedRegisters; offset }, in._17)
        _18.deconstruct(out, { offset += _17.usedRegisters; offset }, in._18)
        _19.deconstruct(out, { offset += _18.usedRegisters; offset }, in._19)
        _20.deconstruct(out, { offset += _19.usedRegisters; offset }, in._20)
        _21.deconstruct(out, { offset += _20.usedRegisters; offset }, in._21)
      }
    }

  def tuple22[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, U, V](
    _1: Deconstructor[A],
    _2: Deconstructor[B],
    _3: Deconstructor[C],
    _4: Deconstructor[D],
    _5: Deconstructor[E],
    _6: Deconstructor[F],
    _7: Deconstructor[G],
    _8: Deconstructor[H],
    _9: Deconstructor[I],
    _10: Deconstructor[J],
    _11: Deconstructor[K],
    _12: Deconstructor[L],
    _13: Deconstructor[M],
    _14: Deconstructor[N],
    _15: Deconstructor[O],
    _16: Deconstructor[P],
    _17: Deconstructor[Q],
    _18: Deconstructor[R],
    _19: Deconstructor[S],
    _20: Deconstructor[T],
    _21: Deconstructor[U],
    _22: Deconstructor[V]
  ): Deconstructor[(A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, U, V)] =
    new Deconstructor[(A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, U, V)] {
      val usedRegisters: RegisterOffset =
        _1.usedRegisters + _2.usedRegisters + _3.usedRegisters + _4.usedRegisters + _5.usedRegisters +
          _6.usedRegisters + _7.usedRegisters + _8.usedRegisters + _9.usedRegisters + _10.usedRegisters +
          _11.usedRegisters + _12.usedRegisters + _13.usedRegisters + _14.usedRegisters + _15.usedRegisters +
          _16.usedRegisters + _17.usedRegisters + _18.usedRegisters + _19.usedRegisters + _20.usedRegisters +
          _21.usedRegisters + _22.usedRegisters

      def deconstruct(
        out: Registers,
        baseOffset: RegisterOffset,
        in: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, U, V)
      ): Unit = {
        var offset = baseOffset
        _1.deconstruct(out, offset, in._1)
        _2.deconstruct(out, { offset += _1.usedRegisters; offset }, in._2)
        _3.deconstruct(out, { offset += _2.usedRegisters; offset }, in._3)
        _4.deconstruct(out, { offset += _3.usedRegisters; offset }, in._4)
        _5.deconstruct(out, { offset += _4.usedRegisters; offset }, in._5)
        _6.deconstruct(out, { offset += _5.usedRegisters; offset }, in._6)
        _7.deconstruct(out, { offset += _6.usedRegisters; offset }, in._7)
        _8.deconstruct(out, { offset += _7.usedRegisters; offset }, in._8)
        _9.deconstruct(out, { offset += _8.usedRegisters; offset }, in._9)
        _10.deconstruct(out, { offset += _9.usedRegisters; offset }, in._10)
        _11.deconstruct(out, { offset += _10.usedRegisters; offset }, in._11)
        _12.deconstruct(out, { offset += _11.usedRegisters; offset }, in._12)
        _13.deconstruct(out, { offset += _12.usedRegisters; offset }, in._13)
        _14.deconstruct(out, { offset += _13.usedRegisters; offset }, in._14)
        _15.deconstruct(out, { offset += _14.usedRegisters; offset }, in._15)
        _16.deconstruct(out, { offset += _15.usedRegisters; offset }, in._16)
        _17.deconstruct(out, { offset += _16.usedRegisters; offset }, in._17)
        _18.deconstruct(out, { offset += _17.usedRegisters; offset }, in._18)
        _19.deconstruct(out, { offset += _18.usedRegisters; offset }, in._19)
        _20.deconstruct(out, { offset += _19.usedRegisters; offset }, in._20)
        _21.deconstruct(out, { offset += _20.usedRegisters; offset }, in._21)
        _22.deconstruct(out, { offset += _21.usedRegisters; offset }, in._22)
      }
    }
}
