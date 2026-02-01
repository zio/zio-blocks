package zio.blocks.schema.migration

import zio.blocks.schema.DynamicOptic

/**
 * Errors that can occur during migration execution. All errors include the full
 * path from root for precise error reporting.
 */
sealed trait MigrationError {
  def path: DynamicOptic
  def message: String

  /**
   * Render the error with path information.
   */
  def render: String = s"Failed at ${path.toScalaString}: $message"
}

object MigrationError {

  /**
   * The specified path was not found in the value.
   */
  final case class PathNotFound(
    path: DynamicOptic,
    available: Set[String]
  ) extends MigrationError {
    def message: String = s"Path not found. Available: ${available.mkString(", ")}"
  }

  /**
   * Type at the path did not match expected type.
   */
  final case class TypeMismatch(
    path: DynamicOptic,
    expected: String,
    actual: String
  ) extends MigrationError {
    def message: String = s"Type mismatch: expected $expected, got $actual"
  }

  /**
   * A transform operation failed.
   */
  final case class TransformFailed(
    path: DynamicOptic,
    cause: String
  ) extends MigrationError {
    def message: String = s"Transform failed: $cause"
  }

  /**
   * Validation of the migration result failed.
   */
  final case class ValidationFailed(
    path: DynamicOptic,
    reason: String
  ) extends MigrationError {
    def message: String = s"Validation failed: $reason"
  }

  /**
   * Enum case was not found.
   */
  final case class CaseNotFound(
    path: DynamicOptic,
    caseName: String,
    available: Set[String]
  ) extends MigrationError {
    def message: String = s"Case '$caseName' not found. Available: ${available.mkString(", ")}"
  }

  /**
   * No default value available when one was required.
   */
  final case class NoDefaultValue(
    path: DynamicOptic,
    typeName: String
  ) extends MigrationError {
    def message: String = s"No default value for $typeName"
  }
}
