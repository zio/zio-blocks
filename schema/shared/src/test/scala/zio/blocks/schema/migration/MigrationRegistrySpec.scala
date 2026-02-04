package zio.blocks.schema.migration

import zio.blocks.schema.{DynamicOptic, SchemaExpr}
import zio.blocks.schema.migration.MigrationAction._
import zio.test._

object MigrationRegistrySpec extends ZIOSpecDefault {

  override def spec =
    suite("MigrationRegistry")(
      test("plan multi-step migration returns composed program") {
        val v1 = SchemaId("User", 1)
        val v2 = SchemaId("User", 2)
        val v3 = SchemaId("User", 3)

        val m12 =
          StoredMigration(
            v1,
            v2,
            DynamicMigration(
              Rename(at = DynamicOptic.root.field("name"), to = "fullName")
            )
          )

        // AddField requires a SchemaExpr; for registry planning we don't need to run it,
        // so a marker is sufficient here.
        val m23 =
          StoredMigration(
            v2,
            v3,
            DynamicMigration(
              AddField(
                at = DynamicOptic.root.field("age"),
                default = SchemaExpr.DefaultValueMarker.asInstanceOf[SchemaExpr[Any, Any]]
              )
            )
          )

        val reg = MigrationRegistry(m12, m23)

        val plan = reg.plan(v1, v3)

        assertTrue(plan.isDefined) &&
        assertTrue(plan.get.actions.length == 2)
      }
    )
}
