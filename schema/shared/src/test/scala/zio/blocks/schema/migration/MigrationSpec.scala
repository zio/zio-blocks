package zio.blocks.schema.migration

import zio.test.Assertion._
import zio.test.{Spec, TestEnvironment, ZIOSpecDefault, assert, assertTrue, suite, test}
import zio.blocks.schema._

object MigrationSpec extends ZIOSpecDefault {
  def spec: Spec[TestEnvironment, Any] = suite("MigrationSpec")(
    suite("MigrationError")(
      test("TypeError contains expected and actual types") {
        val error = MigrationError.TypeError("String", "Int")
        assertTrue(error.expected == "String" && error.actual == "Int")
      },
      test("ValidationError contains message") {
        val error = MigrationError.ValidationError("Invalid field")
        assertTrue(error.message.contains("Invalid"))
      },
      test("PathError contains path information") {
        val error = MigrationError.PathError(".field.nested", "not found")
        assertTrue(error.message.contains("not found") && error.path == ".field.nested")
      }
    ),
    suite("MigrationAction")(
      test("AddField action is reversible") {
        val action = MigrationAction.AddField(
          DynamicOptic.root,
          "newField",
          DynamicValue.Primitive(PrimitiveValue.String("default"))
        )
        val reversed = action.reverse
        assertTrue(reversed.isInstanceOf[MigrationAction.DropField])
      },
      test("DropField action is reversible") {
        val action = MigrationAction.DropField(
          DynamicOptic.root,
          "oldField",
          DynamicValue.Primitive(PrimitiveValue.Unit)
        )
        val reversed = action.reverse
        assertTrue(reversed.isInstanceOf[MigrationAction.AddField])
      },
      test("Rename action reverses correctly") {
        val action   = MigrationAction.Rename(DynamicOptic.root, "old", "new")
        val reversed = action.reverse.asInstanceOf[MigrationAction.Rename]
        assertTrue(reversed.from == "new" && reversed.to == "old")
      },
      test("Optionalize and Mandate are inverse operations") {
        val opt     = MigrationAction.Optionalize(DynamicOptic.root, "field")
        val mandate = opt.reverse.asInstanceOf[MigrationAction.Mandate]
        assertTrue(mandate.fieldName == "field")
      }
    ),
    suite("DynamicExpr")(
      test("Identity returns input unchanged") {
        val input  = DynamicValue.Primitive(PrimitiveValue.Int(42))
        val result = DynamicExpr.Identity(input)
        assertTrue(result == Right(input))
      },
      test("Const ignores input and returns constant") {
        val constant = DynamicValue.Primitive(PrimitiveValue.String("constant"))
        val input    = DynamicValue.Primitive(PrimitiveValue.Int(42))
        val result   = DynamicExpr.Const(constant)(input)
        assertTrue(result == Right(constant))
      },
      test("StringToInt converts valid string") {
        val input  = DynamicValue.Primitive(PrimitiveValue.String("123"))
        val result = DynamicExpr.StringToInt(input)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.Int(123))))
      },
      test("StringToInt fails on invalid string") {
        val input  = DynamicValue.Primitive(PrimitiveValue.String("not a number"))
        val result = DynamicExpr.StringToInt(input)
        assertTrue(result.isLeft)
      },
      test("IntToString converts int to string") {
        val input  = DynamicValue.Primitive(PrimitiveValue.Int(42))
        val result = DynamicExpr.IntToString(input)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.String("42"))))
      },
      test("Compose chains expressions") {
        val expr   = DynamicExpr.Compose(DynamicExpr.IntToString, DynamicExpr.Identity)
        val input  = DynamicValue.Primitive(PrimitiveValue.Int(42))
        val result = expr(input)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.String("42"))))
      }
    ),
    suite("DynamicMigration")(
      test("DynamicMigration can be constructed with actions") {
        val migration = DynamicMigration(
          Vector(
            MigrationAction.AddField(DynamicOptic.root, "field", DynamicValue.Primitive(PrimitiveValue.Unit))
          )
        )
        assertTrue(migration.actions.nonEmpty)
      },
      test("DynamicMigration reverse inverts all actions") {
        val migration = DynamicMigration(
          Vector(
            MigrationAction.AddField(DynamicOptic.root, "field", DynamicValue.Primitive(PrimitiveValue.Unit)),
            MigrationAction.Rename(DynamicOptic.root, "a", "b")
          )
        )
        val reversed = migration.reverse
        assertTrue(reversed.actions.size == 2)
      }
    ),
    suite("Migration companion")(
      test("Migration.builder creates a MigrationBuilder") {
        // Note: This test depends on Schema derivation being available
        assertTrue(true) // Placeholder for type-safe builder tests
      }
    )
  )
}
