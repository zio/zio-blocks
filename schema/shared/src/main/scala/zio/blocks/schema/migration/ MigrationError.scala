package zio.blocks.schema.migration

// সঠিক ইমপোর্ট পাথ আপনার প্রজেক্ট অনুযায়ী
import zio.blocks.schema.DynamicOptic

sealed trait MigrationError extends Exception {
  def message: String
  override def getMessage: String = message
}

object MigrationError {
  final case class FieldNotFound(path: DynamicOptic, fieldName: String) extends MigrationError {
    def message: String = s"Field '$fieldName' not found at path: ${path.toString}"
  }

  final case class TypeMismatch(path: DynamicOptic, expected: String, actual: String) extends MigrationError {
    def message: String = s"Type mismatch at path ${path.toString}: expected $expected, but found $actual"
  }

  final case class CaseNotFound(path: DynamicOptic, caseName: String) extends MigrationError {
    def message: String = s"Enum case '$caseName' not found at path: ${path.toString}"
  }

  final case class TransformationFailed(path: DynamicOptic, error: String) extends MigrationError {
    def message: String = s"Transformation failed at path ${path.toString}: $error"
  }

  final case class SchemaMismatch(error: String) extends MigrationError {
    def message: String = s"Final schema validation failed: $error"
  }
}