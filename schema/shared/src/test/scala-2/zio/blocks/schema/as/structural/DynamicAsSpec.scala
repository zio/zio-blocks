package zio.blocks.schema.as.structural

import zio.blocks.schema._
import zio.test._
import zio.test.Assertion._
import scala.language.dynamics

/**
 * Cross-platform tests for As with Dynamic structural types in Scala 2.
 *
 * As[A, B] requires bidirectional conversion (A → B and B → A).
 * These tests verify that Dynamic structural types work with As derivation.
 *
 * Note: These tests work on JVM and JS. Native is excluded due to long build times.
 *
 * The pure structural type tests (As[Person, { def name: String }]) are in JVM-only
 * tests because the reverse direction requires reflection to read from the generated
 * anonymous Dynamic class.
 */
object DynamicAsSpec extends ZIOSpecDefault {

  // === Case Classes ===
  case class Person(name: String, age: Int)
  case class Point(x: Int, y: Int)

  // === Dynamic implementation with Map constructor ===
  class DynamicRecord(val fields: Map[String, Any]) extends Dynamic {
    def selectDynamic(name: String): Any = fields(name)

    override def equals(obj: Any): Boolean = obj match {
      case other: DynamicRecord => fields == other.fields
      case _ => false
    }

    override def hashCode(): Int = fields.hashCode()
  }

  object DynamicRecord {
    def apply(elems: (String, Any)*): DynamicRecord = new DynamicRecord(elems.toMap)
    def apply(map: Map[String, Any]): DynamicRecord = new DynamicRecord(map)
  }

  // Refined type aliases for Dynamic
  type PersonLike = DynamicRecord { def name: String; def age: Int }
  type PointLike = DynamicRecord { def x: Int; def y: Int }

  def spec: Spec[TestEnvironment, Any] = suite("DynamicAsSpec")(
    suite("As between Case Class and Dynamic")(
      test("derives As[Person, PersonLike]") {
        val as = As.derived[Person, PersonLike]
        val person = Person("Alice", 30)

        val toResult = as.into(person)

        toResult match {
          case Right(dynamic) =>
            assert(dynamic.selectDynamic("name"))(equalTo("Alice")) &&
            assert(dynamic.selectDynamic("age"))(equalTo(30))
          case Left(err) =>
            assert(err.toString)(equalTo("should not fail"))
        }
      },
      test("As[Person, PersonLike] round-trip works") {
        val as = As.derived[Person, PersonLike]
        val original = Person("Bob", 25)

        val roundTrip = for {
          dynamic <- as.into(original)
          back <- as.from(dynamic)
        } yield back

        assert(roundTrip)(isRight(equalTo(original)))
      },
      test("As[Point, PointLike] round-trip works") {
        val as = As.derived[Point, PointLike]
        val original = Point(10, 20)

        val roundTrip = for {
          dynamic <- as.into(original)
          back <- as.from(dynamic)
        } yield back

        assert(roundTrip)(isRight(equalTo(original)))
      }
    ),
    suite("As reverse functionality")(
      test("reverse reverses As direction") {
        val as = As.derived[Person, PersonLike]
        val reversed = as.reverse

        val dynamic: PersonLike = DynamicRecord("name" -> "Eve", "age" -> 28).asInstanceOf[PersonLike]

        // Original: Person → PersonLike
        // Reversed: PersonLike → Person
        val result = reversed.into(dynamic)

        assert(result)(isRight(equalTo(Person("Eve", 28))))
      },
      test("double reverse returns equivalent As") {
        val as = As.derived[Person, PersonLike]
        val doubleReversed = as.reverse.reverse
        val original = Person("Frank", 45)

        val result1 = as.into(original)
        val result2 = doubleReversed.into(original)

        // Both should produce equivalent results
        (result1, result2) match {
          case (Right(d1: DynamicRecord), Right(d2: DynamicRecord)) =>
            assert(d1.selectDynamic("name"))(equalTo(d2.selectDynamic("name"))) &&
            assert(d1.selectDynamic("age"))(equalTo(d2.selectDynamic("age")))
          case _ =>
            assert(false)(equalTo(true))
        }
      }
    ),
    suite("As used as Into")(
      test("As can be used where Into is expected") {
        val as: As[Person, PersonLike] = As.derived[Person, PersonLike]
        val into: Into[Person, PersonLike] = as // As extends Into

        val person = Person("Grace", 32)
        val result = into.into(person)

        result match {
          case Right(dynamic) =>
            assert(dynamic.selectDynamic("name"))(equalTo("Grace")) &&
            assert(dynamic.selectDynamic("age"))(equalTo(32))
          case Left(err) =>
            assert(err.toString)(equalTo("should not fail"))
        }
      }
    )
  )
}

