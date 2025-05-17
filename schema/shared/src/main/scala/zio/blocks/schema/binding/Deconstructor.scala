package zio.blocks.schema.binding

import zio.blocks.schema.binding.RegisterOffset.RegisterOffset

/**
 * A {{Deconstructor}} is a typeclass that can deconstruct a value of type `A`
 * into a set of registers.
 */
abstract class Deconstructor[-A] { self =>
  def usedRegisters: RegisterOffset

  /**
   * Deconstructs a value of type `A` into the registers.
   */
  def deconstruct(out: Registers, baseOffset: RegisterOffset, in: A): Unit

  /**
   * Transforms this deconstructor into one that operates on a different type --
   * albeit one that can be converted to this type.
   */
  final def contramap[B](f: B => A): Deconstructor[B] =
    new Deconstructor[B] {
      def usedRegisters: RegisterOffset = self.usedRegisters

      def deconstruct(out: Registers, baseOffset: RegisterOffset, in: B): Unit =
        self.deconstruct(out, baseOffset, f(in))
    }
}
