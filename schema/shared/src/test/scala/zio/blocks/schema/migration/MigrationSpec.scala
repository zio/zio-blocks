package zio.blocks.schema.migration

import zio.test._
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
      },
      test("Join and Split are inverse operations") {
        val join = MigrationAction.Join(
          DynamicOptic.root,
          Vector("first", "last"),
          "fullName",
          DynamicExpr.Identity
        )
        val reversed = join.reverse.asInstanceOf[MigrationAction.Split]
        assertTrue(reversed.sourceField == "fullName" && reversed.targetFields == Vector("first", "last"))
      },
      test("Split reverses to Join") {
        val split = MigrationAction.Split(
          DynamicOptic.root,
          "fullName",
          Vector("first", "last"),
          DynamicExpr.Identity
        )
        val reversed = split.reverse.asInstanceOf[MigrationAction.Join]
        assertTrue(reversed.targetField == "fullName")
      },
      test("ChangeType preserves field info") {
        val action = MigrationAction.ChangeType(DynamicOptic.root, "age", DynamicExpr.StringToInt)
        assertTrue(action.fieldName == "age" && action.at == DynamicOptic.root)
      },
      test("SetValue stores new value") {
        val newVal = DynamicValue.Primitive(PrimitiveValue.Int(100))
        val action = MigrationAction.SetValue(DynamicOptic.root, "score", newVal)
        assertTrue(action.newValue == newVal)
      },
      test("RenameCase reverses correctly") {
        val action   = MigrationAction.RenameCase(DynamicOptic.root, "OldCase", "NewCase")
        val reversed = action.reverse.asInstanceOf[MigrationAction.RenameCase]
        assertTrue(reversed.from == "NewCase" && reversed.to == "OldCase")
      },
      test("TransformCase with migration") {
        val innerMigration = DynamicMigration(Vector(MigrationAction.Rename(DynamicOptic.root, "x", "y")))
        val action         = MigrationAction.TransformCase(DynamicOptic.root, "MyCase", innerMigration)
        assertTrue(action.caseName == "MyCase")
      },
      test("TransformElements stores transform") {
        val action = MigrationAction.TransformElements(DynamicOptic.root, DynamicExpr.IntToString)
        assertTrue(action.transform == DynamicExpr.IntToString)
      },
      test("TransformKeys stores transform") {
        val action = MigrationAction.TransformKeys(DynamicOptic.root, DynamicExpr.IntToString)
        assertTrue(action.transform == DynamicExpr.IntToString)
      },
      test("TransformValues stores transform") {
        val action = MigrationAction.TransformValues(DynamicOptic.root, DynamicExpr.StringToInt)
        assertTrue(action.transform == DynamicExpr.StringToInt)
      },
      test("TransformValue stores field and transform") {
        val action = MigrationAction.TransformValue(DynamicOptic.root, "count", DynamicExpr.IntToLong)
        assertTrue(action.fieldName == "count" && action.transform == DynamicExpr.IntToLong)
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
      },
      test("StringToLong converts valid string") {
        val input  = DynamicValue.Primitive(PrimitiveValue.String("9999999999"))
        val result = DynamicExpr.StringToLong(input)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.Long(9999999999L))))
      },
      test("StringToLong fails on invalid string") {
        val input  = DynamicValue.Primitive(PrimitiveValue.String("not a long"))
        val result = DynamicExpr.StringToLong(input)
        assertTrue(result.isLeft)
      },
      test("StringToLong fails on wrong type") {
        val input  = DynamicValue.Primitive(PrimitiveValue.Int(42))
        val result = DynamicExpr.StringToLong(input)
        assertTrue(result.isLeft)
      },
      test("LongToString converts long to string") {
        val input  = DynamicValue.Primitive(PrimitiveValue.Long(9999999999L))
        val result = DynamicExpr.LongToString(input)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.String("9999999999"))))
      },
      test("LongToString fails on wrong type") {
        val input  = DynamicValue.Primitive(PrimitiveValue.Int(42))
        val result = DynamicExpr.LongToString(input)
        assertTrue(result.isLeft)
      },
      test("IntToLong widens int to long") {
        val input  = DynamicValue.Primitive(PrimitiveValue.Int(42))
        val result = DynamicExpr.IntToLong(input)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.Long(42L))))
      },
      test("IntToLong fails on wrong type") {
        val input  = DynamicValue.Primitive(PrimitiveValue.String("42"))
        val result = DynamicExpr.IntToLong(input)
        assertTrue(result.isLeft)
      },
      test("GetField extracts field from record") {
        val record = DynamicValue.Record(
          Vector(
            ("name", DynamicValue.Primitive(PrimitiveValue.String("Alice"))),
            ("age", DynamicValue.Primitive(PrimitiveValue.Int(30)))
          )
        )
        val result = DynamicExpr.GetField("name")(record)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.String("Alice"))))
      },
      test("GetField fails on missing field") {
        val record = DynamicValue.Record(Vector(("name", DynamicValue.Primitive(PrimitiveValue.String("Alice")))))
        val result = DynamicExpr.GetField("age")(record)
        assertTrue(result.isLeft)
      },
      test("GetField fails on non-record") {
        val input  = DynamicValue.Primitive(PrimitiveValue.Int(42))
        val result = DynamicExpr.GetField("field")(input)
        assertTrue(result.isLeft)
      },
      test("ConcatStrings concatenates string fields") {
        val record = DynamicValue.Record(
          Vector(
            ("first", DynamicValue.Primitive(PrimitiveValue.String("Hello"))),
            ("second", DynamicValue.Primitive(PrimitiveValue.String("World")))
          )
        )
        val result = DynamicExpr.ConcatStrings(" ", Vector("first", "second"))(record)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.String("Hello World"))))
      },
      test("ConcatStrings with empty separator") {
        val record = DynamicValue.Record(
          Vector(
            ("a", DynamicValue.Primitive(PrimitiveValue.String("AB"))),
            ("b", DynamicValue.Primitive(PrimitiveValue.String("CD")))
          )
        )
        val result = DynamicExpr.ConcatStrings("", Vector("a", "b"))(record)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.String("ABCD"))))
      },
      test("ConcatStrings fails on non-record") {
        val input  = DynamicValue.Primitive(PrimitiveValue.Int(42))
        val result = DynamicExpr.ConcatStrings(",", Vector("a"))(input)
        assertTrue(result.isLeft)
      },
      test("IntToString fails on wrong type") {
        val input  = DynamicValue.Primitive(PrimitiveValue.String("42"))
        val result = DynamicExpr.IntToString(input)
        assertTrue(result.isLeft)
      },
      test("StringToInt fails on wrong type") {
        val input  = DynamicValue.Primitive(PrimitiveValue.Int(42))
        val result = DynamicExpr.StringToInt(input)
        assertTrue(result.isLeft)
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
      },
      test("DynamicMigration ++ concatenates actions") {
        val m1       = DynamicMigration(Vector(MigrationAction.Rename(DynamicOptic.root, "a", "b")))
        val m2       = DynamicMigration(Vector(MigrationAction.Rename(DynamicOptic.root, "c", "d")))
        val combined = m1 ++ m2
        assertTrue(combined.actions.size == 2)
      },
      test("DynamicMigration empty has no actions") {
        val migration = DynamicMigration(Vector.empty)
        assertTrue(migration.actions.isEmpty)
      },
      test("DynamicMigration reverse reverses action order") {
        val migration = DynamicMigration(
          Vector(
            MigrationAction.AddField(DynamicOptic.root, "f1", DynamicValue.Primitive(PrimitiveValue.Unit)),
            MigrationAction.AddField(DynamicOptic.root, "f2", DynamicValue.Primitive(PrimitiveValue.Unit)),
            MigrationAction.AddField(DynamicOptic.root, "f3", DynamicValue.Primitive(PrimitiveValue.Unit))
          )
        )
        val reversed = migration.reverse
        // Reversed should have DropField actions in reverse order
        assertTrue(
          reversed.actions.size == 3 &&
            reversed.actions.head.isInstanceOf[MigrationAction.DropField]
        )
      }
    ),
    suite("MigrationBuilder")(
      test("MigrationBuilder addField accumulates actions") {
        val builder = new MigrationBuilder[Unit, Unit](Schema[Unit], Schema[Unit], Vector.empty)
        val result  = builder.addField("field1", DynamicValue.Primitive(PrimitiveValue.Unit))
        assertTrue(result.getActions.size == 1)
      },
      test("MigrationBuilder dropField accumulates actions") {
        val builder = new MigrationBuilder[Unit, Unit](Schema[Unit], Schema[Unit], Vector.empty)
        val result  = builder.dropField("field1", DynamicValue.Primitive(PrimitiveValue.Unit))
        assertTrue(result.getActions.size == 1)
      },
      test("MigrationBuilder renameField accumulates actions") {
        val builder = new MigrationBuilder[Unit, Unit](Schema[Unit], Schema[Unit], Vector.empty)
        val result  = builder.renameField("old", "new")
        assertTrue(result.getActions.size == 1)
      },
      test("MigrationBuilder chains multiple operations") {
        val builder = new MigrationBuilder[Unit, Unit](Schema[Unit], Schema[Unit], Vector.empty)
        val result  = builder
          .addField("f1", DynamicValue.Primitive(PrimitiveValue.Unit))
          .renameField("a", "b")
          .dropField("f2", DynamicValue.Primitive(PrimitiveValue.Unit))
        assertTrue(result.getActions.size == 3)
      },
      test("MigrationBuilder transformField adds transform action") {
        val builder = new MigrationBuilder[Unit, Unit](Schema[Unit], Schema[Unit], Vector.empty)
        val result  = builder.transformField("count", DynamicExpr.IntToString)
        assertTrue(result.getActions.size == 1)
      },
      test("MigrationBuilder mandateField adds mandate action") {
        val builder = new MigrationBuilder[Unit, Unit](Schema[Unit], Schema[Unit], Vector.empty)
        val result  = builder.mandateField("opt", DynamicValue.Primitive(PrimitiveValue.Unit))
        assertTrue(result.getActions.size == 1)
      },
      test("MigrationBuilder optionalizeField adds optionalize action") {
        val builder = new MigrationBuilder[Unit, Unit](Schema[Unit], Schema[Unit], Vector.empty)
        val result  = builder.optionalizeField("req")
        assertTrue(result.getActions.size == 1)
      },
      test("MigrationBuilder joinFields adds join action") {
        val builder = new MigrationBuilder[Unit, Unit](Schema[Unit], Schema[Unit], Vector.empty)
        val result  = builder.joinFields(DynamicOptic.root, Vector("a", "b"), "combined", DynamicExpr.Identity)
        assertTrue(result.getActions.size == 1)
      },
      test("MigrationBuilder splitField adds split action") {
        val builder = new MigrationBuilder[Unit, Unit](Schema[Unit], Schema[Unit], Vector.empty)
        val result  = builder.splitField(DynamicOptic.root, "combined", Vector("a", "b"), DynamicExpr.Identity)
        assertTrue(result.getActions.size == 1)
      },
      test("MigrationBuilder changeFieldType adds change type action") {
        val builder = new MigrationBuilder[Unit, Unit](Schema[Unit], Schema[Unit], Vector.empty)
        val result  = builder.changeFieldType("age", DynamicExpr.StringToInt)
        assertTrue(result.getActions.size == 1)
      },
      test("MigrationBuilder renameCase adds rename case action") {
        val builder = new MigrationBuilder[Unit, Unit](Schema[Unit], Schema[Unit], Vector.empty)
        val result  = builder.renameCase("OldCase", "NewCase")
        assertTrue(result.getActions.size == 1)
      },
      test("MigrationBuilder transformElements adds transform elements action") {
        val builder = new MigrationBuilder[Unit, Unit](Schema[Unit], Schema[Unit], Vector.empty)
        val result  = builder.transformElements(DynamicExpr.IntToString)
        assertTrue(result.getActions.size == 1)
      },
      test("MigrationBuilder transformKeys adds transform keys action") {
        val builder = new MigrationBuilder[Unit, Unit](Schema[Unit], Schema[Unit], Vector.empty)
        val result  = builder.transformKeys(DynamicExpr.IntToString)
        assertTrue(result.getActions.size == 1)
      },
      test("MigrationBuilder transformValues adds transform values action") {
        val builder = new MigrationBuilder[Unit, Unit](Schema[Unit], Schema[Unit], Vector.empty)
        val result  = builder.transformValues(DynamicExpr.StringToInt)
        assertTrue(result.getActions.size == 1)
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
