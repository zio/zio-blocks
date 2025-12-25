package zio.blocks.schema.migration

import zio.blocks.schema.DynamicOptic
import zio.blocks.schema.DynamicValue

sealed trait MigrationError extends Exception {
  def path: DynamicOptic
  def message: String
  override def getMessage: String = s"Migration error at $path: $message"
}

object MigrationError {
  final case class PathNotFound(path: DynamicOptic, message: String) extends MigrationError

  final case class TypeMismatch(path: DynamicOptic, expected: String, actual: DynamicValue) extends MigrationError {
    def message: String = s"Expected type $expected but got ${actual.getClass.getSimpleName}"
  }

  final case class CalculationError(path: DynamicOptic, cause: String) extends MigrationError {
    def message: String = s"Calculation failed: $cause"
  }

  final case class InvalidOperation(path: DynamicOptic, message: String) extends MigrationError
}
