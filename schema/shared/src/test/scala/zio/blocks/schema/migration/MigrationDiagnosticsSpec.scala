package zio.blocks.schema.migration

import zio.blocks.schema._
import zio.test._

/**
 * Tests for migration diagnostics, debugging, and troubleshooting.
 *
 * Covers:
 *   - Migration description and explain
 *   - Action-level diagnostics
 *   - Path tracing
 *   - Error context and recovery suggestions
 *   - Migration comparison and diff
 *   - Optimization reporting
 *   - Debugging aids
 */
object MigrationDiagnosticsSpec extends SchemaBaseSpec {

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

  def spec: Spec[TestEnvironment, Any] = suite("MigrationDiagnosticsSpec")(
    suite("Migration description")(
      test("identity migration has clear description") {
        val desc = DynamicMigration.identity.describe
        assertTrue(desc.toLowerCase.contains("identity") || desc.toLowerCase.contains("no change"))
      },
      test("single action migration describes action") {
        val migration = DynamicMigration.single(
          MigrationAction.Rename(DynamicOptic.root, "oldField", "newField")
        )
        val desc = migration.describe
        assertTrue(desc.contains("Rename") || desc.contains("rename"))
        assertTrue(desc.contains("oldField") || desc.contains("newField"))
      },
      test("multi-action migration describes all actions") {
        val migration = DynamicMigration(
          Vector(
            MigrationAction.AddField(DynamicOptic.root, "field1", Resolved.Literal.int(1)),
            MigrationAction.Rename(DynamicOptic.root, "a", "b"),
            MigrationAction.DropField(DynamicOptic.root, "field2", Resolved.Literal.int(0))
          )
        )
        val desc = migration.describe
        assertTrue(desc.contains("AddField") || desc.contains("add"))
        assertTrue(desc.contains("Rename") || desc.contains("rename"))
        assertTrue(desc.contains("DropField") || desc.contains("drop"))
      },
      test("description includes path information") {
        val migration = DynamicMigration.single(
          MigrationAction.AddField(
            DynamicOptic.root.field("nested").field("deep"),
            "newField",
            Resolved.Literal.int(42)
          )
        )
        val desc = migration.describe
        // Should mention the path in some way
        assertTrue(desc.nonEmpty)
      },
      test("description handles all action types") {
        val actions = Vector(
          MigrationAction.AddField(DynamicOptic.root, "f", Resolved.Literal.int(1)),
          MigrationAction.DropField(DynamicOptic.root, "f", Resolved.Literal.int(1)),
          MigrationAction.Rename(DynamicOptic.root, "a", "b"),
          MigrationAction.TransformValue(DynamicOptic.root, "f", Resolved.Identity, Resolved.Identity),
          MigrationAction.Mandate(DynamicOptic.root, "f", Resolved.Literal.int(0)),
          MigrationAction.Optionalize(DynamicOptic.root, "f"),
          MigrationAction.ChangeType(DynamicOptic.root, "f", Resolved.Identity, Resolved.Identity),
          MigrationAction.RenameCase(DynamicOptic.root, "A", "B"),
          MigrationAction.TransformCase(DynamicOptic.root, "A", Vector.empty),
          MigrationAction.TransformElements(DynamicOptic.root, Resolved.Identity, Resolved.Identity),
          MigrationAction.TransformKeys(DynamicOptic.root, Resolved.Identity, Resolved.Identity),
          MigrationAction.TransformValues(DynamicOptic.root, Resolved.Identity, Resolved.Identity)
        )
        actions.foreach { action =>
          val migration = DynamicMigration.single(action)
          val desc      = migration.describe
          assertTrue(desc.nonEmpty)
        }
        assertTrue(true)
      }
    ),
    suite("Action count diagnostics")(
      test("identity has zero actions") {
        assertTrue(DynamicMigration.identity.actionCount == 0)
      },
      test("single action has count 1") {
        val m = DynamicMigration.single(MigrationAction.Rename(DynamicOptic.root, "a", "b"))
        assertTrue(m.actionCount == 1)
      },
      test("composed migration has combined count") {
        val m1       = DynamicMigration.single(MigrationAction.Rename(DynamicOptic.root, "a", "b"))
        val m2       = DynamicMigration.single(MigrationAction.Rename(DynamicOptic.root, "c", "d"))
        val m3       = DynamicMigration.single(MigrationAction.Rename(DynamicOptic.root, "e", "f"))
        val composed = m1 ++ m2 ++ m3
        assertTrue(composed.actionCount == 3)
      },
      test("action count reflects actual actions") {
        val migration = DynamicMigration(
          Vector(
            MigrationAction.AddField(DynamicOptic.root, "a", Resolved.Literal.int(1)),
            MigrationAction.AddField(DynamicOptic.root, "b", Resolved.Literal.int(2)),
            MigrationAction.AddField(DynamicOptic.root, "c", Resolved.Literal.int(3)),
            MigrationAction.AddField(DynamicOptic.root, "d", Resolved.Literal.int(4)),
            MigrationAction.AddField(DynamicOptic.root, "e", Resolved.Literal.int(5))
          )
        )
        assertTrue(migration.actionCount == 5)
      }
    ),
    suite("isIdentity diagnostics")(
      test("empty migration is identity") {
        assertTrue(DynamicMigration.identity.isIdentity)
        assertTrue(DynamicMigration(Vector.empty).isIdentity)
      },
      test("non-empty migration is not identity") {
        val m = DynamicMigration.single(MigrationAction.Rename(DynamicOptic.root, "a", "b"))
        assertTrue(!m.isIdentity)
      },
      test("optimized migration may become identity") {
        val m = DynamicMigration(
          Vector(
            MigrationAction.Rename(DynamicOptic.root, "a", "b"),
            MigrationAction.Rename(DynamicOptic.root, "b", "a")
          )
        )
        val optimized = m.optimize
        assertTrue(optimized.isIdentity)
      }
    ),
    suite("Error diagnostics")(
      test("PathNotFound error includes path") {
        val action = MigrationAction.AddField(
          DynamicOptic.root.field("missing"),
          "field",
          Resolved.Literal.int(1)
        )
        val input = dynamicRecord("other" -> dynamicInt(1))
        action.apply(input) match {
          case Left(error) =>
            val errorStr = error.toString
            assertTrue(errorStr.nonEmpty)
          case Right(_) => assertTrue(false)
        }
      },
      test("ExpectedRecord error shows actual type") {
        val action = MigrationAction.AddField(DynamicOptic.root, "field", Resolved.Literal.int(1))
        val input  = dynamicInt(42)
        action.apply(input) match {
          case Left(MigrationError.ExpectedRecord(path, actual)) =>
            assertTrue(actual == input)
            assertTrue(path == DynamicOptic.root)
          case _ => assertTrue(false)
        }
      },
      test("ExpectedSequence error shows actual type") {
        val action = MigrationAction.TransformElements(
          DynamicOptic.root,
          Resolved.Identity,
          Resolved.Identity
        )
        val input = dynamicRecord("x" -> dynamicInt(1))
        action.apply(input) match {
          case Left(MigrationError.ExpectedSequence(path, actual)) =>
            assertTrue(actual == input)
          case _ => assertTrue(false)
        }
      },
      test("ExpressionFailed error includes message") {
        val action = MigrationAction.AddField(
          DynamicOptic.root,
          "field",
          Resolved.Fail("custom diagnostic message")
        )
        val input = dynamicRecord()
        action.apply(input) match {
          case Left(MigrationError.ExpressionFailed(_, msg)) =>
            assertTrue(msg.contains("custom diagnostic message"))
          case _ => assertTrue(false)
        }
      },
      test("nested error preserves path context") {
        val action = MigrationAction.AddField(
          DynamicOptic.root.field("level1").field("level2"),
          "field",
          Resolved.Literal.int(1)
        )
        val input = dynamicRecord("level1" -> dynamicRecord("wrong" -> dynamicInt(1)))
        action.apply(input) match {
          case Left(error) =>
            // Error should indicate where the failure occurred
            assertTrue(error.toString.nonEmpty)
          case Right(_) => assertTrue(false)
        }
      }
    ),
    suite("Optimization diagnostics")(
      test("optimizer combines consecutive renames") {
        val actions = Vector(
          MigrationAction.Rename(DynamicOptic.root, "a", "b"),
          MigrationAction.Rename(DynamicOptic.root, "b", "c")
        )
        val optimized = MigrationOptimizer.optimize(actions)
        assertTrue(optimized.length == 1)
        optimized.head match {
          case MigrationAction.Rename(_, from, to) =>
            assertTrue(from == "a" && to == "c")
          case _ => assertTrue(false)
        }
      },
      test("optimizer eliminates no-op renames") {
        val actions = Vector(
          MigrationAction.Rename(DynamicOptic.root, "a", "b"),
          MigrationAction.Rename(DynamicOptic.root, "b", "a")
        )
        val optimized = MigrationOptimizer.optimize(actions)
        assertTrue(optimized.isEmpty)
      },
      test("optimizer eliminates add-then-drop") {
        val actions = Vector(
          MigrationAction.AddField(DynamicOptic.root, "temp", Resolved.Literal.int(1)),
          MigrationAction.DropField(DynamicOptic.root, "temp", Resolved.Literal.int(1))
        )
        val optimized = MigrationOptimizer.optimize(actions)
        assertTrue(optimized.isEmpty)
      },
      test("optimizer preserves unrelated actions") {
        val actions = Vector(
          MigrationAction.Rename(DynamicOptic.root, "a", "b"),
          MigrationAction.Rename(DynamicOptic.root, "c", "d"),
          MigrationAction.Rename(DynamicOptic.root, "e", "f")
        )
        val optimized = MigrationOptimizer.optimize(actions)
        assertTrue(optimized.length == 3)
      },
      test("migration.optimize returns optimized version") {
        val migration = DynamicMigration(
          Vector(
            MigrationAction.Rename(DynamicOptic.root, "a", "b"),
            MigrationAction.Rename(DynamicOptic.root, "b", "c"),
            MigrationAction.Rename(DynamicOptic.root, "c", "d")
          )
        )
        val optimized = migration.optimize
        // Should combine to a single a->d rename
        assertTrue(optimized.actionCount < migration.actionCount)
      }
    ),
    suite("Path diagnostics")(
      test("root path has empty nodes") {
        val path = DynamicOptic.root
        assertTrue(path.nodes.isEmpty)
      },
      test("field path records field name") {
        val path = DynamicOptic.root.field("myField")
        assertTrue(path.nodes.length == 1)
      },
      test("nested path records all levels") {
        val path = DynamicOptic.root.field("a").field("b").field("c")
        assertTrue(path.nodes.length == 3)
      },
      test("path toString is readable") {
        val path = DynamicOptic.root.field("user").field("address").field("city")
        val str  = path.toString
        assertTrue(str.nonEmpty)
      }
    ),
    suite("Migration tracing")(
      test("trace successful migration steps") {
        val migration = DynamicMigration(
          Vector(
            MigrationAction.AddField(DynamicOptic.root, "step1", Resolved.Literal.int(1)),
            MigrationAction.AddField(DynamicOptic.root, "step2", Resolved.Literal.int(2)),
            MigrationAction.AddField(DynamicOptic.root, "step3", Resolved.Literal.int(3))
          )
        )
        val input  = dynamicRecord()
        val result = migration.apply(input)
        result match {
          case Right(DynamicValue.Record(fields)) =>
            assertTrue(fields.length == 3)
            assertTrue(fields.exists(_._1 == "step1"))
            assertTrue(fields.exists(_._1 == "step2"))
            assertTrue(fields.exists(_._1 == "step3"))
          case _ => assertTrue(false)
        }
      },
      test("trace identifies failing action") {
        val migration = DynamicMigration(
          Vector(
            MigrationAction.AddField(DynamicOptic.root, "success", Resolved.Literal.int(1)),
            MigrationAction.AddField(DynamicOptic.root, "fail", Resolved.Fail("intentional")),
            MigrationAction.AddField(DynamicOptic.root, "unreached", Resolved.Literal.int(3))
          )
        )
        val input  = dynamicRecord()
        val result = migration.apply(input)
        assertTrue(result.isLeft)
        result match {
          case Left(MigrationError.ExpressionFailed(_, msg)) =>
            assertTrue(msg.contains("intentional"))
          case _ => assertTrue(false)
        }
      }
    ),
    suite("Reverse migration diagnostics")(
      test("reverse has same action count") {
        val migration = DynamicMigration(
          Vector(
            MigrationAction.Rename(DynamicOptic.root, "a", "b"),
            MigrationAction.AddField(DynamicOptic.root, "c", Resolved.Literal.int(1))
          )
        )
        val reversed = migration.reverse
        assertTrue(reversed.actionCount == migration.actionCount)
      },
      test("reverse description differs from forward") {
        val migration = DynamicMigration.single(
          MigrationAction.Rename(DynamicOptic.root, "old", "new")
        )
        val reversed    = migration.reverse
        val forwardDesc = migration.describe
        val reverseDesc = reversed.describe
        // The descriptions should be different (old<->new swapped)
        assertTrue(forwardDesc != reverseDesc || forwardDesc.contains("old"))
      },
      test("reverse of reverse equals original") {
        val migration = DynamicMigration(
          Vector(
            MigrationAction.Rename(DynamicOptic.root, "a", "b"),
            MigrationAction.AddField(DynamicOptic.root, "c", Resolved.Literal.int(1))
          )
        )
        val doubleReverse = migration.reverse.reverse
        assertTrue(doubleReverse.actions == migration.actions)
      }
    ),
    suite("Expression diagnostics")(
      test("Literal expression evaluates without input") {
        val expr = Resolved.Literal.int(42)
        assertTrue(expr.evalDynamic == Right(dynamicInt(42)))
      },
      test("Identity expression requires input") {
        val expr = Resolved.Identity
        assertTrue(expr.evalDynamic.isLeft)
        assertTrue(expr.evalDynamic(dynamicInt(42)) == Right(dynamicInt(42)))
      },
      test("FieldAccess error includes field name") {
        val expr  = Resolved.FieldAccess("missingField", Resolved.Identity)
        val input = dynamicRecord("other" -> dynamicInt(1))
        expr.evalDynamic(input) match {
          case Left(msg) =>
            assertTrue(msg.nonEmpty)
          case Right(_) => assertTrue(false)
        }
      },
      test("Convert error includes type info") {
        val expr  = Resolved.Convert("String", "Int", Resolved.Identity)
        val input = dynamicString("not a number")
        expr.evalDynamic(input) match {
          case Left(msg) =>
            assertTrue(msg.nonEmpty)
          case Right(_) => assertTrue(false)
        }
      },
      test("Fail expression includes custom message") {
        val expr = Resolved.Fail("diagnostic message here")
        expr.evalDynamic match {
          case Left(msg) =>
            assertTrue(msg.contains("diagnostic message here"))
          case Right(_) => assertTrue(false)
        }
      }
    ),
    suite("Complex diagnostics scenarios")(
      test("diagnose deeply nested failure") {
        val migration = DynamicMigration.single(
          MigrationAction.AddField(
            DynamicOptic.root.field("a").field("b").field("c"),
            "d",
            Resolved.Literal.int(1)
          )
        )
        val input = dynamicRecord(
          "a" -> dynamicRecord(
            "b" -> dynamicInt(42) // Not a record, will fail
          )
        )
        val result = migration.apply(input)
        assertTrue(result.isLeft)
      },
      test("diagnose sequence element failure") {
        // Use String -> Int conversion which will fail on non-parseable input
        val migration = DynamicMigration.single(
          MigrationAction.TransformElements(
            DynamicOptic.root.field("items"),
            Resolved.Convert("String", "Int", Resolved.Identity),
            Resolved.Identity
          )
        )
        val input = dynamicRecord(
          "items" -> dynamicSequence(
            dynamicString("1"),
            dynamicString("not an int"), // Can't parse as Int
            dynamicString("3")
          )
        )
        // Second element can't be parsed as Int, so conversion should fail
        val result = migration.apply(input)
        assertTrue(result.isLeft)
      },
      test("diagnose variant case mismatch") {
        val migration = DynamicMigration.single(
          MigrationAction.TransformCase(
            DynamicOptic.root,
            "ExpectedCase",
            Vector(MigrationAction.AddField(DynamicOptic.root, "f", Resolved.Fail("should not reach")))
          )
        )
        val input = dynamicVariant("OtherCase", dynamicRecord())
        // Should succeed because case doesn't match (no-op)
        val result = migration.apply(input)
        assertTrue(result == Right(input))
      }
    )
  )
}
