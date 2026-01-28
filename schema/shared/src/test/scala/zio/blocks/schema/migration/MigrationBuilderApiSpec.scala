package zio.blocks.schema.migration

import zio.blocks.schema._
import zio.test._

/**
 * Tests for the MigrationBuilder API and method chaining.
 *
 * Covers:
 *   - Builder construction
 *   - Method chaining
 *   - Type-safe field operations
 *   - Build completion
 *   - Builder state management
 */
object MigrationBuilderApiSpec extends SchemaBaseSpec {

  // ─────────────────────────────────────────────────────────────────────────
  // Test Data Types
  // ─────────────────────────────────────────────────────────────────────────

  case class PersonV1(name: String, age: Int)
  case class PersonV2(fullName: String, age: Int, email: String)

  case class AddressV1(street: String, city: String)
  case class AddressV2(street: String, city: String, zipCode: String)

  case class SimpleRecord(value: Int)
  case class SimpleRecordV2(value: Int, extra: String)

  // ─────────────────────────────────────────────────────────────────────────
  // Test Helpers
  // ─────────────────────────────────────────────────────────────────────────

  def dynamicRecord(fields: (String, DynamicValue)*): DynamicValue =
    DynamicValue.Record(fields.toVector)

  def dynamicString(s: String): DynamicValue =
    DynamicValue.Primitive(PrimitiveValue.String(s))

  def dynamicInt(i: Int): DynamicValue =
    DynamicValue.Primitive(PrimitiveValue.Int(i))

  // ─────────────────────────────────────────────────────────────────────────
  // Tests
  // ─────────────────────────────────────────────────────────────────────────

  def spec: Spec[TestEnvironment, Any] = suite("MigrationBuilderApiSpec")(
    suite("DynamicMigration construction")(
      test("empty migration via identity") {
        val m = DynamicMigration.identity
        assertTrue(m.isIdentity)
      },
      test("single action migration") {
        val m = DynamicMigration.single(
          MigrationAction.Rename(DynamicOptic.root, "old", "new")
        )
        assertTrue(m.actions.length == 1)
      },
      test("multi-action migration via Vector") {
        val m = DynamicMigration(
          Vector(
            MigrationAction.Rename(DynamicOptic.root, "a", "b"),
            MigrationAction.AddField(DynamicOptic.root, "c", Resolved.Literal.int(0))
          )
        )
        assertTrue(m.actions.length == 2)
      }
    ),
    suite("Migration composition")(
      test("compose two migrations with ++") {
        val m1       = DynamicMigration.single(MigrationAction.Rename(DynamicOptic.root, "a", "b"))
        val m2       = DynamicMigration.single(MigrationAction.Rename(DynamicOptic.root, "c", "d"))
        val composed = m1 ++ m2
        assertTrue(composed.actions.length == 2)
      },
      test("compose identity with migration") {
        val m        = DynamicMigration.single(MigrationAction.Rename(DynamicOptic.root, "a", "b"))
        val composed = DynamicMigration.identity ++ m
        assertTrue(composed.actions == m.actions)
      },
      test("compose migration with identity") {
        val m        = DynamicMigration.single(MigrationAction.Rename(DynamicOptic.root, "a", "b"))
        val composed = m ++ DynamicMigration.identity
        assertTrue(composed.actions == m.actions)
      },
      test("associativity of composition") {
        val m1    = DynamicMigration.single(MigrationAction.Rename(DynamicOptic.root, "a", "b"))
        val m2    = DynamicMigration.single(MigrationAction.Rename(DynamicOptic.root, "c", "d"))
        val m3    = DynamicMigration.single(MigrationAction.Rename(DynamicOptic.root, "e", "f"))
        val left  = (m1 ++ m2) ++ m3
        val right = m1 ++ (m2 ++ m3)
        assertTrue(left.actions == right.actions)
      }
    ),
    suite("Migration application order")(
      test("actions applied in order") {
        val m = DynamicMigration(
          Vector(
            MigrationAction.Rename(DynamicOptic.root, "first", "second"),
            MigrationAction.Rename(DynamicOptic.root, "second", "third")
          )
        )
        val input  = dynamicRecord("first" -> dynamicInt(42))
        val result = m.apply(input)
        assertTrue(result == Right(dynamicRecord("third" -> dynamicInt(42))))
      },
      test("later actions see results of earlier actions") {
        val m = DynamicMigration(
          Vector(
            MigrationAction.AddField(DynamicOptic.root, "added", Resolved.Literal.int(1)),
            MigrationAction.Rename(DynamicOptic.root, "added", "renamed")
          )
        )
        val input  = dynamicRecord("existing" -> dynamicString("value"))
        val result = m.apply(input)
        assertTrue(
          result == Right(
            dynamicRecord(
              "existing" -> dynamicString("value"),
              "renamed"  -> dynamicInt(1)
            )
          )
        )
      }
    ),
    suite("Typed Migration construction")(
      test("create Migration with schemas") {
        implicit val schemaV1: Schema[SimpleRecord]   = Schema.derived
        implicit val schemaV2: Schema[SimpleRecordV2] = Schema.derived

        val dynMigration = DynamicMigration.single(
          MigrationAction.AddField(DynamicOptic.root, "extra", Resolved.Literal.string("default"))
        )
        val migration = Migration[SimpleRecord, SimpleRecordV2](dynMigration, schemaV1, schemaV2)
        assertTrue(migration.sourceSchema == schemaV1)
        assertTrue(migration.targetSchema == schemaV2)
      },
      test("Migration applies to typed values") {
        implicit val schemaV1: Schema[SimpleRecord]   = Schema.derived
        implicit val schemaV2: Schema[SimpleRecordV2] = Schema.derived

        val dynMigration = DynamicMigration.single(
          MigrationAction.AddField(DynamicOptic.root, "extra", Resolved.Literal.string("default"))
        )
        val migration = Migration[SimpleRecord, SimpleRecordV2](dynMigration, schemaV1, schemaV2)
        val result    = migration.apply(SimpleRecord(42))
        assertTrue(result == Right(SimpleRecordV2(42, "default")))
      }
    ),
    suite("Migration reverse")(
      test("reverse returns reversed migration") {
        val m        = DynamicMigration.single(MigrationAction.Rename(DynamicOptic.root, "a", "b"))
        val reversed = m.reverse
        reversed.actions.head match {
          case MigrationAction.Rename(_, from, to) =>
            assertTrue(from == "b" && to == "a")
          case _ => assertTrue(false)
        }
      },
      test("reverse of composed migration") {
        val m1       = DynamicMigration.single(MigrationAction.Rename(DynamicOptic.root, "a", "b"))
        val m2       = DynamicMigration.single(MigrationAction.Rename(DynamicOptic.root, "c", "d"))
        val composed = m1 ++ m2
        val reversed = composed.reverse
        // Should be d->c, then b->a
        assertTrue(reversed.actions.length == 2)
        reversed.actions(0) match {
          case MigrationAction.Rename(_, "d", "c") => assertTrue(true)
          case _                                   => assertTrue(false)
        }
        reversed.actions(1) match {
          case MigrationAction.Rename(_, "b", "a") => assertTrue(true)
          case _                                   => assertTrue(false)
        }
      }
    ),
    suite("Action path composition")(
      test("root path") {
        val path   = DynamicOptic.root
        val action = MigrationAction.AddField(path, "field", Resolved.Literal.int(1))
        val input  = dynamicRecord()
        val result = action.apply(input)
        assertTrue(result == Right(dynamicRecord("field" -> dynamicInt(1))))
      },
      test("single field path") {
        val path   = DynamicOptic.root.field("nested")
        val action = MigrationAction.AddField(path, "field", Resolved.Literal.int(1))
        val input  = dynamicRecord("nested" -> dynamicRecord())
        val result = action.apply(input)
        assertTrue(
          result == Right(
            dynamicRecord(
              "nested" -> dynamicRecord("field" -> dynamicInt(1))
            )
          )
        )
      },
      test("multi-level path") {
        val path   = DynamicOptic.root.field("a").field("b")
        val action = MigrationAction.AddField(path, "c", Resolved.Literal.int(1))
        val input  = dynamicRecord(
          "a" -> dynamicRecord(
            "b" -> dynamicRecord()
          )
        )
        val result = action.apply(input)
        assertTrue(
          result == Right(
            dynamicRecord(
              "a" -> dynamicRecord(
                "b" -> dynamicRecord("c" -> dynamicInt(1))
              )
            )
          )
        )
      }
    ),
    suite("Builder pattern scenarios")(
      test("building migration step by step") {
        // Simulating what a builder would do
        var migration = DynamicMigration.identity
        migration = migration ++ DynamicMigration.single(
          MigrationAction.Rename(DynamicOptic.root, "name", "fullName")
        )
        migration = migration ++ DynamicMigration.single(
          MigrationAction.AddField(DynamicOptic.root, "email", Resolved.Literal.string(""))
        )
        assertTrue(migration.actions.length == 2)
      },
      test("builder stored in val maintains correctness") {
        val step1 = DynamicMigration.single(
          MigrationAction.Rename(DynamicOptic.root, "name", "fullName")
        )
        val step2 = step1 ++ DynamicMigration.single(
          MigrationAction.AddField(DynamicOptic.root, "age", Resolved.Literal.int(0))
        )
        val step3 = step2 ++ DynamicMigration.single(
          MigrationAction.DropField(DynamicOptic.root, "temp", Resolved.Literal.int(0))
        )
        // Each step is independent, immutable
        assertTrue(step1.actions.length == 1)
        assertTrue(step2.actions.length == 2)
        assertTrue(step3.actions.length == 3)
      },
      test("fluent chaining") {
        val migration = DynamicMigration.identity ++
          DynamicMigration.single(MigrationAction.Rename(DynamicOptic.root, "a", "b")) ++
          DynamicMigration.single(MigrationAction.Rename(DynamicOptic.root, "c", "d")) ++
          DynamicMigration.single(MigrationAction.Rename(DynamicOptic.root, "e", "f"))
        assertTrue(migration.actions.length == 3)
      }
    ),
    suite("Complex migration scenarios")(
      test("full schema evolution") {
        // PersonV1 -> PersonV2: rename name->fullName, add email
        val migration = DynamicMigration(
          Vector(
            MigrationAction.Rename(DynamicOptic.root, "name", "fullName"),
            MigrationAction.AddField(DynamicOptic.root, "email", Resolved.Literal.string("unknown@example.com"))
          )
        )
        val input = dynamicRecord(
          "name" -> dynamicString("Alice"),
          "age"  -> dynamicInt(30)
        )
        val result = migration.apply(input)
        assertTrue(
          result == Right(
            dynamicRecord(
              "fullName" -> dynamicString("Alice"),
              "age"      -> dynamicInt(30),
              "email"    -> dynamicString("unknown@example.com")
            )
          )
        )
      },
      test("nested schema evolution") {
        // Update address within a person record
        val migration = DynamicMigration.single(
          MigrationAction.AddField(
            DynamicOptic.root.field("address"),
            "zipCode",
            Resolved.Literal.string("00000")
          )
        )
        val input = dynamicRecord(
          "name"    -> dynamicString("Bob"),
          "address" -> dynamicRecord(
            "street" -> dynamicString("123 Main"),
            "city"   -> dynamicString("Boston")
          )
        )
        val result = migration.apply(input)
        assertTrue(
          result == Right(
            dynamicRecord(
              "name"    -> dynamicString("Bob"),
              "address" -> dynamicRecord(
                "street"  -> dynamicString("123 Main"),
                "city"    -> dynamicString("Boston"),
                "zipCode" -> dynamicString("00000")
              )
            )
          )
        )
      }
    ),
    suite("Immutability guarantees")(
      test("original migration unchanged after composition") {
        val original = DynamicMigration.single(
          MigrationAction.Rename(DynamicOptic.root, "a", "b")
        )
        val originalActions = original.actions
        val _               = original ++ DynamicMigration.single(
          MigrationAction.Rename(DynamicOptic.root, "c", "d")
        )
        // Original should be unchanged
        assertTrue(original.actions == originalActions)
        assertTrue(original.actions.length == 1)
      },
      test("reverse does not modify original") {
        val original = DynamicMigration.single(
          MigrationAction.Rename(DynamicOptic.root, "a", "b")
        )
        val originalActions = original.actions
        val _               = original.reverse
        assertTrue(original.actions == originalActions)
      }
    )
  )
}
