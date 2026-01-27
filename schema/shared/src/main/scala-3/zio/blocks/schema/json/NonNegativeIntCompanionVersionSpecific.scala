package zio.blocks.schema.json

import scala.compiletime.error

trait NonNegativeIntCompanionVersionSpecific {

  /**
   * Creates a NonNegativeInt from a literal integer value, validated at compile
   * time. Use this when the value is known at compile time for zero runtime
   * overhead.
   *
   * {{{
   * val min = NonNegativeInt.literal(3)   // Compiles - returns NonNegativeInt directly
   * val bad = NonNegativeInt.literal(-1)  // Compile error!
   * }}}
   */
  inline def literal(inline n: Int): NonNegativeInt =
    inline if (n >= 0) NonNegativeInt.unsafe(n)
    else error("NonNegativeInt requires n >= 0")
}
