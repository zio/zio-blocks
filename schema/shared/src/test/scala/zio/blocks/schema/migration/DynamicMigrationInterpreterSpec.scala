package zio.blocks.schema.migration

import zio.blocks.schema.{DynamicOptic, DynamicValue, PrimitiveValue, SchemaExpr}
import zio.blocks.schema.migration.MigrationAction._
import zio.test._

object DynamicMigrationInterpreterSpec extends ZIOSpecDefault {

  override def spec =
    suite("DynamicMigrationInterpreter")(
      test("rename field in a record") {
        val dv =
          DynamicValue.Record(
            "a" -> DynamicValue.Primitive(PrimitiveValue.Int(1))
          )

        val mig = DynamicMigration(
          Rename(at = DynamicOptic.root.field("a"), to = "b")
        )

        val out = DynamicMigrationInterpreter(mig, dv)

        assertTrue(
          out == Right(
            DynamicValue.Record(
              "b" -> DynamicValue.Primitive(PrimitiveValue.Int(1))
            )
          )
        )
      },

      test("drop field in a record") {
        val dv =
          DynamicValue.Record(
            "a" -> DynamicValue.Primitive(PrimitiveValue.Int(1)),
            "b" -> DynamicValue.Primitive(PrimitiveValue.Int(2))
          )

        // defaultForReverse is not used by the interpreter for DropField, so a marker is fine here.
        val mig = DynamicMigration(
          DropField(
            at = DynamicOptic.root.field("a"),
            defaultForReverse = SchemaExpr.DefaultValueMarker.asInstanceOf[SchemaExpr[Any, Any]]
          )
        )

        val out = DynamicMigrationInterpreter(mig, dv)

        assertTrue(
          out == Right(
            DynamicValue.Record(
              "b" -> DynamicValue.Primitive(PrimitiveValue.Int(2))
            )
          )
        )
      },

      test("optionalize wraps the focused value in Some") {
        val dv =
          DynamicValue.Record(
            "a" -> DynamicValue.Primitive(PrimitiveValue.Int(42))
          )

        val mig = DynamicMigration(
          Optionalize(at = DynamicOptic.root.field("a"))
        )

        val out = DynamicMigrationInterpreter(mig, dv)

        assertTrue(
          out == Right(
            DynamicValue.Record(
              "a" -> DynamicValue.Variant(
                "Some",
                DynamicValue.Primitive(PrimitiveValue.Int(42))
              )
            )
          )
        )
      }
    )
}
