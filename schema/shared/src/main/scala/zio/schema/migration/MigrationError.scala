package zio.schema.migration

/**
 * Represents failure modes during a pure structural migration.
 */
sealed trait MigrationError {
  def message: String
}

object MigrationError {
  final case class PathNotFound(optic: DynamicOptic, value: zio.schema.DynamicValue) extends MigrationError {
    def message: String = s"Cannot apply migration action. Optic path $optic not found in $value."
  }
  
  final case class InvalidTypeCorrection(expected: String, actual: String, optic: DynamicOptic) extends MigrationError {
    def message: String = s"Type mismatch at path $optic. Expected $expected but found $actual."
  }

  final case class UnrecoverableParseError(msg: String) extends MigrationError {
    def message: String = s"Unrecoverable state during migration application: $msg"
  }
}
