package zio.blocks.schema.binding

import zio.blocks.schema.binding.RegisterOffset.RegisterOffset

/**
 * A `Deconstructor` is a typeclass that can deconstruct a value of type `A`
 * into a set of registers.
 */
abstract class Deconstructor[-A] { self =>

  /**
   * The size of the registers required to deconstruct a value of type `A`.
   */
  def usedRegisters: RegisterOffset

  /**
   * Deconstructs a value of type `A` into the registers.
   */
  def deconstruct(out: Registers, baseOffset: RegisterOffset, in: A): Unit
}

class ConstantDeconstructor[A] extends Deconstructor[A] {
  override def usedRegisters: RegisterOffset = 0

  override def deconstruct(out: Registers, baseOffset: RegisterOffset, in: A): Unit = {}
}
