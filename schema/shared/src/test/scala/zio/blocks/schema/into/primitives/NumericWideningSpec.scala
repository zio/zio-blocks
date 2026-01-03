package zio.blocks.schema.into.primitives

import zio.blocks.schema._
import zio.test._
import zio.test.Assertion._

/** Tests for numeric widening conversions. */
object NumericWideningSpec extends ZIOSpecDefault {

  def spec: Spec[TestEnvironment, Any] = suite("NumericWideningSpec")(
    test("widens Byte to Int") {
      val result = Into[Byte, Int].into(42.toByte)
      assert(result)(isRight(equalTo(42)))
    },
    test("widens Short to Long") {
      val result = Into[Short, Long].into(1000.toShort)
      assert(result)(isRight(equalTo(1000L)))
    },
    test("widens Int to Long") {
      val result = Into[Int, Long].into(Int.MaxValue)
      assert(result)(isRight(equalTo(Int.MaxValue.toLong)))
    },
    test("widens Float to Double") {
      val result = Into[Float, Double].into(3.14f)
      assert(result)(isRight(equalTo(3.14f.toDouble)))
    }
  )
}
