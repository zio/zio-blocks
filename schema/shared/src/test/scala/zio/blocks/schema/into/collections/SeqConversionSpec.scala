package zio.blocks.schema.into.collections

import zio.blocks.schema._
import zio.test._
import zio.test.Assertion._

/**
 * Tests for Seq type conversions.
 *
 * Covers:
 *   - Seq[A] ↔ List[A]
 *   - Seq[A] ↔ Vector[A]
 *   - Seq[A] ↔ Set[A]
 *   - Seq[A] ↔ Array[A]
 *   - With element type coercion
 */
object SeqConversionSpec extends ZIOSpecDefault {

  // Test data types
  case class PersonV1(name: String, age: Int)
  case class PersonV2(name: String, age: Long)

  implicit val personV1ToV2: Into[PersonV1, PersonV2] = Into.derived[PersonV1, PersonV2]

  def spec: Spec[TestEnvironment, Any] = suite("SeqConversionSpec")(
    suite("Seq to List")(
      test("converts Seq[Int] to List[Int]") {
        val source: Seq[Int] = Seq(1, 2, 3)
        val result           = Into[Seq[Int], List[Int]].into(source)

        assert(result)(isRight(equalTo(List(1, 2, 3))))
      },
      test("converts empty Seq to empty List") {
        val source: Seq[Int] = Seq.empty
        val result           = Into[Seq[Int], List[Int]].into(source)

        assert(result)(isRight(equalTo(List.empty[Int])))
      },
      test("converts Seq[Int] to List[Long] with coercion") {
        val source: Seq[Int] = Seq(1, 2, 3)
        val result           = Into[Seq[Int], List[Long]].into(source)

        assert(result)(isRight(equalTo(List(1L, 2L, 3L))))
      }
    ),
    suite("List to Seq")(
      test("converts List[Int] to Seq[Int]") {
        val source = List(1, 2, 3)
        val result = Into[List[Int], Seq[Int]].into(source)

        assert(result)(isRight(equalTo(Seq(1, 2, 3))))
      },
      test("converts List[Int] to Seq[Long] with coercion") {
        val source = List(1, 2, 3)
        val result = Into[List[Int], Seq[Long]].into(source)

        assert(result)(isRight(equalTo(Seq(1L, 2L, 3L))))
      }
    ),
    suite("Seq to Vector")(
      test("converts Seq[Int] to Vector[Int]") {
        val source: Seq[Int] = Seq(1, 2, 3)
        val result           = Into[Seq[Int], Vector[Int]].into(source)

        assert(result)(isRight(equalTo(Vector(1, 2, 3))))
      },
      test("converts Seq[Int] to Vector[Long] with coercion") {
        val source: Seq[Int] = Seq(10, 20)
        val result           = Into[Seq[Int], Vector[Long]].into(source)

        assert(result)(isRight(equalTo(Vector(10L, 20L))))
      }
    ),
    suite("Vector to Seq")(
      test("converts Vector[Int] to Seq[Int]") {
        val source = Vector(1, 2, 3)
        val result = Into[Vector[Int], Seq[Int]].into(source)

        assert(result)(isRight(equalTo(Seq(1, 2, 3))))
      },
      test("converts Vector[Int] to Seq[Long] with coercion") {
        val source = Vector(1, 2, 3)
        val result = Into[Vector[Int], Seq[Long]].into(source)

        assert(result)(isRight(equalTo(Seq(1L, 2L, 3L))))
      }
    ),
    suite("Seq to Set")(
      test("converts Seq[Int] to Set[Int]") {
        val source: Seq[Int] = Seq(1, 2, 3, 2, 1)
        val result           = Into[Seq[Int], Set[Int]].into(source)

        assert(result)(isRight(equalTo(Set(1, 2, 3))))
      },
      test("converts Seq[Int] to Set[Long] with coercion") {
        val source: Seq[Int] = Seq(1, 2, 3)
        val result           = Into[Seq[Int], Set[Long]].into(source)

        assert(result)(isRight(equalTo(Set(1L, 2L, 3L))))
      }
    ),
    suite("Set to Seq")(
      test("converts Set[Int] to Seq[Int]") {
        val source = Set(1, 2, 3)
        val result = Into[Set[Int], Seq[Int]].into(source)

        assert(result.map(_.toSet))(isRight(equalTo(Set(1, 2, 3))))
      },
      test("converts Set[Int] to Seq[Long] with coercion") {
        val source = Set(1, 2, 3)
        val result = Into[Set[Int], Seq[Long]].into(source)

        assert(result.map(_.toSet))(isRight(equalTo(Set(1L, 2L, 3L))))
      }
    ),
    suite("Seq to Array")(
      test("converts Seq[Int] to Array[Int]") {
        val source: Seq[Int] = Seq(1, 2, 3)
        val result           = Into[Seq[Int], Array[Int]].into(source)

        assert(result.map(_.toList))(isRight(equalTo(List(1, 2, 3))))
      },
      test("converts Seq[Int] to Array[Long] with coercion") {
        val source: Seq[Int] = Seq(1, 2)
        val result           = Into[Seq[Int], Array[Long]].into(source)

        assert(result.map(_.toList))(isRight(equalTo(List(1L, 2L))))
      }
    ),
    suite("Array to Seq")(
      test("converts Array[Int] to Seq[Int]") {
        val source = Array(1, 2, 3)
        val result = Into[Array[Int], Seq[Int]].into(source)

        assert(result)(isRight(equalTo(Seq(1, 2, 3))))
      },
      test("converts Array[Int] to Seq[Long] with coercion") {
        val source = Array(1, 2)
        val result = Into[Array[Int], Seq[Long]].into(source)

        assert(result)(isRight(equalTo(Seq(1L, 2L))))
      }
    ),
    suite("Seq to Seq")(
      test("converts Seq[Int] to Seq[Int]") {
        val source: Seq[Int] = Seq(1, 2, 3)
        val result           = Into[Seq[Int], Seq[Int]].into(source)

        assert(result)(isRight(equalTo(Seq(1, 2, 3))))
      },
      test("converts Seq[Int] to Seq[Long] with coercion") {
        val source: Seq[Int] = Seq(1, 2, 3)
        val result           = Into[Seq[Int], Seq[Long]].into(source)

        assert(result)(isRight(equalTo(Seq(1L, 2L, 3L))))
      }
    ),
    suite("Narrowing in Seq conversions")(
      test("narrows Seq[Long] to List[Int] when all fit") {
        val source: Seq[Long] = Seq(1L, 2L, 3L)
        val result            = Into[Seq[Long], List[Int]].into(source)

        assert(result)(isRight(equalTo(List(1, 2, 3))))
      },
      test("fails when narrowing overflows") {
        val source: Seq[Long] = Seq(1L, Long.MaxValue)
        val result            = Into[Seq[Long], List[Int]].into(source)

        assert(result)(isLeft)
      }
    ),
    suite("In Products")(
      test("converts case class with Seq field to case class with List field") {
        case class Source(items: Seq[Int])
        case class Target(items: List[Long])

        val source = Source(Seq(1, 2, 3))
        val result = Into.derived[Source, Target].into(source)

        assert(result)(isRight(equalTo(Target(List(1L, 2L, 3L)))))
      },
      test("converts case class with List field to case class with Seq field") {
        case class Source(items: List[Int])
        case class Target(items: Seq[Long])

        val source = Source(List(1, 2))
        val result = Into.derived[Source, Target].into(source)

        assert(result)(isRight(equalTo(Target(Seq(1L, 2L)))))
      },
      test("converts case class with Vector field to case class with Seq field") {
        case class Source(items: Vector[Int])
        case class Target(items: Seq[Long])

        val source = Source(Vector(10, 20))
        val result = Into.derived[Source, Target].into(source)

        assert(result)(isRight(equalTo(Target(Seq(10L, 20L)))))
      }
    ),
    suite("Nested Seq Conversions")(
      test("converts Seq[Seq[Int]] to List[List[Long]]") {
        val source: Seq[Seq[Int]] = Seq(Seq(1, 2), Seq(3, 4))
        val result                = Into[Seq[Seq[Int]], List[List[Long]]].into(source)

        assert(result)(isRight(equalTo(List(List(1L, 2L), List(3L, 4L)))))
      },
      test("converts List[Seq[Int]] to Vector[Vector[Long]]") {
        val source: List[Seq[Int]] = List(Seq(1), Seq(2, 3))
        val result                 = Into[List[Seq[Int]], Vector[Vector[Long]]].into(source)

        assert(result)(isRight(equalTo(Vector(Vector(1L), Vector(2L, 3L)))))
      }
    )
  )
}
