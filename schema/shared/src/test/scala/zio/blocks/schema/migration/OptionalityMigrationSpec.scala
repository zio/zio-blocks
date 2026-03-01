package zio.blocks.schema.migration

import zio.blocks.schema._
import zio.test._

/**
 * Tests for optional field migration operations.
 *
 * Covers:
 *   - Mandate: Converting Option[T] to T with default
 *   - Optionalize: Converting T to Option[T]
 *   - Round-trip conversions
 *   - Edge cases with None values
 *   - Nested optional fields
 */
object OptionalityMigrationSpec extends SchemaBaseSpec {

  // ─────────────────────────────────────────────────────────────────────────
  // Test Helpers
  // ─────────────────────────────────────────────────────────────────────────

  def dynamicRecord(fields: (String, DynamicValue)*): DynamicValue =
    DynamicValue.Record(fields: _*)

  def dynamicString(s: String): DynamicValue =
    DynamicValue.Primitive(PrimitiveValue.String(s))

  def dynamicInt(i: Int): DynamicValue =
    DynamicValue.Primitive(PrimitiveValue.Int(i))

  def dynamicSome(value: DynamicValue): DynamicValue =
    DynamicValue.Variant("Some", DynamicValue.Record(("value", value)))

  def dynamicNone: DynamicValue =
    DynamicValue.Variant("None", DynamicValue.Record())

  // ─────────────────────────────────────────────────────────────────────────
  // Tests
  // ─────────────────────────────────────────────────────────────────────────

  def spec: Spec[TestEnvironment, Any] = suite("OptionalityMigrationSpec")(
    suite("Mandate (Option[T] -> T)")(
      test("extracts Some value") {
        val action = MigrationAction.Mandate(
          DynamicOptic.root,
          "value",
          Resolved.Literal.int(0)
        )
        val input  = dynamicRecord("value" -> dynamicSome(dynamicInt(42)))
        val result = action.apply(input)
        assertTrue(result == Right(dynamicRecord("value" -> dynamicInt(42))))
      },
      test("uses default for None") {
        val action = MigrationAction.Mandate(
          DynamicOptic.root,
          "value",
          Resolved.Literal.int(99)
        )
        val input  = dynamicRecord("value" -> dynamicNone)
        val result = action.apply(input)
        assertTrue(result == Right(dynamicRecord("value" -> dynamicInt(99))))
      },
      test("uses default for Null") {
        val action = MigrationAction.Mandate(
          DynamicOptic.root,
          "value",
          Resolved.Literal.string("default")
        )
        val input  = dynamicRecord("value" -> DynamicValue.Null)
        val result = action.apply(input)
        assertTrue(result == Right(dynamicRecord("value" -> dynamicString("default"))))
      },
      test("passes through non-optional value unchanged") {
        val action = MigrationAction.Mandate(
          DynamicOptic.root,
          "value",
          Resolved.Literal.int(0)
        )
        val input  = dynamicRecord("value" -> dynamicInt(42))
        val result = action.apply(input)
        assertTrue(result == Right(dynamicRecord("value" -> dynamicInt(42))))
      },
      test("mandate with string default") {
        val action = MigrationAction.Mandate(
          DynamicOptic.root,
          "name",
          Resolved.Literal.string("Unknown")
        )
        val input  = dynamicRecord("name" -> dynamicNone)
        val result = action.apply(input)
        assertTrue(result == Right(dynamicRecord("name" -> dynamicString("Unknown"))))
      },
      test("mandate preserves other fields") {
        val action = MigrationAction.Mandate(
          DynamicOptic.root,
          "optional",
          Resolved.Literal.int(0)
        )
        val input = dynamicRecord(
          "required" -> dynamicString("keep"),
          "optional" -> dynamicSome(dynamicInt(42)),
          "another"  -> dynamicInt(99)
        )
        val result = action.apply(input)
        assertTrue(
          result == Right(
            dynamicRecord(
              "required" -> dynamicString("keep"),
              "optional" -> dynamicInt(42),
              "another"  -> dynamicInt(99)
            )
          )
        )
      },
      test("mandate with computed default") {
        val action = MigrationAction.Mandate(
          DynamicOptic.root,
          "value",
          Resolved.FieldAccess("fallback", Resolved.Identity)
        )
        val input = dynamicRecord(
          "value"    -> dynamicNone,
          "fallback" -> dynamicInt(123)
        )
        val result = action.apply(input)
        assertTrue(
          result == Right(
            dynamicRecord(
              "value"    -> dynamicInt(123),
              "fallback" -> dynamicInt(123)
            )
          )
        )
      },
      test("mandate fails when default expression fails") {
        val action = MigrationAction.Mandate(
          DynamicOptic.root,
          "value",
          Resolved.Fail("no default available")
        )
        val input = dynamicRecord("value" -> dynamicNone)
        assertTrue(action.apply(input).isLeft)
      },
      test("mandate extracts nested Some values") {
        val action = MigrationAction.Mandate(
          DynamicOptic.root,
          "nested",
          Resolved.Literal.int(0)
        )
        val nestedRecord = dynamicRecord("x" -> dynamicInt(1), "y" -> dynamicInt(2))
        val input        = dynamicRecord("nested" -> dynamicSome(nestedRecord))
        val result       = action.apply(input)
        assertTrue(result == Right(dynamicRecord("nested" -> nestedRecord)))
      }
    ),
    suite("Optionalize (T -> Option[T])")(
      test("wraps value in Some") {
        val action = MigrationAction.Optionalize(DynamicOptic.root, "value")
        val input  = dynamicRecord("value" -> dynamicInt(42))
        val result = action.apply(input)
        assertTrue(result == Right(dynamicRecord("value" -> dynamicSome(dynamicInt(42)))))
      },
      test("wraps string in Some") {
        val action = MigrationAction.Optionalize(DynamicOptic.root, "name")
        val input  = dynamicRecord("name" -> dynamicString("Alice"))
        val result = action.apply(input)
        assertTrue(result == Right(dynamicRecord("name" -> dynamicSome(dynamicString("Alice")))))
      },
      test("optionalize preserves other fields") {
        val action = MigrationAction.Optionalize(DynamicOptic.root, "toWrap")
        val input  = dynamicRecord(
          "keep1"  -> dynamicInt(1),
          "toWrap" -> dynamicString("wrap me"),
          "keep2"  -> dynamicInt(2)
        )
        val result = action.apply(input)
        assertTrue(
          result == Right(
            dynamicRecord(
              "keep1"  -> dynamicInt(1),
              "toWrap" -> dynamicSome(dynamicString("wrap me")),
              "keep2"  -> dynamicInt(2)
            )
          )
        )
      },
      test("wraps record value in Some") {
        val action       = MigrationAction.Optionalize(DynamicOptic.root, "nested")
        val nestedRecord = dynamicRecord("x" -> dynamicInt(1))
        val input        = dynamicRecord("nested" -> nestedRecord)
        val result       = action.apply(input)
        assertTrue(result == Right(dynamicRecord("nested" -> dynamicSome(nestedRecord))))
      },
      test("wraps sequence in Some") {
        val action   = MigrationAction.Optionalize(DynamicOptic.root, "items")
        val sequence = DynamicValue.Sequence(dynamicInt(1), dynamicInt(2))
        val input    = dynamicRecord("items" -> sequence)
        val result   = action.apply(input)
        assertTrue(result == Right(dynamicRecord("items" -> dynamicSome(sequence))))
      }
    ),
    suite("Round-trip conversions")(
      test("optionalize then mandate recovers original (with Some)") {
        val migration = DynamicMigration(
          Vector(
            MigrationAction.Optionalize(DynamicOptic.root, "value"),
            MigrationAction.Mandate(DynamicOptic.root, "value", Resolved.Literal.int(0))
          )
        )
        val input  = dynamicRecord("value" -> dynamicInt(42))
        val result = migration.apply(input)
        assertTrue(result == Right(input))
      },
      test("mandate then optionalize wraps in Some") {
        val migration = DynamicMigration(
          Vector(
            MigrationAction.Mandate(DynamicOptic.root, "value", Resolved.Literal.int(0)),
            MigrationAction.Optionalize(DynamicOptic.root, "value")
          )
        )
        val input  = dynamicRecord("value" -> dynamicSome(dynamicInt(42)))
        val result = migration.apply(input)
        assertTrue(result == Right(dynamicRecord("value" -> dynamicSome(dynamicInt(42)))))
      },
      test("reverse of optionalize is mandate (structural)") {
        val optionalize = MigrationAction.Optionalize(DynamicOptic.root, "field")
        val reversed    = optionalize.reverse
        assertTrue(reversed.isInstanceOf[MigrationAction.Mandate])
      },
      test("reverse of mandate is optionalize") {
        val mandate  = MigrationAction.Mandate(DynamicOptic.root, "field", Resolved.Literal.int(0))
        val reversed = mandate.reverse
        assertTrue(reversed.isInstanceOf[MigrationAction.Optionalize])
      }
    ),
    suite("Multiple optional fields")(
      test("mandate multiple fields") {
        val migration = DynamicMigration(
          Vector(
            MigrationAction.Mandate(DynamicOptic.root, "a", Resolved.Literal.int(1)),
            MigrationAction.Mandate(DynamicOptic.root, "b", Resolved.Literal.int(2)),
            MigrationAction.Mandate(DynamicOptic.root, "c", Resolved.Literal.int(3))
          )
        )
        val input = dynamicRecord(
          "a" -> dynamicSome(dynamicInt(10)),
          "b" -> dynamicNone,
          "c" -> dynamicSome(dynamicInt(30))
        )
        val result = migration.apply(input)
        assertTrue(
          result == Right(
            dynamicRecord(
              "a" -> dynamicInt(10),
              "b" -> dynamicInt(2),
              "c" -> dynamicInt(30)
            )
          )
        )
      },
      test("optionalize multiple fields") {
        val migration = DynamicMigration(
          Vector(
            MigrationAction.Optionalize(DynamicOptic.root, "a"),
            MigrationAction.Optionalize(DynamicOptic.root, "b")
          )
        )
        val input = dynamicRecord(
          "a" -> dynamicInt(1),
          "b" -> dynamicString("test")
        )
        val result = migration.apply(input)
        assertTrue(
          result == Right(
            dynamicRecord(
              "a" -> dynamicSome(dynamicInt(1)),
              "b" -> dynamicSome(dynamicString("test"))
            )
          )
        )
      }
    ),
    suite("Combined with other operations")(
      test("add field then optionalize it") {
        val migration = DynamicMigration(
          Vector(
            MigrationAction.AddField(DynamicOptic.root, "newField", Resolved.Literal.int(42)),
            MigrationAction.Optionalize(DynamicOptic.root, "newField")
          )
        )
        val input  = dynamicRecord()
        val result = migration.apply(input)
        assertTrue(result == Right(dynamicRecord("newField" -> dynamicSome(dynamicInt(42)))))
      },
      test("mandate then rename") {
        val migration = DynamicMigration(
          Vector(
            MigrationAction.Mandate(DynamicOptic.root, "optionalName", Resolved.Literal.string("Unknown")),
            MigrationAction.Rename(DynamicOptic.root, "optionalName", "name")
          )
        )
        val input  = dynamicRecord("optionalName" -> dynamicSome(dynamicString("Alice")))
        val result = migration.apply(input)
        assertTrue(result == Right(dynamicRecord("name" -> dynamicString("Alice"))))
      },
      test("rename then optionalize") {
        val migration = DynamicMigration(
          Vector(
            MigrationAction.Rename(DynamicOptic.root, "required", "optional"),
            MigrationAction.Optionalize(DynamicOptic.root, "optional")
          )
        )
        val input  = dynamicRecord("required" -> dynamicInt(42))
        val result = migration.apply(input)
        assertTrue(result == Right(dynamicRecord("optional" -> dynamicSome(dynamicInt(42)))))
      }
    ),
    suite("Edge cases")(
      test("mandate on already unwrapped value") {
        val action = MigrationAction.Mandate(DynamicOptic.root, "value", Resolved.Literal.int(0))
        val input  = dynamicRecord("value" -> dynamicInt(42))
        val result = action.apply(input)
        assertTrue(result == Right(input))
      },
      test("double optionalize wraps twice") {
        val migration = DynamicMigration(
          Vector(
            MigrationAction.Optionalize(DynamicOptic.root, "value"),
            MigrationAction.Optionalize(DynamicOptic.root, "value")
          )
        )
        val input  = dynamicRecord("value" -> dynamicInt(42))
        val result = migration.apply(input)
        assertTrue(
          result == Right(
            dynamicRecord(
              "value" -> dynamicSome(dynamicSome(dynamicInt(42)))
            )
          )
        )
      },
      test("double mandate unwraps twice") {
        val migration = DynamicMigration(
          Vector(
            MigrationAction.Mandate(DynamicOptic.root, "value", Resolved.Literal.int(0)),
            MigrationAction.Mandate(DynamicOptic.root, "value", Resolved.Literal.int(0))
          )
        )
        val input  = dynamicRecord("value" -> dynamicSome(dynamicSome(dynamicInt(42))))
        val result = migration.apply(input)
        assertTrue(result == Right(dynamicRecord("value" -> dynamicInt(42))))
      }
    )
  )
}
