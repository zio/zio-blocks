package zio.blocks.schema.into.collections

import zio.blocks.schema._
import zio.test._
import zio.test.Assertion._

/**
 * Tests for Vector to Array type conversions.
 *
 * Covers:
 *   - Vector[A] → Array[A] (same element type)
 *   - Vector[A] → Array[B] (with element coercion)
 *   - Array[A] → Vector[A] (reverse conversion)
 *   - List[A] → Array[A] and Array[A] → List[A]
 */
object VectorToArraySpec extends ZIOSpecDefault {

  // Test data types
  case class PersonV1(name: String, age: Int)
  case class PersonV2(name: String, age: Long)

  implicit val personV1ToV2: Into[PersonV1, PersonV2] = Into.derived[PersonV1, PersonV2]

  def spec: Spec[TestEnvironment, Any] = suite("VectorToArraySpec")(
    suite("Vector to Array - Same Element Type")(
      test("converts Vector[Int] to Array[Int]") {
        val source = Vector(1, 2, 3, 4, 5)
        val result = Into[Vector[Int], Array[Int]].into(source)

        assert(result.map(_.toList))(isRight(equalTo(List(1, 2, 3, 4, 5))))
      },
      test("converts empty Vector to empty Array") {
        val source = Vector.empty[Int]
        val result = Into[Vector[Int], Array[Int]].into(source)

        assert(result.map(_.length))(isRight(equalTo(0)))
      },
      test("converts Vector[String] to Array[String]") {
        val source = Vector("a", "b", "c")
        val result = Into[Vector[String], Array[String]].into(source)

        assert(result.map(_.toList))(isRight(equalTo(List("a", "b", "c"))))
      },
      test("preserves element order") {
        val source = Vector(5, 4, 3, 2, 1)
        val result = Into[Vector[Int], Array[Int]].into(source)

        assert(result.map(_.toList))(isRight(equalTo(List(5, 4, 3, 2, 1))))
      }
    ),
    suite("Vector to Array - With Element Coercion")(
      test("converts Vector[Int] to Array[Long]") {
        val source = Vector(1, 2, 3)
        val result = Into[Vector[Int], Array[Long]].into(source)

        assert(result.map(_.toList))(isRight(equalTo(List(1L, 2L, 3L))))
      },
      test("converts Vector[Byte] to Array[Int]") {
        val source = Vector(1.toByte, 2.toByte, 3.toByte)
        val result = Into[Vector[Byte], Array[Int]].into(source)

        assert(result.map(_.toList))(isRight(equalTo(List(1, 2, 3))))
      },
      test("converts Vector[Float] to Array[Double]") {
        val source = Vector(1.0f, 2.5f)
        val result = Into[Vector[Float], Array[Double]].into(source)

        assert(result.map(_.toList))(isRight(equalTo(List(1.0f.toDouble, 2.5f.toDouble))))
      }
    ),
    suite("Vector to Array - Narrowing")(
      test("narrows Vector[Long] to Array[Int] when all fit") {
        val source = Vector(1L, 2L, 3L)
        val result = Into[Vector[Long], Array[Int]].into(source)

        assert(result.map(_.toList))(isRight(equalTo(List(1, 2, 3))))
      },
      test("fails when any element overflows during narrowing") {
        val source = Vector(1L, Long.MaxValue, 3L)
        val result = Into[Vector[Long], Array[Int]].into(source)

        assert(result)(isLeft)
      }
    ),
    suite("Array to Vector - Same Element Type")(
      test("converts Array[Int] to Vector[Int]") {
        val source = Array(1, 2, 3, 4, 5)
        val result = Into[Array[Int], Vector[Int]].into(source)

        assert(result)(isRight(equalTo(Vector(1, 2, 3, 4, 5))))
      },
      test("converts empty Array to empty Vector") {
        val source = Array.empty[Int]
        val result = Into[Array[Int], Vector[Int]].into(source)

        assert(result)(isRight(equalTo(Vector.empty[Int])))
      }
    ),
    suite("Array to Vector - With Element Coercion")(
      test("converts Array[Int] to Vector[Long]") {
        val source = Array(10, 20, 30)
        val result = Into[Array[Int], Vector[Long]].into(source)

        assert(result)(isRight(equalTo(Vector(10L, 20L, 30L))))
      },
      test("converts Array[Byte] to Vector[Int]") {
        val source = Array(1.toByte, 2.toByte)
        val result = Into[Array[Byte], Vector[Int]].into(source)

        assert(result)(isRight(equalTo(Vector(1, 2))))
      }
    ),
    suite("List to Array")(
      test("converts List[Int] to Array[Int]") {
        val source = List(1, 2, 3)
        val result = Into[List[Int], Array[Int]].into(source)

        assert(result.map(_.toList))(isRight(equalTo(List(1, 2, 3))))
      },
      test("converts List[Int] to Array[Long] with coercion") {
        val source = List(1, 2, 3)
        val result = Into[List[Int], Array[Long]].into(source)

        assert(result.map(_.toList))(isRight(equalTo(List(1L, 2L, 3L))))
      },
      test("converts empty List to empty Array") {
        val source = List.empty[String]
        val result = Into[List[String], Array[String]].into(source)

        assert(result.map(_.length))(isRight(equalTo(0)))
      }
    ),
    suite("Array to List")(
      test("converts Array[Int] to List[Int]") {
        val source = Array(1, 2, 3)
        val result = Into[Array[Int], List[Int]].into(source)

        assert(result)(isRight(equalTo(List(1, 2, 3))))
      },
      test("converts Array[Int] to List[Long] with coercion") {
        val source = Array(10, 20)
        val result = Into[Array[Int], List[Long]].into(source)

        assert(result)(isRight(equalTo(List(10L, 20L))))
      },
      test("converts empty Array to empty List") {
        val source = Array.empty[Int]
        val result = Into[Array[Int], List[Int]].into(source)

        assert(result)(isRight(equalTo(List.empty[Int])))
      }
    ),
    suite("Set to Array")(
      test("converts Set[Int] to Array[Int]") {
        val source = Set(1, 2, 3)
        val result = Into[Set[Int], Array[Int]].into(source)

        assert(result.map(_.toSet))(isRight(equalTo(Set(1, 2, 3))))
      },
      test("converts Set[Int] to Array[Long] with coercion") {
        val source = Set(10, 20)
        val result = Into[Set[Int], Array[Long]].into(source)

        assert(result.map(_.toSet))(isRight(equalTo(Set(10L, 20L))))
      }
    ),
    suite("Array to Set")(
      test("converts Array[Int] to Set[Int]") {
        val source = Array(1, 2, 2, 3)
        val result = Into[Array[Int], Set[Int]].into(source)

        assert(result)(isRight(equalTo(Set(1, 2, 3))))
      },
      test("converts Array[Int] to Set[Long] with coercion") {
        val source = Array(1, 2, 3)
        val result = Into[Array[Int], Set[Long]].into(source)

        assert(result)(isRight(equalTo(Set(1L, 2L, 3L))))
      }
    ),
    suite("In Products")(
      test("converts case class with Vector field to case class with Array field") {
        case class Source(items: Vector[Int])
        case class Target(items: Array[Long])

        val source = Source(Vector(1, 2, 3))
        val result = Into.derived[Source, Target].into(source)

        assert(result.map(_.items.toList))(isRight(equalTo(List(1L, 2L, 3L))))
      },
      test("converts case class with Array field to case class with Vector field") {
        case class Source(items: Array[Int])
        case class Target(items: Vector[Long])

        val source = Source(Array(10, 20))
        val result = Into.derived[Source, Target].into(source)

        assert(result)(isRight(equalTo(Target(Vector(10L, 20L)))))
      },
      test("converts case class with List field to case class with Array field") {
        case class Source(items: List[Int])
        case class Target(items: Array[Long])

        val source = Source(List(1, 2))
        val result = Into.derived[Source, Target].into(source)

        assert(result.map(_.items.toList))(isRight(equalTo(List(1L, 2L))))
      }
    ),
    suite("Array to Array")(
      test("converts Array[Int] to Array[Int] (same type)") {
        val source = Array(1, 2, 3)
        val result = Into[Array[Int], Array[Int]].into(source)

        assert(result.map(_.toList))(isRight(equalTo(List(1, 2, 3))))
      },
      test("converts Array[Int] to Array[Long] with coercion") {
        val source = Array(1, 2, 3)
        val result = Into[Array[Int], Array[Long]].into(source)

        assert(result.map(_.toList))(isRight(equalTo(List(1L, 2L, 3L))))
      },
      test("narrows Array[Long] to Array[Int] when all fit") {
        val source = Array(1L, 2L, 3L)
        val result = Into[Array[Long], Array[Int]].into(source)

        assert(result.map(_.toList))(isRight(equalTo(List(1, 2, 3))))
      },
      test("fails Array[Long] to Array[Int] when overflow") {
        val source = Array(Long.MaxValue)
        val result = Into[Array[Long], Array[Int]].into(source)

        assert(result)(isLeft)
      }
    )
  )
}
