package zio.blocks.schema.migration

import zio.test._
import zio.blocks.schema.{DynamicOptic, DynamicValue, PrimitiveValue}

object MigrationActionSpec extends ZIOSpecDefault {
  def spec = suite("MigrationActionSpec")(
    suite("Record Actions")(
      test("AddField should add a field to a record") {
        val action = MigrationAction.AddField(
          DynamicOptic.root.field("age"),
          SchemaExpr.Constant(DynamicValue.Primitive(PrimitiveValue.Int(42)))
        )
        val record = DynamicValue.Record(Vector("name" -> DynamicValue.Primitive(PrimitiveValue.String("John"))))
        val expected = DynamicValue.Record(Vector("name" -> DynamicValue.Primitive(PrimitiveValue.String("John")), "age" -> DynamicValue.Primitive(PrimitiveValue.Int(42))))

        val result = action(record)
        assertTrue(result == Right(expected))
      },
      test("DropField should remove a field from a record") {
        val action = MigrationAction.DropField(
          DynamicOptic.root.field("age"),
          SchemaExpr.DefaultValue()
        )
        val record = DynamicValue.Record(Vector("name" -> DynamicValue.Primitive(PrimitiveValue.String("John")), "age" -> DynamicValue.Primitive(PrimitiveValue.Int(42))))
        val expected = DynamicValue.Record(Vector("name" -> DynamicValue.Primitive(PrimitiveValue.String("John"))))

        val result = action(record)
        assertTrue(result == Right(expected))
      }
    )
  )
}