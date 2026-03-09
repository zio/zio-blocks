package into

import zio.blocks.schema.Into
import util.ShowExpr.show

// Numeric widening is always lossless — these always return Right.
object IntoWideningExample extends App {
  show(Into[Byte, Int].into(42.toByte))
  show(Into[Int, Long].into(100))
  show(Into[Long, Double].into(9999999L))
  show(Into[Float, Double].into(3.14f))
}

// Numeric narrowing validates the value at runtime.
// Returns Right when the value fits; Left when it is out of range
// or cannot be represented precisely.
object IntoNarrowingExample extends App {
  show(Into[Long, Int].into(42L))
  show(Into[Long, Int].into(Long.MaxValue))
  show(Into[Double, Float].into(1.5))
  show(Into[Double, Int].into(3.14))
}
