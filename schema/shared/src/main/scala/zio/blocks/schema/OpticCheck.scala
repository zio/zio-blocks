package zio.blocks.schema

import java.lang
import scala.util.control.NoStackTrace

final case class OpticCheck(errors: ::[OpticCheck.Single]) extends Exception with NoStackTrace {
  def ++(other: OpticCheck): OpticCheck = new OpticCheck(new ::(errors.head, errors.tail ++ other.errors))

  def hasWarning: Boolean = errors.exists(_.isWarning)

  def hasError: Boolean = errors.exists(_.isError)

  def message: String = {
    val sb = new lang.StringBuilder
    errors.foreach { e =>
      if (sb.length > 0) sb.append('\n')
      sb.append(e.message)
    }
    sb.toString
  }

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
      s"During attempted access at ${full.toScalaString}, encountered an unexpected case at ${prefix.toScalaString}: expected $expectedCase, but got $actualCase"
  }

  case class EmptySequence(full: DynamicOptic, prefix: DynamicOptic) extends Warning {
    def message: String =
      s"During attempted access at ${full.toScalaString}, encountered an empty sequence at ${prefix.toScalaString}"
  }

  case class SequenceIndexOutOfBounds(full: DynamicOptic, prefix: DynamicOptic, index: Int, size: Int) extends Warning {
    def message: String =
      s"During attempted access at ${full.toScalaString}, encountered a sequence out of bounds at ${prefix.toScalaString}: index is $index, but size is $size"
  }

  case class MissingKey(full: DynamicOptic, prefix: DynamicOptic, key: Any) extends Warning {
    def message: String =
      s"During attempted access at ${full.toScalaString}, encountered missing key at ${prefix.toScalaString}"
  }

  case class EmptyMap(full: DynamicOptic, prefix: DynamicOptic) extends Warning {
    def message: String =
      s"During attempted access at ${full.toScalaString}, encountered an empty map at ${prefix.toScalaString}"
  }

  case class WrappingError(full: DynamicOptic, prefix: DynamicOptic, error: SchemaError) extends Error {
    def message: String =
      s"During attempted access at ${full.toScalaString}, encountered an error at ${prefix.toScalaString}: ${error.message}"
  }
}
