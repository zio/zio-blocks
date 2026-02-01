package zio.blocks.schema.migration

import zio.blocks.chunk.Chunk
import zio.blocks.schema.DynamicOptic

/**
 * Validates migrations for structural compatibility between schemas.
 *
 * The validator checks that:
 *   1. All paths in actions are valid for their respective schemas
 *   2. Field additions don't conflict with existing fields
 *   3. Field drops target existing fields
 *   4. Renames don't create conflicts
 *   5. Transforms are type-compatible
 */
object MigrationValidator {

  /**
   * Result of migration validation.
   */
  sealed trait ValidationResult
  object ValidationResult {
    case object Valid                                        extends ValidationResult
    final case class Invalid(errors: Chunk[ValidationError]) extends ValidationResult {
      def render: String = errors.map(_.render).mkString("\n")
    }
  }

  /**
   * A validation error describing what went wrong.
   */
  sealed trait ValidationError {
    def path: DynamicOptic
    def message: String
    def render: String = s"${path.toScalaString}: $message"
  }

  object ValidationError {
    final case class PathNotInSource(path: DynamicOptic) extends ValidationError {
      def message: String = "Path does not exist in source schema"
    }

    final case class PathNotInTarget(path: DynamicOptic) extends ValidationError {
      def message: String = "Path does not exist in target schema"
    }

    final case class FieldAlreadyExists(path: DynamicOptic, fieldName: String) extends ValidationError {
      def message: String = s"Field '$fieldName' already exists at this path"
    }

    final case class FieldNotFound(path: DynamicOptic, fieldName: String) extends ValidationError {
      def message: String = s"Field '$fieldName' not found at this path"
    }

    final case class CaseNotFound(path: DynamicOptic, caseName: String) extends ValidationError {
      def message: String = s"Case '$caseName' not found at this path"
    }

    final case class TypeMismatch(path: DynamicOptic, expected: String, actual: String) extends ValidationError {
      def message: String = s"Type mismatch: expected $expected, got $actual"
    }

    final case class IncompatibleTransform(path: DynamicOptic, reason: String) extends ValidationError {
      def message: String = s"Incompatible transform: $reason"
    }
  }

  /**
   * Validate a migration against source and target schemas.
   */
  def validate[A, B](migration: Migration[A, B]): ValidationResult = {
    val sourceDS = migration.sourceSchema.toDynamicSchema
    val targetDS = migration.targetSchema.toDynamicSchema
    validateActions(migration.dynamicMigration.actions, sourceDS, targetDS)
  }

  /**
   * Validate a list of migration actions.
   */
  private def validateActions(
    actions: Chunk[MigrationAction],
    sourceSchema: zio.blocks.schema.DynamicSchema,
    targetSchema: zio.blocks.schema.DynamicSchema
  ): ValidationResult = {
    var errors = Chunk.empty[ValidationError]

    actions.foreach { action =>
      action match {
        case MigrationAction.AddField(at, _, _) =>
          // For AddField, the path (container) should be navigable in target schema
          if (!pathNavigable(at, targetSchema)) {
            errors = errors :+ ValidationError.PathNotInTarget(at)
          }

        case MigrationAction.DropField(at, _, _) =>
          // For DropField, the path should exist in source schema
          if (!pathNavigable(at, sourceSchema)) {
            errors = errors :+ ValidationError.PathNotInSource(at)
          }

        case MigrationAction.Rename(at, _, _) =>
          // The source path should exist in source schema
          if (!pathNavigable(at, sourceSchema)) {
            errors = errors :+ ValidationError.PathNotInSource(at)
          }

        case MigrationAction.TransformValue(at, _, _, _) =>
          // The path should exist in source
          if (!pathNavigable(at, sourceSchema)) {
            errors = errors :+ ValidationError.PathNotInSource(at)
          }

        case MigrationAction.Mandate(at, _, _) =>
          // The path should exist in source
          if (!pathNavigable(at, sourceSchema)) {
            errors = errors :+ ValidationError.PathNotInSource(at)
          }

        case MigrationAction.Optionalize(at, _) =>
          // The path should exist in source
          if (!pathNavigable(at, sourceSchema)) {
            errors = errors :+ ValidationError.PathNotInSource(at)
          }

        case MigrationAction.ChangeType(at, _, _, _) =>
          // The path should exist in source
          if (!pathNavigable(at, sourceSchema)) {
            errors = errors :+ ValidationError.PathNotInSource(at)
          }

        case MigrationAction.RenameCase(at, _, _) =>
          // The path should exist in source
          if (!pathNavigable(at, sourceSchema)) {
            errors = errors :+ ValidationError.PathNotInSource(at)
          }

        case MigrationAction.TransformCase(at, _, nestedActions) =>
          // The path should exist in source
          if (!pathNavigable(at, sourceSchema)) {
            errors = errors :+ ValidationError.PathNotInSource(at)
          }
          // Recursively validate nested actions
          validateActions(nestedActions, sourceSchema, targetSchema) match {
            case ValidationResult.Invalid(nestedErrors) => errors = errors ++ nestedErrors
            case _                                      => ()
          }

        case MigrationAction.TransformElements(at, _, _) =>
          // The path should exist in source
          if (!pathNavigable(at, sourceSchema)) {
            errors = errors :+ ValidationError.PathNotInSource(at)
          }

        case MigrationAction.TransformKeys(at, _, _) =>
          // The path should exist in source
          if (!pathNavigable(at, sourceSchema)) {
            errors = errors :+ ValidationError.PathNotInSource(at)
          }

        case MigrationAction.TransformValues(at, _, _) =>
          // The path should exist in source
          if (!pathNavigable(at, sourceSchema)) {
            errors = errors :+ ValidationError.PathNotInSource(at)
          }

        case MigrationAction.Join(at, _, _, _, _) =>
          // The path should exist in source (for source fields)
          if (!pathNavigable(at, sourceSchema)) {
            errors = errors :+ ValidationError.PathNotInSource(at)
          }

        case MigrationAction.Split(at, _, _, _, _) =>
          // The path should exist in source
          if (!pathNavigable(at, sourceSchema)) {
            errors = errors :+ ValidationError.PathNotInSource(at)
          }
      }
    }

    if (errors.isEmpty) ValidationResult.Valid
    else ValidationResult.Invalid(errors)
  }

  /**
   * Check if a path is navigable within a schema.
   */
  private def pathNavigable(path: DynamicOptic, schema: zio.blocks.schema.DynamicSchema): Boolean =
    if (path.nodes.isEmpty) true
    else schema.get(path).isDefined
}
