package zio.blocks.schema.into.primitives

import zio.blocks.schema._
import zio.test._
import zio.test.Assertion._

/** Tests for collection element coercion in Into conversions. */
object CollectionCoercionSpec extends ZIOSpecDefault {

  def spec: Spec[TestEnvironment, Any] = suite("CollectionCoercionSpec")(
    test("coerces List[Int] to List[Long]") {
      val result = Into[List[Int], List[Long]].into(List(1, 2, 3))
      assert(result)(isRight(equalTo(List(1L, 2L, 3L))))
    },
    test("coerces Vector[Int] to Vector[Long]") {
      val result = Into[Vector[Int], Vector[Long]].into(Vector(10, 20, 30))
      assert(result)(isRight(equalTo(Vector(10L, 20L, 30L))))
    },
    test("coerces Set[Int] to Set[Long]") {
      val result = Into[Set[Int], Set[Long]].into(Set(1, 2, 3))
      assert(result)(isRight(equalTo(Set(1L, 2L, 3L))))
    },
    test("fails when narrowing element fails") {
      val result = Into[List[Long], List[Int]].into(List(1L, Long.MaxValue))
      assert(result)(isLeft)
    }
  )
}
