package zio.blocks.schema.migration

sealed trait MigrationError extends Throwable with Product with Serializable {
  def message: String
  override final def getMessage: String = message
}

object MigrationError {
  final case class MissingPath(path: Path) extends MigrationError {
    val message: String = s"Missing path: $path"
  }
  final case class TypeMismatch(path: Path, expected: String, got: String) extends MigrationError {
    val message: String = s"Type mismatch at $path. Expected=$expected Got=$got"
  }
  final case class InvalidOp(op: String, details: String) extends MigrationError {
    val message: String = s"Invalid op: $op ($details)"
  }
  final case class UnknownEnumCase(path: Path, got: String) extends MigrationError {
    val message: String = s"Unknown enum case at $path: $got"
  }
}
