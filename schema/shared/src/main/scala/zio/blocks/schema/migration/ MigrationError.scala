package zio.blocks.schema.migration

import zio.blocks.schema.DynamicOptic

sealed trait MigrationError extends Exception {
  def message: String
  override def getMessage: String = message
}

object MigrationError {
  
  case class FieldNotFound(path: DynamicOptic, fieldName: String) extends MigrationError {
    def message: String = s"Field '$fieldName' not found at path: ${path.toString}"
  }

  case class TypeMismatch(path: DynamicOptic, expected: String, actual: String) extends MigrationError {
    def message: String = s"Type mismatch at path ${path.toString}: expected $expected, but found $actual"
  }

  case class CaseNotFound(path: DynamicOptic, caseName: String) extends MigrationError {
    def message: String = s"Enum case '$caseName' not found at path: ${path.toString}"
  }

  case class TransformationFailed(path: DynamicOptic, error: String) extends MigrationError {
    def message: String = s"Failed to apply transformation at ${path.toString}: $error"
  }

  case class SchemaMismatch(error: String) extends MigrationError {
    def message: String = s"Final schema validation failed: $error"
  }

  case class DecodingError(error: String) extends MigrationError {
    def message: String = s"Failed to decode dynamic value: $error"
  }
}