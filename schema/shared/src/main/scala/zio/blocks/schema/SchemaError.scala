package zio.blocks.schema

import scala.collection.immutable.ArraySeq
import scala.util.control.NoStackTrace

final case class SchemaError(errors: ::[SchemaError.Single]) extends Exception with NoStackTrace {
  def ++(other: SchemaError): SchemaError =
    SchemaError(new ::(errors.head, errors.tail ::: other.errors))

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

  def flatten: List[SchemaError.Single] = errors

  def atPath(segment: String): SchemaError =
    SchemaError(new ::(errors.head.prependPath(segment), errors.tail.map(_.prependPath(segment))))
}

object SchemaError {

  // --- Helpers for existing errors ---
  def expectationMismatch(trace: List[DynamicOptic.Node], expectation: String): SchemaError =
    new SchemaError(new ::(new ExpectationMismatch(toDynamicOptic(trace), expectation), Nil))

  def missingField(trace: List[DynamicOptic.Node], fieldName: String): SchemaError =
    new SchemaError(new ::(new MissingField(toDynamicOptic(trace), fieldName), Nil))

  def duplicatedField(trace: List[DynamicOptic.Node], fieldName: String): SchemaError =
    new SchemaError(new ::(new DuplicatedField(toDynamicOptic(trace), fieldName), Nil))

  def unknownCase(trace: List[DynamicOptic.Node], caseName: String): SchemaError =
    new SchemaError(new ::(new UnknownCase(toDynamicOptic(trace), caseName), Nil))

  // --- Helpers for Macro Derivation ---

  private val emptyOptic = new DynamicOptic(ArraySeq.empty)

  def validationFailed(message: String, value: Any, targetType: String): SchemaError =
    new SchemaError(new ::(new ValidationFailed(emptyOptic, message, value, targetType), Nil))

  def numericOverflow(value: Any, sourceType: String, targetType: String): SchemaError =
    new SchemaError(new ::(new NumericOverflow(emptyOptic, value, sourceType, targetType), Nil))

  def typeMismatch(expected: String, actual: String): SchemaError =
    new SchemaError(new ::(new TypeMismatch(emptyOptic, expected, actual), Nil))

  // Updated to use typeName
  def missingField(fieldName: String, typeName: String): SchemaError =
    new SchemaError(new ::(new MissingField(emptyOptic, fieldName, typeName), Nil))

  def accumulateErrors[A](results: Seq[Either[SchemaError, A]]): Either[SchemaError, Seq[A]] = {
    val (errors, successes) = results.partitionMap(identity)
    if (errors.nonEmpty) {
      val allSingles = errors.flatMap(_.flatten).toList
      allSingles match {
        case h :: t => Left(SchemaError(new ::(h, t)))
        case Nil    => Right(successes)
      }
    } else {
      Right(successes)
    }
  }

  // --- Internals ---

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
    def prependPath(segment: String): Single
  }

  case class MissingField(source: DynamicOptic, fieldName: String, typeName: String = "unknown") extends Single {
    override def message: String                      = s"Missing required field '$fieldName' (type: $typeName) at: $source"
    override def prependPath(segment: String): Single = this
  }

  case class DuplicatedField(source: DynamicOptic, fieldName: String) extends Single {
    override def message: String                      = s"Duplicated field '$fieldName' at: $source"
    override def prependPath(segment: String): Single = this
  }

  case class ExpectationMismatch(source: DynamicOptic, expectation: String) extends Single {
    override def message: String                      = s"$expectation at: $source"
    override def prependPath(segment: String): Single = this
  }

  case class UnknownCase(source: DynamicOptic, caseName: String) extends Single {
    override def message: String                      = s"Unknown case '$caseName' at: $source"
    override def prependPath(segment: String): Single = this
  }

  case class ValidationFailed(
    source: DynamicOptic,
    msg: String,
    value: Any,
    targetType: String,
    path: List[String] = Nil
  ) extends Single {
    override def message: String = {
      val p = if (path.isEmpty) "<root>" else path.mkString(".")
      s"Validation failed at $p: $msg. Value '$value' cannot be converted to $targetType."
    }
    override def prependPath(segment: String): Single = copy(path = segment :: path)
  }

  case class NumericOverflow(
    source: DynamicOptic,
    value: Any,
    sourceType: String,
    targetType: String,
    path: List[String] = Nil
  ) extends Single {
    override def message: String = {
      val p = if (path.isEmpty) "<root>" else path.mkString(".")
      s"Numeric overflow at $p: Value $value ($sourceType) is out of bounds for $targetType."
    }
    override def prependPath(segment: String): Single = copy(path = segment :: path)
  }

  case class TypeMismatch(source: DynamicOptic, expected: String, actual: String, path: List[String] = Nil)
      extends Single {
    override def message: String = {
      val p = if (path.isEmpty) "<root>" else path.mkString(".")
      s"Type mismatch at $p: Expected $expected, got $actual."
    }
    override def prependPath(segment: String): Single = copy(path = segment :: path)
  }
}
