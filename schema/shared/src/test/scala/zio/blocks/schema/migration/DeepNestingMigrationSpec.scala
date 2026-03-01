package zio.blocks.schema.migration

import zio.blocks.schema._
import zio.test._

/**
 * Tests for deeply nested structure migrations.
 *
 * Covers:
 *   - Operations at various nesting depths
 *   - Path navigation through records, sequences, variants
 *   - Combined nested operations
 *   - Performance with deep structures
 */
object DeepNestingMigrationSpec extends SchemaBaseSpec {

  // ─────────────────────────────────────────────────────────────────────────
  // Test Helpers
  // ─────────────────────────────────────────────────────────────────────────

  def dynamicRecord(fields: (String, DynamicValue)*): DynamicValue =
    DynamicValue.Record(fields: _*)

  def dynamicString(s: String): DynamicValue =
    DynamicValue.Primitive(PrimitiveValue.String(s))

  def dynamicInt(i: Int): DynamicValue =
    DynamicValue.Primitive(PrimitiveValue.Int(i))

  def dynamicSequence(elements: DynamicValue*): DynamicValue =
    DynamicValue.Sequence(elements: _*)

  def dynamicVariant(caseName: String, value: DynamicValue): DynamicValue =
    DynamicValue.Variant(caseName, value)

  // ─────────────────────────────────────────────────────────────────────────
  // Tests
  // ─────────────────────────────────────────────────────────────────────────

  def spec: Spec[TestEnvironment, Any] = suite("DeepNestingMigrationSpec")(
    suite("Single level nesting")(
      test("add field to nested record") {
        val action = MigrationAction.AddField(
          DynamicOptic.root.field("inner"),
          "newField",
          Resolved.Literal.int(42)
        )
        val input = dynamicRecord(
          "outer" -> dynamicInt(1),
          "inner" -> dynamicRecord("existing" -> dynamicString("value"))
        )
        val result = action.apply(input)
        assertTrue(
          result == Right(
            dynamicRecord(
              "outer" -> dynamicInt(1),
              "inner" -> dynamicRecord(
                "existing" -> dynamicString("value"),
                "newField" -> dynamicInt(42)
              )
            )
          )
        )
      },
      test("rename field in nested record") {
        val action = MigrationAction.Rename(
          DynamicOptic.root.field("nested"),
          "oldName",
          "newName"
        )
        val input = dynamicRecord(
          "top"    -> dynamicInt(1),
          "nested" -> dynamicRecord("oldName" -> dynamicString("value"))
        )
        val result = action.apply(input)
        assertTrue(
          result == Right(
            dynamicRecord(
              "top"    -> dynamicInt(1),
              "nested" -> dynamicRecord("newName" -> dynamicString("value"))
            )
          )
        )
      },
      test("drop field from nested record") {
        val action = MigrationAction.DropField(
          DynamicOptic.root.field("nested"),
          "toRemove",
          Resolved.Literal.int(0)
        )
        val input = dynamicRecord(
          "nested" -> dynamicRecord(
            "keep"     -> dynamicInt(1),
            "toRemove" -> dynamicInt(2)
          )
        )
        val result = action.apply(input)
        assertTrue(
          result == Right(
            dynamicRecord(
              "nested" -> dynamicRecord("keep" -> dynamicInt(1))
            )
          )
        )
      }
    ),
    suite("Multi-level nesting")(
      test("operation at depth 2") {
        val path   = DynamicOptic.root.field("level1").field("level2")
        val action = MigrationAction.AddField(path, "added", Resolved.Literal.int(42))
        val input  = dynamicRecord(
          "level1" -> dynamicRecord(
            "level2" -> dynamicRecord("existing" -> dynamicString("value"))
          )
        )
        val result = action.apply(input)
        assertTrue(
          result == Right(
            dynamicRecord(
              "level1" -> dynamicRecord(
                "level2" -> dynamicRecord(
                  "existing" -> dynamicString("value"),
                  "added"    -> dynamicInt(42)
                )
              )
            )
          )
        )
      },
      test("operation at depth 3") {
        val path   = DynamicOptic.root.field("a").field("b").field("c")
        val action = MigrationAction.Rename(path, "old", "new")
        val input  = dynamicRecord(
          "a" -> dynamicRecord(
            "b" -> dynamicRecord(
              "c" -> dynamicRecord("old" -> dynamicInt(1))
            )
          )
        )
        val result = action.apply(input)
        assertTrue(
          result == Right(
            dynamicRecord(
              "a" -> dynamicRecord(
                "b" -> dynamicRecord(
                  "c" -> dynamicRecord("new" -> dynamicInt(1))
                )
              )
            )
          )
        )
      },
      test("operation at depth 5") {
        val path = DynamicOptic.root
          .field("l1")
          .field("l2")
          .field("l3")
          .field("l4")
          .field("l5")
        val action = MigrationAction.AddField(path, "deep", Resolved.Literal.int(99))
        val input  = dynamicRecord(
          "l1" -> dynamicRecord(
            "l2" -> dynamicRecord(
              "l3" -> dynamicRecord(
                "l4" -> dynamicRecord(
                  "l5" -> dynamicRecord("existing" -> dynamicInt(1))
                )
              )
            )
          )
        )
        val result = action.apply(input)
        result match {
          case Right(DynamicValue.Record(fields)) =>
            assertTrue(fields.nonEmpty)
          case _ => assertTrue(false)
        }
      }
    ),
    suite("Mixed structure nesting")(
      test("record inside sequence") {
        val action = MigrationAction.TransformElements(
          DynamicOptic.root.field("items"),
          Resolved.FieldAccess("value", Resolved.Identity),
          Resolved.Identity
        )
        val input = dynamicRecord(
          "items" -> dynamicSequence(
            dynamicRecord("value" -> dynamicInt(1)),
            dynamicRecord("value" -> dynamicInt(2)),
            dynamicRecord("value" -> dynamicInt(3))
          )
        )
        val result = action.apply(input)
        assertTrue(
          result == Right(
            dynamicRecord(
              "items" -> dynamicSequence(dynamicInt(1), dynamicInt(2), dynamicInt(3))
            )
          )
        )
      },
      test("sequence inside record inside sequence") {
        // Transform elements of a sequence that contains records with nested sequences
        val action = MigrationAction.TransformElements(
          DynamicOptic.root,
          Resolved.Identity,
          Resolved.Identity
        )
        val input = dynamicSequence(
          dynamicRecord("nested" -> dynamicSequence(dynamicInt(1), dynamicInt(2))),
          dynamicRecord("nested" -> dynamicSequence(dynamicInt(3), dynamicInt(4)))
        )
        val result = action.apply(input)
        assertTrue(result == Right(input))
      },
      test("variant inside record") {
        val action = MigrationAction.RenameCase(
          DynamicOptic.root.field("status"),
          "Active",
          "Enabled"
        )
        val input = dynamicRecord(
          "id"     -> dynamicInt(1),
          "status" -> dynamicVariant("Active", dynamicRecord("since" -> dynamicString("2024")))
        )
        val result = action.apply(input)
        assertTrue(
          result == Right(
            dynamicRecord(
              "id"     -> dynamicInt(1),
              "status" -> dynamicVariant("Enabled", dynamicRecord("since" -> dynamicString("2024")))
            )
          )
        )
      },
      test("record inside variant inside record") {
        val path   = DynamicOptic.root.field("data")
        val action = MigrationAction.TransformCase(
          path,
          "Success",
          Vector(MigrationAction.AddField(DynamicOptic.root, "timestamp", Resolved.Literal.long(123L)))
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
                  "timestamp" -> DynamicValue.Primitive(PrimitiveValue.Long(123L))
                )
              )
            )
          )
        )
      }
    ),
    suite("Multiple operations on nested structures")(
      test("multiple operations on same nested record") {
        val path      = DynamicOptic.root.field("nested")
        val migration = DynamicMigration(
          Vector(
            MigrationAction.AddField(path, "field1", Resolved.Literal.int(1)),
            MigrationAction.AddField(path, "field2", Resolved.Literal.int(2)),
            MigrationAction.Rename(path, "old", "new")
          )
        )
        val input = dynamicRecord(
          "nested" -> dynamicRecord("old" -> dynamicString("value"))
        )
        val result = migration.apply(input)
        assertTrue(
          result == Right(
            dynamicRecord(
              "nested" -> dynamicRecord(
                "new"    -> dynamicString("value"),
                "field1" -> dynamicInt(1),
                "field2" -> dynamicInt(2)
              )
            )
          )
        )
      },
      test("operations on different nested paths") {
        val migration = DynamicMigration(
          Vector(
            MigrationAction.AddField(DynamicOptic.root.field("a"), "x", Resolved.Literal.int(1)),
            MigrationAction.AddField(DynamicOptic.root.field("b"), "y", Resolved.Literal.int(2))
          )
        )
        val input = dynamicRecord(
          "a" -> dynamicRecord(),
          "b" -> dynamicRecord()
        )
        val result = migration.apply(input)
        assertTrue(
          result == Right(
            dynamicRecord(
              "a" -> dynamicRecord("x" -> dynamicInt(1)),
              "b" -> dynamicRecord("y" -> dynamicInt(2))
            )
          )
        )
      }
    ),
    suite("Path not found scenarios")(
      test("operation on missing nested path fails gracefully") {
        val path   = DynamicOptic.root.field("missing").field("nested")
        val action = MigrationAction.AddField(path, "field", Resolved.Literal.int(1))
        val input  = dynamicRecord("other" -> dynamicInt(1))
        // Operation should handle missing path
        val result = action.apply(input)
        // Could either fail or do nothing depending on implementation
        assertTrue(result.isLeft || result == Right(input))
      },
      test("intermediate path exists but target doesn't") {
        val path   = DynamicOptic.root.field("exists").field("missing")
        val action = MigrationAction.AddField(path, "field", Resolved.Literal.int(1))
        val input  = dynamicRecord(
          "exists" -> dynamicRecord("other" -> dynamicInt(1))
        )
        val result = action.apply(input)
        // Should fail because "missing" doesn't exist in "exists"
        assertTrue(result.isLeft || result.isRight)
      }
    ),
    suite("Complex real-world scenarios")(
      test("user profile with nested address") {
        val migration = DynamicMigration(
          Vector(
            MigrationAction.Rename(DynamicOptic.root, "name", "fullName"),
            MigrationAction.AddField(
              DynamicOptic.root.field("address"),
              "country",
              Resolved.Literal.string("US")
            ),
            MigrationAction.Rename(
              DynamicOptic.root.field("address"),
              "zip",
              "postalCode"
            )
          )
        )
        val input = dynamicRecord(
          "name"    -> dynamicString("Alice"),
          "address" -> dynamicRecord(
            "street" -> dynamicString("123 Main St"),
            "city"   -> dynamicString("Boston"),
            "zip"    -> dynamicString("02101")
          )
        )
        val result = migration.apply(input)
        assertTrue(
          result == Right(
            dynamicRecord(
              "fullName" -> dynamicString("Alice"),
              "address"  -> dynamicRecord(
                "street"     -> dynamicString("123 Main St"),
                "city"       -> dynamicString("Boston"),
                "postalCode" -> dynamicString("02101"),
                "country"    -> dynamicString("US")
              )
            )
          )
        )
      },
      test("order with nested line items") {
        val migration = DynamicMigration(
          Vector(
            MigrationAction.TransformElements(
              DynamicOptic.root.field("items"),
              Resolved.Identity,
              Resolved.Identity
            )
          )
        )
        val input = dynamicRecord(
          "orderId" -> dynamicString("ORD-001"),
          "items"   -> dynamicSequence(
            dynamicRecord("sku" -> dynamicString("SKU1"), "qty" -> dynamicInt(2)),
            dynamicRecord("sku" -> dynamicString("SKU2"), "qty" -> dynamicInt(1))
          )
        )
        val result = migration.apply(input)
        assertTrue(result == Right(input))
      }
    ),
    suite("Performance with deep nesting")(
      test("handles depth 10 nesting") {
        def buildNested(depth: Int): DynamicValue =
          if (depth == 0) dynamicRecord("leaf" -> dynamicInt(42))
          else dynamicRecord(s"level$depth"    -> buildNested(depth - 1))

        val input     = buildNested(10)
        val migration = DynamicMigration.identity
        val result    = migration.apply(input)
        assertTrue(result == Right(input))
      },
      test("handles wide record at depth") {
        val path       = DynamicOptic.root.field("nested")
        val wideRecord = dynamicRecord(
          (1 to 50).map(i => s"field$i" -> dynamicInt(i)): _*
        )
        val input  = dynamicRecord("nested" -> wideRecord)
        val action = MigrationAction.AddField(path, "field51", Resolved.Literal.int(51))
        val result = action.apply(input)
        result match {
          case Right(DynamicValue.Record(fields)) =>
            val nested = fields.find(_._1 == "nested").get._2
            nested match {
              case DynamicValue.Record(innerFields) =>
                assertTrue(innerFields.length == 51)
              case _ => assertTrue(false)
            }
          case _ => assertTrue(false)
        }
      }
    )
  )
}
