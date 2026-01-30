package zio.blocks.schema.into.collections

import zio.blocks.schema._
import zio.test._
import zio.test.Assertion._

/**
 * Tests for List to Vector type conversions.
 *
 * Covers:
 *   - List[A] → Vector[A] (same element type)
 *   - List[A] → Vector[B] (with element coercion)
 *   - Vector[A] → List[A] (reverse conversion)
 *   - Nested collection type conversions
 */
object ListToVectorSpec extends ZIOSpecDefault {

  // Test data types
  case class PersonV1(name: String, age: Int)
  case class PersonV2(name: String, age: Long)

  implicit val personV1ToV2: Into[PersonV1, PersonV2] = Into.derived[PersonV1, PersonV2]

  def spec: Spec[TestEnvironment, Any] = suite("ListToVectorSpec")(
    suite("List to Vector - Same Element Type")(
      test("converts List[Int] to Vector[Int]") {
        val source = List(1, 2, 3, 4, 5)
        val result = Into[List[Int], Vector[Int]].into(source)

        assert(result)(isRight(equalTo(Vector(1, 2, 3, 4, 5))))
      },
      test("converts empty List to empty Vector") {
        val source = List.empty[String]
        val result = Into[List[String], Vector[String]].into(source)

        assert(result)(isRight(equalTo(Vector.empty[String])))
      },
      test("converts List[String] to Vector[String]") {
        val source = List("a", "b", "c")
        val result = Into[List[String], Vector[String]].into(source)

        assert(result)(isRight(equalTo(Vector("a", "b", "c"))))
      },
      test("preserves element order") {
        val source = List(5, 4, 3, 2, 1)
        val result = Into[List[Int], Vector[Int]].into(source)

        assert(result)(isRight(equalTo(Vector(5, 4, 3, 2, 1))))
      }
    ),
    suite("List to Vector - With Element Coercion")(
      test("converts List[Int] to Vector[Long]") {
        val source = List(1, 2, 3)
        val result = Into[List[Int], Vector[Long]].into(source)

        assert(result)(isRight(equalTo(Vector(1L, 2L, 3L))))
      },
      test("converts List[Byte] to Vector[Int]") {
        val source = List(1.toByte, 2.toByte, 3.toByte)
        val result = Into[List[Byte], Vector[Int]].into(source)

        assert(result)(isRight(equalTo(Vector(1, 2, 3))))
      },
      test("converts List[Float] to Vector[Double]") {
        val source = List(1.0f, 2.5f, 3.14f)
        val result = Into[List[Float], Vector[Double]].into(source)

        assert(result)(isRight(equalTo(Vector(1.0f.toDouble, 2.5f.toDouble, 3.14f.toDouble))))
      },
      test("converts List[CaseClass] to Vector[CaseClass] with coercion") {
        val source = List(PersonV1("Alice", 30), PersonV1("Bob", 25))
        val result = Into[List[PersonV1], Vector[PersonV2]].into(source)

        assert(result)(isRight(equalTo(Vector(PersonV2("Alice", 30L), PersonV2("Bob", 25L)))))
      }
    ),
    suite("List to Vector - Narrowing")(
      test("narrows List[Long] to Vector[Int] when all fit") {
        val source = List(1L, 2L, 3L)
        val result = Into[List[Long], Vector[Int]].into(source)

        assert(result)(isRight(equalTo(Vector(1, 2, 3))))
      },
      test("fails when any element overflows during narrowing") {
        val source = List(1L, Long.MaxValue, 3L)
        val result = Into[List[Long], Vector[Int]].into(source)

        assert(result)(isLeft)
      }
    ),
    suite("Vector to List - Same Element Type")(
      test("converts Vector[Int] to List[Int]") {
        val source = Vector(1, 2, 3, 4, 5)
        val result = Into[Vector[Int], List[Int]].into(source)

        assert(result)(isRight(equalTo(List(1, 2, 3, 4, 5))))
      },
      test("converts empty Vector to empty List") {
        val source = Vector.empty[String]
        val result = Into[Vector[String], List[String]].into(source)

        assert(result)(isRight(equalTo(List.empty[String])))
      }
    ),
    suite("Vector to List - With Element Coercion")(
      test("converts Vector[Int] to List[Long]") {
        val source = Vector(10, 20, 30)
        val result = Into[Vector[Int], List[Long]].into(source)

        assert(result)(isRight(equalTo(List(10L, 20L, 30L))))
      },
      test("converts Vector[CaseClass] to List[CaseClass] with coercion") {
        val source = Vector(PersonV1("Charlie", 35))
        val result = Into[Vector[PersonV1], List[PersonV2]].into(source)

        assert(result)(isRight(equalTo(List(PersonV2("Charlie", 35L)))))
      }
    ),
    suite("In Products")(
      test("converts case class with List field to case class with Vector field") {
        case class Source(items: List[Int])
        case class Target(items: Vector[Long])

        val source = Source(List(1, 2, 3))
        val result = Into.derived[Source, Target].into(source)

        assert(result)(isRight(equalTo(Target(Vector(1L, 2L, 3L)))))
      },
      test("converts case class with Vector field to case class with List field") {
        case class Source(items: Vector[Int])
        case class Target(items: List[Long])

        val source = Source(Vector(10, 20))
        val result = Into.derived[Source, Target].into(source)

        assert(result)(isRight(equalTo(Target(List(10L, 20L)))))
      }
    ),
    suite("Nested Collections")(
      test("converts List[List[Int]] to Vector[Vector[Long]]") {
        val source = List(List(1, 2), List(3, 4))
        val result = Into[List[List[Int]], Vector[Vector[Long]]].into(source)

        assert(result)(isRight(equalTo(Vector(Vector(1L, 2L), Vector(3L, 4L)))))
      },
      test("converts Vector[List[Int]] to List[Vector[Long]]") {
        val source = Vector(List(1, 2), List(3))
        val result = Into[Vector[List[Int]], List[Vector[Long]]].into(source)

        assert(result)(isRight(equalTo(List(Vector(1L, 2L), Vector(3L)))))
      }
    ),
    suite("Large Collections")(
      test("converts large List to Vector") {
        val source = (1 to 10000).toList
        val result = Into[List[Int], Vector[Int]].into(source)

        assert(result.map(_.size))(isRight(equalTo(10000))) &&
        assert(result.map(_.head))(isRight(equalTo(1))) &&
        assert(result.map(_.last))(isRight(equalTo(10000)))
      },
      test("converts large Vector to List") {
        val source = (1 to 10000).toVector
        val result = Into[Vector[Int], List[Int]].into(source)

        assert(result.map(_.size))(isRight(equalTo(10000)))
      }
    )
  )
}
