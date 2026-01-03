package zio.blocks.schema.into.primitives

import zio.blocks.schema._
import zio.test._
import zio.test.Assertion._

/** Tests for numeric narrowing conversions. */
object NumericNarrowingSpec extends ZIOSpecDefault {

  def spec: Spec[TestEnvironment, Any] = suite("NumericNarrowingSpec")(
    test("narrows Long to Int when value fits") {
      val result = Into[Long, Int].into(42L)
      assert(result)(isRight(equalTo(42)))
    },
    test("fails when Long overflows Int") {
      val result = Into[Long, Int].into(Long.MaxValue)
      assert(result)(isLeft)
    },
    test("narrows Int to Short when value fits") {
      val result = Into[Int, Short].into(1000)
      assert(result)(isRight(equalTo(1000.toShort)))
    },
    test("fails when Int overflows Short") {
      val result = Into[Int, Short].into(Int.MaxValue)
      assert(result)(isLeft)
    },
    test("narrows Double to Float when value fits") {
      val result = Into[Double, Float].into(3.14)
      assert(result)(isRight(equalTo(3.14f)))
    }
  )
}
