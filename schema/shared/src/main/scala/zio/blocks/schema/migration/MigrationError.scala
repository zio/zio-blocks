package zio.blocks.schema.migration

import zio.blocks.schema.DynamicOptic

/**
 * Describes an error that occurred during migration.
 *
 * Every error includes the `DynamicOptic` path at which the error occurred,
 * enabling precise diagnostics such as: "Failed to apply TransformValue at
 * .addresses.each.streetNumber"
 */
sealed trait MigrationError {
  def message: String
  def path: DynamicOptic
}

object MigrationError {

  final case class FieldNotFound(fieldName: String, path: DynamicOptic) extends MigrationError {
    def message: String = s"Field '$fieldName' not found at $path"
  }

  final case class FieldAlreadyExists(fieldName: String, path: DynamicOptic) extends MigrationError {
    def message: String = s"Field '$fieldName' already exists at $path"
  }

  final case class TypeMismatch(expected: String, actual: String, path: DynamicOptic) extends MigrationError {
    def message: String = s"Expected $expected but found $actual at $path"
  }

  final case class TransformFailed(reason: String, path: DynamicOptic) extends MigrationError {
    def message: String = s"Transform failed at $path: $reason"
  }

  final case class CaseNotFound(caseName: String, path: DynamicOptic) extends MigrationError {
    def message: String = s"Case '$caseName' not found at $path"
  }

  final case class Custom(reason: String, path: DynamicOptic) extends MigrationError {
    def message: String = s"Migration error at $path: $reason"
  }

  final case class Composed(first: MigrationError, second: MigrationError) extends MigrationError {
    def message: String    = s"${first.message}; then ${second.message}"
    def path: DynamicOptic = first.path
  }
}
