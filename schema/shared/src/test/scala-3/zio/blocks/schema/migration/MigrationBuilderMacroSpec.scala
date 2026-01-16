package zio.blocks.schema.migration

import zio.blocks.schema._
import zio.test._

/**
 * Test suite for Scala 3 Macro-based MigrationBuilder DSL.
 *
 * Per review feedback: All tests use `.build` (not `.buildPartial`) and include
 * nested structure migrations.
 */
object MigrationBuilderMacroSpec extends ZIOSpecDefault {

  // ============================================================================
  // Nested types for testing nested path extraction
  // ============================================================================
  case class Address(street: String, city: String)
  case class Location(geo: String, city: String)

  case class PersonV0(name: String, age: Int, address: Address)
  case class PersonV1(fullName: String, age: Int, address: Location, country: String)

  // Simpler types for individual nested operations
  case class PersonWithAddress(name: String, address: Address)
  case class PersonWithLocation(name: String, address: Location)

  // Extra types for Negative Validation
  case class PersonV2(fullName: String, age: Int)
  case class PersonV3(age: Int)
  case class PersonOpt(name: Option[String], age: Int)

  case class AddressV0(street: String, city: String)
  case class AddressV1(streetName: String, city: String)
  case class PersonWithAddressV0(name: String, address: AddressV0)
  case class PersonWithAddressV1(name: String, address: AddressV1)

  implicit val addressSchema: Schema[Address]                       = Schema.derived[Address]
  implicit val locationSchema: Schema[Location]                     = Schema.derived[Location]
  implicit val personV0Schema: Schema[PersonV0]                     = Schema.derived[PersonV0]
  implicit val personV1Schema: Schema[PersonV1]                     = Schema.derived[PersonV1]
  implicit val personWithAddressSchema: Schema[PersonWithAddress]   = Schema.derived[PersonWithAddress]
  implicit val personWithLocationSchema: Schema[PersonWithLocation] = Schema.derived[PersonWithLocation]

  implicit val personV2Schema: Schema[PersonV2]                       = Schema.derived[PersonV2]
  implicit val personV3Schema: Schema[PersonV3]                       = Schema.derived[PersonV3]
  implicit val personOptSchema: Schema[PersonOpt]                     = Schema.derived[PersonOpt]
  implicit val addressV0Schema: Schema[AddressV0]                     = Schema.derived[AddressV0]
  implicit val addressV1Schema: Schema[AddressV1]                     = Schema.derived[AddressV1]
  implicit val personWithAddressV0Schema: Schema[PersonWithAddressV0] = Schema.derived[PersonWithAddressV0]
  implicit val personWithAddressV1Schema: Schema[PersonWithAddressV1] = Schema.derived[PersonWithAddressV1]

  // ============================================================================
  // Deeply nested types (3 levels)
  // ============================================================================
  case class Country(name: String, code: String)
  case class CountryRenamed(countryName: String, code: String)
  case class City(name: String, country: Country)
  case class CityRenamed(name: String, country: CountryRenamed)
  case class Office(id: Int, city: City)
  case class OfficeRenamed(id: Int, city: CityRenamed)

  implicit val countrySchema: Schema[Country]               = Schema.derived[Country]
  implicit val countryRenamedSchema: Schema[CountryRenamed] = Schema.derived[CountryRenamed]
  implicit val citySchema: Schema[City]                     = Schema.derived[City]
  implicit val cityRenamedSchema: Schema[CityRenamed]       = Schema.derived[CityRenamed]
  implicit val officeSchema: Schema[Office]                 = Schema.derived[Office]
  implicit val officeRenamedSchema: Schema[OfficeRenamed]   = Schema.derived[OfficeRenamed]

  def spec: Spec[TestEnvironment, Any] =
    suite("MigrationBuilderMacroSpec")(
      suite("Top-level Field Operations")(
        test("renameField with selector at top level") {
          case class V0(name: String)
          case class V1(fullName: String)
          val _                       = V0; val _ = V1
          implicit val v0: Schema[V0] = Schema.derived
          implicit val v1: Schema[V1] = Schema.derived

          val migration = Migration
            .builder[V0, V1]
            .renameField(_.name, _.fullName)
            .build

          assertTrue(
            migration.dynamicMigration.actions.head == MigrationAction.Rename(DynamicOptic.root, "name", "fullName")
          )
        },
        test("addField with selector at top level") {
          case class V0(name: String)
          case class V1(name: String, country: String)
          val _                       = V0; val _ = V1
          implicit val v0: Schema[V0] = Schema.derived
          implicit val v1: Schema[V1] = Schema.derived

          val migration = Migration
            .builder[V0, V1]
            .addField(_.country, DynamicValue.Primitive(PrimitiveValue.String("USA")))
            .build

          val action = migration.dynamicMigration.actions.head.asInstanceOf[MigrationAction.AddField]
          assertTrue(action.at == DynamicOptic.root) &&
          assertTrue(action.fieldName == "country")
        },
        test("dropField with selector - verifies action") {
          case class V0(name: String, obsolete: Int)
          case class V1(name: String)
          val _                       = V0; val _ = V1
          implicit val v0: Schema[V0] = Schema.derived
          implicit val v1: Schema[V1] = Schema.derived

          val migration = Migration
            .builder[V0, V1]
            .dropField(_.obsolete)
            .build

          val action = migration.dynamicMigration.actions.head.asInstanceOf[MigrationAction.DropField]
          assertTrue(action.at == DynamicOptic.root) &&
          assertTrue(action.fieldName == "obsolete")
        }
      ),
      suite("Nested Field Operations")(
        test("renameField with nested selector") {
          val migration = Migration
            .builder[PersonWithAddress, PersonWithLocation]
            .renameField(_.address.street, _.address.geo)
            .build

          val action = migration.dynamicMigration.actions.head

          assertTrue(action.at == DynamicOptic.root.field("address")) &&
          assertTrue(action.asInstanceOf[MigrationAction.Rename].from == "street") &&
          assertTrue(action.asInstanceOf[MigrationAction.Rename].to == "geo")
        },
        test("nested rename applies correctly") {
          val migration = Migration
            .builder[PersonWithAddress, PersonWithLocation]
            .renameField(_.address.street, _.address.geo)
            .build

          val input  = PersonWithAddress("John", Address("123 Main St", "NYC"))
          val result = migration.apply(input)

          assertTrue(result.isRight) &&
          assertTrue(result.map(_.address.geo).getOrElse("") == "123 Main St") &&
          assertTrue(result.map(_.address.city).getOrElse("") == "NYC")
        }
      ),
      suite("Deeply Nested Field Operations (3 Levels)")(
        test("renameField at 3rd nesting level extracts correct path") {
          val migration = Migration
            .builder[Office, OfficeRenamed]
            .renameField(_.city.country.name, _.city.country.countryName)
            .build

          val action = migration.dynamicMigration.actions.head.asInstanceOf[MigrationAction.Rename]
          assertTrue(action.at == DynamicOptic.root.field("city").field("country")) &&
          assertTrue(action.from == "name") &&
          assertTrue(action.to == "countryName")
        },
        test("deeply nested rename applies correctly") {
          val migration = Migration
            .builder[Office, OfficeRenamed]
            .renameField(_.city.country.name, _.city.country.countryName)
            .build

          val input  = Office(1, City("New York", Country("United States", "US")))
          val result = migration.apply(input)

          assertTrue(result.isRight) &&
          assertTrue(result.map(_.city.country.countryName).getOrElse("") == "United States") &&
          assertTrue(result.map(_.city.country.code).getOrElse("") == "US") &&
          assertTrue(result.map(_.city.name).getOrElse("") == "New York")
        }
      ),
      suite("Negative Compile-time Validation")(
        suite("Flat Structure Validation")(
          test("rejects empty builder when target has more fields") {
            typeCheck("""
              Migration.builder[PersonV0, PersonV1].build
            """).map { result =>
              assertTrue(result.isLeft) &&
              assertTrue(result.swap.getOrElse("").contains("incomplete")) &&
              assertTrue(result.swap.getOrElse("").contains("country"))
            }
          },
          test("rejects empty builder when field renamed in target") {
            typeCheck("""
              Migration.builder[PersonV0, PersonV2].build
            """).map { result => // V2 has fullName instead of name
              assertTrue(result.isLeft) &&
              assertTrue(result.swap.getOrElse("").contains("incomplete"))
            }
          },
          test("rejects empty builder when field dropped in target") {
            typeCheck("""
              Migration.builder[PersonV0, PersonV3].build
            """).map { result =>
              assertTrue(result.isLeft) &&
              assertTrue(result.swap.getOrElse("").contains("name"))
            }
          },
          test("rejects when addField is needed but not provided") {
            typeCheck("""
              Migration.builder[PersonV0, PersonV1]
                // Missing: .addFieldWithDefault("country", "USA")
                .build
            """).map { result =>
              assertTrue(result.isLeft) &&
              assertTrue(result.swap.getOrElse("").contains("country"))
            }
          },
          test("rejects when renameField is needed but not provided") {
            typeCheck("""
              Migration.builder[PersonV0, PersonV2]
                // Missing: .renameField(_.name, _.fullName)
                .build
            """).map { result =>
              assertTrue(result.isLeft)
            }
          },
          test("rejects when dropField is needed but not provided") {
            typeCheck("""
              Migration.builder[PersonV0, PersonV3]
                // Missing: .dropField(_.name)
                .build
            """).map { result =>
              assertTrue(result.isLeft) &&
              assertTrue(result.swap.getOrElse("").contains("name"))
            }
          }
        ),
        suite("Nested Structure Validation")(
          test("rejects when nested fields are unmapped") {
            // PersonWithAddressV0.address.street -> PersonWithAddressV1.address.streetName
            typeCheck("""
              Migration.builder[PersonWithAddressV0, PersonWithAddressV1].build
            """).map { result =>
              assertTrue(result.isLeft) &&
              assertTrue(result.swap.getOrElse("").contains("incomplete")) &&
              assertTrue(result.swap.getOrElse("").contains("address.street"))
            }
          }
        )
      )
    )
}
