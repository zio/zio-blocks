package zio.blocks.schema.migration

import zio.blocks.schema.{DynamicOptic, SchemaError}

import scala.util.control.NoStackTrace

final case class MigrationError(errors: ::[MigrationError.Single]) extends Exception with NoStackTrace {
  def ++(other: MigrationError): MigrationError =
    MigrationError(new ::(errors.head, errors.tail ++ other.errors))

  override def getMessage: String = message

  def message: String = errors
    .foldLeft(new java.lang.StringBuilder) {
      var lineFeed = false
      (sb, e) =>
        if (lineFeed) sb.append('\n')
        else lineFeed = true
        sb.append(e.message)
    }
    .toString
}

object MigrationError {

  def single(error: Single): MigrationError = new MigrationError(new ::(error, Nil))

  def actionFailed(path: DynamicOptic, actionName: String, details: String): MigrationError =
    single(ActionFailed(path, actionName, details, None))

  def actionFailed(path: DynamicOptic, actionName: String, details: String, cause: SchemaError): MigrationError =
    single(ActionFailed(path, actionName, details, Some(cause)))

  def missingField(path: DynamicOptic, fieldName: String): MigrationError =
    single(MissingField(path, fieldName))

  def typeMismatch(path: DynamicOptic, expected: String, actual: String): MigrationError =
    single(TypeMismatch(path, expected, actual))

  def invalidPath(path: DynamicOptic, details: String): MigrationError =
    single(InvalidPath(path, details))

  def transformFailed(path: DynamicOptic, details: String): MigrationError =
    single(TransformFailed(path, details, None))

  def transformFailed(path: DynamicOptic, details: String, cause: SchemaError): MigrationError =
    single(TransformFailed(path, details, Some(cause)))

  def fromSchemaError(error: SchemaError): MigrationError =
    single(WrappedSchemaError(error))

  sealed trait Single {
    def message: String
    def path: DynamicOptic
  }

  final case class ActionFailed(
    path: DynamicOptic,
    actionName: String,
    details: String,
    cause: Option[SchemaError]
  ) extends Single {
    override def message: String = {
      val base = s"$actionName failed at $path: $details"
      cause.fold(base)(err => s"$base\nCaused by: ${err.message}")
    }
  }

  final case class MissingField(path: DynamicOptic, fieldName: String) extends Single {
    override def message: String = s"Field '$fieldName' not found at $path"
  }

  final case class TypeMismatch(path: DynamicOptic, expected: String, actual: String) extends Single {
    override def message: String = s"Expected $expected at $path, got $actual"
  }

  final case class InvalidPath(path: DynamicOptic, details: String) extends Single {
    override def message: String = s"Invalid path at $path: $details"
  }

  final case class TransformFailed(
    path: DynamicOptic,
    details: String,
    cause: Option[SchemaError]
  ) extends Single {
    override def message: String = {
      val base = s"Transform failed at $path: $details"
      cause.fold(base)(err => s"$base\nCaused by: ${err.message}")
    }
  }

  final case class WrappedSchemaError(error: SchemaError) extends Single {
    override def path: DynamicOptic = error.errors.head.source
    override def message: String = error.message
  }
}
