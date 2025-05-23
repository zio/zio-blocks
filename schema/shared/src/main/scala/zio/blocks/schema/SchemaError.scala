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
    new SchemaError(new ::(new MissingField(source, fieldName, "Missing field"), Nil))

  def duplicatedField(source: DynamicOptic, fieldName: String): SchemaError =
    new SchemaError(new ::(new DuplicatedField(source, fieldName, "Duplicated field"), Nil))

  def unknownCase(source: DynamicOptic, caseName: String): SchemaError =
    new SchemaError(new ::(new UnknownCase(source, caseName, "Unknown case"), Nil))

  sealed trait Single {
    def message: String

    def source: DynamicOptic
  }

  case class MissingField[S, A](source: DynamicOptic, fieldName: String, message: String) extends Single

  case class DuplicatedField[S, A](source: DynamicOptic, fieldName: String, message: String) extends Single

  case class InvalidType[A](source: DynamicOptic, message: String) extends Single

  case class UnknownCase[S, A](source: DynamicOptic, caseName: String, message: String) extends Single
}
