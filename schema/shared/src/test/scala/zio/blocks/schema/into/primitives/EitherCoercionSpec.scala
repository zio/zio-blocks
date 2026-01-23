package zio.blocks.schema.into.primitives

import zio.blocks.schema._
import zio.test._
import zio.test.Assertion._

/** Tests for Either type coercion in Into conversions. */
object EitherCoercionSpec extends ZIOSpecDefault {

  def spec: Spec[TestEnvironment, Any] = suite("EitherCoercionSpec")(
    test("coerces Right[Int] to Right[Long]") {
      val source: Either[String, Int] = Right(42)
      val result                      = Into[Either[String, Int], Either[String, Long]].into(source)
      assert(result)(isRight(equalTo(Right(42L))))
    },
    test("passes Left unchanged when right type changes") {
      val source: Either[String, Int] = Left("error")
      val result                      = Into[Either[String, Int], Either[String, Long]].into(source)
      assert(result)(isRight(equalTo(Left("error"))))
    },
    test("coerces both Left and Right types") {
      val source: Either[Int, Short] = Right(100.toShort)
      val result                     = Into[Either[Int, Short], Either[Long, Int]].into(source)
      assert(result)(isRight(equalTo(Right(100))))
    },
    test("fails when narrowing Right overflows") {
      val source: Either[String, Long] = Right(Long.MaxValue)
      val result                       = Into[Either[String, Long], Either[String, Int]].into(source)
      assert(result)(isLeft)
    }
  )
}
