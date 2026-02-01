package zio.blocks.schema.migration

import zio.blocks.chunk.Chunk
import zio.blocks.schema.{DynamicOptic, DynamicValue, PrimitiveValue}
import zio.blocks.schema.SchemaBaseSpec
import zio.test._

/**
 * Tests for migration law compliance: identity, associativity, structural
 * reverse.
 */
object MigrationLawsSpec extends SchemaBaseSpec {

  // Helper to create DynamicValue from primitives
  private def str(s: String): DynamicValue = DynamicValue.Primitive(PrimitiveValue.String(s))
  private def int(i: Int): DynamicValue    = DynamicValue.Primitive(PrimitiveValue.Int(i))

  private def record(fields: (String, DynamicValue)*): DynamicValue =
    DynamicValue.Record(Chunk.fromArray(fields.toArray))

  def spec: Spec[TestEnvironment, Any] = suite("MigrationLawsSpec")(
    suite("Identity Law")(
      test("identity migration returns value unchanged for record") {
        val value  = record("name" -> str("John"), "age" -> int(30))
        val result = DynamicMigrationInterpreter(DynamicMigration.identity, value)
        assertTrue(result == Right(value))
      },
      test("identity migration returns value unchanged for primitive") {
        val value  = str("hello")
        val result = DynamicMigrationInterpreter(DynamicMigration.identity, value)
        assertTrue(result == Right(value))
      },
      test("identity migration returns value unchanged for sequence") {
        val value  = DynamicValue.Sequence(str("a"), str("b"), str("c"))
        val result = DynamicMigrationInterpreter(DynamicMigration.identity, value)
        assertTrue(result == Right(value))
      },
      test("identity migration returns value unchanged for variant") {
        val value  = DynamicValue.Variant("Some", record("value" -> int(42)))
        val result = DynamicMigrationInterpreter(DynamicMigration.identity, value)
        assertTrue(result == Right(value))
      },
      test("identity migration returns value unchanged for nested record") {
        val value = record(
          "person" -> record(
            "name"    -> str("John"),
            "address" -> record(
              "city"   -> str("NYC"),
              "street" -> str("Main St")
            )
          )
        )
        val result = DynamicMigrationInterpreter(DynamicMigration.identity, value)
        assertTrue(result == Right(value))
      }
    ),
    suite("Associativity Law")(
      test("(m1 ++ m2) ++ m3 == m1 ++ (m2 ++ m3) for AddField actions") {
        val m1 = DynamicMigration.single(
          MigrationAction.AddField(DynamicOptic.root, "a", Resolved.Literal.int(1))
        )
        val m2 = DynamicMigration.single(
          MigrationAction.AddField(DynamicOptic.root, "b", Resolved.Literal.int(2))
        )
        val m3 = DynamicMigration.single(
          MigrationAction.AddField(DynamicOptic.root, "c", Resolved.Literal.int(3))
        )

        val initial = record("x" -> str("value"))

        val leftAssoc  = (m1 ++ m2) ++ m3
        val rightAssoc = m1 ++ (m2 ++ m3)

        val leftResult  = DynamicMigrationInterpreter(leftAssoc, initial)
        val rightResult = DynamicMigrationInterpreter(rightAssoc, initial)

        assertTrue(leftResult == rightResult)
      },
      test("(m1 ++ m2) ++ m3 == m1 ++ (m2 ++ m3) for mixed actions") {
        val m1 = DynamicMigration.single(
          MigrationAction.AddField(DynamicOptic.root, "new", Resolved.Literal.string("added"))
        )
        val m2 = DynamicMigration.single(
          MigrationAction.Rename(DynamicOptic.root, "old", "renamed")
        )
        val m3 = DynamicMigration.single(
          MigrationAction.DropField(DynamicOptic.root, "toRemove", Resolved.Fail("no reverse"))
        )

        val initial = record("old" -> str("value"), "toRemove" -> str("bye"))

        val leftAssoc  = (m1 ++ m2) ++ m3
        val rightAssoc = m1 ++ (m2 ++ m3)

        val leftResult  = DynamicMigrationInterpreter(leftAssoc, initial)
        val rightResult = DynamicMigrationInterpreter(rightAssoc, initial)

        assertTrue(leftResult == rightResult)
      },
      test("empty migration is identity for composition") {
        val m = DynamicMigration.single(
          MigrationAction.AddField(DynamicOptic.root, "x", Resolved.Literal.int(1))
        )
        val identity = DynamicMigration.identity

        val initial = record("y" -> str("value"))

        val leftResult   = DynamicMigrationInterpreter(identity ++ m, initial)
        val rightResult  = DynamicMigrationInterpreter(m ++ identity, initial)
        val centerResult = DynamicMigrationInterpreter(m, initial)

        assertTrue(leftResult == centerResult) && assertTrue(rightResult == centerResult)
      }
    ),
    suite("Structural Reverse Law")(
      test("m.reverse.reverse == m for Rename action") {
        val action    = MigrationAction.Rename(DynamicOptic.root, "old", "new")
        val migration = DynamicMigration.single(action)
        val reversed  = migration.reverse.reverse

        assertTrue(reversed.actions.size == 1) &&
        assertTrue(reversed.actions.head == action)
      },
      test("m.reverse.reverse == m for AddField action") {
        val action = MigrationAction.AddField(
          DynamicOptic.root,
          "field",
          Resolved.Literal.int(42)
        )
        val migration = DynamicMigration.single(action)
        val reversed  = migration.reverse.reverse

        assertTrue(reversed.actions.size == 1) &&
        assertTrue(reversed.actions.head == action)
      },
      test("m.reverse.reverse == m for DropField action") {
        val action = MigrationAction.DropField(
          DynamicOptic.root,
          "field",
          Resolved.Literal.string("default")
        )
        val migration = DynamicMigration.single(action)
        val reversed  = migration.reverse.reverse

        assertTrue(reversed.actions.size == 1) &&
        assertTrue(reversed.actions.head == action)
      },
      test("m.reverse.reverse == m for RenameCase action") {
        val action    = MigrationAction.RenameCase(DynamicOptic.root, "OldCase", "NewCase")
        val migration = DynamicMigration.single(action)
        val reversed  = migration.reverse.reverse

        assertTrue(reversed.actions.size == 1) &&
        assertTrue(reversed.actions.head == action)
      },
      test("m.reverse.reverse == m for composed migration") {
        val m1 = DynamicMigration.single(
          MigrationAction.Rename(DynamicOptic.root, "a", "b")
        )
        val m2 = DynamicMigration.single(
          MigrationAction.AddField(DynamicOptic.root, "c", Resolved.Literal.int(1))
        )
        val composed = m1 ++ m2
        val reversed = composed.reverse.reverse

        assertTrue(reversed.actions == composed.actions)
      }
    ),
    suite("Semantic Inverse Law (best-effort)")(
      test("rename reverse restores original field name") {
        val initial = record("oldName" -> str("value"))

        val migration = DynamicMigration.single(
          MigrationAction.Rename(DynamicOptic.root, "oldName", "newName")
        )

        val forward = DynamicMigrationInterpreter(migration, initial)
        forward match {
          case Right(transformed) =>
            val backward = DynamicMigrationInterpreter(migration.reverse, transformed)
            assertTrue(backward == Right(initial))
          case Left(_) => assertTrue(false)
        }
      },
      test("add/drop field roundtrip restores original") {
        val initial = record("existing" -> str("value"))

        val addMigration = DynamicMigration.single(
          MigrationAction.AddField(
            DynamicOptic.root,
            "newField",
            Resolved.Literal.string("added")
          )
        )

        val forward = DynamicMigrationInterpreter(addMigration, initial)
        forward match {
          case Right(transformed) =>
            val backward = DynamicMigrationInterpreter(addMigration.reverse, transformed)
            assertTrue(backward == Right(initial))
          case Left(_) => assertTrue(false)
        }
      },
      test("renameCase reverse restores original case name") {
        val initial = DynamicValue.Variant("OldCase", record("data" -> int(1)))

        val migration = DynamicMigration.single(
          MigrationAction.RenameCase(DynamicOptic.root, "OldCase", "NewCase")
        )

        val forward = DynamicMigrationInterpreter(migration, initial)
        forward match {
          case Right(transformed) =>
            val backward = DynamicMigrationInterpreter(migration.reverse, transformed)
            assertTrue(backward == Right(initial))
          case Left(_) => assertTrue(false)
        }
      }
    ),
    suite("Edge Cases")(
      test("empty record migration") {
        val initial   = DynamicValue.Record(Chunk.empty)
        val migration = DynamicMigration.identity
        val result    = DynamicMigrationInterpreter(migration, initial)
        assertTrue(result == Right(initial))
      },
      test("sequence of sequence handling") {
        val initial = DynamicValue.Sequence(
          DynamicValue.Sequence(str("a"), str("b")),
          DynamicValue.Sequence(str("c"), str("d"))
        )
        val result = DynamicMigrationInterpreter(DynamicMigration.identity, initial)
        assertTrue(result == Right(initial))
      },
      test("variant with empty record content") {
        val initial   = DynamicValue.Variant("EmptyCase", DynamicValue.Record(Chunk.empty))
        val migration = DynamicMigration.single(
          MigrationAction.RenameCase(DynamicOptic.root, "EmptyCase", "RenamedEmpty")
        )
        val result = DynamicMigrationInterpreter(migration, initial)
        assertTrue(result == Right(DynamicValue.Variant("RenamedEmpty", DynamicValue.Record(Chunk.empty))))
      },
      test("multiple actions on same field") {
        val initial   = record("field" -> str("original"))
        val migration = DynamicMigration(
          Chunk(
            MigrationAction.Rename(DynamicOptic.root, "field", "temp"),
            MigrationAction.Rename(DynamicOptic.root, "temp", "final")
          )
        )

        val result = DynamicMigrationInterpreter(migration, initial)
        result match {
          case Right(DynamicValue.Record(fields)) =>
            assertTrue(fields.exists(_._1 == "final")) &&
            assertTrue(!fields.exists(_._1 == "field")) &&
            assertTrue(!fields.exists(_._1 == "temp"))
          case _ => assertTrue(false)
        }
      },
      test("unicode field names") {
        val initial   = record("名前" -> str("John"), "年齢" -> int(30))
        val migration = DynamicMigration.single(
          MigrationAction.Rename(DynamicOptic.root, "名前", "name")
        )

        val result = DynamicMigrationInterpreter(migration, initial)
        result match {
          case Right(DynamicValue.Record(fields)) =>
            assertTrue(fields.exists(_._1 == "name"))
          case _ => assertTrue(false)
        }
      },
      test("field name with special characters") {
        val initial   = record("field-with-dashes" -> str("value1"), "field.with.dots" -> str("value2"))
        val migration = DynamicMigration.single(
          MigrationAction.Rename(DynamicOptic.root, "field-with-dashes", "normalName")
        )

        val result = DynamicMigrationInterpreter(migration, initial)
        result match {
          case Right(DynamicValue.Record(fields)) =>
            assertTrue(fields.exists(_._1 == "normalName"))
          case _ => assertTrue(false)
        }
      },
      test("large record with many fields") {
        val fields    = (1 to 100).map(i => (s"field$i", int(i))).toArray
        val initial   = DynamicValue.Record(Chunk.fromArray(fields))
        val migration = DynamicMigration.single(
          MigrationAction.Rename(DynamicOptic.root, "field50", "renamedField")
        )

        val result = DynamicMigrationInterpreter(migration, initial)
        result match {
          case Right(DynamicValue.Record(resultFields)) =>
            assertTrue(resultFields.size == 100) &&
            assertTrue(resultFields.exists(_._1 == "renamedField")) &&
            assertTrue(!resultFields.exists(_._1 == "field50"))
          case _ => assertTrue(false)
        }
      }
    )
  )
}
