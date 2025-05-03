package zio.blocks.schema

import scala.util.control.NoStackTrace

final case class OpticCheck(errors: ::[OpticCheck.Single]) extends Exception with NoStackTrace {
  def ++(other: OpticCheck): OpticCheck = OpticCheck(::(errors.head, errors.tail ++ other.errors))

  def message: String = errors.map(_.message).mkString("\n")

  override def getMessage: String = message
}
object OpticCheck {
  def unexpectedCase(
    expectedCase: String,
    actualCase: String,
    full: DynamicOptic,
    prefix: DynamicOptic,
    actualValue: Any
  ): OpticCheck = OpticCheck(::(UnexpectedCase(expectedCase, actualCase, full, prefix, actualValue), Nil))

  sealed trait Single {
    def full: DynamicOptic
    def prefix: DynamicOptic
    def actualValue: Any
    def message: String
  }

  final case class UnexpectedCase(
    expectedCase: String,
    actualCase: String,
    full: DynamicOptic,
    prefix: DynamicOptic,
    actualValue: Any
  ) extends Single {
    def message: String =
      s"During attempted access at $full, encountered an unexpected case at $prefix: Expected $expectedCase, but got $actualCase ($actualValue)"
  }
}
