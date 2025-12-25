package zio.blocks.schema.into.primitives

import zio.test._
import zio.blocks.schema._

object EitherCoercionSpec extends ZIOSpecDefault {

  def spec = suite("EitherCoercionSpec")(
    suite("Right -> Right (Coerced)")(
      test("should coerce Right(Int) to Right(Long)") {
        val derivation = Into.derived[Either[String, Int], Either[String, Long]]
        val input      = Right(42)
        val result     = derivation.into(input)

        assertTrue(result == Right(Right(42L)))
      },
      test("should coerce Right(Int) to Right(Double)") {
        val derivation = Into.derived[Either[String, Int], Either[String, Double]]
        val input      = Right(42)
        val result     = derivation.into(input)

        assertTrue(result == Right(Right(42.0)))
      },
      test("should coerce Right(Float) to Right(Double)") {
        val derivation = Into.derived[Either[String, Float], Either[String, Double]]
        val input      = Right(3.14f)
        val result     = derivation.into(input)

        assertTrue(result.isRight)
        result
          .map(_.map(_.toDouble))
          .fold(
            _ => assertTrue(false),
            either =>
              either.fold(
                _ => assertTrue(false),
                double => assertTrue((double - 3.14).abs < 0.0001)
              )
          )
      }
    ),
    suite("Left -> Left (Coerced)")(
      test("should coerce Left(Int) to Left(Long)") {
        val derivation = Into.derived[Either[Int, String], Either[Long, String]]
        val input      = Left(42)
        val result     = derivation.into(input)

        assertTrue(result == Right(Left(42L)))
      },
      test("should coerce Left(Int) to Left(Double)") {
        val derivation = Into.derived[Either[Int, String], Either[Double, String]]
        val input      = Left(42)
        val result     = derivation.into(input)

        assertTrue(result == Right(Left(42.0)))
      },
      test("should coerce Left(Float) to Left(Double)") {
        val derivation = Into.derived[Either[Float, String], Either[Double, String]]
        val input      = Left(3.14f)
        val result     = derivation.into(input)

        assertTrue(result.isRight)
        result
          .map(_.left.map(_.toDouble))
          .fold(
            _ => assertTrue(false),
            either =>
              either.fold(
                double => assertTrue((double - 3.14).abs < 0.0001),
                _ => assertTrue(false)
              )
          )
      }
    ),
    suite("Failure Cases")(
      test("should fail when Right(Long) exceeds Int.MaxValue") {
        val derivation = Into.derived[Either[String, Long], Either[String, Int]]
        val input      = Right(Int.MaxValue.toLong + 1L)
        val result     = derivation.into(input)

        assertTrue(result.isLeft)
        assertTrue(result.left.exists(_.message.contains("out of Int range")))
      },
      test("should fail when Right(Long) is below Int.MinValue") {
        val derivation = Into.derived[Either[String, Long], Either[String, Int]]
        val input      = Right(Int.MinValue.toLong - 1L)
        val result     = derivation.into(input)

        assertTrue(result.isLeft)
        assertTrue(result.left.exists(_.message.contains("out of Int range")))
      },
      test("should fail when Left(Long) exceeds Int.MaxValue") {
        val derivation = Into.derived[Either[Long, String], Either[Int, String]]
        val input      = Left(Int.MaxValue.toLong + 1L)
        val result     = derivation.into(input)

        assertTrue(result.isLeft)
        assertTrue(result.left.exists(_.message.contains("out of Int range")))
      },
      test("should fail when Left(Long) is below Int.MinValue") {
        val derivation = Into.derived[Either[Long, String], Either[Int, String]]
        val input      = Left(Int.MinValue.toLong - 1L)
        val result     = derivation.into(input)

        assertTrue(result.isLeft)
        assertTrue(result.left.exists(_.message.contains("out of Int range")))
      },
      test("should fail when Right(Double) is too large for Float") {
        val derivation = Into.derived[Either[String, Double], Either[String, Float]]
        val input      = Right(Double.MaxValue)
        val result     = derivation.into(input)

        assertTrue(result.isLeft)
        assertTrue(result.left.exists(_.message.contains("out of Float range")))
      },
      test("should fail when Right(Double) is not a whole number for Int") {
        val derivation = Into.derived[Either[String, Double], Either[String, Int]]
        val input      = Right(3.14)
        val result     = derivation.into(input)

        assertTrue(result.isLeft)
        assertTrue(result.left.exists(_.message.contains("not a whole number")))
      }
    ),
    suite("Type Swap")(
      test("should swap types: Either[String, Int] -> Either[String, Long]") {
        val derivation = Into.derived[Either[String, Int], Either[String, Long]]
        val input      = Right(42)
        val result     = derivation.into(input)

        assertTrue(result == Right(Right(42L)))
      },
      test("should swap types: Either[Int, String] -> Either[Long, String]") {
        val derivation = Into.derived[Either[Int, String], Either[Long, String]]
        val input      = Left(42)
        val result     = derivation.into(input)

        assertTrue(result == Right(Left(42L)))
      },
      test("should swap types: Either[String, Int] -> Either[String, Long] with Left unchanged") {
        val derivation = Into.derived[Either[String, Int], Either[String, Long]]
        val input      = Left("error")
        val result     = derivation.into(input)

        assertTrue(result == Right(Left("error")))
      },
      test("should swap types: Either[Int, String] -> Either[Long, String] with Right unchanged") {
        val derivation = Into.derived[Either[Int, String], Either[Long, String]]
        val input      = Right("success")
        val result     = derivation.into(input)

        assertTrue(result == Right(Right("success")))
      }
    )
  )
}
