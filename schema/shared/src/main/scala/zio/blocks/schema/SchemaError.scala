package zio.blocks.schema

import scala.util.control.NoStackTrace

final case class SchemaError(errors: ::[SchemaError.Single]) extends Exception with NoStackTrace {
  def ++(other: SchemaError): SchemaError = SchemaError(::(errors.head, errors.tail ++ other.errors))

  override def getMessage: String = message

  def message: String = errors.map(_.message).mkString("\n")
}

object SchemaError {
  def invalidData[A](
    source: DynamicOptic,
    focus: DynamicOptic,
    expected: Validation[A],
    actual: A,
    message: String
  ): SchemaError =
    SchemaError(::(InvalidData(source, focus, expected, actual, message), Nil))

  def invalidType[A](source: DynamicOptic, message: String): SchemaError =
    SchemaError(::(InvalidType(source, message), Nil))

  def missingCase[S, A](source: DynamicOptic, caseName: String, message: String): SchemaError =
    SchemaError(::(MissingCase(source, caseName, message), Nil))

  def missingField[S, A](source: DynamicOptic, fieldName: String, message: String): SchemaError =
    SchemaError(::(MissingField(source, fieldName, message), Nil))

  def unknownField[S, A](source: DynamicOptic, fieldName: String, message: String): SchemaError =
    SchemaError(::(UnknownField(source, fieldName, message), Nil))

  def unknownCase[S, A](source: DynamicOptic, caseName: String, message: String): SchemaError =
    SchemaError(::(UnknownCase(source, caseName, message), Nil))

  sealed trait Single {
    def message: String

    def source: DynamicOptic
  }
  case class InvalidData[A](
    source: DynamicOptic,
    focus: DynamicOptic,
    expected: Validation[A],
    actual: A,
    message: String
  ) extends Single

  case class MissingField[S, A](source: DynamicOptic, fieldName: String, message: String) extends Single

  case class UnknownField[S, A](source: DynamicOptic, fieldName: String, message: String) extends Single

  case class InvalidType[A](source: DynamicOptic, message: String) extends Single

  case class MissingCase[S, A](source: DynamicOptic, caseName: String, message: String) extends Single

  case class UnknownCase[S, A](source: DynamicOptic, caseName: String, message: String) extends Single
}
