package zio.blocks.schema.json

import scala.language.experimental.macros
import scala.reflect.macros.blackbox

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
  def literal(n: Int): PositiveNumber = macro PositiveNumberMacros.literalImpl
}

private[json] object PositiveNumberMacros {
  def literalImpl(c: blackbox.Context)(n: c.Expr[Int]): c.Expr[PositiveNumber] = {
    import c.universe._

    n.tree match {
      case Literal(Constant(value: Int)) =>
        if (value > 0) {
          c.Expr[PositiveNumber](q"new _root_.zio.blocks.schema.json.PositiveNumber(BigDecimal($value))")
        } else {
          c.abort(c.enclosingPosition, s"PositiveNumber requires n > 0, got $value")
        }
      case _ =>
        c.abort(
          c.enclosingPosition,
          "PositiveNumber.literal requires a literal Int. Use PositiveNumber.apply for runtime values."
        )
    }
  }
}
