package zio.blocks.schema.migration

import zio.blocks.schema.{DynamicOptic, SchemaError}

/**
 * Errors that can occur during migration operations.
 */
sealed trait MigrationError { self =>

  /**
   * The path at which the error occurred.
   */
  def path: DynamicOptic

  /**
   * A human-readable message describing the error.
   */
  def message: String
}

object MigrationError {

  /**
   * Indicates that a required field is missing.
   */
  final case class MissingField(
    path: DynamicOptic,
    fieldName: String
  ) extends MigrationError {
    def message: String = s"Missing required field '$fieldName' at ${path.toString}"
  }

  /**
   * Indicates that a field could not be transformed.
   */
  final case class TransformationFailed(
    path: DynamicOptic,
    reason: String
  ) extends MigrationError {
    def message: String = s"Transformation failed at ${path.toString}: $reason"
  }

  /**
   * Indicates a type mismatch during migration.
   */
  final case class TypeMismatch(
    path: DynamicOptic,
    expectedType: String,
    actualType: String
  ) extends MigrationError {
    def message: String =
      s"Type mismatch at ${path.toString}: expected $expectedType, got $actualType"
  }

  /**
   * Indicates a validation error.
   */
  final case class ValidationError(
    path: DynamicOptic,
    errors: Seq[SchemaError]
  ) extends MigrationError {
    def message: String =
      s"Validation failed at ${path.toString}: ${errors.map(_.message).mkString(", ")}"
  }

  /**
   * Indicates that a reverse migration failed.
   */
  final case class ReverseMigrationFailed(
    path: DynamicOptic,
    originalError: MigrationError
  ) extends MigrationError {
    def message: String =
      s"Reverse migration failed at ${path.toString}: ${originalError.message}"
  }

  /**
   * Indicates an unsupported operation.
   */
  final case class Unsupported(
    path: DynamicOptic,
    operation: String
  ) extends MigrationError {
    def message: String =
      s"Unsupported operation '$operation' at ${path.toString}"
  }

  /**
   * Combines multiple errors into one.
   */
  final case class Accumulated(
    errors: Seq[MigrationError]
  ) extends MigrationError {
    def path: DynamicOptic = DynamicOptic(IndexedSeq.empty)
    def message: String =
      s"Migration failed with ${errors.length} error(s): ${errors.map(_.message).mkString("; ")}"
  }
}
