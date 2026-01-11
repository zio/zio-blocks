package zio.blocks.schema.into.validation

import zio.blocks.schema._
import zio.test._
import zio.test.Assertion._

/** Tests for numeric narrowing validation. */
object NarrowingValidationSpec extends ZIOSpecDefault {

  case class LongValue(value: Long)
  case class IntValue(value: Int)

  def spec: Spec[TestEnvironment, Any] = suite("NarrowingValidationSpec")(
    test("succeeds when Long value fits in Int range") {
      val result = Into.derived[LongValue, IntValue].into(LongValue(100L))
      assert(result)(isRight(equalTo(IntValue(100))))
    },
    test("fails when Long value exceeds Int.MaxValue") {
      val result = Into.derived[LongValue, IntValue].into(LongValue(Long.MaxValue))
      assert(result)(isLeft)
    },
    test("fails when Long value is below Int.MinValue") {
      val result = Into.derived[LongValue, IntValue].into(LongValue(Long.MinValue))
      assert(result)(isLeft)
    },
    test("succeeds at Int boundary values") {
      val max = Into.derived[LongValue, IntValue].into(LongValue(Int.MaxValue.toLong))
      val min = Into.derived[LongValue, IntValue].into(LongValue(Int.MinValue.toLong))
      assert(max)(isRight(equalTo(IntValue(Int.MaxValue)))) &&
      assert(min)(isRight(equalTo(IntValue(Int.MinValue))))
    }
  )
}
