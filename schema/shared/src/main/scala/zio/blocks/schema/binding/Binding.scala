/*
 * Copyright 2024-2026 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zio.blocks.schema.binding

import zio.blocks.chunk.{Chunk, ChunkMap}
import zio.blocks.maybe.Maybe
import zio.blocks.schema.DynamicValue
import zio.blocks.schema.binding.RegisterOffset.RegisterOffset
import scala.annotation.unchecked.uncheckedVariance
import java.time._
import java.util.{Currency, UUID}

/**
 * A `Binding` holds the Scala functions that a [[zio.blocks.schema.Reflect]]
 * node needs to work with values at run time: how to build a value, how to take
 * it apart, and how to tell the cases of a sum type apart. A `Reflect`
 * describes the shape of a type as plain data; its bindings make that
 * description usable.
 *
 * A `Reflect` node keeps its binding in its metadata `F[T, A]`. A
 * `Reflect.Bound` node, whose `F` is `Binding`, can construct and deconstruct
 * values, and every [[zio.blocks.schema.Schema]] wraps one. A `Reflect.Unbound`
 * node carries a [[NoBinding]] instead: it holds no functions, so it can be
 * serialized, and `DynamicSchema#rebind` gets its bindings back from a
 * [[BindingResolver]].
 *
 * There is one binding kind per node kind (a `Reflect.Deferred` node uses the
 * binding of the node it defers to), and the phantom type `T` names it (see
 * [[BindingType]]):
 *   - [[Binding.Record]]: a [[Constructor]] and a [[Deconstructor]] that move a
 *     record's fields in and out of [[Registers]].
 *   - [[Binding.Variant]]: a [[Discriminator]] that returns the case index of a
 *     value, and [[Matchers]] that downcast a value to each case.
 *   - [[Binding.Seq]]: a [[SeqConstructor]] and a [[SeqDeconstructor]] for a
 *     collection type constructor.
 *   - [[Binding.Map]]: a [[MapConstructor]] and a [[MapDeconstructor]] for a
 *     map type constructor.
 *   - [[Binding.Primitive]]: a marker without functions.
 *   - [[Binding.Wrapper]]: `wrap` and `unwrap` functions between a type and the
 *     type it wraps.
 *   - [[Binding.Dynamic]]: a marker without functions, for a node that holds a
 *     [[zio.blocks.schema.DynamicValue]].
 *
 * `Schema.derived` creates the bindings of a whole schema, and `Binding.of`
 * derives the binding of one type. The example takes the record binding of a
 * derived schema and moves a value through registers:
 *
 * {{{
 * import zio.blocks.schema.binding._
 *
 * case class Book(title: String, year: Int)
 * object Book {
 *   implicit val schema: Schema[Book] = Schema.derived
 * }
 *
 * val record    = Book.schema.reflect.asRecord.get
 * val registers = Registers(record.usedRegisters)
 * record.deconstructor.deconstruct(registers, RegisterOffset.Zero, Book("Zionomicon", 2021))
 * val title = registers.getObject(RegisterOffset.Zero)                     // "Zionomicon"
 * val book  = record.constructor.construct(registers, RegisterOffset.Zero) // Book("Zionomicon", 2021)
 * }}}
 *
 * @tparam T
 *   the binding kind, one of the [[BindingType]] members
 * @tparam A
 *   the type whose values the binding works with
 *
 * @see
 *   [[https://zioblocks.com/reference/schema/binding/ Binding]]
 */
sealed trait Binding[T, +A]

/**
 * The binding kinds, ready-made bindings for standard types, and `Binding.of`,
 * which derives a binding at compile time.
 */
object Binding extends BindingCompanionVersionSpecific {

  /**
   * The binding of a primitive node. It is a marker without data: a primitive
   * value is read and written as it is, so there is nothing to construct. All
   * instances are equal. The companion object has one for each of the 30
   * primitive types.
   */
  final case class Primitive[A]() extends Binding[BindingType.Primitive, A]

  /** The primitive bindings, one per primitive type. */
  object Primitive {

    /** The binding of `Unit`. */
    val unit: Primitive[Unit] = new Primitive[Unit]()

    /** The binding of `Boolean`. */
    val boolean: Primitive[Boolean] = new Primitive[Boolean]()

    /** The binding of `Byte`. */
    val byte: Primitive[Byte] = new Primitive[Byte]()

    /** The binding of `Short`. */
    val short: Primitive[Short] = new Primitive[Short]()

    /** The binding of `Int`. */
    val int: Primitive[Int] = new Primitive[Int]()

    /** The binding of `Long`. */
    val long: Primitive[Long] = new Primitive[Long]()

    /** The binding of `Float`. */
    val float: Primitive[Float] = new Primitive[Float]()

    /** The binding of `Double`. */
    val double: Primitive[Double] = new Primitive[Double]()

    /** The binding of `Char`. */
    val char: Primitive[Char] = new Primitive[Char]()

    /** The binding of `String`. */
    val string: Primitive[String] = new Primitive[String]()

    /** The binding of `BigInt`. */
    val bigInt: Primitive[BigInt] = new Primitive[BigInt]()

    /** The binding of `BigDecimal`. */
    val bigDecimal: Primitive[BigDecimal] = new Primitive[BigDecimal]()

    /** The binding of `java.time.DayOfWeek`. */
    val dayOfWeek: Primitive[DayOfWeek] = new Primitive[DayOfWeek]()

    /** The binding of `java.time.Duration`. */
    val duration: Primitive[Duration] = new Primitive[Duration]()

    /** The binding of `java.time.Instant`. */
    val instant: Primitive[Instant] = new Primitive[Instant]()

    /** The binding of `java.time.LocalDate`. */
    val localDate: Primitive[LocalDate] = new Primitive[LocalDate]()

    /** The binding of `java.time.LocalDateTime`. */
    val localDateTime: Primitive[LocalDateTime] = new Primitive[LocalDateTime]()

    /** The binding of `java.time.LocalTime`. */
    val localTime: Primitive[LocalTime] = new Primitive[LocalTime]()

    /** The binding of `java.time.Month`. */
    val month: Primitive[Month] = new Primitive[Month]()

    /** The binding of `java.time.MonthDay`. */
    val monthDay: Primitive[MonthDay] = new Primitive[MonthDay]()

    /** The binding of `java.time.OffsetDateTime`. */
    val offsetDateTime: Primitive[OffsetDateTime] = new Primitive[OffsetDateTime]()

    /** The binding of `java.time.OffsetTime`. */
    val offsetTime: Primitive[OffsetTime] = new Primitive[OffsetTime]()

    /** The binding of `java.time.Period`. */
    val period: Primitive[Period] = new Primitive[Period]()

    /** The binding of `java.time.Year`. */
    val year: Primitive[Year] = new Primitive[Year]()

    /** The binding of `java.time.YearMonth`. */
    val yearMonth: Primitive[YearMonth] = new Primitive[YearMonth]()

    /** The binding of `java.time.ZoneId`. */
    val zoneId: Primitive[ZoneId] = new Primitive[ZoneId]()

    /** The binding of `java.time.ZoneOffset`. */
    val zoneOffset: Primitive[ZoneOffset] = new Primitive[ZoneOffset]()

    /** The binding of `java.time.ZonedDateTime`. */
    val zonedDateTime: Primitive[ZonedDateTime] = new Primitive[ZonedDateTime]()

    /** The binding of `java.util.Currency`. */
    val currency: Primitive[Currency] = new Primitive[Currency]()

    /** The binding of `java.util.UUID`. */
    val uuid: Primitive[UUID] = new Primitive[UUID]()
  }

  /**
   * The binding of a record node, such as a case class. `constructor` builds a
   * value from [[Registers]], and `deconstructor` writes a value's fields into
   * them.
   *
   * Both must use the layout that `Reflect.Record#registers` computes from the
   * node's fields. In field order, a field stored as a `Boolean`, `Byte`,
   * `Short`, `Char`, `Int`, `Float`, `Long` or `Double` takes the next position
   * of its size in the byte area, a `Unit` field takes no space, and every
   * other field takes the next object slot (see [[Register]]).
   * `Reflect.Record#fromDynamicValue` and `#toDynamicValue` move each field
   * through that layout, so a binding with a different layout reads the wrong
   * registers.
   */
  final case class Record[A](
    constructor: Constructor[A],
    deconstructor: Deconstructor[A]
  ) extends Binding[BindingType.Record, A]

  /** Record bindings for `Option`, `Either` and `Maybe` cases. */
  object Record {

    /**
     * The binding of `Some[A]` for a reference type `A`: the value sits in one
     * object register.
     */
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

    /**
     * The binding of `Some[Double]`: the value sits in one `Double` register,
     * unboxed.
     */
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

    /**
     * The binding of `Some[Long]`: the value sits in one `Long` register,
     * unboxed.
     */
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

    /**
     * The binding of `Some[Float]`: the value sits in one `Float` register,
     * unboxed.
     */
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

    /**
     * The binding of `Some[Int]`: the value sits in one `Int` register,
     * unboxed.
     */
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

    /**
     * The binding of `Some[Char]`: the value sits in one `Char` register,
     * unboxed.
     */
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

    /**
     * The binding of `Some[Short]`: the value sits in one `Short` register,
     * unboxed.
     */
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

    /**
     * The binding of `Some[Boolean]`: the value sits in one `Boolean` register,
     * unboxed.
     */
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

    /**
     * The binding of `Some[Byte]`: the value sits in one `Byte` register,
     * unboxed.
     */
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

    /** The binding of `Some[Unit]`: it uses no registers. */
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

    /**
     * The binding of `None`: it uses no registers and always constructs `None`.
     */
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

    /**
     * The binding of the absent case of a `Maybe`, used by `Reflect.maybe`: it
     * uses no registers and constructs `null`.
     */
    val absent: Record[AnyRef] = new Record(
      constructor = new Constructor[AnyRef] {
        def usedRegisters: RegisterOffset = 0L

        def construct(in: Registers, offset: RegisterOffset): AnyRef = null
      },
      deconstructor = new Deconstructor[AnyRef] {
        def usedRegisters: RegisterOffset = 0L

        def deconstruct(out: Registers, offset: RegisterOffset, in: AnyRef): Unit = ()
      }
    )

    /**
     * The binding of the present case of a `Maybe` of a reference type: the
     * value sits in one object register, and `construct` returns it as it is.
     */
    def present[A <: AnyRef]: Record[AnyRef] = new Record(
      constructor = new Constructor[AnyRef] {
        def usedRegisters: RegisterOffset = RegisterOffset(objects = 1)

        def construct(in: Registers, offset: RegisterOffset): AnyRef = in.getObject(offset)
      },
      deconstructor = new Deconstructor[AnyRef] {
        def usedRegisters: RegisterOffset = RegisterOffset(objects = 1)

        def deconstruct(out: Registers, offset: RegisterOffset, in: AnyRef): Unit = out.setObject(offset, in)
      }
    )

    /**
     * The binding of the present case of a `Maybe[Double]`: the value sits in
     * one `Double` register, and `construct` returns it boxed as a
     * `java.lang.Double`.
     */
    val presentDouble: Record[AnyRef] = new Record(
      constructor = new Constructor[AnyRef] {
        def usedRegisters: RegisterOffset = RegisterOffset(doubles = 1)

        def construct(in: Registers, offset: RegisterOffset): AnyRef =
          java.lang.Double.valueOf(in.getDouble(offset))
      },
      deconstructor = new Deconstructor[AnyRef] {
        def usedRegisters: RegisterOffset = RegisterOffset(doubles = 1)

        def deconstruct(out: Registers, offset: RegisterOffset, in: AnyRef): Unit =
          out.setDouble(offset, in.asInstanceOf[java.lang.Double].doubleValue())
      }
    )

    /**
     * The binding of the present case of a `Maybe[Long]`: the value sits in one
     * `Long` register, and `construct` returns it boxed as a `java.lang.Long`.
     */
    val presentLong: Record[AnyRef] = new Record(
      constructor = new Constructor[AnyRef] {
        def usedRegisters: RegisterOffset = RegisterOffset(longs = 1)

        def construct(in: Registers, offset: RegisterOffset): AnyRef =
          java.lang.Long.valueOf(in.getLong(offset))
      },
      deconstructor = new Deconstructor[AnyRef] {
        def usedRegisters: RegisterOffset = RegisterOffset(longs = 1)

        def deconstruct(out: Registers, offset: RegisterOffset, in: AnyRef): Unit =
          out.setLong(offset, in.asInstanceOf[java.lang.Long].longValue())
      }
    )

    /**
     * The binding of the present case of a `Maybe[Float]`: the value sits in
     * one `Float` register, and `construct` returns it boxed as a
     * `java.lang.Float`.
     */
    val presentFloat: Record[AnyRef] = new Record(
      constructor = new Constructor[AnyRef] {
        def usedRegisters: RegisterOffset = RegisterOffset(floats = 1)

        def construct(in: Registers, offset: RegisterOffset): AnyRef =
          java.lang.Float.valueOf(in.getFloat(offset))
      },
      deconstructor = new Deconstructor[AnyRef] {
        def usedRegisters: RegisterOffset = RegisterOffset(floats = 1)

        def deconstruct(out: Registers, offset: RegisterOffset, in: AnyRef): Unit =
          out.setFloat(offset, in.asInstanceOf[java.lang.Float].floatValue())
      }
    )

    /**
     * The binding of the present case of a `Maybe[Int]`: the value sits in one
     * `Int` register, and `construct` returns it boxed as a
     * `java.lang.Integer`.
     */
    val presentInt: Record[AnyRef] = new Record(
      constructor = new Constructor[AnyRef] {
        def usedRegisters: RegisterOffset = RegisterOffset(ints = 1)

        def construct(in: Registers, offset: RegisterOffset): AnyRef =
          java.lang.Integer.valueOf(in.getInt(offset))
      },
      deconstructor = new Deconstructor[AnyRef] {
        def usedRegisters: RegisterOffset = RegisterOffset(ints = 1)

        def deconstruct(out: Registers, offset: RegisterOffset, in: AnyRef): Unit =
          out.setInt(offset, in.asInstanceOf[java.lang.Integer].intValue())
      }
    )

    /**
     * The binding of the present case of a `Maybe[Char]`: the value sits in one
     * `Char` register, and `construct` returns it boxed as a
     * `java.lang.Character`.
     */
    val presentChar: Record[AnyRef] = new Record(
      constructor = new Constructor[AnyRef] {
        def usedRegisters: RegisterOffset = RegisterOffset(chars = 1)

        def construct(in: Registers, offset: RegisterOffset): AnyRef =
          java.lang.Character.valueOf(in.getChar(offset))
      },
      deconstructor = new Deconstructor[AnyRef] {
        def usedRegisters: RegisterOffset = RegisterOffset(chars = 1)

        def deconstruct(out: Registers, offset: RegisterOffset, in: AnyRef): Unit =
          out.setChar(offset, in.asInstanceOf[java.lang.Character].charValue())
      }
    )

    /**
     * The binding of the present case of a `Maybe[Short]`: the value sits in
     * one `Short` register, and `construct` returns it boxed as a
     * `java.lang.Short`.
     */
    val presentShort: Record[AnyRef] = new Record(
      constructor = new Constructor[AnyRef] {
        def usedRegisters: RegisterOffset = RegisterOffset(shorts = 1)

        def construct(in: Registers, offset: RegisterOffset): AnyRef =
          java.lang.Short.valueOf(in.getShort(offset))
      },
      deconstructor = new Deconstructor[AnyRef] {
        def usedRegisters: RegisterOffset = RegisterOffset(shorts = 1)

        def deconstruct(out: Registers, offset: RegisterOffset, in: AnyRef): Unit =
          out.setShort(offset, in.asInstanceOf[java.lang.Short].shortValue())
      }
    )

    /**
     * The binding of the present case of a `Maybe[Boolean]`: the value sits in
     * one `Boolean` register, and `construct` returns it boxed as a
     * `java.lang.Boolean`.
     */
    val presentBoolean: Record[AnyRef] = new Record(
      constructor = new Constructor[AnyRef] {
        def usedRegisters: RegisterOffset = RegisterOffset(booleans = 1)

        def construct(in: Registers, offset: RegisterOffset): AnyRef =
          java.lang.Boolean.valueOf(in.getBoolean(offset))
      },
      deconstructor = new Deconstructor[AnyRef] {
        def usedRegisters: RegisterOffset = RegisterOffset(booleans = 1)

        def deconstruct(out: Registers, offset: RegisterOffset, in: AnyRef): Unit =
          out.setBoolean(offset, in.asInstanceOf[java.lang.Boolean].booleanValue())
      }
    )

    /**
     * The binding of the present case of a `Maybe[Byte]`: the value sits in one
     * `Byte` register, and `construct` returns it boxed as a `java.lang.Byte`.
     */
    val presentByte: Record[AnyRef] = new Record(
      constructor = new Constructor[AnyRef] {
        def usedRegisters: RegisterOffset = RegisterOffset(bytes = 1)

        def construct(in: Registers, offset: RegisterOffset): AnyRef =
          java.lang.Byte.valueOf(in.getByte(offset))
      },
      deconstructor = new Deconstructor[AnyRef] {
        def usedRegisters: RegisterOffset = RegisterOffset(bytes = 1)

        def deconstruct(out: Registers, offset: RegisterOffset, in: AnyRef): Unit =
          out.setByte(offset, in.asInstanceOf[java.lang.Byte].byteValue())
      }
    )

    /**
     * The binding of the present case of a `Maybe[Unit]`: it uses no registers
     * and constructs the boxed unit value.
     */
    val presentUnit: Record[AnyRef] = new Record(
      constructor = new Constructor[AnyRef] {
        def usedRegisters: RegisterOffset = 0L

        def construct(in: Registers, offset: RegisterOffset): AnyRef =
          ().asInstanceOf[AnyRef]
      },
      deconstructor = new Deconstructor[AnyRef] {
        def usedRegisters: RegisterOffset = 0L

        def deconstruct(out: Registers, offset: RegisterOffset, in: AnyRef): Unit = ()
      }
    )

    /**
     * The binding of `Left[A, B]`: the value sits in one object register, so a
     * primitive value is boxed. `leftInt` and its siblings keep a primitive
     * unboxed.
     */
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

    /**
     * The binding of `Right[A, B]`: the value sits in one object register, so a
     * primitive value is boxed. `rightInt` and its siblings keep a primitive
     * unboxed.
     */
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

    /** Picks the `Left` binding that matches the register type of the value. */
    private[schema] def left[A, B](register: Register[A]): Record[Left[A, B]] =
      (register match {
        case _: Register.Int       => leftInt[B]
        case _: Register.Long      => leftLong[B]
        case _: Register.Float     => leftFloat[B]
        case _: Register.Double    => leftDouble[B]
        case _: Register.Boolean   => leftBoolean[B]
        case _: Register.Byte      => leftByte[B]
        case _: Register.Short     => leftShort[B]
        case _: Register.Char      => leftChar[B]
        case Register.Unit         => leftUnit[B]
        case _: Register.Object[?] => left[A, B]
      }).asInstanceOf[Record[Left[A, B]]]

    /**
     * Picks the `Right` binding that matches the register type of the value.
     */
    private[schema] def right[A, B](register: Register[B]): Record[Right[A, B]] =
      (register match {
        case _: Register.Int       => rightInt[A]
        case _: Register.Long      => rightLong[A]
        case _: Register.Float     => rightFloat[A]
        case _: Register.Double    => rightDouble[A]
        case _: Register.Boolean   => rightBoolean[A]
        case _: Register.Byte      => rightByte[A]
        case _: Register.Short     => rightShort[A]
        case _: Register.Char      => rightChar[A]
        case Register.Unit         => rightUnit[A]
        case _: Register.Object[?] => right[A, B]
      }).asInstanceOf[Record[Right[A, B]]]

    /**
     * The binding of `Left[Int, B]`: the value sits in one `Int` register,
     * unboxed.
     */
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

    /**
     * The binding of `Left[Long, B]`: the value sits in one `Long` register,
     * unboxed.
     */
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

    /**
     * The binding of `Left[Float, B]`: the value sits in one `Float` register,
     * unboxed.
     */
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

    /**
     * The binding of `Left[Double, B]`: the value sits in one `Double`
     * register, unboxed.
     */
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

    /**
     * The binding of `Left[Boolean, B]`: the value sits in one `Boolean`
     * register, unboxed.
     */
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

    /**
     * The binding of `Left[Byte, B]`: the value sits in one `Byte` register,
     * unboxed.
     */
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

    /**
     * The binding of `Left[Short, B]`: the value sits in one `Short` register,
     * unboxed.
     */
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

    /**
     * The binding of `Left[Char, B]`: the value sits in one `Char` register,
     * unboxed.
     */
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

    /** The binding of `Left[Unit, B]`: it uses no registers. */
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

    /**
     * The binding of `Right[A, Int]`: the value sits in one `Int` register,
     * unboxed.
     */
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

    /**
     * The binding of `Right[A, Long]`: the value sits in one `Long` register,
     * unboxed.
     */
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

    /**
     * The binding of `Right[A, Float]`: the value sits in one `Float` register,
     * unboxed.
     */
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

    /**
     * The binding of `Right[A, Double]`: the value sits in one `Double`
     * register, unboxed.
     */
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

    /**
     * The binding of `Right[A, Boolean]`: the value sits in one `Boolean`
     * register, unboxed.
     */
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

    /**
     * The binding of `Right[A, Byte]`: the value sits in one `Byte` register,
     * unboxed.
     */
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

    /**
     * The binding of `Right[A, Short]`: the value sits in one `Short` register,
     * unboxed.
     */
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

    /**
     * The binding of `Right[A, Char]`: the value sits in one `Char` register,
     * unboxed.
     */
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

    /** The binding of `Right[A, Unit]`: it uses no registers. */
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

  /**
   * The binding of a variant node, such as a sealed trait or an enum.
   * `discriminator` returns the index of the case that a value belongs to, and
   * `matchers` holds one [[Matcher]] per case. Both follow the order of the
   * node's cases: case `i` of the node is matched by `matchers(i)`, and its
   * values are discriminated as `i`.
   */
  final case class Variant[A](
    discriminator: Discriminator[A],
    matchers: Matchers[A]
  ) extends Binding[BindingType.Variant, A]

  /** Variant bindings for `Option`, `Either` and `Maybe`. */
  object Variant {

    /** The binding of `Option[A]`: `None` is case 0 and `Some` is case 1. */
    def option[A]: Variant[Option[A]] = new Variant(
      discriminator = new Discriminator[Option[A]] {
        def discriminate(a: Option[A]): Int =
          if (a eq None) 0
          else 1
      },
      matchers = Matchers(
        new Matcher[None.type] {
          override def downcastOrNull(any: Any): None.type = any match {
            case _: None.type => None
            case _            => null.asInstanceOf[None.type]
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

    /**
     * The binding of `Either[A, B]`: `Left` is case 0 and `Right` is case 1.
     *
     * @tparam A
     *   the left value type
     * @tparam B
     *   the right value type
     */
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

    private[this] val absentSentinel: AnyRef = new AnyRef {}

    /**
     * The binding of a `Maybe`, used by `Reflect.maybe`: an absent value is
     * case 0 and a present one is case 1. For an absent value the case 0
     * matcher returns a private non-null marker object, since `null` would mean
     * no match; for a present value the case 1 matcher returns the value inside
     * the `Maybe`. Each returns `null` otherwise.
     */
    def maybe[A]: Variant[AnyRef] = new Variant(
      discriminator = new Discriminator[AnyRef] {
        def discriminate(a: AnyRef): Int =
          if (Maybe.unsafeIsAbsent(a.asInstanceOf[Maybe[Any]])) 0
          else 1
      },
      matchers = Matchers(
        new Matcher[AnyRef] {
          override def downcastOrNull(any: Any): AnyRef =
            if (Maybe.unsafeIsAbsent(any.asInstanceOf[Maybe[Any]])) absentSentinel
            else null
        },
        new Matcher[AnyRef] {
          override def downcastOrNull(any: Any): AnyRef =
            if (!Maybe.unsafeIsAbsent(any.asInstanceOf[Maybe[Any]]))
              Maybe.unsafeGet(any.asInstanceOf[Maybe[Any]]).asInstanceOf[AnyRef]
            else null
        }
      )
    )
  }

  /**
   * The binding of a sequence node for the collection type constructor `C`:
   * `constructor` builds a `C[A]` element by element, and `deconstructor` reads
   * one back as its elements and size. Neither depends on the element type `A`,
   * so one binding serves every element type, which is why [[BindingResolver]]
   * stores sequence bindings by `C` alone.
   */
  final case class Seq[C[_], +A](
    constructor: SeqConstructor[C],
    deconstructor: SeqDeconstructor[C]
  ) extends Binding[BindingType.Seq[C], C[A @uncheckedVariance]]

  /** Sequence bindings for the standard collections. */
  object Seq {

    /** The binding of `Set`. */
    def set[A]: Seq[Set, A] = new Seq(SeqConstructor.setConstructor, SeqDeconstructor.setDeconstructor)

    /** The binding of `List`. */
    def list[A]: Seq[List, A] = new Seq(SeqConstructor.listConstructor, SeqDeconstructor.listDeconstructor)

    /** The binding of `Vector`. */
    def vector[A]: Seq[Vector, A] = new Seq(SeqConstructor.vectorConstructor, SeqDeconstructor.vectorDeconstructor)

    /** The binding of `IndexedSeq`, which builds a `Vector`. */
    def indexedSeq[A]: Seq[IndexedSeq, A] =
      new Seq(SeqConstructor.indexedSeqConstructor, SeqDeconstructor.indexedSeqDeconstructor)

    /**
     * The binding of `scala.collection.immutable.Seq`, which builds a `List`.
     */
    def seq[A]: Seq[collection.immutable.Seq, A] =
      new Seq(SeqConstructor.seqConstructor, SeqDeconstructor.seqDeconstructor)

    /** The binding of `Chunk`. */
    def chunk[A]: Seq[Chunk, A] = new Seq(SeqConstructor.chunkConstructor, SeqDeconstructor.chunkDeconstructor)
  }

  /**
   * The binding of a map node for the map type constructor `M`: `constructor`
   * builds an `M[K, V]` entry by entry, and `deconstructor` reads one back as
   * its entries. Neither depends on `K` or `V`, so one binding serves every key
   * and value type.
   */
  final case class Map[M[_, _], +K, +V](
    constructor: MapConstructor[M],
    deconstructor: MapDeconstructor[M]
  ) extends Binding[BindingType.Map[M], M[K @uncheckedVariance, V @uncheckedVariance]]

  /** Map bindings for the standard maps. */
  object Map {

    /** The binding of `ChunkMap`. */
    def chunkMap[K, V]: Map[ChunkMap, K, V] = new Map(MapConstructor.chunkMap, MapDeconstructor.chunkMap)

    /** The binding of the immutable `Map`. */
    def map[K, V]: Map[Predef.Map, K, V] = new Map(MapConstructor.map, MapDeconstructor.map)
  }

  /**
   * The binding of a wrapper node, which stores a value of type `A` as the
   * value of type `B` inside it, such as a newtype or the result of
   * `Schema#transform`. `wrap` builds an `A` from a `B`, and `unwrap` returns
   * the `B` inside an `A`.
   *
   * `wrap` rejects a `B` by throwing. `Reflect#fromDynamicValue` and the JSON
   * codec catch a non-fatal exception from it and report it as a decoding
   * error.
   *
   * @tparam A
   *   the wrapper type
   * @tparam B
   *   the wrapped type
   */
  final case class Wrapper[A, B](wrap: B => A, unwrap: A => B) extends Binding[BindingType.Wrapper[A, B], A]

  /**
   * The binding of a dynamic node, which holds a
   * [[zio.blocks.schema.DynamicValue]] as it is. It is a marker without data,
   * and all instances are equal.
   */
  final case class Dynamic() extends Binding[BindingType.Dynamic, DynamicValue]

  /**
   * The [[HasBinding]] instance for `Binding` itself: `binding` returns its
   * argument, and `updateBinding` applies the function to it.
   */
  implicit val bindingHasBinding: HasBinding[Binding] = new HasBinding[Binding] {
    def binding[T, A](fa: Binding[T, A]): Binding[T, A] = fa

    def updateBinding[T, A](fa: Binding[T, A], f: Binding[T, A] => Binding[T, A]): Binding[T, A] = f(fa)
  }

  /**
   * The [[FromBinding]] instance for `Binding` itself, which returns the
   * binding unchanged.
   */
  implicit val bindingFromBinding: FromBinding[Binding] = new FromBinding[Binding] {
    def fromBinding[T, A](binding: Binding[T, A]): Binding[T, A] = binding
  }
}
