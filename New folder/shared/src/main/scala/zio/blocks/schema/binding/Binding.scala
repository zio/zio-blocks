package zio.blocks.schema.binding

import scala.annotation.unchecked.uncheckedVariance

import zio.blocks.chunk.Chunk
import zio.blocks.schema.DynamicValue
import zio.blocks.schema.binding.RegisterOffset.RegisterOffset

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
sealed trait Binding[T, +A]

object Binding extends BindingCompanionVersionSpecific {
  final case class Primitive[A]() extends Binding[BindingType.Primitive, A]

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
    deconstructor: Deconstructor[A]
  ) extends Binding[BindingType.Record, A]

  object Record {
    def some[A <: AnyRef]: Record[Some[A]] = new Record(
      constructor = new Constructor[Some[A]] {
        def usedRegisters: RegisterOffset = RegisterOffset(objects = 1)

        def construct(in: Registers, offset: RegisterOffset): Some[A] = new Some(in.getObject(offset).asInstanceOf[A])
      },
      deconstructor = new Deconstructor[Some[A]] {
        def usedRegisters: RegisterOffset = RegisterOffset(objects = 1)

        def deconstruct(out: Registers, offset: RegisterOffset, in: Some[A]): Unit = out.setObject(offset, in.get)
      }
    )

    val someDouble: Record[Some[Double]] = new Record(
      constructor = new Constructor[Some[Double]] {
        def usedRegisters: RegisterOffset = RegisterOffset(doubles = 1)

        def construct(in: Registers, offset: RegisterOffset): Some[Double] = new Some(in.getDouble(offset))
      },
      deconstructor = new Deconstructor[Some[Double]] {
        def usedRegisters: RegisterOffset = RegisterOffset(doubles = 1)

        def deconstruct(out: Registers, offset: RegisterOffset, in: Some[Double]): Unit = out.setDouble(offset, in.get)
      }
    )

    val someLong: Record[Some[Long]] = new Record(
      constructor = new Constructor[Some[Long]] {
        def usedRegisters: RegisterOffset = RegisterOffset(longs = 1)

        def construct(in: Registers, offset: RegisterOffset): Some[Long] = new Some(in.getLong(offset))
      },
      deconstructor = new Deconstructor[Some[Long]] {
        def usedRegisters: RegisterOffset = RegisterOffset(longs = 1)

        def deconstruct(out: Registers, offset: RegisterOffset, in: Some[Long]): Unit = out.setLong(offset, in.get)
      }
    )

    val someFloat: Record[Some[Float]] = new Record(
      constructor = new Constructor[Some[Float]] {
        def usedRegisters: RegisterOffset = RegisterOffset(floats = 1)

        def construct(in: Registers, offset: RegisterOffset): Some[Float] = new Some(in.getFloat(offset))
      },
      deconstructor = new Deconstructor[Some[Float]] {
        def usedRegisters: RegisterOffset = RegisterOffset(floats = 1)

        def deconstruct(out: Registers, offset: RegisterOffset, in: Some[Float]): Unit = out.setFloat(offset, in.get)
      }
    )

    val someInt: Record[Some[Int]] = new Record(
      constructor = new Constructor[Some[Int]] {
        def usedRegisters: RegisterOffset = RegisterOffset(ints = 1)

        def construct(in: Registers, offset: RegisterOffset): Some[Int] = new Some(in.getInt(offset))
      },
      deconstructor = new Deconstructor[Some[Int]] {
        def usedRegisters: RegisterOffset = RegisterOffset(ints = 1)

        def deconstruct(out: Registers, offset: RegisterOffset, in: Some[Int]): Unit = out.setInt(offset, in.get)
      }
    )

    val someChar: Record[Some[Char]] = new Record(
      constructor = new Constructor[Some[Char]] {
        def usedRegisters: RegisterOffset = RegisterOffset(chars = 1)

        def construct(in: Registers, offset: RegisterOffset): Some[Char] = new Some(in.getChar(offset))
      },
      deconstructor = new Deconstructor[Some[Char]] {
        def usedRegisters: RegisterOffset = RegisterOffset(chars = 1)

        def deconstruct(out: Registers, offset: RegisterOffset, in: Some[Char]): Unit = out.setChar(offset, in.get)
      }
    )

    val someShort: Record[Some[Short]] = new Record(
      constructor = new Constructor[Some[Short]] {
        def usedRegisters: RegisterOffset = RegisterOffset(shorts = 1)

        def construct(in: Registers, offset: RegisterOffset): Some[Short] = new Some(in.getShort(offset))
      },
      deconstructor = new Deconstructor[Some[Short]] {
        def usedRegisters: RegisterOffset = RegisterOffset(shorts = 1)

        def deconstruct(out: Registers, offset: RegisterOffset, in: Some[Short]): Unit = out.setShort(offset, in.get)
      }
    )

    val someBoolean: Record[Some[Boolean]] = new Record(
      constructor = new Constructor[Some[Boolean]] {
        def usedRegisters: RegisterOffset = RegisterOffset(booleans = 1)

        def construct(in: Registers, offset: RegisterOffset): Some[Boolean] = new Some(in.getBoolean(offset))
      },
      deconstructor = new Deconstructor[Some[Boolean]] {
        def usedRegisters: RegisterOffset = RegisterOffset(booleans = 1)

        def deconstruct(out: Registers, offset: RegisterOffset, in: Some[Boolean]): Unit =
          out.setBoolean(offset, in.get)
      }
    )

    val someByte: Record[Some[Byte]] = new Record(
      constructor = new Constructor[Some[Byte]] {
        def usedRegisters: RegisterOffset = RegisterOffset(bytes = 1)

        def construct(in: Registers, offset: RegisterOffset): Some[Byte] = new Some(in.getByte(offset))
      },
      deconstructor = new Deconstructor[Some[Byte]] {
        def usedRegisters: RegisterOffset = RegisterOffset(bytes = 1)

        def deconstruct(out: Registers, offset: RegisterOffset, in: Some[Byte]): Unit = out.setByte(offset, in.get)
      }
    )

    val someUnit: Record[Some[Unit]] = new Record(
      constructor = new Constructor[Some[Unit]] {
        def usedRegisters: RegisterOffset = 0L

        def construct(in: Registers, offset: RegisterOffset): Some[Unit] = new Some(())
      },
      deconstructor = new Deconstructor[Some[Unit]] {
        def usedRegisters: RegisterOffset = 0L

        def deconstruct(out: Registers, offset: RegisterOffset, in: Some[Unit]): Unit = ()
      }
    )

    val none: Record[None.type] = new Record(
      constructor = new Constructor[None.type] {
        def usedRegisters: RegisterOffset = 0L

        def construct(in: Registers, offset: RegisterOffset): None.type = None
      },
      deconstructor = new Deconstructor[None.type] {
        def usedRegisters: RegisterOffset = 0L

        def deconstruct(out: Registers, offset: RegisterOffset, in: None.type): Unit = ()
      }
    )

    def left[A, B]: Record[Left[A, B]] = new Record(
      constructor = new Constructor[Left[A, B]] {
        def usedRegisters: RegisterOffset = RegisterOffset(objects = 1)

        def construct(in: Registers, offset: RegisterOffset): Left[A, B] =
          new Left(in.getObject(offset).asInstanceOf[A])
      },
      deconstructor = new Deconstructor[Left[A, B]] {
        def usedRegisters: RegisterOffset = RegisterOffset(objects = 1)

        def deconstruct(out: Registers, offset: RegisterOffset, in: Left[A, B]): Unit =
          out.setObject(offset, in.value.asInstanceOf[AnyRef])
      }
    )

    def right[A, B]: Record[Right[A, B]] = new Record(
      constructor = new Constructor[Right[A, B]] {
        def usedRegisters: RegisterOffset = RegisterOffset(objects = 1)

        def construct(in: Registers, offset: RegisterOffset): Right[A, B] =
          new Right(in.getObject(offset).asInstanceOf[B])
      },
      deconstructor = new Deconstructor[Right[A, B]] {
        def usedRegisters: RegisterOffset = RegisterOffset(objects = 1)

        def deconstruct(out: Registers, offset: RegisterOffset, in: Right[A, B]): Unit =
          out.setObject(offset, in.value.asInstanceOf[AnyRef])
      }
    )

    def leftInt[B]: Record[Left[Int, B]] = new Record(
      constructor = new Constructor[Left[Int, B]] {
        def usedRegisters: RegisterOffset                                  = RegisterOffset(ints = 1)
        def construct(in: Registers, offset: RegisterOffset): Left[Int, B] = new Left(in.getInt(offset))
      },
      deconstructor = new Deconstructor[Left[Int, B]] {
        def usedRegisters: RegisterOffset                                               = RegisterOffset(ints = 1)
        def deconstruct(out: Registers, offset: RegisterOffset, in: Left[Int, B]): Unit = out.setInt(offset, in.value)
      }
    )

    def leftLong[B]: Record[Left[Long, B]] = new Record(
      constructor = new Constructor[Left[Long, B]] {
        def usedRegisters: RegisterOffset                                   = RegisterOffset(longs = 1)
        def construct(in: Registers, offset: RegisterOffset): Left[Long, B] = new Left(in.getLong(offset))
      },
      deconstructor = new Deconstructor[Left[Long, B]] {
        def usedRegisters: RegisterOffset                                                = RegisterOffset(longs = 1)
        def deconstruct(out: Registers, offset: RegisterOffset, in: Left[Long, B]): Unit = out.setLong(offset, in.value)
      }
    )

    def leftFloat[B]: Record[Left[Float, B]] = new Record(
      constructor = new Constructor[Left[Float, B]] {
        def usedRegisters: RegisterOffset                                    = RegisterOffset(floats = 1)
        def construct(in: Registers, offset: RegisterOffset): Left[Float, B] = new Left(in.getFloat(offset))
      },
      deconstructor = new Deconstructor[Left[Float, B]] {
        def usedRegisters: RegisterOffset                                                 = RegisterOffset(floats = 1)
        def deconstruct(out: Registers, offset: RegisterOffset, in: Left[Float, B]): Unit =
          out.setFloat(offset, in.value)
      }
    )

    def leftDouble[B]: Record[Left[Double, B]] = new Record(
      constructor = new Constructor[Left[Double, B]] {
        def usedRegisters: RegisterOffset                                     = RegisterOffset(doubles = 1)
        def construct(in: Registers, offset: RegisterOffset): Left[Double, B] = new Left(in.getDouble(offset))
      },
      deconstructor = new Deconstructor[Left[Double, B]] {
        def usedRegisters: RegisterOffset                                                  = RegisterOffset(doubles = 1)
        def deconstruct(out: Registers, offset: RegisterOffset, in: Left[Double, B]): Unit =
          out.setDouble(offset, in.value)
      }
    )

    def leftBoolean[B]: Record[Left[Boolean, B]] = new Record(
      constructor = new Constructor[Left[Boolean, B]] {
        def usedRegisters: RegisterOffset                                      = RegisterOffset(booleans = 1)
        def construct(in: Registers, offset: RegisterOffset): Left[Boolean, B] = new Left(in.getBoolean(offset))
      },
      deconstructor = new Deconstructor[Left[Boolean, B]] {
        def usedRegisters: RegisterOffset                                                   = RegisterOffset(booleans = 1)
        def deconstruct(out: Registers, offset: RegisterOffset, in: Left[Boolean, B]): Unit =
          out.setBoolean(offset, in.value)
      }
    )

    def leftByte[B]: Record[Left[Byte, B]] = new Record(
      constructor = new Constructor[Left[Byte, B]] {
        def usedRegisters: RegisterOffset                                   = RegisterOffset(bytes = 1)
        def construct(in: Registers, offset: RegisterOffset): Left[Byte, B] = new Left(in.getByte(offset))
      },
      deconstructor = new Deconstructor[Left[Byte, B]] {
        def usedRegisters: RegisterOffset                                                = RegisterOffset(bytes = 1)
        def deconstruct(out: Registers, offset: RegisterOffset, in: Left[Byte, B]): Unit = out.setByte(offset, in.value)
      }
    )

    def leftShort[B]: Record[Left[Short, B]] = new Record(
      constructor = new Constructor[Left[Short, B]] {
        def usedRegisters: RegisterOffset                                    = RegisterOffset(shorts = 1)
        def construct(in: Registers, offset: RegisterOffset): Left[Short, B] = new Left(in.getShort(offset))
      },
      deconstructor = new Deconstructor[Left[Short, B]] {
        def usedRegisters: RegisterOffset                                                 = RegisterOffset(shorts = 1)
        def deconstruct(out: Registers, offset: RegisterOffset, in: Left[Short, B]): Unit =
          out.setShort(offset, in.value)
      }
    )

    def leftChar[B]: Record[Left[Char, B]] = new Record(
      constructor = new Constructor[Left[Char, B]] {
        def usedRegisters: RegisterOffset                                   = RegisterOffset(chars = 1)
        def construct(in: Registers, offset: RegisterOffset): Left[Char, B] = new Left(in.getChar(offset))
      },
      deconstructor = new Deconstructor[Left[Char, B]] {
        def usedRegisters: RegisterOffset                                                = RegisterOffset(chars = 1)
        def deconstruct(out: Registers, offset: RegisterOffset, in: Left[Char, B]): Unit = out.setChar(offset, in.value)
      }
    )

    def leftUnit[B]: Record[Left[Unit, B]] = new Record(
      constructor = new Constructor[Left[Unit, B]] {
        def usedRegisters: RegisterOffset                                   = 0L
        def construct(in: Registers, offset: RegisterOffset): Left[Unit, B] = new Left(())
      },
      deconstructor = new Deconstructor[Left[Unit, B]] {
        def usedRegisters: RegisterOffset                                                = 0L
        def deconstruct(out: Registers, offset: RegisterOffset, in: Left[Unit, B]): Unit = ()
      }
    )

    def rightInt[A]: Record[Right[A, Int]] = new Record(
      constructor = new Constructor[Right[A, Int]] {
        def usedRegisters: RegisterOffset                                   = RegisterOffset(ints = 1)
        def construct(in: Registers, offset: RegisterOffset): Right[A, Int] = new Right(in.getInt(offset))
      },
      deconstructor = new Deconstructor[Right[A, Int]] {
        def usedRegisters: RegisterOffset                                                = RegisterOffset(ints = 1)
        def deconstruct(out: Registers, offset: RegisterOffset, in: Right[A, Int]): Unit = out.setInt(offset, in.value)
      }
    )

    def rightLong[A]: Record[Right[A, Long]] = new Record(
      constructor = new Constructor[Right[A, Long]] {
        def usedRegisters: RegisterOffset                                    = RegisterOffset(longs = 1)
        def construct(in: Registers, offset: RegisterOffset): Right[A, Long] = new Right(in.getLong(offset))
      },
      deconstructor = new Deconstructor[Right[A, Long]] {
        def usedRegisters: RegisterOffset                                                 = RegisterOffset(longs = 1)
        def deconstruct(out: Registers, offset: RegisterOffset, in: Right[A, Long]): Unit =
          out.setLong(offset, in.value)
      }
    )

    def rightFloat[A]: Record[Right[A, Float]] = new Record(
      constructor = new Constructor[Right[A, Float]] {
        def usedRegisters: RegisterOffset                                     = RegisterOffset(floats = 1)
        def construct(in: Registers, offset: RegisterOffset): Right[A, Float] = new Right(in.getFloat(offset))
      },
      deconstructor = new Deconstructor[Right[A, Float]] {
        def usedRegisters: RegisterOffset                                                  = RegisterOffset(floats = 1)
        def deconstruct(out: Registers, offset: RegisterOffset, in: Right[A, Float]): Unit =
          out.setFloat(offset, in.value)
      }
    )

    def rightDouble[A]: Record[Right[A, Double]] = new Record(
      constructor = new Constructor[Right[A, Double]] {
        def usedRegisters: RegisterOffset                                      = RegisterOffset(doubles = 1)
        def construct(in: Registers, offset: RegisterOffset): Right[A, Double] = new Right(in.getDouble(offset))
      },
      deconstructor = new Deconstructor[Right[A, Double]] {
        def usedRegisters: RegisterOffset                                                   = RegisterOffset(doubles = 1)
        def deconstruct(out: Registers, offset: RegisterOffset, in: Right[A, Double]): Unit =
          out.setDouble(offset, in.value)
      }
    )

    def rightBoolean[A]: Record[Right[A, Boolean]] = new Record(
      constructor = new Constructor[Right[A, Boolean]] {
        def usedRegisters: RegisterOffset                                       = RegisterOffset(booleans = 1)
        def construct(in: Registers, offset: RegisterOffset): Right[A, Boolean] = new Right(in.getBoolean(offset))
      },
      deconstructor = new Deconstructor[Right[A, Boolean]] {
        def usedRegisters: RegisterOffset                                                    = RegisterOffset(booleans = 1)
        def deconstruct(out: Registers, offset: RegisterOffset, in: Right[A, Boolean]): Unit =
          out.setBoolean(offset, in.value)
      }
    )

    def rightByte[A]: Record[Right[A, Byte]] = new Record(
      constructor = new Constructor[Right[A, Byte]] {
        def usedRegisters: RegisterOffset                                    = RegisterOffset(bytes = 1)
        def construct(in: Registers, offset: RegisterOffset): Right[A, Byte] = new Right(in.getByte(offset))
      },
      deconstructor = new Deconstructor[Right[A, Byte]] {
        def usedRegisters: RegisterOffset                                                 = RegisterOffset(bytes = 1)
        def deconstruct(out: Registers, offset: RegisterOffset, in: Right[A, Byte]): Unit =
          out.setByte(offset, in.value)
      }
    )

    def rightShort[A]: Record[Right[A, Short]] = new Record(
      constructor = new Constructor[Right[A, Short]] {
        def usedRegisters: RegisterOffset                                     = RegisterOffset(shorts = 1)
        def construct(in: Registers, offset: RegisterOffset): Right[A, Short] = new Right(in.getShort(offset))
      },
      deconstructor = new Deconstructor[Right[A, Short]] {
        def usedRegisters: RegisterOffset                                                  = RegisterOffset(shorts = 1)
        def deconstruct(out: Registers, offset: RegisterOffset, in: Right[A, Short]): Unit =
          out.setShort(offset, in.value)
      }
    )

    def rightChar[A]: Record[Right[A, Char]] = new Record(
      constructor = new Constructor[Right[A, Char]] {
        def usedRegisters: RegisterOffset                                    = RegisterOffset(chars = 1)
        def construct(in: Registers, offset: RegisterOffset): Right[A, Char] = new Right(in.getChar(offset))
      },
      deconstructor = new Deconstructor[Right[A, Char]] {
        def usedRegisters: RegisterOffset                                                 = RegisterOffset(chars = 1)
        def deconstruct(out: Registers, offset: RegisterOffset, in: Right[A, Char]): Unit =
          out.setChar(offset, in.value)
      }
    )

    def rightUnit[A]: Record[Right[A, Unit]] = new Record(
      constructor = new Constructor[Right[A, Unit]] {
        def usedRegisters: RegisterOffset                                    = 0L
        def construct(in: Registers, offset: RegisterOffset): Right[A, Unit] = new Right(())
      },
      deconstructor = new Deconstructor[Right[A, Unit]] {
        def usedRegisters: RegisterOffset                                                 = 0L
        def deconstruct(out: Registers, offset: RegisterOffset, in: Right[A, Unit]): Unit = ()
      }
    )
  }

  final case class Variant[A](
    discriminator: Discriminator[A],
    matchers: Matchers[A]
  ) extends Binding[BindingType.Variant, A]

  object Variant {
    def option[A]: Variant[Option[A]] = new Variant(
      discriminator = new Discriminator[Option[A]] {
        def discriminate(a: Option[A]): Int =
          if (a eq None) 0
          else 1
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

    def either[A, B]: Variant[Either[A, B]] = new Variant(
      discriminator = new Discriminator[Either[A, B]] {
        def discriminate(a: Either[A, B]): Int = if (a.isLeft) 0 else 1
      },
      matchers = Matchers(
        new Matcher[Left[A, B]] {
          override def downcastOrNull(any: Any): Left[A, B] = any match {
            case x: Left[A, B] @scala.unchecked => x
            case _                              => null.asInstanceOf[Left[A, B]]
          }
        },
        new Matcher[Right[A, B]] {
          override def downcastOrNull(any: Any): Right[A, B] = any match {
            case x: Right[A, B] @scala.unchecked => x
            case _                               => null.asInstanceOf[Right[A, B]]
          }
        }
      )
    )
  }

  final case class Seq[C[_], +A](
    constructor: SeqConstructor[C],
    deconstructor: SeqDeconstructor[C]
  ) extends Binding[BindingType.Seq[C], C[A @uncheckedVariance]]

  object Seq {
    def set[A]: Seq[Set, A] = new Seq(SeqConstructor.setConstructor, SeqDeconstructor.setDeconstructor)

    def list[A]: Seq[List, A] = new Seq(SeqConstructor.listConstructor, SeqDeconstructor.listDeconstructor)

    def vector[A]: Seq[Vector, A] = new Seq(SeqConstructor.vectorConstructor, SeqDeconstructor.vectorDeconstructor)

    def indexedSeq[A]: Seq[IndexedSeq, A] =
      new Seq(SeqConstructor.indexedSeqConstructor, SeqDeconstructor.indexedSeqDeconstructor)

    def seq[A]: Seq[collection.immutable.Seq, A] =
      new Seq(SeqConstructor.seqConstructor, SeqDeconstructor.seqDeconstructor)

    def chunk[A]: Seq[Chunk, A] = new Seq(SeqConstructor.chunkConstructor, SeqDeconstructor.chunkDeconstructor)
  }

  final case class Map[M[_, _], +K, +V](
    constructor: MapConstructor[M],
    deconstructor: MapDeconstructor[M]
  ) extends Binding[BindingType.Map[M], M[K @uncheckedVariance, V @uncheckedVariance]]

  object Map {
    def map[K, V]: Map[Predef.Map, K, V] = new Map(MapConstructor.map, MapDeconstructor.map)
  }

  final case class Wrapper[A, B](wrap: B => A, unwrap: A => B) extends Binding[BindingType.Wrapper[A, B], A]

  final case class Dynamic() extends Binding[BindingType.Dynamic, DynamicValue]

  implicit val bindingHasBinding: HasBinding[Binding] = new HasBinding[Binding] {
    def binding[T, A](fa: Binding[T, A]): Binding[T, A] = fa

    def updateBinding[T, A](fa: Binding[T, A], f: Binding[T, A] => Binding[T, A]): Binding[T, A] = f(fa)
  }

  implicit val bindingFromBinding: FromBinding[Binding] = new FromBinding[Binding] {
    def fromBinding[T, A](binding: Binding[T, A]): Binding[T, A] = binding
  }
}
