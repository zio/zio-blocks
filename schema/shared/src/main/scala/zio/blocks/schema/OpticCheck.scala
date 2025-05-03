package zio.blocks.schema

import scala.util.control.NoStackTrace

final case class OpticCheck(errors: ::[OpticCheck.Single]) extends Exception with NoStackTrace {
  def ++(other: OpticCheck): OpticCheck = OpticCheck(::(errors.head, errors.tail ++ other.errors))

  def isWarning: Boolean = errors.exists(_.isWarning)

  def isError: Boolean = errors.exists(_.isError)

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

    def isWarning: Boolean = this match {
      case _: Warning => true
      case _          => false
    }

    def isError: Boolean = !isWarning
  }

  sealed trait Error   extends Single
  sealed trait Warning extends Single

  final case class UnexpectedCase(
    expectedCase: String,
    actualCase: String,
    full: DynamicOptic,
    prefix: DynamicOptic,
    actualValue: Any
  ) extends Error {
    def message: String =
      s"During attempted access at $full, encountered an unexpected case at $prefix: Expected $expectedCase, but got $actualCase ($actualValue)"
  }
}
