package zio.blocks.schema.internal

import scala.reflect.macros.blackbox

class AsMacros(val c: blackbox.Context) {
  import c.universe._

  def deriveImpl[A: c.WeakTypeTag, B: c.WeakTypeTag]: c.Expr[zio.blocks.schema.As[A, B]] = {
    val tpeA = weakTypeOf[A]
    val tpeB = weakTypeOf[B]

    val result = q"""
      new zio.blocks.schema.As[$tpeA, $tpeB] {
        private val forward = implicitly[zio.blocks.schema.Into[$tpeA, $tpeB]]
        private val backward = implicitly[zio.blocks.schema.Into[$tpeB, $tpeA]]

        def to(a: $tpeA) = forward.into(a)
        def from(b: $tpeB) = backward.into(b)
      }
    """
    c.Expr[zio.blocks.schema.As[A, B]](result)
  }
}
