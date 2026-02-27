package zio.blocks.schema.migration

import zio.blocks.schema.DynamicOptic

/**
 * Represents an error that occurred during migration application.
 *
 * All errors include a `path` indicating where in the data structure the error
 * occurred, enabling precise diagnostics like:
 * "Failed to apply TransformValue at `.addresses.each.streetNumber`"
 */
sealed trait MigrationError {
  def path: DynamicOptic
  def message: String
  override def toString: String = s"MigrationError at $path: $message"
}

object MigrationError {

  /** A field that was expected to exist was not found. */
  final case class MissingField(path: DynamicOptic, fieldName: String) extends MigrationError {
    def message: String = s"Missing field '$fieldName'"
  }

  /** A variant case that was expected was not found. */
  final case class MissingCase(path: DynamicOptic, caseName: String) extends MigrationError {
    def message: String = s"Missing case '$caseName'"
  }

  /** A value transformation failed. */
  final case class TransformFailed(path: DynamicOptic, details: String) extends MigrationError {
    def message: String = s"Transform failed: $details"
  }

  /** A type conversion failed. */
  final case class TypeMismatch(path: DynamicOptic, expected: String, actual: String) extends MigrationError {
    def message: String = s"Type mismatch: expected $expected, got $actual"
  }

  /** A field already exists when trying to add it. */
  final case class FieldAlreadyExists(path: DynamicOptic, fieldName: String) extends MigrationError {
    def message: String = s"Field '$fieldName' already exists"
  }

  /** Expected an Option/nullable value but got a non-optional value. */
  final case class MandateFailed(path: DynamicOptic, details: String) extends MigrationError {
    def message: String = s"Mandate failed: $details"
  }

  /** General-purpose migration error. */
  final case class General(path: DynamicOptic, message: String) extends MigrationError
}
