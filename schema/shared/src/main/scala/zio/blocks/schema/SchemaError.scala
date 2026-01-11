package zio.blocks.schema

import scala.collection.immutable.ArraySeq
import scala.util.control.NoStackTrace

final case class SchemaError(errors: ::[SchemaError.Single]) extends Exception with NoStackTrace {
  def ++(other: SchemaError): SchemaError = SchemaError(new ::(errors.head, errors.tail ++ other.errors))

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

object SchemaError {
  def expectationMismatch(trace: List[DynamicOptic.Node], expectation: String): SchemaError =
    new SchemaError(new ::(new ExpectationMismatch(toDynamicOptic(trace), expectation), Nil))

  def missingField(trace: List[DynamicOptic.Node], fieldName: String): SchemaError =
    new SchemaError(new ::(new MissingField(toDynamicOptic(trace), fieldName), Nil))

  def duplicatedField(trace: List[DynamicOptic.Node], fieldName: String): SchemaError =
    new SchemaError(new ::(new DuplicatedField(toDynamicOptic(trace), fieldName), Nil))

  def unknownCase(trace: List[DynamicOptic.Node], caseName: String): SchemaError =
    new SchemaError(new ::(new UnknownCase(toDynamicOptic(trace), caseName), Nil))

  private[this] def toDynamicOptic(trace: List[DynamicOptic.Node]): DynamicOptic = {
    val nodes = trace.toArray
    reverse(nodes)
    new DynamicOptic(ArraySeq.unsafeWrapArray(nodes))
  }

  private[this] def reverse(nodes: Array[DynamicOptic.Node]): Unit =
    if (nodes.length > 1) {
      var idx1 = 0
      var idx2 = nodes.length - 1
      while (idx1 < idx2) {
        val node = nodes(idx1)
        nodes(idx1) = nodes(idx2)
        nodes(idx2) = node
        idx1 += 1
        idx2 -= 1
      }
    }

  sealed trait Single {
    def message: String

    def source: DynamicOptic
  }

  case class MissingField(source: DynamicOptic, fieldName: String) extends Single {
    override def message: String = s"Missing field '$fieldName' at: $source"
  }

  case class DuplicatedField(source: DynamicOptic, fieldName: String) extends Single {
    override def message: String = s"Duplicated field '$fieldName' at: $source"
  }

  case class ExpectationMismatch(source: DynamicOptic, expectation: String) extends Single {
    override def message: String = s"$expectation at: $source"
  }

  case class UnknownCase(source: DynamicOptic, caseName: String) extends Single {
    override def message: String = s"Unknown case '$caseName' at: $source"
  }

  // ============================================================================
  // Migration-specific errors
  // ============================================================================

  /** Type mismatch during migration (expected vs actual type). */
  case class TypeMismatch(source: DynamicOptic, expected: String, actual: String) extends Single {
    override def message: String = s"Type mismatch at $source: expected $expected but got $actual"
  }

  /** Schema expression evaluation failed during migration. */
  case class EvaluationFailed(source: DynamicOptic, reason: String) extends Single {
    override def message: String = s"Expression evaluation failed at $source: $reason"
  }

  /** Migration action could not be reversed. */
  case class NotReversible(source: DynamicOptic, action: String) extends Single {
    override def message: String = s"Migration action '$action' is not reversible at: $source"
  }

  // Migration-specific factory methods

  def typeMismatch(source: DynamicOptic, expected: String, actual: String): SchemaError =
    new SchemaError(new ::(TypeMismatch(source, expected, actual), Nil))

  def evaluationFailed(source: DynamicOptic, reason: String): SchemaError =
    new SchemaError(new ::(EvaluationFailed(source, reason), Nil))

  def notReversible(source: DynamicOptic, action: String): SchemaError =
    new SchemaError(new ::(NotReversible(source, action), Nil))
}
