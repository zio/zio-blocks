package zio.blocks.schema.into.collections

import zio.blocks.schema._
import zio.test._
import zio.test.Assertion._

/**
 * Tests for complex nested collection type conversions.
 *
 * Covers:
 *   - Multiple levels of collection type nesting
 *   - Mixed collection type conversions
 *   - Collections containing Options and Eithers
 *   - Collections in products with type conversions
 */
object NestedCollectionTypeSpec extends ZIOSpecDefault {

  // Test data types
  case class PersonV1(name: String, age: Int)
  case class PersonV2(name: String, age: Long)

  implicit val personV1ToV2: Into[PersonV1, PersonV2] = Into.derived[PersonV1, PersonV2]

  def spec: Spec[TestEnvironment, Any] = suite("NestedCollectionTypeSpec")(
    suite("Two-Level Nesting with Type Change")(
      test("converts List[List[Int]] to Vector[Vector[Long]]") {
        val source = List(List(1, 2), List(3, 4))
        val result = Into[List[List[Int]], Vector[Vector[Long]]].into(source)

        assert(result)(isRight(equalTo(Vector(Vector(1L, 2L), Vector(3L, 4L)))))
      },
      test("converts Vector[Set[Int]] to List[List[Long]]") {
        val source = Vector(Set(1, 2), Set(3))
        val result = Into[Vector[Set[Int]], List[List[Long]]].into(source)

        // Set order is not guaranteed, so check contents
        assert(result.map(_.map(_.toSet)))(isRight(equalTo(List(Set(1L, 2L), Set(3L)))))
      },
      test("converts List[Vector[Byte]] to Vector[List[Int]]") {
        val source = List(Vector(1.toByte, 2.toByte), Vector(3.toByte))
        val result = Into[List[Vector[Byte]], Vector[List[Int]]].into(source)

        assert(result)(isRight(equalTo(Vector(List(1, 2), List(3)))))
      },
      test("converts Set[List[Int]] to Vector[Set[Long]]") {
        val source = Set(List(1, 2), List(3, 4))
        val result = Into[Set[List[Int]], Vector[Set[Long]]].into(source)

        assert(result.map(_.toSet))(isRight(equalTo(Set(Set(1L, 2L), Set(3L, 4L)))))
      }
    ),
    suite("Three-Level Nesting with Type Change")(
      test("converts List[List[List[Int]]] to Vector[Vector[Vector[Long]]]") {
        val source = List(List(List(1, 2), List(3)), List(List(4, 5)))
        val result = Into[List[List[List[Int]]], Vector[Vector[Vector[Long]]]].into(source)

        assert(result)(isRight(equalTo(Vector(Vector(Vector(1L, 2L), Vector(3L)), Vector(Vector(4L, 5L))))))
      },
      test("converts Vector[List[Set[Int]]] to List[Vector[List[Long]]]") {
        val source = Vector(List(Set(1, 2)), List(Set(3)))
        val result = Into[Vector[List[Set[Int]]], List[Vector[List[Long]]]].into(source)

        // Convert to check contents since Set order is not guaranteed
        assert(result.map(_.map(_.map(_.toSet))))(isRight(equalTo(List(Vector(Set(1L, 2L)), Vector(Set(3L))))))
      }
    ),
    suite("Collections with Option")(
      test("converts List[Option[Int]] to Vector[Option[Long]]") {
        val source = List(Some(1), None, Some(3))
        val result = Into[List[Option[Int]], Vector[Option[Long]]].into(source)

        assert(result)(isRight(equalTo(Vector(Some(1L), None, Some(3L)))))
      },
      test("converts Option[List[Int]] to Option[Vector[Long]]") {
        val source: Option[List[Int]] = Some(List(1, 2, 3))
        val result                    = Into[Option[List[Int]], Option[Vector[Long]]].into(source)

        assert(result)(isRight(equalTo(Some(Vector(1L, 2L, 3L)))))
      },
      test("converts Vector[Option[List[Int]]] to List[Option[Vector[Long]]]") {
        val source = Vector(Some(List(1, 2)), None, Some(List(3)))
        val result = Into[Vector[Option[List[Int]]], List[Option[Vector[Long]]]].into(source)

        assert(result)(isRight(equalTo(List(Some(Vector(1L, 2L)), None, Some(Vector(3L))))))
      },
      test("handles None in nested structure") {
        val source: Option[List[Int]] = None
        val result                    = Into[Option[List[Int]], Option[Vector[Long]]].into(source)

        assert(result)(isRight(equalTo(None)))
      }
    ),
    suite("Collections with Either")(
      test("converts List[Either[String, Int]] to Vector[Either[String, Long]]") {
        val source: List[Either[String, Int]] = List(Right(1), Left("error"), Right(3))
        val result                            = Into[List[Either[String, Int]], Vector[Either[String, Long]]].into(source)

        assert(result)(isRight(equalTo(Vector(Right(1L), Left("error"), Right(3L)))))
      },
      test("converts Either[String, List[Int]] to Either[String, Vector[Long]]") {
        val source: Either[String, List[Int]] = Right(List(1, 2, 3))
        val result                            = Into[Either[String, List[Int]], Either[String, Vector[Long]]].into(source)

        assert(result)(isRight(equalTo(Right(Vector(1L, 2L, 3L)))))
      },
      test("converts Left case") {
        val source: Either[String, List[Int]] = Left("error")
        val result                            = Into[Either[String, List[Int]], Either[String, Vector[Long]]].into(source)

        assert(result)(isRight(equalTo(Left("error"))))
      }
    ),
    suite("Map with Collection Values")(
      test("converts Map[String, List[Int]] to Map[String, Vector[Long]]") {
        val source = Map("nums" -> List(1, 2, 3))
        val result = Into[Map[String, List[Int]], Map[String, Vector[Long]]].into(source)

        assert(result)(isRight(equalTo(Map("nums" -> Vector(1L, 2L, 3L)))))
      },
      test("converts Map[Int, Set[Int]] to Map[Long, List[Long]]") {
        val source = Map(1 -> Set(10, 20), 2 -> Set(30))
        val result = Into[Map[Int, Set[Int]], Map[Long, List[Long]]].into(source)

        // Set order not guaranteed
        assert(result.map(_.map { case (k, v) => (k, v.toSet) }))(
          isRight(equalTo(Map(1L -> Set(10L, 20L), 2L -> Set(30L))))
        )
      }
    ),
    suite("Complex Nested Structures in Products")(
      test("converts case class with nested List[List[Int]] to Vector[Vector[Long]]") {
        case class Source(matrix: List[List[Int]])
        case class Target(matrix: Vector[Vector[Long]])

        val source = Source(List(List(1, 2), List(3, 4)))
        val result = Into.derived[Source, Target].into(source)

        assert(result)(isRight(equalTo(Target(Vector(Vector(1L, 2L), Vector(3L, 4L))))))
      },
      test("converts case class with Option[List[Int]] to Option[Vector[Long]]") {
        case class Source(items: Option[List[Int]])
        case class Target(items: Option[Vector[Long]])

        val source = Source(Some(List(1, 2, 3)))
        val result = Into.derived[Source, Target].into(source)

        assert(result)(isRight(equalTo(Target(Some(Vector(1L, 2L, 3L))))))
      },
      test("converts case class with Map[String, List[Int]] to Map[String, Vector[Long]]") {
        case class Source(data: Map[String, List[Int]])
        case class Target(data: Map[String, Vector[Long]])

        val source = Source(Map("a" -> List(1, 2)))
        val result = Into.derived[Source, Target].into(source)

        assert(result)(isRight(equalTo(Target(Map("a" -> Vector(1L, 2L))))))
      },
      test("converts case class with Either[String, List[Int]] to Either[String, Vector[Long]]") {
        case class Source(result: Either[String, List[Int]])
        case class Target(result: Either[String, Vector[Long]])

        val source = Source(Right(List(1, 2, 3)))
        val result = Into.derived[Source, Target].into(source)

        assert(result)(isRight(equalTo(Target(Right(Vector(1L, 2L, 3L))))))
      }
    ),
    suite("Error Propagation in Nested Collection Types")(
      test("fails when inner element coercion fails") {
        val source = List(List(1L, Long.MaxValue))
        val result = Into[List[List[Long]], Vector[Vector[Int]]].into(source)

        assert(result)(isLeft)
      },
      test("fails when nested Option content conversion fails") {
        val source: Option[List[Long]] = Some(List(Long.MaxValue))
        val result                     = Into[Option[List[Long]], Option[Vector[Int]]].into(source)

        assert(result)(isLeft)
      },
      test("fails when Map value collection conversion fails") {
        val source = Map("key" -> List(Long.MaxValue))
        val result = Into[Map[String, List[Long]], Map[String, Vector[Int]]].into(source)

        assert(result)(isLeft)
      }
    ),
    suite("Collections with Case Classes")(
      test("converts List[CaseClass] to Vector[CaseClass] with field coercion") {
        val source = List(PersonV1("Alice", 30), PersonV1("Bob", 25))
        val result = Into[List[PersonV1], Vector[PersonV2]].into(source)

        assert(result)(isRight(equalTo(Vector(PersonV2("Alice", 30L), PersonV2("Bob", 25L)))))
      },
      test("converts Set[CaseClass] to List[CaseClass] with field coercion") {
        val source = Set(PersonV1("Charlie", 35))
        val result = Into[Set[PersonV1], List[PersonV2]].into(source)

        assert(result)(isRight(equalTo(List(PersonV2("Charlie", 35L)))))
      },
      test("converts Map[String, CaseClass] to Map[String, CaseClass] with coercion") {
        val source = Map("user" -> PersonV1("Dave", 40))
        val result = Into[Map[String, PersonV1], Map[String, PersonV2]].into(source)

        assert(result)(isRight(equalTo(Map("user" -> PersonV2("Dave", 40L)))))
      },
      test("converts Option[List[CaseClass]] to Option[Vector[CaseClass]]") {
        val source: Option[List[PersonV1]] = Some(List(PersonV1("Eve", 28)))
        val result                         = Into[Option[List[PersonV1]], Option[Vector[PersonV2]]].into(source)

        assert(result)(isRight(equalTo(Some(Vector(PersonV2("Eve", 28L))))))
      }
    )
  )
}
