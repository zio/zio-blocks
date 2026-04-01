package zio.blocks.schema.migration

import zio.blocks.schema.{DynamicOptic, DynamicValue}

/**
 * Represents an error that occurred during migration.
 * 
 * Migration errors capture both the error message and the path where the error occurred,
 * enabling precise diagnostics such as:
 * 
 * "Failed to apply TransformValue at `.addresses.each.streetNumber`"
 */
sealed trait MigrationError extends Product with Serializable {

  /**
   * The error message describing what went wrong.
   */
  def message: String

  /**
   * The path where the error occurred, if available.
   */
  def path: Option[DynamicOptic]

  /**
   * Returns a new error with an updated message.
   */
  def withMessage(newMessage: String): MigrationError

  /**
   * Returns a new error with an updated path.
   */
  def withPath(newPath: DynamicOptic): MigrationError

  /**
   * Returns a human-readable string representation of this error.
   */
  override def toString: String = this match {
    case MigrationError.FieldNotFound(name, pathOpt) =>
      s"FieldNotFound: field '$name' not found${pathOpt.map(p => s" at path '$p'").getOrElse("")}"
    case MigrationError.CaseNotFound(name, pathOpt) =>
      s"CaseNotFound: case '$name' not found${pathOpt.map(p => s" at path '$p'").getOrElse("")}"
    case MigrationError.TypeMismatch(expected, actual, pathOpt) =>
      s"TypeMismatch: expected $expected but got $actual${pathOpt.map(p => s" at path '$p'").getOrElse("")}"
    case MigrationError.TransformFailed(msg, pathOpt) =>
      s"TransformFailed: $msg${pathOpt.map(p => s" at path '$p'").getOrElse("")}"
    case MigrationError.ValueNotFound(pathOpt) =>
      s"ValueNotFound: value not found${pathOpt.map(p => s" at path '$p'").getOrElse("")}"
    case MigrationError.InvalidOperation(msg, pathOpt) =>
      s"InvalidOperation: $msg${pathOpt.map(p => s" at path '$p'").getOrElse("")}"
    case MigrationError.CompositionFailed(msg, pathOpt) =>
      s"CompositionFailed: $msg${pathOpt.map(p => s" at path '$p'").getOrElse("")}"
    case MigrationError.SerializationFailed(msg, pathOpt) =>
      s"SerializationFailed: $msg${pathOpt.map(p => s" at path '$p'").getOrElse("")}"
    case MigrationError.Generic(msg, pathOpt) =>
      s"MigrationError: $msg${pathOpt.map(p => s" at path '$p'").getOrElse("")}"
  }
}

object MigrationError {

  /**
   * A field was not found at the specified path.
   */
  final case class FieldNotFound(fieldName: String, path: Option[DynamicOptic] = None) extends MigrationError {
    def message: String = s"Field '$fieldName' not found"
    def withMessage(newMessage: String): MigrationError = Generic(newMessage, path)
    def withPath(newPath: DynamicOptic): MigrationError = copy(path = Some(newPath))
  }

  /**
   * A variant case was not found at the specified path.
   */
  final case class CaseNotFound(caseName: String, path: Option[DynamicOptic] = None) extends MigrationError {
    def message: String = s"Case '$caseName' not found"
    def withMessage(newMessage: String): MigrationError = Generic(newMessage, path)
    def withPath(newPath: DynamicOptic): MigrationError = copy(path = Some(newPath))
  }

  /**
   * A type mismatch occurred during migration.
   */
  final case class TypeMismatch(expected: String, actual: String, path: Option[DynamicOptic] = None) extends MigrationError {
    def message: String = s"Expected $expected but got $actual"
    def withMessage(newMessage: String): MigrationError = Generic(newMessage, path)
    def withPath(newPath: DynamicOptic): MigrationError = copy(path = Some(newPath))
  }

  /**
   * A transformation failed during migration.
   */
  final case class TransformFailed(message: String, path: Option[DynamicOptic] = None) extends MigrationError {
    def withMessage(newMessage: String): MigrationError = copy(message = newMessage)
    def withPath(newPath: DynamicOptic): MigrationError = copy(path = Some(newPath))
  }

  /**
   * A value was not found at the specified path.
   */
  final case class ValueNotFound(path: Option[DynamicOptic] = None) extends MigrationError {
    def message: String = "Value not found"
    def withMessage(newMessage: String): MigrationError = Generic(newMessage, path)
    def withPath(newPath: DynamicOptic): MigrationError = copy(path = Some(newPath))
  }

  /**
   * An invalid operation was attempted.
   */
  final case class InvalidOperation(message: String, path: Option[DynamicOptic] = None) extends MigrationError {
    def withMessage(newMessage: String): MigrationError = copy(message = newMessage)
    def withPath(newPath: DynamicOptic): MigrationError = copy(path = Some(newPath))
  }

  /**
   * Migration composition failed.
   */
  final case class CompositionFailed(message: String, path: Option[DynamicOptic] = None) extends MigrationError {
    def withMessage(newMessage: String): MigrationError = copy(message = newMessage)
    def withPath(newPath: DynamicOptic): MigrationError = copy(path = Some(newPath))
  }

  /**
   * Serialization failed.
   */
  final case class SerializationFailed(message: String, path: Option[DynamicOptic] = None) extends MigrationError {
    def withMessage(newMessage: String): MigrationError = copy(message = newMessage)
    def withPath(newPath: DynamicOptic): MigrationError = copy(path = Some(newPath))
  }

  /**
   * A generic migration error.
   */
  final case class Generic(message: String, path: Option[DynamicOptic] = None) extends MigrationError {
    def withMessage(newMessage: String): MigrationError = copy(message = newMessage)
    def withPath(newPath: DynamicOptic): MigrationError = copy(path = Some(newPath))
  }

  // Convenience constructors
  def fieldNotFound(name: String, path: DynamicOptic): MigrationError = 
    FieldNotFound(name, Some(path))
  
  def fieldNotFound(name: String): MigrationError = 
    FieldNotFound(name, None)
  
  def caseNotFound(name: String, path: DynamicOptic): MigrationError = 
    CaseNotFound(name, Some(path))
  
  def caseNotFound(name: String): MigrationError = 
    CaseNotFound(name, None)
  
  def typeMismatch(expected: String, actual: String, path: DynamicOptic): MigrationError = 
    TypeMismatch(expected, actual, Some(path))
  
  def typeMismatch(expected: String, actual: String): MigrationError = 
    TypeMismatch(expected, actual, None)
  
  def transformFailed(msg: String, path: DynamicOptic): MigrationError = 
    TransformFailed(msg, Some(path))
  
  def transformFailed(msg: String): MigrationError = 
    TransformFailed(msg, None)
  
  def valueNotFound(path: DynamicOptic): MigrationError = 
    ValueNotFound(Some(path))
  
  def valueNotFound: MigrationError = 
    ValueNotFound(None)
  
  def invalidOperation(msg: String, path: DynamicOptic): MigrationError = 
    InvalidOperation(msg, Some(path))
  
  def invalidOperation(msg: String): MigrationError = 
    InvalidOperation(msg, None)
  
  def compositionFailed(msg: String, path: DynamicOptic): MigrationError = 
    CompositionFailed(msg, Some(path))
  
  def compositionFailed(msg: String): MigrationError = 
    CompositionFailed(msg, None)
  
  def serializationFailed(msg: String, path: DynamicOptic): MigrationError = 
    SerializationFailed(msg, Some(path))
  
  def serializationFailed(msg: String): MigrationError = 
    SerializationFailed(msg, None)
  
  def generic(msg: String, path: DynamicOptic): MigrationError = 
    Generic(msg, Some(path))
  
  def generic(msg: String): MigrationError = 
    Generic(msg, None)
}
