package zio.blocks.schema.migration

import scala.util.control.NoStackTrace
import zio.blocks.schema.{DynamicOptic, SchemaError}

/**
 * Represents errors that can occur during migration execution.
 * All errors capture path information via `DynamicOptic` to enable
 * precise diagnostics.
 */
final case class MigrationError(errors: ::[MigrationError.Single]) extends Exception with NoStackTrace {

  def ++(other: MigrationError): MigrationError =
    MigrationError(new ::(errors.head, errors.tail ++ other.errors))

  override def getMessage: String = message

  def message: String = errors
    .foldLeft(new java.lang.StringBuilder) {
      var lineFeed = false
      (sb, e) =>
        if (lineFeed) sb.append('\n')
        else lineFeed = true
        sb.append(e.message)
    }
    .toString
}

object MigrationError {

  def pathNotFound(path: DynamicOptic): MigrationError =
    single(PathNotFound(path))

  def typeMismatch(path: DynamicOptic, expected: String, actual: String): MigrationError =
    single(TypeMismatch(path, expected, actual))

  def missingDefault(path: DynamicOptic, fieldName: String): MigrationError =
    single(MissingDefault(path, fieldName))

  def transformFailed(path: DynamicOptic, reason: String): MigrationError =
    single(TransformFailed(path, reason))

  def fieldNotFound(path: DynamicOptic, fieldName: String): MigrationError =
    single(FieldNotFound(path, fieldName))

  def fieldAlreadyExists(path: DynamicOptic, fieldName: String): MigrationError =
    single(FieldAlreadyExists(path, fieldName))

  def caseNotFound(path: DynamicOptic, caseName: String): MigrationError =
    single(CaseNotFound(path, caseName))

  def invalidValue(path: DynamicOptic, reason: String): MigrationError =
    single(InvalidValue(path, reason))

  def mandateFailed(path: DynamicOptic, reason: String): MigrationError =
    single(MandateFailed(path, reason))

  /**
   * Convert a SchemaError to a MigrationError.
   */
  def fromSchemaError(error: SchemaError): MigrationError = {
    val migrationErrors = error.errors.map { schemaError =>
      SchemaConversionError(schemaError.source, schemaError.message): Single
    }
    new MigrationError(new ::(migrationErrors.head, migrationErrors.tail))
  }

  private def single(error: Single): MigrationError =
    new MigrationError(new ::(error, Nil))

  /**
   * Base trait for individual migration errors.
   */
  sealed trait Single {
    def path: DynamicOptic
    def message: String
  }

  /**
   * The specified path does not exist in the value being migrated.
   */
  final case class PathNotFound(path: DynamicOptic) extends Single {
    override def message: String = s"Path not found: $path"
  }

  /**
   * Type mismatch at the specified path.
   */
  final case class TypeMismatch(path: DynamicOptic, expected: String, actual: String) extends Single {
    override def message: String = s"Type mismatch at $path: expected $expected, got $actual"
  }

  /**
   * A required default value was not provided for the specified field.
   */
  final case class MissingDefault(path: DynamicOptic, fieldName: String) extends Single {
    override def message: String = s"Missing default value for field '$fieldName' at $path"
  }

  /**
   * A value transformation failed at the specified path.
   */
  final case class TransformFailed(path: DynamicOptic, reason: String) extends Single {
    override def message: String = s"Transform failed at $path: $reason"
  }

  /**
   * The specified field was not found in the record.
   */
  final case class FieldNotFound(path: DynamicOptic, fieldName: String) extends Single {
    override def message: String = s"Field '$fieldName' not found at $path"
  }

  /**
   * The specified field already exists in the record (cannot add).
   */
  final case class FieldAlreadyExists(path: DynamicOptic, fieldName: String) extends Single {
    override def message: String = s"Field '$fieldName' already exists at $path"
  }

  /**
   * The specified enum case was not found.
   */
  final case class CaseNotFound(path: DynamicOptic, caseName: String) extends Single {
    override def message: String = s"Case '$caseName' not found at $path"
  }

  /**
   * The value at the specified path is invalid.
   */
  final case class InvalidValue(path: DynamicOptic, reason: String) extends Single {
    override def message: String = s"Invalid value at $path: $reason"
  }

  /**
   * Converting an optional to a required field failed (None with no default).
   */
  final case class MandateFailed(path: DynamicOptic, reason: String) extends Single {
    override def message: String = s"Mandate failed at $path: $reason"
  }

  /**
   * Error from schema conversion (fromDynamicValue failed).
   */
  final case class SchemaConversionError(path: DynamicOptic, details: String) extends Single {
    override def message: String = s"Schema conversion error at $path: $details"
  }
}

