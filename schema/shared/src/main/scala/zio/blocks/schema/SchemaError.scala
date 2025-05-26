package zio.blocks.schema

import scala.util.control.NoStackTrace

final case class SchemaError(errors: ::[SchemaError.Single]) extends Exception with NoStackTrace {
  def ++(other: SchemaError): SchemaError = SchemaError(::(errors.head, errors.tail ++ other.errors))

  override def getMessage: String = message

  def message: String = errors.map(_.message).mkString("\n")
}

object SchemaError {
  def invalidType(trace: List[DynamicOptic.Node], message: String): SchemaError =
    new SchemaError(new ::(new InvalidType(DynamicOptic(trace.toVector.reverse), message), Nil))

  def missingField(trace: List[DynamicOptic.Node], fieldName: String): SchemaError =
    new SchemaError(new ::(new MissingField(DynamicOptic(trace.toVector.reverse), fieldName), Nil))

  def duplicatedField(trace: List[DynamicOptic.Node], fieldName: String): SchemaError =
    new SchemaError(new ::(new DuplicatedField(DynamicOptic(trace.toVector.reverse), fieldName), Nil))

  def unknownCase(trace: List[DynamicOptic.Node], caseName: String): SchemaError =
    new SchemaError(new ::(new UnknownCase(DynamicOptic(trace.toVector.reverse), caseName), Nil))

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
