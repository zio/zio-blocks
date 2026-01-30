package zio.blocks.schema.into.product

import zio.blocks.schema._
import zio.test._
import zio.test.Assertion._

/**
 * Tests for field reordering in Into conversions.
 *
 * Focuses on Priority 1: Exact match (same name + same type) with different
 * field ordering.
 *
 * Covers:
 *   - Fields in different order but matching by name
 *   - Multiple field reorderings
 *   - Reordering with different field types
 */
object FieldReorderingSpec extends ZIOSpecDefault {

  // === Test Data Types ===

  // Simple 2-field reordering
  case class Point(x: Int, y: Int)
  case class Coord(y: Int, x: Int)

  // 3-field reordering
  case class Person(name: String, age: Int, email: String)
  case class PersonReordered(email: String, name: String, age: Int)

  // Complex reordering with many fields
  case class Employee(id: Long, name: String, department: String, salary: Double, active: Boolean)
  case class EmployeeReordered(active: Boolean, salary: Double, name: String, id: Long, department: String)

  // Partial reordering (some in order, some not)
  case class Address(street: String, city: String, state: String, zipCode: String)
  case class AddressPartial(street: String, zipCode: String, city: String, state: String)

  // Same field names, different types positions
  case class Data1(a: Int, b: String, c: Boolean, d: Double)
  case class Data2(c: Boolean, a: Int, d: Double, b: String)

  def spec: Spec[TestEnvironment, Any] = suite("FieldReorderingSpec")(
    suite("Basic Reordering")(
      test("maps 2 fields by name despite reversed order") {
        val point  = Point(x = 1, y = 2)
        val result = Into.derived[Point, Coord].into(point)

        // x→x, y→y (by name, not position)
        assert(result)(isRight(equalTo(Coord(y = 2, x = 1))))
      },
      test("maps 3 fields with complete reordering") {
        val person = Person(name = "Alice", age = 30, email = "alice@example.com")
        val result = Into.derived[Person, PersonReordered].into(person)

        assert(result)(
          isRight(
            equalTo(
              PersonReordered(
                email = "alice@example.com",
                name = "Alice",
                age = 30
              )
            )
          )
        )
      }
    ),
    suite("Complex Reordering")(
      test("maps 5 fields with complete shuffle") {
        val employee = Employee(
          id = 123L,
          name = "Bob",
          department = "Engineering",
          salary = 75000.0,
          active = true
        )
        val result = Into.derived[Employee, EmployeeReordered].into(employee)

        assert(result)(
          isRight(
            equalTo(
              EmployeeReordered(
                active = true,
                salary = 75000.0,
                name = "Bob",
                id = 123L,
                department = "Engineering"
              )
            )
          )
        )
      },
      test("maps fields with partial reordering") {
        val address = Address(
          street = "123 Main St",
          city = "Springfield",
          state = "IL",
          zipCode = "62701"
        )
        val result = Into.derived[Address, AddressPartial].into(address)

        assert(result)(
          isRight(
            equalTo(
              AddressPartial(
                street = "123 Main St",
                zipCode = "62701",
                city = "Springfield",
                state = "IL"
              )
            )
          )
        )
      }
    ),
    suite("Reordering with Different Types")(
      test("maps fields by name regardless of type order") {
        val data   = Data1(a = 42, b = "hello", c = true, d = 3.14)
        val result = Into.derived[Data1, Data2].into(data)

        assert(result)(
          isRight(
            equalTo(
              Data2(
                c = true,
                a = 42,
                d = 3.14,
                b = "hello"
              )
            )
          )
        )
      }
    ),
    suite("Edge Cases")(
      test("single field (no reordering possible)") {
        case class Single(value: String)
        case class SingleSame(value: String)

        val source = Single("test")
        val result = Into.derived[Single, SingleSame].into(source)

        assert(result)(isRight(equalTo(SingleSame("test"))))
      },
      test("many fields with complex reordering") {
        case class Source(f1: Int, f2: String, f3: Boolean, f4: Double, f5: Long, f6: Char)
        case class Target(f6: Char, f1: Int, f4: Double, f2: String, f5: Long, f3: Boolean)

        val source = Source(
          f1 = 1,
          f2 = "two",
          f3 = true,
          f4 = 4.0,
          f5 = 5L,
          f6 = 'f'
        )
        val result = Into.derived[Source, Target].into(source)

        assert(result)(
          isRight(
            equalTo(
              Target(
                f6 = 'f',
                f1 = 1,
                f4 = 4.0,
                f2 = "two",
                f5 = 5L,
                f3 = true
              )
            )
          )
        )
      }
    ),
    suite("Reordering with Type Coercion")(
      test("reorders and widens types") {
        case class Source(x: Int, y: Int)
        case class Target(y: Long, x: Long)

        val source = Source(x = 10, y = 20)
        val result = Into.derived[Source, Target].into(source)

        assert(result)(isRight(equalTo(Target(y = 20L, x = 10L))))
      },
      test("reorders and narrows types when values fit") {
        case class Source(a: Long, b: Long)
        case class Target(b: Int, a: Int)

        val source = Source(a = 100L, b = 200L)
        val result = Into.derived[Source, Target].into(source)

        assert(result)(isRight(equalTo(Target(b = 200, a = 100))))
      },
      test("fails when reordering with narrowing overflow") {
        case class Source(a: Long, b: Long)
        case class Target(b: Int, a: Int)

        val source = Source(a = Long.MaxValue, b = 200L)
        val result = Into.derived[Source, Target].into(source)

        assert(result)(isLeft)
      }
    ),
    suite("Priority: Exact Match Takes Precedence")(
      test("exact name match takes precedence over type matching") {
        // Both fields have same type but different names
        // They should match by name, not position
        case class Source(first: String, second: String)
        case class Target(second: String, first: String)

        val source = Source(first = "A", second = "B")
        val result = Into.derived[Source, Target].into(source)

        assert(result)(isRight(equalTo(Target(second = "B", first = "A"))))
      }
    )
  )
}
