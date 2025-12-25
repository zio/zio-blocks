package zio.blocks.schema.into.products

import zio.test._
import zio.blocks.schema._

object NestedProductsSpec extends ZIOSpecDefault {

  def spec = suite("NestedProductsSpec")(
    suite("Basic Nesting (2 Levels)")(
      test("should convert nested case class (Person with Address)") {
        case class AddressV1(street: String, city: String, zip: Int)
        case class AddressV2(street: String, city: String, zip: Int)
        case class PersonV1(name: String, address: AddressV1)
        case class PersonV2(name: String, address: AddressV2)

        val derivation = Into.derived[PersonV1, PersonV2]
        val input      = PersonV1("Alice", AddressV1("123 Main St", "NYC", 10001))
        val result     = derivation.into(input)

        assertTrue(result.isRight)
        assertTrue(result.map(_.name) == Right("Alice"))
        assertTrue(result.map(_.address.street) == Right("123 Main St"))
        assertTrue(result.map(_.address.city) == Right("NYC"))
        assertTrue(result.map(_.address.zip) == Right(10001))
      },
      test("should convert nested case class with field reordering") {
        case class AddressV1(street: String, city: String, zip: Int)
        case class AddressV2(zip: Int, street: String, city: String) // Reordered
        case class PersonV1(name: String, address: AddressV1)
        case class PersonV2(name: String, address: AddressV2)

        val derivation = Into.derived[PersonV1, PersonV2]
        val input      = PersonV1("Bob", AddressV1("456 Oak Ave", "LA", 90001))
        val result     = derivation.into(input)

        assertTrue(result.isRight)
        assertTrue(result.map(_.name) == Right("Bob"))
        assertTrue(result.map(_.address.zip) == Right(90001))
        assertTrue(result.map(_.address.street) == Right("456 Oak Ave"))
        assertTrue(result.map(_.address.city) == Right("LA"))
      }
    ),
    suite("Nesting with Coercion")(
      test("should convert nested case class with coercion in nested field") {
        case class AddressV1(street: String, city: String, zip: Int)
        case class AddressV2(street: String, city: String, zip: Long) // zip: Int -> Long
        case class PersonV1(name: String, address: AddressV1)
        case class PersonV2(name: String, address: AddressV2)

        val derivation = Into.derived[PersonV1, PersonV2]
        val input      = PersonV1("Charlie", AddressV1("789 Pine Rd", "SF", 94102))
        val result     = derivation.into(input)

        assertTrue(result.isRight)
        assertTrue(result.map(_.name) == Right("Charlie"))
        assertTrue(result.map(_.address.zip) == Right(94102L))
      },
      test("should convert nested case class with multiple coercions") {
        case class AddressV1(street: String, city: String, zip: Int, population: Int)
        case class AddressV2(street: String, city: String, zip: Long, population: Double) // Both coerced
        case class PersonV1(name: String, address: AddressV1)
        case class PersonV2(name: String, address: AddressV2)

        val derivation = Into.derived[PersonV1, PersonV2]
        val input      = PersonV1("David", AddressV1("321 Elm St", "Boston", 2101, 700000))
        val result     = derivation.into(input)

        assertTrue(result.isRight)
        assertTrue(result.map(_.address.zip) == Right(2101L))
        assertTrue(result.map(_.address.population) == Right(700000.0))
      }
    ),
    suite("Deep Nesting (3+ Levels)")(
      test("should convert 3-level nested case class (Company -> Dept -> Employee)") {
        case class EmployeeV1(name: String, id: Int)
        case class EmployeeV2(name: String, id: Int)
        case class DeptV1(name: String, manager: EmployeeV1)
        case class DeptV2(name: String, manager: EmployeeV2)
        case class CompanyV1(name: String, dept: DeptV1)
        case class CompanyV2(name: String, dept: DeptV2)

        val derivation = Into.derived[CompanyV1, CompanyV2]
        val input      = CompanyV1("Acme", DeptV1("Engineering", EmployeeV1("Alice", 1)))
        val result     = derivation.into(input)

        assertTrue(result.isRight)
        assertTrue(result.map(_.name) == Right("Acme"))
        assertTrue(result.map(_.dept.name) == Right("Engineering"))
        assertTrue(result.map(_.dept.manager.name) == Right("Alice"))
        assertTrue(result.map(_.dept.manager.id) == Right(1))
      },
      test("should convert 3-level nested with coercion at different levels") {
        case class EmployeeV1(name: String, id: Int)
        case class EmployeeV2(name: String, id: Long) // id: Int -> Long
        case class DeptV1(name: String, budget: Int, manager: EmployeeV1)
        case class DeptV2(name: String, budget: Long, manager: EmployeeV2) // budget: Int -> Long
        case class CompanyV1(name: String, dept: DeptV1)
        case class CompanyV2(name: String, dept: DeptV2)

        val derivation = Into.derived[CompanyV1, CompanyV2]
        val input      = CompanyV1("TechCorp", DeptV1("Sales", 100000, EmployeeV1("Bob", 2)))
        val result     = derivation.into(input)

        assertTrue(result.isRight)
        assertTrue(result.map(_.dept.budget) == Right(100000L))
        assertTrue(result.map(_.dept.manager.id) == Right(2L))
      },
      test("should convert 4-level nested case class") {
        case class Level4V1(value: Int)
        case class Level4V2(value: Int)
        case class Level3V1(name: String, level4: Level4V1)
        case class Level3V2(name: String, level4: Level4V2)
        case class Level2V1(id: Int, level3: Level3V1)
        case class Level2V2(id: Int, level3: Level3V2)
        case class Level1V1(title: String, level2: Level2V1)
        case class Level1V2(title: String, level2: Level2V2)

        val derivation = Into.derived[Level1V1, Level1V2]
        val input      = Level1V1("Top", Level2V1(1, Level3V1("Mid", Level4V1(42))))
        val result     = derivation.into(input)

        assertTrue(result.isRight)
        assertTrue(result.map(_.title) == Right("Top"))
        assertTrue(result.map(_.level2.id) == Right(1))
        assertTrue(result.map(_.level2.level3.name) == Right("Mid"))
        assertTrue(result.map(_.level2.level3.level4.value) == Right(42))
      }
    ),
    suite("Multiple Nested Fields")(
      test("should convert case class with multiple nested fields") {
        case class AddressV1(street: String, city: String)
        case class AddressV2(street: String, city: String)
        case class ContactV1(email: String, phone: String)
        case class ContactV2(email: String, phone: String)
        case class PersonV1(name: String, address: AddressV1, contact: ContactV1)
        case class PersonV2(name: String, address: AddressV2, contact: ContactV2)

        val derivation = Into.derived[PersonV1, PersonV2]
        val input      = PersonV1(
          "Eve",
          AddressV1("999 Park Ave", "Chicago"),
          ContactV1("eve@example.com", "555-1234")
        )
        val result = derivation.into(input)

        assertTrue(result.isRight)
        assertTrue(result.map(_.name) == Right("Eve"))
        assertTrue(result.map(_.address.city) == Right("Chicago"))
        assertTrue(result.map(_.contact.email) == Right("eve@example.com"))
      },
      test("should convert case class with nested fields and coercion") {
        case class AddressV1(street: String, zip: Int)
        case class AddressV2(street: String, zip: Long) // zip: Int -> Long
        case class ContactV1(email: String, age: Int)
        case class ContactV2(email: String, age: Long) // age: Int -> Long
        case class PersonV1(name: String, address: AddressV1, contact: ContactV1)
        case class PersonV2(name: String, address: AddressV2, contact: ContactV2)

        val derivation = Into.derived[PersonV1, PersonV2]
        val input      = PersonV1(
          "Frank",
          AddressV1("111 First St", 12345),
          ContactV1("frank@example.com", 35)
        )
        val result = derivation.into(input)

        assertTrue(result.isRight)
        assertTrue(result.map(_.address.zip) == Right(12345L))
        assertTrue(result.map(_.contact.age) == Right(35L))
      }
    ),
    suite("Nested with Field Renaming")(
      test("should convert nested case class with field renaming in nested type (unique types)") {
        case class AddressV1(streetName: String, zipCode: Int, isResidential: Boolean)
        case class AddressV2(street: String, zip: Int, residential: Boolean) // Renamed fields, all unique types
        case class PersonV1(fullName: String, homeAddress: AddressV1)
        case class PersonV2(name: String, address: AddressV2) // Renamed fields

        val derivation = Into.derived[PersonV1, PersonV2]
        val input      = PersonV1("Grace", AddressV1("222 Second St", 98101, true))
        val result     = derivation.into(input)

        assertTrue(result.isRight)
        assertTrue(result.map(_.name) == Right("Grace"))
        assertTrue(result.map(_.address.street) == Right("222 Second St"))
        assertTrue(result.map(_.address.zip) == Right(98101))
        assertTrue(result.map(_.address.residential) == Right(true))
      }
    )
  )
}
