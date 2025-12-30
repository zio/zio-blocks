package zio.blocks.schema.as.structural

import zio.blocks.schema._
import zio.test._
import zio.test.Assertion._
import scala.language.reflectiveCalls

/**
 * JVM-only tests for As with structural types that require reflection.
 *
 * These tests use pure structural types ({ def name: String; def age: Int }) which
 * require reflection for the reverse direction (reading from the anonymous Dynamic class).
 *
 * The macro generates an anonymous Dynamic class at compile time for Product → Structural,
 * but reading back from it requires reflection on JVM.
 */
object StructuralAsSpec extends ZIOSpecDefault {

  // === Case Classes ===
  case class Person(name: String, age: Int)
  case class Point(x: Int, y: Int)

  def spec: Spec[TestEnvironment, Any] = suite("StructuralAsJVMOnlySpec")(
    suite("As with Pure Structural Types (JVM Only)")(
      test("As between case class and pure structural type") {
        val as = As.derived[Person, { def name: String; def age: Int }]
        val original = Person("Carol", 35)

        val toResult = as.into(original)

        toResult match {
          case Right(r) =>
            assert(r.name)(equalTo("Carol")) &&
            assert(r.age)(equalTo(35))
          case Left(err) =>
            assert(err.toString)(equalTo("should not fail"))
        }
      },
      test("As[Person, Structural] round-trip works") {
        val as = As.derived[Person, { def name: String; def age: Int }]
        val original = Person("Dave", 40)

        val roundTrip = for {
          struct <- as.into(original)
          back <- as.from(struct)
        } yield back

        assert(roundTrip)(isRight(equalTo(original)))
      },
      test("As[Point, Structural] round-trip works") {
        val as = As.derived[Point, { def x: Int; def y: Int }]
        val original = Point(100, 200)

        val roundTrip = for {
          struct <- as.into(original)
          back <- as.from(struct)
        } yield back

        assert(roundTrip)(isRight(equalTo(original)))
      },
      test("As with subset of fields") {
        case class Employee(name: String, age: Int, department: String)

        val as = As.derived[Employee, { def name: String; def age: Int }]
        val original = Employee("Eve", 28, "Engineering")

        val toResult = as.into(original)

        toResult match {
          case Right(r) =>
            assert(r.name)(equalTo("Eve")) &&
            assert(r.age)(equalTo(28))
          case Left(err) =>
            assert(err.toString)(equalTo("should not fail"))
        }
      }
    ),
    suite("As structural round-trip chains (JVM Only)")(
      test("Person → Structural → Person") {
        val as = As.derived[Person, { def name: String; def age: Int }]
        val original = Person("Frank", 45)

        val result = for {
          struct <- as.into(original)
          back <- as.from(struct)
        } yield back

        assert(result)(isRight(equalTo(original)))
      },
      test("Point → Structural → Point") {
        val as = As.derived[Point, { def x: Int; def y: Int }]
        val original = Point(15, 25)

        val result = for {
          struct <- as.into(original)
          back <- as.from(struct)
        } yield back

        assert(result)(isRight(equalTo(original)))
      }
    )
  )
}

