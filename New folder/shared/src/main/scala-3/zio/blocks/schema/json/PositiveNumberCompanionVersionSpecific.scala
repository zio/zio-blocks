package zio.blocks.schema.json

import scala.compiletime.error

trait PositiveNumberCompanionVersionSpecific {

  /**
   * Creates a PositiveNumber from a literal integer value, validated at compile
   * time. Use this when the value is known at compile time for zero runtime
   * overhead.
   *
   * {{{
   * val mult = PositiveNumber.literal(5)  // Compiles - returns PositiveNumber directly
   * val bad = PositiveNumber.literal(0)   // Compile error!
   * }}}
   */
  inline def literal(inline n: Int): PositiveNumber =
    inline if (n > 0) PositiveNumber.unsafe(BigDecimal(n))
    else error("PositiveNumber requires n > 0")
}
