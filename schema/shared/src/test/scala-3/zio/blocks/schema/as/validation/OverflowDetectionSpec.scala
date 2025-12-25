package zio.blocks.schema.as.validation

import zio.test._
import zio.blocks.schema._

object OverflowDetectionSpec extends ZIOSpecDefault {

  def spec = suite("OverflowDetectionSpec")(
    suite("Overflow Detection in As Round-Trip")(
      test("should detect overflow in As[Long, Int] round-trip") {
        val as = As.derived[Long, Int]

        // Valid narrowing (fits in Int)
        val validInput  = 42L
        val validResult = as.into(validInput).flatMap(as.from)
        assertTrue(validResult == Right(42L))

        // Overflow (too large for Int)
        val overflowInput  = Long.MaxValue
        val overflowResult = as.into(overflowInput)
        assertTrue(
          overflowResult.isLeft &&
            overflowResult.left.exists(err =>
              err.message.contains("overflow") ||
                err.message.contains("exceeds") ||
                err.message.contains("out of") ||
                err.message.contains("range")
            )
        )
      },
      test("should detect underflow in As[Long, Int] round-trip") {
        val as = As.derived[Long, Int]

        // Underflow (too small for Int)
        val underflowInput  = Long.MinValue
        val underflowResult = as.into(underflowInput)
        assertTrue(underflowResult.isLeft)
      },
      test("should detect overflow in As[Double, Float] round-trip") {
        val as = As.derived[Double, Float]

        // Valid narrowing (fits in Float)
        val validInput  = 3.14
        val validResult = as.into(validInput).flatMap(as.from)
        assertTrue(validResult.isRight)

        // Overflow (too large for Float)
        val overflowInput  = Double.MaxValue
        val overflowResult = as.into(overflowInput)
        assertTrue(overflowResult.isLeft)
      },
      test("should detect overflow in As[Double, Long] round-trip") {
        val as = As.derived[Double, Long]

        // Valid narrowing (whole number, fits in Long)
        val validInput  = 42.0
        val validResult = as.into(validInput).flatMap(as.from)
        assertTrue(validResult == Right(42.0))

        // Overflow (too large for Long)
        val overflowInput  = Double.MaxValue
        val overflowResult = as.into(overflowInput)
        assertTrue(overflowResult.isLeft)
      },
      test("should fail when Double is not a whole number in As[Double, Long]") {
        val as = As.derived[Double, Long]

        // Not a whole number
        val invalidInput  = 3.14
        val invalidResult = as.into(invalidInput)
        assertTrue(
          invalidResult.isLeft &&
            invalidResult.left.exists(err =>
              err.message.contains("whole number") ||
                err.message.contains("fractional") ||
                err.message.contains("integer")
            )
        )
      },
      test("should handle overflow in nested As conversions") {
        case class Source(value: Long)
        case class Target(value: Int)

        val as = As.derived[Source, Target]

        // Valid
        val validInput  = Source(42L)
        val validResult = as.into(validInput).flatMap(as.from)
        assertTrue(validResult == Right(Source(42L)))

        // Overflow
        val overflowInput  = Source(Long.MaxValue)
        val overflowResult = as.into(overflowInput)
        assertTrue(overflowResult.isLeft)
      },
      test("should handle overflow in As[List[Long], List[Int]] round-trip") {
        val as = As.derived[List[Long], List[Int]]

        // Valid (all fit in Int)
        val validInput  = List(1L, 2L, 3L)
        val validResult = as.into(validInput).flatMap(as.from)
        assertTrue(validResult == Right(List(1L, 2L, 3L)))

        // Overflow (one element too large)
        val overflowInput  = List(1L, Long.MaxValue, 3L)
        val overflowResult = as.into(overflowInput)
        assertTrue(overflowResult.isLeft)
      }
    )
  )
}
