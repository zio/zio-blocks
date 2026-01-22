package zio.blocks.schema.migration

import zio.blocks.schema.{DynamicValue, DynamicOptic, PrimitiveConverter, PrimitiveValue, Schema, SchemaExpr}
import zio.test._

object MigrationActionSpec extends ZIOSpecDefault {

  def spec = suite("MigrationActionSpec")(
    suite("AddField")(
      test("should add a field to a record with default value") {
        val record = DynamicValue.Record(
          Vector(
            "name" -> DynamicValue.Primitive(PrimitiveValue.String("John"))
          )
        )
        val action = MigrationAction.AddField(
          at = DynamicOptic.root.field("age"),
          default = SchemaExpr.Literal(0, Schema.int)
        )

        val result = action.execute(record)

        assertTrue(result.isRight) &&
        assertTrue(
          result.toOption.get == DynamicValue.Record(
            Vector(
              "name" -> DynamicValue.Primitive(PrimitiveValue.String("John")),
              "age"  -> DynamicValue.Primitive(PrimitiveValue.Int(0))
            )
          )
        )
      },
      test("should fail if field already exists") {
        val record = DynamicValue.Record(
          Vector(
            "name" -> DynamicValue.Primitive(PrimitiveValue.String("John")),
            "age"  -> DynamicValue.Primitive(PrimitiveValue.Int(25))
          )
        )
        val action = MigrationAction.AddField(
          at = DynamicOptic.root.field("age"),
          default = SchemaExpr.Literal(0, Schema.int)
        )

        val result = action.execute(record)

        assertTrue(result.isLeft)
      }
    ),
    suite("DropField")(
      test("should remove a field from a record") {
        val record = DynamicValue.Record(
          Vector(
            "name" -> DynamicValue.Primitive(PrimitiveValue.String("John")),
            "age"  -> DynamicValue.Primitive(PrimitiveValue.Int(25))
          )
        )
        val action = MigrationAction.DropField(
          at = DynamicOptic.root.field("age"),
          defaultForReverse = SchemaExpr.Literal(0, Schema.int)
        )

        val result = action.execute(record)

        assertTrue(result.isRight) &&
        assertTrue(
          result.toOption.get == DynamicValue.Record(
            Vector(
              "name" -> DynamicValue.Primitive(PrimitiveValue.String("John"))
            )
          )
        )
      },
      test("should fail if field does not exist") {
        val record = DynamicValue.Record(
          Vector(
            "name" -> DynamicValue.Primitive(PrimitiveValue.String("John"))
          )
        )
        val action = MigrationAction.DropField(
          at = DynamicOptic.root.field("age"),
          defaultForReverse = SchemaExpr.Literal(0, Schema.int)
        )

        val result = action.execute(record)

        assertTrue(result.isLeft)
      }
    ),
    suite("Rename")(
      test("should rename a field in a record") {
        val record = DynamicValue.Record(
          Vector(
            "firstName" -> DynamicValue.Primitive(PrimitiveValue.String("John")),
            "age"       -> DynamicValue.Primitive(PrimitiveValue.Int(25))
          )
        )
        val action = MigrationAction.Rename(
          at = DynamicOptic.root.field("firstName"),
          to = "name"
        )

        val result = action.execute(record)

        assertTrue(result.isRight) &&
        assertTrue(
          result.toOption.get == DynamicValue.Record(
            Vector(
              "name" -> DynamicValue.Primitive(PrimitiveValue.String("John")),
              "age"  -> DynamicValue.Primitive(PrimitiveValue.Int(25))
            )
          )
        )
      },
      test("should fail if source field does not exist") {
        val record = DynamicValue.Record(
          Vector(
            "age" -> DynamicValue.Primitive(PrimitiveValue.Int(25))
          )
        )
        val action = MigrationAction.Rename(
          at = DynamicOptic.root.field("firstName"),
          to = "name"
        )

        val result = action.execute(record)

        assertTrue(result.isLeft)
      },
      test("should fail if target field already exists") {
        val record = DynamicValue.Record(
          Vector(
            "firstName" -> DynamicValue.Primitive(PrimitiveValue.String("John")),
            "name"      -> DynamicValue.Primitive(PrimitiveValue.String("Jane"))
          )
        )
        val action = MigrationAction.Rename(
          at = DynamicOptic.root.field("firstName"),
          to = "name"
        )

        val result = action.execute(record)

        assertTrue(result.isLeft)
      }
    ),
    suite("Reverse")(
      test("AddField.reverse should return DropField") {
        val addField = MigrationAction.AddField(
          at = DynamicOptic.root.field("age"),
          default = SchemaExpr.Literal(0, Schema.int)
        )

        val reversed = addField.reverse

        assertTrue(reversed.isInstanceOf[MigrationAction.DropField]) &&
        assertTrue(reversed.asInstanceOf[MigrationAction.DropField].at == addField.at)
      },
      test("DropField.reverse should return AddField") {
        val dropField = MigrationAction.DropField(
          at = DynamicOptic.root.field("age"),
          defaultForReverse = SchemaExpr.Literal(0, Schema.int)
        )

        val reversed = dropField.reverse

        assertTrue(reversed.isInstanceOf[MigrationAction.AddField]) &&
        assertTrue(reversed.asInstanceOf[MigrationAction.AddField].at == dropField.at)
      },
      test("Rename.reverse should flip to/from") {
        val rename = MigrationAction.Rename(
          at = DynamicOptic.root.field("firstName"),
          to = "name"
        )

        val reversed = rename.reverse

        assertTrue(reversed.isInstanceOf[MigrationAction.Rename]) &&
        assertTrue(reversed.asInstanceOf[MigrationAction.Rename].to == "firstName")
      }
    ),
    suite("TransformValue")(
      test("should transform a field value using literal replacement") {
        val record = DynamicValue.Record(
          Vector(
            "age" -> DynamicValue.Primitive(PrimitiveValue.Int(25))
          )
        )
        val action = MigrationAction.TransformValue(
          at = DynamicOptic.root.field("age"),
          transform = SchemaExpr.Literal[DynamicValue, Int](30, Schema.int)
        )

        val result = action.execute(record)

        assertTrue(result.isRight) &&
        assertTrue(
          result.toOption.get == DynamicValue.Record(
            Vector(
              "age" -> DynamicValue.Primitive(PrimitiveValue.Int(30))
            )
          )
        )
      },
      test("should transform a field value using type conversion") {
        val record = DynamicValue.Record(
          Vector(
            "count" -> DynamicValue.Primitive(PrimitiveValue.Int(42))
          )
        )
        val action = MigrationAction.TransformValue(
          at = DynamicOptic.root.field("count"),
          transform = SchemaExpr.Convert[DynamicValue, Long](
            SchemaExpr.Literal[DynamicValue, Int](42, Schema.int),
            PrimitiveConverter.IntToLong
          )
        )

        val result = action.execute(record)

        assertTrue(result.isRight) &&
        assertTrue(
          result.toOption.get == DynamicValue.Record(
            Vector(
              "count" -> DynamicValue.Primitive(PrimitiveValue.Long(42L))
            )
          )
        )
      },
      test("should fail if field does not exist") {
        val record = DynamicValue.Record(
          Vector(
            "name" -> DynamicValue.Primitive(PrimitiveValue.String("John"))
          )
        )
        val action = MigrationAction.TransformValue(
          at = DynamicOptic.root.field("age"),
          transform = SchemaExpr.Literal[DynamicValue, Int](0, Schema.int)
        )

        val result = action.execute(record)

        assertTrue(result.isLeft)
      }
    ),
    suite("ChangeType")(
      test("should convert string field to int") {
        val record = DynamicValue.Record(
          Vector(
            "age" -> DynamicValue.Primitive(PrimitiveValue.String("25"))
          )
        )
        val action = MigrationAction.ChangeType(
          at = DynamicOptic.root.field("age"),
          converter = PrimitiveConverter.StringToInt
        )

        val result = action.execute(record)

        assertTrue(result.isRight) &&
        assertTrue(
          result.toOption.get == DynamicValue.Record(
            Vector(
              "age" -> DynamicValue.Primitive(PrimitiveValue.Int(25))
            )
          )
        )
      },
      test("should convert int field to long") {
        val record = DynamicValue.Record(
          Vector(
            "count" -> DynamicValue.Primitive(PrimitiveValue.Int(42))
          )
        )
        val action = MigrationAction.ChangeType(
          at = DynamicOptic.root.field("count"),
          converter = PrimitiveConverter.IntToLong
        )

        val result = action.execute(record)

        assertTrue(result.isRight) &&
        assertTrue(
          result.toOption.get == DynamicValue.Record(
            Vector(
              "count" -> DynamicValue.Primitive(PrimitiveValue.Long(42L))
            )
          )
        )
      },
      test("should fail if conversion fails") {
        val record = DynamicValue.Record(
          Vector(
            "age" -> DynamicValue.Primitive(PrimitiveValue.String("not-a-number"))
          )
        )
        val action = MigrationAction.ChangeType(
          at = DynamicOptic.root.field("age"),
          converter = PrimitiveConverter.StringToInt
        )

        val result = action.execute(record)

        assertTrue(result.isLeft)
      },
      test("should fail if field does not exist") {
        val record = DynamicValue.Record(
          Vector(
            "name" -> DynamicValue.Primitive(PrimitiveValue.String("John"))
          )
        )
        val action = MigrationAction.ChangeType(
          at = DynamicOptic.root.field("age"),
          converter = PrimitiveConverter.StringToInt
        )

        val result = action.execute(record)

        assertTrue(result.isLeft)
      }
    )
  )
}
