package zio.blocks.schema.migration

import zio.blocks.schema._
import zio.test._

/**
 * Test suite for MigrationBuilder DSL.
 *
 * Per review feedback:
 *   - Tests that verify complete migrations use `.build` with compile-time
 *     validation
 *   - Tests that verify action creation inspect builder.actions directly
 *   - Includes nested structure migrations (2-3 levels deep)
 */
object MigrationBuilderSpec extends ZIOSpecDefault {

  // ============================================================================
  // Simple flat types
  // ============================================================================
  case class PersonV0(name: String, age: Int)
  case class PersonV1(name: String, age: Int, country: String)
  case class PersonV2(fullName: String, age: Int)
  case class PersonOpt(name: Option[String], age: Int)

  implicit val personV0Schema: Schema[PersonV0]   = Schema.derived[PersonV0]
  implicit val personV1Schema: Schema[PersonV1]   = Schema.derived[PersonV1]
  implicit val personV2Schema: Schema[PersonV2]   = Schema.derived[PersonV2]
  implicit val personOptSchema: Schema[PersonOpt] = Schema.derived[PersonOpt]

  // ============================================================================
  // Nested types for nested structure migration tests
  // ============================================================================
  case class AddressV0(street: String, city: String, zip: String)
  case class AddressV1(streetName: String, city: String, zip: String)
  case class AddressV2(streetName: String, city: String, postalCode: String)

  case class PersonWithAddressV0(name: String, address: AddressV0)
  case class PersonWithAddressV1(name: String, address: AddressV1)
  case class PersonWithAddressV2(name: String, address: AddressV2)

  implicit val addressV0Schema: Schema[AddressV0]                     = Schema.derived[AddressV0]
  implicit val addressV1Schema: Schema[AddressV1]                     = Schema.derived[AddressV1]
  implicit val addressV2Schema: Schema[AddressV2]                     = Schema.derived[AddressV2]
  implicit val personWithAddressV0Schema: Schema[PersonWithAddressV0] = Schema.derived[PersonWithAddressV0]
  implicit val personWithAddressV1Schema: Schema[PersonWithAddressV1] = Schema.derived[PersonWithAddressV1]
  implicit val personWithAddressV2Schema: Schema[PersonWithAddressV2] = Schema.derived[PersonWithAddressV2]

  // ============================================================================
  // Deeply nested types (3 levels)
  // ============================================================================
  case class CountryV0(name: String, code: String)
  case class CountryV1(countryName: String, code: String)

  case class LocationV0(city: String, country: CountryV0)
  case class LocationV1(city: String, country: CountryV1)

  case class CompanyV0(name: String, location: LocationV0)
  case class CompanyV1(name: String, location: LocationV1)

  implicit val countryV0Schema: Schema[CountryV0]   = Schema.derived[CountryV0]
  implicit val countryV1Schema: Schema[CountryV1]   = Schema.derived[CountryV1]
  implicit val locationV0Schema: Schema[LocationV0] = Schema.derived[LocationV0]
  implicit val locationV1Schema: Schema[LocationV1] = Schema.derived[LocationV1]
  implicit val companyV0Schema: Schema[CompanyV0]   = Schema.derived[CompanyV0]
  implicit val companyV1Schema: Schema[CompanyV1]   = Schema.derived[CompanyV1]

  def spec: Spec[TestEnvironment, Any] =
    suite("MigrationBuilderSpec")(
      suite("Flat Structure Migrations with .build")(
        test("complete migration with addFieldWithDefault applies correctly") {
          val migration = Migration
            .builder[PersonV0, PersonV1]
            .addFieldWithDefault("country", "USA")
            .build

          val input  = PersonV0("John Doe", 30)
          val result = migration.apply(input)

          assertTrue(result.isRight) &&
          assertTrue(result.map(_.country).getOrElse("") == "USA") &&
          assertTrue(result.map(_.name).getOrElse("") == "John Doe") &&
          assertTrue(result.map(_.age).getOrElse(0) == 30)
        },
        test("complete migration with renameField applies correctly") {
          val migration = Migration
            .builder[PersonV0, PersonV2]
            .renameField(_.name, _.fullName)
            .build

          val input  = PersonV0("John Doe", 30)
          val result = migration.apply(input)

          assertTrue(result.isRight) &&
          assertTrue(result.map(_.fullName).getOrElse("") == "John Doe") &&
          assertTrue(result.map(_.age).getOrElse(0) == 30)
        },
        test("complete migration with optionalizeField applies correctly") {
          val migration = Migration
            .builder[PersonV0, PersonOpt]
            .optionalizeField(_.name)
            .build

          val input  = PersonV0("Alice", 25)
          val result = migration.apply(input)

          assertTrue(result.isRight) &&
          assertTrue(result.map(_.name).getOrElse(None) == Some("Alice"))
        },
        test("describe returns human-readable description") {
          val migration = Migration
            .builder[PersonV0, PersonV2]
            .renameField(_.name, _.fullName)
            .build

          assertTrue(migration.describe.contains("Rename"))
        },
        test("all actions have 'at' field set to root for top-level") {
          val migration = Migration
            .builder[PersonV0, PersonV2]
            .renameField(_.name, _.fullName)
            .build

          assertTrue(migration.dynamicMigration.actions.head.at == DynamicOptic.root)
        },
        test("type-safe selector extracts correct field names") {
          val migration = Migration
            .builder[PersonV0, PersonV2]
            .renameField(_.name, _.fullName)
            .build

          val action = migration.dynamicMigration.actions.head.asInstanceOf[MigrationAction.Rename]
          assertTrue(action.from == "name") &&
          assertTrue(action.to == "fullName")
        }
      ),
      suite("Action Creation Verification")(
        test("addFieldWithDefault creates AddField action") {
          val builder = Migration
            .builder[PersonV0, PersonV1]
            .addFieldWithDefault("country", "USA")

          assertTrue(builder.actions.length == 1) &&
          assertTrue(builder.actions.head.isInstanceOf[MigrationAction.AddField])
        },
        test("dropField creates DropField action") {
          val builder = Migration
            .builder[PersonV0, PersonV0]
            .dropFieldWithDefault("name", "Unknown")

          val action = builder.actions.head.asInstanceOf[MigrationAction.DropField]
          assertTrue(action.fieldName == "name")
        },
        test("addField with selector creates AddField action") {
          val builder = Migration
            .builder[PersonV0, PersonV1]
            .addField(_.country, DynamicValue.Primitive(PrimitiveValue.String("USA")))

          val action = builder.actions.head.asInstanceOf[MigrationAction.AddField]
          assertTrue(action.fieldName == "country")
        },
        test("renameCase creates RenameCase action") {
          val builder = Migration
            .builder[PersonV0, PersonV0]
            .renameCase("OldCase", "NewCase")

          assertTrue(builder.actions.head.isInstanceOf[MigrationAction.RenameCase])
        },
        test("mandateFieldWithDefault creates Mandate action") {
          val builder = Migration
            .builder[PersonV0, PersonV1]
            .mandateFieldWithDefault("country", "Unknown")

          assertTrue(builder.actions.head.isInstanceOf[MigrationAction.Mandate])
        }
      ),
      suite("Nested Structure Migrations with .build")(
        test("renames field inside nested record and applies correctly") {
          val migration = Migration
            .builder[PersonWithAddressV0, PersonWithAddressV1]
            .renameField(_.address.street, _.address.streetName)
            .build

          val input  = PersonWithAddressV0("John", AddressV0("123 Main St", "NYC", "10001"))
          val result = migration.apply(input)

          assertTrue(result.isRight) &&
          assertTrue(result.map(_.address.streetName).getOrElse("") == "123 Main St") &&
          assertTrue(result.map(_.address.city).getOrElse("") == "NYC")
        },
        test("nested rename action has correct optic path") {
          val migration = Migration
            .builder[PersonWithAddressV0, PersonWithAddressV1]
            .renameField(_.address.street, _.address.streetName)
            .build

          val action = migration.dynamicMigration.actions.head.asInstanceOf[MigrationAction.Rename]
          assertTrue(action.at == DynamicOptic.root.field("address")) &&
          assertTrue(action.from == "street") &&
          assertTrue(action.to == "streetName")
        },
        test("multiple nested field renames in same record") {
          val migration = Migration
            .builder[PersonWithAddressV1, PersonWithAddressV2]
            .renameField(_.address.zip, _.address.postalCode)
            .build

          val input  = PersonWithAddressV1("John", AddressV1("123 Main St", "NYC", "10001"))
          val result = migration.apply(input)

          assertTrue(result.isRight) &&
          assertTrue(result.map(_.address.postalCode).getOrElse("") == "10001")
        }
      ),
      suite("Deeply Nested Structure Migrations (3 Levels) with .build")(
        test("renames field at 3rd nesting level and applies correctly") {
          val migration = Migration
            .builder[CompanyV0, CompanyV1]
            .renameField(_.location.country.name, _.location.country.countryName)
            .build

          val input = CompanyV0(
            "Acme Corp",
            LocationV0("New York", CountryV0("United States", "US"))
          )
          val result = migration.apply(input)

          assertTrue(result.isRight) &&
          assertTrue(result.map(_.location.country.countryName).getOrElse("") == "United States") &&
          assertTrue(result.map(_.location.country.code).getOrElse("") == "US")
        },
        test("deeply nested action has correct multi-level optic path") {
          val migration = Migration
            .builder[CompanyV0, CompanyV1]
            .renameField(_.location.country.name, _.location.country.countryName)
            .build

          val action = migration.dynamicMigration.actions.head.asInstanceOf[MigrationAction.Rename]
          assertTrue(action.at == DynamicOptic.root.field("location").field("country")) &&
          assertTrue(action.from == "name") &&
          assertTrue(action.to == "countryName")
        }
      )
    )
}
