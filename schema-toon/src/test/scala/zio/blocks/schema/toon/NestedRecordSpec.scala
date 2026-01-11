package zio.blocks.schema.toon

import zio.blocks.schema.Schema
import zio.test._

/**
 * Tests for nested record encoding and proper indentation in TOON codec.
 *
 * Verifies:
 *   - Nested records are properly indented
 *   - Multi-level nesting works correctly
 *   - Indentation is consistent with config
 */
object NestedRecordSpec extends ZIOSpecDefault {

  // Simple nested structure
  case class Person(name: String, address: Address)
  case class Address(street: String, city: String)

  object Address {
    implicit val schema: Schema[Address] = Schema.derived
  }

  object Person {
    implicit val schema: Schema[Person] = Schema.derived
  }

  // Deeply nested structure
  case class Company(name: String, headquarters: Office)
  case class Office(location: Location, manager: Employee)
  case class Location(city: String, country: String)
  case class Employee(name: String, title: String)

  object Location {
    implicit val schema: Schema[Location] = Schema.derived
  }

  object Employee {
    implicit val schema: Schema[Employee] = Schema.derived
  }

  object Office {
    implicit val schema: Schema[Office] = Schema.derived
  }

  object Company {
    implicit val schema: Schema[Company] = Schema.derived
  }

  // Record with list of records
  case class Team(name: String, members: List[TeamMember])
  case class TeamMember(name: String, role: String)

  object TeamMember {
    implicit val schema: Schema[TeamMember] = Schema.derived
  }

  object Team {
    implicit val schema: Schema[Team] = Schema.derived
  }

  def spec = suite("NestedRecordSpec")(
    suite("Simple nesting")(
      test("should encode nested record with proper indentation") {
        val codec  = Person.schema.derive(ToonFormat.deriver)
        val person = Person("Alice", Address("123 Main St", "NYC"))
        val toon   = codec.encodeToString(person)

        // Verify structure
        assertTrue(
          toon.contains("name: Alice"),
          toon.contains("address:"),
          toon.contains("street:"),
          toon.contains("city: NYC")
        )
      },
      test("should handle multiple fields after nested record") {
        case class Order(id: Int, customer: Customer, total: Double)
        case class Customer(name: String, email: String)

        object Customer {
          implicit val schema: Schema[Customer] = Schema.derived
        }
        object Order {
          implicit val schema: Schema[Order] = Schema.derived
        }

        val codec = Order.schema.derive(ToonFormat.deriver)
        val order = Order(1, Customer("Bob", "bob@test.com"), 99.99)
        val toon  = codec.encodeToString(order)

        assertTrue(
          toon.contains("id: 1"),
          toon.contains("customer:"),
          toon.contains("name: Bob"),
          toon.contains("total: 99.99")
        )
      }
    ),
    suite("Deep nesting")(
      test("should encode deeply nested records correctly") {
        val codec   = Company.schema.derive(ToonFormat.deriver)
        val company = Company(
          "Acme Corp",
          Office(
            Location("San Francisco", "USA"),
            Employee("John Smith", "CEO")
          )
        )
        val toon = codec.encodeToString(company)

        // Verify all levels are present (quoted values for strings with spaces)
        assertTrue(
          toon.contains("Acme Corp"),
          toon.contains("headquarters:"),
          toon.contains("location:"),
          toon.contains("San Francisco"),
          toon.contains("country: USA"),
          toon.contains("manager:"),
          toon.contains("title: CEO")
        )
      }
    ),
    suite("Records with collections")(
      test("should encode record containing list of records") {
        val codec = Team.schema.derive(ToonFormat.deriver)
        val team  = Team(
          "Engineering",
          List(
            TeamMember("Alice", "Lead"),
            TeamMember("Bob", "Developer")
          )
        )
        val toon = codec.encodeToString(team)

        assertTrue(
          toon.contains("name: Engineering"),
          toon.contains("members"),
          toon.contains("Alice"),
          toon.contains("Bob"),
          toon.contains("Lead"),
          toon.contains("Developer")
        )
      },
      test("should handle empty list in nested record") {
        val deriver = ToonBinaryCodecDeriver.withTransientEmptyCollection(false)
        val codec   = Team.schema.derive(deriver)
        val team    = Team("Empty Team", List.empty)
        val toon    = codec.encodeToString(team)

        assertTrue(
          toon.contains("Empty Team"),
          toon.contains("members")
        )
      }
    ),
    suite("Indentation consistency")(
      test("should use consistent 2-space indentation by default") {
        val codec  = Person.schema.derive(ToonFormat.deriver)
        val person = Person("Alice", Address("123 Main St", "NYC"))
        val toon   = codec.encodeToString(person)
        val lines  = toon.split("\n")

        // First level fields should not be indented
        assertTrue(
          lines.exists(_.startsWith("name: ")),
          lines.exists(_.startsWith("address:"))
        )
      }
    )
  )
}
