package zio.blocks.schema.migration

import zio.blocks.schema._
import zio.test._

/**
 * Tests for enum/variant migration operations.
 *
 * Covers:
 *   - RenameCase: Renaming enum case names
 *   - TransformCase: Transforming case contents
 *   - Multiple case operations
 *   - Nested variant handling
 *   - Edge cases with unknown cases
 */
object VariantMigrationSpec extends SchemaBaseSpec {

  // ─────────────────────────────────────────────────────────────────────────
  // Test Helpers
  // ─────────────────────────────────────────────────────────────────────────

  def dynamicRecord(fields: (String, DynamicValue)*): DynamicValue =
    DynamicValue.Record(fields: _*)

  def dynamicString(s: String): DynamicValue =
    DynamicValue.Primitive(PrimitiveValue.String(s))

  def dynamicInt(i: Int): DynamicValue =
    DynamicValue.Primitive(PrimitiveValue.Int(i))

  def dynamicVariant(caseName: String, value: DynamicValue): DynamicValue =
    DynamicValue.Variant(caseName, value)

  def dynamicUnit: DynamicValue =
    DynamicValue.Primitive(PrimitiveValue.Unit)

  // ─────────────────────────────────────────────────────────────────────────
  // Tests
  // ─────────────────────────────────────────────────────────────────────────

  def spec: Spec[TestEnvironment, Any] = suite("VariantMigrationSpec")(
    suite("RenameCase")(
      test("renames matching case") {
        val action = MigrationAction.RenameCase(DynamicOptic.root, "OldCase", "NewCase")
        val input  = dynamicVariant("OldCase", dynamicInt(42))
        val result = action.apply(input)
        assertTrue(result == Right(dynamicVariant("NewCase", dynamicInt(42))))
      },
      test("preserves case value after rename") {
        val action       = MigrationAction.RenameCase(DynamicOptic.root, "Case", "RenamedCase")
        val complexValue = dynamicRecord("x" -> dynamicInt(1), "y" -> dynamicString("test"))
        val input        = dynamicVariant("Case", complexValue)
        val result       = action.apply(input)
        assertTrue(result == Right(dynamicVariant("RenamedCase", complexValue)))
      },
      test("does not affect non-matching cases") {
        val action = MigrationAction.RenameCase(DynamicOptic.root, "CaseA", "CaseB")
        val input  = dynamicVariant("CaseC", dynamicInt(42))
        val result = action.apply(input)
        assertTrue(result == Right(input))
      },
      test("chain renames: A->B->C") {
        val migration = DynamicMigration(
          Vector(
            MigrationAction.RenameCase(DynamicOptic.root, "A", "B"),
            MigrationAction.RenameCase(DynamicOptic.root, "B", "C")
          )
        )
        val input  = dynamicVariant("A", dynamicUnit)
        val result = migration.apply(input)
        assertTrue(result == Right(dynamicVariant("C", dynamicUnit)))
      },
      test("reverse of RenameCase swaps from/to") {
        val action   = MigrationAction.RenameCase(DynamicOptic.root, "Old", "New")
        val reversed = action.reverse
        reversed match {
          case MigrationAction.RenameCase(_, from, to) =>
            assertTrue(from == "New" && to == "Old")
          case _ => assertTrue(false)
        }
      },
      test("renames case with unit value") {
        val action = MigrationAction.RenameCase(DynamicOptic.root, "EmptyCase", "RenamedEmpty")
        val input  = dynamicVariant("EmptyCase", dynamicUnit)
        val result = action.apply(input)
        assertTrue(result == Right(dynamicVariant("RenamedEmpty", dynamicUnit)))
      },
      test("multiple case renames in sequence") {
        val migration = DynamicMigration(
          Vector(
            MigrationAction.RenameCase(DynamicOptic.root, "Case1", "NewCase1"),
            MigrationAction.RenameCase(DynamicOptic.root, "Case2", "NewCase2"),
            MigrationAction.RenameCase(DynamicOptic.root, "Case3", "NewCase3")
          )
        )
        // Only first matching case is renamed
        val input1 = dynamicVariant("Case1", dynamicUnit)
        val input2 = dynamicVariant("Case2", dynamicUnit)
        val input3 = dynamicVariant("Case3", dynamicUnit)

        assertTrue(
          migration.apply(input1) == Right(dynamicVariant("NewCase1", dynamicUnit)) &&
            migration.apply(input2) == Right(dynamicVariant("NewCase2", dynamicUnit)) &&
            migration.apply(input3) == Right(dynamicVariant("NewCase3", dynamicUnit))
        )
      }
    ),
    suite("TransformCase")(
      test("transforms matching case contents") {
        val action = MigrationAction.TransformCase(
          DynamicOptic.root,
          "MyCase",
          Vector(MigrationAction.AddField(DynamicOptic.root, "newField", Resolved.Literal.int(1)))
        )
        val input  = dynamicVariant("MyCase", dynamicRecord("existing" -> dynamicString("value")))
        val result = action.apply(input)
        assertTrue(
          result == Right(
            dynamicVariant(
              "MyCase",
              dynamicRecord(
                "existing" -> dynamicString("value"),
                "newField" -> dynamicInt(1)
              )
            )
          )
        )
      },
      test("does not transform non-matching case") {
        val action = MigrationAction.TransformCase(
          DynamicOptic.root,
          "TargetCase",
          Vector(MigrationAction.AddField(DynamicOptic.root, "field", Resolved.Literal.int(1)))
        )
        val input  = dynamicVariant("OtherCase", dynamicRecord())
        val result = action.apply(input)
        assertTrue(result == Right(input))
      },
      test("applies multiple actions to case contents") {
        val action = MigrationAction.TransformCase(
          DynamicOptic.root,
          "Case",
          Vector(
            MigrationAction.AddField(DynamicOptic.root, "a", Resolved.Literal.int(1)),
            MigrationAction.AddField(DynamicOptic.root, "b", Resolved.Literal.int(2)),
            MigrationAction.Rename(DynamicOptic.root, "old", "new")
          )
        )
        val input  = dynamicVariant("Case", dynamicRecord("old" -> dynamicString("value")))
        val result = action.apply(input)
        assertTrue(
          result == Right(
            dynamicVariant(
              "Case",
              dynamicRecord(
                "new" -> dynamicString("value"),
                "a"   -> dynamicInt(1),
                "b"   -> dynamicInt(2)
              )
            )
          )
        )
      },
      test("reverse of TransformCase reverses inner actions") {
        val action = MigrationAction.TransformCase(
          DynamicOptic.root,
          "Case",
          Vector(
            MigrationAction.AddField(DynamicOptic.root, "a", Resolved.Literal.int(1)),
            MigrationAction.AddField(DynamicOptic.root, "b", Resolved.Literal.int(2))
          )
        )
        val reversed = action.reverse
        reversed match {
          case MigrationAction.TransformCase(_, caseName, actions) =>
            assertTrue(
              caseName == "Case" &&
                actions.length == 2 &&
                actions(0).isInstanceOf[MigrationAction.DropField] &&
                actions(1).isInstanceOf[MigrationAction.DropField]
            )
          case _ => assertTrue(false)
        }
      },
      test("empty transformation is no-op") {
        val action = MigrationAction.TransformCase(DynamicOptic.root, "Case", Vector.empty)
        val input  = dynamicVariant("Case", dynamicRecord("x" -> dynamicInt(1)))
        val result = action.apply(input)
        assertTrue(result == Right(input))
      }
    ),
    suite("Combined operations")(
      test("rename case then transform it") {
        val migration = DynamicMigration(
          Vector(
            MigrationAction.RenameCase(DynamicOptic.root, "OldName", "NewName"),
            MigrationAction.TransformCase(
              DynamicOptic.root,
              "NewName",
              Vector(MigrationAction.AddField(DynamicOptic.root, "added", Resolved.Literal.int(42)))
            )
          )
        )
        val input  = dynamicVariant("OldName", dynamicRecord())
        val result = migration.apply(input)
        assertTrue(result == Right(dynamicVariant("NewName", dynamicRecord("added" -> dynamicInt(42)))))
      },
      test("transform case then rename it") {
        val migration = DynamicMigration(
          Vector(
            MigrationAction.TransformCase(
              DynamicOptic.root,
              "Original",
              Vector(MigrationAction.AddField(DynamicOptic.root, "x", Resolved.Literal.int(1)))
            ),
            MigrationAction.RenameCase(DynamicOptic.root, "Original", "Renamed")
          )
        )
        val input  = dynamicVariant("Original", dynamicRecord())
        val result = migration.apply(input)
        assertTrue(result == Right(dynamicVariant("Renamed", dynamicRecord("x" -> dynamicInt(1)))))
      }
    ),
    suite("Variant field in record")(
      test("rename case in variant field") {
        val action = MigrationAction.RenameCase(
          DynamicOptic.root.field("status"),
          "Active",
          "Enabled"
        )
        val input = dynamicRecord(
          "name"   -> dynamicString("test"),
          "status" -> dynamicVariant("Active", dynamicUnit)
        )
        val result = action.apply(input)
        assertTrue(
          result == Right(
            dynamicRecord(
              "name"   -> dynamicString("test"),
              "status" -> dynamicVariant("Enabled", dynamicUnit)
            )
          )
        )
      },
      test("transform case in variant field") {
        val action = MigrationAction.TransformCase(
          DynamicOptic.root.field("data"),
          "Success",
          Vector(MigrationAction.AddField(DynamicOptic.root, "timestamp", Resolved.Literal.long(12345L)))
        )
        val input = dynamicRecord(
          "id"   -> dynamicInt(1),
          "data" -> dynamicVariant("Success", dynamicRecord("result" -> dynamicString("ok")))
        )
        val result = action.apply(input)
        assertTrue(
          result == Right(
            dynamicRecord(
              "id"   -> dynamicInt(1),
              "data" -> dynamicVariant(
                "Success",
                dynamicRecord(
                  "result"    -> dynamicString("ok"),
                  "timestamp" -> DynamicValue.Primitive(PrimitiveValue.Long(12345L))
                )
              )
            )
          )
        )
      }
    ),
    suite("Option as Variant")(
      test("rename Some to Present") {
        val action = MigrationAction.RenameCase(DynamicOptic.root, "Some", "Present")
        val input  = dynamicVariant("Some", dynamicInt(42))
        val result = action.apply(input)
        assertTrue(result == Right(dynamicVariant("Present", dynamicInt(42))))
      },
      test("rename None to Absent") {
        val action = MigrationAction.RenameCase(DynamicOptic.root, "None", "Absent")
        val input  = dynamicVariant("None", dynamicUnit)
        val result = action.apply(input)
        assertTrue(result == Right(dynamicVariant("Absent", dynamicUnit)))
      },
      test("transform Some case to add wrapper") {
        val action = MigrationAction.TransformCase(
          DynamicOptic.root,
          "Some",
          Vector(MigrationAction.Optionalize(DynamicOptic.root, "value"))
        )
        val input  = dynamicVariant("Some", dynamicRecord("value" -> dynamicInt(42)))
        val result = action.apply(input)
        // Optionalize wraps in Schema-compatible Some: Variant("Some", Record(Vector(("value", inner))))
        val expectedSome = dynamicVariant("Some", DynamicValue.Record(("value", dynamicInt(42))))
        assertTrue(
          result == Right(
            dynamicVariant(
              "Some",
              dynamicRecord(
                "value" -> expectedSome
              )
            )
          )
        )
      }
    ),
    suite("Edge cases")(
      test("case name with special characters") {
        val action = MigrationAction.RenameCase(DynamicOptic.root, "case-with-dash", "case_with_underscore")
        val input  = dynamicVariant("case-with-dash", dynamicUnit)
        val result = action.apply(input)
        assertTrue(result == Right(dynamicVariant("case_with_underscore", dynamicUnit)))
      },
      test("case name with unicode") {
        val action = MigrationAction.RenameCase(DynamicOptic.root, "ケース", "Case")
        val input  = dynamicVariant("ケース", dynamicInt(1))
        val result = action.apply(input)
        assertTrue(result == Right(dynamicVariant("Case", dynamicInt(1))))
      },
      test("very long case name") {
        val longName = "Case" + ("X" * 1000)
        val action   = MigrationAction.RenameCase(DynamicOptic.root, longName, "ShortName")
        val input    = dynamicVariant(longName, dynamicUnit)
        val result   = action.apply(input)
        assertTrue(result == Right(dynamicVariant("ShortName", dynamicUnit)))
      },
      test("variant with complex nested value") {
        val action      = MigrationAction.RenameCase(DynamicOptic.root, "Complex", "Renamed")
        val nestedValue = dynamicRecord(
          "a" -> dynamicInt(1),
          "b" -> dynamicRecord(
            "c" -> dynamicString("nested"),
            "d" -> DynamicValue.Sequence(dynamicInt(1), dynamicInt(2))
          )
        )
        val input  = dynamicVariant("Complex", nestedValue)
        val result = action.apply(input)
        assertTrue(result == Right(dynamicVariant("Renamed", nestedValue)))
      }
    )
  )
}
