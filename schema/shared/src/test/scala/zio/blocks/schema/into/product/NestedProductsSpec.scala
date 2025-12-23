package zio.blocks.schema.into.product

import zio.blocks.schema._
import zio.test._
import zio.test.Assertion._

/**
 * Tests for nested product conversions in Into.
 *
 * Covers:
 *   - Nested case classes
 *   - Multiple levels of nesting
 *   - Type coercion in nested structures
 *   - Nested conversions with field reordering/renaming
 */
object NestedProductsSpec extends ZIOSpecDefault {

  // === Test Data Types ===

  // Simple nesting
  case class AddressV1(street: String, zip: Int)
  case class PersonV1(name: String, address: AddressV1)

  case class AddressV2(street: String, zip: Long)
  case class PersonV2(name: String, address: AddressV2)

  implicit val addressV1ToV2: Into[AddressV1, AddressV2] = Into.derived[AddressV1, AddressV2]

  // Multiple nested levels
  case class ContactInfo(email: String, phone: String)
  case class Address(street: String, city: String, contactInfo: ContactInfo)
  case class Employee(name: String, employeeId: Int, address: Address)

  case class ContactInfoV2(email: String, phone: String)
  case class AddressV2Alt(street: String, city: String, contactInfo: ContactInfoV2)
  case class EmployeeV2(name: String, employeeId: Long, address: AddressV2Alt)

  implicit val contactToContactV2: Into[ContactInfo, ContactInfoV2] = Into.derived[ContactInfo, ContactInfoV2]
  implicit val addressToAddressV2Alt: Into[Address, AddressV2Alt]   = Into.derived[Address, AddressV2Alt]

  // Nested with field reordering
  case class Inner(x: Int, y: Int)
  case class Outer(label: String, inner: Inner)

  case class InnerReordered(y: Int, x: Int)
  case class OuterReordered(inner: InnerReordered, label: String)

  implicit val innerToInnerReordered: Into[Inner, InnerReordered] = Into.derived[Inner, InnerReordered]

  // Nested with type coercion and renaming
  case class ConfigDetails(timeout: Int, retries: Int)
  case class ConfigV1Full(name: String, details: ConfigDetails)

  case class ConfigSettings(timeout: Long, retries: Long)
  case class ConfigV2Full(name: String, settings: ConfigSettings)

  implicit val detailsToSettings: Into[ConfigDetails, ConfigSettings] = Into.derived[ConfigDetails, ConfigSettings]

  def spec: Spec[TestEnvironment, Any] = suite("NestedProductsSpec")(
    suite("Single Level Nesting")(
      test("converts nested case class with type coercion") {
        val person = PersonV1("Alice", AddressV1("Main St", 12345))
        val result = Into.derived[PersonV1, PersonV2].into(person)

        assert(result)(isRight(equalTo(PersonV2("Alice", AddressV2("Main St", 12345L)))))
      },
      test("converts nested case class with field changes in nested type") {
        case class Inner1(a: Int, b: String)
        case class Outer1(name: String, inner: Inner1)

        case class Inner2(a: Long, b: String)
        case class Outer2(name: String, inner: Inner2)

        implicit val inner1To2: Into[Inner1, Inner2] = Into.derived[Inner1, Inner2]

        val outer  = Outer1("test", Inner1(42, "hello"))
        val result = Into.derived[Outer1, Outer2].into(outer)

        assert(result)(isRight(equalTo(Outer2("test", Inner2(42L, "hello")))))
      }
    ),
    suite("Multiple Levels of Nesting")(
      test("converts 3-level nested structure") {
        val employee = Employee(
          name = "Bob",
          employeeId = 123,
          address = Address(
            street = "123 Main St",
            city = "Springfield",
            contactInfo = ContactInfo(
              email = "bob@example.com",
              phone = "555-1234"
            )
          )
        )
        val result = Into.derived[Employee, EmployeeV2].into(employee)

        assert(result)(
          isRight(
            equalTo(
              EmployeeV2(
                name = "Bob",
                employeeId = 123L,
                address = AddressV2Alt(
                  street = "123 Main St",
                  city = "Springfield",
                  contactInfo = ContactInfoV2(
                    email = "bob@example.com",
                    phone = "555-1234"
                  )
                )
              )
            )
          )
        )
      }
    ),
    suite("Nested with Field Reordering")(
      test("reorders fields in nested case class") {
        val outer  = Outer(label = "test", inner = Inner(x = 1, y = 2))
        val result = Into.derived[Outer, OuterReordered].into(outer)

        assert(result)(
          isRight(
            equalTo(
              OuterReordered(
                inner = InnerReordered(y = 2, x = 1),
                label = "test"
              )
            )
          )
        )
      }
    ),
    suite("Nested with Renaming and Coercion")(
      test("renames nested field and applies type coercion") {
        val config = ConfigV1Full(
          name = "production",
          details = ConfigDetails(timeout = 30, retries = 3)
        )
        val result = Into.derived[ConfigV1Full, ConfigV2Full].into(config)

        assert(result)(
          isRight(
            equalTo(
              ConfigV2Full(
                name = "production",
                settings = ConfigSettings(timeout = 30L, retries = 3L)
              )
            )
          )
        )
      }
    ),
    suite("Nested Optional Fields")(
      test("converts nested structure with optional inner") {
        case class Inner(value: Int)
        case class OuterV1(name: String, inner: Inner)

        case class InnerV2(value: Long)
        case class OuterV2(name: String, inner: Option[InnerV2])

        implicit val innerToV2: Into[Inner, InnerV2]          = Into.derived[Inner, InnerV2]
        implicit val innerToOpt: Into[Inner, Option[InnerV2]] = (i: Inner) => innerToV2.into(i).map(Some(_))

        val outer  = OuterV1("test", Inner(42))
        val result = Into.derived[OuterV1, OuterV2].into(outer)

        assert(result)(isRight(equalTo(OuterV2("test", Some(InnerV2(42L))))))
      }
    ),
    suite("Error Propagation in Nested Conversions")(
      test("propagates conversion error from nested field") {
        case class InnerFail(value: Long)
        case class OuterFail(name: String, inner: InnerFail)

        case class InnerTarget(value: Int)
        case class OuterTarget(name: String, inner: InnerTarget)

        implicit val innerFailToTarget: Into[InnerFail, InnerTarget] =
          Into.derived[InnerFail, InnerTarget]

        val outer  = OuterFail("test", InnerFail(Long.MaxValue))
        val result = Into.derived[OuterFail, OuterTarget].into(outer)

        // Should fail due to overflow in nested conversion
        assert(result)(isLeft)
      },
      test("succeeds when nested conversion is valid") {
        case class InnerOk(value: Long)
        case class OuterOk(name: String, inner: InnerOk)

        case class InnerTarget(value: Int)
        case class OuterTarget(name: String, inner: InnerTarget)

        implicit val innerOkToTarget: Into[InnerOk, InnerTarget] =
          Into.derived[InnerOk, InnerTarget]

        val outer  = OuterOk("test", InnerOk(42L))
        val result = Into.derived[OuterOk, OuterTarget].into(outer)

        assert(result)(isRight(equalTo(OuterTarget("test", InnerTarget(42)))))
      }
    ),
    suite("Deeply Nested Structures")(
      test("converts 4-level nested structure") {
        case class Level4(value: Int)
        case class Level3(data: Level4)
        case class Level2(inner: Level3)
        case class Level1(outer: Level2)

        case class Level4V2(value: Long)
        case class Level3V2(data: Level4V2)
        case class Level2V2(inner: Level3V2)
        case class Level1V2(outer: Level2V2)

        implicit val l4To4V2: Into[Level4, Level4V2] = Into.derived[Level4, Level4V2]
        implicit val l3To3V2: Into[Level3, Level3V2] = Into.derived[Level3, Level3V2]
        implicit val l2To2V2: Into[Level2, Level2V2] = Into.derived[Level2, Level2V2]

        val level1 = Level1(Level2(Level3(Level4(42))))
        val result = Into.derived[Level1, Level1V2].into(level1)

        assert(result)(isRight(equalTo(Level1V2(Level2V2(Level3V2(Level4V2(42L)))))))
      }
    ),
    suite("Multiple Nested Fields")(
      test("converts case class with multiple nested fields") {
        case class Location(lat: Double, lon: Double)
        case class Contact(email: String, phone: String)
        case class Business(name: String, location: Location, contact: Contact)

        case class LocationV2(lat: Double, lon: Double)
        case class ContactV2(email: String, phone: String)
        case class BusinessV2(name: String, location: LocationV2, contact: ContactV2)

        implicit val locToV2: Into[Location, LocationV2] = Into.derived[Location, LocationV2]
        implicit val conToV2: Into[Contact, ContactV2]   = Into.derived[Contact, ContactV2]

        val business = Business(
          name = "Acme Corp",
          location = Location(lat = 40.7128, lon = -74.0060),
          contact = Contact(email = "info@acme.com", phone = "555-0100")
        )
        val result = Into.derived[Business, BusinessV2].into(business)

        assert(result)(
          isRight(
            equalTo(
              BusinessV2(
                name = "Acme Corp",
                location = LocationV2(lat = 40.7128, lon = -74.0060),
                contact = ContactV2(email = "info@acme.com", phone = "555-0100")
              )
            )
          )
        )
      }
    )
  )
}
