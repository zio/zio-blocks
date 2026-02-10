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
   * Join operation requires all source paths and the target path to share the
   * same parent record. This error indicates that paths span different branches
   * of the tree.
   */
  final case class CrossPathJoinNotSupported(
    path: DynamicOptic,
    invalidPaths: Vector[DynamicOptic]
  ) extends MigrationError {
    def message: String =
      s"Join operation requires all paths to share the same parent record. Target: $path, Invalid sources: ${invalidPaths.mkString(", ")}"
  }

  /**
   * Split operation requires all target paths and the source path to share the
   * same parent record. This error indicates that paths span different branches
   * of the tree.
   */
  final case class CrossPathSplitNotSupported(
    path: DynamicOptic,
    invalidPaths: Vector[DynamicOptic]
  ) extends MigrationError {
    def message: String =
      s"Split operation requires all paths to share the same parent record. Source: $path, Invalid targets: ${invalidPaths.mkString(", ")}"
  }

  /**
   * Operation cannot be reversed. The reverse of a Join or Split may not be
   * computable when the combiner/splitter expression type is not recognized.
   */
  final case class IrreversibleOperation(
    path: DynamicOptic,
    reason: String
  ) extends MigrationError {
    def message: String = s"Cannot reverse operation at $path: $reason"
  }
}
