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
  def usedRegisters: RegisterOffset = 0L

  def construct(in: Registers, offset: RegisterOffset): A = constant
}

/**
 * A constructor for structural types that exist only at compile-time.
 * Structural types cannot be instantiated at runtime; they work only with DynamicValue.
 */
abstract class StructuralConstructor[+A] extends Constructor[A]
