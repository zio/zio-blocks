package zio.blocks.schema

import scala.util.control.NoStackTrace

final case class OpticCheck(errors: ::[OpticCheck.Single]) extends Exception with NoStackTrace {
  def ++(other: OpticCheck): OpticCheck = OpticCheck(::(errors.head, errors.tail ++ other.errors))

  override def getMessage: String = message

  def isWarning: Boolean = errors.exists(_.isWarning)

  def isError: Boolean = errors.exists(_.isError)

  def message: String = errors.map(_.message).mkString("\n")

  def shift(prefix: DynamicOptic): OpticCheck =
    OpticCheck(errors.map(_.shift(prefix)).asInstanceOf[::[OpticCheck.Single]])

}
object OpticCheck {
  def emptyMap(full: DynamicOptic, prefix: DynamicOptic, actualValue: Any): OpticCheck =
    OpticCheck(::(EmptyMap(full, prefix, actualValue), Nil))

  def emptySequence(full: DynamicOptic, prefix: DynamicOptic, actualValue: Any): OpticCheck =
    OpticCheck(::(EmptySequence(full, prefix, actualValue), Nil))

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

    def shift(prefix0: DynamicOptic): Single
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

    def shift(prefix0: DynamicOptic): UnexpectedCase =
      copy(full = prefix0(full), prefix = prefix0(prefix))
  }

  final case class EmptySequence(full: DynamicOptic, prefix: DynamicOptic, actualValue: Any) extends Warning {
    def message: String =
      s"During attempted access at $full, encountered an empty sequence at $prefix: $actualValue"

    def shift(prefix0: DynamicOptic): EmptySequence =
      copy(full = prefix0(full), prefix = prefix0(prefix))
  }

  final case class EmptyMap(full: DynamicOptic, prefix: DynamicOptic, actualValue: Any) extends Warning {
    def message: String =
      s"During attempted access at $full, encountered an empty map at $prefix: $actualValue"

    def shift(prefix0: DynamicOptic): EmptyMap =
      copy(full = prefix0(full), prefix = prefix0(prefix))
  }
}
