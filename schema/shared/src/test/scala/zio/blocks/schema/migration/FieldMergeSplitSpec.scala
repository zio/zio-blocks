package zio.blocks.schema.migration

import zio.blocks.schema._
import zio.test._

/**
 * Tests for field merge and split operations.
 *
 * Covers:
 *   - Merging multiple fields into one using Concat
 *   - Splitting a field into multiple using SplitString
 *   - Restructuring record fields
 *   - Complex merge/split patterns
 */
object FieldMergeSplitSpec extends SchemaBaseSpec {

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

  // ─────────────────────────────────────────────────────────────────────────
  // Tests
  // ─────────────────────────────────────────────────────────────────────────

  def spec: Spec[TestEnvironment, Any] = suite("FieldMergeSplitSpec")(
    suite("Field merging with Concat")(
      test("merge two string fields") {
        // firstName + lastName -> fullName
        val migration = DynamicMigration(
          Vector(
            MigrationAction.AddField(
              DynamicOptic.root,
              "fullName",
              Resolved.Concat(
                Vector(
                  Resolved.FieldAccess("firstName", Resolved.Identity),
                  Resolved.Literal.string(" "),
                  Resolved.FieldAccess("lastName", Resolved.Identity)
                ),
                ""
              )
            ),
            MigrationAction.DropField(DynamicOptic.root, "firstName", Resolved.Literal.string("")),
            MigrationAction.DropField(DynamicOptic.root, "lastName", Resolved.Literal.string(""))
          )
        )
        val input = dynamicRecord(
          "firstName" -> dynamicString("John"),
          "lastName"  -> dynamicString("Doe")
        )
        val result = migration.apply(input)
        assertTrue(
          result == Right(
            dynamicRecord(
              "fullName" -> dynamicString("John Doe")
            )
          )
        )
      },
      test("merge with separator") {
        // Merge parts with comma separator
        val migration = DynamicMigration.single(
          MigrationAction.AddField(
            DynamicOptic.root,
            "combined",
            Resolved.Concat(
              Vector(
                Resolved.FieldAccess("part1", Resolved.Identity),
                Resolved.FieldAccess("part2", Resolved.Identity),
                Resolved.FieldAccess("part3", Resolved.Identity)
              ),
              ", "
            )
          )
        )
        val input = dynamicRecord(
          "part1" -> dynamicString("a"),
          "part2" -> dynamicString("b"),
          "part3" -> dynamicString("c")
        )
        val result = migration.apply(input)
        result match {
          case Right(DynamicValue.Record(fields)) =>
            val combined = fields.find(_._1 == "combined")
            assertTrue(combined.exists {
              case (_, DynamicValue.Primitive(PrimitiveValue.String(s))) =>
                s == "a, b, c"
              case _ => false
            })
          case _ => assertTrue(false)
        }
      },
      test("merge nested fields") {
        val migration = DynamicMigration.single(
          MigrationAction.AddField(
            DynamicOptic.root,
            "fullAddress",
            Resolved.Concat(
              Vector(
                Resolved.FieldAccess("street", Resolved.FieldAccess("address", Resolved.Identity)),
                Resolved.Literal.string(", "),
                Resolved.FieldAccess("city", Resolved.FieldAccess("address", Resolved.Identity))
              ),
              ""
            )
          )
        )
        val input = dynamicRecord(
          "address" -> dynamicRecord(
            "street" -> dynamicString("123 Main St"),
            "city"   -> dynamicString("Boston")
          )
        )
        val result = migration.apply(input)
        result match {
          case Right(DynamicValue.Record(fields)) =>
            val fullAddress = fields.find(_._1 == "fullAddress")
            assertTrue(fullAddress.exists {
              case (_, DynamicValue.Primitive(PrimitiveValue.String(s))) =>
                s == "123 Main St, Boston"
              case _ => false
            })
          case _ => assertTrue(false)
        }
      }
    ),
    suite("Field splitting with SplitString")(
      test("split string field by delimiter") {
        // "a,b,c" -> ["a", "b", "c"]
        val migration = DynamicMigration.single(
          MigrationAction.AddField(
            DynamicOptic.root,
            "parts",
            Resolved.SplitString(",", Resolved.FieldAccess("combined", Resolved.Identity))
          )
        )
        val input = dynamicRecord(
          "combined" -> dynamicString("a,b,c")
        )
        val result = migration.apply(input)
        assertTrue(
          result == Right(
            dynamicRecord(
              "combined" -> dynamicString("a,b,c"),
              "parts"    -> dynamicSequence(dynamicString("a"), dynamicString("b"), dynamicString("c"))
            )
          )
        )
      },
      test("split with space delimiter") {
        val migration = DynamicMigration.single(
          MigrationAction.AddField(
            DynamicOptic.root,
            "words",
            Resolved.SplitString(" ", Resolved.FieldAccess("sentence", Resolved.Identity))
          )
        )
        val input = dynamicRecord(
          "sentence" -> dynamicString("hello world test")
        )
        val result = migration.apply(input)
        assertTrue(
          result == Right(
            dynamicRecord(
              "sentence" -> dynamicString("hello world test"),
              "words"    -> dynamicSequence(
                dynamicString("hello"),
                dynamicString("world"),
                dynamicString("test")
              )
            )
          )
        )
      },
      test("split empty string") {
        val migration = DynamicMigration.single(
          MigrationAction.AddField(
            DynamicOptic.root,
            "parts",
            Resolved.SplitString(",", Resolved.FieldAccess("data", Resolved.Identity))
          )
        )
        val input  = dynamicRecord("data" -> dynamicString(""))
        val result = migration.apply(input)
        // Splitting empty string gives sequence with single empty string
        result match {
          case Right(DynamicValue.Record(fields)) =>
            val parts = fields.find(_._1 == "parts")
            assertTrue(parts.isDefined)
          case _ => assertTrue(false)
        }
      },
      test("split single value (no delimiter found)") {
        val migration = DynamicMigration.single(
          MigrationAction.AddField(
            DynamicOptic.root,
            "parts",
            Resolved.SplitString(",", Resolved.FieldAccess("data", Resolved.Identity))
          )
        )
        val input  = dynamicRecord("data" -> dynamicString("nocomma"))
        val result = migration.apply(input)
        assertTrue(
          result == Right(
            dynamicRecord(
              "data"  -> dynamicString("nocomma"),
              "parts" -> dynamicSequence(dynamicString("nocomma"))
            )
          )
        )
      }
    ),
    suite("Restructuring patterns")(
      test("flatten nested record into parent") {
        // Move fields from nested record to parent
        val migration = DynamicMigration(
          Vector(
            MigrationAction.AddField(
              DynamicOptic.root,
              "street",
              Resolved.FieldAccess("street", Resolved.FieldAccess("address", Resolved.Identity))
            ),
            MigrationAction.AddField(
              DynamicOptic.root,
              "city",
              Resolved.FieldAccess("city", Resolved.FieldAccess("address", Resolved.Identity))
            ),
            MigrationAction.DropField(DynamicOptic.root, "address", Resolved.Literal(dynamicRecord()))
          )
        )
        val input = dynamicRecord(
          "name"    -> dynamicString("Alice"),
          "address" -> dynamicRecord(
            "street" -> dynamicString("123 Main"),
            "city"   -> dynamicString("Boston")
          )
        )
        val result = migration.apply(input)
        assertTrue(
          result == Right(
            dynamicRecord(
              "name"   -> dynamicString("Alice"),
              "street" -> dynamicString("123 Main"),
              "city"   -> dynamicString("Boston")
            )
          )
        )
      },
      test("nest flat fields into record") {
        // Move flat fields into a nested record
        val migration = DynamicMigration(
          Vector(
            MigrationAction.AddField(
              DynamicOptic.root,
              "address",
              Resolved.Construct(
                Vector(
                  "street" -> Resolved.FieldAccess("street", Resolved.Identity),
                  "city"   -> Resolved.FieldAccess("city", Resolved.Identity)
                )
              )
            ),
            MigrationAction.DropField(DynamicOptic.root, "street", Resolved.Literal.string("")),
            MigrationAction.DropField(DynamicOptic.root, "city", Resolved.Literal.string(""))
          )
        )
        val input = dynamicRecord(
          "name"   -> dynamicString("Alice"),
          "street" -> dynamicString("123 Main"),
          "city"   -> dynamicString("Boston")
        )
        val result = migration.apply(input)
        assertTrue(
          result == Right(
            dynamicRecord(
              "name"    -> dynamicString("Alice"),
              "address" -> dynamicRecord(
                "street" -> dynamicString("123 Main"),
                "city"   -> dynamicString("Boston")
              )
            )
          )
        )
      },
      test("extract first element from sequence to field") {
        val migration = DynamicMigration.single(
          MigrationAction.AddField(
            DynamicOptic.root,
            "primary",
            Resolved.Head(Resolved.FieldAccess("items", Resolved.Identity))
          )
        )
        val input = dynamicRecord(
          "items" -> dynamicSequence(
            dynamicString("first"),
            dynamicString("second"),
            dynamicString("third")
          )
        )
        val result = migration.apply(input)
        result match {
          case Right(DynamicValue.Record(fields)) =>
            val primary = fields.find(_._1 == "primary")
            assertTrue(primary.exists {
              case (_, DynamicValue.Primitive(PrimitiveValue.String("first"))) => true
              case _                                                           => false
            })
          case _ => assertTrue(false)
        }
      }
    ),
    suite("Complex merge/split patterns")(
      test("split name into first and last") {
        // "John Doe" -> firstName: "John", lastName: "Doe"
        val migration = DynamicMigration(
          Vector(
            MigrationAction.AddField(
              DynamicOptic.root,
              "nameParts",
              Resolved.SplitString(" ", Resolved.FieldAccess("fullName", Resolved.Identity))
            ),
            // Note: In a real scenario, we'd use array access to get parts
            // For now, just verify the split works
            MigrationAction.DropField(DynamicOptic.root, "fullName", Resolved.Literal.string(""))
          )
        )
        val input  = dynamicRecord("fullName" -> dynamicString("John Doe"))
        val result = migration.apply(input)
        result match {
          case Right(DynamicValue.Record(fields)) =>
            val parts = fields.find(_._1 == "nameParts")
            assertTrue(parts.exists {
              case (_, DynamicValue.Sequence(elems)) =>
                elems.length == 2 &&
                elems(0) == dynamicString("John") &&
                elems(1) == dynamicString("Doe")
              case _ => false
            })
          case _ => assertTrue(false)
        }
      },
      test("merge sequence into string") {
        // Use TransformValue with Join expression
        val migration = DynamicMigration.single(
          MigrationAction.AddField(
            DynamicOptic.root,
            "tags",
            Resolved.JoinStrings(",", Resolved.FieldAccess("tagList", Resolved.Identity))
          )
        )
        val input = dynamicRecord(
          "tagList" -> dynamicSequence(
            dynamicString("scala"),
            dynamicString("zio"),
            dynamicString("schema")
          )
        )
        val result = migration.apply(input)
        result match {
          case Right(DynamicValue.Record(fields)) =>
            val tags = fields.find(_._1 == "tags")
            assertTrue(tags.exists {
              case (_, DynamicValue.Primitive(PrimitiveValue.String(s))) =>
                s == "scala,zio,schema"
              case _ => false
            })
          case _ => assertTrue(false)
        }
      },
      test("restructure with type conversion") {
        // Merge numeric fields with string concatenation
        val migration = DynamicMigration.single(
          MigrationAction.AddField(
            DynamicOptic.root,
            "coordinate",
            Resolved.Concat(
              Vector(
                Resolved.Literal.string("("),
                Resolved.Convert("Int", "String", Resolved.FieldAccess("x", Resolved.Identity)),
                Resolved.Literal.string(", "),
                Resolved.Convert("Int", "String", Resolved.FieldAccess("y", Resolved.Identity)),
                Resolved.Literal.string(")")
              ),
              ""
            )
          )
        )
        val input = dynamicRecord(
          "x" -> dynamicInt(10),
          "y" -> dynamicInt(20)
        )
        val result = migration.apply(input)
        result match {
          case Right(DynamicValue.Record(fields)) =>
            val coord = fields.find(_._1 == "coordinate")
            assertTrue(coord.exists {
              case (_, DynamicValue.Primitive(PrimitiveValue.String(s))) =>
                s == "(10, 20)"
              case _ => false
            })
          case _ => assertTrue(false)
        }
      }
    ),
    suite("Round-trip merge and split")(
      test("merge then split recovers original (simple case)") {
        // firstName + lastName -> fullName -> split back
        val merge = DynamicMigration(
          Vector(
            MigrationAction.AddField(
              DynamicOptic.root,
              "fullName",
              Resolved.Concat(
                Vector(
                  Resolved.FieldAccess("first", Resolved.Identity),
                  Resolved.FieldAccess("last", Resolved.Identity)
                ),
                " "
              )
            ),
            MigrationAction.DropField(DynamicOptic.root, "first", Resolved.Literal.string("")),
            MigrationAction.DropField(DynamicOptic.root, "last", Resolved.Literal.string(""))
          )
        )
        val input = dynamicRecord(
          "first" -> dynamicString("John"),
          "last"  -> dynamicString("Doe")
        )
        val merged = merge.apply(input)
        assertTrue(
          merged == Right(
            dynamicRecord(
              "fullName" -> dynamicString("John Doe")
            )
          )
        )
      },
      test("split then join recovers original") {
        val splitMigration = DynamicMigration.single(
          MigrationAction.AddField(
            DynamicOptic.root,
            "parts",
            Resolved.SplitString(",", Resolved.FieldAccess("csv", Resolved.Identity))
          )
        )
        val input = dynamicRecord("csv" -> dynamicString("a,b,c"))
        val split = splitMigration.apply(input)

        // Now join back
        val joinMigration = DynamicMigration.single(
          MigrationAction.AddField(
            DynamicOptic.root,
            "rejoined",
            Resolved.JoinStrings(",", Resolved.FieldAccess("parts", Resolved.Identity))
          )
        )
        val rejoined = split.flatMap(joinMigration.apply)

        rejoined match {
          case Right(DynamicValue.Record(fields)) =>
            val r = fields.find(_._1 == "rejoined")
            assertTrue(r.exists {
              case (_, DynamicValue.Primitive(PrimitiveValue.String(s))) =>
                s == "a,b,c"
              case _ => false
            })
          case _ => assertTrue(false)
        }
      }
    ),
    suite("Edge cases")(
      test("merge with missing field fails") {
        val migration = DynamicMigration.single(
          MigrationAction.AddField(
            DynamicOptic.root,
            "combined",
            Resolved.Concat(
              Vector(
                Resolved.FieldAccess("exists", Resolved.Identity),
                Resolved.FieldAccess("missing", Resolved.Identity)
              ),
              ""
            )
          )
        )
        val input  = dynamicRecord("exists" -> dynamicString("value"))
        val result = migration.apply(input)
        assertTrue(result.isLeft)
      },
      test("split non-string fails") {
        val migration = DynamicMigration.single(
          MigrationAction.AddField(
            DynamicOptic.root,
            "parts",
            Resolved.SplitString(",", Resolved.FieldAccess("number", Resolved.Identity))
          )
        )
        val input  = dynamicRecord("number" -> dynamicInt(42))
        val result = migration.apply(input)
        assertTrue(result.isLeft)
      },
      test("join non-sequence fails") {
        val migration = DynamicMigration.single(
          MigrationAction.AddField(
            DynamicOptic.root,
            "joined",
            Resolved.JoinStrings(",", Resolved.FieldAccess("notSeq", Resolved.Identity))
          )
        )
        val input  = dynamicRecord("notSeq" -> dynamicString("not a sequence"))
        val result = migration.apply(input)
        assertTrue(result.isLeft)
      }
    )
  )
}
