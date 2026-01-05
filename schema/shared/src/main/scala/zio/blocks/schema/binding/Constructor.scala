package zio.blocks.schema.binding

import zio.blocks.schema.binding.RegisterOffset.RegisterOffset

/**
 * A `Constructor` is a typeclass that can construct a value of type `A` from a
 * set of registers.
 */
abstract class Constructor[+A] { self =>

  /**
   * The size of the registers required to construct a value of type `A`.
   */
  def usedRegisters: RegisterOffset

  /**
   * Constructs a value of type `A` from the registers.
   */
  def construct(in: Registers, offset: RegisterOffset): A
}

class ConstantConstructor[A](constant: A) extends Constructor[A] {
  def usedRegisters: RegisterOffset = 0

  def construct(in: Registers, offset: RegisterOffset): A = constant
}
