package zio.blocks.schema.into.collections

import zio.blocks.schema._
import zio.test._
import zio.test.Assertion._

/** Tests for nested collection type conversions. */
object NestedCollectionTypeSpec extends ZIOSpecDefault {

  def spec: Spec[TestEnvironment, Any] = suite("NestedCollectionTypeSpec")(
    test("List[List[Int]] to Vector[Vector[Long]]") {
      val result = Into[List[List[Int]], Vector[Vector[Long]]].into(List(List(1, 2), List(3, 4)))
      assert(result)(isRight(equalTo(Vector(Vector(1L, 2L), Vector(3L, 4L)))))
    },
    test("List[Option[Int]] to Vector[Option[Long]]") {
      val result = Into[List[Option[Int]], Vector[Option[Long]]].into(List(Some(1), None, Some(3)))
      assert(result)(isRight(equalTo(Vector(Some(1L), None, Some(3L)))))
    },
    test("Map[String, List[Int]] to Map[String, Vector[Long]]") {
      val result = Into[Map[String, List[Int]], Map[String, Vector[Long]]].into(Map("nums" -> List(1, 2)))
      assert(result)(isRight(equalTo(Map("nums" -> Vector(1L, 2L)))))
    },
    test("case class with nested collections") {
      case class Source(matrix: List[List[Int]])
      case class Target(matrix: Vector[Vector[Long]])

      val result = Into.derived[Source, Target].into(Source(List(List(1, 2), List(3))))
      assert(result)(isRight(equalTo(Target(Vector(Vector(1L, 2L), Vector(3L))))))
    }
  )
}
