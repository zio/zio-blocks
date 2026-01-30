package zio.blocks.schema.into.collections

import zio.blocks.schema._
import zio.test._
import zio.test.Assertion._

/**
 * Tests for List to Set type conversions.
 *
 * Covers:
 *   - List[A] → Set[A] (same element type)
 *   - List[A] → Set[B] (with element coercion)
 *   - Set[A] → List[A] (reverse conversion)
 *   - Duplicate handling behavior
 */
object ListToSetSpec extends ZIOSpecDefault {

  // Test data types
  case class PersonV1(name: String, age: Int)
  case class PersonV2(name: String, age: Long)

  implicit val personV1ToV2: Into[PersonV1, PersonV2] = Into.derived[PersonV1, PersonV2]

  def spec: Spec[TestEnvironment, Any] = suite("ListToSetSpec")(
    suite("List to Set - Same Element Type")(
      test("converts List[Int] to Set[Int]") {
        val source = List(1, 2, 3, 4, 5)
        val result = Into[List[Int], Set[Int]].into(source)

        assert(result)(isRight(equalTo(Set(1, 2, 3, 4, 5))))
      },
      test("converts empty List to empty Set") {
        val source = List.empty[String]
        val result = Into[List[String], Set[String]].into(source)

        assert(result)(isRight(equalTo(Set.empty[String])))
      },
      test("converts List[String] to Set[String]") {
        val source = List("a", "b", "c")
        val result = Into[List[String], Set[String]].into(source)

        assert(result)(isRight(equalTo(Set("a", "b", "c"))))
      }
    ),
    suite("List to Set - Duplicate Handling")(
      test("removes duplicates when converting List to Set") {
        val source = List(1, 2, 2, 3, 3, 3)
        val result = Into[List[Int], Set[Int]].into(source)

        assert(result)(isRight(equalTo(Set(1, 2, 3))))
      },
      test("removes duplicate strings") {
        val source = List("a", "b", "a", "c", "b")
        val result = Into[List[String], Set[String]].into(source)

        assert(result)(isRight(equalTo(Set("a", "b", "c"))))
      },
      test("single element List converts to single element Set") {
        val source = List(42, 42, 42)
        val result = Into[List[Int], Set[Int]].into(source)

        assert(result)(isRight(equalTo(Set(42))))
      }
    ),
    suite("List to Set - With Element Coercion")(
      test("converts List[Int] to Set[Long]") {
        val source = List(1, 2, 3)
        val result = Into[List[Int], Set[Long]].into(source)

        assert(result)(isRight(equalTo(Set(1L, 2L, 3L))))
      },
      test("converts List[Byte] to Set[Int]") {
        val source = List(1.toByte, 2.toByte, 3.toByte)
        val result = Into[List[Byte], Set[Int]].into(source)

        assert(result)(isRight(equalTo(Set(1, 2, 3))))
      },
      test("converts List[CaseClass] to Set[CaseClass] with coercion") {
        val source = List(PersonV1("Alice", 30), PersonV1("Bob", 25))
        val result = Into[List[PersonV1], Set[PersonV2]].into(source)

        assert(result)(isRight(equalTo(Set(PersonV2("Alice", 30L), PersonV2("Bob", 25L)))))
      },
      test("removes duplicates after coercion") {
        // 1, 2, 1, 3 -> after Int->Long coercion still has duplicate 1L
        val source = List(1, 2, 1, 3)
        val result = Into[List[Int], Set[Long]].into(source)

        assert(result)(isRight(equalTo(Set(1L, 2L, 3L))))
      }
    ),
    suite("List to Set - Narrowing")(
      test("narrows List[Long] to Set[Int] when all fit") {
        val source = List(1L, 2L, 3L)
        val result = Into[List[Long], Set[Int]].into(source)

        assert(result)(isRight(equalTo(Set(1, 2, 3))))
      },
      test("fails when any element overflows during narrowing") {
        val source = List(1L, Long.MaxValue, 3L)
        val result = Into[List[Long], Set[Int]].into(source)

        assert(result)(isLeft)
      }
    ),
    suite("Set to List")(
      test("converts Set[Int] to List[Int]") {
        val source = Set(1, 2, 3)
        val result = Into[Set[Int], List[Int]].into(source)

        // Set order is not guaranteed, so we check contents
        assert(result.map(_.toSet))(isRight(equalTo(Set(1, 2, 3))))
      },
      test("converts empty Set to empty List") {
        val source = Set.empty[String]
        val result = Into[Set[String], List[String]].into(source)

        assert(result)(isRight(equalTo(List.empty[String])))
      },
      test("converts Set[Int] to List[Long] with coercion") {
        val source = Set(10, 20, 30)
        val result = Into[Set[Int], List[Long]].into(source)

        assert(result.map(_.toSet))(isRight(equalTo(Set(10L, 20L, 30L))))
      }
    ),
    suite("Vector to Set")(
      test("converts Vector[Int] to Set[Int]") {
        val source = Vector(1, 2, 3, 2, 1)
        val result = Into[Vector[Int], Set[Int]].into(source)

        assert(result)(isRight(equalTo(Set(1, 2, 3))))
      },
      test("converts Vector[Int] to Set[Long] with coercion") {
        val source = Vector(1, 2, 3)
        val result = Into[Vector[Int], Set[Long]].into(source)

        assert(result)(isRight(equalTo(Set(1L, 2L, 3L))))
      }
    ),
    suite("Set to Vector")(
      test("converts Set[Int] to Vector[Int]") {
        val source = Set(1, 2, 3)
        val result = Into[Set[Int], Vector[Int]].into(source)

        assert(result.map(_.toSet))(isRight(equalTo(Set(1, 2, 3))))
      },
      test("converts Set[Int] to Vector[Long] with coercion") {
        val source = Set(10, 20)
        val result = Into[Set[Int], Vector[Long]].into(source)

        assert(result.map(_.toSet))(isRight(equalTo(Set(10L, 20L))))
      }
    ),
    suite("In Products")(
      test("converts case class with List field to case class with Set field") {
        case class Source(items: List[Int])
        case class Target(items: Set[Long])

        val source = Source(List(1, 2, 2, 3))
        val result = Into.derived[Source, Target].into(source)

        assert(result)(isRight(equalTo(Target(Set(1L, 2L, 3L)))))
      },
      test("converts case class with Set field to case class with List field") {
        case class Source(items: Set[Int])
        case class Target(items: List[Long])

        val source = Source(Set(10, 20))
        val result = Into.derived[Source, Target].into(source)

        assert(result.map(_.items.toSet))(isRight(equalTo(Set(10L, 20L))))
      }
    ),
    suite("Nested Collections")(
      test("converts List[List[Int]] to Set[Set[Long]]") {
        val source = List(List(1, 2), List(3, 4))
        val result = Into[List[List[Int]], Set[Set[Long]]].into(source)

        assert(result)(isRight(equalTo(Set(Set(1L, 2L), Set(3L, 4L)))))
      },
      test("converts Set[List[Int]] to List[Set[Long]]") {
        val source = Set(List(1, 2), List(3))
        val result = Into[Set[List[Int]], List[Set[Long]]].into(source)

        assert(result.map(_.toSet))(isRight(equalTo(Set(Set(1L, 2L), Set(3L)))))
      }
    )
  )
}
