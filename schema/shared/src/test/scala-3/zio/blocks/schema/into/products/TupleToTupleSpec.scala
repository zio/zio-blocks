package zio.blocks.schema.into.products

import zio.test._
import zio.blocks.schema._

object TupleToTupleSpec extends ZIOSpecDefault {

  def spec = suite("TupleToTupleSpec")(
    suite("Basic Conversions")(
      test("should convert tuple to tuple with same types (Tuple2)") {
        type Tuple1 = (Int, String)
        type Tuple2 = (Int, String)

        val derivation = Into.derived[Tuple1, Tuple2]
        val input      = (42, "hello")
        val result     = derivation.into(input)

        assertTrue(result == Right((42, "hello")))
      },
      test("should convert tuple to tuple with same types (Tuple3)") {
        type Tuple1 = (Int, String, Boolean)
        type Tuple2 = (Int, String, Boolean)

        val derivation = Into.derived[Tuple1, Tuple2]
        val input      = (42, "hello", true)
        val result     = derivation.into(input)

        assertTrue(result == Right((42, "hello", true)))
      }
    ),
    suite("With Coercion")(
      test("should convert tuple to tuple with element coercion (Tuple2)") {
        type Tuple1 = (Int, String)
        type Tuple2 = (Long, String)

        val derivation = Into.derived[Tuple1, Tuple2]
        val input      = (42, "hello")
        val result     = derivation.into(input)

        assertTrue(result == Right((42L, "hello")))
      },
      test("should convert tuple to tuple with multiple coercions (Tuple3)") {
        type Tuple1 = (Int, Double, Float)
        type Tuple2 = (Long, Double, Float)

        val derivation = Into.derived[Tuple1, Tuple2]
        val input      = (42, 3.14, 2.5f)
        val result     = derivation.into(input)

        assertTrue(result.isRight)
        assertTrue(result.map(_._1) == Right(42L))
        assertTrue(result.map(_._2) == Right(3.14))
        assertTrue(result.map(_._3) == Right(2.5f))
      },
      test("should convert tuple to tuple with mixed coercions (Tuple4)") {
        type Tuple1 = (Int, Double, Float, Long)
        type Tuple2 = (Long, Double, Float, Int)

        val derivation = Into.derived[Tuple1, Tuple2]
        val input      = (42, 3.14, 2.5f, 100L)
        val result     = derivation.into(input)

        assertTrue(result.isRight)
        assertTrue(result.map(_._1) == Right(42L))
        assertTrue(result.map(_._2) == Right(3.14))
        assertTrue(result.map(_._3) == Right(2.5f))
        // Long to Int with validation
        assertTrue(result.map(_._4) == Right(100))
      }
    ),
    suite("Different Arity")(
      test("should convert Tuple2 to Tuple2") {
        type Tuple1 = (Int, String)
        type Tuple2 = (Long, String)

        val derivation = Into.derived[Tuple1, Tuple2]
        val input      = (42, "hello")
        val result     = derivation.into(input)

        assertTrue(result == Right((42L, "hello")))
      },
      test("should convert Tuple3 to Tuple3") {
        type Tuple1 = (Int, String, Boolean)
        type Tuple2 = (Long, String, Boolean)

        val derivation = Into.derived[Tuple1, Tuple2]
        val input      = (42, "hello", true)
        val result     = derivation.into(input)

        assertTrue(result == Right((42L, "hello", true)))
      },
      test("should convert Tuple4 to Tuple4") {
        type Tuple1 = (Int, String, Boolean, Double)
        type Tuple2 = (Long, String, Boolean, Double)

        val derivation = Into.derived[Tuple1, Tuple2]
        val input      = (42, "hello", true, 3.14)
        val result     = derivation.into(input)

        assertTrue(result == Right((42L, "hello", true, 3.14)))
      },
      test("should convert Tuple5 to Tuple5") {
        type Tuple1 = (Int, Long, String, Boolean, Double)
        type Tuple2 = (Long, Long, String, Boolean, Double)

        val derivation = Into.derived[Tuple1, Tuple2]
        val input      = (42, 100L, "hello", true, 3.14)
        val result     = derivation.into(input)

        assertTrue(result == Right((42L, 100L, "hello", true, 3.14)))
      }
    ),
    suite("Numeric Narrowing")(
      test("should convert tuple with narrowing (Long to Int)") {
        type Tuple1 = (Long, String)
        type Tuple2 = (Int, String)

        val derivation = Into.derived[Tuple1, Tuple2]
        val input      = (42L, "hello")
        val result     = derivation.into(input)

        assertTrue(result == Right((42, "hello")))
      },
      test("should fail tuple narrowing with overflow") {
        type Tuple1 = (Long, String)
        type Tuple2 = (Int, String)

        val derivation = Into.derived[Tuple1, Tuple2]
        val input      = (3000000000L, "hello") // Overflow for Int
        val result     = derivation.into(input)

        assertTrue(result.isLeft)
        assertTrue(result.left.exists(_.message.contains("Long value")))
      },
      test("should convert tuple with Double to Float") {
        type Tuple1 = (Double, String)
        type Tuple2 = (Float, String)

        val derivation = Into.derived[Tuple1, Tuple2]
        val input      = (3.14, "hello")
        val result     = derivation.into(input)

        assertTrue(result.isRight)
        assertTrue(result.map(_._1) == Right(3.14f))
      }
    )
    // Note: Arity mismatch errors are tested at compile-time.
    // The macro correctly fails with clear error messages when arity doesn't match.
  )
}
