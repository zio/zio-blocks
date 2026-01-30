package zio.blocks.schema

import scala.util.control.NoStackTrace

/**
 * Represents an error that occurred during schema migration.
 *
 * All migration errors capture the path (as a `DynamicOptic`) where the error
 * occurred, enabling diagnostics such as: "Failed to apply TransformValue at
 * .addresses.each.streetNumber"
 */
sealed trait MigrationError extends Exception with NoStackTrace { self =>

  /**
   * The path in the value structure where the error occurred.
   */
  def path: DynamicOptic

  /**
   * A human-readable description of the error.
   */
  def message: String

  override def getMessage: String = toString

  override def toString: String =
    if (path.nodes.isEmpty) message
    else s"$message at: ${path.toString}"
}

object MigrationError {

  /**
   * A field that was expected to exist was not found.
   */
  final case class MissingField(path: DynamicOptic, fieldName: String) extends MigrationError {
    def message: String = s"Missing field: $fieldName"
  }

  /**
   * A field that was expected to not exist already exists.
   */
  final case class FieldAlreadyExists(path: DynamicOptic, fieldName: String) extends MigrationError {
    def message: String = s"Field already exists: $fieldName"
  }

  /**
   * The value at the path was not of the expected type.
   */
  final case class TypeMismatch(path: DynamicOptic, expected: String, actual: String) extends MigrationError {
    def message: String = s"Type mismatch: expected $expected but found $actual"
  }

  /**
   * A variant case that was expected to exist was not found.
   */
  final case class UnknownCase(path: DynamicOptic, caseName: String) extends MigrationError {
    def message: String = s"Unknown case: $caseName"
  }

  /**
   * A value transformation (via SchemaExpr) failed.
   */
  final case class TransformFailed(path: DynamicOptic, details: String) extends MigrationError {
    def message: String = s"Transform failed: $details"
  }

  /**
   * The path could not be resolved in the value structure.
   */
  final case class InvalidPath(path: DynamicOptic, details: String) extends MigrationError {
    def message: String = s"Invalid path: $details"
  }

  /**
   * A required default value was not available.
   */
  final case class MissingDefault(path: DynamicOptic, fieldName: String) extends MigrationError {
    def message: String = s"Missing default value for field: $fieldName"
  }

  /**
   * An action could not be reversed because insufficient information was
   * provided.
   */
  final case class IrreversibleAction(path: DynamicOptic, actionName: String) extends MigrationError {
    def message: String = s"Action is not reversible: $actionName"
  }

  // Convenience constructors at root path

  def missingField(fieldName: String): MigrationError =
    MissingField(DynamicOptic.root, fieldName)

  def fieldAlreadyExists(fieldName: String): MigrationError =
    FieldAlreadyExists(DynamicOptic.root, fieldName)

  def typeMismatch(expected: String, actual: String): MigrationError =
    TypeMismatch(DynamicOptic.root, expected, actual)

  def unknownCase(caseName: String): MigrationError =
    UnknownCase(DynamicOptic.root, caseName)

  def transformFailed(details: String): MigrationError =
    TransformFailed(DynamicOptic.root, details)

  def invalidPath(details: String): MigrationError =
    InvalidPath(DynamicOptic.root, details)

  def missingDefault(fieldName: String): MigrationError =
    MissingDefault(DynamicOptic.root, fieldName)

  def irreversibleAction(actionName: String): MigrationError =
    IrreversibleAction(DynamicOptic.root, actionName)
}
