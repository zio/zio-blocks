package zio.blocks.schema.migration

import zio.blocks.chunk.Chunk
import zio.blocks.schema.{DynamicOptic, DynamicValue, PrimitiveValue}
import zio.test._

/**
 * Comprehensive tests for migration utility methods that need branch coverage.
 * Covers DynamicMigration helper methods, MigrationDiagnostics,
 * SchemaShapeValidator, and related utilities with 0% or low coverage in CI
 * scoverage reports.
 */
object MigrationUtilitySpec extends ZIOSpecDefault {

  private def str(s: String): DynamicValue = DynamicValue.Primitive(PrimitiveValue.String(s))
  private def int(i: Int): DynamicValue    = DynamicValue.Primitive(PrimitiveValue.Int(i))
  private def long(l: Long): DynamicValue  = DynamicValue.Primitive(PrimitiveValue.Long(l))
  private def unit: DynamicValue           = DynamicValue.Primitive(PrimitiveValue.Unit)

  def spec = suite("MigrationUtilitySpec")(
    // =========================================================================
    // DynamicMigration utility methods - all with 0% coverage
    // =========================================================================
    suite("DynamicMigration - isIdentity")(
      test("returns true for empty migration") {
        val migration = DynamicMigration.identity
        assertTrue(migration.isIdentity)
      },
      test("returns true for migration with empty actions Chunk") {
        val migration = DynamicMigration(Chunk.empty)
        assertTrue(migration.isIdentity)
      },
      test("returns false for single action migration") {
        val migration = DynamicMigration.single(
          MigrationAction.AddField(DynamicOptic.root, "x", Resolved.Literal(int(1)))
        )
        assertTrue(!migration.isIdentity)
      },
      test("returns false for migration with multiple actions") {
        val migration = DynamicMigration(
          MigrationAction.AddField(DynamicOptic.root, "a", Resolved.Literal(int(1))),
          MigrationAction.Rename(DynamicOptic.root, "b", "c")
        )
        assertTrue(!migration.isIdentity)
      }
    ),
    suite("DynamicMigration - actionCount")(
      test("returns 0 for identity migration") {
        assertTrue(DynamicMigration.identity.actionCount == 0)
      },
      test("returns 1 for single action") {
        val m = DynamicMigration.single(MigrationAction.Rename(DynamicOptic.root, "a", "b"))
        assertTrue(m.actionCount == 1)
      },
      test("returns 3 for three actions") {
        val migration = DynamicMigration(
          MigrationAction.AddField(DynamicOptic.root, "a", Resolved.Literal(int(1))),
          MigrationAction.AddField(DynamicOptic.root, "b", Resolved.Literal(int(2))),
          MigrationAction.Rename(DynamicOptic.root, "c", "d")
        )
        assertTrue(migration.actionCount == 3)
      },
      test("returns correct count for 10 actions") {
        val actions   = (1 to 10).map(i => MigrationAction.AddField(DynamicOptic.root, s"f$i", Resolved.Literal(int(i))))
        val migration = DynamicMigration(Chunk.fromIterable(actions))
        assertTrue(migration.actionCount == 10)
      }
    ),
    suite("DynamicMigration - andThen composition")(
      test("composes two single-action migrations") {
        val m1       = DynamicMigration.single(MigrationAction.AddField(DynamicOptic.root, "x", Resolved.Literal(int(1))))
        val m2       = DynamicMigration.single(MigrationAction.Rename(DynamicOptic.root, "x", "y"))
        val composed = m1.andThen(m2)
        assertTrue(composed.size == 2)
      },
      test("composes identity with non-empty") {
        val m1       = DynamicMigration.identity
        val m2       = DynamicMigration.single(MigrationAction.AddField(DynamicOptic.root, "a", Resolved.Literal(int(1))))
        val composed = m1.andThen(m2)
        assertTrue(composed.size == 1)
      },
      test("composes non-empty with identity") {
        val m1       = DynamicMigration.single(MigrationAction.AddField(DynamicOptic.root, "a", Resolved.Literal(int(1))))
        val m2       = DynamicMigration.identity
        val composed = m1.andThen(m2)
        assertTrue(composed.size == 1)
      },
      test("composes two multi-action migrations") {
        val m1 = DynamicMigration(
          MigrationAction.AddField(DynamicOptic.root, "a", Resolved.Literal(int(1))),
          MigrationAction.AddField(DynamicOptic.root, "b", Resolved.Literal(int(2)))
        )
        val m2 = DynamicMigration(
          MigrationAction.Rename(DynamicOptic.root, "a", "x"),
          MigrationAction.Rename(DynamicOptic.root, "b", "y")
        )
        val composed = m1.andThen(m2)
        assertTrue(composed.size == 4)
      }
    ),
    suite("DynamicMigration - describe method")(
      test("describe on identity migration mentions identity") {
        val migration = DynamicMigration.identity
        val desc      = migration.describe
        assertTrue(desc.contains("Identity migration"))
      },
      test("describe includes action count") {
        val migration = DynamicMigration(
          MigrationAction.AddField(DynamicOptic.root, "a", Resolved.Literal(int(1))),
          MigrationAction.AddField(DynamicOptic.root, "b", Resolved.Literal(int(2)))
        )
        val desc = migration.describe
        assertTrue(desc.contains("2 actions"))
      },
      test("describe shows AddField action") {
        val migration = DynamicMigration.single(
          MigrationAction.AddField(DynamicOptic.root, "email", Resolved.Literal(str("test@test.com")))
        )
        val desc = migration.describe
        assertTrue(desc.contains("AddField") && desc.contains("email"))
      },
      test("describe shows DropField action") {
        val migration = DynamicMigration.single(
          MigrationAction.DropField(DynamicOptic.root, "oldField", Resolved.Literal(str("")))
        )
        val desc = migration.describe
        assertTrue(desc.contains("DropField") && desc.contains("oldField"))
      },
      test("describe shows Rename action with from and to") {
        val migration = DynamicMigration.single(
          MigrationAction.Rename(DynamicOptic.root, "name", "fullName")
        )
        val desc = migration.describe
        assertTrue(desc.contains("Rename") && desc.contains("name") && desc.contains("fullName"))
      },
      test("describe shows TransformValue action") {
        val migration = DynamicMigration.single(
          MigrationAction.TransformValue(DynamicOptic.root, "age", Resolved.Identity, Resolved.Identity)
        )
        val desc = migration.describe
        assertTrue(desc.contains("Transform") && desc.contains("age"))
      },
      test("describe shows Mandate action") {
        val migration = DynamicMigration.single(
          MigrationAction.Mandate(DynamicOptic.root, "required", Resolved.Literal(str("default")))
        )
        val desc = migration.describe
        assertTrue(desc.contains("Mandate") && desc.contains("required"))
      },
      test("describe shows Optionalize action") {
        val migration = DynamicMigration.single(
          MigrationAction.Optionalize(DynamicOptic.root, "optional")
        )
        val desc = migration.describe
        assertTrue(desc.contains("Optionalize") && desc.contains("optional"))
      },
      test("describe shows ChangeType action") {
        val migration = DynamicMigration.single(
          MigrationAction.ChangeType(
            DynamicOptic.root,
            "id",
            Resolved.Convert("Int", "Long", Resolved.Identity),
            Resolved.Convert("Long", "Int", Resolved.Identity)
          )
        )
        val desc = migration.describe
        assertTrue(desc.contains("ChangeType") && desc.contains("id"))
      },
      test("describe shows RenameCase action") {
        val migration = DynamicMigration.single(
          MigrationAction.RenameCase(DynamicOptic.root, "OldCase", "NewCase")
        )
        val desc = migration.describe
        assertTrue(desc.contains("RenameCase") && desc.contains("OldCase"))
      },
      test("describe shows TransformCase action") {
        val migration = DynamicMigration.single(
          MigrationAction.TransformCase(DynamicOptic.root, "SomeCase", Chunk.empty)
        )
        val desc = migration.describe
        assertTrue(desc.contains("TransformCase") && desc.contains("SomeCase"))
      },
      test("describe shows TransformElements action") {
        val migration = DynamicMigration.single(
          MigrationAction.TransformElements(DynamicOptic.root, Resolved.Identity, Resolved.Identity)
        )
        val desc = migration.describe
        assertTrue(desc.contains("TransformElements"))
      },
      test("describe shows TransformKeys action") {
        val migration = DynamicMigration.single(
          MigrationAction.TransformKeys(DynamicOptic.root, Resolved.Identity, Resolved.Identity)
        )
        val desc = migration.describe
        assertTrue(desc.contains("TransformKeys"))
      },
      test("describe shows TransformValues action") {
        val migration = DynamicMigration.single(
          MigrationAction.TransformValues(DynamicOptic.root, Resolved.Identity, Resolved.Identity)
        )
        val desc = migration.describe
        assertTrue(desc.contains("TransformValues"))
      },
      test("describe shows Join action") {
        val migration = DynamicMigration.single(
          MigrationAction.Join(
            DynamicOptic.root,
            "fullName",
            Chunk(DynamicOptic.root.field("first"), DynamicOptic.root.field("last")),
            Resolved.Concat(
              Vector(
                Resolved.FieldAccess("first", Resolved.Identity),
                Resolved.Literal(str(" ")),
                Resolved.FieldAccess("last", Resolved.Identity)
              ),
              ""
            ),
            Resolved.Identity
          )
        )
        val desc = migration.describe
        assertTrue(desc.contains("Join") && desc.contains("fullName"))
      },
      test("describe shows Split action") {
        val migration = DynamicMigration.single(
          MigrationAction.Split(
            DynamicOptic.root,
            "fullName",
            Chunk(DynamicOptic.root.field("first"), DynamicOptic.root.field("last")),
            Resolved.Identity,
            Resolved.Identity
          )
        )
        val desc = migration.describe
        assertTrue(desc.contains("Split") && desc.contains("fullName"))
      },
      test("describe with nested path") {
        val migration = DynamicMigration.single(
          MigrationAction.AddField(DynamicOptic.root.field("address"), "zipCode", Resolved.Literal(str("12345")))
        )
        val desc = migration.describe
        assertTrue(desc.contains("AddField") && desc.contains("zipCode"))
      }
    ),
    suite("DynamicMigration - apply method")(
      test("apply on identity returns same value") {
        val record    = DynamicValue.Record("name" -> str("Alice"), "age" -> int(30))
        val migration = DynamicMigration.identity
        val result    = migration.apply(record)
        assertTrue(result == Right(record))
      },
      test("apply executes AddField action") {
        val record    = DynamicValue.Record("name" -> str("Alice"), "age" -> int(30))
        val migration = DynamicMigration.single(
          MigrationAction.AddField(DynamicOptic.root, "country", Resolved.Literal(str("USA")))
        )
        val result = migration.apply(record)
        assertTrue(result.isRight && result.exists {
          case DynamicValue.Record(fields) => fields.size == 3 && fields.exists(_._1 == "country")
          case _                           => false
        })
      },
      test("apply executes Rename action") {
        val record    = DynamicValue.Record("name" -> str("Alice"))
        val migration = DynamicMigration.single(
          MigrationAction.Rename(DynamicOptic.root, "name", "fullName")
        )
        val result = migration.apply(record)
        assertTrue(result.isRight && result.exists {
          case DynamicValue.Record(fields) => fields.exists(_._1 == "fullName") && !fields.exists(_._1 == "name")
          case _                           => false
        })
      },
      test("apply executes DropField action") {
        val record    = DynamicValue.Record("name" -> str("Alice"), "temp" -> int(0))
        val migration = DynamicMigration.single(
          MigrationAction.DropField(DynamicOptic.root, "temp", Resolved.Literal(int(0)))
        )
        val result = migration.apply(record)
        assertTrue(result.isRight && result.exists {
          case DynamicValue.Record(fields) => fields.size == 1 && !fields.exists(_._1 == "temp")
          case _                           => false
        })
      },
      test("apply on non-record returns error for record operations") {
        val primitive = int(42)
        val migration = DynamicMigration.single(
          MigrationAction.AddField(DynamicOptic.root, "field", Resolved.Literal(int(1)))
        )
        val result = migration.apply(primitive)
        assertTrue(result.isLeft)
      }
    ),
    suite("DynamicMigration - optimize method")(
      test("optimize combines consecutive renames on same path") {
        val migration = DynamicMigration(
          MigrationAction.Rename(DynamicOptic.root, "a", "b"),
          MigrationAction.Rename(DynamicOptic.root, "b", "c")
        )
        val optimized = migration.optimize
        assertTrue(optimized.size == 1)
      },
      test("optimize eliminates AddField then DropField pair") {
        val migration = DynamicMigration(
          MigrationAction.AddField(DynamicOptic.root, "temp", Resolved.Literal(int(0))),
          MigrationAction.DropField(DynamicOptic.root, "temp", Resolved.Literal(int(0)))
        )
        val optimized = migration.optimize
        assertTrue(optimized.isEmpty)
      },
      test("optimize eliminates DropField then AddField pair") {
        val migration = DynamicMigration(
          MigrationAction.DropField(DynamicOptic.root, "x", Resolved.Literal(int(0))),
          MigrationAction.AddField(DynamicOptic.root, "x", Resolved.Literal(int(1)))
        )
        val optimized = migration.optimize
        assertTrue(optimized.isEmpty)
      },
      test("optimize removes no-op rename where from equals to") {
        val migration = DynamicMigration(
          MigrationAction.AddField(DynamicOptic.root, "x", Resolved.Literal(int(1))),
          MigrationAction.Rename(DynamicOptic.root, "same", "same")
        )
        val optimized = migration.optimize
        assertTrue(optimized.size == 1)
      },
      test("optimize keeps non-redundant actions") {
        val migration = DynamicMigration(
          MigrationAction.AddField(DynamicOptic.root, "x", Resolved.Literal(int(1))),
          MigrationAction.AddField(DynamicOptic.root, "y", Resolved.Literal(int(2)))
        )
        val optimized = migration.optimize
        assertTrue(optimized.size == 2)
      },
      test("optimize on empty migration returns empty") {
        val optimized = DynamicMigration.identity.optimize
        assertTrue(optimized.isEmpty)
      },
      test("optimize on single action returns single action") {
        val migration =
          DynamicMigration.single(MigrationAction.AddField(DynamicOptic.root, "x", Resolved.Literal(int(1))))
        val optimized = migration.optimize
        assertTrue(optimized.size == 1)
      },
      test("optimize does not combine renames on different paths") {
        val migration = DynamicMigration(
          MigrationAction.Rename(DynamicOptic.root.field("nested"), "a", "b"),
          MigrationAction.Rename(DynamicOptic.root, "b", "c")
        )
        val optimized = migration.optimize
        assertTrue(optimized.size == 2)
      },
      test("optimize chains multiple consecutive renames") {
        val migration = DynamicMigration(
          MigrationAction.Rename(DynamicOptic.root, "a", "b"),
          MigrationAction.Rename(DynamicOptic.root, "b", "c"),
          MigrationAction.Rename(DynamicOptic.root, "c", "d")
        )
        val optimized = migration.optimize
        assertTrue(optimized.size == 1)
      }
    ),
    // =========================================================================
    // MigrationDiagnostics - formatMigration + formatAction (72% coverage)
    // =========================================================================
    suite("MigrationDiagnostics - formatMigration")(
      test("empty migration shows no actions message") {
        val formatted = MigrationDiagnostics.formatMigration(DynamicMigration.identity)
        assertTrue(formatted.contains("no actions"))
      },
      test("non-empty migration shows numbered actions") {
        val migration = DynamicMigration(
          MigrationAction.AddField(DynamicOptic.root, "a", Resolved.Literal(int(1))),
          MigrationAction.Rename(DynamicOptic.root, "b", "c")
        )
        val formatted = MigrationDiagnostics.formatMigration(migration)
        assertTrue(formatted.contains("1.") && formatted.contains("2."))
      },
      test("formatMigration shows ADD for AddField") {
        val migration = DynamicMigration.single(
          MigrationAction.AddField(DynamicOptic.root, "test", Resolved.Literal(int(42)))
        )
        val formatted = MigrationDiagnostics.formatMigration(migration)
        assertTrue(formatted.contains("ADD field"))
      },
      test("formatMigration shows DROP for DropField") {
        val migration = DynamicMigration.single(
          MigrationAction.DropField(DynamicOptic.root, "oldField", Resolved.Literal(int(0)))
        )
        val formatted = MigrationDiagnostics.formatMigration(migration)
        assertTrue(formatted.contains("DROP field"))
      },
      test("formatMigration shows RENAME for Rename") {
        val migration = DynamicMigration.single(
          MigrationAction.Rename(DynamicOptic.root, "old", "new")
        )
        val formatted = MigrationDiagnostics.formatMigration(migration)
        assertTrue(formatted.contains("RENAME field"))
      },
      test("formatMigration shows TRANSFORM for TransformValue") {
        val migration = DynamicMigration.single(
          MigrationAction.TransformValue(DynamicOptic.root, "value", Resolved.Identity, Resolved.Identity)
        )
        val formatted = MigrationDiagnostics.formatMigration(migration)
        assertTrue(formatted.contains("TRANSFORM field"))
      },
      test("formatMigration shows MANDATE") {
        val migration = DynamicMigration.single(
          MigrationAction.Mandate(DynamicOptic.root, "field", Resolved.Literal(str("default")))
        )
        val formatted = MigrationDiagnostics.formatMigration(migration)
        assertTrue(formatted.contains("MANDATE field"))
      },
      test("formatMigration shows OPTIONALIZE") {
        val migration = DynamicMigration.single(
          MigrationAction.Optionalize(DynamicOptic.root, "field")
        )
        val formatted = MigrationDiagnostics.formatMigration(migration)
        assertTrue(formatted.contains("OPTIONALIZE field"))
      },
      test("formatMigration shows CHANGE TYPE") {
        val migration = DynamicMigration.single(
          MigrationAction.ChangeType(DynamicOptic.root, "field", Resolved.Identity, Resolved.Identity)
        )
        val formatted = MigrationDiagnostics.formatMigration(migration)
        assertTrue(formatted.contains("CHANGE TYPE"))
      },
      test("formatMigration shows RENAME CASE") {
        val migration = DynamicMigration.single(
          MigrationAction.RenameCase(DynamicOptic.root, "OldCase", "NewCase")
        )
        val formatted = MigrationDiagnostics.formatMigration(migration)
        assertTrue(formatted.contains("RENAME CASE"))
      },
      test("formatMigration shows TRANSFORM CASE") {
        val migration = DynamicMigration.single(
          MigrationAction.TransformCase(DynamicOptic.root, "Case", Chunk.empty)
        )
        val formatted = MigrationDiagnostics.formatMigration(migration)
        assertTrue(formatted.contains("TRANSFORM CASE"))
      },
      test("formatMigration shows TRANSFORM ELEMENTS") {
        val migration = DynamicMigration.single(
          MigrationAction.TransformElements(DynamicOptic.root, Resolved.Identity, Resolved.Identity)
        )
        val formatted = MigrationDiagnostics.formatMigration(migration)
        assertTrue(formatted.contains("TRANSFORM ELEMENTS"))
      },
      test("formatMigration shows TRANSFORM KEYS") {
        val migration = DynamicMigration.single(
          MigrationAction.TransformKeys(DynamicOptic.root, Resolved.Identity, Resolved.Identity)
        )
        val formatted = MigrationDiagnostics.formatMigration(migration)
        assertTrue(formatted.contains("TRANSFORM KEYS"))
      },
      test("formatMigration shows TRANSFORM VALUES") {
        val migration = DynamicMigration.single(
          MigrationAction.TransformValues(DynamicOptic.root, Resolved.Identity, Resolved.Identity)
        )
        val formatted = MigrationDiagnostics.formatMigration(migration)
        assertTrue(formatted.contains("TRANSFORM VALUES"))
      },
      test("formatMigration shows JOIN") {
        val migration = DynamicMigration.single(
          MigrationAction.Join(DynamicOptic.root, "target", Chunk.empty, Resolved.Identity, Resolved.Identity)
        )
        val formatted = MigrationDiagnostics.formatMigration(migration)
        assertTrue(formatted.contains("JOIN"))
      },
      test("formatMigration shows SPLIT") {
        val migration = DynamicMigration.single(
          MigrationAction.Split(DynamicOptic.root, "source", Chunk.empty, Resolved.Identity, Resolved.Identity)
        )
        val formatted = MigrationDiagnostics.formatMigration(migration)
        assertTrue(formatted.contains("SPLIT"))
      }
    ),
    suite("MigrationDiagnostics - formatResolved branches")(
      test("formats Literal with primitive") {
        val migration = DynamicMigration.single(
          MigrationAction.AddField(DynamicOptic.root, "x", Resolved.Literal(int(42)))
        )
        val formatted = MigrationDiagnostics.formatMigration(migration)
        assertTrue(formatted.contains("literal"))
      },
      test("formats Literal with Record") {
        val record    = DynamicValue.Record("a" -> int(1), "b" -> int(2))
        val migration = DynamicMigration.single(
          MigrationAction.AddField(DynamicOptic.root, "nested", Resolved.Literal(record))
        )
        val formatted = MigrationDiagnostics.formatMigration(migration)
        assertTrue(formatted.contains("literal") && formatted.contains("Record"))
      },
      test("formats Literal with Sequence") {
        val seq       = DynamicValue.Sequence(int(1), int(2), int(3))
        val migration = DynamicMigration.single(
          MigrationAction.AddField(DynamicOptic.root, "items", Resolved.Literal(seq))
        )
        val formatted = MigrationDiagnostics.formatMigration(migration)
        assertTrue(formatted.contains("literal") && formatted.contains("Seq"))
      },
      test("formats FieldAccess") {
        val migration = DynamicMigration.single(
          MigrationAction.TransformValue(
            DynamicOptic.root,
            "x",
            Resolved.FieldAccess("source", Resolved.Identity),
            Resolved.Identity
          )
        )
        val formatted = MigrationDiagnostics.formatMigration(migration)
        assertTrue(formatted.contains("field(source)"))
      },
      test("formats Identity") {
        val migration = DynamicMigration.single(
          MigrationAction.TransformValue(DynamicOptic.root, "x", Resolved.Identity, Resolved.Identity)
        )
        val formatted = MigrationDiagnostics.formatMigration(migration)
        assertTrue(formatted.contains("identity"))
      },
      test("formats Fail") {
        val migration = DynamicMigration.single(
          MigrationAction.TransformValue(DynamicOptic.root, "x", Resolved.Fail("error message"), Resolved.Identity)
        )
        val formatted = MigrationDiagnostics.formatMigration(migration)
        assertTrue(formatted.contains("fail"))
      },
      test("formats Convert as class name") {
        val migration = DynamicMigration.single(
          MigrationAction.TransformValue(
            DynamicOptic.root,
            "x",
            Resolved.Convert("Int", "Long", Resolved.Identity),
            Resolved.Identity
          )
        )
        val formatted = MigrationDiagnostics.formatMigration(migration)
        assertTrue(formatted.contains("Convert"))
      }
    ),
    suite("MigrationDiagnostics - toMermaidDiagram")(
      test("generates flowchart header") {
        val diagram = MigrationDiagnostics.toMermaidDiagram(DynamicMigration.identity)
        assertTrue(diagram.contains("flowchart LR"))
      },
      test("includes Source node") {
        val diagram = MigrationDiagnostics.toMermaidDiagram(DynamicMigration.identity)
        assertTrue(diagram.contains("Source"))
      },
      test("includes Target node") {
        val diagram = MigrationDiagnostics.toMermaidDiagram(DynamicMigration.identity)
        assertTrue(diagram.contains("Target"))
      },
      test("shows + for AddField") {
        val migration =
          DynamicMigration.single(MigrationAction.AddField(DynamicOptic.root, "x", Resolved.Literal(int(1))))
        val diagram = MigrationDiagnostics.toMermaidDiagram(migration)
        assertTrue(diagram.contains("+"))
      },
      test("shows - for DropField") {
        val migration =
          DynamicMigration.single(MigrationAction.DropField(DynamicOptic.root, "x", Resolved.Literal(int(0))))
        val diagram = MigrationDiagnostics.toMermaidDiagram(migration)
        assertTrue(diagram.contains("-"))
      },
      test("shows arrow for Rename") {
        val migration = DynamicMigration.single(MigrationAction.Rename(DynamicOptic.root, "a", "b"))
        val diagram   = MigrationDiagnostics.toMermaidDiagram(migration)
        assertTrue(diagram.contains("Rename"))
      },
      test("shows symbol for TransformValue") {
        val migration = DynamicMigration.single(
          MigrationAction.TransformValue(DynamicOptic.root, "x", Resolved.Identity, Resolved.Identity)
        )
        val diagram = MigrationDiagnostics.toMermaidDiagram(migration)
        assertTrue(diagram.contains("TransformValue"))
      },
      test("shows symbol for Join") {
        val migration = DynamicMigration.single(
          MigrationAction.Join(DynamicOptic.root, "t", Chunk.empty, Resolved.Identity, Resolved.Identity)
        )
        val diagram = MigrationDiagnostics.toMermaidDiagram(migration)
        assertTrue(diagram.contains("Join"))
      },
      test("shows symbol for Split") {
        val migration = DynamicMigration.single(
          MigrationAction.Split(DynamicOptic.root, "s", Chunk.empty, Resolved.Identity, Resolved.Identity)
        )
        val diagram = MigrationDiagnostics.toMermaidDiagram(migration)
        assertTrue(diagram.contains("Split"))
      },
      test("uses generic symbol for other action types") {
        val migration = DynamicMigration.single(MigrationAction.Optionalize(DynamicOptic.root, "x"))
        val diagram   = MigrationDiagnostics.toMermaidDiagram(migration)
        assertTrue(diagram.contains("Optionalize"))
      }
    ),
    suite("MigrationDiagnostics - analyze")(
      test("returns correct actionCount") {
        val migration = DynamicMigration(
          MigrationAction.AddField(DynamicOptic.root, "a", Resolved.Literal(int(1))),
          MigrationAction.AddField(DynamicOptic.root, "b", Resolved.Literal(int(2)))
        )
        val analysis = MigrationDiagnostics.analyze(migration)
        assertTrue(analysis.actionCount == 2)
      },
      test("detects hasDataLossRisk for DropField") {
        val migration =
          DynamicMigration.single(MigrationAction.DropField(DynamicOptic.root, "x", Resolved.Literal(int(0))))
        val analysis = MigrationDiagnostics.analyze(migration)
        assertTrue(analysis.hasDataLossRisk)
      },
      test("no hasDataLossRisk for AddField only") {
        val migration =
          DynamicMigration.single(MigrationAction.AddField(DynamicOptic.root, "x", Resolved.Literal(int(0))))
        val analysis = MigrationDiagnostics.analyze(migration)
        assertTrue(!analysis.hasDataLossRisk)
      },
      test("isReversible for rename only migration") {
        val migration = DynamicMigration.single(MigrationAction.Rename(DynamicOptic.root, "a", "b"))
        val analysis  = MigrationDiagnostics.analyze(migration)
        assertTrue(analysis.isReversible)
      },
      test("not isReversible for AddField migration") {
        val migration =
          DynamicMigration.single(MigrationAction.AddField(DynamicOptic.root, "x", Resolved.Literal(int(0))))
        val analysis = MigrationDiagnostics.analyze(migration)
        assertTrue(!analysis.isReversible)
      },
      test("warning for drops without renames") {
        val migration =
          DynamicMigration.single(MigrationAction.DropField(DynamicOptic.root, "x", Resolved.Literal(int(0))))
        val analysis = MigrationDiagnostics.analyze(migration)
        assertTrue(analysis.warnings.exists(_.contains("dropped")))
      },
      test("suggestion for join and split combination") {
        val migration = DynamicMigration(
          MigrationAction.Join(DynamicOptic.root, "t", Chunk.empty, Resolved.Identity, Resolved.Identity),
          MigrationAction.Split(DynamicOptic.root, "s", Chunk.empty, Resolved.Identity, Resolved.Identity)
        )
        val analysis = MigrationDiagnostics.analyze(migration)
        assertTrue(analysis.suggestions.exists(_.contains("join and split")))
      },
      test("suggestion for large migration") {
        val actions   = (1 to 11).map(i => MigrationAction.Rename(DynamicOptic.root, s"a$i", s"b$i"))
        val migration = DynamicMigration(Chunk.fromIterable(actions))
        val analysis  = MigrationDiagnostics.analyze(migration)
        assertTrue(analysis.suggestions.exists(_.contains("Large migration")))
      },
      test("detects redundant renames at same path") {
        val migration = DynamicMigration(
          MigrationAction.Rename(DynamicOptic.root, "a", "b"),
          MigrationAction.Rename(DynamicOptic.root, "c", "d")
        )
        val analysis = MigrationDiagnostics.analyze(migration)
        assertTrue(analysis.suggestions.exists(_.contains("Redundant renames")))
      }
    ),
    suite("MigrationDiagnostics.MigrationAnalysis - render")(
      test("render includes Action Count") {
        val analysis = MigrationDiagnostics.MigrationAnalysis(5, true, false, Nil, Nil)
        val rendered = analysis.render
        assertTrue(rendered.contains("Action Count: 5"))
      },
      test("render includes Fully Reversible") {
        val analysis = MigrationDiagnostics.MigrationAnalysis(1, true, false, Nil, Nil)
        val rendered = analysis.render
        assertTrue(rendered.contains("Fully Reversible: true"))
      },
      test("render includes Data Loss Risk") {
        val analysis = MigrationDiagnostics.MigrationAnalysis(1, false, true, Nil, Nil)
        val rendered = analysis.render
        assertTrue(rendered.contains("Data Loss Risk: true"))
      },
      test("render includes warnings") {
        val analysis = MigrationDiagnostics.MigrationAnalysis(1, false, false, List("Warning 1", "Warning 2"), Nil)
        val rendered = analysis.render
        assertTrue(rendered.contains("Warning 1") && rendered.contains("Warning 2"))
      },
      test("render includes suggestions") {
        val analysis =
          MigrationDiagnostics.MigrationAnalysis(1, false, false, Nil, List("Suggestion A", "Suggestion B"))
        val rendered = analysis.render
        assertTrue(rendered.contains("Suggestion A") && rendered.contains("Suggestion B"))
      }
    ),
    suite("MigrationDiagnostics - suggestFixes")(
      test("suggests dropField for unhandled paths") {
        val suggestions = MigrationDiagnostics.suggestFixes(List("oldField"), Nil)
        assertTrue(suggestions.exists(_.contains("dropField")))
      },
      test("suggests addField for unprovided paths") {
        val suggestions = MigrationDiagnostics.suggestFixes(Nil, List("newField"))
        assertTrue(suggestions.exists(_.contains("addField")))
      },
      test("suggests renameField for similar paths") {
        val suggestions = MigrationDiagnostics.suggestFixes(List("userName"), List("username"))
        assertTrue(suggestions.exists(_.contains("renameField")))
      },
      test("handles empty lists") {
        val suggestions = MigrationDiagnostics.suggestFixes(Nil, Nil)
        assertTrue(suggestions.isEmpty)
      },
      test("handles multiple unhandled paths") {
        val suggestions = MigrationDiagnostics.suggestFixes(List("a", "b", "c"), Nil)
        assertTrue(suggestions.size == 3)
      },
      test("handles multiple unprovided paths") {
        val suggestions = MigrationDiagnostics.suggestFixes(Nil, List("x", "y", "z"))
        assertTrue(suggestions.size == 3)
      }
    ),
    // =========================================================================
    // SchemaShapeValidator - MigrationCoverage methods (33% coverage)
    // =========================================================================
    suite("SchemaShapeValidator.MigrationCoverage")(
      test("empty creates empty coverage") {
        val coverage = SchemaShapeValidator.MigrationCoverage.empty
        assertTrue(coverage.handledFromSource.isEmpty && coverage.providedToTarget.isEmpty)
      },
      test("handleField adds to handledFromSource") {
        val coverage = SchemaShapeValidator.MigrationCoverage.empty.handleField("user.name")
        assertTrue(coverage.handledFromSource.exists(_.toFlatString == "user.name"))
      },
      test("provideField adds to providedToTarget") {
        val coverage = SchemaShapeValidator.MigrationCoverage.empty.provideField("user.email")
        assertTrue(coverage.providedToTarget.exists(_.toFlatString == "user.email"))
      },
      test("renameField updates both source and target") {
        val coverage = SchemaShapeValidator.MigrationCoverage.empty.renameField("oldName", "newName")
        assertTrue(
          coverage.handledFromSource.exists(_.toFlatString == "oldName") &&
            coverage.providedToTarget.exists(_.toFlatString == "newName") &&
            coverage.renamedFields.nonEmpty
        )
      },
      test("dropField updates handledFromSource and droppedFields") {
        val coverage = SchemaShapeValidator.MigrationCoverage.empty.dropField("removedField")
        assertTrue(
          coverage.handledFromSource.exists(_.toFlatString == "removedField") &&
            coverage.droppedFields.nonEmpty
        )
      },
      test("addField updates providedToTarget and addedFields") {
        val coverage = SchemaShapeValidator.MigrationCoverage.empty.addField("newField")
        assertTrue(
          coverage.providedToTarget.exists(_.toFlatString == "newField") &&
            coverage.addedFields.nonEmpty
        )
      },
      test("chaining multiple operations") {
        val coverage = SchemaShapeValidator.MigrationCoverage.empty
          .handleField("a")
          .handleField("b")
          .provideField("x")
          .provideField("y")
        assertTrue(coverage.handledFromSource.size == 2 && coverage.providedToTarget.size == 2)
      },
      test("renderByDepth returns non-empty string") {
        val coverage = SchemaShapeValidator.MigrationCoverage.empty
          .handleField("a")
          .provideField("b")
        val rendered = coverage.renderByDepth
        assertTrue(rendered.nonEmpty && rendered.contains("Depth"))
      }
    ),
    suite("SchemaShapeValidator.HierarchicalPath")(
      test("root path has empty segments") {
        val root = SchemaShapeValidator.HierarchicalPath.root
        assertTrue(root.segments.isEmpty)
      },
      test("field creates single-segment path") {
        val path = SchemaShapeValidator.HierarchicalPath.field("name")
        assertTrue(path.segments.size == 1)
      },
      test("/ operator appends segment") {
        val path = SchemaShapeValidator.HierarchicalPath.root / "a" / "b"
        assertTrue(path.segments.size == 2)
      },
      test("depth counts field segments") {
        val path = SchemaShapeValidator.HierarchicalPath.root / "a" / "b" / "c"
        assertTrue(path.depth == 3)
      },
      test("render shows <root> for empty path") {
        assertTrue(SchemaShapeValidator.HierarchicalPath.root.render == "<root>")
      },
      test("render shows segments for non-empty path") {
        val path = SchemaShapeValidator.HierarchicalPath.field("user")
        assertTrue(path.render.contains("field:user"))
      },
      test("toFlatString joins field names with dots") {
        val path = SchemaShapeValidator.HierarchicalPath.root / "a" / "b"
        assertTrue(path.toFlatString == "a.b")
      },
      test("fromDynamicOptic converts Field nodes") {
        val optic = DynamicOptic.root.field("test")
        val path  = SchemaShapeValidator.HierarchicalPath.fromDynamicOptic(optic)
        assertTrue(path.segments.size == 1)
      }
    ),
    suite("SchemaShapeValidator.PathSegment")(
      test("Field renders correctly") {
        val segment = SchemaShapeValidator.PathSegment.Field("name")
        assertTrue(segment.render == "field:name")
      },
      test("Case renders correctly") {
        val segment = SchemaShapeValidator.PathSegment.Case("Success")
        assertTrue(segment.render == "case:Success")
      },
      test("Elements renders correctly") {
        assertTrue(SchemaShapeValidator.PathSegment.Elements.render == "elements")
      },
      test("MapKeys renders correctly") {
        assertTrue(SchemaShapeValidator.PathSegment.MapKeys.render == "mapKeys")
      },
      test("MapValues renders correctly") {
        assertTrue(SchemaShapeValidator.PathSegment.MapValues.render == "mapValues")
      }
    ),
    suite("SchemaShapeValidator.SchemaShape")(
      test("empty creates empty shape") {
        val shape = SchemaShapeValidator.SchemaShape.empty
        assertTrue(shape.fieldPaths.isEmpty && shape.optionalPaths.isEmpty && shape.casePaths.isEmpty)
      },
      test("hasField checks fieldPaths") {
        val path  = SchemaShapeValidator.HierarchicalPath.field("name")
        val shape = SchemaShapeValidator.SchemaShape(Set(path), Set.empty, Set.empty)
        assertTrue(shape.hasField(path))
      },
      test("hasField with string checks flat string") {
        val path  = SchemaShapeValidator.HierarchicalPath.field("name")
        val shape = SchemaShapeValidator.SchemaShape(Set(path), Set.empty, Set.empty)
        assertTrue(shape.hasField("name"))
      },
      test("isOptional checks optionalPaths") {
        val path  = SchemaShapeValidator.HierarchicalPath.field("maybe")
        val shape = SchemaShapeValidator.SchemaShape(Set.empty, Set(path), Set.empty)
        assertTrue(shape.isOptional(path))
      },
      test("hasCase checks casePaths") {
        val path  = SchemaShapeValidator.HierarchicalPath.root / SchemaShapeValidator.PathSegment.Case("Success")
        val shape = SchemaShapeValidator.SchemaShape(Set.empty, Set.empty, Set(path))
        assertTrue(shape.hasCase(path))
      },
      test("allPaths combines all path sets") {
        val field    = SchemaShapeValidator.HierarchicalPath.field("a")
        val optional = SchemaShapeValidator.HierarchicalPath.field("b")
        val case_    = SchemaShapeValidator.HierarchicalPath.root / SchemaShapeValidator.PathSegment.Case("C")
        val shape    = SchemaShapeValidator.SchemaShape(Set(field), Set(optional), Set(case_))
        assertTrue(shape.allPaths.size == 3)
      }
    ),
    // =========================================================================
    // Resolved - additional branch coverage
    // =========================================================================
    suite("Resolved - evalDynamic branches")(
      test("Literal.evalDynamic returns value") {
        val result = Resolved.Literal(int(42)).evalDynamic
        assertTrue(result == Right(int(42)))
      },
      test("Identity.evalDynamic returns error without input") {
        val result = Resolved.Identity.evalDynamic
        assertTrue(result.isLeft)
      },
      test("Identity.evalDynamic with input returns input") {
        val result = Resolved.Identity.evalDynamic(int(42))
        assertTrue(result == Right(int(42)))
      },
      test("FieldAccess.evalDynamic returns error without input") {
        val result = Resolved.FieldAccess("name", Resolved.Identity).evalDynamic
        assertTrue(result.isLeft)
      },
      test("FieldAccess.evalDynamic with Record extracts field") {
        val record = DynamicValue.Record("name" -> str("Alice"))
        val result = Resolved.FieldAccess("name", Resolved.Identity).evalDynamic(record)
        assertTrue(result == Right(str("Alice")))
      },
      test("FieldAccess.evalDynamic with missing field returns error") {
        val record = DynamicValue.Record("other" -> str("value"))
        val result = Resolved.FieldAccess("name", Resolved.Identity).evalDynamic(record)
        assertTrue(result.isLeft)
      },
      test("FieldAccess.evalDynamic with non-Record returns error") {
        val result = Resolved.FieldAccess("name", Resolved.Identity).evalDynamic(int(42))
        assertTrue(result.isLeft)
      },
      test("Fail.evalDynamic always returns error") {
        val result = Resolved.Fail("test error").evalDynamic
        assertTrue(result.isLeft && result.swap.exists(_.contains("test error")))
      },
      test("Fail.evalDynamic with input still returns error") {
        val result = Resolved.Fail("test error").evalDynamic(int(42))
        assertTrue(result.isLeft)
      },
      test("Convert.evalDynamic returns error without input") {
        val result = Resolved.Convert("Int", "Long", Resolved.Identity).evalDynamic
        assertTrue(result.isLeft)
      },
      test("Convert.evalDynamic with input performs conversion") {
        val result = Resolved.Convert("Int", "Long", Resolved.Identity).evalDynamic(int(42))
        assertTrue(result == Right(long(42L)))
      }
    ),
    suite("Resolved - Concat")(
      test("Concat.evalDynamic returns error without input") {
        val concat = Resolved.Concat(Vector(Resolved.Identity), "")
        val result = concat.evalDynamic
        assertTrue(result.isLeft)
      },
      test("Concat.evalDynamic with Literal strings") {
        val concat = Resolved.Concat(
          Vector(Resolved.Literal(str("Hello")), Resolved.Literal(str(" ")), Resolved.Literal(str("World"))),
          ""
        )
        // Provide a dummy input since evalDynamic(input) is required for Concat
        val result = concat.evalDynamic(unit)
        assertTrue(result == Right(str("Hello World")))
      },
      test("Concat.evalDynamic with separator") {
        val concat = Resolved.Concat(
          Vector(Resolved.Literal(str("a")), Resolved.Literal(str("b")), Resolved.Literal(str("c"))),
          ","
        )
        val result = concat.evalDynamic(unit)
        assertTrue(result == Right(str("a,b,c")))
      }
    ),
    suite("Resolved - SplitString")(
      test("SplitString.evalDynamic returns error without input") {
        val result = Resolved.SplitString(Resolved.Identity, ",", 0).evalDynamic
        assertTrue(result.isLeft)
      },
      test("SplitString.evalDynamic splits and returns indexed part") {
        val result = Resolved.SplitString(Resolved.Identity, ",", 1).evalDynamic(str("a,b,c"))
        assertTrue(result == Right(str("b")))
      },
      test("SplitString.evalDynamic with out of bounds index returns error") {
        val result = Resolved.SplitString(Resolved.Identity, ",", 10).evalDynamic(str("a,b"))
        assertTrue(result.isLeft)
      }
    ),
    suite("Resolved - DefaultValue")(
      test("DefaultValue success returns Right value") {
        val dv     = Resolved.DefaultValue(Right(int(42)))
        val result = dv.evalDynamic
        assertTrue(result == Right(int(42)))
      },
      test("DefaultValue failure returns Left error") {
        val dv     = Resolved.DefaultValue(Left("no default"))
        val result = dv.evalDynamic
        assertTrue(result.isLeft)
      }
    ),
    suite("Resolved - UnwrapOption")(
      test("UnwrapOption.evalDynamic returns error without input") {
        val result = Resolved.UnwrapOption(Resolved.Identity, Resolved.Literal(str("fallback"))).evalDynamic
        assertTrue(result.isLeft)
      },
      test("UnwrapOption.evalDynamic with Some extracts value") {
        val someValue = DynamicValue.Variant("Some", DynamicValue.Record("value" -> int(42)))
        val result    = Resolved.UnwrapOption(Resolved.Identity, Resolved.Literal(int(0))).evalDynamic(someValue)
        assertTrue(result == Right(int(42)))
      },
      test("UnwrapOption.evalDynamic with None returns fallback") {
        val noneValue = DynamicValue.Variant("None", DynamicValue.Record())
        val result    = Resolved.UnwrapOption(Resolved.Identity, Resolved.Literal(int(99))).evalDynamic(noneValue)
        assertTrue(result == Right(int(99)))
      },
      test("UnwrapOption.evalDynamic with Null returns fallback") {
        val result =
          Resolved.UnwrapOption(Resolved.Identity, Resolved.Literal(str("default"))).evalDynamic(DynamicValue.Null)
        assertTrue(result == Right(str("default")))
      },
      test("UnwrapOption.evalDynamic with non-Option passes through") {
        val result = Resolved.UnwrapOption(Resolved.Identity, Resolved.Literal(int(0))).evalDynamic(int(42))
        assertTrue(result == Right(int(42)))
      },
      test("UnwrapOption.inverse returns WrapOption") {
        val unwrap  = Resolved.UnwrapOption(Resolved.Identity, Resolved.Literal(int(0)))
        val inverse = unwrap.inverse
        assertTrue(inverse.isInstanceOf[Resolved.WrapOption])
      }
    ),
    suite("Resolved - WrapOption")(
      test("WrapOption.evalDynamic returns error without input") {
        val result = Resolved.WrapOption(Resolved.Identity).evalDynamic
        assertTrue(result.isLeft)
      },
      test("WrapOption.evalDynamic wraps value in Some") {
        val result = Resolved.WrapOption(Resolved.Identity).evalDynamic(int(42))
        assertTrue(result.exists {
          case DynamicValue.Variant("Some", DynamicValue.Record(fields)) =>
            fields.exists { case (k, v) => k == "value" && v == int(42) }
          case _ => false
        })
      },
      test("WrapOption.inverse returns UnwrapOption") {
        val wrap    = Resolved.WrapOption(Resolved.Identity)
        val inverse = wrap.inverse
        assertTrue(inverse.isInstanceOf[Resolved.UnwrapOption])
      }
    ),
    suite("Resolved - OpticAccess")(
      test("OpticAccess.evalDynamic returns error without input") {
        val opticAccess = Resolved.OpticAccess(DynamicOptic.root.field("name"), Resolved.Identity)
        val result      = opticAccess.evalDynamic
        assertTrue(result.isLeft)
      },
      test("OpticAccess.evalDynamic extracts field from Record") {
        val opticAccess = Resolved.OpticAccess(DynamicOptic.root.field("name"), Resolved.Identity)
        val record      = DynamicValue.Record("name" -> str("Alice"), "age" -> int(30))
        val result      = opticAccess.evalDynamic(record)
        assertTrue(result == Right(str("Alice")))
      },
      test("OpticAccess.evalDynamic with invalid path returns error") {
        val opticAccess = Resolved.OpticAccess(DynamicOptic.root.field("missing"), Resolved.Identity)
        val record      = DynamicValue.Record("other" -> str("value"))
        val result      = opticAccess.evalDynamic(record)
        assertTrue(result.isLeft)
      },
      test("OpticAccess.evalDynamic with nested path extracts nested field") {
        val opticAccess = Resolved.OpticAccess(DynamicOptic.root.field("address").field("city"), Resolved.Identity)
        val nested      = DynamicValue.Record(
          "address" -> DynamicValue.Record("city" -> str("NYC"), "zip" -> str("10001"))
        )
        val result = opticAccess.evalDynamic(nested)
        assertTrue(result == Right(str("NYC")))
      }
    ),
    suite("Resolved - SchemaDefault")(
      test("SchemaDefault.evalDynamic returns error") {
        val result = Resolved.SchemaDefault.evalDynamic
        assertTrue(result.isLeft)
      },
      test("SchemaDefault.evalDynamic with input returns error") {
        val result = Resolved.SchemaDefault.evalDynamic(int(42))
        assertTrue(result.isLeft)
      }
    ),
    suite("Resolved - Convert inverse")(
      test("Convert.inverse swaps from and to types") {
        val convert = Resolved.Convert("Int", "Long", Resolved.Identity)
        val inverse = convert.inverse
        assertTrue(inverse match {
          case Resolved.Convert("Long", "Int", _) => true
          case _                                  => false
        })
      }
    ),
    // =========================================================================
    // Additional edge cases for maximum coverage
    // =========================================================================
    suite("DynamicOptic conversion in HierarchicalPath")(
      test("converts Case node") {
        val optic = DynamicOptic.root.caseOf("Success")
        val path  = SchemaShapeValidator.HierarchicalPath.fromDynamicOptic(optic)
        assertTrue(path.segments.exists {
          case SchemaShapeValidator.PathSegment.Case("Success") => true
          case _                                                => false
        })
      },
      test("converts Elements node") {
        val optic = DynamicOptic.root.elements
        val path  = SchemaShapeValidator.HierarchicalPath.fromDynamicOptic(optic)
        assertTrue(path.segments.exists(_ == SchemaShapeValidator.PathSegment.Elements))
      },
      test("converts MapKeys node") {
        val optic = DynamicOptic.root.mapKeys
        val path  = SchemaShapeValidator.HierarchicalPath.fromDynamicOptic(optic)
        assertTrue(path.segments.exists(_ == SchemaShapeValidator.PathSegment.MapKeys))
      },
      test("converts MapValues node") {
        val optic = DynamicOptic.root.mapValues
        val path  = SchemaShapeValidator.HierarchicalPath.fromDynamicOptic(optic)
        assertTrue(path.segments.exists(_ == SchemaShapeValidator.PathSegment.MapValues))
      }
    )
  )
}
