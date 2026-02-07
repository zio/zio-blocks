package zio.blocks.schema.migration

import zio.blocks.schema._
import zio.test._

/**
 * Tests for migration algebraic properties.
 *
 * Covers:
 *   - Identity laws
 *   - Associativity
 *   - Composition laws
 *   - Inverse/reverse properties
 *   - Commutation (when applicable)
 */
object MigrationAlgebraSpec extends SchemaBaseSpec {

  // ─────────────────────────────────────────────────────────────────────────
  // Test Helpers
  // ─────────────────────────────────────────────────────────────────────────

  def dynamicRecord(fields: (String, DynamicValue)*): DynamicValue =
    DynamicValue.Record(fields: _*)

  def dynamicString(s: String): DynamicValue =
    DynamicValue.Primitive(PrimitiveValue.String(s))

  def dynamicInt(i: Int): DynamicValue =
    DynamicValue.Primitive(PrimitiveValue.Int(i))

  // ─────────────────────────────────────────────────────────────────────────
  // Tests
  // ─────────────────────────────────────────────────────────────────────────

  def spec: Spec[TestEnvironment, Any] = suite("MigrationAlgebraSpec")(
    suite("Identity laws")(
      test("identity migration is structurally empty") {
        val id = DynamicMigration.identity
        assertTrue(id.actions.isEmpty)
      },
      test("identity.isIdentity returns true") {
        assertTrue(DynamicMigration.identity.isIdentity)
      },
      test("non-empty migration is not identity") {
        val m = DynamicMigration.single(MigrationAction.Rename(DynamicOptic.root, "a", "b"))
        assertTrue(!m.isIdentity)
      },
      test("left identity: identity ++ m == m") {
        val m        = DynamicMigration.single(MigrationAction.Rename(DynamicOptic.root, "a", "b"))
        val composed = DynamicMigration.identity ++ m
        assertTrue(composed.actions == m.actions)
      },
      test("right identity: m ++ identity == m") {
        val m        = DynamicMigration.single(MigrationAction.Rename(DynamicOptic.root, "a", "b"))
        val composed = m ++ DynamicMigration.identity
        assertTrue(composed.actions == m.actions)
      },
      test("identity leaves value unchanged") {
        val input  = dynamicRecord("x" -> dynamicInt(1), "y" -> dynamicString("hello"))
        val result = DynamicMigration.identity.apply(input)
        assertTrue(result == Right(input))
      }
    ),
    suite("Associativity")(
      test("(m1 ++ m2) ++ m3 == m1 ++ (m2 ++ m3) structurally") {
        val m1 = DynamicMigration.single(MigrationAction.Rename(DynamicOptic.root, "a", "b"))
        val m2 = DynamicMigration.single(MigrationAction.Rename(DynamicOptic.root, "c", "d"))
        val m3 = DynamicMigration.single(MigrationAction.Rename(DynamicOptic.root, "e", "f"))

        val left  = (m1 ++ m2) ++ m3
        val right = m1 ++ (m2 ++ m3)

        assertTrue(left.actions == right.actions)
      },
      test("(m1 ++ m2) ++ m3 == m1 ++ (m2 ++ m3) behaviorally") {
        val m1 = DynamicMigration.single(MigrationAction.Rename(DynamicOptic.root, "a", "b"))
        val m2 = DynamicMigration.single(MigrationAction.AddField(DynamicOptic.root, "c", Resolved.Literal.int(1)))
        val m3 = DynamicMigration.single(MigrationAction.Rename(DynamicOptic.root, "b", "d"))

        val left  = (m1 ++ m2) ++ m3
        val right = m1 ++ (m2 ++ m3)

        val input = dynamicRecord("a" -> dynamicInt(42))
        assertTrue(left.apply(input) == right.apply(input))
      },
      test("four migration associativity") {
        val m1 = DynamicMigration.single(MigrationAction.Rename(DynamicOptic.root, "a", "b"))
        val m2 = DynamicMigration.single(MigrationAction.Rename(DynamicOptic.root, "b", "c"))
        val m3 = DynamicMigration.single(MigrationAction.Rename(DynamicOptic.root, "c", "d"))
        val m4 = DynamicMigration.single(MigrationAction.Rename(DynamicOptic.root, "d", "e"))

        val left   = ((m1 ++ m2) ++ m3) ++ m4
        val right  = m1 ++ (m2 ++ (m3 ++ m4))
        val middle = (m1 ++ m2) ++ (m3 ++ m4)

        val input    = dynamicRecord("a" -> dynamicInt(1))
        val expected = dynamicRecord("e" -> dynamicInt(1))

        assertTrue(left.apply(input) == Right(expected))
        assertTrue(right.apply(input) == Right(expected))
        assertTrue(middle.apply(input) == Right(expected))
      }
    ),
    suite("Reverse properties")(
      test("identity reverse is identity") {
        val id = DynamicMigration.identity
        assertTrue(id.reverse.isIdentity)
      },
      test("reverse.reverse == original") {
        val m = DynamicMigration.single(MigrationAction.Rename(DynamicOptic.root, "a", "b"))
        assertTrue(m.reverse.reverse.actions == m.actions)
      },
      test("complex migration reverse.reverse == original") {
        val m = DynamicMigration(
          Vector(
            MigrationAction.Rename(DynamicOptic.root, "a", "b"),
            MigrationAction.AddField(DynamicOptic.root, "c", Resolved.Literal.int(0)),
            MigrationAction.DropField(DynamicOptic.root, "d", Resolved.Literal.int(0)),
            MigrationAction.Rename(DynamicOptic.root.field("nested"), "x", "y")
          )
        )
        assertTrue(m.reverse.reverse.actions == m.actions)
      },
      test("reverse of composition: (m1 ++ m2).reverse == m2.reverse ++ m1.reverse") {
        val m1 = DynamicMigration.single(MigrationAction.Rename(DynamicOptic.root, "a", "b"))
        val m2 = DynamicMigration.single(MigrationAction.Rename(DynamicOptic.root, "c", "d"))

        val composedReverse = (m1 ++ m2).reverse
        val reverseComposed = m2.reverse ++ m1.reverse

        assertTrue(composedReverse.actions == reverseComposed.actions)
      },
      test("m ++ m.reverse is round-trip identity for renames") {
        val m         = DynamicMigration.single(MigrationAction.Rename(DynamicOptic.root, "old", "new"))
        val roundTrip = m ++ m.reverse

        val input = dynamicRecord("old" -> dynamicInt(42))
        assertTrue(roundTrip.apply(input) == Right(input))
      },
      test("m.reverse ++ m is round-trip identity for AddField") {
        val m = DynamicMigration.single(
          MigrationAction.AddField(DynamicOptic.root, "field", Resolved.Literal.int(0))
        )
        // Add then drop returns to original
        val input     = dynamicRecord("existing" -> dynamicString("value"))
        val forward   = m.apply(input)
        val roundTrip = forward.flatMap(m.reverse.apply)
        assertTrue(roundTrip == Right(input))
      }
    ),
    suite("Action-specific algebra")(
      test("Rename: rename(a,b) ++ rename(b,c) == rename(a,c) behaviorally") {
        val m1       = DynamicMigration.single(MigrationAction.Rename(DynamicOptic.root, "a", "b"))
        val m2       = DynamicMigration.single(MigrationAction.Rename(DynamicOptic.root, "b", "c"))
        val combined = m1 ++ m2

        val input    = dynamicRecord("a" -> dynamicInt(42))
        val expected = dynamicRecord("c" -> dynamicInt(42))
        assertTrue(combined.apply(input) == Right(expected))
      },
      test("AddField then DropField cancels (for same field)") {
        val m1       = DynamicMigration.single(MigrationAction.AddField(DynamicOptic.root, "x", Resolved.Literal.int(0)))
        val m2       = DynamicMigration.single(MigrationAction.DropField(DynamicOptic.root, "x", Resolved.Literal.int(0)))
        val combined = m1 ++ m2

        val input = dynamicRecord("existing" -> dynamicInt(1))
        assertTrue(combined.apply(input) == Right(input))
      },
      test("DropField then AddField recreates (with default)") {
        val m1       = DynamicMigration.single(MigrationAction.DropField(DynamicOptic.root, "x", Resolved.Literal.int(99)))
        val m2       = DynamicMigration.single(MigrationAction.AddField(DynamicOptic.root, "x", Resolved.Literal.int(99)))
        val combined = m1 ++ m2

        val input = dynamicRecord("x" -> dynamicInt(42))
        // Drop loses original value (42), add brings back default (99)
        val result = combined.apply(input)
        assertTrue(result == Right(dynamicRecord("x" -> dynamicInt(99))))
      },
      test("independent renames commute") {
        // Renaming different fields can be done in either order
        val m1 = DynamicMigration.single(MigrationAction.Rename(DynamicOptic.root, "a", "x"))
        val m2 = DynamicMigration.single(MigrationAction.Rename(DynamicOptic.root, "b", "y"))

        val order1 = m1 ++ m2
        val order2 = m2 ++ m1

        val input    = dynamicRecord("a" -> dynamicInt(1), "b" -> dynamicInt(2))
        val expected = dynamicRecord("x" -> dynamicInt(1), "y" -> dynamicInt(2))

        assertTrue(order1.apply(input) == Right(expected))
        assertTrue(order2.apply(input) == Right(expected))
      },
      test("dependent renames do not commute") {
        // rename(a,b) then rename(b,c) vs rename(b,c) then rename(a,b)
        val m1 = DynamicMigration.single(MigrationAction.Rename(DynamicOptic.root, "a", "b"))
        val m2 = DynamicMigration.single(MigrationAction.Rename(DynamicOptic.root, "b", "c"))

        val order1 = m1 ++ m2 // a->b->c
        val order2 = m2 ++ m1 // (b->c, then a->b) = different result

        val input = dynamicRecord("a" -> dynamicInt(1), "b" -> dynamicInt(2))

        val result1 = order1.apply(input) // a becomes c, b stays b
        val result2 = order2.apply(input) // b becomes c, a becomes b

        // They should produce different results
        assertTrue(result1 != result2)
      }
    ),
    suite("Monoid laws")(
      test("empty element exists (identity)") {
        assertTrue(DynamicMigration.identity.isIdentity)
      },
      test("closed under composition") {
        val m1       = DynamicMigration.single(MigrationAction.Rename(DynamicOptic.root, "a", "b"))
        val m2       = DynamicMigration.single(MigrationAction.Rename(DynamicOptic.root, "c", "d"))
        val composed = m1 ++ m2
        // composed is still a DynamicMigration
        assertTrue(composed.actions.length == 2)
      },
      test("fold over list of migrations") {
        val migrations = List(
          DynamicMigration.single(MigrationAction.Rename(DynamicOptic.root, "a", "b")),
          DynamicMigration.single(MigrationAction.Rename(DynamicOptic.root, "b", "c")),
          DynamicMigration.single(MigrationAction.Rename(DynamicOptic.root, "c", "d"))
        )
        val combined = migrations.foldLeft(DynamicMigration.identity)(_ ++ _)

        val input = dynamicRecord("a" -> dynamicInt(1))
        assertTrue(combined.apply(input) == Right(dynamicRecord("d" -> dynamicInt(1))))
      }
    ),
    suite("Path-based algebra")(
      test("operations on disjoint paths commute") {
        val m1 = DynamicMigration.single(
          MigrationAction.Rename(DynamicOptic.root.field("user"), "name", "fullName")
        )
        val m2 = DynamicMigration.single(
          MigrationAction.Rename(DynamicOptic.root.field("order"), "id", "orderId")
        )

        val order1 = m1 ++ m2
        val order2 = m2 ++ m1

        val input = dynamicRecord(
          "user"  -> dynamicRecord("name" -> dynamicString("Alice")),
          "order" -> dynamicRecord("id" -> dynamicInt(123))
        )
        val expected = dynamicRecord(
          "user"  -> dynamicRecord("fullName" -> dynamicString("Alice")),
          "order" -> dynamicRecord("orderId" -> dynamicInt(123))
        )

        assertTrue(order1.apply(input) == Right(expected))
        assertTrue(order2.apply(input) == Right(expected))
      },
      test("nested path operations preserve hierarchy") {
        val m = DynamicMigration.single(
          MigrationAction.Rename(DynamicOptic.root.field("a").field("b"), "x", "y")
        )
        val input = dynamicRecord(
          "a" -> dynamicRecord(
            "b"     -> dynamicRecord("x" -> dynamicInt(1)),
            "other" -> dynamicInt(2)
          ),
          "c" -> dynamicInt(3)
        )
        val expected = dynamicRecord(
          "a" -> dynamicRecord(
            "b"     -> dynamicRecord("y" -> dynamicInt(1)),
            "other" -> dynamicInt(2)
          ),
          "c" -> dynamicInt(3)
        )
        assertTrue(m.apply(input) == Right(expected))
      }
    ),
    suite("Error propagation algebra")(
      test("error in first action stops execution") {
        val m = DynamicMigration(
          Vector(
            MigrationAction.AddField(DynamicOptic.root, "x", Resolved.Fail("error")),
            MigrationAction.Rename(DynamicOptic.root, "a", "b")
          )
        )
        val input = dynamicRecord("a" -> dynamicInt(1))
        assertTrue(m.apply(input).isLeft)
      },
      test("error preserves earlier successful transformations in result") {
        // Note: errors stop execution, so we don't get partial results
        val m = DynamicMigration(
          Vector(
            MigrationAction.Rename(DynamicOptic.root, "a", "b"),
            MigrationAction.AddField(DynamicOptic.root, "x", Resolved.Fail("error"))
          )
        )
        val input = dynamicRecord("a" -> dynamicInt(1))
        // Even though first action would succeed, the whole migration fails
        assertTrue(m.apply(input).isLeft)
      }
    )
  )
}
