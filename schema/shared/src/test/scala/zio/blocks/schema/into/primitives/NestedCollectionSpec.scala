package zio.blocks.schema.into.primitives

import zio.blocks.schema._
import zio.test._
import zio.test.Assertion._

/** Tests for nested collection coercion in Into conversions. */
object NestedCollectionSpec extends ZIOSpecDefault {

  def spec: Spec[TestEnvironment, Any] = suite("NestedCollectionSpec")(
    test("coerces List[List[Int]] to List[List[Long]]") {
      val result = Into[List[List[Int]], List[List[Long]]].into(List(List(1, 2), List(3, 4)))
      assert(result)(isRight(equalTo(List(List(1L, 2L), List(3L, 4L)))))
    },
    test("coerces List[Option[Int]] to List[Option[Long]]") {
      val result = Into[List[Option[Int]], List[Option[Long]]].into(List(Some(1), None, Some(3)))
      assert(result)(isRight(equalTo(List(Some(1L), None, Some(3L)))))
    },
    test("coerces Option[List[Int]] to Option[List[Long]]") {
      val source: Option[List[Int]] = Some(List(1, 2, 3))
      val result                    = Into[Option[List[Int]], Option[List[Long]]].into(source)
      assert(result)(isRight(equalTo(Some(List(1L, 2L, 3L)))))
    },
    test("fails when any nested element fails narrowing") {
      val result = Into[List[List[Long]], List[List[Int]]].into(List(List(1L), List(Long.MaxValue)))
      assert(result)(isLeft)
    }
  )
}
