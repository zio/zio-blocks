package zio.blocks.schema.migration

import zio.blocks.schema.DynamicOptic
import scala.util.control.NoStackTrace

/**
 * Represents errors that can occur during schema migration.
 * 
 * All errors include path information via DynamicOptic to help with debugging.
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
  
  // Helper to create single-error instances
  private def single(error: Single): MigrationError = 
    new MigrationError(new ::(error, Nil))

  def fieldNotFound(at: DynamicOptic, fieldName: String): MigrationError =
    single(new FieldNotFound(at, fieldName))

  def fieldAlreadyExists(at: DynamicOptic, fieldName: String): MigrationError =
    single(new FieldAlreadyExists(at, fieldName))

  def typeMismatch(at: DynamicOptic, expected: String, actual: String): MigrationError =
    single(new TypeMismatch(at, expected, actual))

  def transformFailed(at: DynamicOptic, reason: String): MigrationError =
    single(new TransformFailed(at, reason))

  def caseNotFound(at: DynamicOptic, caseName: String): MigrationError =
    single(new CaseNotFound(at, caseName))

  def invalidPath(at: DynamicOptic, reason: String): MigrationError =
    single(new InvalidPath(at, reason))

  def mandatoryFieldMissing(at: DynamicOptic, fieldName: String): MigrationError =
    single(new MandatoryFieldMissing(at, fieldName))

  def incompatibleSchemas(reason: String): MigrationError =
    single(new IncompatibleSchemas(reason))

  def validationFailed(at: DynamicOptic, reason: String): MigrationError =
    single(new ValidationFailed(at, reason))

  sealed trait Single {
    def message: String
    def source: DynamicOptic
  }

  case class FieldNotFound(source: DynamicOptic, fieldName: String) extends Single {
    override def message: String = s"Field '$fieldName' not found at: $source"
  }

  case class FieldAlreadyExists(source: DynamicOptic, fieldName: String) extends Single {
    override def message: String = s"Field '$fieldName' already exists at: $source"
  }

  case class TypeMismatch(source: DynamicOptic, expected: String, actual: String) extends Single {
    override def message: String = 
      s"Type mismatch at: $source - expected $expected but got $actual"
  }

  case class TransformFailed(source: DynamicOptic, reason: String) extends Single {
    override def message: String = s"Transform failed at: $source - $reason"
  }

  case class CaseNotFound(source: DynamicOptic, caseName: String) extends Single {
    override def message: String = s"Case '$caseName' not found at: $source"
  }

  case class InvalidPath(source: DynamicOptic, reason: String) extends Single {
    override def message: String = s"Invalid path at: $source - $reason"
  }

  case class MandatoryFieldMissing(source: DynamicOptic, fieldName: String) extends Single {
    override def message: String = 
      s"Mandatory field '$fieldName' is missing at: $source"
  }

  case class IncompatibleSchemas(reason: String) extends Single {
    override def message: String = s"Incompatible schemas: $reason"
    override def source: DynamicOptic = DynamicOptic.root
  }

  case class ValidationFailed(source: DynamicOptic, reason: String) extends Single {
    override def message: String = s"Validation failed at: $source - $reason"
  }
}

