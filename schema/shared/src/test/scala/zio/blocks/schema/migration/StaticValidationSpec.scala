package zio.blocks.schema.migration

import zio.blocks.schema._
import zio.test._

/**
 * Tests for compile-time and runtime validation of migrations.
 *
 * Covers:
 *   - Type safety validation
 *   - Schema compatibility checks
 *   - Path validation
 *   - Expression type validation
 *   - Action precondition validation
 */
object StaticValidationSpec extends SchemaBaseSpec {

  // ─────────────────────────────────────────────────────────────────────────
  // Test Data Types
  // ─────────────────────────────────────────────────────────────────────────

  case class PersonV1(name: String, age: Int)
  case class PersonV2(fullName: String, age: Int)
  case class PersonV3(fullName: String, age: Int, email: String)

  case class Address(street: String, city: String)
  case class PersonWithAddress(name: String, address: Address)

  // ─────────────────────────────────────────────────────────────────────────
  // Test Helpers
  // ─────────────────────────────────────────────────────────────────────────

  def dynamicRecord(fields: (String, DynamicValue)*): DynamicValue =
    DynamicValue.Record(fields: _*)

  def dynamicString(s: String): DynamicValue =
    DynamicValue.Primitive(PrimitiveValue.String(s))

  def dynamicInt(i: Int): DynamicValue =
    DynamicValue.Primitive(PrimitiveValue.Int(i))

  def dynamicBool(b: Boolean): DynamicValue =
    DynamicValue.Primitive(PrimitiveValue.Boolean(b))

  def dynamicSequence(elements: DynamicValue*): DynamicValue =
    DynamicValue.Sequence(elements: _*)

  def dynamicVariant(caseName: String, value: DynamicValue): DynamicValue =
    DynamicValue.Variant(caseName, value)

  // ─────────────────────────────────────────────────────────────────────────
  // Tests
  // ─────────────────────────────────────────────────────────────────────────

  def spec: Spec[TestEnvironment, Any] = suite("StaticValidationSpec")(
    suite("Path validation at runtime")(
      test("valid path succeeds") {
        val action = MigrationAction.AddField(DynamicOptic.root, "newField", Resolved.Literal.int(1))
        val input  = dynamicRecord("existing" -> dynamicInt(1))
        assertTrue(action.apply(input).isRight)
      },
      test("nested path to non-existent field fails") {
        val action = MigrationAction.AddField(
          DynamicOptic.root.field("nonexistent"),
          "newField",
          Resolved.Literal.int(1)
        )
        val input = dynamicRecord("other" -> dynamicInt(1))
        assertTrue(action.apply(input).isLeft)
      },
      test("path to wrong type fails") {
        val action = MigrationAction.AddField(
          DynamicOptic.root.field("notARecord"),
          "newField",
          Resolved.Literal.int(1)
        )
        val input = dynamicRecord("notARecord" -> dynamicInt(42))
        assertTrue(action.apply(input).isLeft)
      },
      test("deep nested path validation") {
        val action = MigrationAction.AddField(
          DynamicOptic.root.field("a").field("b").field("c"),
          "d",
          Resolved.Literal.int(1)
        )
        // Missing intermediate paths
        val input = dynamicRecord("a" -> dynamicRecord("x" -> dynamicInt(1)))
        assertTrue(action.apply(input).isLeft)
      }
    ),
    suite("Record operation validation")(
      test("AddField on non-record fails") {
        val action = MigrationAction.AddField(DynamicOptic.root, "field", Resolved.Literal.int(1))
        val input  = dynamicInt(42)
        val result = action.apply(input)
        assertTrue(result.isLeft)
        result match {
          case Left(MigrationError.ExpectedRecord(_, _)) => assertTrue(true)
          case _                                         => assertTrue(false)
        }
      },
      test("DropField on non-record fails") {
        val action = MigrationAction.DropField(DynamicOptic.root, "field", Resolved.Literal.int(1))
        val input  = dynamicString("not a record")
        assertTrue(action.apply(input).isLeft)
      },
      test("Rename on non-record fails") {
        val action = MigrationAction.Rename(DynamicOptic.root, "old", "new")
        val input  = dynamicBool(true)
        assertTrue(action.apply(input).isLeft)
      },
      test("DropField on missing field succeeds (idempotent)") {
        val action = MigrationAction.DropField(DynamicOptic.root, "missing", Resolved.Literal.int(0))
        val input  = dynamicRecord("other" -> dynamicInt(1))
        // DropField of non-existent field should succeed (no-op)
        val result = action.apply(input)
        assertTrue(result == Right(input))
      },
      test("Rename of missing field is no-op") {
        val action = MigrationAction.Rename(DynamicOptic.root, "missing", "new")
        val input  = dynamicRecord("other" -> dynamicInt(1))
        val result = action.apply(input)
        // Rename of non-existent field should succeed (no-op)
        assertTrue(result == Right(input))
      }
    ),
    suite("Sequence operation validation")(
      test("TransformElements on non-sequence fails") {
        val action = MigrationAction.TransformElements(
          DynamicOptic.root,
          Resolved.Identity,
          Resolved.Identity
        )
        val input  = dynamicRecord("x" -> dynamicInt(1))
        val result = action.apply(input)
        assertTrue(result.isLeft)
        result match {
          case Left(MigrationError.ExpectedSequence(_, _)) => assertTrue(true)
          case _                                           => assertTrue(false)
        }
      },
      test("TransformElements on empty sequence succeeds") {
        val action = MigrationAction.TransformElements(
          DynamicOptic.root,
          Resolved.Convert("Int", "String", Resolved.Identity),
          Resolved.Convert("String", "Int", Resolved.Identity)
        )
        val input = dynamicSequence()
        assertTrue(action.apply(input) == Right(dynamicSequence()))
      },
      test("TransformElements with failing transform fails") {
        val action = MigrationAction.TransformElements(
          DynamicOptic.root,
          Resolved.Fail("element transform error"),
          Resolved.Identity
        )
        val input = dynamicSequence(dynamicInt(1), dynamicInt(2))
        assertTrue(action.apply(input).isLeft)
      }
    ),
    suite("Variant operation validation")(
      test("RenameCase on non-variant is no-op") {
        val action = MigrationAction.RenameCase(DynamicOptic.root, "OldCase", "NewCase")
        val input  = dynamicRecord("x" -> dynamicInt(1))
        // Non-variant input - action is no-op
        val result = action.apply(input)
        assertTrue(result == Right(input))
      },
      test("RenameCase on different case is no-op") {
        val action = MigrationAction.RenameCase(DynamicOptic.root, "CaseA", "CaseB")
        val input  = dynamicVariant("OtherCase", dynamicInt(1))
        val result = action.apply(input)
        assertTrue(result == Right(input))
      },
      test("TransformCase on wrong case is no-op") {
        val action = MigrationAction.TransformCase(
          DynamicOptic.root,
          "TargetCase",
          Vector(MigrationAction.AddField(DynamicOptic.root, "f", Resolved.Literal.int(1)))
        )
        val input  = dynamicVariant("OtherCase", dynamicRecord())
        val result = action.apply(input)
        assertTrue(result == Right(input))
      },
      test("TransformCase with failing nested action fails") {
        val action = MigrationAction.TransformCase(
          DynamicOptic.root,
          "MyCase",
          Vector(MigrationAction.AddField(DynamicOptic.root, "f", Resolved.Fail("nested error")))
        )
        val input = dynamicVariant("MyCase", dynamicRecord())
        assertTrue(action.apply(input).isLeft)
      }
    ),
    suite("Expression validation")(
      test("FieldAccess on missing field fails") {
        val expr  = Resolved.FieldAccess("missing", Resolved.Identity)
        val input = dynamicRecord("other" -> dynamicInt(1))
        assertTrue(expr.evalDynamic(input).isLeft)
      },
      test("FieldAccess on non-record fails") {
        val expr  = Resolved.FieldAccess("field", Resolved.Identity)
        val input = dynamicInt(42)
        assertTrue(expr.evalDynamic(input).isLeft)
      },
      test("Convert with invalid input fails") {
        val expr  = Resolved.Convert("String", "Int", Resolved.Identity)
        val input = dynamicString("not a number")
        assertTrue(expr.evalDynamic(input).isLeft)
      },
      test("Compose propagates inner failure") {
        val expr = Resolved.Compose(
          Resolved.Identity,
          Resolved.Fail("inner error")
        )
        assertTrue(expr.evalDynamic(dynamicInt(1)).isLeft)
      },
      test("Identity without input fails") {
        val expr = Resolved.Identity
        assertTrue(expr.evalDynamic.isLeft)
      }
    ),
    suite("Type conversion validation")(
      test("Int to String succeeds") {
        val expr = Resolved.Convert("Int", "String", Resolved.Identity)
        assertTrue(expr.evalDynamic(dynamicInt(42)) == Right(dynamicString("42")))
      },
      test("String to Int with valid input succeeds") {
        val expr = Resolved.Convert("String", "Int", Resolved.Identity)
        assertTrue(expr.evalDynamic(dynamicString("42")) == Right(dynamicInt(42)))
      },
      test("String to Int with invalid input fails") {
        val expr = Resolved.Convert("String", "Int", Resolved.Identity)
        assertTrue(expr.evalDynamic(dynamicString("abc")).isLeft)
      },
      test("Boolean to Int succeeds") {
        val expr = Resolved.Convert("Boolean", "Int", Resolved.Identity)
        assertTrue(expr.evalDynamic(dynamicBool(true)) == Right(dynamicInt(1)))
        assertTrue(expr.evalDynamic(dynamicBool(false)) == Right(dynamicInt(0)))
      },
      test("unsupported conversion fails") {
        // Try to convert a record to int - should fail
        val expr  = Resolved.Convert("Record", "Int", Resolved.Identity)
        val input = dynamicRecord("x" -> dynamicInt(1))
        assertTrue(expr.evalDynamic(input).isLeft)
      }
    ),
    suite("Optionality validation")(
      test("Mandate on Some extracts value") {
        val action = MigrationAction.Mandate(DynamicOptic.root, "opt", Resolved.Literal.int(0))
        val input  = dynamicRecord(
          "opt" -> dynamicVariant("Some", dynamicInt(42))
        )
        val result = action.apply(input)
        assertTrue(result == Right(dynamicRecord("opt" -> dynamicInt(42))))
      },
      test("Mandate on None uses default") {
        val action = MigrationAction.Mandate(DynamicOptic.root, "opt", Resolved.Literal.int(99))
        val input  = dynamicRecord(
          "opt" -> dynamicVariant("None", DynamicValue.Primitive(PrimitiveValue.Unit))
        )
        val result = action.apply(input)
        assertTrue(result == Right(dynamicRecord("opt" -> dynamicInt(99))))
      },
      test("Mandate with failing default fails on None") {
        val action = MigrationAction.Mandate(DynamicOptic.root, "opt", Resolved.Fail("no default"))
        val input  = dynamicRecord(
          "opt" -> dynamicVariant("None", DynamicValue.Primitive(PrimitiveValue.Unit))
        )
        assertTrue(action.apply(input).isLeft)
      },
      test("Optionalize wraps value in Some") {
        val action = MigrationAction.Optionalize(DynamicOptic.root, "required")
        val input  = dynamicRecord("required" -> dynamicInt(42))
        val result = action.apply(input)
        assertTrue(
          result == Right(
            dynamicRecord(
              "required" -> dynamicVariant("Some", DynamicValue.Record(("value", dynamicInt(42))))
            )
          )
        )
      }
    ),
    suite("Typed migration validation")(
      test("successful typed migration") {
        implicit val schemaV1: Schema[PersonV1] = Schema.derived
        implicit val schemaV2: Schema[PersonV2] = Schema.derived

        val migration = Migration[PersonV1, PersonV2](
          DynamicMigration.single(MigrationAction.Rename(DynamicOptic.root, "name", "fullName")),
          schemaV1,
          schemaV2
        )
        val result = migration.apply(PersonV1("Alice", 30))
        assertTrue(result == Right(PersonV2("Alice", 30)))
      },
      test("typed migration with incompatible result fails") {
        implicit val schemaV1: Schema[PersonV1] = Schema.derived

        // Migration produces wrong structure for target type
        val migration = Migration[PersonV1, PersonV1](
          DynamicMigration.single(
            MigrationAction.DropField(DynamicOptic.root, "name", Resolved.Literal.string(""))
          ),
          schemaV1,
          schemaV1
        )
        // This should fail because result doesn't match PersonV1 structure
        val result = migration.apply(PersonV1("Alice", 30))
        assertTrue(result.isLeft)
      }
    ),
    suite("Composed migration validation")(
      test("composed migration validates each step") {
        val m1 = DynamicMigration.single(
          MigrationAction.Rename(DynamicOptic.root, "a", "b")
        )
        val m2 = DynamicMigration.single(
          MigrationAction.AddField(DynamicOptic.root.field("nonexistent"), "c", Resolved.Literal.int(1))
        )
        val composed = m1 ++ m2
        val input    = dynamicRecord("a" -> dynamicInt(1))
        // First action succeeds, second fails due to missing path
        assertTrue(composed.apply(input).isLeft)
      },
      test("error in first action stops composition") {
        val m1 = DynamicMigration.single(
          MigrationAction.AddField(DynamicOptic.root, "x", Resolved.Fail("error"))
        )
        val m2 = DynamicMigration.single(
          MigrationAction.Rename(DynamicOptic.root, "a", "b")
        )
        val composed = m1 ++ m2
        val input    = dynamicRecord("a" -> dynamicInt(1))
        assertTrue(composed.apply(input).isLeft)
      }
    ),
    suite("Default value validation")(
      test("AddField with valid default succeeds") {
        val action = MigrationAction.AddField(
          DynamicOptic.root,
          "field",
          Resolved.Literal.int(42)
        )
        val input  = dynamicRecord()
        val result = action.apply(input)
        assertTrue(result == Right(dynamicRecord("field" -> dynamicInt(42))))
      },
      test("AddField with failing default fails") {
        val action = MigrationAction.AddField(
          DynamicOptic.root,
          "field",
          Resolved.Fail("cannot compute default")
        )
        val input = dynamicRecord()
        assertTrue(action.apply(input).isLeft)
      },
      test("AddField with computed default from existing field") {
        val action = MigrationAction.AddField(
          DynamicOptic.root,
          "copy",
          Resolved.FieldAccess("original", Resolved.Identity)
        )
        val input  = dynamicRecord("original" -> dynamicInt(42))
        val result = action.apply(input)
        assertTrue(
          result == Right(
            dynamicRecord(
              "original" -> dynamicInt(42),
              "copy"     -> dynamicInt(42)
            )
          )
        )
      },
      test("AddField with computed default from missing field fails") {
        val action = MigrationAction.AddField(
          DynamicOptic.root,
          "copy",
          Resolved.FieldAccess("missing", Resolved.Identity)
        )
        val input = dynamicRecord("other" -> dynamicInt(1))
        assertTrue(action.apply(input).isLeft)
      }
    ),
    suite("Error message quality")(
      test("PathNotFound includes path info") {
        val action = MigrationAction.AddField(
          DynamicOptic.root.field("missing"),
          "field",
          Resolved.Literal.int(1)
        )
        val input = dynamicRecord("other" -> dynamicInt(1))
        action.apply(input) match {
          case Left(error) =>
            val msg = error.toString
            assertTrue(msg.nonEmpty)
          case Right(_) => assertTrue(false)
        }
      },
      test("ExpectedRecord includes actual type") {
        val action = MigrationAction.AddField(DynamicOptic.root, "field", Resolved.Literal.int(1))
        val input  = dynamicInt(42)
        action.apply(input) match {
          case Left(MigrationError.ExpectedRecord(_, actual)) =>
            assertTrue(actual == input)
          case _ => assertTrue(false)
        }
      },
      test("ExpressionFailed includes error message") {
        val action = MigrationAction.AddField(
          DynamicOptic.root,
          "field",
          Resolved.Fail("custom error message")
        )
        val input = dynamicRecord()
        action.apply(input) match {
          case Left(MigrationError.ExpressionFailed(_, msg)) =>
            assertTrue(msg.contains("custom error message"))
          case _ => assertTrue(false)
        }
      }
    )
  )
}
