package zio.blocks.schema.into.collections

import zio.blocks.schema._
import zio.test._
import zio.test.Assertion._

/** Tests for Array type conversions. */
object VectorToArraySpec extends ZIOSpecDefault {

  def spec: Spec[TestEnvironment, Any] = suite("VectorToArraySpec")(
    test("Vector[Int] to Array[Int]") {
      val result = Into[Vector[Int], Array[Int]].into(Vector(1, 2, 3))
      assert(result.map(_.toList))(isRight(equalTo(List(1, 2, 3))))
    },
    test("Array[Int] to Vector[Int]") {
      val result = Into[Array[Int], Vector[Int]].into(Array(1, 2, 3))
      assert(result)(isRight(equalTo(Vector(1, 2, 3))))
    },
    test("Vector[Int] to Array[Long] with coercion") {
      val result = Into[Vector[Int], Array[Long]].into(Vector(1, 2, 3))
      assert(result.map(_.toList))(isRight(equalTo(List(1L, 2L, 3L))))
    },
    test("case class with Vector field to case class with Array field") {
      case class Source(items: Vector[Int])
      case class Target(items: Array[Long])

      val result = Into.derived[Source, Target].into(Source(Vector(1, 2, 3)))
      assert(result.map(_.items.toList))(isRight(equalTo(List(1L, 2L, 3L))))
    }
  )
}
