package zio.blocks.schema.migration

import zio.blocks.schema._
import zio.test._

/**
 * Tests for record field migration operations.
 *
 * Covers:
 *   - AddField with various default values
 *   - DropField with and without reverse defaults
 *   - Rename field operations
 *   - TransformValue field operations
 *   - ChangeType field operations
 *   - Multiple field operations in sequence
 *   - Preserving field order
 *   - Edge cases with empty records
 */
object RecordFieldMigrationSpec extends SchemaBaseSpec {

  // ─────────────────────────────────────────────────────────────────────────
  // Test Helpers
  // ─────────────────────────────────────────────────────────────────────────

  def dynamicRecord(fields: (String, DynamicValue)*): DynamicValue =
    DynamicValue.Record(fields: _*)

  def dynamicString(s: String): DynamicValue =
    DynamicValue.Primitive(PrimitiveValue.String(s))

  def dynamicInt(i: Int): DynamicValue =
    DynamicValue.Primitive(PrimitiveValue.Int(i))

  def dynamicLong(l: Long): DynamicValue =
    DynamicValue.Primitive(PrimitiveValue.Long(l))

  def dynamicDouble(d: Double): DynamicValue =
    DynamicValue.Primitive(PrimitiveValue.Double(d))

  def dynamicBool(b: Boolean): DynamicValue =
    DynamicValue.Primitive(PrimitiveValue.Boolean(b))

  // ─────────────────────────────────────────────────────────────────────────
  // Tests
  // ─────────────────────────────────────────────────────────────────────────

  def spec: Spec[TestEnvironment, Any] = suite("RecordFieldMigrationSpec")(
    suite("AddField")(
      test("adds field with int literal default") {
        val action = MigrationAction.AddField(DynamicOptic.root, "count", Resolved.Literal.int(0))
        val input  = dynamicRecord("name" -> dynamicString("test"))
        val result = action.apply(input)
        assertTrue(
          result == Right(
            dynamicRecord(
              "name"  -> dynamicString("test"),
              "count" -> dynamicInt(0)
            )
          )
        )
      },
      test("adds field with string literal default") {
        val action = MigrationAction.AddField(DynamicOptic.root, "status", Resolved.Literal.string("active"))
        val input  = dynamicRecord("id" -> dynamicInt(1))
        val result = action.apply(input)
        assertTrue(
          result == Right(
            dynamicRecord(
              "id"     -> dynamicInt(1),
              "status" -> dynamicString("active")
            )
          )
        )
      },
      test("adds field with boolean literal default") {
        val action = MigrationAction.AddField(DynamicOptic.root, "active", Resolved.Literal.boolean(true))
        val input  = dynamicRecord()
        val result = action.apply(input)
        assertTrue(result == Right(dynamicRecord("active" -> dynamicBool(true))))
      },
      test("adds field with double literal default") {
        val action = MigrationAction.AddField(DynamicOptic.root, "rate", Resolved.Literal.double(3.14))
        val input  = dynamicRecord()
        val result = action.apply(input)
        assertTrue(result == Right(dynamicRecord("rate" -> dynamicDouble(3.14))))
      },
      test("adds field to empty record") {
        val action = MigrationAction.AddField(DynamicOptic.root, "first", Resolved.Literal.int(1))
        val input  = dynamicRecord()
        val result = action.apply(input)
        assertTrue(result == Right(dynamicRecord("first" -> dynamicInt(1))))
      },
      test("adds multiple fields in sequence") {
        val migration = DynamicMigration(
          Vector(
            MigrationAction.AddField(DynamicOptic.root, "a", Resolved.Literal.int(1)),
            MigrationAction.AddField(DynamicOptic.root, "b", Resolved.Literal.int(2)),
            MigrationAction.AddField(DynamicOptic.root, "c", Resolved.Literal.int(3))
          )
        )
        val input  = dynamicRecord()
        val result = migration.apply(input)
        assertTrue(
          result == Right(
            dynamicRecord(
              "a" -> dynamicInt(1),
              "b" -> dynamicInt(2),
              "c" -> dynamicInt(3)
            )
          )
        )
      },
      test("adds field with computed default from existing field") {
        val action = MigrationAction.AddField(
          DynamicOptic.root,
          "nameCopy",
          Resolved.FieldAccess("name", Resolved.Identity)
        )
        val input  = dynamicRecord("name" -> dynamicString("Alice"))
        val result = action.apply(input)
        assertTrue(
          result == Right(
            dynamicRecord(
              "name"     -> dynamicString("Alice"),
              "nameCopy" -> dynamicString("Alice")
            )
          )
        )
      },
      test("preserves existing field order") {
        val action = MigrationAction.AddField(DynamicOptic.root, "z", Resolved.Literal.int(26))
        val input  = dynamicRecord(
          "a" -> dynamicInt(1),
          "b" -> dynamicInt(2),
          "c" -> dynamicInt(3)
        )
        val result = action.apply(input)
        result match {
          case Right(DynamicValue.Record(fields)) =>
            assertTrue(
              fields.map(_._1).toVector == Vector("a", "b", "c", "z")
            )
          case _ => assertTrue(false)
        }
      },
      test("fails when default expression fails") {
        val action = MigrationAction.AddField(DynamicOptic.root, "bad", Resolved.Fail("no default"))
        val input  = dynamicRecord()
        assertTrue(action.apply(input).isLeft)
      },
      test("fails on non-record input") {
        val action = MigrationAction.AddField(DynamicOptic.root, "field", Resolved.Literal.int(1))
        val input  = dynamicString("not a record")
        assertTrue(action.apply(input).isLeft)
      },
      test("fails on sequence input") {
        val action = MigrationAction.AddField(DynamicOptic.root, "field", Resolved.Literal.int(1))
        val input  = DynamicValue.Sequence(dynamicInt(1), dynamicInt(2))
        assertTrue(action.apply(input).isLeft)
      }
    ),
    suite("DropField")(
      test("removes existing field") {
        val action = MigrationAction.DropField(DynamicOptic.root, "toRemove", Resolved.Literal.int(0))
        val input  = dynamicRecord(
          "keep"     -> dynamicString("kept"),
          "toRemove" -> dynamicString("removed")
        )
        val result = action.apply(input)
        assertTrue(result == Right(dynamicRecord("keep" -> dynamicString("kept"))))
      },
      test("removes field from multi-field record") {
        val action = MigrationAction.DropField(DynamicOptic.root, "b", Resolved.Literal.int(0))
        val input  = dynamicRecord(
          "a" -> dynamicInt(1),
          "b" -> dynamicInt(2),
          "c" -> dynamicInt(3)
        )
        val result = action.apply(input)
        assertTrue(
          result == Right(
            dynamicRecord(
              "a" -> dynamicInt(1),
              "c" -> dynamicInt(3)
            )
          )
        )
      },
      test("removes first field") {
        val action = MigrationAction.DropField(DynamicOptic.root, "first", Resolved.Literal.int(0))
        val input  = dynamicRecord(
          "first"  -> dynamicInt(1),
          "second" -> dynamicInt(2)
        )
        val result = action.apply(input)
        assertTrue(result == Right(dynamicRecord("second" -> dynamicInt(2))))
      },
      test("removes last field") {
        val action = MigrationAction.DropField(DynamicOptic.root, "last", Resolved.Literal.int(0))
        val input  = dynamicRecord(
          "first" -> dynamicInt(1),
          "last"  -> dynamicInt(2)
        )
        val result = action.apply(input)
        assertTrue(result == Right(dynamicRecord("first" -> dynamicInt(1))))
      },
      test("removes only field leaves empty record") {
        val action = MigrationAction.DropField(DynamicOptic.root, "only", Resolved.Literal.int(0))
        val input  = dynamicRecord("only" -> dynamicInt(1))
        val result = action.apply(input)
        assertTrue(result == Right(dynamicRecord()))
      },
      test("removing non-existent field is no-op") {
        val action = MigrationAction.DropField(DynamicOptic.root, "missing", Resolved.Literal.int(0))
        val input  = dynamicRecord("existing" -> dynamicInt(1))
        val result = action.apply(input)
        assertTrue(result == Right(dynamicRecord("existing" -> dynamicInt(1))))
      },
      test("removes multiple fields in sequence") {
        val migration = DynamicMigration(
          Vector(
            MigrationAction.DropField(DynamicOptic.root, "a", Resolved.Literal.int(0)),
            MigrationAction.DropField(DynamicOptic.root, "c", Resolved.Literal.int(0))
          )
        )
        val input = dynamicRecord(
          "a" -> dynamicInt(1),
          "b" -> dynamicInt(2),
          "c" -> dynamicInt(3)
        )
        val result = migration.apply(input)
        assertTrue(result == Right(dynamicRecord("b" -> dynamicInt(2))))
      },
      test("drop preserves order of remaining fields") {
        val action = MigrationAction.DropField(DynamicOptic.root, "b", Resolved.Literal.int(0))
        val input  = dynamicRecord(
          "a" -> dynamicInt(1),
          "b" -> dynamicInt(2),
          "c" -> dynamicInt(3),
          "d" -> dynamicInt(4)
        )
        val result = action.apply(input)
        result match {
          case Right(DynamicValue.Record(fields)) =>
            assertTrue(fields.map(_._1).toVector == Vector("a", "c", "d"))
          case _ => assertTrue(false)
        }
      },
      test("fails on non-record input") {
        val action = MigrationAction.DropField(DynamicOptic.root, "field", Resolved.Literal.int(0))
        val input  = dynamicInt(42)
        assertTrue(action.apply(input).isLeft)
      }
    ),
    suite("Rename")(
      test("renames existing field") {
        val action = MigrationAction.Rename(DynamicOptic.root, "oldName", "newName")
        val input  = dynamicRecord("oldName" -> dynamicString("value"))
        val result = action.apply(input)
        assertTrue(result == Right(dynamicRecord("newName" -> dynamicString("value"))))
      },
      test("preserves field value after rename") {
        val action       = MigrationAction.Rename(DynamicOptic.root, "x", "y")
        val complexValue = dynamicRecord("nested" -> dynamicInt(42))
        val input        = dynamicRecord("x" -> complexValue)
        val result       = action.apply(input)
        assertTrue(result == Right(dynamicRecord("y" -> complexValue)))
      },
      test("preserves other fields unchanged") {
        val action = MigrationAction.Rename(DynamicOptic.root, "rename", "renamed")
        val input  = dynamicRecord(
          "keep1"  -> dynamicInt(1),
          "rename" -> dynamicInt(2),
          "keep2"  -> dynamicInt(3)
        )
        val result = action.apply(input)
        assertTrue(
          result == Right(
            dynamicRecord(
              "keep1"   -> dynamicInt(1),
              "renamed" -> dynamicInt(2),
              "keep2"   -> dynamicInt(3)
            )
          )
        )
      },
      test("preserves field position after rename") {
        val action = MigrationAction.Rename(DynamicOptic.root, "b", "B")
        val input  = dynamicRecord(
          "a" -> dynamicInt(1),
          "b" -> dynamicInt(2),
          "c" -> dynamicInt(3)
        )
        val result = action.apply(input)
        result match {
          case Right(DynamicValue.Record(fields)) =>
            assertTrue(fields.map(_._1).toVector == Vector("a", "B", "c"))
          case _ => assertTrue(false)
        }
      },
      test("rename non-existent field is no-op") {
        val action = MigrationAction.Rename(DynamicOptic.root, "missing", "new")
        val input  = dynamicRecord("existing" -> dynamicInt(1))
        val result = action.apply(input)
        assertTrue(result == Right(input))
      },
      test("chained renames: a->b->c") {
        val migration = DynamicMigration(
          Vector(
            MigrationAction.Rename(DynamicOptic.root, "a", "b"),
            MigrationAction.Rename(DynamicOptic.root, "b", "c")
          )
        )
        val input  = dynamicRecord("a" -> dynamicInt(1))
        val result = migration.apply(input)
        assertTrue(result == Right(dynamicRecord("c" -> dynamicInt(1))))
      },
      test("swap field names requires intermediate") {
        // To swap a<->b, need: a->temp, b->a, temp->b
        val migration = DynamicMigration(
          Vector(
            MigrationAction.Rename(DynamicOptic.root, "a", "temp"),
            MigrationAction.Rename(DynamicOptic.root, "b", "a"),
            MigrationAction.Rename(DynamicOptic.root, "temp", "b")
          )
        )
        val input = dynamicRecord(
          "a" -> dynamicInt(1),
          "b" -> dynamicInt(2)
        )
        val result = migration.apply(input)
        assertTrue(
          result == Right(
            dynamicRecord(
              "b" -> dynamicInt(1),
              "a" -> dynamicInt(2)
            )
          )
        )
      },
      test("fails on non-record input") {
        val action = MigrationAction.Rename(DynamicOptic.root, "a", "b")
        assertTrue(action.apply(dynamicString("test")).isLeft)
      }
    ),
    suite("TransformValue")(
      test("transforms field value with identity") {
        val action = MigrationAction.TransformValue(
          DynamicOptic.root,
          "x",
          Resolved.Identity,
          Resolved.Identity
        )
        val input  = dynamicRecord("x" -> dynamicInt(42))
        val result = action.apply(input)
        assertTrue(result == Right(input))
      },
      test("transforms field value with literal replacement") {
        val action = MigrationAction.TransformValue(
          DynamicOptic.root,
          "x",
          Resolved.Literal.int(100),
          Resolved.Literal.int(0)
        )
        val input  = dynamicRecord("x" -> dynamicInt(42))
        val result = action.apply(input)
        assertTrue(result == Right(dynamicRecord("x" -> dynamicInt(100))))
      },
      test("transforms field with type conversion") {
        val action = MigrationAction.TransformValue(
          DynamicOptic.root,
          "value",
          Resolved.Convert("Int", "String", Resolved.Identity),
          Resolved.Convert("String", "Int", Resolved.Identity)
        )
        val input  = dynamicRecord("value" -> dynamicInt(42))
        val result = action.apply(input)
        assertTrue(result == Right(dynamicRecord("value" -> dynamicString("42"))))
      },
      test("preserves other fields") {
        val action = MigrationAction.TransformValue(
          DynamicOptic.root,
          "transform",
          Resolved.Literal.string("transformed"),
          Resolved.Literal.string("original")
        )
        val input = dynamicRecord(
          "keep"      -> dynamicInt(1),
          "transform" -> dynamicString("original"),
          "also_keep" -> dynamicInt(2)
        )
        val result = action.apply(input)
        assertTrue(
          result == Right(
            dynamicRecord(
              "keep"      -> dynamicInt(1),
              "transform" -> dynamicString("transformed"),
              "also_keep" -> dynamicInt(2)
            )
          )
        )
      }
    ),
    suite("ChangeType")(
      test("changes int to string") {
        val action = MigrationAction.ChangeType(
          DynamicOptic.root,
          "value",
          Resolved.Convert("Int", "String", Resolved.Identity),
          Resolved.Convert("String", "Int", Resolved.Identity)
        )
        val input  = dynamicRecord("value" -> dynamicInt(42))
        val result = action.apply(input)
        assertTrue(result == Right(dynamicRecord("value" -> dynamicString("42"))))
      },
      test("changes string to int") {
        val action = MigrationAction.ChangeType(
          DynamicOptic.root,
          "value",
          Resolved.Convert("String", "Int", Resolved.Identity),
          Resolved.Convert("Int", "String", Resolved.Identity)
        )
        val input  = dynamicRecord("value" -> dynamicString("42"))
        val result = action.apply(input)
        assertTrue(result == Right(dynamicRecord("value" -> dynamicInt(42))))
      },
      test("changes int to long") {
        val action = MigrationAction.ChangeType(
          DynamicOptic.root,
          "value",
          Resolved.Convert("Int", "Long", Resolved.Identity),
          Resolved.Convert("Long", "Int", Resolved.Identity)
        )
        val input  = dynamicRecord("value" -> dynamicInt(42))
        val result = action.apply(input)
        assertTrue(result == Right(dynamicRecord("value" -> dynamicLong(42L))))
      }
    ),
    suite("Combined operations")(
      test("add then rename") {
        val migration = DynamicMigration(
          Vector(
            MigrationAction.AddField(DynamicOptic.root, "temp", Resolved.Literal.int(42)),
            MigrationAction.Rename(DynamicOptic.root, "temp", "final")
          )
        )
        val input  = dynamicRecord()
        val result = migration.apply(input)
        assertTrue(result == Right(dynamicRecord("final" -> dynamicInt(42))))
      },
      test("rename then drop original name has no effect") {
        val migration = DynamicMigration(
          Vector(
            MigrationAction.Rename(DynamicOptic.root, "a", "b"),
            MigrationAction.DropField(DynamicOptic.root, "a", Resolved.Literal.int(0))
          )
        )
        val input  = dynamicRecord("a" -> dynamicInt(1))
        val result = migration.apply(input)
        assertTrue(result == Right(dynamicRecord("b" -> dynamicInt(1))))
      },
      test("complex migration: multiple adds, renames, and drops") {
        val migration = DynamicMigration(
          Vector(
            MigrationAction.AddField(DynamicOptic.root, "new1", Resolved.Literal.int(1)),
            MigrationAction.Rename(DynamicOptic.root, "old", "renamed"),
            MigrationAction.DropField(DynamicOptic.root, "obsolete", Resolved.Literal.int(0)),
            MigrationAction.AddField(DynamicOptic.root, "new2", Resolved.Literal.int(2))
          )
        )
        val input = dynamicRecord(
          "old"      -> dynamicString("value"),
          "obsolete" -> dynamicString("removed"),
          "keep"     -> dynamicInt(99)
        )
        val result = migration.apply(input)
        assertTrue(
          result == Right(
            dynamicRecord(
              "renamed" -> dynamicString("value"),
              "keep"    -> dynamicInt(99),
              "new1"    -> dynamicInt(1),
              "new2"    -> dynamicInt(2)
            )
          )
        )
      }
    ),
    suite("Edge cases")(
      test("empty record stays empty with no actions") {
        val migration = DynamicMigration.identity
        val input     = dynamicRecord()
        assertTrue(migration.apply(input) == Right(input))
      },
      test("field name with special characters") {
        val action = MigrationAction.Rename(DynamicOptic.root, "field-with-dash", "field_with_underscore")
        val input  = dynamicRecord("field-with-dash" -> dynamicInt(1))
        val result = action.apply(input)
        assertTrue(result == Right(dynamicRecord("field_with_underscore" -> dynamicInt(1))))
      },
      test("field name with unicode") {
        val action = MigrationAction.Rename(DynamicOptic.root, "名前", "name")
        val input  = dynamicRecord("名前" -> dynamicString("値"))
        val result = action.apply(input)
        assertTrue(result == Right(dynamicRecord("name" -> dynamicString("値"))))
      },
      test("very long field name") {
        val longName = "a" * 1000
        val action   = MigrationAction.AddField(DynamicOptic.root, longName, Resolved.Literal.int(1))
        val input    = dynamicRecord()
        val result   = action.apply(input)
        result match {
          case Right(DynamicValue.Record(fields)) =>
            assertTrue(fields.exists(_._1 == longName))
          case _ => assertTrue(false)
        }
      },
      test("record with many fields") {
        val fields = (1 to 100).map(i => s"field$i" -> dynamicInt(i))
        val input  = dynamicRecord(fields: _*)
        val action = MigrationAction.AddField(DynamicOptic.root, "field101", Resolved.Literal.int(101))
        val result = action.apply(input)
        result match {
          case Right(DynamicValue.Record(resultFields)) =>
            assertTrue(resultFields.length == 101)
          case _ => assertTrue(false)
        }
      }
    )
  )
}
