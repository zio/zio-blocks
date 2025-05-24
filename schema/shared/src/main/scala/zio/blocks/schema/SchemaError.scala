package zio.blocks.schema

import scala.util.control.NoStackTrace

final case class SchemaError(errors: ::[SchemaError.Single]) extends Exception with NoStackTrace {
  def ++(other: SchemaError): SchemaError = SchemaError(::(errors.head, errors.tail ++ other.errors))

  override def getMessage: String = message

  def message: String = errors.map(_.message).mkString("\n")
}

object SchemaError {
  def invalidType(source: DynamicOptic, message: String): SchemaError =
    new SchemaError(new ::(new InvalidType(source, message), Nil))

  def missingField(source: DynamicOptic, fieldName: String): SchemaError =
    new SchemaError(new ::(new MissingField(source, fieldName), Nil))

  def duplicatedField(source: DynamicOptic, fieldName: String): SchemaError =
    new SchemaError(new ::(new DuplicatedField(source, fieldName), Nil))

  def unknownCase(source: DynamicOptic, caseName: String): SchemaError =
    new SchemaError(new ::(new UnknownCase(source, caseName), Nil))

  sealed trait Single {
    def message: String

    def source: DynamicOptic
  }

  case class MissingField(source: DynamicOptic, fieldName: String) extends Single {
    override def message: String = s"Missing field $fieldName"
  }

  case class DuplicatedField(source: DynamicOptic, fieldName: String) extends Single {
    override def message: String = s"Duplicated field $fieldName"
  }

  case class InvalidType(source: DynamicOptic, message: String) extends Single

  case class UnknownCase(source: DynamicOptic, caseName: String) extends Single {
    override def message: String = s"Unknown case $caseName"
  }
}
