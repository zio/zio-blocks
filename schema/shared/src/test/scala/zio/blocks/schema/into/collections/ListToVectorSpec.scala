package zio.blocks.schema.into.collections

import zio.blocks.schema._
import zio.test._
import zio.test.Assertion._

/** Tests for List to Vector type conversions. */
object ListToVectorSpec extends ZIOSpecDefault {

  def spec: Spec[TestEnvironment, Any] = suite("ListToVectorSpec")(
    test("List[Int] to Vector[Int]") {
      val result = Into[List[Int], Vector[Int]].into(List(1, 2, 3))
      assert(result)(isRight(equalTo(Vector(1, 2, 3))))
    },
    test("Vector[Int] to List[Int]") {
      val result = Into[Vector[Int], List[Int]].into(Vector(1, 2, 3))
      assert(result)(isRight(equalTo(List(1, 2, 3))))
    },
    test("List[Int] to Vector[Long] with element coercion") {
      val result = Into[List[Int], Vector[Long]].into(List(1, 2, 3))
      assert(result)(isRight(equalTo(Vector(1L, 2L, 3L))))
    },
    test("case class with List field to case class with Vector field") {
      case class Source(items: List[Int])
      case class Target(items: Vector[Long])

      val result = Into.derived[Source, Target].into(Source(List(1, 2, 3)))
      assert(result)(isRight(equalTo(Target(Vector(1L, 2L, 3L)))))
    }
  )
}
