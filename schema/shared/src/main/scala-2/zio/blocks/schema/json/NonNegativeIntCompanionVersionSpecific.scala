package zio.blocks.schema.json

import scala.language.experimental.macros
import scala.reflect.macros.blackbox

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
  def literal(n: Int): NonNegativeInt = macro NonNegativeIntMacros.literalImpl
}

private[json] object NonNegativeIntMacros {
  def literalImpl(c: blackbox.Context)(n: c.Expr[Int]): c.Expr[NonNegativeInt] = {
    import c.universe._

    n.tree match {
      case Literal(Constant(value: Int)) =>
        if (value >= 0) {
          c.Expr[NonNegativeInt](q"new _root_.zio.blocks.schema.json.NonNegativeInt($value)")
        } else {
          c.abort(c.enclosingPosition, s"NonNegativeInt requires n >= 0, got $value")
        }
      case _ =>
        c.abort(
          c.enclosingPosition,
          "NonNegativeInt.literal requires a literal Int. Use NonNegativeInt.apply for runtime values."
        )
    }
  }
}
