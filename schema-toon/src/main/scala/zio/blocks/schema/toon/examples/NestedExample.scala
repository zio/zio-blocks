package zio.blocks.schema.toon.examples

import zio.blocks.schema.Schema
import zio.blocks.schema.toon.ToonFormat

/**
 * Example demonstrating nested object encoding in TOON format.
 *
 * Run with:
 * `sbt "schema-toon/runMain zio.blocks.schema.toon.examples.NestedExample"`
 */
object NestedExample extends App {

  // Define nested case classes
  case class Address(
    street: String,
    city: String,
    state: String,
    zip: String
  )

  case class ContactInfo(
    email: String,
    phone: String
  )

  case class Employee(
    id: Int,
    name: String,
    title: String,
    address: Address,
    contact: ContactInfo
  )

  object Address {
    implicit val schema: Schema[Address] = Schema.derived
  }

  object ContactInfo {
    implicit val schema: Schema[ContactInfo] = Schema.derived
  }

  object Employee {
    implicit val schema: Schema[Employee] = Schema.derived
  }

  // Derive the codec
  val codec = Employee.schema.derive(ToonFormat.deriver)

  // Create sample data with nested objects
  val employee = Employee(
    id = 12345,
    name = "Jane Doe",
    title = "Senior Developer",
    address = Address(
      street = "123 Tech Lane",
      city = "San Francisco",
      state = "CA",
      zip = "94102"
    ),
    contact = ContactInfo(
      email = "jane.doe@company.com",
      phone = "555-123-4567"
    )
  )

  println("=== Nested Object Example ===")
  println()
  println(codec.encodeToString(employee))
  println()

  // Even deeper nesting
  case class Department(
    name: String,
    manager: Employee
  )

  case class Company(
    name: String,
    headquarters: Address,
    departments: List[Department]
  )

  object Department {
    implicit val schema: Schema[Department] = Schema.derived
  }

  object Company {
    implicit val schema: Schema[Company] = Schema.derived
  }

  val companyCodec = Company.schema.derive(ToonFormat.deriver)

  val company = Company(
    name = "TechCorp Inc",
    headquarters = Address("1 Innovation Way", "Palo Alto", "CA", "94301"),
    departments = List(
      Department("Engineering", employee),
      Department("Marketing", employee.copy(id = 12346, name = "John Smith", title = "Marketing Lead"))
    )
  )

  println("=== Deeply Nested Company Structure ===")
  println()
  println(companyCodec.encodeToString(company))
}
