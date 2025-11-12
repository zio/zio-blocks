package zio.blocks.schema.binding

import zio.blocks.schema.DynamicValue
import zio.blocks.schema.binding.RegisterOffset.RegisterOffset
import scala.collection.immutable.ArraySeq

/**
 * A binding is used to attach non-serializable Scala functions, such as
 * constructors, deconstructors, and matchers, to a reflection type.
 *
 * The `Binding` type is indexed by `T`, which is a phantom type that represents
 * the type of binding. The type `A` represents the type of the reflection type.
 *
 * So, for example, `Binding[BindingType.Record, Int]` represents a binding for
 * the reflection type `Int` that has a record binding (and therefore, both a
 * constructor and a deconstructor).
 */
sealed trait Binding[T, A] {

  /**
   * An optional generator for a default value for the type `A`.
   */
  def defaultValue: Option[() => A]

  def defaultValue(value: => A): Binding[T, A]

  /**
   * A user-defined list of example values for the type `A`, to be used for
   * testing and documentation.
   */
  def examples: Seq[A]

  def examples(value: A, values: A*): Binding[T, A]
}

object Binding {
  final case class Primitive[A](
    defaultValue: Option[() => A] = None,
    examples: collection.immutable.Seq[A] = Nil
  ) extends Binding[BindingType.Primitive, A] {
    def defaultValue(value: => A): Primitive[A] = copy(defaultValue = new Some(() => value))

    def examples(value: A, values: A*): Primitive[A] = copy(examples = value :: values.toList)
  }

  object Primitive {
    val unit: Primitive[Unit] = new Primitive[Unit]()

    val boolean: Primitive[Boolean] = new Primitive[Boolean]()

    val byte: Primitive[Byte] = new Primitive[Byte]()

    val short: Primitive[Short] = new Primitive[Short]()

    val int: Primitive[Int] = new Primitive[Int]()

    val long: Primitive[Long] = new Primitive[Long]()

    val float: Primitive[Float] = new Primitive[Float]()

    val double: Primitive[Double] = new Primitive[Double]()

    val char: Primitive[Char] = new Primitive[Char]()

    val string: Primitive[String] = new Primitive[String]()

    val bigInt: Primitive[BigInt] = new Primitive[BigInt]()

    val bigDecimal: Primitive[BigDecimal] = new Primitive[BigDecimal]()

    val dayOfWeek: Primitive[java.time.DayOfWeek] = new Primitive[java.time.DayOfWeek]()

    val duration: Primitive[java.time.Duration] = new Primitive[java.time.Duration]()

    val instant: Primitive[java.time.Instant] = new Primitive[java.time.Instant]()

    val localDate: Primitive[java.time.LocalDate] = new Primitive[java.time.LocalDate]()

    val localDateTime: Primitive[java.time.LocalDateTime] = new Primitive[java.time.LocalDateTime]()

    val localTime: Primitive[java.time.LocalTime] = new Primitive[java.time.LocalTime]()

    val month: Primitive[java.time.Month] = new Primitive[java.time.Month]()

    val monthDay: Primitive[java.time.MonthDay] = new Primitive[java.time.MonthDay]()

    val offsetDateTime: Primitive[java.time.OffsetDateTime] = new Primitive[java.time.OffsetDateTime]()

    val offsetTime: Primitive[java.time.OffsetTime] = new Primitive[java.time.OffsetTime]()

    val period: Primitive[java.time.Period] = new Primitive[java.time.Period]()

    val year: Primitive[java.time.Year] = new Primitive[java.time.Year]()

    val yearMonth: Primitive[java.time.YearMonth] = new Primitive[java.time.YearMonth]()

    val zoneId: Primitive[java.time.ZoneId] = new Primitive[java.time.ZoneId]()

    val zoneOffset: Primitive[java.time.ZoneOffset] = new Primitive[java.time.ZoneOffset]()

    val zonedDateTime: Primitive[java.time.ZonedDateTime] = new Primitive[java.time.ZonedDateTime]()

    val currency: Primitive[java.util.Currency] = new Primitive[java.util.Currency]()

    val uuid: Primitive[java.util.UUID] = new Primitive[java.util.UUID]()
  }

  final case class Record[A](
    constructor: Constructor[A],
    deconstructor: Deconstructor[A],
    defaultValue: Option[() => A] = None,
    examples: collection.immutable.Seq[A] = Nil
  ) extends Binding[BindingType.Record, A] {
    def defaultValue(value: => A): Record[A] = copy(defaultValue = new Some(() => value))

    def examples(value: A, values: A*): Record[A] = copy(examples = value :: values.toList)
  }

  object Record {
    def some[A <: AnyRef]: Record[Some[A]] = new Record(
      constructor = new Constructor[Some[A]] {
        def usedRegisters: RegisterOffset = RegisterOffset(objects = 1)

        def construct(in: Registers, baseOffset: RegisterOffset): Some[A] =
          new Some(in.getObject(baseOffset, 0).asInstanceOf[A])
      },
      deconstructor = new Deconstructor[Some[A]] {
        def usedRegisters: RegisterOffset = RegisterOffset(objects = 1)

        def deconstruct(out: Registers, baseOffset: RegisterOffset, in: Some[A]): Unit =
          out.setObject(baseOffset, 0, in.get)
      }
    )

    val someDouble: Record[Some[Double]] = new Record(
      constructor = new Constructor[Some[Double]] {
        def usedRegisters: RegisterOffset = RegisterOffset(doubles = 1)

        def construct(in: Registers, baseOffset: RegisterOffset): Some[Double] =
          new Some(in.getDouble(baseOffset, 0))
      },
      deconstructor = new Deconstructor[Some[Double]] {
        def usedRegisters: RegisterOffset = RegisterOffset(doubles = 1)

        def deconstruct(out: Registers, baseOffset: RegisterOffset, in: Some[Double]): Unit =
          out.setDouble(baseOffset, 0, in.get)
      }
    )

    val someLong: Record[Some[Long]] = new Record(
      constructor = new Constructor[Some[Long]] {
        def usedRegisters: RegisterOffset = RegisterOffset(longs = 1)

        def construct(in: Registers, baseOffset: RegisterOffset): Some[Long] =
          new Some(in.getLong(baseOffset, 0))
      },
      deconstructor = new Deconstructor[Some[Long]] {
        def usedRegisters: RegisterOffset = RegisterOffset(longs = 1)

        def deconstruct(out: Registers, baseOffset: RegisterOffset, in: Some[Long]): Unit =
          out.setLong(baseOffset, 0, in.get)
      }
    )

    val someFloat: Record[Some[Float]] = new Record(
      constructor = new Constructor[Some[Float]] {
        def usedRegisters: RegisterOffset = RegisterOffset(floats = 1)

        def construct(in: Registers, baseOffset: RegisterOffset): Some[Float] =
          new Some(in.getFloat(baseOffset, 0))
      },
      deconstructor = new Deconstructor[Some[Float]] {
        def usedRegisters: RegisterOffset = RegisterOffset(floats = 1)

        def deconstruct(out: Registers, baseOffset: RegisterOffset, in: Some[Float]): Unit =
          out.setFloat(baseOffset, 0, in.get)
      }
    )

    val someInt: Record[Some[Int]] = new Record(
      constructor = new Constructor[Some[Int]] {
        def usedRegisters: RegisterOffset = RegisterOffset(ints = 1)

        def construct(in: Registers, baseOffset: RegisterOffset): Some[Int] =
          new Some(in.getInt(baseOffset, 0))
      },
      deconstructor = new Deconstructor[Some[Int]] {
        def usedRegisters: RegisterOffset = RegisterOffset(ints = 1)

        def deconstruct(out: Registers, baseOffset: RegisterOffset, in: Some[Int]): Unit =
          out.setInt(baseOffset, 0, in.get)
      }
    )

    val someChar: Record[Some[Char]] = new Record(
      constructor = new Constructor[Some[Char]] {
        def usedRegisters: RegisterOffset = RegisterOffset(chars = 1)

        def construct(in: Registers, baseOffset: RegisterOffset): Some[Char] =
          new Some(in.getChar(baseOffset, 0))
      },
      deconstructor = new Deconstructor[Some[Char]] {
        def usedRegisters: RegisterOffset = RegisterOffset(chars = 1)

        def deconstruct(out: Registers, baseOffset: RegisterOffset, in: Some[Char]): Unit =
          out.setChar(baseOffset, 0, in.get)
      }
    )

    val someShort: Record[Some[Short]] = new Record(
      constructor = new Constructor[Some[Short]] {
        def usedRegisters: RegisterOffset = RegisterOffset(shorts = 1)

        def construct(in: Registers, baseOffset: RegisterOffset): Some[Short] =
          new Some(in.getShort(baseOffset, 0))
      },
      deconstructor = new Deconstructor[Some[Short]] {
        def usedRegisters: RegisterOffset = RegisterOffset(shorts = 1)

        def deconstruct(out: Registers, baseOffset: RegisterOffset, in: Some[Short]): Unit =
          out.setShort(baseOffset, 0, in.get)
      }
    )

    val someBoolean: Record[Some[Boolean]] = new Record(
      constructor = new Constructor[Some[Boolean]] {
        def usedRegisters: RegisterOffset = RegisterOffset(booleans = 1)

        def construct(in: Registers, baseOffset: RegisterOffset): Some[Boolean] =
          new Some(in.getBoolean(baseOffset, 0))
      },
      deconstructor = new Deconstructor[Some[Boolean]] {
        def usedRegisters: RegisterOffset = RegisterOffset(booleans = 1)

        def deconstruct(out: Registers, baseOffset: RegisterOffset, in: Some[Boolean]): Unit =
          out.setBoolean(baseOffset, 0, in.get)
      }
    )

    val someByte: Record[Some[Byte]] = new Record(
      constructor = new Constructor[Some[Byte]] {
        def usedRegisters: RegisterOffset = RegisterOffset(bytes = 1)

        def construct(in: Registers, baseOffset: RegisterOffset): Some[Byte] =
          new Some(in.getByte(baseOffset, 0))
      },
      deconstructor = new Deconstructor[Some[Byte]] {
        def usedRegisters: RegisterOffset = RegisterOffset(bytes = 1)

        def deconstruct(out: Registers, baseOffset: RegisterOffset, in: Some[Byte]): Unit =
          out.setByte(baseOffset, 0, in.get)
      }
    )

    val someUnit: Record[Some[Unit]] = new Record(
      constructor = new Constructor[Some[Unit]] {
        def usedRegisters: RegisterOffset = 0

        def construct(in: Registers, baseOffset: RegisterOffset): Some[Unit] = new Some(())
      },
      deconstructor = new Deconstructor[Some[Unit]] {
        def usedRegisters: RegisterOffset = 0

        def deconstruct(out: Registers, baseOffset: RegisterOffset, in: Some[Unit]): Unit = ()
      }
    )

    val none: Record[None.type] = new Record(
      constructor = new Constructor[None.type] {
        def usedRegisters: RegisterOffset = 0

        def construct(in: Registers, baseOffset: RegisterOffset): None.type = None
      },
      deconstructor = new Deconstructor[None.type] {
        def usedRegisters: RegisterOffset = 0

        def deconstruct(out: Registers, baseOffset: RegisterOffset, in: None.type): Unit = ()
      }
    )
  }

  final case class Variant[A](
    discriminator: Discriminator[A],
    matchers: Matchers[A],
    defaultValue: Option[() => A] = None,
    examples: collection.immutable.Seq[A] = Nil
  ) extends Binding[BindingType.Variant, A] {
    def defaultValue(value: => A): Variant[A] = copy(defaultValue = new Some(() => value))

    def examples(value: A, values: A*): Variant[A] = copy(examples = value :: values.toList)
  }

  object Variant {
    def option[A]: Variant[Option[A]] = new Variant(
      discriminator = new Discriminator[Option[A]] {
        def discriminate(a: Option[A]): Int = a match {
          case _: Some[A] @scala.unchecked => 1
          case _                           => 0
        }
      },
      matchers = Matchers(
        new Matcher[None.type] {
          override def downcastOrNull(any: Any): None.type = any match {
            case None => None
            case _    => null.asInstanceOf[None.type]
          }
        },
        new Matcher[Some[A]] {
          override def downcastOrNull(any: Any): Some[A] = any match {
            case x: Some[A] @scala.unchecked => x
            case _                           => null.asInstanceOf[Some[A]]
          }
        }
      )
    )
  }

  final case class Seq[C[_], A](
    constructor: SeqConstructor[C],
    deconstructor: SeqDeconstructor[C],
    defaultValue: Option[() => C[A]] = None,
    examples: collection.immutable.Seq[C[A]] = Nil
  ) extends Binding[BindingType.Seq[C], C[A]] {
    def defaultValue(value: => C[A]): Seq[C, A] = copy(defaultValue = new Some(() => value))

    def examples(value: C[A], values: C[A]*): Seq[C, A] = copy(examples = value :: values.toList)
  }

  object Seq {
    def set[A]: Seq[Set, A] = new Seq(SeqConstructor.setConstructor, SeqDeconstructor.setDeconstructor)

    def list[A]: Seq[List, A] = new Seq(SeqConstructor.listConstructor, SeqDeconstructor.listDeconstructor)

    def vector[A]: Seq[Vector, A] = new Seq(SeqConstructor.vectorConstructor, SeqDeconstructor.vectorDeconstructor)

    def arraySeq[A]: Seq[ArraySeq, A] =
      new Seq(SeqConstructor.arraySeqConstructor, SeqDeconstructor.arraySeqDeconstructor)
  }

  final case class Map[M[_, _], K, V](
    constructor: MapConstructor[M],
    deconstructor: MapDeconstructor[M],
    defaultValue: Option[() => M[K, V]] = None,
    examples: collection.immutable.Seq[M[K, V]] = Nil
  ) extends Binding[BindingType.Map[M], M[K, V]] {
    def defaultValue(value: => M[K, V]): Map[M, K, V] = copy(defaultValue = new Some(() => value))

    def examples(value: M[K, V], values: M[K, V]*): Map[M, K, V] = copy(examples = value :: values.toList)
  }

  object Map {
    def map[K, V]: Map[Predef.Map, K, V] = new Map(MapConstructor.map, MapDeconstructor.map)
  }

  final case class Wrapper[A, B](
    wrap: B => Either[String, A],
    unwrap: A => B,
    defaultValue: Option[() => A] = None,
    examples: collection.immutable.Seq[A] = Nil
  ) extends Binding[BindingType.Wrapper[A, B], A] {
    def defaultValue(value: => A): Wrapper[A, B] = copy(defaultValue = new Some(() => value))

    def examples(value: A, values: A*): Wrapper[A, B] = copy(examples = value :: values.toList)
  }

  final case class Dynamic(
    defaultValue: Option[() => DynamicValue] = None,
    examples: collection.immutable.Seq[DynamicValue] = Nil
  ) extends Binding[BindingType.Dynamic, DynamicValue] {
    def defaultValue(value: => DynamicValue): Dynamic = copy(defaultValue = new Some(() => value))

    def examples(value: DynamicValue, values: DynamicValue*): Dynamic = copy(examples = value :: values.toList)
  }

  implicit val bindingHasBinding: HasBinding[Binding] = new HasBinding[Binding] {
    def binding[T, A](fa: Binding[T, A]): Binding[T, A] = fa

    def updateBinding[T, A](fa: Binding[T, A], f: Binding[T, A] => Binding[T, A]): Binding[T, A] = f(fa)
  }

  implicit val bindingFromBinding: FromBinding[Binding] = new FromBinding[Binding] {
    def fromBinding[T, A](binding: Binding[T, A]): Binding[T, A] = binding
  }
}
