package zio.blocks.schema.migration

import zio.blocks.schema.DynamicOptic

sealed trait MigrationError extends Throwable with Product with Serializable {
  def message: String
  override final def getMessage: String = message
}

object MigrationError {

  final case class MissingPath(path: DynamicOptic) extends MigrationError {
    val message: String = s"Missing path: $path"
  }

  final case class TypeMismatch(
      path: DynamicOptic,
      expected: String,
      got: String
  ) extends MigrationError {
    val message: String = s"Type mismatch at $path. Expected=$expected Got=$got"
  }

  final case class InvalidOp(op: String, details: String)
      extends MigrationError {
    val message: String = s"Invalid op: $op ($details)"
  }

  final case class UnknownEnumCase(path: DynamicOptic, got: String)
      extends MigrationError {
    val message: String = s"Unknown enum case at $path: $got"
  }

  final case class OpticCheckFailed(check: zio.blocks.schema.OpticCheck)
      extends MigrationError {
    override def message: String = s"SchemaExpr optic check failed: $check"
  }

}
