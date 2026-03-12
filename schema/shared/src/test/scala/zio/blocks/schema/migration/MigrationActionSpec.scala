package zio.blocks.schema.migration

import zio.blocks.schema.{DynamicOptic, DynamicValue, PrimitiveValue, SchemaBaseSpec}
import zio.test.Assertion._
import zio.test._

object MigrationActionSpec extends SchemaBaseSpec {

  private val root      = DynamicOptic.root
  private val fieldPath = root.field("x")
  private val litExpr   = DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(0)))

  def spec: Spec[TestEnvironment, Any] = suite("MigrationActionSpec")(
    reversalSuite,
    fieldNameSuite,
    renameSuite
  )

  private val reversalSuite = suite("Reversal symmetry")(
    test("AddField.reverse is DropField") {
      val action = MigrationAction.AddField(fieldPath, litExpr)
      assert(action.reverse)(isSubtype[MigrationAction.DropField](anything))
    },
    test("DropField.reverse is AddField") {
      val action = MigrationAction.DropField(fieldPath, litExpr)
      assert(action.reverse)(isSubtype[MigrationAction.AddField](anything))
    },
    test("AddField round-trip: reverse.reverse == original") {
      val action = MigrationAction.AddField(fieldPath, litExpr)
      assert(action.reverse.reverse)(equalTo(action))
    },
    test("DropField round-trip: reverse.reverse == original") {
      val action = MigrationAction.DropField(fieldPath, litExpr)
      assert(action.reverse.reverse)(equalTo(action))
    },
    test("Rename round-trip: reverse.reverse is equivalent") {
      val action = MigrationAction.Rename(root.field("a"), "b")
      val rr     = action.reverse.reverse
      rr match {
        case MigrationAction.Rename(at, to) =>
          assert(to)(equalTo("b")) &&
          assert(at.nodes.last)(equalTo(DynamicOptic.Node.Field("a"): DynamicOptic.Node))
        case _ => assert(rr)(isSubtype[MigrationAction.Rename](anything))
      }
    },
    test("RenameCase round-trip") {
      val action = MigrationAction.RenameCase(root, "A", "B")
      val rr     = action.reverse.reverse.asInstanceOf[MigrationAction.RenameCase]
      assert(rr.from)(equalTo("A")) && assert(rr.to)(equalTo("B"))
    },
    test("Mandate.reverse is Optionalize") {
      val action = MigrationAction.Mandate(fieldPath, litExpr)
      assert(action.reverse)(isSubtype[MigrationAction.Optionalize](anything))
    },
    test("Optionalize.reverse is Mandate") {
      val action = MigrationAction.Optionalize(fieldPath)
      assert(action.reverse)(isSubtype[MigrationAction.Mandate](anything))
    },
    test("TransformValue.reverse is Irreversible") {
      val action = MigrationAction.TransformValue(fieldPath, litExpr)
      assert(action.reverse)(isSubtype[MigrationAction.Irreversible](anything))
    },
    test("Join.reverse is Irreversible") {
      val action = MigrationAction.Join(fieldPath, Vector.empty, litExpr)
      assert(action.reverse)(isSubtype[MigrationAction.Irreversible](anything))
    },
    test("Split.reverse is Irreversible") {
      val action = MigrationAction.Split(fieldPath, Vector.empty, litExpr)
      assert(action.reverse)(isSubtype[MigrationAction.Irreversible](anything))
    },
    test("ChangeType.reverse is Irreversible") {
      val action = MigrationAction.ChangeType(fieldPath, litExpr)
      assert(action.reverse)(isSubtype[MigrationAction.Irreversible](anything))
    },
    test("TransformElements.reverse is Irreversible") {
      val action = MigrationAction.TransformElements(fieldPath, litExpr)
      assert(action.reverse)(isSubtype[MigrationAction.Irreversible](anything))
    },
    test("TransformKeys.reverse is Irreversible") {
      val action = MigrationAction.TransformKeys(fieldPath, litExpr)
      assert(action.reverse)(isSubtype[MigrationAction.Irreversible](anything))
    },
    test("TransformValues.reverse is Irreversible") {
      val action = MigrationAction.TransformValues(fieldPath, litExpr)
      assert(action.reverse)(isSubtype[MigrationAction.Irreversible](anything))
    },
    test("Irreversible.reverse is self") {
      val action = MigrationAction.Irreversible(root, "test")
      assert(action.reverse)(equalTo(action))
    },
    test("ApplyMigration reverses its nested migration") {
      val nested = DynamicMigration(
        Vector(
          MigrationAction.AddField(root.field("a"), litExpr)
        )
      )
      val action   = MigrationAction.ApplyMigration(root, nested)
      val reversed = action.reverse.asInstanceOf[MigrationAction.ApplyMigration]
      assert(reversed.migration.actions.head)(isSubtype[MigrationAction.DropField](anything))
    },
    test("TransformCase reverses its nested actions") {
      val actions = Vector[MigrationAction](
        MigrationAction.AddField(root.field("x"), litExpr),
        MigrationAction.Rename(root.field("a"), "b")
      )
      val action   = MigrationAction.TransformCase(root, actions)
      val reversed = action.reverse.asInstanceOf[MigrationAction.TransformCase]
      assert(reversed.actions.size)(equalTo(2)) &&
      assert(reversed.actions(0))(isSubtype[MigrationAction.Rename](anything)) &&
      assert(reversed.actions(1))(isSubtype[MigrationAction.DropField](anything))
    }
  )

  private val fieldNameSuite = suite("fieldName")(
    test("AddField.fieldName extracts field name") {
      val action = MigrationAction.AddField(root.field("myField"), litExpr)
      assert(action.fieldName)(equalTo("myField"))
    },
    test("DropField.fieldName extracts field name") {
      val action = MigrationAction.DropField(root.field("toRemove"), litExpr)
      assert(action.fieldName)(equalTo("toRemove"))
    }
  )

  private val renameSuite = suite("Rename helpers")(
    test("Rename.from extracts source name") {
      val action = MigrationAction.Rename(root.field("oldName"), "newName")
      assert(action.from)(equalTo("oldName"))
    },
    test("Rename preserves at path") {
      val action = MigrationAction.Rename(root.field("a"), "b")
      assert(action.at)(equalTo(root.field("a")))
    }
  )
}
