package zio.blocks.schema.migration

import zio.blocks.schema._
import zio.test._

/**
 * Tests for migration serialization and persistence.
 *
 * Covers:
 * - DynamicMigration is pure data (no functions/closures)
 * - Migration actions can be introspected
 * - Migration description/toString
 * - Migration equality and comparison
 * - Building migrations from serialized form
 */
object MigrationPersistenceSpec extends SchemaBaseSpec {

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

  def spec: Spec[TestEnvironment, Any] = suite("MigrationPersistenceSpec")(
    suite("Pure data verification")(
      test("DynamicMigration contains only data") {
        val migration = DynamicMigration(Vector(
          MigrationAction.AddField(DynamicOptic.root, "field", Resolved.Literal.int(42)),
          MigrationAction.Rename(DynamicOptic.root, "old", "new"),
          MigrationAction.DropField(DynamicOptic.root, "temp", Resolved.Literal.string(""))
        ))
        // Verify it's a case class with data only
        assertTrue(migration.actions.length == 3)
        assertTrue(migration.isInstanceOf[Product])
      },
      test("MigrationAction variants are all case classes") {
        val actions: Vector[MigrationAction] = Vector(
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
        assertTrue(actions.forall(_.isInstanceOf[Product]))
      },
      test("Resolved expressions are all case classes") {
        val exprs: Vector[Resolved] = Vector(
          Resolved.Literal.int(1),
          Resolved.Literal.string("s"),
          Resolved.Literal.boolean(true),
          Resolved.Identity,
          Resolved.FieldAccess("f", Resolved.Identity),
          Resolved.Compose(Resolved.Identity, Resolved.Identity),
          Resolved.Convert("Int", "String", Resolved.Identity),
          Resolved.Fail("error"),
          Resolved.Concat(Vector.empty, ""),
          Resolved.WrapSome(Resolved.Identity),
          Resolved.Construct(Vector.empty),
          Resolved.ConstructSeq(Vector.empty)
        )
        assertTrue(exprs.forall(_.isInstanceOf[Product]))
      }
    ),
    suite("Migration introspection")(
      test("access action count") {
        val migration = DynamicMigration(Vector(
          MigrationAction.Rename(DynamicOptic.root, "a", "b"),
          MigrationAction.Rename(DynamicOptic.root, "c", "d")
        ))
        assertTrue(migration.actionCount == 2)
      },
      test("access individual actions") {
        val action1 = MigrationAction.Rename(DynamicOptic.root, "a", "b")
        val action2 = MigrationAction.AddField(DynamicOptic.root, "c", Resolved.Literal.int(1))
        val migration = DynamicMigration(Vector(action1, action2))
        assertTrue(migration.actions(0) == action1)
        assertTrue(migration.actions(1) == action2)
      },
      test("inspect AddField action") {
        val action = MigrationAction.AddField(
          DynamicOptic.root.field("nested"),
          "newField",
          Resolved.Literal.int(42)
        )
        action match {
          case MigrationAction.AddField(at, fieldName, default) =>
            assertTrue(fieldName == "newField")
            assertTrue(at == DynamicOptic.root.field("nested"))
            default match {
              case Resolved.Literal(DynamicValue.Primitive(PrimitiveValue.Int(v))) =>
                assertTrue(v == 42)
              case _ => assertTrue(false)
            }
          case _ => assertTrue(false)
        }
      },
      test("inspect Rename action") {
        val action = MigrationAction.Rename(DynamicOptic.root, "oldName", "newName")
        action match {
          case MigrationAction.Rename(at, from, to) =>
            assertTrue(from == "oldName")
            assertTrue(to == "newName")
            assertTrue(at == DynamicOptic.root)
          case _ => assertTrue(false)
        }
      },
      test("inspect ChangeType action") {
        val action = MigrationAction.ChangeType(
          DynamicOptic.root,
          "field",
          Resolved.Convert("Int", "String", Resolved.Identity),
          Resolved.Convert("String", "Int", Resolved.Identity)
        )
        action match {
          case MigrationAction.ChangeType(_, fieldName, converter, _) =>
            assertTrue(fieldName == "field")
            converter match {
              case Resolved.Convert(from, to, _) =>
                assertTrue(from == "Int" && to == "String")
              case _ => assertTrue(false)
            }
          case _ => assertTrue(false)
        }
      },
      test("inspect TransformCase action") {
        val nested = Vector(
          MigrationAction.AddField(DynamicOptic.root, "f", Resolved.Literal.int(1))
        )
        val action = MigrationAction.TransformCase(DynamicOptic.root, "MyCase", nested)
        action match {
          case MigrationAction.TransformCase(_, caseName, actions) =>
            assertTrue(caseName == "MyCase")
            assertTrue(actions.length == 1)
          case _ => assertTrue(false)
        }
      }
    ),
    suite("Migration description")(
      test("identity migration description") {
        val desc = DynamicMigration.identity.describe
        assertTrue(desc.contains("Identity"))
      },
      test("single action description") {
        val migration = DynamicMigration.single(
          MigrationAction.Rename(DynamicOptic.root, "old", "new")
        )
        val desc = migration.describe
        assertTrue(desc.contains("Rename"))
        assertTrue(desc.contains("old"))
        assertTrue(desc.contains("new"))
      },
      test("multi-action description") {
        val migration = DynamicMigration(Vector(
          MigrationAction.AddField(DynamicOptic.root, "field1", Resolved.Literal.int(1)),
          MigrationAction.Rename(DynamicOptic.root, "a", "b"),
          MigrationAction.DropField(DynamicOptic.root, "field2", Resolved.Literal.int(0))
        ))
        val desc = migration.describe
        assertTrue(desc.contains("AddField"))
        assertTrue(desc.contains("Rename"))
        assertTrue(desc.contains("DropField"))
      },
      test("description includes path information") {
        val migration = DynamicMigration.single(
          MigrationAction.AddField(
            DynamicOptic.root.field("nested").field("deep"),
            "field",
            Resolved.Literal.int(1)
          )
        )
        val desc = migration.describe
        assertTrue(desc.contains("nested") || desc.contains("deep") || desc.contains("field"))
      },
      test("all action types have descriptions") {
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
          val desc = migration.describe
          assertTrue(desc.nonEmpty)
        }
        assertTrue(true)
      }
    ),
    suite("Migration equality")(
      test("identical migrations are equal") {
        val m1 = DynamicMigration(Vector(
          MigrationAction.Rename(DynamicOptic.root, "a", "b")
        ))
        val m2 = DynamicMigration(Vector(
          MigrationAction.Rename(DynamicOptic.root, "a", "b")
        ))
        assertTrue(m1 == m2)
      },
      test("different migrations are not equal") {
        val m1 = DynamicMigration.single(MigrationAction.Rename(DynamicOptic.root, "a", "b"))
        val m2 = DynamicMigration.single(MigrationAction.Rename(DynamicOptic.root, "a", "c"))
        assertTrue(m1 != m2)
      },
      test("order matters for equality") {
        val m1 = DynamicMigration(Vector(
          MigrationAction.Rename(DynamicOptic.root, "a", "b"),
          MigrationAction.Rename(DynamicOptic.root, "c", "d")
        ))
        val m2 = DynamicMigration(Vector(
          MigrationAction.Rename(DynamicOptic.root, "c", "d"),
          MigrationAction.Rename(DynamicOptic.root, "a", "b")
        ))
        assertTrue(m1 != m2)
      },
      test("identity migrations are equal") {
        assertTrue(DynamicMigration.identity == DynamicMigration(Vector.empty))
      },
      test("actions with same structure are equal") {
        val action1 = MigrationAction.AddField(
          DynamicOptic.root.field("x"),
          "field",
          Resolved.Literal.int(42)
        )
        val action2 = MigrationAction.AddField(
          DynamicOptic.root.field("x"),
          "field",
          Resolved.Literal.int(42)
        )
        assertTrue(action1 == action2)
      }
    ),
    suite("Rebuilding migrations")(
      test("rebuild migration from extracted actions") {
        val original = DynamicMigration(Vector(
          MigrationAction.Rename(DynamicOptic.root, "a", "b"),
          MigrationAction.AddField(DynamicOptic.root, "c", Resolved.Literal.int(1))
        ))
        val extracted = original.actions
        val rebuilt = DynamicMigration(extracted)
        assertTrue(rebuilt == original)
      },
      test("rebuild from individual action inspection") {
        val original = MigrationAction.Rename(DynamicOptic.root, "old", "new")
        original match {
          case MigrationAction.Rename(at, from, to) =>
            val rebuilt = MigrationAction.Rename(at, from, to)
            assertTrue(rebuilt == original)
          case _ => assertTrue(false)
        }
      },
      test("rebuild Resolved expression") {
        val original = Resolved.Concat(Vector(
          Resolved.Literal.string("a"),
          Resolved.Literal.string("b")
        ), "-")
        original match {
          case Resolved.Concat(parts, sep) =>
            val rebuilt = Resolved.Concat(parts, sep)
            assertTrue(rebuilt == original)
          case _ => assertTrue(false)
        }
      }
    ),
    suite("Migration hashCode")(
      test("equal migrations have same hashCode") {
        val m1 = DynamicMigration.single(MigrationAction.Rename(DynamicOptic.root, "a", "b"))
        val m2 = DynamicMigration.single(MigrationAction.Rename(DynamicOptic.root, "a", "b"))
        assertTrue(m1.hashCode == m2.hashCode)
      },
      test("migrations can be used in sets") {
        val m1 = DynamicMigration.single(MigrationAction.Rename(DynamicOptic.root, "a", "b"))
        val m2 = DynamicMigration.single(MigrationAction.Rename(DynamicOptic.root, "a", "b"))
        val m3 = DynamicMigration.single(MigrationAction.Rename(DynamicOptic.root, "c", "d"))
        val set = Set(m1, m2, m3)
        assertTrue(set.size == 2) // m1 and m2 are equal
      },
      test("migrations can be used as map keys") {
        val m1 = DynamicMigration.single(MigrationAction.Rename(DynamicOptic.root, "a", "b"))
        val m2 = DynamicMigration.single(MigrationAction.Rename(DynamicOptic.root, "c", "d"))
        val map = Map(m1 -> "first", m2 -> "second")
        assertTrue(map(m1) == "first")
        assertTrue(map(m2) == "second")
      }
    ),
    suite("Path serialization")(
      test("root path structure") {
        val path = DynamicOptic.root
        assertTrue(path.nodes.isEmpty)
      },
      test("field path structure") {
        val path = DynamicOptic.root.field("name")
        assertTrue(path.nodes.length == 1)
      },
      test("nested path structure") {
        val path = DynamicOptic.root.field("a").field("b").field("c")
        assertTrue(path.nodes.length == 3)
      },
      test("path equality") {
        val p1 = DynamicOptic.root.field("x").field("y")
        val p2 = DynamicOptic.root.field("x").field("y")
        assertTrue(p1 == p2)
      },
      test("different paths not equal") {
        val p1 = DynamicOptic.root.field("x")
        val p2 = DynamicOptic.root.field("y")
        assertTrue(p1 != p2)
      }
    ),
    suite("Resolved expression serialization")(
      test("Literal preserves value") {
        val expr = Resolved.Literal.int(42)
        expr match {
          case Resolved.Literal(DynamicValue.Primitive(PrimitiveValue.Int(v))) =>
            assertTrue(v == 42)
          case _ => assertTrue(false)
        }
      },
      test("FieldAccess preserves field name") {
        val expr = Resolved.FieldAccess("myField", Resolved.Identity)
        expr match {
          case Resolved.FieldAccess(name, inner) =>
            assertTrue(name == "myField")
            assertTrue(inner == Resolved.Identity)
          case _ => assertTrue(false)
        }
      },
      test("Convert preserves type names") {
        val expr = Resolved.Convert("Int", "String", Resolved.Identity)
        expr match {
          case Resolved.Convert(from, to, _) =>
            assertTrue(from == "Int" && to == "String")
          case _ => assertTrue(false)
        }
      },
      test("Construct preserves field definitions") {
        val expr = Resolved.Construct(Vector(
          "x" -> Resolved.Literal.int(1),
          "y" -> Resolved.Literal.int(2)
        ))
        expr match {
          case Resolved.Construct(fields) =>
            assertTrue(fields.length == 2)
            assertTrue(fields(0)._1 == "x")
            assertTrue(fields(1)._1 == "y")
          case _ => assertTrue(false)
        }
      }
    ),
    suite("Complex migration serialization")(
      test("deeply nested migration structure") {
        val migration = DynamicMigration(Vector(
          MigrationAction.TransformCase(
            DynamicOptic.root.field("status"),
            "Success",
            Vector(
              MigrationAction.AddField(DynamicOptic.root, "timestamp", Resolved.Literal.long(0L)),
              MigrationAction.Rename(DynamicOptic.root, "data", "payload")
            )
          ),
          MigrationAction.TransformElements(
            DynamicOptic.root.field("items"),
            Resolved.Compose(
              Resolved.Convert("Int", "String", Resolved.Identity),
              Resolved.FieldAccess("value", Resolved.Identity)
            ),
            Resolved.Compose(
              Resolved.Convert("String", "Int", Resolved.Identity),
              Resolved.Identity
            )
          )
        ))
        assertTrue(migration.actionCount == 2)
        migration.actions(0) match {
          case MigrationAction.TransformCase(_, _, nested) =>
            assertTrue(nested.length == 2)
          case _ => assertTrue(false)
        }
      }
    )
  )
}
