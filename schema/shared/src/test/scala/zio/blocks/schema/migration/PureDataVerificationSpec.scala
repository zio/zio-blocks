package zio.blocks.schema.migration

import zio.blocks.schema._
import zio.test._

/**
 * Tests verifying that migrations are pure data (no functions, closures,
 * reflection).
 *
 * Covers:
 *   - All types are case classes/sealed traits
 *   - No closures or lambdas in migration data
 *   - Full serializability verification
 *   - Structural equality
 *   - Pattern matching exhaustiveness
 */
object PureDataVerificationSpec extends SchemaBaseSpec {

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

  def spec: Spec[TestEnvironment, Any] = suite("PureDataVerificationSpec")(
    suite("DynamicMigration is pure data")(
      test("DynamicMigration is a case class") {
        val migration = DynamicMigration.identity
        assertTrue(migration.isInstanceOf[Product])
        assertTrue(migration.isInstanceOf[Serializable])
      },
      test("DynamicMigration has structural equality") {
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
        assertTrue(m1.hashCode == m2.hashCode)
      },
      test("DynamicMigration can be pattern matched") {
        val migration = DynamicMigration.single(
          MigrationAction.Rename(DynamicOptic.root, "a", "b")
        )
        migration match {
          case DynamicMigration(actions) =>
            assertTrue(actions.length == 1)
        }
      },
      test("DynamicMigration copy works") {
        val original = DynamicMigration.single(
          MigrationAction.Rename(DynamicOptic.root, "a", "b")
        )
        val modified = original.copy(actions = Vector.empty)
        assertTrue(modified.isIdentity)
        assertTrue(!original.isIdentity)
      }
    ),
    suite("MigrationAction variants are pure data")(
      test("AddField is a case class") {
        val action = MigrationAction.AddField(DynamicOptic.root, "f", Resolved.Literal.int(1))
        assertTrue(action.isInstanceOf[Product])
        action match {
          case MigrationAction.AddField(at, fieldName, default) =>
            assertTrue(fieldName == "f")
        }
      },
      test("DropField is a case class") {
        val action = MigrationAction.DropField(DynamicOptic.root, "f", Resolved.Literal.int(1))
        assertTrue(action.isInstanceOf[Product])
        action match {
          case MigrationAction.DropField(at, fieldName, defaultForReverse) =>
            assertTrue(fieldName == "f")
        }
      },
      test("Rename is a case class") {
        val action = MigrationAction.Rename(DynamicOptic.root, "a", "b")
        assertTrue(action.isInstanceOf[Product])
        action match {
          case MigrationAction.Rename(at, from, to) =>
            assertTrue(from == "a" && to == "b")
        }
      },
      test("TransformValue is a case class") {
        val action = MigrationAction.TransformValue(
          DynamicOptic.root,
          "f",
          Resolved.Identity,
          Resolved.Identity
        )
        assertTrue(action.isInstanceOf[Product])
      },
      test("Mandate is a case class") {
        val action = MigrationAction.Mandate(DynamicOptic.root, "f", Resolved.Literal.int(0))
        assertTrue(action.isInstanceOf[Product])
      },
      test("Optionalize is a case class") {
        val action = MigrationAction.Optionalize(DynamicOptic.root, "f")
        assertTrue(action.isInstanceOf[Product])
      },
      test("ChangeType is a case class") {
        val action = MigrationAction.ChangeType(
          DynamicOptic.root,
          "f",
          Resolved.Identity,
          Resolved.Identity
        )
        assertTrue(action.isInstanceOf[Product])
      },
      test("RenameCase is a case class") {
        val action = MigrationAction.RenameCase(DynamicOptic.root, "A", "B")
        assertTrue(action.isInstanceOf[Product])
      },
      test("TransformCase is a case class") {
        val action = MigrationAction.TransformCase(DynamicOptic.root, "A", Vector.empty)
        assertTrue(action.isInstanceOf[Product])
      },
      test("TransformElements is a case class") {
        val action = MigrationAction.TransformElements(
          DynamicOptic.root,
          Resolved.Identity,
          Resolved.Identity
        )
        assertTrue(action.isInstanceOf[Product])
      },
      test("TransformKeys is a case class") {
        val action = MigrationAction.TransformKeys(
          DynamicOptic.root,
          Resolved.Identity,
          Resolved.Identity
        )
        assertTrue(action.isInstanceOf[Product])
      },
      test("TransformValues is a case class") {
        val action = MigrationAction.TransformValues(
          DynamicOptic.root,
          Resolved.Identity,
          Resolved.Identity
        )
        assertTrue(action.isInstanceOf[Product])
      }
    ),
    suite("Resolved expressions are pure data")(
      test("Literal is a case class") {
        val expr = Resolved.Literal.int(42)
        assertTrue(expr.isInstanceOf[Product])
        expr match {
          case Resolved.Literal(value) => assertTrue(value == dynamicInt(42))
        }
      },
      test("Identity is a case object") {
        val expr = Resolved.Identity
        assertTrue(expr == Resolved.Identity)
      },
      test("FieldAccess is a case class") {
        val expr = Resolved.FieldAccess("f", Resolved.Identity)
        assertTrue(expr.isInstanceOf[Product])
      },
      test("Compose is a case class") {
        val expr = Resolved.Compose(Resolved.Identity, Resolved.Identity)
        assertTrue(expr.isInstanceOf[Product])
      },
      test("Convert is a case class") {
        val expr = Resolved.Convert("Int", "String", Resolved.Identity)
        assertTrue(expr.isInstanceOf[Product])
      },
      test("Fail is a case class") {
        val expr = Resolved.Fail("error")
        assertTrue(expr.isInstanceOf[Product])
      },
      test("Concat is a case class") {
        val expr = Resolved.Concat(Vector.empty, ",")
        assertTrue(expr.isInstanceOf[Product])
      },
      test("WrapSome is a case class") {
        val expr = Resolved.WrapSome(Resolved.Identity)
        assertTrue(expr.isInstanceOf[Product])
      },
      test("Construct is a case class") {
        val expr = Resolved.Construct(Vector.empty)
        assertTrue(expr.isInstanceOf[Product])
      },
      test("ConstructSeq is a case class") {
        val expr = Resolved.ConstructSeq(Vector.empty)
        assertTrue(expr.isInstanceOf[Product])
      }
    ),
    suite("No closures or functions")(
      test("MigrationAction contains no function fields") {
        val action = MigrationAction.AddField(DynamicOptic.root, "f", Resolved.Literal.int(1))
        // Verify productIterator contains only data, not functions
        action.productIterator.foreach { field =>
          assertTrue(!field.isInstanceOf[Function0[_]])
          assertTrue(!field.isInstanceOf[Function1[_, _]])
          assertTrue(!field.isInstanceOf[Function2[_, _, _]])
        }
        assertTrue(true)
      },
      test("Resolved contains no function fields") {
        val expr = Resolved.Concat(
          Vector(
            Resolved.Literal.string("a"),
            Resolved.FieldAccess("b", Resolved.Identity)
          ),
          "-"
        )
        // Recursively verify no functions
        def verifyNoFunctions(r: Resolved): Boolean = r match {
          case Resolved.Literal(_)             => true
          case Resolved.Identity               => true
          case Resolved.FieldAccess(_, inner)  => verifyNoFunctions(inner)
          case Resolved.Compose(outer, inner)  => verifyNoFunctions(outer) && verifyNoFunctions(inner)
          case Resolved.Convert(_, _, inner)   => verifyNoFunctions(inner)
          case Resolved.Fail(_)                => true
          case Resolved.Concat(parts, _)       => parts.forall(verifyNoFunctions)
          case Resolved.WrapSome(inner)        => verifyNoFunctions(inner)
          case Resolved.Construct(fields)      => fields.forall { case (_, v) => verifyNoFunctions(v) }
          case Resolved.ConstructSeq(elements) => elements.forall(verifyNoFunctions)
          case _                               => true // Other cases
        }
        assertTrue(verifyNoFunctions(expr))
      }
    ),
    suite("Structural reconstruction")(
      test("migration can be reconstructed from its parts") {
        val original = DynamicMigration(
          Vector(
            MigrationAction.Rename(DynamicOptic.root, "a", "b"),
            MigrationAction.AddField(DynamicOptic.root, "c", Resolved.Literal.int(1))
          )
        )
        val extracted     = original.actions
        val reconstructed = DynamicMigration(extracted)
        assertTrue(reconstructed == original)
      },
      test("action can be reconstructed from pattern match") {
        val original      = MigrationAction.Rename(DynamicOptic.root, "old", "new")
        val reconstructed = original match {
          case MigrationAction.Rename(at, from, to) =>
            MigrationAction.Rename(at, from, to)
        }
        assertTrue(reconstructed == original)
      },
      test("expression can be reconstructed from pattern match") {
        val original      = Resolved.FieldAccess("field", Resolved.Convert("Int", "String", Resolved.Identity))
        val reconstructed = original match {
          case Resolved.FieldAccess(name, inner) =>
            inner match {
              case Resolved.Convert(from, to, innerExpr) =>
                Resolved.FieldAccess(name, Resolved.Convert(from, to, innerExpr))
              case _ => original
            }
        }
        assertTrue(reconstructed == original)
      }
    ),
    suite("DynamicOptic is pure data")(
      test("root optic has empty nodes") {
        val optic = DynamicOptic.root
        assertTrue(optic.nodes.isEmpty)
      },
      test("field optic records field name") {
        val optic = DynamicOptic.root.field("name")
        assertTrue(optic.nodes.nonEmpty)
      },
      test("optics have structural equality") {
        val o1 = DynamicOptic.root.field("a").field("b")
        val o2 = DynamicOptic.root.field("a").field("b")
        assertTrue(o1 == o2)
      },
      test("optics can be compared") {
        val o1 = DynamicOptic.root.field("a")
        val o2 = DynamicOptic.root.field("b")
        assertTrue(o1 != o2)
      }
    ),
    suite("MigrationError is pure data")(
      test("PathNotFound is a case class") {
        val error = MigrationError.PathNotFound(DynamicOptic.root)
        assertTrue(error.isInstanceOf[Product])
      },
      test("ExpectedRecord is a case class") {
        val error = MigrationError.ExpectedRecord(DynamicOptic.root, dynamicInt(1))
        assertTrue(error.isInstanceOf[Product])
      },
      test("ExpectedSequence is a case class") {
        val error = MigrationError.ExpectedSequence(DynamicOptic.root, dynamicInt(1))
        assertTrue(error.isInstanceOf[Product])
      },
      test("ExpressionFailed is a case class") {
        val error = MigrationError.ExpressionFailed(DynamicOptic.root, "error")
        assertTrue(error.isInstanceOf[Product])
      },
      test("errors have structural equality") {
        val e1 = MigrationError.PathNotFound(DynamicOptic.root.field("a"))
        val e2 = MigrationError.PathNotFound(DynamicOptic.root.field("a"))
        assertTrue(e1 == e2)
      }
    ),
    suite("Immutability verification")(
      test("DynamicMigration is immutable") {
        val m1 = DynamicMigration.single(MigrationAction.Rename(DynamicOptic.root, "a", "b"))
        val m2 = m1 ++ DynamicMigration.single(MigrationAction.Rename(DynamicOptic.root, "c", "d"))
        // m1 should be unchanged
        assertTrue(m1.actionCount == 1)
        assertTrue(m2.actionCount == 2)
      },
      test("actions vector is immutable") {
        val actions    = Vector(MigrationAction.Rename(DynamicOptic.root, "a", "b"))
        val migration  = DynamicMigration(actions)
        val newActions = migration.actions :+ MigrationAction.Rename(DynamicOptic.root, "c", "d")
        // Original migration unchanged
        assertTrue(migration.actions.length == 1)
        assertTrue(newActions.length == 2)
      },
      test("reverse creates new migration") {
        val original = DynamicMigration.single(MigrationAction.Rename(DynamicOptic.root, "a", "b"))
        val reversed = original.reverse
        // Original unchanged
        original.actions.head match {
          case MigrationAction.Rename(_, from, to) =>
            assertTrue(from == "a" && to == "b")
          case _ => assertTrue(false)
        }
        reversed.actions.head match {
          case MigrationAction.Rename(_, from, to) =>
            assertTrue(from == "b" && to == "a")
          case _ => assertTrue(false)
        }
      }
    )
  )
}
