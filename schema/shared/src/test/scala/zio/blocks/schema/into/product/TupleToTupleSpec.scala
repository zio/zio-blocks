package zio.blocks.schema.into.product

import zio.blocks.schema._
import zio.test._
import zio.test.Assertion._

/** Tests for tuple to tuple conversions. */
object TupleToTupleSpec extends ZIOSpecDefault {

  def spec: Spec[TestEnvironment, Any] = suite("TupleToTupleSpec")(
    test("converts Tuple2 to Tuple2 with same types") {
      val result = Into.derived[(Int, String), (Int, String)].into((42, "hello"))
      assert(result)(isRight(equalTo((42, "hello"))))
    },
    test("widens Int to Long in tuple element") {
      val result = Into.derived[(Int, String), (Long, String)].into((42, "hello"))
      assert(result)(isRight(equalTo((42L, "hello"))))
    },
    test("widens multiple elements") {
      val result = Into.derived[(Int, Short, Byte), (Long, Int, Short)].into((10, 20.toShort, 30.toByte))
      assert(result)(isRight(equalTo((10L, 20, 30.toShort))))
    },
    test("fails when narrowing would overflow") {
      val result = Into.derived[(Long, String), (Int, String)].into((Long.MaxValue, "test"))
      assert(result)(isLeft)
    }
  )
}
