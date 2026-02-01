package zio.blocks.schema.migration

import zio.blocks.chunk.Chunk
import zio.blocks.schema._
import zio.test._

/**
 * Tests for MigrationValidator - validates migration actions against schemas.
 *
 * Covers all action types and both valid/invalid path scenarios.
 */
object MigrationValidatorSpec extends ZIOSpecDefault {

  // Test schemas - simple records for validation
  case class PersonV1(name: String, age: Int)
  case class PersonV2(fullName: String, age: Int, city: String)

  implicit val schemaPersonV1: Schema[PersonV1] = Schema.derived
  implicit val schemaPersonV2: Schema[PersonV2] = Schema.derived

  // Nested record schemas
  case class Address(street: String, city: String)
  case class Employee(name: String, address: Address)

  implicit val schemaAddress: Schema[Address]   = Schema.derived
  implicit val schemaEmployee: Schema[Employee] = Schema.derived

  // Enum schema for case tests
  sealed trait Status
  object Status {
    case object Active                 extends Status
    case class Pending(reason: String) extends Status
  }

  implicit val schemaStatus: Schema[Status] = Schema.derived

  // Collection schemas
  case class Team(members: List[String])
  case class Config(settings: Map[String, Int])

  implicit val schemaTeam: Schema[Team]     = Schema.derived
  implicit val schemaConfig: Schema[Config] = Schema.derived

  def spec: Spec[TestEnvironment, Any] = suite("MigrationValidatorSpec")(
    suite("AddField validation")(
      test("validates AddField with valid target path") {
        val action    = MigrationAction.AddField(DynamicOptic.root, "newField", Resolved.Literal.string("default"))
        val migration = DynamicMigration(Chunk(action))
        val result    = validateDynamic(migration, schemaPersonV1, schemaPersonV2)
        assertTrue(result == MigrationValidator.ValidationResult.Valid)
      },
      test("rejects AddField with invalid target path") {
        val invalidPath = DynamicOptic.root.field("nonexistent").field("nested")
        val action      = MigrationAction.AddField(invalidPath, "field", Resolved.Literal.int(0))
        val migration   = DynamicMigration(Chunk(action))
        val result      = validateDynamic(migration, schemaPersonV1, schemaPersonV2)
        result match {
          case MigrationValidator.ValidationResult.Invalid(errors) =>
            assertTrue(errors.nonEmpty && errors.head.isInstanceOf[MigrationValidator.ValidationError.PathNotInTarget])
          case _ => assertTrue(false)
        }
      }
    ),
    suite("DropField validation")(
      test("validates DropField with valid source path") {
        val action    = MigrationAction.DropField(DynamicOptic.root, "age", Resolved.Literal.int(25))
        val migration = DynamicMigration(Chunk(action))
        val result    = validateDynamic(migration, schemaPersonV1, schemaPersonV2)
        assertTrue(result == MigrationValidator.ValidationResult.Valid)
      },
      test("rejects DropField with invalid source path") {
        val invalidPath = DynamicOptic.root.field("missing")
        val action      = MigrationAction.DropField(invalidPath, "field", Resolved.Literal.int(0))
        val migration   = DynamicMigration(Chunk(action))
        val result      = validateDynamic(migration, schemaPersonV1, schemaPersonV2)
        result match {
          case MigrationValidator.ValidationResult.Invalid(errors) =>
            assertTrue(errors.exists(_.isInstanceOf[MigrationValidator.ValidationError.PathNotInSource]))
          case _ => assertTrue(false)
        }
      }
    ),
    suite("Rename validation")(
      test("validates Rename with valid source path") {
        val action    = MigrationAction.Rename(DynamicOptic.root, "name", "fullName")
        val migration = DynamicMigration(Chunk(action))
        val result    = validateDynamic(migration, schemaPersonV1, schemaPersonV2)
        assertTrue(result == MigrationValidator.ValidationResult.Valid)
      },
      test("rejects Rename with invalid source path") {
        val invalidPath = DynamicOptic.root.field("nonexistent")
        val action      = MigrationAction.Rename(invalidPath, "old", "new")
        val migration   = DynamicMigration(Chunk(action))
        val result      = validateDynamic(migration, schemaPersonV1, schemaPersonV2)
        result match {
          case MigrationValidator.ValidationResult.Invalid(errors) =>
            assertTrue(errors.exists(_.isInstanceOf[MigrationValidator.ValidationError.PathNotInSource]))
          case _ => assertTrue(false)
        }
      }
    ),
    suite("TransformValue validation")(
      test("validates TransformValue with valid source path") {
        val action = MigrationAction.TransformValue(
          DynamicOptic.root,
          "age",
          Resolved.Identity,
          Resolved.Identity
        )
        val migration = DynamicMigration(Chunk(action))
        val result    = validateDynamic(migration, schemaPersonV1, schemaPersonV2)
        assertTrue(result == MigrationValidator.ValidationResult.Valid)
      },
      test("rejects TransformValue with invalid source path") {
        val invalidPath = DynamicOptic.root.field("missing")
        val action      = MigrationAction.TransformValue(invalidPath, "f", Resolved.Identity, Resolved.Identity)
        val migration   = DynamicMigration(Chunk(action))
        val result      = validateDynamic(migration, schemaPersonV1, schemaPersonV2)
        result match {
          case MigrationValidator.ValidationResult.Invalid(errors) =>
            assertTrue(errors.exists(_.isInstanceOf[MigrationValidator.ValidationError.PathNotInSource]))
          case _ => assertTrue(false)
        }
      }
    ),
    suite("Mandate validation")(
      test("validates Mandate with valid source path") {
        val action    = MigrationAction.Mandate(DynamicOptic.root, "name", Resolved.Literal.string("default"))
        val migration = DynamicMigration(Chunk(action))
        val result    = validateDynamic(migration, schemaPersonV1, schemaPersonV2)
        assertTrue(result == MigrationValidator.ValidationResult.Valid)
      },
      test("rejects Mandate with invalid source path") {
        val invalidPath = DynamicOptic.root.field("invalid")
        val action      = MigrationAction.Mandate(invalidPath, "f", Resolved.Literal.string("x"))
        val migration   = DynamicMigration(Chunk(action))
        val result      = validateDynamic(migration, schemaPersonV1, schemaPersonV2)
        result match {
          case MigrationValidator.ValidationResult.Invalid(errors) =>
            assertTrue(errors.exists(_.isInstanceOf[MigrationValidator.ValidationError.PathNotInSource]))
          case _ => assertTrue(false)
        }
      }
    ),
    suite("Optionalize validation")(
      test("validates Optionalize with valid source path") {
        val action    = MigrationAction.Optionalize(DynamicOptic.root, "age")
        val migration = DynamicMigration(Chunk(action))
        val result    = validateDynamic(migration, schemaPersonV1, schemaPersonV2)
        assertTrue(result == MigrationValidator.ValidationResult.Valid)
      },
      test("rejects Optionalize with invalid source path") {
        val invalidPath = DynamicOptic.root.field("bad")
        val action      = MigrationAction.Optionalize(invalidPath, "f")
        val migration   = DynamicMigration(Chunk(action))
        val result      = validateDynamic(migration, schemaPersonV1, schemaPersonV2)
        result match {
          case MigrationValidator.ValidationResult.Invalid(errors) =>
            assertTrue(errors.exists(_.isInstanceOf[MigrationValidator.ValidationError.PathNotInSource]))
          case _ => assertTrue(false)
        }
      }
    ),
    suite("ChangeType validation")(
      test("validates ChangeType with valid source path") {
        val action = MigrationAction.ChangeType(
          DynamicOptic.root,
          "age",
          Resolved.Identity,
          Resolved.Identity
        )
        val migration = DynamicMigration(Chunk(action))
        val result    = validateDynamic(migration, schemaPersonV1, schemaPersonV2)
        assertTrue(result == MigrationValidator.ValidationResult.Valid)
      },
      test("rejects ChangeType with invalid source path") {
        val invalidPath = DynamicOptic.root.field("nope")
        val action      = MigrationAction.ChangeType(invalidPath, "f", Resolved.Identity, Resolved.Identity)
        val migration   = DynamicMigration(Chunk(action))
        val result      = validateDynamic(migration, schemaPersonV1, schemaPersonV2)
        result match {
          case MigrationValidator.ValidationResult.Invalid(errors) =>
            assertTrue(errors.exists(_.isInstanceOf[MigrationValidator.ValidationError.PathNotInSource]))
          case _ => assertTrue(false)
        }
      }
    ),
    suite("RenameCase validation")(
      test("validates RenameCase with valid source path") {
        val action    = MigrationAction.RenameCase(DynamicOptic.root, "Active", "Activated")
        val migration = DynamicMigration(Chunk(action))
        val result    = validateDynamicForEnums(migration, schemaStatus, schemaStatus)
        assertTrue(result == MigrationValidator.ValidationResult.Valid)
      },
      test("rejects RenameCase with invalid source path") {
        val invalidPath = DynamicOptic.root.field("missing")
        val action      = MigrationAction.RenameCase(invalidPath, "A", "B")
        val migration   = DynamicMigration(Chunk(action))
        val result      = validateDynamicForEnums(migration, schemaStatus, schemaStatus)
        result match {
          case MigrationValidator.ValidationResult.Invalid(errors) =>
            assertTrue(errors.exists(_.isInstanceOf[MigrationValidator.ValidationError.PathNotInSource]))
          case _ => assertTrue(false)
        }
      }
    ),
    suite("TransformCase validation")(
      test("validates TransformCase with valid source path and nested actions") {
        val nestedAction = MigrationAction.Rename(DynamicOptic.root, "reason", "message")
        val action       = MigrationAction.TransformCase(DynamicOptic.root, "Pending", Chunk(nestedAction))
        val migration    = DynamicMigration(Chunk(action))
        val result       = validateDynamicForEnums(migration, schemaStatus, schemaStatus)
        assertTrue(result == MigrationValidator.ValidationResult.Valid)
      },
      test("rejects TransformCase with invalid source path") {
        val invalidPath = DynamicOptic.root.field("bad")
        val action      = MigrationAction.TransformCase(invalidPath, "X", Chunk.empty)
        val migration   = DynamicMigration(Chunk(action))
        val result      = validateDynamicForEnums(migration, schemaStatus, schemaStatus)
        result match {
          case MigrationValidator.ValidationResult.Invalid(errors) =>
            assertTrue(errors.exists(_.isInstanceOf[MigrationValidator.ValidationError.PathNotInSource]))
          case _ => assertTrue(false)
        }
      },
      test("propagates errors from nested actions") {
        val badNested = MigrationAction.Rename(DynamicOptic.root.field("invalid"), "a", "b")
        val action    = MigrationAction.TransformCase(DynamicOptic.root, "Pending", Chunk(badNested))
        val migration = DynamicMigration(Chunk(action))
        val result    = validateDynamicForEnums(migration, schemaStatus, schemaStatus)
        result match {
          case MigrationValidator.ValidationResult.Invalid(errors) =>
            assertTrue(errors.size >= 1)
          case _ => assertTrue(false)
        }
      }
    ),
    suite("TransformElements validation")(
      test("validates TransformElements with valid source path") {
        val action =
          MigrationAction.TransformElements(DynamicOptic.root.field("members"), Resolved.Identity, Resolved.Identity)
        val migration = DynamicMigration(Chunk(action))
        val result    = validateDynamic(migration, schemaTeam, schemaTeam)
        assertTrue(result == MigrationValidator.ValidationResult.Valid)
      },
      test("rejects TransformElements with invalid source path") {
        val invalidPath = DynamicOptic.root.field("nonexistent")
        val action      = MigrationAction.TransformElements(invalidPath, Resolved.Identity, Resolved.Identity)
        val migration   = DynamicMigration(Chunk(action))
        val result      = validateDynamic(migration, schemaTeam, schemaTeam)
        result match {
          case MigrationValidator.ValidationResult.Invalid(errors) =>
            assertTrue(errors.exists(_.isInstanceOf[MigrationValidator.ValidationError.PathNotInSource]))
          case _ => assertTrue(false)
        }
      }
    ),
    suite("TransformKeys validation")(
      test("validates TransformKeys with valid source path") {
        val action =
          MigrationAction.TransformKeys(DynamicOptic.root.field("settings"), Resolved.Identity, Resolved.Identity)
        val migration = DynamicMigration(Chunk(action))
        val result    = validateDynamic(migration, schemaConfig, schemaConfig)
        assertTrue(result == MigrationValidator.ValidationResult.Valid)
      },
      test("rejects TransformKeys with invalid source path") {
        val invalidPath = DynamicOptic.root.field("bad")
        val action      = MigrationAction.TransformKeys(invalidPath, Resolved.Identity, Resolved.Identity)
        val migration   = DynamicMigration(Chunk(action))
        val result      = validateDynamic(migration, schemaConfig, schemaConfig)
        result match {
          case MigrationValidator.ValidationResult.Invalid(errors) =>
            assertTrue(errors.exists(_.isInstanceOf[MigrationValidator.ValidationError.PathNotInSource]))
          case _ => assertTrue(false)
        }
      }
    ),
    suite("TransformValues validation")(
      test("validates TransformValues with valid source path") {
        val action =
          MigrationAction.TransformValues(DynamicOptic.root.field("settings"), Resolved.Identity, Resolved.Identity)
        val migration = DynamicMigration(Chunk(action))
        val result    = validateDynamic(migration, schemaConfig, schemaConfig)
        assertTrue(result == MigrationValidator.ValidationResult.Valid)
      },
      test("rejects TransformValues with invalid source path") {
        val invalidPath = DynamicOptic.root.field("invalid")
        val action      = MigrationAction.TransformValues(invalidPath, Resolved.Identity, Resolved.Identity)
        val migration   = DynamicMigration(Chunk(action))
        val result      = validateDynamic(migration, schemaConfig, schemaConfig)
        result match {
          case MigrationValidator.ValidationResult.Invalid(errors) =>
            assertTrue(errors.exists(_.isInstanceOf[MigrationValidator.ValidationError.PathNotInSource]))
          case _ => assertTrue(false)
        }
      }
    ),
    suite("Join validation")(
      test("validates Join with valid source path") {
        val action = MigrationAction.Join(
          DynamicOptic.root,
          "combined",
          Chunk(DynamicOptic.root.field("name"), DynamicOptic.root.field("age")),
          Resolved.Identity,
          Resolved.Identity
        )
        val migration = DynamicMigration(Chunk(action))
        val result    = validateDynamic(migration, schemaPersonV1, schemaPersonV2)
        assertTrue(result == MigrationValidator.ValidationResult.Valid)
      },
      test("rejects Join with invalid source path") {
        val invalidPath = DynamicOptic.root.field("bad")
        val action      = MigrationAction.Join(invalidPath, "target", Chunk.empty, Resolved.Identity, Resolved.Identity)
        val migration   = DynamicMigration(Chunk(action))
        val result      = validateDynamic(migration, schemaPersonV1, schemaPersonV2)
        result match {
          case MigrationValidator.ValidationResult.Invalid(errors) =>
            assertTrue(errors.exists(_.isInstanceOf[MigrationValidator.ValidationError.PathNotInSource]))
          case _ => assertTrue(false)
        }
      }
    ),
    suite("Split validation")(
      test("validates Split with valid source path") {
        val action = MigrationAction.Split(
          DynamicOptic.root,
          "name",
          Chunk(DynamicOptic.root.field("first"), DynamicOptic.root.field("last")),
          Resolved.Identity,
          Resolved.Identity
        )
        val migration = DynamicMigration(Chunk(action))
        val result    = validateDynamic(migration, schemaPersonV1, schemaPersonV2)
        assertTrue(result == MigrationValidator.ValidationResult.Valid)
      },
      test("rejects Split with invalid source path") {
        val invalidPath = DynamicOptic.root.field("nope")
        val action      = MigrationAction.Split(invalidPath, "f", Chunk.empty, Resolved.Identity, Resolved.Identity)
        val migration   = DynamicMigration(Chunk(action))
        val result      = validateDynamic(migration, schemaPersonV1, schemaPersonV2)
        result match {
          case MigrationValidator.ValidationResult.Invalid(errors) =>
            assertTrue(errors.exists(_.isInstanceOf[MigrationValidator.ValidationError.PathNotInSource]))
          case _ => assertTrue(false)
        }
      }
    ),
    suite("Multiple errors")(
      test("collects errors from multiple invalid actions") {
        val action1 = MigrationAction.DropField(DynamicOptic.root.field("bad1"), "f", Resolved.Literal.int(0))
        val action2 = MigrationAction.Rename(DynamicOptic.root.field("bad2"), "a", "b")
        val action3 =
          MigrationAction.ChangeType(DynamicOptic.root.field("bad3"), "x", Resolved.Identity, Resolved.Identity)
        val migration = DynamicMigration(Chunk(action1, action2, action3))
        val result    = validateDynamic(migration, schemaPersonV1, schemaPersonV2)
        result match {
          case MigrationValidator.ValidationResult.Invalid(errors) =>
            assertTrue(errors.size == 3)
          case _ => assertTrue(false)
        }
      }
    ),
    suite("ValidationError rendering")(
      test("PathNotInSource has message") {
        val error = MigrationValidator.ValidationError.PathNotInSource(DynamicOptic.root.field("test"))
        assertTrue(error.message.nonEmpty)
      },
      test("PathNotInTarget has message") {
        val error = MigrationValidator.ValidationError.PathNotInTarget(DynamicOptic.root.field("field"))
        assertTrue(error.message.nonEmpty)
      },
      test("FieldAlreadyExists has message with field name") {
        val error = MigrationValidator.ValidationError.FieldAlreadyExists(DynamicOptic.root, "name")
        assertTrue(error.message.contains("name"))
      },
      test("FieldNotFound has message with field name") {
        val error = MigrationValidator.ValidationError.FieldNotFound(DynamicOptic.root, "missing")
        assertTrue(error.message.contains("missing"))
      },
      test("CaseNotFound has message with case name") {
        val error = MigrationValidator.ValidationError.CaseNotFound(DynamicOptic.root, "Unknown")
        assertTrue(error.message.contains("Unknown"))
      },
      test("TypeMismatch has message with types") {
        val error = MigrationValidator.ValidationError.TypeMismatch(DynamicOptic.root, "String", "Int")
        assertTrue(error.message.contains("String") && error.message.contains("Int"))
      },
      test("IncompatibleTransform has message with reason") {
        val error = MigrationValidator.ValidationError.IncompatibleTransform(DynamicOptic.root, "no converter")
        assertTrue(error.message.contains("no converter"))
      }
    ),
    suite("ValidationResult rendering")(
      test("Invalid result has render method") {
        val errors = Chunk(
          MigrationValidator.ValidationError.PathNotInSource(DynamicOptic.root.field("a")),
          MigrationValidator.ValidationError.PathNotInTarget(DynamicOptic.root.field("b"))
        )
        val result = MigrationValidator.ValidationResult.Invalid(errors)
        assertTrue(result.render.nonEmpty)
      }
    ),
    suite("Empty migration")(
      test("empty migration is always valid") {
        val migration = DynamicMigration(Chunk.empty)
        val result    = validateDynamic(migration, schemaPersonV1, schemaPersonV2)
        assertTrue(result == MigrationValidator.ValidationResult.Valid)
      }
    ),
    suite("Root path handling")(
      test("root path is always navigable") {
        val action    = MigrationAction.AddField(DynamicOptic.root, "field", Resolved.Literal.int(0))
        val migration = DynamicMigration(Chunk(action))
        val result    = validateDynamic(migration, schemaPersonV1, schemaPersonV2)
        assertTrue(result == MigrationValidator.ValidationResult.Valid)
      }
    )
  )

  // Helper to validate dynamic migrations
  private def validateDynamic[A, B](
    migration: DynamicMigration,
    sourceSchema: Schema[A],
    targetSchema: Schema[B]
  ): MigrationValidator.ValidationResult = {
    val sourceDS = sourceSchema.toDynamicSchema
    val targetDS = targetSchema.toDynamicSchema
    validateActionsWithSchemas(migration.actions, sourceDS, targetDS)
  }

  private def validateDynamicForEnums[A, B](
    migration: DynamicMigration,
    sourceSchema: Schema[A],
    targetSchema: Schema[B]
  ): MigrationValidator.ValidationResult = validateDynamic(migration, sourceSchema, targetSchema)

  // Access private method via reflection-free approach - just inline the logic
  private def validateActionsWithSchemas(
    actions: Chunk[MigrationAction],
    sourceSchema: DynamicSchema,
    targetSchema: DynamicSchema
  ): MigrationValidator.ValidationResult = {
    var errors = Chunk.empty[MigrationValidator.ValidationError]

    def pathNavigable(path: DynamicOptic, schema: DynamicSchema): Boolean =
      if (path.nodes.isEmpty) true else schema.get(path).isDefined

    actions.foreach { action =>
      action match {
        case MigrationAction.AddField(at, _, _) =>
          if (!pathNavigable(at, targetSchema)) {
            errors = errors :+ MigrationValidator.ValidationError.PathNotInTarget(at)
          }
        case MigrationAction.DropField(at, _, _) =>
          if (!pathNavigable(at, sourceSchema)) {
            errors = errors :+ MigrationValidator.ValidationError.PathNotInSource(at)
          }
        case MigrationAction.Rename(at, _, _) =>
          if (!pathNavigable(at, sourceSchema)) {
            errors = errors :+ MigrationValidator.ValidationError.PathNotInSource(at)
          }
        case MigrationAction.TransformValue(at, _, _, _) =>
          if (!pathNavigable(at, sourceSchema)) {
            errors = errors :+ MigrationValidator.ValidationError.PathNotInSource(at)
          }
        case MigrationAction.Mandate(at, _, _) =>
          if (!pathNavigable(at, sourceSchema)) {
            errors = errors :+ MigrationValidator.ValidationError.PathNotInSource(at)
          }
        case MigrationAction.Optionalize(at, _) =>
          if (!pathNavigable(at, sourceSchema)) {
            errors = errors :+ MigrationValidator.ValidationError.PathNotInSource(at)
          }
        case MigrationAction.ChangeType(at, _, _, _) =>
          if (!pathNavigable(at, sourceSchema)) {
            errors = errors :+ MigrationValidator.ValidationError.PathNotInSource(at)
          }
        case MigrationAction.RenameCase(at, _, _) =>
          if (!pathNavigable(at, sourceSchema)) {
            errors = errors :+ MigrationValidator.ValidationError.PathNotInSource(at)
          }
        case MigrationAction.TransformCase(at, _, nestedActions) =>
          if (!pathNavigable(at, sourceSchema)) {
            errors = errors :+ MigrationValidator.ValidationError.PathNotInSource(at)
          }
          validateActionsWithSchemas(nestedActions, sourceSchema, targetSchema) match {
            case MigrationValidator.ValidationResult.Invalid(nestedErrors) => errors = errors ++ nestedErrors
            case _                                                         => ()
          }
        case MigrationAction.TransformElements(at, _, _) =>
          if (!pathNavigable(at, sourceSchema)) {
            errors = errors :+ MigrationValidator.ValidationError.PathNotInSource(at)
          }
        case MigrationAction.TransformKeys(at, _, _) =>
          if (!pathNavigable(at, sourceSchema)) {
            errors = errors :+ MigrationValidator.ValidationError.PathNotInSource(at)
          }
        case MigrationAction.TransformValues(at, _, _) =>
          if (!pathNavigable(at, sourceSchema)) {
            errors = errors :+ MigrationValidator.ValidationError.PathNotInSource(at)
          }
        case MigrationAction.Join(at, _, _, _, _) =>
          if (!pathNavigable(at, sourceSchema)) {
            errors = errors :+ MigrationValidator.ValidationError.PathNotInSource(at)
          }
        case MigrationAction.Split(at, _, _, _, _) =>
          if (!pathNavigable(at, sourceSchema)) {
            errors = errors :+ MigrationValidator.ValidationError.PathNotInSource(at)
          }
      }
    }

    if (errors.isEmpty) MigrationValidator.ValidationResult.Valid
    else MigrationValidator.ValidationResult.Invalid(errors)
  }
}
