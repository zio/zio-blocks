package zio.blocks.schema.migration

import zio.blocks.schema._
import zio.test._

/**
 * Test suite for MigrationBuilder DSL.
 */
object MigrationBuilderSpec extends ZIOSpecDefault {

  case class PersonV0(name: String, age: Int)
  case class PersonV1(name: String, age: Int, country: String)

  implicit val personV0Schema: Schema[PersonV0] = Schema.derived[PersonV0]
  implicit val personV1Schema: Schema[PersonV1] = Schema.derived[PersonV1]

  case class PersonV2(fullName: String, age: Int)
  implicit val personV2Schema: Schema[PersonV2] = Schema.derived[PersonV2]

  def spec: Spec[TestEnvironment, Any] =
    suite("MigrationBuilderSpec")(
      test("builds a simple migration with addFieldWithDefault") {
        val migration = Migration
          .builder[PersonV0, PersonV1]
          .addFieldWithDefault("country", "USA")
          .build

        assertTrue(migration.dynamicMigration.actions.length == 1) &&
        assertTrue(migration.dynamicMigration.actions.head.isInstanceOf[MigrationAction.AddField])
      },
      test("builds migration with multiple actions") {
        val migration = Migration
          .builder[PersonV0, PersonV1]
          .addFieldWithDefault("country", "USA")
          .mandateFieldWithDefault("country", "Unknown")
          .build

        assertTrue(migration.dynamicMigration.actions.length == 2)
      },
      test("builds migration with renameCase") {
        val migration = Migration
          .builder[PersonV0, PersonV1]
          .renameCase("OldCase", "NewCase")
          .build

        assertTrue(migration.dynamicMigration.actions.head.isInstanceOf[MigrationAction.RenameCase])
      },
      test("builds migration with renameField") {
        val migration = Migration
          .builder[PersonV0, PersonV1]
          .renameField("oldField", "newField")
          .build

        assertTrue(migration.dynamicMigration.actions.head.isInstanceOf[MigrationAction.Rename])
      },
      test("builds migration with dropField") {
        val migration = Migration
          .builder[PersonV0, PersonV1]
          .dropField("obsoleteField")
          .build

        assertTrue(migration.dynamicMigration.actions.head.isInstanceOf[MigrationAction.DropField])
      },
      test("builds migration with optionalizeField") {
        val migration = Migration
          .builder[PersonV0, PersonV1]
          .optionalizeField("maybeField")
          .build

        assertTrue(migration.dynamicMigration.actions.head.isInstanceOf[MigrationAction.Optionalize])
      },
      test("builder produces working migration") {
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
      test("buildPartial works without validation") {
        val migration = Migration
          .builder[PersonV0, PersonV1]
          .addFieldWithDefault("country", "USA")
          .buildPartial

        assertTrue(migration.dynamicMigration.actions.length == 1)
      },
      test("describe returns human-readable description with paths") {
        val migration = Migration
          .builder[PersonV0, PersonV1]
          .renameField("old", "new")
          .addFieldWithDefault("country", "USA")
          .build

        assertTrue(migration.describe.contains("Rename")) &&
        assertTrue(migration.describe.contains("Add field"))
      },
      test("all actions have 'at' field set to root by default") {
        val migration = Migration
          .builder[PersonV0, PersonV1]
          .renameField("a", "b")
          .build

        assertTrue(migration.dynamicMigration.actions.head.at == DynamicOptic.root)
      },
      test("convenience methods work") {
        val migration = Migration
          .builder[PersonV0, PersonV1]
          .rename("old", "new")
          .add("country", "USA")
          .build

        assertTrue(migration.dynamicMigration.actions.length == 2)
      },
      test("builds migration with type-safe selectors") {
        val migration = Migration
          .builder[PersonV0, PersonV2]
          .renameField(_.name, _.fullName)
          .buildPartial

        assertTrue(migration.dynamicMigration.actions.head.isInstanceOf[MigrationAction.Rename]) &&
        assertTrue(migration.dynamicMigration.actions.head.asInstanceOf[MigrationAction.Rename].from == "name") &&
        assertTrue(migration.dynamicMigration.actions.head.asInstanceOf[MigrationAction.Rename].to == "fullName")
      },
      test("builds migration with addField and dropField selectors") {
        import zio.blocks.schema.{DynamicValue, PrimitiveValue}
        val migration = Migration
          .builder[PersonV0, PersonV1]
          .dropField(_.age)
          .addField(_.country, DynamicValue.Primitive(PrimitiveValue.String("USA")))
          .buildPartial

        val actions = migration.dynamicMigration.actions
        assertTrue(actions.length == 2) &&
        assertTrue(actions(0).isInstanceOf[MigrationAction.DropField]) &&
        assertTrue(actions(0).asInstanceOf[MigrationAction.DropField].fieldName == "age") &&
        assertTrue(actions(1).isInstanceOf[MigrationAction.AddField]) &&
        assertTrue(actions(1).asInstanceOf[MigrationAction.AddField].fieldName == "country")
      }
    )
}
