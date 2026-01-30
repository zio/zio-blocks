package zio.blocks.schema.into.structural

import zio.blocks.schema._
import zio.test._
import zio.test.Assertion._
import scala.language.dynamics

/**
 * Cross-platform tests for Dynamic structural types in Scala 2.
 *
 * These tests work on JVM, JS, and Native because they use scala.Dynamic
 * which provides selectDynamic - a compile-time generated call that doesn't
 * require reflection.
 *
 * The macro supports:
 *   - Dynamic source → Case class target (using selectDynamic)
 *   - Case class source → Dynamic target (using Map constructor/apply)
 *   - Case class source → Pure structural target (generates anonymous Dynamic)
 *
 * Requirements for Product → Dynamic:
 *   - Dynamic class must have either:
 *     - A constructor taking Map[String, Any]
 *     - A companion apply method taking Map[String, Any]
 *
 * Note: Tests that use ad-hoc structural types (new { def name = ... }) as SOURCE
 * are in JVM-only tests because accessing them requires reflection.
 */
object DynamicTypeSpec extends ZIOSpecDefault {

  // === Target Case Classes ===
  case class Person(name: String, age: Int)
  case class Point(x: Int, y: Int)
  case class Employee(name: String, age: Int, department: String)

  // === Dynamic implementation with Map constructor ===
  // This is the key: DynamicRecord extends Dynamic and has selectDynamic
  class DynamicRecord(val fields: Map[String, Any]) extends Dynamic {
    def selectDynamic(name: String): Any = fields(name)
  }

  object DynamicRecord {
    def apply(elems: (String, Any)*): DynamicRecord = new DynamicRecord(elems.toMap)
    def apply(map: Map[String, Any]): DynamicRecord = new DynamicRecord(map)
  }

  // Refined type aliases for Dynamic
  type PersonLike = DynamicRecord { def name: String; def age: Int }
  type PointLike = DynamicRecord { def x: Int; def y: Int }

  def spec: Spec[TestEnvironment, Any] = suite("DynamicTypeSpec")(
    suite("Case Class to Structural Type (Cross-Platform)")(
      // These work cross-platform because the macro generates an anonymous Dynamic class
      test("converts case class to structural type") {
        val source = Person("Dave", 40)
        val into = Into.derived[Person, { def name: String; def age: Int }]
        val result = into.into(source)

        result match {
          case Right(r) =>
            assert(r.name)(equalTo("Dave")) &&
            assert(r.age)(equalTo(40))
          case Left(err) =>
            assert(err.toString)(equalTo("should not fail"))
        }
      },
      test("converts case class to structural subset") {
        val source = Employee("Eve", 28, "Marketing")
        val into = Into.derived[Employee, { def name: String; def age: Int }]
        val result = into.into(source)

        result match {
          case Right(r) =>
            assert(r.name)(equalTo("Eve")) &&
            assert(r.age)(equalTo(28))
          case Left(err) =>
            assert(err.toString)(equalTo("should not fail"))
        }
      }
    ),
    suite("Case Class to Dynamic (with Map apply)")(
      test("converts case class to Dynamic structural type") {
        val source = Point(10, 20)
        val into = Into.derived[Point, PointLike]
        val result = into.into(source)

        result match {
          case Right(r) =>
            assert(r.selectDynamic("x"))(equalTo(10)) &&
            assert(r.selectDynamic("y"))(equalTo(20))
          case Left(err) =>
            assert(err.toString)(equalTo("should not fail"))
        }
      },
      test("converts Person to Dynamic structural type") {
        val source = Person("Grace", 32)
        val into = Into.derived[Person, PersonLike]
        val result = into.into(source)

        result match {
          case Right(r) =>
            assert(r.selectDynamic("name"))(equalTo("Grace")) &&
            assert(r.selectDynamic("age"))(equalTo(32))
          case Left(err) =>
            assert(err.toString)(equalTo("should not fail"))
        }
      }
    ),
    suite("Dynamic to Case Class (Cross-Platform)")(
      // These work cross-platform because selectDynamic is called at compile time
      test("converts Dynamic structural type to case class") {
        val source: PersonLike = DynamicRecord("name" -> "Henry", "age" -> 45).asInstanceOf[PersonLike]
        val into = Into.derived[PersonLike, Person]
        val result = into.into(source)

        assert(result)(isRight(equalTo(Person("Henry", 45))))
      },
      test("converts Dynamic Point to case class") {
        val source: PointLike = DynamicRecord("x" -> 5, "y" -> 15).asInstanceOf[PointLike]
        val into = Into.derived[PointLike, Point]
        val result = into.into(source)

        assert(result)(isRight(equalTo(Point(5, 15))))
      }
    ),
    suite("Dynamic Round-Trip (Cross-Platform)")(
      test("case class to Dynamic to case class round trip") {
        val original = Person("Ivy", 29)

        val toDynamic = Into.derived[Person, PersonLike]
        val fromDynamic = Into.derived[PersonLike, Person]

        val intermediate = toDynamic.into(original)
        val result = intermediate.flatMap(d => fromDynamic.into(d))

        assert(result)(isRight(equalTo(original)))
      }
    ),
    suite("Pure Structural Types - No Predefined Dynamic Class (Cross-Platform)")(
      // These tests demonstrate that the macro can generate anonymous Dynamic classes
      // at compile time, so users don't need to define their own DynamicRecord class
      test("case class to pure structural type with single field") {
        val source = Person("Jack", 33)
        val into = Into.derived[Person, { def name: String }]
        val result = into.into(source)

        result match {
          case Right(r) =>
            assert(r.name)(equalTo("Jack"))
          case Left(err) =>
            assert(err.toString)(equalTo("should not fail"))
        }
      },
      test("case class to pure structural type with multiple fields") {
        val source = Person("Kate", 27)
        val into = Into.derived[Person, { def name: String; def age: Int }]
        val result = into.into(source)

        result match {
          case Right(r) =>
            assert(r.name)(equalTo("Kate")) &&
            assert(r.age)(equalTo(27))
          case Left(err) =>
            assert(err.toString)(equalTo("should not fail"))
        }
      },
      test("case class to pure structural type with subset of fields") {
        val source = Employee("Leo", 42, "Finance")
        val into = Into.derived[Employee, { def name: String; def department: String }]
        val result = into.into(source)

        result match {
          case Right(r) =>
            assert(r.name)(equalTo("Leo")) &&
            assert(r.department)(equalTo("Finance"))
          case Left(err) =>
            assert(err.toString)(equalTo("should not fail"))
        }
      }
    )
  )
}

