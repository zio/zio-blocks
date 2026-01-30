package zio.blocks.schema.into.product

import zio.blocks.schema._
import zio.test._
import zio.test.Assertion._

/**
 * Tests for field renaming with unique type matching in Into conversions.
 *
 * Focuses on Priority 3: Unique type match (each type appears once in both
 * source and target).
 *
 * Covers:
 *   - Field renaming with unique types
 *   - Multiple renamed fields
 *   - Combined name match and type match
 */
object FieldRenamingSpec extends ZIOSpecDefault {

  // === Test Data Types ===

  // Simple renaming with unique types
  case class PersonV1(fullName: String, yearOfBirth: Int)
  case class PersonV2(name: String, birthYear: Int)

  // Multiple unique types
  case class Person(name: String, age: Int, active: Boolean)
  case class User(username: String, yearsOld: Int, enabled: Boolean)

  // Renaming with some matching names
  case class ConfigV1(hostName: String, portNumber: Int, timeout: Long)
  case class ConfigV2(host: String, port: Int, timeout: Long)

  // Complex renaming
  case class EmployeeV1(employeeId: Long, fullName: String, departmentName: String, yearsOfService: Int)
  case class EmployeeV2(id: Long, name: String, department: String, tenure: Int)

  // Renaming with type coercion
  case class DataV1(identifier: Int, label: String, count: Int)
  case class DataV2(id: Long, name: String, total: Long)

  def spec: Spec[TestEnvironment, Any] = suite("FieldRenamingSpec")(
    suite("Basic Renaming with Unique Types")(
      test("maps renamed fields by unique type") {
        val person = PersonV1(fullName = "Alice Smith", yearOfBirth = 1990)
        val result = Into.derived[PersonV1, PersonV2].into(person)

        // fullName (String) → name (String), yearOfBirth (Int) → birthYear (Int)
        assert(result)(isRight(equalTo(PersonV2(name = "Alice Smith", birthYear = 1990))))
      },
      test("maps multiple renamed fields with unique types") {
        val person = Person(name = "Bob", age = 30, active = true)
        val result = Into.derived[Person, User].into(person)

        // String→String, Int→Int, Boolean→Boolean (all unique)
        assert(result)(isRight(equalTo(User(username = "Bob", yearsOld = 30, enabled = true))))
      }
    ),
    suite("Partial Renaming")(
      test("maps some fields by name, others by unique type") {
        val config = ConfigV1(hostName = "localhost", portNumber = 8080, timeout = 5000L)
        val result = Into.derived[ConfigV1, ConfigV2].into(config)

        // timeout matches by name, hostName→host and portNumber→port by unique type
        assert(result)(isRight(equalTo(ConfigV2(host = "localhost", port = 8080, timeout = 5000L))))
      }
    ),
    suite("Complex Renaming")(
      test("maps multiple renamed fields in larger structure") {
        val employee = EmployeeV1(
          employeeId = 12345L,
          fullName = "Charlie Brown",
          departmentName = "Engineering",
          yearsOfService = 5
        )
        val result = Into.derived[EmployeeV1, EmployeeV2].into(employee)

        assert(result)(
          isRight(
            equalTo(
              EmployeeV2(
                id = 12345L,
                name = "Charlie Brown",
                department = "Engineering",
                tenure = 5
              )
            )
          )
        )
      }
    ),
    suite("Renaming with Type Coercion")(
      test("renames and widens types") {
        val data   = DataV1(identifier = 100, label = "test", count = 50)
        val result = Into.derived[DataV1, DataV2].into(data)

        // identifier→id (Int→Long), label→name (String→String), count→total (Int→Long)
        assert(result)(isRight(equalTo(DataV2(id = 100L, name = "test", total = 50L))))
      },
      test("renames and narrows when values fit") {
        case class Source(primaryKey: Long, description: String)
        case class Target(id: Int, label: String)

        val source = Source(primaryKey = 42L, description = "item")
        val result = Into.derived[Source, Target].into(source)

        assert(result)(isRight(equalTo(Target(id = 42, label = "item"))))
      },
      test("fails when renaming with narrowing overflow") {
        case class Source(primaryKey: Long, description: String)
        case class Target(id: Int, label: String)

        val source = Source(primaryKey = Long.MaxValue, description = "item")
        val result = Into.derived[Source, Target].into(source)

        assert(result)(isLeft)
      }
    ),
    suite("Unique Type Constraint")(
      test("succeeds when each type appears exactly once") {
        case class Source(a: String, b: Int, c: Boolean, d: Double)
        case class Target(w: String, x: Int, y: Boolean, z: Double)

        val source = Source(a = "text", b = 42, c = true, d = 3.14)
        val result = Into.derived[Source, Target].into(source)

        assert(result)(isRight(equalTo(Target(w = "text", x = 42, y = true, z = 3.14))))
      }
      // Note: Ambiguous cases (duplicate types) are tested in disambiguation specs
    ),
    suite("Edge Cases")(
      test("single renamed field with unique type") {
        case class Source(oldName: String)
        case class Target(newName: String)

        val source = Source(oldName = "value")
        val result = Into.derived[Source, Target].into(source)

        assert(result)(isRight(equalTo(Target(newName = "value"))))
      },
      test("all fields renamed with unique types") {
        case class Source(f1: Int, f2: String, f3: Boolean)
        case class Target(a: Int, b: String, c: Boolean)

        val source = Source(f1 = 1, f2 = "two", f3 = true)
        val result = Into.derived[Source, Target].into(source)

        assert(result)(isRight(equalTo(Target(a = 1, b = "two", c = true))))
      }
    ),
    suite("With Optional Fields")(
      test("renames fields and adds optional field") {
        case class Source(oldName: String, value: Int)
        case class Target(newName: String, value: Int, extra: Option[String])

        val source = Source(oldName = "test", value = 42)
        val result = Into.derived[Source, Target].into(source)

        assert(result)(isRight(equalTo(Target(newName = "test", value = 42, extra = None))))
      },
      test("renames fields and removes optional field") {
        case class Source(oldName: String, value: Int, extra: Option[String])
        case class Target(newName: String, value: Int)

        val source = Source(oldName = "test", value = 42, extra = Some("data"))
        val result = Into.derived[Source, Target].into(source)

        assert(result)(isRight(equalTo(Target(newName = "test", value = 42))))
      }
    )
  )
}
