package zio.blocks.schema.migration

import zio.blocks.schema._
import zio.blocks.chunk.Chunk
import zio.test._

object MigrationActionSpec extends SchemaBaseSpec {
  def spec: Spec[TestEnvironment, Any] = suite("MigrationActionSpec")(
    suite("Identity")(
      test("has root path") {
        assertTrue(MigrationAction.Identity.at == DynamicOptic.root)
      },
      test("reverse is identity") {
        assertTrue(MigrationAction.Identity.reverse == MigrationAction.Identity)
      }
    ),
    suite("AddField")(
      test("reverse is DropField with same default") {
        val action = MigrationAction.AddField(
          DynamicOptic.root.field("person"),
          "age",
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(0)))
        )
        val reversed = action.reverse
        assertTrue(reversed.isInstanceOf[MigrationAction.DropField])
        val drop = reversed.asInstanceOf[MigrationAction.DropField]
        assertTrue(
          drop.at == action.at,
          drop.name == action.name
        )
      }
    ),
    suite("DropField")(
      test("reverse is AddField") {
        val action = MigrationAction.DropField(
          DynamicOptic.root,
          "removed",
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("default")))
        )
        val reversed = action.reverse
        assertTrue(reversed.isInstanceOf[MigrationAction.AddField])
        val add = reversed.asInstanceOf[MigrationAction.AddField]
        assertTrue(
          add.at == action.at,
          add.name == action.name
        )
      }
    ),
    suite("RenameField")(
      test("reverse swaps from and to") {
        val action   = MigrationAction.RenameField(DynamicOptic.root, "oldName", "newName")
        val reversed = action.reverse.asInstanceOf[MigrationAction.RenameField]
        assertTrue(
          reversed.from == "newName",
          reversed.to == "oldName",
          reversed.at == action.at
        )
      }
    ),
    suite("TransformValue")(
      test("reverse swaps transform and reverseTransform") {
        val transform        = DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(1)))
        val reverseTransform = DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(2)))
        val action           = MigrationAction.TransformValue(DynamicOptic.root.field("x"), transform, reverseTransform)
        val reversed         = action.reverse.asInstanceOf[MigrationAction.TransformValue]
        assertTrue(
          reversed.transform == reverseTransform,
          reversed.reverseTransform == transform
        )
      }
    ),
    suite("Mandate")(
      test("reverse is Optionalize") {
        val action = MigrationAction.Mandate(
          DynamicOptic.root.field("opt"),
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(0)))
        )
        assertTrue(action.reverse.isInstanceOf[MigrationAction.Optionalize])
      }
    ),
    suite("Optionalize")(
      test("reverse is Mandate with DefaultValue") {
        val action   = MigrationAction.Optionalize(DynamicOptic.root.field("field"))
        val reversed = action.reverse
        assertTrue(reversed.isInstanceOf[MigrationAction.Mandate])
        val mandate = reversed.asInstanceOf[MigrationAction.Mandate]
        assertTrue(mandate.default == DynamicSchemaExpr.DefaultValue)
      }
    ),
    suite("ChangeType")(
      test("reverse swaps converters") {
        val converter        = DynamicSchemaExpr.CoercePrimitive(DynamicSchemaExpr.Path(DynamicOptic.root), "String")
        val reverseConverter = DynamicSchemaExpr.CoercePrimitive(DynamicSchemaExpr.Path(DynamicOptic.root), "Int")
        val action           = MigrationAction.ChangeType(DynamicOptic.root.field("x"), converter, reverseConverter)
        val reversed         = action.reverse.asInstanceOf[MigrationAction.ChangeType]
        assertTrue(
          reversed.converter == reverseConverter,
          reversed.reverseConverter == converter
        )
      }
    ),
    suite("Join")(
      test("reverse is Split") {
        val action = MigrationAction.Join(
          DynamicOptic.root.field("fullName"),
          Vector(DynamicOptic.root.field("firstName"), DynamicOptic.root.field("lastName")),
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String(""))),
          DynamicSchemaExpr.Literal(DynamicValue.Sequence(Chunk.empty))
        )
        assertTrue(action.reverse.isInstanceOf[MigrationAction.Split])
      }
    ),
    suite("Split")(
      test("reverse is Join") {
        val action = MigrationAction.Split(
          DynamicOptic.root.field("fullName"),
          Vector(DynamicOptic.root.field("firstName"), DynamicOptic.root.field("lastName")),
          DynamicSchemaExpr.Literal(DynamicValue.Sequence(Chunk.empty)),
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("")))
        )
        assertTrue(action.reverse.isInstanceOf[MigrationAction.Join])
      }
    ),
    suite("RenameCase")(
      test("reverse swaps from and to") {
        val action   = MigrationAction.RenameCase(DynamicOptic.root, "OldCase", "NewCase")
        val reversed = action.reverse.asInstanceOf[MigrationAction.RenameCase]
        assertTrue(
          reversed.from == "NewCase",
          reversed.to == "OldCase"
        )
      }
    ),
    suite("TransformCase")(
      test("reverse reverses nested actions in reverse order") {
        val nestedActions = Vector(
          MigrationAction.RenameField(DynamicOptic.root, "a", "b"),
          MigrationAction.AddField(
            DynamicOptic.root,
            "c",
            DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(1)))
          )
        )
        val action   = MigrationAction.TransformCase(DynamicOptic.root, "Case1", nestedActions)
        val reversed = action.reverse.asInstanceOf[MigrationAction.TransformCase]
        assertTrue(
          reversed.caseName == "Case1",
          reversed.actions.length == 2,
          reversed.actions.head.isInstanceOf[MigrationAction.DropField]
        )
      }
    ),
    suite("TransformElements")(
      test("reverse swaps transforms") {
        val transform        = DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(1)))
        val reverseTransform = DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(2)))
        val action           = MigrationAction.TransformElements(DynamicOptic.root.field("items"), transform, reverseTransform)
        val reversed         = action.reverse.asInstanceOf[MigrationAction.TransformElements]
        assertTrue(
          reversed.transform == reverseTransform,
          reversed.reverseTransform == transform
        )
      }
    ),
    suite("TransformKeys")(
      test("reverse swaps transforms") {
        val transform        = DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("new")))
        val reverseTransform = DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("old")))
        val action           = MigrationAction.TransformKeys(DynamicOptic.root.field("map"), transform, reverseTransform)
        val reversed         = action.reverse.asInstanceOf[MigrationAction.TransformKeys]
        assertTrue(
          reversed.transform == reverseTransform,
          reversed.reverseTransform == transform
        )
      }
    ),
    suite("TransformValues")(
      test("reverse swaps transforms") {
        val transform        = DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(100)))
        val reverseTransform = DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(1)))
        val action           = MigrationAction.TransformValues(DynamicOptic.root.field("map"), transform, reverseTransform)
        val reversed         = action.reverse.asInstanceOf[MigrationAction.TransformValues]
        assertTrue(
          reversed.transform == reverseTransform,
          reversed.reverseTransform == transform
        )
      }
    )
  )
}
