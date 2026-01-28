package zio.blocks.schema.migration

import zio.blocks.schema.DynamicOptic

sealed trait MigrationError {
  def message: String
}

object MigrationError {
  final case class EvaluationError(path: DynamicOptic, message: String) extends MigrationError
  final case class ValidationError(message: String) extends MigrationError
}
