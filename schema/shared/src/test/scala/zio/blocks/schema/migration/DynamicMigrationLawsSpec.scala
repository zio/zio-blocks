package zio.blocks.schema.migration

import zio.blocks.schema.{DynamicOptic, SchemaExpr}
import zio.blocks.schema.migration.MigrationAction._
import zio.test._

object DynamicMigrationLawsSpec extends ZIOSpecDefault {

  override def spec =
    suite("DynamicMigration laws")(
      test("identity") {
        val p = DynamicMigration.id
        assertTrue(p.actions.isEmpty) &&
        assertTrue((p ++ p).actions.isEmpty)
      },

      test("associativity of ++") {
        val a = DynamicMigration(Rename(DynamicOptic.root.field("x"), "y"))
        val b = DynamicMigration(
          DropField(
            DynamicOptic.root.field("z"),
            SchemaExpr.DefaultValueMarker.asInstanceOf[SchemaExpr[Any, Any]]
          )
        )
        val c = DynamicMigration(
          AddField(
            DynamicOptic.root.field("k"),
            SchemaExpr.DefaultValueMarker.asInstanceOf[SchemaExpr[Any, Any]]
          )
        )

        val left  = (a ++ b) ++ c
        val right = a ++ (b ++ c)

        assertTrue(left.actions == right.actions)
      },

      test("reverse is an involution for structurally invertible actions") {
        val p = DynamicMigration(
          Rename(DynamicOptic.root.field("a"), "b"),
          DropField(
            DynamicOptic.root.field("x"),
            SchemaExpr.DefaultValueMarker.asInstanceOf[SchemaExpr[Any, Any]]
          )
        )
        assertTrue(p.reverse.reverse.actions == p.actions)
      }
    )
}
