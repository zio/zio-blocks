package zio.blocks.schema.migration

import zio.blocks.schema.DynamicOptic

sealed trait MigrationError extends Product with Serializable

object MigrationError {
  final case class ConversionError(message: String) extends MigrationError
  final case class PathError(path: DynamicOptic, message: String) extends MigrationError
  final case class TransformationError(path: DynamicOptic, message: String) extends MigrationError
  final case class IncompatibleSchemas(message: String) extends MigrationError
  case object NotYetImplemented extends MigrationError
}
