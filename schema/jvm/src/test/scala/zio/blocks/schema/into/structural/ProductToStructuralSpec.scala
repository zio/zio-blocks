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
object ProductToStructuralSpec extends SchemaBaseSpec {

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
            assertTrue(r.name == "Alice", r.age == 30)
          case Left(err) =>
            assertTrue(false) ?? s"Conversion failed with error: $err"
        }
      },
      test("converts case class with single field") {
        val source = NameOnly("Bob")
        val into   = Into.derived[NameOnly, HasName]
        val result = into.into(source)

        result match {
          case Right(r) =>
            assertTrue(r.name == "Bob")
          case Left(_) =>
            assertTrue(false)
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
            assertTrue(r.name == "Carol", r.age == 25)
          case Left(_) =>
            assertTrue(false)
        }
      },
      test("converts case class to structural with single field from many") {
        val source = PersonWithExtra("Dave", 35, "dave@example.com", "555-1234")
        val into   = Into.derived[PersonWithExtra, HasName]
        val result = into.into(source)

        result match {
          case Right(r) =>
            assertTrue(r.name == "Dave")
          case Left(_) =>
            assertTrue(false)
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
            assertTrue(r.name == "Eve", r.age == 28, r.department == "Sales")
          case Left(_) =>
            assertTrue(false)
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
    ),
    suite("Deep Nested Structural Types")(
      test("2-level nested case class to structural") {
        case class Inner(x: Int, y: Int)
        case class Outer(name: String, inner: Inner)

        type NestedStruct = {
          def name: String
          def inner: { def x: Int; def y: Int }
        }

        val source = Outer("test", Inner(10, 20))
        val into   = Into.derived[Outer, NestedStruct]
        val result = into.into(source)

        result match {
          case Right(r) =>
            assertTrue(r.name == "test", r.inner.x == 10, r.inner.y == 20)
          case Left(err) =>
            assertTrue(false) ?? s"Conversion failed with error: $err"
        }
      },
      test("3-level nested case class to structural") {
        case class Level3(value: Int)
        case class Level2(name: String, level3: Level3)
        case class Level1(id: Long, level2: Level2)

        type DeepStruct = {
          def id: Long
          def level2: {
            def name: String
            def level3: { def value: Int }
          }
        }

        val source = Level1(1L, Level2("nested", Level3(42)))
        val into   = Into.derived[Level1, DeepStruct]
        val result = into.into(source)

        result match {
          case Right(r) =>
            assertTrue(
              r.id == 1L,
              r.level2.name == "nested",
              r.level2.level3.value == 42
            )
          case Left(err) =>
            assertTrue(false) ?? s"Conversion failed with error: $err"
        }
      },
      test("4-level nested case class to structural") {
        case class Level4(code: String)
        case class Level3(value: Int, level4: Level4)
        case class Level2(name: String, level3: Level3)
        case class Level1(id: Long, level2: Level2)

        type VeryDeepStruct = {
          def id: Long
          def level2: {
            def name: String
            def level3: {
              def value: Int
              def level4: { def code: String }
            }
          }
        }

        val source = Level1(1L, Level2("deep", Level3(100, Level4("ABC"))))
        val into   = Into.derived[Level1, VeryDeepStruct]
        val result = into.into(source)

        result match {
          case Right(r) =>
            assertTrue(
              r.id == 1L,
              r.level2.name == "deep",
              r.level2.level3.value == 100,
              r.level2.level3.level4.code == "ABC"
            )
          case Left(err) =>
            assertTrue(false) ?? s"Conversion failed with error: $err"
        }
      }
    )
  )
}
