package zio.blocks.schema

import scala.util.control.NoStackTrace

final case class OpticCheck(errors: ::[OpticCheck.Single]) extends Exception with NoStackTrace {
  def ++(other: OpticCheck): OpticCheck = new OpticCheck(new ::(errors.head, errors.tail ++ other.errors))

  def hasWarning: Boolean = errors.exists(_.isWarning)

  def hasError: Boolean = errors.exists(_.isError)

  def message: String = errors.map(_.message).mkString("\n")

  override def getMessage: String = message
}

object OpticCheck {
  sealed trait Single {
    def full: DynamicOptic

    def prefix: DynamicOptic

    def message: String

    def isWarning: Boolean = this match {
      case _: Warning => true
      case _          => false
    }

    def isError: Boolean = !isWarning
  }

  sealed trait Error extends Single

  sealed trait Warning extends Single

  case class UnexpectedCase(
    expectedCase: String,
    actualCase: String,
    full: DynamicOptic,
    prefix: DynamicOptic,
    actualValue: Any
  ) extends Error {
    def message: String =
      s"During attempted access at $full, encountered an unexpected case at $prefix: expected $expectedCase, but got $actualCase"
  }

  case class EmptySequence(full: DynamicOptic, prefix: DynamicOptic) extends Warning {
    def message: String =
      s"During attempted access at $full, encountered an empty sequence at $prefix"
  }

  case class SequenceIndexOutOfBounds(full: DynamicOptic, prefix: DynamicOptic, index: Int, size: Int) extends Warning {
    def message: String =
      s"During attempted access at $full, encountered a sequence out of bounds at $prefix: index is $index, but size is $size"
  }

  case class MissingKey(full: DynamicOptic, prefix: DynamicOptic, key: Any) extends Warning {
    def message: String =
      s"During attempted access at $full, encountered missing key at $prefix"
  }

  case class EmptyMap(full: DynamicOptic, prefix: DynamicOptic) extends Warning {
    def message: String =
      s"During attempted access at $full, encountered an empty map at $prefix"
  }
}
