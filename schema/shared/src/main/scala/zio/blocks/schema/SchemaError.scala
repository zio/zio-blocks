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

  def missingField(at: DynamicOptic, fieldName: String): SchemaError =
    new SchemaError(new ::(new MissingField(at, fieldName), Nil))

  def duplicatedField(trace: List[DynamicOptic.Node], fieldName: String): SchemaError =
    new SchemaError(new ::(new DuplicatedField(toDynamicOptic(trace), fieldName), Nil))

  def unknownCase(trace: List[DynamicOptic.Node], caseName: String): SchemaError =
    new SchemaError(new ::(new UnknownCase(toDynamicOptic(trace), caseName), Nil))

  // Migration-specific error constructors
  def fieldAlreadyExists(at: DynamicOptic, fieldName: String): SchemaError =
    new SchemaError(new ::(new FieldAlreadyExists(at, fieldName), Nil))

  def typeMismatch(at: DynamicOptic, expected: String, actual: String): SchemaError =
    new SchemaError(new ::(new TypeMismatch(at, expected, actual), Nil))

  def transformFailed(at: DynamicOptic, reason: String): SchemaError =
    new SchemaError(new ::(new TransformFailed(at, reason), Nil))

  def invalidPath(at: DynamicOptic, reason: String): SchemaError =
    new SchemaError(new ::(new InvalidPath(at, reason), Nil))

  def mandatoryFieldMissing(at: DynamicOptic, fieldName: String): SchemaError =
    new SchemaError(new ::(new MandatoryFieldMissing(at, fieldName), Nil))

  def incompatibleSchemas(reason: String): SchemaError =
    new SchemaError(new ::(new IncompatibleSchemas(reason), Nil))

  def validationFailed(at: DynamicOptic, reason: String): SchemaError =
    new SchemaError(new ::(new ValidationFailed(at, reason), Nil))

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

  /** Sub-trait for migration-specific errors */
  sealed trait MigrationError extends Single {
    def source: DynamicOptic
  }

  case class FieldAlreadyExists(source: DynamicOptic, fieldName: String) extends MigrationError {
    override def message: String = s"Field '$fieldName' already exists at: $source"
  }

  case class TypeMismatch(source: DynamicOptic, expected: String, actual: String) extends MigrationError {
    override def message: String =
      s"Type mismatch at: $source - expected $expected but got $actual"
  }

  case class TransformFailed(source: DynamicOptic, reason: String) extends MigrationError {
    override def message: String = s"Transform failed at: $source - $reason"
  }

  case class InvalidPath(source: DynamicOptic, reason: String) extends MigrationError {
    override def message: String = s"Invalid path at: $source - $reason"
  }

  case class MandatoryFieldMissing(source: DynamicOptic, fieldName: String) extends MigrationError {
    override def message: String =
      s"Mandatory field '$fieldName' is missing at: $source"
  }

  case class IncompatibleSchemas(reason: String) extends MigrationError {
    override def message: String      = s"Incompatible schemas: $reason"
    override def source: DynamicOptic = DynamicOptic.root
  }

  case class ValidationFailed(source: DynamicOptic, reason: String) extends MigrationError {
    override def message: String = s"Validation failed at: $source - $reason"
  }
}
