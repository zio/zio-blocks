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

  // Patch-specific error types
  case class FieldNotFoundError(source: DynamicOptic, fieldName: String, availableFields: List[String]) extends Single {
    override def message: String = s"Field '$fieldName' not found at: $source. Available fields: ${availableFields.mkString(", ")}"
  }

  case class TypeMismatchError(source: DynamicOptic, expected: String, actual: String) extends Single {
    override def message: String = s"Type mismatch at: $source. Expected $expected, got $actual"
  }

  case class CaseMismatchError(source: DynamicOptic, expected: String, actual: String) extends Single {
    override def message: String = s"Case mismatch at: $source. Expected $expected, got $actual"
  }

  case class IndexOutOfBoundsError(source: DynamicOptic, index: Int, size: Int) extends Single {
    override def message: String = s"Index $index out of bounds (size: $size) at: $source"
  }

  case class KeyNotFoundError(source: DynamicOptic, key: String) extends Single {
    override def message: String = s"Key '$key' not found at: $source"
  }

  case class KeyAlreadyExistsError(source: DynamicOptic, key: String) extends Single {
    override def message: String = s"Key '$key' already exists at: $source"
  }

  // Factory methods for patch operations (use root optic by default)
  def FieldNotFound(fieldName: String, availableFields: List[String]): SchemaError =
    new SchemaError(new ::(FieldNotFoundError(DynamicOptic.root, fieldName, availableFields), Nil))

  def TypeMismatch(expected: String, actual: String): SchemaError =
    new SchemaError(new ::(TypeMismatchError(DynamicOptic.root, expected, actual), Nil))

  def CaseMismatch(expected: String, actual: String): SchemaError =
    new SchemaError(new ::(CaseMismatchError(DynamicOptic.root, expected, actual), Nil))

  def IndexOutOfBounds(index: Int, size: Int): SchemaError =
    new SchemaError(new ::(IndexOutOfBoundsError(DynamicOptic.root, index, size), Nil))

  def KeyNotFound(key: String): SchemaError =
    new SchemaError(new ::(KeyNotFoundError(DynamicOptic.root, key), Nil))

  def KeyAlreadyExists(key: String): SchemaError =
    new SchemaError(new ::(KeyAlreadyExistsError(DynamicOptic.root, key), Nil))
}
