package zio.blocks.schema.migration

import zio.blocks.schema._
import zio.test._

/**
 * Scala 3 specific tests for MigrationBuilder demonstrating:
 *   - Structural type migrations as described in Issue #519
 *   - Enum case selectors with DynamicOptic.caseOf pattern
 */
object MigrationBuilderScala3Spec extends ZIOSpecDefault {

  // Issue #519 structural types
  type PersonV0 = StructuralInstance { val firstName: String; val lastName: String }
  type PersonV1 = StructuralInstance { val firstName: String; val lastName: String; val age: Int }

  def spec: Spec[TestEnvironment, Any] =
    suite("MigrationBuilderScala3Spec")(
      suite("Structural Type Migrations - Issue #519")(
        test("structural type schema derivation") {
          val v0Schema = Schema.structural[PersonV0]
          val v1Schema = Schema.structural[PersonV1]

          val v0Fields = v0Schema.reflect.asRecord.get.fields
          val v1Fields = v1Schema.reflect.asRecord.get.fields

          assertTrue(v0Fields.length == 2) &&
          assertTrue(v0Fields.exists(_.name == "firstName")) &&
          assertTrue(v0Fields.exists(_.name == "lastName")) &&
          assertTrue(v1Fields.length == 3) &&
          assertTrue(v1Fields.exists(_.name == "age"))
        },
        test("structural type migration with addField - Issue #519 pattern") {
          // Issue #519 example: Migration.newBuilder[PersonV0, PersonV1].addField(_.age, 0).build
          // For structural types, we use the string-based API as they use selectDynamic
          implicit val v0Schema: Schema[PersonV0] = Schema.structural[PersonV0]
          implicit val v1Schema: Schema[PersonV1] = Schema.structural[PersonV1]

          val migration = Migration
            .builder[PersonV0, PersonV1]
            .addField("age", 0) // String-based for structural types
            .buildPartial

          assertTrue(migration.dynamic.actions.length == 1) &&
          assertTrue(migration.dynamic.actions.head.isInstanceOf[MigrationAction.AddField])
        },
        test("structural type migration produces correct DynamicOptic path") {
          implicit val v0Schema: Schema[PersonV0] = Schema.structural[PersonV0]
          implicit val v1Schema: Schema[PersonV1] = Schema.structural[PersonV1]

          val migration = Migration
            .builder[PersonV0, PersonV1]
            .addField("age", 0)
            .buildPartial

          val addFieldAction = migration.dynamic.actions.head.asInstanceOf[MigrationAction.AddField]

          // Verify the optic path is correctly extracted
          assertTrue(addFieldAction.at.nodes.last match {
            case DynamicOptic.Node.Field(name) => name == "age"
            case _                             => false
          })
        },
        test("structural type with multiple fields") {
          type PersonV2 = StructuralInstance {
            val firstName: String; val lastName: String; val age: Int; val country: String
          }

          implicit val v0Schema: Schema[PersonV0] = Schema.structural[PersonV0]
          implicit val v2Schema: Schema[PersonV2] = Schema.structural[PersonV2]

          val migration = Migration
            .builder[PersonV0, PersonV2]
            .addField("age", 0)
            .addField("country", "USA")
            .buildPartial

          assertTrue(migration.dynamic.actions.length == 2)
        }
      ),
      suite("Enum Case Selectors")(
        test("DynamicOptic.caseOf for enum case selection") {
          // The _.status.when[Active] pattern compiles to DynamicOptic.caseOf
          // This test verifies the optic structure is correct

          val optic = DynamicOptic.root.field("status").caseOf("Active")

          assertTrue(optic.nodes.length == 2) &&
          assertTrue(optic.nodes.head == DynamicOptic.Node.Field("status")) &&
          assertTrue(optic.nodes.last == DynamicOptic.Node.Case("Active"))
        },
        test("renameCase for enum migrations") {
          sealed trait StatusV0
          object StatusV0 {
            case object Active   extends StatusV0
            case object Inactive extends StatusV0
          }

          sealed trait StatusV1
          object StatusV1 {
            case object Active extends StatusV1
            case object Legacy extends StatusV1
          }

          implicit val v0Schema: Schema[StatusV0] = Schema.derived
          implicit val v1Schema: Schema[StatusV1] = Schema.derived

          val migration = Migration
            .builder[StatusV0, StatusV1]
            .renameCase("Inactive", "Legacy")
            .buildPartial

          assertTrue(migration.dynamic.actions.length == 1) &&
          assertTrue(migration.dynamic.actions.head.isInstanceOf[MigrationAction.RenameCase])
        },
        test("removeCase for enum migrations") {
          sealed trait StatusV0
          object StatusV0 {
            case object Active   extends StatusV0
            case object Inactive extends StatusV0
          }

          sealed trait StatusV1
          object StatusV1 {
            case object Active extends StatusV1
          }

          implicit val v0Schema: Schema[StatusV0] = Schema.derived
          implicit val v1Schema: Schema[StatusV1] = Schema.derived

          val migration = Migration
            .builder[StatusV0, StatusV1]
            .removeCase("Inactive")
            .buildPartial

          assertTrue(migration.dynamic.actions.length == 1) &&
          assertTrue(migration.dynamic.actions.head.isInstanceOf[MigrationAction.RemoveCase])
        }
      )
    )
}
