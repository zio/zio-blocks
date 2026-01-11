package zio.blocks.schema.toon

import zio.test._
import zio.blocks.schema.Schema

// Test data classes
case class Simple(name: String, age: Int)
object Simple {
  implicit val schema: Schema[Simple] = Schema.derived
}

case class Address(street: String, city: String, zip: String)
object Address {
  implicit val schema: Schema[Address] = Schema.derived
}

case class PersonWithAddress(name: String, age: Int, address: Address)
object PersonWithAddress {
  implicit val schema: Schema[PersonWithAddress] = Schema.derived
}

case class Company(name: String, employees: List[Simple])
object Company {
  implicit val schema: Schema[Company] = Schema.derived
}

/**
 * Comprehensive tests for record (case class) codecs.
 */
object ComprehensiveRecordSpec extends ZIOSpecDefault {
  def spec = suite("ComprehensiveRecord")(
    suite("Simple Records")(
      test("basic case class") {
        val codec   = Simple.schema.derive(ToonFormat.deriver)
        val encoded = codec.encodeToString(Simple("Alice", 30))
        assertTrue(encoded == "name: Alice\nage: 30")
      },
      test("string with space is quoted") {
        val codec   = Simple.schema.derive(ToonFormat.deriver)
        val encoded = codec.encodeToString(Simple("Alice Smith", 25))
        assertTrue(encoded == "name: \"Alice Smith\"\nage: 25")
      },
      test("three field record") {
        val codec   = Address.schema.derive(ToonFormat.deriver)
        val encoded = codec.encodeToString(Address("123 Main St", "Springfield", "12345"))
        assertTrue(
          encoded.contains("street: \"123 Main St\"") &&
            encoded.contains("city: Springfield") &&
            encoded.contains("zip: 12345")
        )
      }
    ),
    suite("Nested Records")(
      test("record with nested record") {
        val codec  = PersonWithAddress.schema.derive(ToonFormat.deriver)
        val person = PersonWithAddress(
          "Bob",
          35,
          Address("456 Oak Ave", "Shelbyville", "67890")
        )
        val encoded = codec.encodeToString(person)
        // Should contain all nested fields
        assertTrue(
          encoded.contains("name: Bob") &&
            encoded.contains("age: 35") &&
            encoded.contains("street:") &&
            encoded.contains("city:")
        )
      }
    ),
    suite("Records with Collections")(
      test("record with list of records") {
        val codec   = Company.schema.derive(ToonFormat.deriver)
        val company = Company(
          "Acme Corp",
          List(Simple("Alice", 30), Simple("Bob", 25))
        )
        val encoded = codec.encodeToString(company)
        assertTrue(
          encoded.contains("name: \"Acme Corp\"") &&
            encoded.contains("employees")
        )
      }
    )
  )
}
