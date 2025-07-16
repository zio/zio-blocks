package zio.blocks.schema.binding

import zio.blocks.schema.binding.RegisterOffset.RegisterOffset

sealed trait Register[A] {
  final type Boxed = A

  protected def get(registers: Registers, base: RegisterOffset): Boxed

  protected def set(registers: Registers, base: RegisterOffset, boxed: Boxed): Unit

  protected def usedRegisters: RegisterOffset
}

object Register {
  case object Unit extends Register[scala.Unit] {
    def get(registers: Registers, base: RegisterOffset): scala.Unit = ()

    def set(registers: Registers, base: RegisterOffset, boxed: scala.Unit): Unit = ()

    def usedRegisters: RegisterOffset = RegisterOffset.Zero
  }

  case class Boolean(relativeIndex: scala.Int) extends Register[scala.Boolean] {
    def get(registers: Registers, base: RegisterOffset): scala.Boolean = registers.getBoolean(base, relativeIndex)

    def set(registers: Registers, base: RegisterOffset, boxed: scala.Boolean): Unit =
      registers.setBoolean(base, relativeIndex, boxed)

    def usedRegisters: RegisterOffset = RegisterOffset.incrementBooleansAndBytes(RegisterOffset.Zero)
  }

  case class Byte(relativeIndex: scala.Int) extends Register[scala.Byte] {
    def get(registers: Registers, base: RegisterOffset): scala.Byte = registers.getByte(base, relativeIndex)

    def set(registers: Registers, base: RegisterOffset, boxed: scala.Byte): Unit =
      registers.setByte(base, relativeIndex, boxed)

    def usedRegisters: RegisterOffset = RegisterOffset.incrementBooleansAndBytes(RegisterOffset.Zero)
  }

  case class Short(relativeIndex: scala.Int) extends Register[scala.Short] {
    def get(registers: Registers, base: RegisterOffset): scala.Short = registers.getShort(base, relativeIndex)

    def set(registers: Registers, base: RegisterOffset, boxed: scala.Short): Unit =
      registers.setShort(base, relativeIndex, boxed)

    def usedRegisters: RegisterOffset = RegisterOffset.incrementCharsAndShorts(RegisterOffset.Zero)
  }

  case class Int(relativeIndex: scala.Int) extends Register[scala.Int] {
    def get(registers: Registers, base: RegisterOffset): scala.Int = registers.getInt(base, relativeIndex)

    def set(registers: Registers, base: RegisterOffset, boxed: scala.Int): Unit =
      registers.setInt(base, relativeIndex, boxed)

    def usedRegisters: RegisterOffset = RegisterOffset.incrementFloatsAndInts(RegisterOffset.Zero)
  }

  case class Long(relativeIndex: scala.Int) extends Register[scala.Long] {
    def get(registers: Registers, base: RegisterOffset): scala.Long = registers.getLong(base, relativeIndex)

    def set(registers: Registers, base: RegisterOffset, boxed: scala.Long): Unit =
      registers.setLong(base, relativeIndex, boxed)

    def usedRegisters: RegisterOffset = RegisterOffset.incrementDoublesAndLongs(RegisterOffset.Zero)
  }

  case class Float(relativeIndex: scala.Int) extends Register[scala.Float] {
    def get(registers: Registers, base: RegisterOffset): scala.Float = registers.getFloat(base, relativeIndex)

    def set(registers: Registers, base: RegisterOffset, boxed: scala.Float): Unit =
      registers.setFloat(base, relativeIndex, boxed)

    def usedRegisters: RegisterOffset = RegisterOffset.incrementFloatsAndInts(RegisterOffset.Zero)
  }

  case class Double(relativeIndex: scala.Int) extends Register[scala.Double] {
    def get(registers: Registers, base: RegisterOffset): scala.Double = registers.getDouble(base, relativeIndex)

    def set(registers: Registers, base: RegisterOffset, boxed: scala.Double): Unit =
      registers.setDouble(base, relativeIndex, boxed)

    def usedRegisters: RegisterOffset = RegisterOffset.incrementDoublesAndLongs(RegisterOffset.Zero)
  }

  case class Char(relativeIndex: scala.Int) extends Register[scala.Char] {
    def get(registers: Registers, base: RegisterOffset): scala.Char = registers.getChar(base, relativeIndex)

    def set(registers: Registers, base: RegisterOffset, boxed: scala.Char): Unit =
      registers.setChar(base, relativeIndex, boxed)

    def usedRegisters: RegisterOffset = RegisterOffset.incrementCharsAndShorts(RegisterOffset.Zero)
  }

  case class Object[A <: AnyRef](relativeIndex: scala.Int) extends Register[A] {
    def get(registers: Registers, base: RegisterOffset): Boxed =
      registers.getObject(base, relativeIndex).asInstanceOf[Boxed]

    def set(registers: Registers, base: RegisterOffset, boxed: Boxed): Unit =
      registers.setObject(base, relativeIndex, boxed)

    def usedRegisters: RegisterOffset = RegisterOffset.incrementObjects(RegisterOffset.Zero)
  }

  def get[A](registers: Registers, offset: RegisterOffset, register: Register[A]): A =
    register match {
      case reg: Object[_] => reg.get(registers, offset).asInstanceOf[A]
      case reg: Int       => reg.get(registers, offset)
      case reg: Long      => reg.get(registers, offset)
      case reg: Boolean   => reg.get(registers, offset)
      case reg: Double    => reg.get(registers, offset)
      case reg: Float     => reg.get(registers, offset)
      case reg: Byte      => reg.get(registers, offset)
      case reg: Short     => reg.get(registers, offset)
      case reg: Char      => reg.get(registers, offset)
      case Unit           => Unit.get(registers, offset)
    }

  def set[A](registers: Registers, offset: RegisterOffset, register: Register[A], value: A): Unit =
    register match {
      case reg: Object[_] => reg.set(registers, offset, value.asInstanceOf[reg.Boxed])
      case reg: Int       => reg.set(registers, offset, value)
      case reg: Long      => reg.set(registers, offset, value)
      case reg: Boolean   => reg.set(registers, offset, value)
      case reg: Double    => reg.set(registers, offset, value)
      case reg: Float     => reg.set(registers, offset, value)
      case reg: Byte      => reg.set(registers, offset, value)
      case reg: Short     => reg.set(registers, offset, value)
      case reg: Char      => reg.set(registers, offset, value)
      case Unit           => Unit.set(registers, offset, value)
    }

  def usedRegisters(register: Register[_]): RegisterOffset =
    register match {
      case reg: Object[_] => reg.usedRegisters
      case reg: Int       => reg.usedRegisters
      case reg: Long      => reg.usedRegisters
      case reg: Boolean   => reg.usedRegisters
      case reg: Double    => reg.usedRegisters
      case reg: Float     => reg.usedRegisters
      case reg: Byte      => reg.usedRegisters
      case reg: Short     => reg.usedRegisters
      case reg: Char      => reg.usedRegisters
      case Unit           => Unit.usedRegisters
    }
}
