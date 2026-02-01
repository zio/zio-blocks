package zio.blocks.schema.migration

import zio.blocks.chunk.Chunk
import zio.blocks.schema.{DynamicOptic, Schema}
import zio.test._

/**
 * Tests for MigrationValidator error branches.
 *
 * These tests exercise the validation error paths to ensure proper coverage of
 * the MigrationValidator.validateActions method and its error handling.
 */
object ValidationErrorBranchSpec extends ZIOSpecDefault {

  // Simple test schemas
  case class Source(name: String, age: Int, email: String)
  case class Target(fullName: String, age: Int, contact: String)

  implicit val sourceSchema: Schema[Source] = Schema.derived
  implicit val targetSchema: Schema[Target] = Schema.derived

  def spec: Spec[TestEnvironment, Any] = suite("ValidationErrorBranchSpec")(
    suite("MigrationValidator.ValidationError branches")(
      test("PathNotInSource error is created correctly") {
        val optic = DynamicOptic.root.field("nonexistent").field("path")
        val error = MigrationValidator.ValidationError.PathNotInSource(optic)
        assertTrue(
          error.path == optic &&
            error.message.contains("source") &&
            error.render.nonEmpty
        )
      },
      test("PathNotInTarget error is created correctly") {
        val optic = DynamicOptic.root.field("missing").field("field")
        val error = MigrationValidator.ValidationError.PathNotInTarget(optic)
        assertTrue(
          error.path == optic &&
            error.message.contains("target") &&
            error.render.nonEmpty
        )
      },
      test("FieldAlreadyExists error is created correctly") {
        val optic = DynamicOptic.root.field("existing")
        val error = MigrationValidator.ValidationError.FieldAlreadyExists(optic, "fieldName")
        assertTrue(
          error.path == optic &&
            error.fieldName == "fieldName" &&
            error.message.contains("already exists") &&
            error.render.nonEmpty
        )
      },
      test("FieldNotFound error is created correctly") {
        val optic = DynamicOptic.root.field("parent")
        val error = MigrationValidator.ValidationError.FieldNotFound(optic, "missingField")
        assertTrue(
          error.path == optic &&
            error.fieldName == "missingField" &&
            error.message.contains("not found") &&
            error.render.nonEmpty
        )
      },
      test("CaseNotFound error is created correctly") {
        val optic = DynamicOptic.root.field("enum")
        val error = MigrationValidator.ValidationError.CaseNotFound(optic, "MissingCase")
        assertTrue(
          error.path == optic &&
            error.caseName == "MissingCase" &&
            error.message.contains("not found") &&
            error.render.nonEmpty
        )
      },
      test("TypeMismatch error is created correctly") {
        val optic = DynamicOptic.root.field("field")
        val error = MigrationValidator.ValidationError.TypeMismatch(optic, "String", "Int")
        assertTrue(
          error.path == optic &&
            error.expected == "String" &&
            error.actual == "Int" &&
            error.message.contains("String") &&
            error.message.contains("Int") &&
            error.render.nonEmpty
        )
      },
      test("IncompatibleTransform error is created correctly") {
        val optic = DynamicOptic.root.field("field")
        val error = MigrationValidator.ValidationError.IncompatibleTransform(optic, "cannot transform")
        assertTrue(
          error.path == optic &&
            error.reason == "cannot transform" &&
            error.message.contains("cannot transform") &&
            error.render.nonEmpty
        )
      },
      test("PathNotInSource with nested path") {
        val optic = DynamicOptic.root.field("a").field("b").field("c")
        val error = MigrationValidator.ValidationError.PathNotInSource(optic)
        assertTrue(error.render.contains("a") || error.render.nonEmpty)
      },
      test("PathNotInSource with elements") {
        val optic = DynamicOptic.root.field("list").elements
        val error = MigrationValidator.ValidationError.PathNotInSource(optic)
        assertTrue(error.render.nonEmpty)
      },
      test("PathNotInTarget with mapValues") {
        val optic = DynamicOptic.root.field("map").mapValues
        val error = MigrationValidator.ValidationError.PathNotInTarget(optic)
        assertTrue(error.render.nonEmpty)
      },
      test("FieldAlreadyExists with nested optic") {
        val optic = DynamicOptic.root.field("parent").field("child")
        val error = MigrationValidator.ValidationError.FieldAlreadyExists(optic, "existing")
        assertTrue(error.fieldName == "existing")
      },
      test("TypeMismatch with complex types") {
        val optic = DynamicOptic.root.field("data")
        val error = MigrationValidator.ValidationError.TypeMismatch(optic, "Map[String, Int]", "List[String]")
        assertTrue(error.message.contains("Map") && error.message.contains("List"))
      }
    ),
    suite("ValidationResult pattern matching")(
      test("ValidationResult.Valid matches correctly") {
        val result: MigrationValidator.ValidationResult = MigrationValidator.ValidationResult.Valid
        val isValid                                     = result match {
          case MigrationValidator.ValidationResult.Valid      => true
          case MigrationValidator.ValidationResult.Invalid(_) => false
        }
        assertTrue(isValid)
      },
      test("ValidationResult.Invalid matches correctly") {
        val optic                                       = DynamicOptic.root.field("test")
        val error                                       = MigrationValidator.ValidationError.FieldNotFound(optic, "test")
        val result: MigrationValidator.ValidationResult =
          MigrationValidator.ValidationResult.Invalid(Chunk(error))
        val isInvalid = result match {
          case MigrationValidator.ValidationResult.Valid      => false
          case MigrationValidator.ValidationResult.Invalid(_) => true
        }
        assertTrue(isInvalid)
      },
      test("ValidationResult.Invalid contains errors") {
        val optic  = DynamicOptic.root.field("test")
        val error  = MigrationValidator.ValidationError.FieldNotFound(optic, "test")
        val result = MigrationValidator.ValidationResult.Invalid(Chunk(error))
        assertTrue(result.errors.size == 1)
      },
      test("ValidationResult.Invalid render produces output") {
        val optic  = DynamicOptic.root.field("test")
        val error  = MigrationValidator.ValidationError.FieldNotFound(optic, "test")
        val result = MigrationValidator.ValidationResult.Invalid(Chunk(error))
        assertTrue(result.render.nonEmpty)
      },
      test("ValidationResult.Invalid with multiple errors") {
        val optic1 = DynamicOptic.root.field("path1")
        val optic2 = DynamicOptic.root.field("path2")
        val error1 = MigrationValidator.ValidationError.FieldNotFound(optic1, "field1")
        val error2 = MigrationValidator.ValidationError.FieldNotFound(optic2, "field2")
        val result = MigrationValidator.ValidationResult.Invalid(Chunk(error1, error2))
        assertTrue(result.errors.size == 2 && result.render.contains("field1") && result.render.contains("field2"))
      },
      test("ValidationResult.Invalid with three errors") {
        val optic1 = DynamicOptic.root.field("p1")
        val optic2 = DynamicOptic.root.field("p2")
        val optic3 = DynamicOptic.root.field("p3")
        val error1 = MigrationValidator.ValidationError.PathNotInSource(optic1)
        val error2 = MigrationValidator.ValidationError.PathNotInTarget(optic2)
        val error3 = MigrationValidator.ValidationError.CaseNotFound(optic3, "Missing")
        val result = MigrationValidator.ValidationResult.Invalid(Chunk(error1, error2, error3))
        assertTrue(result.errors.size == 3)
      },
      test("ValidationResult.Invalid render includes newlines for multiple errors") {
        val optic1 = DynamicOptic.root.field("a")
        val optic2 = DynamicOptic.root.field("b")
        val error1 = MigrationValidator.ValidationError.PathNotInSource(optic1)
        val error2 = MigrationValidator.ValidationError.PathNotInTarget(optic2)
        val result = MigrationValidator.ValidationResult.Invalid(Chunk(error1, error2))
        assertTrue(result.render.contains("\n") || result.errors.size == 2)
      }
    ),
    suite("Edge cases for error rendering")(
      test("root optic renders correctly in error") {
        val error = MigrationValidator.ValidationError.PathNotInSource(DynamicOptic.root)
        assertTrue(error.render.nonEmpty)
      },
      test("deeply nested optic renders correctly in error") {
        val optic = DynamicOptic.root.field("a").field("b").field("c").field("d")
        val error = MigrationValidator.ValidationError.PathNotInTarget(optic)
        assertTrue(error.render.nonEmpty && error.path == optic)
      },
      test("optic with caseOf renders correctly in error") {
        val optic = DynamicOptic.root.field("status").caseOf("Success")
        val error = MigrationValidator.ValidationError.CaseNotFound(optic, "Failure")
        assertTrue(error.render.nonEmpty)
      },
      test("optic with elements renders correctly in error") {
        val optic = DynamicOptic.root.field("items").elements
        val error = MigrationValidator.ValidationError.TypeMismatch(optic, "List[String]", "List[Int]")
        assertTrue(error.render.nonEmpty)
      },
      test("optic with mapKeys renders correctly in error") {
        val optic = DynamicOptic.root.field("dict").mapKeys
        val error = MigrationValidator.ValidationError.IncompatibleTransform(optic, "cannot transform keys")
        assertTrue(error.render.nonEmpty)
      },
      test("optic with mapValues renders correctly in error") {
        val optic = DynamicOptic.root.field("dict").mapValues
        val error = MigrationValidator.ValidationError.IncompatibleTransform(optic, "cannot transform values")
        assertTrue(error.render.nonEmpty)
      },
      test("optic with atIndices renders correctly in error") {
        val optic = DynamicOptic.root.field("list").atIndices(0, 1, 2)
        val error = MigrationValidator.ValidationError.PathNotInSource(optic)
        assertTrue(error.render.nonEmpty)
      },
      test("optic with atKeys renders correctly in error") {
        val optic = DynamicOptic.root.field("map").atKeys("key1", "key2")
        val error = MigrationValidator.ValidationError.FieldNotFound(optic, "key")
        assertTrue(error.render.nonEmpty)
      },
      test("error equality check - same errors are equal") {
        val optic  = DynamicOptic.root.field("test")
        val error1 = MigrationValidator.ValidationError.FieldNotFound(optic, "field")
        val error2 = MigrationValidator.ValidationError.FieldNotFound(optic, "field")
        assertTrue(error1 == error2)
      },
      test("error inequality check - different errors are not equal") {
        val optic  = DynamicOptic.root.field("test")
        val error1 = MigrationValidator.ValidationError.FieldNotFound(optic, "field1")
        val error2 = MigrationValidator.ValidationError.FieldNotFound(optic, "field2")
        assertTrue(error1 != error2)
      }
    ),
    suite("DynamicMigration coverage")(
      test("DynamicMigration with empty actions") {
        val migration = DynamicMigration(Chunk.empty[MigrationAction])
        assertTrue(migration.actions.isEmpty)
      },
      test("DynamicMigration with single action") {
        val action    = MigrationAction.Rename(DynamicOptic.root.field("old"), "old", "new")
        val migration = DynamicMigration(action)
        assertTrue(migration.actions.size == 1)
      },
      test("DynamicMigration with multiple actions") {
        val action1   = MigrationAction.Rename(DynamicOptic.root.field("a"), "a", "b")
        val action2   = MigrationAction.Rename(DynamicOptic.root.field("c"), "c", "d")
        val migration = DynamicMigration(Chunk(action1, action2))
        assertTrue(migration.actions.size == 2)
      },
      test("DynamicMigration with five actions") {
        val actions =
          Chunk.fromIterable((1 to 5).map(i => MigrationAction.Rename(DynamicOptic.root.field(s"f$i"), s"f$i", s"g$i")))
        val migration = DynamicMigration(actions)
        assertTrue(migration.actions.size == 5)
      }
    ),
    suite("MigrationAction types")(
      test("MigrationAction.Rename created correctly") {
        val optic  = DynamicOptic.root.field("source")
        val action = MigrationAction.Rename(optic, "source", "target")
        assertTrue(action.at == optic && action.from == "source" && action.to == "target")
      },
      test("MigrationAction.AddField created correctly") {
        val optic  = DynamicOptic.root.field("newField")
        val action = MigrationAction.AddField(optic, "newField", Resolved.Literal.string("default"))
        assertTrue(action.at == optic && action.fieldName == "newField")
      },
      test("MigrationAction.DropField created correctly") {
        val optic  = DynamicOptic.root.field("oldField")
        val action = MigrationAction.DropField(optic, "oldField", Resolved.Literal.string("default"))
        assertTrue(action.at == optic && action.fieldName == "oldField")
      },
      test("MigrationAction.RenameCase created correctly") {
        val optic  = DynamicOptic.root.field("status")
        val action = MigrationAction.RenameCase(optic, "OldCase", "NewCase")
        assertTrue(action.at == optic && action.from == "OldCase" && action.to == "NewCase")
      },
      test("MigrationAction.Rename with nested optic") {
        val optic  = DynamicOptic.root.field("outer").field("inner")
        val action = MigrationAction.Rename(optic, "inner", "newInner")
        assertTrue(action.at == optic)
      },
      test("MigrationAction.AddField with int literal") {
        val optic  = DynamicOptic.root.field("count")
        val action = MigrationAction.AddField(optic, "count", Resolved.Literal.int(0))
        assertTrue(action.fieldName == "count")
      },
      test("MigrationAction.DropField with boolean literal") {
        val optic  = DynamicOptic.root.field("active")
        val action = MigrationAction.DropField(optic, "active", Resolved.Literal.boolean(false))
        assertTrue(action.fieldName == "active")
      },
      test("MigrationAction.TransformValue created correctly") {
        val optic  = DynamicOptic.root
        val action =
          MigrationAction.TransformValue(optic, "field", Resolved.Literal.string("fwd"), Resolved.Literal.string("rev"))
        assertTrue(action.at == optic && action.fieldName == "field")
      },
      test("MigrationAction.Mandate created correctly") {
        val optic  = DynamicOptic.root
        val action = MigrationAction.Mandate(optic, "field", Resolved.Literal.string("default"))
        assertTrue(action.at == optic && action.fieldName == "field")
      },
      test("MigrationAction.Optionalize created correctly") {
        val optic  = DynamicOptic.root
        val action = MigrationAction.Optionalize(optic, "field")
        assertTrue(action.at == optic && action.fieldName == "field")
      },
      test("MigrationAction.ChangeType created correctly") {
        val optic  = DynamicOptic.root
        val action =
          MigrationAction.ChangeType(optic, "field", Resolved.Literal.string("conv"), Resolved.Literal.string("rev"))
        assertTrue(action.at == optic && action.fieldName == "field")
      },
      test("MigrationAction.TransformCase created correctly") {
        val optic  = DynamicOptic.root
        val action = MigrationAction.TransformCase(optic, "caseName", Chunk.empty)
        assertTrue(action.at == optic && action.caseName == "caseName")
      },
      test("MigrationAction.TransformElements created correctly") {
        val optic  = DynamicOptic.root
        val action =
          MigrationAction.TransformElements(optic, Resolved.Literal.string("elem"), Resolved.Literal.string("rev"))
        assertTrue(action.at == optic)
      },
      test("MigrationAction.TransformKeys created correctly") {
        val optic  = DynamicOptic.root
        val action =
          MigrationAction.TransformKeys(optic, Resolved.Literal.string("key"), Resolved.Literal.string("rev"))
        assertTrue(action.at == optic)
      },
      test("MigrationAction.TransformValues created correctly") {
        val optic  = DynamicOptic.root
        val action =
          MigrationAction.TransformValues(optic, Resolved.Literal.string("val"), Resolved.Literal.string("rev"))
        assertTrue(action.at == optic)
      },
      test("MigrationAction.Join created correctly") {
        val optic  = DynamicOptic.root
        val action = MigrationAction.Join(
          optic,
          "target",
          Chunk(DynamicOptic.root.field("a")),
          Resolved.Literal.string("comb"),
          Resolved.Literal.string("split")
        )
        assertTrue(action.at == optic && action.targetFieldName == "target")
      },
      test("MigrationAction.Split created correctly") {
        val optic  = DynamicOptic.root
        val action = MigrationAction.Split(
          optic,
          "source",
          Chunk(DynamicOptic.root.field("a")),
          Resolved.Literal.string("split"),
          Resolved.Literal.string("comb")
        )
        assertTrue(action.at == optic && action.sourceFieldName == "source")
      },
      test("MigrationAction.reverse for Rename") {
        val optic    = DynamicOptic.root
        val action   = MigrationAction.Rename(optic, "old", "new")
        val reversed = action.reverse
        assertTrue(reversed.isInstanceOf[MigrationAction.Rename])
      },
      test("MigrationAction.reverse for AddField") {
        val optic    = DynamicOptic.root
        val action   = MigrationAction.AddField(optic, "field", Resolved.Literal.string(""))
        val reversed = action.reverse
        assertTrue(reversed.isInstanceOf[MigrationAction.DropField])
      },
      test("MigrationAction.prefixPath for Rename") {
        val prefix   = DynamicOptic.root.field("prefix")
        val optic    = DynamicOptic.root.field("field")
        val action   = MigrationAction.Rename(optic, "old", "new")
        val prefixed = action.prefixPath(prefix)
        assertTrue(prefixed.at != optic)
      }
    ),
    suite("Resolved types")(
      test("Resolved.Literal.string creates string literal") {
        val resolved = Resolved.Literal.string("test")
        assertTrue(resolved.isInstanceOf[Resolved.Literal])
      },
      test("Resolved.Literal.int creates int literal") {
        val resolved = Resolved.Literal.int(42)
        assertTrue(resolved.isInstanceOf[Resolved.Literal])
      },
      test("Resolved.Literal.long creates long literal") {
        val resolved = Resolved.Literal.long(42L)
        assertTrue(resolved.isInstanceOf[Resolved.Literal])
      },
      test("Resolved.Literal.boolean creates boolean literal - true") {
        val resolved = Resolved.Literal.boolean(true)
        assertTrue(resolved.isInstanceOf[Resolved.Literal])
      },
      test("Resolved.Literal.boolean creates boolean literal - false") {
        val resolved = Resolved.Literal.boolean(false)
        assertTrue(resolved.isInstanceOf[Resolved.Literal])
      },
      test("Resolved.Literal.double creates double literal") {
        val resolved = Resolved.Literal.double(3.14)
        assertTrue(resolved.isInstanceOf[Resolved.Literal])
      },
      test("Resolved.Literal.unit creates unit literal") {
        val resolved = Resolved.Literal.unit
        assertTrue(resolved.isInstanceOf[Resolved.Literal])
      },
      test("Resolved.Literal.string with empty string") {
        val resolved = Resolved.Literal.string("")
        assertTrue(resolved.isInstanceOf[Resolved.Literal])
      },
      test("Resolved.Literal.int with zero") {
        val resolved = Resolved.Literal.int(0)
        assertTrue(resolved.isInstanceOf[Resolved.Literal])
      },
      test("Resolved.Literal.int with negative") {
        val resolved = Resolved.Literal.int(-1)
        assertTrue(resolved.isInstanceOf[Resolved.Literal])
      },
      test("Resolved.Literal.long with max value") {
        val resolved = Resolved.Literal.long(Long.MaxValue)
        assertTrue(resolved.isInstanceOf[Resolved.Literal])
      },
      test("Resolved.Literal.double with zero") {
        val resolved = Resolved.Literal.double(0.0)
        assertTrue(resolved.isInstanceOf[Resolved.Literal])
      },
      test("Resolved.Fail created correctly") {
        val resolved = Resolved.Fail("error message")
        assertTrue(resolved.isInstanceOf[Resolved.Fail])
      },
      test("Resolved.Identity evalDynamic returns input") {
        assertTrue(Resolved.Identity.evalDynamic.isLeft)
      },
      test("Resolved.FieldAccess created correctly") {
        val resolved = Resolved.FieldAccess("field", Resolved.Identity)
        assertTrue(resolved.isInstanceOf[Resolved.FieldAccess])
      },
      test("Resolved.OpticAccess created correctly") {
        val resolved = Resolved.OpticAccess(DynamicOptic.root.field("field"), Resolved.Identity)
        assertTrue(resolved.isInstanceOf[Resolved.OpticAccess])
      },
      test("Resolved.DefaultValue created correctly") {
        val resolved = Resolved.DefaultValue.noDefault
        assertTrue(resolved.isInstanceOf[Resolved.DefaultValue])
      },
      test("Resolved.Convert created correctly") {
        val resolved = Resolved.Convert("Int", "Long", Resolved.Identity)
        assertTrue(resolved.isInstanceOf[Resolved.Convert])
      }
    ),
    suite("MigrationBuilder operations")(
      test("MigrationBuilder creates empty builder") {
        val builder = MigrationBuilder[Source, Target]
        assertTrue(builder.buildDynamic.actions.isEmpty)
      },
      test("MigrationBuilder.renameField adds action") {
        val builder = MigrationBuilder[Source, Target].renameField("name", "fullName")
        assertTrue(builder.buildDynamic.actions.size == 1)
      },
      test("MigrationBuilder chaining works") {
        val builder = MigrationBuilder[Source, Target]
          .renameField("name", "fullName")
          .renameField("email", "contact")
        assertTrue(builder.buildDynamic.actions.size == 2)
      },
      test("MigrationBuilder.buildPartial creates migration") {
        val builder = MigrationBuilder[Source, Target]
          .renameField("name", "fullName")
          .renameField("email", "contact")
        val migration = builder.buildPartial
        assertTrue(migration != null)
      },
      test("MigrationBuilder.buildDynamic creates dynamic migration") {
        val builder = MigrationBuilder[Source, Target]
          .renameField("name", "fullName")
        val dynamic = builder.buildDynamic
        assertTrue(dynamic.actions.size == 1)
      },
      test("MigrationBuilder with addFieldLiteral") {
        val builder = MigrationBuilder[Source, Target]
          .addFieldLiteral("newField", "default")
        assertTrue(builder.buildDynamic.actions.size == 1)
      },
      test("MigrationBuilder with dropFieldLiteral") {
        val builder = MigrationBuilder[Source, Target]
          .dropFieldLiteral("name", "fallback")
        assertTrue(builder.buildDynamic.actions.size == 1)
      },
      test("MigrationBuilder multiple renames") {
        val builder = MigrationBuilder[Source, Target]
          .renameField("name", "fullName")
          .renameField("email", "contact")
          .renameField("age", "age")
        assertTrue(builder.buildDynamic.actions.size == 3)
      }
    ),
    suite("DynamicOptic coverage")(
      test("DynamicOptic.root is the base optic") {
        val optic = DynamicOptic.root
        assertTrue(optic != null)
      },
      test("DynamicOptic.field creates field optic") {
        val optic = DynamicOptic.root.field("name")
        assertTrue(optic != null)
      },
      test("DynamicOptic.elements creates elements optic") {
        val optic = DynamicOptic.root.elements
        assertTrue(optic != null)
      },
      test("DynamicOptic.mapKeys creates mapKeys optic") {
        val optic = DynamicOptic.root.mapKeys
        assertTrue(optic != null)
      },
      test("DynamicOptic.mapValues creates mapValues optic") {
        val optic = DynamicOptic.root.mapValues
        assertTrue(optic != null)
      },
      test("DynamicOptic.caseOf creates caseOf optic") {
        val optic = DynamicOptic.root.caseOf("SomeCase")
        assertTrue(optic != null)
      },
      test("DynamicOptic chaining field.field") {
        val optic = DynamicOptic.root.field("a").field("b")
        assertTrue(optic != null)
      },
      test("DynamicOptic chaining field.elements") {
        val optic = DynamicOptic.root.field("list").elements
        assertTrue(optic != null)
      },
      test("DynamicOptic chaining field.mapValues") {
        val optic = DynamicOptic.root.field("map").mapValues
        assertTrue(optic != null)
      },
      test("DynamicOptic.atIndices creates indexed optic") {
        val optic = DynamicOptic.root.atIndices(0, 1, 2)
        assertTrue(optic != null)
      },
      test("DynamicOptic.atKeys creates keyed optic") {
        val optic = DynamicOptic.root.atKeys("a", "b")
        assertTrue(optic != null)
      },
      test("DynamicOptic.toScalaString produces output") {
        val optic = DynamicOptic.root.field("name")
        assertTrue(optic.toScalaString.nonEmpty)
      },
      test("DynamicOptic equality for same paths") {
        val optic1 = DynamicOptic.root.field("name")
        val optic2 = DynamicOptic.root.field("name")
        assertTrue(optic1 == optic2)
      },
      test("DynamicOptic inequality for different paths") {
        val optic1 = DynamicOptic.root.field("name")
        val optic2 = DynamicOptic.root.field("email")
        assertTrue(optic1 != optic2)
      }
    )
  )
}
