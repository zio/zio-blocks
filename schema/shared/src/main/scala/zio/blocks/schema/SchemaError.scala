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

  /** Prepends a field access to the path of all errors in this SchemaError. */
  def atField(name: String): SchemaError =
    mapSource(path => DynamicOptic.root.field(name)(path))

  /** Prepends an index access to the path of all errors in this SchemaError. */
  def atIndex(index: Int): SchemaError =
    mapSource(path => DynamicOptic.root.at(index)(path))

  /** Prepends a case access to the path of all errors in this SchemaError. */
  def atCase(name: String): SchemaError =
    mapSource(path => DynamicOptic.root.caseOf(name)(path))

  /**
   * Prepends a map key access to the path of all errors in this SchemaError.
   */
  def atKey(key: DynamicValue): SchemaError =
    mapSource(path => new DynamicOptic(DynamicOptic.Node.AtMapKey(key) +: path.nodes))

  private def mapSource(f: DynamicOptic => DynamicOptic): SchemaError =
    SchemaError(new ::(SchemaError.remapSource(errors.head, f), errors.tail.map(SchemaError.remapSource(_, f))))
}

object SchemaError {
  def conversionFailed(trace: List[DynamicOptic.Node], details: String): SchemaError =
    new SchemaError(new ::(ConversionFailed(toDynamicOptic(trace), details, None), Nil))

  def conversionFailed(contextMessage: String, cause: SchemaError): SchemaError =
    new SchemaError(
      new ::(
        ConversionFailed(
          DynamicOptic.root,
          contextMessage,
          Some(cause)
        ),
        Nil
      )
    )

  def expectationMismatch(trace: List[DynamicOptic.Node], expectation: String): SchemaError =
    new SchemaError(new ::(new ExpectationMismatch(toDynamicOptic(trace), expectation), Nil))

  def missingField(trace: List[DynamicOptic.Node], fieldName: String): SchemaError =
    new SchemaError(new ::(new MissingField(toDynamicOptic(trace), fieldName), Nil))

  def duplicatedField(trace: List[DynamicOptic.Node], fieldName: String): SchemaError =
    new SchemaError(new ::(new DuplicatedField(toDynamicOptic(trace), fieldName), Nil))

  def unknownCase(trace: List[DynamicOptic.Node], caseName: String): SchemaError =
    new SchemaError(new ::(new UnknownCase(toDynamicOptic(trace), caseName), Nil))

  /**
   * Creates a SchemaError from a validation failure message. Use this to
   * convert string-based validation errors (e.g., from smart constructors) into
   * SchemaError.
   */
  def validationFailed(message: String): SchemaError =
    conversionFailed(Nil, message)

  /**
   * Creates a SchemaError with a simple message at the given path. This is the
   * primary replacement for JsonError(message, path) and
   * DynamicValueError(message, path).
   */
  def message(details: String, path: DynamicOptic = DynamicOptic.root): SchemaError =
    new SchemaError(new ::(Message(path, details), Nil))

  /**
   * Creates a SchemaError with just a message at the root path.
   */
  def apply(details: String): SchemaError = message(details)

  private[schema] def remapSource(single: Single, f: DynamicOptic => DynamicOptic): Single =
    single match {
      case ConversionFailed(src, details, cause) => ConversionFailed(f(src), details, cause)
      case MissingField(src, fieldName)          => MissingField(f(src), fieldName)
      case DuplicatedField(src, fieldName)       => DuplicatedField(f(src), fieldName)
      case ExpectationMismatch(src, exp)         => ExpectationMismatch(f(src), exp)
      case UnknownCase(src, caseName)            => UnknownCase(f(src), caseName)
      case Message(src, details)                 => Message(f(src), details)
    }

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

  /** Sub-trait for Into conversion errors */
  sealed trait IntoError extends Single {
    def source: DynamicOptic
  }

  case class ConversionFailed(
    source: DynamicOptic,
    details: String,
    cause: Option[SchemaError] = None
  ) extends IntoError {
    override def message: String =
      cause match {
        case Some(causeErr) =>
          val causeMessages =
            if (causeErr.errors.isEmpty) " <no further details>"
            else if (causeErr.errors.length == 1) s"  Caused by: ${causeErr.errors.head.message}"
            else "Caused by:\n" + causeErr.errors.map(e => s"  - ${e.message}").mkString("\n")
          s"$details\n$causeMessages"
        case None => details
      }
  }

  case class MissingField(source: DynamicOptic, fieldName: String) extends Single {
    override def message: String = s"Missing field '$fieldName' at: ${source.toScalaString}"
  }

  case class DuplicatedField(source: DynamicOptic, fieldName: String) extends Single {
    override def message: String = s"Duplicated field '$fieldName' at: ${source.toScalaString}"
  }

  case class ExpectationMismatch(source: DynamicOptic, expectation: String) extends Single {
    override def message: String = s"$expectation at: ${source.toScalaString}"
  }

  case class UnknownCase(source: DynamicOptic, caseName: String) extends Single {
    override def message: String = s"Unknown case '$caseName' at: ${source.toScalaString}"
  }

  /**
   * A generic message error with a path. This is the primary replacement for
   * JsonError and DynamicValueError.
   */
  case class Message(source: DynamicOptic, details: String) extends Single {
    override def message: String =
      if (source.nodes.isEmpty) details
      else s"$details at: ${source.toString}"
  }
}
