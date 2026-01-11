package zio.blocks.schema.migration

import zio._
import zio.blocks.schema.{DynamicValue, PrimitiveValue}
import zio.test._

object DynamicMigrationInterpreterSpec extends ZIOSpecDefault {

  override def spec =
    suite("DynamicMigrationInterpreter")(
      test("add + delete field") {
        val dv =
          DynamicValue.Record(
            Vector("a" -> DynamicValue.Primitive(PrimitiveValue.Int(1)))
          )

        val mig =
          DynamicMigration.sequence(
            DynamicMigration.AddField(Path.root, "b", DynamicValue.Primitive(PrimitiveValue.Int(2))),
            DynamicMigration.DeleteField(Path.root, "a")
          )

        val out = DynamicMigrationInterpreter(mig, dv)

        assertTrue(
          out == Right(
            DynamicValue.Record(
              Vector("b" -> DynamicValue.Primitive(PrimitiveValue.Int(2)))
            )
          )
        )
      },

      test("wrap and unwrap array") {
        val dv = DynamicValue.Primitive(PrimitiveValue.Int(42))

        val mig =
          DynamicMigration.sequence(
            DynamicMigration.WrapInArray(Path.root),
            DynamicMigration.UnwrapArray(Path.root)
          )

        assertTrue(DynamicMigrationInterpreter(mig, dv) == Right(dv))
      },

      test("primitive conversion") {
        val dv = DynamicValue.Primitive(PrimitiveValue.Int(5))

        val mig =
          DynamicMigration.ConvertPrimitive(
            Path.root,
            DynamicMigration.Primitive.Int,
            DynamicMigration.Primitive.String
          )

        val out = DynamicMigrationInterpreter(mig, dv)

        assertTrue(out == Right(DynamicValue.Primitive(PrimitiveValue.String("5"))))
      }
    )
}
