package zio.blocks.schema.into.validation

import zio.blocks.schema._
import zio.test._
import zio.test.Assertion._

/** Tests for validation error handling and messages. */
object ValidationErrorSpec extends ZIOSpecDefault {

  case class LongValue(value: Long)
  case class IntValue(value: Int)

  def spec: Spec[TestEnvironment, Any] = suite("ValidationErrorSpec")(
    test("narrowing overflow returns Left") {
      val result = Into.derived[LongValue, IntValue].into(LongValue(Long.MaxValue))
      assert(result)(isLeft)
    },
    test("successful conversion returns Right") {
      val result = Into.derived[LongValue, IntValue].into(LongValue(100L))
      assert(result)(isRight)
    },
    test("error is SchemaError type") {
      val result = Into.derived[LongValue, IntValue].into(LongValue(Long.MaxValue))
      result match {
        case Left(err) => assertTrue(err.isInstanceOf[SchemaError])
        case Right(_)  => assertTrue(false)
      }
    },
    test("error message contains context") {
      val result = Into.derived[LongValue, IntValue].into(LongValue(Long.MaxValue))
      result match {
        case Left(err) =>
          val msg = err.toString.toLowerCase
          assertTrue(msg.contains("overflow") || msg.contains("range") || msg.contains("conversion"))
        case Right(_) => assertTrue(false)
      }
    }
  )
}
