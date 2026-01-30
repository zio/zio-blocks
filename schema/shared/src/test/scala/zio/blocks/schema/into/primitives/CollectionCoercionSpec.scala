package zio.blocks.schema.into.primitives

import zio.blocks.schema._
import zio.test._
import zio.test.Assertion._

/**
 * Tests for collection element coercion in Into conversions.
 *
 * Covers:
 *   - List[A] → List[B] with element coercion
 *   - Vector[A] → Vector[B] with element coercion
 *   - Set[A] → Set[B] with element coercion
 *   - Array[A] → Array[B] with element coercion
 *   - Seq[A] → Seq[B] with element coercion
 */
object CollectionCoercionSpec extends ZIOSpecDefault {

  // Test data types for collection element coercion
  case class PersonV1(name: String, age: Int)
  case class PersonV2(name: String, age: Long)

  implicit val personV1ToV2: Into[PersonV1, PersonV2] = Into.derived[PersonV1, PersonV2]

  def spec: Spec[TestEnvironment, Any] = suite("CollectionCoercionSpec")(
    suite("List Element Coercion")(
      test("coerces List[Int] to List[Long]") {
        val source = List(1, 2, 3)
        val result = Into.derived[List[Int], List[Long]].into(source)

        assert(result)(isRight(equalTo(List(1L, 2L, 3L))))
      },
      test("coerces empty List") {
        val source = List.empty[Int]
        val result = Into.derived[List[Int], List[Long]].into(source)

        assert(result)(isRight(equalTo(List.empty[Long])))
      },
      test("coerces List[Byte] to List[Int]") {
        val source = List(1.toByte, 2.toByte, 3.toByte)
        val result = Into.derived[List[Byte], List[Int]].into(source)

        assert(result)(isRight(equalTo(List(1, 2, 3))))
      },
      test("coerces List[Float] to List[Double]") {
        val source = List(1.0f, 2.5f, 3.14f)
        val result = Into.derived[List[Float], List[Double]].into(source)

        assert(result)(isRight(equalTo(List(1.0f.toDouble, 2.5f.toDouble, 3.14f.toDouble))))
      },
      test("coerces List of case classes") {
        val source = List(PersonV1("Alice", 30), PersonV1("Bob", 25))
        val result = Into.derived[List[PersonV1], List[PersonV2]].into(source)

        assert(result)(isRight(equalTo(List(PersonV2("Alice", 30L), PersonV2("Bob", 25L)))))
      },
      test("fails when narrowing element fails") {
        val source = List(1L, Long.MaxValue, 3L)
        val result = Into.derived[List[Long], List[Int]].into(source)

        assert(result)(isLeft)
      }
    ),
    suite("Vector Element Coercion")(
      test("coerces Vector[Int] to Vector[Long]") {
        val source = Vector(10, 20, 30)
        val result = Into.derived[Vector[Int], Vector[Long]].into(source)

        assert(result)(isRight(equalTo(Vector(10L, 20L, 30L))))
      },
      test("coerces empty Vector") {
        val source = Vector.empty[Short]
        val result = Into.derived[Vector[Short], Vector[Int]].into(source)

        assert(result)(isRight(equalTo(Vector.empty[Int])))
      },
      test("coerces Vector of case classes") {
        val source = Vector(PersonV1("Charlie", 35))
        val result = Into.derived[Vector[PersonV1], Vector[PersonV2]].into(source)

        assert(result)(isRight(equalTo(Vector(PersonV2("Charlie", 35L)))))
      }
    ),
    suite("Set Element Coercion")(
      test("coerces Set[Int] to Set[Long]") {
        val source = Set(1, 2, 3)
        val result = Into.derived[Set[Int], Set[Long]].into(source)

        assert(result)(isRight(equalTo(Set(1L, 2L, 3L))))
      },
      test("coerces empty Set") {
        val source = Set.empty[Int]
        val result = Into.derived[Set[Int], Set[Long]].into(source)

        assert(result)(isRight(equalTo(Set.empty[Long])))
      },
      test("coerces Set[Byte] to Set[Short]") {
        val source = Set(1.toByte, 2.toByte, 3.toByte)
        val result = Into.derived[Set[Byte], Set[Short]].into(source)

        assert(result)(isRight(equalTo(Set(1.toShort, 2.toShort, 3.toShort))))
      }
    ),
    suite("Seq Element Coercion")(
      test("coerces Seq[Int] to Seq[Long]") {
        val source: Seq[Int] = Seq(100, 200, 300)
        val result           = Into.derived[Seq[Int], Seq[Long]].into(source)

        assert(result)(isRight(equalTo(Seq(100L, 200L, 300L))))
      },
      test("coerces empty Seq") {
        val source: Seq[Int] = Seq.empty
        val result           = Into.derived[Seq[Int], Seq[Long]].into(source)

        assert(result)(isRight(equalTo(Seq.empty[Long])))
      }
    ),
    suite("Map Value Coercion")(
      test("coerces Map[String, Int] to Map[String, Long]") {
        val source = Map("a" -> 1, "b" -> 2)
        val result = Into.derived[Map[String, Int], Map[String, Long]].into(source)

        assert(result)(isRight(equalTo(Map("a" -> 1L, "b" -> 2L))))
      },
      test("coerces empty Map") {
        val source = Map.empty[String, Int]
        val result = Into.derived[Map[String, Int], Map[String, Long]].into(source)

        assert(result)(isRight(equalTo(Map.empty[String, Long])))
      },
      test("coerces Map with case class values") {
        val source = Map("alice" -> PersonV1("Alice", 30))
        val result = Into.derived[Map[String, PersonV1], Map[String, PersonV2]].into(source)

        assert(result)(isRight(equalTo(Map("alice" -> PersonV2("Alice", 30L)))))
      }
    ),
    suite("Large Collections")(
      test("coerces large List") {
        val source = (1 to 1000).toList
        val result = Into.derived[List[Int], List[Long]].into(source)

        assert(result.map(_.size))(isRight(equalTo(1000)))
      },
      test("coerces large Vector") {
        val source = (1 to 1000).toVector
        val result = Into.derived[Vector[Int], Vector[Long]].into(source)

        assert(result.map(_.size))(isRight(equalTo(1000)))
      }
    ),
    suite("Narrowing in Collections")(
      test("narrows List[Long] to List[Int] when all values fit") {
        val source = List(1L, 2L, 3L)
        val result = Into.derived[List[Long], List[Int]].into(source)

        assert(result)(isRight(equalTo(List(1, 2, 3))))
      },
      test("fails when any narrowing overflows") {
        val source = List(1L, Long.MaxValue, 3L)
        val result = Into.derived[List[Long], List[Int]].into(source)

        assert(result)(isLeft)
      },
      test("narrows Vector[Int] to Vector[Short] when all fit") {
        val source = Vector(100, 200, 300)
        val result = Into.derived[Vector[Int], Vector[Short]].into(source)

        assert(result)(isRight(equalTo(Vector(100.toShort, 200.toShort, 300.toShort))))
      }
    )
  )
}
