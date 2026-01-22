package zio.blocks.schema.migration

import zio.blocks.schema.DynamicOptic

/**
 * Represents errors that can occur during migration execution. All errors
 * capture path information for diagnostics.
 */
sealed trait MigrationError {
  def path: DynamicOptic
  def message: String

  /**
   * Returns a human-readable error message with full path information.
   */
  def prettyPrint: String = message
}

object MigrationError {

  /**
   * Field was not found at the specified path.
   */
  final case class FieldNotFound(
    path: DynamicOptic,
    fieldName: String
  ) extends MigrationError {
    def message: String = s"Field '$fieldName' not found at: $path"
  }

  /**
   * Field already exists at the specified path.
   */
  final case class FieldAlreadyExists(
    path: DynamicOptic,
    fieldName: String
  ) extends MigrationError {
    def message: String = s"Field '$fieldName' already exists at: $path"
  }

  /**
   * Expected a specific structure but found something else.
   */
  final case class InvalidStructure(
    path: DynamicOptic,
    expected: String,
    actual: String
  ) extends MigrationError {
    def message: String = s"Expected $expected but found $actual at: $path"
  }

  /**
   * Error evaluating a SchemaExpr during migration.
   */
  final case class EvaluationError(
    path: DynamicOptic,
    override val message: String
  ) extends MigrationError

  /**
   * Error converting DynamicValue back to typed value. Wraps SchemaError from
   * Schema.fromDynamicValue.
   */
  final case class FromDynamicValueFailed(
    path: DynamicOptic,
    schemaError: zio.blocks.schema.SchemaError
  ) extends MigrationError {
    def message: String = s"Failed to convert from DynamicValue at: $path. ${schemaError.message}"
  }

  /**
   * Intermediate field not found during nested navigation.
   */
  final case class IntermediateFieldNotFound(
    path: DynamicOptic,
    fieldName: String,
    depth: Int
  ) extends MigrationError {
    def message: String = s"Intermediate field '$fieldName' not found at depth $depth while navigating: $path"
  }

  /**
   * Intermediate field is not a Record during nested navigation.
   */
  final case class IntermediateFieldNotRecord(
    path: DynamicOptic,
    fieldName: String,
    depth: Int,
    actualType: String
  ) extends MigrationError {
    def message: String =
      s"Intermediate field '$fieldName' at depth $depth is not a Record (found $actualType) while navigating: $path"
  }

  /**
   * Migration validation failed during build. Captures unhandled source fields
   * and unprovided target fields.
   */
  final case class ValidationError(
    path: DynamicOptic,
    unhandledSourceFields: Set[String],
    unprovidedTargetFields: Set[String]
  ) extends MigrationError {
    def message: String = {
      val parts = Vector.newBuilder[String]
      if (unhandledSourceFields.nonEmpty) {
        parts += s"Unhandled source fields: ${unhandledSourceFields.mkString(", ")}"
      }
      if (unprovidedTargetFields.nonEmpty) {
        parts += s"Unprovided target fields: ${unprovidedTargetFields.mkString(", ")}"
      }
      val details = parts.result().mkString("; ")
      s"Migration validation failed at: $path. $details"
    }
  }

  /**
   * Join operation requires all source paths and the target path to share the
   * same parent record. This error indicates that paths span different branches
   * of the tree.
   */
  final case class CrossPathJoinNotSupported(
    path: DynamicOptic,
    targetPath: DynamicOptic,
    sourcePaths: Vector[DynamicOptic]
  ) extends MigrationError {
    def message: String = {
      val invalidPaths = sourcePaths.filterNot { sourcePath =>
        if (targetPath.nodes.isEmpty || sourcePath.nodes.isEmpty) {
          false // Root-level paths are always invalid for cross-path
        } else if (targetPath.nodes.length != sourcePath.nodes.length) {
          false // Different depths
        } else {
          targetPath.nodes.dropRight(1) == sourcePath.nodes.dropRight(1)
        }
      }
      s"Join operation requires all paths to share the same parent record. Target: $targetPath, Invalid sources: ${invalidPaths.mkString(", ")}"
    }
  }

  /**
   * Split operation requires all target paths and the source path to share the
   * same parent record. This error indicates that paths span different branches
   * of the tree.
   */
  final case class CrossPathSplitNotSupported(
    path: DynamicOptic,
    sourcePath: DynamicOptic,
    targetPaths: Vector[DynamicOptic]
  ) extends MigrationError {
    def message: String = {
      val invalidPaths = targetPaths.filterNot { targetPath =>
        if (sourcePath.nodes.isEmpty || targetPath.nodes.isEmpty) {
          false // Root-level paths are always invalid for cross-path
        } else if (sourcePath.nodes.length != targetPath.nodes.length) {
          false // Different depths
        } else {
          sourcePath.nodes.dropRight(1) == targetPath.nodes.dropRight(1)
        }
      }
      s"Split operation requires all paths to share the same parent record. Source: $sourcePath, Invalid targets: ${invalidPaths.mkString(", ")}"
    }
  }
}
