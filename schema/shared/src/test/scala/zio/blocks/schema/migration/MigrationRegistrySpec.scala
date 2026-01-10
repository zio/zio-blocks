package zio.blocks.schema.migration

import zio._
import zio.blocks.schema.{DynamicValue, PrimitiveValue}
import zio.test._

object MigrationRegistrySpec extends ZIOSpecDefault {

  override def spec =
    suite("MigrationRegistry")(
      test("plan multi-step migration") {
        val v1 = SchemaId("User", 1)
        val v2 = SchemaId("User", 2)
        val v3 = SchemaId("User", 3)

        val m12 =
          StoredMigration(
            v1,
            v2,
            DynamicMigration.RenameField(Path.root, "name", "fullName")
          )

        val m23 =
          StoredMigration(
            v2,
            v3,
            DynamicMigration.AddField(
              Path.root,
              "age",
              DynamicValue.Primitive(PrimitiveValue.Int(0))
            )
          )

        val reg = MigrationRegistry(m12, m23)

        val plan = reg.plan(v1, v3)

        assertTrue(plan.isDefined)
      }
    )
}
