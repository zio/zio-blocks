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

import zio.blocks.schema.binding.RegisterOffset.RegisterOffset

/**
 * The position of one field of a record in [[Registers]], and the kind of
 * register that holds it. `Reflect.Record#registers` computes one per field, in
 * field order, from the fields' types: a field stored as a `Boolean`, `Byte`,
 * `Short`, `Char`, `Int`, `Float`, `Long` or `Double` (a primitive node of that
 * type, or a wrapper node that `Reflect` treats as one, such as an opaque type
 * over `Int`) gets the register of that type at the next free position of the
 * byte area, a `Unit` field gets `Register.Unit`, and every other field gets a
 * `Register.Object` at the next object slot. Lenses,
 * `Reflect.Record#fromDynamicValue` and `Reflect.Record#toDynamicValue` use
 * these registers to move a field's value in and out of registers without
 * knowing its type.
 *
 * `get` and `set` take a `base` offset, which is added to the register's own
 * offset, so the same register works for a record placed anywhere in a larger
 * `Registers`.
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
 * val year      = record.registers(1) // Int(1): byte 0, after the object slot of title
 * val registers = Registers(record.usedRegisters)
 * record.deconstructor.deconstruct(registers, RegisterOffset.Zero, Book("Zionomicon", 2021))
 * val value = year.get(registers, RegisterOffset.Zero) // 2021
 * }}}
 *
 * @tparam A
 *   the type of the field
 *
 * @see
 *   [[https://zioblocks.com/reference/schema/registers/ Register System]]
 */
sealed trait Register[A] {

  /** The type of the field's value, which `get` returns and `set` takes. */
  final type Boxed = A

  /** Reads the field from `registers` at this register's offset plus `base`. */
  def get(registers: Registers, base: RegisterOffset): Boxed

  /** Writes `boxed` into `registers` at this register's offset plus `base`. */
  def set(registers: Registers, base: RegisterOffset, boxed: Boxed): Unit

  /**
   * The space the field takes, as a [[RegisterOffset]]: the size of its type in
   * the byte area, one object slot, or nothing for `Unit`.
   */
  def usedRegisters: RegisterOffset
}

/** The register kinds of record fields. */
object Register {

  /**
   * The register of a `Unit` field: it takes no space, `get` returns `()` and
   * `set` does nothing.
   */
  case object Unit extends Register[scala.Unit] {
    def get(registers: Registers, base: RegisterOffset): scala.Unit = ()

    def set(registers: Registers, base: RegisterOffset, boxed: scala.Unit): Unit = ()

    def usedRegisters: RegisterOffset = 0L
  }

  /**
   * A `Boolean` register at `offset`, stored as one byte (1 for `true`). It
   * takes 1 byte of the byte area.
   */
  case class Boolean(offset: RegisterOffset) extends Register[scala.Boolean] {
    def get(registers: Registers, base: RegisterOffset): scala.Boolean = registers.getBoolean(offset + base)

    def set(registers: Registers, base: RegisterOffset, boxed: scala.Boolean): Unit =
      registers.setBoolean(offset + base, boxed)

    def usedRegisters: RegisterOffset = RegisterOffset.incrementBooleansAndBytes(0)
  }

  /** A `Byte` register at `offset`. It takes 1 byte of the byte area. */
  case class Byte(offset: RegisterOffset) extends Register[scala.Byte] {
    def get(registers: Registers, base: RegisterOffset): scala.Byte = registers.getByte(offset + base)

    def set(registers: Registers, base: RegisterOffset, boxed: scala.Byte): Unit =
      registers.setByte(offset + base, boxed)

    def usedRegisters: RegisterOffset = RegisterOffset.incrementBooleansAndBytes(0)
  }

  /** A `Short` register at `offset`. It takes 2 bytes of the byte area. */
  case class Short(offset: RegisterOffset) extends Register[scala.Short] {
    def get(registers: Registers, base: RegisterOffset): scala.Short = registers.getShort(offset + base)

    def set(registers: Registers, base: RegisterOffset, boxed: scala.Short): Unit =
      registers.setShort(offset + base, boxed)

    def usedRegisters: RegisterOffset = RegisterOffset.incrementCharsAndShorts(0)
  }

  /** An `Int` register at `offset`. It takes 4 bytes of the byte area. */
  case class Int(offset: RegisterOffset) extends Register[scala.Int] {
    def get(registers: Registers, base: RegisterOffset): scala.Int = registers.getInt(offset + base)

    def set(registers: Registers, base: RegisterOffset, boxed: scala.Int): Unit =
      registers.setInt(offset + base, boxed)

    def usedRegisters: RegisterOffset = RegisterOffset.incrementFloatsAndInts(0)
  }

  /** A `Long` register at `offset`. It takes 8 bytes of the byte area. */
  case class Long(offset: RegisterOffset) extends Register[scala.Long] {
    def get(registers: Registers, base: RegisterOffset): scala.Long = registers.getLong(offset + base)

    def set(registers: Registers, base: RegisterOffset, boxed: scala.Long): Unit =
      registers.setLong(offset + base, boxed)

    def usedRegisters: RegisterOffset = RegisterOffset.incrementDoublesAndLongs(0)
  }

  /** A `Float` register at `offset`. It takes 4 bytes of the byte area. */
  case class Float(offset: RegisterOffset) extends Register[scala.Float] {
    def get(registers: Registers, base: RegisterOffset): scala.Float = registers.getFloat(offset + base)

    def set(registers: Registers, base: RegisterOffset, boxed: scala.Float): Unit =
      registers.setFloat(offset + base, boxed)

    def usedRegisters: RegisterOffset = RegisterOffset.incrementFloatsAndInts(0)
  }

  /** A `Double` register at `offset`. It takes 8 bytes of the byte area. */
  case class Double(offset: RegisterOffset) extends Register[scala.Double] {
    def get(registers: Registers, base: RegisterOffset): scala.Double = registers.getDouble(offset + base)

    def set(registers: Registers, base: RegisterOffset, boxed: scala.Double): Unit =
      registers.setDouble(offset + base, boxed)

    def usedRegisters: RegisterOffset = RegisterOffset.incrementDoublesAndLongs(0)
  }

  /** A `Char` register at `offset`. It takes 2 bytes of the byte area. */
  case class Char(offset: RegisterOffset) extends Register[scala.Char] {
    def get(registers: Registers, base: RegisterOffset): scala.Char = registers.getChar(offset + base)

    def set(registers: Registers, base: RegisterOffset, boxed: scala.Char): Unit =
      registers.setChar(offset + base, boxed)

    def usedRegisters: RegisterOffset = RegisterOffset.incrementCharsAndShorts(0)
  }

  /**
   * A register at `offset` for a field of a reference type, or of a primitive
   * type other than the eight above, such as `String`. It takes one object
   * slot.
   */
  case class Object[A <: AnyRef](offset: RegisterOffset) extends Register[A] {
    def get(registers: Registers, base: RegisterOffset): Boxed =
      registers.getObject(offset + base).asInstanceOf[Boxed]

    def set(registers: Registers, base: RegisterOffset, boxed: Boxed): Unit = registers.setObject(offset + base, boxed)

    def usedRegisters: RegisterOffset = RegisterOffset.incrementObjects(0)
  }
}
