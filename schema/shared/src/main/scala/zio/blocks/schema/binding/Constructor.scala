package zio.blocks.schema.binding

import zio.blocks.schema.binding.RegisterOffset.RegisterOffset

/**
 * A {{Constructor}} is a typeclass that can construct a value of type `A` from
 * a set of registers.
 */
abstract class Constructor[+A] { self =>

  /**
   * The size of the registers required to construct a value of type `A`.
   */
  def usedRegisters: RegisterOffset

  /**
   * Constructs a value of type `A` from the registers.
   */
  def construct(in: Registers, baseOffset: RegisterOffset): A

  /**
   * Maps a function over the constructed value.
   */
  final def map[B](f: A => B): Constructor[B] =
    new Constructor[B] {
      def usedRegisters: RegisterOffset = self.usedRegisters

      def construct(in: Registers, baseOffset: RegisterOffset): B = f(self.construct(in, baseOffset))
    }
}

object Constructor {
  val unit: Constructor[Unit] =
    new Constructor[Unit] {
      def usedRegisters: RegisterOffset = RegisterOffset.Zero

      def construct(in: Registers, baseOffset: RegisterOffset): Unit = ()
    }
}
