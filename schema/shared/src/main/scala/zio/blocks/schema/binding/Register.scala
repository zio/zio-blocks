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

    def usedRegisters: RegisterOffset = 0
  }

  case class Boolean(relativeIndex: scala.Int) extends Register[scala.Boolean] {
    def get(registers: Registers, base: RegisterOffset): scala.Boolean = registers.getBoolean(base, relativeIndex)

    def set(registers: Registers, base: RegisterOffset, boxed: scala.Boolean): Unit =
      registers.setBoolean(base, relativeIndex, boxed)

    def usedRegisters: RegisterOffset = RegisterOffset.incrementBooleansAndBytes(0)
  }

  case class Byte(relativeIndex: scala.Int) extends Register[scala.Byte] {
    def get(registers: Registers, base: RegisterOffset): scala.Byte = registers.getByte(base, relativeIndex)

    def set(registers: Registers, base: RegisterOffset, boxed: scala.Byte): Unit =
      registers.setByte(base, relativeIndex, boxed)

    def usedRegisters: RegisterOffset = RegisterOffset.incrementBooleansAndBytes(0)
  }

  case class Short(relativeIndex: scala.Int) extends Register[scala.Short] {
    def get(registers: Registers, base: RegisterOffset): scala.Short = registers.getShort(base, relativeIndex)

    def set(registers: Registers, base: RegisterOffset, boxed: scala.Short): Unit =
      registers.setShort(base, relativeIndex, boxed)

    def usedRegisters: RegisterOffset = RegisterOffset.incrementCharsAndShorts(0)
  }

  case class Int(relativeIndex: scala.Int) extends Register[scala.Int] {
    def get(registers: Registers, base: RegisterOffset): scala.Int = registers.getInt(base, relativeIndex)

    def set(registers: Registers, base: RegisterOffset, boxed: scala.Int): Unit =
      registers.setInt(base, relativeIndex, boxed)

    def usedRegisters: RegisterOffset = RegisterOffset.incrementFloatsAndInts(0)
  }

  case class Long(relativeIndex: scala.Int) extends Register[scala.Long] {
    def get(registers: Registers, base: RegisterOffset): scala.Long = registers.getLong(base, relativeIndex)

    def set(registers: Registers, base: RegisterOffset, boxed: scala.Long): Unit =
      registers.setLong(base, relativeIndex, boxed)

    def usedRegisters: RegisterOffset = RegisterOffset.incrementDoublesAndLongs(0)
  }

  case class Float(relativeIndex: scala.Int) extends Register[scala.Float] {
    def get(registers: Registers, base: RegisterOffset): scala.Float = registers.getFloat(base, relativeIndex)

    def set(registers: Registers, base: RegisterOffset, boxed: scala.Float): Unit =
      registers.setFloat(base, relativeIndex, boxed)

    def usedRegisters: RegisterOffset = RegisterOffset.incrementFloatsAndInts(0)
  }

  case class Double(relativeIndex: scala.Int) extends Register[scala.Double] {
    def get(registers: Registers, base: RegisterOffset): scala.Double = registers.getDouble(base, relativeIndex)

    def set(registers: Registers, base: RegisterOffset, boxed: scala.Double): Unit =
      registers.setDouble(base, relativeIndex, boxed)

    def usedRegisters: RegisterOffset = RegisterOffset.incrementDoublesAndLongs(0)
  }

  case class Char(relativeIndex: scala.Int) extends Register[scala.Char] {
    def get(registers: Registers, base: RegisterOffset): scala.Char = registers.getChar(base, relativeIndex)

    def set(registers: Registers, base: RegisterOffset, boxed: scala.Char): Unit =
      registers.setChar(base, relativeIndex, boxed)

    def usedRegisters: RegisterOffset = RegisterOffset.incrementCharsAndShorts(0)
  }

  case class Object[A <: AnyRef](relativeIndex: scala.Int) extends Register[A] {
    def get(registers: Registers, base: RegisterOffset): Boxed =
      registers.getObject(base, relativeIndex).asInstanceOf[Boxed]

    def set(registers: Registers, base: RegisterOffset, boxed: Boxed): Unit =
      registers.setObject(base, relativeIndex, boxed)

    def usedRegisters: RegisterOffset = RegisterOffset.incrementObjects(0)
  }
}
