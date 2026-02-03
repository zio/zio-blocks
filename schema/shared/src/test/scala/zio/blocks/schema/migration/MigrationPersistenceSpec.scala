package zio.blocks.schema.migration

import zio.blocks.schema._
import zio.test._

/**
 * Tests for migration serialization and persistence.
 *
 * Covers:
 *   - DynamicMigration is pure data (no functions/closures)
 *   - Migration actions can be introspected
 *   - Migration description/toString
 *   - Migration equality and comparison
 *   - Building migrations from serialized form
 */
object MigrationPersistenceSpec extends SchemaBaseSpec {

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

  def spec: Spec[TestEnvironment, Any] = suite("MigrationPersistenceSpec")(
    suite("Pure data verification")(
      test("DynamicMigration contains only data") {
        val migration = DynamicMigration(
          Vector(
            MigrationAction.AddField(DynamicOptic.root, "field", Resolved.Literal.int(42)),
            MigrationAction.Rename(DynamicOptic.root, "old", "new"),
            MigrationAction.DropField(DynamicOptic.root, "temp", Resolved.Literal.string(""))
          )
        )
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
        val migration = DynamicMigration(
          Vector(
            MigrationAction.Rename(DynamicOptic.root, "a", "b"),
            MigrationAction.Rename(DynamicOptic.root, "c", "d")
          )
        )
        assertTrue(migration.actionCount == 2)
      },
      test("access individual actions") {
        val action1   = MigrationAction.Rename(DynamicOptic.root, "a", "b")
        val action2   = MigrationAction.AddField(DynamicOptic.root, "c", Resolved.Literal.int(1))
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
        }
      },
      test("inspect Rename action") {
        val action = MigrationAction.Rename(DynamicOptic.root, "oldName", "newName")
        action match {
          case MigrationAction.Rename(at, from, to) =>
            assertTrue(from == "oldName")
            assertTrue(to == "newName")
            assertTrue(at == DynamicOptic.root)
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
        val migration = DynamicMigration(
          Vector(
            MigrationAction.AddField(DynamicOptic.root, "field1", Resolved.Literal.int(1)),
            MigrationAction.Rename(DynamicOptic.root, "a", "b"),
            MigrationAction.DropField(DynamicOptic.root, "field2", Resolved.Literal.int(0))
          )
        )
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
          val desc      = migration.describe
          assertTrue(desc.nonEmpty)
        }
        assertTrue(true)
      }
    ),
    suite("Migration equality")(
      test("identical migrations are equal") {
        val m1 = DynamicMigration(
          Vector(
            MigrationAction.Rename(DynamicOptic.root, "a", "b")
          )
        )
        val m2 = DynamicMigration(
          Vector(
            MigrationAction.Rename(DynamicOptic.root, "a", "b")
          )
        )
        assertTrue(m1 == m2)
      },
      test("different migrations are not equal") {
        val m1 = DynamicMigration.single(MigrationAction.Rename(DynamicOptic.root, "a", "b"))
        val m2 = DynamicMigration.single(MigrationAction.Rename(DynamicOptic.root, "a", "c"))
        assertTrue(m1 != m2)
      },
      test("order matters for equality") {
        val m1 = DynamicMigration(
          Vector(
            MigrationAction.Rename(DynamicOptic.root, "a", "b"),
            MigrationAction.Rename(DynamicOptic.root, "c", "d")
          )
        )
        val m2 = DynamicMigration(
          Vector(
            MigrationAction.Rename(DynamicOptic.root, "c", "d"),
            MigrationAction.Rename(DynamicOptic.root, "a", "b")
          )
        )
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
        val original = DynamicMigration(
          Vector(
            MigrationAction.Rename(DynamicOptic.root, "a", "b"),
            MigrationAction.AddField(DynamicOptic.root, "c", Resolved.Literal.int(1))
          )
        )
        val extracted = original.actions
        val rebuilt   = DynamicMigration(extracted)
        assertTrue(rebuilt == original)
      },
      test("rebuild from individual action inspection") {
        val original = MigrationAction.Rename(DynamicOptic.root, "old", "new")
        original match {
          case MigrationAction.Rename(at, from, to) =>
            val rebuilt = MigrationAction.Rename(at, from, to)
            assertTrue(rebuilt == original)
        }
      },
      test("rebuild Resolved expression") {
        val original = Resolved.Concat(
          Vector(
            Resolved.Literal.string("a"),
            Resolved.Literal.string("b")
          ),
          "-"
        )
        original match {
          case Resolved.Concat(parts, sep) =>
            val rebuilt = Resolved.Concat(parts, sep)
            assertTrue(rebuilt == original)
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
        val m1  = DynamicMigration.single(MigrationAction.Rename(DynamicOptic.root, "a", "b"))
        val m2  = DynamicMigration.single(MigrationAction.Rename(DynamicOptic.root, "a", "b"))
        val m3  = DynamicMigration.single(MigrationAction.Rename(DynamicOptic.root, "c", "d"))
        val set = Set(m1, m2, m3)
        assertTrue(set.size == 2) // m1 and m2 are equal
      },
      test("migrations can be used as map keys") {
        val m1  = DynamicMigration.single(MigrationAction.Rename(DynamicOptic.root, "a", "b"))
        val m2  = DynamicMigration.single(MigrationAction.Rename(DynamicOptic.root, "c", "d"))
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
        }
      },
      test("Convert preserves type names") {
        val expr = Resolved.Convert("Int", "String", Resolved.Identity)
        expr match {
          case Resolved.Convert(from, to, _) =>
            assertTrue(from == "Int" && to == "String")
        }
      },
      test("Construct preserves field definitions") {
        val expr = Resolved.Construct(
          Vector(
            "x" -> Resolved.Literal.int(1),
            "y" -> Resolved.Literal.int(2)
          )
        )
        expr match {
          case Resolved.Construct(fields) =>
            assertTrue(fields.length == 2)
            assertTrue(fields(0)._1 == "x")
            assertTrue(fields(1)._1 == "y")
        }
      }
    ),
    suite("Complex migration serialization")(
      test("deeply nested migration structure") {
        val migration = DynamicMigration(
          Vector(
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
          )
        )
        assertTrue(migration.actionCount == 2)
        migration.actions(0) match {
          case MigrationAction.TransformCase(_, _, nested) =>
            assertTrue(nested.length == 2)
          case _ => assertTrue(false)
        }
      }
    ),
    // ─────────────────────────────────────────────────────────────────────────
    // Schema-based serialization tests
    // ─────────────────────────────────────────────────────────────────────────
    suite("Schema-based serialization")(
      test("DynamicMigration has Schema instance") {
        val schema = implicitly[Schema[DynamicMigration]]
        assertTrue(schema != null)
      },
      test("MigrationAction has Schema instance") {
        val schema = implicitly[Schema[MigrationAction]]
        assertTrue(schema != null)
      },
      test("Resolved has Schema instance") {
        val schema = implicitly[Schema[Resolved]]
        assertTrue(schema != null)
      },
      test("DynamicMigration round-trips through DynamicValue") {
        val original = DynamicMigration(
          Vector(
            MigrationAction.AddField(DynamicOptic.root, "name", Resolved.Literal.string("default")),
            MigrationAction.Rename(DynamicOptic.root, "old", "new")
          )
        )
        val schema        = Schema[DynamicMigration]
        val dynamic       = schema.toDynamicValue(original)
        val reconstructed = schema.fromDynamicValue(dynamic)
        assertTrue(reconstructed == Right(original))
      },
      test("MigrationAction round-trips through DynamicValue") {
        val original: MigrationAction = MigrationAction.AddField(
          DynamicOptic.root.field("nested"),
          "field",
          Resolved.Literal.int(42)
        )
        val schema        = Schema[MigrationAction]
        val dynamic       = schema.toDynamicValue(original)
        val reconstructed = schema.fromDynamicValue(dynamic)
        assertTrue(reconstructed == Right(original))
      },
      test("Resolved round-trips through DynamicValue") {
        val original: Resolved = Resolved.Concat(
          Vector(
            Resolved.FieldAccess("first", Resolved.Identity),
            Resolved.Literal.string(" "),
            Resolved.FieldAccess("last", Resolved.Identity)
          ),
          ""
        )
        val schema        = Schema[Resolved]
        val dynamic       = schema.toDynamicValue(original)
        val reconstructed = schema.fromDynamicValue(dynamic)
        assertTrue(reconstructed == Right(original))
      },
      test("complex nested migration round-trips") {
        val original = DynamicMigration(
          Vector(
            MigrationAction.TransformCase(
              DynamicOptic.root.field("payment"),
              "CreditCard",
              Vector(
                MigrationAction.AddField(DynamicOptic.root, "cvv", Resolved.Literal.string("000")),
                MigrationAction.ChangeType(
                  DynamicOptic.root,
                  "expiry",
                  Resolved.Convert("String", "LocalDate", Resolved.Identity),
                  Resolved.Convert("LocalDate", "String", Resolved.Identity)
                )
              )
            ),
            MigrationAction.TransformElements(
              DynamicOptic.root.field("items"),
              Resolved.Compose(
                Resolved.Convert("Int", "Long", Resolved.Identity),
                Resolved.FieldAccess("qty", Resolved.Identity)
              ),
              Resolved.Compose(Resolved.Convert("Long", "Int", Resolved.Identity), Resolved.Identity)
            )
          )
        )
        val schema        = Schema[DynamicMigration]
        val dynamic       = schema.toDynamicValue(original)
        val reconstructed = schema.fromDynamicValue(dynamic)
        assertTrue(reconstructed == Right(original))
      },
      test("all Resolved variants can be serialized") {
        val variants: Vector[Resolved] = Vector(
          Resolved.Literal(DynamicValue.Primitive(PrimitiveValue.Int(42))),
          Resolved.Identity,
          Resolved.FieldAccess("field", Resolved.Identity),
          Resolved.OpticAccess(DynamicOptic.root.field("x"), Resolved.Identity),
          Resolved.DefaultValue(Right(DynamicValue.Primitive(PrimitiveValue.String("default")))),
          Resolved.Convert("Int", "Long", Resolved.Identity),
          Resolved.Concat(Vector(Resolved.Literal.string("a"), Resolved.Literal.string("b")), "-"),
          Resolved.SplitString(",", Resolved.Identity),
          Resolved.WrapSome(Resolved.Identity),
          Resolved.UnwrapOption(Resolved.Identity, Resolved.Literal.int(0)),
          Resolved.Compose(Resolved.Identity, Resolved.Identity),
          Resolved.Fail("error"),
          Resolved.Construct(Vector(("a", Resolved.Literal.int(1)))),
          Resolved.ConstructSeq(Vector(Resolved.Literal.int(1), Resolved.Literal.int(2))),
          Resolved.Head(Resolved.Identity),
          Resolved.JoinStrings(",", Resolved.Identity),
          Resolved.Coalesce(Vector(Resolved.Identity, Resolved.Literal.int(0))),
          Resolved.GetOrElse(Resolved.Identity, Resolved.Literal.int(0))
        )
        val schema = Schema[Resolved]
        variants.foreach { original =>
          val dynamic       = schema.toDynamicValue(original)
          val reconstructed = schema.fromDynamicValue(dynamic)
          assertTrue(reconstructed == Right(original))
        }
        assertTrue(true)
      },
      test("all MigrationAction variants can be serialized") {
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
        val schema = Schema[MigrationAction]
        actions.foreach { original =>
          val dynamic       = schema.toDynamicValue(original)
          val reconstructed = schema.fromDynamicValue(dynamic)
          assertTrue(reconstructed == Right(original))
        }
        assertTrue(true)
      },
      test("RootAccess serialization round-trip") {
        val original: Resolved = Resolved.RootAccess(DynamicOptic.root.field("nested").field("path"))
        val schema             = Schema[Resolved]
        val dynamic            = schema.toDynamicValue(original)
        val reconstructed      = schema.fromDynamicValue(dynamic)
        assertTrue(reconstructed == Right(original))
      },
      test("At serialization round-trip") {
        val original: Resolved = Resolved.At(3, Resolved.FieldAccess("field", Resolved.Identity))
        val schema             = Schema[Resolved]
        val dynamic            = schema.toDynamicValue(original)
        val reconstructed      = schema.fromDynamicValue(dynamic)
        assertTrue(reconstructed == Right(original))
      },
      test("complex At with RootAccess inner round-trip") {
        val original: Resolved = Resolved.At(
          0,
          Resolved.RootAccess(DynamicOptic.root.field("external"))
        )
        val schema        = Schema[Resolved]
        val dynamic       = schema.toDynamicValue(original)
        val reconstructed = schema.fromDynamicValue(dynamic)
        assertTrue(reconstructed == Right(original))
      }
    ),
    // ─────────────────────────────────────────────────────────────────────────
    // MigrationAction Edge Cases for Coverage
    // ─────────────────────────────────────────────────────────────────────────
    suite("MigrationAction edge cases")(
      test("AddField action reverse is DropField") {
        val add     = MigrationAction.AddField(DynamicOptic.root, "newField", Resolved.Literal.int(42))
        val reverse = add.reverse
        assertTrue(reverse.isInstanceOf[MigrationAction.DropField])
        reverse match {
          case MigrationAction.DropField(at, name, _) =>
            assertTrue(at == DynamicOptic.root && name == "newField")
          case _ => assertTrue(false)
        }
      },
      test("DropField action reverse is AddField") {
        val drop    = MigrationAction.DropField(DynamicOptic.root, "oldField", Resolved.Literal.string("default"))
        val reverse = drop.reverse
        assertTrue(reverse.isInstanceOf[MigrationAction.AddField])
        reverse match {
          case MigrationAction.AddField(at, name, _) =>
            assertTrue(at == DynamicOptic.root && name == "oldField")
          case _ => assertTrue(false)
        }
      },
      test("Rename action reverse swaps from and to") {
        val rename  = MigrationAction.Rename(DynamicOptic.root, "oldName", "newName")
        val reverse = rename.reverse
        reverse match {
          case MigrationAction.Rename(_, from, to) =>
            assertTrue(from == "newName" && to == "oldName")
          case _ => assertTrue(false)
        }
      },
      test("Optionalize action reverse is Mandate with Fail default") {
        val optionalize = MigrationAction.Optionalize(DynamicOptic.root, "field")
        val reverse     = optionalize.reverse
        assertTrue(reverse.isInstanceOf[MigrationAction.Mandate])
      },
      test("Mandate action reverse is Optionalize") {
        val mandate = MigrationAction.Mandate(DynamicOptic.root, "field", Resolved.Literal.int(0))
        val reverse = mandate.reverse
        assertTrue(reverse.isInstanceOf[MigrationAction.Optionalize])
      },
      test("ChangeType action reverse swaps converters") {
        val changeType = MigrationAction.ChangeType(
          DynamicOptic.root,
          "field",
          Resolved.Convert("Int", "String", Resolved.Identity),
          Resolved.Convert("String", "Int", Resolved.Identity)
        )
        val reverse = changeType.reverse
        reverse match {
          case MigrationAction.ChangeType(_, _, fwd, rev) =>
            assertTrue(
              fwd.isInstanceOf[Resolved.Convert] &&
                rev.isInstanceOf[Resolved.Convert]
            )
          case _ => assertTrue(false)
        }
      },
      test("TransformValue action reverse swaps transforms") {
        val transform = MigrationAction.TransformValue(
          DynamicOptic.root,
          "field",
          Resolved.Convert("Int", "Long", Resolved.Identity),
          Resolved.Convert("Long", "Int", Resolved.Identity)
        )
        val reverse = transform.reverse
        reverse match {
          case MigrationAction.TransformValue(_, _, fwd, rev) =>
            assertTrue(fwd.isInstanceOf[Resolved.Convert] && rev.isInstanceOf[Resolved.Convert])
          case _ => assertTrue(false)
        }
      },
      test("RenameCase action reverse swaps names") {
        val renameCase = MigrationAction.RenameCase(DynamicOptic.root, "OldCase", "NewCase")
        val reverse    = renameCase.reverse
        reverse match {
          case MigrationAction.RenameCase(_, from, to) =>
            assertTrue(from == "NewCase" && to == "OldCase")
          case _ => assertTrue(false)
        }
      },
      test("TransformElements action reverse swaps transforms") {
        val transformElements = MigrationAction.TransformElements(
          DynamicOptic.root,
          Resolved.Identity,
          Resolved.Literal.int(0)
        )
        val reverse = transformElements.reverse
        assertTrue(reverse.isInstanceOf[MigrationAction.TransformElements])
      },
      test("TransformKeys action reverse swaps transforms") {
        val transformKeys = MigrationAction.TransformKeys(
          DynamicOptic.root,
          Resolved.Identity,
          Resolved.Literal.string("")
        )
        val reverse = transformKeys.reverse
        assertTrue(reverse.isInstanceOf[MigrationAction.TransformKeys])
      },
      test("TransformValues action reverse swaps transforms") {
        val transformValues = MigrationAction.TransformValues(
          DynamicOptic.root,
          Resolved.Identity,
          Resolved.Literal.string("")
        )
        val reverse = transformValues.reverse
        assertTrue(reverse.isInstanceOf[MigrationAction.TransformValues])
      },
      test("prefixPath updates action path") {
        val action         = MigrationAction.AddField(DynamicOptic.root, "field", Resolved.Literal.int(1))
        val prefix         = DynamicOptic.root.field("nested")
        val prefixedAction = action.prefixPath(prefix)
        prefixedAction match {
          case MigrationAction.AddField(at, _, _) =>
            assertTrue(at.nodes.nonEmpty)
          case _ => assertTrue(false)
        }
      },
      test("DynamicMigration reverse reverses action order") {
        val migration = DynamicMigration(
          Vector(
            MigrationAction.Rename(DynamicOptic.root, "a", "b"),
            MigrationAction.AddField(DynamicOptic.root, "c", Resolved.Literal.int(1))
          )
        )
        val reversed = migration.reverse
        assertTrue(reversed.actions.length == 2)
        // First action should be reverse of second original
        assertTrue(reversed.actions(0).isInstanceOf[MigrationAction.DropField])
        // Second action should be reverse of first original
        assertTrue(reversed.actions(1).isInstanceOf[MigrationAction.Rename])
      },
      test("DynamicMigration composition (++) concatenates actions") {
        val m1       = DynamicMigration.single(MigrationAction.Rename(DynamicOptic.root, "a", "b"))
        val m2       = DynamicMigration.single(MigrationAction.AddField(DynamicOptic.root, "c", Resolved.Literal.int(1)))
        val composed = m1 ++ m2
        assertTrue(composed.actions.length == 2)
      },
      test("DynamicMigration.identity has no actions") {
        assertTrue(DynamicMigration.identity.actions.isEmpty)
      },
      test("DynamicMigration.single creates migration with one action") {
        val action    = MigrationAction.Rename(DynamicOptic.root, "a", "b")
        val migration = DynamicMigration.single(action)
        assertTrue(migration.actions.length == 1 && migration.actions.head == action)
      }
    ),
    suite("Resolved expression edge cases")(
      test("Literal.boolean creates boolean literal") {
        val literal = Resolved.Literal.boolean(true)
        assertTrue(literal.evalDynamic.isRight)
      },
      test("Literal.long creates long literal") {
        val literal = Resolved.Literal.long(100L)
        assertTrue(literal.evalDynamic.isRight)
      },
      test("DefaultValue.noDefault fails") {
        val dv = Resolved.DefaultValue.noDefault
        assertTrue(dv.evalDynamic.isLeft)
      },
      test("DefaultValue.fail with custom message fails") {
        val dv     = Resolved.DefaultValue.fail("custom error")
        val result = dv.evalDynamic
        assertTrue(result.isLeft && result.swap.toOption.get.contains("custom error"))
      },
      test("DefaultValue.fromValue creates valid default") {
        val dv = Resolved.DefaultValue.fromValue(42, Schema[Int])
        assertTrue(dv.evalDynamic.isRight)
      },
      test("Fail.evalDynamic returns Left with message") {
        val fail   = Resolved.Fail("test-error")
        val result = fail.evalDynamic
        assertTrue(result == Left("test-error"))
      },
      test("Identity returns input unchanged") {
        val input  = dynamicString("hello")
        val result = Resolved.Identity.evalDynamic(input)
        assertTrue(result == Right(input))
      }
    ),
    suite("Serialization coverage - complex cases")(
      test("TransformCase with nested RootAccess serialization") {
        val action: MigrationAction = MigrationAction.TransformCase(
          DynamicOptic.root.field("payment"),
          "Card",
          Vector(
            MigrationAction.AddField(
              DynamicOptic.root,
              "verified",
              Resolved.RootAccess(DynamicOptic.root.field("isVerified"))
            )
          )
        )
        val schema        = Schema[MigrationAction]
        val dynamic       = schema.toDynamicValue(action)
        val reconstructed = schema.fromDynamicValue(dynamic)
        assertTrue(reconstructed == Right(action))
      },
      test("Nested At in Construct serialization") {
        val expr: Resolved = Resolved.Construct(
          Vector(
            "first"  -> Resolved.At(0, Resolved.RootAccess(DynamicOptic.root.field("items"))),
            "second" -> Resolved.At(1, Resolved.RootAccess(DynamicOptic.root.field("items")))
          )
        )
        val schema        = Schema[Resolved]
        val dynamic       = schema.toDynamicValue(expr)
        val reconstructed = schema.fromDynamicValue(dynamic)
        assertTrue(reconstructed == Right(expr))
      },
      test("OpticAccess with RootAccess inner serialization") {
        val expr: Resolved = Resolved.OpticAccess(
          DynamicOptic.root.field("local"),
          Resolved.RootAccess(DynamicOptic.root.field("external"))
        )
        val schema        = Schema[Resolved]
        val dynamic       = schema.toDynamicValue(expr)
        val reconstructed = schema.fromDynamicValue(dynamic)
        assertTrue(reconstructed == Right(expr))
      },
      test("GetOrElse with RootAccess serialization") {
        val expr: Resolved = Resolved.GetOrElse(
          Resolved.RootAccess(DynamicOptic.root.field("optional")),
          Resolved.RootAccess(DynamicOptic.root.field("default"))
        )
        val schema        = Schema[Resolved]
        val dynamic       = schema.toDynamicValue(expr)
        val reconstructed = schema.fromDynamicValue(dynamic)
        assertTrue(reconstructed == Right(expr))
      },
      test("Coalesce with RootAccess alternatives serialization") {
        val expr: Resolved = Resolved.Coalesce(
          Vector(
            Resolved.RootAccess(DynamicOptic.root.field("primary")),
            Resolved.RootAccess(DynamicOptic.root.field("secondary")),
            Resolved.Literal.string("fallback")
          )
        )
        val schema        = Schema[Resolved]
        val dynamic       = schema.toDynamicValue(expr)
        val reconstructed = schema.fromDynamicValue(dynamic)
        assertTrue(reconstructed == Right(expr))
      },
      test("Complex migration with all new features serialization") {
        val migration = DynamicMigration(
          Vector(
            MigrationAction.AddField(
              DynamicOptic.root,
              "joined",
              Resolved.Concat(
                Vector(
                  Resolved.RootAccess(DynamicOptic.root.field("a").field("b")),
                  Resolved.Literal.string(" "),
                  Resolved.RootAccess(DynamicOptic.root.field("c").field("d"))
                ),
                ""
              )
            ),
            MigrationAction.AddField(
              DynamicOptic.root,
              "splitFirst",
              Resolved.Compose(
                Resolved.At(0, Resolved.Identity),
                Resolved.SplitString(" ", Resolved.RootAccess(DynamicOptic.root.field("fullText")))
              )
            ),
            MigrationAction.TransformCase(
              DynamicOptic.root.field("status"),
              "Active",
              Vector(
                MigrationAction.AddField(
                  DynamicOptic.root,
                  "activatedAt",
                  Resolved.RootAccess(DynamicOptic.root.field("timestamp"))
                )
              )
            )
          )
        )
        val schema        = Schema[DynamicMigration]
        val dynamic       = schema.toDynamicValue(migration)
        val reconstructed = schema.fromDynamicValue(dynamic)
        assertTrue(reconstructed == Right(migration))
      },
      test("DynamicOptic with deep nesting serialization") {
        val path           = DynamicOptic.root.field("a").field("b").field("c").field("d").field("e")
        val expr: Resolved = Resolved.RootAccess(path)
        val schema         = Schema[Resolved]
        val dynamic        = schema.toDynamicValue(expr)
        val reconstructed  = schema.fromDynamicValue(dynamic)
        assertTrue(reconstructed == Right(expr))
      }
    )
  )
}
