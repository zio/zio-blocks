package zio.schema.migration

import zio.schema._

/** Errors that can occur during schema migration */
sealed trait MigrationError {
  def message: String
}

object MigrationError {
  case class FieldNotFound(path: String) extends MigrationError {
    def message: String = s"Field not found at path: $path"
  }

  case class TypeMismatch(path: String, expected: String, actual: String) extends MigrationError {
    def message: String =
      s"Type mismatch at $path: expected $expected but got $actual"
  }

  case class ValidationFailed(path: String, reason: String) extends MigrationError {
    def message: String =
      s"Validation failed at $path: $reason"
  }

  case class TransformationFailed(path: String, reason: String) extends MigrationError {
    def message: String =
      s"Transformation failed at $path: $reason"
  }

  case class IrreversibleMigration(reason: String) extends MigrationError {
    def message: String =
      s"Cannot reverse migration: $reason"
  }

  case class InvalidMigration(reason: String) extends MigrationError {
    def message: String =
      s"Invalid migration: $reason"
  }

  implicit val schema: Schema[MigrationError] = DeriveSchema.gen[MigrationError]
}
