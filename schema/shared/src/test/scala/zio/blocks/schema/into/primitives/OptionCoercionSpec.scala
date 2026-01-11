package zio.blocks.schema.into.primitives

import zio.blocks.schema._
import zio.test._
import zio.test.Assertion._

/** Tests for Option type coercion in Into conversions. */
object OptionCoercionSpec extends ZIOSpecDefault {

  def spec: Spec[TestEnvironment, Any] = suite("OptionCoercionSpec")(
    test("coerces Some[Int] to Some[Long]") {
      val source: Option[Int] = Some(42)
      val result              = Into[Option[Int], Option[Long]].into(source)
      assert(result)(isRight(equalTo(Some(42L))))
    },
    test("coerces None[Int] to None[Long]") {
      val source: Option[Int] = None
      val result              = Into[Option[Int], Option[Long]].into(source)
      assert(result)(isRight(equalTo(None)))
    },
    test("fails when narrowing Some[Long] overflows Int") {
      val source: Option[Long] = Some(Long.MaxValue)
      val result               = Into[Option[Long], Option[Int]].into(source)
      assert(result)(isLeft)
    },
    test("None passes even with narrowing conversion") {
      val source: Option[Long] = None
      val result               = Into[Option[Long], Option[Int]].into(source)
      assert(result)(isRight(equalTo(None)))
    }
  )
}
