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
  final def zip[B](that: Constructor[B]): Constructor[(A, B)] = Constructor.tuple2(self, that)
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

  def some[A](c: Constructor[A]): Constructor[Some[A]] = c.map(Some(_))

  val none: Constructor[None.type] = Constructor.unit.map(_ => None)

  def left[A](c: Constructor[A]): Constructor[Left[A, Nothing]] = c.map(Left(_))

  def right[B](c: Constructor[B]): Constructor[Right[Nothing, B]] = c.map(Right(_))

  def tuple2[A, B](_1: Constructor[A], _2: Constructor[B]): Constructor[(A, B)] =
    new Constructor[(A, B)] {
      val usedRegisters: RegisterOffset = _1.usedRegisters + _2.usedRegisters

      def construct(in: Registers, baseOffset: RegisterOffset): (A, B) = {
        var offset = baseOffset
        (
          _1.construct(in, offset),
          _2.construct(in, { offset += _1.usedRegisters; offset })
        )
      }
    }

  def tuple3[A, B, C](_1: Constructor[A], _2: Constructor[B], _3: Constructor[C]): Constructor[(A, B, C)] =
    new Constructor[(A, B, C)] {
      val usedRegisters: RegisterOffset = _1.usedRegisters + _2.usedRegisters + _3.usedRegisters

      def construct(in: Registers, baseOffset: RegisterOffset): (A, B, C) = {
        var offset = baseOffset
        (
          _1.construct(in, offset),
          _2.construct(in, { offset += _1.usedRegisters; offset }),
          _3.construct(in, { offset += _2.usedRegisters; offset })
        )
      }
    }

  def tuple4[A, B, C, D](
    _1: Constructor[A],
    _2: Constructor[B],
    _3: Constructor[C],
    _4: Constructor[D]
  ): Constructor[(A, B, C, D)] =
    new Constructor[(A, B, C, D)] {
      val usedRegisters: RegisterOffset = _1.usedRegisters + _2.usedRegisters + _3.usedRegisters + _4.usedRegisters

      def construct(in: Registers, baseOffset: RegisterOffset): (A, B, C, D) = {
        var offset = baseOffset
        (
          _1.construct(in, offset),
          _2.construct(in, { offset += _1.usedRegisters; offset }),
          _3.construct(in, { offset += _2.usedRegisters; offset }),
          _4.construct(in, { offset += _3.usedRegisters; offset })
        )
      }
    }

  def tuple5[A, B, C, D, E](
    _1: Constructor[A],
    _2: Constructor[B],
    _3: Constructor[C],
    _4: Constructor[D],
    _5: Constructor[E]
  ): Constructor[(A, B, C, D, E)] =
    new Constructor[(A, B, C, D, E)] {
      val usedRegisters: RegisterOffset =
        _1.usedRegisters + _2.usedRegisters + _3.usedRegisters + _4.usedRegisters + _5.usedRegisters

      def construct(in: Registers, baseOffset: RegisterOffset): (A, B, C, D, E) = {
        var offset = baseOffset
        (
          _1.construct(in, offset),
          _2.construct(in, { offset += _1.usedRegisters; offset }),
          _3.construct(in, { offset += _2.usedRegisters; offset }),
          _4.construct(in, { offset += _3.usedRegisters; offset }),
          _5.construct(in, { offset += _4.usedRegisters; offset })
        )
      }
    }

  def tuple6[A, B, C, D, E, F](
    _1: Constructor[A],
    _2: Constructor[B],
    _3: Constructor[C],
    _4: Constructor[D],
    _5: Constructor[E],
    _6: Constructor[F]
  ): Constructor[(A, B, C, D, E, F)] =
    new Constructor[(A, B, C, D, E, F)] {
      val usedRegisters: RegisterOffset =
        _1.usedRegisters + _2.usedRegisters + _3.usedRegisters + _4.usedRegisters + _5.usedRegisters + _6.usedRegisters

      def construct(in: Registers, baseOffset: RegisterOffset): (A, B, C, D, E, F) = {
        var offset = baseOffset
        (
          _1.construct(in, offset),
          _2.construct(in, { offset += _1.usedRegisters; offset }),
          _3.construct(in, { offset += _2.usedRegisters; offset }),
          _4.construct(in, { offset += _3.usedRegisters; offset }),
          _5.construct(in, { offset += _4.usedRegisters; offset }),
          _6.construct(in, { offset += _5.usedRegisters; offset })
        )
      }
    }

  def tuple7[A, B, C, D, E, F, G](
    _1: Constructor[A],
    _2: Constructor[B],
    _3: Constructor[C],
    _4: Constructor[D],
    _5: Constructor[E],
    _6: Constructor[F],
    _7: Constructor[G]
  ): Constructor[(A, B, C, D, E, F, G)] =
    new Constructor[(A, B, C, D, E, F, G)] {
      val usedRegisters: RegisterOffset =
        _1.usedRegisters + _2.usedRegisters + _3.usedRegisters + _4.usedRegisters + _5.usedRegisters +
          _6.usedRegisters + _7.usedRegisters

      def construct(in: Registers, baseOffset: RegisterOffset): (A, B, C, D, E, F, G) = {
        var offset = baseOffset
        (
          _1.construct(in, offset),
          _2.construct(in, { offset += _1.usedRegisters; offset }),
          _3.construct(in, { offset += _2.usedRegisters; offset }),
          _4.construct(in, { offset += _3.usedRegisters; offset }),
          _5.construct(in, { offset += _4.usedRegisters; offset }),
          _6.construct(in, { offset += _5.usedRegisters; offset }),
          _7.construct(in, { offset += _6.usedRegisters; offset })
        )
      }
    }

  def tuple8[A, B, C, D, E, F, G, H](
    _1: Constructor[A],
    _2: Constructor[B],
    _3: Constructor[C],
    _4: Constructor[D],
    _5: Constructor[E],
    _6: Constructor[F],
    _7: Constructor[G],
    _8: Constructor[H]
  ): Constructor[(A, B, C, D, E, F, G, H)] =
    new Constructor[(A, B, C, D, E, F, G, H)] {
      val usedRegisters: RegisterOffset =
        _1.usedRegisters + _2.usedRegisters + _3.usedRegisters + _4.usedRegisters + _5.usedRegisters +
          _6.usedRegisters + _7.usedRegisters + _8.usedRegisters

      def construct(in: Registers, baseOffset: RegisterOffset): (A, B, C, D, E, F, G, H) = {
        var offset = baseOffset
        (
          _1.construct(in, offset),
          _2.construct(in, { offset += _1.usedRegisters; offset }),
          _3.construct(in, { offset += _2.usedRegisters; offset }),
          _4.construct(in, { offset += _3.usedRegisters; offset }),
          _5.construct(in, { offset += _4.usedRegisters; offset }),
          _6.construct(in, { offset += _5.usedRegisters; offset }),
          _7.construct(in, { offset += _6.usedRegisters; offset }),
          _8.construct(in, { offset += _7.usedRegisters; offset })
        )
      }
    }

  def tuple9[A, B, C, D, E, F, G, H, I](
    _1: Constructor[A],
    _2: Constructor[B],
    _3: Constructor[C],
    _4: Constructor[D],
    _5: Constructor[E],
    _6: Constructor[F],
    _7: Constructor[G],
    _8: Constructor[H],
    _9: Constructor[I]
  ): Constructor[(A, B, C, D, E, F, G, H, I)] =
    new Constructor[(A, B, C, D, E, F, G, H, I)] {
      val usedRegisters: RegisterOffset =
        _1.usedRegisters + _2.usedRegisters + _3.usedRegisters + _4.usedRegisters + _5.usedRegisters +
          _6.usedRegisters + _7.usedRegisters + _8.usedRegisters + _9.usedRegisters

      def construct(in: Registers, baseOffset: RegisterOffset): (A, B, C, D, E, F, G, H, I) = {
        var offset = baseOffset
        (
          _1.construct(in, offset),
          _2.construct(in, { offset += _1.usedRegisters; offset }),
          _3.construct(in, { offset += _2.usedRegisters; offset }),
          _4.construct(in, { offset += _3.usedRegisters; offset }),
          _5.construct(in, { offset += _4.usedRegisters; offset }),
          _6.construct(in, { offset += _5.usedRegisters; offset }),
          _7.construct(in, { offset += _6.usedRegisters; offset }),
          _8.construct(in, { offset += _7.usedRegisters; offset }),
          _9.construct(in, { offset += _8.usedRegisters; offset })
        )
      }
    }

  def tuple10[A, B, C, D, E, F, G, H, I, J](
    _1: Constructor[A],
    _2: Constructor[B],
    _3: Constructor[C],
    _4: Constructor[D],
    _5: Constructor[E],
    _6: Constructor[F],
    _7: Constructor[G],
    _8: Constructor[H],
    _9: Constructor[I],
    _10: Constructor[J]
  ): Constructor[(A, B, C, D, E, F, G, H, I, J)] =
    new Constructor[(A, B, C, D, E, F, G, H, I, J)] {
      val usedRegisters: RegisterOffset =
        _1.usedRegisters + _2.usedRegisters + _3.usedRegisters + _4.usedRegisters + _5.usedRegisters +
          _6.usedRegisters + _7.usedRegisters + _8.usedRegisters + _9.usedRegisters + _10.usedRegisters

      def construct(in: Registers, baseOffset: RegisterOffset): (A, B, C, D, E, F, G, H, I, J) = {
        var offset = baseOffset
        (
          _1.construct(in, offset),
          _2.construct(in, { offset += _1.usedRegisters; offset }),
          _3.construct(in, { offset += _2.usedRegisters; offset }),
          _4.construct(in, { offset += _3.usedRegisters; offset }),
          _5.construct(in, { offset += _4.usedRegisters; offset }),
          _6.construct(in, { offset += _5.usedRegisters; offset }),
          _7.construct(in, { offset += _6.usedRegisters; offset }),
          _8.construct(in, { offset += _7.usedRegisters; offset }),
          _9.construct(in, { offset += _8.usedRegisters; offset }),
          _10.construct(in, { offset += _9.usedRegisters; offset })
        )
      }
    }

  def tuple11[A, B, C, D, E, F, G, H, I, J, K](
    _1: Constructor[A],
    _2: Constructor[B],
    _3: Constructor[C],
    _4: Constructor[D],
    _5: Constructor[E],
    _6: Constructor[F],
    _7: Constructor[G],
    _8: Constructor[H],
    _9: Constructor[I],
    _10: Constructor[J],
    _11: Constructor[K]
  ): Constructor[(A, B, C, D, E, F, G, H, I, J, K)] =
    new Constructor[(A, B, C, D, E, F, G, H, I, J, K)] {
      val usedRegisters: RegisterOffset =
        _1.usedRegisters + _2.usedRegisters + _3.usedRegisters + _4.usedRegisters + _5.usedRegisters +
          _6.usedRegisters + _7.usedRegisters + _8.usedRegisters + _9.usedRegisters + _10.usedRegisters +
          _11.usedRegisters

      def construct(in: Registers, baseOffset: RegisterOffset): (A, B, C, D, E, F, G, H, I, J, K) = {
        var offset = baseOffset
        (
          _1.construct(in, offset),
          _2.construct(in, { offset += _1.usedRegisters; offset }),
          _3.construct(in, { offset += _2.usedRegisters; offset }),
          _4.construct(in, { offset += _3.usedRegisters; offset }),
          _5.construct(in, { offset += _4.usedRegisters; offset }),
          _6.construct(in, { offset += _5.usedRegisters; offset }),
          _7.construct(in, { offset += _6.usedRegisters; offset }),
          _8.construct(in, { offset += _7.usedRegisters; offset }),
          _9.construct(in, { offset += _8.usedRegisters; offset }),
          _10.construct(in, { offset += _9.usedRegisters; offset }),
          _11.construct(in, { offset += _10.usedRegisters; offset })
        )
      }
    }

  def tuple12[A, B, C, D, E, F, G, H, I, J, K, L](
    _1: Constructor[A],
    _2: Constructor[B],
    _3: Constructor[C],
    _4: Constructor[D],
    _5: Constructor[E],
    _6: Constructor[F],
    _7: Constructor[G],
    _8: Constructor[H],
    _9: Constructor[I],
    _10: Constructor[J],
    _11: Constructor[K],
    _12: Constructor[L]
  ): Constructor[(A, B, C, D, E, F, G, H, I, J, K, L)] =
    new Constructor[(A, B, C, D, E, F, G, H, I, J, K, L)] {
      val usedRegisters: RegisterOffset =
        _1.usedRegisters + _2.usedRegisters + _3.usedRegisters + _4.usedRegisters + _5.usedRegisters +
          _6.usedRegisters + _7.usedRegisters + _8.usedRegisters + _9.usedRegisters + _10.usedRegisters +
          _11.usedRegisters + _12.usedRegisters

      def construct(in: Registers, baseOffset: RegisterOffset): (A, B, C, D, E, F, G, H, I, J, K, L) = {
        var offset = baseOffset
        (
          _1.construct(in, offset),
          _2.construct(in, { offset += _1.usedRegisters; offset }),
          _3.construct(in, { offset += _2.usedRegisters; offset }),
          _4.construct(in, { offset += _3.usedRegisters; offset }),
          _5.construct(in, { offset += _4.usedRegisters; offset }),
          _6.construct(in, { offset += _5.usedRegisters; offset }),
          _7.construct(in, { offset += _6.usedRegisters; offset }),
          _8.construct(in, { offset += _7.usedRegisters; offset }),
          _9.construct(in, { offset += _8.usedRegisters; offset }),
          _10.construct(in, { offset += _9.usedRegisters; offset }),
          _11.construct(in, { offset += _10.usedRegisters; offset }),
          _12.construct(in, { offset += _11.usedRegisters; offset })
        )
      }
    }

  def tuple13[A, B, C, D, E, F, G, H, I, J, K, L, M](
    _1: Constructor[A],
    _2: Constructor[B],
    _3: Constructor[C],
    _4: Constructor[D],
    _5: Constructor[E],
    _6: Constructor[F],
    _7: Constructor[G],
    _8: Constructor[H],
    _9: Constructor[I],
    _10: Constructor[J],
    _11: Constructor[K],
    _12: Constructor[L],
    _13: Constructor[M]
  ): Constructor[(A, B, C, D, E, F, G, H, I, J, K, L, M)] =
    new Constructor[(A, B, C, D, E, F, G, H, I, J, K, L, M)] {
      val usedRegisters: RegisterOffset =
        _1.usedRegisters + _2.usedRegisters + _3.usedRegisters + _4.usedRegisters + _5.usedRegisters +
          _6.usedRegisters + _7.usedRegisters + _8.usedRegisters + _9.usedRegisters + _10.usedRegisters +
          _11.usedRegisters + _12.usedRegisters + _13.usedRegisters

      def construct(in: Registers, baseOffset: RegisterOffset): (A, B, C, D, E, F, G, H, I, J, K, L, M) = {
        var offset = baseOffset
        (
          _1.construct(in, offset),
          _2.construct(in, { offset += _1.usedRegisters; offset }),
          _3.construct(in, { offset += _2.usedRegisters; offset }),
          _4.construct(in, { offset += _3.usedRegisters; offset }),
          _5.construct(in, { offset += _4.usedRegisters; offset }),
          _6.construct(in, { offset += _5.usedRegisters; offset }),
          _7.construct(in, { offset += _6.usedRegisters; offset }),
          _8.construct(in, { offset += _7.usedRegisters; offset }),
          _9.construct(in, { offset += _8.usedRegisters; offset }),
          _10.construct(in, { offset += _9.usedRegisters; offset }),
          _11.construct(in, { offset += _10.usedRegisters; offset }),
          _12.construct(in, { offset += _11.usedRegisters; offset }),
          _13.construct(in, { offset += _12.usedRegisters; offset })
        )
      }
    }

  def tuple14[A, B, C, D, E, F, G, H, I, J, K, L, M, N](
    _1: Constructor[A],
    _2: Constructor[B],
    _3: Constructor[C],
    _4: Constructor[D],
    _5: Constructor[E],
    _6: Constructor[F],
    _7: Constructor[G],
    _8: Constructor[H],
    _9: Constructor[I],
    _10: Constructor[J],
    _11: Constructor[K],
    _12: Constructor[L],
    _13: Constructor[M],
    _14: Constructor[N]
  ): Constructor[(A, B, C, D, E, F, G, H, I, J, K, L, M, N)] =
    new Constructor[(A, B, C, D, E, F, G, H, I, J, K, L, M, N)] {
      val usedRegisters: RegisterOffset =
        _1.usedRegisters + _2.usedRegisters + _3.usedRegisters + _4.usedRegisters + _5.usedRegisters +
          _6.usedRegisters + _7.usedRegisters + _8.usedRegisters + _9.usedRegisters + _10.usedRegisters +
          _11.usedRegisters + _12.usedRegisters + _13.usedRegisters + _14.usedRegisters

      def construct(in: Registers, baseOffset: RegisterOffset): (A, B, C, D, E, F, G, H, I, J, K, L, M, N) = {
        var offset = baseOffset
        (
          _1.construct(in, offset),
          _2.construct(in, { offset += _1.usedRegisters; offset }),
          _3.construct(in, { offset += _2.usedRegisters; offset }),
          _4.construct(in, { offset += _3.usedRegisters; offset }),
          _5.construct(in, { offset += _4.usedRegisters; offset }),
          _6.construct(in, { offset += _5.usedRegisters; offset }),
          _7.construct(in, { offset += _6.usedRegisters; offset }),
          _8.construct(in, { offset += _7.usedRegisters; offset }),
          _9.construct(in, { offset += _8.usedRegisters; offset }),
          _10.construct(in, { offset += _9.usedRegisters; offset }),
          _11.construct(in, { offset += _10.usedRegisters; offset }),
          _12.construct(in, { offset += _11.usedRegisters; offset }),
          _13.construct(in, { offset += _12.usedRegisters; offset }),
          _14.construct(in, { offset += _13.usedRegisters; offset })
        )
      }
    }

  def tuple15[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O](
    _1: Constructor[A],
    _2: Constructor[B],
    _3: Constructor[C],
    _4: Constructor[D],
    _5: Constructor[E],
    _6: Constructor[F],
    _7: Constructor[G],
    _8: Constructor[H],
    _9: Constructor[I],
    _10: Constructor[J],
    _11: Constructor[K],
    _12: Constructor[L],
    _13: Constructor[M],
    _14: Constructor[N],
    _15: Constructor[O]
  ): Constructor[(A, B, C, D, E, F, G, H, I, J, K, L, M, N, O)] =
    new Constructor[(A, B, C, D, E, F, G, H, I, J, K, L, M, N, O)] {
      val usedRegisters: RegisterOffset =
        _1.usedRegisters + _2.usedRegisters + _3.usedRegisters + _4.usedRegisters + _5.usedRegisters +
          _6.usedRegisters + _7.usedRegisters + _8.usedRegisters + _9.usedRegisters + _10.usedRegisters +
          _11.usedRegisters + _12.usedRegisters + _13.usedRegisters + _14.usedRegisters + _15.usedRegisters

      def construct(in: Registers, baseOffset: RegisterOffset): (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O) = {
        var offset = baseOffset
        (
          _1.construct(in, offset),
          _2.construct(in, { offset += _1.usedRegisters; offset }),
          _3.construct(in, { offset += _2.usedRegisters; offset }),
          _4.construct(in, { offset += _3.usedRegisters; offset }),
          _5.construct(in, { offset += _4.usedRegisters; offset }),
          _6.construct(in, { offset += _5.usedRegisters; offset }),
          _7.construct(in, { offset += _6.usedRegisters; offset }),
          _8.construct(in, { offset += _7.usedRegisters; offset }),
          _9.construct(in, { offset += _8.usedRegisters; offset }),
          _10.construct(in, { offset += _9.usedRegisters; offset }),
          _11.construct(in, { offset += _10.usedRegisters; offset }),
          _12.construct(in, { offset += _11.usedRegisters; offset }),
          _13.construct(in, { offset += _12.usedRegisters; offset }),
          _14.construct(in, { offset += _13.usedRegisters; offset }),
          _15.construct(in, { offset += _14.usedRegisters; offset })
        )
      }
    }

  def tuple16[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P](
    _1: Constructor[A],
    _2: Constructor[B],
    _3: Constructor[C],
    _4: Constructor[D],
    _5: Constructor[E],
    _6: Constructor[F],
    _7: Constructor[G],
    _8: Constructor[H],
    _9: Constructor[I],
    _10: Constructor[J],
    _11: Constructor[K],
    _12: Constructor[L],
    _13: Constructor[M],
    _14: Constructor[N],
    _15: Constructor[O],
    _16: Constructor[P]
  ): Constructor[(A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P)] =
    new Constructor[(A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P)] {
      val usedRegisters: RegisterOffset =
        _1.usedRegisters + _2.usedRegisters + _3.usedRegisters + _4.usedRegisters + _5.usedRegisters +
          _6.usedRegisters + _7.usedRegisters + _8.usedRegisters + _9.usedRegisters + _10.usedRegisters +
          _11.usedRegisters + _12.usedRegisters + _13.usedRegisters + _14.usedRegisters + _15.usedRegisters +
          _16.usedRegisters

      def construct(in: Registers, baseOffset: RegisterOffset): (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P) = {
        var offset = baseOffset
        (
          _1.construct(in, offset),
          _2.construct(in, { offset += _1.usedRegisters; offset }),
          _3.construct(in, { offset += _2.usedRegisters; offset }),
          _4.construct(in, { offset += _3.usedRegisters; offset }),
          _5.construct(in, { offset += _4.usedRegisters; offset }),
          _6.construct(in, { offset += _5.usedRegisters; offset }),
          _7.construct(in, { offset += _6.usedRegisters; offset }),
          _8.construct(in, { offset += _7.usedRegisters; offset }),
          _9.construct(in, { offset += _8.usedRegisters; offset }),
          _10.construct(in, { offset += _9.usedRegisters; offset }),
          _11.construct(in, { offset += _10.usedRegisters; offset }),
          _12.construct(in, { offset += _11.usedRegisters; offset }),
          _13.construct(in, { offset += _12.usedRegisters; offset }),
          _14.construct(in, { offset += _13.usedRegisters; offset }),
          _15.construct(in, { offset += _14.usedRegisters; offset }),
          _16.construct(in, { offset += _15.usedRegisters; offset })
        )
      }
    }

  def tuple17[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q](
    _1: Constructor[A],
    _2: Constructor[B],
    _3: Constructor[C],
    _4: Constructor[D],
    _5: Constructor[E],
    _6: Constructor[F],
    _7: Constructor[G],
    _8: Constructor[H],
    _9: Constructor[I],
    _10: Constructor[J],
    _11: Constructor[K],
    _12: Constructor[L],
    _13: Constructor[M],
    _14: Constructor[N],
    _15: Constructor[O],
    _16: Constructor[P],
    _17: Constructor[Q]
  ): Constructor[(A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q)] =
    new Constructor[(A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q)] {
      val usedRegisters: RegisterOffset =
        _1.usedRegisters + _2.usedRegisters + _3.usedRegisters + _4.usedRegisters + _5.usedRegisters +
          _6.usedRegisters + _7.usedRegisters + _8.usedRegisters + _9.usedRegisters + _10.usedRegisters +
          _11.usedRegisters + _12.usedRegisters + _13.usedRegisters + _14.usedRegisters + _15.usedRegisters +
          _16.usedRegisters + _17.usedRegisters

      def construct(in: Registers, baseOffset: RegisterOffset): (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q) = {
        var offset = baseOffset
        (
          _1.construct(in, offset),
          _2.construct(in, { offset += _1.usedRegisters; offset }),
          _3.construct(in, { offset += _2.usedRegisters; offset }),
          _4.construct(in, { offset += _3.usedRegisters; offset }),
          _5.construct(in, { offset += _4.usedRegisters; offset }),
          _6.construct(in, { offset += _5.usedRegisters; offset }),
          _7.construct(in, { offset += _6.usedRegisters; offset }),
          _8.construct(in, { offset += _7.usedRegisters; offset }),
          _9.construct(in, { offset += _8.usedRegisters; offset }),
          _10.construct(in, { offset += _9.usedRegisters; offset }),
          _11.construct(in, { offset += _10.usedRegisters; offset }),
          _12.construct(in, { offset += _11.usedRegisters; offset }),
          _13.construct(in, { offset += _12.usedRegisters; offset }),
          _14.construct(in, { offset += _13.usedRegisters; offset }),
          _15.construct(in, { offset += _14.usedRegisters; offset }),
          _16.construct(in, { offset += _15.usedRegisters; offset }),
          _17.construct(in, { offset += _16.usedRegisters; offset })
        )
      }
    }

  def tuple18[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R](
    _1: Constructor[A],
    _2: Constructor[B],
    _3: Constructor[C],
    _4: Constructor[D],
    _5: Constructor[E],
    _6: Constructor[F],
    _7: Constructor[G],
    _8: Constructor[H],
    _9: Constructor[I],
    _10: Constructor[J],
    _11: Constructor[K],
    _12: Constructor[L],
    _13: Constructor[M],
    _14: Constructor[N],
    _15: Constructor[O],
    _16: Constructor[P],
    _17: Constructor[Q],
    _18: Constructor[R]
  ): Constructor[(A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R)] =
    new Constructor[(A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R)] {
      val usedRegisters: RegisterOffset =
        _1.usedRegisters + _2.usedRegisters + _3.usedRegisters + _4.usedRegisters + _5.usedRegisters +
          _6.usedRegisters + _7.usedRegisters + _8.usedRegisters + _9.usedRegisters + _10.usedRegisters +
          _11.usedRegisters + _12.usedRegisters + _13.usedRegisters + _14.usedRegisters + _15.usedRegisters +
          _16.usedRegisters + _17.usedRegisters + _18.usedRegisters

      def construct(
        in: Registers,
        baseOffset: RegisterOffset
      ): (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R) = {
        var offset = baseOffset
        (
          _1.construct(in, offset),
          _2.construct(in, { offset += _1.usedRegisters; offset }),
          _3.construct(in, { offset += _2.usedRegisters; offset }),
          _4.construct(in, { offset += _3.usedRegisters; offset }),
          _5.construct(in, { offset += _4.usedRegisters; offset }),
          _6.construct(in, { offset += _5.usedRegisters; offset }),
          _7.construct(in, { offset += _6.usedRegisters; offset }),
          _8.construct(in, { offset += _7.usedRegisters; offset }),
          _9.construct(in, { offset += _8.usedRegisters; offset }),
          _10.construct(in, { offset += _9.usedRegisters; offset }),
          _11.construct(in, { offset += _10.usedRegisters; offset }),
          _12.construct(in, { offset += _11.usedRegisters; offset }),
          _13.construct(in, { offset += _12.usedRegisters; offset }),
          _14.construct(in, { offset += _13.usedRegisters; offset }),
          _15.construct(in, { offset += _14.usedRegisters; offset }),
          _16.construct(in, { offset += _15.usedRegisters; offset }),
          _17.construct(in, { offset += _16.usedRegisters; offset }),
          _18.construct(in, { offset += _17.usedRegisters; offset })
        )
      }
    }

  def tuple19[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S](
    _1: Constructor[A],
    _2: Constructor[B],
    _3: Constructor[C],
    _4: Constructor[D],
    _5: Constructor[E],
    _6: Constructor[F],
    _7: Constructor[G],
    _8: Constructor[H],
    _9: Constructor[I],
    _10: Constructor[J],
    _11: Constructor[K],
    _12: Constructor[L],
    _13: Constructor[M],
    _14: Constructor[N],
    _15: Constructor[O],
    _16: Constructor[P],
    _17: Constructor[Q],
    _18: Constructor[R],
    _19: Constructor[S]
  ): Constructor[(A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S)] =
    new Constructor[(A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S)] {
      val usedRegisters: RegisterOffset =
        _1.usedRegisters + _2.usedRegisters + _3.usedRegisters + _4.usedRegisters + _5.usedRegisters +
          _6.usedRegisters + _7.usedRegisters + _8.usedRegisters + _9.usedRegisters + _10.usedRegisters +
          _11.usedRegisters + _12.usedRegisters + _13.usedRegisters + _14.usedRegisters + _15.usedRegisters +
          _16.usedRegisters + _17.usedRegisters + _18.usedRegisters + _19.usedRegisters

      def construct(
        in: Registers,
        baseOffset: RegisterOffset
      ): (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S) = {
        var offset = baseOffset
        (
          _1.construct(in, offset),
          _2.construct(in, { offset += _1.usedRegisters; offset }),
          _3.construct(in, { offset += _2.usedRegisters; offset }),
          _4.construct(in, { offset += _3.usedRegisters; offset }),
          _5.construct(in, { offset += _4.usedRegisters; offset }),
          _6.construct(in, { offset += _5.usedRegisters; offset }),
          _7.construct(in, { offset += _6.usedRegisters; offset }),
          _8.construct(in, { offset += _7.usedRegisters; offset }),
          _9.construct(in, { offset += _8.usedRegisters; offset }),
          _10.construct(in, { offset += _9.usedRegisters; offset }),
          _11.construct(in, { offset += _10.usedRegisters; offset }),
          _12.construct(in, { offset += _11.usedRegisters; offset }),
          _13.construct(in, { offset += _12.usedRegisters; offset }),
          _14.construct(in, { offset += _13.usedRegisters; offset }),
          _15.construct(in, { offset += _14.usedRegisters; offset }),
          _16.construct(in, { offset += _15.usedRegisters; offset }),
          _17.construct(in, { offset += _16.usedRegisters; offset }),
          _18.construct(in, { offset += _17.usedRegisters; offset }),
          _19.construct(in, { offset += _18.usedRegisters; offset })
        )
      }
    }

  def tuple20[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T](
    _1: Constructor[A],
    _2: Constructor[B],
    _3: Constructor[C],
    _4: Constructor[D],
    _5: Constructor[E],
    _6: Constructor[F],
    _7: Constructor[G],
    _8: Constructor[H],
    _9: Constructor[I],
    _10: Constructor[J],
    _11: Constructor[K],
    _12: Constructor[L],
    _13: Constructor[M],
    _14: Constructor[N],
    _15: Constructor[O],
    _16: Constructor[P],
    _17: Constructor[Q],
    _18: Constructor[R],
    _19: Constructor[S],
    _20: Constructor[T]
  ): Constructor[(A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T)] =
    new Constructor[(A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T)] {
      val usedRegisters: RegisterOffset =
        _1.usedRegisters + _2.usedRegisters + _3.usedRegisters + _4.usedRegisters + _5.usedRegisters +
          _6.usedRegisters + _7.usedRegisters + _8.usedRegisters + _9.usedRegisters + _10.usedRegisters +
          _11.usedRegisters + _12.usedRegisters + _13.usedRegisters + _14.usedRegisters + _15.usedRegisters +
          _16.usedRegisters + _17.usedRegisters + _18.usedRegisters + _19.usedRegisters + _20.usedRegisters

      def construct(
        in: Registers,
        baseOffset: RegisterOffset
      ): (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T) = {
        var offset = baseOffset
        (
          _1.construct(in, offset),
          _2.construct(in, { offset += _1.usedRegisters; offset }),
          _3.construct(in, { offset += _2.usedRegisters; offset }),
          _4.construct(in, { offset += _3.usedRegisters; offset }),
          _5.construct(in, { offset += _4.usedRegisters; offset }),
          _6.construct(in, { offset += _5.usedRegisters; offset }),
          _7.construct(in, { offset += _6.usedRegisters; offset }),
          _8.construct(in, { offset += _7.usedRegisters; offset }),
          _9.construct(in, { offset += _8.usedRegisters; offset }),
          _10.construct(in, { offset += _9.usedRegisters; offset }),
          _11.construct(in, { offset += _10.usedRegisters; offset }),
          _12.construct(in, { offset += _11.usedRegisters; offset }),
          _13.construct(in, { offset += _12.usedRegisters; offset }),
          _14.construct(in, { offset += _13.usedRegisters; offset }),
          _15.construct(in, { offset += _14.usedRegisters; offset }),
          _16.construct(in, { offset += _15.usedRegisters; offset }),
          _17.construct(in, { offset += _16.usedRegisters; offset }),
          _18.construct(in, { offset += _17.usedRegisters; offset }),
          _19.construct(in, { offset += _18.usedRegisters; offset }),
          _20.construct(in, { offset += _19.usedRegisters; offset })
        )
      }
    }

  def tuple21[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, U](
    _1: Constructor[A],
    _2: Constructor[B],
    _3: Constructor[C],
    _4: Constructor[D],
    _5: Constructor[E],
    _6: Constructor[F],
    _7: Constructor[G],
    _8: Constructor[H],
    _9: Constructor[I],
    _10: Constructor[J],
    _11: Constructor[K],
    _12: Constructor[L],
    _13: Constructor[M],
    _14: Constructor[N],
    _15: Constructor[O],
    _16: Constructor[P],
    _17: Constructor[Q],
    _18: Constructor[R],
    _19: Constructor[S],
    _20: Constructor[T],
    _21: Constructor[U]
  ): Constructor[(A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, U)] =
    new Constructor[(A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, U)] {
      val usedRegisters: RegisterOffset =
        _1.usedRegisters + _2.usedRegisters + _3.usedRegisters + _4.usedRegisters + _5.usedRegisters +
          _6.usedRegisters + _7.usedRegisters + _8.usedRegisters + _9.usedRegisters + _10.usedRegisters +
          _11.usedRegisters + _12.usedRegisters + _13.usedRegisters + _14.usedRegisters + _15.usedRegisters +
          _16.usedRegisters + _17.usedRegisters + _18.usedRegisters + _19.usedRegisters + _20.usedRegisters +
          _21.usedRegisters

      def construct(
        in: Registers,
        baseOffset: RegisterOffset
      ): (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, U) = {
        var offset = baseOffset
        (
          _1.construct(in, offset),
          _2.construct(in, { offset += _1.usedRegisters; offset }),
          _3.construct(in, { offset += _2.usedRegisters; offset }),
          _4.construct(in, { offset += _3.usedRegisters; offset }),
          _5.construct(in, { offset += _4.usedRegisters; offset }),
          _6.construct(in, { offset += _5.usedRegisters; offset }),
          _7.construct(in, { offset += _6.usedRegisters; offset }),
          _8.construct(in, { offset += _7.usedRegisters; offset }),
          _9.construct(in, { offset += _8.usedRegisters; offset }),
          _10.construct(in, { offset += _9.usedRegisters; offset }),
          _11.construct(in, { offset += _10.usedRegisters; offset }),
          _12.construct(in, { offset += _11.usedRegisters; offset }),
          _13.construct(in, { offset += _12.usedRegisters; offset }),
          _14.construct(in, { offset += _13.usedRegisters; offset }),
          _15.construct(in, { offset += _14.usedRegisters; offset }),
          _16.construct(in, { offset += _15.usedRegisters; offset }),
          _17.construct(in, { offset += _16.usedRegisters; offset }),
          _18.construct(in, { offset += _17.usedRegisters; offset }),
          _19.construct(in, { offset += _18.usedRegisters; offset }),
          _20.construct(in, { offset += _19.usedRegisters; offset }),
          _21.construct(in, { offset += _20.usedRegisters; offset })
        )
      }
    }

  def tuple22[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, U, V](
    _1: Constructor[A],
    _2: Constructor[B],
    _3: Constructor[C],
    _4: Constructor[D],
    _5: Constructor[E],
    _6: Constructor[F],
    _7: Constructor[G],
    _8: Constructor[H],
    _9: Constructor[I],
    _10: Constructor[J],
    _11: Constructor[K],
    _12: Constructor[L],
    _13: Constructor[M],
    _14: Constructor[N],
    _15: Constructor[O],
    _16: Constructor[P],
    _17: Constructor[Q],
    _18: Constructor[R],
    _19: Constructor[S],
    _20: Constructor[T],
    _21: Constructor[U],
    _22: Constructor[V]
  ): Constructor[(A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, U, V)] =
    new Constructor[(A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, U, V)] {
      val usedRegisters: RegisterOffset =
        _1.usedRegisters + _2.usedRegisters + _3.usedRegisters + _4.usedRegisters + _5.usedRegisters +
          _6.usedRegisters + _7.usedRegisters + _8.usedRegisters + _9.usedRegisters + _10.usedRegisters +
          _11.usedRegisters + _12.usedRegisters + _13.usedRegisters + _14.usedRegisters + _15.usedRegisters +
          _16.usedRegisters + _17.usedRegisters + _18.usedRegisters + _19.usedRegisters + _20.usedRegisters +
          _21.usedRegisters + _22.usedRegisters

      def construct(
        in: Registers,
        baseOffset: RegisterOffset
      ): (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, U, V) = {
        var offset = baseOffset
        (
          _1.construct(in, offset),
          _2.construct(in, { offset += _1.usedRegisters; offset }),
          _3.construct(in, { offset += _2.usedRegisters; offset }),
          _4.construct(in, { offset += _3.usedRegisters; offset }),
          _5.construct(in, { offset += _4.usedRegisters; offset }),
          _6.construct(in, { offset += _5.usedRegisters; offset }),
          _7.construct(in, { offset += _6.usedRegisters; offset }),
          _8.construct(in, { offset += _7.usedRegisters; offset }),
          _9.construct(in, { offset += _8.usedRegisters; offset }),
          _10.construct(in, { offset += _9.usedRegisters; offset }),
          _11.construct(in, { offset += _10.usedRegisters; offset }),
          _12.construct(in, { offset += _11.usedRegisters; offset }),
          _13.construct(in, { offset += _12.usedRegisters; offset }),
          _14.construct(in, { offset += _13.usedRegisters; offset }),
          _15.construct(in, { offset += _14.usedRegisters; offset }),
          _16.construct(in, { offset += _15.usedRegisters; offset }),
          _17.construct(in, { offset += _16.usedRegisters; offset }),
          _18.construct(in, { offset += _17.usedRegisters; offset }),
          _19.construct(in, { offset += _18.usedRegisters; offset }),
          _20.construct(in, { offset += _19.usedRegisters; offset }),
          _21.construct(in, { offset += _20.usedRegisters; offset }),
          _22.construct(in, { offset += _21.usedRegisters; offset })
        )
      }
    }
}
