package zio.blocks.schema.into.structural

import zio.blocks.schema._
import zio.test._
import zio.test.Assertion._
import scala.language.reflectiveCalls

/**
 * Tests for case class to structural type conversions in Into derivation.
 *
 * These tests verify that Into can convert from case classes to structural
 * types. The case class must have all the members required by the structural
 * type.
 */
object ProductToStructuralSpec extends ZIOSpecDefault {

  // === Source Case Classes ===
  case class Person(name: String, age: Int)
  case class Employee(name: String, age: Int, department: String)
  case class NameOnly(name: String)
  case class PersonWithExtra(name: String, age: Int, email: String, phone: String)

  // === Structural type aliases ===
  type HasName        = { def name: String }
  type HasNameAge     = { def name: String; def age: Int }
  type HasNameAgeDept = { def name: String; def age: Int; def department: String }

  def spec: Spec[TestEnvironment, Any] = suite("ProductToStructuralSpec")(
    suite("Case Class to Structural - Exact Match")(
      test("converts case class to structural with same fields") {
        val source = Person("Alice", 30)
        val into   = Into.derived[Person, HasNameAge]
        val result = into.into(source)

        result match {
          case Right(r) =>
            assert(r.name)(equalTo("Alice")) &&
            assert(r.age)(equalTo(30))
          case Left(err) =>
            assert(false)(equalTo(true)) // Force failure with error message
        }
      },
      test("converts case class with single field") {
        val source = NameOnly("Bob")
        val into   = Into.derived[NameOnly, HasName]
        val result = into.into(source)

        result match {
          case Right(r) =>
            assert(r.name)(equalTo("Bob"))
          case Left(_) =>
            assert(false)(equalTo(true))
        }
      }
    ),
    suite("Case Class to Structural - Subset")(
      test("converts case class to structural with fewer fields") {
        val source = Employee("Carol", 25, "Engineering")
        val into   = Into.derived[Employee, HasNameAge]
        val result = into.into(source)

        result match {
          case Right(r) =>
            assert(r.name)(equalTo("Carol")) &&
            assert(r.age)(equalTo(25))
          case Left(_) =>
            assert(false)(equalTo(true))
        }
      },
      test("converts case class to structural with single field from many") {
        val source = PersonWithExtra("Dave", 35, "dave@example.com", "555-1234")
        val into   = Into.derived[PersonWithExtra, HasName]
        val result = into.into(source)

        result match {
          case Right(r) =>
            assert(r.name)(equalTo("Dave"))
          case Left(_) =>
            assert(false)(equalTo(true))
        }
      }
    ),
    suite("Case Class to Structural - Full Match")(
      test("converts case class with all fields to matching structural") {
        val source = Employee("Eve", 28, "Sales")
        val into   = Into.derived[Employee, HasNameAgeDept]
        val result = into.into(source)

        result match {
          case Right(r) =>
            assert(r.name)(equalTo("Eve")) &&
            assert(r.age)(equalTo(28)) &&
            assert(r.department)(equalTo("Sales"))
          case Left(_) =>
            assert(false)(equalTo(true))
        }
      }
    ),
    suite("Chain Conversions")(
      test("case class to structural to case class") {
        val source = Employee("Frank", 40, "HR")

        val toStructural = Into.derived[Employee, HasNameAge]
        val toPerson     = Into.derived[HasNameAge, Person]

        val intermediate = toStructural.into(source)
        val result       = intermediate.flatMap(s => toPerson.into(s))

        assert(result)(isRight(equalTo(Person("Frank", 40))))
      }
    )
  )
}
