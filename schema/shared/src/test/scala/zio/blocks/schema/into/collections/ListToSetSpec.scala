package zio.blocks.schema.into.collections

import zio.blocks.schema._
import zio.test._
import zio.test.Assertion._

/** Tests for collection type conversions (List, Set, Vector). */
object ListToSetSpec extends ZIOSpecDefault {

  def spec: Spec[TestEnvironment, Any] = suite("ListToSetSpec")(
    test("List[Int] to Set[Int]") {
      val result = Into[List[Int], Set[Int]].into(List(1, 2, 2, 3))
      assert(result)(isRight(equalTo(Set(1, 2, 3))))
    },
    test("Set[Int] to List[Int]") {
      val result = Into[Set[Int], List[Int]].into(Set(1, 2, 3))
      assert(result.map(_.toSet))(isRight(equalTo(Set(1, 2, 3))))
    },
    test("List[Int] to Set[Long] with element coercion") {
      val result = Into[List[Int], Set[Long]].into(List(1, 2, 3))
      assert(result)(isRight(equalTo(Set(1L, 2L, 3L))))
    },
    test("Vector[Int] to Set[Int]") {
      val result = Into[Vector[Int], Set[Int]].into(Vector(1, 2, 2, 3))
      assert(result)(isRight(equalTo(Set(1, 2, 3))))
    },
    test("case class with List field to case class with Set field") {
      case class Source(items: List[Int])
      case class Target(items: Set[Long])

      val result = Into.derived[Source, Target].into(Source(List(1, 2, 2, 3)))
      assert(result)(isRight(equalTo(Target(Set(1L, 2L, 3L)))))
    }
  )
}
