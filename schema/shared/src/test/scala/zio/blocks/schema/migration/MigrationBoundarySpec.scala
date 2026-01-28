package zio.blocks.schema.migration

import zio.blocks.schema._
import zio.test._

/**
 * Tests for edge cases and boundary conditions in migrations.
 *
 * Covers:
 *   - Empty structures
 *   - Large structures
 *   - Special characters in names
 *   - Unicode handling
 *   - Numeric edge cases
 *   - Deeply nested structures
 *   - Very long field names
 *   - Many fields
 */
object MigrationBoundarySpec extends SchemaBaseSpec {

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

  def dynamicSequence(elements: DynamicValue*): DynamicValue =
    DynamicValue.Sequence(elements: _*)

  def dynamicVariant(caseName: String, value: DynamicValue): DynamicValue =
    DynamicValue.Variant(caseName, value)

  // ─────────────────────────────────────────────────────────────────────────
  // Tests
  // ─────────────────────────────────────────────────────────────────────────

  def spec: Spec[TestEnvironment, Any] = suite("MigrationBoundarySpec")(
    suite("Empty structures")(
      test("migrate empty record") {
        val migration = DynamicMigration.single(
          MigrationAction.AddField(DynamicOptic.root, "field", Resolved.Literal.int(1))
        )
        val input = dynamicRecord()
        assertTrue(migration.apply(input) == Right(dynamicRecord("field" -> dynamicInt(1))))
      },
      test("drop all fields from record") {
        val migration = DynamicMigration(
          Vector(
            MigrationAction.DropField(DynamicOptic.root, "a", Resolved.Literal.int(0)),
            MigrationAction.DropField(DynamicOptic.root, "b", Resolved.Literal.int(0))
          )
        )
        val input = dynamicRecord("a" -> dynamicInt(1), "b" -> dynamicInt(2))
        assertTrue(migration.apply(input) == Right(dynamicRecord()))
      },
      test("transform empty sequence") {
        val migration = DynamicMigration.single(
          MigrationAction.TransformElements(
            DynamicOptic.root,
            Resolved.Convert("Int", "String", Resolved.Identity),
            Resolved.Identity
          )
        )
        val input = dynamicSequence()
        assertTrue(migration.apply(input) == Right(dynamicSequence()))
      },
      test("identity on empty record") {
        val input = dynamicRecord()
        assertTrue(DynamicMigration.identity.apply(input) == Right(input))
      }
    ),
    suite("Large structures")(
      test("record with many fields") {
        val numFields = 100
        val fields    = (1 to numFields).map(i => s"field$i" -> dynamicInt(i))
        val input     = dynamicRecord(fields: _*)
        val migration = DynamicMigration.single(
          MigrationAction.Rename(DynamicOptic.root, "field50", "renamedField")
        )
        val result = migration.apply(input)
        result match {
          case Right(DynamicValue.Record(resultFields)) =>
            assertTrue(resultFields.exists(_._1 == "renamedField"))
            assertTrue(!resultFields.exists(_._1 == "field50"))
            assertTrue(resultFields.length == numFields)
          case _ => assertTrue(false)
        }
      },
      test("sequence with many elements") {
        val numElements = 1000
        val elements    = (1 to numElements).map(dynamicInt)
        val input       = dynamicSequence(elements: _*)
        val migration   = DynamicMigration.single(
          MigrationAction.TransformElements(
            DynamicOptic.root,
            Resolved.Identity,
            Resolved.Identity
          )
        )
        val result = migration.apply(input)
        result match {
          case Right(DynamicValue.Sequence(resultElements)) =>
            assertTrue(resultElements.length == numElements)
          case _ => assertTrue(false)
        }
      },
      test("many actions in migration") {
        val numActions = 50
        val actions    =
          (1 to numActions).map(i => MigrationAction.AddField(DynamicOptic.root, s"field$i", Resolved.Literal.int(i)))
        val migration = DynamicMigration(actions.toVector)
        val input     = dynamicRecord()
        val result    = migration.apply(input)
        result match {
          case Right(DynamicValue.Record(fields)) =>
            assertTrue(fields.length == numActions)
          case _ => assertTrue(false)
        }
      }
    ),
    suite("Special characters in names")(
      test("field name with spaces") {
        val migration = DynamicMigration.single(
          MigrationAction.Rename(DynamicOptic.root, "field with spaces", "newField")
        )
        val input = dynamicRecord("field with spaces" -> dynamicInt(1))
        assertTrue(migration.apply(input) == Right(dynamicRecord("newField" -> dynamicInt(1))))
      },
      test("field name with special characters") {
        val migration = DynamicMigration.single(
          MigrationAction.Rename(DynamicOptic.root, "field-with-dashes", "field_with_underscores")
        )
        val input = dynamicRecord("field-with-dashes" -> dynamicInt(1))
        assertTrue(migration.apply(input) == Right(dynamicRecord("field_with_underscores" -> dynamicInt(1))))
      },
      test("field name with dots") {
        val migration = DynamicMigration.single(
          MigrationAction.Rename(DynamicOptic.root, "field.with.dots", "newField")
        )
        val input = dynamicRecord("field.with.dots" -> dynamicInt(1))
        assertTrue(migration.apply(input) == Right(dynamicRecord("newField" -> dynamicInt(1))))
      },
      test("field name starting with number") {
        val migration = DynamicMigration.single(
          MigrationAction.Rename(DynamicOptic.root, "123field", "newField")
        )
        val input = dynamicRecord("123field" -> dynamicInt(1))
        assertTrue(migration.apply(input) == Right(dynamicRecord("newField" -> dynamicInt(1))))
      }
    ),
    suite("Unicode handling")(
      test("field name with unicode characters") {
        val migration = DynamicMigration.single(
          MigrationAction.Rename(DynamicOptic.root, "字段", "field")
        )
        val input = dynamicRecord("字段" -> dynamicInt(1))
        assertTrue(migration.apply(input) == Right(dynamicRecord("field" -> dynamicInt(1))))
      },
      test("field name with emoji") {
        val migration = DynamicMigration.single(
          MigrationAction.Rename(DynamicOptic.root, "field🎉", "field")
        )
        val input = dynamicRecord("field🎉" -> dynamicInt(1))
        assertTrue(migration.apply(input) == Right(dynamicRecord("field" -> dynamicInt(1))))
      },
      test("unicode string value") {
        val migration = DynamicMigration.single(
          MigrationAction.AddField(DynamicOptic.root, "greeting", Resolved.Literal.string("你好世界"))
        )
        val input  = dynamicRecord()
        val result = migration.apply(input)
        assertTrue(result == Right(dynamicRecord("greeting" -> dynamicString("你好世界"))))
      },
      test("arabic field name") {
        val migration = DynamicMigration.single(
          MigrationAction.Rename(DynamicOptic.root, "حقل", "field")
        )
        val input = dynamicRecord("حقل" -> dynamicInt(1))
        assertTrue(migration.apply(input) == Right(dynamicRecord("field" -> dynamicInt(1))))
      }
    ),
    suite("Numeric edge cases")(
      test("Int.MaxValue") {
        val migration = DynamicMigration.single(
          MigrationAction.AddField(DynamicOptic.root, "max", Resolved.Literal.int(Int.MaxValue))
        )
        val input = dynamicRecord()
        assertTrue(migration.apply(input) == Right(dynamicRecord("max" -> dynamicInt(Int.MaxValue))))
      },
      test("Int.MinValue") {
        val migration = DynamicMigration.single(
          MigrationAction.AddField(DynamicOptic.root, "min", Resolved.Literal.int(Int.MinValue))
        )
        val input = dynamicRecord()
        assertTrue(migration.apply(input) == Right(dynamicRecord("min" -> dynamicInt(Int.MinValue))))
      },
      test("Long.MaxValue") {
        val migration = DynamicMigration.single(
          MigrationAction.AddField(DynamicOptic.root, "max", Resolved.Literal.long(Long.MaxValue))
        )
        val input = dynamicRecord()
        assertTrue(migration.apply(input) == Right(dynamicRecord("max" -> dynamicLong(Long.MaxValue))))
      },
      test("zero values") {
        val migration = DynamicMigration(
          Vector(
            MigrationAction.AddField(DynamicOptic.root, "zeroInt", Resolved.Literal.int(0)),
            MigrationAction.AddField(DynamicOptic.root, "zeroDouble", Resolved.Literal.double(0.0))
          )
        )
        val input  = dynamicRecord()
        val result = migration.apply(input)
        assertTrue(result.isRight)
      },
      test("negative zero double") {
        val migration = DynamicMigration.single(
          MigrationAction.AddField(DynamicOptic.root, "negZero", Resolved.Literal.double(-0.0))
        )
        val input = dynamicRecord()
        assertTrue(migration.apply(input).isRight)
      },
      test("Double.NaN") {
        val migration = DynamicMigration.single(
          MigrationAction.AddField(DynamicOptic.root, "nan", Resolved.Literal.double(Double.NaN))
        )
        val input = dynamicRecord()
        assertTrue(migration.apply(input).isRight)
      },
      test("Double.PositiveInfinity") {
        val migration = DynamicMigration.single(
          MigrationAction.AddField(DynamicOptic.root, "inf", Resolved.Literal.double(Double.PositiveInfinity))
        )
        val input = dynamicRecord()
        assertTrue(migration.apply(input).isRight)
      }
    ),
    suite("Deep nesting")(
      test("very deeply nested path - 10 levels") {
        def deepRecord(depth: Int): DynamicValue =
          if (depth <= 0) dynamicRecord("leaf" -> dynamicInt(42))
          else dynamicRecord("nested"          -> deepRecord(depth - 1))

        val migration = DynamicMigration.identity
        val input     = deepRecord(10)
        assertTrue(migration.apply(input) == Right(input))
      },
      test("modify at deep path") {
        val input = dynamicRecord(
          "l1" -> dynamicRecord(
            "l2" -> dynamicRecord(
              "l3" -> dynamicRecord(
                "l4" -> dynamicRecord(
                  "l5" -> dynamicRecord("target" -> dynamicInt(1))
                )
              )
            )
          )
        )
        val migration = DynamicMigration.single(
          MigrationAction.Rename(
            DynamicOptic.root.field("l1").field("l2").field("l3").field("l4").field("l5"),
            "target",
            "renamed"
          )
        )
        val result = migration.apply(input)
        assertTrue(result.isRight)
      }
    ),
    suite("Very long field names")(
      test("field name with 100 characters") {
        val longName  = "a" * 100
        val migration = DynamicMigration.single(
          MigrationAction.Rename(DynamicOptic.root, longName, "short")
        )
        val input = dynamicRecord(longName -> dynamicInt(1))
        assertTrue(migration.apply(input) == Right(dynamicRecord("short" -> dynamicInt(1))))
      },
      test("field name with 1000 characters") {
        val longName  = "field" + ("x" * 995)
        val migration = DynamicMigration.single(
          MigrationAction.AddField(DynamicOptic.root, longName, Resolved.Literal.int(1))
        )
        val input  = dynamicRecord()
        val result = migration.apply(input)
        result match {
          case Right(DynamicValue.Record(fields)) =>
            assertTrue(fields.exists(_._1 == longName))
          case _ => assertTrue(false)
        }
      }
    ),
    suite("String edge cases")(
      test("empty string field name") {
        val migration = DynamicMigration.single(
          MigrationAction.Rename(DynamicOptic.root, "", "newField")
        )
        val input = dynamicRecord("" -> dynamicInt(1))
        assertTrue(migration.apply(input) == Right(dynamicRecord("newField" -> dynamicInt(1))))
      },
      test("empty string value") {
        val migration = DynamicMigration.single(
          MigrationAction.AddField(DynamicOptic.root, "empty", Resolved.Literal.string(""))
        )
        val input = dynamicRecord()
        assertTrue(migration.apply(input) == Right(dynamicRecord("empty" -> dynamicString(""))))
      },
      test("very long string value") {
        val longString = "x" * 10000
        val migration  = DynamicMigration.single(
          MigrationAction.AddField(DynamicOptic.root, "long", Resolved.Literal.string(longString))
        )
        val input  = dynamicRecord()
        val result = migration.apply(input)
        assertTrue(result == Right(dynamicRecord("long" -> dynamicString(longString))))
      },
      test("string with newlines") {
        val multiline = "line1\nline2\nline3"
        val migration = DynamicMigration.single(
          MigrationAction.AddField(DynamicOptic.root, "multi", Resolved.Literal.string(multiline))
        )
        val input = dynamicRecord()
        assertTrue(migration.apply(input) == Right(dynamicRecord("multi" -> dynamicString(multiline))))
      },
      test("string with tabs") {
        val tabbed    = "col1\tcol2\tcol3"
        val migration = DynamicMigration.single(
          MigrationAction.AddField(DynamicOptic.root, "tabbed", Resolved.Literal.string(tabbed))
        )
        val input = dynamicRecord()
        assertTrue(migration.apply(input) == Right(dynamicRecord("tabbed" -> dynamicString(tabbed))))
      },
      test("string with null character") {
        val withNull  = "before\u0000after"
        val migration = DynamicMigration.single(
          MigrationAction.AddField(DynamicOptic.root, "null", Resolved.Literal.string(withNull))
        )
        val input = dynamicRecord()
        assertTrue(migration.apply(input) == Right(dynamicRecord("null" -> dynamicString(withNull))))
      }
    ),
    suite("Case sensitivity")(
      test("rename preserves case sensitivity") {
        val migration = DynamicMigration.single(
          MigrationAction.Rename(DynamicOptic.root, "Field", "field")
        )
        val input = dynamicRecord("Field" -> dynamicInt(1))
        assertTrue(migration.apply(input) == Right(dynamicRecord("field" -> dynamicInt(1))))
      },
      test("different case fields are different") {
        val migration = DynamicMigration.single(
          MigrationAction.Rename(DynamicOptic.root, "field", "Field")
        )
        val input  = dynamicRecord("field" -> dynamicInt(1), "Field" -> dynamicInt(2))
        val result = migration.apply(input)
        result match {
          case Right(DynamicValue.Record(fields)) =>
            // Both "Field" entries should exist (one original, one renamed)
            assertTrue(fields.exists(_._1 == "Field"))
          case _ => assertTrue(false)
        }
      }
    ),
    suite("Composition edge cases")(
      test("compose with self") {
        val m        = DynamicMigration.single(MigrationAction.Rename(DynamicOptic.root, "a", "b"))
        val composed = m ++ m
        val input    = dynamicRecord("a" -> dynamicInt(1))
        // First: a->b, Second: b stays b (no 'a' to rename)
        val result = composed.apply(input)
        assertTrue(result == Right(dynamicRecord("b" -> dynamicInt(1))))
      },
      test("compose with reverse") {
        val m         = DynamicMigration.single(MigrationAction.Rename(DynamicOptic.root, "a", "b"))
        val roundTrip = m ++ m.reverse
        val input     = dynamicRecord("a" -> dynamicInt(1))
        assertTrue(roundTrip.apply(input) == Right(input))
      },
      test("compose many migrations") {
        val migrations = (1 to 100).map(i =>
          DynamicMigration.single(MigrationAction.AddField(DynamicOptic.root, s"f$i", Resolved.Literal.int(i)))
        )
        val composed = migrations.foldLeft(DynamicMigration.identity)(_ ++ _)
        val input    = dynamicRecord()
        val result   = composed.apply(input)
        result match {
          case Right(DynamicValue.Record(fields)) =>
            assertTrue(fields.length == 100)
          case _ => assertTrue(false)
        }
      }
    ),
    suite("Variant edge cases")(
      test("variant with empty case name") {
        val migration = DynamicMigration.single(
          MigrationAction.RenameCase(DynamicOptic.root, "", "NonEmpty")
        )
        val input = dynamicVariant("", dynamicInt(1))
        assertTrue(migration.apply(input) == Right(dynamicVariant("NonEmpty", dynamicInt(1))))
      },
      test("variant with very long case name") {
        val longCase  = "Case" + ("X" * 100)
        val migration = DynamicMigration.single(
          MigrationAction.RenameCase(DynamicOptic.root, longCase, "Short")
        )
        val input = dynamicVariant(longCase, dynamicInt(1))
        assertTrue(migration.apply(input) == Right(dynamicVariant("Short", dynamicInt(1))))
      }
    )
  )
}
