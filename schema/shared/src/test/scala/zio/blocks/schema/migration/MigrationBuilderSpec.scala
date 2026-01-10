package zio.blocks.schema.migration

import zio.blocks.schema._
import zio.test._

/**
 * Comprehensive test suite for MigrationBuilder DSL.
 *
 * Tests the type-safe builder API for creating migrations.
 */
object MigrationBuilderSpec extends ZIOSpecDefault {

  // Test schemas - V0 and V1 must be compatible for migration
  case class PersonV0(name: String, age: Int)
  case class PersonV1(name: String, age: Int, country: String)

  enum StatusV0 { case Active, Inactive }
  enum StatusV1 { case Active, Legacy   }
  enum StatusV2 { case Active           }

  implicit val personV0Schema: Schema[PersonV0] = Schema.derived[PersonV0]
  implicit val personV1Schema: Schema[PersonV1] = Schema.derived[PersonV1]
  implicit val statusV0Schema: Schema[StatusV0] = Schema.derived[StatusV0]
  implicit val statusV1Schema: Schema[StatusV1] = Schema.derived[StatusV1]
  implicit val statusV2Schema: Schema[StatusV2] = Schema.derived[StatusV2]

  def spec: Spec[TestEnvironment, Any] =
    suite("MigrationBuilderSpec")(
      test("builds a simple migration with addField") {
        val migration = Migration
          .builder[PersonV0, PersonV1]
          .addField("country", "USA")
          .build
          .toOption
          .get

        assertTrue(migration.dynamic.actions.length == 1) &&
        assertTrue(migration.dynamic.actions.head.isInstanceOf[MigrationAction.AddField])
      },
      test("builds migration with multiple actions") {
        val migration = Migration
          .builder[PersonV0, PersonV1]
          .addField("country", "USA")
          .mandate("country", "Unknown")
          .build
          .toOption
          .get

        assertTrue(migration.dynamic.actions.length == 2)
      },
      test("builds migration with renameCase") {
        val migration = Migration
          .builder[StatusV0, StatusV1]
          .renameCase("Inactive", "Legacy")
          .build
          .toOption
          .get

        assertTrue(migration.dynamic.actions.head.isInstanceOf[MigrationAction.RenameCase])
      },
      test("builds migration with removeCase") {
        val migration = Migration
          .builder[StatusV0, StatusV2]
          .removeCase("Inactive")
          .build
          .toOption
          .get

        assertTrue(migration.dynamic.actions.head.isInstanceOf[MigrationAction.RemoveCase])
      },
      test("builder produces working migration") {
        // V0 has 'name', V1 has 'name' - they're compatible!
        val migration = Migration
          .builder[PersonV0, PersonV1]
          .addField("country", "USA")
          .build
          .toOption
          .get

        val input  = PersonV0("John Doe", 30)
        val result = migration.apply(input)

        // Should succeed since we're adding the missing 'country' field
        assertTrue(result.isRight) &&
        assertTrue(result.map(_.country).getOrElse("") == "USA")
      }
    )
}
