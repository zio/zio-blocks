package zio.blocks.schema.migration

import zio.blocks.schema._
import zio.test._

/**
 * Test suite for Scala 3 Macro-based MigrationBuilderDSL.
 */
object MigrationBuilderMacroSpec extends ZIOSpecDefault {

  case class Address(street: String, city: String)
  case class Location(geo: String)

  case class PersonV0(name: String, age: Int, address: Address)
  case class PersonV1(fullName: String, age: Int, address: Location, country: String)

  implicit val addressSchema: Schema[Address]   = Schema.derived[Address]
  implicit val locationSchema: Schema[Location] = Schema.derived[Location]
  implicit val personV0Schema: Schema[PersonV0] = Schema.derived[PersonV0]
  implicit val personV1Schema: Schema[PersonV1] = Schema.derived[PersonV1]

  def spec: Spec[TestEnvironment, Any] =
    suite("MigrationBuilderMacroSpec")(
      test("renameField with selector") {
        val migration = Migration
          .builder[PersonV0, PersonV1]
          .renameField(_.name, _.fullName)
          .buildPartial

        assertTrue(
          migration.dynamicMigration.actions.head == MigrationAction.Rename(DynamicOptic.root, "name", "fullName")
        )
      },
      test("renameField with nested selector") {
        // Migrating personV0.address.street -> personV1.address.geo (simulating a rename at nested level)
        // Note: Schema structure implies different types for 'address' field, requiring complex migration.
        // For simple rename test, we might need flexible types or just test the path generation.

        // Let's test exact path extraction behavior:
        val builder = MigrationBuilder
          .create(personV0Schema, personV1Schema)
          .renameField(_.address.street, _.address.geo)

        val action = builder.actions.head

        // Expected: Rename at .address path, renaming 'street' to 'geo'
        // The Macro implementation logic:
        // If parents differ, it might be a full path rename? Or currently implementation:
        // if (fromParent != toPath.init) override with opticExpr

        // .address.street -> .address.geo
        // Parents: .address vs .address. Same parent.
        // Action: Rename(at = .address, from = "street", to = "geo")

        assertTrue(action.at == DynamicOptic.root.field("address")) &&
        assertTrue(action.asInstanceOf[MigrationAction.Rename].from == "street") &&
        assertTrue(action.asInstanceOf[MigrationAction.Rename].to == "geo")
      },
      test("addField with selector") {
        val migration = Migration
          .builder[PersonV0, PersonV1]
          .addField(_.country, DynamicValue.Primitive(PrimitiveValue.String("USA")))
          .buildPartial

        val action = migration.dynamicMigration.actions.head.asInstanceOf[MigrationAction.AddField]
        assertTrue(action.at == DynamicOptic.root) &&
        assertTrue(action.fieldName == "country")
      },
      test("dropField with selector") {
        val migration = Migration
          .builder[PersonV0, PersonV1]
          .dropField(_.age)
          .buildPartial

        val action = migration.dynamicMigration.actions.head.asInstanceOf[MigrationAction.DropField]
        assertTrue(action.at == DynamicOptic.root) &&
        assertTrue(action.fieldName == "age")
      },
      test("optionalizeField with nested selector") {
        val builder = MigrationBuilder
          .create(personV0Schema, personV1Schema)
          .optionalizeField(_.address.city)

        val action = builder.actions.head.asInstanceOf[MigrationAction.Optionalize]
        assertTrue(action.at == DynamicOptic.root.field("address")) &&
        assertTrue(action.fieldName == "city")
      }
    )
}
