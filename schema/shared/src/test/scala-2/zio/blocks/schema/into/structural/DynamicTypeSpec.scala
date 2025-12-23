package zio.blocks.schema.into.structural

import zio.blocks.schema._
import zio.test._
import zio.test.Assertion._
import scala.language.dynamics
import scala.language.reflectiveCalls

/**
 * Scala 2 specific tests for Dynamic structural types.
 *
 * In Scala 2, Dynamic can be used for more flexible structural-like access,
 * but the primary structural type support uses refinement types.
 */
object DynamicTypeSpec extends ZIOSpecDefault {

  // === Target Case Classes ===
  case class Person(name: String, age: Int)
  case class Employee(name: String, age: Int, department: String)

  // === Dynamic implementation for flexible access ===
  class DynamicRecord(val fields: Map[String, Any]) extends Dynamic {
    def selectDynamic(name: String): Any = fields(name)
  }

  object DynamicRecord {
    def apply(elems: (String, Any)*): DynamicRecord = new DynamicRecord(elems.toMap)
  }

  // === Helper to create structural type instances for testing ===
  def makePersonStruct(n: String, a: Int): { def name: String; def age: Int } = new {
    def name: String = n
    def age: Int = a
  }

  def makeEmployeeStruct(n: String, a: Int, d: String): { def name: String; def age: Int; def department: String } = new {
    def name: String = n
    def age: Int = a
    def department: String = d
  }

  def spec: Spec[TestEnvironment, Any] = suite("DynamicTypeSpec")(
    suite("Structural Type to Case Class")(
      test("converts structural type to case class with matching fields") {
        val source = makePersonStruct("Alice", 30)
        val into = Into.derived[{ def name: String; def age: Int }, Person]
        val result = into.into(source)

        assert(result)(isRight(equalTo(Person("Alice", 30))))
      },
      test("converts structural type with extra fields") {
        val source = makeEmployeeStruct("Bob", 25, "Engineering")
        val into = Into.derived[{ def name: String; def age: Int; def department: String }, Person]
        val result = into.into(source)

        assert(result)(isRight(equalTo(Person("Bob", 25))))
      },
      test("converts structural type with all fields") {
        val source = makeEmployeeStruct("Carol", 35, "Sales")
        val into = Into.derived[{ def name: String; def age: Int; def department: String }, Employee]
        val result = into.into(source)

        assert(result)(isRight(equalTo(Employee("Carol", 35, "Sales"))))
      }
    ),
    suite("Case Class to Structural Type")(
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
    suite("Chain Conversions")(
      test("structural to case class to structural") {
        val source = makeEmployeeStruct("Frank", 45, "HR")

        val toEmployee = Into.derived[{ def name: String; def age: Int; def department: String }, Employee]
        val toPersonStruct = Into.derived[Employee, { def name: String; def age: Int }]

        val intermediate = toEmployee.into(source)
        val result = intermediate.flatMap(emp => toPersonStruct.into(emp))

        result match {
          case Right(r) =>
            assert(r.name)(equalTo("Frank")) &&
            assert(r.age)(equalTo(45))
          case Left(err) =>
            assert(err.toString)(equalTo("should not fail"))
        }
      }
    )
  )
}

