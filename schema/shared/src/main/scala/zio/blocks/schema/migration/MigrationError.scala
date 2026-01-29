package zio.blocks.schema.migration

import zio.blocks.schema.DynamicOptic

/**
 * Errors that can occur during schema migration.
 *
 * All errors include path information to enable precise diagnostics.
 */
sealed trait MigrationError {
  def message: String
}

object MigrationError {

  /**
   * Error that occurred during expression evaluation at a specific path.
   */
  final case class EvaluationError(path: DynamicOptic, message: String) extends MigrationError

  /**
   * Error that occurred during validation (e.g., schema mismatch).
   */
  final case class ValidationError(message: String) extends MigrationError

  /**
   * Error indicating a path was not found in the value.
   */
  final case class PathNotFound(path: DynamicOptic) extends MigrationError {
    def message: String = s"Path not found: ${path.toString}"
  }

  /**
   * Error indicating a field operation failed.
   */
  final case class FieldError(path: DynamicOptic, fieldName: String, reason: String) extends MigrationError {
    def message: String = s"Field operation failed at ${path.toString} for field '$fieldName': $reason"
  }

  /**
   * Error indicating a type mismatch.
   */
  final case class TypeMismatchError(path: DynamicOptic, expected: String, actual: String) extends MigrationError {
    def message: String = s"Type mismatch at ${path.toString}: expected $expected, got $actual"
  }

  /**
   * Error indicating an operation is not supported.
   */
  final case class UnsupportedOperationError(path: DynamicOptic, operation: String) extends MigrationError {
    def message: String = s"Operation '$operation' not supported at ${path.toString}"
  }

  /**
   * Error indicating migration composition failed.
   */
  final case class CompositionError(reason: String) extends MigrationError {
    def message: String = s"Migration composition failed: $reason"
  }

  /**
   * Error indicating reverse migration failed.
   */
  final case class ReverseError(path: DynamicOptic, reason: String) extends MigrationError {
    def message: String = s"Reverse migration failed at ${path.toString}: $reason"
  }
}
