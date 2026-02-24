package into

import zio.blocks.schema.Into

// Numeric widening is always lossless — these always return Right.
object IntoWideningExample extends App {
  println(Into[Byte,  Int   ].into(42.toByte))
  println(Into[Int,   Long  ].into(100))
  println(Into[Long,  Double].into(9999999L))
  println(Into[Float, Double].into(3.14f))
}

// Numeric narrowing validates the value at runtime.
// Returns Right when the value fits; Left when it is out of range
// or cannot be represented precisely.
object IntoNarrowingExample extends App {
  println(Into[Long,   Int  ].into(42L))           // fits — Right(42)
  println(Into[Long,   Int  ].into(Long.MaxValue))  // overflow — Left
  println(Into[Double, Float].into(1.5))            // fits — Right(1.5)
  println(Into[Double, Int  ].into(3.14))           // not a whole number — Left
}
