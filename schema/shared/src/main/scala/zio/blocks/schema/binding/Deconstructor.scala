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
  def deconstruct(out: Registers, offset: RegisterOffset, in: A): Unit
}

class ConstantDeconstructor[A] extends Deconstructor[A] {
  override def usedRegisters: RegisterOffset = 0L

  override def deconstruct(out: Registers, offset: RegisterOffset, in: A): Unit = {}
}

/**
 * A deconstructor for structural types that exist only at compile-time.
 * Structural types cannot be deconstructed at runtime; they work only with DynamicValue.
 */
abstract class StructuralDeconstructor[-A] extends Deconstructor[A]
