package zio.blocks.schema.migration

import zio.blocks.schema.DynamicOptic

sealed trait MigrationError {
  def message: String
}

object MigrationError {
  final case class PathError(path: DynamicOptic, reason: String) extends MigrationError {
    def message: String = s"Failed to apply action at ${path}: $reason"
  }
  
  final case class EvaluationError(reason: String) extends MigrationError {
    def message: String = s"Evaluation error: $reason"
  }
}
