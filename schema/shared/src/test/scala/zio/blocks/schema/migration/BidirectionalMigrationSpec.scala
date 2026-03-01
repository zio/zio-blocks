package zio.blocks.schema.migration

import zio.blocks.schema._
import zio.test._

/**
 * Tests for bidirectional (reversible) migrations.
 *
 * Covers:
 *   - Round-trip migrations
 *   - Structural reverse properties
 *   - Semantic inverse properties
 *   - Information-preserving vs information-losing migrations
 *   - Reverse migration edge cases
 */
object BidirectionalMigrationSpec extends SchemaBaseSpec {

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

  def spec: Spec[TestEnvironment, Any] = suite("BidirectionalMigrationSpec")(
    suite("Structural reverse")(
      test("identity reverse is identity") {
        val m = DynamicMigration.identity
        assertTrue(m.reverse.isIdentity)
      },
      test("double reverse equals original") {
        val m = DynamicMigration(
          Vector(
            MigrationAction.Rename(DynamicOptic.root, "a", "b"),
            MigrationAction.AddField(DynamicOptic.root, "c", Resolved.Literal.int(1)),
            MigrationAction.DropField(DynamicOptic.root, "d", Resolved.Literal.int(2))
          )
        )
        assertTrue(m.reverse.reverse.actions == m.actions)
      },
      test("reverse of single rename") {
        val m        = DynamicMigration.single(MigrationAction.Rename(DynamicOptic.root, "old", "new"))
        val reversed = m.reverse
        reversed.actions.head match {
          case MigrationAction.Rename(_, from, to) =>
            assertTrue(from == "new" && to == "old")
          case _ => assertTrue(false)
        }
      },
      test("reverse of AddField is DropField with same default") {
        val default  = Resolved.Literal.int(42)
        val m        = DynamicMigration.single(MigrationAction.AddField(DynamicOptic.root, "field", default))
        val reversed = m.reverse
        reversed.actions.head match {
          case MigrationAction.DropField(_, name, defaultForReverse) =>
            assertTrue(name == "field" && defaultForReverse == default)
          case _ => assertTrue(false)
        }
      },
      test("reverse of DropField is AddField with same default") {
        val default  = Resolved.Literal.string("default")
        val m        = DynamicMigration.single(MigrationAction.DropField(DynamicOptic.root, "field", default))
        val reversed = m.reverse
        reversed.actions.head match {
          case MigrationAction.AddField(_, name, addDefault) =>
            assertTrue(name == "field" && addDefault == default)
          case _ => assertTrue(false)
        }
      },
      test("reverse of Mandate is Optionalize") {
        val m = DynamicMigration.single(
          MigrationAction.Mandate(DynamicOptic.root, "field", Resolved.Literal.int(0))
        )
        assertTrue(m.reverse.actions.head.isInstanceOf[MigrationAction.Optionalize])
      },
      test("reverse of Optionalize is Mandate") {
        val m = DynamicMigration.single(MigrationAction.Optionalize(DynamicOptic.root, "field"))
        assertTrue(m.reverse.actions.head.isInstanceOf[MigrationAction.Mandate])
      },
      test("reverse preserves action order (reversed)") {
        val m = DynamicMigration(
          Vector(
            MigrationAction.Rename(DynamicOptic.root, "a", "b"),
            MigrationAction.Rename(DynamicOptic.root, "c", "d"),
            MigrationAction.Rename(DynamicOptic.root, "e", "f")
          )
        )
        val reversed = m.reverse
        // Should be: f->e, d->c, b->a
        reversed.actions(0) match {
          case MigrationAction.Rename(_, "f", "e") => assertTrue(true)
          case _                                   => assertTrue(false)
        }
        reversed.actions(1) match {
          case MigrationAction.Rename(_, "d", "c") => assertTrue(true)
          case _                                   => assertTrue(false)
        }
        reversed.actions(2) match {
          case MigrationAction.Rename(_, "b", "a") => assertTrue(true)
          case _                                   => assertTrue(false)
        }
      }
    ),
    suite("Information-preserving round-trips")(
      test("rename round-trip preserves value") {
        val m         = DynamicMigration.single(MigrationAction.Rename(DynamicOptic.root, "old", "new"))
        val input     = dynamicRecord("old" -> dynamicInt(42))
        val forward   = m.apply(input)
        val roundTrip = forward.flatMap(m.reverse.apply)
        assertTrue(roundTrip == Right(input))
      },
      test("add then drop round-trip preserves record") {
        val m = DynamicMigration.single(
          MigrationAction.AddField(DynamicOptic.root, "temp", Resolved.Literal.int(0))
        )
        val input     = dynamicRecord("existing" -> dynamicString("value"))
        val forward   = m.apply(input)
        val roundTrip = forward.flatMap(m.reverse.apply)
        assertTrue(roundTrip == Right(input))
      },
      test("type conversion round-trip preserves value (Int -> Long -> Int)") {
        val m = DynamicMigration.single(
          MigrationAction.ChangeType(
            DynamicOptic.root,
            "value",
            Resolved.Convert("Int", "Long", Resolved.Identity),
            Resolved.Convert("Long", "Int", Resolved.Identity)
          )
        )
        val input     = dynamicRecord("value" -> dynamicInt(42))
        val forward   = m.apply(input)
        val roundTrip = forward.flatMap(m.reverse.apply)
        assertTrue(roundTrip == Right(input))
      },
      test("optionalize round-trip with Some value") {
        val m = DynamicMigration.single(MigrationAction.Optionalize(DynamicOptic.root, "value"))
        // Note: round-trip of optionalize leaves value wrapped in Some after mandate
        val input   = dynamicRecord("value" -> dynamicInt(42))
        val forward = m.apply(input)
        assertTrue(forward == Right(dynamicRecord("value" -> dynamicSome(dynamicInt(42)))))
        // Reverse (mandate with Fail default) should extract the Some value
        val roundTrip = forward.flatMap(m.reverse.apply)
        assertTrue(roundTrip == Right(input))
      },
      test("multiple renames round-trip") {
        val m = DynamicMigration(
          Vector(
            MigrationAction.Rename(DynamicOptic.root, "a", "b"),
            MigrationAction.Rename(DynamicOptic.root, "c", "d")
          )
        )
        val input     = dynamicRecord("a" -> dynamicInt(1), "c" -> dynamicInt(2))
        val forward   = m.apply(input)
        val roundTrip = forward.flatMap(m.reverse.apply)
        assertTrue(roundTrip == Right(input))
      },
      test("RenameCase round-trip") {
        val m = DynamicMigration.single(
          MigrationAction.RenameCase(DynamicOptic.root, "OldCase", "NewCase")
        )
        val input     = DynamicValue.Variant("OldCase", dynamicInt(42))
        val forward   = m.apply(input)
        val roundTrip = forward.flatMap(m.reverse.apply)
        assertTrue(roundTrip == Right(input))
      }
    ),
    suite("Information-losing migrations")(
      test("drop field without proper default loses information") {
        val m = DynamicMigration.single(
          MigrationAction.DropField(DynamicOptic.root, "dropped", Resolved.Literal.int(0))
        )
        val input = dynamicRecord(
          "kept"    -> dynamicString("value"),
          "dropped" -> dynamicInt(42) // This value is lost
        )
        val forward   = m.apply(input)
        val roundTrip = forward.flatMap(m.reverse.apply)
        // Round trip gives default (0) instead of original (42)
        assertTrue(
          roundTrip == Right(
            dynamicRecord(
              "kept"    -> dynamicString("value"),
              "dropped" -> dynamicInt(0)
            )
          )
        )
      },
      test("mandate with None uses default instead of original") {
        val m = DynamicMigration.single(
          MigrationAction.Mandate(DynamicOptic.root, "value", Resolved.Literal.int(99))
        )
        val input   = dynamicRecord("value" -> dynamicNone)
        val forward = m.apply(input)
        assertTrue(forward == Right(dynamicRecord("value" -> dynamicInt(99))))
        // Round trip optionalizes back, but we can't recover None
        val roundTrip = forward.flatMap(m.reverse.apply)
        assertTrue(roundTrip == Right(dynamicRecord("value" -> dynamicSome(dynamicInt(99)))))
      },
      test("reverse of optionalize fails for mandate without default") {
        val optionalize = MigrationAction.Optionalize(DynamicOptic.root, "value")
        val reversed    = optionalize.reverse // Mandate with Fail default
        val input       = dynamicRecord("value" -> dynamicNone)
        // Reverse should fail because None has no value and default is Fail
        assertTrue(reversed.apply(input).isLeft)
      }
    ),
    suite("Complex round-trips")(
      test("multi-step migration round-trip") {
        val m = DynamicMigration(
          Vector(
            MigrationAction.Rename(DynamicOptic.root, "name", "fullName"),
            MigrationAction.AddField(DynamicOptic.root, "age", Resolved.Literal.int(0)),
            MigrationAction.Rename(DynamicOptic.root, "id", "userId")
          )
        )
        val input = dynamicRecord(
          "name" -> dynamicString("Alice"),
          "id"   -> dynamicInt(123)
        )
        val forward   = m.apply(input)
        val roundTrip = forward.flatMap(m.reverse.apply)
        assertTrue(roundTrip == Right(input))
      },
      test("transformation with type changes round-trip") {
        val m = DynamicMigration(
          Vector(
            MigrationAction.ChangeType(
              DynamicOptic.root,
              "count",
              Resolved.Convert("Int", "String", Resolved.Identity),
              Resolved.Convert("String", "Int", Resolved.Identity)
            ),
            MigrationAction.Rename(DynamicOptic.root, "count", "countStr")
          )
        )
        val input   = dynamicRecord("count" -> dynamicInt(42))
        val forward = m.apply(input)
        assertTrue(forward == Right(dynamicRecord("countStr" -> dynamicString("42"))))
        val roundTrip = forward.flatMap(m.reverse.apply)
        assertTrue(roundTrip == Right(input))
      }
    ),
    suite("Composed migration reverse")(
      test("reverse of (m1 ++ m2) equals (m2.reverse ++ m1.reverse)") {
        val m1              = DynamicMigration.single(MigrationAction.Rename(DynamicOptic.root, "a", "b"))
        val m2              = DynamicMigration.single(MigrationAction.Rename(DynamicOptic.root, "c", "d"))
        val composed        = m1 ++ m2
        val composedReverse = composed.reverse
        val manualReverse   = m2.reverse ++ m1.reverse

        val input    = dynamicRecord("a" -> dynamicInt(1), "c" -> dynamicInt(2))
        val forward  = composed.apply(input)
        val reverse1 = forward.flatMap(composedReverse.apply)
        val reverse2 = forward.flatMap(manualReverse.apply)
        assertTrue(reverse1 == reverse2)
      },
      test("(m ++ m.reverse) applied to value returns value (for renames)") {
        val m         = DynamicMigration.single(MigrationAction.Rename(DynamicOptic.root, "old", "new"))
        val roundTrip = m ++ m.reverse
        val input     = dynamicRecord("old" -> dynamicInt(42))
        val result    = roundTrip.apply(input)
        assertTrue(result == Right(input))
      }
    ),
    suite("Edge cases")(
      test("empty migration reverse") {
        val m = DynamicMigration(Vector.empty)
        assertTrue(m.reverse.actions.isEmpty)
      },
      test("reverse of very long migration chain") {
        val actions =
          (1 to 100).map(i => MigrationAction.Rename(DynamicOptic.root, s"field$i", s"field${i}_renamed")).toVector
        val m        = DynamicMigration(actions)
        val reversed = m.reverse
        assertTrue(reversed.actions.length == 100)
        reversed.actions.head match {
          case MigrationAction.Rename(_, from, to) =>
            assertTrue(from == "field100_renamed" && to == "field100")
          case _ => assertTrue(false)
        }
      },
      test("reverse preserves path information") {
        val path     = DynamicOptic.root.field("nested").field("deep")
        val m        = DynamicMigration.single(MigrationAction.Rename(path, "a", "b"))
        val reversed = m.reverse
        reversed.actions.head match {
          case MigrationAction.Rename(reversedPath, _, _) =>
            assertTrue(reversedPath == path)
          case _ => assertTrue(false)
        }
      }
    )
  )
}
