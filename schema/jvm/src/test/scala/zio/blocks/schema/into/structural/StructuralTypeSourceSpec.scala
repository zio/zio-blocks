package zio.blocks.schema.into.structural

import zio.blocks.schema._
import zio.test._
import zio.test.Assertion._
import scala.language.reflectiveCalls

/**
 * Tests for structural type as source in Into derivation.
 *
 * Structural types are types defined by their members rather than their name.
 * Example: { def name: String; def age: Int }
 *
 * These tests verify that Into can convert from structural types to case
 * classes.
 */
object StructuralTypeSourceSpec extends ZIOSpecDefault {

  // === Target Case Classes ===
  case class Person(name: String, age: Int)
  case class NameOnly(name: String)
  case class Employee(name: String, age: Int, department: String)
  case class PersonWithDefault(name: String, age: Int, active: Boolean = true)
  case class PersonWithOptional(name: String, age: Int, nickname: Option[String])

  // === Helper to create structural type instances ===
  def makePerson(n: String, a: Int): { def name: String; def age: Int } = new {
    def name: String = n
    def age: Int     = a
  }

  def makeNamed(n: String): { def name: String } = new {
    def name: String = n
  }

  def makeEmployee(n: String, a: Int, d: String): { def name: String; def age: Int; def department: String } = new {
    def name: String       = n
    def age: Int           = a
    def department: String = d
  }

  def spec: Spec[TestEnvironment, Any] = suite("StructuralTypeSourceSpec")(
    suite("Structural to Case Class - Exact Match")(
      test("converts structural type with matching fields") {
        val source = makePerson("Alice", 30)
        val into   = Into.derived[{ def name: String; def age: Int }, Person]
        val result = into.into(source)

        assert(result)(isRight(equalTo(Person("Alice", 30))))
      },
      test("converts structural type with single field") {
        val source = makeNamed("Bob")
        val into   = Into.derived[{ def name: String }, NameOnly]
        val result = into.into(source)

        assert(result)(isRight(equalTo(NameOnly("Bob"))))
      },
      test("converts structural type with three fields") {
        val source = makeEmployee("Carol", 25, "Engineering")
        val into   = Into.derived[{ def name: String; def age: Int; def department: String }, Employee]
        val result = into.into(source)

        assert(result)(isRight(equalTo(Employee("Carol", 25, "Engineering"))))
      }
    ),
    suite("Structural to Case Class - Extra Source Fields")(
      test("converts structural with extra fields (drops extras)") {
        val source = makeEmployee("Dave", 35, "Sales")
        val into   = Into.derived[{ def name: String; def age: Int; def department: String }, Person]
        val result = into.into(source)

        assert(result)(isRight(equalTo(Person("Dave", 35))))
      }
    ),
    suite("Structural to Case Class - Target with Defaults")(
      test("converts structural with fewer fields using defaults") {
        val source = makePerson("Eve", 28)
        val into   = Into.derived[{ def name: String; def age: Int }, PersonWithDefault]
        val result = into.into(source)

        assert(result)(isRight(equalTo(PersonWithDefault("Eve", 28, true))))
      }
    ),
    suite("Structural to Case Class - Target with Optional")(
      test("converts structural with fewer fields using None for optional") {
        val source = makePerson("Frank", 40)
        val into   = Into.derived[{ def name: String; def age: Int }, PersonWithOptional]
        val result = into.into(source)

        assert(result)(isRight(equalTo(PersonWithOptional("Frank", 40, None))))
      }
    ),
    suite("Structural Type Identity")(
      test("structural to same structural") {
        val source = makePerson("Grace", 45)
        // Note: structural types can be used as both source and target
        val into   = Into.derived[{ def name: String; def age: Int }, Person]
        val result = into.into(source)

        assert(result)(isRight(equalTo(Person("Grace", 45))))
      }
    ),
    suite("Multiple Structural Conversions")(
      test("chain of structural to case class conversions") {
        val source = makeEmployee("Henry", 50, "Marketing")

        val intoEmployee = Into.derived[{ def name: String; def age: Int; def department: String }, Employee]
        val intoPerson   = Into.derived[Employee, Person]

        val intermediate = intoEmployee.into(source)
        val result       = intermediate.flatMap(emp => intoPerson.into(emp))

        assert(result)(isRight(equalTo(Person("Henry", 50))))
      }
    )
  )
}
