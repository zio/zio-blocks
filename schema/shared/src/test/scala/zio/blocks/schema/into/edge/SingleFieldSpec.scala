package zio.blocks.schema.into.edge

import zio.blocks.schema._
import zio.test._
import zio.test.Assertion._

/** Tests for single-field case class conversions. */
object SingleFieldSpec extends ZIOSpecDefault {

  case class SingleInt(value: Int)
  case class SingleLong(value: Long)
  case class SingleString(name: String)
  case class SingleStringAlt(label: String)

  def spec: Spec[TestEnvironment, Any] = suite("SingleFieldSpec")(
    test("single field with same type") {
      val result = Into.derived[SingleInt, SingleInt].into(SingleInt(42))
      assert(result)(isRight(equalTo(SingleInt(42))))
    },
    test("single field with different name") {
      val result = Into.derived[SingleString, SingleStringAlt].into(SingleString("hello"))
      assert(result)(isRight(equalTo(SingleStringAlt("hello"))))
    },
    test("single field with coercion") {
      val result = Into.derived[SingleInt, SingleLong].into(SingleInt(100))
      assert(result)(isRight(equalTo(SingleLong(100L))))
    },
    test("single field to Tuple1") {
      val result = Into.derived[SingleInt, Tuple1[Int]].into(SingleInt(42))
      assert(result)(isRight(equalTo(Tuple1(42))))
    }
  )
}
