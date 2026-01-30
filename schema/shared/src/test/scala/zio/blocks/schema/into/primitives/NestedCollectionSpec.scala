package zio.blocks.schema.into.primitives

import zio.blocks.schema._
import zio.test._
import zio.test.Assertion._

/**
 * Tests for nested collection coercion in Into conversions.
 *
 * Covers:
 *   - List[List[A]] → List[List[B]]
 *   - List[Option[A]] → List[Option[B]]
 *   - Option[List[A]] → Option[List[B]]
 *   - Deeply nested structures
 *   - Collections containing products
 */
object NestedCollectionSpec extends ZIOSpecDefault {

  // Test data types
  case class PersonV1(name: String, age: Int)
  case class PersonV2(name: String, age: Long)

  implicit val personV1ToV2: Into[PersonV1, PersonV2] = Into.derived[PersonV1, PersonV2]

  def spec: Spec[TestEnvironment, Any] = suite("NestedCollectionSpec")(
    suite("List of Lists")(
      test("coerces List[List[Int]] to List[List[Long]]") {
        val source = List(List(1, 2), List(3, 4, 5))
        val result = Into.derived[List[List[Int]], List[List[Long]]].into(source)

        assert(result)(isRight(equalTo(List(List(1L, 2L), List(3L, 4L, 5L)))))
      },
      test("coerces empty outer List") {
        val source = List.empty[List[Int]]
        val result = Into.derived[List[List[Int]], List[List[Long]]].into(source)

        assert(result)(isRight(equalTo(List.empty[List[Long]])))
      },
      test("coerces List containing empty inner Lists") {
        val source = List(List.empty[Int], List(1), List.empty[Int])
        val result = Into.derived[List[List[Int]], List[List[Long]]].into(source)

        assert(result)(isRight(equalTo(List(List.empty[Long], List(1L), List.empty[Long]))))
      },
      test("fails when any nested element fails narrowing") {
        val source = List(List(1L, 2L), List(Long.MaxValue))
        val result = Into.derived[List[List[Long]], List[List[Int]]].into(source)

        assert(result)(isLeft)
      }
    ),
    suite("List of Options")(
      test("coerces List[Option[Int]] to List[Option[Long]]") {
        val source = List(Some(1), None, Some(3))
        val result = Into.derived[List[Option[Int]], List[Option[Long]]].into(source)

        assert(result)(isRight(equalTo(List(Some(1L), None, Some(3L)))))
      },
      test("coerces List of all None") {
        val source: List[Option[Int]] = List(None, None, None)
        val result                    = Into.derived[List[Option[Int]], List[Option[Long]]].into(source)

        assert(result)(isRight(equalTo(List(None, None, None))))
      },
      test("coerces List of all Some") {
        val source = List(Some(10), Some(20), Some(30))
        val result = Into.derived[List[Option[Int]], List[Option[Long]]].into(source)

        assert(result)(isRight(equalTo(List(Some(10L), Some(20L), Some(30L)))))
      }
    ),
    suite("Option of Lists")(
      test("coerces Some[List[Int]] to Some[List[Long]]") {
        val source: Option[List[Int]] = Some(List(1, 2, 3))
        val result                    = Into.derived[Option[List[Int]], Option[List[Long]]].into(source)

        assert(result)(isRight(equalTo(Some(List(1L, 2L, 3L)))))
      },
      test("coerces None[List[Int]] to None[List[Long]]") {
        val source: Option[List[Int]] = None
        val result                    = Into.derived[Option[List[Int]], Option[List[Long]]].into(source)

        assert(result)(isRight(equalTo(None)))
      },
      test("coerces Some with empty List") {
        val source: Option[List[Int]] = Some(List.empty)
        val result                    = Into.derived[Option[List[Int]], Option[List[Long]]].into(source)

        assert(result)(isRight(equalTo(Some(List.empty[Long]))))
      }
    ),
    suite("Vector of Vectors")(
      test("coerces Vector[Vector[Byte]] to Vector[Vector[Int]]") {
        val source = Vector(Vector(1.toByte, 2.toByte), Vector(3.toByte))
        val result = Into.derived[Vector[Vector[Byte]], Vector[Vector[Int]]].into(source)

        assert(result)(isRight(equalTo(Vector(Vector(1, 2), Vector(3)))))
      }
    ),
    suite("Set of Options")(
      test("coerces Set[Option[Int]] to Set[Option[Long]]") {
        val source = Set(Some(1), Some(2), None)
        val result = Into.derived[Set[Option[Int]], Set[Option[Long]]].into(source)

        assert(result)(isRight(equalTo(Set(Some(1L), Some(2L), None))))
      }
    ),
    suite("Map with Nested Collections")(
      test("coerces Map[String, List[Int]] to Map[String, List[Long]]") {
        val source = Map("a" -> List(1, 2), "b" -> List(3, 4, 5))
        val result = Into.derived[Map[String, List[Int]], Map[String, List[Long]]].into(source)

        assert(result)(isRight(equalTo(Map("a" -> List(1L, 2L), "b" -> List(3L, 4L, 5L)))))
      },
      test("coerces Map[String, Option[Int]] to Map[String, Option[Long]]") {
        val source = Map("x" -> Some(1), "y" -> None)
        val result = Into.derived[Map[String, Option[Int]], Map[String, Option[Long]]].into(source)

        assert(result)(isRight(equalTo(Map("x" -> Some(1L), "y" -> None))))
      }
    ),
    suite("Triple Nesting")(
      test("coerces List[List[List[Int]]] to List[List[List[Long]]]") {
        val source = List(List(List(1, 2), List(3)), List(List(4, 5, 6)))
        val result = Into.derived[List[List[List[Int]]], List[List[List[Long]]]].into(source)

        assert(result)(isRight(equalTo(List(List(List(1L, 2L), List(3L)), List(List(4L, 5L, 6L))))))
      },
      test("coerces Option[Option[Option[Int]]] to Option[Option[Option[Long]]]") {
        val source: Option[Option[Option[Int]]] = Some(Some(Some(42)))
        val result                              = Into.derived[Option[Option[Option[Int]]], Option[Option[Option[Long]]]].into(source)

        assert(result)(isRight(equalTo(Some(Some(Some(42L))))))
      },
      test("coerces deeply nested None") {
        val source: Option[Option[Option[Int]]] = Some(Some(None))
        val result                              = Into.derived[Option[Option[Option[Int]]], Option[Option[Option[Long]]]].into(source)

        assert(result)(isRight(equalTo(Some(Some(None)))))
      }
    ),
    suite("Collections with Products")(
      test("coerces List[CaseClass] with nested type coercion") {
        val source = List(PersonV1("Alice", 30), PersonV1("Bob", 25))
        val result = Into.derived[List[PersonV1], List[PersonV2]].into(source)

        assert(result)(isRight(equalTo(List(PersonV2("Alice", 30L), PersonV2("Bob", 25L)))))
      },
      test("coerces Option[List[CaseClass]]") {
        val source: Option[List[PersonV1]] = Some(List(PersonV1("Charlie", 35)))
        val result                         = Into.derived[Option[List[PersonV1]], Option[List[PersonV2]]].into(source)

        assert(result)(isRight(equalTo(Some(List(PersonV2("Charlie", 35L))))))
      },
      test("coerces List[Option[CaseClass]]") {
        val source = List(Some(PersonV1("Dave", 40)), None)
        val result = Into.derived[List[Option[PersonV1]], List[Option[PersonV2]]].into(source)

        assert(result)(isRight(equalTo(List(Some(PersonV2("Dave", 40L)), None))))
      },
      test("coerces Map[String, List[CaseClass]]") {
        val source = Map("people" -> List(PersonV1("Eve", 28)))
        val result = Into.derived[Map[String, List[PersonV1]], Map[String, List[PersonV2]]].into(source)

        assert(result)(isRight(equalTo(Map("people" -> List(PersonV2("Eve", 28L))))))
      }
    ),
    suite("Products with Nested Collections")(
      test("coerces case class with List[List[Int]] field") {
        case class Source(matrix: List[List[Int]])
        case class Target(matrix: List[List[Long]])

        val source = Source(List(List(1, 2), List(3, 4)))
        val result = Into.derived[Source, Target].into(source)

        assert(result)(isRight(equalTo(Target(List(List(1L, 2L), List(3L, 4L))))))
      },
      test("coerces case class with Option[List[Int]] field") {
        case class Source(values: Option[List[Int]])
        case class Target(values: Option[List[Long]])

        val source = Source(Some(List(1, 2, 3)))
        val result = Into.derived[Source, Target].into(source)

        assert(result)(isRight(equalTo(Target(Some(List(1L, 2L, 3L))))))
      },
      test("coerces case class with Map[String, Option[Int]] field") {
        case class Source(config: Map[String, Option[Int]])
        case class Target(config: Map[String, Option[Long]])

        val source = Source(Map("a" -> Some(1), "b" -> None))
        val result = Into.derived[Source, Target].into(source)

        assert(result)(isRight(equalTo(Target(Map("a" -> Some(1L), "b" -> None)))))
      }
    ),
    suite("Error Propagation in Nested Collections")(
      test("fails early when nested narrowing overflows") {
        val source = List(List(1L), List(Long.MaxValue))
        val result = Into.derived[List[List[Long]], List[List[Int]]].into(source)

        assert(result)(isLeft)
      },
      test("fails when Option content fails") {
        val source: Option[List[Long]] = Some(List(Long.MaxValue))
        val result                     = Into.derived[Option[List[Long]], Option[List[Int]]].into(source)

        assert(result)(isLeft)
      }
    )
  )
}
