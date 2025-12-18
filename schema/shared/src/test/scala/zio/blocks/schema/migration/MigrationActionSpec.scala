package zio.blocks.schema.migration

import zio.test._
import zio.blocks.schema.{DynamicOptic, DynamicValue, PrimitiveValue, Schema}
import zio.blocks.schema.DeriveSchema

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
    ),
    suite("MigrationBuilder DSL")(
      test("addField with explicit DynamicOptic") {
        case class PersonV1(name: String)
        case class PersonV2(name: String, age: Int)

        implicit val schemaV1: Schema[PersonV1] = DeriveSchema.gen[PersonV1]
        implicit val schemaV2: Schema[PersonV2] = DeriveSchema.gen[PersonV2]

        val migration = Migration.newBuilder[PersonV1, PersonV2]
          .addField(DynamicOptic.root.field("age"), SchemaExpr.Constant(PrimitiveValue.Int(42)))
          .build

        val personV1 = PersonV1("John")
        val expectedPersonV2 = PersonV2("John", 42)

        val result = migration(personV1)
        assertTrue(result == Right(expectedPersonV2))
      }
    )
  )
}