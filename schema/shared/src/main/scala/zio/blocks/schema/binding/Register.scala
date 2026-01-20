/*
 * Copyright 2023 ZIO Blocks Maintainers
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

sealed trait Register[A] {
  final type Boxed = A

  def get(registers: Registers, base: RegisterOffset): Boxed

  def set(registers: Registers, base: RegisterOffset, boxed: Boxed): Unit

  def usedRegisters: RegisterOffset
}

object Register {
  case object Unit extends Register[scala.Unit] {
    def get(registers: Registers, base: RegisterOffset): scala.Unit = ()

    def set(registers: Registers, base: RegisterOffset, boxed: scala.Unit): Unit = ()

    def usedRegisters: RegisterOffset = 0L
  }

  case class Boolean(offset: RegisterOffset) extends Register[scala.Boolean] {
    def get(registers: Registers, base: RegisterOffset): scala.Boolean = registers.getBoolean(offset + base)

    def set(registers: Registers, base: RegisterOffset, boxed: scala.Boolean): Unit =
      registers.setBoolean(offset + base, boxed)

    def usedRegisters: RegisterOffset = RegisterOffset.incrementBooleansAndBytes(0)
  }

  case class Byte(offset: RegisterOffset) extends Register[scala.Byte] {
    def get(registers: Registers, base: RegisterOffset): scala.Byte = registers.getByte(offset + base)

    def set(registers: Registers, base: RegisterOffset, boxed: scala.Byte): Unit =
      registers.setByte(offset + base, boxed)

    def usedRegisters: RegisterOffset = RegisterOffset.incrementBooleansAndBytes(0)
  }

  case class Short(offset: RegisterOffset) extends Register[scala.Short] {
    def get(registers: Registers, base: RegisterOffset): scala.Short = registers.getShort(offset + base)

    def set(registers: Registers, base: RegisterOffset, boxed: scala.Short): Unit =
      registers.setShort(offset + base, boxed)

    def usedRegisters: RegisterOffset = RegisterOffset.incrementCharsAndShorts(0)
  }

  case class Int(offset: RegisterOffset) extends Register[scala.Int] {
    def get(registers: Registers, base: RegisterOffset): scala.Int = registers.getInt(offset + base)

    def set(registers: Registers, base: RegisterOffset, boxed: scala.Int): Unit =
      registers.setInt(offset + base, boxed)

    def usedRegisters: RegisterOffset = RegisterOffset.incrementFloatsAndInts(0)
  }

  case class Long(offset: RegisterOffset) extends Register[scala.Long] {
    def get(registers: Registers, base: RegisterOffset): scala.Long = registers.getLong(offset + base)

    def set(registers: Registers, base: RegisterOffset, boxed: scala.Long): Unit =
      registers.setLong(offset + base, boxed)

    def usedRegisters: RegisterOffset = RegisterOffset.incrementDoublesAndLongs(0)
  }

  case class Float(offset: RegisterOffset) extends Register[scala.Float] {
    def get(registers: Registers, base: RegisterOffset): scala.Float = registers.getFloat(offset + base)

    def set(registers: Registers, base: RegisterOffset, boxed: scala.Float): Unit =
      registers.setFloat(offset + base, boxed)

    def usedRegisters: RegisterOffset = RegisterOffset.incrementFloatsAndInts(0)
  }

  case class Double(offset: RegisterOffset) extends Register[scala.Double] {
    def get(registers: Registers, base: RegisterOffset): scala.Double = registers.getDouble(offset + base)

    def set(registers: Registers, base: RegisterOffset, boxed: scala.Double): Unit =
      registers.setDouble(offset + base, boxed)

    def usedRegisters: RegisterOffset = RegisterOffset.incrementDoublesAndLongs(0)
  }

  case class Char(offset: RegisterOffset) extends Register[scala.Char] {
    def get(registers: Registers, base: RegisterOffset): scala.Char = registers.getChar(offset + base)

    def set(registers: Registers, base: RegisterOffset, boxed: scala.Char): Unit =
      registers.setChar(offset + base, boxed)

    def usedRegisters: RegisterOffset = RegisterOffset.incrementCharsAndShorts(0)
  }

  case class Object[A <: AnyRef](offset: RegisterOffset) extends Register[A] {
    def get(registers: Registers, base: RegisterOffset): Boxed =
      registers.getObject(offset + base).asInstanceOf[Boxed]

    def set(registers: Registers, base: RegisterOffset, boxed: Boxed): Unit = registers.setObject(offset + base, boxed)

    def usedRegisters: RegisterOffset = RegisterOffset.incrementObjects(0)
  }
}
